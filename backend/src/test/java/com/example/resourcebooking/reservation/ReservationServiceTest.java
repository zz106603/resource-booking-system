package com.example.resourcebooking.reservation;

import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ReservationServiceTest {

    final ReservationTransactions transactions = mock(ReservationTransactions.class);
    final ReservationService service = new ReservationService(transactions);
    final CreateReservationRequest request = new CreateReservationRequest(1L, "예약자",
            OffsetDateTime.parse("2030-01-01T01:00:00Z"), OffsetDateTime.parse("2030-01-01T02:00:00Z"));
    final ReservationResponse response = new ReservationResponse(1L, 1L, "예약자", request.startAt().toInstant(),
            request.endAt().toInstant(), ReservationStatus.CONFIRMED, Instant.parse("2030-01-01T00:00:00Z"), null);

    @Test
    void 해당_배타_제약의_23P01이면_중복으로_변환하고_재시도하지_않는다() {
        when(transactions.create(request)).thenThrow(sqlFailure("23P01", ReservationSqlErrors.OVERLAP_CONSTRAINT));
        assertThatThrownBy(() -> service.create(request)).isInstanceOfSatisfying(ReservationException.class,
                failure -> assertThat(failure.getReason()).isEqualTo(ReservationException.Reason.RESERVATION_CONFLICT));
        verify(transactions).create(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"23503", "23505", "40001", "55P03", "57014"})
    void 교착이_아닌_SQLSTATE는_중복으로_변환하거나_재시도하지_않는다(String state) {
        var failure = sqlFailure(state, ReservationSqlErrors.OVERLAP_CONSTRAINT);
        when(transactions.create(request)).thenThrow(failure);
        assertThatThrownBy(() -> service.create(request)).isSameAs(failure);
        verify(transactions).create(request);
    }

    @Test
    void 다른_이름의_배타_제약은_예약_중복으로_변환하지_않는다() {
        var failure = sqlFailure("23P01", "another_constraint");
        when(transactions.create(request)).thenThrow(failure);
        assertThatThrownBy(() -> service.create(request)).isSameAs(failure);
        verify(transactions).create(request);
    }

    @Test
    void 원인과_다음_예외에_감싸진_40P01도_찾아_재시도한다() {
        var wrapper = new SQLException("batch", "00000");
        wrapper.setNextException(new SQLException("deadlock", "40P01"));
        when(transactions.create(request)).thenThrow(new CannotAcquireLockException("wrapped", wrapper)).thenReturn(response);
        assertThat(service.create(request)).isEqualTo(response);
        verify(transactions, times(2)).create(request);
    }

    @Test
    void 교착_재시도_후_배타_위반이_발생하면_중복으로_종료한다() {
        when(transactions.create(request)).thenThrow(sqlFailure("40P01", null))
                .thenThrow(sqlFailure("23P01", ReservationSqlErrors.OVERLAP_CONSTRAINT));
        assertThatThrownBy(() -> service.create(request)).isInstanceOfSatisfying(ReservationException.class,
                failure -> assertThat(failure.getReason()).isEqualTo(ReservationException.Reason.RESERVATION_CONFLICT));
        verify(transactions, times(2)).create(request);
    }

    @Test
    void 교착이_계속되면_최초_포함_3회에서_소진_예외로_종료한다() {
        when(transactions.create(request)).thenThrow(sqlFailure("40P01", null));
        assertThatThrownBy(() -> service.create(request)).isInstanceOf(ReservationRetryExhaustedException.class);
        verify(transactions, times(3)).create(request);
    }

    @Test
    void 취소도_교착일_때만_재시도한다() {
        when(transactions.cancel(1L)).thenThrow(sqlFailure("40P01", null)).thenReturn(response);
        assertThat(service.cancel(1L)).isEqualTo(response);
        verify(transactions, times(2)).cancel(1L);
    }

    @Test
    void 대기_중_인터럽트되면_재시도를_멈추고_인터럽트_상태를_보존한다() {
        when(transactions.create(request)).thenThrow(sqlFailure("40P01", null));
        try {
            Thread.currentThread().interrupt();
            assertThatThrownBy(() -> service.create(request)).isInstanceOf(ReservationRetryExhaustedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(transactions).create(request);
        } finally {
            Thread.interrupted();
        }
    }

    private RuntimeException sqlFailure(String state, String constraint) {
        String message = "SERROR\0C" + state + "\0Mtest database failure\0"
                + (constraint == null ? "" : "n" + constraint + "\0") + "\0";
        return new DataIntegrityViolationException("database failure", new PSQLException(new ServerErrorMessage(message)));
    }
}
