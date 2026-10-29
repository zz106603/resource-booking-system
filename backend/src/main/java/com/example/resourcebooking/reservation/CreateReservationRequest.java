package com.example.resourcebooking.reservation;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateReservationRequest(
        @NotNull @Positive Long spaceId,
        @NotBlank @Size(max = 100) String bookerName,
        @NotNull @JsonDeserialize(using = OffsetDateTimeStringDeserializer.class) OffsetDateTime startAt,
        @NotNull @JsonDeserialize(using = OffsetDateTimeStringDeserializer.class) OffsetDateTime endAt) {
}
