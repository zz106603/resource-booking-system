package com.example.resourcebooking.reservation;

public class ReservationRetryExhaustedException extends RuntimeException {

    public ReservationRetryExhaustedException(Throwable cause) {
        super("예약 트랜잭션을 완료하지 못했습니다.", cause);
    }
}
