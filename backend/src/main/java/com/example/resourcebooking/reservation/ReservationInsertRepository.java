package com.example.resourcebooking.reservation;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReservationInsertRepository {

    private final JdbcTemplate jdbc;

    public ReservationInsertRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ReservationResponse> insert(long spaceId, String name, Instant start, Instant end, Instant now) {
        var rows = jdbc.query("""
                INSERT INTO reservations(space_id, booker_name, start_at, end_at, status, created_at)
                VALUES (?, ?, ?, ?, 'CONFIRMED', ?)
                ON CONFLICT ON CONSTRAINT reservations_confirmed_no_overlap DO NOTHING
                RETURNING id, space_id, booker_name, start_at, end_at, status, created_at, cancelled_at
                """, statement -> {
            statement.setLong(1, spaceId);
            statement.setString(2, name);
            statement.setObject(3, start.atOffset(ZoneOffset.UTC));
            statement.setObject(4, end.atOffset(ZoneOffset.UTC));
            statement.setObject(5, now.atOffset(ZoneOffset.UTC));
        }, (result, index) -> {
            OffsetDateTime cancelledAt = result.getObject("cancelled_at", OffsetDateTime.class);
            return new ReservationResponse(result.getLong("id"), result.getLong("space_id"),
                    result.getString("booker_name"), result.getObject("start_at", OffsetDateTime.class).toInstant(),
                    result.getObject("end_at", OffsetDateTime.class).toInstant(),
                    ReservationStatus.valueOf(result.getString("status")),
                    result.getObject("created_at", OffsetDateTime.class).toInstant(),
                    cancelledAt == null ? null : cancelledAt.toInstant());
        });
        return rows.stream().findFirst();
    }
}
