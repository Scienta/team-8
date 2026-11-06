package no.scienta.cfp;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Stores who registered for which workshop, in the order they registered.
 */
@Repository
public class RegistrationDao {

    private static final RowMapper<Registration> MAPPER = (rs, n) -> new Registration(rs.getLong("id"),
            rs.getLong("workshop_id"), rs.getString("name"), rs.getString("email"));

    private final JdbcTemplate jdbc;

    public RegistrationDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The workshop's registrations, in the order they were made. */
    public List<Registration> findByWorkshop(long workshopId) {
        return jdbc.query("SELECT id, workshop_id, name, email FROM registration WHERE workshop_id = ? ORDER BY id",
                MAPPER, workshopId);
    }

    public int count(long workshopId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM registration WHERE workshop_id = ?", Integer.class,
                workshopId);
        return n == null ? 0 : n;
    }

    /** Whether someone with this normalised email ({@link WorkshopRegistrationService#emailKey}) is registered. */
    public boolean isRegistered(long workshopId, String emailKey) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM registration WHERE workshop_id = ? AND email_key = ?",
                Integer.class, workshopId, emailKey);
        return n != null && n > 0;
    }

    public Registration insert(long workshopId, String name, String email, String emailKey) {
        return jdbc.queryForObject("INSERT INTO registration (workshop_id, name, email, email_key) VALUES (?, ?, ?, ?) "
                + "RETURNING id, workshop_id, name, email", MAPPER, workshopId, name, email, emailKey);
    }
}
