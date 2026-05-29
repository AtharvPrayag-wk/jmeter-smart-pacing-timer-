package com.github.tharvprayag.jmeter.pacing;

/**
 * Core pacing calculation engine.
 * Pure logic — no JMeter dependencies. This makes it easy to unit test.
 *
 * Pacing Formula:
 *   Basic:    pacing_ms = (vUsers / targetTPS) * 1000
 *   Adjusted: pacing_ms = max(0, (vUsers / targetTPS) * 1000 - avgResponseTime - thinkTime)
 *
 * Load Multiplier:
 *   effectiveRate = baseThroughput * multiplier
 *   totalTransactions = effectiveRate * (durationMinutes / 60)
 *
 * Suggested Users:
 *   idealUsers = ceil(effectiveTPS * iterationTime_seconds)
 *   where iterationTime = avgResponseTime + thinkTime + minPacingBuffer
 */
public class PacingCalculator {

    public enum ThroughputUnit {
        TPS,  // Transactions Per Second
        TPM,  // Transactions Per Minute
        TPH   // Transactions Per Hour
    }

    /**
     * Calculate the required pacing delay in milliseconds.
     *
     * @param targetThroughput The desired throughput value (already multiplied if using load multiplier)
     * @param unit             The unit of throughput (TPS, TPM, TPH)
     * @param numberOfUsers    Number of virtual users (threads)
     * @param avgResponseTimeMs Average response time in milliseconds (0 if unknown)
     * @param thinkTimeMs      Think time in milliseconds (0 if none)
     * @return Pacing delay in milliseconds (minimum 0)
     */
    public static long calculatePacing(double targetThroughput, ThroughputUnit unit,
                                       int numberOfUsers, long avgResponseTimeMs,
                                       long thinkTimeMs) {

        if (targetThroughput <= 0) {
            throw new IllegalArgumentException("Target throughput must be greater than 0");
        }
        if (numberOfUsers <= 0) {
            throw new IllegalArgumentException("Number of users must be greater than 0");
        }

        // Convert everything to TPS
        double targetTPS = convertToTPS(targetThroughput, unit);

        // Core formula: Iteration Interval = (Users / TPS) * 1000 ms
        // Pacing = Iteration Interval - Response Time - Think Time
        double iterationIntervalMs = (numberOfUsers / targetTPS) * 1000.0;

        // Subtract response time and think time (pacing is the WAIT portion of the iteration)
        double adjustedPacingMs = iterationIntervalMs - avgResponseTimeMs - thinkTimeMs;

        // Never return negative pacing
        return Math.max(0, Math.round(adjustedPacingMs));
    }

    /**
     * Calculate pacing without response time/think time adjustment.
     */
    public static long calculateBasicPacing(double targetThroughput, ThroughputUnit unit,
                                            int numberOfUsers) {
        return calculatePacing(targetThroughput, unit, numberOfUsers, 0, 0);
    }

    /**
     * Apply load multiplier to base throughput.
     * e.g., baseThroughput=120 TPH, multiplier=2.0 → effectiveRate=240 TPH
     */
    public static double applyMultiplier(double baseThroughput, double multiplier) {
        if (multiplier <= 0) {
            throw new IllegalArgumentException("Load multiplier must be greater than 0");
        }
        return baseThroughput * multiplier;
    }

    /**
     * Calculate total transactions expected during steady state.
     *
     * @param effectiveThroughput The effective rate (base × multiplier)
     * @param unit                Throughput unit
     * @param steadyStateDurationMinutes Duration of steady state in minutes
     * @return Total number of transactions expected
     */
    public static long calculateTotalTransactions(double effectiveThroughput, ThroughputUnit unit,
                                                  double steadyStateDurationMinutes) {
        if (steadyStateDurationMinutes <= 0) {
            throw new IllegalArgumentException("Duration must be greater than 0");
        }
        double tps = convertToTPS(effectiveThroughput, unit);
        double totalSeconds = steadyStateDurationMinutes * 60.0;
        return Math.round(tps * totalSeconds);
    }

    /**
     * Calculate the ideal (suggested) number of users to achieve target throughput.
     *
     * Formula: Users = ceil(TPS × iteration_time_seconds)
     * Where iteration_time = avgResponseTime + thinkTime + minPacingBuffer
     * The minPacingBuffer ensures there's always some pacing (we use 20% of iteration time as buffer).
     *
     * @param targetThroughput Effective throughput target
     * @param unit             Throughput unit
     * @param avgResponseTimeMs Average response time in ms
     * @param thinkTimeMs      Think time in ms
     * @return Suggested number of users (rounded up)
     */
    public static int calculateSuggestedUsers(double targetThroughput, ThroughputUnit unit,
                                              long avgResponseTimeMs, long thinkTimeMs) {
        if (targetThroughput <= 0) {
            throw new IllegalArgumentException("Target throughput must be greater than 0");
        }

        double targetTPS = convertToTPS(targetThroughput, unit);

        // Iteration "busy" time = response time + think time
        double busyTimeMs = avgResponseTimeMs + thinkTimeMs;

        // Add 20% buffer to ensure comfortable pacing (not running at 100% capacity)
        // Minimum buffer is 1 second to avoid zero-pacing situations
        double bufferMs = Math.max(1000, busyTimeMs * 0.2);
        double totalIterationTimeMs = busyTimeMs + bufferMs;

        // Users needed = TPS × iteration_time_in_seconds
        double users = targetTPS * (totalIterationTimeMs / 1000.0);

        // At minimum 1 user
        return Math.max(1, (int) Math.ceil(users));
    }

    /**
     * Calculate iterations per user during steady state.
     *
     * @param steadyStateDurationMinutes Duration in minutes
     * @param pacingMs                   Calculated pacing in ms
     * @param avgResponseTimeMs          Average response time in ms
     * @param thinkTimeMs                Think time in ms
     * @return Approximate number of iterations each user will perform
     */
    public static long calculateIterationsPerUser(double steadyStateDurationMinutes,
                                                  long pacingMs, long avgResponseTimeMs,
                                                  long thinkTimeMs) {
        if (steadyStateDurationMinutes <= 0) {
            return 0;
        }
        // Total iteration time = pacing + response time + think time
        long iterationTimeMs = pacingMs + avgResponseTimeMs + thinkTimeMs;
        if (iterationTimeMs <= 0) {
            return 0;
        }
        double totalMs = steadyStateDurationMinutes * 60.0 * 1000.0;
        return (long) (totalMs / iterationTimeMs);
    }

    /**
     * Convert any throughput unit to Transactions Per Second.
     */
    public static double convertToTPS(double throughput, ThroughputUnit unit) {
        switch (unit) {
            case TPS:
                return throughput;
            case TPM:
                return throughput / 60.0;
            case TPH:
                return throughput / 3600.0;
            default:
                throw new IllegalArgumentException("Unknown throughput unit: " + unit);
        }
    }

    /**
     * Calculate the number of users required to achieve target throughput
     * given a known pacing value.
     *
     * @param targetThroughput Target throughput
     * @param unit             Throughput unit
     * @param pacingMs         Desired pacing in milliseconds
     * @return Number of users needed (rounded up)
     */
    public static int calculateRequiredUsers(double targetThroughput, ThroughputUnit unit,
                                             long pacingMs) {
        if (targetThroughput <= 0 || pacingMs <= 0) {
            throw new IllegalArgumentException("Throughput and pacing must be greater than 0");
        }
        double targetTPS = convertToTPS(targetThroughput, unit);
        // Users = TPS * Pacing(sec)
        double users = targetTPS * (pacingMs / 1000.0);
        return (int) Math.ceil(users);
    }

    /**
     * Calculate the achievable throughput given users and pacing.
     *
     * @param numberOfUsers Number of virtual users
     * @param pacingMs      Pacing in milliseconds
     * @param unit          Desired output unit
     * @return Achievable throughput in the specified unit
     */
    public static double calculateAchievableThroughput(int numberOfUsers, long pacingMs,
                                                       ThroughputUnit unit) {
        if (numberOfUsers <= 0 || pacingMs <= 0) {
            throw new IllegalArgumentException("Users and pacing must be greater than 0");
        }
        // TPS = Users / Pacing(sec)
        double tps = numberOfUsers / (pacingMs / 1000.0);

        switch (unit) {
            case TPS:
                return tps;
            case TPM:
                return tps * 60.0;
            case TPH:
                return tps * 3600.0;
            default:
                throw new IllegalArgumentException("Unknown throughput unit: " + unit);
        }
    }
}
