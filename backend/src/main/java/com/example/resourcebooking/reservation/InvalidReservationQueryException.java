package com.example.resourcebooking.reservation;

public class InvalidReservationQueryException extends RuntimeException {

    public InvalidReservationQueryException() {
        super("예약 조회 조건과 페이지 입력값을 확인해 주세요.");
    }
}
