package no.scienta.cfp;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * A talk's place on the programme, as the API shows it: its room and slot, and when the slot starts and ends.
 */
@JsonPropertyOrder({"room", "slot", "start", "end", "talkId"})
public class ProgrammeEntry {

    public final String room;
    public final String slot;
    public final String start;
    public final String end;
    public final long talkId;

    public ProgrammeEntry(Room room, Slot slot, long talkId) {
        this.room = room.name();
        this.slot = slot.name();
        this.start = slot.start;
        this.end = slot.end;
        this.talkId = talkId;
    }
}
