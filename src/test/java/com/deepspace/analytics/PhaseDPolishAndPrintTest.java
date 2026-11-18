package com.deepspace.analytics;

import com.deepspace.analytics.controller.AnalysisController;
import com.deepspace.analytics.controller.HomeController;
import com.deepspace.analytics.service.AnalysisDashboardService;
import com.deepspace.analytics.service.GeometricService;
import com.deepspace.analytics.service.PoissonService;
import com.deepspace.analytics.validation.AnalysisInputValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class PhaseDPolishAndPrintTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("templates/");
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode("HTML");
        templateResolver.setCharacterEncoding("UTF-8");

        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(templateResolver);

        ThymeleafViewResolver viewResolver = new ThymeleafViewResolver();
        viewResolver.setTemplateEngine(templateEngine);
        viewResolver.setCharacterEncoding("UTF-8");

        PoissonService poissonService = new PoissonService();
        GeometricService geometricService = new GeometricService();
        AnalysisDashboardService dashboardService = new AnalysisDashboardService(poissonService, geometricService);
        AnalysisInputValidator validator = new AnalysisInputValidator();

        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        HomeController homeController = new HomeController();
        AnalysisController analysisController = new AnalysisController(
                validator, poissonService, geometricService, dashboardService, objectMapper);

        this.mockMvc = MockMvcBuilders.standaloneSetup(homeController, analysisController)
                .setViewResolvers(viewResolver)
                .build();
    }

    @Test
    @DisplayName("Phase D.1: Floating Back-to-Top button is rendered with accessible attributes across pages")
    void testBackToTopButtonRenderedAcrossPages() throws Exception {
        // 1. Homepage
        MvcResult homeResult = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn();
        String homeHtml = homeResult.getResponse().getContentAsString();
        assertTrue(homeHtml.contains("id=\"back-to-top-btn\""), "Homepage must include back-to-top button ID");
        assertTrue(homeHtml.contains("aria-label=\"Back to top\""), "Homepage back-to-top must have accessible label");
        assertTrue(homeHtml.contains("class=\"back-to-top-btn\""), "Homepage back-to-top must have styling class");

        // 2. Analysis Input Page
        MvcResult analysisResult = mockMvc.perform(get("/analysis"))
                .andExpect(status().isOk())
                .andReturn();
        String analysisHtml = analysisResult.getResponse().getContentAsString();
        assertTrue(analysisHtml.contains("id=\"back-to-top-btn\""), "Analysis page must include back-to-top button");

        // 3. Results Dashboard
        MvcResult resultsResult = mockMvc.perform(post("/analysis/submit")
                        .param("lambda", "3")
                        .param("dropCount", "2")
                        .param("successProbability", "0.6")
                        .param("maxAttempts", "5")
                        .param("analysisType", "COMBINED"))
                .andExpect(status().isOk())
                .andReturn();
        String resultsHtml = resultsResult.getResponse().getContentAsString();
        assertTrue(resultsHtml.contains("id=\"back-to-top-btn\""), "Results dashboard must include back-to-top button");
    }

    @Test
    @DisplayName("Phase D.2: Button loading states and label preservation logic are implemented without spinner animation")
    void testLoadingStateStylesAndScripts() throws Exception {
        // CSS assertions
        String css = Files.readString(Path.of("src/main/resources/static/css/styles.css"));
        assertTrue(css.contains(".btn.is-loading"), "CSS must define .btn.is-loading");
        assertTrue(css.contains("cursor: wait"), "CSS must set wait cursor during loading");
        assertFalse(css.contains(".btn.is-loading::before"), "CSS must not define spinner animation via ::before");
        assertFalse(css.contains("btn-spinner"), "CSS must not include spinner keyframes");

        // JS assertions
        String js = Files.readString(Path.of("src/main/resources/static/js/main.js"));
        assertTrue(js.contains("isSubmitting"), "JS must track submission state to prevent duplicate submissions");
        assertTrue(js.contains("data-original-label"), "JS must preserve original button labels");
        assertTrue(js.contains("classList.add(\"is-loading\")"), "JS must apply is-loading class");
        assertTrue(js.contains("classList.remove(\"is-loading\")"), "JS must remove is-loading class in finally/pageshow");
    }

    @Test
    @DisplayName("Phase D.3: Comprehensive hover, focus-visible, and active/pressed states are present")
    void testHoverFocusActiveStatesInCss() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/static/css/styles.css"));

        // Button active / focus states
        assertTrue(css.contains(".btn:active"), "CSS must define .btn:active");
        assertTrue(css.contains(".btn-primary:active"), "CSS must define .btn-primary:active");
        assertTrue(css.contains(".btn-secondary:active"), "CSS must define .btn-secondary:active");
        assertTrue(css.contains(".btn-report:active"), "CSS must define .btn-report:active");

        // Focus-visible keyboard outlines
        assertTrue(css.contains(".btn:focus-visible"), "CSS must define .btn:focus-visible");
        assertTrue(css.contains(".nav-link:focus-visible"), "CSS must define .nav-link:focus-visible");
        assertTrue(css.contains(".nav-toggle:focus-visible"), "CSS must define .nav-toggle:focus-visible");

        // Back-to-top states
        assertTrue(css.contains(".back-to-top-btn:hover"), "CSS must define .back-to-top-btn:hover");
        assertTrue(css.contains(".back-to-top-btn:active"), "CSS must define .back-to-top-btn:active");
        assertTrue(css.contains(".back-to-top-btn:focus-visible"), "CSS must define .back-to-top-btn:focus-visible");
    }

    @Test
    @DisplayName("Phase D.4: Print stylesheet (@media print) provides high-contrast layout, hiding non-print elements")
    void testPrintStylesheet() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/static/css/styles.css"));
        assertTrue(css.contains("@media print"), "CSS must include @media print section");
        assertTrue(css.contains("size: A4"), "Print stylesheet must target A4 paper size");
        assertTrue(css.contains(".site-header"), "Print stylesheet must target site header for hiding");
        assertTrue(css.contains("#back-to-top-btn"), "Print stylesheet must hide back-to-top button");
        assertTrue(css.contains(".dashboard-actions-panel") || css.contains("#dashboard-actions"),
                "Print stylesheet must hide action buttons panel");
        assertTrue(css.contains("page-break-inside: avoid") || css.contains("break-inside: avoid"),
                "Print stylesheet must prevent awkward card and table page breaks");
    }

    @Test
    @DisplayName("Phase D.5: Navigation contains singular links and no duplicate analysis CTAs")
    void testNavigationAndDuplicateCtaAudit() throws Exception {
        MvcResult homeResult = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn();
        String homeHtml = homeResult.getResponse().getContentAsString();

        // Top navigation items in header
        assertTrue(homeHtml.contains("href=\"/\""), "Nav must contain Home link");
        assertTrue(homeHtml.contains("id=\"about-dialog-trigger\""), "Nav must contain About Project trigger");

        // Verify primary navigation specifically does not contain the Analysis link
        int navStartIndex = homeHtml.indexOf("<nav id=\"primary-navigation\"");
        int navEndIndex = homeHtml.indexOf("</nav>", navStartIndex);
        String navSection = homeHtml.substring(navStartIndex, navEndIndex);
        assertFalse(navSection.contains("href=\"/analysis\""), "Navbar should not contain the Analysis link");

        // Hero primary CTA remains
        assertTrue(homeHtml.contains("href=\"/analysis\""), "Homepage must still contain Start Analysis CTA link");
        int startAnalysisCount = countOccurrences(homeHtml, "Start Analysis");
        assertEquals(1, startAnalysisCount, "Homepage should have exactly one 'Start Analysis' primary CTA button");
    }

    private int countOccurrences(String source, String target) {
        int count = 0;
        int idx = 0;
        while ((idx = source.indexOf(target, idx)) != -1) {
            count++;
            idx += target.length();
        }
        return count;
    }
}
