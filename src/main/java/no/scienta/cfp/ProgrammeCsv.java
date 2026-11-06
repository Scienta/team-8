package no.scienta.cfp;

import java.util.List;

/**
 * Writes the exported programme as the print shop's CSV: RFC 4180 with Unix line endings, a fixed header line, and a
 * field quoted only when it has to be. See {@code workflow/specs/programme-export.md}.
 */
final class ProgrammeCsv {

    static final String HEADER = "slot,start,end,room,talkId,title,format,speakers";

    /** Separates the speakers' names in their one field. */
    static final String SPEAKER_SEPARATOR = "; ";

    private ProgrammeCsv() {
    }

    /** The header line and then one line per row, in the given order, each ending in a newline. */
    static String write(List<ProgrammeRow> rows) {
        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        for (ProgrammeRow row : rows) {
            csv.append(String.join(",", row.slot, row.start, row.end, row.room, String.valueOf(row.talkId),
                    field(row.title), field(row.format), field(String.join(SPEAKER_SEPARATOR, row.speakers))))
                    .append('\n');
        }
        return csv.toString();
    }

    /** The value as a CSV field: quoted, with its quotes doubled, if it has a comma, a quote or a line break in it. */
    static String field(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
