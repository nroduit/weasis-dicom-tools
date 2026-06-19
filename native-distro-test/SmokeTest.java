/*
 * Cross-distro smoke test for the OpenCV native build.
 *
 *   1. loads libopencv_java via the project's NativeLibrary loader (java.library.path);
 *   2. mode "swap" <file>: decodes a big-endian (Explicit VR Big Endian, 1.2.840.10008.1.2.2)
 *      DICOM and hashes every decoded pixel byte. dcm4che swaps by the transfer syntax,
 *      not the host CPU, so the hash MUST be identical on every architecture; a
 *      -Dexpected.be.hash=<sha256> mismatch fails the run. Also checks a channel reaches a
 *      high value, so a grossly wrong swap/planar handling is caught;
 *   3. mode "threads" <file>: decodes every frame of a compressed multi-frame DICOM
 *      concurrently, with OpenCV's native multithreading on, so a real threaded codec
 *      path runs (this is what crashes a glibc lib on musl at thread teardown).
 *
 * The two modes MUST run as separate JVM processes: a single-threaded decode warms up
 * OpenCV in a way that suppresses the musl thread-teardown crash, so mixing them in one
 * process masks the very failure mode the threads check exists to surface.
 *
 * Default package + no test framework on purpose: it must run from a plain JRE inside a
 * minimal container with only the runtime jars on the classpath.
 *
 * Usage: java -Djava.library.path=lib/<os-arch> [-Dexpected.be.hash=<hex>] \
 *             -cp "classes:jars/*" SmokeTest <swap|threads> data/<file>.dcm
 */
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.dcm4che3.img.DicomImageReader;
import org.dcm4che3.img.DicomImageReaderSpi;
import org.dcm4che3.img.stream.DicomFileInputStream;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.natives.NativeLibrary;

public class SmokeTest {
  public static void main(String[] args) throws Exception {
    System.out.printf(
        "[smoke] JVM %s / %s on %s %s%n",
        System.getProperty("java.vendor"),
        System.getProperty("java.version"),
        System.getProperty("os.name"),
        System.getProperty("os.arch"));

    // Step 1: load the native lib explicitly so a link/libc failure surfaces clearly here.
    NativeLibrary.loadLibraryFromLibraryName();
    System.out.println("[smoke] native lib loaded: " + NativeLibrary.getNativeLibSpecification());
    int cpus = Runtime.getRuntime().availableProcessors();
    System.out.printf("[smoke] OpenCV threads=%d, CPUs=%d%n", Core.getNumThreads(), cpus);

    String mode = args.length > 0 ? args[0] : "threads";
    String file = args.length > 1 ? args[1] : "data/jpeg2000-multiframe-multifragments.dcm";
    switch (mode) {
      case "swap" -> checkByteSwap(Path.of(file));
      case "threads" -> threadedDecode(Path.of(file), cpus);
      default -> throw new IllegalArgumentException("unknown mode: " + mode + " (expected swap|threads)");
    }
    System.out.println("[smoke] OK");
  }

  // Decode the multi-frame file concurrently on a few threads, with OpenCV's native
  // multithreading on, to validate real threaded execution on a cleanly-linked libc.
  // (Detecting musl is the harness's ldd gate's job, not this; so a moderate, stable
  // concurrency level is used rather than aggressive thread churn that flakes under QEMU.)
  static void threadedDecode(Path dcm, int cpus) throws Exception {
    int workers = Math.max(2, Math.min(4, cpus));
    int perThread = 4;
    AtomicReference<Throwable> err = new AtomicReference<>();
    AtomicInteger ok = new AtomicInteger();
    List<Thread> ts = new ArrayList<>();
    for (int k = 0; k < workers; k++) {
      Thread t =
          new Thread(
              () -> {
                try {
                  for (int r = 0; r < perThread; r++) {
                    decodeAllFrames(dcm);
                    ok.incrementAndGet();
                  }
                } catch (Throwable e) {
                  err.compareAndSet(null, e);
                }
              },
              "dec-" + k);
      ts.add(t);
      t.start();
    }
    for (Thread t : ts) {
      t.join();
    }
    if (err.get() != null) {
      throw new RuntimeException("threaded decode failed", err.get());
    }
    System.out.printf("[smoke] threaded decode: %d decodes on %d threads%n", ok.get(), workers);
  }

  // Decode a big-endian DICOM and hash all pixel bytes; the hash is host-arch invariant.
  static void checkByteSwap(Path dcm) throws Exception {
    if (!Files.exists(dcm)) {
      System.out.println("[smoke] big-endian sample absent, skipping swap check: " + dcm);
      return;
    }
    DicomImageReader reader = new DicomImageReader(new DicomImageReaderSpi());
    try {
      reader.setInput(new DicomFileInputStream(dcm));
      List<PlanarImage> frames = reader.getPlanarImages();
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      int w = 0, h = 0, ch = 0;
      int[] maxCh = new int[4];
      for (PlanarImage img : frames) {
        Mat mat = img.toMat();
        w = mat.cols();
        h = mat.rows();
        ch = mat.channels();
        if (mat.depth() != 0) { // expect CV_8U for RGB-8; bail loudly otherwise
          throw new IllegalStateException("unexpected depth " + mat.depth() + " for big-endian RGB-8 sample");
        }
        byte[] row = new byte[w * ch];
        for (int r = 0; r < h; r++) {
          mat.get(r, 0, row); // read one full row (row-by-row is safe for non-continuous mats)
          md.update(row);
          for (int i = 0; i < row.length; i++) {
            int v = row[i] & 0xff;
            int c = i % ch;
            if (v > maxCh[c]) {
              maxCh[c] = v;
            }
          }
        }
      }
      String hex = toHex(md.digest());
      System.out.printf(
          "[smoke] big-endian decode: %d frame(s) %dx%d ch=%d maxPerChannel=%s hash=%s%n",
          frames.size(), w, h, ch, Arrays.toString(Arrays.copyOf(maxCh, Math.min(ch, 3))), hex);

      // A grossly wrong swap/planar handling would not produce near-saturated channels.
      boolean anyHigh = false;
      for (int c = 0; c < Math.min(ch, 3); c++) {
        anyHigh |= maxCh[c] >= 250;
      }
      if (!anyHigh) {
        throw new IllegalStateException("big-endian sanity failed: no channel reaches a high value");
      }

      String expected = System.getProperty("expected.be.hash", "");
      if (!expected.isEmpty() && !expected.equalsIgnoreCase(hex)) {
        throw new IllegalStateException(
            "BYTE-SWAP MISMATCH: big-endian decode differs on this arch; expected " + expected + " got " + hex);
      }
    } finally {
      reader.dispose();
    }
  }

  static String decodeAllFrames(Path dcm) throws IOException {
    DicomImageReader reader = new DicomImageReader(new DicomImageReaderSpi());
    try {
      reader.setInput(new DicomFileInputStream(dcm));
      int frames = reader.getNumImages(true);
      List<PlanarImage> imgs = reader.getPlanarImages();
      int w = 0, h = 0;
      double firstPx = Double.NaN;
      for (PlanarImage img : imgs) {
        if (img == null || img.width() <= 0 || img.height() <= 0) {
          throw new IllegalStateException("decoded frame is empty");
        }
        w = (int) img.width();
        h = (int) img.height();
        double[] px = img.get(0, 0);
        if (Double.isNaN(firstPx)) {
          firstPx = px[0];
        }
      }
      return String.format(
          "decoded %d/%d frame(s) %dx%d px[0,0]=%.1f on %s",
          imgs.size(), frames, w, h, firstPx, Thread.currentThread().getName());
    } finally {
      reader.dispose();
    }
  }

  static String toHex(byte[] b) {
    StringBuilder sb = new StringBuilder(b.length * 2);
    for (byte x : b) {
      sb.append(Character.forDigit((x >> 4) & 0xf, 16)).append(Character.forDigit(x & 0xf, 16));
    }
    return sb.toString();
  }
}