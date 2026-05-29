# JMeter Smart Pacing Timer Plugin

A **smart, adaptive JMeter Timer plugin** that automatically calculates and applies pacing delays based on your target throughput. Supports static and adaptive modes, JMeter variables, load multipliers, and auto-user-suggestion.

---

## Features

| Feature | Description |
|---------|-------------|
| **Auto-calculate pacing** | From target throughput (TPS, TPM, or TPH) and user count |
| **Adaptive Pacing** | Runtime feedback loop that auto-adjusts pacing to hit target throughput |
| **Load Multiplier** | Scale throughput (0.5x, 1x, 2x, etc.) without changing base values |
| **JMeter Variables/Properties** | Use `${__P(targetTPS,100)}` in any field — resolved at runtime |
| **End-to-End mode** | Single combined RT+TT value when you have end-to-end iteration time |
| **Auto-suggest users** | Calculates ideal virtual user count for your target |
| **Auto-detect users** | Reads active thread count from thread group at runtime |
| **Randomization** | ±X% variance to avoid thundering herd effect |
| **Skip first iteration** | Option to skip pacing on first iteration (users start immediately) |
| **Live preview** | See calculated pacing, total transactions, and iterations/user before running |
| **Thread-safe** | Works correctly with multiple concurrent virtual users |
| **Azure Load Test compatible** | Upload JAR as custom plugin — works out of the box |

---

## How It Calculates — At a Glance

### Core Pacing Formula

```
Pacing (ms) = max(0, (VUsers / Target_TPS) × 1000 - Avg_Response_Time - Think_Time)
```

Where `Target_TPS` is derived from your input:
- If TPH: TPS = TPH / 3600
- If TPM: TPS = TPM / 60
- If TPS: used directly

### With Load Multiplier

```
Effective_Throughput = Base_Throughput × Load_Multiplier
```

The pacing formula uses `Effective_Throughput` instead of the base value.

### Suggested Users Formula

```
busy_time = avg_response_time + think_time
buffer = max(1000ms, busy_time × 0.2)
iteration_time = busy_time + buffer
suggested_users = ceil(target_TPS × iteration_time / 1000)
```

The 20% buffer ensures users aren't running at 100% capacity (leaves room for response time variance).

### Adaptive Pacing (Runtime)

When enabled, the timer monitors actual throughput and adjusts:

```
error = (actual_TPS - target_TPS) / target_TPS
adjustment = base_pacing × dampening × error
adjusted_pacing = base_pacing + adjustment
```

- Actual > Target → pacing increases (slow down)
- Actual < Target → pacing decreases (speed up)
- Clamped to [0, base_pacing × 3] to prevent runaway

---

## Installation

### Build from Source

```bash
# Prerequisites: Java 11+, Maven 3.6+
mvn clean package
```

### Deploy to JMeter

```bash
# Copy JAR to JMeter's lib/ext directory
cp target/jmeter-smart-pacing-timer-1.0.0-SNAPSHOT.jar /path/to/jmeter/lib/ext/
```

Restart JMeter. The **"Smart Pacing Timer"** appears under **Timers** menu.

---

## Steps to Use

### Step 1: Add the Timer

Right-click Thread Group → **Add → Timer → Smart Pacing Timer**

### Step 2: Configure Throughput Target

| Field | What to Enter |
|-------|---------------|
| **Base Throughput** | Your target (e.g., `200` for 200 TPH) |
| **Unit** | TPS, TPM, or TPH |
| **Load Multiplier** | `1.0` for baseline, `2.0` for double load |

### Step 3: Configure Timing

| Field | What to Enter |
|-------|---------------|
| **Avg Response Time** | Time for 1 user to complete 1 full iteration (all requests combined) in ms |
| **Total Think Time** | Sum of all think times in your script per iteration in ms |
| **OR: End-to-End Time** | Check the E2E box and enter a single combined value (RT + TT) in ms |

### Step 4: Configure Users

- Enter the number of VUsers manually, OR
- Click **"Suggest"** to auto-calculate ideal user count, OR
- Check **"Auto-detect from Thread Group"** to read active threads at runtime

### Step 5: Verify Calculations

Click **"Calculate All"** to see:
- **Effective Rate**: Your throughput after multiplier
- **Pacing**: Calculated delay in ms/sec/min
- **Total Transactions**: Expected transactions during steady state
- **Iterations/User**: How many times each user iterates

### Step 6 (Optional): Enable Adaptive Pacing

- Check **"Enable Adaptive Pacing"**
- Set **Monitoring Window**: 5-30 seconds (default: 10)
- Set **Dampening**: 0.1-1.0 (default: 0.3)

The timer will auto-adjust at runtime to hit your exact target throughput.

### Step 7: Run

Run your test. The timer applies the calculated pacing delay between iterations.

---

## Example Scenarios

| Scenario | Target | VUsers | Avg RT | Think Time | Calculated Pacing |
|----------|--------|--------|--------|------------|-------------------|
| Login flow | 10 TPS | 10 | 0ms | 0ms | 1,000ms (1 sec) |
| Search | 200 TPH | 16 | 34,000ms | 18,000ms | 236,000ms (3.93 min) |
| Checkout | 500 TPH | 50 | 2,000ms | 1,000ms | 357,000ms (5.95 min) |
| API stress | 100 TPS | 20 | 50ms | 0ms | 150ms |

---

## Using with Azure Load Testing

Azure Load Testing runs Apache JMeter in the cloud and supports custom plugin JARs.

### Step 1: Prepare Your Test Plan

1. Create your `.jmx` test plan locally with the Smart Pacing Timer configured
2. Verify it works locally in JMeter first

### Step 2: Upload to Azure Load Testing

1. Go to **Azure Portal** → **Azure Load Testing** → your test resource
2. Create or edit a test
3. Under **Test plan**, upload your `.jmx` file
4. Under **Additional files** (or "Load test files"), upload:
   ```
   jmeter-smart-pacing-timer-1.0.0-SNAPSHOT.jar
   ```
   Azure automatically places it in JMeter's `lib/ext/` on all test engines

### Step 3: Configure for Distributed Engines

If Azure scales across multiple engines (e.g., 2 engines, 8 users each = 16 total):

**Option A:** Use JMeter Properties (recommended)
```
# In your timer, set Base Throughput to:
${__P(targetTPH,200)}

# Then in Azure's "Environment variables" or "Parameters":
targetTPH = 100    (per engine: 200 total / 2 engines)
```

**Option B:** Use "Auto-detect from Thread Group"
- Check the auto-detect users checkbox
- Set throughput as **per-engine target** (total ÷ number of engines)
- Each engine adapts based on its actual thread count

### Step 4: Run and Monitor

1. Start the test in Azure Load Testing
2. Monitor throughput in Azure's built-in metrics dashboard
3. If adaptive mode is enabled, the timer will self-correct on each engine independently

### Important Notes for Azure

- **JMeter version**: Azure uses JMeter 5.4.3 – 5.6.3. This plugin is compatible.
- **Engine count**: Each engine runs independently. Divide your target by engine count unless using a per-engine property.
- **No GUI in cloud**: The timer calculates at runtime using your saved configuration. No GUI interaction needed during the test.
- **Logs**: Check the JMeter log in Azure's test results for `Smart Pacing Timer` messages to verify pacing values.

---

## Using JMeter Variables/Properties

Any numeric field supports JMeter expressions:

```
# Read from JMeter property (set via command line or CI/CD)
${__P(targetTPH,200)}

# Read from CSV variable
${throughput}

# Use JMeter function
${__groovy(100 * 2)}
```

Example command-line usage:
```bash
jmeter -JtargetTPH=200 -JnumUsers=16 -n -t test.jmx
```

In the timer, set:
- Base Throughput: `${__P(targetTPH,200)}`
- Number of VUsers: `${__P(numUsers,10)}`

The GUI shows "will resolve at runtime" for variable fields. At runtime, variables are resolved before each pacing calculation.

---

## Adaptive Pacing — When to Use

| Scenario | Use Adaptive? | Settings |
|----------|---------------|----------|
| Stable app, consistent RT | No (static is fine) | — |
| Response times vary significantly | **Yes** | Window: 10s, Dampening: 0.3 |
| Need precise TPS control | **Yes** | Window: 5s, Dampening: 0.5 |
| Long soak test | **Yes** | Window: 30s, Dampening: 0.3 |
| Short spike/smoke test | No | — |

---

## Known Behavior

### Test Duration Overshoot

When using long pacing values (e.g., 236s), JMeter may run slightly beyond the configured "Duration" in the Thread Group. This is standard JMeter behavior — threads complete their current sleep/iteration before checking the stop flag.

**Workaround:** Set duration = `target_duration - max_pacing` (e.g., 3900 - 236 = 3664 seconds).

### First Iteration Pacing

By default, pacing is applied on the first iteration (strict throughput accuracy). If you want all users to start immediately without waiting, check **"Skip pacing on first iteration"** — but note this adds one extra transaction per user.

---

## Building & Testing

```bash
# Build
mvn clean package

# Run tests (51 tests: 34 calculator + 17 adaptive controller)
mvn test

# Package without tests
mvn package -DskipTests
```

---

## Project Structure

```
src/main/java/com/github/tharvprayag/jmeter/pacing/
├── PacingCalculator.java           # Pure calculation logic (no JMeter deps)
├── PacingCalculatorTimer.java      # JMeter Timer implementation
├── PacingCalculatorTimerGui.java   # Swing GUI
└── AdaptivePacingController.java   # Thread-safe adaptive feedback controller

src/test/java/com/github/tharvprayag/jmeter/pacing/
├── PacingCalculatorTest.java       # 34 unit tests for core calculations
└── AdaptivePacingControllerTest.java # 17 tests for adaptive controller
```

---

## License

Apache License 2.0
