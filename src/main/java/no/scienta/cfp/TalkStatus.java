package no.scienta.cfp;

import java.util.EnumSet;
import java.util.Set;

/**
 * The statuses a talk moves through, and which moves the committee and the speaker may make. See
 * {@code workflow/specs/acceptance.md}.
 */
public enum TalkStatus {
    SUBMITTED, ACCEPTED, REJECTED, WAITLISTED, WITHDRAWN;

    /** The statuses a committee decision may set. */
    static final Set<TalkStatus> DECISIONS = EnumSet.of(ACCEPTED, REJECTED, WAITLISTED);

    /** Whether the committee may move a talk from this status to {@code decision}; never to the status it has. */
    boolean committeeMayMoveTo(TalkStatus decision) {
        if (decision == this) {
            return false;
        }
        switch (this) {
            case SUBMITTED:
                return DECISIONS.contains(decision);
            case WAITLISTED:
                return decision == ACCEPTED || decision == REJECTED;
            default:
                return false;
        }
    }

    /** Whether the main speaker may withdraw a talk in this status. */
    boolean speakerMayWithdraw() {
        return this != REJECTED && this != WITHDRAWN;
    }

    /** Whether a talk in this status is still in the running, and so counts towards its speakers' load. */
    boolean isInTheRunning() {
        return this != REJECTED && this != WITHDRAWN;
    }
}
