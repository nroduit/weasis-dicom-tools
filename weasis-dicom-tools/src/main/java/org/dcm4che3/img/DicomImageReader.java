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
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageReaderSpi;
import org.dcm4che3.data.*;
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
import org.dcm4che3.img.util.PaletteColorUtils;
import org.dcm4che3.img.util.PixelDataUtils;
import org.dcm4che3.img.util.SupplierEx;
import org.dcm4che3.io.BulkDataDescriptor;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.util.TagUtils;
import org.opencv.core.Core.MinMaxLocResult;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.osgi.OpenCVNativeLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.Pair;
import org.weasis.core.util.StreamUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.opencv.op.ImageTransformer;

/**
 * DICOM image reader supporting compressed and uncompressed pixel data using OpenCV.
 *
 * <p>This reader handles all DICOM objects containing pixel data, including:
 *
 * <ul>
 *   <li>Multi-frame images with fragment-based compression
 *   <li>Various photometric interpretations (RGB, YBR, Monochrome, etc.)
 *   <li>Different bit depths and pixel representations
 *   <li>Palette color lookup tables
 *   <li>Modality and VOI LUT transformations
 * </ul>
 *
 * <p>The reader uses OpenCV's native library for efficient image processing and supports both
 * file-based and memory-based DICOM input streams.
 *
 * @author Nicolas Roduit
 * @since Jan 2020
 */
public class DicomImageReader extends ImageReader {

  private static final Logger LOG = LoggerFactory.getLogger(DicomImageReader.class);

  /** DICOM tags that should be treated as bulk data (not loaded into memory) */
  public static final Set<Integer> BULK_TAGS =
      Set.of(
          Tag.PixelDataProviderURL,
          Tag.AudioSampleData,
          Tag.CurveData,
          Tag.SpectroscopyData,
          Tag.RedPaletteColorLookupTableData,
          Tag.GreenPaletteColorLookupTableData,
          Tag.BluePaletteColorLookupTableData,
          Tag.AlphaPaletteColorLookupTableData,
          Tag.LargeRedPaletteColorLookupTableData,
          Tag.LargeGreenPaletteColorLookupTableData,
          Tag.LargeBluePaletteColorLookupTableData,
          Tag.SegmentedRedPaletteColorLookupTableData,
          Tag.SegmentedGreenPaletteColorLookupTableData,
          Tag.SegmentedBluePaletteColorLookupTableData,
          Tag.SegmentedAlphaPaletteColorLookupTableData,
          Tag.OverlayData,
          Tag.EncapsulatedDocument,
          Tag.FloatPixelData,
          Tag.DoubleFloatPixelData,
          Tag.PixelData);

  public static final BulkDataDescriptor BULK_DATA_DESCRIPTOR = DicomImageReader::shouldBeBulkData;

  private static final Map<String, Boolean> series2FloatImages = new ConcurrentHashMap<>();

  private final List<Integer> fragmentsPositions = new ArrayList<>();

  private BytesWithImageDescriptor bdis;
  private DicomFileInputStream dis;

  static {
    new OpenCVNativeLoader().init();
  }

  public DicomImageReader(ImageReaderSpi originatingProvider) {
    super(originatingProvider);
  }

  /**
   * Sets the input source for reading DICOM images.
   *
   * @param input the input source (DicomFileInputStream or BytesWithImageDescriptor)
   * @param seekForwardOnly whether seeking is allowed only forward
   * @param ignoreMetadata whether to ignore metadata
   * @throws IllegalArgumentException if input type is not supported
   */
  @Override
  public void setInput(Object input, boolean seekForwardOnly, boolean ignoreMetadata) {
    resetInternalState();
    if (input instanceof DicomFileInputStream dicomStream) {
      configureFileInputStream(dicomStream, seekForwardOnly, ignoreMetadata);
    } else if (input instanceof BytesWithImageDescriptor bytesDescriptor) {
      this.bdis = bytesDescriptor;
    } else {
      throw new IllegalArgumentException("Unsupported input type: " + input.getClass().getName());
    }
  }

  private void configureFileInputStream(
      DicomFileInputStream dicomStream, boolean seekForwardOnly, boolean ignoreMetadata) {
    super.setInput(dicomStream, seekForwardOnly, ignoreMetadata);
    this.dis = dicomStream;
    dis.setIncludeBulkData(IncludeBulkData.URI);
    dis.setBulkDataDescriptor(BULK_DATA_DESCRIPTOR);
    // Avoid copying pixel data to temporary file
    dis.setURI(dis.getPath().toUri().toString());
  }

  /** Gets the image descriptor containing DICOM image metadata. */
  public ImageDescriptor getImageDescriptor() {
    if (bdis != null) {
      return bdis.getImageDescriptor();
    }
    if (dis == null) {
      throw new IllegalStateException("DicomInputStream is not set");
    }
    return dis.getImageDescriptor();
  }

  /** Returns the number of regular images (frames), excluding overlays. */
  @Override
  public int getNumImages(boolean allowSearch) {
    return getImageDescriptor().getFrames();
  }

  @Override
  public int getWidth(int frameIndex) {
    checkFrameIndex(frameIndex);
    return getImageDescriptor().getColumns();
  }

  @Override
  public int getHeight(int frameIndex) {
    checkFrameIndex(frameIndex);
    return getImageDescriptor().getRows();
  }

  @Override
  public Iterator<ImageTypeSpecifier> getImageTypes(int frameIndex) {
    throw new UnsupportedOperationException("getImageTypes not implemented");
  }

  @Override
  public ImageReadParam getDefaultReadParam() {
    return new DicomImageReadParam();
  }

  /** Gets the stream metadata. May not contain post-pixel data unless specifically requested. */
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
      PlanarImage img = getPlanarImage(frameIndex, ensureDicomParam(param));
      return ImageConversion.toBufferedImage(img).getRaster();
    } catch (Exception e) {
      LOG.error("Error reading raster for frame {}", frameIndex, e);
      return null;
    }
  }

  @Override
  public BufferedImage read(int frameIndex, ImageReadParam param) {
    try {
      PlanarImage img = getPlanarImage(frameIndex, ensureDicomParam(param));
      return ImageConversion.toBufferedImage(img);
    } catch (Exception e) {
      LOG.error("Error reading image for frame {}", frameIndex, e);
      return null;
    }
  }

  /**
   * Creates lazy suppliers for all planar images to enable on-demand loading.
   *
   * @param param read parameters
   * @param editor optional image editor for post-processing
   * @return list of image suppliers
   */
  public List<SupplierEx<PlanarImage, IOException>> getLazyPlanarImages(
      DicomImageReadParam param, Editable<PlanarImage> editor) {
    int frameCount = getImageDescriptor().getFrames();
    return IntStream.range(0, frameCount)
        .mapToObj(i -> createLazyImageSupplier(i, param, editor))
        .toList();
  }

  private SupplierEx<PlanarImage, IOException> createLazyImageSupplier(
      int frameIndex, DicomImageReadParam param, Editable<PlanarImage> editor) {

    SupplierEx<PlanarImage, IOException> baseSupplier =
        () -> {
          var img = getPlanarImage(frameIndex, param);
          if (editor != null) {
            var processedImg = editor.process(img);
            if (!img.equals(processedImg)) {
              img.release(); // Release original if it was replaced
            }
            return processedImg;
          }
          return img;
        };

    // Return memoized version for lazy loading with caching
    return baseSupplier.memoized();
  }

  /** Gets all planar images with default parameters. */
  public List<PlanarImage> getPlanarImages() throws IOException {
    return getPlanarImages(null);
  }

  /** Gets all planar images with specified parameters. */
  public List<PlanarImage> getPlanarImages(DicomImageReadParam param) throws IOException {
    int frameCount = getImageDescriptor().getFrames();
    List<PlanarImage> images = new ArrayList<>(frameCount);

    for (int i = 0; i < frameCount; i++) {
      images.add(getPlanarImage(i, param));
    }
    return images;
  }

  /** Gets the first planar image with default parameters. */
  public PlanarImage getPlanarImage() throws IOException {
    return getPlanarImage(0, null);
  }

  /**
   * Gets a planar image for the specified frame with optional transformations applied.
   *
   * @param frameIndex the frame index to read
   * @param param read parameters for region selection, scaling, etc.
   * @return the processed planar image
   * @throws IOException if reading fails
   */
  public PlanarImage getPlanarImage(int frameIndex, DicomImageReadParam param) throws IOException {
    if (frameIndex < 0 || frameIndex >= getImageDescriptor().getFrames()) {
      return null;
    }
    PlanarImage rawImage = getRawImage(frameIndex, param);
    return applyImageTransformations(rawImage, frameIndex, param);
  }

  private PlanarImage applyImageTransformations(
      PlanarImage rawImage, int frameIndex, DicomImageReadParam param) {
    PlanarImage result = rawImage;

    result = applyPaletteColorLUT(result);
    result = applyRegionTransformations(result, param);
    if (param != null && param.isAllowFloatImageConversion()) {
      result = applyFloatConversion(result, getImageDescriptor(), frameIndex);
    }

    if (!rawImage.equals(result)) {
      rawImage.release();
    }

    return result;
  }

  private PlanarImage applyPaletteColorLUT(PlanarImage image) {
    ImageDescriptor desc = getImageDescriptor();
    if (desc.hasPaletteColorLookupTable()) {
      LookupTableCV paletteColorLUT = desc.getPaletteColorLookupTable();
      return PaletteColorUtils.getRGBImageFromPaletteColorModel(image, paletteColorLUT);
    }
    return image;
  }

  private PlanarImage applyRegionTransformations(PlanarImage image, DicomImageReadParam param) {
    if (param == null) return image;

    PlanarImage result = image;

    // Apply source region cropping
    if (param.getSourceRegion() != null) {
      result = ImageTransformer.crop(result.toMat(), param.getSourceRegion());
      if (!result.equals(image)) image.release();
    }
    // Apply scaling
    if (param.getSourceRenderSize() != null) {
      PlanarImage scaledImage =
          ImageTransformer.scale(
              result.toMat(), param.getSourceRenderSize(), Imgproc.INTER_LANCZOS4);
      if (!scaledImage.equals(result)) result.release();
      result = scaledImage;
    }
    return result;
  }

  private PlanarImage applyFloatConversion(
      PlanarImage image, ImageDescriptor desc, int frameIndex) {
    String seriesUID = desc.getSeriesInstanceUID();
    if (!StringUtil.hasText(seriesUID)) return image;
    Boolean isFloatPixelData = series2FloatImages.get(seriesUID);
    if (isFloatPixelData == Boolean.FALSE) return image;

    PlanarImage result = rangeOutsideLut(image, desc, frameIndex, isFloatPixelData != null);
    series2FloatImages.put(seriesUID, CvType.depth(result.type()) == CvType.CV_32F);

    return result;
  }

  /** Converts image to float if modality LUT values exceed standard ranges. */
  static PlanarImage rangeOutsideLut(
      PlanarImage input, ImageDescriptor desc, int frameIndex, boolean forceFloat) {
    OptionalDouble rescaleSlope = desc.getModalityLutForFrame(frameIndex).getRescaleSlope();
    if (!forceFloat && rescaleSlope.isEmpty()) return input;
    double slope = rescaleSlope.orElse(1.0);
    double intercept = desc.getModalityLutForFrame(frameIndex).getRescaleIntercept().orElse(0.0);
    MinMaxLocResult minMax = DicomImageAdapter.getMinMaxValues(input, desc, frameIndex);
    Pair<Double, Double> rescale = getRescaleSlopeAndIntercept(slope, intercept, minMax);
    if (forceFloat || slope < 0.5 || rangeOutsideLut(rescale, desc)) {
      return convertToFloatImage(input, slope, intercept, rescale, desc);
    }

    return input;
  }

  private static PlanarImage convertToFloatImage(
      PlanarImage input,
      double slope,
      double intercept,
      Pair<Double, Double> rescale,
      ImageDescriptor desc) {
    ImageCV dstImg = new ImageCV();
    boolean invertLUT =
        desc.getPhotometricInterpretation() == PhotometricInterpretation.MONOCHROME1;
    double alpha = slope;
    double beta = intercept;
    if (invertLUT) {
      alpha = -slope;
      beta = rescale.second() + rescale.first() - intercept;
    }
    input.toImageCV().convertTo(dstImg, CvType.CV_32F, alpha, beta);
    return dstImg;
  }

  private static boolean rangeOutsideLut(Pair<Double, Double> rescale, ImageDescriptor desc) {
    boolean outputSigned = rescale.first() < 0 || desc.isSigned();
    Pair<Double, Double> minMax = PixelDataUtils.getMinMax(desc.getBitsAllocated(), outputSigned);
    return rescale.first() + 1 < minMax.first() || rescale.second() - 1 > minMax.second();
  }

  private static Pair<Double, Double> getRescaleSlopeAndIntercept(
      double slope, double intercept, MinMaxLocResult minMax) {
    double min = minMax.minVal * slope + intercept;
    double max = minMax.maxVal * slope + intercept;
    return new Pair<>(Math.min(min, max), Math.max(min, max));
  }

  /** Gets the raw image data without transformations. */
  public PlanarImage getRawImage(int frameIndex, DicomImageReadParam param) throws IOException {
    return dis != null
        ? getRawImageFromFile(frameIndex, param)
        : getRawImageFromBytes(frameIndex, param);
  }

  /** Reads raw image data from file-based DICOM stream. */
  protected PlanarImage getRawImageFromFile(int frameIndex, DicomImageReadParam param)
      throws IOException {
    if (dis == null) {
      throw new IOException("No DicomInputStream available");
    }
    Attributes dcm = dis.getMetadata().getDicomObject();
    PixelDataInfo pixelInfo = extractPixelDataInfo(dcm);
    validatePixelData(pixelInfo);

    ExtendSegmentedInputImageStream segmentedStream =
        buildSegmentedImageInputStream(frameIndex, pixelInfo.fragments, pixelInfo.bulkData);

    return readImageFromSegments(segmentedStream, frameIndex, pixelInfo, param);
  }

  protected PlanarImage getRawImageFromBytes(int frameIndex, DicomImageReadParam param)
      throws IOException {
    if (bdis == null) {
      throw new IOException("No BytesWithImageDescriptor available");
    }

    ImageDescriptor desc = getImageDescriptor();
    String tsuid = bdis.getTransferSyntax();
    int dcmFlags = buildBytesDecodingFlags(tsuid, desc, frameIndex, param);

    var buffer = createMatFromBytes(bdis.getBytes(frameIndex));
    TransferSyntaxType type = TransferSyntaxType.forUID(tsuid);
    boolean isRawData = type == TransferSyntaxType.NATIVE || type == TransferSyntaxType.RLE;

    ImageCV imageCV =
        isRawData
            ? readRawBytesData(buffer, dcmFlags, desc)
            : readCompressedBytesData(buffer, dcmFlags);

    return applyReleaseImageAfterProcessing(imageCV, param);
  }

  // ... existing code ...
  /** Container for pixel data information extracted from DICOM attributes. */
  private record PixelDataInfo(
      Object pixelData,
      Fragments fragments,
      BulkData bulkData,
      boolean bigEndian,
      boolean floatPixelData,
      Holder pixelDataVR) {}

  private PixelDataInfo extractPixelDataInfo(Attributes dcm) {
    Holder pixelDataVR = new Holder();
    Object pixelData = null;
    boolean floatPixelData = false;

    // Try different pixel data tags in order of preference
    var tags = List.of(Tag.PixelData, Tag.FloatPixelData, Tag.DoubleFloatPixelData);
    for (int tag : tags) {
      pixelData = dcm.getValue(tag, pixelDataVR);
      if (pixelData != null) {
        floatPixelData = tag != Tag.PixelData;
        break;
      }
    }

    Fragments fragments = null;
    BulkData bulkData = null;
    boolean bigEndian = false;

    // Determine data structure and endianness
    if (pixelData instanceof BulkData b) {
      bulkData = b;
      bigEndian = bulkData.bigEndian();
    } else if (pixelData instanceof Fragments f) {
      fragments = f;
      bigEndian = fragments.bigEndian();
    }

    return new PixelDataInfo(
        pixelData, fragments, bulkData, bigEndian, floatPixelData, pixelDataVR);
  }

  private void validatePixelData(PixelDataInfo pixelInfo) {
    if (pixelInfo.pixelData == null || getImageDescriptor().getBitsStored() < 1) {
      throw new IllegalStateException("No valid pixel data in DICOM object");
    }
  }

  private PlanarImage readImageFromSegments(
      ExtendSegmentedInputImageStream segmentedStream,
      int frameIndex,
      PixelDataInfo pixelInfo,
      DicomImageReadParam param)
      throws IOException {

    if (segmentedStream.getSegmentCount() <= 0) {
      return null;
    }

    frameIndex = Math.min(frameIndex, segmentedStream.getSegmentCount() - 1);

    String tsuid = dis.getMetadata().getTransferSyntaxUID();
    int dcmFlags =
        buildDecodingFlags(
            tsuid, getImageDescriptor(), pixelInfo, segmentedStream, frameIndex, param);

    var positions = createMatOfDouble(segmentedStream.segmentPositions());
    var lengths = createMatOfDouble(segmentedStream.segmentLengths());
    TransferSyntaxType type = TransferSyntaxType.forUID(tsuid);
    boolean isRawData =
        pixelInfo.fragments == null
            || type == TransferSyntaxType.NATIVE
            || type == TransferSyntaxType.RLE;

    ImageCV imageCV =
        isRawData
            ? readRawImageData(
                segmentedStream, dcmFlags, getImageDescriptor(), pixelInfo, positions, lengths)
            : readCompressedImageData(segmentedStream, dcmFlags, positions, lengths);

    return applyReleaseImageAfterProcessing(imageCV, param);
  }

  private ImageCV readRawImageData(
      ExtendSegmentedInputImageStream segmentedStream,
      int dcmFlags,
      ImageDescriptor desc,
      PixelDataInfo pixelInfo,
      MatOfDouble positions,
      MatOfDouble lengths) {
    int bitsStored = desc.getBitsStored();
    int bits = (bitsStored <= 8 && desc.getBitsAllocated() > 8) ? 9 : bitsStored;
    int streamVR = pixelInfo.pixelDataVR.vr.numEndianBytes();

    var dicomParams =
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

    return ImageCV.fromMat(
        Imgcodecs.dicomRawFileRead(
            segmentedStream.path().toString(),
            positions,
            lengths,
            dicomParams,
            desc.getPhotometricInterpretation().name()));
  }

  private ImageCV readCompressedImageData(
      ExtendSegmentedInputImageStream segmentedStream,
      int dcmFlags,
      MatOfDouble positions,
      MatOfDouble lengths) {
    return ImageCV.fromMat(
        Imgcodecs.dicomJpgFileRead(
            segmentedStream.path().toString(),
            positions,
            lengths,
            dcmFlags,
            Imgcodecs.IMREAD_UNCHANGED));
  }

  private int buildDecodingFlags(
      String tsuid,
      ImageDescriptor desc,
      PixelDataInfo pixelInfo,
      ExtendSegmentedInputImageStream segmentedStream,
      int frameIndex,
      DicomImageReadParam param) {
    TransferSyntaxType type = TransferSyntaxType.forUID(tsuid);
    PhotometricInterpretation pmi = desc.getPhotometricInterpretation();

    int dcmFlags =
        (type.canEncodeSigned() && desc.isSigned())
            ? Imgcodecs.DICOM_FLAG_SIGNED
            : Imgcodecs.DICOM_FLAG_UNSIGNED;

    boolean rawData =
        pixelInfo.fragments == null
            || type == TransferSyntaxType.NATIVE
            || type == TransferSyntaxType.RLE;
    if (!rawData && shouldConvertYbr2Rgb(pmi, tsuid, segmentedStream, frameIndex, param)) {
      dcmFlags |= Imgcodecs.DICOM_FLAG_YBR;
      if (type == TransferSyntaxType.JPEG_LS) {
        dcmFlags |= Imgcodecs.DICOM_FLAG_FORCE_RGB_CONVERSION;
      }
    }

    if (pixelInfo.bigEndian) dcmFlags |= Imgcodecs.DICOM_FLAG_BIGENDIAN;
    if (pixelInfo.floatPixelData) dcmFlags |= Imgcodecs.DICOM_FLAG_FLOAT;
    if (UID.RLELossless.equals(tsuid)) dcmFlags |= Imgcodecs.DICOM_FLAG_RLE;

    return dcmFlags;
  }

  private ImageCV readRawBytesData(Mat buffer, int dcmFlags, ImageDescriptor desc) {
    int bitsStored = desc.getBitsStored();
    int bits = (bitsStored <= 8 && desc.getBitsAllocated() > 8) ? 9 : bitsStored;
    int streamVR = bdis.getPixelDataVR().numEndianBytes();

    var dicomParams =
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

    return ImageCV.fromMat(
        Imgcodecs.dicomRawMatRead(buffer, dicomParams, desc.getPhotometricInterpretation().name()));
  }

  private ImageCV readCompressedBytesData(Mat buffer, int dcmFlags) {
    return ImageCV.fromMat(Imgcodecs.dicomJpgMatRead(buffer, dcmFlags, Imgcodecs.IMREAD_UNCHANGED));
  }

  private int buildBytesDecodingFlags(
      String tsuid, ImageDescriptor desc, int frameIndex, DicomImageReadParam param) {
    TransferSyntaxType type = TransferSyntaxType.forUID(tsuid);
    PhotometricInterpretation pmi = desc.getPhotometricInterpretation();
    int dcmFlags =
        (type.canEncodeSigned() && desc.isSigned())
            ? Imgcodecs.DICOM_FLAG_SIGNED
            : Imgcodecs.DICOM_FLAG_UNSIGNED;
    boolean rawData = type == TransferSyntaxType.NATIVE || type == TransferSyntaxType.RLE;
    if (!rawData && shouldConvertYbr2Rgb(pmi, tsuid, frameIndex, param)) {
      dcmFlags |= Imgcodecs.DICOM_FLAG_YBR;
      if (type == TransferSyntaxType.JPEG_LS) {
        dcmFlags |= Imgcodecs.DICOM_FLAG_FORCE_RGB_CONVERSION;
      }
    }
    if (bdis.isBigEndian()) dcmFlags |= Imgcodecs.DICOM_FLAG_BIGENDIAN;
    if (bdis.isFloatPixelData()) dcmFlags |= Imgcodecs.DICOM_FLAG_FLOAT;
    if (UID.RLELossless.equals(tsuid)) dcmFlags |= Imgcodecs.DICOM_FLAG_RLE;

    return dcmFlags;
  }

  private boolean shouldConvertYbr2Rgb(
      PhotometricInterpretation pmi,
      String tsuid,
      ExtendSegmentedInputImageStream segmentedStream,
      int frameIndex,
      DicomImageReadParam param) {
    BooleanSupplier isYbrModel =
        () -> {
          try (SeekableByteChannel channel =
              Files.newByteChannel(dis.getPath(), StandardOpenOption.READ)) {
            channel.position(segmentedStream.segmentPositions()[frameIndex]);
            return isYbrModel(channel, pmi, param);
          } catch (IOException e) {
            LOG.error("Cannot read JPEG header", e);
            return false;
          }
        };
    return shouldConvertYbr2Rgb(pmi, tsuid, isYbrModel);
  }

  private boolean shouldConvertYbr2Rgb(
      PhotometricInterpretation pmi, String tsuid, int frameIndex, DicomImageReadParam param) {
    BooleanSupplier isYbrModel =
        () -> {
          try (var channel = new SeekableInMemoryByteChannel(bdis.getBytes(frameIndex).array())) {
            return isYbrModel(channel, pmi, param);
          } catch (Exception e) {
            LOG.error("Cannot read JPEG header", e);
            return false;
          }
        };
    return shouldConvertYbr2Rgb(pmi, tsuid, isYbrModel);
  }

  private static boolean isYbrModel(
      SeekableByteChannel channel, PhotometricInterpretation pmi, DicomImageReadParam param)
      throws IOException {
    JPEGParser parser = new JPEGParser(channel);
    String tsuid = null;
    try {
      tsuid = parser.getTransferSyntaxUID();
    } catch (XPEGParserException e) {
      LOG.warn("Cannot parse JPEG type", e);
    }

    if (tsuid != null && !TransferSyntaxType.isLossyCompression(tsuid)) {
      return false;
    }
    boolean keepRgbForLossyJpeg =
        param != null && param.getKeepRgbForLossyJpeg().orElse(Boolean.FALSE);
    if (pmi == PhotometricInterpretation.RGB && !keepRgbForLossyJpeg) {
      // Force YBR color model for JPEG Baseline when RGB has JFIF header or incorrect components
      return !"RGB".equals(parser.getParams().colorPhotometricInterpretation());
    }
    return false;
  }

  private static boolean shouldConvertYbr2Rgb(
      PhotometricInterpretation pmi, String tsuid, BooleanSupplier isYbrModel) {
    return switch (pmi) {
      case MONOCHROME1, MONOCHROME2, PALETTE_COLOR, YBR_ICT, YBR_RCT -> false;
      default ->
          switch (tsuid) {
            // Only apply for IJG native decoder
            case UID.JPEGBaseline8Bit,
                UID.JPEGExtended12Bit,
                UID.JPEGSpectralSelectionNonHierarchical68,
                UID.JPEGFullProgressionNonHierarchical1012 ->
                pmi != PhotometricInterpretation.RGB || isYbrModel.getAsBoolean();
            default -> pmi.name().startsWith("YBR");
          };
    };
  }

  /** Builds segmented input stream for frame data access. */
  private ExtendSegmentedInputImageStream buildSegmentedImageInputStream(
      int frameIndex, Fragments fragments, BulkData bulkData) throws IOException {
    ImageDescriptor desc = getImageDescriptor();
    if (bulkData != null && fragments == null) {
      return buildBulkDataStream(frameIndex, bulkData, desc);
    } else if (fragments != null) {
      return buildFragmentedStream(frameIndex, fragments, desc);
    } else {
      throw new IOException("Neither fragments nor BulkData available");
    }
  }

  private ExtendSegmentedInputImageStream buildBulkDataStream(
      int frameIndex, BulkData bulkData, ImageDescriptor desc) {
    int frameLength =
        desc.getPhotometricInterpretation()
            .frameLength(
                desc.getColumns(), desc.getRows(), desc.getSamples(), desc.getBitsAllocated());

    long[] offsets = {bulkData.offset() + (long) frameIndex * frameLength};
    int[] lengths = {frameLength};

    return new ExtendSegmentedInputImageStream(dis.getPath(), offsets, lengths, desc);
  }

  private ExtendSegmentedInputImageStream buildFragmentedStream(
      int frameIndex, Fragments fragments, ImageDescriptor desc) throws IOException {
    int fragmentCount = fragments.size();
    int frameCount = desc.getFrames();

    return frameCount >= fragmentCount - 1
        ? buildSingleFragmentStream(frameIndex, fragments, fragmentCount)
        : buildMultiFragmentStream(frameIndex, fragments, frameCount, fragmentCount);
  }

  private ExtendSegmentedInputImageStream buildSingleFragmentStream(
      int frameIndex, Fragments fragments, int fragmentCount) {
    int index = Math.min(frameIndex + 1, fragmentCount - 1);
    BulkData bulkData = (BulkData) fragments.get(index);

    long[] offsets = {bulkData.offset()};
    int[] lengths = {bulkData.length()};

    return new ExtendSegmentedInputImageStream(
        dis.getPath(), offsets, lengths, getImageDescriptor());
  }

  private ExtendSegmentedInputImageStream buildMultiFragmentStream(
      int frameIndex, Fragments fragments, int frameCount, int fragmentCount) throws IOException {

    return frameCount == 1
        ? buildAllFragmentsStream(frameIndex, fragments, fragmentCount)
        : buildPerFrameFragmentStream(frameIndex, fragments, frameCount, fragmentCount);
  }

  private ExtendSegmentedInputImageStream buildAllFragmentsStream(
      int frameIndex, Fragments fragments, int fragmentCount) {
    long[] offsets = new long[fragmentCount - 1];
    int[] lengths = new int[offsets.length];

    for (int i = 0; i < lengths.length; i++) {
      BulkData bulkData = (BulkData) fragments.get(i + frameIndex + 1);
      offsets[i] = bulkData.offset();
      lengths[i] = bulkData.length();
    }

    return new ExtendSegmentedInputImageStream(
        dis.getPath(), offsets, lengths, getImageDescriptor());
  }

  private ExtendSegmentedInputImageStream buildPerFrameFragmentStream(
      int frameIndex, Fragments fragments, int frameCount, int fragmentCount) throws IOException {
    if (fragmentsPositions.isEmpty()) {
      identifyFrameFragments(fragments, fragmentCount);
    }

    if (fragmentsPositions.size() != frameCount) {
      throw new IOException(
          "Cannot match fragments to frames: " + fragmentsPositions.size() + " vs " + frameCount);
    }

    int startFragment = fragmentsPositions.get(frameIndex);
    int endFragment =
        (frameIndex + 1) >= fragmentsPositions.size()
            ? fragmentCount
            : fragmentsPositions.get(frameIndex + 1);

    long[] offsets = new long[endFragment - startFragment];
    int[] lengths = new int[offsets.length];

    for (int i = 0; i < offsets.length; i++) {
      BulkData bulkData = (BulkData) fragments.get(startFragment + i);
      offsets[i] = bulkData.offset();
      lengths[i] = bulkData.length();
    }

    return new ExtendSegmentedInputImageStream(
        dis.getPath(), offsets, lengths, getImageDescriptor());
  }

  private void identifyFrameFragments(Fragments fragments, int fragmentCount) throws IOException {
    Path path = dis.getPath();
    try (SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ)) {
      for (int i = 1; i < fragmentCount; i++) {
        BulkData bulkData = (BulkData) fragments.get(i);
        channel.position(bulkData.offset());
        try {
          new JPEGParser(channel);
          fragmentsPositions.add(i);
        } catch (Exception e) {
          // Not a JPEG stream - skip
        }
      }
    }
  }

  protected DicomImageReadParam ensureDicomParam(ImageReadParam param) {
    if (param instanceof DicomImageReadParam dicomParam) {
      return dicomParam;
    }
    return param == null ? new DicomImageReadParam() : new DicomImageReadParam(param);
  }

  private void resetInternalState() {
    StreamUtil.safeClose(dis);
    dis = null;
    bdis = null;
    fragmentsPositions.clear();
  }

  private void checkFrameIndex(int frameIndex) {
    int maxFrames = getImageDescriptor().getFrames();
    if (frameIndex < 0 || frameIndex >= maxFrames) {
      throw new IndexOutOfBoundsException(
          "Frame index %d exceeds available frames (%d)".formatted(frameIndex, maxFrames));
    }
  }

  @Override
  public void dispose() {
    resetInternalState();
  }

  // Static utility methods

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

  public static void closeMat(Mat mat) {
    if (mat != null) {
      mat.release();
    }
  }

  /** Checks if the transfer syntax is supported by this reader. */
  public static boolean isSupportedSyntax(String uid) {
    return switch (uid) {
      case UID.ImplicitVRLittleEndian,
          UID.ExplicitVRLittleEndian,
          UID.ExplicitVRBigEndian,
          UID.RLELossless,
          UID.JPEGBaseline8Bit,
          UID.JPEGExtended12Bit,
          UID.JPEGSpectralSelectionNonHierarchical68,
          UID.JPEGFullProgressionNonHierarchical1012,
          UID.JPEGLossless,
          UID.JPEGLosslessSV1,
          UID.JPEGLSLossless,
          UID.JPEGLSNearLossless,
          UID.JPEG2000Lossless,
          UID.JPEG2000,
          UID.JPEG2000MCLossless,
          UID.JPEG2000MC,
          UID.JPEGXL,
          UID.JPEGXLLossless,
          UID.JPEGXLJPEGRecompression ->
          true;
      default -> false;
    };
  }

  /**
   * Adds a series to the cache for float image conversion.
   *
   * @param seriesInstanceUID the series instance UID
   * @param forceToFloatImages true to force conversion to float images
   */
  public static void addSeriesToFloatImages(String seriesInstanceUID, Boolean forceToFloatImages) {
    series2FloatImages.put(seriesInstanceUID, forceToFloatImages);
  }

  /**
   * Checks if a series is marked for float image conversion.
   *
   * @param seriesInstanceUID the series instance UID
   * @return true if the series is marked for float conversion, false otherwise
   */
  public static Boolean getForceToFloatImages(String seriesInstanceUID) {
    return series2FloatImages.get(seriesInstanceUID);
  }

  /**
   * Removes a series from the float conversion cache.
   *
   * <p><strong>Note:</strong> Call when series is disposed to prevent memory leaks.
   */
  public static void removeSeriesToFloatImages(String seriesInstanceUID) {
    series2FloatImages.remove(seriesInstanceUID);
  }

  // Private utility methods for resource management

  private static boolean shouldBeBulkData(
      List<ItemPointer> itemPointer,
      String privateCreator,
      int tag,
      org.dcm4che3.data.VR vr,
      long length) {
    int tagNormalized = TagUtils.normalizeRepeatingGroup(tag);

    if (tagNormalized == Tag.WaveformData) {
      return itemPointer.size() == 1 && itemPointer.get(0).sequenceTag == Tag.WaveformSequence;
    }

    if (BULK_TAGS.contains(tagNormalized)) {
      return itemPointer.isEmpty();
    }

    if (TagUtils.isPrivateTag(tag)) {
      return length > 1000; // Don't read private values larger than 1KB into memory
    }

    return switch (vr) {
      case OB, OD, OF, OL, OW, UN -> length > 64;
      default -> false;
    };
  }

  private static Mat createMatFromBytes(ByteBuffer byteBuffer) {
    Mat buffer = new Mat(1, byteBuffer.limit(), CvType.CV_8UC1);
    buffer.put(0, 0, byteBuffer.array());
    return buffer;
  }

  private static MatOfDouble createMatOfDouble(long[] values) {
    return new MatOfDouble(Arrays.stream(values).asDoubleStream().toArray());
  }

  private static MatOfDouble createMatOfDouble(int[] values) {
    return new MatOfDouble(Arrays.stream(values).asDoubleStream().toArray());
  }
}
