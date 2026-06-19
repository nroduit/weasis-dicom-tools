#!/usr/bin/env bash
#
# Downloads the codec-benchmark corpus from the Weasis demo archive (static GitHub
# Pages, https://nroduit.github.io/demo-archive/demo/) into a local directory, using
# the vendored Weasis manifests under benchmark/manifests/ as the file list.
#
# Each manifest's <arcQuery baseUrl="..."> + every <Instance DirectDownloadFile="...">
# resolves to one byte-exact stored DICOM object (original transfer syntax — the archive
# is a plain static file host, so nothing is transcoded). Files land under
#   <out-dir>/<manifest-name>/<DirectDownloadFile>
# so CodecBenchmark's recursive walk picks them up with readable per-category labels.
#
# Reproducibility: every downloaded file is checked against benchmark/corpus.lock
# (sha256<TAB>relative-path). A mismatch or a missing/extra file fails the run, so a
# re-curation of the demo data surfaces loudly instead of silently skewing throughput
# numbers that get committed under benchmark/results/.
#
# Usage:
#   fetch-corpus.sh [out-dir]          # download + verify against corpus.lock (default)
#
# Env:
#   LOCK_MODE=verify   (default) fail on any checksum mismatch / missing / extra file
#   LOCK_MODE=write    regenerate benchmark/corpus.lock from what was downloaded
#                      (the explicit "I am bumping the corpus" action)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MANIFEST_DIR="$SCRIPT_DIR/manifests"
LOCK="$SCRIPT_DIR/corpus.lock"
OUT_DIR="${1:-$SCRIPT_DIR/corpus}"
LOCK_MODE="${LOCK_MODE:-verify}"

case "$OUT_DIR" in /*) ;; *) OUT_DIR="$PWD/$OUT_DIR" ;; esac

# Cross-platform sha256 (sha256sum on Linux, shasum on macOS).
if command -v sha256sum >/dev/null 2>&1; then
  sha256() { sha256sum "$1" | cut -d' ' -f1; }
elif command -v shasum >/dev/null 2>&1; then
  sha256() { shasum -a 256 "$1" | cut -d' ' -f1; }
else
  echo "fetch-corpus: need sha256sum or shasum on PATH" >&2; exit 1
fi

shopt -s nullglob
manifests=("$MANIFEST_DIR"/*.xml)
if [ ${#manifests[@]} -eq 0 ]; then
  echo "fetch-corpus: no manifests in $MANIFEST_DIR" >&2; exit 1
fi

# Attribute extraction: the manifest is a flat, attribute-only IOD, so grep is robust and
# avoids the default-namespace pain xmllint/xpath would add. baseUrl is per-arcQuery.
attr() { grep -o "$1=\"[^\"]*\"" "$2" | sed "s/^$1=\"//;s/\"$//"; }

mkdir -p "$OUT_DIR"
declare -a downloaded=()
total=0

for manifest in "${manifests[@]}"; do
  category="$(basename "${manifest%.xml}")"
  base="$(attr baseUrl "$manifest" | head -n1)"
  if [ -z "$base" ]; then
    echo "fetch-corpus: no baseUrl in $manifest" >&2; exit 1
  fi
  case "$base" in */) ;; *) base="$base/" ;; esac

  while IFS= read -r rel; do
    [ -n "$rel" ] || continue
    rel="${rel#/}"                                   # DirectDownloadFile may be absolute-rooted
    dest="$OUT_DIR/$category/$rel"
    relpath="$category/$rel"
    mkdir -p "$(dirname "$dest")"
    # Skip re-download when the file is already present and already matches the lock — keeps
    # local re-runs and warm caches fast. (In verify mode an out-of-date file is re-fetched.)
    if [ -f "$dest" ] && [ "$LOCK_MODE" = verify ] && [ -f "$LOCK" ] \
       && grep -qx "$(sha256 "$dest")	$relpath" "$LOCK"; then
      downloaded+=("$relpath"); total=$((total + 1)); continue
    fi
    echo "# GET ${base}${rel}" >&2
    curl -sSfL --retry 3 --retry-delay 2 -o "$dest" "${base}${rel}"
    downloaded+=("$relpath"); total=$((total + 1))
  done < <(attr DirectDownloadFile "$manifest")
done

echo "# downloaded $total file(s) into $OUT_DIR" >&2

if [ "$LOCK_MODE" = write ]; then
  : > "$LOCK"
  for relpath in $(printf '%s\n' "${downloaded[@]}" | sort); do
    printf '%s\t%s\n' "$(sha256 "$OUT_DIR/$relpath")" "$relpath" >> "$LOCK"
  done
  echo "# wrote $LOCK ($total entries)" >&2
  exit 0
fi

# verify: every lock entry must be present and match; no extra files outside the lock.
if [ ! -f "$LOCK" ]; then
  echo "fetch-corpus: $LOCK missing — run once with LOCK_MODE=write to create it" >&2; exit 1
fi
fail=0
while IFS=$'\t' read -r want relpath; do
  [ -n "$relpath" ] || continue
  dest="$OUT_DIR/$relpath"
  if [ ! -f "$dest" ]; then echo "MISSING  $relpath" >&2; fail=1; continue; fi
  got="$(sha256 "$dest")"
  if [ "$got" != "$want" ]; then echo "MISMATCH $relpath: want $want got $got" >&2; fail=1; fi
done < "$LOCK"
expected=$(grep -c $'\t' "$LOCK" || true)
if [ "$total" -ne "$expected" ]; then
  echo "COUNT mismatch: downloaded $total, lock has $expected" >&2; fail=1
fi
if [ "$fail" -ne 0 ]; then
  echo "fetch-corpus: corpus does not match $LOCK (demo data changed? run LOCK_MODE=write to re-pin)" >&2
  exit 1
fi
echo "# verified $total file(s) against $LOCK" >&2