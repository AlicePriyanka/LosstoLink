package com.deepspace.analytics.model;

/**
 * Request payload for generating the Stage 9 downloadable mathematical PDF
 * report. Extends {@link CommunicationAnalysisInput} so the backend always
 * re-validates all four parameters and computes authoritative results via
 * {@code PoissonService} and {@code GeometricService}, while optionally
 * accepting Chart.js PNG snapshots rendered for an academic white theme.
 */
public class PdfReportRequest extends CommunicationAnalysisInput {

    private String poissonChartImage;
    private String geometricChartImage;
    private String reliabilityChartImage;

    public String getPoissonChartImage() {
        return poissonChartImage;
    }

    public void setPoissonChartImage(String poissonChartImage) {
        this.poissonChartImage = poissonChartImage;
    }

    public String getGeometricChartImage() {
        return geometricChartImage;
    }

    public void setGeometricChartImage(String geometricChartImage) {
        this.geometricChartImage = geometricChartImage;
    }

    public String getReliabilityChartImage() {
        return reliabilityChartImage;
    }

    public void setReliabilityChartImage(String reliabilityChartImage) {
        this.reliabilityChartImage = reliabilityChartImage;
    }
}

