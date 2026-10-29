package com.example.resourcebooking.reservation;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class OffsetDateTimeStringDeserializer extends JsonDeserializer<OffsetDateTime> {

    @Override
    public OffsetDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return (OffsetDateTime) context.handleUnexpectedToken(OffsetDateTime.class, parser);
        }
        String value = parser.getText();
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            return (OffsetDateTime) context.handleWeirdStringValue(OffsetDateTime.class, value,
                    "오프셋을 포함한 ISO-8601 시각 문자열이어야 합니다.");
        }
    }
}
