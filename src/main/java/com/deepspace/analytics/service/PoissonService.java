package com.deepspace.analytics.service;

import com.deepspace.analytics.model.CommunicationAnalysisInput;
import com.deepspace.analytics.model.PoissonResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Calculation engine for the Poisson Distribution model used to analyse
 * discrete transmission drops over a fixed deep-space communication interval.
 *
 * <p>Uses logarithmic factorial evaluation to avoid arithmetic overflow for
 * large values of {@code k} and handles {@code λ = 0} boundary cases cleanly.
 */
@Service
public class PoissonService {

    public static final double MAX_LAMBDA = 1_000_000_000.0;
    public static final long MAX_DROP_COUNT = 1_000_000_000L;

    private static final Pattern NUMBER_PATTERN = Pattern.compile("^[+-]?(?:\\d+\\.?\\d*|\\.\\d+)$");
    private static final Pattern INTEGER_PATTERN = Pattern.compile("^[+-]?\\d+$");
    private static final long EXACT_LOG_FACTORIAL_THRESHOLD = 10_000L;

    /**
     * Parses and validates raw string parameters from the unified input form,
     * then computes the structured Poisson metrics.
     *
     * @param input validated form input containing {@code lambda} and {@code dropCount}
     * @return structured {@link PoissonResult}
     */
    public PoissonResult calculate(CommunicationAnalysisInput input) {
        if (input == null) {
            throw new IllegalArgumentException("Input parameters must not be null.");
        }
        double lambda = parseLambda(input.getLambda());
        long k = parseDropCount(input.getDropCount());
        return calculate(lambda, k);
    }

    /**
     * Computes Poisson probabilities and summary moments for transmission drops.
     *
     * @param lambda average number of transmission drops ({@code λ >= 0})
     * @param k      target number of transmission drops ({@code k >= 0})
     * @return structured {@link PoissonResult} with bounded valid probabilities
     */
    public PoissonResult calculate(double lambda, long k) {
        validateInputs(lambda, k);

        final double probabilityExactK;
        final double probabilityZero;
        final double probabilityAtLeastOne;

        if (lambda == 0.0) {
            probabilityExactK = (k == 0L) ? 1.0 : 0.0;
            probabilityZero = 1.0;
            probabilityAtLeastOne = 0.0;
        } else {
            double logProbability = computeLogProbability(lambda, k);
            probabilityExactK = clampProbability(Math.exp(logProbability));
            probabilityZero = clampProbability(Math.exp(-lambda));
            probabilityAtLeastOne = clampProbability(-Math.expm1(-lambda));
        }

        double expectedDrops = lambda;
        double variance = lambda;

        return new PoissonResult(
                lambda,
                k,
                probabilityExactK,
                expectedDrops,
                probabilityZero,
                probabilityAtLeastOne,
                variance
        );
    }

    private void validateInputs(double lambda, long k) {
        if (!Double.isFinite(lambda) || lambda < 0.0 || lambda > MAX_LAMBDA) {
            throw new IllegalArgumentException(
                    "Average Transmission Drop Rate (λ) must be a finite number between 0 and 1,000,000,000.");
        }
        if (k < 0L || k > MAX_DROP_COUNT) {
            throw new IllegalArgumentException(
                    "Number of Transmission Drops (k) must be an integer between 0 and 1,000,000,000.");
        }
    }

    private double parseLambda(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Average Transmission Drop Rate (λ) is required.");
        }
        String trimmed = raw.trim();
        if (!NUMBER_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Average Transmission Drop Rate (λ) must be a valid numerical value.");
        }
        if (trimmed.length() > 128) {
            throw new IllegalArgumentException("Average Transmission Drop Rate (λ) must not exceed 1,000,000,000.");
        }
        double value = new BigDecimal(trimmed).doubleValue();
        if (!Double.isFinite(value) || value < 0.0 || value > MAX_LAMBDA) {
            throw new IllegalArgumentException(
                    "Average Transmission Drop Rate (λ) must be a finite number between 0 and 1,000,000,000.");
        }
        return value;
    }

    private long parseDropCount(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Number of Transmission Drops (k) is required.");
        }
        String trimmed = raw.trim();
        if (!INTEGER_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                    "Number of Transmission Drops (k) must be a valid whole number (integer).");
        }
        if (trimmed.length() > 128) {
            throw new IllegalArgumentException("Number of Transmission Drops (k) must not exceed 1,000,000,000.");
        }
        try {
            long value = Long.parseLong(trimmed);
            if (value < 0L || value > MAX_DROP_COUNT) {
                throw new IllegalArgumentException(
                        "Number of Transmission Drops (k) must be between 0 and 1,000,000,000.");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Number of Transmission Drops (k) exceeds supported integer calculation range.");
        }
    }

    /**
     * Evaluates {@code ln(P(X = k))} accurately without overflow or floating-point
     * cancellation for both small and large {@code (λ, k)} values.
     */
    private double computeLogProbability(double lambda, long k) {
        if (k <= EXACT_LOG_FACTORIAL_THRESHOLD) {
            return -lambda + (k * Math.log(lambda)) - logFactorial(k);
        }
        double n = (double) k;
        double invN = 1.0 / n;
        double invN2 = invN * invN;
        double stirlingRemainder = (0.5 * Math.log(2.0 * Math.PI * n))
                + (invN / 12.0)
                - (invN * invN2 / 360.0)
                + (invN * invN2 * invN2 / 1260.0);

        double ratioDiff = (lambda - n) / n;
        double devianceTerm;
        if (Math.abs(ratioDiff) < 1e-4) {
            // Exact series for n * (ln(1 + r) - r) avoids subtracting nearly equal large terms
            devianceTerm = -n * ratioDiff * ratioDiff * (0.5 - ratioDiff * (1.0 / 3.0
                    - ratioDiff * (0.25 - ratioDiff * (0.2 - ratioDiff / 6.0))));
        } else {
            devianceTerm = (n - lambda) + (n * Math.log(lambda / n));
        }
        return devianceTerm - stirlingRemainder;
    }

    /**
     * Computes {@code ln(k!)} accurately without factorial overflow.
     * Uses direct log summation up to 10,000 and Stirling's asymptotic series
     * above that threshold.
     */
    private double logFactorial(long k) {
        if (k <= 1L) {
            return 0.0;
        }
        if (k <= EXACT_LOG_FACTORIAL_THRESHOLD) {
            double sum = 0.0;
            for (long i = 2L; i <= k; i++) {
                sum += Math.log((double) i);
            }
            return sum;
        }
        double n = (double) k;
        double invN = 1.0 / n;
        double invN2 = invN * invN;
        return (n * Math.log(n))
                - n
                + (0.5 * Math.log(2.0 * Math.PI * n))
                + (invN / 12.0)
                - (invN * invN2 / 360.0)
                + (invN * invN2 * invN2 / 1260.0);
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

