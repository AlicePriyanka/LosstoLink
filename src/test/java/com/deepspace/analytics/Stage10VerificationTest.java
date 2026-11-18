package com.deepspace.analytics;

import com.deepspace.analytics.controller.AnalysisController;
import com.deepspace.analytics.controller.HomeController;
import com.deepspace.analytics.controller.ReportController;
import com.deepspace.analytics.model.AnalysisDashboardView;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class Stage10VerificationTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private PoissonService poissonService;
    private GeometricService geometricService;
    private AnalysisDashboardService dashboardService;
    private PdfReportService pdfReportService;
    private ReportChartImageService reportChartImageService;

    @Controller
    static class TestErrorController {
        @GetMapping("/error")
        public String handleError(HttpServletRequest request, HttpServletResponse response, Model model) {
            response.setStatus(404);
            model.addAttribute("status", 404);
            return "error";
        }
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        poissonService = new PoissonService();
        geometricService = new GeometricService();
        dashboardService = new AnalysisDashboardService(poissonService, geometricService);
        reportChartImageService = new ReportChartImageService();
        AnalysisInputValidator validator = new AnalysisInputValidator();
        pdfReportService = new PdfReportService(
                poissonService, geometricService, dashboardService, reportChartImageService, validator);

        AnalysisController analysisController = new AnalysisController(
                validator, poissonService, geometricService, dashboardService, objectMapper);
        ReportController reportController = new ReportController(validator, pdfReportService);
        HomeController homeController = new HomeController();

        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("templates/");
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode(TemplateMode.HTML);
        templateResolver.setCharacterEncoding("UTF-8");

        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(templateResolver);

        ThymeleafViewResolver viewResolver = new ThymeleafViewResolver();
        viewResolver.setTemplateEngine(templateEngine);
        viewResolver.setCharacterEncoding("UTF-8");

        mockMvc = MockMvcBuilders.standaloneSetup(
                        analysisController, reportController, homeController, new TestErrorController())
                .setViewResolvers(viewResolver)
                .build();
    }

    @Test
    void rejectsExtremeLengthAndPrecisionEdgeInputsAcrossFormAndPdf() throws Exception {
        String overlongTiny = "0." + "0".repeat(80) + "1";
        String overlongDigits = "9".repeat(70);

        // 1. Overlong strings, >1e9 lambda/k, p just above 1 (1.00000000000000001), n > 1000
        mockMvc.perform(post("/analysis/submit")
                        .param("lambda", "1000000001")
                        .param("dropCount", "1000000001")
                        .param("successProbability", "1.00000000000000001")
                        .param("maxAttempts", "1001"))
                .andExpect(status().isOk())
                .andExpect(view().name("analysis"))
                .andExpect(model().attributeHasFieldErrors(
                        "analysisInput", "lambda", "dropCount", "successProbability", "maxAttempts"))
                .andExpect(content().string(containsString("must not exceed 1,000,000,000")))
                .andExpect(content().string(containsString("must be less than or equal to 1")))
                .andExpect(content().string(containsString("must not exceed 1,000")));

        // 2. Overlong tiny p (< 1e-12) and overlong huge integer
        mockMvc.perform(post("/analysis/submit")
                        .param("lambda", overlongDigits)
                        .param("dropCount", overlongDigits)
                        .param("successProbability", overlongTiny)
                        .param("maxAttempts", overlongDigits))
                .andExpect(status().isOk())
                .andExpect(view().name("analysis"))
                .andExpect(model().attributeHasFieldErrors(
                        "analysisInput", "lambda", "dropCount", "successProbability", "maxAttempts"))
                .andExpect(content().string(containsString("at least 0.000000000001")));

        // 3. Rejects p below minimum supported precision in PDF endpoint
        Map<String, String> badPdf = new LinkedHashMap<>();
        badPdf.put("lambda", "3");
        badPdf.put("dropCount", "2");
        badPdf.put("successProbability", "0.0000000000005");
        badPdf.put("maxAttempts", "5");

        MvcResult pdfErrRes = mockMvc.perform(post("/analysis/report/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badPdf)))
                .andExpect(status().isBadRequest())
                .andReturn();
        JsonNode pdfErr = objectMapper.readTree(pdfErrRes.getResponse().getContentAsString());
        assertTrue(pdfErr.get("errors").has("successProbability"));
    }

    @Test
    void highScaleValidInputsRenderCleanDashboardDownsampledChartsSmallPeakPngsAndConcisePdf() throws Exception {
        CommunicationAnalysisInput input = new CommunicationAnalysisInput();
        input.setLambda("500000");
        input.setDropCount("500000");
        input.setSuccessProbability("0.0001");
        input.setMaxAttempts("500");

        PoissonResult poisson = poissonService.calculate(input);
        GeometricResult geometric = geometricService.calculate(input);

        assertTrue(Double.isFinite(poisson.getProbabilityOfExactDrops()) && poisson.getProbabilityOfExactDrops() > 0.0);
        assertEquals(500000.0, poisson.getExpectedDrops(), 1e-6);
        assertEquals(500, geometric.getAttemptProbabilities().size());

        AnalysisDashboardView dashboard = dashboardService.buildDashboardView(input, poisson, geometric);
        assertEquals(500, dashboard.getAttemptRows().size());
        assertEquals("0.0487730", dashboard.getAttemptRows().get(499).getCumulativeDecimal());
        assertTrue(dashboard.getCharts().getPoissonSeries().size() <= 35);
        assertTrue(dashboard.getCharts().getGeometricSeries().size() <= 60);
        assertTrue(dashboard.getCharts().getReliabilitySeries().size() <= 60);
        assertEquals(1, dashboard.getCharts().getGeometricSeries().get(0).getAttempt());
        assertEquals(500, dashboard.getCharts().getGeometricSeries()
                .get(dashboard.getCharts().getGeometricSeries().size() - 1).getAttempt());
        assertEquals(1, dashboard.getCharts().getReliabilitySeries().get(0).getAttempt());
        assertEquals(500, dashboard.getCharts().getReliabilitySeries()
                .get(dashboard.getCharts().getReliabilitySeries().size() - 1).getAttempt());

        // Java2D small-peak PNG charts should render cleanly without clipping or exception
        byte[] poissonPng = reportChartImageService.resolvePoissonChartPng(null, dashboard.getCharts());
        byte[] geometricPng = reportChartImageService.resolveGeometricChartPng(null, dashboard.getCharts());
        byte[] reliabilityPng = reportChartImageService.resolveReliabilityChartPng(null, dashboard.getCharts());
        BufferedImage pImg = ImageIO.read(new ByteArrayInputStream(poissonPng));
        BufferedImage gImg = ImageIO.read(new ByteArrayInputStream(geometricPng));
        BufferedImage rImg = ImageIO.read(new ByteArrayInputStream(reliabilityPng));
        assertNotNull(pImg);
        assertNotNull(gImg);
        assertNotNull(rImg);
        assertEquals(1120, pImg.getWidth());

        // Full SSR dashboard request
        mockMvc.perform(post("/analysis/submit")
                        .param("lambda", "500000")
                        .param("dropCount", "500000")
                        .param("successProbability", "0.0001")
                        .param("maxAttempts", "500"))
                .andExpect(status().isOk())
                .andExpect(view().name("analysis-results"))
                .andExpect(content().string(not(containsString("NaN"))))
                .andExpect(content().string(not(containsString("Infinity"))));

        // PDF summarizes 500-row table concisely into 5 clean pages and omits NaN/Infinity
        PdfReportRequest pdfReq = new PdfReportRequest();
        pdfReq.setLambda("500000");
        pdfReq.setDropCount("500000");
        pdfReq.setSuccessProbability("0.0001");
        pdfReq.setMaxAttempts("500");

        GeneratedPdfReport report = pdfReportService.generateReport(pdfReq);
        try (PDDocument pdf = Loader.loadPDF(report.getBytes())) {
            assertEquals(2, pdf.getNumberOfPages(), "Report must strictly have 2 pages");
            String fullText = new PDFTextStripper().getText(pdf);
            assertTrue(fullText.contains("Attempts 4 to 499"));
            assertTrue(fullText.contains("Attempt 500"));
            assertTrue(fullText.contains("Page 2 of 2"));
            assertFalse(fullText.contains("NaN"));
            assertFalse(fullText.contains("Infinity"));
        }
    }

    @Test
    void routeRedirectsAndCustomErrorPageWorkProperly() throws Exception {
        mockMvc.perform(get("/analysis/submit"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/analysis"));

        mockMvc.perform(get("/analysis/report/pdf"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/analysis"));

        mockMvc.perform(get("/error")
                        .accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/unknown-telemetry-page"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error"))
                .andExpect(content().string(containsString("Page Not Found")))
                .andExpect(content().string(containsString("Start Communication Analysis")))
                .andExpect(content().string(containsString("Back to Home")));
    }

    @Test
    void part8FullAcceptanceTestMatchesExactExpectedOutputsAndWorkflow() throws Exception {
        // Step 1 & 2: Home -> Input
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Stage 1"))));

        mockMvc.perform(get("/analysis"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Stage 3"))));

        // Step 3: Core inputs lambda=3, k=2, p=0.6, n=5
        mockMvc.perform(post("/analysis/submit")
                        .param("lambda", "3")
                        .param("dropCount", "2")
                        .param("successProbability", "0.6")
                        .param("maxAttempts", "5"))
                .andExpect(status().isOk())
                .andExpect(view().name("analysis-results"))
                // Poisson
                .andExpect(content().string(containsString("0.2240418")))
                .andExpect(content().string(containsString("0.0497871")))
                .andExpect(content().string(containsString("0.9502129")))
                // Geometric
                .andExpect(content().string(containsString("1.6666667")))
                .andExpect(content().string(containsString("0.9897600")))
                .andExpect(content().string(containsString("0.0102400")))
                .andExpect(content().string(containsString("0.6000000")))
                .andExpect(content().string(containsString("0.2400000")))
                .andExpect(content().string(containsString("0.0960000")))
                .andExpect(content().string(containsString("0.0384000")))
                .andExpect(content().string(containsString("0.0153600")))
                // Clean copy
                .andExpect(content().string(not(containsString("Stage 3"))))
                .andExpect(content().string(not(containsString("Stage 8"))));

        // Step 4: Download PDF Report and verify core outputs on 5-page PDF
        Map<String, String> pdfPayload = new LinkedHashMap<>();
        pdfPayload.put("lambda", "3");
        pdfPayload.put("dropCount", "2");
        pdfPayload.put("successProbability", "0.6");
        pdfPayload.put("maxAttempts", "5");

        MvcResult pdfResult = mockMvc.perform(post("/analysis/report/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pdfPayload)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        containsString("DeepSpace-Analytics-Report-lambda-3-k-2-p-0.6-n-5.pdf")))
                .andReturn();

        try (PDDocument pdf = Loader.loadPDF(pdfResult.getResponse().getContentAsByteArray())) {
            assertEquals(2, pdf.getNumberOfPages(), "PDF must strictly have 2 pages");
            String fullText = new PDFTextStripper().getText(pdf);
            assertTrue(fullText.contains("0.2240418"));
            assertTrue(fullText.contains("0.0497871"));
            assertTrue(fullText.contains("0.9502129"));
            assertTrue(fullText.contains("1.6666667"));
            assertTrue(fullText.contains("0.9897600"));
            assertTrue(fullText.contains("0.0102400"));
            assertTrue(fullText.contains("0.6000000"));
            assertTrue(fullText.contains("Page 2 of 2"));
            assertFalse(fullText.contains("Multi-Scenario"));
        }
    }
}
