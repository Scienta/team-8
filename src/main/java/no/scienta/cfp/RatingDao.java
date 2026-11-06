package no.scienta.cfp;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RatingDao {

    private final JdbcTemplate jdbc;

    public RatingDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts or replaces the reviewer's rating if the talk exists; returns false, changing nothing, if it doesn't.
     * One statement, so two clicks at once can't make two rows, and one round trip to the database.
     */
    public boolean saveIfTalkExists(long talkId, long reviewerId, int score, String ratedAt) {
        return jdbc.update("INSERT INTO rating (talk_id, reviewer_id, score, rated_at) "
                + "SELECT id, ?, ?, ? FROM talk WHERE id = ? "
                + "ON CONFLICT (talk_id, reviewer_id) DO UPDATE SET score = excluded.score, rated_at = excluded.rated_at",
                reviewerId, score, ratedAt, talkId) > 0;
    }

    // public double average(long talkId) {
    //     return jdbc.queryForObject("SELECT AVG(score) FROM rating WHERE talk_id = ?", Double.class, talkId);
    // }

    /** The number of different reviewers who rated the talk: one rating each, as the primary key makes sure. */
    public int reviewerCount(long talkId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM rating WHERE talk_id = ?",
                Integer.class, talkId);
        return n == null ? 0 : n;
    }
}
