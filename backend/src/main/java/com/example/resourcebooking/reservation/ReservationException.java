package com.example.resourcebooking.reservation;

public class ReservationException extends RuntimeException {

    public enum Reason {
        INVALID_RESERVATION("예약 시간 구간과 정밀도를 확인해 주세요."),
        RESERVATION_NOT_FOUND("예약을 찾을 수 없습니다."),
        RESERVATION_CONFLICT("해당 공간에 시간이 겹치는 확정 예약이 있습니다."),
        RESERVATION_ALREADY_STARTED("시작 시각에 도달한 예약은 취소할 수 없습니다.");

        private final String message;

        Reason(String message) {
            this.message = message;
        }
    }

    private final Reason reason;

    public ReservationException(Reason reason) {
        super(reason.message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
