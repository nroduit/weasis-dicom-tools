#!/usr/bin/env bash
#
# Cross-distro test harness for the OpenCV native build.
#
# The shipped lib is glibc-DYNAMIC: it embeds libstdc++/libgcc/OpenCV but links glibc
# (NEEDED libc.so.6/libm/libpthread/libdl, ~228 @GLIBC_* imports up to GLIBC_2.27). So
# it runs on every glibc distro from ~2018 on. Alpine/musl also passes the decode path,
# but only because musl is a partial glibc drop-in and the path dodges the 2 glibc-only
# symbols musl lacks — it is best-effort, NOT supported. See README.md for the details.
#
# Stage 1 (host): inspect the .so — libstdc++/libgcc should be embedded (not NEEDED),
#                 and the highest @GLIBC_x.y sets the oldest glibc it can run on.
# Stage 2 (host): assemble a self-contained dist/ (runtime jars + native lib +
#                 sample DICOM + compiled SmokeTest) so containers need only a JRE.
# Stage 3 (docker): run SmokeTest across musl + modern/older/enterprise glibc distros.
#                   JRE-install failures (network/TLS/EOL repos) report SKIP, not FAIL.
#
# Usage:
#   ./run-matrix.sh                 # full run: build dist + linkage check + matrix
#   ./run-matrix.sh --no-build      # reuse existing dist/
#   ./run-matrix.sh --check-only    # only the readelf/ldd linkage inspection
#   ARCH=linux-aarch64 PLATFORM=linux/arm64 ./run-matrix.sh   # cross-arch via QEMU
#
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
MODULE="$REPO/weasis-dicom-tools"
DIST="$HERE/dist"

# All runs mutate the shared dist/ (native lib + JRE staging). Two concurrent invocations
# clobber each other (one's ensure_portable_jre rm -rf dist/jre yanks it from under the
# other's running combo). Refuse to start if another run holds the lock. mkdir is atomic.
LOCK="$HERE/.run.lock"
acquire_lock() {
  if ! mkdir "$LOCK" 2>/dev/null; then
    err "another run-matrix.sh is using $DIST (lock: $LOCK)."
    err "wait for it to finish, or remove the lock if it is stale: rmdir '$LOCK'"
    exit 2
  fi
  trap 'rmdir "$LOCK" 2>/dev/null || true' EXIT
}

# Linux classifier to test. This harness always tests a *Linux* build (it runs Linux
# containers), so ARCH is a linux-* classifier regardless of the host OS.
case "$(uname -m)" in
  x86_64|amd64)  DEFAULT_ARCH=linux-x86-64 ;;
  aarch64|arm64) DEFAULT_ARCH=linux-aarch64 ;;
  *)             DEFAULT_ARCH=linux-x86-64 ;;
esac
ARCH="${ARCH:-$DEFAULT_ARCH}"

# Native lib under test:
#   - NATIVE_LIB set  -> use that file as-is (e.g. a custom or musl build you built locally).
#   - NATIVE_LIB unset -> resolve the project's lib for $ARCH from the LOCAL Maven repo
#     (~/.m2), offline. This works on any host OS (a Mac's target/lib only holds the
#     macosx .dylib) and bypasses repo mirrors that don't carry the weasis OpenCV natives.
#     It resolves the exact version the build uses (e.g. 5.0.0-dcm).
# DYN=1 selects the "-dyn" classifier (libopencv_java-<ver>-<arch>-dyn.so) instead of the
# default static one. The -dyn build links libstdc++/libgcc DYNAMICALLY (NEEDED:
# libstdc++.so.6, libgcc_s.so.1) rather than embedding them, so it additionally requires
# the distro to provide a compatible libstdc++/GLIBCXX — i.e. it's less self-contained.
NATIVE_LIB="${NATIVE_LIB:-}"
DYN="${DYN:-0}"
RESOLVED_LIB=""

PLATFORM="${PLATFORM:-}"           # e.g. linux/arm64 to run under QEMU/binfmt
PLATFORM_ARG=()
[ -n "$PLATFORM" ] && PLATFORM_ARG=(--platform "$PLATFORM")

# Bytecode target for SmokeTest. Must be <= the JRE installed in the containers
# (the IMAGES below install JRE 17), independent of the host's javac version.
JRE_RELEASE="${JRE_RELEASE:-17}"

# Sample DICOM to decode. A compressed MULTI-FRAME file (JPEG2000) is used on purpose:
# decoding all its frames drives OpenCV's native worker threads, which is what can
# surface a missing glibc-only thread symbol on musl (see SmokeTest). Override with
# SAMPLE_SRC=/path/to/file.dcm.
SAMPLE_SRC="${SAMPLE_SRC:-$MODULE/src/test/resources/org/dcm4che3/img/jpeg2000-multiframe-multifragments.dcm}"
SAMPLE_NAME="$(basename "$SAMPLE_SRC")"

# Big-endian (Explicit VR Big Endian) sample for the byte-swap check. dcm4che swaps by
# transfer syntax, not host CPU, so the decoded-pixel hash must be identical on every
# arch; EXPECTED_BE_HASH is the golden value SmokeTest asserts against. If the file is
# absent the swap check is skipped (logged). Override BE_SAMPLE_SRC to point elsewhere.
BE_SAMPLE_SRC="${BE_SAMPLE_SRC:-$MODULE/src/test/resources/org/dcm4che3/img/US-RGB-8-epicard-Banded-bigendian.dcm}"
BE_SAMPLE_NAME="$(basename "$BE_SAMPLE_SRC")"
# Golden SHA-256 of the decoded pixels of US-RGB-8-epicard-Banded-bigendian.dcm (640x480
# RGB). Verified identical on Alpine/Ubuntu/Debian on linux-aarch64 AND under linux-x86-64
# emulation — the swap is host-arch invariant. Clear it (EXPECTED_BE_HASH="") to just print
# the hash instead of asserting, or override when changing BE_SAMPLE_SRC.
EXPECTED_BE_HASH="${EXPECTED_BE_HASH:-4f31564042a349abd8e556b92b7121d25cc3c63fd0ac36a109b33922a38261d7}"

# How many times to run the threaded decode per distro. This is runtime STRESS on a
# cleanly-linked libc (musl incompatibility is already caught deterministically by the
# ldd gate, not here). The glibc-on-musl crash is intermittent so it's not a reliable
# catch on its own; raise this only to stress a libc that links cleanly (e.g. a real
# musl build, or to fish for the crash on musl for demonstration). Each run is a fresh JVM.
SMOKE_RUNS="${SMOKE_RUNS:-3}"

# PORTABLE_JRE=1 stages a Temurin JRE into dist/jre (downloaded once on the HOST, which has
# working TLS, and cached). Containers then run /dist/jre/bin/java instead of installing a
# JRE — the only way to actually RUN the JVM checks on EOL distros (CentOS 7, Debian 9)
# whose package repos are dead and which never shipped JDK 17. Temurin's glibc build needs
# glibc 2.17 (= the x86-64 floor), so it runs on every glibc distro the lib supports. Not
# for musl/Alpine (a glibc JRE won't run there — but Alpine fails the gate first anyway).
PORTABLE_JRE="${PORTABLE_JRE:-0}"

# Distro matrix: image -> JRE install command. Spans musl + a wide glibc-age range, oldest
# first. The glibc FLOOR is arch-dependent (x86-64 build needs up to GLIBC_2.17; aarch64
# build needs up to GLIBC_2.27), so an old distro can pass on x86-64 yet fail on aarch64 —
# the ldd gate decides per-arch. Distros whose package repos are EOL (centos:7, debian:9)
# or unreachable usually SKIP on the libc where they'd otherwise pass; the gate still gives
# a deterministic verdict without a JRE. archlinux is published amd64-only -> on aarch64 the
# image can't be pulled and it SKIPs (exit 125); run it under PLATFORM=linux/amd64.
APT='export DEBIAN_FRONTEND=noninteractive; apt-get update -qq && apt-get install -y -qq openjdk-17-jre-headless'
IMAGES=(
  "alpine:3|apk add --no-cache openjdk17-jre-headless"      # musl
  "centos:7|yum -y -q install java-17-openjdk-headless"     # glibc 2.17 (EOL repos)
  "debian:9|$APT"                                            # glibc 2.24 (EOL repos)
  "ubuntu:20.04|$APT"                                        # glibc 2.31
  "debian:11|$APT"                                           # glibc 2.31
  "ubuntu:22.04|$APT"                                        # glibc 2.35
  "rockylinux:8|dnf install -y -q java-17-openjdk-headless"  # glibc 2.28
  "rockylinux:9|dnf install -y -q java-17-openjdk-headless"  # glibc 2.34
  "archlinux:latest|pacman -Sy --noconfirm --needed jre17-openjdk"  # newest glibc (amd64-only image)
)

log() { printf '\n\033[1;34m== %s\033[0m\n' "$*"; }
err() { printf '\033[1;31m%s\033[0m\n' "$*" >&2; }

# Resolve the native .so into $RESOLVED_LIB. Uses $NATIVE_LIB verbatim when set,
# otherwise copies the $ARCH classifier from the local ~/.m2 repo (offline).
resolve_native() {
  if [ -n "$NATIVE_LIB" ]; then
    [ -f "$NATIVE_LIB" ] || { err "NATIVE_LIB not found: $NATIVE_LIB"; return 1; }
    RESOLVED_LIB="$NATIVE_LIB"
    return 0
  fi
  local cache="$HERE/.native-cache"
  rm -rf "$cache"; mkdir -p "$cache"
  log "Resolving libopencv_java ($ARCH) from local ~/.m2 (offline)"
  if ! ( cd "$REPO" && mvn -o -q -f weasis-dicom-tools dependency:copy-dependencies \
        -DincludeGroupIds=org.weasis.thirdparty.org.opencv \
        -DincludeArtifactIds=libopencv_java \
        -DincludeClassifiers="$ARCH" \
        -DoutputDirectory="$cache" ); then
    err "offline resolve failed for classifier $ARCH — build the project once (mvn -f weasis-dicom-tools install) so the native is cached in ~/.m2."
    return 1
  fi
  RESOLVED_LIB="$(ls "$cache"/libopencv_java-*-"$ARCH".so 2>/dev/null | head -1)"
  [ -n "$RESOLVED_LIB" ] || { err "no libopencv_java-*-$ARCH.so resolved into $cache"; return 1; }

  # DYN=1: switch to the "-dyn" classifier. It is not a declared dependency, so it isn't
  # in the copy-dependencies output — fetch the sibling directly from ~/.m2 (same version
  # dir as the static one we just resolved).
  if [ "$DYN" = 1 ]; then
    local base ver dynsrc
    base="$(basename "$RESOLVED_LIB")"          # libopencv_java-<ver>-<arch>.so
    ver="${base#libopencv_java-}"; ver="${ver%-$ARCH.so}"
    dynsrc="$HOME/.m2/repository/org/weasis/thirdparty/org/opencv/libopencv_java/$ver/libopencv_java-$ver-$ARCH-dyn.so"
    [ -f "$dynsrc" ] || { err "-dyn variant not in ~/.m2: $dynsrc (build the project so it is cached)"; return 1; }
    cp "$dynsrc" "$cache/libopencv_java-$ver-$ARCH-dyn.so"
    RESOLVED_LIB="$cache/libopencv_java-$ver-$ARCH-dyn.so"
  fi
  return 0
}

# Stage a portable Temurin JRE into dist/jre (host download, cached) when PORTABLE_JRE=1.
ensure_portable_jre() {
  if [ "$PORTABLE_JRE" != 1 ]; then
    rm -rf "$DIST/jre"
    return 0
  fi
  local ajarch
  case "$ARCH" in
    linux-x86-64)  ajarch=x64 ;;
    linux-aarch64) ajarch=aarch64 ;;
    *) err "PORTABLE_JRE unsupported for ARCH=$ARCH"; return 1 ;;
  esac
  local cache="$HERE/.jre-cache/$ARCH"
  if [ ! -x "$cache/bin/java" ]; then
    log "Downloading Temurin $JRE_RELEASE JRE ($ajarch) for old-distro testing"
    rm -rf "$cache"; mkdir -p "$cache"
    local url="https://api.adoptium.net/v3/binary/latest/$JRE_RELEASE/ga/linux/$ajarch/jre/hotspot/normal/eclipse"
    curl -fsSL "$url" | tar -xz -C "$cache" --strip-components=1 \
      || { err "Temurin JRE download failed: $url"; return 1; }
  fi
  rm -rf "$DIST/jre"; mkdir -p "$DIST/jre"
  cp -R "$cache/." "$DIST/jre/"
  log "Bundled portable JRE for $ARCH (containers will use /dist/jre/bin/java)"
}

inspect_so() {
  local so="$1"
  log "Linkage inspection: $so"
  file "$so" 2>/dev/null || true
  if ! command -v readelf >/dev/null; then
    err "readelf not found on host (common on macOS) — skipping the NEEDED/PT_INTERP check."
    err "Run --check-only on Linux, or rely on the Docker matrix below."
    return 0
  fi
  echo "-- dynamic NEEDED entries (expect glibc: libc.so.6/libm/libpthread/libdl;"
  echo "   libstdc++/libgcc should be ABSENT = statically embedded):"
  readelf -d "$so" 2>/dev/null | grep -E 'NEEDED' || echo "   (none)"
  if readelf -d "$so" 2>/dev/null | grep -Eq 'libstdc\+\+|libgcc_s'; then
    err "WARNING: keeps a host libstdc++/libgcc dependency — that is the -dyn variant's trait,"
    err "narrowing portability to a matching libstdc++ on the host."
  fi
  echo "-- oldest glibc it can run on (highest \@GLIBC_x.y among imports):"
  readelf --dyn-syms "$so" 2>/dev/null | grep -oE 'GLIBC_[0-9.]+' | sort -V | tail -1 \
    | sed 's/^/   /' || echo "   (no versioned glibc symbols)"
}

# Arch-independent dist assembly: runtime jars, module classes, sample DICOMs, compiled
# SmokeTest. Done once; the per-combo native lib + JRE are staged separately.
build_core() {
  log "Building module + assembling self-contained dist/"
  ( cd "$REPO" && mvn -q -f weasis-dicom-tools -DskipTests \
      install \
      dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory="$DIST/jars" )
  # slf4j-api is declared provided scope (downstream consumers supply it), so the
  # runtime copy above skips it. Add it plus a simple binding so the standalone
  # SmokeTest has logging on its classpath.
  ( cd "$REPO" && mvn -q -f weasis-dicom-tools \
      dependency:copy-dependencies -DincludeArtifactIds=slf4j-api,slf4j-simple \
      -DoutputDirectory="$DIST/jars" )

  rm -rf "$DIST/classes" "$DIST/data"
  mkdir -p "$DIST/classes" "$DIST/data"
  cp -r "$MODULE/target/classes/." "$DIST/classes/"

  [ -f "$SAMPLE_SRC" ] || { err "sample DICOM not found: $SAMPLE_SRC"; exit 1; }
  cp "$SAMPLE_SRC" "$DIST/data/$SAMPLE_NAME"
  log "Sample DICOM (threaded decode): $SAMPLE_NAME"
  if [ -f "$BE_SAMPLE_SRC" ]; then
    cp "$BE_SAMPLE_SRC" "$DIST/data/$BE_SAMPLE_NAME"
    log "Sample DICOM (big-endian swap check): $BE_SAMPLE_NAME"
  else
    err "big-endian sample not found ($BE_SAMPLE_SRC) — swap check will be skipped."
  fi

  log "Compiling SmokeTest (--release $JRE_RELEASE)"
  javac --release "$JRE_RELEASE" -cp "$DIST/classes:$DIST/jars/*" -d "$DIST/classes" "$HERE/SmokeTest.java"
}

# Stage the native lib for the current $ARCH/$DYN into dist/lib (cheap; per combo).
stage_native() {
  resolve_native || return 1
  rm -rf "$DIST/lib"; mkdir -p "$DIST/lib/$ARCH"
  cp "$RESOLVED_LIB" "$DIST/lib/$ARCH/libopencv_java.so"
  log "Using native lib ($([ "$DYN" = 1 ] && echo 'DYN - dynamic libstdc++/libgcc' || echo 'static')): $RESOLVED_LIB"
}

build_dist() {
  build_core
  stage_native || exit 1
}

run_matrix() {
  local rc=0
  # Big-endian swap-check args, derived from what's actually in dist/data (so this works
  # under --no-build too). Empty -> SmokeTest skips the check.
  local be_arg="" hashprop=""
  [ -f "$DIST/data/$BE_SAMPLE_NAME" ] && be_arg="data/$BE_SAMPLE_NAME"
  [ -n "$EXPECTED_BE_HASH" ] && hashprop="-Dexpected.be.hash=$EXPECTED_BE_HASH"
  for entry in "${IMAGES[@]}"; do
    local img="${entry%%|*}" install="${entry#*|}"
    log "Distro: $img  (arch=$ARCH ${PLATFORM:+platform=$PLATFORM})"
    # Exit codes from the container: 97 = JRE install unavailable (EOL/dead repos, TLS,
    # no jdk17 package) -> SKIP; 1 = lib incompatible with this distro's libc -> FAIL;
    # other non-zero = a JVM check crashed -> FAIL. Docker itself returns 125 when the
    # image has no manifest for this platform (e.g. amd64-only archlinux on aarch64) -> SKIP.
    # The static lib's gate needs only libc, so it runs first (verdict even with no JRE).
    # The -dyn lib also NEEDs libstdc++/libgcc, which the JRE install provides, so for DYN
    # the gate runs AFTER the JRE step (gate_first=false).
    local status=0 gate_first=true
    [ "$DYN" = 1 ] && gate_first=false
    local outfile; outfile="$(mktemp)"
    docker run --rm ${PLATFORM_ARG[@]+"${PLATFORM_ARG[@]}"} -v "$DIST:/dist:ro" -w /dist "$img" \
        sh -c "SO=lib/$ARCH/libopencv_java.so
               # DETERMINISTIC compat gate: 'ldd -r' forces full relocation, surfacing the
               # glibc VERSION floor ('version GLIBC_2.27 not found') and missing libs
               # ('libstdc++.so.6 => not found' for -dyn); plain 'ldd' surfaces musl's
               # missing symbols ('symbol not found'). Any => unsupported on this distro.
               do_gate() {
                 reloc=\$( { ldd -r \$SO 2>&1; ldd \$SO 2>&1; } | grep -iE 'not found|Error relocating' | sort -u )
                 if [ -n \"\$reloc\" ]; then
                   echo '--- unsupported on this distro (missing symbol/lib or version too old) ---'
                   printf '%s\n' \"\$reloc\" | head -4
                   exit 1
                 fi
                 echo '[gate] compatible'
               }
               # JRE: prefer the mounted portable JRE (PORTABLE_JRE=1); else install from the
               # distro. Unavailable (EOL/dead repos, TLS, no jdk17 pkg) -> SKIP. The distro
               # install also pulls in libstdc++/libgcc that the -dyn lib needs.
               do_jre() {
                 if [ -x /dist/jre/bin/java ]; then
                   JAVA=/dist/jre/bin/java; echo '[jre] using bundled Temurin'
                 else
                   { $install ; } >/tmp/setup.log 2>&1 || { echo '--- JRE install unavailable (infra/EOL) ---'; tail -6 /tmp/setup.log; exit 97; }
                   JAVA=java
                 fi
               }
               if $gate_first; then do_gate; do_jre; else do_jre; do_gate; fi
               # byte-swap correctness, then threaded stress. swap + threads run as SEPARATE
               # JVMs: a single-threaded decode warms up OpenCV and suppresses the musl
               # thread-teardown crash, so they must not share a process.
               if [ -n '$be_arg' ]; then
                 \$JAVA $hashprop -Djava.library.path=lib/$ARCH -cp 'classes:jars/*' SmokeTest swap $be_arg; r=\$?
                 if [ \$r -ne 0 ]; then echo \"--- byte-swap check failed (exit \$r) ---\"; exit \$r; fi
               fi
               i=1
               while [ \$i -le $SMOKE_RUNS ]; do
                 if [ \$i -eq 1 ]; then
                   \$JAVA -Djava.library.path=lib/$ARCH -cp 'classes:jars/*' SmokeTest threads data/$SAMPLE_NAME; r=\$?
                 else
                   \$JAVA -Djava.library.path=lib/$ARCH -cp 'classes:jars/*' SmokeTest threads data/$SAMPLE_NAME >/tmp/run.log 2>&1; r=\$?
                 fi
                 if [ \$r -ne 0 ]; then echo \"--- threaded run \$i/$SMOKE_RUNS crashed (exit \$r; 134=SIGABRT,139=SIGSEGV) ---\"; tail -6 /tmp/run.log 2>/dev/null; exit \$r; fi
                 i=\$((i+1))
               done
               echo \"[smoke] gate ok + swap ok + $SMOKE_RUNS threaded run(s) clean\"" \
        >"$outfile" 2>&1 || status=$?
    cat "$outfile"
    local verdict reason
    case "$status" in
      0)   verdict=PASS; reason="$SMOKE_RUNS threaded run(s) clean"
           printf '\033[1;32m[PASS] %s (%dx)\033[0m\n' "$img" "$SMOKE_RUNS" ;;
      97)  verdict=SKIP; reason="JRE unavailable (EOL/dead repos, TLS)"
           printf '\033[1;33m[SKIP] %s — %s\033[0m\n' "$img" "$reason" ;;
      125) verdict=SKIP; reason="image not available for this platform"
           printf '\033[1;33m[SKIP] %s — %s\033[0m\n' "$img" "$reason" ;;
      *)   verdict=FAIL; rc=1
           reason="$(grep -E '^---' "$outfile" | head -1 | sed -E 's/^--- //; s/ ---$//')"
           [ -n "$reason" ] || reason="exit $status"
           printf '\033[1;31m[FAIL] %s — %s\033[0m\n' "$img" "$reason" ;;
    esac
    # Record one row per (arch, variant, distro) for the markdown report.
    if [ -n "${RESULTS_FILE:-}" ]; then
      printf '%s\t%s\t%s\t%s\t%s\n' "$ARCH" "${VARIANT_LABEL:-static}" "$img" "$verdict" "$reason" >> "$RESULTS_FILE"
    fi
    rm -f "$outfile"
  done
  return $rc
}

docker_ready() {
  command -v docker >/dev/null || { err "docker not found."; return 1; }
  docker info >/dev/null 2>&1 || { err "Docker daemon not reachable — start Docker Desktop / the daemon and retry."; return 1; }
}

verdict_icon() {
  case "$1" in PASS) printf '✅' ;; FAIL) printf '❌' ;; SKIP) printf '⏭️' ;; *) printf '—' ;; esac
}

# --full: every variant — both arches × {static,-dyn} × {distro JRE, bundled Temurin} —
# then a markdown report. The arch matching the host runs natively; the other is emulated
# (needs binfmt/QEMU). Records every (arch,variant,distro) verdict to $RESULTS_FILE.
run_full() {
  docker_ready || exit 0
  local host_arch
  case "$(uname -m)" in x86_64|amd64) host_arch=linux-x86-64 ;; *) host_arch=linux-aarch64 ;; esac
  RESULTS_FILE="$(mktemp)"
  build_core
  local arch dyn portable
  for arch in linux-x86-64 linux-aarch64; do
    ARCH="$arch"
    if [ "$arch" = "$host_arch" ]; then PLATFORM=""; else
      case "$arch" in linux-x86-64) PLATFORM=linux/amd64 ;; *) PLATFORM=linux/arm64 ;; esac
    fi
    PLATFORM_ARG=(); [ -n "$PLATFORM" ] && PLATFORM_ARG=(--platform "$PLATFORM")
    for dyn in 0 1; do
      for portable in 0 1; do
        DYN="$dyn"; PORTABLE_JRE="$portable"
        VARIANT_LABEL=static
        [ "$dyn" = 1 ] && VARIANT_LABEL=dyn
        [ "$portable" = 1 ] && VARIANT_LABEL="$VARIANT_LABEL+jre"
        log "FULL combo: $arch / $VARIANT_LABEL ${PLATFORM:+(emulated $PLATFORM)}"
        stage_native || { err "skip combo (native resolve failed): $arch $VARIANT_LABEL"; continue; }
        ensure_portable_jre || { err "skip combo (JRE stage failed): $arch $VARIANT_LABEL"; continue; }
        run_matrix || true   # don't abort the whole sweep on a distro FAIL
      done
    done
  done
  generate_report
}

generate_report() {
  local report="$HERE/REPORT.md" var img v entry
  local variants="static static+jre dyn dyn+jre"
  {
    echo "# Native cross-distro test report"
    echo
    echo "- **Generated:** $(date -u '+%Y-%m-%d %H:%M:%SZ')"
    echo "- **Host:** $(uname -srm)"
    echo "- **Lib:** \`org.weasis.thirdparty.org.opencv:libopencv_java\` (\`-dyn\` = dynamic libstdc++/libgcc variant)"
    echo "- **Columns:** \`+jre\` = bundled Temurin JRE (\`PORTABLE_JRE=1\`) instead of the distro's"
    echo "- **Legend:** ✅ PASS · ❌ FAIL · ⏭️ SKIP (infra / EOL repos / no image) · — not run"
    echo
    for arch in linux-x86-64 linux-aarch64; do
      awk -F'\t' -v a="$arch" '$1==a{f=1} END{exit !f}' "$RESULTS_FILE" || continue
      echo "## $arch"
      echo
      echo "| Distro | static | static +jre | dyn | dyn +jre |"
      echo "|---|:--:|:--:|:--:|:--:|"
      for entry in "${IMAGES[@]}"; do
        img="${entry%%|*}"
        printf '| `%s` |' "$img"
        for var in $variants; do
          v="$(awk -F'\t' -v a="$arch" -v vr="$var" -v im="$img" '$1==a&&$2==vr&&$3==im{print $4; exit}' "$RESULTS_FILE")"
          printf ' %s |' "$(verdict_icon "$v")"
        done
        printf '\n'
      done
      echo
    done
    echo "## Notes (non-PASS)"
    echo
    awk -F'\t' '$4!="PASS"{printf "- `%s` / %s / `%s` → **%s** — %s\n",$1,$2,$3,$4,$5}' "$RESULTS_FILE" | sort -u
  } > "$report"
  log "Markdown report written: $report"
}

# --local: run the smoke test on THIS machine (host JVM + host native classifier), no
# Docker. There's no distro matrix for macOS/Windows — the OS minimum is encoded in the
# binary (otool minos / PE subsystem), so a real "does it load+decode here" smoke is the
# useful runtime check. Handles macOS (.dylib) and Linux (.so); Windows uses run-local.ps1.
run_local() {
  local os ext art
  case "$(uname -s)" in
    Darwin) os=macosx; ext=dylib; art=libopencv_java ;;
    Linux)  os=linux;  ext=so;    art=libopencv_java ;;
    *) err "--local supports macOS/Linux here; on Windows run run-local.ps1"; exit 2 ;;
  esac
  local cpu
  case "$(uname -m)" in arm64|aarch64) cpu=aarch64 ;; x86_64|amd64) cpu=x86-64 ;; *) err "unsupported arch $(uname -m)"; exit 2 ;; esac
  local cls="$os-$cpu" libname="$art.$ext"

  [ -f "$DIST/classes/SmokeTest.class" ] || build_core
  [ -f "$SAMPLE_SRC" ] && { mkdir -p "$DIST/data"; cp -f "$SAMPLE_SRC" "$DIST/data/$SAMPLE_NAME"; }
  [ -f "$BE_SAMPLE_SRC" ] && { mkdir -p "$DIST/data"; cp -f "$BE_SAMPLE_SRC" "$DIST/data/$BE_SAMPLE_NAME"; }

  local cache="$HERE/.native-cache"; rm -rf "$cache"; mkdir -p "$cache"
  log "Resolving $cls native from local ~/.m2 (offline)"
  ( cd "$REPO" && mvn -o -q -f weasis-dicom-tools dependency:copy-dependencies \
      -DincludeGroupIds=org.weasis.thirdparty.org.opencv -DincludeArtifactIds="$art" \
      -DincludeClassifiers="$cls" -DoutputDirectory="$cache" ) \
    || { err "resolve failed for $cls — build the project once so it is cached in ~/.m2."; exit 1; }
  local lib; lib="$(ls "$cache/$art"-*-"$cls.$ext" 2>/dev/null | head -1)"
  [ -n "$lib" ] || { err "no $art-*-$cls.$ext in $cache"; exit 1; }
  rm -rf "$DIST/lib"; mkdir -p "$DIST/lib/$cls"; cp "$lib" "$DIST/lib/$cls/$libname"
  log "Local smoke on $cls: $(basename "$lib")"

  local cp="$DIST/classes:$DIST/jars/*" libpath="$DIST/lib/$cls" rc=0 hashprop=""
  [ -n "$EXPECTED_BE_HASH" ] && hashprop="-Dexpected.be.hash=$EXPECTED_BE_HASH"
  if [ -f "$DIST/data/$BE_SAMPLE_NAME" ]; then
    java $hashprop -Djava.library.path="$libpath" -cp "$cp" SmokeTest swap "$DIST/data/$BE_SAMPLE_NAME" || rc=$?
  fi
  [ "$rc" = 0 ] && { java -Djava.library.path="$libpath" -cp "$cp" SmokeTest threads "$DIST/data/$SAMPLE_NAME" || rc=$?; }
  if [ "$rc" = 0 ]; then printf '\033[1;32m[PASS] %s (local smoke)\033[0m\n' "$cls"
  else printf '\033[1;31m[FAIL] %s (local smoke) — exit %d\033[0m\n' "$cls" "$rc"; fi
  return $rc
}

main() {
  local do_build=1 check_only=0 full=0 local_smoke=0
  for a in "$@"; do
    case "$a" in
      --full)       full=1 ;;
      --local)      local_smoke=1 ;;
      --no-build)   do_build=0 ;;
      --check-only) check_only=1 ;;
      *) err "unknown arg: $a"; exit 2 ;;
    esac
  done

  if [ "$check_only" = 1 ]; then
    resolve_native || exit 1
    inspect_so "$RESOLVED_LIB"
    exit 0
  fi

  acquire_lock   # serialize everything below: it all mutates the shared dist/

  if [ "$local_smoke" = 1 ]; then
    run_local
    exit 0
  fi

  if [ "$full" = 1 ]; then
    run_full
    exit 0
  fi

  [ "$do_build" = 1 ] && build_dist
  ensure_portable_jre || exit 1   # stages dist/jre when PORTABLE_JRE=1 (works with --no-build too)
  inspect_so "$DIST/lib/$ARCH"/libopencv_java.so
  docker_ready || exit 0
  run_matrix
}

main "$@"