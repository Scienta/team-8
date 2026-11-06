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
     * Insert or replace the reviewer's rating. One statement, so two clicks at once can't make two rows.
     */
    public void save(long talkId, long reviewerId, int score, String ratedAt) {
        jdbc.update("INSERT INTO rating (talk_id, reviewer_id, score, rated_at) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT (talk_id, reviewer_id) DO UPDATE SET score = excluded.score, rated_at = excluded.rated_at",
                talkId, reviewerId, score, ratedAt);
    }

    /** What the talk's ratings add up to, read in one statement so they agree with each other. */
    public RatingSummary summary(long talkId) {
        return jdbc.queryForObject("SELECT COUNT(*) AS cnt, COALESCE(SUM(score), 0) AS total, "
                + "COALESCE(MIN(score), 0) AS lowest, COALESCE(MAX(score), 0) AS highest FROM rating WHERE talk_id = ?",
                (rs, row) -> new RatingSummary(rs.getInt("cnt"), rs.getLong("total"), rs.getInt("lowest"),
                        rs.getInt("highest")), talkId);
    }

    // public double average(long talkId) {
    //     return jdbc.queryForObject("SELECT AVG(score) FROM rating WHERE talk_id = ?", Double.class, talkId);
    // }

    /** The number of different reviewers who rated the talk. */
    public int reviewerCount(long talkId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(DISTINCT reviewer_id) FROM rating WHERE talk_id = ?",
                Integer.class, talkId);
        return n == null ? 0 : n;
    }
}
