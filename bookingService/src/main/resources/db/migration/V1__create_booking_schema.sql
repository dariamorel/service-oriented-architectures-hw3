CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    flight_id UUID,
    passenger_name VARCHAR(255) NOT NULL,
    passenger_email VARCHAR(255) NOT NULL,
    seat_count INTEGER NOT NULL CHECK (seat_count > 0),
    total_price BIGINT NOT NULL CHECK (total_price > 0),
    reservation_id UUID,
    status VARCHAR(32) NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    cancelled_at TIMESTAMPTZ
);

CREATE INDEX idx_bookings_user_id
    ON bookings (user_id);

CREATE INDEX idx_bookings_flight_id
    ON bookings (flight_id);

CREATE INDEX idx_bookings_passenger_email
    ON bookings (passenger_email);
