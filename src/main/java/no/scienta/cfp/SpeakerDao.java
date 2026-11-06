package no.scienta.cfp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SpeakerDao {

    private final JdbcTemplate jdbc;

    public SpeakerDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> findById(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, name, email, bio FROM speaker WHERE id = ?", id);
        if (rows.isEmpty()) {
            return null;
        }
        return rows.get(0);
    }

    public boolean exists(long id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM speaker WHERE id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    /**
     * Locks the speaker's row until the transaction ends, so whatever adds to their talks is applied one at a time.
     * Returns the speaker's email, or null if there is no such speaker, so that one round trip also tells whether
     * they exist.
     */
    public String lockForUpdate(long id) {
        List<String> emails = jdbc.queryForList("SELECT email FROM speaker WHERE id = ? FOR UPDATE", String.class, id);
        return emails.isEmpty() ? null : emails.get(0);
    }

    /**
     * The speaker's load: the number of talks still in the running ({@link TalkStatus#isInTheRunning()}) that they
     * present, as main speaker or as co-speaker. Finds their talks through the indexes on speaker, so it reads only
     * their rows and not every talk.
     */
    public int load(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM talk t WHERE t.status NOT IN ('REJECTED', 'WITHDRAWN') "
                        + "AND t.id IN (SELECT id FROM talk WHERE speaker_id = ? "
                        + "UNION ALL SELECT talk_id FROM co_speaker WHERE speaker_id = ?)",
                Integer.class, id, id);
    }

    public boolean emailTaken(String email) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM speaker WHERE lower(email) = lower(?)", Integer.class, email);
        return n != null && n > 0;
    }

    public long insert(String name, String email, String bio) {
        return jdbc.queryForObject(
                "INSERT INTO speaker (name, email, bio, created) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, name, email, bio, Utils.today());
    }

    public int update(long id, String name, String email, String bio) {
        return jdbc.update("UPDATE speaker SET name = ?, email = ?, bio = ? WHERE id = ?", name, email, bio, id);
    }

    public int count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM speaker", Integer.class);
    }

    /** The number of speakers who are the main speaker of at least one talk. */
    public int countWithTalks() {
        return jdbc.queryForObject(
                "SELECT COUNT(DISTINCT t.speaker_id) FROM talk t JOIN speaker s ON s.id = t.speaker_id", Integer.class);
    }

    /** The names of the speakers who are the main speaker of at least one talk, by speaker id. */
    public Map<Long, String> namesOfSpeakersWithTalks() {
        Map<Long, String> names = new HashMap<>();
        jdbc.query("SELECT s.id, s.name FROM speaker s WHERE EXISTS (SELECT 1 FROM talk t WHERE t.speaker_id = s.id)",
                rs -> {
                    names.put(rs.getLong("id"), rs.getString("name"));
                });
        return names;
    }

    // Used by the old admin page to look people up.
    public List<Map<String, Object>> search(String name) {
        return jdbc.queryForList("SELECT id, name, email, bio FROM speaker WHERE name ILIKE '%" + name + "%' ORDER BY name");
    }
}
