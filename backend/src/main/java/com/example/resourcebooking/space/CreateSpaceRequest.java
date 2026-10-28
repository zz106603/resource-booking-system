package com.example.resourcebooking.space;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateSpaceRequest(
        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 100, message = "이름은 100자 이하여야 합니다.") String name,
        @Size(max = 2000, message = "설명은 2000자 이하여야 합니다.") String description,
        @NotNull(message = "수용 인원은 필수입니다.")
        @Positive(message = "수용 인원은 양의 정수여야 합니다.") Integer capacity) {
}
