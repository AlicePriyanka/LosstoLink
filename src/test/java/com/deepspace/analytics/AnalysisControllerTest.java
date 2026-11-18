package com.deepspace.analytics;

import com.deepspace.analytics.controller.AnalysisController;
import com.deepspace.analytics.controller.ReportController;
import com.deepspace.analytics.model.AnalysisDashboardView;
import com.deepspace.analytics.service.AnalysisDashboardService;
import com.deepspace.analytics.service.GeometricService;
import com.deepspace.analytics.service.PdfReportService;
import com.deepspace.analytics.service.PoissonService;
import com.deepspace.analytics.service.ReportChartImageService;
import com.deepspace.analytics.validation.AnalysisInputValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class AnalysisControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        AnalysisInputValidator validator = new AnalysisInputValidator();
        PoissonService poissonService = new PoissonService();
        GeometricService geometricService = new GeometricService();
        AnalysisDashboardService dashboardService = new AnalysisDashboardService(poissonService, geometricService);
        PdfReportService pdfReportService = new PdfReportService(
                poissonService, geometricService, dashboardService, new ReportChartImageService(), validator);

        AnalysisController analysisController = new AnalysisController(
                validator, poissonService, geometricService, dashboardService, objectMapper);
        ReportController reportController = new ReportController(validator, pdfReportService);

        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
        viewResolver.setPrefix("/templates/");
        viewResolver.setSuffix(".html");

        mockMvc = MockMvcBuilders.standaloneSetup(analysisController, reportController)
                .setViewResolvers(viewResolver)
                .build();
    }

    @Test
    void unifiedInputScreenLoadsCleanlyWithoutInitialErrors() throws Exception {
        mockMvc.perform(get("/analysis"))
                .andExpect(status().isOk())
                .andExpect(view().name("analysis"))
                .andExpect(model().attribute("activePage", "analysis"))
                .andExpect(model().attribute("hasServerErrors", false))
                .andExpect(model().attributeExists("analysisInput"));
    }

    @Test
    void case1_allFieldsEmptyFailsValidation() throws Exception {
        mockMvc.perform(post("/analysis/submit")
                        .param("lambda", "")
                        .param("dropCount", "")
                        .param("successProbability", "")
                        .param("maxAttempts", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("analysis"))
                .andExpect(model().attributeHasFieldErrors("analysisInput",
                        "lambda", "dropCount", "successProbability", "maxAttempts"))
                .andExpect(model().attribute("hasServerErrors", true))
                .andExpect(model().attribute("generalErrorMessage",
                        containsString("Please complete all required fields")));
    }

    @Test
    void case2_negativeLambdaFailsValidationAndPreservesInputs() throws Exception {
        MvcResult res = mockMvc.perform(post("/analysis/submit")
                        .param("lambda", "-1")
                        .param("dropCount", "2")
                        .param("successProbability", "0.6")
                        .param("maxAttempts", "5"))
                .andExpect(status().isOk())
                .andExpect(view().name("analysis"))
                .andExpect(model().attributeHasFieldErrors("analysisInput", "lambda"))
                .andExpect(model().attribute("hasServerErrors", true))
                .andReturn();

        assertEquals("-1", ((com.deepspace.analytics.model.CommunicationAnalysisInput)
                res.getModelAndView().getModel().get("analysisInput")).getLambda());
    }

    @Test
    void case3_negativeDropCountFailsValidation() throws Exception {
        mockMvc.perform(post("/analysis/submit")
                        .param("lambda", "3")
                        .param("dropCount", "-2")
                        .param("successProbability", "0.6")
                        .param("maxAttempts", "5"))
                .andExpect(status().isOk())
                .andExpect(view().name("analysis"))
                .andExpect(model().attributeHasFieldErrors("analysisInput", "dropCount"));
    }

    @Test
    void case4_decimalDropCountFailsValidation() throws Exception {
        mockMvc.perform(post("/analysis/submit")
                        .param("lambda", "3")
                        .param("dropCount", "2.5")
                        .param("successProbability", "0.6")
                        .param("maxAttempts", "5"))
                .andExpect(status().isOk())
                .andExpect(view().name("analysis"))
                .andExpect(model().attributeHasFieldErrors("analysisInput", "dropCount"));
    }

    @Test
    void case5_zeroSuccessProbabilityFailsValidation() throws Exception {
        mockMvc.perform(post("/analysis/submit")
                        .param("lambda", "3")
                        .param("dropCount", "2")
                        .param("successProbability", "0")
                        .param("maxAttempts", "5"))
                .andExpect(status().isOk())
                .andExpect(view().name("analysis"))
                .andExpect(model().attributeHasFieldErrors("analysisInput", "successProbability"));
    }

    @Test
    void case6_probabilityGreaterThanOneFailsValidation() throws Exception {
        mockMvc.perform(post("/analysis/submit")
                        .param("lambda", "3")
                        .param("dropCount", "2")
                        .param("successProbability", "1.2")
                        .param("maxAttempts", "5"))
                .andExpect(status().isOk())
                .andExpect(view().name("analysis"))
                .andExpect(model().attributeHasFieldErrors("analysisInput", "successProbability"));
    }

    @Test
    void case7_decimalMaxAttemptsFailsValidation() throws Exception {
        mockMvc.perform(post("/analysis/submit")
                        .param("lambda", "3")
                        .param("dropCount", "2")
                        .param("successProbability", "0.6")
                        .param("maxAttempts", "3.5"))
                .andExpect(status().isOk())
                .andExpect(view().name("analysis"))
                .andExpect(model().attributeHasFieldErrors("analysisInput", "maxAttempts"));
    }

    @Test
    void case8_zeroMaxAttemptsFailsValidation() throws Exception {
        mockMvc.perform(post("/analysis/submit")
                        .param("lambda", "3")
                        .param("dropCount", "2")
                        .param("successProbability", "0.6")
                        .param("maxAttempts", "0"))
                .andExpect(status().isOk())
                .andExpect(view().name("analysis"))
                .andExpect(model().attributeHasFieldErrors("analysisInput", "maxAttempts"));
    }

    @Test
    void case9_allFourInputsValidPassesAndRendersCompleteDashboard() throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/analysis/submit")
                        .param("lambda", "3")
                        .param("dropCount", "2")
                        .param("successProbability", "0.6")
                        .param("maxAttempts", "5"))
                .andExpect(status().isOk())
                .andExpect(view().name("analysis-results"))
                .andExpect(model().hasNoErrors())
                .andExpect(model().attribute("validationConfirmed", true))
                .andExpect(model().attributeExists("poissonResult"))
                .andExpect(model().attributeExists("geometricResult"))
                .andExpect(model().attributeExists("dashboard"))
                .andExpect(model().attributeExists("dashboardChartsJson"))
                .andReturn();

        AnalysisDashboardView dashboard = (AnalysisDashboardView) mvcResult.getModelAndView().getModel().get("dashboard");
        assertNotNull(dashboard);
        assertEquals("0.2240418", dashboard.getPoisson().getExactDropDecimal());
        assertEquals("22.4042%", dashboard.getPoisson().getExactDropPercent());
        assertEquals("0.0497871", dashboard.getPoisson().getProbabilityZeroDecimal());
        assertEquals("0.9502129", dashboard.getPoisson().getProbabilityAtLeastOneDecimal());
        assertEquals("1.6666667", dashboard.getGeometric().getExpectedAttemptsValue());
        assertEquals("0.9897600", dashboard.getGeometric().getWithinAttemptsDecimal());
        assertEquals("98.9760%", dashboard.getGeometric().getWithinAttemptsPercent());
        assertEquals("0.0102400", dashboard.getGeometric().getMoreThanAttemptsDecimal());
        assertEquals("1.0240%", dashboard.getGeometric().getMoreThanAttemptsPercent());
        assertEquals(5, dashboard.getAttemptRows().size());
        assertEquals("0.6000000", dashboard.getAttemptRows().get(0).getProbabilityDecimal());
        assertEquals("0.0153600", dashboard.getAttemptRows().get(4).getProbabilityDecimal());

        // Verify all 3 graphs active
        assertFalse(dashboard.getCharts().getPoissonSeries().isEmpty());
        assertEquals(5, dashboard.getCharts().getGeometricSeries().size());
        assertEquals(5, dashboard.getCharts().getReliabilitySeries().size());
    }

    @Test
    void alternativeValidInputsDynamicallyRecalculateDashboard() throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/analysis/submit")
                        .param("lambda", "0.5")
                        .param("dropCount", "1")
                        .param("successProbability", "0.4")
                        .param("maxAttempts", "3"))
                .andExpect(status().isOk())
                .andExpect(view().name("analysis-results"))
                .andExpect(model().hasNoErrors())
                .andReturn();

        AnalysisDashboardView dashboard = (AnalysisDashboardView) mvcResult.getModelAndView().getModel().get("dashboard");
        assertNotNull(dashboard);
        assertEquals("0.3032653", dashboard.getPoisson().getExactDropDecimal());
        assertEquals("0.6065307", dashboard.getPoisson().getProbabilityZeroDecimal());
        assertEquals("0.3934693", dashboard.getPoisson().getProbabilityAtLeastOneDecimal());
        assertEquals("2.5", dashboard.getGeometric().getExpectedAttemptsValue());
        assertEquals("0.7840000", dashboard.getGeometric().getWithinAttemptsDecimal());
        assertEquals("0.2160000", dashboard.getGeometric().getMoreThanAttemptsDecimal());
        assertEquals(3, dashboard.getAttemptRows().size());
    }

    @Test
    void textCharactersRejectedIndependentlyOnBackend() throws Exception {
        mockMvc.perform(post("/analysis/submit")
                        .param("lambda", "abc")
                        .param("dropCount", "2")
                        .param("successProbability", "0.6")
                        .param("maxAttempts", "5"))
                .andExpect(status().isOk())
                .andExpect(view().name("analysis"))
                .andExpect(model().attributeHasFieldErrors("analysisInput", "lambda"));
    }

    @Test
    void boundaryValidValuesPassValidation() throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/analysis/submit")
                        .param("lambda", "0")
                        .param("dropCount", "0")
                        .param("successProbability", "1")
                        .param("maxAttempts", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("analysis-results"))
                .andExpect(model().hasNoErrors())
                .andReturn();

        AnalysisDashboardView dashboard = (AnalysisDashboardView) mvcResult.getModelAndView().getModel().get("dashboard");
        assertNotNull(dashboard);
        assertEquals("1.0000000", dashboard.getPoisson().getExactDropDecimal());
        assertEquals("100.0000%", dashboard.getPoisson().getExactDropPercent());
    }

    @Test
    void stage9DownloadPdfReturnsValidFivePageReport() throws Exception {
        String payload = objectMapper.writeValueAsString(new java.util.LinkedHashMap<String, String>() {{
            put("lambda", "3");
            put("dropCount", "2");
            put("successProbability", "0.6");
            put("maxAttempts", "5");
        }});

        MvcResult pdfResult = mockMvc.perform(post("/analysis/report/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();

        String disposition = pdfResult.getResponse().getHeader("Content-Disposition");
        assertTrue(disposition != null && disposition.contains("DeepSpace-Analytics-Report-lambda-3-k-2-p-0.6-n-5.pdf"));

        byte[] pdfBytes = pdfResult.getResponse().getContentAsByteArray();
        assertTrue(pdfBytes.length > 10_000);
        assertEquals("%PDF-", new String(pdfBytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII));

        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
            assertEquals(2, pdf.getNumberOfPages());
            String fullText = new PDFTextStripper().getText(pdf);
            assertTrue(fullText.contains("DeepSpace Analytics"));
            assertTrue(fullText.contains("Deep-Space Communication Using Poisson and Geometric Models"));
            assertTrue(fullText.contains("0.2240418"));
            assertTrue(fullText.contains("0.0497871"));
            assertTrue(fullText.contains("0.9502129"));
            assertTrue(fullText.contains("1.6666667"));
            assertTrue(fullText.contains("0.9897600"));
            assertTrue(fullText.contains("0.0102400"));
            assertTrue(fullText.contains("Page 1 of 2"));
            assertTrue(fullText.contains("Page 2 of 2"));
            assertTrue(!fullText.contains("Multi-Scenario"));
        }
    }

    @Test
    void stage9DownloadPdfAlternativeInputsAndFormEncodedWork() throws Exception {
        MvcResult pdfResult = mockMvc.perform(post("/analysis/report/pdf")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("lambda", "0.5")
                        .param("dropCount", "1")
                        .param("successProbability", "0.4")
                        .param("maxAttempts", "3"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();

        try (PDDocument pdf = Loader.loadPDF(pdfResult.getResponse().getContentAsByteArray())) {
            assertEquals(2, pdf.getNumberOfPages());
            String fullText = new PDFTextStripper().getText(pdf);
            assertTrue(fullText.contains("0.3032653"));
            assertTrue(fullText.contains("0.6065307"));
            assertTrue(fullText.contains("0.3934693"));
            assertTrue(fullText.contains("2.5"));
            assertTrue(fullText.contains("0.7840000"));
            assertTrue(fullText.contains("0.2160000"));
            assertTrue(fullText.contains("0.1440000"));
            assertTrue(fullText.contains("Attempt 3"));
            assertTrue(!fullText.contains("0.2240418"));
        }
    }

    @Test
    void stage9DownloadPdfRejectsInvalidInputsGracefully() throws Exception {
        String invalidPayload = objectMapper.writeValueAsString(new java.util.LinkedHashMap<String, String>() {{
            put("lambda", "-1");
            put("dropCount", "2.5");
            put("successProbability", "0");
            put("maxAttempts", "0");
        }});

        MvcResult result = mockMvc.perform(post("/analysis/report/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode err = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(err.hasNonNull("generalError"));
        assertTrue(err.get("errors").has("lambda"));
        assertTrue(err.get("errors").has("dropCount"));
        assertTrue(err.get("errors").has("successProbability"));
        assertTrue(err.get("errors").has("maxAttempts"));
    }
}
