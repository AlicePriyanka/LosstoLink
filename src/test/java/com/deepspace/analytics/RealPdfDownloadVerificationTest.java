package com.deepspace.analytics;

import com.deepspace.analytics.controller.ReportController;
import com.deepspace.analytics.model.AnalysisType;
import com.deepspace.analytics.model.PdfReportRequest;
import com.deepspace.analytics.service.AnalysisDashboardService;
import com.deepspace.analytics.service.GeometricService;
import com.deepspace.analytics.service.PdfReportService;
import com.deepspace.analytics.service.PoissonService;
import com.deepspace.analytics.service.ReportChartImageService;
import com.deepspace.analytics.validation.AnalysisInputValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration test verifying the actual PDF download workflow
 * through ReportController endpoint (POST /analysis/report/pdf).
 *
 * Saves generated PDFs to target/verified-downloads/ and asserts strict 2-page limits,
 * embedded graph counts, and mathematical accuracy with Apache PDFBox.
 */
public class RealPdfDownloadVerificationTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private File outputDir;

    // Minimal valid 1x1 transparent PNG as base64 data URI for chart simulation
    private static final String SAMPLE_CHART_PNG_DATA_URI =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

    @BeforeEach
    void setUp() {
        PoissonService poissonService = new PoissonService();
        GeometricService geometricService = new GeometricService();
        AnalysisDashboardService dashboardService = new AnalysisDashboardService(poissonService, geometricService);
        ReportChartImageService chartImageService = new ReportChartImageService();
        AnalysisInputValidator validator = new AnalysisInputValidator();
        PdfReportService pdfReportService = new PdfReportService(
                poissonService, geometricService, dashboardService, chartImageService, validator);

        ReportController controller = new ReportController(validator, pdfReportService);
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        this.objectMapper = new ObjectMapper();

        this.outputDir = new File("target/verified-downloads");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
    }

    @Test
    @DisplayName("Workflow 1: POISSON_ONLY (lambda=3, k=2) downloaded PDF is strictly 2 pages with 1 chart")
    void verifyPoissonOnlyRealDownload() throws Exception {
        PdfReportRequest request = new PdfReportRequest();
        request.setAnalysisType(AnalysisType.POISSON_ONLY);
        request.setLambda("3");
        request.setDropCount("2");
        request.setPoissonChartImage(SAMPLE_CHART_PNG_DATA_URI);

        MvcResult result = mockMvc.perform(post("/analysis/report/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("DeepSpace-Analytics-Report-poisson-lambda-3-k-2.pdf")))
                .andReturn();

        byte[] pdfBytes = result.getResponse().getContentAsByteArray();
        assertTrue(pdfBytes.length > 5000, "Downloaded PDF must not be empty");

        File savedFile = new File(outputDir, "verified-poisson-download.pdf");
        try (FileOutputStream fos = new FileOutputStream(savedFile)) {
            fos.write(pdfBytes);
        }
        assertTrue(savedFile.exists());

        try (PDDocument doc = Loader.loadPDF(savedFile)) {
            assertEquals(2, doc.getNumberOfPages(), "POISSON_ONLY downloaded PDF MUST be strictly 2 pages");

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            String p1 = stripper.getText(doc).replaceAll("\\s+", " ");
            assertTrue(p1.contains("DeepSpace Analytics"));
            assertTrue(p1.contains("Poisson Distribution Results (Random Variable X)"));
            assertTrue(p1.contains("P(X = 2)"));
            assertTrue(p1.contains("0.2240418"));
            assertTrue(p1.contains("E(X) = \u03BB"));
            assertTrue(p1.contains("Var(X) = \u03BB"));
            assertTrue(p1.contains("Page 1 of 2"));
            assertFalse(p1.contains("Geometric Distribution"));

            stripper.setStartPage(2);
            stripper.setEndPage(2);
            String p2 = stripper.getText(doc).replaceAll("\\s+", " ");
            assertTrue(p2.contains("Visualisation Suite & Practical Conclusion"));
            assertTrue(p2.contains("Figure 1"));
            assertTrue(p2.contains("FINAL PRACTICAL CONCLUSION"));
            assertTrue(p2.contains("MODEL ASSUMPTIONS AND LIMITATIONS"));
            assertTrue(p2.contains("Poisson Drop Model"));
            assertFalse(p2.contains("Geometric First-Success Model"));
            assertTrue(p2.contains("Page 2 of 2"));

            assertEquals(1, countImages(doc.getPage(1)), "Page 2 must contain exactly 1 Poisson chart");
        }
    }

    @Test
    @DisplayName("Workflow 2: GEOMETRIC_ONLY (p=0.6, n=5) downloaded PDF is strictly 2 pages with 2 charts")
    void verifyGeometricOnlyRealDownload() throws Exception {
        PdfReportRequest request = new PdfReportRequest();
        request.setAnalysisType(AnalysisType.GEOMETRIC_ONLY);
        request.setSuccessProbability("0.6");
        request.setMaxAttempts("5");
        request.setGeometricChartImage(SAMPLE_CHART_PNG_DATA_URI);
        request.setReliabilityChartImage(SAMPLE_CHART_PNG_DATA_URI);

        MvcResult result = mockMvc.perform(post("/analysis/report/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("DeepSpace-Analytics-Report-geometric-p-0.6-n-5.pdf")))
                .andReturn();

        byte[] pdfBytes = result.getResponse().getContentAsByteArray();
        assertTrue(pdfBytes.length > 5000);

        File savedFile = new File(outputDir, "verified-geometric-download.pdf");
        try (FileOutputStream fos = new FileOutputStream(savedFile)) {
            fos.write(pdfBytes);
        }

        try (PDDocument doc = Loader.loadPDF(savedFile)) {
            assertEquals(2, doc.getNumberOfPages(), "GEOMETRIC_ONLY downloaded PDF MUST be strictly 2 pages");

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            String p1 = stripper.getText(doc).replaceAll("\\s+", " ");
            assertTrue(p1.contains("DeepSpace Analytics"));
            assertTrue(p1.contains("Geometric Distribution & Reliability (Random Variable Y)"));
            assertTrue(p1.contains("E(Y) = 1 / p"));
            assertTrue(p1.contains("1.6666667"));
            assertTrue(p1.contains("R(n) = P(Y \u2264 n)"));
            assertTrue(p1.contains("0.9897600"));
            assertTrue(p1.contains("Mode(Y) = 1"));
            assertTrue(p1.contains("P(Y > n)"));
            assertTrue(p1.contains("Attempt-by-Attempt Probability Schedule"));
            assertTrue(p1.contains("Page 1 of 2"));
            assertFalse(p1.contains("Poisson Distribution Results"));

            stripper.setStartPage(2);
            stripper.setEndPage(2);
            String p2 = stripper.getText(doc).replaceAll("\\s+", " ");
            assertTrue(p2.contains("Visualisation Suite & Practical Conclusion"));
            assertTrue(p2.contains("Figure 1"));
            assertTrue(p2.contains("Figure 2"));
            assertTrue(p2.contains("FINAL PRACTICAL CONCLUSION"));
            assertTrue(p2.contains("MODEL ASSUMPTIONS AND LIMITATIONS"));
            assertTrue(p2.contains("Geometric First-Success Model"));
            assertFalse(p2.contains("Poisson Drop Model"));
            assertTrue(p2.contains("Page 2 of 2"));

            assertEquals(2, countImages(doc.getPage(1)), "Page 2 must contain exactly 2 Geometric charts");
        }
    }

    @Test
    @DisplayName("Workflow 3: COMBINED (lambda=3, k=2, p=0.6, n=5) downloaded PDF is strictly 2 pages with 3 charts")
    void verifyCombinedRealDownload() throws Exception {
        PdfReportRequest request = new PdfReportRequest();
        request.setAnalysisType(AnalysisType.COMBINED);
        request.setLambda("3");
        request.setDropCount("2");
        request.setSuccessProbability("0.6");
        request.setMaxAttempts("5");
        request.setPoissonChartImage(SAMPLE_CHART_PNG_DATA_URI);
        request.setGeometricChartImage(SAMPLE_CHART_PNG_DATA_URI);
        request.setReliabilityChartImage(SAMPLE_CHART_PNG_DATA_URI);

        MvcResult result = mockMvc.perform(post("/analysis/report/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("DeepSpace-Analytics-Report-lambda-3-k-2-p-0.6-n-5.pdf")))
                .andReturn();

        byte[] pdfBytes = result.getResponse().getContentAsByteArray();
        assertTrue(pdfBytes.length > 5000);

        File savedFile = new File(outputDir, "verified-combined-download.pdf");
        try (FileOutputStream fos = new FileOutputStream(savedFile)) {
            fos.write(pdfBytes);
        }

        try (PDDocument doc = Loader.loadPDF(savedFile)) {
            assertEquals(2, doc.getNumberOfPages(), "COMBINED downloaded PDF MUST be strictly 2 pages");

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            String p1 = stripper.getText(doc).replaceAll("\\s+", " ");
            assertTrue(p1.contains("DeepSpace Analytics"));
            assertTrue(p1.contains("Poisson Distribution Results (Random Variable X)"));
            assertTrue(p1.contains("0.2240418"));
            assertTrue(p1.contains("Geometric Distribution & Reliability (Random Variable Y)"));
            assertTrue(p1.contains("1.6666667"));
            assertTrue(p1.contains("0.9897600"));
            assertTrue(p1.contains("Attempt-by-Attempt Probability Schedule"));
            assertTrue(p1.contains("Page 1 of 2"));

            stripper.setStartPage(2);
            stripper.setEndPage(2);
            String p2 = stripper.getText(doc).replaceAll("\\s+", " ");
            assertTrue(p2.contains("Visualisation Suite & Practical Conclusion"));
            assertTrue(p2.contains("Figure 1"));
            assertTrue(p2.contains("Figure 2"));
            assertTrue(p2.contains("Figure 3"));
            assertTrue(p2.contains("FINAL PRACTICAL CONCLUSION"));
            assertTrue(p2.contains("MODEL ASSUMPTIONS AND LIMITATIONS"));
            assertTrue(p2.contains("Poisson Drop Model"));
            assertTrue(p2.contains("Geometric First-Success Model"));
            assertTrue(p2.contains("Page 2 of 2"));

            assertEquals(3, countImages(doc.getPage(1)), "Page 2 must contain all 3 graphs");
        }
    }

    private int countImages(PDPage page) throws Exception {
        PDResources resources = page.getResources();
        if (resources == null) {
            return 0;
        }
        int count = 0;
        for (COSName name : resources.getXObjectNames()) {
            PDXObject xObject = resources.getXObject(name);
            if (xObject instanceof PDImageXObject) {
                count++;
            }
        }
        return count;
    }
}
