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

    /**
     * How many have registered for the workshop, and whether someone with this normalised email
     * ({@link WorkshopRegistrationService#emailKey}) is one of them, in one round trip.
     */
    public Tally tally(long workshopId, String emailKey) {
        return jdbc.queryForObject("SELECT COUNT(*) AS registrations, COALESCE(bool_or(email_key = ?), false) AS taken "
                        + "FROM registration WHERE workshop_id = ?",
                (rs, n) -> new Tally(rs.getInt("registrations"), rs.getBoolean("taken")), emailKey, workshopId);
    }

    /** A workshop's number of registrations and whether one email is among them. */
    public record Tally(int registrations, boolean emailTaken) {
    }

    public Registration insert(long workshopId, String name, String email, String emailKey) {
        return jdbc.queryForObject("INSERT INTO registration (workshop_id, name, email, email_key) VALUES (?, ?, ?, ?) "
                + "RETURNING id, workshop_id, name, email", MAPPER, workshopId, name, email, emailKey);
    }
}
