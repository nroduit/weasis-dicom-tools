/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.bench;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.awt.image.Raster;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.imageio.codec.TransferSyntaxType;
import org.dcm4che3.img.DicomImageReadParam;
import org.dcm4che3.img.DicomImageReader;
import org.dcm4che3.img.DicomJpegWriteParam;
import org.dcm4che3.img.DicomMetaData;
import org.dcm4che3.img.DicomOutputData;
import org.dcm4che3.img.DicomTranscodeParam;
import org.dcm4che3.img.Transcoder;
import org.dcm4che3.img.stream.DicomFileInputStream;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.dcm4che3.img.util.SupplierEx;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.weasis.opencv.data.PlanarImage;

/**
 * Standalone A/B benchmark for the native image codec (weasis-core-img / OpenCV).
 *
 * <p>It walks a directory of DICOM files and, for each one, measures <b>decode</b> (compressed
 * pixels &rarr; raw raster), <b>transcode</b> (decode + re-encode back to the <i>source</i> codec
 * &mdash; a same-codec round-trip) and <b>encode</b> alone (re-encode of already-decoded pixels
 * &mdash; the frames are decoded eagerly and untimed first, so the timer covers only the encoder)
 * and prints one CSV row per file. Both the transcode and the encode pass re-encode with the
 * encoder that matches the file's own decoder, so {@code decode_*}, {@code transcode_*} and {@code
 * encode_*} all exercise the same codec; the {@code decoder} / {@code encoder} columns name it. The
 * transcode is skipped (its columns left blank) for raw sources &mdash; nothing to decompress
 * &mdash; and for compressed codecs the library cannot encode (RLE, HTJ2K); the encode columns are
 * likewise blank when the source codec has no encoder. Run it once against the baseline native
 * library and once against the candidate, then diff the two CSVs:
 *
 * <ul>
 *   <li><b>{@code dst_ts}</b> is the syntax actually written by the round-trip; it equals {@code
 *       src_ts} (same codec in, same codec out). The harness <i>verifies</i> the process genuinely
 *       decompressed and recompressed: if the encoder downgraded the output to raw because the
 *       pixel type rules out the source codec, no recompression happened and the file is reported
 *       as an {@code ERROR} row rather than misleading timings.
 *   <li><b>{@code *_mpps}</b> columns give throughput (megapixels/s) &mdash; the speed gate.
 *   <li><b>{@code decode_sha1}</b> is a hash of the raw decoded pixels &mdash; for lossless source
 *       syntaxes it must be identical between the two builds (correctness gate). For the color
 *       samples this catches an RGB/BGR swap. For lossy source it may legitimately differ.
 *   <li><b>{@code enc_sha1}</b> hashes the re-encoded stream &mdash; identical only when the
 *       encoder is deterministic and lossless.
 *   <li><b>{@code psnr_db} / {@code ssim}</b> measure round-trip quality: the re-encoded stream is
 *       decoded back and compared against the original raster. Relevant for <i>lossy</i> source
 *       codecs (the round-trip's generation loss) &mdash; the candidate's values must be &ge; the
 *       baseline's (no quality regression). For lossless source codecs PSNR is {@code inf} and SSIM
 *       is {@code 1.000}, which also doubles as a losslessness check.
 * </ul>
 *
 * <pre>
 * Usage: CodecBenchmark &lt;sample-dir&gt; [warmup] [iterations]
 *   warmup       untimed passes per file (default 2)
 *   iterations   timed passes per file, median reported (default 5)
 * </pre>
 *
 * @author Nicolas Roduit
 * @since Jun 2026
 */
// Standalone CLI: stdout is the CSV data channel and stderr carries diagnostics, so the
// console-output and printStackTrace rules do not apply here.
@SuppressWarnings({"java:S106", "java:S1148"})
public class CodecBenchmark {

  // SSIM is computed over non-overlapping windows, processed in horizontal strips to bound memory.
  private static final int SSIM_WINDOW = 8;
  private static final int STRIP_HEIGHT =
      32; // multiple of SSIM_WINDOW so whole windows fit a strip

  private static final String CSV_HEADER =
      "file,frames,rows,cols,mp_per_frame,src_ts,"
          + "decoder,decode_med_ms,decode_p95_ms,decode_mpps,decode_sha1,"
          + "dst_ts,transcode_med_ms,transcode_p95_ms,transcode_mpps,out_kb,enc_sha1,psnr_db,ssim,"
          + "encoder,encode_med_ms,encode_p95_ms,encode_mpps";

  public static void main(String[] args) throws IOException {
    if (args.length < 1) {
      System.err.println("Usage: CodecBenchmark <sample-dir> [warmup] [iterations]");
      System.exit(2);
    }
    Path dir = Path.of(args[0]);
    int warmup = args.length > 1 ? Integer.parseInt(args[1]) : 2;
    int iters = args.length > 2 ? Integer.parseInt(args[2]) : 5;

    // Collect files recursively (so passing a folder also picks up its subdirectories).
    Path root;
    List<Path> candidates = new ArrayList<>();
    if (Files.isDirectory(dir)) {
      root = dir;
      try (Stream<Path> walk = Files.walk(root)) {
        walk.filter(Files::isRegularFile).sorted().forEach(candidates::add);
      }
    } else {
      Path parent = dir.getParent();
      root = parent != null ? parent : dir;
      candidates = Collections.singletonList(dir);
    }

    // Keep only DICOM objects that carry decodable pixel data; everything else (non-DICOM files,
    // structured reports, presentation states, …) is silently skipped instead of producing ERROR
    // rows. Skipped paths are listed on stderr so the filtering is never silent.
    List<Path> files = new ArrayList<>();
    int skipped = 0;
    for (Path f : candidates) {
      if (isDecodableImage(f)) {
        files.add(f);
      } else {
        skipped++;
        System.err.println("# skip (no decodable image): " + root.relativize(f));
      }
    }
    if (files.isEmpty()) {
      System.err.println("No DICOM image files in " + dir);
      System.exit(1);
    }

    System.err.printf(
        "# weasis-core-img=%s  java=%s  os=%s/%s  cores=%d%n",
        System.getProperty("weasis.core.img.version", "?"),
        System.getProperty("java.version"),
        System.getProperty("os.name"),
        System.getProperty("os.arch"),
        Runtime.getRuntime().availableProcessors());
    System.err.printf(
        "# transcode=same-codec round-trip  warmup=%d  iterations=%d  files=%d  skipped=%d%n",
        warmup, iters, files.size(), skipped);
    System.out.println(CSV_HEADER);

    for (Path f : files) {
      // CSV 'file' column = path relative to the root, so rows stay unique across subdirectories.
      String label = root.relativize(f).toString();
      try {
        benchOne(f, label, warmup, iters);
      } catch (Exception e) {
        System.out.printf("%s,ERROR:%s%n", csv(label), sanitize(e.toString()));
        System.err.println("FAILED " + label + System.lineSeparator() + stackTrace(e));
      }
    }
  }

  /** True if {@code f} is a DICOM object whose pixel data the codec can decode. */
  private static boolean isDecodableImage(Path f) {
    try (DicomInputStream dis = new DicomInputStream(Files.newInputStream(f))) {
      dis.setIncludeBulkData(DicomInputStream.IncludeBulkData.URI);
      Attributes attrs = dis.readDataset();
      return attrs.getInt(Tag.Rows, 0) > 0
          && attrs.getInt(Tag.Columns, 0) > 0
          && attrs.contains(Tag.PixelData);
    } catch (Exception e) {
      return false; // not a DICOM stream, truncated, or otherwise unreadable
    }
  }

  /** Per-file source metadata: frame count, geometry, pixel depth and transfer syntax. */
  private record FrameInfo(
      int frames, int rows, int cols, int bitsStored, int samplesPerPixel, String srcTS) {
    double mpPerFrame() {
      return rows * (double) cols / 1_000_000.0;
    }

    double totalMp() {
      return mpPerFrame() * frames;
    }
  }

  /** Timed transcode result plus the round-trip verification (output syntax, size, quality). */
  private record TranscodeResult(
      double medMs,
      double p95Ms,
      double mpps,
      long outBytes,
      String encSha1,
      String dstTS,
      double psnr,
      double ssim) {}

  private static void benchOne(Path f, String label, int warmup, int iters)
      throws IOException, NoSuchAlgorithmException {
    // The caller has already verified via isDecodableImage that this file carries pixel data.
    FrameInfo info = readFrameInfo(f);

    long[] decodeNs = new long[iters];
    String decodeSha1 = timeDecode(f, info.frames(), warmup, iters, decodeNs);

    // The encoder is the one that matches this file's own decoder: transcode and encode both target
    // the SOURCE transfer syntax. A round-trip only makes sense for a compressed source that the
    // library can also encode — raw sources have nothing to decompress, and codecs without an
    // encoder (RLE, HTJ2K) cannot recompress, so both skip the transcode/encode stages.
    boolean hasEncoder = DicomOutputData.isSupportedSyntax(info.srcTS());
    boolean compressedSource = isEncapsulated(info.srcTS());

    TranscodeResult transcode =
        compressedSource && hasEncoder ? timeTranscode(f, info, warmup, iters) : null;
    long[] encodeNs = hasEncoder ? timeEncode(f, info.srcTS(), info.frames(), warmup, iters) : null;

    System.out.println(
        buildRow(label, info, hasEncoder, decodeNs, decodeSha1, transcode, encodeNs));
  }

  /** Reads frame count, geometry, pixel depth and transfer syntax without loading pixel data. */
  private static FrameInfo readFrameInfo(Path f) throws IOException {
    try (DicomInputStream dis = new DicomInputStream(Files.newInputStream(f))) {
      dis.setIncludeBulkData(DicomInputStream.IncludeBulkData.NO);
      Attributes attrs = dis.readDataset();
      return new FrameInfo(
          attrs.getInt(Tag.NumberOfFrames, 1),
          attrs.getInt(Tag.Rows, 0),
          attrs.getInt(Tag.Columns, 0),
          attrs.getInt(Tag.BitsStored, attrs.getInt(Tag.BitsAllocated, 8)),
          attrs.getInt(Tag.SamplesPerPixel, 1),
          dis.getTransferSyntax());
    }
  }

  /**
   * Re-creates reader + stream each pass so pixels are genuinely re-decoded; returns the digest.
   */
  private static String timeDecode(Path f, int frames, int warmup, int iters, long[] ns)
      throws IOException, NoSuchAlgorithmException {
    String digest = null;
    for (int i = -warmup; i < iters; i++) {
      long t0 = System.nanoTime();
      String h = decodeAllFrames(f, frames);
      long dt = System.nanoTime() - t0;
      if (i >= 0) {
        ns[i] = dt;
        digest = h; // deterministic across passes; keep the last
      }
    }
    return digest;
  }

  /** Full decode + re-encode to the source codec (output discarded but counted), then verified. */
  private static TranscodeResult timeTranscode(Path f, FrameInfo info, int warmup, int iters)
      throws IOException, NoSuchAlgorithmException {
    String srcTS = info.srcTS();
    long[] ns = new long[iters];
    long outBytes = -1;
    String encSha1 = null;
    for (int i = -warmup; i < iters; i++) {
      CountingDigestStream sink = new CountingDigestStream();
      long t0 = System.nanoTime();
      transcode(f, srcTS, sink);
      long dt = System.nanoTime() - t0;
      if (i >= 0) {
        ns[i] = dt;
        outBytes = sink.count;
        encSha1 = sink.hex();
      }
    }
    // ---- VERIFY + QUALITY (untimed): decode the re-encoded stream and compare to the original.
    // ----
    // Written next to the source (a caller-controlled directory) rather than the world-writable
    // system temp dir, then deleted in the finally block.
    Path tmp = Files.createTempFile(f.toAbsolutePath().getParent(), "codecbench-", ".dcm");
    try {
      try (OutputStream out = Files.newOutputStream(tmp)) {
        transcode(f, srcTS, out);
      }
      String actualDstTS = readTransferSyntax(tmp); // ground truth of what was written
      // Verify the round-trip genuinely decompressed and recompressed: the output must be written
      // in the same encapsulated codec as the source. If the library downgraded to raw (the pixel
      // type ruled out the codec) no recompression happened — fail the row loudly.
      if (!srcTS.equals(actualDstTS) || !isEncapsulated(actualDstTS)) {
        throw new IOException(
            "transcode did not recompress to source codec (" + srcTS + " -> " + actualDstTS + ")");
      }
      // Dynamic range used as PSNR/SSIM peak. Signed pixels use their full bit-depth span.
      double maxVal = (double) (1L << Math.max(1, info.bitsStored())) - 1;
      double[] q = quality(f, tmp, info.frames(), maxVal, info.samplesPerPixel() >= 3);
      return new TranscodeResult(
          ns2ms(median(ns)),
          ns2ms(p95(ns)),
          info.totalMp() / (median(ns) / 1e9),
          outBytes,
          encSha1,
          actualDstTS,
          q[0],
          q[1]);
    } finally {
      Files.deleteIfExists(tmp);
    }
  }

  /**
   * Re-encodes to the SOURCE syntax; frames are decoded eagerly (untimed), only encode is timed.
   */
  private static long[] timeEncode(Path f, String srcTS, int frames, int warmup, int iters)
      throws IOException, NoSuchAlgorithmException {
    long[] ns = new long[iters];
    for (int i = -warmup; i < iters; i++) {
      long dt = encodeOnly(f, srcTS, frames);
      if (i >= 0) {
        ns[i] = dt;
      }
    }
    return ns;
  }

  /** Assembles one CSV row from the decode / transcode / encode measurements. */
  private static String buildRow(
      String label,
      FrameInfo info,
      boolean hasEncoder,
      long[] decodeNs,
      String decodeSha1,
      TranscodeResult transcode,
      long[] encodeNs) {
    double totalMp = info.totalMp();
    StringBuilder sb = new StringBuilder();
    sb.append(csv(label))
        .append(',')
        .append(info.frames())
        .append(',')
        .append(info.rows())
        .append(',')
        .append(info.cols())
        .append(',')
        .append(fmt(info.mpPerFrame()))
        .append(',')
        .append(info.srcTS())
        .append(',')
        .append(csv(codecName(info.srcTS()))) // decoder (names the codec before the decode columns)
        .append(',')
        .append(fmt(ns2ms(median(decodeNs))))
        .append(',')
        .append(fmt(ns2ms(p95(decodeNs))))
        .append(',')
        .append(fmt(totalMp / (median(decodeNs) / 1e9)))
        .append(',')
        .append(decodeSha1);
    if (transcode != null) {
      sb.append(',')
          .append(transcode.dstTS())
          .append(',')
          .append(fmt(transcode.medMs()))
          .append(',')
          .append(fmt(transcode.p95Ms()))
          .append(',')
          .append(fmt(transcode.mpps()))
          .append(',')
          .append(fmt(transcode.outBytes() / 1024.0))
          .append(',')
          .append(transcode.encSha1())
          .append(',')
          .append(fmtPsnr(transcode.psnr()))
          .append(',')
          .append(fmt(transcode.ssim()));
    } else {
      sb.append(",,,,,,,,"); // 8 transcode columns
    }
    // encoder names the codec before the encode columns; blank when the source has no encoder.
    sb.append(',').append(csv(hasEncoder ? codecName(info.srcTS()) : ""));
    // encode columns are independent: present whenever the source codec has an encoder.
    if (encodeNs != null) {
      sb.append(',')
          .append(fmt(ns2ms(median(encodeNs))))
          .append(',')
          .append(fmt(ns2ms(p95(encodeNs))))
          .append(',')
          .append(fmt(totalMp / (median(encodeNs) / 1e9)));
    } else {
      sb.append(",,,"); // 3 encode columns
    }
    return sb.toString();
  }

  // The library registers its ImageIO SPI under a non-standard resource path, so ImageIO's
  // service lookup does not find it; instantiate the reader directly from the public SPI instead
  // (exactly how org.dcm4che3.img.Transcoder does internally).
  private static DicomImageReader newReader() {
    return new DicomImageReader(Transcoder.dicomImageReaderSpi);
  }

  /** Reads every frame's raw raster and returns a SHA-256 over the decoded pixel data. */
  private static String decodeAllFrames(Path f, int frames)
      throws IOException, NoSuchAlgorithmException {
    DicomImageReader reader = newReader();
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    try (DicomFileInputStream iis = new DicomFileInputStream(f)) {
      reader.setInput(iis);
      DicomImageReadParam param = (DicomImageReadParam) reader.getDefaultReadParam();
      int n = reader.getNumImages(true);
      if (n <= 0) n = frames;
      for (int i = 0; i < n; i++) {
        Raster raster = reader.readRaster(i, param);
        digestRaster(md, raster);
      }
    } finally {
      reader.dispose();
    }
    return hex(md.digest());
  }

  private static void transcode(Path f, String dstTS, OutputStream sink) throws IOException {
    DicomTranscodeParam params = new DicomTranscodeParam(dstTS);
    params.setOutputFmi(true);
    Transcoder.dcm2dcm(f, sink, params);
  }

  /** Transfer syntax actually recorded in a (transcoded) file's meta information. */
  private static String readTransferSyntax(Path f) throws IOException {
    try (DicomInputStream dis = new DicomInputStream(Files.newInputStream(f))) {
      dis.setIncludeBulkData(DicomInputStream.IncludeBulkData.NO);
      dis.readDataset();
      return dis.getTransferSyntax();
    }
  }

  /**
   * Times the encode step alone: all frames are decoded eagerly first (untimed), then only the
   * re-encode to {@code targetTS} is measured. Mirrors what {@link Transcoder#dcm2dcm} does
   * internally, but the lazy decode is forced up front so the timer covers the encoder only. Output
   * is hashed into a counting sink and discarded.
   */
  private static long encodeOnly(Path f, String targetTS, int frames)
      throws IOException, NoSuchAlgorithmException {
    DicomImageReader reader = newReader();
    try (DicomFileInputStream iis = new DicomFileInputStream(f)) {
      reader.setInput(iis);
      DicomMetaData meta = reader.getStreamMetadata();
      ImageDescriptor desc = meta.getImageDescriptor();
      Attributes dataSet = new Attributes(meta.getDicomObject());
      dataSet.remove(Tag.PixelData);
      DicomImageReadParam param = (DicomImageReadParam) reader.getDefaultReadParam();

      // Decode every frame into memory (untimed) so the encoder has nothing left to decode.
      int n = reader.getNumImages(true);
      if (n <= 0) n = frames;
      List<SupplierEx<PlanarImage, IOException>> images = new ArrayList<>(n);
      for (int fr = 0; fr < n; fr++) {
        PlanarImage img = reader.getPlanarImage(fr, param);
        images.add(() -> img);
      }

      DicomOutputData out = new DicomOutputData(images, desc, targetTS);
      String actualTS = out.getTsuid();
      DicomJpegWriteParam writeParams = new DicomTranscodeParam(targetTS).getWriteJpegParam();
      if (!actualTS.equals(targetTS) && !DicomOutputData.isNativeSyntax(actualTS)) {
        writeParams = DicomJpegWriteParam.buildDicomImageWriteParam(actualTS);
      }

      // ---- timed region: encode the already-decoded frames ----
      CountingDigestStream sink = new CountingDigestStream();
      long t0 = System.nanoTime();
      try (DicomOutputStream dos = new DicomOutputStream(sink, actualTS)) {
        dos.writeFileMetaInformation(dataSet.createFileMetaInformation(actualTS));
        if (DicomOutputData.isNativeSyntax(actualTS)) {
          out.writeRawImageData(dos, dataSet);
        } else {
          int[] jpegParams =
              out.adaptTagsToCompressedImage(dataSet, out.getFirstImage().get(), desc, writeParams);
          out.writeCompressedImageData(dos, dataSet, jpegParams);
        }
      }
      return System.nanoTime() - t0;
    } finally {
      reader.dispose();
    }
  }

  /**
   * Decodes {@code orig} and {@code enc} frame by frame and returns {@code {PSNR_dB, mean-SSIM}} of
   * the re-encoded image against the original. PSNR is +Inf and SSIM 1.0 when the two decode
   * identically (i.e. a lossless round-trip). Processed in horizontal strips so peak memory stays
   * bounded for large pathology frames; SSIM uses non-overlapping 8x8 windows per band.
   *
   * <p>Grayscale is compared on the raw decoded samples (peak = {@code maxVal}). Color is compared
   * in the reader's RGB presentation via {@link DicomImageReader#read} with peak 255: a lossless
   * color transcode may change the photometric interpretation (e.g. YBR_FULL &harr; RGB), so the
   * raw samples differ while the displayed pixels are identical &mdash; comparing in RGB makes a
   * lossless color round-trip score Inf/1.0.
   */
  private static double[] quality(Path orig, Path enc, int frames, double maxVal, boolean color)
      throws IOException {
    final double peak = color ? 255.0 : maxVal;
    final double c1 = sq(0.01 * peak);
    final double c2 = sq(0.03 * peak);

    DicomImageReader r1 = newReader();
    DicomImageReader r2 = newReader();
    // accumulators: sum of squared error, sample count, SSIM sum, window count
    double[] acc = new double[4];
    try (DicomFileInputStream i1 = new DicomFileInputStream(orig);
        DicomFileInputStream i2 = new DicomFileInputStream(enc)) {
      r1.setInput(i1);
      r2.setInput(i2);
      DicomImageReadParam p1 = (DicomImageReadParam) r1.getDefaultReadParam();
      DicomImageReadParam p2 = (DicomImageReadParam) r2.getDefaultReadParam();
      int n = Math.min(r1.getNumImages(true), r2.getNumImages(true));
      if (n <= 0) n = frames;
      for (int fr = 0; fr < n; fr++) {
        if (color) {
          accumulateColorFrame(acc, r1.read(fr, p1), r2.read(fr, p2), c1, c2);
        } else {
          accumulateGrayFrame(acc, r1.readRaster(fr, p1), r2.readRaster(fr, p2), c1, c2);
        }
      }
    } finally {
      r1.dispose();
      r2.dispose();
    }
    double mse = acc[1] == 0 ? 0 : acc[0] / acc[1];
    double psnr = mse == 0 ? Double.POSITIVE_INFINITY : 10 * Math.log10(peak * peak / mse);
    double ssim = acc[3] == 0 ? 1.0 : acc[2] / acc[3];
    return new double[] {psnr, ssim};
  }

  /** Grayscale (and other raw-sample) comparison: per band, in horizontal strips, on the raster. */
  private static void accumulateGrayFrame(
      double[] acc, Raster ra, Raster rb, double c1, double c2) {
    int w = Math.min(ra.getWidth(), rb.getWidth());
    int h = Math.min(ra.getHeight(), rb.getHeight());
    int bands = Math.min(ra.getNumBands(), rb.getNumBands());
    for (int b = 0; b < bands; b++) {
      for (int y = 0; y < h; y += STRIP_HEIGHT) {
        int hh = Math.min(STRIP_HEIGHT, h - y);
        double[] a = ra.getSamples(0, y, w, hh, b, (double[]) null);
        double[] e = rb.getSamples(0, y, w, hh, b, (double[]) null);
        accumulateStrip(acc, a, e, w, hh, c1, c2);
      }
    }
  }

  /**
   * Color comparison in actual sRGB via {@link BufferedImage#getRGB}, which applies each image's
   * color model. This normalizes a photometric-interpretation change (e.g. YBR_FULL &harr; RGB)
   * across a lossless transcode; comparing the raw rasters would not. The R/G/B channels are scored
   * independently and pooled, in horizontal strips to bound memory on large frames.
   */
  private static void accumulateColorFrame(
      double[] acc, BufferedImage ba, BufferedImage be, double c1, double c2) {
    int w = Math.min(ba.getWidth(), be.getWidth());
    int h = Math.min(ba.getHeight(), be.getHeight());
    for (int y = 0; y < h; y += STRIP_HEIGHT) {
      int hh = Math.min(STRIP_HEIGHT, h - y);
      int[] ia = ba.getRGB(0, y, w, hh, null, 0, w);
      int[] ie = be.getRGB(0, y, w, hh, null, 0, w);
      double[] a = new double[ia.length];
      double[] e = new double[ie.length];
      for (int shift = 16; shift >= 0; shift -= 8) { // R, G, B
        for (int k = 0; k < ia.length; k++) {
          a[k] = (ia[k] >> shift) & 0xff;
          e[k] = (ie[k] >> shift) & 0xff;
        }
        accumulateStrip(acc, a, e, w, hh, c1, c2);
      }
    }
  }

  /** Adds one strip's squared error and SSIM windows into the running accumulator. */
  private static void accumulateStrip(
      double[] acc, double[] a, double[] e, int w, int hh, double c1, double c2) {
    for (int k = 0; k < a.length; k++) {
      double d = a[k] - e[k];
      acc[0] += d * d;
    }
    acc[1] += a.length;
    for (int wy = 0; wy + SSIM_WINDOW <= hh; wy += SSIM_WINDOW) {
      for (int wx = 0; wx + SSIM_WINDOW <= w; wx += SSIM_WINDOW) {
        acc[2] += ssimWindow(a, e, w, wx, wy, c1, c2);
        acc[3] += 1;
      }
    }
  }

  /** SSIM over a single window at (wx,wy) in two row-major strip buffers of width w. */
  private static double ssimWindow(
      double[] a, double[] e, int w, int wx, int wy, double c1, double c2) {
    int n = SSIM_WINDOW * SSIM_WINDOW;
    double sa = 0;
    double se = 0;
    double saa = 0;
    double see = 0;
    double sae = 0;
    for (int r = 0; r < SSIM_WINDOW; r++) {
      int row = (wy + r) * w + wx;
      for (int c = 0; c < SSIM_WINDOW; c++) {
        double va = a[row + c];
        double ve = e[row + c];
        sa += va;
        se += ve;
        saa += va * va;
        see += ve * ve;
        sae += va * ve;
      }
    }
    double ma = sa / n;
    double me = se / n;
    double va = saa / n - ma * ma;
    double ve = see / n - me * me;
    double cov = sae / n - ma * me;
    return ((2 * ma * me + c1) * (2 * cov + c2)) / ((ma * ma + me * me + c1) * (va + ve + c2));
  }

  private static double sq(double v) {
    return v * v;
  }

  private static void digestRaster(MessageDigest md, Raster raster) {
    DataBuffer db = raster.getDataBuffer();
    for (int b = 0; b < db.getNumBanks(); b++) {
      if (db instanceof DataBufferByte dbb) {
        md.update(dbb.getData(b));
      } else if (db instanceof DataBufferUShort dbus) {
        update(md, dbus.getData(b));
      } else if (db instanceof DataBufferShort dbs) {
        update(md, dbs.getData(b));
      } else if (db instanceof DataBufferInt dbi) {
        update(md, dbi.getData(b));
      } else {
        int size = db.getSize();
        for (int i = 0; i < size; i++) {
          update(md, db.getElem(b, i));
        }
      }
    }
  }

  private static void update(MessageDigest md, short[] a) {
    byte[] buf = new byte[a.length * 2];
    for (int i = 0; i < a.length; i++) {
      int j = i * 2;
      buf[j] = (byte) (a[i] >> 8);
      buf[j + 1] = (byte) a[i];
    }
    md.update(buf);
  }

  private static void update(MessageDigest md, int[] a) {
    byte[] buf = new byte[a.length * 4];
    for (int i = 0; i < a.length; i++) {
      int j = i * 4;
      buf[j] = (byte) (a[i] >> 24);
      buf[j + 1] = (byte) (a[i] >> 16);
      buf[j + 2] = (byte) (a[i] >> 8);
      buf[j + 3] = (byte) a[i];
    }
    md.update(buf);
  }

  private static void update(MessageDigest md, int v) {
    md.update((byte) (v >> 24));
    md.update((byte) (v >> 16));
    md.update((byte) (v >> 8));
    md.update((byte) v);
  }

  /** Output stream that discards data but counts bytes and keeps a running SHA-256. */
  private static final class CountingDigestStream extends OutputStream {
    long count;
    final MessageDigest md;

    CountingDigestStream() throws NoSuchAlgorithmException {
      md = MessageDigest.getInstance("SHA-256");
    }

    @Override
    public void write(int b) {
      md.update((byte) b);
      count++;
    }

    @Override
    public void write(byte[] b, int off, int len) {
      md.update(b, off, len);
      count += len;
    }

    String hex() {
      return CodecBenchmark.hex(md.digest());
    }
  }

  private static long median(long[] a) {
    long[] s = a.clone();
    Arrays.sort(s);
    if (s.length == 0) return 0L;
    return s[s.length / 2];
  }

  private static long p95(long[] a) {
    long[] s = a.clone();
    Arrays.sort(s);
    if (s.length == 0) return 0L;
    int idx = (int) Math.ceil(0.95 * s.length) - 1;
    return s[Math.max(0, Math.min(idx, s.length - 1))];
  }

  private static double ns2ms(long ns) {
    return ns / 1e6;
  }

  private static String fmt(double d) {
    return String.format(java.util.Locale.ROOT, "%.3f", d);
  }

  private static String fmtPsnr(double d) {
    if (Double.isNaN(d)) return "";
    return Double.isInfinite(d) ? "inf" : fmt(d);
  }

  /** True if the pixel data of {@code tsuid} is encapsulated (a compressed codec, not raw). */
  private static boolean isEncapsulated(String tsuid) {
    TransferSyntaxType t = TransferSyntaxType.forUID(tsuid);
    return t != null && t.isPixeldataEncapsulated();
  }

  // Human-readable codec name (with the native library behind it) for the decoder/encoder columns.
  private static String codecName(String tsuid) {
    if (tsuid == null || tsuid.isEmpty()) return "";
    return switch (tsuid) {
      case UID.ImplicitVRLittleEndian -> "Raw (Implicit LE)";
      case UID.ExplicitVRLittleEndian -> "Raw (Explicit LE)";
      case UID.DeflatedExplicitVRLittleEndian -> "Raw (Deflated)";
      case UID.RLELossless -> "RLE";
      case UID.JPEGBaseline8Bit -> "JPEG (libjpeg)";
      case UID.JPEGExtended12Bit -> "JPEG12 (libjpeg)";
      case UID.JPEGLossless, UID.JPEGLosslessSV1 -> "JPEG-LL (libjpeg)";
      case UID.JPEGLSLossless, UID.JPEGLSNearLossless -> "JPEG-LS (CharLS)";
      case UID.JPEG2000Lossless, UID.JPEG2000 -> "J2K (OpenJPEG)";
      case UID.JPEGXLLossless, UID.JPEGXL, UID.JPEGXLJPEGRecompression -> "JPEG-XL (libjxl)";
      default -> tsuid;
    };
  }

  private static String sanitize(String s) {
    return s.replace(',', ';').replace('\n', ' ');
  }

  /** Full stack trace as a string, so a failed file is diagnosable without a logging framework. */
  private static String stackTrace(Throwable t) {
    StringWriter sw = new StringWriter();
    t.printStackTrace(new PrintWriter(sw));
    return sw.toString();
  }

  // RFC-4180 quoting for the 'file' column: paths may contain a comma (e.g. a study folder named
  // "MR Original Sagittal,1 st") which would otherwise shift every later column by one.
  private static String csv(String s) {
    if (s.indexOf(',') < 0 && s.indexOf('"') < 0 && s.indexOf('\n') < 0 && s.indexOf('\r') < 0) {
      return s;
    }
    return '"' + s.replace("\"", "\"\"") + '"';
  }

  private static String hex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes)
      sb.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
    return sb.toString();
  }

  private CodecBenchmark() {}
}
