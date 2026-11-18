package com.deepspace.analytics;

import com.deepspace.analytics.model.PoissonResult;
import com.deepspace.analytics.service.PoissonService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoissonServiceTest {

    private final PoissonService poissonService = new PoissonService();

    @Test
    void test1_lambda3_k2() {
        PoissonResult result = poissonService.calculate(3.0, 2L);

        assertEquals(3.0, result.getLambda(), 1e-12);
        assertEquals(2L, result.getDropCount());
        assertEquals(0.2240418, result.getProbabilityOfExactDrops(), 1e-7);
        assertEquals(3.0, result.getExpectedDrops(), 1e-12);
        assertEquals(0.0497871, result.getProbabilityOfZeroDrops(), 1e-7);
        assertEquals(0.9502129, result.getProbabilityOfAtLeastOneDrop(), 1e-7);
        assertEquals(3.0, result.getVariance(), 1e-12);
    }

    @Test
    void test2_lambda0_k0() {
        PoissonResult result = poissonService.calculate(0.0, 0L);

        assertEquals(1.0, result.getProbabilityOfExactDrops(), 1e-12);
        assertEquals(0.0, result.getExpectedDrops(), 1e-12);
        assertEquals(1.0, result.getProbabilityOfZeroDrops(), 1e-12);
        assertEquals(0.0, result.getProbabilityOfAtLeastOneDrop(), 1e-12);
        assertEquals(0.0, result.getVariance(), 1e-12);
    }

    @Test
    void test3_lambda0_k2() {
        PoissonResult result = poissonService.calculate(0.0, 2L);

        assertEquals(0.0, result.getProbabilityOfExactDrops(), 1e-12);
        assertEquals(0.0, result.getExpectedDrops(), 1e-12);
        assertEquals(1.0, result.getProbabilityOfZeroDrops(), 1e-12);
        assertEquals(0.0, result.getProbabilityOfAtLeastOneDrop(), 1e-12);
        assertEquals(0.0, result.getVariance(), 1e-12);
    }

    @Test
    void test4_lambda1_k0() {
        PoissonResult result = poissonService.calculate(1.0, 0L);

        assertEquals(0.3678794, result.getProbabilityOfExactDrops(), 1e-7);
        assertEquals(1.0, result.getExpectedDrops(), 1e-12);
        assertEquals(0.3678794, result.getProbabilityOfZeroDrops(), 1e-7);
        assertEquals(0.6321206, result.getProbabilityOfAtLeastOneDrop(), 1e-7);
        assertEquals(1.0, result.getVariance(), 1e-12);
    }

    @Test
    void test5_lambda5_k3() {
        PoissonResult result = poissonService.calculate(5.0, 3L);

        assertEquals(0.1403739, result.getProbabilityOfExactDrops(), 1e-7);
        assertEquals(5.0, result.getExpectedDrops(), 1e-12);
        assertEquals(0.0067379, result.getProbabilityOfZeroDrops(), 1e-7);
        assertEquals(0.9932621, result.getProbabilityOfAtLeastOneDrop(), 1e-7);
        assertEquals(5.0, result.getVariance(), 1e-12);
    }

    @Test
    void avoidsFactorialOverflowForLargeK() {
        PoissonResult large = poissonService.calculate(250.0, 250L);
        assertTrue(Double.isFinite(large.getProbabilityOfExactDrops()));
        assertTrue(large.getProbabilityOfExactDrops() > 0.0 && large.getProbabilityOfExactDrops() < 1.0);

        PoissonResult veryLarge = poissonService.calculate(15000.0, 15000L);
        assertTrue(Double.isFinite(veryLarge.getProbabilityOfExactDrops()));
        assertTrue(veryLarge.getProbabilityOfExactDrops() > 0.0 && veryLarge.getProbabilityOfExactDrops() < 1.0);
    }

    @Test
    void rejectsInvalidInputsInsideService() {
        assertThrows(IllegalArgumentException.class, () -> poissonService.calculate(-0.1, 2L));
        assertThrows(IllegalArgumentException.class, () -> poissonService.calculate(Double.NaN, 2L));
        assertThrows(IllegalArgumentException.class, () -> poissonService.calculate(Double.POSITIVE_INFINITY, 2L));
        assertThrows(IllegalArgumentException.class, () -> poissonService.calculate(3.0, -1L));
    }
}

