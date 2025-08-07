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

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Fragments;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.data.VR.Holder;
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
public class ImageAdapter {
  private static final Logger LOGGER = LoggerFactory.getLogger(ImageAdapter.class);

  private static final DicomImageReadParam DICOM_IMAGE_READ_PARAM = new DicomImageReadParam();

  static {
    DICOM_IMAGE_READ_PARAM.setReleaseImageAfterProcessing(true);
  }

  protected static final byte[] EMPTY_BYTES = {};

  /**
   * Configuration class for managing transfer syntax adaptation during DICOM image processing.
   * Encapsulates the original transfer syntax, requested syntax, and the final suitable syntax
   * along with compression parameters.
   */
  public static class AdaptTransferSyntax {
    private final String original;
    private final String requested;
    private String suitable;
    private int jpegQuality;
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
        throw new IllegalArgumentException("A non empty value is required");
      }
      this.original = original;
      this.requested = requested;
      this.suitable = requested;
      this.jpegQuality = 85;
    }

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

    public void setJpegQuality(int jpegQuality) {
      this.jpegQuality = jpegQuality;
    }

    public int getCompressionRatioFactor() {
      return compressionRatioFactor;
    }

    public void setCompressionRatioFactor(int compressionRatioFactor) {
      this.compressionRatioFactor = compressionRatioFactor;
    }

    /**
     * Sets the suitable transfer syntax if it's a valid and supported syntax.
     *
     * @param suitable the transfer syntax UID to set as suitable
     */
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
   * @param file the output file to write
   * @return true if the file was written successfully, false otherwise
   */
  public static boolean writeDicomFile(
      Attributes data,
      AdaptTransferSyntax syntax,
      Editable<PlanarImage> editable,
      BytesWithImageDescriptor desc,
      File file) {
    if (desc == null) {
      return writeMetadataOnlyFile(data, syntax, file);
    }
    return writeImageFile(data, syntax, editable, desc, file);
  }

  private static boolean writeMetadataOnlyFile(
      Attributes data, AdaptTransferSyntax syntax, File file) {
    adjustSyntaxForMetadataOnly(syntax);
    try (DicomOutputStream writer = new DicomOutputStream(file)) {
      writer.writeDataset(data.createFileMetaInformation(syntax.suitable), data);
      writer.finish();
      return true;
    } catch (Exception e) {
      LOGGER.error("Writing DICOM metadata file", e);
      FileUtil.delete(file.toPath());
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
      File file) {
    DicomImageReader reader = new DicomImageReader(Transcoder.dicomImageReaderSpi);
    try {
      DicomOutputData imgData = getDicomOutputData(reader, syntax.requested, desc, editable);
      checkSyntax(syntax, imgData);

      Attributes dataSet = new Attributes(data);
      dataSet.remove(Tag.PixelData);
      String dstTsuid = syntax.suitable;
      try (DicomOutputStream dos =
          new DicomOutputStream(new BufferedOutputStream(new FileOutputStream(file)), dstTsuid)) {
        dos.writeFileMetaInformation(dataSet.createFileMetaInformation(dstTsuid));
        writeImage(syntax, desc, imgData, dataSet, dstTsuid, dos);
      }

      return true;
    } catch (IOException e) {
      LOGGER.error("Get DicomOutputData", e);
      return false;
    } catch (Exception e) {
      LOGGER.error("Transcoding image data", e);
      FileUtil.delete(file.toPath());
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
      imgData.writRawImageData(dos, dataSet);
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

    DicomJpegWriteParam dicomJpegWriteParam =
        DicomJpegWriteParam.buildDicomImageWriteParam(dstTsuid);

    configureCompressionQuality(syntax, dicomJpegWriteParam);
    configureCompressionRatio(syntax, dicomJpegWriteParam);

    int[] jpegWriteParams =
        imgData.adaptTagsToCompressedImage(
            dataSet, imgData.getFirstImage().get(), desc.getImageDescriptor(), dicomJpegWriteParam);
    imgData.writeCompressedImageData(dos, dataSet, jpegWriteParams);
  }

  private static void configureCompressionQuality(
      AdaptTransferSyntax syntax, DicomJpegWriteParam params) {
    if (params.getCompressionQuality() > 0) {
      int quality = syntax.getJpegQuality() <= 0 ? 85 : syntax.getJpegQuality();
      params.setCompressionQuality(quality);
    }
  }

  private static void configureCompressionRatio(
      AdaptTransferSyntax syntax, DicomJpegWriteParam params) {
    if (params.getCompressionRatioFactor() > 0 && syntax.getCompressionRatioFactor() > 0) {
      params.setCompressionRatioFactor(syntax.getCompressionRatioFactor());
    }
  }

  /**
   * Validates and adjusts the transfer syntax based on the actual output capabilities. Updates the
   * suitable syntax if the requested syntax cannot be used.
   *
   * @param syntax the transfer syntax configuration to check and potentially modify
   * @param imgData the image output data containing the actual transfer syntax used
   */
  public static void checkSyntax(AdaptTransferSyntax syntax, DicomOutputData imgData) {
    if (!syntax.requested.equals(imgData.getTsuid())) {
      syntax.suitable = imgData.getTsuid();
      LOGGER.warn(
          "Transcoding into {} is not possible, used instead {}",
          syntax.requested,
          syntax.suitable);
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
    if (desc == null) {
      return createMetadataDataWriter(data, syntax);
    }

    return createImageDataWriter(data, syntax, editable, desc);
  }

  private static DataWriter createMetadataDataWriter(Attributes data, AdaptTransferSyntax syntax) {
    syntax.suitable = syntax.original;
    return (out, tsuid) -> {
      try (DicomOutputStream writer = new DicomOutputStream(out, tsuid)) {
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
    DicomImageReader reader = new DicomImageReader(Transcoder.dicomImageReaderSpi);
    DicomOutputData imgData = getDicomOutputData(reader, syntax.requested, desc, editable);
    checkSyntax(syntax, imgData);

    return (out, tsuid) -> {
      Attributes dataSet = new Attributes(data);
      dataSet.remove(Tag.PixelData);
      try (DicomOutputStream dos = new DicomOutputStream(out, tsuid)) {
        writeImage(syntax, desc, imgData, dataSet, tsuid, dos);
      } catch (Exception e) {
        LOGGER.error("Transcoding image data", e);
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
   * Creates a BytesWithImageDescriptor for image transcoding operations. This method extracts pixel
   * data from DICOM attributes and provides access to image frames with support for various
   * transfer syntaxes and pixel processing.
   *
   * @param data the DICOM attributes containing image data
   * @param syntax the transfer syntax configuration
   * @param context the attribute editor context for pixel processing
   * @return a BytesWithImageDescriptor instance, or null if transcoding is not applicable
   */
  public static BytesWithImageDescriptor imageTranscode(
      Attributes data, AdaptTransferSyntax syntax, AttributeEditorContext context) {

    Holder pixeldataVR = new Holder();
    Object pixdata = data.getValue(Tag.PixelData, pixeldataVR);
    if (!isTranscodingApplicable(pixdata, syntax, context)) {
      return null;
    }

    ImageDescriptor imageDescriptor = new ImageDescriptor(data);
    return new ImageBytesDescriptor(pixdata, pixeldataVR, imageDescriptor, syntax.original);
  }

  private static boolean isTranscodingApplicable(
      Object pixdata, AdaptTransferSyntax syntax, AttributeEditorContext context) {

    return pixdata != null
        && DicomImageReader.isSupportedSyntax(syntax.original)
        && DicomOutputData.isSupportedSyntax(syntax.requested)
        && (context.hasPixelProcessing() || isTranscodable(syntax.original, syntax.requested));
  }

  /** Implementation of BytesWithImageDescriptor for handling DICOM pixel data access. */
  private static class ImageBytesDescriptor implements BytesWithImageDescriptor {
    private final Object pixdata;
    private final Holder pixeldataVR;
    private final ImageDescriptor imageDescriptor;
    private final String transferSyntax;
    private final ByteBuffer[] multiFrameBuffer = new ByteBuffer[1];
    private final List<Integer> fragmentsPositions = new ArrayList<>();

    ImageBytesDescriptor(
        Object pixdata,
        Holder pixeldataVR,
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
    public boolean bigEndian() {
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
    public ByteBuffer getBytes(int frame) throws IOException {
      int bitsStored = imageDescriptor.getBitsStored();
      if (bitsStored < 1) {
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
        multiFrameBuffer[0] = ByteBuffer.wrap(bulkData.toBytes(pixeldataVR.vr, bigEndian()));
      }

      validateFrameAccess(frame, frameLength);

      byte[] bytes = new byte[frameLength];
      multiFrameBuffer[0].position(frame * frameLength);
      multiFrameBuffer[0].get(bytes, 0, frameLength);
      return ByteBuffer.wrap(bytes);
    }

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
      if (multiFrameBuffer[0].limit() < frame * frameLength + frameLength) {
        throw new IOException("Frame out of the stream limit");
      }
    }

    private ByteBuffer getBytesFromFragments(Fragments fragments, int frame) throws IOException {
      int numberOfFrames = imageDescriptor.getFrames();

      if (numberOfFrames == 1) {
        return getSingleFrameFromFragments(fragments);
      } else {
        return getMultiFrameFromFragments(fragments, frame, numberOfFrames);
      }
    }

    private ByteBuffer getSingleFrameFromFragments(Fragments fragments) throws IOException {
      int nbFragments = fragments.size();
      int length = calculateTotalFragmentLength(fragments, nbFragments);

      try (ByteArrayOutputStream out = new ByteArrayOutputStream(length)) {
        writeFragmentsToStream(fragments, nbFragments, out);
        return ByteBuffer.wrap(out.toByteArray());
      }
    }

    private int calculateTotalFragmentLength(Fragments fragments, int nbFragments) {
      int length = 0;
      for (int i = 1; i < nbFragments; i++) {
        BulkData b = (BulkData) fragments.get(i);
        length += b.length();
      }
      return length;
    }

    private void writeFragmentsToStream(
        Fragments fragments, int nbFragments, ByteArrayOutputStream out) throws IOException {
      for (int i = 1; i < nbFragments; i++) {
        BulkData b = (BulkData) fragments.get(i);
        byte[] bytes = b.toBytes(pixeldataVR.vr, bigEndian());
        out.write(bytes, 0, bytes.length);
      }
    }

    private ByteBuffer getMultiFrameFromFragments(
        Fragments fragments, int frame, int numberOfFrames) throws IOException {
      if (fragmentsPositions.isEmpty()) {
        initializeFragmentPositions(fragments);
      }

      if (fragmentsPositions.size() != numberOfFrames) {
        throw new IOException("Cannot match all the fragments to all the frames!");
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
        BulkData b = (BulkData) fragments.get(i);
        if (isValidJPEGFragment(b)) {
          fragmentsPositions.add(i);
        }
      }
    }

    private boolean isValidJPEGFragment(BulkData bulkData) {
      try (ByteArrayOutputStream out = new ByteArrayOutputStream(bulkData.length())) {
        byte[] bytes = bulkData.toBytes(pixeldataVR.vr, bigEndian());
        out.write(bytes, 0, bytes.length);
        try (SeekableInMemoryByteChannel channel =
            new SeekableInMemoryByteChannel(out.toByteArray())) {
          new JPEGParser(channel);
          return true;
        }
      } catch (Exception e) {
        return false; // Not a valid JPEG stream
      }
    }

    private ByteBuffer extractFrameFromFragments(Fragments fragments, int frame)
        throws IOException {
      int start = fragmentsPositions.get(frame);
      int end =
          (frame + 1) >= fragmentsPositions.size()
              ? fragments.size()
              : fragmentsPositions.get(frame + 1);

      int length = calculateFragmentRangeLength(fragments, start, end);

      try (ByteArrayOutputStream out = new ByteArrayOutputStream(length)) {
        writeFragmentRange(fragments, start, end, out);
        return ByteBuffer.wrap(out.toByteArray());
      }
    }

    private int calculateFragmentRangeLength(Fragments fragments, int start, int end) {
      int length = 0;
      for (int i = start; i < end; i++) {
        BulkData b = (BulkData) fragments.get(i);
        length += b.length();
      }
      return length;
    }

    private void writeFragmentRange(
        Fragments fragments, int start, int end, ByteArrayOutputStream out) throws IOException {
      for (int i = start; i < end; i++) {
        BulkData b = (BulkData) fragments.get(i);
        byte[] bytes = b.toBytes(pixeldataVR.vr, bigEndian());
        out.write(bytes, 0, bytes.length);
      }
    }

    @Override
    public String getTransferSyntax() {
      return transferSyntax;
    }
  }

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
