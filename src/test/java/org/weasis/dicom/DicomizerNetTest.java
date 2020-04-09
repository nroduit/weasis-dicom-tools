package org.weasis.dicom;

import static org.junit.Assert.assertTrue;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.log4j.BasicConfigurator;
import org.dcm4che6.data.DicomObject;
import org.dcm4che6.data.Tag;
import org.dcm4che6.data.VR;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.tool.Dicomizer;

public class DicomizerNetTest {
    
    @BeforeAll
    public static void setLogger() throws MalformedURLException {
        BasicConfigurator.configure();
    }
    
    @Test
    public void dicomizePdf() {
        DicomObject dcm = DicomObject.newDicomObject();
        dcm.setString(Tag.PatientID, VR.LO, "id1234");
        dcm.setString(Tag.PatientName, VR.PN, "Dicomizer Test");
        dcm.setString(Tag.SeriesDescription, VR.LO, "Test pdf");
        try {
            Path pdfFile = Path.of(getClass().getResource("weasis-histogram.pdf").toURI());
            Path dcmFile = Path.of("target/images-output/weasis-histogram.dcm");
            Dicomizer.pdf(dcm, pdfFile, dcmFile);
            assertTrue(Files.size(dcmFile) > 10000);
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }
        
    }
    @Test
    public void dicomizeJpg() {
        DicomObject dcm = DicomObject.newDicomObject();
        dcm.setString(Tag.PatientID, VR.LO, "id1234");
        dcm.setString(Tag.PatientName, VR.PN, "Dicomizer Test");
        dcm.setString(Tag.SeriesDescription, VR.LO, "Test jpg");
        try {
            Path jpgFile = Path.of(getClass().getResource("us.jpg").toURI());
            Path dcmFile = Path.of("target/images-output/us.dcm");
            Dicomizer.jpegOrMpeg(dcm, jpgFile, dcmFile, false);
            assertTrue(Files.size(dcmFile) > 10000);
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }
        
    }
    
    @Test
    public void dicomizeMeg2() {
        DicomObject dcm = DicomObject.newDicomObject();
        dcm.setString(Tag.PatientID, VR.LO, "id1234");
        dcm.setString(Tag.PatientName, VR.PN, "Dicomizer Test");
        dcm.setString(Tag.SeriesDescription, VR.LO, "Test mpeg2");
        try {
            Path jpgFile = Path.of(getClass().getResource("video-mpeg2.mpg").toURI());
            Path dcmFile = Path.of("target/images-output/video-mpeg2.dcm");
            Dicomizer.jpegOrMpeg(dcm, jpgFile, dcmFile, false);
            assertTrue(Files.size(dcmFile) > 100000);
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }
    
    @Test
    public void dicomizeMeg4H264() {
        DicomObject dcm = DicomObject.newDicomObject();
        dcm.setString(Tag.PatientID, VR.LO, "id1234");
        dcm.setString(Tag.PatientName, VR.PN, "Dicomizer Test");
        dcm.setString(Tag.SeriesDescription, VR.LO, "Test mpeg4 H.264");
        try {
            Path jpgFile = Path.of(getClass().getResource("video-h264.mp4").toURI());
            Path dcmFile = Path.of("target/images-output/video-h264.dcm");
            Dicomizer.jpegOrMpeg(dcm, jpgFile, dcmFile, false);
            assertTrue(Files.size(dcmFile) > 100000);
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }
    
    @Test
    public void dicomizeMeg4H265() {
        DicomObject dcm = DicomObject.newDicomObject();
        dcm.setString(Tag.PatientID, VR.LO, "id1234");
        dcm.setString(Tag.PatientName, VR.PN, "Dicomizer Test");
        dcm.setString(Tag.SeriesDescription, VR.LO, "Test mpeg4 H.265");
        try {
            Path jpgFile = Path.of(getClass().getResource("video-h265.mp4").toURI());
            Path dcmFile = Path.of("target/images-output/video-h265.dcm");
            Dicomizer.jpegOrMpeg(dcm, jpgFile, dcmFile, false);
            assertTrue(Files.size(dcmFile) > 100000);
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }
}
