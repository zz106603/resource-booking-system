package com.example.resourcebooking.reservation;

import java.time.Instant;

public record ReservationResponse(Long id, Long spaceId, String bookerName, Instant startAt,
                                  Instant endAt, ReservationStatus status,
                                  Instant createdAt, Instant cancelledAt) {
}
