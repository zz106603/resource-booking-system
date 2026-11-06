package com.example.resourcebooking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("local")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
class LocalSpaceInitializerIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.11-alpine");

    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;

    @Test
    void local로_시작하면_샘플_공간_네_개가_실제_DB에_등록된다() {
        assertThat(jdbc.queryForObject("select count(*) from spaces", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForList("select name from spaces order by id", String.class))
                .containsExactly("회의실 A", "회의실 B", "스터디룸", "세미나실");
        assertThat(jdbc.queryForList("select capacity from spaces order by id", Integer.class))
                .containsExactly(6, 10, 4, 20);
        assertThat(jdbc.queryForObject("select count(*) from reservations", Integer.class)).isZero();
    }

    @Test
    void 초기화를_다시_실행해도_기존_샘플의_ID와_내용이_유지된다() throws Exception {
        var before = jdbc.queryForList("select * from spaces order by id");
        initialize();
        assertThat(jdbc.queryForList("select * from spaces order by id")).isEqualTo(before);
    }

    @Test
    void 공간이_비어_있으면_초기화로_네_개를_다시_등록한다() throws Exception {
        jdbc.update("delete from spaces");
        initialize();
        assertThat(jdbc.queryForObject("select count(*) from spaces", Integer.class)).isEqualTo(4);
    }

    @Test
    void 기존_공간과_예약이_있으면_샘플을_추가하거나_데이터를_변경하지_않는다() throws Exception {
        jdbc.update("delete from spaces");
        Long space = jdbc.queryForObject("insert into spaces(name,description,capacity) values ('기존 공간','보존',3) returning id", Long.class);
        jdbc.update("insert into reservations(space_id,booker_name,start_at,end_at,status,created_at) "
                + "values (?,'기존 예약',now()+interval '1 day',now()+interval '2 days','CONFIRMED',now())", space);
        var spaces = jdbc.queryForList("select * from spaces order by id");
        var reservations = jdbc.queryForList("select * from reservations order by id");
        initialize();
        assertThat(jdbc.queryForList("select * from spaces order by id")).isEqualTo(spaces);
        assertThat(jdbc.queryForList("select * from reservations order by id")).isEqualTo(reservations);
    }

    private void initialize() throws Exception {
        ApplicationArguments args = new DefaultApplicationArguments(new String[0]);
        context.getBean("localSpaceInitializer", ApplicationRunner.class).run(args);
    }
}
