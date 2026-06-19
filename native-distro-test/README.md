# Cross-distro test for the native build

Verifies that the project's `libopencv_java` native build loads and decodes a DICOM
across Linux distributions of different **glibc ages**, plus a musl (Alpine) data point.
Standalone — it is **not** wired into the Maven build (it needs Docker and is run on
demand).

> **What the shipped lib actually is** (measured on `5.0.0-dcm`): a **glibc-dynamic**
> `.so`. It statically embeds **libstdc++ / libgcc / OpenCV** (no `NEEDED` for those), but
> dynamically links **glibc** (`NEEDED: libc.so.6, libm.so.6, libpthread.so.0,
> libdl.so.2`). Its **glibc floor is architecture-dependent**:
>
> | arch | highest `@GLIBC_` | oldest glibc it runs on |
> |---|---|---|
> | **linux-x86-64** | `GLIBC_2.17` | RHEL/CentOS 7, Debian 8, Ubuntu 14.04 (≈2012+) |
> | **linux-aarch64** | `GLIBC_2.27` (`expf/logf/powf`) | RHEL 8, Debian 10, Ubuntu 18.04 (≈2018+) |
>
> So the same old distro can **pass on x86-64 yet fail on aarch64**. It is **not**
> statically linked against libc and is **not** a single universal artifact — and under a
> real **threaded** decode it crashes on musl. See
> [Alpine/musl](#alpinemusl-loads-but-crashes-under-threading).

## What it does

1. **Linkage inspection** (`readelf`/`file`, host-only, instant; skipped if `readelf`
   is absent, e.g. macOS). The meaningful facts to read off:
   - `libstdc++`/`libgcc` should **not** be in `NEEDED` (statically embedded);
   - the **highest `@GLIBC_x.y`** among the dynamic symbols = the **oldest glibc** the
     lib can run on. Watch this number — if a build bumps it, you drop older distros.
2. **Self-contained `dist/`**: bundles the runtime jars, the native lib, the sample
   DICOMs, and the compiled `SmokeTest` so each container only needs a JRE.
3. **Docker matrix** — musl (Alpine), a wide glibc-age range (CentOS 7 → Arch), and
   enterprise glibc (Rocky). Each container runs, in order:
   - **libc-compat gate (deterministic, no JRE needed)** — `ldd -r` + `ldd` of the lib
     against *that distro's* libc. `ldd -r` forces full relocation, so it surfaces the
     **glibc version floor** (`version GLIBC_2.27 not found` on too-old glibc); plain `ldd`
     surfaces **musl's** missing symbols (`__cxa_thread_atexit_impl` / `__strftime_l`).
     Either → immediate FAIL with the offending lines printed. Runs *before* the JRE
     install, so even distros with no installable JDK 17 still get a verdict.
   - **`swap`** (JVM) — decodes a **big-endian** (Explicit VR Big Endian) DICOM and
     SHA-256s every decoded pixel byte. dcm4che swaps by the transfer syntax, not the host
     CPU, so the hash must equal the golden `EXPECTED_BE_HASH` on **every architecture**
     (verified identical on linux-aarch64 and under linux-x86-64 emulation). Also asserts a
     channel saturates, catching grossly wrong swap/planar handling.
   - **`threads`** (separate JVMs, ×`SMOKE_RUNS`) — decodes every frame of a compressed
     multi-frame (JPEG2000) DICOM concurrently with OpenCV's native multithreading on:
     runtime stress for a libc that links cleanly.

   `swap` and `threads` **must** be separate processes: a single-threaded decode warms up
   OpenCV and *suppresses* the musl thread-teardown crash, so mixing them in one JVM masks
   it. (That crash is intermittent anyway — the `ldd` gate is what catches musl reliably.)

## Expected results

The matrix spans Alpine (musl), CentOS 7, Debian 9, Ubuntu 20.04, Debian 11, Ubuntu 22.04,
Rocky 8/9, and Arch. Outcome depends on the **arch's glibc floor** (x86-64 = 2.17,
aarch64 = 2.27):

| Distro | glibc | x86-64 | aarch64 |
|---|---|---|---|
| Alpine 3 | musl | **FAIL** (missing symbols) | **FAIL** (missing symbols) |
| CentOS 7 | 2.17 | gate OK → SKIP* | **FAIL** (floor) |
| Debian 9 | 2.24 | gate OK → SKIP* (PASS with `PORTABLE_JRE=1`) | **FAIL** (floor) |
| Ubuntu 20.04, Debian 11, Ubuntu 22.04 | 2.31–2.35 | **PASS** | **PASS** |
| Rocky 8, 9 | 2.28 / 2.34 | **PASS** or SKIP† | **PASS** or SKIP† |
| Arch | newest | **PASS** | SKIP‡ |

\* CentOS 7 / Debian 9 are EOL: their repos are dead *and* they never shipped JDK 17, so
the gate confirms link-compatibility but a JRE can't be installed → `[SKIP]`. Use
**`PORTABLE_JRE=1`** (below) to mount a Temurin JRE and actually run them → `[PASS]` on
x86-64 (glibc 2.17 ≥ floor). On aarch64 they still `[FAIL]` at the gate (floor 2.27).
† Rocky's HTTPS mirrorlist often fails behind a corporate TLS proxy → `[SKIP]`.
‡ the `archlinux` image is published amd64-only → no arm64 manifest → `[SKIP]` (exit 125);
test it under `PLATFORM=linux/amd64`.

A `[FAIL]` is a real signal: missing symbols / glibc too old (gate), a wrong byte-swap
hash, or a crash during/after decode. `[SKIP]` is non-verdict (couldn't install a JRE, or
no image for this platform).

Many of the `[SKIP]`s above are environmental (EOL/dead repos for CentOS 7 / Debian 9,
or a corporate TLS proxy breaking Rocky/Arch package mirrors). On an unfiltered network
the installable distros run for real; the gate verdict is unaffected either way.

## Alpine/musl: loads, but crashes under threading

Alpine ships **musl**, has no `libc.so.6` and no glibc — yet the glibc-linked `.so`
*loads* and a single-frame decode even succeeds. That's **not** portability; musl is a
deliberate *partial drop-in* for glibc:

1. **Soname aliasing** — musl's loader answers to glibc's library names: `ldd` shows
   `libc.so.6 => /lib/ld-musl-*.so.1` (same for `libm`/`libpthread`/`libdl`). Every
   `NEEDED` is satisfied by musl itself.
2. **Name-based resolution** — musl ignores glibc symbol *versioning* and binds
   `memcpy@GLIBC_2.17` by the bare name `memcpy`, which it implements. ~226 of the 228
   imports resolve straight to musl.
3. **The 2 it can't provide** — `__cxa_thread_atexit_impl` and `__strftime_l` are glibc
   internals musl lacks (`ldd` prints `Error relocating … symbol not found`). They're
   function (PLT) relocations, so musl loads the library anyway; only a *call* to those
   addresses fails. `__cxa_thread_atexit_impl` registers a `thread_local` C++ object's
   destructor — which is exactly what OpenCV's **worker threads** do.

So a single-frame decode looks fine, but the **threaded** decode makes OpenCV spawn
native worker threads with `thread_local` state. On musl that path leads to an
**`abort()`/`SIGSEGV` (exit 134/139)** at thread/JVM teardown — but it's **intermittent**
(~20–50% per process, varies with load) and fires *after* `[smoke] OK` prints, so it is
**not** a reliable detector on its own. That's why the harness gates on **`ldd`** instead:
the unresolved symbols are reported deterministically, every time, on musl — and never on
glibc. The threaded runs remain as runtime stress for libcs that *do* link cleanly.

Note the `swap` check would *pass* on Alpine (the byte-swap is correct on musl too — same
golden hash); musl's incompatibility is the unresolved thread/locale symbols, which the
`ldd` gate catches before the JVM checks even run.

**Conclusion:** the glibc lib on musl is unsupported — it can crash under real,
threaded workloads. **For Alpine, ship a musl-built classifier** and point `NATIVE_LIB`
at it.

## Usage

```bash
cd native-distro-test
./run-matrix.sh                 # build dist + linkage check + full matrix (one variant)
./run-matrix.sh --no-build      # reuse an existing dist/
./run-matrix.sh --check-only    # only the readelf/ldd linkage inspection
./run-matrix.sh --full          # sweep ALL variants + write REPORT.md (see below)
./run-matrix.sh --local         # smoke on THIS host (macOS/Linux), no Docker (see macOS & Windows)
pwsh ./run-local.ps1            # same host smoke on Windows (opencv_java.dll)

# test a different lib (e.g. a musl build, or the -dyn variant):
NATIVE_LIB=/path/to/libopencv_java.so ./run-matrix.sh

# pick the native arch with ARCH; on an x86-64 host this is the default (no PLATFORM):
ARCH=linux-x86-64 ./run-matrix.sh

# run a NON-native arch under QEMU emulation (one-time: docker run --privileged tonistiigi/binfmt --install all):
ARCH=linux-x86-64  PLATFORM=linux/amd64 ./run-matrix.sh   # x86-64 from an arm host
ARCH=linux-aarch64 PLATFORM=linux/arm64 ./run-matrix.sh   # aarch64 from an x86-64 host

# actually RUN the EOL distros (CentOS 7, Debian 9) whose package repos are dead:
PORTABLE_JRE=1 ARCH=linux-x86-64 PLATFORM=linux/amd64 ./run-matrix.sh

# test the "-dyn" variant (links libstdc++/libgcc dynamically instead of embedding them):
DYN=1 ./run-matrix.sh

# more threaded-decode stress / pick a different sample DICOM:
SMOKE_RUNS=20 ./run-matrix.sh --no-build
SAMPLE_SRC=/path/to/other.dcm ./run-matrix.sh
```

- `DYN` (default `0`) — when `1`, test the **`-dyn` classifier**
  (`libopencv_java-<ver>-<arch>-dyn.so`, fetched from `~/.m2`) instead of the default
  static one. The `-dyn` build links **libstdc++/libgcc dynamically** (`NEEDED:
  libstdc++.so.6, libgcc_s.so.1`) rather than embedding them, so it additionally needs the
  distro to provide a compatible libstdc++ — it is **less self-contained**. Consequences:
  on Alpine it fails the gate with missing **C++** symbols (`_Unwind_Resume`,
  `_ZNKSt13runtime_error4whatEv`, …), not just the thread/locale ones; and because its
  gate needs libstdc++, it runs **after** the JRE install (which pulls libstdc++ in), so
  EOL distros that can't install a JRE `[SKIP]` instead of showing a floor verdict. Pair
  with `PORTABLE_JRE=1` only if the image already has libstdc++ (the bundled JRE doesn't
  add one to the system path).
- `PORTABLE_JRE` (default `0`) — when `1`, download a Temurin `JRE_RELEASE` JRE for `ARCH`
  on the **host** (cached in `.jre-cache/`, needs `curl`), stage it into `dist/jre`, and
  have every container run `/dist/jre/bin/java` instead of installing one. This is **how to
  test EOL/old distros**: their dead repos and missing JDK-17 packages no longer matter,
  and Temurin's glibc-2.17 build runs anywhere the lib's floor allows. (glibc only — a
  glibc JRE won't run on Alpine/musl, which fails the gate first anyway.)
- `SMOKE_RUNS` (default `3`) — threaded-decode runs per distro (runtime stress on a
  cleanly-linked libc; musl is caught deterministically by the `ldd` gate, not by this).
  Raise it only to fish for the intermittent crash on musl for demonstration.
- `SAMPLE_SRC` — the DICOM to decode; defaults to a compressed multi-frame JPEG2000 so
  the threaded codec path actually runs.
- `JRE_RELEASE` (default `17`) — JRE/bytecode version; the bundled `PORTABLE_JRE` and the
  `SmokeTest` bytecode target both follow it. Keep it ≤ any distro-installed JRE.

### `--full`: sweep every variant + markdown report

`./run-matrix.sh --full` runs the **whole cross product** and writes `REPORT.md`:

- **arch** × **{static, `-dyn`}** × **{distro JRE, bundled Temurin `+jre`}** = 8 combos,
  each across the distro matrix. The arch matching the host runs natively; the other is
  **emulated** (needs binfmt/QEMU — see above), so a full sweep is slow.
- It builds the arch-independent `dist/` once, then re-stages only the native lib + JRE per
  combo, and records every `(arch, variant, distro)` verdict.
- `REPORT.md` has one table per arch (rows = distros, columns = the four variants, cells
  ✅/❌/⏭️/—) plus a **Notes** section listing every non-PASS with its reason. It captures
  the whole story: musl always fails; the glibc floor (x86-64 = 2.17, aarch64 = 2.27)
  gates the old distros; `+jre` rescues EOL distros; `-dyn` adds the libstdc++ dependency.

### Where the native lib comes from

By default the harness resolves the `<ARCH>` Linux classifier of
`org.weasis.thirdparty.org.opencv:libopencv_java` from your **local `~/.m2` repo**,
offline (`mvn -o dependency:copy-dependencies`). This:

- works on any host OS — a Mac's `target/lib` only ever holds the `macosx-*` `.dylib`,
  never the Linux `.so` this harness needs;
- bypasses repo mirrors that don't carry the weasis OpenCV natives;
- resolves the **exact version the build uses** (e.g. `5.0.0-dcm`, derived
  transitively from `weasis-core-img-bom`, *not* `weasis.core.img.version`) and the
  default classifier. The separate `…-<arch>-dyn` classifier is the variant that also
  links libstdc++/libgcc dynamically — point `NATIVE_LIB` at it to contrast.

So **build the project once** (`mvn -f weasis-dicom-tools install`) before running, so
the artifact is cached in `~/.m2`. Runtime jars come from the module's `runtime` closure;
`slf4j-api` (declared `provided`) plus a simple binding are copied in separately so the
standalone `SmokeTest` has logging.

## macOS & Windows

There's no distro/libc matrix for macOS or Windows — the **OS minimum is baked into the
binary**, and old macOS/Windows aren't dockerable the way Linux is. So validate two ways:

**1. Minimum-OS (static, from any machine):** the floor lives in the binary's metadata.

| target | where the minimum lives | shipped value | tool |
|---|---|---|---|
| macOS x86-64 | Mach-O `LC_VERSION_MIN_MACOSX` | **10.13** | `otool -l <dylib>` / `vtool -show-build` |
| macOS arm64 | Mach-O `LC_BUILD_VERSION` `minos` | **11.0** (Apple-Silicon floor) | `otool -l <dylib>` |
| Windows x64 | PE `MajorSubsystemVersion` + imports | **6.0** (Vista+, so Win 10 ✅), MSVC runtime **statically linked** (no VC++ redist) | `dumpbin /headers /dependents` (or `objdump -p`) |

Building with `-mmacosx-version-min=10.13` is what *enforces* the macOS floor (the
toolchain weak-links newer APIs); the PE import table is the Windows equivalent (no
`vcruntime140.dll`/`msvcp140.dll` ⇒ no redistributable required).

**2. Runtime smoke (no Docker, no version pin):** run the same `SmokeTest` (load native →
big-endian swap-hash → threaded decode) on the host JVM with the host classifier:

```bash
./run-matrix.sh --local      # macOS or Linux host: uses the macosx/linux dylib/.so
pwsh ./run-local.ps1         # Windows host: uses opencv_java.dll (windows-x86-64)
```

This confirms the lib actually loads and decodes on *this* machine (e.g. macOS produces the
same `4f31…61d7` swap hash as Linux). True validation on **old** macOS 10.13 / Windows 10
still needs a real machine or VM — those aren't containerizable — but the embedded minimum
above plus a smoke on any supported host is the practical check.

## Note: JNI vs the Foreign Function API

A recurring question is whether the Foreign Function & Memory API (FFM, JEP 454) would
change any of this. It would not: **portability here is a property of how the native is
*linked* (which glibc symbols/versions it imports, what it embeds vs. leaves dynamic),
not of how Java *calls* it.** FFM still `dlopen`s the same `.so` into the JVM process, so
the glibc-floor and the musl-compat story above are identical. FFM is also C-ABI only
(OpenCV's API is C++, so you'd still ship a native C shim) and is finalized in JDK 22
while this project targets Java 17. Its real win would be dropping the hand-maintained
JNI wrapper — not portability. (GraalVM `native-image --static --libc=musl` produces a
single static *executable*, a different model that abandons "loaded into a host JVM as a
library", which every downstream consumer relies on.)

## Notes

- `dist/`, `.native-cache/` and `.jre-cache/` are generated; do not commit them (add to
  `.gitignore`).
- Default JRE is 17 (matches the project). Adjust the `IMAGES` array in
  `run-matrix.sh` to test other JREs/distros (and `JRE_RELEASE` to match).