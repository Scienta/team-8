package no.scienta.cfp;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Remembers which versions of the app have opened their port, so that a start-up can tell a deploy (a new version)
 * from a cold start after scaling to zero (a version that has opened before).
 */
@Repository
class Deployments {

    private final JdbcTemplate jdbc;

    Deployments(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    boolean hasOpened(String version) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM deployment WHERE version = ?)", Boolean.class, version));
    }

    void recordOpened(String version) {
        jdbc.update("INSERT INTO deployment (version) VALUES (?) ON CONFLICT DO NOTHING", version);
    }
}
