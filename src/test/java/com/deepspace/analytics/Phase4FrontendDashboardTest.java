package com.deepspace.analytics;

import com.deepspace.analytics.controller.AnalysisController;
import com.deepspace.analytics.controller.HomeController;
import com.deepspace.analytics.model.AnalysisDashboardView;
import com.deepspace.analytics.model.AnalysisType;
import com.deepspace.analytics.model.CommunicationAnalysisInput;
import com.deepspace.analytics.model.GeometricResult;
import com.deepspace.analytics.model.PoissonResult;
import com.deepspace.analytics.service.AnalysisDashboardService;
import com.deepspace.analytics.service.GeometricService;
import com.deepspace.analytics.service.PoissonService;
import com.deepspace.analytics.validation.AnalysisInputValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Automated verification for Phase 4:
 * 1. Flexible input screen & confirmation modal dialog markup
 * 2. Homepage eyebrow badge update and duplicate "Start Analysis" navbar removal
 * 3. Results dashboard 4-Section layout across POISSON_ONLY, GEOMETRIC_ONLY, and COMBINED modes
 * 4. Module-integrated graphs (Graph 1 in Module 1, Graph 2 in Module 2, Graph 3 in Module 3)
 * 5. Responsive side-by-side grid layout for Module 2 and Module 3
 * 6. Target reliability milestones (90%, 95%, 99%)
 * 7. Non-redundant content presentation
 */
class Phase4FrontendDashboardTest {

    private PoissonService poissonService;
    private GeometricService geometricService;
    private AnalysisDashboardService dashboardService;
    private AnalysisInputValidator validator;
    private AnalysisController analysisController;
    private HomeController homeController;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        poissonService = new PoissonService();
        geometricService = new GeometricService();
        dashboardService = new AnalysisDashboardService(poissonService, geometricService);
        validator = new AnalysisInputValidator();
        objectMapper = new ObjectMapper();
        analysisController = new AnalysisController(
                validator, poissonService, geometricService, dashboardService, objectMapper);
        homeController = new HomeController();
    }

    // =========================================================================
    // 1. Homepage & Navigation Updates
    // =========================================================================

    @Test
    @DisplayName("Homepage & Navbar: badge updated, 'College Mathematics Project' removed, duplicate nav button removed")
    void homepageAndNavbarUpdatedProperly() throws Exception {
        Path homePath = Path.of("src/main/resources/templates/index.html");
        String homeHtml = Files.readString(homePath);

        // Eyebrow badge check
        assertTrue(homeHtml.contains("Deep-Space Communication · Poisson &amp; Geometric Modelling")
                        || homeHtml.contains("Deep-Space Communication · Poisson & Geometric Modelling"),
                "Homepage eyebrow badge must display 'Deep-Space Communication · Poisson & Geometric Modelling'");
        assertFalse(homeHtml.contains("College Mathematics Project"),
                "Homepage must no longer contain 'College Mathematics Project'");

        // Hero CTA button must be retained
        assertTrue(homeHtml.contains("Start Analysis"),
                "Homepage hero must retain the primary 'Start Analysis' CTA button");
        assertTrue(homeHtml.contains("Explore Probability Models"),
                "Homepage must retain the secondary CTA button");

        // Navbar checks
        Path navPath = Path.of("src/main/resources/templates/fragments/navbar.html");
        String navHtml = Files.readString(navPath);
        assertTrue(navHtml.contains("Home"), "Navbar must contain 'Home'");
        assertTrue(navHtml.contains("About Project"), "Navbar must contain 'About Project'");
        assertFalse(navHtml.contains("nav-cta-item"),
                "Navbar must NOT contain duplicate 'Start Analysis' CTA item");
    }

    // =========================================================================
    // 2. Flexible Input Screen & Confirmation Modal Markup
    // =========================================================================

    @Test
    @DisplayName("Analysis input screen: contains flexible mode support and accessible confirmation modal")
    void analysisScreenContainsModeConfirmationModal() throws Exception {
        Path analysisPath = Path.of("src/main/resources/templates/analysis.html");
        String html = Files.readString(analysisPath);

        // Core form elements
        assertTrue(html.contains("id=\"communication-analysis-form\""), "Form must have id");
        assertTrue(html.contains("id=\"analysisType\""), "Hidden analysisType input must exist");
        assertTrue(html.contains("id=\"lambda\""), "Lambda input must exist");
        assertTrue(html.contains("id=\"dropCount\""), "DropCount input must exist");
        assertTrue(html.contains("id=\"successProbability\""), "SuccessProbability input must exist");
        assertTrue(html.contains("id=\"maxAttempts\""), "MaxAttempts input must exist");
        assertTrue(html.contains("id=\"calculate-results-btn\""), "Calculate Results button must exist");
        assertTrue(html.contains("id=\"reset-inputs-btn\""), "Reset button must exist");

        // Action bar note reflects flexible modes
        assertTrue(html.contains("Supports Poisson-only") || html.contains("Poisson-only"),
                "Actions note must mention flexible Poisson-only and Geometric-only support");

        // Confirmation modal markup & accessibility
        assertTrue(html.contains("id=\"mode-confirmation-modal\""), "Confirmation modal must exist");
        assertTrue(html.contains("role=\"dialog\""), "Modal must have role='dialog'");
        assertTrue(html.contains("aria-modal=\"true\""), "Modal must have aria-modal='true'");
        assertTrue(html.contains("aria-labelledby=\"modal-title\""), "Modal must reference title");
        assertTrue(html.contains("aria-describedby=\"modal-description\""), "Modal must reference description");
        assertTrue(html.contains("id=\"modal-title\""), "Modal title element must exist");
        assertTrue(html.contains("id=\"modal-description\""), "Modal description element must exist");
        assertTrue(html.contains("id=\"modal-mode-badge\""), "Modal mode badge must exist");
        assertTrue(html.contains("id=\"modal-params-summary\""), "Modal parameter summary container must exist");
        assertTrue(html.contains("id=\"modal-cancel-btn\""), "Modal cancel button must exist");
        assertTrue(html.contains("id=\"modal-confirm-btn\""), "Modal confirm button must exist");
    }

    // =========================================================================
    // 3. Results Dashboard: 4-Section Template Architecture
    // =========================================================================

    @Test
    @DisplayName("Results Dashboard template contains 4 main sections and side-by-side grid layout")
    void resultsDashboardTemplateStructure() throws Exception {
        Path resultsPath = Path.of("src/main/resources/templates/analysis-results.html");
        String html = Files.readString(resultsPath);

        // Section 1: Submitted Parameters
        assertTrue(html.contains("id=\"analysis-summary\""));
        assertTrue(html.contains("Section 1 · Submitted Parameters"));
        assertTrue(html.contains("param-chip--poisson"));
        assertTrue(html.contains("param-chip--geometric"));

        // Section 2: Module 1 · Poisson Distribution Results
        assertTrue(html.contains("id=\"poisson-results\""));
        assertTrue(html.contains("Section 2 · Module 1: Poisson Distribution"));
        assertTrue(html.contains("th:if=\"${dashboard.poissonAvailable}\""));
        // Graph 1 inside Module 1
        assertTrue(html.contains("id=\"poisson-chart\""));

        // Two-column Combined Grid container for Module 1 & Module 2 (side-by-side in COMBINED mode)
        assertTrue(html.contains("dashboard-combined-grid"));
        assertTrue(html.contains("dashboard-combined-grid--two-col"));

        // Section 3: Module 2 · Geometric Distribution Results
        assertTrue(html.contains("id=\"geometric-results\""));
        assertTrue(html.contains("Section 3 · Module 2: Geometric Distribution"));
        assertTrue(html.contains("Attempt-by-Attempt Probability Table"));
        assertTrue(html.contains("id=\"geometric-chart\""));

        // Section 4: Module 3 · Communication Reliability
        assertTrue(html.contains("id=\"overall-reliability\""));
        assertTrue(html.contains("Section 4 · Module 3: Communication Reliability"));
        assertTrue(html.contains("id=\"reliability-chart\""));
        assertTrue(html.contains("Required Attempts for Target Reliability Levels"));

        // Practical Conclusion
        assertTrue(html.contains("id=\"result-interpretation\""));
        assertTrue(html.contains("Final Practical Result"));

        // Next Actions
        assertTrue(html.contains("id=\"dashboard-actions\""));
        assertTrue(html.contains("id=\"new-analysis-btn\""));
        assertTrue(html.contains("id=\"back-to-home-btn\""));
        assertTrue(html.contains("id=\"download-report-btn\""));
    }

    // =========================================================================
    // 4. Controller & Dashboard Execution: All Three Modes
    // =========================================================================

    @Test
    @DisplayName("Controller execution for POISSON_ONLY, GEOMETRIC_ONLY, and COMBINED modes")
    void controllerExecutionAcrossAllThreeModes() {
        // Mode 1: POISSON_ONLY
        CommunicationAnalysisInput pInput = new CommunicationAnalysisInput();
        pInput.setLambda("3");
        pInput.setDropCount("2");
        BeanPropertyBindingResult pResult = new BeanPropertyBindingResult(pInput, "analysisInput");
        ExtendedModelMap pModel = new ExtendedModelMap();

        String pView = analysisController.submitAnalysisForm(pInput, pResult, pModel);
        assertEquals("analysis-results", pView);
        assertEquals(AnalysisType.POISSON_ONLY, pModel.getAttribute("analysisType"));
        AnalysisDashboardView pDash = (AnalysisDashboardView) pModel.getAttribute("dashboard");
        assertNotNull(pDash);
        assertTrue(pDash.isPoissonAvailable());
        assertFalse(pDash.isGeometricAvailable());
        assertFalse(pDash.isReliabilityAvailable());
        assertNotNull(pDash.getPoisson());
        assertNull(pDash.getGeometric());
        assertNull(pDash.getReliability());
        assertFalse(pDash.getCharts().getPoissonSeries().isEmpty());
        assertTrue(pDash.getCharts().getGeometricSeries().isEmpty());
        assertTrue(pDash.getCharts().getReliabilitySeries().isEmpty());

        // Mode 2: GEOMETRIC_ONLY
        CommunicationAnalysisInput gInput = new CommunicationAnalysisInput();
        gInput.setSuccessProbability("0.6");
        gInput.setMaxAttempts("5");
        BeanPropertyBindingResult gResult = new BeanPropertyBindingResult(gInput, "analysisInput");
        ExtendedModelMap gModel = new ExtendedModelMap();

        String gView = analysisController.submitAnalysisForm(gInput, gResult, gModel);
        assertEquals("analysis-results", gView);
        assertEquals(AnalysisType.GEOMETRIC_ONLY, gModel.getAttribute("analysisType"));
        AnalysisDashboardView gDash = (AnalysisDashboardView) gModel.getAttribute("dashboard");
        assertNotNull(gDash);
        assertFalse(gDash.isPoissonAvailable());
        assertTrue(gDash.isGeometricAvailable());
        assertTrue(gDash.isReliabilityAvailable());
        assertNull(gDash.getPoisson());
        assertNotNull(gDash.getGeometric());
        assertNotNull(gDash.getReliability());
        assertTrue(gDash.getCharts().getPoissonSeries().isEmpty());
        assertFalse(gDash.getCharts().getGeometricSeries().isEmpty());
        assertFalse(gDash.getCharts().getReliabilitySeries().isEmpty());
        // Verify Target Reliability Milestones populated
        assertEquals(3, gDash.getReliability().getTargetAttempts90());
        assertEquals(4, gDash.getReliability().getTargetAttempts95());
        assertEquals(6, gDash.getReliability().getTargetAttempts99());

        // Mode 3: COMBINED
        CommunicationAnalysisInput cInput = new CommunicationAnalysisInput();
        cInput.setLambda("3");
        cInput.setDropCount("2");
        cInput.setSuccessProbability("0.6");
        cInput.setMaxAttempts("5");
        BeanPropertyBindingResult cResult = new BeanPropertyBindingResult(cInput, "analysisInput");
        ExtendedModelMap cModel = new ExtendedModelMap();

        String cView = analysisController.submitAnalysisForm(cInput, cResult, cModel);
        assertEquals("analysis-results", cView);
        assertEquals(AnalysisType.COMBINED, cModel.getAttribute("analysisType"));
        AnalysisDashboardView cDash = (AnalysisDashboardView) cModel.getAttribute("dashboard");
        assertNotNull(cDash);
        assertTrue(cDash.isPoissonAvailable());
        assertTrue(cDash.isGeometricAvailable());
        assertTrue(cDash.isReliabilityAvailable());
        assertNotNull(cDash.getPoisson());
        assertNotNull(cDash.getGeometric());
        assertNotNull(cDash.getReliability());
        assertFalse(cDash.getCharts().getPoissonSeries().isEmpty());
        assertFalse(cDash.getCharts().getGeometricSeries().isEmpty());
        assertFalse(cDash.getCharts().getReliabilitySeries().isEmpty());
        assertEquals(3, cDash.getReliability().getTargetAttempts90());
        assertEquals(4, cDash.getReliability().getTargetAttempts95());
        assertEquals(6, cDash.getReliability().getTargetAttempts99());
    }

    // =========================================================================
    // 5. Target Reliability Milestone Calculations
    // =========================================================================

    @Test
    @DisplayName("Target Reliability Milestones: mathematically accurate for various p values")
    void targetReliabilityMilestonesCalculation() {
        // Case A: p = 0.6
        assertEquals(1, AnalysisDashboardService.calculateTargetAttempts(0.6, 0.50));
        assertEquals(3, AnalysisDashboardService.calculateTargetAttempts(0.6, 0.90));
        assertEquals(4, AnalysisDashboardService.calculateTargetAttempts(0.6, 0.95));
        assertEquals(6, AnalysisDashboardService.calculateTargetAttempts(0.6, 0.99));

        // Case B: p = 0.2
        assertEquals(11, AnalysisDashboardService.calculateTargetAttempts(0.2, 0.90));
        assertEquals(14, AnalysisDashboardService.calculateTargetAttempts(0.2, 0.95));
        assertEquals(21, AnalysisDashboardService.calculateTargetAttempts(0.2, 0.99));

        // Case C: p = 1.0 (deterministic success)
        assertEquals(1, AnalysisDashboardService.calculateTargetAttempts(1.0, 0.90));
        assertEquals(1, AnalysisDashboardService.calculateTargetAttempts(1.0, 0.95));
        assertEquals(1, AnalysisDashboardService.calculateTargetAttempts(1.0, 0.99));

        // Cumulative reliability calculation bounds
        assertEquals(1.0, AnalysisDashboardService.calculateCumulativeReliability(1.0, 1), 1e-9);
        assertEquals(0.936, AnalysisDashboardService.calculateCumulativeReliability(0.6, 3), 1e-9);
        assertEquals(0.9744, AnalysisDashboardService.calculateCumulativeReliability(0.6, 4), 1e-9);
        assertEquals(0.995904, AnalysisDashboardService.calculateCumulativeReliability(0.6, 6), 1e-9);
    }
}
