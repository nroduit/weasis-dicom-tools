#!/usr/bin/env bash
#
# Runs org.dcm4che3.img.bench.CodecBenchmark in a *forked* JVM.
# (Maven's exec:java runs in-process and ignores -Djava.library.path, so the
#  OpenCV native library never loads — hence this launcher.)
#
# Usage:
#   run-codec-benchmark.sh <sample-dir> [warmup] [iterations]
#
#   warmup       untimed passes per file (default 2)
#   iterations   timed passes per file, median reported (default 5)
#
#   Transcode re-encodes each file back to its own (source) codec — a same-codec round-trip — so the
#   decoder and encoder always match. There is no destination-syntax argument.
#
# Env overrides:
#   OUT=<file.csv>         also write the CSV to this file (default: stdout only). When set,
#                          a <file>.json build-metadata sidecar is written next to it
#                          (host CPU, OS/arch, git commit) — same shape as the CI run.
#   WEASIS_VERSION=<x.y.z> rebuild weasis-dicom-tools against this weasis-core-img (native) version
#                          first (use for the A/B baseline run, e.g. WEASIS_VERSION=4.12.0.1)
#   JAVA_OPTS=...          JVM options (default: -Xms2g -Xmx8g)
#   TASKSET=0-7           pin to these CPUs via taskset for stable numbers (Linux)
#   OFFLINE=0             build online instead of offline (-o). Default 1 (offline, for
#                          stable numbers); set 0 on a fresh clone so Maven can download
#                          the deps/native into ~/.m2 the first time.
#
# A/B example:
#   OUT=candidate.csv ./run-codec-benchmark.sh /data/samples
#   OUT=baseline.csv  WEASIS_VERSION=4.12.0.1 ./run-codec-benchmark.sh /data/samples
#   diff <(cut -d, -f1,11 baseline.csv) <(cut -d, -f1,11 candidate.csv)   # decode_sha1 (lossless gate)
#
set -euo pipefail

MODULE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$MODULE_DIR/.." && pwd)"
MVN="${MVN:-mvn}"

SAMPLE_DIR="${1:-}"
WARMUP="${2:-2}"
ITERS="${3:-5}"
OUT="${OUT:-}"
WEASIS_VERSION="${WEASIS_VERSION:-}"
# Split JAVA_OPTS into an array so a multi-token value (e.g. "-Xms2g -Xmx8g") becomes
# separate java arguments, not one bogus option.
read -r -a JAVA_OPTS_ARR <<< "${JAVA_OPTS:--Xms2g -Xmx8g}"
OFF=(-o); [ "${OFFLINE:-1}" = 0 ] && OFF=()

if [ -z "$SAMPLE_DIR" ]; then
  sed -n '2,26p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//' >&2
  exit 2
fi

# Make user-supplied paths absolute *before* cd'ing into the reactor root below,
# so they keep working regardless of where the script was invoked from.
case "$SAMPLE_DIR" in /*) ;; *) SAMPLE_DIR="$PWD/$SAMPLE_DIR" ;; esac
[ -n "$OUT" ] && case "$OUT" in /*) ;; *) OUT="$PWD/$OUT" ;; esac

# -pl resolves against the reactor root, so run everything from the repo root.
cd "$REPO_ROOT"

# Native lib dir name = <os-name>-<cpu-name>, matching the root pom OS/arch profiles.
case "$(uname -s)" in
  Linux)  OS=linux ;;
  Darwin) OS=macosx ;;
  *) echo "Unsupported OS '$(uname -s)' — use run-codec-benchmark.bat on Windows." >&2; exit 1 ;;
esac
case "$(uname -m)" in
  x86_64|amd64)  ARCH=x86-64 ;;
  aarch64|arm64) ARCH=aarch64 ;;
  *) echo "Unsupported arch '$(uname -m)'." >&2; exit 1 ;;
esac
NATIVE_DIR_NAME="${OS}-${ARCH}"

VFLAG=()
[ -n "$WEASIS_VERSION" ] && VFLAG=(-Dweasis.core.img.version="$WEASIS_VERSION")

# Install the weasis-dicom-tools wrapper (built against the chosen native lib) into the local
# repo so the benchmark's classpath can resolve it, then compile the benchmark + copy the native.
echo "# building (weasis-core-img=${WEASIS_VERSION:-default}) ..." >&2
"$MVN" "${OFF[@]+"${OFF[@]}"}" -q install -DskipTests "${VFLAG[@]+"${VFLAG[@]}"}" -pl weasis-dicom-tools -am
"$MVN" "${OFF[@]+"${OFF[@]}"}" -q process-classes "${VFLAG[@]+"${VFLAG[@]}"}" -pl benchmark

LIB_DIR="$MODULE_DIR/target/lib/$NATIVE_DIR_NAME"
if [ ! -d "$LIB_DIR" ]; then
  echo "Native lib dir not found: $LIB_DIR" >&2
  echo "Available: $(ls "$MODULE_DIR/target/lib" 2>/dev/null || echo none)" >&2
  exit 1
fi

CP_FILE="$MODULE_DIR/target/codec-bench-cp.txt"
"$MVN" "${OFF[@]+"${OFF[@]}"}" -q dependency:build-classpath "${VFLAG[@]+"${VFLAG[@]}"}" -pl benchmark \
  -Dmdep.includeScope=runtime -Dmdep.outputFile="$CP_FILE"
CP="$MODULE_DIR/target/classes:$(cat "$CP_FILE")"

RUN=(java "${JAVA_OPTS_ARR[@]}"
  -Dweasis.core.img.version="${WEASIS_VERSION:-default}"
  -Djava.library.path="$LIB_DIR"
  --enable-native-access=ALL-UNNAMED
  -cp "$CP"
  org.dcm4che3.img.bench.CodecBenchmark "$SAMPLE_DIR" "$WARMUP" "$ITERS")

if [ -n "${TASKSET:-}" ] && command -v taskset >/dev/null 2>&1; then
  RUN=(taskset -c "$TASKSET" "${RUN[@]}")
fi

if [ -n "$OUT" ]; then
  "${RUN[@]}" | tee "$OUT"
  # Build-metadata sidecar next to the CSV (parity with the CI run).
  bash "$MODULE_DIR/collect-metadata.sh" "$NATIVE_DIR_NAME" "${OUT%.csv}.json" || true
else
  "${RUN[@]}"
fi