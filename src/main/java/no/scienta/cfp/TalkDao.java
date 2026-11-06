package no.scienta.cfp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class TalkDao {

    private static final String COLUMNS = "id, title, format, duration, level, status, speaker_id, submitted_at";

    /**
     * A talk with what it shows besides its own row, its ratings and its co-speakers, so that a talk or a list of
     * talks takes one round trip to the database instead of one plus two per talk.
     */
    private static final String WITH_DETAILS = "SELECT t.id, t.title, t.format, t.duration, t.level, t.status, "
            + "t.speaker_id, t.submitted_at, r.cnt, r.total, r.lowest, r.highest, c.co_speaker_ids FROM talk t "
            + "CROSS JOIN LATERAL (SELECT COUNT(*) AS cnt, COALESCE(SUM(score), 0) AS total, "
            + "COALESCE(MIN(score), 0) AS lowest, COALESCE(MAX(score), 0) AS highest "
            + "FROM rating WHERE talk_id = t.id) r "
            + "CROSS JOIN LATERAL (SELECT COALESCE(array_agg(speaker_id ORDER BY position), '{}') AS co_speaker_ids "
            + "FROM co_speaker WHERE talk_id = t.id) c ";

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

    private final RowMapper<TalkDto> detailedMapper = (rs, rowNum) -> {
        TalkDto t = mapper.mapRow(rs, rowNum);
        RatingSummary ratings = new RatingSummary(rs.getInt("cnt"), rs.getLong("total"), rs.getInt("lowest"),
                rs.getInt("highest"));
        t.ratingCount = ratings.count();
        t.averageRating = ratings.average();
        t.coSpeakerIds = coSpeakerIds(rs);
        return t;
    };

    public TalkDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public TalkDto findById(long id) {
        List<TalkDto> list = jdbc.query("SELECT " + COLUMNS + " FROM talk WHERE id = ?", mapper, id);
        return list.isEmpty() ? null : list.get(0);
    }

    /** Finds the talk with its ratings and co-speakers, or null if there is none. */
    public TalkDto findWithDetailsById(long id) {
        List<TalkDto> list = jdbc.query(WITH_DETAILS + "WHERE t.id = ?", detailedMapper, id);
        return list.isEmpty() ? null : list.get(0);
    }

    /** The speaker's talks as main speaker, with their ratings and co-speakers, oldest first. */
    public List<TalkDto> findWithDetailsBySpeaker(long speakerId) {
        return jdbc.query(WITH_DETAILS + "WHERE t.speaker_id = ? ORDER BY t.id", detailedMapper, speakerId);
    }

    /** All talks with their ratings and co-speakers, oldest first. */
    public List<TalkDto> findAllWithDetails() {
        return jdbc.query(WITH_DETAILS + "ORDER BY t.id", detailedMapper);
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

    private static List<Long> coSpeakerIds(ResultSet rs) throws SQLException {
        Integer[] ids = (Integer[]) rs.getArray("co_speaker_ids").getArray();
        return new ArrayList<>(Arrays.stream(ids).map(Integer::longValue).toList());
    }

    // old version, raced when the mobile app double-posted
    // public long insert(String title, String format, int duration, String level, long speakerId) {
    //     jdbc.update("INSERT INTO talk (title, format, duration, level, status, speaker_id) VALUES (?, ?, ?, ?, 'SUBMITTED', ?)",
    //             title, format, duration, level, speakerId);
    //     return jdbc.queryForObject("SELECT currval('talk_id_seq')", Long.class);
    // }
}
