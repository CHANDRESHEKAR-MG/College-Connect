package com.collegeconnect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CollegeConnectApplicationTests extends IntegrationTestSupport {

    @Autowired
    private Flyway flyway;

    @Test
    void allSchemaMigrationsAreAppliedInOrder() {
        String[] appliedVersions = Arrays.stream(flyway.info().applied())
                .map(migration -> migration.getVersion().getVersion())
                .toArray(String[]::new);

        assertThat(appliedVersions).containsExactly("1", "2", "3", "4", "5");
    }

    @Test
    void contextLoads() {
    }

    @Test
    void latestSchemaMigrationIsApplied() {
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("5");
    }
}
