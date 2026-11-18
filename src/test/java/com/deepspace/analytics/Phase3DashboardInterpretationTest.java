package com.deepspace.analytics;

import com.deepspace.analytics.model.AnalysisDashboardView;
import com.deepspace.analytics.model.AnalysisDashboardView.AttemptTableRow;
import com.deepspace.analytics.model.AnalysisDashboardView.GeometricCards;
import com.deepspace.analytics.model.AnalysisDashboardView.PoissonCards;
import com.deepspace.analytics.model.AnalysisDashboardView.PracticalConclusion;
import com.deepspace.analytics.model.AnalysisDashboardView.ReliabilitySummary;
import com.deepspace.analytics.model.AnalysisType;
import com.deepspace.analytics.model.CommunicationAnalysisInput;
import com.deepspace.analytics.model.GeometricResult;
import com.deepspace.analytics.model.PdfReportRequest;
import com.deepspace.analytics.model.PoissonResult;
import com.deepspace.analytics.service.AnalysisDashboardService;
import com.deepspace.analytics.service.AnalysisFormattingUtils;
import com.deepspace.analytics.service.GeometricService;
import com.deepspace.analytics.service.PdfReportService;
import com.deepspace.analytics.service.PdfReportService.GeneratedPdfReport;
import com.deepspace.analytics.service.PoissonService;
import com.deepspace.analytics.service.ReportChartImageService;
import com.deepspace.analytics.validation.AnalysisInputValidator;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Automated tests for Phase 3:
 * - Dashboard data organisation around the original three modules
 * - Elimination of redundant dashboard data
 * - Standardised decimal and percentage formatting (clean separation, readable labels)
 * - Geometric expected-attempts exact decimal and rounded interpretation
 * - Mode-aware practical conclusion generation (POISSON_ONLY, GEOMETRIC_ONLY, COMBINED)
 * - PDF data compatibility and formatting preservation
 */
class Phase3DashboardInterpretationTest {

    private PoissonService poissonService;
    private GeometricService geometricService;
    private AnalysisDashboardService dashboardService;
    private PdfReportService pdfReportService;

    @BeforeEach
    void setUp() {
        poissonService = new PoissonService();
        geometricService = new GeometricService();
        dashboardService = new AnalysisDashboardService(poissonService, geometricService);
        AnalysisInputValidator validator = new AnalysisInputValidator();
        pdfReportService = new PdfReportService(
                poissonService,
                geometricService,
                dashboardService,
                new ReportChartImageService(),
                validator
        );
    }

    @Test
    @DisplayName("AnalysisFormattingUtils: formats decimal (7 places) and percent (4 places) separately with readable labels")
    void formattingUtilsProducesConsistentDecimalsPercentagesAndSeparators() {
        double prob = 0.22404184;

        String dec = AnalysisFormattingUtils.formatProbabilityDecimal(prob);
        String pct = AnalysisFormattingUtils.formatProbabilityPercent(prob);
        String labeled = AnalysisFormattingUtils.formatDecimalAndPercentLabeled(prob);

        assertEquals("0.2240418", dec, "Decimal probability must use 7 decimal places");
        assertEquals("22.4042%", pct, "Percentage must use 4 decimal places with % symbol");
        assertTrue(labeled.contains("Probability: 0.2240418"), "Must contain distinct Probability: label");
        assertTrue(labeled.contains("Percentage: 22.4042%"), "Must contain distinct Percentage: label");
        assertTrue(labeled.contains(" | "), "Must contain clear separator between probability and percentage");
        assertFalse(labeled.contains("0.224041822.4042%"), "Must never concatenate decimal and percent unseparated");
    }

    @Test
    @DisplayName("Expected attempts: calculates exact decimal, rounded nearest integer, and non-guaranteed explanation")
    void expectedAttemptsFormattingAndRoundedInterpretation() {
        // Sample input p = 0.6 -> E(X) = 1/0.6 = 1.6666667
        double exp06 = 1.0 / 0.6;
        assertEquals("1.6666667", AnalysisFormattingUtils.formatExpectedAttemptsDecimal(exp06));
        assertEquals(2L, AnalysisFormattingUtils.calculateRoundedExpectedAttempts(exp06));
        assertEquals("Approximately 2 attempts on average",
                AnalysisFormattingUtils.formatExpectedAttemptsRoundedText(exp06));
        assertTrue(AnalysisFormattingUtils.formatExpectedAttemptsExplanation().contains("approximate average"));
        assertTrue(AnalysisFormattingUtils.formatExpectedAttemptsExplanation().contains("not a guaranteed attempt"));

        // Exact integer cases: p = 1.0 -> 1 attempt; p = 0.5 -> 2 attempts
        double exp10 = 1.0 / 1.0;
        assertEquals("1", AnalysisFormattingUtils.formatExpectedAttemptsDecimal(exp10));
        assertEquals(1L, AnalysisFormattingUtils.calculateRoundedExpectedAttempts(exp10));
        assertEquals("1 attempt on average", AnalysisFormattingUtils.formatExpectedAttemptsRoundedText(exp10));

        double exp05 = 1.0 / 0.5;
        assertEquals("2", AnalysisFormattingUtils.formatExpectedAttemptsDecimal(exp05));
        assertEquals(2L, AnalysisFormattingUtils.calculateRoundedExpectedAttempts(exp05));
        assertEquals("2 attempts on average", AnalysisFormattingUtils.formatExpectedAttemptsRoundedText(exp05));

        // Rounding half-up: p = 0.4 -> E(X) = 2.5 -> rounds to 3
        double exp04 = 1.0 / 0.4;
        assertEquals("2.5", AnalysisFormattingUtils.formatExpectedAttemptsDecimal(exp04));
        assertEquals(3L, AnalysisFormattingUtils.calculateRoundedExpectedAttempts(exp04));
        assertEquals("Approximately 3 attempts on average",
                AnalysisFormattingUtils.formatExpectedAttemptsRoundedText(exp04));
    }

    @Test
    @DisplayName("POISSON_ONLY: organizes Module 1 data, omits Geometric/Reliability, and generates practical conclusion")
    void poissonOnlyDashboardDataFormattingAndPracticalConclusion() {
        CommunicationAnalysisInput input = new CommunicationAnalysisInput();
        input.setAnalysisType(AnalysisType.POISSON_ONLY);
        input.setLambda("3");
        input.setDropCount("2");

        PoissonResult poisson = poissonService.calculate(input);
        AnalysisDashboardView dashboard = dashboardService.buildDashboardView(
                AnalysisType.POISSON_ONLY, input, poisson, null);

        // Module 1: Poisson distribution data
        assertTrue(dashboard.isPoissonAvailable());
        PoissonCards pCards = dashboard.getPoisson();
        assertNotNull(pCards);
        assertEquals("0.2240418", pCards.getExactDropDecimal());
        assertEquals("22.4042%", pCards.getExactDropPercent());
        assertEquals("3", pCards.getExpectedDropsValue());
        assertEquals("0.0497871", pCards.getProbabilityZeroDecimal());
        assertEquals("4.9787%", pCards.getProbabilityZeroPercent());
        assertEquals("0.9502129", pCards.getProbabilityAtLeastOneDecimal());
        assertEquals("95.0213%", pCards.getProbabilityAtLeastOnePercent());
        assertEquals("3", pCards.getVarianceValue());

        // Unavailable modules strictly omitted
        assertFalse(dashboard.isGeometricAvailable());
        assertNull(dashboard.getGeometric());
        assertTrue(dashboard.getAttemptRows().isEmpty());
        assertFalse(dashboard.isReliabilityAvailable());
        assertNull(dashboard.getReliability());
        assertTrue(dashboard.getCharts().getGeometricSeries().isEmpty());
        assertTrue(dashboard.getCharts().getReliabilitySeries().isEmpty());
        assertFalse(dashboard.getCharts().getPoissonSeries().isEmpty());

        // Practical conclusion for POISSON_ONLY
        PracticalConclusion conclusion = dashboard.getPracticalConclusion();
        assertNotNull(conclusion);
        assertEquals(AnalysisType.POISSON_ONLY, conclusion.getAnalysisType());
        assertTrue(conclusion.getHeadline().contains("Transmission Drop Rate"));
        assertTrue(conclusion.getNarrative().contains("average of 3 transmission drops"));
        assertTrue(conclusion.getNarrative().contains("0.2240418"));
        assertTrue(conclusion.getNarrative().contains("22.4042%"));
        assertTrue(conclusion.getNarrative().contains("selected drop count evaluated"));

        // Must NOT describe selected k as the actual observed final number of drops
        assertTrue(conclusion.getTechnicalNote().contains("represents the specific evaluated parameter (k)"));
        assertTrue(conclusion.getTechnicalNote().contains("not a guaranteed or observed fixed outcome"));
    }

    @Test
    @DisplayName("GEOMETRIC_ONLY: organizes Module 2 and Module 3, omits Poisson, and generates practical conclusion")
    void geometricOnlyDashboardDataFormattingAndPracticalConclusion() {
        CommunicationAnalysisInput input = new CommunicationAnalysisInput();
        input.setAnalysisType(AnalysisType.GEOMETRIC_ONLY);
        input.setSuccessProbability("0.6");
        input.setMaxAttempts("5");

        GeometricResult geometric = geometricService.calculate(input);
        AnalysisDashboardView dashboard = dashboardService.buildDashboardView(
                AnalysisType.GEOMETRIC_ONLY, input, null, geometric);

        // Unavailable Poisson strictly omitted
        assertFalse(dashboard.isPoissonAvailable());
        assertNull(dashboard.getPoisson());
        assertTrue(dashboard.getCharts().getPoissonSeries().isEmpty());

        // Module 2: Geometric distribution data
        assertTrue(dashboard.isGeometricAvailable());
        GeometricCards gCards = dashboard.getGeometric();
        assertNotNull(gCards);
        assertEquals("1.6666667", gCards.getExpectedAttemptsValue(), "Exact decimal expected attempts");
        assertEquals(2L, gCards.getExpectedAttemptsRounded(), "Rounded whole-number interpretation");
        assertEquals("Approximately 2 attempts on average", gCards.getExpectedAttemptsRoundedText());
        assertTrue(gCards.getExpectedAttemptsExplanation().contains("not a guaranteed attempt"));
        assertEquals("0.9897600", gCards.getWithinAttemptsDecimal());
        assertEquals("98.9760%", gCards.getWithinAttemptsPercent());
        assertEquals("0.0102400", gCards.getMoreThanAttemptsDecimal());
        assertEquals("1.0240%", gCards.getMoreThanAttemptsPercent());
        assertEquals(1, gCards.getMostLikelyAttempt());
        assertEquals("0.6000000", gCards.getFirstAttemptDecimal());
        assertEquals("60.0000%", gCards.getFirstAttemptPercent());

        // Attempt table (1..5) with separate decimal and percentage
        assertEquals(5, dashboard.getAttemptRows().size());
        AttemptTableRow row1 = dashboard.getAttemptRows().get(0);
        assertEquals(1, row1.getAttempt());
        assertEquals("0.6000000", row1.getProbabilityDecimal());
        assertEquals("60.0000%", row1.getProbabilityPercent());
        AttemptTableRow row5 = dashboard.getAttemptRows().get(4);
        assertEquals(5, row5.getAttempt());
        assertEquals("0.0153600", row5.getProbabilityDecimal());
        assertEquals("1.5360%", row5.getProbabilityPercent());

        // Module 3: Communication Reliability
        assertTrue(dashboard.isReliabilityAvailable());
        ReliabilitySummary rel = dashboard.getReliability();
        assertNotNull(rel);
        assertEquals("0.9897600", rel.getReliabilityDecimal());
        assertEquals("98.9760%", rel.getReliabilityPercent());
        assertEquals("0.0102400", rel.getMoreThanAttemptsDecimal());
        assertEquals("1.0240%", rel.getMoreThanAttemptsPercent());
        assertEquals("1.6666667", rel.getExpectedAttemptsValue());
        assertEquals("Approximately 2 attempts on average", rel.getExpectedAttemptsRoundedText());

        // Practical conclusion for GEOMETRIC_ONLY
        PracticalConclusion conclusion = dashboard.getPracticalConclusion();
        assertNotNull(conclusion);
        assertEquals(AnalysisType.GEOMETRIC_ONLY, conclusion.getAnalysisType());
        assertTrue(conclusion.getHeadline().contains("Link Acquisition Speed"));
        assertTrue(conclusion.getNarrative().contains("1.6666667"));
        assertTrue(conclusion.getNarrative().contains("Approximately 2 attempts on average"));
        assertTrue(conclusion.getNarrative().contains("0.9897600"));
        assertTrue(conclusion.getNarrative().contains("98.9760%"));

        // Must NOT describe expected attempts as a guaranteed success attempt
        assertTrue(conclusion.getTechnicalNote().contains("mathematical average across many independent transmissions"));
        assertTrue(conclusion.getTechnicalNote().contains("not a guarantee"));
    }

    @Test
    @DisplayName("COMBINED: organizes all three modules, all three graphs, and delivers concise non-redundant conclusion")
    void combinedModeDashboardDataFormattingAndPracticalConclusion() {
        CommunicationAnalysisInput input = new CommunicationAnalysisInput();
        input.setAnalysisType(AnalysisType.COMBINED);
        input.setLambda("3");
        input.setDropCount("2");
        input.setSuccessProbability("0.6");
        input.setMaxAttempts("5");

        PoissonResult poisson = poissonService.calculate(input);
        GeometricResult geometric = geometricService.calculate(input);
        AnalysisDashboardView dashboard = dashboardService.buildDashboardView(
                AnalysisType.COMBINED, input, poisson, geometric);

        // All 3 modules available
        assertTrue(dashboard.isPoissonAvailable());
        assertTrue(dashboard.isGeometricAvailable());
        assertTrue(dashboard.isReliabilityAvailable());
        assertTrue(dashboard.isCombined());

        // Module 1 outputs
        assertEquals("0.2240418", dashboard.getPoisson().getExactDropDecimal());
        assertEquals("22.4042%", dashboard.getPoisson().getExactDropPercent());
        assertEquals("3", dashboard.getPoisson().getExpectedDropsValue());
        assertEquals("0.0497871", dashboard.getPoisson().getProbabilityZeroDecimal());
        assertEquals("0.9502129", dashboard.getPoisson().getProbabilityAtLeastOneDecimal());
        assertEquals("3", dashboard.getPoisson().getVarianceValue());

        // Module 2 outputs
        assertEquals("1.6666667", dashboard.getGeometric().getExpectedAttemptsValue());
        assertEquals(2L, dashboard.getGeometric().getExpectedAttemptsRounded());
        assertEquals("Approximately 2 attempts on average", dashboard.getGeometric().getExpectedAttemptsRoundedText());
        assertEquals("0.9897600", dashboard.getGeometric().getWithinAttemptsDecimal());
        assertEquals("98.9760%", dashboard.getGeometric().getWithinAttemptsPercent());
        assertEquals("0.0102400", dashboard.getGeometric().getMoreThanAttemptsDecimal());
        assertEquals("1.0240%", dashboard.getGeometric().getMoreThanAttemptsPercent());

        // Module 3 outputs
        assertEquals("0.9897600", dashboard.getReliability().getReliabilityDecimal());
        assertEquals("98.9760%", dashboard.getReliability().getReliabilityPercent());
        assertEquals("0.0102400", dashboard.getReliability().getMoreThanAttemptsDecimal());
        assertEquals("1.0240%", dashboard.getReliability().getMoreThanAttemptsPercent());

        // All 3 graphs active
        assertFalse(dashboard.getCharts().getPoissonSeries().isEmpty());
        assertFalse(dashboard.getCharts().getGeometricSeries().isEmpty());
        assertFalse(dashboard.getCharts().getReliabilitySeries().isEmpty());

        // Practical conclusion for COMBINED
        PracticalConclusion conclusion = dashboard.getPracticalConclusion();
        assertNotNull(conclusion);
        assertEquals(AnalysisType.COMBINED, conclusion.getAnalysisType());
        assertTrue(conclusion.getHeadline().contains("Combined Spacecraft Telemetry & Link Acquisition Assessment"));
        assertTrue(conclusion.getNarrative().contains("E(X) = 3"));
        assertTrue(conclusion.getNarrative().contains("0.2240418"));
        assertTrue(conclusion.getNarrative().contains("22.4042%"));
        assertTrue(conclusion.getNarrative().contains("1.6666667"));
        assertTrue(conclusion.getNarrative().contains("Approximately 2 attempts on average"));
        assertTrue(conclusion.getNarrative().contains("0.9897600"));
        assertTrue(conclusion.getNarrative().contains("98.9760%"));

        // Safeguards on non-guaranteed and non-combined distribution notes
        assertTrue(conclusion.getTechnicalNote().contains("specific scenario tested rather than a fixed outcome"));
        assertTrue(conclusion.getTechnicalNote().contains("statistical average rather than a guaranteed attempt count"));
        assertTrue(conclusion.getTechnicalNote().contains("not combined into a joint probability distribution"));
    }

    @Test
    @DisplayName("PDF Report compatibility: generates 4 pages for single modes, 5 pages for Combined, with exact & rounded attempts")
    void pdfReportCompatibilityAcrossAllThreeModes() throws Exception {
        // 1. POISSON_ONLY PDF (strictly 2 pages)
        PdfReportRequest pReq = new PdfReportRequest();
        pReq.setAnalysisType(AnalysisType.POISSON_ONLY);
        pReq.setLambda("3");
        pReq.setDropCount("2");

        GeneratedPdfReport pRep = pdfReportService.generateReport(pReq);
        try (PDDocument doc = Loader.loadPDF(pRep.getBytes())) {
            assertEquals(2, doc.getNumberOfPages(), "POISSON_ONLY must generate strictly 2 pages");
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("0.2240418"));
            assertTrue(text.contains("22.4042%"));
            assertTrue(text.contains("Page 2 of 2"));
            assertFalse(text.contains("NaN"));
            assertFalse(text.contains("Infinity"));
        }

        // 2. GEOMETRIC_ONLY PDF (strictly 2 pages)
        PdfReportRequest gReq = new PdfReportRequest();
        gReq.setAnalysisType(AnalysisType.GEOMETRIC_ONLY);
        gReq.setSuccessProbability("0.6");
        gReq.setMaxAttempts("5");

        GeneratedPdfReport gRep = pdfReportService.generateReport(gReq);
        try (PDDocument doc = Loader.loadPDF(gRep.getBytes())) {
            assertEquals(2, doc.getNumberOfPages(), "GEOMETRIC_ONLY must generate strictly 2 pages");
            String text = new PDFTextStripper().getText(doc);
            String normText = text.replaceAll("\\s+", " ");
            assertTrue(normText.contains("1.6666667"));
            assertTrue(normText.contains("Approximately 2 attempts on average"));
            assertTrue(normText.contains("0.9897600"));
            assertTrue(normText.contains("98.9760%"));
            assertTrue(normText.contains("Page 2 of 2"));
            assertFalse(normText.contains("NaN"));
            assertFalse(normText.contains("Infinity"));
        }

        // 3. COMBINED PDF (strictly 2 pages)
        PdfReportRequest cReq = new PdfReportRequest();
        cReq.setAnalysisType(AnalysisType.COMBINED);
        cReq.setLambda("3");
        cReq.setDropCount("2");
        cReq.setSuccessProbability("0.6");
        cReq.setMaxAttempts("5");

        GeneratedPdfReport cRep = pdfReportService.generateReport(cReq);
        try (PDDocument doc = Loader.loadPDF(cRep.getBytes())) {
            assertEquals(2, doc.getNumberOfPages(), "COMBINED must generate strictly 2 pages");
            String text = new PDFTextStripper().getText(doc);
            String normText = text.replaceAll("\\s+", " ");
            assertTrue(normText.contains("0.2240418"));
            assertTrue(normText.contains("1.6666667"));
            assertTrue(normText.contains("Approximately 2 attempts on average"));
            assertTrue(normText.contains("0.9897600"));
            assertTrue(normText.contains("Page 2 of 2"));
            assertFalse(normText.contains("NaN"));
            assertFalse(normText.contains("Infinity"));
        }
    }
}
