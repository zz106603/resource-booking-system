package com.example.resourcebooking.reservation;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long spaceId;

    @Column(nullable = false, length = 100, updatable = false)
    private String bookerName;

    @Column(nullable = false, updatable = false)
    private Instant startAt;

    @Column(nullable = false, updatable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReservationStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant cancelledAt;

    protected Reservation() {
    }

    Reservation(long spaceId, String bookerName, Instant startAt, Instant endAt, Instant createdAt) {
        this.spaceId = spaceId;
        this.bookerName = bookerName;
        this.startAt = startAt;
        this.endAt = endAt;
        this.createdAt = createdAt;
        this.status = ReservationStatus.CONFIRMED;
    }

    void cancel(Instant now) {
        if (status == ReservationStatus.CANCELLED) {
            return;
        }
        if (!startAt.isAfter(now)) {
            throw new ReservationException(ReservationException.Reason.RESERVATION_ALREADY_STARTED);
        }
        status = ReservationStatus.CANCELLED;
        cancelledAt = now;
    }

    ReservationResponse toResponse() {
        return new ReservationResponse(id, spaceId, bookerName, startAt, endAt, status, createdAt, cancelledAt);
    }
}
