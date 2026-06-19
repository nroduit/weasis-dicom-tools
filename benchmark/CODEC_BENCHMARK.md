# Native codec benchmark (`CodecBenchmark`)

A/B benchmark for the native image codec (`weasis-core-img` / OpenCV) used by the
`org.dcm4che3.img` pipeline (`DicomImageReader` / `Transcoder`) in **weasis-dicom-tools**. It
measures **decode**, **encode/transcode** speed and **output quality** so you can confirm a native-
library change (e.g. weasis-core-img 4.12.0.1 → 4.13.0.1) is **faster or at least not slower**, and
**lossless-correct**, on representative DICOM samples.

The core idea is to hold everything constant — same machine, same JVM, same files, same code — and
vary **only** the `weasis.core.img.version` (the native library), then diff the two CSV reports.

## What it measures

The benchmark prints **CSV to stdout** (one header row, then one row per file) and a few
informational `#` lines to **stderr** (so a redirect or `OUT=` yields a clean CSV):

```
# weasis-core-img=4.13.0.1  java=17.0.19  os=Linux/amd64  cores=32
# transcode=same-codec round-trip  warmup=2  iterations=5  files=42  skipped=3
```

## Output CSV columns

23 columns, in order (the `#` index is handy for `cut -d, -f<#>`). The `decoder` column sits just
before the decode columns and `encoder` just before the encode columns, so each codec name reads
next to the numbers it produced:

| # | Column | Unit | Meaning |
|---|--------|------|---------|
| 1 | `file` | — | input path **relative to the sample dir** (unique across subdirectories) |
| 2 | `frames` | count | number of frames (1 for single‑frame) |
| 3 | `rows` | px | image height (`Rows`) |
| 4 | `cols` | px | image width (`Columns`) |
| 5 | `mp_per_frame` | megapixels | `rows × cols / 1e6` — per‑frame size |
| 6 | `src_ts` | UID | source transfer syntax (the decode path being timed) |
| 7 | `decoder` | — | **name of the source codec** and the native library behind it (e.g. `J2K (OpenJPEG)`) — names the decode path |
| 8 | `decode_med_ms` | ms | **median** decode time over the timed passes (all frames) |
| 9 | `decode_p95_ms` | ms | 95th‑percentile decode time (tail/jitter indicator) |
| 10 | `decode_mpps` | MP/s | **decode throughput** = `frames × mp_per_frame / median_decode_s` — the decode speed gate |
| 11 | `decode_sha1` | hex | SHA‑1 of the **raw decoded pixels** of all frames — the **lossless correctness gate** (must match between builds for lossless sources) |
| 12 | `dst_ts` | UID | transfer syntax written by the transcode; **equals `src_ts`** (a same‑codec round‑trip), **blank** when the transcode is skipped (raw source, or a source codec with no encoder). A non‑empty `dst_ts` that differs from `src_ts` cannot occur — if the round‑trip can't recompress to the source codec the row is an `ERROR` |
| 13 | `transcode_med_ms` | ms | median transcode (decode + re‑encode to the source codec) time |
| 14 | `transcode_p95_ms` | ms | 95th‑percentile transcode time |
| 15 | `transcode_mpps` | MP/s | **transcode throughput** — the round‑trip speed gate |
| 16 | `out_kb` | KiB | size of the re‑encoded output stream (proxy for compression ratio) |
| 17 | `enc_sha1` | hex | SHA‑1 of the **re‑encoded stream**; identical across builds only for a deterministic, lossless encoder |
| 18 | `psnr_db` | dB | round‑trip quality vs the original (`inf` = bit‑identical / lossless source) |
| 19 | `ssim` | 0–1 | round‑trip structural similarity vs the original (`1.000` = identical) |
| 20 | `encoder` | — | **name of the encoder** used by the transcode and the encode pass — the same codec as `decoder`. **Blank** when the source codec has no encoder in the library (e.g. RLE) |
| 21 | `encode_med_ms` | ms | median **encode‑only** time (re‑encode of already‑decoded pixels; frames are decoded eagerly and untimed first, so this excludes decode). The encode target is the **source** syntax (col 6) — it tests the encoder matching the file's own decoder. **Empty** when the source codec has no encoder |
| 22 | `encode_p95_ms` | ms | 95th‑percentile encode‑only time |
| 23 | `encode_mpps` | MP/s | **encode throughput** — isolates the encoder for the *source* codec, so `decode_mpps`/`encode_mpps` are a same‑codec read/write pair |

**Timing model.** Each file gets `warmup` untimed passes (JIT + native warm‑up) followed by
`iterations` timed passes; the reader and input stream are re‑created on every pass so pixels are
genuinely re‑decoded (no caching). `*_med_ms` is the median of the timed passes and `*_p95_ms` the
95th percentile; throughput is computed from the **median**. The `encode_*` columns time only the
re‑encode: every frame is decoded into memory **before** the timer starts (so a large multiframe is
held fully decoded — raise the heap if needed), then only `DicomOutputData`'s write is measured.

**Special row values.** A file that fails to process yields `<file>,ERROR:<message>` (non‑image
files are skipped before benchmarking, so they no longer appear). The transcode columns 12–19 are
**blank** for raw sources (nothing to decompress) and for compressed sources whose codec has no
encoder (RLE, HTJ2K); the encode columns 21–23 (and the `encoder` name in col 20) are likewise blank
for any source codec with no encoder. A compressed source **with** an encoder that nonetheless can't
recompress to its own codec — because the encoder downgrades the pixel type to raw — is reported as
an `ERROR` row rather than misleading timings (see below).

**Round‑trip verification.** The transcode re‑encodes to the **source** codec, so it is a true
decompress → recompress round‑trip. The harness verifies this: it reads back the transfer syntax
actually written and requires it to equal `src_ts` and still be an encapsulated (compressed) syntax.
If the library downgraded the output to raw, no recompression happened and the file is failed loudly
as an `ERROR` row.

### Quality columns (`psnr_db` / `ssim`)

The re‑encoded stream is decoded back and compared against the original:

- **Grayscale** is compared on the raw decoded samples, with the PSNR peak derived from `BitsStored`.
- **Color** is compared in actual **sRGB** (via `BufferedImage.getRGB`), so a photometric‑
  interpretation change across the round‑trip (e.g. `YBR_FULL` ↔ `RGB`) does **not** show up as a
  false quality loss.

A **lossless** source codec therefore scores `psnr_db = inf` and `ssim = 1.000` for both grayscale
and color (this doubles as a losslessness check). For a **lossy** source codec the round‑trip incurs
a generation loss; the candidate's PSNR/SSIM must be **≥** the baseline's.

## Prerequisites

- JDK 17+, Maven 3.8.1+, and the project dependencies cached locally (the scripts build offline with
  `-o`; run `mvn -o install -DskipTests` once first if your local repo is cold).
- A directory of DICOM sample files. To exercise every native code path, the set should span the
  **source transfer syntaxes** (each codec), the **pixel types** (bit depth, signedness, photometric
  interpretation, planar configuration) and **single‑ vs multi‑frame** — these are the dimensions
  that select different branches in the decoder/encoder.

### Suggested sample coverage

**By source transfer syntax** (decode path under test):

| Transfer syntax | UID | Notes |
|-----------------|-----|-------|
| Implicit/Explicit VR Little Endian (uncompressed) | `…1.2` / `…1.2.1` | raw baseline; also a transcode *source* |
| RLE Lossless | `…1.2.5` | run‑length path |
| JPEG Baseline (8‑bit lossy) | `…1.2.4.50` | IJG 8‑bit |
| JPEG Extended (12‑bit lossy) | `…1.2.4.51` | IJG 12‑bit |
| JPEG Lossless, SV1 | `…1.2.4.70` | IJG lossless |
| JPEG‑LS Lossless | `…1.2.4.80` | CharLS |
| JPEG‑LS Near‑Lossless | `…1.2.4.81` | CharLS lossy |
| JPEG 2000 Lossless | `…1.2.4.90` | OpenJPEG reversible |
| JPEG 2000 (lossy) | `…1.2.4.91` | OpenJPEG irreversible |
| JPEG XL Lossless / Recompression | `…1.2.4.110` / `….111` / `….112` | libjxl (if available) |

**By pixel type** (decode + color handling):

| Dimension | Cover at least |
|-----------|----------------|
| Grayscale bit depth | 8‑bit `MONOCHROME2`, 12‑bit and 16‑bit (e.g. CT/MR/CR) |
| Signedness | one **signed** 16‑bit image (`PixelRepresentation=1`) |
| Inverted grayscale | one `MONOCHROME1` |
| Color RGB | `RGB`, interleaved (`PlanarConfiguration=0`) and planar (`=1`) |
| Color YBR | `YBR_FULL`, `YBR_FULL_422`, `YBR_PARTIAL_420` — exercises chroma subsampling / RGB↔YBR |
| Palette | one `PALETTE COLOR` image |
| Bits allocated vs stored | a case with `BitsStored < BitsAllocated` (e.g. 12‑in‑16) and a non‑zero `HighBit`/overlay‑in‑pixel‑data oddity |

**By size / frame count** (throughput, memory, multithreading):

| Case | Why |
|------|-----|
| Normal single‑frame CT (≈ 0.25 MP, 16‑bit) | the common path |
| Large color study (US / transthoracic echo, many frames) | per‑frame throughput + color conversion |
| Big multiframe (enhanced CT/MR, hundreds of frames) | sustained throughput, JXL multithreading, segmented‑stream reader |
| Whole‑slide / pathology (very large frame, color JPEG/J2K) | SIMD at scale and peak memory (raise the heap) |

You don't need a separate file for every cell — pick a compact set where each file covers several
dimensions (e.g. a multiframe **YBR_FULL_422 JPEG Baseline** US covers lossy‑color + chroma‑
subsampling + multiframe at once). The project's own `weasis-dicom-tools/src/test/resources/` test
fixtures already contain several of these and are a convenient starting point.

### Shared corpus from the demo archive

For a wider, codec-focused corpus the CI run pulls from the [Weasis demo archive](https://nroduit.github.io/en/demo/)
(static GitHub Pages — files are served byte-exact in their stored transfer syntax, nothing is
transcoded). The file list is the four vendored Weasis manifests under `benchmark/manifests/`
(`color`, `compression1`, `compression2`, `compression3` — 61 files covering RGB/YBR/palette and the
JPEG / JPEG-LS / JPEG 2000 / RLE families).

```bash
# Download + verify against benchmark/corpus.lock, then benchmark it:
benchmark/fetch-corpus.sh benchmark/corpus
benchmark/run-codec-benchmark.sh benchmark/corpus
```

`fetch-corpus.sh` checks every file's sha256 against `benchmark/corpus.lock`, so a re-curation of
the demo data fails loudly instead of silently shifting the committed throughput history. The corpus
itself is git-ignored (reproducible from the manifests + lock). To intentionally bump it — add a
manifest, or pick up upstream data changes — re-pin with:

```bash
LOCK_MODE=write benchmark/fetch-corpus.sh benchmark/corpus   # regenerates corpus.lock
```

## Running it

Use the launcher scripts — they fork a JVM with the correct `java.library.path` for the native
library (Maven's `exec:java` runs in‑process and cannot set it):

```bash
# Linux / macOS
benchmark/run-codec-benchmark.sh <sample-dir> [warmup] [iterations]
```
```bat
:: Windows (x64)
benchmark\run-codec-benchmark.bat <sample-dir> [warmup] [iterations]
```

The transcode always re‑encodes each file back to its own (source) codec — a same‑codec round‑trip,
so the decoder and encoder match — and there is no destination‑syntax argument.

**Positional arguments**

| Arg | Default | Notes |
|-----|---------|-------|
| `sample-dir` | *(required)* | directory of DICOM files, searched **recursively** (subdirectories included), or a single file |
| `warmup` | `2` | untimed passes per file (JIT + native warm‑up) |
| `iterations` | `5` | timed passes per file; the median is reported |

**Environment overrides**

| Var | Purpose |
|-----|---------|
| `OUT=<file.csv>` | also write the CSV to this file (otherwise stdout only) |
| `WEASIS_VERSION=<x.y.z>` | rebuild `weasis-dicom-tools` against this `weasis.core.img.version` (native lib) first — use it for the **baseline** run |
| `JAVA_OPTS=...` | JVM options (default `-Xms2g -Xmx8g`); raise the heap for whole‑slide images |
| `TASKSET=0-7` | (Linux) pin to these CPU cores via `taskset` for stable numbers |
| `MVN=...` | Maven command to use (default `mvn`) |

The CSV goes to **stdout**; the `#` header lines and Maven output go to **stderr**, so `OUT=...` (or
a redirect) yields a clean CSV.

## A/B workflow

```bash
# 1. Candidate (the current default weasis-core-img version in the root pom)
OUT=candidate.csv \
  benchmark/run-codec-benchmark.sh /data/samples

# 2. Baseline (rebuilds weasis-dicom-tools against the old native lib first)
OUT=baseline.csv WEASIS_VERSION=4.12.0.1 \
  benchmark/run-codec-benchmark.sh /data/samples

# 3. Diff the two reports — joins on file+dst_ts, shows per-file deltas and a
#    PASS/FAIL summary against the acceptance criteria below.
benchmark/compare-codec-benchmark.py baseline.csv candidate.csv
```

`compare-codec-benchmark.py` (stdlib Python 3, no deps) is the recommended way to
diff two reports. It joins on `file` + `dst_ts` and prints, per file, the
decode / **encode** / transcode throughput change (%), the `decode_sha1`
correctness gate, and any `enc_sha1` / `out_kb` change — then a summary that exits
non-zero if a correctness or quality gate fails (handy in CI). The **decoder** is
printed just before the `decode Δ` column and the **encoder** just before
`encode Δ` (both read straight from the CSV's `decoder` / `encoder` columns), so
each codec name sits next to the numbers it produced. The `decode Δ` and
`encode Δ` columns are the same‑codec read/write pair (encode re‑encodes to the
source syntax), so a slow encoder shows up directly in `encode Δ`; it is blank for
sources with no encoder (e.g. RLE). Quality (`psnr`/`ssim`) is still checked and
gated; it just isn't a table column. The `--csv` output keeps the raw psnr/ssim
values.

```bash
benchmark/compare-codec-benchmark.py baseline.csv candidate.csv      # colored table
benchmark/compare-codec-benchmark.py base.csv cand.csv -t 5          # stricter: 5% speed-noise tolerance (default 8)
benchmark/compare-codec-benchmark.py base.csv cand.csv --sort encode # worst encode first
benchmark/compare-codec-benchmark.py base.csv cand.csv --csv > diff.csv
benchmark/compare-codec-benchmark.py base.csv cand.csv --html > codec-diff.html     # richest coloring
```

`--html` is the best-looking format: a self-contained page (open in a browser, no
assets) where **every metric cell is colored independently** — green improvement, red
regression, gray noise — failed rows are highlighted, and each gate gets a PASS/FAIL
badge. It also shows `psnr`/`ssim` as `base→cand`. Prefer it when you want true
per-cell color; it has a dark-mode variant built in.

Lower-level one-liners if you just want a column or two:

```bash
# Correctness gate — raw decoded pixels must be identical (lossless paths). decode_sha1 is col 11.
diff <(cut -d, -f1,11 baseline.csv) <(cut -d, -f1,11 candidate.csv)

# Speed — eyeball decode_mpps (col 10), or join on the file column
paste -d, <(cut -d, -f1,10 baseline.csv) <(cut -d, -f10 candidate.csv)   # file, base_mpps, cand_mpps
```

**Lossy** source codecs are handled automatically: a file in JPEG baseline
(`1.2.840.10008.1.2.4.50`), JPEG‑LS near‑lossless (`1.2.840.10008.1.2.4.81`) or J2K lossy
(`1.2.840.10008.1.2.4.91`) round‑trips through that same lossy encoder, so its `psnr_db` / `ssim`
reflect the round‑trip's generation loss — compare them between the two CSVs. Add such files to the
sample set to exercise the lossy encoders; there is no destination argument to set.

## Acceptance criteria

- **Speed:** candidate `decode_mpps` and `transcode_mpps` ≥ baseline on every sample (faster, or
  within run‑to‑run noise — never materially slower).
- **Lossless correctness:** `decode_sha1` identical between the two builds for every file; a lossless
  destination yields `psnr_db = inf`, `ssim = 1.000`.
- **Lossy quality:** candidate `psnr_db` / `ssim` ≥ baseline (no quality regression).
- **Memory:** peak heap / native RSS not materially higher on the multiframe and whole‑slide cases
  (watch the process, e.g. `/usr/bin/time -v`).

## Tips for stable measurements

- Run on an otherwise idle machine; set the CPU governor to `performance`.
- Use `TASKSET` to pin cores, and a fixed heap via `JAVA_OPTS` (avoids GC‑driven variance).
- Loop many frames per file (multiframe samples) rather than timing many tiny single‑frame files —
  JVM/process overhead would dominate otherwise (the launcher already keeps everything in one forked
  JVM).
- For whole‑slide images, raise the heap (`JAVA_OPTS="-Xms4g -Xmx16g"`).

## CI: every OS/arch + committed history

The `native-benchmark` GitHub workflow (Actions → *Native codec benchmark* → *Run
workflow*) runs the native smoke test, then this benchmark, on all five supported
runners (`linux-x86-64`, `linux-aarch64`, `macosx-x86-64`, `macosx-aarch64`,
`windows-x86-64`). A first `corpus` job fetches the shared corpus once (above) and hands it to every
runner as an artifact, so the demo host is hit once and the benchmark stays offline. It commits the
per-platform CSVs under `results/<date>/` and
diffs each platform against the previous dated run, so regressions surface across
commits over time. See [`results/README.md`](results/README.md). Locally, collect a
set of CSVs the same way with `aggregate-results.py --incoming <dir> --date <YYYY-MM-DD>`.

## Files

- `src/main/java/org/dcm4che3/img/bench/CodecBenchmark.java` — the harness (`main`).
- `run-codec-benchmark.sh` / `run-codec-benchmark.bat` — launchers (classpath + native lib + fork).
  Set `OFFLINE=0` on a fresh clone to let Maven download deps/native the first time.
- `fetch-corpus.sh` — downloads the shared corpus from the demo archive (per `manifests/*.xml`) and
  verifies it against `corpus.lock`. `LOCK_MODE=write` re-pins the lock.
- `manifests/*.xml` — vendored Weasis manifests selecting the corpus; `corpus.lock` — sha256 pin.
- `compare-codec-benchmark.py` — diffs two CSV reports (per-file deltas + PASS/FAIL gates).
- `aggregate-results.py` — collects per-platform CSVs into `results/<date>/` and diffs vs the previous run.
- `collect-metadata.sh` / `collect-metadata.ps1` — write the build-metadata sidecar (host CPU,
  OS/arch, git commit; CI adds runner image + run URL). Shared by the launchers and CI so local
  and CI results carry the same provenance.
- `results/` — committed, dated benchmark history (one CSV + metadata sidecar per platform per run).

## Notes / limitations

- The harness re‑creates the reader and input stream on every pass, so decode work is genuinely
  repeated (no result caching) and timings reflect real decode cost.
- `enc_sha1` matches across builds only when the encoder is deterministic; treat a difference as
  "investigate", not automatically "regression" (lossy encoders and some lossless encoders embed
  non‑pixel bytes).
- The native library loads once per process, so a single run uses a single version; the A/B
  comparison is two separate runs.
- The launcher installs `weasis-dicom-tools` into your local Maven repo on each run (so the benchmark
  module can resolve it against the selected native lib). This is expected.