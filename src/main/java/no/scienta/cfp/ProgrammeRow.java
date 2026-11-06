package no.scienta.cfp;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * One talk on the exported programme, as the website and the print shop see it: where and when it is, and the talk's
 * title, format and speakers as they are now.
 */
@JsonPropertyOrder({"slot", "start", "end", "room", "talkId", "title", "format", "speakers"})
public class ProgrammeRow {

    public final String slot;
    public final String start;
    public final String end;
    public final String room;
    public final long talkId;
    public final String title;
    public final String format;
    /** The main speaker's name first, then the co-speakers' in the order they were added. */
    public final List<String> speakers;

    public ProgrammeRow(Slot slot, Room room, long talkId, String title, String format, List<String> speakers) {
        this.slot = slot.name();
        this.start = slot.start;
        this.end = slot.end;
        this.room = room.name();
        this.talkId = talkId;
        this.title = title;
        this.format = format;
        this.speakers = List.copyOf(speakers);
    }
}
