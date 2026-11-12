package com.collegeconnect;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class FlywayMigrationTest {

    @Test
    void allMigrationsApplyToAFreshH2Database() throws Exception {
        String url = "jdbc:h2:mem:collegeconnect-migration-" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        Flyway flyway = Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load();

        flyway.migrate();

        String[] appliedVersions = Arrays.stream(flyway.info().applied())
                .map(migration -> migration.getVersion().getVersion())
                .toArray(String[]::new);
        assertThat(appliedVersions).containsExactly("1", "2", "3", "4", "5");

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement();
                ResultSet columns = statement.executeQuery(
                        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                                + "WHERE TABLE_NAME = 'WHATSAPP_WEBHOOK_EVENT' "
                                + "AND COLUMN_NAME IN ('PAYLOAD_JSON', 'PROCESSING_STATUS', "
                                + "'PROCESSING_ATTEMPTS', 'PROCESSING_ERROR', 'PROCESSED_AT')")) {
            assertThat(columns.next()).isTrue();
            assertThat(columns.getInt(1)).isEqualTo(5);
        }

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement();
                ResultSet preferences =
                        statement.executeQuery("SELECT category FROM notification_default_preference")) {
            Set<String> categories = new java.util.HashSet<>();
            while (preferences.next()) {
                categories.add(preferences.getString(1));
            }
            assertThat(categories).hasSize(10).contains("ALL_MESSAGES", "ATTENDANCE_ABSENT", "SEE");
        }
    }
}
