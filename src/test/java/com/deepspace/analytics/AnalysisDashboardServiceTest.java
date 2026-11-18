package com.deepspace.analytics;

import com.deepspace.analytics.model.AnalysisDashboardView;
import com.deepspace.analytics.model.CommunicationAnalysisInput;
import com.deepspace.analytics.model.GeometricResult;
import com.deepspace.analytics.model.PoissonResult;
import com.deepspace.analytics.service.AnalysisDashboardService;
import com.deepspace.analytics.service.GeometricService;
import com.deepspace.analytics.service.PoissonService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisDashboardServiceTest {

    private final PoissonService poissonService = new PoissonService();
    private final GeometricService geometricService = new GeometricService();
    private final AnalysisDashboardService dashboardService =
            new AnalysisDashboardService(poissonService, geometricService);

    @Test
    void buildsAccurateDashboardForExampleInputs() {
        CommunicationAnalysisInput input = buildInput("3", "2", "0.6", "5");
        PoissonResult poisson = poissonService.calculate(input);
        GeometricResult geometric = geometricService.calculate(input);

        AnalysisDashboardView view = dashboardService.buildDashboardView(input, poisson, geometric);

        assertEquals("3", view.getSubmittedLambda());
        assertEquals("2", view.getSubmittedDropCount());
        assertEquals("0.6", view.getSubmittedSuccessProbability());
        assertEquals("5", view.getSubmittedMaxAttempts());

        assertEquals("0.2240418", view.getPoisson().getExactDropDecimal());
        assertEquals("22.4042%", view.getPoisson().getExactDropPercent());
        assertEquals("3", view.getPoisson().getExpectedDropsValue());
        assertEquals("0.0497871", view.getPoisson().getProbabilityZeroDecimal());
        assertEquals("0.9502129", view.getPoisson().getProbabilityAtLeastOneDecimal());
        assertEquals("3", view.getPoisson().getVarianceValue());

        assertEquals("1.6666667", view.getGeometric().getExpectedAttemptsValue());
        assertEquals("0.9897600", view.getGeometric().getWithinAttemptsDecimal());
        assertEquals("98.9760%", view.getGeometric().getWithinAttemptsPercent());
        assertEquals("0.0102400", view.getGeometric().getMoreThanAttemptsDecimal());
        assertEquals("1.0240%", view.getGeometric().getMoreThanAttemptsPercent());
        assertEquals(1, view.getGeometric().getMostLikelyAttempt());

        assertEquals(5, view.getAttemptRows().size());
        assertEquals("0.6000000", view.getAttemptRows().get(0).getProbabilityDecimal());
        assertEquals("60.0000%", view.getAttemptRows().get(0).getProbabilityPercent());
        assertEquals("0.0153600", view.getAttemptRows().get(4).getProbabilityDecimal());
        assertEquals("1.5360%", view.getAttemptRows().get(4).getProbabilityPercent());

        assertEquals("3", view.getReliability().getExpectedDropsValue());
        assertEquals(2L, view.getReliability().getDropCount());
        assertEquals("0.2240418", view.getReliability().getExactDropDecimal());
        assertEquals("22.4042%", view.getReliability().getExactDropPercent());
        assertEquals("1.6666667", view.getReliability().getExpectedAttemptsValue());
        assertEquals("0.9897600", view.getReliability().getReliabilityDecimal());
        assertEquals("98.9760%", view.getReliability().getReliabilityPercent());
        assertEquals("0.0102400", view.getReliability().getMoreThanAttemptsDecimal());
        assertEquals("1.0240%", view.getReliability().getMoreThanAttemptsPercent());
        assertTrue(view.getReliability().getOverallInterpretation().contains(
                "expected number of transmission drops is 3"));
        assertTrue(view.getReliability().getOverallInterpretation().contains("1.6666667"));
        assertTrue(view.getReliability().getOverallInterpretation().toLowerCase().contains("approximately 2 attempts on average"));
        assertTrue(view.getReliability().getOverallInterpretation().contains("98.9760%"));
        assertTrue(view.getReliability().getOverallInterpretation().contains("1.0240%"));
        assertTrue(view.getReliability().getMathematicalClarification().contains("never multiplied together"));
        assertTrue(view.getInterpretations().stream().anyMatch(s -> s.contains("22.40%")));
        assertTrue(view.getInterpretations().stream().anyMatch(s -> s.contains("98.976%")));

        // Verify Stage 7 real Java chart datasets
        var charts = view.getCharts();
        assertEquals(2L, charts.getSelectedDropCount());
        assertEquals(1, charts.getMostLikelyAttempt());
        assertEquals(5, charts.getSelectedMaxAttempts());

        assertTrue(charts.getPoissonSeries().size() >= 11);
        var selectedPoissonBar = charts.getPoissonSeries().stream()
                .filter(pt -> pt.getDropCount() == 2L)
                .findFirst()
                .orElseThrow();
        assertEquals(0.2240418, selectedPoissonBar.getProbability(), 1e-7);
        assertTrue(selectedPoissonBar.isSelected());

        assertEquals(5, charts.getGeometricSeries().size());
        assertEquals(0.6, charts.getGeometricSeries().get(0).getProbability(), 1e-12);
        assertTrue(charts.getGeometricSeries().get(0).isMostLikely());
        assertEquals(0.24, charts.getGeometricSeries().get(1).getProbability(), 1e-12);
        assertEquals(0.096, charts.getGeometricSeries().get(2).getProbability(), 1e-12);
        assertEquals(0.0384, charts.getGeometricSeries().get(3).getProbability(), 1e-12);
        assertEquals(0.01536, charts.getGeometricSeries().get(4).getProbability(), 1e-12);

        assertEquals(5, charts.getReliabilitySeries().size());
        assertEquals(0.6, charts.getReliabilitySeries().get(0).getCumulativeProbability(), 1e-12);
        assertEquals(0.84, charts.getReliabilitySeries().get(1).getCumulativeProbability(), 1e-12);
        assertEquals(0.936, charts.getReliabilitySeries().get(2).getCumulativeProbability(), 1e-12);
        assertEquals(0.9744, charts.getReliabilitySeries().get(3).getCumulativeProbability(), 1e-12);
        assertEquals(0.98976, charts.getReliabilitySeries().get(4).getCumulativeProbability(), 1e-12);
        assertTrue(charts.getReliabilitySeries().get(4).isSelectedMaxAttempt());
    }

    @Test
    void singleMaxAttemptExtendsReliabilityLineCurveAndMarksSelectedN() {
        CommunicationAnalysisInput input = buildInput("0", "0", "0.5", "1");
        AnalysisDashboardView view = dashboardService.buildDashboardView(
                input, poissonService.calculate(input), geometricService.calculate(input));

        assertEquals(1, view.getCharts().getGeometricSeries().size());
        assertTrue(view.getCharts().getReliabilitySeries().size() >= 5);
        assertTrue(view.getCharts().getReliabilitySeries().get(0).isSelectedMaxAttempt());
        assertFalse(view.getCharts().getReliabilitySeries().get(1).isSelectedMaxAttempt());
    }

    @Test
    void buildsDynamicInterpretationsForDifferentInputs() {
        CommunicationAnalysisInput inputA = buildInput("3", "2", "0.6", "5");
        CommunicationAnalysisInput inputB = buildInput("0.5", "1", "0.4", "3");

        AnalysisDashboardView viewA = dashboardService.buildDashboardView(
                inputA, poissonService.calculate(inputA), geometricService.calculate(inputA));
        AnalysisDashboardView viewB = dashboardService.buildDashboardView(
                inputB, poissonService.calculate(inputB), geometricService.calculate(inputB));

        assertEquals("2.5", viewB.getGeometric().getExpectedAttemptsValue());
        assertEquals("0.7840000", viewB.getGeometric().getWithinAttemptsDecimal());
        assertEquals(3, viewB.getAttemptRows().size());
        assertFalse(viewB.getInterpretations().isEmpty());
        assertNotEquals(viewA.getInterpretations(), viewB.getInterpretations());
        assertTrue(viewB.getInterpretations().stream().anyMatch(s -> s.contains("2.5")));
        assertTrue(viewB.getInterpretations().stream().anyMatch(s -> s.contains("78.4%")));
    }

    private CommunicationAnalysisInput buildInput(
            String lambda, String dropCount, String successProbability, String maxAttempts) {
        CommunicationAnalysisInput input = new CommunicationAnalysisInput();
        input.setLambda(lambda);
        input.setDropCount(dropCount);
        input.setSuccessProbability(successProbability);
        input.setMaxAttempts(maxAttempts);
        return input;
    }
}

