package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * The order of the day of {@link ProgrammeExportService} (R4 of {@code workflow/specs/programme-export.md}).
 */
class ProgrammeExportServiceTest {

    @Test
    void r4_earliestSlotFirstThenByRoom() {
        List<ProgrammeRow> rows = List.of(row(Slot.S5, Room.R1), row(Slot.S1, Room.R3), row(Slot.S4, Room.R2),
                row(Slot.S1, Room.R1), row(Slot.S4, Room.R1), row(Slot.S1, Room.R2));
        assertEquals("S1 R1, S1 R2, S1 R3, S4 R1, S4 R2, S5 R1", places(ProgrammeExportService.inDayOrder(rows)));
    }

    @Test
    void r4_theGivenListIsLeftAsItWas() {
        List<ProgrammeRow> rows = new ArrayList<>(List.of(row(Slot.S2, Room.R1), row(Slot.S1, Room.R1)));
        ProgrammeExportService.inDayOrder(rows);
        assertEquals("S2 R1, S1 R1", places(rows));
    }

    private static ProgrammeRow row(Slot slot, Room room) {
        return new ProgrammeRow(slot, room, 1, "Talk", "PRESENTATION", List.of("Ada Lovelace"));
    }

    private static String places(List<ProgrammeRow> rows) {
        return rows.stream().map(r -> r.slot + " " + r.room).collect(Collectors.joining(", "));
    }
}
