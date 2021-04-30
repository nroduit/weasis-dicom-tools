/*
 * Copyright (c) 2009-2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.stream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Objects;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Fragments;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.data.VR.Holder;
import org.dcm4che3.imageio.codec.jpeg.JPEGParser;
import org.dcm4che3.img.DicomImageReader;
import org.dcm4che3.img.DicomJpegWriteParam;
import org.dcm4che3.img.DicomOutputData;
import org.dcm4che3.img.Transcoder;
import org.dcm4che3.img.util.DicomUtils;
import org.dcm4che3.img.util.Editable;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.net.DataWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.param.AttributeEditorContext;
import org.weasis.dicom.web.Payload;
import org.weasis.opencv.data.PlanarImage;

public class ImageAdapter {
  private static final Logger LOGGER = LoggerFactory.getLogger(ImageAdapter.class);

  protected static final byte[] EMPTY_BYTES = {};

  public static class AdaptTransferSyntax {
    private final String original;
    private final String requested;
    private String suitable;

    public AdaptTransferSyntax(String original, String requested) {
      if (!StringUtil.hasText(original) || !StringUtil.hasText(requested)) {
        throw new IllegalArgumentException("A non empty value is required");
      }
      this.original = original;
      this.requested = requested;
      this.suitable = requested;
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

    public void setSuitable(String suitable) {
      this.suitable = suitable;
    }
  }

  private ImageAdapter() {}

  public static DataWriter buildDataWriter(
      Attributes data,
      AdaptTransferSyntax syntax,
      Editable<PlanarImage> editable,
      BytesWithImageDescriptor desc)
      throws IOException {
    if (desc == null) {
      syntax.suitable = syntax.original;
      return (out, tsuid) -> {
        try (DicomOutputStream writer = new DicomOutputStream(out, tsuid)) {
          writer.writeDataset(null, data);
          writer.finish();
        }
      };
    }

    DicomImageReader reader = new DicomImageReader(Transcoder.dicomImageReaderSpi);
    DicomOutputData imgData = geDicomOutputData(reader, syntax.requested, desc, editable);
    if (!syntax.requested.equals(imgData.getTsuid())) {
      syntax.suitable = imgData.getTsuid();
      LOGGER.warn(
          "Transcoding into {} is not possible, used instead {}",
          syntax.requested,
          syntax.suitable);
    }

    return (out, tsuid) -> {
      Attributes dataSet = new Attributes(data);
      dataSet.remove(Tag.PixelData);
      try (DicomOutputStream dos = new DicomOutputStream(out, tsuid)) {
        if (DicomOutputData.isNativeSyntax(tsuid)) {
          imgData.writRawImageData(dos, dataSet);
        } else {
          int[] jpegWriteParams =
              imgData.adaptTagsToCompressedImage(
                  dataSet,
                  imgData.getFistImage().get(),
                  desc.getImageDescriptor(),
                  DicomJpegWriteParam.buildDicomImageWriteParam(tsuid));
          imgData.writCompressedImageData(dos, dataSet, jpegWriteParams);
        }
      } catch (Exception e) {
        LOGGER.error("Transcoding image data", e);
      } finally {
        reader.dispose();
      }
    };
  }

  private static boolean isTranscodable(String origUid, String desUid) {
    if (!desUid.equals(origUid)) {
      return !(DicomUtils.isNative(origUid) && DicomUtils.isNative(desUid));
    }
    return false;
  }

  public static BytesWithImageDescriptor imageTranscode(
      Attributes data, AdaptTransferSyntax syntax, AttributeEditorContext context) {

    Holder pixeldataVR = new Holder();
    Object pixdata = data.getValue(Tag.PixelData, pixeldataVR);
    if (pixdata != null
        && DicomImageReader.isSupportedSyntax(syntax.original)
        && DicomOutputData.isSupportedSyntax(syntax.requested)
        && (Objects.nonNull(context.getMaskArea())
            || isTranscodable(syntax.original, syntax.requested))) {

      ImageDescriptor imdDesc = new ImageDescriptor(data);
      ByteBuffer[] mfByteBuffer = new ByteBuffer[1];
      ArrayList<Integer> fragmentsPositions = new ArrayList<>();
      return new BytesWithImageDescriptor() {
        @Override
        public ImageDescriptor getImageDescriptor() {
          return imdDesc;
        }

        @Override
        public boolean bigEndian() {
          if (pixdata instanceof BulkData) {
            return ((BulkData) pixdata).bigEndian();
          } else if (pixdata instanceof Fragments) {
            return ((Fragments) pixdata).bigEndian();
          }
          return false;
        }

        @Override
        public VR getPixelDataVR() {
          return pixeldataVR.vr;
        }

        @Override
        public ByteBuffer getBytes(int frame) throws IOException {
          ImageDescriptor desc = getImageDescriptor();
          int bitsStored = desc.getBitsStored();
          if (bitsStored < 1) {
            return ByteBuffer.wrap(EMPTY_BYTES);
          } else {
            Fragments fragments = null;
            BulkData bulkData = null;
            boolean bigEndian = bigEndian();
            if (pixdata instanceof BulkData) {
              bulkData = (BulkData) pixdata;
            } else if (pixdata instanceof Fragments) {
              fragments = (Fragments) pixdata;
            }

            boolean hasFragments = fragments != null;
            if (!hasFragments && bulkData != null) {
              int frameLength =
                  desc.getPhotometricInterpretation()
                      .frameLength(
                          desc.getColumns(),
                          desc.getRows(),
                          desc.getSamples(),
                          desc.getBitsAllocated());
              if (mfByteBuffer[0] == null) {
                mfByteBuffer[0] = ByteBuffer.wrap(bulkData.toBytes(pixeldataVR.vr, bigEndian));
              }

              if (mfByteBuffer[0].limit() < frame * frameLength + frameLength) {
                throw new IOException("Frame out of the stream limit");
              }

              byte[] bytes = new byte[frameLength];
              mfByteBuffer[0].position(frame * frameLength);
              mfByteBuffer[0].get(bytes, 0, frameLength);
              return ByteBuffer.wrap(bytes);
            } else if (hasFragments) {
              int nbFragments = fragments.size();
              int numberOfFrame = desc.getFrames();
              if (numberOfFrame == 1) {
                int length = 0;
                for (int i = 0; i < nbFragments - 1; i++) {
                  BulkData b = (BulkData) fragments.get(i + 1);
                  length += b.length();
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream(length);
                for (int i = 0; i < nbFragments - 1; i++) {
                  BulkData b = (BulkData) fragments.get(i + 1);
                  byte[] bytes = b.toBytes(pixeldataVR.vr, bigEndian);
                  out.write(bytes, 0, bytes.length);
                }
                return ByteBuffer.wrap(out.toByteArray());
              } else {
                // Multi-frames where each frames can have multiple fragments.
                if (fragmentsPositions.isEmpty()) {
                  if (UID.RLELossless.equals(syntax.original)) {
                    for (int i = 1; i < nbFragments; i++) {
                      fragmentsPositions.add(i);
                    }
                  } else {
                    for (int i = 1; i < nbFragments; i++) {
                      BulkData b = (BulkData) fragments.get(i);
                      try (ByteArrayOutputStream out = new ByteArrayOutputStream(b.length())) {
                        byte[] bytes = b.toBytes(pixeldataVR.vr, bigEndian);
                        out.write(bytes, 0, bytes.length);
                        try (SeekableInMemoryByteChannel channel =
                            new SeekableInMemoryByteChannel(out.toByteArray())) {
                          new JPEGParser(channel);
                          fragmentsPositions.add(i);
                        }
                      } catch (Exception e) {
                        // Not jpeg stream
                      }
                    }
                  }
                }

                if (fragmentsPositions.size() == numberOfFrame) {
                  int start = fragmentsPositions.get(frame);
                  int end =
                      (frame + 1) >= fragmentsPositions.size()
                          ? nbFragments
                          : fragmentsPositions.get(frame + 1);

                  int length = 0;
                  for (int i = 0; i < end - start; i++) {
                    BulkData b = (BulkData) fragments.get(start + i);
                    length += b.length();
                  }
                  ByteArrayOutputStream out = new ByteArrayOutputStream(length);
                  for (int i = 0; i < end - start; i++) {
                    BulkData b = (BulkData) fragments.get(start + i);
                    byte[] bytes = b.toBytes(pixeldataVR.vr, bigEndian);
                    out.write(bytes, 0, bytes.length);
                  }
                  return ByteBuffer.wrap(out.toByteArray());
                } else {
                  throw new IOException("Cannot match all the fragments to all the frames!");
                }
              }
            }
          }
          throw new IOException("Neither fragments nor BulkData!");
        }

        @Override
        public String getTransferSyntax() {
          return syntax.original;
        }

        @Override
        public Attributes getPaletteColorLookupTable() {
          Attributes dcm = new Attributes(10);
          copyValue(data, dcm, Tag.RedPaletteColorLookupTableDescriptor);
          copyValue(data, dcm, Tag.RedPaletteColorLookupTableDescriptor);
          copyValue(data, dcm, Tag.GreenPaletteColorLookupTableDescriptor);
          copyValue(data, dcm, Tag.BluePaletteColorLookupTableDescriptor);
          copyValue(data, dcm, Tag.RedPaletteColorLookupTableData);
          copyValue(data, dcm, Tag.GreenPaletteColorLookupTableData);
          copyValue(data, dcm, Tag.BluePaletteColorLookupTableData);
          copyValue(data, dcm, Tag.SegmentedRedPaletteColorLookupTableData);
          copyValue(data, dcm, Tag.SegmentedGreenPaletteColorLookupTableData);
          copyValue(data, dcm, Tag.SegmentedBluePaletteColorLookupTableData);
          return dcm;
        }
      };
    }
    return null;
  }

  private static void copyValue(Attributes original, Attributes copy, int tag) {
    if (original.containsValue(tag)) {
      copy.setValue(tag, original.getVR(tag), original.getValue(tag));
    }
  }

  public static Payload preparePlayload(
      Attributes data,
      AdaptTransferSyntax syntax,
      BytesWithImageDescriptor desc,
      Editable<PlanarImage> editable)
      throws IOException {
    DicomImageReader reader = new DicomImageReader(Transcoder.dicomImageReaderSpi);
    DicomOutputData imgData = geDicomOutputData(reader, syntax.requested, desc, editable);
    if (!syntax.requested.equals(imgData.getTsuid())) {
      syntax.suitable = imgData.getTsuid();
      LOGGER.warn(
          "Transcoding into {} is not possible, used instead {}",
          syntax.requested,
          syntax.suitable);
    }
    Attributes dataSet = new Attributes(data);
    dataSet.remove(Tag.PixelData);

    return new Payload() {
      @Override
      public long size() {
        return -1;
      }

      @Override
      public InputStream newInputStream() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DicomOutputStream dos = new DicomOutputStream(out, imgData.getTsuid())) {
          dos.writeFileMetaInformation(dataSet.createFileMetaInformation(imgData.getTsuid()));
          if (DicomOutputData.isNativeSyntax(imgData.getTsuid())) {
            imgData.writRawImageData(dos, dataSet);
          } else {
            int[] jpegWriteParams =
                imgData.adaptTagsToCompressedImage(
                    dataSet,
                    imgData.getFistImage().get(),
                    desc.getImageDescriptor(),
                    DicomJpegWriteParam.buildDicomImageWriteParam(imgData.getTsuid()));
            imgData.writCompressedImageData(dos, dataSet, jpegWriteParams);
          }
        } catch (IOException e) {
          LOGGER.error("Cannot write DICOM", e);
          return new ByteArrayInputStream(new byte[] {});
        } finally {
          reader.dispose();
        }
        return new ByteArrayInputStream(out.toByteArray());
      }
    };
  }

  private static DicomOutputData geDicomOutputData(
      DicomImageReader reader,
      String outputTsuid,
      BytesWithImageDescriptor desc,
      Editable<PlanarImage> editable)
      throws IOException {
    reader.setInput(desc);
    var images = reader.getLazyPlanarImages(null, editable);
    return new DicomOutputData(images, desc.getImageDescriptor(), outputTsuid);
  }
}
