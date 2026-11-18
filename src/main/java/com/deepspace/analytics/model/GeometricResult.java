package com.deepspace.analytics.model;

import java.util.Collections;
import java.util.List;

/**
 * Structured immutable result produced by {@code GeometricService} for
 * identifying the first successful deep-space transmission attempt.
 *
 * <p>Prepared for integration into the unified Results Dashboard.
 */
public class GeometricResult {

    private final double successProbability;
    private final int maxAttempts;
    private final List<AttemptProbability> attemptProbabilities;
    private final double expectedAttempts;
    private final double probabilityWithinAttempts;
    private final double probabilityMoreThanAttempts;
    private final int mostLikelyAttempt;

    public GeometricResult(
            double successProbability,
            int maxAttempts,
            List<AttemptProbability> attemptProbabilities,
            double expectedAttempts,
            double probabilityWithinAttempts,
            double probabilityMoreThanAttempts,
            int mostLikelyAttempt) {
        this.successProbability = successProbability;
        this.maxAttempts = maxAttempts;
        this.attemptProbabilities = Collections.unmodifiableList(attemptProbabilities);
        this.expectedAttempts = expectedAttempts;
        this.probabilityWithinAttempts = probabilityWithinAttempts;
        this.probabilityMoreThanAttempts = probabilityMoreThanAttempts;
        this.mostLikelyAttempt = mostLikelyAttempt;
    }

    public double getSuccessProbability() {
        return successProbability;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public List<AttemptProbability> getAttemptProbabilities() {
        return attemptProbabilities;
    }

    public double getExpectedAttempts() {
        return expectedAttempts;
    }

    public double getProbabilityWithinAttempts() {
        return probabilityWithinAttempts;
    }

    public double getProbabilityMoreThanAttempts() {
        return probabilityMoreThanAttempts;
    }

    public int getMostLikelyAttempt() {
        return mostLikelyAttempt;
    }

    /**
     * Probability mass entry for a single transmission attempt {@code k}.
     */
    public static class AttemptProbability {

        private final int attempt;
        private final double probability;

        public AttemptProbability(int attempt, double probability) {
            this.attempt = attempt;
            this.probability = probability;
        }

        public int getAttempt() {
            return attempt;
        }

        public double getProbability() {
            return probability;
        }
    }
}

