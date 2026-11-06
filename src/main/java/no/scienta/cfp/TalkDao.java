package no.scienta.cfp;

import java.util.Date;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class TalkDao {

    private static final String COLUMNS = "id, title, format, duration, level, status, speaker_id, submitted_at";

    private final JdbcTemplate jdbc;

    private final RowMapper<TalkDto> mapper = (rs, rowNum) -> {
        TalkDto t = new TalkDto();
        t.id = rs.getLong("id");
        t.title = rs.getString("title");
        t.format = rs.getString("format");
        t.durationMinutes = rs.getInt("duration");
        t.level = rs.getString("level");
        t.status = rs.getString("status");
        t.speakerId = rs.getLong("speaker_id");
        long millis = rs.getLong("submitted_at");
        t.submittedAt = Utils.reportingTimestamp(millis);
        t.submittedOn = Utils.reportingDate(millis);
        t.submittedTime = new Date(millis);
        return t;
    };

    public TalkDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public TalkDto findById(long id) {
        List<TalkDto> list = jdbc.query("SELECT " + COLUMNS + " FROM talk WHERE id = ?", mapper, id);
        return list.isEmpty() ? null : list.get(0);
    }

    /** Finds the talk and locks its row until the transaction ends. */
    public TalkDto findByIdForUpdate(long id) {
        List<TalkDto> list = jdbc.query("SELECT " + COLUMNS + " FROM talk WHERE id = ? FOR UPDATE", mapper, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public void updateStatus(long id, String status) {
        jdbc.update("UPDATE talk SET status = ? WHERE id = ?", status, id);
    }

    public List<TalkDto> findBySpeaker(long speakerId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM talk WHERE speaker_id = ? ORDER BY id", mapper, speakerId);
    }

    public List<TalkDto> findAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM talk ORDER BY id", mapper);
    }

    public boolean exists(long id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM talk WHERE id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    /** Inserts a new {@code SUBMITTED} talk and returns its id. */
    public long insert(String title, String format, int duration, String level, long speakerId, long submittedAt) {
        return jdbc.queryForObject(
                "INSERT INTO talk (title, format, duration, level, status, speaker_id, submitted_at) "
                        + "VALUES (?, ?, ?, ?, 'SUBMITTED', ?, ?) RETURNING id",
                Long.class, title, format, duration, level, speakerId, submittedAt);
    }

    // old version, raced when the mobile app double-posted
    // public long insert(String title, String format, int duration, String level, long speakerId) {
    //     jdbc.update("INSERT INTO talk (title, format, duration, level, status, speaker_id) VALUES (?, ?, ?, ?, 'SUBMITTED', ?)",
    //             title, format, duration, level, speakerId);
    //     return jdbc.queryForObject("SELECT currval('talk_id_seq')", Long.class);
    // }
}
