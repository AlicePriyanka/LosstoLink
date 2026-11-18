package com.deepspace.analytics.controller;

import com.deepspace.analytics.model.PdfReportRequest;
import com.deepspace.analytics.service.PdfReportService;
import com.deepspace.analytics.service.PdfReportService.GeneratedPdfReport;
import com.deepspace.analytics.validation.AnalysisInputValidator;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backend PDF report download endpoint for Stage 9. Validates inputs via the
 * existing Stage 3 validator, reuses live Poisson and Geometric calculation
 * results, and streams a binary PDF download.
 */
@RestController
@RequestMapping("/analysis/report")
public class ReportController {

    private final AnalysisInputValidator analysisInputValidator;
    private final PdfReportService pdfReportService;

    public ReportController(
            AnalysisInputValidator analysisInputValidator,
            PdfReportService pdfReportService) {
        this.analysisInputValidator = analysisInputValidator;
        this.pdfReportService = pdfReportService;
    }

    /**
     * Redirects direct GET requests on the report download path cleanly back to the input form.
     */
    @GetMapping("/pdf")
    public ResponseEntity<Void> redirectGetPdfToAnalysis() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "/analysis")
                .build();
    }

    /**
     * Generates and streams the mathematical analysis PDF report from JSON input
     * (including optional browser-rendered white-theme Chart.js PNG snapshots).
     */
    @PostMapping(value = "/pdf", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> downloadReportJson(
            @RequestBody(required = false) PdfReportRequest request) {
        return buildPdfDownloadResponse(request);
    }

    /**
     * Fallback form-encoded endpoint allowing direct POST submissions without JSON.
     */
    @PostMapping(value = "/pdf", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> downloadReportForm(
            @ModelAttribute PdfReportRequest request) {
        return buildPdfDownloadResponse(request);
    }

    private ResponseEntity<?> buildPdfDownloadResponse(PdfReportRequest request) {
        Map<String, String> fieldErrors = analysisInputValidator.validateParameters(request);
        if (!fieldErrors.isEmpty()) {
            boolean incomplete = analysisInputValidator.hasMissingFields(request);
            String generalError = incomplete
                    ? "Report could not be generated because one or more required analysis parameters are missing."
                    : "Report could not be generated because one or more analysis parameters are invalid.";
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(errorBody(generalError, fieldErrors));
        }

        final GeneratedPdfReport generated;
        try {
            generated = pdfReportService.generateReport(request);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(errorBody(ex.getMessage(), Collections.emptyMap()));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(errorBody("An unexpected error occurred while generating the PDF report. Your dashboard data remains intact.", Collections.emptyMap()));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename(generated.getFilename(), StandardCharsets.UTF_8)
                        .build());
        headers.setCacheControl(CacheControl.noStore());
        headers.setContentLength(generated.getBytes().length);

        return new ResponseEntity<>(generated.getBytes(), headers, HttpStatus.OK);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleMalformedPayload() {
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorBody("Invalid report request payload. Please try downloading the report again.", Collections.emptyMap()));
    }

    private Map<String, Object> errorBody(String generalError, Map<String, String> fieldErrors) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("generalError", generalError);
        body.put("errors", fieldErrors);
        return body;
    }
}

