package com.github.tharvprayag.jmeter.pacing;

import org.apache.jmeter.gui.util.VerticalPanel;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.timers.gui.AbstractTimerGui;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * GUI class for the Smart Pacing Timer.
 * Extends AbstractTimerGui which provides the standard JMeter timer panel structure.
 *
 * Features:
 * - Load multiplier (1x, 2x, 0.5x, etc.)
 * - Steady state duration for total transaction calculation
 * - Auto-suggest ideal user count
 * - Display iterations per user, total transactions, effective rate
 * - Preview all calculations before running
 */
public class PacingCalculatorTimerGui extends AbstractTimerGui {

    // --- Throughput Configuration ---
    private JTextField targetThroughputField;
    private JComboBox<String> throughputUnitCombo;

    // --- Load Profile ---
    private JTextField loadMultiplierField;
    private JTextField steadyStateDurationField;
    private JTextField rampUpTimeField;

    // --- User Configuration ---
    private JTextField numberOfUsersField;
    private JCheckBox autoDetectUsersCheckbox;
    private JButton suggestUsersButton;

    // --- Timing Adjustments ---
    private JTextField thinkTimeField;
    private JTextField avgResponseTimeField;
    private JTextField endToEndResponseTimeField;
    private JCheckBox useEndToEndCheckbox;
    private JCheckBox skipFirstIterationCheckbox;
    private JTextField randomizationField;

    // --- Adaptive Pacing ---
    private JCheckBox adaptiveModeCheckbox;
    private JTextField adaptiveWindowField;
    private JTextField adaptiveDampeningField;

    // --- Calculated Results Display ---
    private JLabel effectiveRateLabel;
    private JLabel suggestedUsersLabel;
    private JLabel pacingLabel;
    private JLabel iterationsPerUserLabel;
    private JLabel totalTransactionsLabel;

    public PacingCalculatorTimerGui() {
        init();
    }

    private void init() {
        setLayout(new BorderLayout(0, 5));
        setBorder(makeBorder());

        add(makeTitlePanel(), BorderLayout.NORTH);

        JPanel mainPanel = new JPanel(new BorderLayout(0, 5));
        mainPanel.add(createInputPanel(), BorderLayout.NORTH);
        mainPanel.add(createResultsPanel(), BorderLayout.CENTER);

        add(mainPanel, BorderLayout.CENTER);
    }

    /**
     * Create the input configuration panels (throughput, load profile, users, timing).
     */
    private JPanel createInputPanel() {
        VerticalPanel inputPanel = new VerticalPanel();

        // Row 1: Throughput + Load Profile side by side
        JPanel topRow = new JPanel(new GridLayout(1, 2, 10, 0));
        topRow.add(createThroughputPanel());
        topRow.add(createLoadProfilePanel());
        inputPanel.add(topRow);

        // Row 2: Users + Timing side by side
        JPanel bottomRow = new JPanel(new GridLayout(1, 2, 10, 0));
        bottomRow.add(createUsersPanel());
        bottomRow.add(createTimingPanel());
        inputPanel.add(bottomRow);

        // Row 3: Adaptive Pacing
        inputPanel.add(createAdaptivePanel());

        return inputPanel;
    }

    private JPanel createThroughputPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Throughput Target (1x Baseline)"));
        GridBagConstraints gbc = createGbc();

        // Target Throughput
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Base Throughput:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        targetThroughputField = new JTextField(8);
        targetThroughputField.setToolTipText("Baseline throughput for 1x load (e.g., 120 for 120 TPH at 1x)");
        panel.add(targetThroughputField, gbc);

        // Unit
        gbc.gridx = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        throughputUnitCombo = new JComboBox<>(new String[]{"TPS", "TPM", "TPH"});
        throughputUnitCombo.setToolTipText("TPS=per second, TPM=per minute, TPH=per hour");
        panel.add(throughputUnitCombo, gbc);

        return panel;
    }

    private JPanel createLoadProfilePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Load Profile"));
        GridBagConstraints gbc = createGbc();

        // Load Multiplier
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Load Multiplier (x):"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        loadMultiplierField = new JTextField(6);
        loadMultiplierField.setText("1.0");
        loadMultiplierField.setToolTipText("1.0 = baseline, 2.0 = double load, 0.5 = half load");
        panel.add(loadMultiplierField, gbc);

        // Steady State Duration
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Steady State (min):"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        steadyStateDurationField = new JTextField(6);
        steadyStateDurationField.setText("60");
        steadyStateDurationField.setToolTipText("Duration of steady state in minutes");
        panel.add(steadyStateDurationField, gbc);

        // Ramp-up
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Ramp-up (min):"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        rampUpTimeField = new JTextField(6);
        rampUpTimeField.setText("5");
        rampUpTimeField.setToolTipText("Ramp-up time in minutes (informational, does not affect pacing calculation)");
        panel.add(rampUpTimeField, gbc);

        return panel;
    }

    private JPanel createUsersPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Virtual Users"));
        GridBagConstraints gbc = createGbc();

        // Number of Users
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Number of VUsers:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        numberOfUsersField = new JTextField(6);
        numberOfUsersField.setToolTipText("Number of virtual users. Use 'Suggest' to auto-calculate ideal count.");
        panel.add(numberOfUsersField, gbc);

        // Suggest button
        gbc.gridx = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        suggestUsersButton = new JButton("Suggest");
        suggestUsersButton.setToolTipText("Calculate ideal user count based on throughput and response time");
        suggestUsersButton.addActionListener(e -> suggestUsers());
        panel.add(suggestUsersButton, gbc);

        // Auto-detect checkbox
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 3;
        autoDetectUsersCheckbox = new JCheckBox("Auto-detect from Thread Group (runtime)");
        autoDetectUsersCheckbox.addActionListener(e -> {
            boolean auto = autoDetectUsersCheckbox.isSelected();
            numberOfUsersField.setEnabled(!auto);
            suggestUsersButton.setEnabled(!auto);
        });
        panel.add(autoDetectUsersCheckbox, gbc);

        // Suggested users info
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 3;
        suggestedUsersLabel = new JLabel(" ");
        suggestedUsersLabel.setFont(suggestedUsersLabel.getFont().deriveFont(Font.ITALIC, 11f));
        suggestedUsersLabel.setForeground(new Color(0, 100, 0));
        panel.add(suggestedUsersLabel, gbc);

        return panel;
    }

    private JPanel createTimingPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Iteration Timing"));
        GridBagConstraints gbc = createGbc();

        // End-to-End checkbox (toggle)
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        useEndToEndCheckbox = new JCheckBox("Use End-to-End Response Time (includes think time)");
        useEndToEndCheckbox.setToolTipText("Check this if you have a single value for the entire iteration time (RT + Think Time combined)");
        useEndToEndCheckbox.addActionListener(e -> toggleTimingMode());
        panel.add(useEndToEndCheckbox, gbc);

        // End-to-End Response Time (shown when checkbox is checked)
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("End-to-End Time (ms):"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        endToEndResponseTimeField = new JTextField(8);
        endToEndResponseTimeField.setText("0");
        endToEndResponseTimeField.setEnabled(false);
        endToEndResponseTimeField.setToolTipText("Total time for 1 user to complete 1 full iteration including think time (ms)");
        panel.add(endToEndResponseTimeField, gbc);

        // Average Response Time (1 user 1 iteration)
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Avg Response Time - 1 user 1 iteration (ms):"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        avgResponseTimeField = new JTextField(8);
        avgResponseTimeField.setText("0");
        avgResponseTimeField.setToolTipText("Average response time for 1 user completing 1 full iteration (all requests combined) in ms");
        panel.add(avgResponseTimeField, gbc);

        // Total Think Time
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Total Think Time (ms):"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        thinkTimeField = new JTextField(8);
        thinkTimeField.setText("0");
        thinkTimeField.setToolTipText("Total think time within 1 iteration (sum of all think times in the script) in ms");
        panel.add(thinkTimeField, gbc);

        // Randomization
        gbc.gridx = 0; gbc.gridy = 4; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Randomization (%):"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        randomizationField = new JTextField(8);
        randomizationField.setText("0");
        randomizationField.setToolTipText("Adds random variance to pacing (e.g., 10 = pacing varies +-10%). Prevents all users hitting server at same instant.");
        panel.add(randomizationField, gbc);

        // Skip first iteration
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        skipFirstIterationCheckbox = new JCheckBox("Skip pacing on first iteration (all users start immediately)");
        skipFirstIterationCheckbox.setSelected(false);
        skipFirstIterationCheckbox.setToolTipText("When checked, pacing is not applied on the first iteration — users start immediately. Leave unchecked for strict throughput accuracy.");
        panel.add(skipFirstIterationCheckbox, gbc);

        return panel;
    }

    /**
     * Toggle between End-to-End mode and individual RT + Think Time mode.
     * When E2E is checked: disable individual fields, enable E2E field.
     * When unchecked: enable individual fields, disable E2E field.
     */
    private void toggleTimingMode() {
        boolean useE2E = useEndToEndCheckbox.isSelected();
        endToEndResponseTimeField.setEnabled(useE2E);
        avgResponseTimeField.setEnabled(!useE2E);
        thinkTimeField.setEnabled(!useE2E);
    }

    private JPanel createAdaptivePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(180, 100, 0)),
                "Adaptive Pacing (Runtime Auto-Adjust)",
                TitledBorder.LEFT, TitledBorder.TOP,
                panel.getFont().deriveFont(Font.BOLD),
                new Color(180, 100, 0)));
        GridBagConstraints gbc = createGbc();

        // Enable checkbox
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 4;
        adaptiveModeCheckbox = new JCheckBox("Enable Adaptive Pacing");
        adaptiveModeCheckbox.setToolTipText(
                "When enabled, pacing auto-adjusts at runtime to match target throughput. " +
                "If response times increase, pacing shrinks to compensate. " +
                "The static calculation becomes the starting point.");
        adaptiveModeCheckbox.addActionListener(e -> {
            boolean adaptive = adaptiveModeCheckbox.isSelected();
            adaptiveWindowField.setEnabled(adaptive);
            adaptiveDampeningField.setEnabled(adaptive);
        });
        panel.add(adaptiveModeCheckbox, gbc);

        // Window
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Monitoring Window (sec):"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 0.5;
        adaptiveWindowField = new JTextField(5);
        adaptiveWindowField.setText("10");
        adaptiveWindowField.setEnabled(false);
        adaptiveWindowField.setToolTipText("Time window (seconds) to measure actual throughput. Larger = smoother but slower to react.");
        panel.add(adaptiveWindowField, gbc);

        // Dampening
        gbc.gridx = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Dampening (0.1-1.0):"), gbc);
        gbc.gridx = 3; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 0.5;
        adaptiveDampeningField = new JTextField(5);
        adaptiveDampeningField.setText("0.3");
        adaptiveDampeningField.setEnabled(false);
        adaptiveDampeningField.setToolTipText("How aggressively to adjust. 0.1 = gentle, 1.0 = aggressive. Recommended: 0.3");
        panel.add(adaptiveDampeningField, gbc);

        return panel;
    }

    /**
     * Create the results/output panel showing all calculated values.
     */
    private JPanel createResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(50, 100, 150), 2),
                " Calculation Results ",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 12),
                new Color(50, 100, 150)));

        // Results grid
        JPanel resultsGrid = new JPanel(new GridBagLayout());
        resultsGrid.setBackground(new Color(245, 248, 252));
        resultsGrid.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;

        Font labelFont = new Font("SansSerif", Font.PLAIN, 12);
        Font valueFont = new Font("SansSerif", Font.BOLD, 13);

        // Effective Rate
        gbc.gridx = 0; gbc.gridy = 0;
        JLabel l1 = new JLabel("Effective Rate:");
        l1.setFont(labelFont);
        resultsGrid.add(l1, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        effectiveRateLabel = new JLabel("-");
        effectiveRateLabel.setFont(valueFont);
        resultsGrid.add(effectiveRateLabel, gbc);

        // Pacing
        gbc.gridx = 2; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        JLabel l2 = new JLabel("Pacing:");
        l2.setFont(labelFont);
        resultsGrid.add(l2, gbc);
        gbc.gridx = 3; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        pacingLabel = new JLabel("-");
        pacingLabel.setFont(valueFont);
        pacingLabel.setForeground(new Color(0, 100, 0));
        resultsGrid.add(pacingLabel, gbc);

        // Total Transactions
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        JLabel l3 = new JLabel("Total Transactions:");
        l3.setFont(labelFont);
        resultsGrid.add(l3, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        totalTransactionsLabel = new JLabel("-");
        totalTransactionsLabel.setFont(valueFont);
        resultsGrid.add(totalTransactionsLabel, gbc);

        // Iterations per User
        gbc.gridx = 2; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        JLabel l4 = new JLabel("Iterations/User:");
        l4.setFont(labelFont);
        resultsGrid.add(l4, gbc);
        gbc.gridx = 3; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        iterationsPerUserLabel = new JLabel("-");
        iterationsPerUserLabel.setFont(valueFont);
        resultsGrid.add(iterationsPerUserLabel, gbc);

        panel.add(resultsGrid, BorderLayout.CENTER);

        // Calculate button
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton calculateButton = new JButton("Calculate All");
        calculateButton.setFont(new Font("SansSerif", Font.BOLD, 12));
        calculateButton.addActionListener(e -> recalculateAll());
        buttonPanel.add(calculateButton);

        JButton suggestAndCalcButton = new JButton("Suggest Users & Calculate");
        suggestAndCalcButton.setToolTipText("Auto-suggest ideal users, then calculate pacing");
        suggestAndCalcButton.addActionListener(e -> {
            suggestUsers();
            recalculateAll();
        });
        buttonPanel.add(suggestAndCalcButton);

        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    // --- Calculation Logic ---

    private void suggestUsers() {
        try {
            double baseThroughput = parseDouble(targetThroughputField, 1.0);
            double multiplier = parseDouble(loadMultiplierField, 1.0);
            double effectiveThroughput = PacingCalculator.applyMultiplier(baseThroughput, multiplier);
            String unitStr = (String) throughputUnitCombo.getSelectedItem();
            PacingCalculator.ThroughputUnit unit = PacingCalculator.ThroughputUnit.valueOf(unitStr);

            long avgRT;
            long thinkTime;
            if (useEndToEndCheckbox.isSelected()) {
                // E2E mode: end-to-end time = RT + TT combined
                avgRT = parseLong(endToEndResponseTimeField, 0);
                thinkTime = 0;
            } else {
                avgRT = parseLong(avgResponseTimeField, 0);
                thinkTime = parseLong(thinkTimeField, 0);
            }

            int suggested = PacingCalculator.calculateSuggestedUsers(effectiveThroughput, unit, avgRT, thinkTime);

            numberOfUsersField.setText(String.valueOf(suggested));
            suggestedUsersLabel.setText("Suggested: " + suggested + " users (based on effective rate & iteration time)");
        } catch (Exception ex) {
            suggestedUsersLabel.setText("Cannot suggest: " + ex.getMessage());
        }
    }

    /**
     * Get the effective response time and think time based on the current mode.
     * In E2E mode: RT = end-to-end value, TT = 0 (already included).
     * In individual mode: RT and TT from their respective fields.
     */
    private long getEffectiveAvgRT() {
        if (useEndToEndCheckbox.isSelected()) {
            return parseLong(endToEndResponseTimeField, 0);
        }
        return parseLong(avgResponseTimeField, 0);
    }

    private long getEffectiveThinkTime() {
        if (useEndToEndCheckbox.isSelected()) {
            return 0; // already included in E2E
        }
        return parseLong(thinkTimeField, 0);
    }

    private void recalculateAll() {
        try {
            double baseThroughput = parseDouble(targetThroughputField, -1);
            double multiplier = parseDouble(loadMultiplierField, -1);

            // If fields contain variables, show info instead of trying to calculate
            if (baseThroughput < 0 || multiplier < 0) {
                effectiveRateLabel.setText("Contains variable — will resolve at runtime");
                pacingLabel.setText("Contains variable — will resolve at runtime");
                totalTransactionsLabel.setText("-");
                iterationsPerUserLabel.setText("-");
                return;
            }

            double effectiveThroughput = PacingCalculator.applyMultiplier(baseThroughput, multiplier);
            String unitStr = (String) throughputUnitCombo.getSelectedItem();
            PacingCalculator.ThroughputUnit unit = PacingCalculator.ThroughputUnit.valueOf(unitStr);
            int users = parseInt(numberOfUsersField, -1);
            if (users < 0) {
                effectiveRateLabel.setText(String.format("%.2f %s [%.1fx]", effectiveThroughput, unitStr, multiplier));
                pacingLabel.setText("Users field contains variable — will resolve at runtime");
                totalTransactionsLabel.setText("-");
                iterationsPerUserLabel.setText("-");
                return;
            }

            long avgRT = getEffectiveAvgRT();
            long thinkTime = getEffectiveThinkTime();
            double steadyStateMins = parseDouble(steadyStateDurationField, 60);

            // Effective rate display
            double effectiveTPS = PacingCalculator.convertToTPS(effectiveThroughput, unit);
            effectiveRateLabel.setText(String.format("%.2f %s (%.4f TPS) [%.1fx]",
                    effectiveThroughput, unitStr, effectiveTPS, multiplier));

            // Pacing
            long pacing = PacingCalculator.calculatePacing(effectiveThroughput, unit, users, avgRT, thinkTime);
            if (pacing > 60000) {
                pacingLabel.setText(String.format("%,d ms (%.1f sec = %.2f min)",
                        pacing, pacing / 1000.0, pacing / 60000.0));
            } else {
                pacingLabel.setText(String.format("%,d ms (%.2f sec)", pacing, pacing / 1000.0));
            }

            // Adaptive mode indicator
            if (adaptiveModeCheckbox.isSelected()) {
                pacingLabel.setText(pacingLabel.getText() + " [Adaptive: will auto-adjust at runtime]");
            }

            // Total transactions in steady state
            long totalTx = PacingCalculator.calculateTotalTransactions(effectiveThroughput, unit, steadyStateMins);
            totalTransactionsLabel.setText(String.format("%,d transactions in %.0f min", totalTx, steadyStateMins));

            // Iterations per user
            long itersPerUser = PacingCalculator.calculateIterationsPerUser(steadyStateMins, pacing, avgRT, thinkTime);
            iterationsPerUserLabel.setText(String.format("%,d iterations", itersPerUser));

        } catch (Exception ex) {
            pacingLabel.setText("Error: " + ex.getMessage());
            effectiveRateLabel.setText("-");
            totalTransactionsLabel.setText("-");
            iterationsPerUserLabel.setText("-");
        }
    }

    // --- JMeter GUI Contract Methods ---

    @Override
    public String getLabelResource() {
        return "smart_pacing_timer_title";
    }

    @Override
    public String getStaticLabel() {
        return "Smart Pacing Timer";
    }

    @Override
    public TestElement makeTestElement() {
        PacingCalculatorTimer timer = new PacingCalculatorTimer();
        modifyTestElement(timer);
        return timer;
    }

    @Override
    public TestElement createTestElement() {
        PacingCalculatorTimer timer = new PacingCalculatorTimer();
        modifyTestElement(timer);
        return timer;
    }

    @Override
    public void modifyTestElement(TestElement element) {
        super.configureTestElement(element);

        if (element instanceof PacingCalculatorTimer) {
            PacingCalculatorTimer timer = (PacingCalculatorTimer) element;

            // Store raw strings (supports JMeter variables like ${__P(targetTPS,100)})
            timer.setTargetThroughput(targetThroughputField.getText().trim());
            String unitStr = (String) throughputUnitCombo.getSelectedItem();
            timer.setThroughputUnit(PacingCalculator.ThroughputUnit.valueOf(unitStr));
            timer.setLoadMultiplier(loadMultiplierField.getText().trim());
            timer.setSteadyStateDuration(steadyStateDurationField.getText().trim());
            timer.setRampUpTime(rampUpTimeField.getText().trim());
            timer.setNumberOfUsers(numberOfUsersField.getText().trim());
            timer.setAutoDetectUsers(autoDetectUsersCheckbox.isSelected());
            timer.setUseEndToEnd(useEndToEndCheckbox.isSelected());
            timer.setEndToEndResponseTime(endToEndResponseTimeField.getText().trim());
            timer.setThinkTime(thinkTimeField.getText().trim());
            timer.setAvgResponseTime(avgResponseTimeField.getText().trim());
            timer.setRandomizationPercent(randomizationField.getText().trim());
            timer.setSkipFirstIteration(skipFirstIterationCheckbox.isSelected());

            // Adaptive pacing
            timer.setAdaptiveMode(adaptiveModeCheckbox.isSelected());
            timer.setAdaptiveWindowSeconds(adaptiveWindowField.getText().trim());
            timer.setAdaptiveDampening(adaptiveDampeningField.getText().trim());
        }
    }

    @Override
    public void configure(TestElement element) {
        super.configure(element);

        if (element instanceof PacingCalculatorTimer) {
            PacingCalculatorTimer timer = (PacingCalculatorTimer) element;

            // Load raw strings (preserves JMeter variables)
            targetThroughputField.setText(timer.getTargetThroughputString());
            throughputUnitCombo.setSelectedItem(timer.getThroughputUnit().name());
            loadMultiplierField.setText(timer.getLoadMultiplierString());
            steadyStateDurationField.setText(timer.getSteadyStateDurationString());
            rampUpTimeField.setText(timer.getRampUpTimeString());
            numberOfUsersField.setText(timer.getNumberOfUsersString());
            autoDetectUsersCheckbox.setSelected(timer.isAutoDetectUsers());
            numberOfUsersField.setEnabled(!timer.isAutoDetectUsers());
            suggestUsersButton.setEnabled(!timer.isAutoDetectUsers());

            useEndToEndCheckbox.setSelected(timer.isUseEndToEnd());
            endToEndResponseTimeField.setText(timer.getEndToEndResponseTimeString());
            toggleTimingMode();

            thinkTimeField.setText(timer.getThinkTimeString());
            avgResponseTimeField.setText(timer.getAvgResponseTimeString());
            randomizationField.setText(timer.getRandomizationPercentString());
            skipFirstIterationCheckbox.setSelected(timer.isSkipFirstIteration());

            // Adaptive pacing
            adaptiveModeCheckbox.setSelected(timer.isAdaptiveMode());
            adaptiveWindowField.setText(timer.getAdaptiveWindowSecondsString());
            adaptiveDampeningField.setText(timer.getAdaptiveDampeningString());
            adaptiveWindowField.setEnabled(timer.isAdaptiveMode());
            adaptiveDampeningField.setEnabled(timer.isAdaptiveMode());

            recalculateAll();
        }
    }

    @Override
    public void clearGui() {
        super.clearGui();
        targetThroughputField.setText("1.0");
        throughputUnitCombo.setSelectedIndex(0);
        loadMultiplierField.setText("1.0");
        steadyStateDurationField.setText("60");
        rampUpTimeField.setText("5");
        numberOfUsersField.setText("1");
        numberOfUsersField.setEnabled(true);
        suggestUsersButton.setEnabled(true);
        autoDetectUsersCheckbox.setSelected(false);
        useEndToEndCheckbox.setSelected(false);
        endToEndResponseTimeField.setText("0");
        endToEndResponseTimeField.setEnabled(false);
        thinkTimeField.setText("0");
        thinkTimeField.setEnabled(true);
        avgResponseTimeField.setText("0");
        avgResponseTimeField.setEnabled(true);
        randomizationField.setText("0");
        skipFirstIterationCheckbox.setSelected(false);
        adaptiveModeCheckbox.setSelected(false);
        adaptiveWindowField.setText("10");
        adaptiveWindowField.setEnabled(false);
        adaptiveDampeningField.setText("0.3");
        adaptiveDampeningField.setEnabled(false);
        suggestedUsersLabel.setText(" ");
        effectiveRateLabel.setText("-");
        pacingLabel.setText("-");
        totalTransactionsLabel.setText("-");
        iterationsPerUserLabel.setText("-");
    }

    // --- Utility methods ---

    private GridBagConstraints createGbc() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 4);
        gbc.anchor = GridBagConstraints.WEST;
        return gbc;
    }

    private double parseDouble(JTextField field, double defaultVal) {
        try {
            String text = field.getText().trim();
            return text.isEmpty() ? defaultVal : Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private int parseInt(JTextField field, int defaultVal) {
        try {
            String text = field.getText().trim();
            return text.isEmpty() ? defaultVal : Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private long parseLong(JTextField field, long defaultVal) {
        try {
            String text = field.getText().trim();
            return text.isEmpty() ? defaultVal : Long.parseLong(text);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
