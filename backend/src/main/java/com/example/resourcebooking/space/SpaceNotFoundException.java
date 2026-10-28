package com.example.resourcebooking.space;

public class SpaceNotFoundException extends RuntimeException {

    public SpaceNotFoundException() {
        super("공간을 찾을 수 없습니다.");
    }
}
