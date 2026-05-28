package com.github.tharvprayag.jmeter.pacing;

import org.apache.jmeter.testelement.AbstractTestElement;
import org.apache.jmeter.testelement.TestStateListener;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.timers.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JMeter Timer that automatically calculates and applies pacing delay
 * based on target throughput (TPS/TPH/TPM) and number of virtual users.
 *
 * How it works:
 * 1. User specifies target throughput and unit
 * 2. User specifies number of VUsers (or auto-detect from thread group)
 * 3. Timer calculates: pacing = (vUsers / targetTPS) * 1000 ms
 * 4. Timer subtracts avg response time and think time if configured
 * 5. Timer pauses the thread for the calculated duration
 */
public class PacingCalculatorTimer extends AbstractTestElement implements Timer, TestStateListener {

    private static final Logger log = LoggerFactory.getLogger(PacingCalculatorTimer.class);

    // Property keys for storing configuration in JMeter test plan
    public static final String TARGET_THROUGHPUT = "PacingCalculatorTimer.targetThroughput";
    public static final String THROUGHPUT_UNIT = "PacingCalculatorTimer.throughputUnit";
    public static final String NUMBER_OF_USERS = "PacingCalculatorTimer.numberOfUsers";
    public static final String THINK_TIME = "PacingCalculatorTimer.thinkTime";
    public static final String AVG_RESPONSE_TIME = "PacingCalculatorTimer.avgResponseTime";
    public static final String AUTO_DETECT_USERS = "PacingCalculatorTimer.autoDetectUsers";
    public static final String RANDOMIZATION_PERCENT = "PacingCalculatorTimer.randomizationPercent";

    /**
     * Called by JMeter to get the delay (in milliseconds) to pause the current thread.
     * This is the core method of the Timer interface.
     *
     * @return delay in milliseconds
     */
    @Override
    public long delay() {
        try {
            double targetThroughput = getTargetThroughput();
            PacingCalculator.ThroughputUnit unit = getThroughputUnit();
            int users = getEffectiveNumberOfUsers();
            long thinkTime = getThinkTime();
            long avgResponseTime = getAvgResponseTime();

            long pacing = PacingCalculator.calculatePacing(
                    targetThroughput, unit, users, avgResponseTime, thinkTime);

            // Apply randomization if configured
            double randomPercent = getRandomizationPercent();
            if (randomPercent > 0) {
                double factor = 1.0 + (Math.random() * 2 - 1) * (randomPercent / 100.0);
                pacing = Math.max(0, Math.round(pacing * factor));
            }

            log.debug("Smart Pacing Timer: calculated delay = {} ms (target={} {}, users={}, rt={}, tt={})",
                    pacing, targetThroughput, unit, users, avgResponseTime, thinkTime);

            return pacing;

        } catch (Exception e) {
            log.warn("Smart Pacing Timer: Error calculating pacing, using 1000ms default. Error: {}",
                    e.getMessage());
            return 1000;
        }
    }

    // --- Property Getters and Setters ---

    public double getTargetThroughput() {
        String val = getPropertyAsString(TARGET_THROUGHPUT, "1.0");
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return 1.0;
        }
    }

    public void setTargetThroughput(double throughput) {
        setProperty(TARGET_THROUGHPUT, String.valueOf(throughput));
    }

    public PacingCalculator.ThroughputUnit getThroughputUnit() {
        String unitStr = getPropertyAsString(THROUGHPUT_UNIT, "TPS");
        try {
            return PacingCalculator.ThroughputUnit.valueOf(unitStr);
        } catch (IllegalArgumentException e) {
            return PacingCalculator.ThroughputUnit.TPS;
        }
    }

    public void setThroughputUnit(PacingCalculator.ThroughputUnit unit) {
        setProperty(THROUGHPUT_UNIT, unit.name());
    }

    public int getNumberOfUsers() {
        return getPropertyAsInt(NUMBER_OF_USERS, 1);
    }

    public void setNumberOfUsers(int users) {
        setProperty(NUMBER_OF_USERS, users);
    }

    public boolean isAutoDetectUsers() {
        return getPropertyAsBoolean(AUTO_DETECT_USERS, false);
    }

    public void setAutoDetectUsers(boolean autoDetect) {
        setProperty(AUTO_DETECT_USERS, autoDetect);
    }

    public long getThinkTime() {
        return getPropertyAsLong(THINK_TIME, 0);
    }

    public void setThinkTime(long thinkTimeMs) {
        setProperty(THINK_TIME, thinkTimeMs);
    }

    public long getAvgResponseTime() {
        return getPropertyAsLong(AVG_RESPONSE_TIME, 0);
    }

    public void setAvgResponseTime(long avgResponseTimeMs) {
        setProperty(AVG_RESPONSE_TIME, avgResponseTimeMs);
    }

    public double getRandomizationPercent() {
        String val = getPropertyAsString(RANDOMIZATION_PERCENT, "0.0");
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public void setRandomizationPercent(double percent) {
        setProperty(RANDOMIZATION_PERCENT, String.valueOf(percent));
    }

    /**
     * Get effective number of users - either from config or auto-detected from thread group.
     */
    private int getEffectiveNumberOfUsers() {
        if (isAutoDetectUsers()) {
            int activeThreads = JMeterContextService.getContext().getThreadGroup().numberOfActiveThreads();
            return activeThreads > 0 ? activeThreads : 1;
        }
        return getNumberOfUsers();
    }

    // --- TestStateListener methods ---

    @Override
    public void testStarted() {
        log.info("Smart Pacing Timer '{}' started. Target: {} {}, Users: {}{}",
                getName(), getTargetThroughput(), getThroughputUnit(),
                isAutoDetectUsers() ? "auto-detect" : getNumberOfUsers(),
                getRandomizationPercent() > 0 ? ", Randomization: ±" + getRandomizationPercent() + "%" : "");
    }

    @Override
    public void testStarted(String host) {
        testStarted();
    }

    @Override
    public void testEnded() {
        log.info("Smart Pacing Timer '{}' ended.", getName());
    }

    @Override
    public void testEnded(String host) {
        testEnded();
    }
}
