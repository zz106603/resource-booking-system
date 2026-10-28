package com.example.resourcebooking.reservation;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.function.Predicate;

import org.postgresql.util.PSQLException;

final class ReservationSqlErrors {

    static final String OVERLAP_CONSTRAINT = "reservations_confirmed_no_overlap";

    private ReservationSqlErrors() {
    }

    static boolean isOverlap(Throwable failure) {
        return anySqlException(failure, sql -> sql instanceof PSQLException postgres
                && "23P01".equals(postgres.getSQLState())
                && postgres.getServerErrorMessage() != null
                && OVERLAP_CONSTRAINT.equals(postgres.getServerErrorMessage().getConstraint()));
    }

    static boolean isDeadlock(Throwable failure) {
        return anySqlException(failure, sql -> "40P01".equals(sql.getSQLState()));
    }

    private static boolean anySqlException(Throwable failure, Predicate<SQLException> match) {
        var pending = new ArrayDeque<Throwable>();
        var visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        pending.add(failure);
        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (!visited.add(current)) continue;
            if (current instanceof SQLException sql) {
                if (match.test(sql)) return true;
                if (sql.getNextException() != null) pending.add(sql.getNextException());
            }
            if (current.getCause() != null) pending.add(current.getCause());
        }
        return false;
    }
}
