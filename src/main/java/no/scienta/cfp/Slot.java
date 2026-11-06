package no.scienta.cfp;

import java.util.Optional;

/**
 * A time slot on the programme, the same in every room, and which talks fit it. The slots are fixed: nobody can add
 * or change them. Times are written as on the signs, {@code HH:mm} on a 24-hour clock.
 */
public enum Slot {
    S1("09:00", "10:00"),
    S2("10:15", "11:15"),
    S3("11:30", "12:30"),
    S4("13:30", "15:30"),
    S5("15:45", "16:45");

    /** The long slot, the only one a workshop fits. */
    static final Slot LONG = S4;

    final String start;
    final String end;

    Slot(String start, String end) {
        this.start = start;
        this.end = end;
    }

    /** Whether a talk of this format fits the slot: a workshop needs the long slot, everything else fits anywhere. */
    boolean fits(String format) {
        return !WorkshopRegistrationService.WORKSHOP.equals(format) || this == LONG;
    }

    /** The slot with exactly this name, if there is one. */
    static Optional<Slot> named(String name) {
        for (Slot slot : values()) {
            if (slot.name().equals(name)) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }
}
