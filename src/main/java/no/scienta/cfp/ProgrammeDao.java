package no.scienta.cfp;

import java.util.Arrays;
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

    /**
     * What scheduling the talk into the room and slot depends on, in one round trip: the talk in that place, whether
     * the talk is on the programme anywhere, and whether a main speaker or co-speaker of the talk is a main speaker or
     * co-speaker of a talk in this slot, in any room. The speaker check starts from the talk's own speakers and the few
     * talks in the slot, so it reads neither every talk nor every co-speaker.
     */
    public Placement placement(Room room, Slot slot, long talkId) {
        return jdbc.queryForObject(
                "WITH candidate AS (SELECT speaker_id FROM talk WHERE id = ? "
                        + "UNION ALL SELECT speaker_id FROM co_speaker WHERE talk_id = ?) "
                        + "SELECT (SELECT talk_id FROM programme_entry WHERE room = ? AND slot = ?) AS talk_in_place, "
                        + "EXISTS (SELECT 1 FROM programme_entry WHERE talk_id = ?) AS scheduled, "
                        + "EXISTS (SELECT 1 FROM programme_entry e JOIN talk t ON t.id = e.talk_id "
                        + "WHERE e.slot = ? AND (t.speaker_id IN (SELECT speaker_id FROM candidate) "
                        + "OR EXISTS (SELECT 1 FROM co_speaker c WHERE c.talk_id = e.talk_id "
                        + "AND c.speaker_id IN (SELECT speaker_id FROM candidate)))) AS speaker_busy",
                (rs, n) -> {
                    long talkInPlace = rs.getLong("talk_in_place");
                    return new Placement(rs.wasNull() ? null : talkInPlace, rs.getBoolean("scheduled"),
                            rs.getBoolean("speaker_busy"));
                },
                talkId, talkId, room.name(), slot.name(), talkId, slot.name());
    }

    /**
     * The programme as it bears on putting one talk in one room and slot.
     *
     * @param talkInPlace the talk in the room and slot, or null if it's free
     * @param talkScheduled whether the talk is on the programme anywhere
     * @param speakerBusy whether one of the talk's speakers speaks in the slot already, in any room
     */
    public record Placement(Long talkInPlace, boolean talkScheduled, boolean speakerBusy) {
    }

    public void insert(Room room, Slot slot, long talkId) {
        jdbc.update("INSERT INTO programme_entry (room, slot, talk_id) VALUES (?, ?, ?)", room.name(), slot.name(),
                talkId);
    }

    /**
     * Every talk on the programme with its room, slot, title, format and speakers' names (main speaker first, then
     * co-speakers in the order they were added), as they are now. One statement, so one consistent snapshot; in no
     * particular order.
     */
    public List<ProgrammeRow> findRows() {
        return jdbc.query("SELECT e.room, e.slot, t.id, t.title, t.format, "
                        + "ARRAY(SELECT s.name FROM speaker s WHERE s.id = t.speaker_id) "
                        + "|| ARRAY(SELECT s.name FROM co_speaker c JOIN speaker s ON s.id = c.speaker_id "
                        + "WHERE c.talk_id = t.id ORDER BY c.position) AS speakers "
                        + "FROM programme_entry e JOIN talk t ON t.id = e.talk_id",
                (rs, n) -> new ProgrammeRow(Slot.valueOf(rs.getString("slot")), Room.valueOf(rs.getString("room")),
                        rs.getLong("id"), rs.getString("title"), rs.getString("format"),
                        Arrays.asList((String[]) rs.getArray("speakers").getArray())));
    }

    /** Takes the talk out of the room and slot; returns false, changing nothing, if there was none. */
    public boolean remove(Room room, Slot slot) {
        return jdbc.update("DELETE FROM programme_entry WHERE room = ? AND slot = ?", room.name(), slot.name()) > 0;
    }
}
