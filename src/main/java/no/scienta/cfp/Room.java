package no.scienta.cfp;

import java.util.Arrays;
import java.util.Optional;

/**
 * A room at the venue. The rooms are fixed: nobody can add or change them.
 */
public enum Room {
    R1, R2, R3;

    /** The room with exactly this name, if there is one. */
    static Optional<Room> named(String name) {
        return Arrays.stream(values())
            .filter(room ->
                room.name().equals(name))
                .findAny();
    }
}
