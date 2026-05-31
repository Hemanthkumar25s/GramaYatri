#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════════════
# GramaYatri — Cold Start Benchmark Script
# ═══════════════════════════════════════════════════════════════════════════════
# Measures Android app cold-start times using ADB.
# Target: < 2,000 ms for all three apps.
#
# Usage:
#   chmod +x GramaYatri/scripts/cold_start_benchmark.sh
#   ./GramaYatri/scripts/cold_start_benchmark.sh              # Run 10 iterations for all apps
#   ./GramaYatri/scripts/cold_start_benchmark.sh --iter 20    # Custom iterations
#   ./GramaYatri/scripts/cold_start_benchmark.sh --app user   # Test only User app
#   ./GramaYatri/scripts/cold_start_benchmark.sh --help       # Show help
# ═══════════════════════════════════════════════════════════════════════════════

set -euo pipefail

# ─── Configuration ──────────────────────────────────────────────────────────

ITERATIONS=10
TEST_USER=false
TEST_DRIVER=false
TEST_TICKET=false

# App definitions: (label, package, activity)
USER_APP=("User App" "com.gramayatri" ".LaunchActivity")
DRIVER_APP=("Driver App" "com.gramayatri.driver" ".DriverActivity")
TICKET_APP=("TicketMachine App" "com.gramayatri.ticketmachine" ".TicketMachineActivity")

# ─── Parse Arguments ───────────────────────────────────────────────────────

while [[ $# -gt 0 ]]; do
    case "$1" in
        --iter)
            ITERATIONS="$2"
            shift 2
            ;;
        --app)
            case "$2" in
                user) TEST_USER=true ;;
                driver) TEST_DRIVER=true ;;
                ticketmachine|ticket) TEST_TICKET=true ;;
                all)
                    TEST_USER=true
                    TEST_DRIVER=true
                    TEST_TICKET=true
                    ;;
                *)
                    echo "Unknown app: $2"
                    echo "Valid: user, driver, ticketmachine, all"
                    exit 1
                    ;;
            esac
            shift 2
            ;;
        --help|-h)
            echo "GramaYatri Cold-Start Benchmark"
            echo ""
            echo "Usage: $0 [--iter N] [--app user|driver|ticketmachine|all]"
            echo ""
            echo "Options:"
            echo "  --iter N      Number of iterations (default: 10)"
            echo "  --app NAME    Which app to test (default: all)"
            echo "  --help        Show this help"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage"
            exit 1
            ;;
    esac
done

# Default: test all if none specified
if ! $TEST_USER && ! $TEST_DRIVER && ! $TEST_TICKET; then
    TEST_USER=true
    TEST_DRIVER=true
    TEST_TICKET=true
fi

# ─── Helper Functions ───────────────────────────────────────────────────────

check_adb() {
    if ! command -v adb &> /dev/null; then
        echo "❌ ERROR: ADB not found. Install Android SDK and add adb to PATH."
        exit 1
    fi

    DEVICES=$(adb devices | grep -v "List" | grep -v "^$" | wc -l)
    if [[ "$DEVICES" -eq 0 ]]; then
        echo "❌ ERROR: No Android device/emulator connected."
        echo "   Connect a device or start an emulator first."
        exit 1
    fi
    echo "📱 Connected device(s):"
    adb devices | grep -v "List" | grep -v "^$"
    echo ""
}

measure_cold_start() {
    local label="$1"
    local package="$2"
    local activity="$3"
    local times=()

    echo ""
    echo "═══════════════════════════════════════════════════════════════"
    echo "  Testing: ${label}"
    echo "  Package: ${package}"
    echo "  Activity: ${activity}"
    echo "  Iterations: ${ITERATIONS}"
    echo "  Target: < 2,000 ms"
    echo "═══════════════════════════════════════════════════════════════"

    for i in $(seq 1 $ITERATIONS); do
        # Force stop the app first
        adb shell am force-stop "$package" 2>/dev/null

        # Brief wait for process cleanup
        sleep 2

        # Launch and measure
        RESULT=$(adb shell am start \
            -W \
            -n "${package}/${activity}" \
            2>/dev/null | grep "TotalTime" | awk '{print $2}')

        if [[ -z "$RESULT" ]]; then
            echo "  Iteration $i: ❌ FAILED to get timing"
            continue
        fi

        times+=("$RESULT")
        STATUS=""
        if [[ "$RESULT" -lt 2000 ]]; then
            STATUS="✅"
        else
            STATUS="⚠️  OVER 2s"
        fi
        echo "  Iteration $i: ${RESULT}ms ${STATUS}"

        # Wait between iterations to let system settle
        sleep 3
    done

    # ─── Stats ───────────────────────────────────────────────────────
    if [[ ${#times[@]} -eq 0 ]]; then
        echo ""
        echo "❌ No valid measurements collected for ${label}"
        return
    fi

    # Sort for percentile calculations
    IFS=$'\n' sorted=($(sort -n <<<"${times[*]}")); unset IFS

    local count=${#sorted[@]}
    local sum=0
    for t in "${sorted[@]}"; do
        sum=$((sum + t))
    done
    local avg=$((sum / count))
    local min=${sorted[0]}
    local max=${sorted[$((count - 1))]}
    local p95_idx=$((count * 95 / 100 - 1))
    local p99_idx=$((count * 99 / 100 - 1))
    [[ $p95_idx -lt 0 ]] && p95_idx=0
    [[ $p99_idx -lt 0 ]] && p99_idx=0
    local p95=${sorted[$p95_idx]}
    local p99=${sorted[$p99_idx]}

    # Median
    local mid=$((count / 2))
    local median
    if [[ $((count % 2)) -eq 0 ]]; then
        median=$(( (sorted[mid-1] + sorted[mid]) / 2 ))
    else
        median=${sorted[mid]}
    fi

# Std deviation using awk (portable, no bc needed)
    local stddev=$(awk -v avg="$avg" 'BEGIN{sum=0; count=0}
        {sum+=($1-avg)^2; count++}
        END{printf "%.1f", sqrt(sum/count)}' <<< "$(printf "%s\n" "${sorted[@]}")" 2>/dev/null || echo "0")

    # RESULTS TABLE
    echo ""
    echo "─── Results for ${label} ───────────────────────────"
    printf "  %-25s %s\n" "Measurements:" "${count}"
    printf "  %-25s %s ms\n" "Min:" "${min}"
    printf "  %-25s %s ms\n" "Average:" "${avg}"
    printf "  %-25s %s ms\n" "Median (p50):" "${median}"
    printf "  %-25s %s ms\n" "p95:" "${p95}"
    printf "  %-25s %s ms\n" "p99:" "${p99}"
    printf "  %-25s %s ms\n" "Max:" "${max}"
    printf "  %-25s %s\n" "Std Dev:" "$(printf "%.1f" "$stddev")"

    # PASS / FAIL
    echo ""
    if [[ "$avg" -lt 2000 ]]; then
        echo "  ✅ PASS: Average cold start (${avg}ms) is under 2,000ms target"
    else
        echo "  ❌ FAIL: Average cold start (${avg}ms) EXCEEDS 2,000ms target"
        echo "     Optimization needed!"
    fi

    if [[ "$p95" -ge 2000 ]]; then
        echo "  ⚠️  95th percentile (${p95}ms) exceeds 2,000ms — outliers present"
    fi

    echo "───────────────────────────────────────────────────────"
}

# ─── Main ───────────────────────────────────────────────────────────────────

echo ""
echo "╔══════════════════════════════════════════════════════════════════╗"
echo "║      GramaYatri — Cold Start Benchmark Suite                    ║"
echo "║      Target: All apps < 2,000 ms cold start                    ║"
echo "╚══════════════════════════════════════════════════════════════════╝"
echo ""

check_adb

echo "Iterations per app: ${ITERATIONS}"
echo "Testing: User=$TEST_USER Driver=$TEST_DRIVER TicketMachine=$TEST_TICKET"
echo "Started at: $(date)"
echo ""

# Clear app caches before starting
echo "🧹 Clearing system caches..."
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS 2>/dev/null || true
echo ""

START_TIME=$(date +%s)

if $TEST_USER; then
    measure_cold_start "${USER_APP[@]}"
fi

if $TEST_DRIVER; then
    measure_cold_start "${DRIVER_APP[@]}"
fi

if $TEST_TICKET; then
    measure_cold_start "${TICKET_APP[@]}"
fi

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

# ─── Summary ────────────────────────────────────────────────────────────────

echo ""
echo "╔══════════════════════════════════════════════════════════════════╗"
echo "║                      BENCHMARK COMPLETE                         ║"
echo "╚══════════════════════════════════════════════════════════════════╝"
echo "  Duration: ${DURATION}s"
echo "  Finished: $(date)"
echo ""
echo "  Compare against performance targets (TEST_PLAN.md):"
echo "    Metric          | Target    | Status"
echo "    ─────────────────────────────────────"
echo "    Cold start      | < 2,000ms | See results above"
echo "    Warm start      | < 500ms   | Run additional test"
echo "    Firebase read   | < 500ms   | See GramaYatri/loadtesting"
echo "    Firebase write  | < 800ms   | See GramaYatri/loadtesting"
echo "    APK size        | < 15MB    | Check build output"
echo "    Memory usage    | < 128MB   | Profile with ADB"
echo ""
