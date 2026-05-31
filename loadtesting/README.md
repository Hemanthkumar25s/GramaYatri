# GramaYatri — Load Testing & Performance Benchmarks

## Overview

This directory contains tools to load test the GramaYatri Firebase backend and benchmark app cold-start performance as specified in `TEST_PLAN.md`.

## Load Targets

| User Type | Concurrent Users | Weight |
|-----------|-----------------|--------|
| Passengers (User App) | 5,000 | 50% |
| Drivers (Driver App) | 3,000 | 30% |
| Ticket Machines | 2,000 | 20% |
| **Total** | **10,000** | 100% |

## Performance Targets

| Metric | Target |
|--------|--------|
| Cold start time | < 2,000 ms |
| Firebase read (p95) | < 500 ms |
| Firebase write (p95) | < 800 ms |

---

## 1. Locust Load Test (Python)

Simulates realistic user behavior including think times and diverse API calls.

### Setup

```bash
pip install locust faker
```

### Run (Headless — 10,000 users)

```bash
export FIREBASE_HOST=https://gramayatri-xxxxx.firebaseio.com

locust -f loadtesting/locustfile.py \
    --headless \
    -u 10000 \
    -r 200 \
    --run-time 10m \
    --host $FIREBASE_HOST
```

### Run (Web UI)

```bash
locust -f loadtesting/locustfile.py \
    --host https://gramayatri-xxxxx.firebaseio.com
# Open http://localhost:8089
```

## 2. JMeter Load Test

A JMeter test plan with 3 thread groups matching our user distribution.

### Setup

1. Install [Apache JMeter](https://jmeter.apache.org/download_jmeter.cgi) 5.x
2. Open `loadtesting/gramayatri_loadtest.jmx` in JMeter GUI
3. Edit the `FIREBASE_HOST` variable to match your project

### Run (CLI)

```bash
jmeter -n -t loadtesting/gramayatri_loadtest.jmx \
    -l results.jtl \
    -e -o reports/
```

### Run (100 users quick test)

```bash
jmeter -n -t loadtesting/gramayatri_loadtest.jmx \
    -JThreadGroup.num_threads=100 \
    -l results-quick.jtl
```

## 3. Cold-Start Benchmark (ADB)

Measures app launch times on a connected Android device.

### Run

```bash
./scripts/cold_start_benchmark.sh           # All apps, 10 iterations
./scripts/cold_start_benchmark.sh --iter 20  # Custom iterations
./scripts/cold_start_benchmark.sh --app user # Single app
```

## 4. E2E Integration Tests (ADB)

Automated end-to-end tests verifying all three apps work together.

### Run

```bash
./scripts/e2e_integration_test.sh                   # All tests
./scripts/e2e_integration_test.sh --test flow        # Trip flow only
./scripts/e2e_integration_test.sh --test launch      # App launch only
```

## Important Notes

- **Replace `https://gramayatri-xxxxx.firebaseio.com`** with your actual Firebase project URL before running
- Load tests hit the **real Firebase Realtime Database** — use a **test/development project**, not production
- Firebase has built-in [rate limits](https://firebase.google.com/docs/database/usage/limits) — sustained traffic over limits may trigger throttling
- For more realistic results, run from multiple machines or use distributed Locust/JMeter
