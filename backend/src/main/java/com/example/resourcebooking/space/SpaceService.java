package com.example.resourcebooking.space;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SpaceService {

    private final SpaceRepository repository;
    private final Clock clock;

    public SpaceService(SpaceRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public SpaceResponse create(CreateSpaceRequest request) {
        Space space = repository.save(new Space(request.name(), request.description(),
                request.capacity(), clock.instant().truncatedTo(ChronoUnit.MICROS)));
        return SpaceResponse.from(space);
    }

    public SpaceResponse find(long id) {
        return SpaceResponse.from(repository.findById(id).orElseThrow(SpaceNotFoundException::new));
    }

    public SpacePageResponse list(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("id"));
        if (pageRequest.getOffset() > Integer.MAX_VALUE) {
            throw new InvalidPaginationException();
        }
        return SpacePageResponse.from(repository.findAll(pageRequest));
    }
}
