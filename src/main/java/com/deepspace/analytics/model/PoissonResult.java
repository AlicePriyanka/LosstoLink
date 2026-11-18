package com.deepspace.analytics.model;

/**
 * Structured immutable result produced by {@code PoissonService} for
 * transmission-drop analysis over a fixed communication interval.
 *
 * <p>Prepared for later integration into the unified Results Dashboard.
 */
public class PoissonResult {

    private final double lambda;
    private final long dropCount;
    private final double probabilityOfExactDrops;
    private final double expectedDrops;
    private final double probabilityOfZeroDrops;
    private final double probabilityOfAtLeastOneDrop;
    private final double variance;

    public PoissonResult(
            double lambda,
            long dropCount,
            double probabilityOfExactDrops,
            double expectedDrops,
            double probabilityOfZeroDrops,
            double probabilityOfAtLeastOneDrop,
            double variance) {
        this.lambda = lambda;
        this.dropCount = dropCount;
        this.probabilityOfExactDrops = probabilityOfExactDrops;
        this.expectedDrops = expectedDrops;
        this.probabilityOfZeroDrops = probabilityOfZeroDrops;
        this.probabilityOfAtLeastOneDrop = probabilityOfAtLeastOneDrop;
        this.variance = variance;
    }

    public double getLambda() {
        return lambda;
    }

    public long getDropCount() {
        return dropCount;
    }

    public double getProbabilityOfExactDrops() {
        return probabilityOfExactDrops;
    }

    public double getExpectedDrops() {
        return expectedDrops;
    }

    public double getProbabilityOfZeroDrops() {
        return probabilityOfZeroDrops;
    }

    public double getProbabilityOfAtLeastOneDrop() {
        return probabilityOfAtLeastOneDrop;
    }

    public double getVariance() {
        return variance;
    }
}

