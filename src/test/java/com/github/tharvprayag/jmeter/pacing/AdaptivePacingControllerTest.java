package com.github.tharvprayag.jmeter.pacing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AdaptivePacingController.
 * Tests sliding window accuracy, adjustment direction, clamping, and thread safety.
 */
class AdaptivePacingControllerTest {

    private AdaptivePacingController controller;

    @BeforeEach
    void setUp() {
        controller = AdaptivePacingController.getInstance("test-timer");
        controller.reset();
    }

    @Test
    void testRecordCompletionIncrementsTotalCount() {
        assertEquals(0, controller.getTotalCompletions());
        controller.recordCompletion();
        controller.recordCompletion();
        controller.recordCompletion();
        assertEquals(3, controller.getTotalCompletions());
    }

    @Test
    void testGetActualTPSWithNoCompletions() {
        double tps = controller.getActualTPS(10);
        assertEquals(0.0, tps, 0.001);
    }

    @Test
    void testGetActualTPSWithRecentCompletions() {
        // Record 10 completions right now
        for (int i = 0; i < 10; i++) {
            controller.recordCompletion();
        }

        // Should be 10 completions in a 10-second window = 1.0 TPS
        double tps = controller.getActualTPS(10);
        assertEquals(1.0, tps, 0.001);
    }

    @Test
    void testGetActualTPSWithDifferentWindowSizes() {
        // Record 20 completions
        for (int i = 0; i < 20; i++) {
            controller.recordCompletion();
        }

        // 20 completions in 10-second window = 2.0 TPS
        assertEquals(2.0, controller.getActualTPS(10), 0.001);

        // 20 completions in 5-second window = 4.0 TPS
        assertEquals(4.0, controller.getActualTPS(5), 0.001);

        // 20 completions in 20-second window = 1.0 TPS
        assertEquals(1.0, controller.getActualTPS(20), 0.001);
    }

    @Test
    void testCalculateAdjustedPacingWhenActualMatchesTarget() {
        // When actual == target, no adjustment needed
        // Need at least 3 completions for adaptive to kick in
        controller.recordCompletion();
        controller.recordCompletion();
        controller.recordCompletion();

        long adjusted = controller.calculateAdjustedPacing(1000, 10.0, 10.0, 0.3);
        assertEquals(1000, adjusted); // No change when error = 0
    }

    @Test
    void testCalculateAdjustedPacingWhenTooFast() {
        // Actual > Target → should INCREASE pacing (slow down)
        controller.recordCompletion();
        controller.recordCompletion();
        controller.recordCompletion();

        long adjusted = controller.calculateAdjustedPacing(1000, 10.0, 15.0, 0.3);
        // error = (15 - 10) / 10 = 0.5
        // adjustment = 1000 * 0.3 * 0.5 = 150
        // adjusted = 1000 + 150 = 1150
        assertEquals(1150, adjusted);
    }

    @Test
    void testCalculateAdjustedPacingWhenTooSlow() {
        // Actual < Target → should DECREASE pacing (speed up)
        controller.recordCompletion();
        controller.recordCompletion();
        controller.recordCompletion();

        long adjusted = controller.calculateAdjustedPacing(1000, 10.0, 5.0, 0.3);
        // error = (5 - 10) / 10 = -0.5
        // adjustment = 1000 * 0.3 * (-0.5) = -150
        // adjusted = 1000 - 150 = 850
        assertEquals(850, adjusted);
    }

    @Test
    void testCalculateAdjustedPacingNeverGoesNegative() {
        controller.recordCompletion();
        controller.recordCompletion();
        controller.recordCompletion();

        // Very high dampening with actual much lower than target
        long adjusted = controller.calculateAdjustedPacing(100, 100.0, 1.0, 1.0);
        // error = (1 - 100) / 100 = -0.99
        // adjustment = 100 * 1.0 * (-0.99) = -99
        // adjusted = 100 - 99 = 1 (still positive)
        assertTrue(adjusted >= 0, "Pacing should never be negative, got: " + adjusted);
    }

    @Test
    void testCalculateAdjustedPacingClampedToMax() {
        controller.recordCompletion();
        controller.recordCompletion();
        controller.recordCompletion();

        // Actual way above target with high dampening
        long adjusted = controller.calculateAdjustedPacing(1000, 1.0, 100.0, 1.0);
        // error = (100 - 1) / 1 = 99
        // adjustment = 1000 * 1.0 * 99 = 99000
        // adjusted = 1000 + 99000 = 100000 but clamped to max(1000*3, 60000) = 60000
        long maxPacing = Math.max(1000 * 3, 60000);
        assertTrue(adjusted <= maxPacing,
                "Pacing should be clamped to max " + maxPacing + ", got: " + adjusted);
    }

    @Test
    void testCalculateAdjustedPacingReturnsBaseWhenNoData() {
        // No completions recorded → should return base pacing
        long adjusted = controller.calculateAdjustedPacing(5000, 10.0, 0.0, 0.3);
        assertEquals(5000, adjusted);
    }

    @Test
    void testCalculateAdjustedPacingReturnBaseWhenFewCompletions() {
        // Only 2 completions (less than threshold of 3) → should return base pacing
        controller.recordCompletion();
        controller.recordCompletion();

        long adjusted = controller.calculateAdjustedPacing(5000, 10.0, 2.0, 0.3);
        assertEquals(5000, adjusted);
    }

    @Test
    void testResetClearsState() {
        controller.recordCompletion();
        controller.recordCompletion();
        assertEquals(2, controller.getTotalCompletions());

        controller.reset();
        assertEquals(0, controller.getTotalCompletions());
        assertEquals(0.0, controller.getActualTPS(10), 0.001);
    }

    @Test
    void testSingletonPerTimerName() {
        AdaptivePacingController c1 = AdaptivePacingController.getInstance("timer-A");
        AdaptivePacingController c2 = AdaptivePacingController.getInstance("timer-A");
        AdaptivePacingController c3 = AdaptivePacingController.getInstance("timer-B");

        assertSame(c1, c2, "Same timer name should return same instance");
        assertNotSame(c1, c3, "Different timer names should return different instances");

        // Cleanup
        AdaptivePacingController.removeInstance("timer-A");
        AdaptivePacingController.removeInstance("timer-B");
    }

    @Test
    void testRemoveInstanceCleansUp() {
        AdaptivePacingController c1 = AdaptivePacingController.getInstance("remove-test");
        c1.recordCompletion();

        AdaptivePacingController.removeInstance("remove-test");

        // Getting a new instance should give a fresh controller
        AdaptivePacingController c2 = AdaptivePacingController.getInstance("remove-test");
        assertEquals(0, c2.getTotalCompletions());

        AdaptivePacingController.removeInstance("remove-test");
    }

    @Test
    void testDampeningFactorEffect() {
        controller.recordCompletion();
        controller.recordCompletion();
        controller.recordCompletion();

        // Same scenario with different dampening
        long lowDampening = controller.calculateAdjustedPacing(1000, 10.0, 15.0, 0.1);
        long highDampening = controller.calculateAdjustedPacing(1000, 10.0, 15.0, 0.9);

        // Higher dampening should produce larger adjustment
        assertTrue(highDampening > lowDampening,
                "Higher dampening should produce larger pacing increase. Low=" + lowDampening + " High=" + highDampening);
    }

    @Test
    void testThreadSafety() throws InterruptedException {
        // Spawn multiple threads recording completions simultaneously
        int threadCount = 10;
        int completionsPerThread = 100;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < completionsPerThread; j++) {
                    controller.recordCompletion();
                }
            });
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        assertEquals(threadCount * completionsPerThread, controller.getTotalCompletions(),
                "All completions should be recorded across threads");
    }

    @Test
    void testDefaultWindowHandlesZeroOrNegative() {
        controller.recordCompletion();
        // Should not throw, should default to 10 seconds
        double tps = controller.getActualTPS(0);
        assertEquals(0.1, tps, 0.001); // 1 completion / 10 seconds (default)

        tps = controller.getActualTPS(-5);
        assertEquals(0.1, tps, 0.001); // Should also default
    }
}
