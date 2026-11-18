package com.deepspace.analytics.service;

import com.deepspace.analytics.model.AnalysisDashboardView;
import com.deepspace.analytics.model.AnalysisDashboardView.AttemptTableRow;
import com.deepspace.analytics.model.AnalysisDashboardView.DashboardCharts;
import com.deepspace.analytics.model.AnalysisDashboardView.GeometricCards;
import com.deepspace.analytics.model.AnalysisDashboardView.GeometricChartPoint;
import com.deepspace.analytics.model.AnalysisDashboardView.PoissonCards;
import com.deepspace.analytics.model.AnalysisDashboardView.PoissonChartPoint;
import com.deepspace.analytics.model.AnalysisDashboardView.PracticalConclusion;
import com.deepspace.analytics.model.AnalysisDashboardView.ReliabilityChartPoint;
import com.deepspace.analytics.model.AnalysisDashboardView.ReliabilitySummary;
import com.deepspace.analytics.model.AnalysisType;
import com.deepspace.analytics.model.CommunicationAnalysisInput;
import com.deepspace.analytics.model.GeometricResult;
import com.deepspace.analytics.model.GeometricResult.AttemptProbability;
import com.deepspace.analytics.model.PoissonResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

import static com.deepspace.analytics.service.AnalysisFormattingUtils.calculateRoundedExpectedAttempts;
import static com.deepspace.analytics.service.AnalysisFormattingUtils.formatCompact;
import static com.deepspace.analytics.service.AnalysisFormattingUtils.formatCompactPercent;
import static com.deepspace.analytics.service.AnalysisFormattingUtils.formatExpectedAttemptsDecimal;
import static com.deepspace.analytics.service.AnalysisFormattingUtils.formatExpectedAttemptsExplanation;
import static com.deepspace.analytics.service.AnalysisFormattingUtils.formatExpectedAttemptsRoundedText;
import static com.deepspace.analytics.service.AnalysisFormattingUtils.formatProbabilityDecimal;
import static com.deepspace.analytics.service.AnalysisFormattingUtils.formatProbabilityPercent;
import static com.deepspace.analytics.service.AnalysisFormattingUtils.pluralize;
import static com.deepspace.analytics.service.AnalysisFormattingUtils.trimOrFallback;

/**
 * Formats computed {@link PoissonResult} and {@link GeometricResult} outputs into
 * the unified Results Dashboard view model, builds interactive graph datasets
 * directly from {@link PoissonService} and {@link GeometricService}, and generates
 * dynamic, data-driven interpretations without altering any distribution formulas.
 */
@Service
public class AnalysisDashboardService {

    private static final int MAX_POISSON_CHART_BARS = 35;
    private static final int MAX_GEOMETRIC_CHART_POINTS = 60;
    private static final int MIN_RELIABILITY_LINE_POINTS = 5;

    private final PoissonService poissonService;
    private final GeometricService geometricService;

    public AnalysisDashboardService(
            PoissonService poissonService,
            GeometricService geometricService) {
        this.poissonService = poissonService;
        this.geometricService = geometricService;
    }

    /**
     * Builds the combined dashboard view model from validated user inputs and the
     * actual outputs computed by {@link PoissonService} and {@link GeometricService}.
     *
     * @param input           submitted form parameters
     * @param poissonResult   computed Poisson distribution output
     * @param geometricResult computed Geometric distribution output
     * @return immutable {@link AnalysisDashboardView} ready for Thymeleaf rendering
     */
    public AnalysisDashboardView buildDashboardView(
            CommunicationAnalysisInput input,
            PoissonResult poissonResult,
            GeometricResult geometricResult) {
        if (input == null) {
            throw new IllegalArgumentException("Submitted input is required to build the dashboard.");
        }
        AnalysisType mode;
        if (input.getAnalysisType() != null) {
            mode = input.getAnalysisType();
        } else if (poissonResult != null && geometricResult != null) {
            mode = AnalysisType.COMBINED;
        } else if (poissonResult != null) {
            mode = AnalysisType.POISSON_ONLY;
        } else if (geometricResult != null) {
            mode = AnalysisType.GEOMETRIC_ONLY;
        } else {
            throw new IllegalArgumentException(
                    "At least one of PoissonResult or GeometricResult is required to build the dashboard.");
        }
        return buildDashboardView(mode, input, poissonResult, geometricResult);
    }

    /**
     * Builds a mode-aware dashboard view model for {@link AnalysisType#POISSON_ONLY},
     * {@link AnalysisType#GEOMETRIC_ONLY}, or {@link AnalysisType#COMBINED}.
     *
     * @param analysisType    resolved execution mode
     * @param input           submitted form parameters
     * @param poissonResult   computed Poisson output (required when Poisson is active; ignored otherwise)
     * @param geometricResult computed Geometric output (required when Geometric is active; ignored otherwise)
     * @return immutable {@link AnalysisDashboardView} ready for Thymeleaf rendering
     */
    public AnalysisDashboardView buildDashboardView(
            AnalysisType analysisType,
            CommunicationAnalysisInput input,
            PoissonResult poissonResult,
            GeometricResult geometricResult) {
        if (analysisType == null || input == null) {
            throw new IllegalArgumentException(
                    "AnalysisType and submitted input are required to build the dashboard.");
        }

        boolean includePoisson = analysisType.includesPoisson();
        boolean includeGeometric = analysisType.includesGeometric();

        if (includePoisson && poissonResult == null) {
            throw new IllegalArgumentException(
                    "PoissonResult is required for " + analysisType + " analysis.");
        }
        if (includeGeometric && geometricResult == null) {
            throw new IllegalArgumentException(
                    "GeometricResult is required for " + analysisType + " analysis.");
        }

        String submittedLambda = includePoisson
                ? trimOrFallback(input.getLambda(), formatCompact(poissonResult.getLambda(), 7))
                : null;
        String submittedDropCount = includePoisson
                ? trimOrFallback(input.getDropCount(), String.valueOf(poissonResult.getDropCount()))
                : null;
        String submittedSuccessProb = includeGeometric
                ? trimOrFallback(input.getSuccessProbability(), formatCompact(geometricResult.getSuccessProbability(), 7))
                : null;
        String submittedMaxAttempts = includeGeometric
                ? trimOrFallback(input.getMaxAttempts(), String.valueOf(geometricResult.getMaxAttempts()))
                : null;

        PoissonCards poissonCards = includePoisson ? buildPoissonCards(poissonResult) : null;

        GeometricCards geometricCards = null;
        List<AttemptTableRow> attemptRows = Collections.emptyList();
        List<AttemptProbability> rawCumulativeSeries = Collections.emptyList();
        if (includeGeometric) {
            int selectedN = geometricResult.getMaxAttempts();
            int reliabilityHorizon = (selectedN == 1) ? MIN_RELIABILITY_LINE_POINTS : selectedN;
            if (input.getSuccessProbability() != null && !input.getSuccessProbability().trim().isEmpty()) {
                rawCumulativeSeries = geometricService.calculateCumulativeSeries(input, reliabilityHorizon);
            } else {
                rawCumulativeSeries = geometricService.calculateCumulativeSeries(
                        geometricResult.getSuccessProbability(), reliabilityHorizon);
            }
            geometricCards = buildGeometricCards(geometricResult);
            attemptRows = buildAttemptRows(
                    geometricResult.getAttemptProbabilities(), rawCumulativeSeries, geometricResult);
        }

        ReliabilitySummary reliability = includeGeometric
                ? buildReliabilitySummary(analysisType, poissonResult, geometricResult)
                : null;
        List<String> interpretations = buildInterpretations(analysisType, poissonResult, geometricResult);
        DashboardCharts charts = buildDashboardCharts(
                analysisType,
                includePoisson ? poissonResult : null,
                includeGeometric ? geometricResult : null,
                rawCumulativeSeries);
        PracticalConclusion practicalConclusion = buildPracticalConclusion(
                analysisType,
                includePoisson ? poissonResult : null,
                includeGeometric ? geometricResult : null);

        return new AnalysisDashboardView(
                analysisType,
                submittedLambda,
                submittedDropCount,
                submittedSuccessProb,
                submittedMaxAttempts,
                poissonCards,
                geometricCards,
                attemptRows,
                reliability,
                interpretations,
                charts,
                practicalConclusion
        );
    }

    private PoissonCards buildPoissonCards(PoissonResult result) {
        String lambdaText = formatCompact(result.getLambda(), 7);
        long k = result.getDropCount();
        String dropWord = pluralize(k, "drop", "drops");

        String exactDecimal = formatProbabilityDecimal(result.getProbabilityOfExactDrops());
        String exactPercent = formatProbabilityPercent(result.getProbabilityOfExactDrops());
        String exactMeaning = String.format(
                Locale.US,
                "Likelihood of observing exactly %d transmission %s within one interval when the average drop rate is %s.",
                k, dropWord, lambdaText);

        String expectedText = formatCompact(result.getExpectedDrops(), 7);
        String zeroDecimal = formatProbabilityDecimal(result.getProbabilityOfZeroDrops());
        String zeroPercent = formatProbabilityPercent(result.getProbabilityOfZeroDrops());
        String atLeastOneDecimal = formatProbabilityDecimal(result.getProbabilityOfAtLeastOneDrop());
        String atLeastOnePercent = formatProbabilityPercent(result.getProbabilityOfAtLeastOneDrop());
        String varianceText = formatCompact(result.getVariance(), 7);

        return new PoissonCards(
                lambdaText,
                k,
                exactDecimal,
                exactPercent,
                exactMeaning,
                expectedText,
                zeroDecimal,
                zeroPercent,
                atLeastOneDecimal,
                atLeastOnePercent,
                varianceText
        );
    }

    private GeometricCards buildGeometricCards(GeometricResult result) {
        String pText = formatCompact(result.getSuccessProbability(), 7);
        int n = result.getMaxAttempts();
        String attemptWord = pluralize(n, "attempt", "attempts");

        double expected = result.getExpectedAttempts();
        String expectedAttemptsText = formatExpectedAttemptsDecimal(expected);
        long expectedAttemptsRounded = calculateRoundedExpectedAttempts(expected);
        String expectedAttemptsRoundedText = formatExpectedAttemptsRoundedText(expected);
        String expectedAttemptsExplanation = formatExpectedAttemptsExplanation();

        String withinDecimal = formatProbabilityDecimal(result.getProbabilityWithinAttempts());
        String withinPercent = formatProbabilityPercent(result.getProbabilityWithinAttempts());
        String withinMeaning = String.format(
                Locale.US,
                "Cumulative probability R(%d) that the first successful link occurs on or before %d %s.",
                n, n, attemptWord);

        String moreThanDecimal = formatProbabilityDecimal(result.getProbabilityMoreThanAttempts());
        String moreThanPercent = formatProbabilityPercent(result.getProbabilityMoreThanAttempts());
        String moreThanMeaning = String.format(
                Locale.US,
                "Tail probability that all first %d %s fail with single-attempt success rate %s.",
                n, attemptWord, pText);

        double firstAttemptProb = result.getAttemptProbabilities().isEmpty()
                ? result.getSuccessProbability()
                : result.getAttemptProbabilities().get(0).getProbability();

        return new GeometricCards(
                pText,
                n,
                expectedAttemptsText,
                expectedAttemptsRounded,
                expectedAttemptsRoundedText,
                expectedAttemptsExplanation,
                withinDecimal,
                withinPercent,
                withinMeaning,
                moreThanDecimal,
                moreThanPercent,
                moreThanMeaning,
                result.getMostLikelyAttempt(),
                formatProbabilityDecimal(firstAttemptProb),
                formatProbabilityPercent(firstAttemptProb)
        );
    }

    private List<AttemptTableRow> buildAttemptRows(
            List<AttemptProbability> probabilities,
            List<AttemptProbability> cumulativeSeries,
            GeometricResult geometricResult) {
        List<AttemptTableRow> rows = new ArrayList<>(probabilities.size());
        int selectedN = geometricResult.getMaxAttempts();
        for (int i = 0; i < probabilities.size(); i++) {
            AttemptProbability entry = probabilities.get(i);
            String cumulativeDec = "";
            String cumulativePct = "";
            if (i < cumulativeSeries.size()) {
                double cumProb = (entry.getAttempt() == selectedN)
                        ? geometricResult.getProbabilityWithinAttempts()
                        : cumulativeSeries.get(i).getProbability();
                cumulativeDec = formatProbabilityDecimal(cumProb);
                cumulativePct = formatProbabilityPercent(cumProb);
            }
            rows.add(new AttemptTableRow(
                    entry.getAttempt(),
                    formatProbabilityDecimal(entry.getProbability()),
                    formatProbabilityPercent(entry.getProbability()),
                    cumulativeDec,
                    cumulativePct
            ));
        }
        return rows;
    }

    private ReliabilitySummary buildReliabilitySummary(
            AnalysisType analysisType,
            PoissonResult poisson,
            GeometricResult geometric) {
        if (!analysisType.includesReliability() || geometric == null) {
            return null;
        }

        boolean includePoisson = analysisType.includesPoisson() && poisson != null;

        String expectedDrops = includePoisson ? formatCompact(poisson.getExpectedDrops(), 7) : null;
        long dropCount = includePoisson ? poisson.getDropCount() : 0L;
        String exactDropDecimal = includePoisson ? formatProbabilityDecimal(poisson.getProbabilityOfExactDrops()) : null;
        String exactDropPercent = includePoisson ? formatProbabilityPercent(poisson.getProbabilityOfExactDrops()) : null;

        double expAttempts = geometric.getExpectedAttempts();
        String expectedAttempts = formatExpectedAttemptsDecimal(expAttempts);
        long expectedAttemptsRounded = calculateRoundedExpectedAttempts(expAttempts);
        String expectedAttemptsRoundedText = formatExpectedAttemptsRoundedText(expAttempts);
        String expectedAttemptsExplanation = formatExpectedAttemptsExplanation();
        int n = geometric.getMaxAttempts();
        String nWord = pluralize(n, "attempt", "attempts");

        double reliabilityProb = geometric.getProbabilityWithinAttempts();
        String reliabilityDecimal = formatProbabilityDecimal(reliabilityProb);
        String reliabilityPercent = formatProbabilityPercent(reliabilityProb);
        String moreThanAttemptsDecimal = formatProbabilityDecimal(geometric.getProbabilityMoreThanAttempts());
        String moreThanAttemptsPercent = formatProbabilityPercent(geometric.getProbabilityMoreThanAttempts());

        String statusBadge;
        if (reliabilityProb >= 0.95) {
            statusBadge = "High Link Acquisition Reliability";
        } else if (reliabilityProb >= 0.75) {
            statusBadge = "Moderate-to-High Link Acquisition Reliability";
        } else if (reliabilityProb >= 0.50) {
            statusBadge = "Moderate Link Acquisition Reliability";
        } else {
            statusBadge = "Low Link Acquisition Reliability";
        }

        final String overallInterpretation;
        final String clarification;
        if (includePoisson) {
            String kDropWord = pluralize(dropCount, "drop", "drops");
            overallInterpretation = String.format(
                    Locale.US,
                    "Under the selected communication conditions, the expected number of transmission drops is %s, with a %s (%s) probability of experiencing exactly %d %s (the evaluated drop count). The expected number of attempts until the first successful transmission is %s (%s). The probability of achieving at least one successful transmission within %d %s is %s (%s), while the probability of requiring more than %d %s is %s (%s).",
                    expectedDrops,
                    exactDropDecimal,
                    exactDropPercent,
                    dropCount,
                    kDropWord,
                    expectedAttempts,
                    expectedAttemptsRoundedText,
                    n,
                    nWord,
                    reliabilityDecimal,
                    reliabilityPercent,
                    n,
                    nWord,
                    moreThanAttemptsDecimal,
                    moreThanAttemptsPercent);

            clarification = String.format(
                    Locale.US,
                    "Mathematical Clarification: The Poisson model independently evaluates transmission drop behavior per interval (E(X) = %s and P(X = %d) = %s), whereas the Geometric model independently evaluates link acquisition using R(%d) = 1 − (1 − p)^%d = %s (%s) and P(X > %d) = %s (%s). Poisson and Geometric probabilities are never multiplied together and do not form a combined probability distribution.",
                    expectedDrops,
                    dropCount,
                    exactDropDecimal,
                    n,
                    n,
                    reliabilityDecimal,
                    reliabilityPercent,
                    n,
                    moreThanAttemptsDecimal,
                    moreThanAttemptsPercent);
        } else {
            overallInterpretation = String.format(
                    Locale.US,
                    "Under the selected Geometric communication parameters (p = %s, n = %d), the expected number of attempts until the first successful transmission is %s (%s). The cumulative reliability of achieving at least one successful transmission within %d %s is %s (%s), while the probability of requiring more than %d %s is %s (%s).",
                    formatCompact(geometric.getSuccessProbability(), 7),
                    n,
                    expectedAttempts,
                    expectedAttemptsRoundedText,
                    n,
                    nWord,
                    reliabilityDecimal,
                    reliabilityPercent,
                    n,
                    nWord,
                    moreThanAttemptsDecimal,
                    moreThanAttemptsPercent);

            clarification = String.format(
                    Locale.US,
                    "Mathematical Clarification: Cumulative communication reliability within %d %s is evaluated using the Geometric cumulative model R(%d) = 1 − (1 − p)^%d = %s (%s), with exceedance probability P(X > %d) = (1 − p)^%d = %s (%s).",
                    n,
                    nWord,
                    n,
                    n,
                    reliabilityDecimal,
                    reliabilityPercent,
                    n,
                    n,
                    moreThanAttemptsDecimal,
                    moreThanAttemptsPercent);
        }

        double p = geometric.getSuccessProbability();
        int targetAttempts90 = calculateTargetAttempts(p, 0.90);
        int targetAttempts95 = calculateTargetAttempts(p, 0.95);
        int targetAttempts99 = calculateTargetAttempts(p, 0.99);

        String targetReliability90Percent = formatProbabilityPercent(calculateCumulativeReliability(p, targetAttempts90));
        String targetReliability95Percent = formatProbabilityPercent(calculateCumulativeReliability(p, targetAttempts95));
        String targetReliability99Percent = formatProbabilityPercent(calculateCumulativeReliability(p, targetAttempts99));

        return new ReliabilitySummary(
                expectedDrops,
                dropCount,
                exactDropDecimal,
                exactDropPercent,
                expectedAttempts,
                expectedAttemptsRounded,
                expectedAttemptsRoundedText,
                expectedAttemptsExplanation,
                n,
                reliabilityDecimal,
                reliabilityPercent,
                moreThanAttemptsDecimal,
                moreThanAttemptsPercent,
                statusBadge,
                overallInterpretation,
                clarification,
                targetAttempts90,
                targetAttempts95,
                targetAttempts99,
                targetReliability90Percent,
                targetReliability95Percent,
                targetReliability99Percent
        );
    }

    public static int calculateTargetAttempts(double p, double target) {
        if (p <= 0.0 || target <= 0.0 || target >= 1.0) {
            return 1;
        }
        if (p >= target || p >= 1.0) {
            return 1;
        }
        double q = 1.0 - p;
        double required = Math.ceil(Math.log(1.0 - target) / Math.log(q));
        return (int) Math.max(1, required);
    }

    public static double calculateCumulativeReliability(double p, int m) {
        if (p >= 1.0) {
            return 1.0;
        }
        if (p <= 0.0 || m < 1) {
            return 0.0;
        }
        double q = 1.0 - p;
        double qPowM = Math.pow(q, m);
        return Math.max(0.0, Math.min(1.0, 1.0 - qPowM));
    }

    public PracticalConclusion buildPracticalConclusion(
            AnalysisType analysisType,
            PoissonResult poisson,
            GeometricResult geometric) {
        if (analysisType == null) {
            analysisType = AnalysisType.COMBINED;
        }

        switch (analysisType) {
            case POISSON_ONLY: {
                if (poisson == null) {
                    return new PracticalConclusion(
                            AnalysisType.POISSON_ONLY,
                            "Transmission Drop Analysis",
                            "No transmission drop calculations available.",
                            "");
                }
                String lambdaStr = formatCompact(poisson.getExpectedDrops(), 7);
                long k = poisson.getDropCount();
                String kWord = pluralize(k, "drop", "drops");
                String exactDec = formatProbabilityDecimal(poisson.getProbabilityOfExactDrops());
                String exactPct = formatProbabilityPercent(poisson.getProbabilityOfExactDrops());

                String headline = "Transmission Drop Rate & Evaluated Drop Likelihood";
                String narrative = String.format(
                        Locale.US,
                        "Under the configured communication conditions, an average of %s transmission drops is expected per communication interval. The probability of experiencing exactly %d %s (the selected drop count evaluated) is %s (%s). Over repeated communication intervals, drop counts will fluctuate around the average of %s drops per window.",
                        lambdaStr,
                        k,
                        kWord,
                        exactDec,
                        exactPct,
                        lambdaStr);
                String technicalNote = String.format(
                        Locale.US,
                        "Important note: The count of %d %s represents the specific evaluated parameter (k), not a guaranteed or observed fixed outcome for future communication windows.",
                        k,
                        kWord);
                return new PracticalConclusion(analysisType, headline, narrative, technicalNote);
            }
            case GEOMETRIC_ONLY: {
                if (geometric == null) {
                    return new PracticalConclusion(
                            AnalysisType.GEOMETRIC_ONLY,
                            "Link Acquisition Speed & Multi-Attempt Success Probability",
                            "No link acquisition calculations available.",
                            "");
                }
                double exp = geometric.getExpectedAttempts();
                String expDec = formatExpectedAttemptsDecimal(exp);
                String roundedText = formatExpectedAttemptsRoundedText(exp);
                int n = geometric.getMaxAttempts();
                String nWord = pluralize(n, "attempt", "attempts");
                String withinDec = formatProbabilityDecimal(geometric.getProbabilityWithinAttempts());
                String withinPct = formatProbabilityPercent(geometric.getProbabilityWithinAttempts());
                String moreThanDec = formatProbabilityDecimal(geometric.getProbabilityMoreThanAttempts());
                String moreThanPct = formatProbabilityPercent(geometric.getProbabilityMoreThanAttempts());

                String headline = "Link Acquisition Speed & Multi-Attempt Success Probability";
                String narrative = String.format(
                        Locale.US,
                        "The communication link is expected to achieve its first successful transmission in %s attempts (%s). Within %d allowed %s, the cumulative probability of establishing a successful link is %s (%s), leaving a %s (%s) tail probability of requiring more than %d %s.",
                        expDec,
                        roundedText,
                        n,
                        nWord,
                        withinDec,
                        withinPct,
                        moreThanDec,
                        moreThanPct,
                        n,
                        nWord);
                String technicalNote = String.format(
                        Locale.US,
                        "Important note: The expected value (%s) is a mathematical average across many independent transmissions, not a guarantee that any individual transmission will succeed on or before that attempt.",
                        roundedText);
                return new PracticalConclusion(analysisType, headline, narrative, technicalNote);
            }
            case COMBINED:
            default: {
                if (poisson == null || geometric == null) {
                    return new PracticalConclusion(
                            AnalysisType.COMBINED,
                            "Dual-Model Communication Reliability Assessment",
                            "Dual-model calculations incomplete.",
                            "");
                }
                String lambdaStr = formatCompact(poisson.getExpectedDrops(), 7);
                long k = poisson.getDropCount();
                String kWord = pluralize(k, "drop", "drops");
                String exactDec = formatProbabilityDecimal(poisson.getProbabilityOfExactDrops());
                String exactPct = formatProbabilityPercent(poisson.getProbabilityOfExactDrops());

                double exp = geometric.getExpectedAttempts();
                String expDec = formatExpectedAttemptsDecimal(exp);
                String roundedText = formatExpectedAttemptsRoundedText(exp);
                int n = geometric.getMaxAttempts();
                String nWord = pluralize(n, "attempt", "attempts");
                String withinDec = formatProbabilityDecimal(geometric.getProbabilityWithinAttempts());
                String withinPct = formatProbabilityPercent(geometric.getProbabilityWithinAttempts());

                String headline = "Combined Spacecraft Telemetry & Link Acquisition Assessment";
                String narrative = String.format(
                        Locale.US,
                        "Under the configured communication conditions, an average of %s transmission drops is expected per interval (E(X) = %s), with a %s (%s) probability of experiencing exactly %d %s (the selected drop count evaluated). For link establishment, the expected number of attempts until the first successful transmission is %s (%s), achieving a cumulative reliability of %s (%s) within %d %s.",
                        lambdaStr,
                        lambdaStr,
                        exactDec,
                        exactPct,
                        k,
                        kWord,
                        expDec,
                        roundedText,
                        withinDec,
                        withinPct,
                        n,
                        nWord);
                String technicalNote = String.format(
                        Locale.US,
                        "Important distinctions: The evaluated drop count (%d %s) is a specific scenario tested rather than a fixed outcome. The expected %s is a statistical average rather than a guaranteed attempt count. Poisson drop frequency and Geometric link retries evaluate independent communication phenomena and are not combined into a joint probability distribution.",
                        k,
                        kWord,
                        roundedText);
                return new PracticalConclusion(analysisType, headline, narrative, technicalNote);
            }
        }
    }

    private String formatHumanExpectedAttempts(double expectedAttempts) {
        if (!Double.isFinite(expectedAttempts)) {
            return "0";
        }
        if (Math.abs(expectedAttempts) >= 1e9) {
            return formatCompact(expectedAttempts, 7);
        }
        BigDecimal exact = BigDecimal.valueOf(expectedAttempts);
        BigDecimal rounded2 = exact.setScale(2, RoundingMode.HALF_UP);
        if (exact.subtract(rounded2).abs().compareTo(new BigDecimal("0.000001")) <= 0) {
            return rounded2.stripTrailingZeros().toPlainString();
        }
        return "approximately " + rounded2.toPlainString();
    }

    private List<String> buildInterpretations(
            AnalysisType analysisType,
            PoissonResult poisson,
            GeometricResult geometric) {
        List<String> lines = new ArrayList<>(6);

        if (analysisType.includesPoisson() && poisson != null) {
            long k = poisson.getDropCount();
            String kDropWord = pluralize(k, "drop", "drops");
            lines.add(String.format(
                    Locale.US,
                    "The probability of exactly %d transmission %s %s (%s).",
                    k,
                    kDropWord,
                    formatReadablePercentClause(poisson.getProbabilityOfExactDrops()),
                    formatProbabilityDecimal(poisson.getProbabilityOfExactDrops())));

            lines.add(String.format(
                    Locale.US,
                    "The expected number of transmission drops is %s, and the variance of transmission drops is %s.",
                    formatCompact(poisson.getExpectedDrops(), 7),
                    formatCompact(poisson.getVariance(), 7)));

            lines.add(String.format(
                    Locale.US,
                    "The probability of experiencing zero transmission drops %s (%s), whereas the probability of at least one transmission drop %s (%s).",
                    formatReadablePercentClause(poisson.getProbabilityOfZeroDrops()),
                    formatProbabilityDecimal(poisson.getProbabilityOfZeroDrops()),
                    formatReadablePercentClause(poisson.getProbabilityOfAtLeastOneDrop()),
                    formatProbabilityDecimal(poisson.getProbabilityOfAtLeastOneDrop())));
        }

        if (analysisType.includesGeometric() && geometric != null) {
            lines.add(String.format(
                    Locale.US,
                    "The expected number of attempts before the first successful transmission is %s.",
                    formatCompact(geometric.getExpectedAttempts(), 7)));

            int n = geometric.getMaxAttempts();
            String nAttemptWord = pluralize(n, "attempt", "attempts");
            lines.add(String.format(
                    Locale.US,
                    "The probability of achieving at least one successful transmission within %d %s %s (%s), leaving a probability of %s (%s) that more than %d %s will be required.",
                    n,
                    nAttemptWord,
                    formatReadablePercentClause(geometric.getProbabilityWithinAttempts()),
                    formatProbabilityDecimal(geometric.getProbabilityWithinAttempts()),
                    formatCompactPercent(geometric.getProbabilityMoreThanAttempts()),
                    formatProbabilityDecimal(geometric.getProbabilityMoreThanAttempts()),
                    n,
                    nAttemptWord));

            lines.add(String.format(
                    Locale.US,
                    "Attempt %d is the most likely attempt for the first successful transmission, occurring with probability %s (%s).",
                    geometric.getMostLikelyAttempt(),
                    formatCompactPercent(geometric.getSuccessProbability()),
                    formatProbabilityDecimal(geometric.getSuccessProbability())));
        }

        return lines;
    }

    private DashboardCharts buildDashboardCharts(
            AnalysisType analysisType,
            PoissonResult poissonResult,
            GeometricResult geometricResult,
            List<AttemptProbability> rawCumulativeSeries) {
        boolean includePoisson = analysisType.includesPoisson() && poissonResult != null;
        boolean includeGeometric = analysisType.includesGeometric() && geometricResult != null;

        List<PoissonChartPoint> poissonSeries = includePoisson
                ? buildPoissonChartSeries(poissonResult)
                : Collections.emptyList();
        List<GeometricChartPoint> geometricSeries = includeGeometric
                ? buildGeometricChartSeries(geometricResult)
                : Collections.emptyList();
        List<ReliabilityChartPoint> reliabilitySeries = includeGeometric
                ? buildReliabilityChartSeries(rawCumulativeSeries, geometricResult)
                : Collections.emptyList();

        return new DashboardCharts(
                analysisType,
                includePoisson ? formatCompact(poissonResult.getLambda(), 7) : null,
                includePoisson ? poissonResult.getDropCount() : 0L,
                poissonSeries,
                includeGeometric ? formatCompact(geometricResult.getSuccessProbability(), 7) : null,
                includeGeometric ? geometricResult.getMaxAttempts() : 0,
                includeGeometric ? geometricResult.getMostLikelyAttempt() : 0,
                geometricSeries,
                reliabilitySeries
        );
    }

    private List<PoissonChartPoint> buildPoissonChartSeries(PoissonResult poissonResult) {
        double lambda = poissonResult.getLambda();
        long selectedK = poissonResult.getDropCount();

        long tailUpper = (lambda == 0.0)
                ? 5L
                : (long) Math.ceil(lambda + 4.0 * Math.sqrt(lambda));
        long upperBound = Math.max(8L, tailUpper);
        if (selectedK <= Long.MAX_VALUE - 2L) {
            upperBound = Math.max(upperBound, selectedK + 2L);
        } else {
            upperBound = selectedK;
        }

        long startK = 0L;
        long endK = upperBound;
        if (endK > MAX_POISSON_CHART_BARS - 1L) {
            long halfWindow = MAX_POISSON_CHART_BARS / 2L;
            startK = (selectedK > halfWindow) ? selectedK - halfWindow : 0L;
            long maxOffset = MAX_POISSON_CHART_BARS - 1L;
            endK = (startK <= Long.MAX_VALUE - maxOffset) ? startK + maxOffset : Long.MAX_VALUE;
            if (selectedK > endK) {
                endK = selectedK;
                startK = Math.max(0L, endK - maxOffset);
            }
        }

        int barCount = (int) (endK - startK + 1L);
        List<PoissonChartPoint> points = new ArrayList<>(barCount);
        for (long k = startK; k <= endK; k++) {
            double prob = (k == selectedK)
                    ? poissonResult.getProbabilityOfExactDrops()
                    : poissonService.calculate(lambda, k).getProbabilityOfExactDrops();
            points.add(new PoissonChartPoint(
                    k,
                    prob,
                    formatProbabilityDecimal(prob),
                    formatProbabilityPercent(prob),
                    k == selectedK
            ));
            if (k == Long.MAX_VALUE) {
                break;
            }
        }
        return points;
    }

    private List<GeometricChartPoint> buildGeometricChartSeries(GeometricResult geometricResult) {
        int mostLikely = geometricResult.getMostLikelyAttempt();
        List<AttemptProbability> probabilities = geometricResult.getAttemptProbabilities();
        List<Integer> indices = selectSampledIndices(
                probabilities.size(), MAX_GEOMETRIC_CHART_POINTS, 0, probabilities.size() - 1);
        List<GeometricChartPoint> points = new ArrayList<>(indices.size());
        for (int idx : indices) {
            AttemptProbability entry = probabilities.get(idx);
            double prob = entry.getProbability();
            points.add(new GeometricChartPoint(
                    entry.getAttempt(),
                    prob,
                    formatProbabilityDecimal(prob),
                    formatProbabilityPercent(prob),
                    entry.getAttempt() == mostLikely
            ));
        }
        return points;
    }

    private List<ReliabilityChartPoint> buildReliabilityChartSeries(
            List<AttemptProbability> cumulativeSeries,
            GeometricResult geometricResult) {
        int selectedN = geometricResult.getMaxAttempts();
        int selectedIdx = Math.max(0, Math.min(cumulativeSeries.size() - 1, selectedN - 1));
        List<Integer> indices = selectSampledIndices(
                cumulativeSeries.size(),
                MAX_GEOMETRIC_CHART_POINTS,
                0,
                selectedIdx,
                cumulativeSeries.size() - 1);
        List<ReliabilityChartPoint> points = new ArrayList<>(indices.size());
        for (int idx : indices) {
            AttemptProbability entry = cumulativeSeries.get(idx);
            double prob = (entry.getAttempt() == selectedN)
                    ? geometricResult.getProbabilityWithinAttempts()
                    : entry.getProbability();
            points.add(new ReliabilityChartPoint(
                    entry.getAttempt(),
                    prob,
                    formatProbabilityDecimal(prob),
                    formatProbabilityPercent(prob),
                    entry.getAttempt() == selectedN
            ));
        }
        return points;
    }

    private List<Integer> selectSampledIndices(int size, int maxPoints, int... requiredIndices) {
        if (size <= maxPoints) {
            List<Integer> all = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                all.add(i);
            }
            return all;
        }
        TreeSet<Integer> selected = new TreeSet<>();
        for (int i = 0; i < maxPoints; i++) {
            int idx = (int) Math.round((double) i * (size - 1) / (maxPoints - 1));
            selected.add(Math.max(0, Math.min(size - 1, idx)));
        }
        for (int req : requiredIndices) {
            if (req >= 0 && req < size) {
                selected.add(req);
            }
        }
        return new ArrayList<>(selected);
    }

    private String formatReadablePercentClause(double probability) {
        if (!Double.isFinite(probability)) {
            return "is 0%";
        }
        double percent = probability * 100.0;
        if (percent == 0.0) {
            return "is 0%";
        }
        BigDecimal bd = BigDecimal.valueOf(percent);
        BigDecimal rounded4 = bd.setScale(4, RoundingMode.HALF_UP);
        if (bd.subtract(rounded4).abs().compareTo(new BigDecimal("0.0000001")) <= 0) {
            return "is " + rounded4.stripTrailingZeros().toPlainString() + "%";
        }
        if (percent < 0.01) {
            if (percent < 0.0001) {
                return String.format(Locale.US, "is approximately %.3e%%", percent);
            }
            return "is approximately " + bd.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
        }
        return String.format(Locale.US, "is approximately %.2f%%", percent);
    }
}
