package no.scienta.cfp;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Stores which speakers co-speak on which talk, in the order they were added.
 */
@Repository
public class CoSpeakerDao {

    private final JdbcTemplate jdbc;

    public CoSpeakerDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The talk's co-speakers' ids, in the order they were added. */
    public List<Long> findSpeakerIds(long talkId) {
        return jdbc.queryForList("SELECT speaker_id FROM co_speaker WHERE talk_id = ? ORDER BY position",
                Long.class, talkId);
    }

    /** Adds the co-speaker last in the order, or does nothing if they already co-speak on the talk. */
    public void add(long talkId, long speakerId) {
        jdbc.update("INSERT INTO co_speaker (talk_id, speaker_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                talkId, speakerId);
    }

    /** Removes the co-speaker, or does nothing if they don't co-speak on the talk. */
    public void remove(long talkId, long speakerId) {
        jdbc.update("DELETE FROM co_speaker WHERE talk_id = ? AND speaker_id = ?", talkId, speakerId);
    }
}
