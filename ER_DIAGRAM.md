# ER Diagram

```mermaid
erDiagram
    FLIGHTS {
        UUID id PK
        VARCHAR flight_number
        TIMESTAMPTZ departure_date
        VARCHAR airline
        VARCHAR departure_airport_iata
        VARCHAR arrival_airport_iata
        TIMESTAMPTZ departure_time
        TIMESTAMPTZ arrival_time
        INT total_seats
        INT available_seats
        BIGINT ticket_price
        VARCHAR status
    }

    SEAT_RESERVATIONS {
        UUID id PK
        UUID flight_id FK
        UUID booking_id UK
        INT reserved_seats
        VARCHAR status
        TIMESTAMPTZ created_at
        TIMESTAMPTZ released_at
    }

    BOOKINGS {
        UUID id PK
        UUID user_id
        UUID flight_id
        VARCHAR passenger_name
        VARCHAR passenger_email
        INT seat_count
        BIGINT total_price
        UUID reservation_id
        VARCHAR status
        TIMESTAMPTZ created_at
        TIMESTAMPTZ cancelled_at
    }

    FLIGHTS ||--o{ SEAT_RESERVATIONS : has
```

## 3NF notes

- `Flight` stores only attributes of one concrete flight.
- `SeatReservation` stores only attributes of one reservation and references exactly one flight.
- `Booking` stores only attributes of one booking in Booking Service.
- Redundant columns like passenger first/last name split and flight snapshot fields are removed from `Booking`, so non-key attributes depend only on the booking key.

## Integrity constraints

- `flights.total_seats > 0`
- `flights.available_seats >= 0`
- `flights.available_seats <= flights.total_seats`
- `flights.ticket_price > 0`
- `flights.arrival_time > flights.departure_time`
- `flights.departure_airport_iata <> flights.arrival_airport_iata`
- unique flight: `UNIQUE(flight_number, departure_date)`
- `seat_reservations.reserved_seats > 0`
- one booking -> one reservation: `UNIQUE(booking_id)`
- `bookings.seat_count > 0`
- `bookings.total_price > 0`

