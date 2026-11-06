package no.scienta.cfp;

import java.util.Optional;

/**
 * A room at the venue. The rooms are fixed: nobody can add or change them.
 */
public enum Room {
    R1, R2, R3;

    /** The room with exactly this name, if there is one. */
    static Optional<Room> named(String name) {
        for (Room room : values()) {
            if (room.name().equals(name)) {
                return Optional.of(room);
            }
        }
        return Optional.empty();
    }
}
