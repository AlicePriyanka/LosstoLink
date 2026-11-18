package com.deepspace.analytics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Shared formatting and grammar helpers used consistently across the Results Dashboard
 * and PDF Report Generator so all numeric displays remain
 * finite, readable, and identical across views.
 */
public final class AnalysisFormattingUtils {

    private static final double MIN_FIXED_PROBABILITY = 1e-7;
    private static final double MIN_FIXED_PERCENT = 1e-4;
    private static final double MAX_PLAIN_DISPLAY = 1e9;

    private AnalysisFormattingUtils() {
    }

    public static String formatProbabilityDecimal(double value) {
        if (!Double.isFinite(value)) {
            return "0.0000000";
        }
        if (value == 0.0) {
            return "0.0000000";
        }
        if (value > 0.0 && value < MIN_FIXED_PROBABILITY) {
            return String.format(Locale.US, "%.4e", value);
        }
        return String.format(Locale.US, "%.7f", value);
    }

    public static String formatProbabilityPercent(double probability) {
        if (!Double.isFinite(probability)) {
            return "0.0000%";
        }
        double percent = probability * 100.0;
        if (percent == 0.0) {
            return "0.0000%";
        }
        if (percent > 0.0 && percent < MIN_FIXED_PERCENT) {
            return String.format(Locale.US, "%.4e%%", percent);
        }
        return String.format(Locale.US, "%.4f%%", percent);
    }

    public static String formatCompact(double value, int maxDecimals) {
        if (!Double.isFinite(value)) {
            return "0";
        }
        if (value == 0.0) {
            return "0";
        }
        double abs = Math.abs(value);
        if (abs < Math.pow(10, -maxDecimals) || abs >= MAX_PLAIN_DISPLAY) {
            return String.format(Locale.US, "%.4e", value);
        }
        BigDecimal rounded = BigDecimal.valueOf(value)
                .setScale(maxDecimals, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        if (rounded.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }
        return rounded.toPlainString();
    }

    public static String formatCompactPercent(double probability) {
        if (!Double.isFinite(probability)) {
            return "0%";
        }
        double percent = probability * 100.0;
        if (percent == 0.0) {
            return "0%";
        }
        if (percent > 0.0 && percent < 0.0001) {
            return String.format(Locale.US, "%.3e%%", percent);
        }
        BigDecimal exactUpTo4 = BigDecimal.valueOf(percent)
                .setScale(4, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return exactUpTo4.toPlainString() + "%";
    }

    public static String trimOrFallback(String raw, String fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        return raw.trim();
    }

    public static String pluralize(long count, String singular, String plural) {
        return count == 1L ? singular : plural;
    }

    /**
     * Formats expected attempts as an exact decimal string (up to 7 decimal places).
     * The value is not rounded before formatting.
     */
    public static String formatExpectedAttemptsDecimal(double expectedAttempts) {
        if (!Double.isFinite(expectedAttempts)) {
            return "0.0000000";
        }
        return formatCompact(expectedAttempts, 7);
    }

    /**
     * Calculates the nearest whole number for expected attempts using standard nearest-integer rounding.
     */
    public static long calculateRoundedExpectedAttempts(double expectedAttempts) {
        if (!Double.isFinite(expectedAttempts) || expectedAttempts <= 0.0) {
            return 0L;
        }
        if (expectedAttempts >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.round(expectedAttempts);
    }

    /**
     * Exposes a practical whole-number interpretation for expected attempts.
     * Example for p = 0.6: "Approximately 2 attempts on average".
     */
    public static String formatExpectedAttemptsRoundedText(double expectedAttempts) {
        if (!Double.isFinite(expectedAttempts)) {
            return "0 attempts on average";
        }
        long rounded = calculateRoundedExpectedAttempts(expectedAttempts);
        String attemptWord = pluralize(rounded, "attempt", "attempts");
        if (Math.abs(expectedAttempts - rounded) < 1e-6) {
            return String.format(Locale.US, "%d %s on average", rounded, attemptWord);
        }
        return String.format(Locale.US, "Approximately %d %s on average", rounded, attemptWord);
    }

    /**
     * Clear explanation that the rounded value is an approximate average, not a guaranteed attempt.
     */
    public static String formatExpectedAttemptsExplanation() {
        return "Note: The rounded whole number is an approximate average over repeated transmissions, not a guaranteed attempt count.";
    }

    /**
     * Standardizes decimal probability and percentage pairing with a clear label to avoid unspaced concatenation.
     */
    public static String formatDecimalAndPercentLabeled(double probability) {
        return String.format(Locale.US, "Probability: %s | Percentage: %s",
                formatProbabilityDecimal(probability),
                formatProbabilityPercent(probability));
    }
}

