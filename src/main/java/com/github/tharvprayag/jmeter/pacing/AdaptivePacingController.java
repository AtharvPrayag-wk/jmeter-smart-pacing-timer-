package com.github.tharvprayag.jmeter.pacing;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe controller for adaptive pacing.
 * Tracks actual throughput via a sliding time window of completed iterations
 * and adjusts pacing using a proportional feedback controller.
 *
 * One instance is shared per timer name across all threads — this gives
 * an aggregate view of actual throughput for that timer.
 */
public class AdaptivePacingController {

    // Singleton instances keyed by timer name
    private static final ConcurrentHashMap<String, AdaptivePacingController> INSTANCES = new ConcurrentHashMap<>();

    // Sliding window of completion timestamps (epoch ms)
    private final ConcurrentLinkedDeque<Long> completionTimestamps = new ConcurrentLinkedDeque<>();

    // Total completions counter
    private final AtomicLong totalCompletions = new AtomicLong(0);

    // Maximum entries to keep in sliding window (safety limit)
    private static final int MAX_WINDOW_ENTRIES = 50000;

    /**
     * Get or create a controller instance for the given timer name.
     */
    public static AdaptivePacingController getInstance(String timerName) {
        return INSTANCES.computeIfAbsent(timerName, k -> new AdaptivePacingController());
    }

    /**
     * Remove and cleanup a controller instance when test ends.
     */
    public static void removeInstance(String timerName) {
        AdaptivePacingController controller = INSTANCES.remove(timerName);
        if (controller != null) {
            controller.completionTimestamps.clear();
        }
    }

    /**
     * Record that an iteration has completed (called by each thread after each iteration).
     */
    public void recordCompletion() {
        long now = System.currentTimeMillis();
        completionTimestamps.addLast(now);
        totalCompletions.incrementAndGet();

        // Prevent unbounded memory growth - trim old entries
        while (completionTimestamps.size() > MAX_WINDOW_ENTRIES) {
            completionTimestamps.pollFirst();
        }
    }

    /**
     * Calculate actual TPS over the last N seconds.
     *
     * @param windowSeconds The time window to measure (e.g., 10 = last 10 seconds)
     * @return Actual transactions per second in the window
     */
    public double getActualTPS(int windowSeconds) {
        if (windowSeconds <= 0) {
            windowSeconds = 10;
        }

        long now = System.currentTimeMillis();
        long windowStartMs = now - (windowSeconds * 1000L);

        // Remove entries older than the window
        while (!completionTimestamps.isEmpty()) {
            Long oldest = completionTimestamps.peekFirst();
            if (oldest != null && oldest < windowStartMs) {
                completionTimestamps.pollFirst();
            } else {
                break;
            }
        }

        // Count remaining entries (those within the window)
        int count = completionTimestamps.size();
        return (double) count / windowSeconds;
    }

    /**
     * Calculate adjusted pacing using a proportional controller.
     *
     * Logic:
     * - If actual TPS > target TPS → we're going too fast → INCREASE pacing (slow down)
     * - If actual TPS < target TPS → we're going too slow → DECREASE pacing (speed up)
     *
     * The dampening factor (0.1 to 1.0) controls how aggressively we adjust.
     * Lower values = more conservative (less oscillation).
     * Higher values = faster convergence (but may oscillate).
     *
     * @param basePacing  The statically calculated pacing (ms)
     * @param targetTPS   The desired TPS
     * @param actualTPS   The measured actual TPS
     * @param dampening   Dampening factor (0.1 - 1.0, recommended 0.3)
     * @return Adjusted pacing in milliseconds (clamped to [0, basePacing * 3])
     */
    public long calculateAdjustedPacing(long basePacing, double targetTPS, double actualTPS, double dampening) {
        // If no data yet (test just started), use base pacing
        if (actualTPS <= 0 || totalCompletions.get() < 3) {
            return basePacing;
        }

        // Proportional error: positive = too fast, negative = too slow
        double error = (actualTPS - targetTPS) / targetTPS;

        // Apply dampening: adjustment = basePacing * dampening * error
        double adjustment = basePacing * dampening * error;

        long adjustedPacing = Math.round(basePacing + adjustment);

        // Clamp to reasonable range: never negative, never more than 3x base
        long maxPacing = Math.max(basePacing * 3, 60000); // at least 60s max
        adjustedPacing = Math.max(0, Math.min(adjustedPacing, maxPacing));

        return adjustedPacing;
    }

    /**
     * Get total number of completions recorded since last reset.
     */
    public long getTotalCompletions() {
        return totalCompletions.get();
    }

    /**
     * Reset the controller state (called at test start).
     */
    public void reset() {
        completionTimestamps.clear();
        totalCompletions.set(0);
    }
}
