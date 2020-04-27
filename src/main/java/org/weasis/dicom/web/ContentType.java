package org.weasis.dicom.web;

import org.dcm4che6.codec.CompressedPixelParser;
import org.dcm4che6.codec.JPEGParser;
import org.dcm4che6.codec.MP4Parser;
import org.dcm4che6.codec.MPEG2Parser;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.data.VR;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

public enum ContentType {
    APPLICATION_DICOM("application/dicom", -1, null, null),
    APPLICATION_PDF("application/pdf", Tag.EncapsulatedDocument, x -> "pdf.xml", null),
    TEXT_XML("text/xml", Tag.EncapsulatedDocument, x -> "cda.xml", JPEGParser::new),
    IMAGE_JPEG("image/jpeg", Tag.PixelData, x -> x.isPhoto() ? "photo.xml" : "sc.xml", JPEGParser::new),
    IMAGE_JP2("image/jp2", Tag.PixelData, x -> x.isPhoto() ? "photo.xml" : "sc.xml", JPEGParser::new),
    VIDEO_MPEG("video/mpeg", Tag.PixelData, x -> "video.xml", MPEG2Parser::new),
    VIDEO_MP4("video/mp4", Tag.PixelData, x -> "video.xml", MP4Parser::new),
    VIDEO_QT("video/quicktime", Tag.PixelData, x -> "video.xml", MP4Parser::new);

    @FunctionalInterface
    interface ParserGenerator {
        CompressedPixelParser apply(SeekableByteChannel channel) throws IOException;
    }

    final String type;
    final int bulkdataTag;
    final Function<DicomStowRS, String> resource;
    final ParserGenerator parserGenerator;

    ContentType(String type, int bulkdataTag, Function<DicomStowRS, String> resource, ParserGenerator parserGenerator) {
        this.type = type;
        this.bulkdataTag = bulkdataTag;
        this.resource = resource;
        this.parserGenerator = parserGenerator;
    }

    static ContentType probe(Path path) {
        try {
            String type = Files.probeContentType(path);
            if (type == null)
                throw new IOException(String.format("failed to determine content type of file: '%s'", path));
            switch (type.toLowerCase()) {
                case "application/dicom":
                    return ContentType.APPLICATION_DICOM;
                case "application/pdf":
                    return ContentType.APPLICATION_PDF;
                case "text/xml":
                    return ContentType.TEXT_XML;
                case "image/jpeg":
                    return ContentType.IMAGE_JPEG;
                case "image/jp2":
                    return ContentType.IMAGE_JP2;
                case "video/mpeg":
                    return ContentType.VIDEO_MPEG;
                case "video/mp4":
                    return ContentType.VIDEO_MP4;
                case "video/quicktime":
                    return ContentType.VIDEO_QT;
            }
            throw new UnsupportedOperationException(
                    String.format("unsupported content type: '%s' of file: '%s'", type, path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void setBulkDataURI(DicomObject metadata, Path file) {
        metadata.setBulkData(bulkdataTag, VR.OB, file.toUri().toASCIIString(), null);
    }

    public String contentType(boolean appendTransferSyntax, Path path) {
        if (appendTransferSyntax && parserGenerator != null)
            try (SeekableByteChannel channel = Files.newByteChannel(path)) {
                return type + ";transfer-syntax=" + parserGenerator.apply(channel).getTransferSyntaxUID();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        return type;
    }
}