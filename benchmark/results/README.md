# Benchmark history

Committed output of the codec benchmark, one folder per run date, one CSV per
platform. The `native-benchmark` GitHub workflow (manually triggered) writes
here; you can also populate it locally with `aggregate-results.py`.

```
results/
  <YYYY-MM-DD>/
    linux-x86-64.csv          # raw CodecBenchmark CSV for this platform
    linux-x86-64.json         # runner image, CPU model/cores, and git sha/ref/CI run url
    linux-aarch64.csv
    macosx-x86-64.csv
    macosx-aarch64.csv
    windows-x86-64.csv
    windows-x86-64-emulated.csv   # x86-64 DLL under Windows-on-ARM x64 emulation
    <classifier>-vs-<prev>.html   # per-platform diff against the previous dated run
```

`windows-x86-64-emulated` runs the x86-64 native on a Windows-on-ARM runner (no native
arm64 build exists), so it is a *does-it-load-and-decode* check — its throughput is
emulation-bound and not comparable to the native `windows-x86-64` numbers.

### Local vs CI series

Runs produced locally (the launcher's metadata sidecar has no CI `run_url`) are filed under
a separate `<classifier>-local` key, because a local run typically uses a different sample
set and different hardware than CI. They are only ever diffed against previous **local** runs
of the same platform, never against the CI series. Within a series, a throughput Δ across a
CPU change (a CI runner rotation, or switching local machines) is marked ⚠️ "not comparable"
in the summary; correctness/quality (deterministic) stays valid regardless.

The matrix uses rolling runner labels (`ubuntu-latest`, `macos-latest`, `windows-latest`)
where a stable one exists, so each CSV ships a `<classifier>.json` sidecar recording the
exact OS image that produced it — that way the history stays traceable as the labels roll
forward.

Platform classifiers match the OpenCV native classifiers (`${os-name}-${cpu-name}`)
from the root `pom.xml` profiles.

## Comparing runs

Each new run is diffed against the most recent previous dated folder for the same
platform, so regressions surface across commits over time. To compare two runs by
hand:

```bash
python3 ../compare-codec-benchmark.py \
    2026-06-01/linux-x86-64.csv 2026-06-21/linux-x86-64.csv          # table
python3 ../compare-codec-benchmark.py \
    2026-06-01/linux-x86-64.csv 2026-06-21/linux-x86-64.csv --html > diff.html
```

The comparison is only meaningful **per platform** (same machine class, same files);
throughput is not comparable across different OS/arch runners.