/*
 * Copyright (c) 2009-2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.stream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Fragments;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.imageio.codec.TransferSyntaxType;
import org.dcm4che3.imageio.codec.jpeg.JPEGParser;
import org.dcm4che3.img.*;
import org.dcm4che3.img.util.DicomUtils;
import org.dcm4che3.img.util.Editable;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.net.DataWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.param.AttributeEditorContext;
import org.weasis.opencv.data.PlanarImage;

/**
 * Utility class for adapting and transcoding DICOM images between different transfer syntaxes.
 * Provides functionality to read, process, and write DICOM files with various compression formats.
 *
 * <p>This class handles:
 *
 * <ul>
 *   <li>Transfer syntax adaptation and validation
 *   <li>Image transcoding between different compression formats
 *   <li>DICOM file writing with proper metadata handling
 *   <li>Data writer creation for streaming operations
 * </ul>
 *
 * @author Nicolas Roduit
 */
public final class ImageAdapter {
  private static final Logger LOGGER = LoggerFactory.getLogger(ImageAdapter.class);

  private static final DicomImageReadParam DICOM_IMAGE_READ_PARAM = createReadParam();
  private static final byte[] EMPTY_BYTES = new byte[0];

  /** Creates configured DICOM read parameters */
  private static DicomImageReadParam createReadParam() {
    var param = new DicomImageReadParam();
    param.setReleaseImageAfterProcessing(true);
    return param;
  }

  /**
   * Configuration for managing transfer syntax adaptation during DICOM image processing.
   * Encapsulates the original transfer syntax, requested syntax, and the final suitable syntax
   * along with compression parameters.
   */
  public static final class AdaptTransferSyntax {
    private final String original;
    private final String requested;
    private String suitable;
    private int jpegQuality = 85;
    private int compressionRatioFactor;

    /**
     * Creates a new transfer syntax adaptation configuration.
     *
     * @param original the original transfer syntax UID
     * @param requested the requested target transfer syntax UID
     * @throws IllegalArgumentException if either parameter is null or empty
     */
    public AdaptTransferSyntax(String original, String requested) {
      if (!StringUtil.hasText(original) || !StringUtil.hasText(requested)) {
        throw new IllegalArgumentException("Non-empty values required for transfer syntax UIDs");
      }
      this.original = original;
      this.requested = requested;
      this.suitable = requested;
    }

    // Getters
    public String getOriginal() {
      return original;
    }

    public String getRequested() {
      return requested;
    }

    public String getSuitable() {
      return suitable;
    }

    public int getJpegQuality() {
      return jpegQuality;
    }

    public int getCompressionRatioFactor() {
      return compressionRatioFactor;
    }

    // Setters
    public void setJpegQuality(int jpegQuality) {
      this.jpegQuality = jpegQuality;
    }

    public void setCompressionRatioFactor(int compressionRatioFactor) {
      this.compressionRatioFactor = compressionRatioFactor;
    }

    /** Sets the suitable transfer syntax if it's valid and supported */
    public void setSuitable(String suitable) {
      if (TransferSyntaxType.forUID(suitable) != TransferSyntaxType.UNKNOWN) {
        this.suitable = suitable;
      }
    }
  }

  private ImageAdapter() {}

  /**
   * Writes a DICOM file with the specified attributes, transfer syntax, and image data.
   *
   * @param data the DICOM attributes to write
   * @param syntax the transfer syntax configuration
   * @param editable the image editing operations to apply
   * @param desc the image descriptor containing pixel data, or null for metadata-only files
   * @param outputPath the output path to write
   * @return true if the file was written successfully, false otherwise
   */
  public static boolean writeDicomFile(
      Attributes data,
      AdaptTransferSyntax syntax,
      Editable<PlanarImage> editable,
      BytesWithImageDescriptor desc,
      Path outputPath) {

    if (outputPath == null) {
      throw new IllegalArgumentException("Output path is required");
    }
    try {
      FileUtil.prepareToWriteFile(outputPath);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return desc == null
        ? writeMetadataOnlyFile(data, syntax, outputPath)
        : writeImageFile(data, syntax, editable, desc, outputPath);
  }

  /** Writes a metadata-only DICOM file */
  private static boolean writeMetadataOnlyFile(
      Attributes data, AdaptTransferSyntax syntax, Path outputPath) {
    adjustSyntaxForMetadataOnly(syntax);
    try (var outputStream = Files.newOutputStream(outputPath);
        var writer = new DicomOutputStream(outputStream, UID.ExplicitVRLittleEndian)) {
      writer.writeDataset(data.createFileMetaInformation(syntax.suitable), data);
      writer.finish();
      return true;
    } catch (Exception e) {
      LOGGER.error("Writing DICOM metadata file to {}", outputPath, e);
      FileUtil.delete(outputPath);
      return false;
    }
  }

  private static void adjustSyntaxForMetadataOnly(AdaptTransferSyntax syntax) {
    if (UID.ImplicitVRLittleEndian.equals(syntax.suitable)
        || UID.ExplicitVRBigEndian.equals(syntax.suitable)) {
      syntax.suitable = UID.ImplicitVRLittleEndian;
    }
  }

  private static boolean writeImageFile(
      Attributes data,
      AdaptTransferSyntax syntax,
      Editable<PlanarImage> editable,
      BytesWithImageDescriptor desc,
      Path outputPath) {

    var reader = new DicomImageReader(Transcoder.dicomImageReaderSpi);
    try {
      var imgData = getDicomOutputData(reader, syntax.requested, desc, editable);
      checkSyntax(syntax, imgData);

      var dataSet = new Attributes(data);
      dataSet.remove(Tag.PixelData);
      String dstTsuid = syntax.suitable;
      try (var outputStream = Files.newOutputStream(outputPath);
          var dos = new DicomOutputStream(outputStream, dstTsuid)) {
        dos.writeFileMetaInformation(dataSet.createFileMetaInformation(dstTsuid));
        writeImage(syntax, desc, imgData, dataSet, dstTsuid, dos);
      }

      return true;
    } catch (IOException e) {
      LOGGER.error("Error getting DICOM output data for {}", outputPath, e);
      return false;
    } catch (Exception e) {
      LOGGER.error("Error transcoding image data for {}", outputPath, e);
      FileUtil.delete(outputPath);
      return false;
    } finally {
      reader.dispose();
    }
  }

  private static void writeImage(
      AdaptTransferSyntax syntax,
      BytesWithImageDescriptor desc,
      DicomOutputData imgData,
      Attributes dataSet,
      String dstTsuid,
      DicomOutputStream dos)
      throws IOException {
    if (DicomOutputData.isNativeSyntax(dstTsuid)) {
      imgData.writeRawImageData(dos, dataSet);
    } else {
      writeCompressedImage(syntax, desc, imgData, dataSet, dstTsuid, dos);
    }
  }

  private static void writeCompressedImage(
      AdaptTransferSyntax syntax,
      BytesWithImageDescriptor desc,
      DicomOutputData imgData,
      Attributes dataSet,
      String dstTsuid,
      DicomOutputStream dos)
      throws IOException {

    var writeParam = DicomJpegWriteParam.buildDicomImageWriteParam(dstTsuid);
    configureCompressionParameters(syntax, writeParam);

    int[] jpegWriteParams =
        imgData.adaptTagsToCompressedImage(
            dataSet, imgData.getFirstImage().get(), desc.getImageDescriptor(), writeParam);
    imgData.writeCompressedImageData(dos, dataSet, jpegWriteParams);
  }

  /** Configures compression parameters for JPEG writing */
  private static void configureCompressionParameters(
      AdaptTransferSyntax syntax, DicomJpegWriteParam params) {
    if (params.getCompressionQuality() > 0 && !UID.JPEGXLLossless.equals(syntax.suitable)) {
      int quality = syntax.getJpegQuality() <= 0 ? 85 : syntax.getJpegQuality();
      params.setCompressionQuality(quality);
    }

    if (params.getCompressionRatioFactor() > 0 && syntax.getCompressionRatioFactor() > 0) {
      params.setCompressionRatioFactor(syntax.getCompressionRatioFactor());
    }
  }

  /**
   * Validates and adjusts the transfer syntax based on actual output capabilities. Updates the
   * suitable syntax if the requested syntax cannot be used.
   */
  public static void checkSyntax(AdaptTransferSyntax syntax, DicomOutputData imgData) {
    if (!syntax.requested.equals(imgData.getTsuid())) {
      syntax.suitable = imgData.getTsuid();
      LOGGER.warn(
          "Transcoding into {} not possible, using {} instead", syntax.requested, syntax.suitable);
    }
  }

  /**
   * Creates a DataWriter for streaming DICOM data with the specified configuration.
   *
   * @param data the DICOM attributes
   * @param syntax the transfer syntax configuration
   * @param editable the image editing operations
   * @param desc the image descriptor, or null for metadata-only writing
   * @return a DataWriter configured for the specified parameters
   * @throws IOException if an error occurs during preparation
   */
  public static DataWriter buildDataWriter(
      Attributes data,
      AdaptTransferSyntax syntax,
      Editable<PlanarImage> editable,
      BytesWithImageDescriptor desc)
      throws IOException {

    return desc == null
        ? createMetadataDataWriter(data, syntax)
        : createImageDataWriter(data, syntax, editable, desc);
  }

  private static DataWriter createMetadataDataWriter(Attributes data, AdaptTransferSyntax syntax) {
    syntax.suitable = syntax.original;
    return (out, tsuid) -> {
      try (var writer = new DicomOutputStream(out, tsuid)) {
        writer.writeDataset(null, data);
        writer.finish();
      }
    };
  }

  private static DataWriter createImageDataWriter(
      Attributes data,
      AdaptTransferSyntax syntax,
      Editable<PlanarImage> editable,
      BytesWithImageDescriptor desc)
      throws IOException {

    var reader = new DicomImageReader(Transcoder.dicomImageReaderSpi);
    var imgData = getDicomOutputData(reader, syntax.requested, desc, editable);
    checkSyntax(syntax, imgData);

    return (out, tsuid) -> {
      var dataSet = new Attributes(data);
      dataSet.remove(Tag.PixelData);
      try (var dos = new DicomOutputStream(out, tsuid)) {
        writeImage(syntax, desc, imgData, dataSet, tsuid, dos);
      } catch (Exception e) {
        LOGGER.error("Error transcoding image data", e);
      } finally {
        reader.dispose();
      }
    };
  }

  private static boolean isTranscodable(String origUid, String desUid) {
    return !desUid.equals(origUid)
        && !(DicomUtils.isNative(origUid) && DicomUtils.isNative(desUid));
  }

  /**
   * Creates a BytesWithImageDescriptor for image transcoding operations. Extracts pixel data from
   * DICOM attributes and provides access to image frames.
   *
   * @param data the DICOM attributes containing image data
   * @param syntax the transfer syntax configuration
   * @param context the attribute editor context for pixel processing
   * @return a BytesWithImageDescriptor instance, or null if transcoding is not applicable
   */
  public static BytesWithImageDescriptor imageTranscode(
      Attributes data, AdaptTransferSyntax syntax, AttributeEditorContext context) {

    var pixelDataVR = new VR.Holder();
    Object dataValue = data.getValue(Tag.PixelData, pixelDataVR);
    if (!isTranscodingApplicable(dataValue, syntax, context)) {
      return null;
    }

    var imageDescriptor = new ImageDescriptor(data);
    return new ImageBytesDescriptor(dataValue, pixelDataVR, imageDescriptor, syntax.original);
  }

  private static boolean isTranscodingApplicable(
      Object pixData, AdaptTransferSyntax syntax, AttributeEditorContext context) {

    return pixData != null
        && DicomImageReader.isSupportedSyntax(syntax.original)
        && DicomOutputData.isSupportedSyntax(syntax.requested)
        && (context.hasPixelProcessing() || isTranscodable(syntax.original, syntax.requested));
  }

  /** Implementation of BytesWithImageDescriptor for DICOM pixel data access */
  private static final class ImageBytesDescriptor implements BytesWithImageDescriptor {
    private final Object pixdata;
    private final VR.Holder pixeldataVR;
    private final ImageDescriptor imageDescriptor;
    private final String transferSyntax;
    private final ByteBuffer[] multiFrameBuffer = new ByteBuffer[1];
    private final List<Integer> fragmentsPositions = new ArrayList<>();

    ImageBytesDescriptor(
        Object pixdata,
        VR.Holder pixeldataVR,
        ImageDescriptor imageDescriptor,
        String transferSyntax) {
      this.pixdata = pixdata;
      this.pixeldataVR = pixeldataVR;
      this.imageDescriptor = imageDescriptor;
      this.transferSyntax = transferSyntax;
    }

    @Override
    public ImageDescriptor getImageDescriptor() {
      return imageDescriptor;
    }

    @Override
    public boolean isBigEndian() {
      if (pixdata instanceof BulkData bulkData) {
        return bulkData.bigEndian();
      } else if (pixdata instanceof Fragments fragments) {
        return fragments.bigEndian();
      }
      return false;
    }

    @Override
    public VR getPixelDataVR() {
      return pixeldataVR.vr;
    }

    @Override
    public String getTransferSyntax() {
      return transferSyntax;
    }

    @Override
    public ByteBuffer getBytes(int frame) throws IOException {
      if (imageDescriptor.getBitsStored() < 1) {
        return ByteBuffer.wrap(EMPTY_BYTES);
      }

      if (pixdata instanceof BulkData bulkData) {
        return getBytesFromBulkData(bulkData, frame);
      } else if (pixdata instanceof Fragments fragments) {
        return getBytesFromFragments(fragments, frame);
      }

      throw new IOException("Neither fragments nor BulkData!");
    }

    private ByteBuffer getBytesFromBulkData(BulkData bulkData, int frame) throws IOException {
      int frameLength = calculateFrameLength();

      if (multiFrameBuffer[0] == null) {
        multiFrameBuffer[0] = ByteBuffer.wrap(bulkData.toBytes(pixeldataVR.vr, isBigEndian()));
      }

      validateFrameAccess(frame, frameLength);

      var frameData = new byte[frameLength];
      multiFrameBuffer[0].position(frame * frameLength);
      multiFrameBuffer[0].get(frameData, 0, frameLength);
      return ByteBuffer.wrap(frameData);
    }

    /** Calculates the length of a single frame in bytes */
    private int calculateFrameLength() {
      return imageDescriptor
          .getPhotometricInterpretation()
          .frameLength(
              imageDescriptor.getColumns(),
              imageDescriptor.getRows(),
              imageDescriptor.getSamples(),
              imageDescriptor.getBitsAllocated());
    }

    private void validateFrameAccess(int frame, int frameLength) throws IOException {
      if (multiFrameBuffer[0].limit() < (frame + 1) * frameLength) {
        throw new IOException("Frame " + frame + " exceeds stream bounds");
      }
    }

    private ByteBuffer getBytesFromFragments(Fragments fragments, int frame) throws IOException {
      int numberOfFrames = imageDescriptor.getFrames();

      return numberOfFrames == 1
          ? getSingleFrameFromFragments(fragments)
          : getMultiFrameFromFragments(fragments, frame, numberOfFrames);
    }

    private ByteBuffer getSingleFrameFromFragments(Fragments fragments) throws IOException {
      int nbFragments = fragments.size();
      int totalLength = calculateTotalFragmentLength(fragments, nbFragments);

      try (var out = new ByteArrayOutputStream(totalLength)) {
        writeFragmentsToStream(fragments, 1, nbFragments, out);
        return ByteBuffer.wrap(out.toByteArray());
      }
    }

    private int calculateTotalFragmentLength(Fragments fragments, int nbFragments) {
      return fragments.stream()
          .skip(1)
          .limit(nbFragments - 1)
          .mapToInt(obj -> ((BulkData) obj).length())
          .sum();
    }

    /** Writes fragments to output stream */
    private void writeFragmentsToStream(
        Fragments fragments, int start, int end, ByteArrayOutputStream out) throws IOException {
      for (int i = start; i < end; i++) {
        var bulkData = (BulkData) fragments.get(i);
        var bytes = bulkData.toBytes(pixeldataVR.vr, isBigEndian());
        out.write(bytes);
      }
    }

    private ByteBuffer getMultiFrameFromFragments(
        Fragments fragments, int frame, int numberOfFrames) throws IOException {
      if (fragmentsPositions.isEmpty()) {
        initializeFragmentPositions(fragments);
      }

      if (fragmentsPositions.size() != numberOfFrames) {
        throw new IOException(
            "Fragment count mismatch: expected "
                + numberOfFrames
                + ", found "
                + fragmentsPositions.size());
      }

      return extractFrameFromFragments(fragments, frame);
    }

    private void initializeFragmentPositions(Fragments fragments) {
      int nbFragments = fragments.size();

      if (UID.RLELossless.equals(transferSyntax)) {
        initializeRLEFragmentPositions(nbFragments);
      } else {
        initializeJPEGFragmentPositions(fragments, nbFragments);
      }
    }

    private void initializeRLEFragmentPositions(int nbFragments) {
      for (int i = 1; i < nbFragments; i++) {
        fragmentsPositions.add(i);
      }
    }

    private void initializeJPEGFragmentPositions(Fragments fragments, int nbFragments) {
      for (int i = 1; i < nbFragments; i++) {
        var bulkData = (BulkData) fragments.get(i);
        if (isValidJPEGFragment(bulkData)) {
          fragmentsPositions.add(i);
        }
      }
    }

    private boolean isValidJPEGFragment(BulkData bulkData) {
      try {
        var bytes = bulkData.toBytes(pixeldataVR.vr, isBigEndian());
        try (var channel = new SeekableInMemoryByteChannel(bytes)) {
          new JPEGParser(channel);
          return true;
        }
      } catch (Exception e) {
        return false;
      }
    }

    private ByteBuffer extractFrameFromFragments(Fragments fragments, int frame)
        throws IOException {
      int start = fragmentsPositions.get(frame);
      int end =
          (frame + 1) >= fragmentsPositions.size()
              ? fragments.size()
              : fragmentsPositions.get(frame + 1);
      int totalLength = calculateFragmentRangeLength(fragments, start, end);

      try (var out = new ByteArrayOutputStream(totalLength)) {
        writeFragmentsToStream(fragments, start, end, out);
        return ByteBuffer.wrap(out.toByteArray());
      }
    }

    private int calculateFragmentRangeLength(Fragments fragments, int start, int end) {
      int length = 0;
      for (int i = start; i < end; i++) {
        length += ((BulkData) fragments.get(i)).length();
      }
      return length;
    }
  }

  /** Creates DicomOutputData from reader and descriptor */
  private static DicomOutputData getDicomOutputData(
      DicomImageReader reader,
      String outputTsuid,
      BytesWithImageDescriptor desc,
      Editable<PlanarImage> editable)
      throws IOException {
    reader.setInput(desc);
    var images = reader.getLazyPlanarImages(DICOM_IMAGE_READ_PARAM, editable);
    return new DicomOutputData(images, desc.getImageDescriptor(), outputTsuid);
  }
}
