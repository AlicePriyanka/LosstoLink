package com.deepspace.analytics.controller;

import com.deepspace.analytics.model.AnalysisDashboardView;
import com.deepspace.analytics.model.AnalysisType;
import com.deepspace.analytics.model.CommunicationAnalysisInput;
import com.deepspace.analytics.model.GeometricResult;
import com.deepspace.analytics.model.PoissonResult;
import com.deepspace.analytics.service.AnalysisDashboardService;
import com.deepspace.analytics.service.GeometricService;
import com.deepspace.analytics.service.PoissonService;
import com.deepspace.analytics.validation.AnalysisInputValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * MVC controller for the unified Communication Analysis input screen,
 * Stage 3 backend input validation, Stage 4 Poisson calculation integration,
 * Stage 5 Geometric calculation integration, and Stage 6/7 Combined Results Dashboard.
 */
@Controller
public class AnalysisController {

    private final AnalysisInputValidator analysisInputValidator;
    private final PoissonService poissonService;
    private final GeometricService geometricService;
    private final AnalysisDashboardService analysisDashboardService;
    private final ObjectMapper objectMapper;

    public AnalysisController(
            AnalysisInputValidator analysisInputValidator,
            PoissonService poissonService,
            GeometricService geometricService,
            AnalysisDashboardService analysisDashboardService,
            ObjectMapper objectMapper) {
        this.analysisInputValidator = analysisInputValidator;
        this.poissonService = poissonService;
        this.geometricService = geometricService;
        this.analysisDashboardService = analysisDashboardService;
        this.objectMapper = objectMapper;
    }

    /**
     * Renders the unified Communication Analysis input screen containing all
     * four parameters for the Poisson and Geometric models.
     *
     * @param model Thymeleaf view model
     * @return the analysis template name
     */
    @GetMapping("/analysis")
    public String showAnalysisForm(Model model) {
        model.addAttribute("activePage", "analysis");
        if (!model.containsAttribute("analysisInput")) {
            model.addAttribute("analysisInput", new CommunicationAnalysisInput());
        }
        model.addAttribute("hasServerErrors", false);
        return "analysis";
    }

    /**
     * Redirects direct GET requests on the form submission endpoint cleanly back
     * to the unified input screen.
     *
     * @return redirect instruction to {@code /analysis}
     */
    @GetMapping("/analysis/submit")
    public String redirectDirectSubmitToForm() {
        return "redirect:/analysis";
    }

    /**
     * Validates submitted parameters according to the resolved or selected
     * {@link AnalysisType} ({@code POISSON_ONLY}, {@code GEOMETRIC_ONLY}, or
     * {@code COMBINED}), executes only the required calculation service(s), and
     * renders the Results Dashboard.
     *
     * @param analysisInput bound form input containing Poisson and/or Geometric parameters
     * @param bindingResult Spring validation result populated by {@link AnalysisInputValidator}
     * @param model Thymeleaf view model
     * @return {@code analysis} when validation fails, or {@code analysis-results} when valid
     */
    @PostMapping("/analysis/submit")
    public String submitAnalysisForm(
            @ModelAttribute("analysisInput") CommunicationAnalysisInput analysisInput,
            BindingResult bindingResult,
            Model model) {
        model.addAttribute("activePage", "analysis");

        analysisInputValidator.validate(analysisInput, bindingResult);

        if (bindingResult.hasErrors()) {
            model.addAttribute("hasServerErrors", true);
            boolean incomplete = analysisInputValidator.hasMissingFields(analysisInput);
            model.addAttribute("generalErrorMessage", incomplete
                    ? "Please complete all required fields and fix any highlighted errors before calculating results."
                    : "Please review and correct the highlighted parameter errors before calculating results.");
            return "analysis";
        }

        AnalysisType analysisType = analysisInputValidator.resolveAnalysisType(analysisInput);
        analysisInput.setAnalysisType(analysisType);

        final PoissonResult poissonResult;
        final GeometricResult geometricResult;
        try {
            poissonResult = analysisType.includesPoisson()
                    ? poissonService.calculate(analysisInput)
                    : null;
            geometricResult = analysisType.includesGeometric()
                    ? geometricService.calculate(analysisInput)
                    : null;
        } catch (IllegalArgumentException ex) {
            model.addAttribute("hasServerErrors", true);
            model.addAttribute("generalErrorMessage", ex.getMessage());
            return "analysis";
        }

        AnalysisDashboardView dashboard = analysisDashboardService.buildDashboardView(
                analysisType, analysisInput, poissonResult, geometricResult);

        model.addAttribute("validationConfirmed", true);
        model.addAttribute("confirmationMessage", "Analysis completed successfully.");
        model.addAttribute("analysisType", analysisType);
        model.addAttribute("analysisInput", analysisInput);
        model.addAttribute("poissonResult", poissonResult);
        model.addAttribute("geometricResult", geometricResult);
        model.addAttribute("dashboard", dashboard);
        model.addAttribute("dashboardChartsJson", serializeJsonPayload(dashboard.getCharts(), "dashboard chart data"));
        return "analysis-results";
    }

    private String serializeJsonPayload(Object payload, String label) {
        try {
            return objectMapper.writeValueAsString(payload)
                    .replace("<", "\\u003c")
                    .replace(">", "\\u003e")
                    .replace("&", "\\u0026");
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize " + label + ".", ex);
        }
    }
}

