package com.example.resourcebooking.reservation;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.example.resourcebooking.reservation.ReservationException.Reason.RESERVATION_CONFLICT;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class ReservationService {

    static final int MAX_ATTEMPTS = 3;
    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);
    private final ReservationTransactions transactions;

    public ReservationService(ReservationTransactions transactions) {
        this.transactions = transactions;
    }

    public ReservationResponse create(CreateReservationRequest request) {
        return execute(() -> transactions.create(request));
    }

    public ReservationResponse cancel(long id) {
        return execute(() -> transactions.cancel(id));
    }

    private ReservationResponse execute(Supplier<ReservationResponse> operation) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return operation.get();
            } catch (RuntimeException failure) {
                if (ReservationSqlErrors.isOverlap(failure)) throw new ReservationException(RESERVATION_CONFLICT);
                if (!ReservationSqlErrors.isDeadlock(failure)) throw failure;
                lastFailure = failure;
                if (attempt < MAX_ATTEMPTS) {
                    log.warn("Retrying reservation after deadlock, attempt {}/{}", attempt + 1, MAX_ATTEMPTS);
                    try {
                        Thread.sleep(ThreadLocalRandom.current().nextLong(10, 31));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new ReservationRetryExhaustedException(interrupted);
                    }
                }
            }
        }
        throw new ReservationRetryExhaustedException(lastFailure);
    }
}
