CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flight_number VARCHAR(16) NOT NULL,
    flight_departure_date DATE NOT NULL,
    passenger_first_name VARCHAR(100) NOT NULL,
    passenger_last_name VARCHAR(100) NOT NULL,
    passenger_email VARCHAR(255) NOT NULL,
    seat_count INTEGER NOT NULL CHECK (seat_count > 0),
    total_price BIGINT NOT NULL CHECK (total_price > 0),
    reservation_id UUID,
    status VARCHAR(32) NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    cancelled_at TIMESTAMPTZ
);

CREATE INDEX idx_bookings_flight_lookup
    ON bookings (flight_number, flight_departure_date);

CREATE INDEX idx_bookings_passenger_email
    ON bookings (passenger_email);
