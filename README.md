# JMeter Smart Pacing Timer Plugin

A JMeter Timer plugin that **automatically calculates and applies pacing delays** based on your target throughput (TPS/TPH/TPM) and number of virtual users.

## The Problem

Every performance tester has done this calculation manually:

```
Pacing = (Number of VUsers / Target TPS) × 1000 ms
```

And then adjusted for response time and think time. This plugin does it automatically, inside JMeter, in real-time.

## Features

- **Auto-calculate pacing** from target throughput (TPS, TPM, or TPH)
- **Adjusts for response time** — subtract expected avg response time
- **Adjusts for think time** — subtract think time already in your script
- **Auto-detect VUsers** — reads active thread count from thread group at runtime
- **Randomization** — ±X% variance to avoid thundering herd effect
- **Preview pacing** — see calculated pacing before running the test

## Installation

1. Build: `mvn clean package`
2. Copy `target/jmeter-smart-pacing-timer-1.0.0-SNAPSHOT.jar` to JMeter's `lib/ext/` directory
3. Restart JMeter
4. The "Smart Pacing Timer" will appear under **Timers** in the test plan menu

## Usage

1. Add **Smart Pacing Timer** to your Thread Group (Right-click → Add → Timer → Smart Pacing Timer)
2. Configure:
   - **Target Throughput**: Your desired TPS/TPM/TPH
   - **Number of VUsers**: Total threads in this thread group
   - **Think Time** (optional): If your script already has think time, enter it here
   - **Avg Response Time** (optional): Expected response time to subtract from pacing
   - **Randomization %** (optional): Add variance to prevent synchronized requests
3. Click "Preview Pacing" to verify the calculated value
4. Run your test!

## Formula

```
Basic Pacing (ms) = (VUsers / Target_TPS) × 1000

Adjusted Pacing = max(0, Basic_Pacing - Avg_Response_Time - Think_Time)
```

## Example

| Scenario | Target | VUsers | Avg RT | Think Time | Calculated Pacing |
|----------|--------|--------|--------|------------|-------------------|
| Login flow | 10 TPS | 10 | 0ms | 0ms | 1000ms |
| Search | 500 TPH | 50 | 2000ms | 1000ms | 357000ms (5.95 min) |
| Checkout | 5 TPS | 20 | 500ms | 200ms | 3300ms |

## Building from Source

### Prerequisites
- Java 11+
- Maven 3.6+

### Build
```bash
mvn clean package
```

### Run Tests
```bash
mvn test
```

## License

Apache License 2.0
