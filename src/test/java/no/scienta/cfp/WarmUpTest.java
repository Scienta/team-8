package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The warm-up traffic: every request is answered without a server error, and none of them writes to the database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WarmUpTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void everyRequestIsAnsweredWithoutAServerError() {
        Map<String, Integer> answers = WarmUpBeforeTraffic.send(port, 1, Duration.ofSeconds(30));

        assertEquals(WarmUpTraffic.REQUESTS.size(), answers.values().stream().mapToInt(Integer::intValue).sum());
        answers.keySet().forEach(status -> assertTrue(status.matches("[234]\\d\\d"), "answers: " + answers));
    }

    @Test
    void theRequestsLeaveTheDatabaseAsItWas() {
        Map<String, Long> before = rowsPerTable();

        WarmUpBeforeTraffic.send(port, 2, Duration.ofSeconds(30));

        assertEquals(before, rowsPerTable());
        assertEquals(0L, before.get("speaker"), "the warm-up at start-up wrote speakers");
        assertEquals(0L, before.get("talk"), "the warm-up at start-up wrote talks");
    }

    /** Rows in each table of the schema, and the next value of each sequence, as ids taken also count as writes. */
    private Map<String, Long> rowsPerTable() {
        Map<String, Long> rows = new LinkedHashMap<>();
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name",
                String.class);
        for (String table : tables) {
            rows.put(table, jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class));
        }
        List<String> sequences = jdbc.queryForList(
                "SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = 'public' "
                        + "ORDER BY sequence_name", String.class);
        for (String sequence : sequences) {
            rows.put(sequence, jdbc.queryForObject("SELECT last_value FROM " + sequence, Long.class));
        }
        return rows;
    }
}
