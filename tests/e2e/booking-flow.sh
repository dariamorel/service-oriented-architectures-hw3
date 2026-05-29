#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
API_URL="${API_URL:-http://localhost:8080}"

FLIGHT_ID="11111111-1111-1111-1111-111111111111"
USER_ID="00000000-0000-0000-0000-00000000e2e1"
PASSENGER_NAME="E2E Test Passenger"
PASSENGER_EMAIL="e2e.passenger@example.com"
SEAT_COUNT=2
EXPECTED_TOTAL_PRICE=10000

RESPONSE="$(mktemp)"

cleanup_files() {
    rm -f "$RESPONSE"
}
trap cleanup_files EXIT

compose() {
    (cd "$ROOT_DIR" && docker compose "$@")
}

log() {
    printf '[e2e] %s\n' "$*"
}

fail() {
    printf '[e2e] ERROR: %s\n' "$*" >&2
    exit 1
}

booking_sql() {
    compose exec -T booking-db psql -U postgres -d booking -v ON_ERROR_STOP=1 -tAc "$1" | tr -d '[:space:]'
}

flight_sql() {
    compose exec -T flight-db psql -U postgres -d flight -v ON_ERROR_STOP=1 -tAc "$1" | tr -d '[:space:]'
}

expect_equal() {
    local actual="$1"
    local expected="$2"
    local message="$3"

    if [[ "$actual" != "$expected" ]]; then
        fail "$message: expected '$expected', got '$actual'"
    fi
}

wait_for_api() {
    log "Waiting for booking-service API at $API_URL"

    for _ in {1..60}; do
        local status
        status="$(curl -sS -o /dev/null -w '%{http_code}' "$API_URL/flights?origin=SVO&destination=LED" || true)"
        if [[ "$status" == "200" ]]; then
            return
        fi
        sleep 2
    done

    fail "booking-service API did not become ready"
}

reset_test_state() {
    log "Resetting test data"

    local previous_booking_ids
    previous_booking_ids="$(booking_sql "select id from bookings where user_id = '$USER_ID';")"

    if [[ -n "$previous_booking_ids" ]]; then
        while IFS= read -r booking_id; do
            [[ -z "$booking_id" ]] && continue
            flight_sql "delete from seat_reservations where booking_id = '$booking_id';" >/dev/null
        done <<< "$previous_booking_ids"
    fi

    booking_sql "delete from bookings where user_id = '$USER_ID';" >/dev/null
    flight_sql "delete from seat_reservations where flight_id = '$FLIGHT_ID';" >/dev/null
    flight_sql "update flights set available_seats = 180 where id = '$FLIGHT_ID';" >/dev/null
}

validate_create_response() {
    python3 - "$1" "$USER_ID" "$FLIGHT_ID" "$SEAT_COUNT" "$EXPECTED_TOTAL_PRICE" <<'PY'
import json
import sys
import uuid

path, user_id, flight_id, seat_count, total_price = sys.argv[1:]
body = json.load(open(path))

uuid.UUID(body["id"])
uuid.UUID(body["reservationId"])

assert body["status"] == "CONFIRMED", body
assert body["userId"] == user_id, body
assert body["flightId"] == flight_id, body
assert body["flightNumber"] == "SU-100", body
assert body["passengerName"] == "E2E Test Passenger", body
assert body["passengerEmail"] == "e2e.passenger@example.com", body
assert isinstance(body["seatCount"], int), body
assert body["seatCount"] == int(seat_count), body
assert isinstance(body["totalPrice"], int), body
assert body["totalPrice"] == int(total_price), body
PY
}

validate_get_response() {
    python3 - "$1" "$2" <<'PY'
import json
import sys

path, booking_id = sys.argv[1:]
body = json.load(open(path))

assert body["id"] == booking_id, body
assert body["status"] == "CONFIRMED", body
assert body["seatCount"] == 2, body
assert body["totalPrice"] == 10000, body
PY
}

validate_cancel_response() {
    python3 - "$1" "$2" <<'PY'
import json
import sys

path, booking_id = sys.argv[1:]
body = json.load(open(path))

assert body["id"] == booking_id, body
assert body["status"] == "CANCELLED", body
assert body["seatCount"] == 2, body
PY
}

extract_json_field() {
    python3 - "$1" "$2" <<'PY'
import json
import sys

path, field = sys.argv[1:]
print(json.load(open(path))[field])
PY
}

log "Starting full system with docker compose"
compose up --build -d --wait

wait_for_api
reset_test_state

log "Checking that the seed flight is visible through the public API"
FLIGHTS_STATUS="$(curl -sS -o /tmp/e2e-flights.json -w '%{http_code}' "$API_URL/flights?origin=SVO&destination=LED")"
expect_equal "$FLIGHTS_STATUS" "200" "GET /flights status"

python3 - "$FLIGHT_ID" <<'PY'
import json
import sys

flight_id = sys.argv[1]
flights = json.load(open("/tmp/e2e-flights.json"))
assert any(f["id"] == flight_id and f["availableSeats"] == 180 for f in flights), flights
PY

log "Creating booking through REST API"
CREATE_STATUS="$(
    curl -sS -o "$RESPONSE" -w '%{http_code}' \
        -H 'Content-Type: application/json' \
        -d "{
              \"user_id\": \"$USER_ID\",
              \"flight_id\": \"$FLIGHT_ID\",
              \"passenger_name\": \"$PASSENGER_NAME\",
              \"passenger_email\": \"$PASSENGER_EMAIL\",
              \"seat_count\": $SEAT_COUNT
            }" \
        "$API_URL/bookings"
)"
expect_equal "$CREATE_STATUS" "201" "POST /bookings status"
validate_create_response "$RESPONSE"

BOOKING_ID="$(extract_json_field "$RESPONSE" "id")"
RESERVATION_ID="$(extract_json_field "$RESPONSE" "reservationId")"

log "Reading created booking through REST API"
GET_STATUS="$(curl -sS -o "$RESPONSE" -w '%{http_code}' "$API_URL/bookings/$BOOKING_ID")"
expect_equal "$GET_STATUS" "200" "GET /bookings/{id} status"
validate_get_response "$RESPONSE" "$BOOKING_ID"

log "Checking booking and reservation in databases"
expect_equal "$(booking_sql "select status from bookings where id = '$BOOKING_ID';")" "CONFIRMED" "booking status in booking-db"
expect_equal "$(booking_sql "select seat_count from bookings where id = '$BOOKING_ID';")" "$SEAT_COUNT" "booking seat_count in booking-db"
expect_equal "$(booking_sql "select total_price from bookings where id = '$BOOKING_ID';")" "$EXPECTED_TOTAL_PRICE" "booking total_price in booking-db"
expect_equal "$(flight_sql "select available_seats from flights where id = '$FLIGHT_ID';")" "178" "available seats after reservation in flight-db"
expect_equal "$(flight_sql "select status from seat_reservations where id = '$RESERVATION_ID' and booking_id = '$BOOKING_ID';")" "ACTIVE" "reservation status in flight-db"

log "Cancelling booking through REST API"
CANCEL_STATUS="$(curl -sS -o "$RESPONSE" -w '%{http_code}' -X POST "$API_URL/bookings/$BOOKING_ID/cancel")"
expect_equal "$CANCEL_STATUS" "200" "POST /bookings/{id}/cancel status"
validate_cancel_response "$RESPONSE" "$BOOKING_ID"

log "Checking cancelled booking and released reservation in databases"
expect_equal "$(booking_sql "select status from bookings where id = '$BOOKING_ID';")" "CANCELLED" "cancelled booking status in booking-db"
expect_equal "$(booking_sql "select count(*) from bookings where id = '$BOOKING_ID' and cancelled_at is not null;")" "1" "booking cancelled_at in booking-db"
expect_equal "$(flight_sql "select available_seats from flights where id = '$FLIGHT_ID';")" "180" "available seats after cancellation in flight-db"
expect_equal "$(flight_sql "select status from seat_reservations where id = '$RESERVATION_ID';")" "RELEASED" "reservation status after cancellation in flight-db"
expect_equal "$(flight_sql "select count(*) from seat_reservations where id = '$RESERVATION_ID' and released_at is not null;")" "1" "reservation released_at in flight-db"

log "E2E booking flow passed"
