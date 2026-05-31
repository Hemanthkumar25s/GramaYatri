"""
GramaYatri — Locust Load Test Script
=======================================
Simulates 10,000 concurrent users (Passengers, Drivers, Ticket Machines)
interacting with the Firebase Realtime Database REST API.

Usage:
    # Install Locust
    pip install locust

    # Run in web UI mode (http://localhost:8089)
    locust -f loadtesting/locustfile.py

    # Run headless (10,000 users, 200/sec spawn rate, 5 minutes)
    locust -f loadtesting/locustfile.py \
        --headless \
        -u 10000 \
        -r 200 \
        --run-time 5m \
        --host https://<PROJECT>.firebaseio.com

    # OR use environment variables
    export FIREBASE_HOST=https://gramayatri-xxxxx.firebaseio.com
    locust -f loadtesting/locustfile.py --headless -u 10000 -r 200 --run-time 10m

Requirements:
    pip install locust faker
"""

import os
import random
import json
import time
from locust import HttpUser, task, between, events
from faker import Faker

fake = Faker("en_IN")

# ─── Configuration ──────────────────────────────────────────────────────────

FIREBASE_HOST = os.getenv(
    "FIREBASE_HOST",
    "https://gramayatri-xxxxx.firebaseio.com",  # Replace with your actual project
)

# Simulate 10,000 routes for realistic load distribution
ROUTE_IDS = [f"KSRTC-{i}" for i in range(1, 201)] + [
    f"BMTC-{i}" for i in range(1, 101)
]
STOP_NAMES = [
    "Majestic Bus Stand",
    "Kempegowda Bus Stop",
    "Vijayanagar",
    "Jayanagar",
    "Malleswaram",
    "Rajajinagar",
    "Basavanagudi",
    "Indiranagar",
    "Koramangala",
    "Whitefield",
    "Electronic City",
    "Bannerghatta Road",
    "Mysore Road",
    "Tumkur Road",
    "Kanakapura Road",
]

PING_TYPES = [
    "BUS_AT_STOP",
    "ON_THE_BUS",
    "BUS_LEFT_STOP",
    "BUS_DELAYED",
    "BUS_CANCELLED",
    "EXTRA_BUS",
]

LOCATION_SOURCES = ["DRIVER", "TICKET_MACHINE"]


# ─── Helper Functions ───────────────────────────────────────────────────────


def random_lat_lng():
    """Generate a random lat/lng within ~Bengaluru area."""
    return (
        round(12.85 + random.uniform(-0.15, 0.15), 6),
        round(77.55 + random.uniform(-0.15, 0.15), 6),
    )


def make_ping(route_id, user_name=None):
    lat, lng = random_lat_lng()
    return {
        "routeId": route_id,
        "stopId": f"stop-{random.randint(1, 50)}",
        "stopName": random.choice(STOP_NAMES),
        "stopSequence": random.randint(1, 30),
        "lat": lat,
        "lng": lng,
        "userName": user_name or fake.first_name(),
        "deviceId": fake.uuid4(),
        "type": random.choice(PING_TYPES),
        "timestamp": int(time.time() * 1000),
        "isActive": True,
        "confirmationCount": 0,
        "denialCount": 0,
    }


def make_live_location(route_id, source):
    lat, lng = random_lat_lng()
    return {
        "routeId": route_id,
        "lat": lat,
        "lng": lng,
        "speed": round(random.uniform(0, 40), 1),
        "bearing": round(random.uniform(0, 360), 1),
        "accuracy": round(random.uniform(5, 50), 1),
        "timestamp": int(time.time() * 1000),
        "reporterName": fake.first_name(),
        "driverName": fake.first_name(),
        "driverId": fake.uuid4(),
        "isActive": True,
        "tripId": f"trip-{route_id}-{random.randint(1000, 9999)}",
        "source": source,
    }


def make_ticket_session(route_id):
    return {
        "routeId": route_id,
        "tripId": f"trip-{route_id}-{random.randint(1000, 9999)}",
        "machineId": f"TM-{random.randint(100, 999)}",
        "verificationToken": fake.uuid4()[:12],
        "qrPayload": json.dumps(
            {"trip": f"trip-{route_id}", "token": fake.uuid4()[:8]}
        ),
        "lat": round(12.85 + random.uniform(-0.15, 0.15), 6),
        "lng": round(77.55 + random.uniform(-0.15, 0.15), 6),
        "createdAt": int(time.time() * 1000),
        "expiresAt": int(time.time() * 1000) + 28800000,  # 8 hours
        "isActive": True,
    }


# ─── User Classes ────────────────────────────────────────────────────────────


class GramaYatriPassengerUser(HttpUser):
    """
    Simulates a passenger using the GramaYatri User app.
    Weight: ~50% of total traffic (5,000 of 10,000 users)
    """

    wait_time = between(3, 15)
    weight = 5

    def on_start(self):
        self.user_name = fake.first_name()
        self.device_id = fake.uuid4()

    @task(4)
    def view_routes(self):
        """Fetch routes data (most common operation)."""
        with self.client.get(
            "/routes.json",
            name="GET /routes",
            catch_response=True,
        ) as resp:
            if resp.status_code == 200:
                resp.success()
            else:
                resp.failure(f"Status {resp.status_code}")

    @task(3)
    def view_live_locations(self):
        """Check live bus locations."""
        route_id = random.choice(ROUTE_IDS)
        with self.client.get(
            f"/live_locations/{route_id}.json",
            name="GET /live_locations/{routeId}",
            catch_response=True,
        ) as resp:
            if resp.status_code == 200:
                resp.success()
            else:
                resp.failure(f"Status {resp.status_code}")

    @task(2)
    def view_active_pings(self):
        """View active pings on a route."""
        route_id = random.choice(ROUTE_IDS)
        with self.client.get(
            f"/pings/{route_id}.json?orderBy=\"timestamp\"&limitToLast=5",
            name="GET /pings/{routeId} (recent)",
            catch_response=True,
        ) as resp:
            if resp.status_code == 200:
                resp.success()
            else:
                resp.failure(f"Status {resp.status_code}")

    @task(1)
    def submit_ping(self):
        """Submit a crowd-sourced bus ping."""
        route_id = random.choice(ROUTE_IDS)
        ping_data = make_ping(route_id, self.user_name)
        with self.client.put(
            f"/pings/{route_id}/{self.device_id}.json",
            json=ping_data,
            name="PUT /pings/{routeId} (submit)",
            catch_response=True,
        ) as resp:
            if resp.status_code in (200, 204):
                resp.success()
            else:
                resp.failure(f"Status {resp.status_code}")

    @task(1)
    def view_alerts(self):
        """Check alerts on a route."""
        route_id = random.choice(ROUTE_IDS)
        with self.client.get(
            f"/alerts/{route_id}.json?orderBy=\"timestamp\"&limitToLast=3",
            name="GET /alerts/{routeId}",
            catch_response=True,
        ) as resp:
            if resp.status_code == 200:
                resp.success()
            else:
                resp.failure(f"Status {resp.status_code}")


class GramaYatriDriverUser(HttpUser):
    """
    Simulates a driver using the GramaYatri Driver app.
    Weight: ~30% of total traffic (3,000 of 10,000 users)
    """

    wait_time = between(2, 10)
    weight = 3

    @task(3)
    def update_live_location(self):
        """Send GPS location update (most frequent driver action)."""
        route_id = random.choice(ROUTE_IDS)
        location = make_live_location(route_id, "DRIVER")
        with self.client.put(
            f"/live_locations/{route_id}/DRIVER.json",
            json=location,
            name="PUT /live_locations/{routeId}/DRIVER",
            catch_response=True,
        ) as resp:
            if resp.status_code in (200, 204):
                resp.success()
            else:
                resp.failure(f"Status {resp.status_code}")

    @task(1)
    def verify_session(self):
        """Verify QR session from TicketMachine."""
        route_id = random.choice(ROUTE_IDS)
        trip_id = f"trip-{route_id}-{random.randint(1000, 9999)}"
        with self.client.get(
            f"/ticket_machine_sessions/{trip_id}.json",
            name="GET /ticket_machine_sessions/{tripId}",
            catch_response=True,
        ) as resp:
            if resp.status_code == 200:
                resp.success()
            else:
                resp.failure(f"Status {resp.status_code}")


class GramaYatriTicketMachineUser(HttpUser):
    """
    Simulates a ticket machine broadcasting GPS location.
    Weight: ~20% of total traffic (2,000 of 10,000 users)
    """

    wait_time = between(5, 15)
    weight = 2

    @task(2)
    def update_live_location(self):
        """Send GPS location from ticket machine."""
        route_id = random.choice(ROUTE_IDS)
        location = make_live_location(route_id, "TICKET_MACHINE")
        with self.client.put(
            f"/live_locations/{route_id}/TICKET_MACHINE.json",
            json=location,
            name="PUT /live_locations/{routeId}/TICKET_MACHINE",
            catch_response=True,
        ) as resp:
            if resp.status_code in (200, 204):
                resp.success()
            else:
                resp.failure(f"Status {resp.status_code}")

    @task(1)
    def publish_session(self):
        """Publish a ticket machine session for driver verification."""
        route_id = random.choice(ROUTE_IDS)
        session = make_ticket_session(route_id)
        with self.client.put(
            f"/ticket_machine_sessions/{session['tripId']}.json",
            json=session,
            name="PUT /ticket_machine_sessions/{tripId}",
            catch_response=True,
        ) as resp:
            if resp.status_code in (200, 204):
                resp.success()
            else:
                resp.failure(f"Status {resp.status_code}")


# ─── Events ─────────────────────────────────────────────────────────────────


@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    print("🚍 GramaYatri Load Test Starting")
    print(f"   Target: {FIREBASE_HOST}")
    print("   Simulating: Passengers(50%) + Drivers(30%) + TicketMachines(20%)")


@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):
    print("\n✅ GramaYatri Load Test Complete")
    if environment.stats:
        stats = environment.stats.total
        print(f"   Total requests: {stats.num_requests}")
        print(f"   Failures: {stats.num_failures}")
        print(f"   Avg response time: {stats.avg_response_time:.2f} ms")
        print(f"   P95 response time: {stats.get_response_time_percentile(0.95):.2f} ms")
        print(f"   P99 response time: {stats.get_response_time_percentile(0.99):.2f} ms")
        print(f"   Requests/sec: {stats.current_rps:.2f}")
