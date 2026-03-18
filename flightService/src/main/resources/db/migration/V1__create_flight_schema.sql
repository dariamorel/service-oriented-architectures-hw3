CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE flights (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flight_number VARCHAR(16) NOT NULL,
    departure_date TIMESTAMPTZ NOT NULL,
    airline VARCHAR(120) NOT NULL,
    departure_airport_iata VARCHAR(3) NOT NULL,
    arrival_airport_iata VARCHAR(3) NOT NULL,
    departure_time TIMESTAMPTZ NOT NULL,
    arrival_time TIMESTAMPTZ NOT NULL,
    total_seats INTEGER NOT NULL CHECK (total_seats > 0),
    available_seats INTEGER NOT NULL CHECK (available_seats >= 0 AND available_seats <= total_seats),
    ticket_price BIGINT NOT NULL CHECK (ticket_price > 0),
    status VARCHAR(32) NOT NULL CHECK (status IN ('SCHEDULED', 'DEPARTED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT uq_flights_number_date UNIQUE (flight_number, departure_date),
    CONSTRAINT chk_flights_airports CHECK (departure_airport_iata <> arrival_airport_iata),
    CONSTRAINT chk_flights_timeline CHECK (arrival_time > departure_time)
);

CREATE TABLE seat_reservations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flight_id UUID NOT NULL REFERENCES flights (id) ON DELETE RESTRICT,
    booking_id UUID NOT NULL UNIQUE,
    reserved_seats INTEGER NOT NULL CHECK (reserved_seats > 0),
    status VARCHAR(32) NOT NULL CHECK (status IN ('ACTIVE', 'RELEASED', 'EXPIRED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    released_at TIMESTAMPTZ
);

CREATE INDEX idx_seat_reservations_flight_id
    ON seat_reservations (flight_id);
