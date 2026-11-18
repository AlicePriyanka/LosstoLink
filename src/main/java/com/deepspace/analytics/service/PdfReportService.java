package com.deepspace.analytics.service;

import com.deepspace.analytics.model.AnalysisDashboardView;
import com.deepspace.analytics.model.AnalysisDashboardView.AttemptTableRow;
import com.deepspace.analytics.model.AnalysisDashboardView.DashboardCharts;
import com.deepspace.analytics.model.AnalysisDashboardView.GeometricCards;
import com.deepspace.analytics.model.AnalysisDashboardView.PoissonCards;
import com.deepspace.analytics.model.AnalysisDashboardView.ReliabilitySummary;
import com.deepspace.analytics.model.AnalysisType;
import com.deepspace.analytics.model.GeometricResult;
import com.deepspace.analytics.model.PdfReportRequest;
import com.deepspace.analytics.model.PoissonResult;
import com.deepspace.analytics.service.AnalysisFormattingUtils;
import com.deepspace.analytics.validation.AnalysisInputValidator;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Generates an authoritative, publication-quality, maximum 2-page academic PDF analysis report
 * evaluating Poisson transmission drop frequency and Geometric first-success link acquisition latency.
 *
 * Strictly limited to 2 pages for all three analysis modes (POISSON_ONLY, GEOMETRIC_ONLY, COMBINED).
 * Page 1: Parameters and Mathematical Results.
 * Page 2: Visualisation Suite, Final Practical Conclusion, and Model Assumptions & Limitations.
 */
@Service
public class PdfReportService {

    private static final String PROJECT_TITLE =
            "Deep-Space Communication Using Poisson and Geometric Models";
    private static final String APPLICATION_NAME = "DeepSpace Analytics";
    private static final DateTimeFormatter REPORT_DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMMM dd, yyyy · HH:mm:ss z", Locale.US);

    // Academic clean white & deep-navy / blue color palette
    private static final Color NAVY_DARK = new Color(15, 23, 42);
    private static final Color BLUE_PRIMARY = new Color(29, 78, 216);
    private static final Color BLUE_ACCENT = new Color(37, 99, 235);
    private static final Color BLUE_BORDER = new Color(147, 197, 253);
    private static final Color BLUE_TINT = new Color(239, 246, 255);
    private static final Color SLATE_BODY = new Color(30, 41, 59);
    private static final Color SLATE_MUTED = new Color(71, 85, 105);
    private static final Color SURFACE_TINT = new Color(248, 250, 252);
    private static final Color TABLE_BORDER = new Color(203, 213, 225);
    private static final Color EMERALD_ACCENT = new Color(5, 150, 105);

    private static final String[] REGULAR_FONT_CANDIDATES = {
            "C:/Windows/Fonts/segoeui.ttf",
            "C:/Windows/Fonts/arial.ttf",
            "C:/Windows/Fonts/calibri.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf"
    };
    private static final String[] BOLD_FONT_CANDIDATES = {
            "C:/Windows/Fonts/segoeuib.ttf",
            "C:/Windows/Fonts/arialbd.ttf",
            "C:/Windows/Fonts/calibrib.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
            "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf"
    };
    private static final String[] ITALIC_FONT_CANDIDATES = {
            "C:/Windows/Fonts/segoeuii.ttf",
            "C:/Windows/Fonts/ariali.ttf",
            "C:/Windows/Fonts/calibrii.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans-Oblique.ttf",
            "/usr/share/fonts/truetype/liberation/LiberationSans-Italic.ttf"
    };
    private static final String[] MONO_FONT_CANDIDATES = {
            "C:/Windows/Fonts/consola.ttf",
            "C:/Windows/Fonts/cour.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf",
            "/usr/share/fonts/truetype/liberation/LiberationMono-Regular.ttf"
    };
    private static final String[] MONO_BOLD_FONT_CANDIDATES = {
            "C:/Windows/Fonts/consolab.ttf",
            "C:/Windows/Fonts/courbd.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf",
            "/usr/share/fonts/truetype/liberation/LiberationMono-Bold.ttf"
    };

    private final PoissonService poissonService;
    private final GeometricService geometricService;
    private final AnalysisDashboardService analysisDashboardService;
    private final ReportChartImageService reportChartImageService;
    private final AnalysisInputValidator analysisInputValidator;

    public PdfReportService(
            PoissonService poissonService,
            GeometricService geometricService,
            AnalysisDashboardService analysisDashboardService,
            ReportChartImageService reportChartImageService) {
        this(
                poissonService,
                geometricService,
                analysisDashboardService,
                reportChartImageService,
                new AnalysisInputValidator());
    }

    @Autowired
    public PdfReportService(
            PoissonService poissonService,
            GeometricService geometricService,
            AnalysisDashboardService analysisDashboardService,
            ReportChartImageService reportChartImageService,
            AnalysisInputValidator analysisInputValidator) {
        this.poissonService = poissonService;
        this.geometricService = geometricService;
        this.analysisDashboardService = analysisDashboardService;
        this.reportChartImageService = reportChartImageService;
        this.analysisInputValidator = (analysisInputValidator != null)
                ? analysisInputValidator
                : new AnalysisInputValidator();
    }

    /**
     * Evaluates the validated request using only the mathematical services
     * required for the active {@link AnalysisType} and builds a complete, strictly 2-page PDF
     * binary payload.
     *
     * @param request parameter input + optional analysisType and browser PNG snapshots
     * @return generated PDF document bytes and safe download filename
     */
    public GeneratedPdfReport generateReport(PdfReportRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Report request parameters are required.");
        }

        Map<String, String> validationErrors = analysisInputValidator.validateParameters(request);
        if (!validationErrors.isEmpty()) {
            throw new IllegalArgumentException(validationErrors.values().iterator().next());
        }

        AnalysisType analysisType = analysisInputValidator.resolveAnalysisType(request);
        request.setAnalysisType(analysisType);

        PoissonResult poissonResult = analysisType.includesPoisson()
                ? poissonService.calculate(request)
                : null;
        GeometricResult geometricResult = analysisType.includesGeometric()
                ? geometricService.calculate(request)
                : null;
        AnalysisDashboardView dashboard = analysisDashboardService.buildDashboardView(
                analysisType, request, poissonResult, geometricResult);

        byte[] pdfBytes = buildPdfBytes(request, poissonResult, geometricResult, dashboard);
        String filename = buildDownloadFilename(dashboard);
        return new GeneratedPdfReport(pdfBytes, filename);
    }

    /**
     * Generates a clean, filesystem-safe download filename reflecting the active
     * analysis mode and analyzed parameters.
     */
    public String buildDownloadFilename(AnalysisDashboardView dashboard) {
        AnalysisType mode = dashboard.getAnalysisType();
        if (mode == AnalysisType.POISSON_ONLY) {
            String lambdaToken = sanitizeFilenameToken(dashboard.getSubmittedLambda());
            String kToken = sanitizeFilenameToken(dashboard.getSubmittedDropCount());
            return String.format(
                    Locale.US,
                    "DeepSpace-Analytics-Report-poisson-lambda-%s-k-%s.pdf",
                    lambdaToken, kToken);
        }
        if (mode == AnalysisType.GEOMETRIC_ONLY) {
            String pToken = sanitizeFilenameToken(dashboard.getSubmittedSuccessProbability());
            String nToken = sanitizeFilenameToken(dashboard.getSubmittedMaxAttempts());
            return String.format(
                    Locale.US,
                    "DeepSpace-Analytics-Report-geometric-p-%s-n-%s.pdf",
                    pToken, nToken);
        }
        String lambdaToken = sanitizeFilenameToken(dashboard.getSubmittedLambda());
        String kToken = sanitizeFilenameToken(dashboard.getSubmittedDropCount());
        String pToken = sanitizeFilenameToken(dashboard.getSubmittedSuccessProbability());
        String nToken = sanitizeFilenameToken(dashboard.getSubmittedMaxAttempts());
        return String.format(
                Locale.US,
                "DeepSpace-Analytics-Report-lambda-%s-k-%s-p-%s-n-%s.pdf",
                lambdaToken, kToken, pToken, nToken);
    }

    private byte[] buildPdfBytes(
            PdfReportRequest request,
            PoissonResult poissonResult,
            GeometricResult geometricResult,
            AnalysisDashboardView dashboard) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ReportFonts fonts = loadReportFonts();
            // A4 page with 36pt (0.5 in) margins
            Document document = new Document(PageSize.A4, 36f, 36f, 36f, 36f);
            PdfWriter writer = PdfWriter.getInstance(document, baos);

            AnalysisType mode = dashboard.getAnalysisType();
            String generatedAt = REPORT_DATE_FORMAT.format(ZonedDateTime.now());
            String footerParams = buildFooterParams(dashboard);
            String headerScope = buildHeaderScopeLabel(mode);

            writer.setPageEvent(new AcademicHeaderFooterEvent(fonts, generatedAt, footerParams, headerScope));

            document.addTitle("DeepSpace Analytics — Mathematical Analysis Report");
            document.addAuthor(APPLICATION_NAME);
            document.addSubject(PROJECT_TITLE);
            document.addCreator("DeepSpace Analytics PDF Report Generator");

            document.open();

            // PAGE 1 – PARAMETERS AND MATHEMATICAL RESULTS
            appendPageOneParametersAndResults(document, fonts, dashboard, poissonResult, geometricResult, generatedAt);

            // PAGE 2 – GRAPHS AND CONCLUSION (Strict 2-Page Limit)
            document.newPage();
            appendPageTwoGraphsAndConclusion(document, fonts, request, dashboard, poissonResult, geometricResult);

            document.close();
            return baos.toByteArray();
        } catch (DocumentException | IOException ex) {
            throw new IllegalStateException("Unable to generate PDF analysis report.", ex);
        }
    }

    private String buildFooterParams(AnalysisDashboardView dashboard) {
        AnalysisType mode = dashboard.getAnalysisType();
        if (mode == AnalysisType.POISSON_ONLY) {
            return String.format(
                    Locale.US,
                    "\u03BB = %s  \u00B7  k = %s",
                    dashboard.getSubmittedLambda(),
                    dashboard.getSubmittedDropCount());
        }
        if (mode == AnalysisType.GEOMETRIC_ONLY) {
            return String.format(
                    Locale.US,
                    "p = %s  \u00B7  n = %s",
                    dashboard.getSubmittedSuccessProbability(),
                    dashboard.getSubmittedMaxAttempts());
        }
        return String.format(
                Locale.US,
                "\u03BB = %s  \u00B7  k = %s  \u00B7  p = %s  \u00B7  n = %s",
                dashboard.getSubmittedLambda(),
                dashboard.getSubmittedDropCount(),
                dashboard.getSubmittedSuccessProbability(),
                dashboard.getSubmittedMaxAttempts());
    }

    private String buildHeaderScopeLabel(AnalysisType mode) {
        if (mode == null) {
            return "Combined Analysis (2 Pages)";
        }
        return switch (mode) {
            case POISSON_ONLY -> "Poisson Analysis (2 Pages)";
            case GEOMETRIC_ONLY -> "Geometric Analysis (2 Pages)";
            case COMBINED -> "Combined Analysis (2 Pages)";
        };
    }

    // =========================================================================
    // PAGE 1: PARAMETERS AND MATHEMATICAL RESULTS
    // =========================================================================

    private void appendPageOneParametersAndResults(
            Document document,
            ReportFonts fonts,
            AnalysisDashboardView dashboard,
            PoissonResult poissonResult,
            GeometricResult geometricResult,
            String generatedAt) throws DocumentException {

        AnalysisType mode = dashboard.getAnalysisType();

        // 1. Report Header
        PdfPTable headerTable = new PdfPTable(new float[]{3.2f, 1.8f});
        headerTable.setWidthPercentage(100);
        headerTable.setSpacingAfter(8f);

        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(0);
        leftCell.setPadding(0);

        Paragraph appPara = new Paragraph(APPLICATION_NAME, fonts.headerAppName);
        appPara.setSpacingAfter(2f);
        leftCell.addElement(appPara);

        Paragraph titlePara = new Paragraph(PROJECT_TITLE, fonts.headerProjectTitle);
        titlePara.setSpacingAfter(3f);
        leftCell.addElement(titlePara);

        Paragraph metaPara = new Paragraph(
                "Report Date: " + generatedAt + "  ·  Mode: " + mode.getDisplayName(),
                fonts.headerMeta);
        leftCell.addElement(metaPara);
        headerTable.addCell(leftCell);

        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(0);
        rightCell.setPadding(0);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        rightCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        PdfPTable modeBadge = new PdfPTable(1);
        modeBadge.setWidthPercentage(95);
        modeBadge.setHorizontalAlignment(Element.ALIGN_RIGHT);
        PdfPCell badgeCell = new PdfPCell(new Phrase(mode.getDisplayName().toUpperCase(Locale.US), fonts.modeBadgeFont));
        badgeCell.setBackgroundColor(BLUE_TINT);
        badgeCell.setBorderColor(BLUE_ACCENT);
        badgeCell.setBorderWidth(1.0f);
        badgeCell.setPadding(6f);
        badgeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        badgeCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        modeBadge.addCell(badgeCell);
        rightCell.addElement(modeBadge);
        headerTable.addCell(rightCell);

        document.add(headerTable);

        // Divider rule
        document.add(createHorizontalRule(BLUE_ACCENT, 1.5f, 7f));

        // 2. Submitted Parameters
        document.add(createSectionHeading(fonts, "1. Submitted Parameters"));
        PdfPTable paramTable = createParametersTable(fonts, dashboard);
        paramTable.setSpacingAfter(8f);
        document.add(paramTable);

        // 3. Mathematical Results
        int sectionIndex = 2;

        // Module 1: Poisson Results (when enabled)
        if (dashboard.isPoissonAvailable() && poissonResult != null) {
            PoissonCards pCards = dashboard.getPoisson();
            document.add(createSectionHeading(fonts, sectionIndex++ + ". Poisson Distribution Results (Random Variable X)"));

            PdfPTable pTable = new PdfPTable(new float[]{1.7f, 1.25f, 1.25f, 2.8f});
            pTable.setWidthPercentage(100);
            pTable.setHeaderRows(1);
            pTable.setSpacingAfter(8f);
            addTableHeader(pTable, fonts, "Metric Name", "Notation", "Calculated Output", "Communication Interpretation");

            boolean shaded = false;
            addCompactResultRow(pTable, fonts, "Exact-k Probability",
                    "P(X = " + pCards.getDropCount() + ")",
                    pCards.getExactDropDecimal() + " (" + pCards.getExactDropPercent() + ")",
                    "Likelihood of exactly " + pCards.getDropCount() + " transmission drops in one interval", shaded);
            shaded = !shaded;

            addCompactResultRow(pTable, fonts, "Expected Drops",
                    "E(X) = \u03BB",
                    pCards.getExpectedDropsValue() + " drops / interval",
                    "Long-run mean drop count expected per communication window", shaded);
            shaded = !shaded;

            addCompactResultRow(pTable, fonts, "Zero Drops",
                    "P(X = 0) = e^(-\u03BB)",
                    pCards.getProbabilityZeroDecimal() + " (" + pCards.getProbabilityZeroPercent() + ")",
                    "Probability of an uninterrupted, clean transmission interval", shaded);
            shaded = !shaded;

            addCompactResultRow(pTable, fonts, "At Least One Drop",
                    "P(X \u2265 1) = 1 \u2212 e^(-\u03BB)",
                    pCards.getProbabilityAtLeastOneDecimal() + " (" + pCards.getProbabilityAtLeastOnePercent() + ")",
                    "Likelihood that one or more drops interrupt the communication window", shaded);
            shaded = !shaded;

            addCompactResultRow(pTable, fonts, "Variance of Drops",
                    "Var(X) = \u03BB",
                    pCards.getVarianceValue() + " drops\u00B2",
                    "Statistical dispersion equal to expected mean \u03BB for Poisson model", shaded);

            document.add(pTable);
        }

        // Module 2 & 3: Geometric & Reliability Results (when enabled)
        if (dashboard.isGeometricAvailable() && geometricResult != null) {
            GeometricCards gCards = dashboard.getGeometric();
            ReliabilitySummary rSummary = dashboard.getReliability();

            document.add(createSectionHeading(fonts, sectionIndex++ + ". Geometric Distribution & Reliability (Random Variable Y)"));

            PdfPTable gTable = new PdfPTable(new float[]{1.7f, 1.25f, 1.25f, 2.8f});
            gTable.setWidthPercentage(100);
            gTable.setHeaderRows(1);
            gTable.setSpacingAfter(8f);
            addTableHeader(gTable, fonts, "Metric Name", "Notation", "Calculated Output", "Communication Interpretation");

            boolean shaded = false;
            addCompactResultRow(gTable, fonts, "Expected Attempts",
                    "E(Y) = 1 / p",
                    gCards.getExpectedAttemptsValue() + " (" + gCards.getExpectedAttemptsRoundedText() + ")",
                    "Average transmission attempts required until first successful reception", shaded);
            shaded = !shaded;

            addCompactResultRow(gTable, fonts, "Most Likely First Success",
                    "Mode(Y) = 1",
                    "Attempt 1 (" + gCards.getFirstAttemptPercent() + ")",
                    "Attempt 1 carries the highest single-attempt probability: P(Y = 1) = p", shaded);
            shaded = !shaded;

            addCompactResultRow(gTable, fonts, "Cumulative Reliability",
                    "R(n) = P(Y \u2264 n)",
                    gCards.getWithinAttemptsDecimal() + " (" + gCards.getWithinAttemptsPercent() + ")",
                    "Cumulative probability of link lock on or before attempt n = " + gCards.getMaxAttempts() + ": 1 \u2212 (1\u2212p)^n", shaded);
            shaded = !shaded;

            addCompactResultRow(gTable, fonts, "Tail Exceedance Risk",
                    "P(Y > n) = (1 \u2212 p)^n",
                    rSummary.getMoreThanAttemptsDecimal() + " (" + rSummary.getMoreThanAttemptsPercent() + ")",
                    "Probability that first link acquisition requires more than " + gCards.getMaxAttempts() + " attempts", shaded);

            document.add(gTable);

            // Compact Attempt-by-Attempt Probability Table
            document.add(createSectionHeading(fonts, sectionIndex++ + ". Attempt-by-Attempt Probability Schedule"));
            PdfPTable attemptTable = createAttemptScheduleTable(fonts, dashboard, geometricResult);
            attemptTable.setSpacingAfter(0f);
            document.add(attemptTable);
        }
    }

    private PdfPTable createParametersTable(ReportFonts fonts, AnalysisDashboardView dashboard) {
        AnalysisType mode = dashboard.getAnalysisType();
        if (mode == AnalysisType.POISSON_ONLY) {
            PdfPTable table = new PdfPTable(new float[]{1.8f, 1f, 1.8f, 1f});
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            addTableHeader(table, fonts, "Parameter Name", "Symbol", "Domain / Type", "Submitted Value");
            addParamRow(table, fonts, "Average Transmission Drop Rate", "\u03BB", "\u03BB \u2265 0 (Rate)", dashboard.getSubmittedLambda(), false);
            addParamRow(table, fonts, "Evaluated Transmission Drops", "k", "k \u2265 0 (Integer)", dashboard.getSubmittedDropCount(), true);
            return table;
        }
        if (mode == AnalysisType.GEOMETRIC_ONLY) {
            PdfPTable table = new PdfPTable(new float[]{1.8f, 1f, 1.8f, 1f});
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            addTableHeader(table, fonts, "Parameter Name", "Symbol", "Domain / Type", "Submitted Value");
            addParamRow(table, fonts, "Single-Attempt Success Probability", "p", "0 < p \u2264 1 (Prob.)", dashboard.getSubmittedSuccessProbability(), false);
            addParamRow(table, fonts, "Maximum Number of Attempts", "n", "n \u2265 1 (Integer)", dashboard.getSubmittedMaxAttempts(), true);
            return table;
        }
        // COMBINED mode: compact single-row or 4-row table
        PdfPTable table = new PdfPTable(new float[]{1.6f, 0.7f, 1.3f, 0.9f, 1.8f, 0.7f, 1.4f, 0.9f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        addTableHeader(table, fonts,
                "Poisson \u03BB", "Symbol", "Evaluated k", "Symbol",
                "Success p", "Symbol", "Max Horizon n", "Symbol");

        Color bg = SURFACE_TINT;
        table.addCell(bodyCell(dashboard.getSubmittedLambda(), fonts.monoBoldSmall, bg, 4.0f));
        table.addCell(bodyCell("\u03BB", fonts.monoSmall, bg, 4.0f));
        table.addCell(bodyCell(dashboard.getSubmittedDropCount(), fonts.monoBoldSmall, bg, 4.0f));
        table.addCell(bodyCell("k", fonts.monoSmall, bg, 4.0f));
        table.addCell(bodyCell(dashboard.getSubmittedSuccessProbability(), fonts.monoBoldSmall, bg, 4.0f));
        table.addCell(bodyCell("p", fonts.monoSmall, bg, 4.0f));
        table.addCell(bodyCell(dashboard.getSubmittedMaxAttempts(), fonts.monoBoldSmall, bg, 4.0f));
        table.addCell(bodyCell("n", fonts.monoSmall, bg, 4.0f));
        return table;
    }

    private void addParamRow(
            PdfPTable table,
            ReportFonts fonts,
            String name,
            String symbol,
            String domain,
            String value,
            boolean shaded) {
        Color bg = shaded ? SURFACE_TINT : Color.WHITE;
        table.addCell(bodyCell(name, fonts.bodyBoldSmall, bg, 4.0f));
        table.addCell(bodyCell(symbol, fonts.monoBoldSmall, bg, 4.0f));
        table.addCell(bodyCell(domain, fonts.bodySmall, bg, 4.0f));
        table.addCell(bodyCell(value, fonts.monoBoldSmall, bg, 4.0f));
    }

    private void addCompactResultRow(
            PdfPTable table,
            ReportFonts fonts,
            String metric,
            String notation,
            String value,
            String interpretation,
            boolean shaded) {
        Color bg = shaded ? SURFACE_TINT : Color.WHITE;
        table.addCell(bodyCell(metric, fonts.bodyBoldSmall, bg, 3.8f));
        table.addCell(bodyCell(notation, fonts.monoSmall, bg, 3.8f));
        table.addCell(bodyCell(value, fonts.monoBoldSmall, bg, 3.8f));
        table.addCell(bodyCell(interpretation, fonts.bodySmall, bg, 3.8f));
    }


    private PdfPTable createAttemptScheduleTable(
            ReportFonts fonts,
            AnalysisDashboardView dashboard,
            GeometricResult geometricResult) {
        PdfPTable table = new PdfPTable(new float[]{1.1f, 1.4f, 1.3f, 1.4f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        addTableHeader(table, fonts, "Attempt (k)", "Formula P(Y = k)", "Probability P(Y = k)", "Cumulative R(k) = P(Y \u2264 k)");

        int n = geometricResult.getMaxAttempts();
        double p = geometricResult.getSuccessProbability();
        int maxRows = 5;

        if (n <= maxRows) {
            double cum = 0.0;
            for (int k = 1; k <= n; k++) {
                double prob = Math.pow(1.0 - p, k - 1) * p;
                cum = Math.min(1.0, cum + prob);
                Color bg = (k == 1) ? BLUE_TINT : (k % 2 == 0 ? SURFACE_TINT : Color.WHITE);
                Font fMono = (k == 1) ? fonts.monoBoldSmall : fonts.monoSmall;
                String kLabel = (k == 1) ? "Attempt 1 (Mode)" : "Attempt " + k;

                table.addCell(bodyCell(kLabel, fMono, bg, 2.8f));
                table.addCell(bodyCell("(1\u2212p)^" + (k - 1) + " \u00B7 p", fMono, bg, 2.8f));
                table.addCell(bodyCell(String.format(Locale.US, "%.7f (%.4f%%)", prob, prob * 100), fMono, bg, 2.8f));
                table.addCell(bodyCell(String.format(Locale.US, "%.7f (%.4f%%)", cum, cum * 100), fMono, bg, 2.8f));
            }
        } else {
            // Capped schedule: show attempts 1, 2, 3, an ellipsis row, and attempt n
            double cum = 0.0;
            for (int k = 1; k <= 3; k++) {
                double prob = Math.pow(1.0 - p, k - 1) * p;
                cum = Math.min(1.0, cum + prob);
                Color bg = (k == 1) ? BLUE_TINT : (k % 2 == 0 ? SURFACE_TINT : Color.WHITE);
                Font fMono = (k == 1) ? fonts.monoBoldSmall : fonts.monoSmall;
                String kLabel = (k == 1) ? "Attempt 1 (Mode)" : "Attempt " + k;

                table.addCell(bodyCell(kLabel, fMono, bg, 2.8f));
                table.addCell(bodyCell("(1\u2212p)^" + (k - 1) + " \u00B7 p", fMono, bg, 2.8f));
                table.addCell(bodyCell(String.format(Locale.US, "%.7f (%.4f%%)", prob, prob * 100), fMono, bg, 2.8f));
                table.addCell(bodyCell(String.format(Locale.US, "%.7f (%.4f%%)", cum, cum * 100), fMono, bg, 2.8f));
            }

            // Ellipsis row
            Color ellBg = SURFACE_TINT;
            table.addCell(bodyCell("... Attempts 4 to " + (n - 1) + " ...", fonts.bodySmall, ellBg, 2.8f));
            table.addCell(bodyCell("Sequential geometric decays", fonts.caption, ellBg, 2.8f));
            table.addCell(bodyCell("Evaluated in model", fonts.caption, ellBg, 2.8f));
            table.addCell(bodyCell("Continuous progression", fonts.caption, ellBg, 2.8f));

            // Attempt n row
            double probN = Math.pow(1.0 - p, n - 1) * p;
            double cumN = 1.0 - Math.pow(1.0 - p, n);
            Color nBg = Color.WHITE;
            table.addCell(bodyCell("Attempt " + n + " (Horizon)", fonts.monoBoldSmall, nBg, 2.8f));
            table.addCell(bodyCell("(1\u2212p)^" + (n - 1) + " \u00B7 p", fonts.monoSmall, nBg, 2.8f));
            table.addCell(bodyCell(String.format(Locale.US, "%.7f (%.4f%%)", probN, probN * 100), fonts.monoSmall, nBg, 2.8f));
            table.addCell(bodyCell(String.format(Locale.US, "%.7f (%.4f%%)", cumN, cumN * 100), fonts.monoBoldSmall, nBg, 2.8f));
        }

        // Cumulative Footer
        PdfPCell fLabel = bodyCell("Cumulative Sum (k = 1 .. " + n + ")", fonts.bodyBoldSmall, NAVY_DARK, 3.2f);
        fLabel.setPhrase(new Phrase("Cumulative Sum (k = 1 .. " + n + ")", fonts.tableHeader));
        table.addCell(fLabel);

        PdfPCell fFormula = bodyCell("R(n) = 1 \u2212 (1 \u2212 p)^n", fonts.monoSmall, NAVY_DARK, 3.2f);
        fFormula.setPhrase(new Phrase("R(n) = 1 \u2212 (1 \u2212 p)^n", fonts.tableHeader));
        table.addCell(fFormula);

        PdfPCell fProb = bodyCell(dashboard.getGeometric().getWithinAttemptsDecimal(), fonts.monoBoldSmall, NAVY_DARK, 3.2f);
        fProb.setPhrase(new Phrase(dashboard.getGeometric().getWithinAttemptsDecimal(), fonts.tableHeader));
        table.addCell(fProb);

        PdfPCell fPct = bodyCell(dashboard.getGeometric().getWithinAttemptsPercent(), fonts.monoBoldSmall, NAVY_DARK, 3.2f);
        fPct.setPhrase(new Phrase(dashboard.getGeometric().getWithinAttemptsPercent(), fonts.tableHeader));
        table.addCell(fPct);

        return table;
    }

    // =========================================================================
    // PAGE 2: GRAPHS AND CONCLUSION
    // =========================================================================

    private void appendPageTwoGraphsAndConclusion(
            Document document,
            ReportFonts fonts,
            PdfReportRequest request,
            AnalysisDashboardView dashboard,
            PoissonResult poissonResult,
            GeometricResult geometricResult) throws DocumentException {

        AnalysisType mode = dashboard.getAnalysisType();
        DashboardCharts charts = dashboard.getCharts();

        document.add(createSectionHeading(fonts, "Visualisation Suite & Practical Conclusion"));

        // Render mode-relevant charts
        if (mode == AnalysisType.COMBINED) {
            byte[] poissonPng = reportChartImageService.resolvePoissonChartPng(request.getPoissonChartImage(), charts);
            byte[] geometricPng = reportChartImageService.resolveGeometricChartPng(request.getGeometricChartImage(), charts);
            byte[] reliabilityPng = reportChartImageService.resolveReliabilityChartPng(request.getReliabilityChartImage(), charts);

            // Row 1: Two charts side-by-side
            PdfPTable twoCharts = new PdfPTable(new float[]{1f, 1f});
            twoCharts.setWidthPercentage(100);
            twoCharts.setSpacingAfter(6f);

            addChartToCell(twoCharts, fonts,
                    "Figure 1: Poisson Drop Distribution (\u03BB = " + charts.getLambdaDisplay() + ", k = " + dashboard.getSubmittedDropCount() + ")",
                    poissonPng, 245f, 120f);
            addChartToCell(twoCharts, fonts,
                    "Figure 2: Geometric First Success Prob. P(Y = k) (p = " + charts.getSuccessProbabilityDisplay() + ")",
                    geometricPng, 245f, 120f);
            document.add(twoCharts);

            // Row 2: Cumulative Reliability Curve full width
            addFullWidthChart(document, fonts,
                    "Figure 3: Cumulative Communication Reliability R(n) = 1 \u2212 (1 \u2212 p)^n across Attempts (n = " + charts.getSelectedMaxAttempts() + ")",
                    reliabilityPng, 512f, 110f);

        } else if (mode == AnalysisType.GEOMETRIC_ONLY) {
            byte[] geometricPng = reportChartImageService.resolveGeometricChartPng(request.getGeometricChartImage(), charts);
            byte[] reliabilityPng = reportChartImageService.resolveReliabilityChartPng(request.getReliabilityChartImage(), charts);

            // Two charts side-by-side
            PdfPTable twoCharts = new PdfPTable(new float[]{1f, 1f});
            twoCharts.setWidthPercentage(100);
            twoCharts.setSpacingAfter(8f);

            addChartToCell(twoCharts, fonts,
                    "Figure 1: Geometric First Success Prob. P(Y = k) (p = " + charts.getSuccessProbabilityDisplay() + ")",
                    geometricPng, 245f, 150f);
            addChartToCell(twoCharts, fonts,
                    "Figure 2: Cumulative Communication Reliability R(n) = 1 \u2212 (1 \u2212 p)^n",
                    reliabilityPng, 245f, 150f);
            document.add(twoCharts);

        } else {
            // POISSON_ONLY
            byte[] poissonPng = reportChartImageService.resolvePoissonChartPng(request.getPoissonChartImage(), charts);
            addFullWidthChart(document, fonts,
                    "Figure 1: Poisson Transmission Drop Probability Distribution (\u03BB = " + charts.getLambdaDisplay() + ", k = " + dashboard.getSubmittedDropCount() + ")",
                    poissonPng, 480f, 185f);
        }

        // Final Practical Conclusion Callout
        String conclusionText = buildFinalConclusion(mode, poissonResult, geometricResult, dashboard);
        document.add(createCompactCalloutBox(
                fonts,
                "FINAL PRACTICAL CONCLUSION",
                conclusionText,
                SURFACE_TINT,
                TABLE_BORDER,
                7f));

        // Model Assumptions and Limitations Callout
        String assumptionsText = buildAssumptionsText(mode);
        document.add(createCompactCalloutBox(
                fonts,
                "MODEL ASSUMPTIONS AND LIMITATIONS",
                assumptionsText,
                BLUE_TINT,
                BLUE_BORDER,
                0f));
    }

    private void addChartToCell(
            PdfPTable table,
            ReportFonts fonts,
            String caption,
            byte[] pngBytes,
            float maxWidth,
            float maxHeight) {
        PdfPCell cell = new PdfPCell();
        cell.setBorderColor(TABLE_BORDER);
        cell.setBorderWidth(0.6f);
        cell.setPadding(4f);
        cell.setBackgroundColor(Color.WHITE);

        Paragraph cap = new Paragraph(caption, fonts.captionBold);
        cap.setSpacingAfter(2f);
        cell.addElement(cap);

        try {
            Image img = Image.getInstance(pngBytes);
            img.scaleToFit(maxWidth, maxHeight);
            img.setAlignment(Element.ALIGN_CENTER);
            cell.addElement(img);
        } catch (Exception ex) {
            Paragraph err = new Paragraph("Graph unavailable: " + ex.getMessage(), fonts.caption);
            cell.addElement(err);
        }
        table.addCell(cell);
    }

    private void addFullWidthChart(
            Document document,
            ReportFonts fonts,
            String caption,
            byte[] pngBytes,
            float maxWidth,
            float maxHeight) throws DocumentException {
        PdfPTable frame = new PdfPTable(1);
        frame.setWidthPercentage(100);
        frame.setSpacingAfter(7f);

        PdfPCell cell = new PdfPCell();
        cell.setBorderColor(TABLE_BORDER);
        cell.setBorderWidth(0.6f);
        cell.setPadding(4f);
        cell.setBackgroundColor(Color.WHITE);

        Paragraph cap = new Paragraph(caption, fonts.captionBold);
        cap.setSpacingAfter(2f);
        cell.addElement(cap);

        try {
            Image img = Image.getInstance(pngBytes);
            img.scaleToFit(maxWidth, maxHeight);
            img.setAlignment(Element.ALIGN_CENTER);
            cell.addElement(img);
        } catch (Exception ex) {
            Paragraph err = new Paragraph("Graph unavailable: " + ex.getMessage(), fonts.caption);
            cell.addElement(err);
        }
        frame.addCell(cell);
        document.add(frame);
    }

    private String buildFinalConclusion(
            AnalysisType analysisType,
            PoissonResult poissonResult,
            GeometricResult geometricResult,
            AnalysisDashboardView dashboard) {
        if (analysisType == AnalysisType.POISSON_ONLY && poissonResult != null) {
            PoissonCards p = dashboard.getPoisson();
            return String.format(
                    Locale.US,
                    "Under average drop rate \u03BB = %s, transmission drop analysis yields an expected E(X) = %s drops per communication interval, an exact-k probability of P(X = %d) = %s (%s), and a clean-interval probability of P(X = 0) = %s (%s). The parameter k = %d represents an evaluated drop count threshold rather than an observed transmission outcome. Calculated expectations reflect long-run statistical behavior across repeated communication windows.",
                    p.getLambdaDisplay(),
                    p.getExpectedDropsValue(),
                    poissonResult.getDropCount(),
                    p.getExactDropDecimal(),
                    p.getExactDropPercent(),
                    p.getProbabilityZeroDecimal(),
                    p.getProbabilityZeroPercent(),
                    poissonResult.getDropCount());
        }
        if (analysisType == AnalysisType.GEOMETRIC_ONLY && geometricResult != null) {
            GeometricCards g = dashboard.getGeometric();
            ReliabilitySummary r = dashboard.getReliability();
            return String.format(
                    Locale.US,
                    "With single-attempt success probability p = %s, first successful transmission requires an expected E(Y) = %s attempts (%s), reaching a cumulative link reliability of R(%d) = P(Y \u2264 %d) = %s (%s) within %d attempts, with a %s (%s) tail exceedance risk P(Y > %d). Attempt 1 carries the highest single-attempt likelihood at %s. Expected attempts represent a long-run statistical average across repeated trials rather than a guaranteed attempt limit for any individual transmission.",
                    g.getSuccessProbabilityDisplay(),
                    g.getExpectedAttemptsValue(),
                    g.getExpectedAttemptsRoundedText(),
                    geometricResult.getMaxAttempts(),
                    geometricResult.getMaxAttempts(),
                    r.getReliabilityDecimal(),
                    r.getReliabilityPercent(),
                    geometricResult.getMaxAttempts(),
                    r.getMoreThanAttemptsDecimal(),
                    r.getMoreThanAttemptsPercent(),
                    geometricResult.getMaxAttempts(),
                    g.getFirstAttemptPercent());
        }
        // COMBINED mode
        PoissonCards p = dashboard.getPoisson();
        GeometricCards g = dashboard.getGeometric();
        ReliabilitySummary r = dashboard.getReliability();
        long kVal = dashboard.getSubmittedDropCount() != null ? Long.parseLong(dashboard.getSubmittedDropCount()) : 0L;
        int nVal = geometricResult.getMaxAttempts();
        return String.format(
                Locale.US,
                "For the evaluated telemetry link, discrete drop analysis yields an expected E(X) = %s drops with evaluated exact-k probability P(X = %d) = %s (%s), while link acquisition achieves an expected E(Y) = %s attempts (%s) and %s cumulative reliability R(%d) = P(Y \u2264 %d) within %d attempts (exceedance risk P(Y > %d) = %s). The parameter k = %d is an evaluated theoretical drop threshold rather than an observed event. Expected attempts represent a statistical average over repeated transmissions rather than an operational guarantee.",
                p.getExpectedDropsValue(),
                kVal,
                p.getExactDropDecimal(),
                p.getExactDropPercent(),
                g.getExpectedAttemptsValue(),
                g.getExpectedAttemptsRoundedText(),
                r.getReliabilityPercent(),
                nVal,
                nVal,
                nVal,
                nVal,
                r.getMoreThanAttemptsPercent(),
                kVal);
    }

    private String buildAssumptionsText(AnalysisType mode) {
        StringBuilder sb = new StringBuilder();
        if (mode == AnalysisType.POISSON_ONLY || mode == AnalysisType.COMBINED) {
            sb.append("\u2022  Poisson Drop Model: Transmission drops are modeled as discrete, independent random events occurring at a constant average rate \u03BB per fixed communication interval. Correlated burst disruptions or non-stationary channel fading lie outside the stationary Poisson model.\n");
        }
        if (mode == AnalysisType.GEOMETRIC_ONLY || mode == AnalysisType.COMBINED) {
            sb.append("\u2022  Geometric First-Success Model: Sequential transmission attempts are modeled as independent Bernoulli trials with constant single-attempt success probability p. Dynamic link adaptation, power escalation, or channel memory effects across retries lie outside the stationary Geometric model.");
        }
        return sb.toString().trim();
    }

    // =========================================================================
    // HELPER LAYOUT & FORMATTING UTILITIES
    // =========================================================================

    private Paragraph createSectionHeading(ReportFonts fonts, String title) {
        Paragraph para = new Paragraph(title, fonts.sectionHeading);
        para.setSpacingBefore(4f);
        para.setSpacingAfter(4f);
        return para;
    }

    private PdfPTable createHorizontalRule(Color color, float thickness, float spacingAfter) {
        PdfPTable ruleTable = new PdfPTable(1);
        ruleTable.setWidthPercentage(100);
        ruleTable.setSpacingAfter(spacingAfter);
        PdfPCell cell = new PdfPCell();
        cell.setBorder(0);
        cell.setBorderWidthBottom(thickness);
        cell.setBorderColorBottom(color);
        cell.setFixedHeight(thickness);
        ruleTable.addCell(cell);
        return ruleTable;
    }

    private PdfPTable createCompactCalloutBox(
            ReportFonts fonts,
            String heading,
            String bodyText,
            Color background,
            Color border,
            float spacingAfter) {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingAfter(spacingAfter);

        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(background);
        cell.setBorderColor(border);
        cell.setBorderWidth(0.6f);
        cell.setBorderWidthLeft(3.0f);
        cell.setBorderColorLeft(BLUE_PRIMARY);
        cell.setPadding(6.5f);

        Paragraph pHead = new Paragraph(heading, fonts.calloutLabel);
        pHead.setSpacingAfter(2.5f);
        cell.addElement(pHead);

        Paragraph pBody = new Paragraph(bodyText, fonts.bodySmall);
        pBody.setLeading(11.5f);
        cell.addElement(pBody);

        table.addCell(cell);
        return table;
    }

    private void addTableHeader(PdfPTable table, ReportFonts fonts, String... headers) {
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, fonts.tableHeader));
            cell.setBackgroundColor(NAVY_DARK);
            cell.setBorderColor(NAVY_DARK);
            cell.setPaddingTop(5f);
            cell.setPaddingBottom(5f);
            cell.setPaddingLeft(5.5f);
            cell.setPaddingRight(5.5f);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            table.addCell(cell);
        }
    }

    private PdfPCell bodyCell(String text, Font font, Color background, float verticalPad) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(background);
        cell.setBorderColor(TABLE_BORDER);
        cell.setBorderWidth(0.5f);
        cell.setPaddingTop(verticalPad);
        cell.setPaddingBottom(verticalPad);
        cell.setPaddingLeft(5.5f);
        cell.setPaddingRight(5.5f);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    private String sanitizeFilenameToken(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "val";
        }
        String sanitized = raw.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        if (sanitized.length() > 24) {
            sanitized = sanitized.substring(0, 24);
        }
        return sanitized;
    }

    private ReportFonts loadReportFonts() throws DocumentException, IOException {
        BaseFont regular = loadUnicodeBaseFont(REGULAR_FONT_CANDIDATES, BaseFont.HELVETICA);
        BaseFont bold = loadUnicodeBaseFont(BOLD_FONT_CANDIDATES, BaseFont.HELVETICA_BOLD);
        BaseFont italic = loadUnicodeBaseFont(ITALIC_FONT_CANDIDATES, BaseFont.HELVETICA_OBLIQUE);
        BaseFont mono = loadUnicodeBaseFont(MONO_FONT_CANDIDATES, BaseFont.COURIER);
        BaseFont monoBold = loadUnicodeBaseFont(MONO_BOLD_FONT_CANDIDATES, BaseFont.COURIER_BOLD);
        return new ReportFonts(regular, bold, italic, mono, monoBold);
    }

    private BaseFont loadUnicodeBaseFont(String[] candidates, String fallbackType1) throws DocumentException, IOException {
        for (String path : candidates) {
            File file = new File(path);
            if (file.exists() && file.isFile()) {
                return BaseFont.createFont(file.getAbsolutePath(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            }
        }
        return BaseFont.createFont(fallbackType1, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
    }

    /**
     * Running header and {@code Page X of Y} footer renderer for every report page.
     */
    private static class AcademicHeaderFooterEvent extends PdfPageEventHelper {

        private final ReportFonts fonts;
        private final String generatedAt;
        private final String footerParams;
        private final String headerScope;
        private PdfTemplate totalPagesTemplate;
        private int renderedPageCount;

        AcademicHeaderFooterEvent(ReportFonts fonts, String generatedAt, String footerParams, String headerScope) {
            this.fonts = fonts;
            this.generatedAt = generatedAt;
            this.footerParams = footerParams;
            this.headerScope = headerScope;
            this.renderedPageCount = 0;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            totalPagesTemplate = writer.getDirectContent().createTemplate(24f, 12f);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            float left = document.left();
            float right = document.right();
            int pageNumber = writer.getPageNumber();
            renderedPageCount = Math.max(renderedPageCount, pageNumber);

            // Running header on Page 2+
            if (pageNumber > 1) {
                float headerRuleY = document.top() + 6f;
                float headerTextY = document.top() + 11f;

                ColumnText.showTextAligned(
                        cb, Element.ALIGN_LEFT,
                        new Phrase(APPLICATION_NAME + "  \u2014  " + PROJECT_TITLE, fonts.headerBold),
                        left, headerTextY, 0);
                ColumnText.showTextAligned(
                        cb, Element.ALIGN_RIGHT,
                        new Phrase(headerScope, fonts.headerMuted),
                        right, headerTextY, 0);

                cb.saveState();
                cb.setColorStroke(BLUE_ACCENT);
                cb.setLineWidth(0.75f);
                cb.moveTo(left, headerRuleY);
                cb.lineTo(right, headerRuleY);
                cb.stroke();
                cb.restoreState();
            }

            // Running footer on every page: "DeepSpace Analytics · [Params] · [Date] ... Page X of Y"
            float footerRuleY = document.bottom() - 8f;
            float footerTextY = document.bottom() - 19f;

            cb.saveState();
            cb.setColorStroke(TABLE_BORDER);
            cb.setLineWidth(0.75f);
            cb.moveTo(left, footerRuleY);
            cb.lineTo(right, footerRuleY);
            cb.stroke();
            cb.restoreState();

            ColumnText.showTextAligned(
                    cb, Element.ALIGN_LEFT,
                    new Phrase(APPLICATION_NAME + "  \u00B7  " + footerParams + "  \u00B7  " + generatedAt, fonts.footerMuted),
                    left, footerTextY, 0);

            String prefix = "Page " + pageNumber + " of ";
            float prefixWidth = fonts.footerBaseFont.getWidthPoint(prefix, 8.0f);
            float totalSlotWidth = fonts.footerBaseFont.getWidthPoint("00", 8.0f);
            float prefixStartX = right - totalSlotWidth - prefixWidth;

            ColumnText.showTextAligned(
                    cb, Element.ALIGN_LEFT,
                    new Phrase(prefix, fonts.footerBold),
                    prefixStartX, footerTextY, 0);
            cb.addTemplate(totalPagesTemplate, right - totalSlotWidth, footerTextY);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            int totalPages = Math.max(1, renderedPageCount);
            ColumnText.showTextAligned(
                    totalPagesTemplate,
                    Element.ALIGN_LEFT,
                    new Phrase(String.valueOf(totalPages), fonts.footerBold),
                    0f, 0f, 0);
        }
    }

    private static class ReportFonts {
        final Font headerAppName;
        final Font headerProjectTitle;
        final Font headerMeta;
        final Font modeBadgeFont;
        final Font sectionHeading;
        final Font body;
        final Font bodySmall;
        final Font bodyBoldSmall;
        final Font caption;
        final Font captionBold;
        final Font calloutLabel;
        final Font monoSmall;
        final Font monoBoldSmall;
        final Font tableHeader;
        final Font headerBold;
        final Font headerMuted;
        final Font footerMuted;
        final Font footerBold;
        final BaseFont footerBaseFont;

        ReportFonts(BaseFont regular, BaseFont bold, BaseFont italic, BaseFont mono, BaseFont monoBold) {
            this.headerAppName = new Font(bold, 13.5f, Font.NORMAL, NAVY_DARK);
            this.headerProjectTitle = new Font(bold, 9.5f, Font.NORMAL, SLATE_BODY);
            this.headerMeta = new Font(regular, 8.0f, Font.NORMAL, SLATE_MUTED);
            this.modeBadgeFont = new Font(bold, 7.8f, Font.NORMAL, BLUE_PRIMARY);
            this.sectionHeading = new Font(bold, 9.8f, Font.NORMAL, NAVY_DARK);
            this.body = new Font(regular, 8.5f, Font.NORMAL, SLATE_BODY);
            this.bodySmall = new Font(regular, 7.8f, Font.NORMAL, SLATE_BODY);
            this.bodyBoldSmall = new Font(bold, 7.8f, Font.NORMAL, NAVY_DARK);
            this.caption = new Font(regular, 7.5f, Font.NORMAL, SLATE_MUTED);
            this.captionBold = new Font(bold, 7.5f, Font.NORMAL, NAVY_DARK);
            this.calloutLabel = new Font(bold, 8.2f, Font.NORMAL, BLUE_PRIMARY);
            this.monoSmall = new Font(mono, 7.8f, Font.NORMAL, NAVY_DARK);
            this.monoBoldSmall = new Font(monoBold, 8.0f, Font.NORMAL, NAVY_DARK);
            this.tableHeader = new Font(bold, 7.8f, Font.NORMAL, Color.WHITE);
            this.headerBold = new Font(bold, 7.8f, Font.NORMAL, NAVY_DARK);
            this.headerMuted = new Font(regular, 7.5f, Font.NORMAL, SLATE_MUTED);
            this.footerMuted = new Font(regular, 7.2f, Font.NORMAL, SLATE_MUTED);
            this.footerBold = new Font(bold, 7.6f, Font.NORMAL, NAVY_DARK);
            this.footerBaseFont = bold;
        }
    }

    /**
     * Immutable generated PDF response bundle carrying binary bytes and download filename.
     */
    public static class GeneratedPdfReport {

        private final byte[] bytes;
        private final String filename;

        public GeneratedPdfReport(byte[] bytes, String filename) {
            this.bytes = bytes;
            this.filename = filename;
        }

        public byte[] getBytes() {
            return bytes;
        }

        public String getFilename() {
            return filename;
        }
    }
}
