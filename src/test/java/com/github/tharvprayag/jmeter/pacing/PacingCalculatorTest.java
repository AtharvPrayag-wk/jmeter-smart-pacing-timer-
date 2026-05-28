package com.github.tharvprayag.jmeter.pacing;

import com.github.tharvprayag.jmeter.pacing.PacingCalculator.ThroughputUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PacingCalculator.
 * Tests the core pacing calculation logic independently of JMeter.
 */
class PacingCalculatorTest {

    @Test
    @DisplayName("Basic pacing: 10 users, 10 TPS => 1000ms pacing")
    void testBasicPacing_10Users_10TPS() {
        // 10 users / 10 TPS = 1 second per iteration = 1000ms
        long pacing = PacingCalculator.calculateBasicPacing(10, ThroughputUnit.TPS, 10);
        assertEquals(1000, pacing);
    }

    @Test
    @DisplayName("Basic pacing: 5 users, 10 TPS => 500ms pacing")
    void testBasicPacing_5Users_10TPS() {
        // 5 users / 10 TPS = 0.5 seconds = 500ms
        long pacing = PacingCalculator.calculateBasicPacing(10, ThroughputUnit.TPS, 5);
        assertEquals(500, pacing);
    }

    @Test
    @DisplayName("Basic pacing: 100 users, 50 TPS => 2000ms pacing")
    void testBasicPacing_100Users_50TPS() {
        // 100 users / 50 TPS = 2 seconds = 2000ms
        long pacing = PacingCalculator.calculateBasicPacing(50, ThroughputUnit.TPS, 100);
        assertEquals(2000, pacing);
    }

    @Test
    @DisplayName("TPH conversion: 100 users, 3600 TPH => 100000ms pacing")
    void testPacing_TPH() {
        // 3600 TPH = 1 TPS, 100 users / 1 TPS = 100 seconds = 100000ms
        long pacing = PacingCalculator.calculateBasicPacing(3600, ThroughputUnit.TPH, 100);
        assertEquals(100000, pacing);
    }

    @Test
    @DisplayName("TPM conversion: 10 users, 60 TPM => 10000ms pacing")
    void testPacing_TPM() {
        // 60 TPM = 1 TPS, 10 users / 1 TPS = 10 seconds = 10000ms
        long pacing = PacingCalculator.calculateBasicPacing(60, ThroughputUnit.TPM, 10);
        assertEquals(10000, pacing);
    }

    @Test
    @DisplayName("Adjusted pacing: subtract response time and think time")
    void testAdjustedPacing() {
        // Basic pacing: 10 users / 10 TPS = 1000ms
        // Minus 200ms response time, minus 300ms think time = 500ms
        long pacing = PacingCalculator.calculatePacing(10, ThroughputUnit.TPS, 10, 200, 300);
        assertEquals(500, pacing);
    }

    @Test
    @DisplayName("Adjusted pacing: should not go below zero")
    void testAdjustedPacing_NoNegative() {
        // Basic pacing: 10 users / 10 TPS = 1000ms
        // Minus 800ms response time, minus 500ms think time = -300ms => should be 0
        long pacing = PacingCalculator.calculatePacing(10, ThroughputUnit.TPS, 10, 800, 500);
        assertEquals(0, pacing);
    }

    @Test
    @DisplayName("Zero throughput should throw exception")
    void testZeroThroughput_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                PacingCalculator.calculateBasicPacing(0, ThroughputUnit.TPS, 10));
    }

    @Test
    @DisplayName("Zero users should throw exception")
    void testZeroUsers_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                PacingCalculator.calculateBasicPacing(10, ThroughputUnit.TPS, 0));
    }

    @Test
    @DisplayName("Negative throughput should throw exception")
    void testNegativeThroughput_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                PacingCalculator.calculateBasicPacing(-5, ThroughputUnit.TPS, 10));
    }

    @Test
    @DisplayName("Convert TPS to TPS returns same value")
    void testConvertToTPS_FromTPS() {
        assertEquals(10.0, PacingCalculator.convertToTPS(10, ThroughputUnit.TPS));
    }

    @Test
    @DisplayName("Convert TPM to TPS")
    void testConvertToTPS_FromTPM() {
        assertEquals(1.0, PacingCalculator.convertToTPS(60, ThroughputUnit.TPM), 0.001);
    }

    @Test
    @DisplayName("Convert TPH to TPS")
    void testConvertToTPS_FromTPH() {
        assertEquals(1.0, PacingCalculator.convertToTPS(3600, ThroughputUnit.TPH), 0.001);
    }

    @Test
    @DisplayName("Calculate required users")
    void testCalculateRequiredUsers() {
        // 10 TPS with 1000ms pacing => need 10 users
        int users = PacingCalculator.calculateRequiredUsers(10, ThroughputUnit.TPS, 1000);
        assertEquals(10, users);
    }

    @Test
    @DisplayName("Calculate achievable throughput")
    void testCalculateAchievableThroughput() {
        // 10 users with 1000ms pacing => 10 TPS
        double tps = PacingCalculator.calculateAchievableThroughput(10, 1000, ThroughputUnit.TPS);
        assertEquals(10.0, tps, 0.001);
    }

    @Test
    @DisplayName("Calculate achievable throughput in TPH")
    void testCalculateAchievableThroughput_TPH() {
        // 10 users with 1000ms pacing => 10 TPS => 36000 TPH
        double tph = PacingCalculator.calculateAchievableThroughput(10, 1000, ThroughputUnit.TPH);
        assertEquals(36000.0, tph, 0.001);
    }

    @Test
    @DisplayName("Real-world scenario: 50 users, target 500 TPH, avg RT 2sec, think time 1sec")
    void testRealWorldScenario() {
        // 500 TPH = 0.1389 TPS
        // Basic pacing = 50 / 0.1389 = 360 seconds = 360000ms
        // Adjusted = 360000 - 2000 - 1000 = 357000ms
        long pacing = PacingCalculator.calculatePacing(500, ThroughputUnit.TPH, 50, 2000, 1000);
        assertEquals(357000, pacing);
    }
}
