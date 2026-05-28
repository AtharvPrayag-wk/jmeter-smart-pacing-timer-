package com.github.tharvprayag.jmeter.pacing;

import org.apache.jmeter.gui.util.VerticalPanel;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.timers.gui.AbstractTimerGui;

import javax.swing.*;
import java.awt.*;

/**
 * GUI class for the Smart Pacing Timer.
 * Extends AbstractTimerGui which provides the standard JMeter timer panel structure.
 *
 * This is separate from the Timer logic (PacingCalculatorTimer) per JMeter's MVC pattern:
 * - GUI class handles display and user input
 * - TestElement class handles the actual timer logic
 */
public class PacingCalculatorTimerGui extends AbstractTimerGui {

    // GUI Components
    private JTextField targetThroughputField;
    private JComboBox<String> throughputUnitCombo;
    private JTextField numberOfUsersField;
    private JCheckBox autoDetectUsersCheckbox;
    private JTextField thinkTimeField;
    private JTextField avgResponseTimeField;
    private JTextField randomizationField;

    // Calculated pacing display (read-only, shows user what pacing will be)
    private JLabel calculatedPacingLabel;

    public PacingCalculatorTimerGui() {
        init();
    }

    /**
     * Initialize and lay out the GUI components.
     */
    private void init() {
        setLayout(new BorderLayout(0, 5));
        setBorder(makeBorder());

        // Top panel: standard JMeter title panel (name field, etc.)
        add(makeTitlePanel(), BorderLayout.NORTH);

        // Center panel: our custom configuration fields
        JPanel configPanel = createConfigPanel();
        add(configPanel, BorderLayout.CENTER);
    }

    /**
     * Create the main configuration panel with all input fields.
     */
    private JPanel createConfigPanel() {
        VerticalPanel mainPanel = new VerticalPanel();

        // --- Throughput Configuration ---
        JPanel throughputPanel = new JPanel(new GridBagLayout());
        throughputPanel.setBorder(BorderFactory.createTitledBorder("Throughput Target"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        // Target Throughput
        gbc.gridx = 0; gbc.gridy = 0;
        throughputPanel.add(new JLabel("Target Throughput:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        targetThroughputField = new JTextField(10);
        targetThroughputField.setToolTipText("Enter the desired throughput value (e.g., 10 for 10 TPS)");
        throughputPanel.add(targetThroughputField, gbc);

        // Throughput Unit
        gbc.gridx = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        throughputUnitCombo = new JComboBox<>(new String[]{"TPS", "TPM", "TPH"});
        throughputUnitCombo.setToolTipText("TPS=per second, TPM=per minute, TPH=per hour");
        throughputPanel.add(throughputUnitCombo, gbc);

        mainPanel.add(throughputPanel);

        // --- User Configuration ---
        JPanel usersPanel = new JPanel(new GridBagLayout());
        usersPanel.setBorder(BorderFactory.createTitledBorder("Virtual Users"));
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        // Number of Users
        gbc.gridx = 0; gbc.gridy = 0;
        usersPanel.add(new JLabel("Number of VUsers:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        numberOfUsersField = new JTextField(10);
        numberOfUsersField.setToolTipText("Total number of virtual users (threads) in this thread group");
        usersPanel.add(numberOfUsersField, gbc);

        // Auto-detect checkbox
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        autoDetectUsersCheckbox = new JCheckBox("Auto-detect from Thread Group (runtime)");
        autoDetectUsersCheckbox.setToolTipText("If checked, number of users is detected at runtime from active threads");
        autoDetectUsersCheckbox.addActionListener(e ->
                numberOfUsersField.setEnabled(!autoDetectUsersCheckbox.isSelected()));
        usersPanel.add(autoDetectUsersCheckbox, gbc);

        mainPanel.add(usersPanel);

        // --- Timing Adjustments ---
        JPanel timingPanel = new JPanel(new GridBagLayout());
        timingPanel.setBorder(BorderFactory.createTitledBorder("Timing Adjustments (Optional)"));
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        // Think Time
        gbc.gridx = 0; gbc.gridy = 0;
        timingPanel.add(new JLabel("Think Time (ms):"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        thinkTimeField = new JTextField(10);
        thinkTimeField.setToolTipText("Think time already included in the script (ms). Will be subtracted from pacing.");
        timingPanel.add(thinkTimeField, gbc);

        // Average Response Time
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        timingPanel.add(new JLabel("Avg Response Time (ms):"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        avgResponseTimeField = new JTextField(10);
        avgResponseTimeField.setToolTipText("Expected average response time (ms). Will be subtracted from pacing.");
        timingPanel.add(avgResponseTimeField, gbc);

        // Randomization
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        timingPanel.add(new JLabel("Randomization (%):"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        randomizationField = new JTextField(10);
        randomizationField.setToolTipText("Add ±X% randomization to pacing to avoid thundering herd effect (0 = no randomization)");
        timingPanel.add(randomizationField, gbc);

        mainPanel.add(timingPanel);

        // --- Calculated Pacing Display ---
        JPanel resultPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        resultPanel.setBorder(BorderFactory.createTitledBorder("Calculated Pacing"));
        calculatedPacingLabel = new JLabel("Configure values above to see calculated pacing");
        calculatedPacingLabel.setFont(calculatedPacingLabel.getFont().deriveFont(Font.BOLD, 13f));
        resultPanel.add(calculatedPacingLabel);

        // Add a "Calculate" button to preview pacing
        JButton calculateButton = new JButton("Preview Pacing");
        calculateButton.addActionListener(e -> updateCalculatedPacing());
        resultPanel.add(calculateButton);

        mainPanel.add(resultPanel);

        return mainPanel;
    }

    /**
     * Update the calculated pacing label based on current field values.
     */
    private void updateCalculatedPacing() {
        try {
            double throughput = Double.parseDouble(targetThroughputField.getText().trim());
            String unitStr = (String) throughputUnitCombo.getSelectedItem();
            PacingCalculator.ThroughputUnit unit = PacingCalculator.ThroughputUnit.valueOf(unitStr);
            int users = Integer.parseInt(numberOfUsersField.getText().trim());

            long thinkTime = 0;
            if (!thinkTimeField.getText().trim().isEmpty()) {
                thinkTime = Long.parseLong(thinkTimeField.getText().trim());
            }

            long avgRT = 0;
            if (!avgResponseTimeField.getText().trim().isEmpty()) {
                avgRT = Long.parseLong(avgResponseTimeField.getText().trim());
            }

            long pacing = PacingCalculator.calculatePacing(throughput, unit, users, avgRT, thinkTime);
            calculatedPacingLabel.setText(String.format(
                    "Pacing = %d ms (%.2f seconds) | Effective TPS = %.2f",
                    pacing, pacing / 1000.0,
                    PacingCalculator.calculateAchievableThroughput(users, pacing > 0 ? pacing : 1, PacingCalculator.ThroughputUnit.TPS)));
        } catch (NumberFormatException ex) {
            calculatedPacingLabel.setText("Error: Please enter valid numeric values");
        } catch (IllegalArgumentException ex) {
            calculatedPacingLabel.setText("Error: " + ex.getMessage());
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

    /**
     * Transfer data from GUI fields into the TestElement (timer).
     * Called by JMeter when saving or running.
     */
    @Override
    public void modifyTestElement(TestElement element) {
        super.configureTestElement(element);

        if (element instanceof PacingCalculatorTimer) {
            PacingCalculatorTimer timer = (PacingCalculatorTimer) element;

            // Target throughput
            try {
                timer.setTargetThroughput(Double.parseDouble(targetThroughputField.getText().trim()));
            } catch (NumberFormatException e) {
                timer.setTargetThroughput(1.0);
            }

            // Throughput unit
            String unitStr = (String) throughputUnitCombo.getSelectedItem();
            timer.setThroughputUnit(PacingCalculator.ThroughputUnit.valueOf(unitStr));

            // Number of users
            try {
                timer.setNumberOfUsers(Integer.parseInt(numberOfUsersField.getText().trim()));
            } catch (NumberFormatException e) {
                timer.setNumberOfUsers(1);
            }

            // Auto-detect
            timer.setAutoDetectUsers(autoDetectUsersCheckbox.isSelected());

            // Think time
            try {
                String tt = thinkTimeField.getText().trim();
                timer.setThinkTime(tt.isEmpty() ? 0 : Long.parseLong(tt));
            } catch (NumberFormatException e) {
                timer.setThinkTime(0);
            }

            // Average response time
            try {
                String rt = avgResponseTimeField.getText().trim();
                timer.setAvgResponseTime(rt.isEmpty() ? 0 : Long.parseLong(rt));
            } catch (NumberFormatException e) {
                timer.setAvgResponseTime(0);
            }

            // Randomization
            try {
                String rnd = randomizationField.getText().trim();
                timer.setRandomizationPercent(rnd.isEmpty() ? 0 : Double.parseDouble(rnd));
            } catch (NumberFormatException e) {
                timer.setRandomizationPercent(0);
            }
        }
    }

    /**
     * Transfer data from the TestElement into the GUI fields.
     * Called by JMeter when a user selects this timer in the test plan tree.
     */
    @Override
    public void configure(TestElement element) {
        super.configure(element);

        if (element instanceof PacingCalculatorTimer) {
            PacingCalculatorTimer timer = (PacingCalculatorTimer) element;

            targetThroughputField.setText(String.valueOf(timer.getTargetThroughput()));
            throughputUnitCombo.setSelectedItem(timer.getThroughputUnit().name());
            numberOfUsersField.setText(String.valueOf(timer.getNumberOfUsers()));
            autoDetectUsersCheckbox.setSelected(timer.isAutoDetectUsers());
            numberOfUsersField.setEnabled(!timer.isAutoDetectUsers());

            long tt = timer.getThinkTime();
            thinkTimeField.setText(tt > 0 ? String.valueOf(tt) : "");

            long rt = timer.getAvgResponseTime();
            avgResponseTimeField.setText(rt > 0 ? String.valueOf(rt) : "");

            double rnd = timer.getRandomizationPercent();
            randomizationField.setText(rnd > 0 ? String.valueOf(rnd) : "");

            updateCalculatedPacing();
        }
    }

    /**
     * Reset GUI to default state.
     */
    @Override
    public void clearGui() {
        super.clearGui();
        targetThroughputField.setText("1.0");
        throughputUnitCombo.setSelectedIndex(0);
        numberOfUsersField.setText("1");
        autoDetectUsersCheckbox.setSelected(false);
        numberOfUsersField.setEnabled(true);
        thinkTimeField.setText("");
        avgResponseTimeField.setText("");
        randomizationField.setText("");
        calculatedPacingLabel.setText("Configure values above to see calculated pacing");
    }
}
