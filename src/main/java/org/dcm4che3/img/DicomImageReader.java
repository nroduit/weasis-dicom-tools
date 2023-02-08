/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageReaderSpi;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Fragments;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR.Holder;
import org.dcm4che3.image.PhotometricInterpretation;
import org.dcm4che3.imageio.codec.TransferSyntaxType;
import org.dcm4che3.imageio.codec.XPEGParserException;
import org.dcm4che3.imageio.codec.jpeg.JPEGParser;
import org.dcm4che3.img.stream.BytesWithImageDescriptor;
import org.dcm4che3.img.stream.DicomFileInputStream;
import org.dcm4che3.img.stream.ExtendSegmentedInputImageStream;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.dcm4che3.img.stream.SeekableInMemoryByteChannel;
import org.dcm4che3.img.util.Editable;
import org.dcm4che3.img.util.SupplierEx;
import org.dcm4che3.io.BulkDataDescriptor;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.util.TagUtils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.osgi.OpenCVNativeLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.FileUtil;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.opencv.op.ImageProcessor;

/**
 * Reads image data from a DICOM object.
 *
 * <p>Supports all the DICOM objects containing pixel data. Use the OpenCV native library to read
 * compressed and uncompressed pixel data.
 *
 * @author Nicolas Roduit
 * @since Jan 2020
 */
public class DicomImageReader extends ImageReader {

  private static final Logger LOG = LoggerFactory.getLogger(DicomImageReader.class);

  static {
    // Load the native OpenCV library
    OpenCVNativeLoader loader = new OpenCVNativeLoader();
    loader.init();
  }

  public static final BulkDataDescriptor BULKDATA_DESCRIPTOR =
      (itemPointer, privateCreator, tag, vr, length) -> {
        switch (TagUtils.normalizeRepeatingGroup(tag)) {
          case Tag.PixelDataProviderURL:
          case Tag.AudioSampleData:
          case Tag.CurveData:
          case Tag.SpectroscopyData:
          case Tag.RedPaletteColorLookupTableData:
          case Tag.GreenPaletteColorLookupTableData:
          case Tag.BluePaletteColorLookupTableData:
          case Tag.AlphaPaletteColorLookupTableData:
          case Tag.LargeRedPaletteColorLookupTableData:
          case Tag.LargeGreenPaletteColorLookupTableData:
          case Tag.LargeBluePaletteColorLookupTableData:
          case Tag.SegmentedRedPaletteColorLookupTableData:
          case Tag.SegmentedGreenPaletteColorLookupTableData:
          case Tag.SegmentedBluePaletteColorLookupTableData:
          case Tag.SegmentedAlphaPaletteColorLookupTableData:
          case Tag.OverlayData:
          case Tag.EncapsulatedDocument:
          case Tag.FloatPixelData:
          case Tag.DoubleFloatPixelData:
          case Tag.PixelData:
            return itemPointer.isEmpty();
          case Tag.WaveformData:
            return itemPointer.size() == 1
                && itemPointer.get(0).sequenceTag == Tag.WaveformSequence;
        }
        if (TagUtils.isPrivateTag(tag)) {
          return length > 1000; // Do no read in memory private value more than 1 KB
        }

        switch (vr) {
          case OB:
          case OD:
          case OF:
          case OL:
          case OW:
          case UN:
            return length > 64;
        }
        return false;
      };

  private final ArrayList<Integer> fragmentsPositions = new ArrayList<>();

  private BytesWithImageDescriptor bdis;
  private DicomFileInputStream dis;

  public DicomImageReader(ImageReaderSpi originatingProvider) {
    super(originatingProvider);
  }

  @Override
  public void setInput(Object input, boolean seekForwardOnly, boolean ignoreMetadata) {
    resetInternalState();
    if (input instanceof DicomFileInputStream) {
      super.setInput(input, seekForwardOnly, ignoreMetadata);
      this.dis = (DicomFileInputStream) input;
      dis.setIncludeBulkData(IncludeBulkData.URI);
      dis.setBulkDataDescriptor(BULKDATA_DESCRIPTOR);
      // avoid a copy of pixeldata into temporary file
      dis.setURI(dis.getPath().toUri().toString());
    } else if (input instanceof BytesWithImageDescriptor) {
      this.bdis = (BytesWithImageDescriptor) input;
    } else {
      throw new IllegalArgumentException("Unsupported inputStream: " + input.getClass().getName());
    }
  }

  public ImageDescriptor getImageDescriptor() {
    if (bdis != null) return bdis.getImageDescriptor();
    return dis.getImageDescriptor();
  }

  /** Returns the number of regular images in the study. This excludes overlays. */
  @Override
  public int getNumImages(boolean allowSearch) {
    return getImageDescriptor().getFrames();
  }

  @Override
  public int getWidth(int frameIndex) {
    checkIndex(frameIndex);
    return getImageDescriptor().getColumns();
  }

  @Override
  public int getHeight(int frameIndex) {
    checkIndex(frameIndex);
    return getImageDescriptor().getRows();
  }

  @Override
  public Iterator<ImageTypeSpecifier> getImageTypes(int frameIndex) {
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public ImageReadParam getDefaultReadParam() {
    return new DicomImageReadParam();
  }

  /**
   * Gets the stream metadata. May not contain post pixel data unless there are no images or the
   * getStreamMetadata has been called with the post pixel data node being specified.
   */
  @Override
  public DicomMetaData getStreamMetadata() throws IOException {
    return dis == null ? null : dis.getMetadata();
  }

  @Override
  public IIOMetadata getImageMetadata(int frameIndex) {
    return null;
  }

  @Override
  public boolean canReadRaster() {
    return true;
  }

  @Override
  public Raster readRaster(int frameIndex, ImageReadParam param) {
    try {
      PlanarImage img = getPlanarImage(frameIndex, getDefaultReadParam(param));
      return ImageConversion.toBufferedImage(img).getRaster();
    } catch (Exception e) {
      LOG.error("Reading image", e);
      return null;
    }
  }

  @Override
  public BufferedImage read(int frameIndex, ImageReadParam param) {
    try {
      PlanarImage img = getPlanarImage(frameIndex, getDefaultReadParam(param));
      return ImageConversion.toBufferedImage(img);
    } catch (Exception e) {
      LOG.error("Reading image", e);
      return null;
    }
  }

  protected DicomImageReadParam getDefaultReadParam(ImageReadParam param) {
    DicomImageReadParam dcmParam;
    if (param instanceof DicomImageReadParam) {
      dcmParam = (DicomImageReadParam) param;
    } else {
      dcmParam = new DicomImageReadParam(param);
    }
    return dcmParam;
  }

  private void resetInternalState() {
    FileUtil.safeClose(dis);
    dis = null;
    bdis = null;
    fragmentsPositions.clear();
  }

  private void checkIndex(int frameIndex) {
    if (frameIndex < 0 || frameIndex >= getImageDescriptor().getFrames())
      throw new IndexOutOfBoundsException("imageIndex: " + frameIndex);
  }

  @Override
  public void dispose() {
    resetInternalState();
  }

  private boolean fileYbr2rgb(
      PhotometricInterpretation pmi,
      String tsuid,
      ExtendSegmentedInputImageStream seg,
      int frame,
      DicomImageReadParam param) {
    BooleanSupplier isYbrModel =
        () -> {
          try (SeekableByteChannel channel =
              Files.newByteChannel(dis.getPath(), StandardOpenOption.READ)) {
            channel.position(seg.getSegmentPositions()[frame]);
            return isYbrModel(channel, pmi, param);
          } catch (IOException e) {
            LOG.error("Cannot read jpeg header", e);
          }
          return false;
        };
    return ybr2rgb(pmi, tsuid, isYbrModel);
  }

  private static boolean isYbrModel(
      SeekableByteChannel channel, PhotometricInterpretation pmi, DicomImageReadParam param)
      throws IOException {
    JPEGParser parser = new JPEGParser(channel);
    String tsuid = null;
    try {
      tsuid = parser.getTransferSyntaxUID();
    } catch (XPEGParserException e) {
      LOG.warn("Cannot parse jpeg type", e);
    }
    if (tsuid != null && !TransferSyntaxType.isLossyCompression(tsuid)) {
      return false;
    }
    boolean keepRgbForLossyJpeg;
    if (param == null) {
      keepRgbForLossyJpeg = false;
    } else {
      keepRgbForLossyJpeg = param.getKeepRgbForLossyJpeg().orElse(false);
    }

    if (pmi == PhotometricInterpretation.RGB && !keepRgbForLossyJpeg) {
      // Force JPEG Baseline (1.2.840.10008.1.2.4.50) to YBR_FULL_422 color model when RGB with
      // JFIF header or not RGB components (error made by some constructors).
      return !"RGB".equals(parser.getParams().colorPhotometricInterpretation());
    }
    return false;
  }

  private boolean byteYbr2rgb(
      PhotometricInterpretation pmi, String tsuid, int frame, DicomImageReadParam param) {
    BooleanSupplier isYbrModel =
        () -> {
          try (SeekableInMemoryByteChannel channel =
              new SeekableInMemoryByteChannel(bdis.getBytes(frame).array())) {
            return isYbrModel(channel, pmi, param);
          } catch (Exception e) {
            LOG.error("Cannot read jpeg header", e);
          }
          return false;
        };
    return ybr2rgb(pmi, tsuid, isYbrModel);
  }

  private static boolean ybr2rgb(
      PhotometricInterpretation pmi, String tsuid, BooleanSupplier isYbrModel) {
    // Option only for IJG native decoder
    switch (pmi) {
      case MONOCHROME1:
      case MONOCHROME2:
      case PALETTE_COLOR:
      case YBR_ICT:
      case YBR_RCT:
        return false;
      default:
        break;
    }

    switch (tsuid) {
      case UID.JPEGBaseline8Bit:
      case UID.JPEGExtended12Bit:
      case UID.JPEGSpectralSelectionNonHierarchical68:
      case UID.JPEGFullProgressionNonHierarchical1012:
        if (pmi == PhotometricInterpretation.RGB) {
          return isYbrModel.getAsBoolean();
        }
        return true;
      default:
        return pmi.name().startsWith("YBR");
    }
  }

  public List<SupplierEx<PlanarImage, IOException>> getLazyPlanarImages(
      DicomImageReadParam param, Editable<PlanarImage> editor) {
    int size = getImageDescriptor().getFrames();
    List<SupplierEx<PlanarImage, IOException>> suppliers = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      final int index = i;
      suppliers.add(
          new SupplierEx<>() {
            SupplierEx<PlanarImage, IOException> delegate = this::firstTime;
            boolean initialized;

            public PlanarImage get() throws IOException {
              return delegate.get();
            }

            private synchronized PlanarImage firstTime() throws IOException {
              if (!initialized) {
                PlanarImage img = getPlanarImage(index, param);
                PlanarImage value;
                if (editor == null) {
                  value = img;
                } else {
                  value = editor.process(img);
                  img.release();
                }
                delegate = () -> value;
                initialized = true;
              }
              return delegate.get();
            }
          });
    }
    return suppliers;
  }

  public List<PlanarImage> getPlanarImages() throws IOException {
    return getPlanarImages(null);
  }

  public List<PlanarImage> getPlanarImages(DicomImageReadParam param) throws IOException {
    List<PlanarImage> list = new ArrayList<>();
    for (int i = 0; i < getImageDescriptor().getFrames(); i++) {
      list.add(getPlanarImage(i, param));
    }
    return list;
  }

  public PlanarImage getPlanarImage() throws IOException {
    return getPlanarImage(0, null);
  }

  public PlanarImage getPlanarImage(int frame, DicomImageReadParam param) throws IOException {
    PlanarImage img = getRawImage(frame, param);
    PlanarImage out = img;
    if (getImageDescriptor().hasPaletteColorLookupTable()) {
      if (dis == null) {
        out =
            DicomImageUtils.getRGBImageFromPaletteColorModel(
                out, bdis.getPaletteColorLookupTable());
      } else {
        out =
            DicomImageUtils.getRGBImageFromPaletteColorModel(
                out, dis.getMetadata().getDicomObject());
      }
    }
    if (param != null && param.getSourceRegion() != null) {
      out = ImageProcessor.crop(out.toMat(), param.getSourceRegion());
    }
    if (param != null && param.getSourceRenderSize() != null) {
      out = ImageProcessor.scale(out.toMat(), param.getSourceRenderSize(), Imgproc.INTER_LANCZOS4);
    }
    if (!img.equals(out)) {
      img.release();
    }
    return out;
  }

  public PlanarImage getRawImage(int frame, DicomImageReadParam param) throws IOException {
    if (dis == null) {
      return getRawImageFromBytes(frame, param);
    } else {
      return getRawImageFromFile(frame, param);
    }
  }

  protected PlanarImage getRawImageFromFile(int frame, DicomImageReadParam param)
      throws IOException {
    if (dis == null) {
      throw new IOException("No DicomInputStream found");
    }
    Attributes dcm = dis.getMetadata().getDicomObject();
    boolean floatPixData = false;
    Holder pixeldataVR = new Holder();
    Object pixdata = dcm.getValue(Tag.PixelData, pixeldataVR);
    if (pixdata == null) {
      pixdata = dcm.getValue(Tag.FloatPixelData, pixeldataVR);
      if (pixdata != null) {
        floatPixData = true;
      }
    }
    if (pixdata == null) {
      pixdata = dcm.getValue(Tag.DoubleFloatPixelData, pixeldataVR);
      if (pixdata != null) {
        floatPixData = true;
      }
    }

    ImageDescriptor desc = getImageDescriptor();
    int bitsStored = desc.getBitsStored();
    if (pixdata == null || bitsStored < 1) {
      throw new IllegalStateException("No pixel data in this DICOM object");
    }

    Fragments pixeldataFragments = null;
    BulkData bulkData = null;
    boolean bigendian = false;
    if (pixdata instanceof BulkData) {
      bulkData = (BulkData) pixdata;
      bigendian = bulkData.bigEndian();
    } else if (dcm.getString(Tag.PixelDataProviderURL) != null) {
      // TODO Handle JPIP
      // always little endian:
      // http://dicom.nema.org/medical/dicom/2017b/output/chtml/part05/sect_A.6.html
    } else if (pixdata instanceof Fragments) {
      pixeldataFragments = (Fragments) pixdata;
      bigendian = pixeldataFragments.bigEndian();
    }

    ExtendSegmentedInputImageStream seg =
        buildSegmentedImageInputStream(frame, pixeldataFragments, bulkData);
    if (seg.getSegmentPositions() == null) {
      return null;
    }

    String tsuid = dis.getMetadata().getTransferSyntaxUID();
    TransferSyntaxType type = TransferSyntaxType.forUID(tsuid);
    PhotometricInterpretation pmi = desc.getPhotometricInterpretation();
    boolean rawData =
        pixeldataFragments == null
            || type == TransferSyntaxType.NATIVE
            || type == TransferSyntaxType.RLE;
    int dcmFlags =
        (type.canEncodeSigned() && desc.isSigned())
            ? Imgcodecs.DICOM_FLAG_SIGNED
            : Imgcodecs.DICOM_FLAG_UNSIGNED;
    if (!rawData && fileYbr2rgb(pmi, tsuid, seg, frame, param)) {
      dcmFlags |= Imgcodecs.DICOM_FLAG_YBR;
      if (type == TransferSyntaxType.JPEG_LS) {
        dcmFlags |= Imgcodecs.DICOM_FLAG_FORCE_RGB_CONVERSION;
      }
    }
    if (bigendian) {
      dcmFlags |= Imgcodecs.DICOM_FLAG_BIGENDIAN;
    }
    if (floatPixData) {
      dcmFlags |= Imgcodecs.DICOM_FLAG_FLOAT;
    }
    if (UID.RLELossless.equals(tsuid)) {
      dcmFlags |= Imgcodecs.DICOM_FLAG_RLE;
    }

    MatOfDouble positions = null;
    MatOfDouble lengths = null;
    try {
      positions =
          new MatOfDouble(Arrays.stream(seg.getSegmentPositions()).asDoubleStream().toArray());
      lengths = new MatOfDouble(Arrays.stream(seg.getSegmentLengths()).asDoubleStream().toArray());
      if (rawData) {
        int bits = bitsStored <= 8 && desc.getBitsAllocated() > 8 ? 9 : bitsStored;
        int streamVR = pixeldataVR.vr.numEndianBytes();
        MatOfInt dicomparams =
            new MatOfInt(
                Imgcodecs.IMREAD_UNCHANGED,
                dcmFlags,
                desc.getColumns(),
                desc.getRows(),
                Imgcodecs.DICOM_CP_UNKNOWN,
                desc.getSamples(),
                bits,
                desc.isBanded() ? Imgcodecs.ILV_NONE : Imgcodecs.ILV_SAMPLE,
                streamVR);
        ImageCV imageCV =
            ImageCV.toImageCV(
                Imgcodecs.dicomRawFileRead(
                    seg.getPath().toString(), positions, lengths, dicomparams, pmi.name()));
        return applyReleaseImageAfterProcessing(imageCV, param);
      }
      ImageCV imageCV =
          ImageCV.toImageCV(
              Imgcodecs.dicomJpgFileRead(
                  seg.getPath().toString(),
                  positions,
                  lengths,
                  dcmFlags,
                  Imgcodecs.IMREAD_UNCHANGED));
      return applyReleaseImageAfterProcessing(imageCV, param);
    } finally {
      closeMat(positions);
      closeMat(lengths);
    }
  }

  public static ImageCV applyReleaseImageAfterProcessing(
      ImageCV imageCV, DicomImageReadParam param) {
    if (isReleaseImageAfterProcessing(param)) {
      imageCV.setReleasedAfterProcessing(true);
    }
    return imageCV;
  }

  public static boolean isReleaseImageAfterProcessing(DicomImageReadParam param) {
    return param != null && param.getReleaseImageAfterProcessing().orElse(Boolean.FALSE);
  }

  protected PlanarImage getRawImageFromBytes(int frame, DicomImageReadParam param)
      throws IOException {
    if (bdis == null) {
      throw new IOException("No BytesWithImageDescriptor found");
    }

    ImageDescriptor desc = getImageDescriptor();
    int bitsStored = desc.getBitsStored();

    String tsuid = bdis.getTransferSyntax();
    TransferSyntaxType type = TransferSyntaxType.forUID(tsuid);
    PhotometricInterpretation pmi = desc.getPhotometricInterpretation();
    boolean rawData = type == TransferSyntaxType.NATIVE || type == TransferSyntaxType.RLE;
    int dcmFlags =
        (type.canEncodeSigned() && desc.isSigned())
            ? Imgcodecs.DICOM_FLAG_SIGNED
            : Imgcodecs.DICOM_FLAG_UNSIGNED;
    if (!rawData && byteYbr2rgb(pmi, tsuid, frame, param)) {
      dcmFlags |= Imgcodecs.DICOM_FLAG_YBR;
      if (type == TransferSyntaxType.JPEG_LS) {
        dcmFlags |= Imgcodecs.DICOM_FLAG_FORCE_RGB_CONVERSION;
      }
    }
    if (bdis.bigEndian()) {
      dcmFlags |= Imgcodecs.DICOM_FLAG_BIGENDIAN;
    }
    if (bdis.floatPixelData()) {
      dcmFlags |= Imgcodecs.DICOM_FLAG_FLOAT;
    }
    if (UID.RLELossless.equals(tsuid)) {
      dcmFlags |= Imgcodecs.DICOM_FLAG_RLE;
    }

    Mat buf = null;
    try {
      ByteBuffer b = bdis.getBytes(frame);
      buf = new Mat(1, b.limit(), CvType.CV_8UC1);
      buf.put(0, 0, b.array());
      if (rawData) {
        int bits = bitsStored <= 8 && desc.getBitsAllocated() > 8 ? 9 : bitsStored; // Fix #94
        int streamVR = bdis.getPixelDataVR().numEndianBytes();
        MatOfInt dicomparams =
            new MatOfInt(
                Imgcodecs.IMREAD_UNCHANGED,
                dcmFlags,
                desc.getColumns(),
                desc.getRows(),
                Imgcodecs.DICOM_CP_UNKNOWN,
                desc.getSamples(),
                bits,
                desc.isBanded() ? Imgcodecs.ILV_NONE : Imgcodecs.ILV_SAMPLE,
                streamVR);
        ImageCV imageCV =
            ImageCV.toImageCV(Imgcodecs.dicomRawMatRead(buf, dicomparams, pmi.name()));
        return applyReleaseImageAfterProcessing(imageCV, param);
      }
      ImageCV imageCV =
          ImageCV.toImageCV(Imgcodecs.dicomJpgMatRead(buf, dcmFlags, Imgcodecs.IMREAD_UNCHANGED));
      return applyReleaseImageAfterProcessing(imageCV, param);
    } finally {
      closeMat(buf);
    }
  }

  public static void closeMat(Mat mat) {
    if (mat != null) {
      mat.release();
    }
  }

  private ExtendSegmentedInputImageStream buildSegmentedImageInputStream(
      int frameIndex, Fragments fragments, BulkData bulkData) throws IOException {
    long[] offsets;
    int[] length;
    ImageDescriptor desc = getImageDescriptor();
    boolean hasFragments = fragments != null;
    if (!hasFragments && bulkData != null) {
      int frameLength =
          desc.getPhotometricInterpretation()
              .frameLength(
                  desc.getColumns(), desc.getRows(), desc.getSamples(), desc.getBitsAllocated());
      offsets = new long[1];
      length = new int[offsets.length];
      offsets[0] = bulkData.offset() + (long) frameIndex * frameLength;
      length[0] = frameLength;
    } else if (hasFragments) {
      int nbFragments = fragments.size();
      int numberOfFrame = desc.getFrames();

      if (numberOfFrame >= nbFragments - 1) {
        // nbFrames > nbFragments should never happen
        offsets = new long[1];
        length = new int[offsets.length];
        int index = frameIndex < nbFragments - 1 ? frameIndex + 1 : nbFragments - 1;
        BulkData b = (BulkData) fragments.get(index);
        offsets[0] = b.offset();
        length[0] = b.length();
      } else {
        if (numberOfFrame == 1) {
          offsets = new long[nbFragments - 1];
          length = new int[offsets.length];
          for (int i = 0; i < length.length; i++) {
            BulkData b = (BulkData) fragments.get(i + frameIndex + 1);
            offsets[i] = b.offset();
            length[i] = b.length();
          }
        } else {
          // Multi-frames where each frames can have multiple fragments.
          if (fragmentsPositions.isEmpty()) {
            try (SeekableByteChannel channel =
                Files.newByteChannel(dis.getPath(), StandardOpenOption.READ)) {
              for (int i = 1; i < nbFragments; i++) {
                BulkData b = (BulkData) fragments.get(i);
                channel.position(b.offset());
                try {
                  new JPEGParser(channel);
                  fragmentsPositions.add(i);
                } catch (Exception e) {
                  // Not jpeg stream
                }
              }
            }
          }

          if (fragmentsPositions.size() == numberOfFrame) {
            int start = fragmentsPositions.get(frameIndex);
            int end =
                (frameIndex + 1) >= fragmentsPositions.size()
                    ? nbFragments
                    : fragmentsPositions.get(frameIndex + 1);

            offsets = new long[end - start];
            length = new int[offsets.length];
            for (int i = 0; i < offsets.length; i++) {
              BulkData b = (BulkData) fragments.get(start + i);
              offsets[i] = b.offset();
              length[i] = b.length();
            }
          } else {
            throw new IOException("Cannot match all the fragments to all the frames!");
          }
        }
      }
    } else {
      throw new IOException("Neither fragments nor BulkData!");
    }
    return new ExtendSegmentedInputImageStream(dis.getPath(), offsets, length, desc);
  }

  public static boolean isSupportedSyntax(String uid) {
    switch (uid) {
      case UID.ImplicitVRLittleEndian:
      case UID.ExplicitVRLittleEndian:
      case UID.ExplicitVRBigEndian:
      case UID.RLELossless:
      case UID.JPEGBaseline8Bit:
      case UID.JPEGExtended12Bit:
      case UID.JPEGSpectralSelectionNonHierarchical68:
      case UID.JPEGFullProgressionNonHierarchical1012:
      case UID.JPEGLossless:
      case UID.JPEGLosslessSV1:
      case UID.JPEGLSLossless:
      case UID.JPEGLSNearLossless:
      case UID.JPEG2000Lossless:
      case UID.JPEG2000:
      case UID.JPEG2000MCLossless:
      case UID.JPEG2000MC:
        return true;
      default:
        return false;
    }
  }
}
