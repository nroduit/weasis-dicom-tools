#!/usr/bin/env bash
# SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
#
# Emit the benchmark build-metadata sidecar JSON for one platform. Shared by the
# native-benchmark CI workflow and the local launcher so committed results carry the
# same provenance regardless of where the run happened.
#
# Usage: collect-metadata.sh <classifier> <out.json>
#
# Host facts (CPU model/cores, OS/arch, git sha/ref) are detected here. CI supplies the
# rest via the environment; locally they are blank / derived:
#   RUNNER_LABEL   runner label (CI) — defaults to "local:<hostname>"
#   EMULATED       true|false (CI sets it for the Windows-on-ARM leg) — defaults to false
#   GIT_SHA/GIT_REF  override the detected git commit/branch
#   RUN_URL        CI run URL — blank locally
#   ImageOS/ImageVersion  GitHub-hosted image (auto-set on CI runners) — blank locally
set -euo pipefail

CLASSIFIER="${1:?usage: collect-metadata.sh <classifier> <out.json>}"
OUT="${2:?usage: collect-metadata.sh <classifier> <out.json>}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Host OS/arch: prefer CI's RUNNER_* (Linux/macOS/Windows), else derive from uname.
os="${RUNNER_OS:-}"
[ -n "$os" ] || case "$(uname -s)" in Linux) os=Linux ;; Darwin) os=macOS ;; *) os="$(uname -s)" ;; esac
arch="${RUNNER_ARCH:-$(uname -m)}"

case "$os" in
  Linux)   cpu_model=$(lscpu 2>/dev/null | sed -n 's/^Model name: *//p' | head -1)
           cpu_count=$(getconf _NPROCESSORS_ONLN 2>/dev/null) ;;
  macOS)   cpu_model=$(sysctl -n machdep.cpu.brand_string 2>/dev/null)
           cpu_count=$(getconf _NPROCESSORS_ONLN 2>/dev/null) ;;
  Windows) cpu_model=$(wmic cpu get name /value 2>/dev/null | sed -n 's/^Name=//p' | tr -d '\r' | head -1)
           cpu_count="${NUMBER_OF_PROCESSORS:-}" ;;
  *)       cpu_model=""; cpu_count=$(getconf _NPROCESSORS_ONLN 2>/dev/null) ;;
esac
[ -n "$cpu_model" ] || cpu_model="${PROCESSOR_IDENTIFIER:-unknown}"

git_sha="${GIT_SHA:-$(git -C "$HERE" rev-parse HEAD 2>/dev/null || true)}"
git_ref="${GIT_REF:-$(git -C "$HERE" rev-parse --abbrev-ref HEAD 2>/dev/null || true)}"
runner_label="${RUNNER_LABEL:-local:$(hostname 2>/dev/null || echo unknown)}"

printf '{\n  "classifier": "%s",\n  "runner_label": "%s",\n  "runner_os": "%s",\n  "runner_arch": "%s",\n  "emulated": %s,\n  "image_os": "%s",\n  "image_version": "%s",\n  "cpu_model": "%s",\n  "cpu_count": "%s",\n  "git_sha": "%s",\n  "git_ref": "%s",\n  "run_url": "%s"\n}\n' \
  "$CLASSIFIER" "$runner_label" "$os" "$arch" "${EMULATED:-false}" \
  "${ImageOS:-}" "${ImageVersion:-}" "$cpu_model" "$cpu_count" \
  "$git_sha" "$git_ref" "${RUN_URL:-}" \
  > "$OUT"