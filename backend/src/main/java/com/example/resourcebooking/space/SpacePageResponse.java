package com.example.resourcebooking.space;

import java.util.List;

import org.springframework.data.domain.Page;

public record SpacePageResponse(List<SpaceResponse> content, int page, int size,
                                long totalElements, int totalPages) {

    static SpacePageResponse from(Page<Space> spaces) {
        return new SpacePageResponse(spaces.getContent().stream().map(SpaceResponse::from).toList(),
                spaces.getNumber(), spaces.getSize(), spaces.getTotalElements(), spaces.getTotalPages());
    }
}
