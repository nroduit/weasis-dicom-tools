#!/usr/bin/env python3
# SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
"""Build one overview report across every dated run in results/.

Where aggregate-results.py files a single new run and diffs it against the
immediately previous folder, this walks the *whole* results/ history and
produces a single dashboard: for every platform classifier, every dated run,
side by side, with the run-to-run throughput trend and the correctness/quality
gate against the previous run.

It reuses compare-codec-benchmark.py for CSV loading / medians / per-run diff,
so the numbers match the per-run HTML diffs exactly.

Layout consumed (written by aggregate-results.py):
  results/<YYYY-MM-DD>/<classifier>.csv    raw per-platform run
  results/<YYYY-MM-DD>/<classifier>.json   runner/CPU/git-sha sidecar (optional)
  results/<YYYY-MM-DD>/<classifier>-vs-<prev>.html   per-run diff (linked if present)

Usage:
  report-results.py                         # history: -> results/report.html
  report-results.py -o out.html             # choose the output path
  report-results.py --format md             # Markdown instead of HTML (stdout or -o)
  report-results.py --results-dir DIR       # point at a different results/ tree

  # Compare every platform within one date against another date:
  report-results.py --compare 2026-06-21 2026-07-05
                                            # -> results/report-<A>-vs-<B>.html plus,
                                            #    per platform, <key>-<A>-vs-<B>.html diffs

  # Interactive: one self-contained page with platform / base / compare pickers
  # that recompute the per-file diff live in the browser:
  report-results.py --format interactive    # -> results/report-interactive.html

The comparison is only meaningful per platform (same machine class, same files);
throughput is never compared across different OS/arch classifiers.
"""
from __future__ import annotations

import argparse
import contextlib
import html
import importlib.util
import io
import json
import math
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")

# Throughput Δ smaller than this (in %) is treated as run-to-run noise, matching
# compare-codec-benchmark.py's default speed threshold.
NOISE_PCT = 8.0


def load_compare():
    """Import the sibling compare-codec-benchmark.py (dashed name) as a module."""
    spec = importlib.util.spec_from_file_location(
        "compare_codec_benchmark", HERE / "compare-codec-benchmark.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def read_meta(path: Path):
    """Parse a sidecar JSON, or None if absent/unreadable."""
    if not path.exists():
        return None
    try:
        return json.loads(path.read_text())
    except ValueError:
        return None


def median_metric(cmp, csv_path: Path, col: str):
    """Median of one throughput column across files, ignoring blanks/inf."""
    table = cmp.load(str(csv_path))
    vals = [v for v in (cmp.num(r, col) for r in table.values())
            if v is not None and not math.isinf(v)]
    return cmp.median(vals) if vals else None


def system_label(meta):
    """Concrete runner image from the sidecar JSON, e.g. 'ubuntu-latest · ubuntu24 20260628'."""
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


def gate_vs_prev(cmp, prev_csv: Path, cur_csv: Path):
    """PASS/FAIL of the correctness+quality gate of cur vs prev (rc==0 means pass)."""
    buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        rc = cmp.main([str(prev_csv), str(cur_csv), "--html"])
    return rc == 0


def collect(cmp, results_dir: Path):
    """Walk results/ into {classifier: [run, …]} ordered by date, with run-to-run deltas."""
    dates = sorted(p.name for p in results_dir.iterdir()
                   if p.is_dir() and DATE_RE.match(p.name))

    # classifier -> {date: {csv, meta, metrics}}
    by_key: dict[str, dict] = {}
    for date in dates:
        ddir = results_dir / date
        for csv in sorted(ddir.glob("*.csv")):
            key = csv.stem
            meta = read_meta(ddir / f"{key}.json")
            run = {
                "date": date,
                "csv": csv,
                "meta": meta,
                "files": len(cmp.load(str(csv))),
                "decode": median_metric(cmp, csv, "decode_mpps"),
                "encode": median_metric(cmp, csv, "encode_mpps"),
                "transcode": median_metric(cmp, csv, "transcode_mpps"),
            }
            by_key.setdefault(key, {})[date] = run

    # order each classifier's runs by date and fill in vs-previous fields
    result: dict[str, list] = {}
    for key, runs_by_date in sorted(by_key.items()):
        runs = [runs_by_date[d] for d in sorted(runs_by_date)]
        for i, run in enumerate(runs):
            prev = runs[i - 1] if i > 0 else None
            run["prev_date"] = prev["date"] if prev else None
            for m in ("decode", "encode", "transcode"):
                run[f"{m}_delta"] = (
                    cmp.pct_change(prev[m], run[m]) if prev else None)
            run["comparable"] = True
            run["gate"] = None  # None = baseline (no previous run)
            if prev:
                run["gate"] = gate_vs_prev(cmp, prev["csv"], run["csv"])
                pm, cm = prev["meta"], run["meta"]
                if pm and cm:
                    same_cpu = pm.get("cpu_model") == cm.get("cpu_model")
                    same_emu = bool(pm.get("emulated")) == bool(cm.get("emulated"))
                    run["comparable"] = same_cpu and same_emu
                # link the per-run diff HTML if aggregate-results.py wrote it
                diff = run["csv"].parent / f"{key}-vs-{prev['date']}.html"
                run["diff_html"] = diff.name if diff.exists() else None
            else:
                run["diff_html"] = None
        result[key] = runs
    return result, dates


def compare_dates(cmp, results_dir: Path, date_a: str, date_b: str, out_dir: Path):
    """Compare every platform present in both date folders; write a per-platform
    diff HTML and return one summary row per classifier (+ classifiers only in one)."""
    dir_a, dir_b = results_dir / date_a, results_dir / date_b
    keys_a = {p.stem for p in dir_a.glob("*.csv")}
    keys_b = {p.stem for p in dir_b.glob("*.csv")}

    rows = []
    for key in sorted(keys_a & keys_b):
        csv_a, csv_b = dir_a / f"{key}.csv", dir_b / f"{key}.csv"
        meta_a, meta_b = read_meta(dir_a / f"{key}.json"), read_meta(dir_b / f"{key}.json")

        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            rc = cmp.main([str(csv_a), str(csv_b), "--html"])
        diff_name = f"{key}-{date_a}-vs-{date_b}.html"
        (out_dir / diff_name).write_text(buf.getvalue())

        comparable = True
        if meta_a and meta_b:
            comparable = (meta_a.get("cpu_model") == meta_b.get("cpu_model")
                          and bool(meta_a.get("emulated")) == bool(meta_b.get("emulated")))
        row = {"key": key, "meta_b": meta_b, "cpu_a": cpu_label(meta_a),
               "cpu_b": cpu_label(meta_b), "gate": rc == 0, "diff_html": diff_name,
               "comparable": comparable, "only": None}
        for m, col in (("decode", "decode_mpps"), ("encode", "encode_mpps"),
                       ("transcode", "transcode_mpps")):
            a, b = median_metric(cmp, csv_a, col), median_metric(cmp, csv_b, col)
            row[f"{m}_a"], row[f"{m}_b"] = a, b
            row[f"{m}_delta"] = cmp.pct_change(a, b)
        rows.append(row)

    for key in sorted(keys_a - keys_b):
        rows.append({"key": key, "only": date_a})
    for key in sorted(keys_b - keys_a):
        rows.append({"key": key, "only": date_b})
    return rows


# ---------------------------------------------------------------------------
# formatting helpers
# ---------------------------------------------------------------------------

def fmt_mpps(v):
    return "—" if v is None else f"{v:.1f}"


def fmt_pct(v):
    return "—" if v is None else f"{v:+.1f}%"


def provenance(meta):
    if not meta:
        return "—"
    sha = (meta.get("git_sha") or "")[:9]
    ref = meta.get("git_ref") or ""
    return (sha + (f" ({ref})" if ref else "")) if sha else "—"


# ---------------------------------------------------------------------------
# Markdown output
# ---------------------------------------------------------------------------

def emit_markdown(report, dates, results_dir):
    out = ["# Codec benchmark history", ""]
    out.append(f"{len(dates)} dated run(s): "
               + ", ".join(f"`{d}`" for d in dates))
    out.append("")
    out.append("Throughput is comparable **per platform only** "
               f"(same OS/arch, same files); Δ within ±{NOISE_PCT:g}% is noise. "
               "⚠️ marks a Δ across a CPU/emulation change (not comparable).")
    out.append("")

    for key, runs in report.items():
        out.append(f"## `{key}`")
        out.append("")
        out.append("| Date | System | CPU (cores) | Files "
                   "| Decode MP/s | Δ | Encode MP/s | Δ | Transcode MP/s | Δ "
                   "| Gate | Commit |")
        out.append("|---|---|---|--:|--:|--:|--:|--:|--:|--:|:--:|---|")
        for r in runs:
            warn = "" if r["comparable"] else " ⚠️"
            gate = "—" if r["gate"] is None else ("✅" if r["gate"] else "❌")
            out.append(
                f"| `{r['date']}` | {system_label(r['meta'])} "
                f"| {cpu_label(r['meta'])} | {r['files']} "
                f"| {fmt_mpps(r['decode'])} | {fmt_pct(r['decode_delta'])}{warn} "
                f"| {fmt_mpps(r['encode'])} | {fmt_pct(r['encode_delta'])}{warn} "
                f"| {fmt_mpps(r['transcode'])} | {fmt_pct(r['transcode_delta'])}{warn} "
                f"| {gate} | {provenance(r['meta'])} |")
        out.append("")
    return "\n".join(out) + "\n"


# ---------------------------------------------------------------------------
# HTML output — reuses the palette from compare-codec-benchmark.py
# ---------------------------------------------------------------------------

HTML_CSS = """
:root { color-scheme: light dark; }
body { font-family: system-ui, -apple-system, Segoe UI, Arial, sans-serif;
       margin: 2rem; color: #1b1f23; }
h1 { font-size: 1.5rem; margin: 0 0 .3rem; }
h2 { font-size: 1.1rem; margin: 1.8rem 0 .5rem; }
h2 code { background: #f6f8fa; padding: 1px 7px; border-radius: 5px; font-size: .95em; }
.meta { color: #57606a; font-size: .85rem; line-height: 1.6; margin-bottom: 1rem; }
.meta code { background: #f6f8fa; padding: 1px 5px; border-radius: 4px; }
table { border-collapse: collapse; font-size: 13px; width: 100%; margin-bottom: .5rem; }
th, td { padding: 4px 10px; border-bottom: 1px solid #eaecef; white-space: nowrap; }
th { text-align: left; border-bottom: 2px solid #d0d7de; }
td.num, th.num { text-align: right; font-variant-numeric: tabular-nums; }
td.mono, th.mono { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
.up   { color: #1a7f37; font-weight: 600; }
.down { color: #cf222e; font-weight: 600; }
.noise{ color: #8c959f; }
.warn { color: #9a6700; font-weight: 600; }
.base { color: #8c959f; }
.overview td.num { font-weight: 600; }
a { color: #0969da; text-decoration: none; }
a:hover { text-decoration: underline; }
.legend span { margin-right: 1rem; }
@media (prefers-color-scheme: dark) {
  body { color: #c9d1d9; background: #0d1117; }
  th { border-bottom-color: #30363d; }
  th, td { border-bottom-color: #21262d; }
  h2 code, .meta code { background: #161b22; }
  .up { color: #3fb950; } .down { color: #f85149; } .noise, .base { color: #8b949e; }
  .warn { color: #d29922; } a { color: #58a6ff; }
}
"""


def pct_cell(p, comparable=True):
    """Percent Δ cell, green up / red down / gray noise, ⚠️ across a hw change."""
    if p is None:
        return '<td class="num base">–</td>'
    cls = "down" if p < -NOISE_PCT else ("up" if p > NOISE_PCT else "noise")
    warn = ' <span class="warn" title="CPU/emulation changed — not comparable">⚠️</span>' if not comparable else ""
    return f'<td class="num {cls}">{p:+.1f}%{warn}</td>'


def gate_cell(gate, diff_html):
    if gate is None:
        return '<td class="base">baseline</td>'
    label = "PASS" if gate else "FAIL"
    cls = "up" if gate else "down"
    inner = f'<a href="{html.escape(diff_html)}">{label}</a>' if diff_html else label
    return f'<td class="{cls}">{inner}</td>'


def emit_html(report, dates, results_dir):
    esc = html.escape
    out = ["<!doctype html><html lang='en'><head><meta charset='utf-8'>",
           "<meta name='viewport' content='width=device-width, initial-scale=1'>",
           "<title>Codec benchmark history</title>",
           f"<style>{HTML_CSS}</style></head><body>",
           "<h1>Codec benchmark history</h1>",
           "<div class='meta'>"
           f"{len(dates)} dated run(s): "
           + " · ".join(f"<code>{esc(d)}</code>" for d in dates)
           + "<br>Throughput is comparable <b>per platform only</b> "
           f"(same OS/arch, same files). Δ within ±{NOISE_PCT:g}% is noise."
           "<div class='legend' style='margin-top:.5rem'>"
           "<span class='up'>green = faster</span>"
           "<span class='down'>red = slower / gate fail</span>"
           "<span class='noise'>gray = within noise</span>"
           "<span class='warn'>⚠️ = CPU/emulation change, not comparable</span></div>"
           "</div>"]

    # overview matrix: platforms × dates, median decode MP/s
    out.append("<h2>Overview — median decode MP/s</h2>")
    out.append("<table class='overview'><thead><tr><th>platform</th>"
               + "".join(f"<th class='num'>{esc(d)}</th>" for d in dates)
               + "</tr></thead><tbody>")
    for key, runs in report.items():
        by_date = {r["date"]: r for r in runs}
        cells = []
        for d in dates:
            r = by_date.get(d)
            cells.append(f"<td class='num'>{fmt_mpps(r['decode'])}</td>"
                         if r else "<td class='num base'>–</td>")
        out.append(f"<tr><td class='mono'>{esc(key)}</td>" + "".join(cells) + "</tr>")
    out.append("</tbody></table>")

    # one detail table per platform
    for key, runs in report.items():
        out.append(f"<h2><code>{esc(key)}</code></h2>")
        out.append("<table><thead><tr>"
                   "<th>date</th><th>system</th><th>CPU (cores)</th>"
                   "<th class='num'>files</th>"
                   "<th class='num'>decode</th><th class='num'>Δ</th>"
                   "<th class='num'>encode</th><th class='num'>Δ</th>"
                   "<th class='num'>transcode</th><th class='num'>Δ</th>"
                   "<th>gate</th><th class='mono'>commit</th>"
                   "</tr></thead><tbody>")
        for r in runs:
            out.append(
                "<tr>"
                f"<td class='mono'>{esc(r['date'])}</td>"
                f"<td>{esc(system_label(r['meta']))}</td>"
                f"<td>{esc(cpu_label(r['meta']))}</td>"
                f"<td class='num'>{r['files']}</td>"
                f"<td class='num'>{fmt_mpps(r['decode'])}</td>"
                + pct_cell(r["decode_delta"], r["comparable"])
                + f"<td class='num'>{fmt_mpps(r['encode'])}</td>"
                + pct_cell(r["encode_delta"], r["comparable"])
                + f"<td class='num'>{fmt_mpps(r['transcode'])}</td>"
                + pct_cell(r["transcode_delta"], r["comparable"])
                + gate_cell(r["gate"], r["diff_html"])
                + f"<td class='mono'>{esc(provenance(r['meta']))}</td>"
                "</tr>")
        out.append("</tbody></table>")

    out.append("</body></html>")
    return "\n".join(out) + "\n"


def emit_compare_markdown(rows, date_a, date_b):
    out = [f"# Codec benchmark — `{date_a}` → `{date_b}`", ""]
    out.append("One row per platform; Δ is date-B vs date-A throughput. Comparable "
               f"**per platform only**; Δ within ±{NOISE_PCT:g}% is noise, ⚠️ marks a "
               "CPU/emulation change. Gate = decoded-pixel + lossy-quality correctness.")
    out.append("")
    out.append("| Platform | CPU (cores) | Decode Δ | Encode Δ | Transcode Δ | Gate | Diff |")
    out.append("|---|---|--:|--:|--:|:--:|---|")
    for r in rows:
        if r["only"]:
            out.append(f"| `{r['key']}` | _only in {r['only']}_ | — | — | — | — | — |")
            continue
        warn = "" if r["comparable"] else " ⚠️"
        out.append(
            f"| `{r['key']}` | {r['cpu_b']} "
            f"| {fmt_pct(r['decode_delta'])}{warn} | {fmt_pct(r['encode_delta'])}{warn} "
            f"| {fmt_pct(r['transcode_delta'])}{warn} "
            f"| {'✅' if r['gate'] else '❌'} | [{r['diff_html']}]({r['diff_html']}) |")
    out.append("")
    return "\n".join(out) + "\n"


def emit_compare_html(rows, date_a, date_b):
    esc = html.escape
    out = ["<!doctype html><html lang='en'><head><meta charset='utf-8'>",
           "<meta name='viewport' content='width=device-width, initial-scale=1'>",
           f"<title>Codec benchmark {esc(date_a)} → {esc(date_b)}</title>",
           f"<style>{HTML_CSS}</style></head><body>",
           f"<h1>Codec benchmark — <code>{esc(date_a)}</code> → <code>{esc(date_b)}</code></h1>",
           "<div class='meta'>"
           "Δ is date-B throughput vs date-A, per platform. "
           f"Δ within ±{NOISE_PCT:g}% is noise. Gate = decoded-pixel + lossy-quality "
           "correctness; follow the per-platform diff for the file-level breakdown."
           "<div class='legend' style='margin-top:.5rem'>"
           "<span class='up'>green = faster</span>"
           "<span class='down'>red = slower / gate fail</span>"
           "<span class='noise'>gray = within noise</span>"
           "<span class='warn'>⚠️ = CPU/emulation change, not comparable</span></div>"
           "</div>",
           "<table><thead><tr><th>platform</th><th>CPU (cores)</th>"
           "<th class='num'>decode Δ</th><th class='num'>encode Δ</th>"
           "<th class='num'>transcode Δ</th><th>gate</th><th>diff</th>"
           "</tr></thead><tbody>"]
    for r in rows:
        if r["only"]:
            out.append(f"<tr><td class='mono'>{esc(r['key'])}</td>"
                       f"<td class='base' colspan='6'>only in {esc(r['only'])}</td></tr>")
            continue
        out.append(
            f"<tr><td class='mono'>{esc(r['key'])}</td>"
            f"<td>{esc(r['cpu_b'])}</td>"
            + pct_cell(r["decode_delta"], r["comparable"])
            + pct_cell(r["encode_delta"], r["comparable"])
            + pct_cell(r["transcode_delta"], r["comparable"])
            + gate_cell(r["gate"], None)
            + f"<td><a href='{esc(r['diff_html'])}'>files →</a></td></tr>")
    out.append("</tbody></table></body></html>")
    return "\n".join(out) + "\n"


# ---------------------------------------------------------------------------
# Interactive HTML — embeds every run's rows; the diff is recomputed in JS to
# mirror compare-codec-benchmark.py so the numbers match the static diffs.
# ---------------------------------------------------------------------------

# Only the columns the browser-side diff needs, kept short to bound page size.
INTERACTIVE_COLS = {
    "f": "file", "dts": "dst_ts", "sts": "src_ts", "dc": "decoder", "ec": "encoder",
    "dec": "decode_mpps", "tr": "transcode_mpps", "en": "encode_mpps",
    "dsha": "decode_sha1", "esha": "enc_sha1", "kb": "out_kb",
    "psnr": "psnr_db", "ssim": "ssim",
}


def build_interactive_data(cmp, results_dir: Path):
    """{classifier: {"runs": {date: {cpu, system, commit, emulated, cpu_model, rows}}}}."""
    dates = sorted(p.name for p in results_dir.iterdir()
                   if p.is_dir() and DATE_RE.match(p.name))
    out: dict[str, dict] = {}
    for date in dates:
        ddir = results_dir / date
        for csv in sorted(ddir.glob("*.csv")):
            key = csv.stem
            meta = read_meta(ddir / f"{key}.json")
            rows = [{short: (r.get(col) or "").strip()
                     for short, col in INTERACTIVE_COLS.items()}
                    for r in cmp.load(str(csv)).values()]
            out.setdefault(key, {"runs": {}})["runs"][date] = {
                "cpu": cpu_label(meta), "system": system_label(meta),
                "commit": provenance(meta), "emulated": bool(meta.get("emulated")) if meta else False,
                "cpu_model": (meta or {}).get("cpu_model", ""), "rows": rows,
            }
    return out


INTERACTIVE_CONTROLS_CSS = """
.controls { display: flex; flex-wrap: wrap; gap: 1rem; align-items: end;
            margin: 1rem 0 1.2rem; }
.controls label { display: flex; flex-direction: column; font-size: .8rem;
                  color: #57606a; gap: .25rem; }
.controls select { font-size: 14px; padding: 4px 8px; border-radius: 6px;
                   border: 1px solid #d0d7de; background: #fff; color: inherit; }
.swap { padding: 5px 12px; border-radius: 6px; border: 1px solid #d0d7de;
        background: #f6f8fa; color: inherit; cursor: pointer; font-size: 13px; }
.runinfo { font-size: .82rem; color: #57606a; line-height: 1.6; margin-bottom: .8rem; }
.runinfo b { color: inherit; }
.banner { padding: .5rem .8rem; border-radius: 6px; margin-bottom: 1rem;
          font-size: .85rem; background: #fff8c5; color: #633c01; }
.gates { list-style: none; padding: 0; }
.gates li { margin: .35rem 0; }
.badge { display: inline-block; padding: 2px 10px; border-radius: 6px;
         font-weight: 600; font-size: 12px; color: #fff; }
.badge.pass { background: #1a7f37; } .badge.fail { background: #cf222e; }
.detail { color: #57606a; margin-left: .5rem; }
tr.fail td { background: #ffebe9; }
@media (prefers-color-scheme: dark) {
  .controls select, .swap { background: #161b22; border-color: #30363d; }
  .controls label, .runinfo { color: #8b949e; }
  .banner { background: #3b2e00; color: #f2cc60; }
  tr.fail td { background: #3d1418; }
}
"""


def emit_interactive(cmp, results_dir: Path):
    data = build_interactive_data(cmp, results_dir)
    # `</script>` inside a JSON string literal would close the tag early — escape it.
    payload = json.dumps(data, separators=(",", ":")).replace("</", "<\\/")
    decoders = json.dumps(cmp.DECODERS, separators=(",", ":"))
    return (
        "<!doctype html><html lang='en'><head><meta charset='utf-8'>"
        "<meta name='viewport' content='width=device-width, initial-scale=1'>"
        "<title>Codec benchmark — interactive compare</title>"
        f"<style>{HTML_CSS}{INTERACTIVE_CONTROLS_CSS}</style></head><body>"
        "<h1>Codec benchmark — interactive compare</h1>"
        "<div class='meta'>Pick a platform and two dates; the per-file diff is computed "
        f"in your browser. Δ is <b>compare</b> vs <b>base</b>; within ±{NOISE_PCT:g}% is noise. "
        "Comparison is meaningful per platform only.</div>"
        "<div class='controls'>"
        "<label>Platform<select id='platform'></select></label>"
        "<label>Base date<select id='base'></select></label>"
        "<label>Compare date<select id='cand'></select></label>"
        "<button class='swap' id='swap' title='Swap base and compare'>⇄ swap</button>"
        "</div>"
        "<div id='banner'></div><div id='runinfo' class='runinfo'></div>"
        "<div id='out'></div>"
        f"<script>const DATA={payload};const DECODERS={decoders};"
        f"const NOISE={NOISE_PCT};const QEPS=0;</script>"
        f"<script>{INTERACTIVE_JS}</script></body></html>\n")


INTERACTIVE_JS = r"""
const esc = s => String(s ?? "").replace(/[&<>"]/g,
  c => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"}[c]));

function num(v){
  v = (v ?? "").trim();
  if (!v) return null;
  if (/^[+]?inf(inity)?$/i.test(v)) return Infinity;
  const n = parseFloat(v);
  return Number.isNaN(n) ? null : n;
}
function pct(base, cand){
  if (base == null || cand == null || base === 0) return null;
  if (!isFinite(base) || !isFinite(cand)) return base === cand ? 0 : null;
  return (cand - base) / base * 100;
}
function decoderName(ts){ ts = (ts ?? "").trim(); return DECODERS[ts] || ts || "?"; }
function encoderName(ts){ ts = (ts ?? "").trim(); return ts ? decoderName(ts) : "—"; }
function cell(v){ if (v == null) return "–"; return !isFinite(v) ? "∞" : v.toFixed(3); }
function median(xs){
  const s = [...xs].sort((a,b)=>a-b), m = s.length;
  if (!m) return 0;
  return m % 2 ? s[(m-1)/2] : (s[m/2-1] + s[m/2]) / 2;
}

const $ = id => document.getElementById(id);
const platformSel = $("platform"), baseSel = $("base"), candSel = $("cand");

function fill(sel, opts, selected){
  sel.innerHTML = opts.map(o => `<option${o===selected?" selected":""}>${esc(o)}</option>`).join("");
}
function datesFor(p){ return Object.keys(DATA[p].runs).sort(); }

fill(platformSel, Object.keys(DATA).sort());
function refreshDates(keepBase, keepCand){
  const ds = datesFor(platformSel.value);
  fill(baseSel, ds, keepBase && ds.includes(keepBase) ? keepBase : ds[0]);
  fill(candSel, ds, keepCand && ds.includes(keepCand) ? keepCand : ds[ds.length-1]);
}
refreshDates();

platformSel.onchange = () => { refreshDates(); render(); };
baseSel.onchange = candSel.onchange = render;
$("swap").onclick = () => {
  const b = baseSel.value; baseSel.value = candSel.value; candSel.value = b; render();
};

function pctCell(p, higherBetter=true){
  if (p == null) return '<td class="num noise">–</td>';
  const good = higherBetter ? p >= 0 : p <= 0;
  const reg = higherBetter ? p < -NOISE : p > NOISE;
  const cls = reg ? "down" : (good && Math.abs(p) > NOISE ? "up" : "noise");
  return `<td class="num ${cls}">${p>=0?"+":""}${p.toFixed(1)}%</td>`;
}
function qualCell(bv, cv){
  if (bv == null && cv == null) return '<td class="num noise">–</td>';
  let cls = "noise";
  if (bv != null && cv != null){
    if (isFinite(bv) && !isFinite(cv)) cls = "up";
    else if (!isFinite(bv) && isFinite(cv)) cls = "down";
    else if (isFinite(bv) && isFinite(cv)){
      if (cv < bv - QEPS) cls = "down"; else if (cv > bv + QEPS) cls = "up";
    }
  }
  return `<td class="num ${cls}">${cell(bv)}→${cell(cv)}</td>`;
}

function render(){
  const p = platformSel.value, da = baseSel.value, dc = candSel.value;
  const A = DATA[p].runs[da], B = DATA[p].runs[dc];

  $("runinfo").innerHTML =
    `<b>base</b> ${esc(da)} — ${esc(A.system)} · ${esc(A.cpu)} · ${esc(A.commit)}<br>` +
    `<b>compare</b> ${esc(dc)} — ${esc(B.system)} · ${esc(B.cpu)} · ${esc(B.commit)}`;

  const comparable = A.cpu_model === B.cpu_model && A.emulated === B.emulated;
  $("banner").innerHTML = (da === dc)
    ? "<div class='banner'>Base and compare are the same run — every Δ is 0.</div>"
    : (comparable ? "" :
       "<div class='banner'>⚠️ CPU/emulation changed between these runs — throughput Δ "
       + "is <b>not comparable</b>. Correctness/quality (deterministic) stays valid.</div>");

  const key = r => r.f + "	" + r.dts;
  const mapA = new Map(A.rows.map(r => [key(r), r]));
  const mapB = new Map(B.rows.map(r => [key(r), r]));

  const rows = [];
  for (const [k, b] of mapA){
    if (!mapB.has(k)) continue;
    const c = mapB.get(k);
    const r = {
      file: b.f,
      decoder: (c.dc || b.dc || decoderName(c.sts || b.sts)),
      encoder: (c.ec || b.ec || encoderName(c.dts || b.dts)),
      sts_b: b.sts, sts_c: c.sts,
      dec: pct(num(b.dec), num(c.dec)),
      en:  pct(num(b.en),  num(c.en)),
      tr:  pct(num(b.tr),  num(c.tr)),
      kb:  pct(num(b.kb),  num(c.kb)),
      dsha_b: b.dsha, dsha_c: c.dsha, esha_b: b.esha, esha_c: c.esha,
      psnr_b: num(b.psnr), psnr_c: num(c.psnr), ssim_b: num(b.ssim), ssim_c: num(c.ssim),
    };
    r.shaReg = r.dsha_b && r.dsha_c && r.dsha_b !== r.dsha_c;
    r.encChg = r.esha_b && r.esha_c && r.esha_b !== r.esha_c;
    r.qualReg = [[r.psnr_b,r.psnr_c],[r.ssim_b,r.ssim_c]].some(([bv,cv]) =>
      bv != null && cv != null &&
      ((isFinite(bv) && !isFinite(cv)) || (isFinite(cv) && cv < bv - QEPS)));
    rows.push(r);
  }
  const onlyBase = [...mapA.keys()].filter(k => !mapB.has(k)).length;
  const onlyCand = [...mapB.keys()].filter(k => !mapA.has(k)).length;
  rows.sort((a,b) => (a.tr==null?1e9:a.tr) - (b.tr==null?1e9:b.tr));

  let h = "<table><thead><tr><th>file</th><th>decoder</th><th class='num'>decode Δ</th>"
        + "<th>encoder</th><th class='num'>encode Δ</th><th class='num'>transc Δ</th>"
        + "<th class='num'>out_kb Δ</th><th>sha</th><th>enc</th>"
        + "<th class='num'>psnr</th><th class='num'>ssim</th></tr></thead><tbody>";
  for (const r of rows){
    const trCls = (r.shaReg || r.qualReg) ? " class='fail'" : "";
    const sha = (r.dsha_b && r.dsha_c && r.dsha_b === r.dsha_c) ? '<td class="up">ok</td>'
              : (r.shaReg ? '<td class="down">DIFF</td>' : '<td class="noise">–</td>');
    const enc = r.encChg ? '<td class="warn">chg</td>'
              : ((r.esha_b && r.esha_c) ? '<td class="noise">==</td>' : '<td class="noise">–</td>');
    const decCell = (r.sts_c && r.sts_c !== r.sts_b)
      ? `<td class="down">${esc(r.decoder)} ≠ ${esc(decoderName(r.sts_c))}</td>`
      : `<td>${esc(r.decoder)}</td>`;
    h += `<tr${trCls}><td class='file'>${esc(r.file)}</td>${decCell}`
       + pctCell(r.dec) + `<td>${esc(r.encoder)}</td>` + pctCell(r.en)
       + pctCell(r.tr) + pctCell(r.kb, false) + sha + enc
       + qualCell(r.psnr_b, r.psnr_c) + qualCell(r.ssim_b, r.ssim_c) + "</tr>";
  }
  h += "</tbody></table>";

  const decs = rows.map(r=>r.dec).filter(v=>v!=null);
  const ens  = rows.map(r=>r.en).filter(v=>v!=null);
  const trs  = rows.map(r=>r.tr).filter(v=>v!=null);
  const shaReg = rows.filter(r=>r.shaReg), qualReg = rows.filter(r=>r.qualReg);
  const speedReg = rows.filter(r=>(r.dec!=null&&r.dec<-NOISE)||(r.tr!=null&&r.tr<-NOISE));
  const encChg = rows.filter(r=>r.encChg);
  const stat = (xs,l) => xs.length ? `${l}: median <b>${median(xs)>=0?"+":""}${median(xs).toFixed(1)}%</b> `
      + `(min ${Math.min(...xs).toFixed(1)}%, max ${Math.max(...xs).toFixed(1)}%)<br>` : "";
  h += "<h2>Summary</h2><div class='meta'>"
     + `files compared: <b>${rows.length}</b>`
     + (onlyBase ? ` · only in base: ${onlyBase}` : "")
     + (onlyCand ? ` · only in compare: ${onlyCand}` : "") + "<br>"
     + stat(decs,"decode throughput") + stat(ens,"encode throughput") + stat(trs,"transcode throughput")
     + (encChg.length ? `encoded output changed on ${encChg.length} file(s) (enc_sha1 differs)<br>` : "")
     + "</div>";
  const gate = (ok,name,detail) =>
    `<li><span class="badge ${ok?"pass":"fail"}">${ok?"PASS":"FAIL"}</span> <b>${esc(name)}</b>`
    + `<span class="detail">${esc(detail)}</span></li>`;
  h += "<ul class='gates'>"
     + gate(!shaReg.length, "lossless correctness", shaReg.length
         ? `${shaReg.length} file(s) changed decoded pixels` : "decode_sha1 identical on every comparable file")
     + gate(!qualReg.length, "lossy quality", qualReg.length
         ? `${qualReg.length} file(s) dropped quality` : "no psnr/ssim regression")
     + gate(!speedReg.length, `speed (±${NOISE}% tol)`, speedReg.length
         ? `${speedReg.length} file(s) slower` : "no throughput regression beyond noise")
     + "</ul>";
  $("out").innerHTML = h;
}
render();
"""


def main(argv=None):
    ap = argparse.ArgumentParser(
        description="Overview report across every dated run in results/.",
        formatter_class=argparse.RawDescriptionHelpFormatter, epilog=__doc__)
    ap.add_argument("--results-dir", type=Path, default=HERE / "results")
    ap.add_argument("--format", choices=("html", "md", "interactive"), default="html")
    ap.add_argument("--compare", nargs=2, metavar=("DATE_A", "DATE_B"),
                    help="compare every platform of DATE_A against DATE_B instead of "
                         "the full history overview")
    ap.add_argument("-o", "--output", type=Path,
                    help="output file (default: results/report[-A-vs-B].html for html; "
                         "stdout for md)")
    args = ap.parse_args(argv)

    if not args.results_dir.is_dir():
        ap.error(f"results dir not found: {args.results_dir}")

    cmp = load_compare()

    if args.format == "interactive":
        if args.compare:
            ap.error("--format interactive already lets you pick both dates; drop --compare")
        text = emit_interactive(cmp, args.results_dir)
        out_path = args.output or (args.results_dir / "report-interactive.html")
        out_path.write_text(text)
        print(f"Wrote {out_path}", file=sys.stderr)
        return 0

    if args.compare:
        date_a, date_b = args.compare
        for d in (date_a, date_b):
            if not DATE_RE.match(d):
                ap.error(f"--compare dates must be YYYY-MM-DD, got {d!r}")
            if not (args.results_dir / d).is_dir():
                ap.error(f"no such dated run folder: {args.results_dir / d}")
        out_path = args.output
        if out_path is None and args.format == "html":
            out_path = args.results_dir / f"report-{date_a}-vs-{date_b}.html"
        # per-platform diff HTMLs are written next to the summary so their links resolve
        diff_dir = out_path.parent if out_path else args.results_dir
        rows = compare_dates(cmp, args.results_dir, date_a, date_b, diff_dir)
        if not any(r["only"] is None for r in rows):
            print(f"No platform CSVs common to {date_a} and {date_b}", file=sys.stderr)
            return 1
        render = emit_compare_html if args.format == "html" else emit_compare_markdown
        text = render(rows, date_a, date_b)
        n = sum(1 for r in rows if r["only"] is None)
        if out_path is None:
            sys.stdout.write(text)
        else:
            out_path.write_text(text)
            print(f"Wrote {out_path} ({n} platform(s) compared, "
                  f"plus per-platform diffs in {diff_dir})", file=sys.stderr)
        return 0

    report, dates = collect(cmp, args.results_dir)
    if not dates:
        print(f"No dated run folders (YYYY-MM-DD) under {args.results_dir}",
              file=sys.stderr)
        return 1

    render = emit_html if args.format == "html" else emit_markdown
    text = render(report, dates, args.results_dir)

    out_path = args.output
    if out_path is None and args.format == "html":
        out_path = args.results_dir / "report.html"
    if out_path is None:
        sys.stdout.write(text)
    else:
        out_path.write_text(text)
        print(f"Wrote {out_path} ({len(report)} platform(s), {len(dates)} run(s))",
              file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())