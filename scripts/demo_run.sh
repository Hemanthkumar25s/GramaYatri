#!/bin/bash
# demo_run.sh — Launch all 3 GramaYatri apps in parallel on connected devices/emulators
#
# Usage:
#   ./GramaYatri/scripts/demo_run.sh                  # Launch on single device
#   ./GramaYatri/scripts/demo_run.sh emulator-5554    # Launch on specific device
#
# Prerequisites:
#   - ADB installed and devices connected
#   - APKs built and installed: ./gradlew assembleDebug (in each app dir)

set -e

DEVICE=${1:-}
DEVICE_ARG=""
if [ -n "$DEVICE" ]; then
    DEVICE_ARG="-s $DEVICE"
fi

echo "=== GramaYatri Demo Run ==="

# Verify device is connected
if [ -z "$DEVICE" ]; then
    DEVICES=$(adb devices | grep -E "^\w+" | grep -v "List" | head -1 | awk '{print $1}')
    if [ -z "$DEVICES" ]; then
        echo "❌ No devices/emulators found. Connect a device or start an emulator first."
        echo "   Run: adb devices -l"
        exit 1
    fi
    echo "Using device: $DEVICES"
fi

echo ""
echo "Launching TicketMachine..."
adb $DEVICE_ARG shell am start -n "com.gramayatri.ticketmachine/.TicketMachineActivity" || {
    echo "⚠️  Could not launch TicketMachine. Make sure it's installed."
    echo "   Run: adb $DEVICE_ARG install -r GramaYatri-TicketMachine/app/build/outputs/apk/release/app-release.apk"
}

sleep 1

echo "Launching Driver App..."
adb $DEVICE_ARG shell am start -n "com.gramayatri.driverapp/.DriverActivity" || {
    echo "⚠️  Could not launch Driver App."
}

sleep 1

echo "Launching User App..."
adb $DEVICE_ARG shell am start -n "com.gramayatri/.GramaYatriApp" || {
    echo "⚠️  Could not launch User App."
}

echo ""
echo "=== All apps launched! ==="
echo ""
echo "Instructions:"
echo "  1. TicketMachine: Enter Route ID > Enable Demo Mode > START GPS"
echo "  2. Driver App: Use same Route ID to verify"
echo "  3. User App: Browse to Route ID to see live tracking"
echo ""
echo "See GramaYatri/scripts/demo_run_guide.md for detailed walkthrough."
