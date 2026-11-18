package com.deepspace.analytics;

import com.deepspace.analytics.model.GeometricResult;
import com.deepspace.analytics.service.GeometricService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeometricServiceTest {

    private final GeometricService geometricService = new GeometricService();

    @Test
    void test1_p04_k3() {
        double prob = geometricService.calculateProbabilityForAttempt(0.4, 3);
        assertEquals(0.144, prob, 1e-12);
    }

    @Test
    void test2_p05_expectedAttempts() {
        GeometricResult result = geometricService.calculate(0.5, 4);
        assertEquals(2.0, result.getExpectedAttempts(), 1e-12);
    }

    @Test
    void test3_p1_k1() {
        double probAttempt1 = geometricService.calculateProbabilityForAttempt(1.0, 1);
        double probAttempt2 = geometricService.calculateProbabilityForAttempt(1.0, 2);
        GeometricResult result = geometricService.calculate(1.0, 3);

        assertEquals(1.0, probAttempt1, 1e-12);
        assertEquals(0.0, probAttempt2, 1e-12);
        assertEquals(1.0, result.getExpectedAttempts(), 1e-12);
        assertEquals(1.0, result.getProbabilityWithinAttempts(), 1e-12);
        assertEquals(0.0, result.getProbabilityMoreThanAttempts(), 1e-12);
        assertEquals(1, result.getMostLikelyAttempt());
    }

    @Test
    void test4_p04_n3_withinAttempts() {
        GeometricResult result = geometricService.calculate(0.4, 3);
        assertEquals(0.784, result.getProbabilityWithinAttempts(), 1e-12);
    }

    @Test
    void test5_p04_n3_moreThanAttempts() {
        GeometricResult result = geometricService.calculate(0.4, 3);
        assertEquals(0.216, result.getProbabilityMoreThanAttempts(), 1e-12);
    }

    @Test
    void test6_p06_n5_fullMetricsAndSeries() {
        GeometricResult result = geometricService.calculate(0.6, 5);

        assertEquals(0.6, result.getSuccessProbability(), 1e-12);
        assertEquals(5, result.getMaxAttempts());
        assertEquals(1.6666667, result.getExpectedAttempts(), 1e-7);
        assertEquals(0.98976, result.getProbabilityWithinAttempts(), 1e-12);
        assertEquals(0.01024, result.getProbabilityMoreThanAttempts(), 1e-12);
        assertEquals(1, result.getMostLikelyAttempt());
        assertEquals(5, result.getAttemptProbabilities().size());

        double sum = result.getAttemptProbabilities().stream()
                .mapToDouble(GeometricResult.AttemptProbability::getProbability)
                .sum();
        assertEquals(result.getProbabilityWithinAttempts(), sum, 1e-12);
        assertEquals(1.0, result.getProbabilityWithinAttempts() + result.getProbabilityMoreThanAttempts(), 1e-12);
        assertTrue(result.getAttemptProbabilities().stream()
                .allMatch(item -> item.getProbability() >= 0.0 && item.getProbability() <= 1.0));
    }

    @Test
    void cumulativeSeriesMatchesFormulaAcrossAttempts() {
        var series = geometricService.calculateCumulativeSeries(0.6, 5);
        assertEquals(5, series.size());
        assertEquals(0.6, series.get(0).getProbability(), 1e-12);
        assertEquals(0.84, series.get(1).getProbability(), 1e-12);
        assertEquals(0.936, series.get(2).getProbability(), 1e-12);
        assertEquals(0.9744, series.get(3).getProbability(), 1e-12);
        assertEquals(0.98976, series.get(4).getProbability(), 1e-12);

        var boundarySeries = geometricService.calculateCumulativeSeries(1.0, 3);
        assertEquals(3, boundarySeries.size());
        assertTrue(boundarySeries.stream().allMatch(pt -> Math.abs(pt.getProbability() - 1.0) < 1e-12));
    }

    @Test
    void rejectsInvalidInputsInsideService() {
        assertThrows(IllegalArgumentException.class, () -> geometricService.calculate(0.0, 3));
        assertThrows(IllegalArgumentException.class, () -> geometricService.calculate(-0.2, 3));
        assertThrows(IllegalArgumentException.class, () -> geometricService.calculate(1.1, 3));
        assertThrows(IllegalArgumentException.class, () -> geometricService.calculate(Double.NaN, 3));
        assertThrows(IllegalArgumentException.class, () -> geometricService.calculate(0.5, 0));
        assertThrows(IllegalArgumentException.class, () -> geometricService.calculate(0.5, -2));
        assertThrows(IllegalArgumentException.class, () -> geometricService.calculateProbabilityForAttempt(0.5, 0));
    }
}

