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

public class PhaseBAboutDialogAndNavTest {

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
    @DisplayName("Homepage: About section is removed from page scroll; hero and models preserved")
    void homepageAboutSectionRemovedFromScroll() throws Exception {
        Path indexPath = Path.of("src/main/resources/templates/index.html");
        String indexHtml = Files.readString(indexPath);

        assertFalse(indexHtml.contains("id=\"about-project\""),
                "Homepage must NOT contain standalone '#about-project' section in page scroll");
        assertFalse(indexHtml.contains("overview-section"),
                "Homepage must NOT contain 'overview-section'");
        assertTrue(indexHtml.contains("hero-section"), "Hero section must be preserved");
        assertTrue(indexHtml.contains("id=\"models\""), "Probability models section must be preserved");
    }

    @Test
    @DisplayName("Navbar & Shared Dialog: Available across Homepage, Analysis Input, and Results Dashboard")
    void navbarAndDialogAvailableAcrossAllPages() throws Exception {
        // 1. Check Homepage
        MvcResult homeResult = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn();
        String homeHtml = homeResult.getResponse().getContentAsString();
        assertDialogAndNavPresent(homeHtml, "Homepage");

        // 2. Check Analysis Input Page
        MvcResult inputResult = mockMvc.perform(get("/analysis"))
                .andExpect(status().isOk())
                .andReturn();
        String inputHtml = inputResult.getResponse().getContentAsString();
        assertDialogAndNavPresent(inputHtml, "Analysis Input");

        // 3. Check Results Dashboard Page
        MvcResult resultsResult = mockMvc.perform(post("/analysis/submit")
                        .param("lambda", "3")
                        .param("dropCount", "2")
                        .param("successProbability", "0.6")
                        .param("maxAttempts", "5"))
                .andExpect(status().isOk())
                .andReturn();
        String resultsHtml = resultsResult.getResponse().getContentAsString();
        assertDialogAndNavPresent(resultsHtml, "Results Dashboard");
    }

    @Test
    @DisplayName("About Dialog Content: Academic rigor, Poisson, Geometric, and reliability preserved")
    void aboutDialogContentPreserved() throws Exception {
        Path navPath = Path.of("src/main/resources/templates/fragments/navbar.html");
        String navHtml = Files.readString(navPath);

        assertTrue(navHtml.contains("Project Overview &amp; Research Motivation")
                        || navHtml.contains("Project Overview & Research Motivation"),
                "Must explain project motivation");
        assertTrue(navHtml.contains("Dual-Model Statistical Framework"),
                "Must include dual-model framework");
        assertTrue(navHtml.contains("Poisson Distribution"),
                "Must include Poisson distribution");
        assertTrue(navHtml.contains("P(X = k)"),
                "Poisson notation must use X");
        assertTrue(navHtml.contains("Geometric Distribution"),
                "Must include Geometric distribution");
        assertTrue(navHtml.contains("P(Y = k)"),
                "Geometric notation must use Y");
        assertTrue(navHtml.contains("R(n) = P(Y &le; n)") || navHtml.contains("R(n) = P(Y <= n)"),
                "Reliability definition must use Y");
        assertTrue(navHtml.contains("Defined Scope &amp; Theoretical Assumptions")
                        || navHtml.contains("Defined Scope & Theoretical Assumptions"),
                "Must include scope and assumptions");
        assertTrue(navHtml.contains("Stationary Poisson Rate"),
                "Must note stationary arrival rate assumption");
        assertTrue(navHtml.contains("Independent Bernoulli Trials"),
                "Must note independent Bernoulli trials assumption");
    }

    @Test
    @DisplayName("CSS & JS: Spring physics, reduced-motion, focus trapping, and backdrop blur implemented")
    void cssAndJsDialogInteractionsImplemented() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/static/css/styles.css"));
        assertTrue(css.contains(".about-dialog-backdrop"), "CSS must contain backdrop styles");
        assertTrue(css.contains(".about-dialog-card"), "CSS must contain dialog card styles");
        assertTrue(css.contains("cubic-bezier(0.34, 1.56, 0.64, 1)"), "CSS must use spring cubic-bezier");
        assertTrue(css.contains("body.dialog-open"), "CSS must include body scroll lock");
        assertTrue(css.contains("@media (prefers-reduced-motion: reduce)"), "CSS must respect reduced motion");

        String js = Files.readString(Path.of("src/main/resources/static/js/main.js"));
        assertTrue(js.contains("initAboutDialog"), "JS must contain initAboutDialog");
        assertTrue(js.contains("Escape"), "JS must handle Escape key");
        assertTrue(js.contains("Tab"), "JS must handle Tab focus trapping");
        assertTrue(js.contains("transformOrigin"), "JS must compute transform origin from trigger");
    }

    private void assertDialogAndNavPresent(String html, String pageName) {
        assertTrue(html.contains("id=\"about-dialog-trigger\""),
                pageName + " must contain About dialog trigger button");
        assertTrue(html.contains("id=\"about-dialog-backdrop\""),
                pageName + " must contain About dialog backdrop modal");
        assertTrue(html.contains("role=\"dialog\""),
                pageName + " must include semantic role=\"dialog\"");
        assertTrue(html.contains("aria-modal=\"true\""),
                pageName + " must include aria-modal=\"true\"");
        assertTrue(html.contains("aria-labelledby=\"about-dialog-title\""),
                pageName + " must label dialog with title");
        assertTrue(html.contains("id=\"about-dialog-close-btn\""),
                pageName + " must contain accessible close button");
    }
}
