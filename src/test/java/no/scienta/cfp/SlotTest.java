package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * The fixed venue of {@code workflow/specs/scheduling.md} (R1) and which talks fit which slot (R6).
 */
class SlotTest {

    @Test
    void theSlotsAreTheSameAsOnTheSigns() {
        assertEquals("09:00–10:00 10:15–11:15 11:30–12:30 13:30–15:30 15:45–16:45", signs());
    }

    @Test
    void aWorkshopFitsOnlyTheLongSlot() {
        for (Slot slot : Slot.values()) {
            assertEquals(slot == Slot.S4, slot.fits("WORKSHOP"), slot.name());
        }
    }

    @Test
    void everythingElseFitsAnywhere() {
        for (String format : new String[] {"PRESENTATION", "LIGHTNING_10", "LIGHTNING_20"}) {
            for (Slot slot : Slot.values()) {
                assertTrue(slot.fits(format), format + " " + slot);
            }
        }
    }

    @Test
    void roomsAndSlotsAreNamedExactly() {
        assertEquals(Optional.of(Slot.S3), Slot.named("S3"));
        assertEquals(Optional.of(Room.R2), Room.named("R2"));
        assertFalse(Slot.named("s3").isPresent());
        assertFalse(Slot.named("S6").isPresent());
        assertFalse(Room.named("R4").isPresent());
        assertFalse(Room.named("r1").isPresent());
    }

    private static String signs() {
        StringBuilder signs = new StringBuilder();
        for (Slot slot : Slot.values()) {
            signs.append(signs.length() == 0 ? "" : " ").append(slot.start).append('–').append(slot.end);
        }
        return signs.toString();
    }
}
