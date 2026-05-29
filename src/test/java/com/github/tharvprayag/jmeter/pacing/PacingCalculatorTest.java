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

    // --- Load Multiplier Tests ---

    @Test
    @DisplayName("Apply multiplier: 120 TPH x 2.0 = 240 TPH")
    void testApplyMultiplier_2x() {
        double result = PacingCalculator.applyMultiplier(120, 2.0);
        assertEquals(240.0, result, 0.001);
    }

    @Test
    @DisplayName("Apply multiplier: 120 TPH x 0.5 = 60 TPH")
    void testApplyMultiplier_Half() {
        double result = PacingCalculator.applyMultiplier(120, 0.5);
        assertEquals(60.0, result, 0.001);
    }

    @Test
    @DisplayName("Apply multiplier: zero multiplier throws exception")
    void testApplyMultiplier_ZeroThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                PacingCalculator.applyMultiplier(120, 0));
    }

    // --- Total Transactions Tests ---

    @Test
    @DisplayName("Total transactions: 240 TPH for 60 min = 240")
    void testTotalTransactions_240TPH_60min() {
        long total = PacingCalculator.calculateTotalTransactions(240, ThroughputUnit.TPH, 60);
        assertEquals(240, total);
    }

    @Test
    @DisplayName("Total transactions: 240 TPH for 30 min = 120")
    void testTotalTransactions_240TPH_30min() {
        long total = PacingCalculator.calculateTotalTransactions(240, ThroughputUnit.TPH, 30);
        assertEquals(120, total);
    }

    @Test
    @DisplayName("Total transactions: 60 TPH for 60 min = 60")
    void testTotalTransactions_60TPH_60min() {
        long total = PacingCalculator.calculateTotalTransactions(60, ThroughputUnit.TPH, 60);
        assertEquals(60, total);
    }

    @Test
    @DisplayName("Total transactions: 60 TPH for 30 min = 30")
    void testTotalTransactions_60TPH_30min() {
        long total = PacingCalculator.calculateTotalTransactions(60, ThroughputUnit.TPH, 30);
        assertEquals(30, total);
    }

    @Test
    @DisplayName("Total transactions: 10 TPS for 10 min = 6000")
    void testTotalTransactions_10TPS_10min() {
        long total = PacingCalculator.calculateTotalTransactions(10, ThroughputUnit.TPS, 10);
        assertEquals(6000, total);
    }

    // --- Suggested Users Tests ---

    @Test
    @DisplayName("Suggested users: 120 TPH with 152s RT and 0 TT")
    void testSuggestedUsers_120TPH_152sRT() {
        // 120 TPH = 0.0333 TPS
        // Busy time = 152000ms, buffer = max(1000, 152000*0.2) = 30400ms
        // Total iteration = 182400ms = 182.4s
        // Users = 0.0333 * 182.4 = 6.08 → 7
        int suggested = PacingCalculator.calculateSuggestedUsers(120, ThroughputUnit.TPH, 152000, 0);
        assertTrue(suggested >= 6 && suggested <= 8,
                "Expected 6-8 users, got " + suggested);
    }

    @Test
    @DisplayName("Suggested users: 10 TPS with 500ms RT")
    void testSuggestedUsers_10TPS_500msRT() {
        // 10 TPS, busy=500ms, buffer=max(1000, 100)=1000ms, total=1500ms=1.5s
        // Users = 10 * 1.5 = 15
        int suggested = PacingCalculator.calculateSuggestedUsers(10, ThroughputUnit.TPS, 500, 0);
        assertEquals(15, suggested);
    }

    // --- Iterations Per User Tests ---

    @Test
    @DisplayName("Iterations per user: 60 min, pacing 300s, RT 50s, TT 10s")
    void testIterationsPerUser() {
        // Total iteration = 300000 + 50000 + 10000 = 360000ms = 360s
        // In 60 min (3600s): 3600/360 = 10 iterations
        long iters = PacingCalculator.calculateIterationsPerUser(60, 300000, 50000, 10000);
        assertEquals(10, iters);
    }

    @Test
    @DisplayName("Iterations per user: 30 min, pacing 1000ms, RT 200ms, TT 0")
    void testIterationsPerUser_Fast() {
        // Total iteration = 1000 + 200 + 0 = 1200ms
        // In 30 min (1800000ms): 1800000/1200 = 1500 iterations
        long iters = PacingCalculator.calculateIterationsPerUser(30, 1000, 200, 0);
        assertEquals(1500, iters);
    }

    // --- Full Scenario: User's example ---

    @Test
    @DisplayName("Full scenario: 1x = 120 TPH, 12 users, 152s RT, 60 min steady state")
    void testFullScenario_1x() {
        double baseThroughput = 120;
        double multiplier = 1.0;
        double effective = PacingCalculator.applyMultiplier(baseThroughput, multiplier);
        assertEquals(120.0, effective);

        long totalTx = PacingCalculator.calculateTotalTransactions(effective, ThroughputUnit.TPH, 60);
        assertEquals(120, totalTx);

        long pacing = PacingCalculator.calculatePacing(effective, ThroughputUnit.TPH, 12, 152000, 0);
        // Iteration interval = 12/0.0333*1000 = 360000ms, pacing = 360000-152000 = 208000ms
        assertEquals(208000, pacing);

        long iters = PacingCalculator.calculateIterationsPerUser(60, pacing, 152000, 0);
        assertEquals(10, iters); // 3600000/(208000+152000) = 10
    }

    @Test
    @DisplayName("Full scenario: 2x = 240 TPH, 60 min → 240 total transactions")
    void testFullScenario_2x_60min() {
        double effective = PacingCalculator.applyMultiplier(120, 2.0);
        assertEquals(240.0, effective);

        long totalTx = PacingCalculator.calculateTotalTransactions(effective, ThroughputUnit.TPH, 60);
        assertEquals(240, totalTx);
    }

    @Test
    @DisplayName("Full scenario: 2x = 240 TPH, 30 min → 120 total transactions")
    void testFullScenario_2x_30min() {
        double effective = PacingCalculator.applyMultiplier(120, 2.0);
        long totalTx = PacingCalculator.calculateTotalTransactions(effective, ThroughputUnit.TPH, 30);
        assertEquals(120, totalTx);
    }

    @Test
    @DisplayName("Full scenario: 0.5x = 60 TPH, 60 min → 60 total transactions")
    void testFullScenario_halfx_60min() {
        double effective = PacingCalculator.applyMultiplier(120, 0.5);
        assertEquals(60.0, effective);
        long totalTx = PacingCalculator.calculateTotalTransactions(effective, ThroughputUnit.TPH, 60);
        assertEquals(60, totalTx);
    }

    @Test
    @DisplayName("Full scenario: 0.5x = 60 TPH, 30 min → 30 total transactions")
    void testFullScenario_halfx_30min() {
        double effective = PacingCalculator.applyMultiplier(120, 0.5);
        long totalTx = PacingCalculator.calculateTotalTransactions(effective, ThroughputUnit.TPH, 30);
        assertEquals(30, totalTx);
    }
}
