package com.example.resourcebooking.reservation;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.example.resourcebooking.space.SpaceNotFoundException;
import com.example.resourcebooking.space.SpaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.example.resourcebooking.reservation.ReservationException.Reason.INVALID_RESERVATION;
import static com.example.resourcebooking.reservation.ReservationException.Reason.RESERVATION_NOT_FOUND;
import static com.example.resourcebooking.reservation.ReservationException.Reason.RESERVATION_CONFLICT;

@Service
public class ReservationTransactions {

    private static final Instant MIN_TIME = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant MAX_TIME = Instant.parse("+10000-01-01T00:00:00Z");

    private final ReservationRepository reservations;
    private final SpaceRepository spaces;
    private final ReservationInsertRepository inserts;
    private final Clock clock;

    public ReservationTransactions(ReservationRepository reservations, SpaceRepository spaces,
                                   ReservationInsertRepository inserts, Clock clock) {
        this.reservations = reservations;
        this.spaces = spaces;
        this.inserts = inserts;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED, timeout = 15)
    public ReservationResponse create(CreateReservationRequest request) {
        Instant start = request.startAt().toInstant();
        Instant end = request.endAt().toInstant();
        if (!start.isBefore(end) || start.isBefore(MIN_TIME) || !end.isBefore(MAX_TIME)
                || start.getNano() % 1000 != 0 || end.getNano() % 1000 != 0) {
            throw new ReservationException(INVALID_RESERVATION);
        }
        if (!spaces.existsById(request.spaceId())) throw new SpaceNotFoundException();
        Instant now = clock.instant();
        if (!start.isAfter(now)) throw new ReservationException(INVALID_RESERVATION);
        var inserted = inserts.insert(request.spaceId(), request.bookerName(), start, end,
                now.truncatedTo(ChronoUnit.MICROS));
        // Exclusion/FK checks can wait: validate again after the INSERT has completed.
        if (!start.isAfter(clock.instant())) throw new ReservationException(INVALID_RESERVATION);
        return inserted.orElseThrow(() -> new ReservationException(RESERVATION_CONFLICT));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED, timeout = 15)
    public ReservationResponse cancel(long id) {
        Reservation reservation = reservations.findByIdForUpdate(id)
                .orElseThrow(() -> new ReservationException(RESERVATION_NOT_FOUND));
        reservation.cancel(clock.instant().truncatedTo(ChronoUnit.MICROS));
        return reservation.toResponse();
    }
}
