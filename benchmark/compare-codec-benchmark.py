#!/usr/bin/env python3
# SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
"""Compare two CodecBenchmark CSV reports and show the per-file diff.

The two CSVs come from the A/B workflow in CODEC_BENCHMARK.md: same machine, same
files, same code, only ``weasis.core.img.version`` (the native library) changes.
This joins them on ``file`` + ``dst_ts`` and reports, per file:

  * decode / transcode throughput change (candidate vs baseline, in %)
  * the lossless-correctness gate (``decode_sha1`` must stay identical)
  * encoded-output changes (``enc_sha1``, ``out_kb``)
  * lossy-quality change (``psnr_db`` / ``ssim`` must not drop)

and prints a PASS/FAIL summary against the acceptance criteria.

Usage:
  compare-codec-benchmark.py BASELINE.csv CANDIDATE.csv [options]

Options:
  -t, --threshold PCT   speed-noise tolerance in %% (default: 8). A throughput
                        drop smaller than this is treated as run-to-run noise.
  -q, --quality EPS     psnr/ssim drop tolerated as noise (default: 0 = strict).
  --sort {file,decode,transcode}  row order (default: transcode, worst first).
  --no-color            disable ANSI colors (also auto-off when not a TTY).
  --csv                 emit the comparison as CSV instead of a table.
  --html                emit a self-contained HTML report with true per-cell
                        coloring (open in a browser) — every metric colored
                        independently:

                          compare-codec-benchmark.py base.csv cand.csv --html > codec-diff.html
"""
from __future__ import annotations

import argparse
import csv
import html
import math
import sys

KEY = ("file", "dst_ts")

# Native decoder selected by the source transfer syntax (src_ts, column 6).
DECODERS = {
    "1.2.840.10008.1.2": "Raw (Implicit LE)",
    "1.2.840.10008.1.2.1": "Raw (Explicit LE)",
    "1.2.840.10008.1.2.1.99": "Raw (Deflated)",
    "1.2.840.10008.1.2.5": "RLE",
    "1.2.840.10008.1.2.4.50": "JPEG (libjpeg)",
    "1.2.840.10008.1.2.4.51": "JPEG12 (libjpeg)",
    "1.2.840.10008.1.2.4.53": "JPEG (retired)",
    "1.2.840.10008.1.2.4.55": "JPEG (retired)",
    "1.2.840.10008.1.2.4.57": "JPEG-LL (libjpeg)",
    "1.2.840.10008.1.2.4.70": "JPEG-LL (libjpeg)",
    "1.2.840.10008.1.2.4.80": "JPEG-LS (CharLS)",
    "1.2.840.10008.1.2.4.81": "JPEG-LS (CharLS)",
    "1.2.840.10008.1.2.4.90": "J2K (OpenJPEG)",
    "1.2.840.10008.1.2.4.91": "J2K (OpenJPEG)",
    "1.2.840.10008.1.2.4.110": "JPEG-XL (libjxl)",
    "1.2.840.10008.1.2.4.111": "JPEG-XL (libjxl)",
    "1.2.840.10008.1.2.4.112": "JPEG-XL (libjxl)",
}


def decoder_name(src_ts):
    src_ts = (src_ts or "").strip()
    return DECODERS.get(src_ts, src_ts or "?")


def encoder_name(dst_ts):
    """Codec that wrote dst_ts; '—' when transcoding was skipped (decode-only run)."""
    dst_ts = (dst_ts or "").strip()
    return decoder_name(dst_ts) if dst_ts else "—"


def col_value(b, c, col, fallback):
    """Prefer the explicit CSV column (candidate, then baseline); a present-but-blank value means
    'none' (e.g. no encoder) and renders as '—'. Older CSVs lack the column, so derive via fallback."""
    for row in (c, b):
        if col in row:
            return (row.get(col) or "").strip() or "—"
    return fallback(c) or fallback(b) or "?"


def load(path):
    with open(path, newline="") as f:
        rows = list(csv.DictReader(f))
    table = {}
    for r in rows:
        table[(r.get("file", ""), r.get("dst_ts", ""))] = r
    return table


def num(row, col):
    """Parse a numeric cell; return None for blank/NO_PIXELS/ERROR, math.inf for 'inf'."""
    if row is None:
        return None
    v = (row.get(col) or "").strip()
    if not v:
        return None
    if v.lower() in ("inf", "+inf", "infinity"):
        return math.inf
    try:
        return float(v)
    except ValueError:
        return None


def pct_change(base, cand):
    """Percent change cand vs base; None if not computable."""
    if base is None or cand is None or base == 0:
        return None
    if math.isinf(base) or math.isinf(cand):
        return 0.0 if base == cand else None
    return (cand - base) / base * 100.0


class C:
    RESET = "\033[0m"
    RED = "\033[31m"
    GREEN = "\033[32m"
    YELLOW = "\033[33m"
    DIM = "\033[2m"
    BOLD = "\033[1m"


def disable_color():
    for n in ("RESET", "RED", "GREEN", "YELLOW", "DIM", "BOLD"):
        setattr(C, n, "")


def color_pct(p, threshold, higher_is_better=True):
    """Format a percent change, green=improvement, red=regression, dim=noise."""
    if p is None:
        return f"{C.DIM}    -{C.RESET}"
    good = p >= 0 if higher_is_better else p <= 0
    regression = (p < -threshold) if higher_is_better else (p > threshold)
    txt = f"{p:+6.1f}%"
    if regression:
        return f"{C.RED}{txt}{C.RESET}"
    if good and abs(p) > threshold:
        return f"{C.GREEN}{txt}{C.RESET}"
    return f"{C.DIM}{txt}{C.RESET}"


def main(argv=None):
    ap = argparse.ArgumentParser(
        description="Compare two CodecBenchmark CSV reports.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    ap.add_argument("baseline", help="baseline CSV (the reference run)")
    ap.add_argument("candidate", help="candidate CSV (the run under test)")
    ap.add_argument("-t", "--threshold", type=float, default=8.0,
                    help="speed-noise tolerance in %% (default: 8)")
    ap.add_argument("-q", "--quality", type=float, default=0.0,
                    help="psnr/ssim drop tolerated as noise (default: 0)")
    ap.add_argument("--sort", choices=("file", "decode", "encode", "transcode"),
                    default="transcode")
    ap.add_argument("--no-color", action="store_true")
    ap.add_argument("--csv", action="store_true", help="emit CSV not a table")
    ap.add_argument("--html", action="store_true",
                    help="emit a self-contained HTML report with per-cell coloring")
    args = ap.parse_args(argv)

    if args.no_color or args.csv or args.html or not sys.stdout.isatty():
        disable_color()

    base = load(args.baseline)
    cand = load(args.candidate)

    only_base = sorted(set(base) - set(cand))
    only_cand = sorted(set(cand) - set(base))
    common = set(base) & set(cand)

    rows = []
    for key in common:
        b, c = base[key], cand[key]
        rows.append({
            "file": key[0],
            "dst_ts": key[1],
            "src_ts_b": (b.get("src_ts") or "").strip(),
            "src_ts_c": (c.get("src_ts") or "").strip(),
            # decoder/encoder come straight from the CSV columns (the harness names the codec);
            # fall back to deriving them from src_ts/dst_ts for older CSVs without those columns.
            "decoder": col_value(b, c, "decoder",
                                  lambda r: decoder_name(r.get("src_ts"))),
            "encoder": col_value(b, c, "encoder",
                                  lambda r: encoder_name((r.get("dst_ts") or "").strip())),
            "dec_b": num(b, "decode_mpps"),
            "dec_c": num(c, "decode_mpps"),
            "tr_b": num(b, "transcode_mpps"),
            "tr_c": num(c, "transcode_mpps"),
            "encm_b": num(b, "encode_mpps"),
            "encm_c": num(c, "encode_mpps"),
            "dec_sha_b": (b.get("decode_sha1") or "").strip(),
            "dec_sha_c": (c.get("decode_sha1") or "").strip(),
            "enc_sha_b": (b.get("enc_sha1") or "").strip(),
            "enc_sha_c": (c.get("enc_sha1") or "").strip(),
            "kb_b": num(b, "out_kb"),
            "kb_c": num(c, "out_kb"),
            "psnr_b": num(b, "psnr_db"),
            "psnr_c": num(c, "psnr_db"),
            "ssim_b": num(b, "ssim"),
            "ssim_c": num(c, "ssim"),
        })
        r = rows[-1]
        r["dec_pct"] = pct_change(r["dec_b"], r["dec_c"])
        r["tr_pct"] = pct_change(r["tr_b"], r["tr_c"])
        r["enc_pct"] = pct_change(r["encm_b"], r["encm_c"])
        r["kb_pct"] = pct_change(r["kb_b"], r["kb_c"])

    # gates ---------------------------------------------------------------
    def sha_regression(r):
        # decode pixels must be bit-identical when both sides produced a hash
        return (r["dec_sha_b"] and r["dec_sha_c"]
                and r["dec_sha_b"] != r["dec_sha_c"])

    def quality_regression(r):
        bad = False
        for bcol, ccol in (("psnr_b", "psnr_c"), ("ssim_b", "ssim_c")):
            bv, cv = r[bcol], r[ccol]
            if bv is None or cv is None:
                continue
            if math.isinf(bv) and math.isinf(cv):
                continue
            if math.isinf(bv) and not math.isinf(cv):
                bad = True  # was lossless, now isn't
            elif cv < bv - args.quality:
                bad = True
        return bad

    def speed_regression(r):
        return ((r["dec_pct"] is not None and r["dec_pct"] < -args.threshold)
                or (r["tr_pct"] is not None and r["tr_pct"] < -args.threshold))

    for r in rows:
        r["sha_reg"] = sha_regression(r)
        r["qual_reg"] = quality_regression(r)
        r["speed_reg"] = speed_regression(r)
        r["enc_changed"] = (r["enc_sha_b"] and r["enc_sha_c"]
                            and r["enc_sha_b"] != r["enc_sha_c"])

    sort_key = {"decode": "dec_pct", "encode": "enc_pct", "transcode": "tr_pct"}
    if args.sort == "file":
        rows.sort(key=lambda r: r["file"])
    else:
        k = sort_key[args.sort]
        rows.sort(key=lambda r: (r[k] if r[k] is not None else 1e9))

    if args.html:
        emit_html(rows, only_base, only_cand, args)
    elif args.csv:
        emit_csv(rows)
        emit_summary(rows, only_base, only_cand, args)
    else:
        emit_table(rows, args)
        emit_summary(rows, only_base, only_cand, args)

    # exit non-zero if any hard gate (correctness/quality) failed
    failed = any(r["sha_reg"] or r["qual_reg"] for r in rows)
    return 1 if failed else 0


def emit_csv(rows):
    w = csv.writer(sys.stdout)
    w.writerow(["file", "dst_ts",
                "decoder", "decode_mpps_base", "decode_mpps_cand", "decode_pct",
                "encoder", "encode_mpps_base", "encode_mpps_cand", "encode_pct",
                "transcode_mpps_base", "transcode_mpps_cand", "transcode_pct",
                "out_kb_base", "out_kb_cand", "out_kb_pct",
                "decode_sha1_match", "enc_sha1_match", "psnr_base", "psnr_cand",
                "ssim_base", "ssim_cand", "flags"])
    for r in rows:
        flags = "".join([
            "S" if r["sha_reg"] else "",
            "Q" if r["qual_reg"] else "",
            "P" if r["speed_reg"] else "",
            "E" if r["enc_changed"] else "",
        ])
        w.writerow([
            r["file"], r["dst_ts"],
            r["decoder"], fmt(r["dec_b"]), fmt(r["dec_c"]), fmtp(r["dec_pct"]),
            r["encoder"], fmt(r["encm_b"]), fmt(r["encm_c"]), fmtp(r["enc_pct"]),
            fmt(r["tr_b"]), fmt(r["tr_c"]), fmtp(r["tr_pct"]),
            fmt(r["kb_b"]), fmt(r["kb_c"]), fmtp(r["kb_pct"]),
            "" if not (r["dec_sha_b"] and r["dec_sha_c"]) else r["dec_sha_b"] == r["dec_sha_c"],
            "" if not (r["enc_sha_b"] and r["enc_sha_c"]) else r["enc_sha_b"] == r["enc_sha_c"],
            fmt(r["psnr_b"]), fmt(r["psnr_c"]), fmt(r["ssim_b"]), fmt(r["ssim_c"]),
            flags,
        ])


def fmt(v):
    if v is None:
        return ""
    if math.isinf(v):
        return "inf"
    return f"{v:.3f}"


def fmtp(p):
    return "" if p is None else f"{p:.1f}"


def emit_table(rows, args):
    print(f"{C.BOLD}baseline:{C.RESET}  {args.baseline}")
    print(f"{C.BOLD}candidate:{C.RESET} {args.candidate}")
    print(f"{C.DIM}speed threshold ±{args.threshold:g}%  |  "
          f"green=faster red=slower/regression  |  "
          f"sha = decoded-pixel hash  |  transcode = same-codec round-trip{C.RESET}\n")

    # decoder names the codec ahead of its decode column; encoder ahead of its encode column.
    hdr = (f"{'file':<40} {'decoder':<17} {'decode Δ':>9} "
           f"{'encoder':<17} {'encode Δ':>9} {'transc Δ':>9} "
           f"{'out_kb Δ':>9} {'sha':>4} {'enc':>4}")
    print(C.BOLD + hdr + C.RESET)
    print(C.DIM + "-" * len(hdr) + C.RESET)

    for r in rows:
        fname = r["file"]
        if len(fname) > 40:
            fname = "…" + fname[-39:]

        sha = (f"{C.GREEN}ok{C.RESET}" if (r["dec_sha_b"] and r["dec_sha_c"]
               and r["dec_sha_b"] == r["dec_sha_c"]) else
               (f"{C.RED}DIFF{C.RESET}" if r["sha_reg"] else f"{C.DIM}-{C.RESET}"))
        enc = (f"{C.YELLOW}chg{C.RESET}" if r["enc_changed"] else
               (f"{C.DIM}=={C.RESET}" if (r["enc_sha_b"] and r["enc_sha_c"])
                else f"{C.DIM}-{C.RESET}"))

        print(f"{fname:<40} {decoder_cell(r)} {color_pct(r['dec_pct'], args.threshold):>9} "
              f"{r['encoder']:<17} {color_pct(r['enc_pct'], args.threshold):>9} "
              f"{color_pct(r['tr_pct'], args.threshold):>9} "
              f"{color_pct(r['kb_pct'], args.threshold, higher_is_better=False):>9} "
              f"{sha:>4} {enc:>4}")


def decoder_cell(r, width=17):
    name = r["decoder"]
    # both CSVs decode the same file, so src_ts should match — flag if it doesn't
    if r["src_ts_c"] and r["src_ts_c"] != r["src_ts_b"]:
        return f"{C.RED}{name} ≠ {decoder_name(r['src_ts_c'])}{C.RESET}"
    return f"{name:<{width}}"


def emit_summary(rows, only_base, only_cand, args):
    n = len(rows)
    sha_reg = [r for r in rows if r["sha_reg"]]
    qual_reg = [r for r in rows if r["qual_reg"]]
    speed_reg = [r for r in rows if r["speed_reg"]]
    enc_chg = [r for r in rows if r["enc_changed"]]

    dec_pcts = [r["dec_pct"] for r in rows if r["dec_pct"] is not None]
    enc_pcts = [r["enc_pct"] for r in rows if r["enc_pct"] is not None]
    tr_pcts = [r["tr_pct"] for r in rows if r["tr_pct"] is not None]

    print()
    print(C.BOLD + "summary" + C.RESET)
    print(f"  files compared:        {n}")
    if only_base:
        print(f"  {C.YELLOW}only in baseline:      {len(only_base)}{C.RESET}"
              f"  ({', '.join(k[0] for k in only_base[:4])}"
              f"{'…' if len(only_base) > 4 else ''})")
    if only_cand:
        print(f"  {C.YELLOW}only in candidate:     {len(only_cand)}{C.RESET}"
              f"  ({', '.join(k[0] for k in only_cand[:4])}"
              f"{'…' if len(only_cand) > 4 else ''})")
    if dec_pcts:
        print(f"  decode throughput:     median {median(dec_pcts):+.1f}%  "
              f"(min {min(dec_pcts):+.1f}%, max {max(dec_pcts):+.1f}%)")
    if enc_pcts:
        print(f"  encode throughput:     median {median(enc_pcts):+.1f}%  "
              f"(min {min(enc_pcts):+.1f}%, max {max(enc_pcts):+.1f}%)")
    if tr_pcts:
        print(f"  transcode throughput:  median {median(tr_pcts):+.1f}%  "
              f"(min {min(tr_pcts):+.1f}%, max {max(tr_pcts):+.1f}%)")
    if enc_chg:
        print(f"  {C.YELLOW}encoded-output changed: {len(enc_chg)} file(s) "
              f"(enc_sha1 differs — investigate, not always a regression){C.RESET}")

    print()
    gate(len(sha_reg) == 0, "lossless correctness",
         "decode_sha1 identical on every comparable file" if not sha_reg
         else f"{len(sha_reg)} file(s) changed decoded pixels: "
              + ", ".join(r["file"] for r in sha_reg[:5]))
    gate(len(qual_reg) == 0, "lossy quality",
         "no psnr/ssim regression" if not qual_reg
         else f"{len(qual_reg)} file(s) dropped quality: "
              + ", ".join(r["file"] for r in qual_reg[:5]))
    gate(len(speed_reg) == 0, f"speed (±{args.threshold:g}% tol)",
         "no throughput regression beyond noise" if not speed_reg
         else f"{len(speed_reg)} file(s) slower: "
              + ", ".join(r["file"] for r in speed_reg[:5]))


def gate(ok, name, detail):
    tag = f"{C.GREEN}PASS{C.RESET}" if ok else f"{C.RED}FAIL{C.RESET}"
    print(f"  [{tag}] {name:<26} {detail}")


HTML_CSS = """
:root { color-scheme: light dark; }
body { font-family: system-ui, -apple-system, Segoe UI, Arial, sans-serif;
       margin: 2rem; color: #1b1f23; }
h1 { font-size: 1.4rem; margin: 0 0 .3rem; }
.meta { color: #57606a; font-size: .85rem; line-height: 1.6; margin-bottom: 1rem; }
.meta code { background: #f6f8fa; padding: 1px 5px; border-radius: 4px; }
table { border-collapse: collapse; font-size: 13px; width: 100%; }
th, td { padding: 4px 10px; border-bottom: 1px solid #eaecef; white-space: nowrap; }
th { text-align: left; border-bottom: 2px solid #d0d7de; position: sticky; top: 0;
     background: #fff; }
td.num, th.num { text-align: right; font-variant-numeric: tabular-nums; }
.file { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
.up   { color: #1a7f37; font-weight: 600; }   /* improvement */
.down { color: #cf222e; font-weight: 600; }   /* regression */
.noise{ color: #8c959f; }                      /* within noise */
.warn { color: #9a6700; font-weight: 600; }
tr.fail td { background: #ffebe9; }
.legend span { margin-right: 1rem; }
.badge { display: inline-block; padding: 2px 10px; border-radius: 6px;
         font-weight: 600; font-size: 12px; color: #fff; }
.badge.pass { background: #1a7f37; }
.badge.fail { background: #cf222e; }
.gates { list-style: none; padding: 0; }
.gates li { margin: .35rem 0; }
.gates .detail { color: #57606a; margin-left: .5rem; }
@media (prefers-color-scheme: dark) {
  body { color: #c9d1d9; background: #0d1117; }
  th { background: #0d1117; border-bottom-color: #30363d; }
  th, td { border-bottom-color: #21262d; }
  .meta code { background: #161b22; }
  tr.fail td { background: #3d1418; }
  .up { color: #3fb950; } .down { color: #f85149; } .noise { color: #8b949e; }
  .warn { color: #d29922; }
}
"""


def html_pct_cell(p, threshold, higher_is_better=True):
    if p is None:
        return '<td class="num noise">–</td>'
    good = p >= 0 if higher_is_better else p <= 0
    regression = (p < -threshold) if higher_is_better else (p > threshold)
    cls = "down" if regression else ("up" if (good and abs(p) > threshold) else "noise")
    return f'<td class="num {cls}">{p:+.1f}%</td>'


def html_quality_cell(bv, cv, eps):
    """One cell showing base→cand for a quality metric, colored by direction."""
    def q(v):
        if v is None:
            return "–"
        return "∞" if math.isinf(v) else f"{v:.3f}"
    if bv is None and cv is None:
        return '<td class="num noise">–</td>'
    cls = "noise"
    if bv is not None and cv is not None:
        if math.isinf(bv) and math.isinf(cv):
            cls = "noise"
        elif math.isinf(bv) and not math.isinf(cv):
            cls = "down"  # was lossless, now isn't
        elif cv < bv - eps:
            cls = "down"
        elif cv > bv + eps:
            cls = "up"
    return f'<td class="num {cls}">{q(bv)}→{q(cv)}</td>'


def emit_html(rows, only_base, only_cand, args):
    esc = html.escape
    out = []
    out.append("<!doctype html><html lang='en'><head><meta charset='utf-8'>")
    out.append("<meta name='viewport' content='width=device-width, initial-scale=1'>")
    out.append("<title>Codec benchmark comparison</title>")
    out.append(f"<style>{HTML_CSS}</style></head><body>")
    out.append("<h1>Codec benchmark comparison</h1>")
    out.append("<div class='meta'>"
               f"baseline: <code>{esc(args.baseline)}</code><br>"
               f"candidate: <code>{esc(args.candidate)}</code><br>"
               "transcode: same-codec round-trip (re-encode to the source codec)<br>"
               f"speed threshold ±{args.threshold:g}% · quality tolerance {args.quality:g}"
               "<div class='legend' style='margin-top:.5rem'>"
               "<span class='up'>green = improvement</span>"
               "<span class='down'>red = regression</span>"
               "<span class='noise'>gray = within noise</span></div>"
               "</div>")

    # decoder names the codec ahead of its decode column; encoder ahead of its encode column.
    out.append("<table><thead><tr>"
               "<th>file</th>"
               "<th>decoder</th><th class='num'>decode Δ</th>"
               "<th>encoder</th><th class='num'>encode Δ</th>"
               "<th class='num'>transc Δ</th><th class='num'>out_kb Δ</th>"
               "<th>sha</th><th>enc</th>"
               "<th class='num'>psnr</th><th class='num'>ssim</th>"
               "</tr></thead><tbody>")
    for r in rows:
        tr_cls = " class='fail'" if (r["sha_reg"] or r["qual_reg"]) else ""
        sha = ('<td class="up">ok</td>'
               if (r["dec_sha_b"] and r["dec_sha_c"] and r["dec_sha_b"] == r["dec_sha_c"])
               else ('<td class="down">DIFF</td>' if r["sha_reg"]
                     else '<td class="noise">–</td>'))
        enc = ('<td class="warn">chg</td>' if r["enc_changed"]
               else ('<td class="noise">==</td>'
                     if (r["enc_sha_b"] and r["enc_sha_c"]) else '<td class="noise">–</td>'))
        if r["src_ts_c"] and r["src_ts_c"] != r["src_ts_b"]:
            dec_cell = (f'<td class="down">{esc(r["decoder"])} ≠ '
                        f'{esc(decoder_name(r["src_ts_c"]))}</td>')
        else:
            dec_cell = f"<td>{esc(r['decoder'])}</td>"
        out.append(
            f"<tr{tr_cls}><td class='file'>{esc(r['file'])}</td>"
            + dec_cell
            + html_pct_cell(r["dec_pct"], args.threshold)
            + f"<td>{esc(r['encoder'])}</td>"
            + html_pct_cell(r["enc_pct"], args.threshold)
            + html_pct_cell(r["tr_pct"], args.threshold)
            + html_pct_cell(r["kb_pct"], args.threshold, higher_is_better=False)
            + sha + enc
            + html_quality_cell(r["psnr_b"], r["psnr_c"], args.quality)
            + html_quality_cell(r["ssim_b"], r["ssim_c"], args.quality)
            + "</tr>")
    out.append("</tbody></table>")

    out.append(html_summary(rows, only_base, only_cand, args, esc))
    out.append("</body></html>")
    sys.stdout.write("\n".join(out) + "\n")


def html_summary(rows, only_base, only_cand, args, esc):
    n = len(rows)
    sha_reg = [r for r in rows if r["sha_reg"]]
    qual_reg = [r for r in rows if r["qual_reg"]]
    speed_reg = [r for r in rows if r["speed_reg"]]
    enc_chg = [r for r in rows if r["enc_changed"]]
    dec_pcts = [r["dec_pct"] for r in rows if r["dec_pct"] is not None]
    enc_pcts = [r["enc_pct"] for r in rows if r["enc_pct"] is not None]
    tr_pcts = [r["tr_pct"] for r in rows if r["tr_pct"] is not None]

    s = ["<h2>Summary</h2>", "<div class='meta'>"]
    s.append(f"files compared: <b>{n}</b><br>")
    if only_base:
        s.append(f"only in baseline: {len(only_base)} "
                 f"({esc(', '.join(k[0] for k in only_base[:4]))}"
                 f"{'…' if len(only_base) > 4 else ''})<br>")
    if only_cand:
        s.append(f"only in candidate: {len(only_cand)} "
                 f"({esc(', '.join(k[0] for k in only_cand[:4]))}"
                 f"{'…' if len(only_cand) > 4 else ''})<br>")
    if dec_pcts:
        s.append(f"decode throughput: median <b>{median(dec_pcts):+.1f}%</b> "
                 f"(min {min(dec_pcts):+.1f}%, max {max(dec_pcts):+.1f}%)<br>")
    if enc_pcts:
        s.append(f"encode throughput: median <b>{median(enc_pcts):+.1f}%</b> "
                 f"(min {min(enc_pcts):+.1f}%, max {max(enc_pcts):+.1f}%)<br>")
    if tr_pcts:
        s.append(f"transcode throughput: median <b>{median(tr_pcts):+.1f}%</b> "
                 f"(min {min(tr_pcts):+.1f}%, max {max(tr_pcts):+.1f}%)<br>")
    if enc_chg:
        s.append(f"encoded output changed on {len(enc_chg)} file(s) "
                 "(enc_sha1 differs — investigate, not always a regression)<br>")
    s.append("</div>")

    gates = [
        (not sha_reg, "lossless correctness",
         "decode_sha1 identical on every comparable file" if not sha_reg
         else f"{len(sha_reg)} file(s) changed decoded pixels: "
              + ", ".join(r["file"] for r in sha_reg[:5])),
        (not qual_reg, "lossy quality",
         "no psnr/ssim regression" if not qual_reg
         else f"{len(qual_reg)} file(s) dropped quality: "
              + ", ".join(r["file"] for r in qual_reg[:5])),
        (not speed_reg, f"speed (±{args.threshold:g}% tol)",
         "no throughput regression beyond noise" if not speed_reg
         else f"{len(speed_reg)} file(s) slower: "
              + ", ".join(r["file"] for r in speed_reg[:5])),
    ]
    s.append("<ul class='gates'>")
    for ok, name, detail in gates:
        badge = "<span class='badge pass'>PASS</span>" if ok \
            else "<span class='badge fail'>FAIL</span>"
        s.append(f"<li>{badge} <b>{esc(name)}</b>"
                 f"<span class='detail'>{esc(detail)}</span></li>")
    s.append("</ul>")
    return "\n".join(s)


def median(xs):
    s = sorted(xs)
    m = len(s)
    if m == 0:
        return 0.0
    return s[m // 2] if m % 2 else (s[m // 2 - 1] + s[m // 2]) / 2


if __name__ == "__main__":
    try:
        sys.exit(main())
    except BrokenPipeError:
        # downstream closed the pipe (e.g. `| head`) — exit quietly
        try:
            sys.stdout.close()
        except BrokenPipeError:
            pass
        sys.exit(0)