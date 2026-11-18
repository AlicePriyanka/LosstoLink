package com.deepspace.analytics.service;

import com.deepspace.analytics.model.CommunicationAnalysisInput;
import com.deepspace.analytics.model.GeometricResult;
import com.deepspace.analytics.model.GeometricResult.AttemptProbability;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Calculation engine for the Geometric Distribution model used to identify
 * the attempt on which the first successful deep-space transmission occurs.
 *
 * <p>Uses high-precision {@link BigDecimal} arithmetic internally before
 * bounding probabilities to {@code [0, 1]} so decimal probabilities and
 * cumulative terms remain accurate without floating-point drift.
 */
@Service
public class GeometricService {

    public static final int MAX_ATTEMPT_SERIES = 1_000;
    public static final BigDecimal MIN_PROBABILITY = new BigDecimal("0.000000000001");

    private static final Pattern NUMBER_PATTERN = Pattern.compile("^[+-]?(?:\\d+\\.?\\d*|\\.\\d+)$");
    private static final Pattern INTEGER_PATTERN = Pattern.compile("^[+-]?\\d+$");
    private static final MathContext MC = MathContext.DECIMAL128;

    /**
     * Parses and validates raw string parameters from the unified input form,
     * then computes the structured Geometric distribution metrics.
     *
     * @param input validated form input containing {@code successProbability} and {@code maxAttempts}
     * @return structured {@link GeometricResult}
     */
    public GeometricResult calculate(CommunicationAnalysisInput input) {
        if (input == null) {
            throw new IllegalArgumentException("Input parameters must not be null.");
        }
        BigDecimal pDecimal = parseSuccessProbability(input.getSuccessProbability());
        int n = parseMaxAttempts(input.getMaxAttempts());
        return calculateInternal(pDecimal, n);
    }

    /**
     * Computes Geometric probabilities, expected attempt count, cumulative
     * bounds, and per-attempt probability series from {@code 1} to {@code n}.
     *
     * @param p probability of successful transmission in one attempt ({@code 0 < p <= 1})
     * @param n maximum number of attempts to consider ({@code n >= 1})
     * @return structured {@link GeometricResult}
     */
    public GeometricResult calculate(double p, int n) {
        validateProbability(p);
        validateAttemptNumber(n, "Maximum Number of Attempts (n)");
        return calculateInternal(BigDecimal.valueOf(p), n);
    }

    /**
     * Computes the probability that the first successful transmission occurs on
     * exact attempt {@code k}: {@code P(X = k) = (1 - p)^(k - 1) * p}.
     *
     * @param p probability of successful transmission in one attempt ({@code 0 < p <= 1})
     * @param k positive integer attempt number ({@code k >= 1})
     * @return bounded probability in {@code [0, 1]}
     */
    public double calculateProbabilityForAttempt(double p, int k) {
        validateProbability(p);
        validateAttemptNumber(k, "Attempt number (k)");
        BigDecimal pDecimal = BigDecimal.valueOf(p);
        return probabilityOnAttempt(pDecimal, k);
    }

    /**
     * Computes cumulative reliability probabilities {@code R(t) = 1 - (1 - p)^t}
     * for {@code t = 1 .. maxAttemptsToPlot} using exact {@link BigDecimal}
     * arithmetic and the validated submitted input {@code p}.
     *
     * @param input             validated form input containing {@code successProbability}
     * @param maxAttemptsToPlot positive integer upper horizon ({@code >= 1})
     * @return ordered list of cumulative probabilities for attempts {@code 1 .. maxAttemptsToPlot}
     */
    public List<AttemptProbability> calculateCumulativeSeries(CommunicationAnalysisInput input, int maxAttemptsToPlot) {
        if (input == null) {
            throw new IllegalArgumentException("Input parameters must not be null.");
        }
        BigDecimal pDecimal = parseSuccessProbability(input.getSuccessProbability());
        return calculateCumulativeSeriesInternal(pDecimal, maxAttemptsToPlot);
    }

    /**
     * Computes cumulative reliability probabilities {@code R(t) = 1 - (1 - p)^t}
     * for {@code t = 1 .. maxAttemptsToPlot}.
     *
     * @param p                 probability of successful transmission in one attempt ({@code 0 < p <= 1})
     * @param maxAttemptsToPlot positive integer upper horizon ({@code >= 1})
     * @return ordered list of cumulative probabilities for attempts {@code 1 .. maxAttemptsToPlot}
     */
    public List<AttemptProbability> calculateCumulativeSeries(double p, int maxAttemptsToPlot) {
        validateProbability(p);
        return calculateCumulativeSeriesInternal(BigDecimal.valueOf(p), maxAttemptsToPlot);
    }

    private List<AttemptProbability> calculateCumulativeSeriesInternal(BigDecimal pDecimal, int maxAttemptsToPlot) {
        validateProbabilityDecimal(pDecimal);
        validateAttemptNumber(maxAttemptsToPlot, "Maximum Number of Attempts (n)");
        if (maxAttemptsToPlot > MAX_ATTEMPT_SERIES) {
            throw new IllegalArgumentException(
                    "Maximum Number of Attempts (n) must not exceed " + MAX_ATTEMPT_SERIES + ".");
        }

        List<AttemptProbability> series = new ArrayList<>(maxAttemptsToPlot);
        if (pDecimal.compareTo(BigDecimal.ONE) == 0) {
            for (int attempt = 1; attempt <= maxAttemptsToPlot; attempt++) {
                series.add(new AttemptProbability(attempt, 1.0));
            }
            return series;
        }

        BigDecimal qDecimal = BigDecimal.ONE.subtract(pDecimal, MC);
        BigDecimal qPower = qDecimal;
        for (int attempt = 1; attempt <= maxAttemptsToPlot; attempt++) {
            double cumulative = clampProbability(BigDecimal.ONE.subtract(qPower, MC).doubleValue());
            series.add(new AttemptProbability(attempt, cumulative));
            qPower = qPower.multiply(qDecimal, MC);
        }
        return series;
    }

    private GeometricResult calculateInternal(BigDecimal pDecimal, int n) {
        validateProbabilityDecimal(pDecimal);
        validateAttemptNumber(n, "Maximum Number of Attempts (n)");
        if (n > MAX_ATTEMPT_SERIES) {
            throw new IllegalArgumentException(
                    "Maximum Number of Attempts (n) must not exceed " + MAX_ATTEMPT_SERIES + ".");
        }

        double pDouble = pDecimal.doubleValue();
        double expectedAttempts = BigDecimal.ONE.divide(pDecimal, MC).doubleValue();
        if (!Double.isFinite(expectedAttempts)) {
            throw new IllegalStateException("Expected number of attempts overflowed finite numeric bounds.");
        }

        final double probabilityMoreThanN;
        final double probabilityWithinN;
        List<AttemptProbability> series = new ArrayList<>(n);

        if (pDecimal.compareTo(BigDecimal.ONE) == 0) {
            probabilityMoreThanN = 0.0;
            probabilityWithinN = 1.0;
            for (int attempt = 1; attempt <= n; attempt++) {
                double prob = (attempt == 1) ? 1.0 : 0.0;
                series.add(new AttemptProbability(attempt, prob));
            }
        } else {
            BigDecimal qDecimal = BigDecimal.ONE.subtract(pDecimal, MC);
            BigDecimal qPowN = qDecimal.pow(n, MC);
            probabilityMoreThanN = clampProbability(qPowN.doubleValue());
            probabilityWithinN = clampProbability(BigDecimal.ONE.subtract(qPowN, MC).doubleValue());

            BigDecimal currentTerm = pDecimal;
            for (int attempt = 1; attempt <= n; attempt++) {
                series.add(new AttemptProbability(attempt, clampProbability(currentTerm.doubleValue())));
                currentTerm = currentTerm.multiply(qDecimal, MC);
            }
        }

        // For 0 < p <= 1, P(X = k) is non-increasing in k, so attempt 1 is always most likely.
        int mostLikelyAttempt = 1;

        return new GeometricResult(
                pDouble,
                n,
                series,
                expectedAttempts,
                probabilityWithinN,
                probabilityMoreThanN,
                mostLikelyAttempt
        );
    }

    private double probabilityOnAttempt(BigDecimal pDecimal, int k) {
        if (pDecimal.compareTo(BigDecimal.ONE) == 0) {
            return (k == 1) ? 1.0 : 0.0;
        }
        BigDecimal qDecimal = BigDecimal.ONE.subtract(pDecimal, MC);
        BigDecimal result = qDecimal.pow(k - 1, MC).multiply(pDecimal, MC);
        return clampProbability(result.doubleValue());
    }

    private void validateProbability(double p) {
        if (!Double.isFinite(p) || p <= 0.0 || p > 1.0) {
            throw new IllegalArgumentException(
                    "Probability of Successful Transmission (p) must be greater than 0 and at most 1.");
        }
        if (p < MIN_PROBABILITY.doubleValue()) {
            throw new IllegalArgumentException(
                    "Probability of Successful Transmission (p) must be at least 0.000000000001.");
        }
    }

    private void validateProbabilityDecimal(BigDecimal pDecimal) {
        if (pDecimal == null
                || pDecimal.compareTo(BigDecimal.ZERO) <= 0
                || pDecimal.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "Probability of Successful Transmission (p) must be greater than 0 and at most 1.");
        }
        if (pDecimal.compareTo(MIN_PROBABILITY) < 0) {
            throw new IllegalArgumentException(
                    "Probability of Successful Transmission (p) must be at least 0.000000000001 to remain within supported numerical precision.");
        }
        double asDouble = pDecimal.doubleValue();
        if (!Double.isFinite(asDouble) || asDouble <= 0.0) {
            throw new IllegalArgumentException(
                    "Probability of Successful Transmission (p) is outside supported numerical precision.");
        }
    }

    private void validateAttemptNumber(int attempt, String label) {
        if (attempt < 1) {
            throw new IllegalArgumentException(label + " must be a positive integer greater than or equal to 1.");
        }
    }

    private BigDecimal parseSuccessProbability(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Probability of Successful Transmission (p) is required.");
        }
        String trimmed = raw.trim();
        if (!NUMBER_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                    "Probability of Successful Transmission (p) must be a valid numerical value.");
        }
        if (trimmed.length() > 128) {
            throw new IllegalArgumentException(
                    "Probability of Successful Transmission (p) is outside supported numerical precision.");
        }
        BigDecimal value = new BigDecimal(trimmed);
        validateProbabilityDecimal(value);
        return value;
    }

    private int parseMaxAttempts(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Maximum Number of Attempts (n) is required.");
        }
        String trimmed = raw.trim();
        if (!INTEGER_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                    "Maximum Number of Attempts (n) must be a valid whole number (integer).");
        }
        if (trimmed.length() > 128) {
            throw new IllegalArgumentException(
                    "Maximum Number of Attempts (n) must not exceed 1,000.");
        }
        try {
            int value = Integer.parseInt(trimmed);
            validateAttemptNumber(value, "Maximum Number of Attempts (n)");
            if (value > MAX_ATTEMPT_SERIES) {
                throw new IllegalArgumentException(
                        "Maximum Number of Attempts (n) must not exceed 1,000.");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Maximum Number of Attempts (n) exceeds supported integer calculation range.");
        }
    }

    private double clampProbability(double value) {
        if (Double.isNaN(value)) {
            throw new IllegalStateException("Calculated probability resulted in NaN.");
        }
        if (value <= 0.0) {
            return 0.0;
        }
        if (value >= 1.0) {
            return 1.0;
        }
        return value;
    }
}

