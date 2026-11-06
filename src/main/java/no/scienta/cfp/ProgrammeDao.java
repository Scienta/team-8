package no.scienta.cfp;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Stores which talk is in which room and slot.
 */
@Repository
public class ProgrammeDao {

    private final JdbcTemplate jdbc;

    public ProgrammeDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Locks the slot, in every room, until the transaction ends. */
    public void lockSlot(Slot slot) {
        jdbc.queryForList("SELECT slot FROM programme_slot WHERE slot = ? FOR UPDATE", slot.name());
    }

    /** The id of the talk in this room and slot, or null if it's free. */
    public Long findTalkId(Room room, Slot slot) {
        List<Long> ids = jdbc.queryForList("SELECT talk_id FROM programme_entry WHERE room = ? AND slot = ?",
                Long.class, room.name(), slot.name());
        return ids.isEmpty() ? null : ids.get(0);
    }

    public boolean isScheduled(long talkId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM programme_entry WHERE talk_id = ?", Integer.class,
                talkId);
        return n != null && n > 0;
    }

    /**
     * Whether a main speaker or co-speaker of the talk is a main speaker or co-speaker of a talk in this slot, in
     * any room.
     */
    public boolean hasSpeakerInSlot(long talkId, Slot slot) {
        Boolean clash = jdbc.queryForObject(
                "WITH people AS (SELECT id AS talk_id, speaker_id FROM talk "
                        + "UNION SELECT talk_id, speaker_id FROM co_speaker) "
                        + "SELECT EXISTS (SELECT 1 FROM programme_entry e "
                        + "JOIN people scheduled ON scheduled.talk_id = e.talk_id "
                        + "JOIN people candidate ON candidate.speaker_id = scheduled.speaker_id "
                        + "WHERE e.slot = ? AND candidate.talk_id = ?)",
                Boolean.class, slot.name(), talkId);
        return Boolean.TRUE.equals(clash);
    }

    public void insert(Room room, Slot slot, long talkId) {
        jdbc.update("INSERT INTO programme_entry (room, slot, talk_id) VALUES (?, ?, ?)", room.name(), slot.name(),
                talkId);
    }

    /** Takes the talk out of the room and slot; returns false, changing nothing, if there was none. */
    public boolean remove(Room room, Slot slot) {
        return jdbc.update("DELETE FROM programme_entry WHERE room = ? AND slot = ?", room.name(), slot.name()) > 0;
    }
}
