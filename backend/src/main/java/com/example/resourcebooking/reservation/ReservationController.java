package com.example.resourcebooking.reservation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService service;
    private final ReservationQueryService queries;

    public ReservationController(ReservationService service, ReservationQueryService queries) {
        this.service = service;
        this.queries = queries;
    }

    @GetMapping
    public ReservationPageResponse list(
            @RequestParam(name = "spaceId", required = false) String spaceId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size) {
        return queries.list(spaceId, status, from, to, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(@Valid @RequestBody CreateReservationRequest request) {
        return service.create(request);
    }

    @PostMapping("/{id}/cancel")
    public ReservationResponse cancel(@PathVariable("id") @Positive long id) {
        return service.cancel(id);
    }
}
