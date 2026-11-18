package com.deepspace.analytics.model;

/**
 * Unified form backing object holding communication parameters for the
 * Poisson ({@code λ}, {@code k}) and Geometric ({@code p}, {@code n}) models,
 * together with the optional or resolved {@link AnalysisType}.
 *
 * <p>Numeric parameters are stored as raw strings so the form accepts and
 * preserves user input for validation.
 */
public class CommunicationAnalysisInput {

    private String lambda;
    private String dropCount;
    private String successProbability;
    private String maxAttempts;
    private AnalysisType analysisType;

    public String getLambda() {
        return lambda;
    }

    public void setLambda(String lambda) {
        this.lambda = lambda;
    }

    public String getDropCount() {
        return dropCount;
    }

    public void setDropCount(String dropCount) {
        this.dropCount = dropCount;
    }

    public String getSuccessProbability() {
        return successProbability;
    }

    public void setSuccessProbability(String successProbability) {
        this.successProbability = successProbability;
    }

    public String getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(String maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public AnalysisType getAnalysisType() {
        return analysisType;
    }

    public void setAnalysisType(AnalysisType analysisType) {
        this.analysisType = analysisType;
    }
}

