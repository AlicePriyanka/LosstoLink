package com.deepspace.analytics.model;

/**
 * Supported analysis execution modes for DeepSpace Analytics.
 *
 * <ul>
 *     <li>{@link #POISSON_ONLY}: evaluates transmission drop counts using {@code λ} and {@code k}.</li>
 *     <li>{@link #GEOMETRIC_ONLY}: evaluates first successful transmission and cumulative reliability {@code R(n)} using {@code p} and {@code n}.</li>
 *     <li>{@link #COMBINED}: evaluates both Poisson and Geometric models independently from all four parameters.</li>
 * </ul>
 */
public enum AnalysisType {

    POISSON_ONLY("Poisson Distribution Only"),
    GEOMETRIC_ONLY("Geometric Distribution Only"),
    COMBINED("Poisson & Geometric Combined");

    private final String displayName;

    AnalysisType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean includesPoisson() {
        return this == POISSON_ONLY || this == COMBINED;
    }

    public boolean includesGeometric() {
        return this == GEOMETRIC_ONLY || this == COMBINED;
    }

    public boolean includesReliability() {
        return includesGeometric();
    }

    public boolean isCombined() {
        return this == COMBINED;
    }
}

