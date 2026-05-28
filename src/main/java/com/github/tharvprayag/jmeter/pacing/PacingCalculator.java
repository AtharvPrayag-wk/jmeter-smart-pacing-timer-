package com.github.tharvprayag.jmeter.pacing;

/**
 * Core pacing calculation engine.
 * Pure logic — no JMeter dependencies. This makes it easy to unit test.
 *
 * Pacing Formula:
 *   Basic:    pacing_ms = (vUsers / targetTPS) * 1000
 *   Adjusted: pacing_ms = max(0, (vUsers / targetTPS) * 1000 - avgResponseTime - thinkTime)
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
     * @param targetThroughput The desired throughput value
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

        // Core formula: Pacing = (Users / TPS) * 1000 ms
        double rawPacingMs = (numberOfUsers / targetTPS) * 1000.0;

        // Subtract response time and think time (pacing includes iteration time)
        double adjustedPacingMs = rawPacingMs - avgResponseTimeMs - thinkTimeMs;

        // Never return negative pacing
        return Math.max(0, Math.round(adjustedPacingMs));
    }

    /**
     * Calculate pacing without response time/think time adjustment.
     * Useful when you want to set a fixed iteration interval.
     */
    public static long calculateBasicPacing(double targetThroughput, ThroughputUnit unit,
                                            int numberOfUsers) {
        return calculatePacing(targetThroughput, unit, numberOfUsers, 0, 0);
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
