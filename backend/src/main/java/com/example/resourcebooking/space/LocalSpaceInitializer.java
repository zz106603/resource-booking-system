package com.example.resourcebooking.space;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
public class LocalSpaceInitializer implements ApplicationRunner {
    private final SpaceRepository spaces;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public LocalSpaceInitializer(SpaceRepository spaces, JdbcTemplate jdbc, Clock clock) {
        this.spaces = spaces;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Protect the empty-table check even when two local instances start together.
        // This lock is held only for this short startup transaction.
        jdbc.execute("LOCK TABLE spaces IN SHARE ROW EXCLUSIVE MODE");
        if (spaces.count() != 0) return;

        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        spaces.saveAll(List.of(
                new Space("회의실 A", "소규모 회의를 위한 6인 회의실", 6, now),
                new Space("회의실 B", "화이트보드가 있는 10인 회의실", 10, now),
                new Space("스터디룸", "집중 학습을 위한 4인 스터디룸", 4, now),
                new Space("세미나실", "발표와 워크숍을 위한 20인 세미나실", 20, now)));
    }
}
