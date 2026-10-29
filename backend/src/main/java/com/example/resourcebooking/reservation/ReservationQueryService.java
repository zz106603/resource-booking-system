package com.example.resourcebooking.reservation;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;

import com.example.resourcebooking.space.SpaceNotFoundException;
import com.example.resourcebooking.space.SpaceRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ReservationQueryService {

    private static final Instant MIN_TIME = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant MAX_TIME = Instant.parse("+10000-01-01T00:00:00Z");

    private final ReservationRepository reservations;
    private final SpaceRepository spaces;

    public ReservationQueryService(ReservationRepository reservations, SpaceRepository spaces) {
        this.reservations = reservations;
        this.spaces = spaces;
    }

    public ReservationPageResponse list(String spaceId, String status, String from, String to,
                                        String page, String size) {
        Long space = parseSpace(spaceId);
        ReservationStatus state = parseStatus(status);
        if ((from == null) != (to == null)) throw new InvalidReservationQueryException();
        Instant start = parseTime(from);
        Instant end = parseTime(to);
        if (start != null && !start.isBefore(end)) throw new InvalidReservationQueryException();
        int pageNumber = parseNumber(page, 0);
        int pageSize = parseNumber(size, 20);
        if (pageNumber < 0 || pageSize < 1 || pageSize > 100
                || (long) pageNumber * pageSize > Integer.MAX_VALUE) {
            throw new InvalidReservationQueryException();
        }
        if (space != null && !spaces.existsById(space)) throw new SpaceNotFoundException();
        var pageable = PageRequest.of(pageNumber, pageSize, Sort.by("startAt", "id"));
        var result = reservations.findAll((root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (space != null) predicates.add(cb.equal(root.get("spaceId"), space));
            if (state != null) predicates.add(cb.equal(root.get("status"), state));
            if (start != null) {
                predicates.add(cb.lessThan(root.<Instant>get("startAt"), end));
                predicates.add(cb.greaterThan(root.<Instant>get("endAt"), start));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        }, pageable);
        return ReservationPageResponse.from(result);
    }

    private static Long parseSpace(String value) {
        if (value == null) return null;
        try {
            long id = Long.parseLong(value);
            if (id <= 0) throw new InvalidReservationQueryException();
            return id;
        } catch (NumberFormatException exception) {
            throw new InvalidReservationQueryException();
        }
    }

    private static ReservationStatus parseStatus(String value) {
        if (value == null) return null;
        try {
            return ReservationStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidReservationQueryException();
        }
    }

    private static int parseNumber(String value, int defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new InvalidReservationQueryException();
        }
    }

    private static Instant parseTime(String value) {
        if (value == null) return null;
        try {
            Instant time = OffsetDateTime.parse(value).toInstant();
            if (time.isBefore(MIN_TIME) || !time.isBefore(MAX_TIME) || time.getNano() % 1000 != 0) {
                throw new InvalidReservationQueryException();
            }
            return time;
        } catch (DateTimeParseException exception) {
            throw new InvalidReservationQueryException();
        }
    }
}
