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

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.management.ThreadMXBean;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.dcm4che3.img.DicomImageAdapter;
import org.dcm4che3.img.DicomImageReadParam;
import org.dcm4che3.img.DicomImageReader;
import org.dcm4che3.img.DicomImageReaderSpi;
import org.dcm4che3.img.ImageRendering;
import org.dcm4che3.img.data.OverlayData;
import org.dcm4che3.img.lut.WindLevelParameters;
import org.dcm4che3.img.stream.DicomFileInputStream;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.natives.NativeLibrary;
import org.weasis.opencv.op.ImageConversion;

/**
 * Baseline of the rendering path: decode, window/level chain and its parts, display conversion and
 * overlay. Not part of the normal test run (the class name does not match the Surefire pattern).
 *
 * <p>Run with {@code mvn -pl weasis-dicom-tools test -Dtest=RenderingBenchmark
 * -Dbench.cases=<file>}, where the file holds one {@code label|path} line per DICOM file. Optional:
 * {@code -Dbench.warmup=5 -Dbench.iterations=20 -Dbench.out=<markdown file>}.
 */
class RenderingBenchmark {
  private static final ThreadMXBean THREADS = (ThreadMXBean) ManagementFactory.getThreadMXBean();
  private static final int WARMUP = Integer.getInteger("bench.warmup", 5);
  private static final int ITERATIONS = Integer.getInteger("bench.iterations", 20);
  private static final int MAX_CINE_FRAMES = Integer.getInteger("bench.cine.frames", 60);

  private final List<String> rows = new ArrayList<>();
  private DicomImageAdapter baseAdapter;
  private double baseCenter;
  private double baseWidth;

  @BeforeAll
  static void loadNativeLibrary() {
    NativeLibrary.loadLibraryFromLibraryName();
  }

  @FunctionalInterface
  private interface Step {
    /** Returns the produced image so it can be released outside the timed section. */
    Object run(int iteration) throws Exception;
  }

  private record Stats(double medianMs, double maxMs, double allocMb) {}

  @Test
  void baseline() throws Exception {
    String cases = System.getProperty("bench.cases");
    assumeTrue(cases != null, "bench.cases is not set");

    rows.add("| Case | Measure | Median ms | Max ms | Heap MB/call | Note |");
    rows.add("|---|---|---|---|---|---|");
    for (String line : Files.readAllLines(Path.of(cases))) {
      if (line.isBlank() || line.startsWith("#")) {
        continue;
      }
      String[] parts = line.split("\\|", 2);
      benchFile(parts[0].trim(), Path.of(parts[1].trim()));
    }
    benchDisplayConversion();

    String header =
        "Threads available: %d, OpenCV threads: %d, warm-up %d, iterations %d, Java %s%n%n"
            .formatted(
                Runtime.getRuntime().availableProcessors(),
                Core.getNumThreads(),
                WARMUP,
                ITERATIONS,
                Runtime.version());
    String report = header + String.join(System.lineSeparator(), rows) + System.lineSeparator();
    System.out.println(report);
    String out = System.getProperty("bench.out");
    if (out != null) {
      Files.writeString(Path.of(out), report);
    }
  }

  private void benchFile(String label, Path file) throws Exception {
    var reader = new DicomImageReader(new DicomImageReaderSpi());
    try {
      reader.setInput(new DicomFileInputStream(file));
      ImageDescriptor desc = reader.getImageDescriptor();
      int frames = reader.getNumImages(true);
      String geometry =
          "%d × %d, %d frame(s), %s"
              .formatted(
                  desc.getColumns(), desc.getRows(), frames, desc.getPhotometricInterpretation());

      if (frames > 1) {
        benchCine(label, file, Math.min(frames, MAX_CINE_FRAMES), geometry);
      }
      add(label, "decode frame 0", measure(i -> decode(file, 0)), geometry);

      PlanarImage source = reader.getPlanarImage(0, null);
      var adapter = new DicomImageAdapter(source, desc, 0);
      if (desc.getPhotometricInterpretation().isMonochrome()) {
        benchWindowLevel(label, source, adapter);
      }
      benchOverlay(label, source, desc);

      var params = new DicomImageReadParam();
      add(
          label,
          "getDefaultRenderedImage (overlay strip + W/L + overlay)",
          measure(
              i ->
                  fresh(
                      ImageRendering.getDefaultRenderedImage(source, desc, window(adapter, i), 0),
                      source)),
          "");
      PlanarImage rendered = ImageRendering.getDefaultRenderedImage(source, desc, params, 0);
      add(
          label,
          "toBufferedImage of the rendered image (full size)",
          measure(i -> ImageConversion.toBufferedImage(rendered)),
          CvType.typeToString(rendered.type()));
    } finally {
      reader.dispose();
    }
  }

  private static PlanarImage decode(Path file, int frame) throws IOException {
    var reader = new DicomImageReader(new DicomImageReaderSpi());
    try {
      reader.setInput(new DicomFileInputStream(file));
      return reader.getPlanarImage(frame, null);
    } finally {
      reader.dispose();
    }
  }

  private void benchCine(String label, Path file, int frames, String geometry) throws Exception {
    var reader = new DicomImageReader(new DicomImageReaderSpi());
    try {
      reader.setInput(new DicomFileInputStream(file));
      Stats stats =
          measure(
              i -> {
                for (int f = 0; f < frames; f++) {
                  release(reader.getPlanarImage(f, null));
                }
                return null;
              },
              1,
              3);
      rows.add(
          "| %s | cine decode, %d frames, one reader | %.2f per frame | %.2f per frame | %.2f per frame | %s |"
              .formatted(
                  label,
                  frames,
                  stats.medianMs / frames,
                  stats.maxMs / frames,
                  stats.allocMb / frames,
                  geometry));
    } finally {
      reader.dispose();
    }
  }

  // A different window on each call, as a drag does, so no cached table can be hit
  private DicomImageReadParam window(DicomImageAdapter adapter, int iteration) {
    if (adapter != baseAdapter) {
      var defaults = new WindLevelParameters(adapter);
      baseAdapter = adapter;
      baseCenter = defaults.getLevel();
      baseWidth = defaults.getWindow();
    }
    var params = new DicomImageReadParam();
    params.setWindowCenter(baseCenter + iteration);
    params.setWindowWidth(Math.max(1.0, baseWidth + iteration));
    return params;
  }

  private void benchWindowLevel(String label, PlanarImage source, DicomImageAdapter adapter)
      throws Exception {
    Mat src = source.toMat();
    String type = CvType.typeToString(src.type());

    add(
        label,
        "W/L step: getVoiLutImage",
        measure(i -> ImageRendering.getVoiLutImage(source, adapter, window(adapter, i))),
        type);
    add(
        label,
        "part: VOI LUT table build",
        measure(i -> adapter.getVOILookup(new WindLevelParameters(adapter, window(adapter, i)))),
        "");
    var wl = new WindLevelParameters(adapter, window(adapter, 0));
    var modality = adapter.getModalityLookup(wl, wl.isInverseLut());
    if (modality != null) {
      add(label, "part: modality LUT pass alone", measure(i -> modality.lookup(src)), "");
    }
  }

  private void benchOverlay(String label, PlanarImage source, ImageDescriptor desc)
      throws Exception {
    boolean embedded = !desc.getEmbeddedOverlay().isEmpty();
    if (!embedded && desc.getOverlayData().isEmpty()) {
      return;
    }
    var params = new DicomImageReadParam();
    PlanarImage stripped = ImageRendering.getImageWithoutEmbeddedOverlay(source, desc, 0);
    PlanarImage voi = ImageRendering.getVoiLutImage(stripped, desc, params, 0);
    add(
        label,
        "overlay: OverlayData.getOverlayImage",
        measure(i -> fresh(OverlayData.getOverlayImage(source, voi, desc, params, 0), voi), 2, 5),
        embedded ? "embedded in the pixel data" : "overlay data (60xx,3000)");
  }

  private void benchDisplayConversion() throws Exception {
    int[][] sizes = {{1600, 1200}, {3840, 2160}};
    int[] types = {CvType.CV_8UC1, CvType.CV_8UC3};
    for (int[] size : sizes) {
      for (int type : types) {
        Mat view = new Mat(size[1], size[0], type);
        Core.randu(view, 0, 255);
        add(
            "viewport %d × %d".formatted(size[0], size[1]),
            "toBufferedImage (paid on every repaint)",
            measure(i -> ImageConversion.toBufferedImage(view)),
            CvType.typeToString(type));
        view.release();
      }
    }
  }

  private void add(String label, String measure, Stats stats, String note) {
    rows.add(
        String.format(
            Locale.ROOT,
            "| %s | %s | %.2f | %.2f | %.2f | %s |",
            label,
            measure,
            stats.medianMs,
            stats.maxMs,
            stats.allocMb,
            note));
  }

  private static Stats measure(Step step) throws Exception {
    return measure(step, WARMUP, ITERATIONS);
  }

  private static Stats measure(Step step, int warmup, int iterations) throws Exception {
    for (int i = 0; i < warmup; i++) {
      release(step.run(i));
    }
    long thread = Thread.currentThread().getId();
    double[] times = new double[iterations];
    long allocated = 0;
    for (int i = 0; i < iterations; i++) {
      long heap = THREADS.getThreadAllocatedBytes(thread);
      long start = System.nanoTime();
      Object result = step.run(warmup + i);
      times[i] = (System.nanoTime() - start) / 1e6;
      allocated += THREADS.getThreadAllocatedBytes(thread) - heap;
      release(result);
    }
    Arrays.sort(times);
    return new Stats(
        times[iterations / 2], times[iterations - 1], allocated / (double) iterations / (1 << 20));
  }

  // A color image can come back untouched: it must not be released with the results
  private static Object fresh(PlanarImage result, PlanarImage source) {
    return result == source ? null : result;
  }

  private static void release(Object result) {
    if (result instanceof PlanarImage image) {
      image.release();
    } else if (result instanceof Mat mat) {
      mat.release();
    }
  }
}
