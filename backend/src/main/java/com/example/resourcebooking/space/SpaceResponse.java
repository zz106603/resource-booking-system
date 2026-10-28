package com.example.resourcebooking.space;

import java.time.Instant;

public record SpaceResponse(Long id, String name, String description, int capacity, Instant createdAt) {

    static SpaceResponse from(Space space) {
        return new SpaceResponse(space.getId(), space.getName(), space.getDescription(),
                space.getCapacity(), space.getCreatedAt());
    }
}
