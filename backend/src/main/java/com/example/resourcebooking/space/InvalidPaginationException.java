package com.example.resourcebooking.space;

public class InvalidPaginationException extends RuntimeException {

    public InvalidPaginationException() {
        super("page와 size의 곱은 2147483647 이하여야 합니다.");
    }
}
