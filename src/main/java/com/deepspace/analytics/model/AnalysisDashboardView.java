package com.deepspace.analytics.model;

import java.util.Collections;
import java.util.List;

/**
 * Structured view model for the Results Dashboard.
 *
 * <p>Combines formatted outputs from {@link PoissonResult} and/or {@link GeometricResult}
 * according to the active {@link AnalysisType}, alongside dynamic plain-English
 * interpretations and reliability metrics without altering the underlying
 * distribution calculations.
 */
public class AnalysisDashboardView {

    private final AnalysisType analysisType;

    private final String submittedLambda;
    private final String submittedDropCount;
    private final String submittedSuccessProbability;
    private final String submittedMaxAttempts;

    private final PoissonCards poisson;
    private final GeometricCards geometric;
    private final List<AttemptTableRow> attemptRows;
    private final ReliabilitySummary reliability;
    private final List<String> interpretations;
    private final DashboardCharts charts;
    private final PracticalConclusion practicalConclusion;

    public AnalysisDashboardView(
            String submittedLambda,
            String submittedDropCount,
            String submittedSuccessProbability,
            String submittedMaxAttempts,
            PoissonCards poisson,
            GeometricCards geometric,
            List<AttemptTableRow> attemptRows,
            ReliabilitySummary reliability,
            List<String> interpretations,
            DashboardCharts charts) {
        this(
                AnalysisType.COMBINED,
                submittedLambda,
                submittedDropCount,
                submittedSuccessProbability,
                submittedMaxAttempts,
                poisson,
                geometric,
                attemptRows,
                reliability,
                interpretations,
                charts,
                null
        );
    }

    public AnalysisDashboardView(
            AnalysisType analysisType,
            String submittedLambda,
            String submittedDropCount,
            String submittedSuccessProbability,
            String submittedMaxAttempts,
            PoissonCards poisson,
            GeometricCards geometric,
            List<AttemptTableRow> attemptRows,
            ReliabilitySummary reliability,
            List<String> interpretations,
            DashboardCharts charts) {
        this(
                analysisType,
                submittedLambda,
                submittedDropCount,
                submittedSuccessProbability,
                submittedMaxAttempts,
                poisson,
                geometric,
                attemptRows,
                reliability,
                interpretations,
                charts,
                null
        );
    }

    public AnalysisDashboardView(
            AnalysisType analysisType,
            String submittedLambda,
            String submittedDropCount,
            String submittedSuccessProbability,
            String submittedMaxAttempts,
            PoissonCards poisson,
            GeometricCards geometric,
            List<AttemptTableRow> attemptRows,
            ReliabilitySummary reliability,
            List<String> interpretations,
            DashboardCharts charts,
            PracticalConclusion practicalConclusion) {
        this.analysisType = (analysisType != null) ? analysisType : AnalysisType.COMBINED;
        this.submittedLambda = submittedLambda;
        this.submittedDropCount = submittedDropCount;
        this.submittedSuccessProbability = submittedSuccessProbability;
        this.submittedMaxAttempts = submittedMaxAttempts;
        this.poisson = poisson;
        this.geometric = geometric;
        this.attemptRows = (attemptRows != null)
                ? Collections.unmodifiableList(attemptRows)
                : Collections.emptyList();
        this.reliability = reliability;
        this.interpretations = (interpretations != null)
                ? Collections.unmodifiableList(interpretations)
                : Collections.emptyList();
        this.charts = charts;
        this.practicalConclusion = practicalConclusion;
    }

    public AnalysisType getAnalysisType() {
        return analysisType;
    }

    public boolean isPoissonAvailable() {
        return analysisType.includesPoisson() && poisson != null;
    }

    public boolean isGeometricAvailable() {
        return analysisType.includesGeometric() && geometric != null;
    }

    public boolean isReliabilityAvailable() {
        return analysisType.includesReliability() && reliability != null;
    }

    public boolean isCombined() {
        return analysisType == AnalysisType.COMBINED;
    }

    public String getSubmittedLambda() {
        return submittedLambda;
    }

    public String getSubmittedDropCount() {
        return submittedDropCount;
    }

    public String getSubmittedSuccessProbability() {
        return submittedSuccessProbability;
    }

    public String getSubmittedMaxAttempts() {
        return submittedMaxAttempts;
    }

    public PoissonCards getPoisson() {
        return poisson;
    }

    public GeometricCards getGeometric() {
        return geometric;
    }

    public List<AttemptTableRow> getAttemptRows() {
        return attemptRows;
    }

    public ReliabilitySummary getReliability() {
        return reliability;
    }

    public List<String> getInterpretations() {
        return interpretations;
    }

    public DashboardCharts getCharts() {
        return charts;
    }

    public PracticalConclusion getPracticalConclusion() {
        return practicalConclusion;
    }

    /**
     * Formatted metrics for Section 2 (Poisson Distribution Results).
     */
    public static class PoissonCards {

        private final String lambdaDisplay;
        private final long dropCount;
        private final String exactDropDecimal;
        private final String exactDropPercent;
        private final String exactDropMeaning;
        private final String expectedDropsValue;
        private final String probabilityZeroDecimal;
        private final String probabilityZeroPercent;
        private final String probabilityAtLeastOneDecimal;
        private final String probabilityAtLeastOnePercent;
        private final String varianceValue;

        public PoissonCards(
                String lambdaDisplay,
                long dropCount,
                String exactDropDecimal,
                String exactDropPercent,
                String exactDropMeaning,
                String expectedDropsValue,
                String probabilityZeroDecimal,
                String probabilityZeroPercent,
                String probabilityAtLeastOneDecimal,
                String probabilityAtLeastOnePercent,
                String varianceValue) {
            this.lambdaDisplay = lambdaDisplay;
            this.dropCount = dropCount;
            this.exactDropDecimal = exactDropDecimal;
            this.exactDropPercent = exactDropPercent;
            this.exactDropMeaning = exactDropMeaning;
            this.expectedDropsValue = expectedDropsValue;
            this.probabilityZeroDecimal = probabilityZeroDecimal;
            this.probabilityZeroPercent = probabilityZeroPercent;
            this.probabilityAtLeastOneDecimal = probabilityAtLeastOneDecimal;
            this.probabilityAtLeastOnePercent = probabilityAtLeastOnePercent;
            this.varianceValue = varianceValue;
        }

        public String getLambdaDisplay() {
            return lambdaDisplay;
        }

        public long getDropCount() {
            return dropCount;
        }

        public String getExactDropDecimal() {
            return exactDropDecimal;
        }

        public String getExactDropPercent() {
            return exactDropPercent;
        }

        public String getExactDropMeaning() {
            return exactDropMeaning;
        }

        public String getExpectedDropsValue() {
            return expectedDropsValue;
        }

        public String getProbabilityZeroDecimal() {
            return probabilityZeroDecimal;
        }

        public String getProbabilityZeroPercent() {
            return probabilityZeroPercent;
        }

        public String getProbabilityAtLeastOneDecimal() {
            return probabilityAtLeastOneDecimal;
        }

        public String getProbabilityAtLeastOnePercent() {
            return probabilityAtLeastOnePercent;
        }

        public String getVarianceValue() {
            return varianceValue;
        }
    }

    /**
     * Formatted metrics for Section 3 (Geometric Distribution Results).
     */
    public static class GeometricCards {

        private final String successProbabilityDisplay;
        private final int maxAttempts;
        private final String expectedAttemptsValue;
        private final long expectedAttemptsRounded;
        private final String expectedAttemptsRoundedText;
        private final String expectedAttemptsExplanation;
        private final String withinAttemptsDecimal;
        private final String withinAttemptsPercent;
        private final String withinAttemptsMeaning;
        private final String moreThanAttemptsDecimal;
        private final String moreThanAttemptsPercent;
        private final String moreThanAttemptsMeaning;
        private final int mostLikelyAttempt;
        private final String firstAttemptDecimal;
        private final String firstAttemptPercent;

        public GeometricCards(
                String successProbabilityDisplay,
                int maxAttempts,
                String expectedAttemptsValue,
                long expectedAttemptsRounded,
                String expectedAttemptsRoundedText,
                String expectedAttemptsExplanation,
                String withinAttemptsDecimal,
                String withinAttemptsPercent,
                String withinAttemptsMeaning,
                String moreThanAttemptsDecimal,
                String moreThanAttemptsPercent,
                String moreThanAttemptsMeaning,
                int mostLikelyAttempt,
                String firstAttemptDecimal,
                String firstAttemptPercent) {
            this.successProbabilityDisplay = successProbabilityDisplay;
            this.maxAttempts = maxAttempts;
            this.expectedAttemptsValue = expectedAttemptsValue;
            this.expectedAttemptsRounded = expectedAttemptsRounded;
            this.expectedAttemptsRoundedText = expectedAttemptsRoundedText;
            this.expectedAttemptsExplanation = expectedAttemptsExplanation;
            this.withinAttemptsDecimal = withinAttemptsDecimal;
            this.withinAttemptsPercent = withinAttemptsPercent;
            this.withinAttemptsMeaning = withinAttemptsMeaning;
            this.moreThanAttemptsDecimal = moreThanAttemptsDecimal;
            this.moreThanAttemptsPercent = moreThanAttemptsPercent;
            this.moreThanAttemptsMeaning = moreThanAttemptsMeaning;
            this.mostLikelyAttempt = mostLikelyAttempt;
            this.firstAttemptDecimal = firstAttemptDecimal;
            this.firstAttemptPercent = firstAttemptPercent;
        }

        public GeometricCards(
                String successProbabilityDisplay,
                int maxAttempts,
                String expectedAttemptsValue,
                String withinAttemptsDecimal,
                String withinAttemptsPercent,
                String withinAttemptsMeaning,
                String moreThanAttemptsDecimal,
                String moreThanAttemptsPercent,
                String moreThanAttemptsMeaning,
                int mostLikelyAttempt,
                String firstAttemptDecimal,
                String firstAttemptPercent) {
            this(
                    successProbabilityDisplay,
                    maxAttempts,
                    expectedAttemptsValue,
                    parseOrRound(expectedAttemptsValue),
                    "Approximately " + parseOrRound(expectedAttemptsValue) + " attempts on average",
                    "Note: The rounded whole number is an approximate average over repeated transmissions, not a guaranteed attempt count.",
                    withinAttemptsDecimal,
                    withinAttemptsPercent,
                    withinAttemptsMeaning,
                    moreThanAttemptsDecimal,
                    moreThanAttemptsPercent,
                    moreThanAttemptsMeaning,
                    mostLikelyAttempt,
                    firstAttemptDecimal,
                    firstAttemptPercent
            );
        }

        private static long parseOrRound(String value) {
            if (value == null) {
                return 0L;
            }
            try {
                return Math.round(Double.parseDouble(value));
            } catch (Exception e) {
                return 0L;
            }
        }

        public String getSuccessProbabilityDisplay() {
            return successProbabilityDisplay;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public String getExpectedAttemptsValue() {
            return expectedAttemptsValue;
        }

        public long getExpectedAttemptsRounded() {
            return expectedAttemptsRounded;
        }

        public String getExpectedAttemptsRoundedText() {
            return expectedAttemptsRoundedText;
        }

        public String getExpectedAttemptsExplanation() {
            return expectedAttemptsExplanation;
        }

        public String getWithinAttemptsDecimal() {
            return withinAttemptsDecimal;
        }

        public String getWithinAttemptsPercent() {
            return withinAttemptsPercent;
        }

        public String getWithinAttemptsMeaning() {
            return withinAttemptsMeaning;
        }

        public String getMoreThanAttemptsDecimal() {
            return moreThanAttemptsDecimal;
        }

        public String getMoreThanAttemptsPercent() {
            return moreThanAttemptsPercent;
        }

        public String getMoreThanAttemptsMeaning() {
            return moreThanAttemptsMeaning;
        }

        public int getMostLikelyAttempt() {
            return mostLikelyAttempt;
        }

        public String getFirstAttemptDecimal() {
            return firstAttemptDecimal;
        }

        public String getFirstAttemptPercent() {
            return firstAttemptPercent;
        }
    }

    /**
     * Formatted row entry for the dynamic Geometric per-attempt table (1..n).
     */
    public static class AttemptTableRow {

        private final int attempt;
        private final String probabilityDecimal;
        private final String probabilityPercent;
        private final String cumulativeDecimal;
        private final String cumulativePercent;

        public AttemptTableRow(int attempt, String probabilityDecimal, String probabilityPercent) {
            this(attempt, probabilityDecimal, probabilityPercent, "", "");
        }

        public AttemptTableRow(
                int attempt,
                String probabilityDecimal,
                String probabilityPercent,
                String cumulativeDecimal,
                String cumulativePercent) {
            this.attempt = attempt;
            this.probabilityDecimal = probabilityDecimal;
            this.probabilityPercent = probabilityPercent;
            this.cumulativeDecimal = cumulativeDecimal;
            this.cumulativePercent = cumulativePercent;
        }

        public int getAttempt() {
            return attempt;
        }

        public String getProbabilityDecimal() {
            return probabilityDecimal;
        }

        public String getProbabilityPercent() {
            return probabilityPercent;
        }

        public String getCumulativeDecimal() {
            return cumulativeDecimal;
        }

        public String getCumulativePercent() {
            return cumulativePercent;
        }
    }

    /**
     * Summary values and mathematical clarification for Section 7
     * (Overall Communication Reliability).
     */
    public static class ReliabilitySummary {

        private final String expectedDropsValue;
        private final long dropCount;
        private final String exactDropDecimal;
        private final String exactDropPercent;
        private final String expectedAttemptsValue;
        private final long expectedAttemptsRounded;
        private final String expectedAttemptsRoundedText;
        private final String expectedAttemptsExplanation;
        private final int maxAttempts;
        private final String reliabilityDecimal;
        private final String reliabilityPercent;
        private final String moreThanAttemptsDecimal;
        private final String moreThanAttemptsPercent;
        private final String statusBadge;
        private final String overallInterpretation;
        private final String mathematicalClarification;
        private final int targetAttempts90;
        private final int targetAttempts95;
        private final int targetAttempts99;
        private final String targetReliability90Percent;
        private final String targetReliability95Percent;
        private final String targetReliability99Percent;

        public ReliabilitySummary(
                String expectedDropsValue,
                long dropCount,
                String exactDropDecimal,
                String exactDropPercent,
                String expectedAttemptsValue,
                long expectedAttemptsRounded,
                String expectedAttemptsRoundedText,
                String expectedAttemptsExplanation,
                int maxAttempts,
                String reliabilityDecimal,
                String reliabilityPercent,
                String moreThanAttemptsDecimal,
                String moreThanAttemptsPercent,
                String statusBadge,
                String overallInterpretation,
                String mathematicalClarification,
                int targetAttempts90,
                int targetAttempts95,
                int targetAttempts99,
                String targetReliability90Percent,
                String targetReliability95Percent,
                String targetReliability99Percent) {
            this.expectedDropsValue = expectedDropsValue;
            this.dropCount = dropCount;
            this.exactDropDecimal = exactDropDecimal;
            this.exactDropPercent = exactDropPercent;
            this.expectedAttemptsValue = expectedAttemptsValue;
            this.expectedAttemptsRounded = expectedAttemptsRounded;
            this.expectedAttemptsRoundedText = expectedAttemptsRoundedText;
            this.expectedAttemptsExplanation = expectedAttemptsExplanation;
            this.maxAttempts = maxAttempts;
            this.reliabilityDecimal = reliabilityDecimal;
            this.reliabilityPercent = reliabilityPercent;
            this.moreThanAttemptsDecimal = moreThanAttemptsDecimal;
            this.moreThanAttemptsPercent = moreThanAttemptsPercent;
            this.statusBadge = statusBadge;
            this.overallInterpretation = overallInterpretation;
            this.mathematicalClarification = mathematicalClarification;
            this.targetAttempts90 = targetAttempts90;
            this.targetAttempts95 = targetAttempts95;
            this.targetAttempts99 = targetAttempts99;
            this.targetReliability90Percent = targetReliability90Percent;
            this.targetReliability95Percent = targetReliability95Percent;
            this.targetReliability99Percent = targetReliability99Percent;
        }

        public ReliabilitySummary(
                String expectedDropsValue,
                long dropCount,
                String exactDropDecimal,
                String exactDropPercent,
                String expectedAttemptsValue,
                long expectedAttemptsRounded,
                String expectedAttemptsRoundedText,
                String expectedAttemptsExplanation,
                int maxAttempts,
                String reliabilityDecimal,
                String reliabilityPercent,
                String moreThanAttemptsDecimal,
                String moreThanAttemptsPercent,
                String statusBadge,
                String overallInterpretation,
                String mathematicalClarification) {
            this(
                    expectedDropsValue,
                    dropCount,
                    exactDropDecimal,
                    exactDropPercent,
                    expectedAttemptsValue,
                    expectedAttemptsRounded,
                    expectedAttemptsRoundedText,
                    expectedAttemptsExplanation,
                    maxAttempts,
                    reliabilityDecimal,
                    reliabilityPercent,
                    moreThanAttemptsDecimal,
                    moreThanAttemptsPercent,
                    statusBadge,
                    overallInterpretation,
                    mathematicalClarification,
                    0,
                    0,
                    0,
                    "",
                    "",
                    ""
            );
        }

        public ReliabilitySummary(
                String expectedDropsValue,
                long dropCount,
                String exactDropDecimal,
                String exactDropPercent,
                String expectedAttemptsValue,
                int maxAttempts,
                String reliabilityDecimal,
                String reliabilityPercent,
                String moreThanAttemptsDecimal,
                String moreThanAttemptsPercent,
                String statusBadge,
                String overallInterpretation,
                String mathematicalClarification) {
            this(
                    expectedDropsValue,
                    dropCount,
                    exactDropDecimal,
                    exactDropPercent,
                    expectedAttemptsValue,
                    parseOrRound(expectedAttemptsValue),
                    "Approximately " + parseOrRound(expectedAttemptsValue) + " attempts on average",
                    "Note: The rounded whole number is an approximate average over repeated transmissions, not a guaranteed attempt count.",
                    maxAttempts,
                    reliabilityDecimal,
                    reliabilityPercent,
                    moreThanAttemptsDecimal,
                    moreThanAttemptsPercent,
                    statusBadge,
                    overallInterpretation,
                    mathematicalClarification
            );
        }

        private static long parseOrRound(String value) {
            if (value == null) {
                return 0L;
            }
            try {
                return Math.round(Double.parseDouble(value));
            } catch (Exception e) {
                return 0L;
            }
        }

        public String getExpectedDropsValue() {
            return expectedDropsValue;
        }

        public long getDropCount() {
            return dropCount;
        }

        public String getExactDropDecimal() {
            return exactDropDecimal;
        }

        public String getExactDropPercent() {
            return exactDropPercent;
        }

        public String getExpectedAttemptsValue() {
            return expectedAttemptsValue;
        }

        public long getExpectedAttemptsRounded() {
            return expectedAttemptsRounded;
        }

        public String getExpectedAttemptsRoundedText() {
            return expectedAttemptsRoundedText;
        }

        public String getExpectedAttemptsExplanation() {
            return expectedAttemptsExplanation;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public String getReliabilityDecimal() {
            return reliabilityDecimal;
        }

        public String getReliabilityPercent() {
            return reliabilityPercent;
        }

        public String getMoreThanAttemptsDecimal() {
            return moreThanAttemptsDecimal;
        }

        public String getMoreThanAttemptsPercent() {
            return moreThanAttemptsPercent;
        }

        public String getStatusBadge() {
            return statusBadge;
        }

        public String getOverallInterpretation() {
            return overallInterpretation;
        }

        public String getMathematicalClarification() {
            return mathematicalClarification;
        }

        public int getTargetAttempts90() {
            return targetAttempts90;
        }

        public int getTargetAttempts95() {
            return targetAttempts95;
        }

        public int getTargetAttempts99() {
            return targetAttempts99;
        }

        public String getTargetReliability90Percent() {
            return targetReliability90Percent;
        }

        public String getTargetReliability95Percent() {
            return targetReliability95Percent;
        }

        public String getTargetReliability99Percent() {
            return targetReliability99Percent;
        }
    }

    /**
     * Structured Chart.js datasets derived strictly from {@code PoissonService}
     * and {@code GeometricService} for the Stage 7 Results Dashboard graphs.
     */
    public static class DashboardCharts {

        private final AnalysisType analysisType;

        private final String lambdaDisplay;
        private final long selectedDropCount;
        private final List<PoissonChartPoint> poissonSeries;

        private final String successProbabilityDisplay;
        private final int selectedMaxAttempts;
        private final int mostLikelyAttempt;
        private final List<GeometricChartPoint> geometricSeries;

        private final List<ReliabilityChartPoint> reliabilitySeries;

        public DashboardCharts(
                String lambdaDisplay,
                long selectedDropCount,
                List<PoissonChartPoint> poissonSeries,
                String successProbabilityDisplay,
                int selectedMaxAttempts,
                int mostLikelyAttempt,
                List<GeometricChartPoint> geometricSeries,
                List<ReliabilityChartPoint> reliabilitySeries) {
            this(
                    AnalysisType.COMBINED,
                    lambdaDisplay,
                    selectedDropCount,
                    poissonSeries,
                    successProbabilityDisplay,
                    selectedMaxAttempts,
                    mostLikelyAttempt,
                    geometricSeries,
                    reliabilitySeries
            );
        }

        public DashboardCharts(
                AnalysisType analysisType,
                String lambdaDisplay,
                long selectedDropCount,
                List<PoissonChartPoint> poissonSeries,
                String successProbabilityDisplay,
                int selectedMaxAttempts,
                int mostLikelyAttempt,
                List<GeometricChartPoint> geometricSeries,
                List<ReliabilityChartPoint> reliabilitySeries) {
            this.analysisType = (analysisType != null) ? analysisType : AnalysisType.COMBINED;
            this.lambdaDisplay = lambdaDisplay;
            this.selectedDropCount = selectedDropCount;
            this.poissonSeries = (poissonSeries != null)
                    ? Collections.unmodifiableList(poissonSeries)
                    : Collections.emptyList();
            this.successProbabilityDisplay = successProbabilityDisplay;
            this.selectedMaxAttempts = selectedMaxAttempts;
            this.mostLikelyAttempt = mostLikelyAttempt;
            this.geometricSeries = (geometricSeries != null)
                    ? Collections.unmodifiableList(geometricSeries)
                    : Collections.emptyList();
            this.reliabilitySeries = (reliabilitySeries != null)
                    ? Collections.unmodifiableList(reliabilitySeries)
                    : Collections.emptyList();
        }

        public AnalysisType getAnalysisType() {
            return analysisType;
        }

        public String getLambdaDisplay() {
            return lambdaDisplay;
        }

        public long getSelectedDropCount() {
            return selectedDropCount;
        }

        public List<PoissonChartPoint> getPoissonSeries() {
            return poissonSeries;
        }

        public String getSuccessProbabilityDisplay() {
            return successProbabilityDisplay;
        }

        public int getSelectedMaxAttempts() {
            return selectedMaxAttempts;
        }

        public int getMostLikelyAttempt() {
            return mostLikelyAttempt;
        }

        public List<GeometricChartPoint> getGeometricSeries() {
            return geometricSeries;
        }

        public List<ReliabilityChartPoint> getReliabilitySeries() {
            return reliabilitySeries;
        }
    }

    /**
     * Single bar point for the Poisson distribution chart ({@code k} vs {@code P(X = k)}).
     */
    public static class PoissonChartPoint {

        private final long dropCount;
        private final double probability;
        private final String probabilityDecimal;
        private final String probabilityPercent;
        private final boolean selected;

        public PoissonChartPoint(
                long dropCount,
                double probability,
                String probabilityDecimal,
                String probabilityPercent,
                boolean selected) {
            this.dropCount = dropCount;
            this.probability = probability;
            this.probabilityDecimal = probabilityDecimal;
            this.probabilityPercent = probabilityPercent;
            this.selected = selected;
        }

        public long getDropCount() {
            return dropCount;
        }

        public double getProbability() {
            return probability;
        }

        public String getProbabilityDecimal() {
            return probabilityDecimal;
        }

        public String getProbabilityPercent() {
            return probabilityPercent;
        }

        public boolean isSelected() {
            return selected;
        }
    }

    /**
     * Single bar point for the Geometric distribution chart (attempt {@code k} vs {@code P(X = k)}).
     */
    public static class GeometricChartPoint {

        private final int attempt;
        private final double probability;
        private final String probabilityDecimal;
        private final String probabilityPercent;
        private final boolean mostLikely;

        public GeometricChartPoint(
                int attempt,
                double probability,
                String probabilityDecimal,
                String probabilityPercent,
                boolean mostLikely) {
            this.attempt = attempt;
            this.probability = probability;
            this.probabilityDecimal = probabilityDecimal;
            this.probabilityPercent = probabilityPercent;
            this.mostLikely = mostLikely;
        }

        public int getAttempt() {
            return attempt;
        }

        public double getProbability() {
            return probability;
        }

        public String getProbabilityDecimal() {
            return probabilityDecimal;
        }

        public String getProbabilityPercent() {
            return probabilityPercent;
        }

        public boolean isMostLikely() {
            return mostLikely;
        }
    }

    /**
     * Single line point for the cumulative communication reliability chart
     * ({@code R(t) = 1 - (1 - p)^t}).
     */
    public static class ReliabilityChartPoint {

        private final int attempt;
        private final double cumulativeProbability;
        private final String probabilityDecimal;
        private final String probabilityPercent;
        private final boolean selectedMaxAttempt;

        public ReliabilityChartPoint(
                int attempt,
                double cumulativeProbability,
                String probabilityDecimal,
                String probabilityPercent,
                boolean selectedMaxAttempt) {
            this.attempt = attempt;
            this.cumulativeProbability = cumulativeProbability;
            this.probabilityDecimal = probabilityDecimal;
            this.probabilityPercent = probabilityPercent;
            this.selectedMaxAttempt = selectedMaxAttempt;
        }

        public int getAttempt() {
            return attempt;
        }

        public double getCumulativeProbability() {
            return cumulativeProbability;
        }

        public String getProbabilityDecimal() {
            return probabilityDecimal;
        }

        public String getProbabilityPercent() {
            return probabilityPercent;
        }

        public boolean isSelectedMaxAttempt() {
            return selectedMaxAttempt;
        }
    }

    /**
     * Practical plain-English conclusion for the active analysis mode.
     * Explains the results without confusing terminology, while accurately distinguishing
     * the evaluated drop count k from an observed outcome and the expected attempts
     * from a guaranteed attempt count.
     */
    public static class PracticalConclusion {

        private final AnalysisType analysisType;
        private final String headline;
        private final String narrative;
        private final String technicalNote;

        public PracticalConclusion(
                AnalysisType analysisType,
                String headline,
                String narrative,
                String technicalNote) {
            this.analysisType = (analysisType != null) ? analysisType : AnalysisType.COMBINED;
            this.headline = headline;
            this.narrative = narrative;
            this.technicalNote = technicalNote;
        }

        public AnalysisType getAnalysisType() {
            return analysisType;
        }

        public String getHeadline() {
            return headline;
        }

        public String getNarrative() {
            return narrative;
        }

        public String getTechnicalNote() {
            return technicalNote;
        }
    }
}

