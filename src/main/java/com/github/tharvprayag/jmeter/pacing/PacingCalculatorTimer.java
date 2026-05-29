package com.github.tharvprayag.jmeter.pacing;

import org.apache.jmeter.engine.util.CompoundVariable;
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
 * Features:
 * - Static pacing calculation with load multiplier
 * - Adaptive pacing mode (runtime feedback loop)
 * - JMeter properties/variables support in all numeric fields
 * - End-to-End response time mode
 * - Randomization
 */
public class PacingCalculatorTimer extends AbstractTestElement implements Timer, TestStateListener {

    private static final Logger log = LoggerFactory.getLogger(PacingCalculatorTimer.class);

    // Property keys for storing configuration in JMeter test plan
    public static final String TARGET_THROUGHPUT = "PacingCalculatorTimer.targetThroughput";
    public static final String THROUGHPUT_UNIT = "PacingCalculatorTimer.throughputUnit";
    public static final String LOAD_MULTIPLIER = "PacingCalculatorTimer.loadMultiplier";
    public static final String STEADY_STATE_DURATION = "PacingCalculatorTimer.steadyStateDuration";
    public static final String RAMP_UP_TIME = "PacingCalculatorTimer.rampUpTime";
    public static final String NUMBER_OF_USERS = "PacingCalculatorTimer.numberOfUsers";
    public static final String THINK_TIME = "PacingCalculatorTimer.thinkTime";
    public static final String AVG_RESPONSE_TIME = "PacingCalculatorTimer.avgResponseTime";
    public static final String END_TO_END_RESPONSE_TIME = "PacingCalculatorTimer.endToEndResponseTime";
    public static final String USE_END_TO_END = "PacingCalculatorTimer.useEndToEnd";
    public static final String AUTO_DETECT_USERS = "PacingCalculatorTimer.autoDetectUsers";
    public static final String RANDOMIZATION_PERCENT = "PacingCalculatorTimer.randomizationPercent";

    // Adaptive pacing properties
    public static final String ADAPTIVE_MODE = "PacingCalculatorTimer.adaptiveMode";
    public static final String ADAPTIVE_WINDOW_SECONDS = "PacingCalculatorTimer.adaptiveWindowSeconds";
    public static final String ADAPTIVE_DAMPENING = "PacingCalculatorTimer.adaptiveDampening";

    // First iteration skip
    public static final String SKIP_FIRST_ITERATION = "PacingCalculatorTimer.skipFirstIteration";

    // ThreadLocal to track first iteration per thread (user)
    private static final ThreadLocal<Boolean> isFirstIteration = ThreadLocal.withInitial(() -> Boolean.TRUE);

    // --- Variable Resolution ---

    /**
     * Resolve a raw property value that may contain JMeter variables/functions.
     * e.g., "${__P(targetTPS,100)}" → "100", "${myVar}" → resolved value.
     * If no variables present or resolution fails, returns the raw string as-is.
     */
    private String resolveValue(String rawValue) {
        if (rawValue == null || rawValue.isEmpty() || !rawValue.contains("${")) {
            return rawValue;
        }
        try {
            CompoundVariable cv = new CompoundVariable(rawValue);
            return cv.execute();
        } catch (Exception e) {
            log.debug("Could not resolve variable '{}', using raw value. Error: {}", rawValue, e.getMessage());
            return rawValue;
        }
    }

    private double resolveDouble(String key, String defaultValue) {
        String raw = getPropertyAsString(key, defaultValue);
        String resolved = resolveValue(raw);
        try {
            return Double.parseDouble(resolved);
        } catch (NumberFormatException e) {
            return Double.parseDouble(defaultValue);
        }
    }

    private long resolveLong(String key, String defaultValue) {
        String raw = getPropertyAsString(key, defaultValue);
        String resolved = resolveValue(raw);
        try {
            return Long.parseLong(resolved);
        } catch (NumberFormatException e) {
            return Long.parseLong(defaultValue);
        }
    }

    private int resolveInt(String key, String defaultValue) {
        String raw = getPropertyAsString(key, defaultValue);
        String resolved = resolveValue(raw);
        try {
            return Integer.parseInt(resolved);
        } catch (NumberFormatException e) {
            return Integer.parseInt(defaultValue);
        }
    }

    // --- Core Timer Method ---

    @Override
    public long delay() {
        try {
            // Skip pacing on first iteration — all users start immediately
            if (isSkipFirstIteration() && isFirstIteration.get()) {
                isFirstIteration.set(Boolean.FALSE);
                log.debug("Smart Pacing Timer: First iteration for thread '{}' — skipping pacing",
                        Thread.currentThread().getName());
                return 0;
            }

            double baseThroughput = getTargetThroughput();
            double multiplier = getLoadMultiplier();
            double effectiveThroughput = PacingCalculator.applyMultiplier(baseThroughput, multiplier);

            PacingCalculator.ThroughputUnit unit = getThroughputUnit();
            int users = getEffectiveNumberOfUsers();

            // Determine RT and TT based on mode
            long avgResponseTime;
            long thinkTime;
            if (isUseEndToEnd()) {
                avgResponseTime = getEndToEndResponseTime();
                thinkTime = 0;
            } else {
                avgResponseTime = getAvgResponseTime();
                thinkTime = getThinkTime();
            }

            long pacing = PacingCalculator.calculatePacing(
                    effectiveThroughput, unit, users, avgResponseTime, thinkTime);

            // Adaptive mode: adjust pacing based on actual throughput feedback
            if (isAdaptiveMode()) {
                double targetTPS = PacingCalculator.convertToTPS(effectiveThroughput, unit);
                AdaptivePacingController controller = AdaptivePacingController.getInstance(getName());
                controller.recordCompletion();
                double actualTPS = controller.getActualTPS(getAdaptiveWindowSeconds());
                pacing = controller.calculateAdjustedPacing(
                        pacing, targetTPS, actualTPS, getAdaptiveDampening());

                log.debug("Smart Pacing Timer [ADAPTIVE]: delay={}ms, targetTPS={}, actualTPS={:.2f}, window={}s",
                        pacing, targetTPS, actualTPS, getAdaptiveWindowSeconds());
            }

            // Apply randomization if configured
            double randomPercent = getRandomizationPercent();
            if (randomPercent > 0) {
                double factor = 1.0 + (Math.random() * 2 - 1) * (randomPercent / 100.0);
                pacing = Math.max(0, Math.round(pacing * factor));
            }

            log.debug("Smart Pacing Timer: delay={}ms (base={} x{} = {} {}, users={}, rt={}, tt={}, adaptive={})",
                    pacing, baseThroughput, multiplier, effectiveThroughput, unit,
                    users, avgResponseTime, thinkTime, isAdaptiveMode());

            return pacing;

        } catch (Exception e) {
            log.warn("Smart Pacing Timer: Error calculating pacing, using 1000ms default. Error: {}",
                    e.getMessage());
            return 1000;
        }
    }

    // --- Property Getters and Setters (with variable resolution) ---

    public double getTargetThroughput() {
        return resolveDouble(TARGET_THROUGHPUT, "1.0");
    }

    public String getTargetThroughputString() {
        return getPropertyAsString(TARGET_THROUGHPUT, "1.0");
    }

    public void setTargetThroughput(String throughput) {
        setProperty(TARGET_THROUGHPUT, throughput);
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

    public double getLoadMultiplier() {
        return resolveDouble(LOAD_MULTIPLIER, "1.0");
    }

    public String getLoadMultiplierString() {
        return getPropertyAsString(LOAD_MULTIPLIER, "1.0");
    }

    public void setLoadMultiplier(String multiplier) {
        setProperty(LOAD_MULTIPLIER, multiplier);
    }

    public double getSteadyStateDuration() {
        return resolveDouble(STEADY_STATE_DURATION, "60");
    }

    public String getSteadyStateDurationString() {
        return getPropertyAsString(STEADY_STATE_DURATION, "60");
    }

    public void setSteadyStateDuration(String minutes) {
        setProperty(STEADY_STATE_DURATION, minutes);
    }

    public double getRampUpTime() {
        return resolveDouble(RAMP_UP_TIME, "0");
    }

    public String getRampUpTimeString() {
        return getPropertyAsString(RAMP_UP_TIME, "0");
    }

    public void setRampUpTime(String minutes) {
        setProperty(RAMP_UP_TIME, minutes);
    }

    public int getNumberOfUsers() {
        return resolveInt(NUMBER_OF_USERS, "1");
    }

    public String getNumberOfUsersString() {
        return getPropertyAsString(NUMBER_OF_USERS, "1");
    }

    public void setNumberOfUsers(String users) {
        setProperty(NUMBER_OF_USERS, users);
    }

    public boolean isAutoDetectUsers() {
        return getPropertyAsBoolean(AUTO_DETECT_USERS, false);
    }

    public void setAutoDetectUsers(boolean autoDetect) {
        setProperty(AUTO_DETECT_USERS, autoDetect);
    }

    public long getThinkTime() {
        return resolveLong(THINK_TIME, "0");
    }

    public String getThinkTimeString() {
        return getPropertyAsString(THINK_TIME, "0");
    }

    public void setThinkTime(String thinkTimeMs) {
        setProperty(THINK_TIME, thinkTimeMs);
    }

    public long getAvgResponseTime() {
        return resolveLong(AVG_RESPONSE_TIME, "0");
    }

    public String getAvgResponseTimeString() {
        return getPropertyAsString(AVG_RESPONSE_TIME, "0");
    }

    public void setAvgResponseTime(String avgResponseTimeMs) {
        setProperty(AVG_RESPONSE_TIME, avgResponseTimeMs);
    }

    public boolean isUseEndToEnd() {
        return getPropertyAsBoolean(USE_END_TO_END, false);
    }

    public void setUseEndToEnd(boolean useEndToEnd) {
        setProperty(USE_END_TO_END, useEndToEnd);
    }

    public long getEndToEndResponseTime() {
        return resolveLong(END_TO_END_RESPONSE_TIME, "0");
    }

    public String getEndToEndResponseTimeString() {
        return getPropertyAsString(END_TO_END_RESPONSE_TIME, "0");
    }

    public void setEndToEndResponseTime(String endToEndMs) {
        setProperty(END_TO_END_RESPONSE_TIME, endToEndMs);
    }

    public double getRandomizationPercent() {
        return resolveDouble(RANDOMIZATION_PERCENT, "0.0");
    }

    public String getRandomizationPercentString() {
        return getPropertyAsString(RANDOMIZATION_PERCENT, "0.0");
    }

    public void setRandomizationPercent(String percent) {
        setProperty(RANDOMIZATION_PERCENT, percent);
    }

    // --- Adaptive Pacing Properties ---

    public boolean isAdaptiveMode() {
        return getPropertyAsBoolean(ADAPTIVE_MODE, false);
    }

    public void setAdaptiveMode(boolean adaptive) {
        setProperty(ADAPTIVE_MODE, adaptive);
    }

    public int getAdaptiveWindowSeconds() {
        return resolveInt(ADAPTIVE_WINDOW_SECONDS, "10");
    }

    public String getAdaptiveWindowSecondsString() {
        return getPropertyAsString(ADAPTIVE_WINDOW_SECONDS, "10");
    }

    public void setAdaptiveWindowSeconds(String seconds) {
        setProperty(ADAPTIVE_WINDOW_SECONDS, seconds);
    }

    public double getAdaptiveDampening() {
        return resolveDouble(ADAPTIVE_DAMPENING, "0.3");
    }

    public String getAdaptiveDampeningString() {
        return getPropertyAsString(ADAPTIVE_DAMPENING, "0.3");
    }

    public void setAdaptiveDampening(String dampening) {
        setProperty(ADAPTIVE_DAMPENING, dampening);
    }

    // --- First Iteration Skip ---

    public boolean isSkipFirstIteration() {
        return getPropertyAsBoolean(SKIP_FIRST_ITERATION, false);
    }

    public void setSkipFirstIteration(boolean skip) {
        setProperty(SKIP_FIRST_ITERATION, skip);
    }

    // --- Internal Helpers ---

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
        // Reset first-iteration tracking for re-runs
        isFirstIteration.set(Boolean.TRUE);

        double effective = PacingCalculator.applyMultiplier(getTargetThroughput(), getLoadMultiplier());
        log.info("Smart Pacing Timer '{}' started. Base: {} {}, Multiplier: {}x, Effective: {} {}, Users: {}, Duration: {} min, Adaptive: {}",
                getName(), getTargetThroughputString(), getThroughputUnit(),
                getLoadMultiplierString(), effective, getThroughputUnit(),
                isAutoDetectUsers() ? "auto-detect" : getNumberOfUsersString(),
                getSteadyStateDurationString(), isAdaptiveMode());

        if (isAdaptiveMode()) {
            AdaptivePacingController.getInstance(getName()).reset();
            log.info("Smart Pacing Timer '{}': Adaptive mode enabled (window={}s, dampening={})",
                    getName(), getAdaptiveWindowSeconds(), getAdaptiveDampening());
        }
    }

    @Override
    public void testStarted(String host) {
        testStarted();
    }

    @Override
    public void testEnded() {
        if (isAdaptiveMode()) {
            AdaptivePacingController controller = AdaptivePacingController.getInstance(getName());
            log.info("Smart Pacing Timer '{}' ended. Adaptive stats: total completions={}, final actual TPS={:.2f}",
                    getName(), controller.getTotalCompletions(),
                    controller.getActualTPS(getAdaptiveWindowSeconds()));
            AdaptivePacingController.removeInstance(getName());
        }
        log.info("Smart Pacing Timer '{}' ended.", getName());
    }

    @Override
    public void testEnded(String host) {
        testEnded();
    }
}
