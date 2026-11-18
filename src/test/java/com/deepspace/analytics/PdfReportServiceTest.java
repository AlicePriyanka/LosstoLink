package com.deepspace.analytics;

import com.deepspace.analytics.model.PdfReportRequest;
import com.deepspace.analytics.service.AnalysisDashboardService;
import com.deepspace.analytics.service.GeometricService;
import com.deepspace.analytics.service.PdfReportService;
import com.deepspace.analytics.service.PdfReportService.GeneratedPdfReport;
import com.deepspace.analytics.service.PoissonService;
import com.deepspace.analytics.service.ReportChartImageService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 5 Verification: Final PDF Report Redesign (Maximum 2 Pages).
 * Verifies strict 2-page limit, mode-specific content, mathematical notation (X for Poisson, Y for Geometric),
 * graphs arrangement, final conclusion, assumptions, and absence of redundant summary pages.
 */
class PdfReportServiceTest {

    private PdfReportService pdfReportService;

    @BeforeEach
    void setUp() {
        PoissonService poissonService = new PoissonService();
        GeometricService geometricService = new GeometricService();
        AnalysisDashboardService dashboardService = new AnalysisDashboardService(poissonService, geometricService);
        ReportChartImageService chartImageService = new ReportChartImageService();
        pdfReportService = new PdfReportService(
                poissonService,
                geometricService,
                dashboardService,
                chartImageService);
    }

    @Test
    @DisplayName("COMBINED Mode: Generates strictly 2 pages with all parameters, mathematical results, 3 graphs, and conclusion")
    void combinedModeGeneratesStrictTwoPageReport() throws Exception {
        PdfReportRequest request = buildRequest("3", "2", "0.6", "5");

        GeneratedPdfReport report = pdfReportService.generateReport(request);
        assertEquals("DeepSpace-Analytics-Report-lambda-3-k-2-p-0.6-n-5.pdf", report.getFilename());
        assertTrue(report.getBytes().length > 10_000);

        try (PDDocument pdf = Loader.loadPDF(report.getBytes())) {
            // STRICT PAGE LIMIT: Exactly 2 pages
            assertEquals(2, pdf.getNumberOfPages(), "Combined mode PDF report must be strictly 2 pages");

            // Page 1: Parameters and Mathematical Results
            String page1 = extractPageText(pdf, 1);
            assertTrue(page1.contains("DeepSpace Analytics"));
            assertTrue(page1.contains("Deep-Space Communication Using Poisson and Geometric Models"));
            assertTrue(page1.contains("1. Submitted Parameters"));
            assertTrue(page1.contains("Combined"));
            assertTrue(page1.contains("Page 1 of 2"));

            // Poisson Results on Page 1
            assertTrue(page1.contains("Poisson Distribution Results (Random Variable X)"));
            assertTrue(page1.contains("P(X = 2)") || page1.contains("P(X = k)"));
            assertTrue(page1.contains("0.2240418"));
            assertTrue(page1.contains("22.4042%"));
            assertTrue(page1.contains("0.0497871"));
            assertTrue(page1.contains("0.9502129"));
            assertTrue(page1.contains("E(X) = \u03BB"));
            assertTrue(page1.contains("Var(X) = \u03BB"));

            // Geometric & Reliability Results on Page 1
            assertTrue(page1.contains("Geometric Distribution & Reliability (Random Variable Y)"));
            assertTrue(page1.contains("E(Y) = 1 / p"));
            assertTrue(page1.contains("1.6666667"));
            assertTrue(page1.contains("Mode(Y) = 1"));
            assertTrue(page1.contains("R(n) = P(Y \u2264 n)"));
            assertTrue(page1.contains("0.9897600"));
            assertTrue(page1.contains("98.9760%"));
            assertTrue(page1.contains("P(Y > n)"));
            assertTrue(page1.contains("0.0102400"));
            assertTrue(page1.contains("1.0240%"));

            // Attempt Schedule on Page 1
            assertTrue(page1.contains("Attempt-by-Attempt Probability Schedule"));
            assertTrue(page1.contains("Attempt 1"));
            assertTrue(page1.contains("Attempt 5"));

            // Page 2: Graphs and Conclusion
            String page2 = extractPageText(pdf, 2);
            assertTrue(page2.contains("Visualisation Suite & Practical Conclusion"));
            assertTrue(page2.contains("Figure 1"));
            assertTrue(page2.contains("Figure 2"));
            assertTrue(page2.contains("Figure 3"));
            assertTrue(page2.contains("FINAL PRACTICAL CONCLUSION"));
            assertTrue(page2.contains("MODEL ASSUMPTIONS AND LIMITATIONS"));
            assertTrue(page2.contains("Poisson Drop Model"));
            assertTrue(page2.contains("Geometric First-Success Model"));
            assertTrue(page2.contains("Page 2 of 2"));

            // Embedded images: 3 graphs on Page 2
            assertEquals(3, countEmbeddedImages(pdf.getPage(1)), "Page 2 must contain all 3 graphs");

            // Must NOT contain separate overview/cover pages or redundant summary sections
            assertFalse(page2.contains("5.1 Combined Summary"));
            assertFalse(page2.contains("5.2 AUTOMATIC OVERALL COMMUNICATION INTERPRETATION"));
        }
    }

    @Test
    @DisplayName("POISSON_ONLY Mode: Generates strictly 2 pages with Poisson results, 1 graph, and conclusion")
    void poissonOnlyModeGeneratesStrictTwoPageReport() throws Exception {
        PdfReportRequest request = new PdfReportRequest();
        request.setLambda("3");
        request.setDropCount("2");

        GeneratedPdfReport report = pdfReportService.generateReport(request);
        assertEquals("DeepSpace-Analytics-Report-poisson-lambda-3-k-2.pdf", report.getFilename());

        try (PDDocument pdf = Loader.loadPDF(report.getBytes())) {
            assertEquals(2, pdf.getNumberOfPages(), "Poisson-only mode PDF report must be strictly 2 pages");

            String page1 = extractPageText(pdf, 1);
            assertTrue(page1.contains("DeepSpace Analytics"));
            assertTrue(page1.contains("Poisson Distribution"));
            assertTrue(page1.contains("Poisson Distribution Results (Random Variable X)"));
            assertTrue(page1.contains("P(X = 2)") || page1.contains("P(X = k)"));
            assertTrue(page1.contains("0.2240418"));
            assertTrue(page1.contains("0.0497871"));
            assertTrue(page1.contains("0.9502129"));
            assertTrue(page1.contains("Page 1 of 2"));

            // Must NOT contain Geometric results
            assertFalse(page1.contains("Geometric Distribution"));
            assertFalse(page1.contains("E(Y)"));

            String page2 = extractPageText(pdf, 2);
            assertTrue(page2.contains("Visualisation Suite & Practical Conclusion"));
            assertTrue(page2.contains("Figure 1"));
            assertTrue(page2.contains("FINAL PRACTICAL CONCLUSION"));
            assertTrue(page2.contains("MODEL ASSUMPTIONS AND LIMITATIONS"));
            assertTrue(page2.contains("Poisson Drop Model"));
            assertFalse(page2.contains("Geometric First-Success Model"));
            assertTrue(page2.contains("Page 2 of 2"));

            // Exactly 1 graph embedded on Page 2
            assertEquals(1, countEmbeddedImages(pdf.getPage(1)), "Page 2 must contain only 1 Poisson graph");
        }
    }

    @Test
    @DisplayName("GEOMETRIC_ONLY Mode: Generates strictly 2 pages with Geometric results, 2 graphs, milestones, and conclusion")
    void geometricOnlyModeGeneratesStrictTwoPageReport() throws Exception {
        PdfReportRequest request = new PdfReportRequest();
        request.setSuccessProbability("0.6");
        request.setMaxAttempts("5");

        GeneratedPdfReport report = pdfReportService.generateReport(request);
        assertEquals("DeepSpace-Analytics-Report-geometric-p-0.6-n-5.pdf", report.getFilename());

        try (PDDocument pdf = Loader.loadPDF(report.getBytes())) {
            assertEquals(2, pdf.getNumberOfPages(), "Geometric-only mode PDF report must be strictly 2 pages");

            String page1 = extractPageText(pdf, 1);
            assertTrue(page1.contains("DeepSpace Analytics"));
            assertTrue(page1.contains("Geometric Distribution"));
            assertTrue(page1.contains("Geometric Distribution & Reliability (Random Variable Y)"));
            assertTrue(page1.contains("E(Y) = 1 / p"));
            assertTrue(page1.contains("1.6666667"));
            assertTrue(page1.contains("R(n) = P(Y \u2264 n)"));
            assertTrue(page1.contains("0.9897600"));
            assertTrue(page1.contains("Attempt-by-Attempt Probability Schedule"));
            assertTrue(page1.contains("Page 1 of 2"));

            // Must NOT contain Poisson results
            assertFalse(page1.contains("Poisson Distribution Results"));
            assertFalse(page1.contains("P(X = k)"));

            String page2 = extractPageText(pdf, 2);
            assertTrue(page2.contains("Visualisation Suite & Practical Conclusion"));
            assertTrue(page2.contains("Figure 1"));
            assertTrue(page2.contains("Figure 2"));
            assertTrue(page2.contains("FINAL PRACTICAL CONCLUSION"));
            assertTrue(page2.contains("MODEL ASSUMPTIONS AND LIMITATIONS"));
            assertTrue(page2.contains("Geometric First-Success Model"));
            assertFalse(page2.contains("Poisson Drop Model"));
            assertTrue(page2.contains("Page 2 of 2"));

            // Exactly 2 graphs embedded on Page 2
            assertEquals(2, countEmbeddedImages(pdf.getPage(1)), "Page 2 must contain 2 Geometric graphs");
        }
    }

    @Test
    @DisplayName("Mathematical Notation: Geometric random variable uses Y consistently across the entire PDF, never X")
    void geometricNotationUsesYConsistently() throws Exception {
        PdfReportRequest request = buildRequest("3", "2", "0.6", "5");
        GeneratedPdfReport report = pdfReportService.generateReport(request);

        try (PDDocument pdf = Loader.loadPDF(report.getBytes())) {
            String fullText = new PDFTextStripper().getText(pdf);

            // Geometric notation checks
            assertTrue(fullText.contains("E(Y) = 1 / p"), "Must use E(Y) = 1 / p");
            assertTrue(fullText.contains("P(Y \u2264 n)") || fullText.contains("P(Y \u2264 5)"), "Must use P(Y \u2264 n)");
            assertTrue(fullText.contains("P(Y > n)") || fullText.contains("P(Y > 5)"), "Must use P(Y > n)");
            assertTrue(fullText.contains("R(n) = P(Y \u2264 n)"), "Must define R(n) = P(Y \u2264 n)");

            // Negative check: never use X for geometric
            assertFalse(fullText.contains("E(X) = 1/p") || fullText.contains("E(X) = 1 / p"),
                    "Must NEVER use E(X) for Geometric expected attempts");
            assertFalse(fullText.contains("P(X \u2264 n)") || fullText.contains("P(X \u2264 5)"),
                    "Must NEVER use P(X \u2264 n) for Geometric cumulative probability");
            assertFalse(fullText.contains("P(X > n)") || fullText.contains("P(X > 5)"),
                    "Must NEVER use P(X > n) for Geometric exceedance probability");
        }
    }

    @Test
    @DisplayName("Large attempt horizon (n=18): Sensibly capped schedule never pushes report beyond 2 pages")
    void largerAttemptHorizonDoesNotExceedTwoPages() throws Exception {
        PdfReportRequest request = buildRequest("2.4", "3", "0.35", "18");

        GeneratedPdfReport report = pdfReportService.generateReport(request);
        try (PDDocument pdf = Loader.loadPDF(report.getBytes())) {
            assertEquals(2, pdf.getNumberOfPages(), "Report with n=18 must strictly remain within 2 pages");
            String fullText = new PDFTextStripper().getText(pdf);
            assertTrue(fullText.contains("Attempt 18"));
            assertTrue(fullText.contains("FINAL PRACTICAL CONCLUSION"));
            assertTrue(fullText.contains("Page 1 of 2"));
            assertTrue(fullText.contains("Page 2 of 2"));
        }
    }

    @Test
    @DisplayName("Boundary inputs (0, 0, 1, 1): Gracefully handled within strictly 2 pages")
    void boundaryInputsHandledGracefullyWithinTwoPages() throws Exception {
        PdfReportRequest request = buildRequest("0", "0", "1", "1");
        request.setPoissonChartImage("data:image/png;base64,NOT_VALID_BASE64!!!");
        request.setGeometricChartImage("data:image/svg+xml;base64,PHN2Zz4=");
        request.setReliabilityChartImage("short");

        GeneratedPdfReport report = pdfReportService.generateReport(request);
        assertEquals("DeepSpace-Analytics-Report-lambda-0-k-0-p-1-n-1.pdf", report.getFilename());

        try (PDDocument pdf = Loader.loadPDF(report.getBytes())) {
            assertEquals(2, pdf.getNumberOfPages(), "Boundary report must have exactly 2 pages");
            String page1 = extractPageText(pdf, 1);
            assertTrue(page1.contains("1.0000000"));
            assertTrue(page1.contains("100.0000%"));
            assertTrue(page1.contains("Page 1 of 2"));
            assertEquals(3, countEmbeddedImages(pdf.getPage(1)));
        }
    }

    private PdfReportRequest buildRequest(String lambda, String dropCount, String p, String n) {
        PdfReportRequest request = new PdfReportRequest();
        request.setLambda(lambda);
        request.setDropCount(dropCount);
        request.setSuccessProbability(p);
        request.setMaxAttempts(n);
        return request;
    }

    private String extractPageText(PDDocument pdf, int pageNumber) throws Exception {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(pageNumber);
        stripper.setEndPage(pageNumber);
        return stripper.getText(pdf);
    }

    private int countEmbeddedImages(PDPage page) throws Exception {
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
