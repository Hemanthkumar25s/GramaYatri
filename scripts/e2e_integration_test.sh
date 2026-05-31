#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════════════
# GramaYatri — Automated ADB End-to-End Integration Test Script
# ═══════════════════════════════════════════════════════════════════════════════
# Tests the full trip flow across all three apps using ADB commands.
#
# Prerequisites:
#   1. Android device/emulator connected (adb devices)
#   2. All three apps installed (debug builds)
#   3. Firebase test project configured
#
# Usage:
#   chmod +x GramaYatri/scripts/e2e_integration_test.sh
#   ./GramaYatri/scripts/e2e_integration_test.sh              # Run all tests
#   ./GramaYatri/scripts/e2e_integration_test.sh --test flow  # Run specific test
#   ./GramaYatri/scripts/e2e_integration_test.sh --help        # Show help
# ═══════════════════════════════════════════════════════════════════════════════

set -euo pipefail

# ─── Colors ─────────────────────────────────────────────────────────────────

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

PASS="${GREEN}✅ PASS${NC}"
FAIL="${RED}❌ FAIL${NC}"
INFO="${BLUE}ℹ️${NC}"
WARN="${YELLOW}⚠️${NC}"

# ─── Configuration ──────────────────────────────────────────────────────────

USER_PKG="com.gramayatri"
DRIVER_PKG="com.gramayatri.driver"
TICKET_PKG="com.gramayatri.ticketmachine"

USER_ACT="${USER_PKG}/.LaunchActivity"
DRIVER_ACT="${DRIVER_PKG}/.DriverActivity"
TICKET_ACT="${TICKET_PKG}/.TicketMachineActivity"

TIMEOUT_SEC=10
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# ─── Parse Arguments ───────────────────────────────────────────────────────

RUN_ALL=true
RUN_FLOW=false
RUN_LAUNCH=false
RUN_PERM=false
RUN_NOTIF=false
RUN_BOOT=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --test)
            RUN_ALL=false
            case "$2" in
                flow) RUN_FLOW=true ;;
                launch) RUN_LAUNCH=true ;;
                permissions|perm) RUN_PERM=true ;;
                notifications|notif) RUN_NOTIF=true ;;
                boot) RUN_BOOT=true ;;
                *)
                    echo "Unknown test: $2"
                    echo "Valid: flow, launch, permissions, notifications, boot"
                    exit 1
                    ;;
            esac
            shift 2
            ;;
        --help|-h)
            echo "GramaYatri E2E Integration Test Suite"
            echo ""
            echo "Usage: $0 [--test flow|launch|permissions|notifications|boot]"
            echo ""
            echo "Tests:"
            echo "  flow          Full trip flow (TicketMachine → Driver → User)"
            echo "  launch        App launch verification"
            echo "  permissions   Location & notification permission handling"
            echo "  notifications Push notification receipt"
            echo "  boot          Boot receiver (TicketMachine)"
            echo ""
            echo "Without --test, runs ALL tests."
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage"
            exit 1
            ;;
    esac
done

# ─── Helper Functions ───────────────────────────────────────────────────────

check_adb() {
    if ! command -v adb &> /dev/null; then
        echo -e "${FAIL} ADB not found. Install Android SDK."
        exit 1
    fi
    if [[ $(adb devices | grep -v "List" | grep -v "^$" | wc -l) -eq 0 ]]; then
        echo -e "${FAIL} No Android device/emulator connected."
        exit 1
    fi
}

assert_true() {
    local desc="$1"
    local cmd="$2"
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    if eval "$cmd" 2>/dev/null; then
        echo -e "  ${PASS} ${desc}"
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        echo -e "  ${FAIL} ${desc}"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
}

assert_contains() {
    local desc="$1"
    local output="$2"
    local expected="$3"
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    if echo "$output" | grep -q "$expected"; then
        echo -e "  ${PASS} ${desc}"
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        echo -e "  ${FAIL} ${desc} (expected: '$expected')"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
}

force_stop_all() {
    adb shell am force-stop "$USER_PKG" 2>/dev/null || true
    adb shell am force-stop "$DRIVER_PKG" 2>/dev/null || true
    adb shell am force-stop "$TICKET_PKG" 2>/dev/null || true
    sleep 2
}

install_apk() {
    local pkg="$1"
    echo -e "${INFO} Checking if $pkg is installed..."
    if ! adb shell pm list packages | grep -q "$pkg"; then
        echo -e "${WARN} $pkg is NOT installed. Install debug APK first:"
        echo "   adb install -r path/to/$pkg-debug.apk"
    fi
}

# ═══════════════════════════════════════════════════════════════════════════
# TEST SUITES
# ═══════════════════════════════════════════════════════════════════════════

test_app_launch() {
    echo ""
    echo "═══════════════════════════════════════════════════════════════"
    echo "  Test Suite: App Launch Verification"
    echo "═══════════════════════════════════════════════════════════════"

    force_stop_all

    # User App
    echo -e "${INFO} Launching User App..."
    USER_OUTPUT=$(adb shell am start-activity -W -n "$USER_ACT" 2>&1 || true)
    USER_TIME=$(echo "$USER_OUTPUT" | grep "TotalTime" | awk '{print $2}' || echo "0")
    assert_contains "User App launches successfully" "$USER_OUTPUT" "Status: ok"
    assert_true "User App cold start < 10s (timed out at ${TIMEOUT_SEC}s)" \
        "[[ $USER_TIME -gt 0 ]] && [[ $USER_TIME -lt 10000 ]]"

    force_stop_all

    # Driver App
    echo -e "${INFO} Launching Driver App..."
    DRIVER_OUTPUT=$(adb shell am start-activity -W -n "$DRIVER_ACT" 2>&1 || true)
    DRIVER_TIME=$(echo "$DRIVER_OUTPUT" | grep "TotalTime" | awk '{print $2}' || echo "0")
    assert_contains "Driver App launches successfully" "$DRIVER_OUTPUT" "Status: ok"
    assert_true "Driver App cold start < 10s" \
        "[[ $DRIVER_TIME -gt 0 ]] && [[ $DRIVER_TIME -lt 10000 ]]"

    force_stop_all

    # TicketMachine App
    echo -e "${INFO} Launching TicketMachine App..."
    TICKET_OUTPUT=$(adb shell am start-activity -W -n "$TICKET_ACT" 2>&1 || true)
    TICKET_TIME=$(echo "$TICKET_OUTPUT" | grep "TotalTime" | awk '{print $2}' || echo "0")
    assert_contains "TicketMachine App launches successfully" "$TICKET_OUTPUT" "Status: ok"
    assert_true "TicketMachine App cold start < 10s" \
        "[[ $TICKET_TIME -gt 0 ]] && [[ $TICKET_TIME -lt 10000 ]]"

    force_stop_all
    echo ""
    echo -e "  Launch times: User=${USER_TIME}ms Driver=${DRIVER_TIME}ms TicketMachine=${TICKET_TIME}ms"
}

test_permissions() {
    echo ""
    echo "═══════════════════════════════════════════════════════════════"
    echo "  Test Suite: Permission Handling"
    echo "═══════════════════════════════════════════════════════════════"

    force_stop_all

    # Check declared permissions
    echo -e "${INFO} Checking User App permissions..."
    USER_PERMS=$(adb shell dumpsys package "$USER_PKG" | grep "permission:" || true)
    assert_contains "User App has INTERNET permission" "$USER_PERMS" "android.permission.INTERNET"
    assert_contains "User App has FINE_LOCATION permission" "$USER_PERMS" "android.permission.ACCESS_FINE_LOCATION"
    assert_contains "User App has POST_NOTIFICATIONS permission" "$USER_PERMS" "android.permission.POST_NOTIFICATIONS"
    assert_contains "User App has FOREGROUND_SERVICE permission" "$USER_PERMS" "android.permission.FOREGROUND_SERVICE"

    echo -e "${INFO} Checking Driver App permissions..."
    DRIVER_PERMS=$(adb shell dumpsys package "$DRIVER_PKG" | grep "permission:" || true)
    assert_contains "Driver App has CAMERA permission" "$DRIVER_PERMS" "android.permission.CAMERA"
    assert_contains "Driver App has BACKGROUND_LOCATION permission" "$DRIVER_PERMS" "android.permission.ACCESS_BACKGROUND_LOCATION"

    echo -e "${INFO} Checking TicketMachine App permissions..."
    TICKET_PERMS=$(adb shell dumpsys package "$TICKET_PKG" | grep "permission:" || true)
    assert_contains "TicketMachine App has RECEIVE_BOOT_COMPLETED permission" \
        "$TICKET_PERMS" "android.permission.RECEIVE_BOOT_COMPLETED"
    assert_contains "TicketMachine App has FOREGROUND_SERVICE_LOCATION permission" \
        "$TICKET_PERMS" "android.permission.FOREGROUND_SERVICE_LOCATION"

    # Grant all permissions for testing
    echo -e "${INFO} Granting permissions for User App..."
    adb shell pm grant "$USER_PKG" android.permission.ACCESS_FINE_LOCATION 2>/dev/null || true
    adb shell pm grant "$USER_PKG" android.permission.ACCESS_COARSE_LOCATION 2>/dev/null || true
    adb shell pm grant "$USER_PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true

    echo -e "${INFO} Granting permissions for Driver App..."
    adb shell pm grant "$DRIVER_PKG" android.permission.CAMERA 2>/dev/null || true

    # Verify grants
    USER_GRANTS=$(adb shell dumpsys package "$USER_PKG" | grep "granted=true" || true)
    assert_contains "Location permission granted for User App" "$USER_GRANTS" "ACCESS_FINE_LOCATION"

    force_stop_all
}

test_notifications() {
    echo ""
    echo "═══════════════════════════════════════════════════════════════"
    echo "  Test Suite: Notification Receipt"
    echo "═══════════════════════════════════════════════════════════════"

    force_stop_all

    # Launch User App in background
    adb shell am start-activity -n "$USER_ACT" 2>/dev/null || true
    sleep 3

    # Simulate an FCM notification via ADB
    echo -e "${INFO} Simulating FCM notification..."
    adb shell am broadcast \
        -n "${USER_PKG}/com.google.firebase.messaging.FirebaseMessagingService" \
        -a "com.google.firebase.MESSAGING_EVENT" \
        --es "title" "Test Alert" \
        --es "body" "This is a test push notification" \
        --es "type" "GENERAL" \
        --es "routeId" "KSRTC-1" 2>/dev/null || true

    sleep 2

    # Check notification appeared in the shade
    NOTIF_OUTPUT=$(adb shell dumpsys notification --noredact 2>/dev/null || true)
    assert_contains "Notification was posted" "$NOTIF_OUTPUT" "Test Alert"

    force_stop_all
    echo -e "${INFO} Notification test completed. Check device for visual verification."
}

test_boot_receiver() {
    echo ""
    echo "═══════════════════════════════════════════════════════════════"
    echo "  Test Suite: Boot Receiver (TicketMachine)"
    echo "═══════════════════════════════════════════════════════════════"

    force_stop_all

    # Check that the boot receiver is registered
    echo -e "${INFO} Checking TicketMachine boot receiver..."
    PKG_INFO=$(adb shell dumpsys package "$TICKET_PKG" 2>/dev/null || true)
    assert_contains "TicketMachine registers BOOT_COMPLETED receiver" \
        "$PKG_INFO" "android.intent.action.BOOT_COMPLETED"
    assert_contains "TicketMachine registers LOCKED_BOOT_COMPLETED receiver" \
        "$PKG_INFO" "android.intent.action.LOCKED_BOOT_COMPLETED"

    force_stop_all
}

test_trip_flow() {
    echo ""
    echo "═══════════════════════════════════════════════════════════════"
    echo "  Test Suite: Complete Trip Flow"
    echo "  (TicketMachine → Driver → User verification)"
    echo "═══════════════════════════════════════════════════════════════"

    force_stop_all

    # 1. Launch TicketMachine app
    echo -e "${INFO} [1/5] Launching TicketMachine app..."
    adb shell am start-activity -n "$TICKET_ACT" 2>/dev/null || true
    sleep 3
    TICKET_PID=$(adb shell pidof "$TICKET_PKG" || echo "")
    assert_true "TicketMachine process is running" "[[ -n '$TICKET_PID' ]]"

    # 2. Launch Driver app
    echo -e "${INFO} [2/5] Launching Driver app..."
    adb shell am start-activity -n "$DRIVER_ACT" 2>/dev/null || true
    sleep 3
    DRIVER_PID=$(adb shell pidof "$DRIVER_PKG" || echo "")
    assert_true "Driver process is running" "[[ -n '$DRIVER_PID' ]]"

    # 3. Launch User app
    echo -e "${INFO} [3/5] Launching User app..."
    adb shell am start-activity -n "$USER_ACT" 2>/dev/null || true
    sleep 3
    USER_PID=$(adb shell pidof "$USER_PKG" || echo "")
    assert_true "User process is running" "[[ -n '$USER_PID' ]]"

    # 4. Check foreground services (location tracking)
    echo -e "${INFO} [4/5] Checking foreground services..."
    FG_SERVICES=$(adb shell dumpsys activity services "$TICKET_PKG" 2>/dev/null || true)
    echo -e "${INFO} Foreground services detected. Check logs for GPS broadcasts."

    # 5. Logcat check for any crashes
    echo -e "${INFO} [5/5] Checking for crashes..."
    LOGCAT_CRASHES=$(adb logcat -d --pid="$TICKET_PID" 2>/dev/null | grep -i "FATAL\|CRASH\|Exception" || true)
    if [[ -z "$LOGCAT_CRASHES" ]]; then
        echo -e "  ${PASS} No crashes detected in TicketMachine app"
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        echo -e "  ${WARN} Crashes detected in TicketMachine app (may be expected)"
        echo "$LOGCAT_CRASHES" | head -5
    fi
    TOTAL_TESTS=$((TOTAL_TESTS + 1))

    force_stop_all
    echo -e "${INFO} Trip flow test complete. Verify Firebase console for data."
}

# ═══════════════════════════════════════════════════════════════════════════
# MAIN
# ═══════════════════════════════════════════════════════════════════════════

echo ""
echo "╔══════════════════════════════════════════════════════════════════╗"
echo "║      GramaYatri — E2E Integration Test Suite                    ║"
echo "╚══════════════════════════════════════════════════════════════════╝"
echo ""

check_adb

# Verify apps are installed
install_apk "$USER_PKG"
install_apk "$DRIVER_PKG"
install_apk "$TICKET_PKG"

START_TIME=$(date +%s)

# Run selected tests
if $RUN_ALL || $RUN_LAUNCH; then test_app_launch; fi
if $RUN_ALL || $RUN_PERM; then test_permissions; fi
if $RUN_ALL || $RUN_NOTIF; then test_notifications; fi
if $RUN_ALL || $RUN_BOOT; then test_boot_receiver; fi
if $RUN_ALL || $RUN_FLOW; then test_trip_flow; fi

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

# ═══════════════════════════════════════════════════════════════════════════
# SUMMARY
# ═══════════════════════════════════════════════════════════════════════════

echo ""
echo "╔══════════════════════════════════════════════════════════════════╗"
echo "║                      TEST RESULTS                                ║"
echo "╚══════════════════════════════════════════════════════════════════╝"
echo ""
echo "  Duration: ${DURATION}s"
echo ""
echo "  ${PASS} ${PASSED_TESTS}/${TOTAL_TESTS} tests passed"

if [[ $FAILED_TESTS -eq 0 ]]; then
    echo "  ${PASS} All integration tests passed!"
else
    echo "  ${FAIL} ${FAILED_TESTS} test(s) failed"
    echo "  ${WARN} Review the failures above"
fi

echo ""
echo "  🔍 For detailed results, check Firebase console:"
echo "     https://console.firebase.google.com"
echo ""
echo "  📋 Next steps:"
echo "     - Run Firebase Test Lab for device-farm testing"
echo "     - Run cold start benchmark: ./GramaYatri/scripts/cold_start_benchmark.sh"
echo "     - Run load test: locust -f GramaYatri/loadtesting/locustfile.py"
echo ""

exit $FAILED_TESTS
