package com.deepspace.analytics;

import com.deepspace.analytics.controller.AnalysisController;
import com.deepspace.analytics.controller.ReportController;
import com.deepspace.analytics.model.AnalysisDashboardView;
import com.deepspace.analytics.model.AnalysisType;
import com.deepspace.analytics.model.CommunicationAnalysisInput;
import com.deepspace.analytics.model.GeometricResult;
import com.deepspace.analytics.model.PdfReportRequest;
import com.deepspace.analytics.model.PoissonResult;
import com.deepspace.analytics.service.AnalysisDashboardService;
import com.deepspace.analytics.service.GeometricService;
import com.deepspace.analytics.service.PdfReportService;
import com.deepspace.analytics.service.PdfReportService.GeneratedPdfReport;
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
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase2AnalysisModeTest {

    private TrackingPoissonService poissonTracker;
    private TrackingGeometricService geometricTracker;
    private AnalysisInputValidator validator;
    private AnalysisDashboardService dashboardService;
    private PdfReportService pdfReportService;
    private AnalysisController analysisController;
    private ReportController reportController;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        poissonTracker = new TrackingPoissonService();
        geometricTracker = new TrackingGeometricService();
        validator = new AnalysisInputValidator();
        dashboardService = new AnalysisDashboardService(poissonTracker, geometricTracker);
        pdfReportService = new PdfReportService(
                poissonTracker, geometricTracker, dashboardService, new ReportChartImageService(), validator);
        objectMapper = new ObjectMapper();
        analysisController = new AnalysisController(
                validator, poissonTracker, geometricTracker, dashboardService, objectMapper);
        reportController = new ReportController(validator, pdfReportService);
    }

    // -------------------------------------------------------------------------
    // 1. POISSON_ONLY Workflow (λ=3, k=2)
    // -------------------------------------------------------------------------

    @Test
    void poissonOnlyExecutesOnlyPoissonServiceAcrossControllerDashboardAndPdf() throws Exception {
        CommunicationAnalysisInput input = buildInput("3", "2", "", "");
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(input, "analysisInput");
        ExtendedModelMap model = new ExtendedModelMap();

        String viewName = analysisController.submitAnalysisForm(input, bindingResult, model);

        // Controller assertions
        assertEquals("analysis-results", viewName);
        assertFalse(bindingResult.hasErrors());
        assertEquals(AnalysisType.POISSON_ONLY, model.getAttribute("analysisType"));
        assertNotNull(model.getAttribute("poissonResult"));
        assertNull(model.getAttribute("geometricResult"));

        // Verify ONLY Poisson service was invoked
        assertEquals(1, poissonTracker.calculateInputCalls, "PoissonService must be invoked once");
        assertEquals(0, geometricTracker.calculateInputCalls, "GeometricService must NOT be invoked in Poisson-only mode");

        // Dashboard view assertions
        AnalysisDashboardView dashboard = (AnalysisDashboardView) model.getAttribute("dashboard");
        assertNotNull(dashboard);
        assertEquals(AnalysisType.POISSON_ONLY, dashboard.getAnalysisType());
        assertTrue(dashboard.isPoissonAvailable());
        assertFalse(dashboard.isGeometricAvailable());
        assertFalse(dashboard.isReliabilityAvailable());
        assertNotNull(dashboard.getPoisson());
        assertNull(dashboard.getGeometric());
        assertTrue(dashboard.getAttemptRows().isEmpty());
        assertNull(dashboard.getReliability());
        assertEquals(3, dashboard.getInterpretations().size());

        // Dashboard charts assertions: only Poisson graph active
        assertFalse(dashboard.getCharts().getPoissonSeries().isEmpty());
        assertTrue(dashboard.getCharts().getGeometricSeries().isEmpty());
        assertTrue(dashboard.getCharts().getReliabilitySeries().isEmpty());

        // PDF Generation via ReportController
        poissonTracker.reset();
        geometricTracker.reset();
        PdfReportRequest pdfReq = buildPdfRequest("3", "2", null, null, AnalysisType.POISSON_ONLY);
        ResponseEntity<?> response = reportController.downloadReportJson(pdfReq);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, poissonTracker.calculateInputCalls, "PoissonService must be invoked for PDF");
        assertEquals(0, geometricTracker.calculateInputCalls, "GeometricService must NOT be invoked for Poisson-only PDF");

        byte[] pdfBytes = (byte[]) response.getBody();
        assertNotNull(pdfBytes);
        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
            assertEquals(2, pdf.getNumberOfPages(), "Poisson-only report must have strictly 2 pages");
            assertEquals(1, countEmbeddedImages(pdf.getPage(1)), "Poisson-only graphs page must have exactly 1 chart");
            String fullText = new PDFTextStripper().getText(pdf);
            assertTrue(fullText.contains("0.2240418"));
            assertTrue(fullText.contains("0.0497871"));
            assertTrue(fullText.contains("0.9502129"));
            assertFalse(fullText.contains("Geometric Distribution & Reliability"));
            assertFalse(fullText.contains("Attempt-by-Attempt"));
        }
    }

    // -------------------------------------------------------------------------
    // 2. GEOMETRIC_ONLY Workflow (p=0.6, n=5)
    // -------------------------------------------------------------------------

    @Test
    void geometricOnlyExecutesOnlyGeometricServiceAcrossControllerDashboardAndPdf() throws Exception {
        CommunicationAnalysisInput input = buildInput("", "", "0.6", "5");
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(input, "analysisInput");
        ExtendedModelMap model = new ExtendedModelMap();

        String viewName = analysisController.submitAnalysisForm(input, bindingResult, model);

        // Controller assertions
        assertEquals("analysis-results", viewName);
        assertFalse(bindingResult.hasErrors());
        assertEquals(AnalysisType.GEOMETRIC_ONLY, model.getAttribute("analysisType"));
        assertNull(model.getAttribute("poissonResult"));
        assertNotNull(model.getAttribute("geometricResult"));

        // Verify ONLY Geometric service was invoked
        assertEquals(0, poissonTracker.calculateInputCalls, "PoissonService must NOT be invoked in Geometric-only mode");
        assertTrue(geometricTracker.calculateInputCalls >= 1, "GeometricService must be invoked");

        // Dashboard view assertions
        AnalysisDashboardView dashboard = (AnalysisDashboardView) model.getAttribute("dashboard");
        assertNotNull(dashboard);
        assertEquals(AnalysisType.GEOMETRIC_ONLY, dashboard.getAnalysisType());
        assertFalse(dashboard.isPoissonAvailable());
        assertTrue(dashboard.isGeometricAvailable());
        assertTrue(dashboard.isReliabilityAvailable());
        assertNull(dashboard.getPoisson());
        assertNotNull(dashboard.getGeometric());
        assertEquals(5, dashboard.getAttemptRows().size());

        // Reliability summary contains cumulative geometric R(n)
        assertNotNull(dashboard.getReliability());
        assertNull(dashboard.getReliability().getExpectedDropsValue());
        assertEquals("0.9897600", dashboard.getReliability().getReliabilityDecimal());
        assertEquals("98.9760%", dashboard.getReliability().getReliabilityPercent());
        assertEquals(3, dashboard.getInterpretations().size());

        // Dashboard charts assertions: Geometric and Reliability graphs active, Poisson empty
        assertTrue(dashboard.getCharts().getPoissonSeries().isEmpty());
        assertEquals(5, dashboard.getCharts().getGeometricSeries().size());
        assertEquals(5, dashboard.getCharts().getReliabilitySeries().size());

        // PDF Generation via ReportController
        poissonTracker.reset();
        geometricTracker.reset();
        PdfReportRequest pdfReq = buildPdfRequest(null, null, "0.6", "5", AnalysisType.GEOMETRIC_ONLY);
        ResponseEntity<?> response = reportController.downloadReportJson(pdfReq);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, poissonTracker.calculateInputCalls, "PoissonService must NOT be invoked for Geometric-only PDF");
        assertTrue(geometricTracker.calculateInputCalls >= 1, "GeometricService must be invoked for PDF");

        byte[] pdfBytes = (byte[]) response.getBody();
        assertNotNull(pdfBytes);
        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
            assertEquals(2, pdf.getNumberOfPages(), "Geometric-only report must have strictly 2 pages");
            assertEquals(2, countEmbeddedImages(pdf.getPage(1)), "Geometric-only graphs page must have exactly 2 charts");
            String fullText = new PDFTextStripper().getText(pdf);
            assertTrue(fullText.contains("1.6666667"));
            assertTrue(fullText.contains("0.9897600"));
            assertTrue(fullText.contains("0.0102400"));
            assertTrue(fullText.contains("Attempt 1"));
            assertTrue(fullText.contains("Attempt 5"));
            assertFalse(fullText.contains("Poisson Distribution Results"));
            assertFalse(fullText.contains("Expected Drops"));
        }
    }

    // -------------------------------------------------------------------------
    // 3. COMBINED Workflow (λ=3, k=2, p=0.6, n=5)
    // -------------------------------------------------------------------------

    @Test
    void combinedModeExecutesBothServicesAndPreservesCompleteOutputs() throws Exception {
        CommunicationAnalysisInput input = buildInput("3", "2", "0.6", "5");
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(input, "analysisInput");
        ExtendedModelMap model = new ExtendedModelMap();

        String viewName = analysisController.submitAnalysisForm(input, bindingResult, model);

        assertEquals("analysis-results", viewName);
        assertEquals(AnalysisType.COMBINED, model.getAttribute("analysisType"));
        assertEquals(1, poissonTracker.calculateInputCalls, "PoissonService must be invoked once");
        assertTrue(geometricTracker.calculateInputCalls >= 1, "GeometricService must be invoked");

        AnalysisDashboardView dashboard = (AnalysisDashboardView) model.getAttribute("dashboard");
        assertNotNull(dashboard.getPoisson());
        assertNotNull(dashboard.getGeometric());
        assertNotNull(dashboard.getReliability());
        assertEquals(6, dashboard.getInterpretations().size());

        // All 3 graphs active
        assertFalse(dashboard.getCharts().getPoissonSeries().isEmpty());
        assertEquals(5, dashboard.getCharts().getGeometricSeries().size());
        assertEquals(5, dashboard.getCharts().getReliabilitySeries().size());

        // PDF Generation via ReportController
        PdfReportRequest pdfReq = buildPdfRequest("3", "2", "0.6", "5", AnalysisType.COMBINED);
        ResponseEntity<?> response = reportController.downloadReportJson(pdfReq);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        byte[] pdfBytes = (byte[]) response.getBody();
        assertNotNull(pdfBytes);
        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
            assertEquals(2, pdf.getNumberOfPages(), "Combined report must have strictly 2 pages");
            assertEquals(3, countEmbeddedImages(pdf.getPage(1)), "Combined graphs page must have exactly 3 charts");
            String fullText = new PDFTextStripper().getText(pdf);
            assertTrue(fullText.contains("0.2240418"));
            assertTrue(fullText.contains("1.6666667"));
            assertTrue(fullText.contains("0.9897600"));
        }
    }

    // -------------------------------------------------------------------------
    // 4. All 14 Validation Scenarios
    // -------------------------------------------------------------------------

    @Test
    void validationCase1_allFourEmptyRejected() {
        CommunicationAnalysisInput input = buildInput("", "", "", "");
        Map<String, String> errors = validator.validateParameters(input);
        assertEquals(4, errors.size());
        assertTrue(errors.containsKey("lambda"));
        assertTrue(errors.containsKey("dropCount"));
        assertTrue(errors.containsKey("successProbability"));
        assertTrue(errors.containsKey("maxAttempts"));
        assertTrue(validator.hasMissingFields(input));
    }

    @Test
    void validationCases2To5_partialSingleGroupInputsRejectedCleanly() {
        // Case 2: Only lambda supplied without k
        CommunicationAnalysisInput onlyLambda = buildInput("3", "", "", "");
        Map<String, String> errLambda = validator.validateParameters(onlyLambda);
        assertEquals(1, errLambda.size());
        assertTrue(errLambda.containsKey("dropCount"));
        assertTrue(validator.hasMissingFields(onlyLambda));

        // Case 3: Only k supplied without lambda
        CommunicationAnalysisInput onlyK = buildInput("", "2", "", "");
        Map<String, String> errK = validator.validateParameters(onlyK);
        assertEquals(1, errK.size());
        assertTrue(errK.containsKey("lambda"));
        assertTrue(validator.hasMissingFields(onlyK));

        // Case 4: Only p supplied without n
        CommunicationAnalysisInput onlyP = buildInput("", "", "0.6", "");
        Map<String, String> errP = validator.validateParameters(onlyP);
        assertEquals(1, errP.size());
        assertTrue(errP.containsKey("maxAttempts"));
        assertTrue(validator.hasMissingFields(onlyP));

        // Case 5: Only n supplied without p
        CommunicationAnalysisInput onlyN = buildInput("", "", "", "5");
        Map<String, String> errN = validator.validateParameters(onlyN);
        assertEquals(1, errN.size());
        assertTrue(errN.containsKey("successProbability"));
        assertTrue(validator.hasMissingFields(onlyN));
    }

    @Test
    void validationCases6And7_completeGroupPlusPartialOtherGroupRejectedRatherThanIgnored() {
        // Case 6: Complete Poisson + incomplete Geometric (p supplied without n)
        CommunicationAnalysisInput compPoissonPartialGeom = buildInput("3", "2", "0.6", "");
        Map<String, String> err6 = validator.validateParameters(compPoissonPartialGeom);
        assertEquals(1, err6.size());
        assertTrue(err6.containsKey("maxAttempts"));
        assertTrue(validator.hasMissingFields(compPoissonPartialGeom));

        // Case 7: Complete Geometric + incomplete Poisson (lambda supplied without k)
        CommunicationAnalysisInput compGeomPartialPoisson = buildInput("3", "", "0.6", "5");
        Map<String, String> err7 = validator.validateParameters(compGeomPartialPoisson);
        assertEquals(1, err7.size());
        assertTrue(err7.containsKey("dropCount"));
        assertTrue(validator.hasMissingFields(compGeomPartialPoisson));
    }

    @Test
    void validationCases8To14_singleModeNumericBoundsRejectedWithoutRequiringOtherModel() {
        // Case 8: Negative lambda in Poisson-only mode
        CommunicationAnalysisInput negLambda = buildInput("-1", "2", "", "");
        Map<String, String> err8 = validator.validateParameters(negLambda);
        assertEquals(1, err8.size());
        assertTrue(err8.containsKey("lambda"));
        assertFalse(validator.hasMissingFields(negLambda), "Valid group is complete; not missing fields");

        // Case 9: Negative k in Poisson-only mode
        CommunicationAnalysisInput negK = buildInput("3", "-2", "", "");
        Map<String, String> err9 = validator.validateParameters(negK);
        assertEquals(1, err9.size());
        assertTrue(err9.containsKey("dropCount"));

        // Case 10: Decimal k in Poisson-only mode
        CommunicationAnalysisInput decK = buildInput("3", "2.5", "", "");
        Map<String, String> err10 = validator.validateParameters(decK);
        assertEquals(1, err10.size());
        assertTrue(err10.containsKey("dropCount"));

        // Case 11: p = 0 in Geometric-only mode
        CommunicationAnalysisInput zeroP = buildInput("", "", "0", "5");
        Map<String, String> err11 = validator.validateParameters(zeroP);
        assertEquals(1, err11.size());
        assertTrue(err11.containsKey("successProbability"));

        // Case 12: p > 1 in Geometric-only mode
        CommunicationAnalysisInput highP = buildInput("", "", "1.2", "5");
        Map<String, String> err12 = validator.validateParameters(highP);
        assertEquals(1, err12.size());
        assertTrue(err12.containsKey("successProbability"));

        // Case 13: n = 0 in Geometric-only mode
        CommunicationAnalysisInput zeroN = buildInput("", "", "0.6", "0");
        Map<String, String> err13 = validator.validateParameters(zeroN);
        assertEquals(1, err13.size());
        assertTrue(err13.containsKey("maxAttempts"));

        // Case 14: Decimal n in Geometric-only mode
        CommunicationAnalysisInput decN = buildInput("", "", "0.6", "3.5");
        Map<String, String> err14 = validator.validateParameters(decN);
        assertEquals(1, err14.size());
        assertTrue(err14.containsKey("maxAttempts"));
    }

    @Test
    void dashboardServiceThrowsWhenRequiredResultMissingForMode() {
        AnalysisDashboardService service = new AnalysisDashboardService(new PoissonService(), new GeometricService());
        assertThrows(IllegalArgumentException.class, () ->
                service.buildDashboardView(AnalysisType.POISSON_ONLY, buildInput("3", "2", "", ""), null, null));
        assertThrows(IllegalArgumentException.class, () ->
                service.buildDashboardView(AnalysisType.GEOMETRIC_ONLY, buildInput("", "", "0.6", "5"), null, null));
        assertThrows(IllegalArgumentException.class, () ->
                service.buildDashboardView(AnalysisType.COMBINED, buildInput("3", "2", "0.6", "5"),
                        new PoissonService().calculate(3.0, 2L), null));
    }

    // -------------------------------------------------------------------------
    // Helpers & Tracking Service Spies
    // -------------------------------------------------------------------------

    private CommunicationAnalysisInput buildInput(
            String lambda, String dropCount, String successProbability, String maxAttempts) {
        CommunicationAnalysisInput input = new CommunicationAnalysisInput();
        input.setLambda(lambda);
        input.setDropCount(dropCount);
        input.setSuccessProbability(successProbability);
        input.setMaxAttempts(maxAttempts);
        return input;
    }

    private PdfReportRequest buildPdfRequest(
            String lambda, String dropCount, String successProbability, String maxAttempts, AnalysisType mode) {
        PdfReportRequest req = new PdfReportRequest();
        req.setLambda(lambda);
        req.setDropCount(dropCount);
        req.setSuccessProbability(successProbability);
        req.setMaxAttempts(maxAttempts);
        req.setAnalysisType(mode);
        return req;
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

    static class TrackingPoissonService extends PoissonService {
        int calculateInputCalls = 0;

        @Override
        public PoissonResult calculate(CommunicationAnalysisInput input) {
            calculateInputCalls++;
            return super.calculate(input);
        }

        void reset() {
            calculateInputCalls = 0;
        }
    }

    static class TrackingGeometricService extends GeometricService {
        int calculateInputCalls = 0;

        @Override
        public GeometricResult calculate(CommunicationAnalysisInput input) {
            calculateInputCalls++;
            return super.calculate(input);
        }

        void reset() {
            calculateInputCalls = 0;
        }
    }
}
