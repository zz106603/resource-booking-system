package com.example.resourcebooking.reservation;

import java.util.List;

import org.springframework.data.domain.Page;

public record ReservationPageResponse(List<ReservationResponse> content, int page, int size,
                                      long totalElements, int totalPages) {

    static ReservationPageResponse from(Page<Reservation> reservations) {
        return new ReservationPageResponse(reservations.getContent().stream().map(Reservation::toResponse).toList(),
                reservations.getNumber(), reservations.getSize(),
                reservations.getTotalElements(), reservations.getTotalPages());
    }
}
