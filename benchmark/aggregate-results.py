#!/usr/bin/env python3
# SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
"""Collect per-platform CodecBenchmark CSVs into a dated results/ folder and,
when a previous dated run exists, diff each platform against it.

Used by the native-benchmark workflow (and runnable locally). Reuses
compare-codec-benchmark.py for the per-file diff / HTML report.

Layout written under results/<date>/:
  <classifier>.csv                  raw per-platform run (the incoming CSV)
  <classifier>-vs-<prev>.html       diff against the previous dated run

Usage:
  aggregate-results.py --incoming DIR --date YYYY-MM-DD
                       [--results-dir DIR] [--summary-file FILE]

--incoming      directory holding the new per-platform CSVs, one per platform,
                named <classifier>.csv (e.g. linux-x86-64.csv).
--summary-file  append a Markdown summary here (e.g. $GITHUB_STEP_SUMMARY);
                always also printed to stdout.
"""
from __future__ import annotations

import argparse
import contextlib
import importlib.util
import io
import json
import re
import shutil
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")


def load_compare():
    """Import the sibling compare-codec-benchmark.py (dashed name) as a module."""
    spec = importlib.util.spec_from_file_location(
        "compare_codec_benchmark", HERE / "compare-codec-benchmark.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def median_decode_mpps(cmp, csv_path):
    """Median decode throughput (MP/s) across files, ignoring blanks/inf."""
    table = cmp.load(str(csv_path))
    vals = [v for v in (cmp.num(r, "decode_mpps") for r in table.values())
            if v is not None and v not in (float("inf"),)]
    return cmp.median(vals) if vals else None


def read_meta(path: Path):
    """Parse a sidecar JSON, or None if absent/unreadable."""
    if not path.exists():
        return None
    try:
        return json.loads(path.read_text())
    except ValueError:
        return None


def previous_dir(results_dir: Path, date: str):
    """Most recent dated folder before `date`, or None."""
    dates = sorted(p.name for p in results_dir.iterdir()
                   if p.is_dir() and DATE_RE.match(p.name) and p.name < date)
    return results_dir / dates[-1] if dates else None


def fmt_mpps(v):
    return "—" if v is None else f"{v:.1f}"


def fmt_pct(v):
    return "—" if v is None else f"{v:+.1f}%"


def system_label(meta):
    """Concrete runner image from the sidecar JSON, e.g. 'macos-latest · macos15 20240901.1'."""
    if not meta:
        return "—"
    image = f"{meta.get('image_os', '')} {meta.get('image_version', '')}".strip()
    parts = [p for p in (meta.get("runner_label", ""), image) if p]
    label = " · ".join(parts) or "—"
    return f"{label} (emulated)" if meta.get("emulated") else label


def cpu_label(meta):
    """CPU model and core count, e.g. 'Apple M1 (×3)'. Tells a hardware rotation apart from a regression."""
    if not meta:
        return "—"
    model = meta.get("cpu_model", "").strip() or "?"
    count = str(meta.get("cpu_count", "")).strip()
    return f"{model} (×{count})" if count else model


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--incoming", required=True, type=Path)
    ap.add_argument("--date", required=True)
    ap.add_argument("--results-dir", type=Path, default=HERE / "results")
    ap.add_argument("--summary-file", type=Path)
    args = ap.parse_args(argv)

    if not DATE_RE.match(args.date):
        ap.error(f"--date must be YYYY-MM-DD, got {args.date!r}")

    cmp = load_compare()
    results_dir: Path = args.results_dir
    results_dir.mkdir(parents=True, exist_ok=True)
    new_dir = results_dir / args.date
    new_dir.mkdir(exist_ok=True)
    prev_dir = previous_dir(results_dir, args.date)

    incoming = sorted(args.incoming.glob("*.csv"))
    if not incoming:
        print(f"No CSVs found in {args.incoming}", file=sys.stderr)
        return 1

    rows, regressions, provenance, hw_changes = [], 0, None, []
    for csv in incoming:
        meta = read_meta(args.incoming / f"{csv.stem}.json")
        # Local runs (no CI run_url) form their own '<classifier>-local' series: they use a
        # different dataset and hardware, so they must never be diffed against the CI series.
        base = (meta or {}).get("classifier") or csv.stem
        key = base + ("-local" if meta and not (meta.get("run_url") or "").strip() else "")

        dest = new_dir / f"{key}.csv"
        shutil.copyfile(csv, dest)
        new_med = median_decode_mpps(cmp, dest)
        if meta:
            shutil.copyfile(args.incoming / f"{csv.stem}.json", new_dir / f"{key}.json")
        # git sha/ref/run url are identical across the matrix — keep the first seen.
        if provenance is None and meta:
            provenance = meta

        delta, gate, comparable = None, "—", True
        prev_csv = prev_dir / f"{key}.csv" if prev_dir else None
        if prev_csv and prev_csv.exists():
            prev_med = median_decode_mpps(cmp, prev_csv)
            delta = cmp.pct_change(prev_med, new_med)
            buf = io.StringIO()
            with contextlib.redirect_stdout(buf):
                rc = cmp.main([str(prev_csv), str(dest), "--html"])
            (new_dir / f"{key}-vs-{prev_dir.name}.html").write_text(buf.getvalue())
            gate = "✅" if rc == 0 else "❌"
            if rc != 0:
                regressions += 1
            # Within a series, throughput Δ is only meaningful on the same hardware; flag a
            # CPU/emulation change (e.g. a runner rotation, or switching local machines).
            prev_meta = read_meta(prev_dir / f"{key}.json")
            if meta and prev_meta:
                same_cpu = meta.get("cpu_model") == prev_meta.get("cpu_model")
                same_emu = bool(meta.get("emulated")) == bool(prev_meta.get("emulated"))
                if not (same_cpu and same_emu):
                    comparable = False
                    hw_changes.append((key, prev_meta.get("cpu_model", "?"),
                                       meta.get("cpu_model", "?")))
        rows.append((key, system_label(meta), cpu_label(meta),
                     len(cmp.load(str(dest))), new_med, delta, gate, comparable))

    lines = [f"## Codec benchmark — {args.date}", ""]
    lines.append(f"Compared against previous run: "
                 f"{'`' + prev_dir.name + '`' if prev_dir else '_none (baseline)_'}")
    if provenance:
        sha = provenance.get("git_sha", "")
        ref = provenance.get("git_ref", "")
        url = provenance.get("run_url", "")
        prov = []
        if sha:
            prov.append(f"commit `{sha[:9]}`" + (f" (`{ref}`)" if ref else ""))
        if url:
            prov.append(f"[CI run]({url})")
        if prov:
            lines.append("")
            lines.append(" · ".join(prov))
    lines.append("")
    lines.append("| Platform | System | CPU (cores) | Files | Median decode MP/s | Δ decode vs prev | Correctness/quality |")
    lines.append("|---|---|---|--:|--:|--:|:--:|")
    for classifier, system, cpu, nfiles, med, delta, gate, comparable in rows:
        delta_cell = fmt_pct(delta) + ("" if comparable else " ⚠️")
        lines.append(f"| `{classifier}` | {system} | {cpu} | {nfiles} | {fmt_mpps(med)} "
                     f"| {delta_cell} | {gate} |")
    lines.append("")
    if hw_changes:
        lines.append(f"> ⚠️ throughput Δ marked ⚠️ is **not comparable** — the CPU changed vs "
                     f"`{prev_dir.name}` (e.g. a local run diffed against CI, or a runner rotation): "
                     + "; ".join(f"`{c}` {a} → {b}" for c, a, b in hw_changes)
                     + ". Correctness/quality stays valid (it is deterministic).")
    if regressions:
        lines.append(f"> ⚠️ {regressions} platform(s) flagged a correctness/quality regression "
                     f"vs `{prev_dir.name}` — see the `*-vs-{prev_dir.name}.html` diff(s).")
    summary = "\n".join(lines) + "\n"

    print(summary)
    if args.summary_file:
        with args.summary_file.open("a") as f:
            f.write(summary)
    return 0


if __name__ == "__main__":
    sys.exit(main())