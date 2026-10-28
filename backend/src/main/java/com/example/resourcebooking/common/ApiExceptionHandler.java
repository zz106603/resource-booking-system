package com.example.resourcebooking.common;

import java.util.List;

import com.example.resourcebooking.reservation.ReservationException;
import com.example.resourcebooking.reservation.ReservationRetryExhaustedException;
import com.example.resourcebooking.space.InvalidPaginationException;
import com.example.resourcebooking.space.SpaceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ReservationException.class)
    ResponseEntity<ApiError> reservationFailure(ReservationException exception) {
        HttpStatus status = switch (exception.getReason()) {
            case INVALID_RESERVATION -> HttpStatus.BAD_REQUEST;
            case RESERVATION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case RESERVATION_CONFLICT, RESERVATION_ALREADY_STARTED -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(new ApiError(
                exception.getReason().name(), exception.getMessage(), List.of()));
    }

    @ExceptionHandler({PessimisticLockingFailureException.class, QueryTimeoutException.class,
            TransactionTimedOutException.class, ReservationRetryExhaustedException.class})
    ResponseEntity<ApiError> temporaryDatabaseFailure(Exception exception) {
        log.warn("Temporary database concurrency failure", exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("TEMPORARY_DATABASE_FAILURE", "잠시 후 다시 요청해 주세요.", List.of()));
    }

    @ExceptionHandler(InvalidPaginationException.class)
    ResponseEntity<ApiError> invalidPagination(InvalidPaginationException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_REQUEST", exception.getMessage(), List.of()));
    }

    @ExceptionHandler(SpaceNotFoundException.class)
    ResponseEntity<ApiError> notFound(SpaceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("SPACE_NOT_FOUND", exception.getMessage(), List.of()));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        var fields = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return new ResponseEntity<>(new ApiError("VALIDATION_ERROR", "입력값을 확인해 주세요.", fields),
                headers, status);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String code = status.is5xxServerError() ? "INTERNAL_ERROR" : "INVALID_REQUEST";
        String message = status.is5xxServerError()
                ? "서버 오류가 발생했습니다." : "요청 형식이나 입력값을 확인해 주세요.";
        return super.handleExceptionInternal(exception, new ApiError(code, message, List.of()),
                headers, status, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception) {
        log.error("Unexpected request failure", exception);
        return ResponseEntity.internalServerError()
                .body(new ApiError("INTERNAL_ERROR", "서버 오류가 발생했습니다.", List.of()));
    }
}
