package no.scienta.cfp;

import static no.scienta.cfp.TalkStatus.ACCEPTED;
import static no.scienta.cfp.TalkStatus.REJECTED;
import static no.scienta.cfp.TalkStatus.SUBMITTED;
import static no.scienta.cfp.TalkStatus.WAITLISTED;
import static no.scienta.cfp.TalkStatus.WITHDRAWN;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The transition table of {@code workflow/specs/acceptance.md} (R2, R3, R6).
 */
class TalkStatusTest {

    @Test
    void committeeMoves() {
        assertEquals(EnumSet.of(ACCEPTED, REJECTED, WAITLISTED), committeeTargets(SUBMITTED));
        assertEquals(EnumSet.of(ACCEPTED, REJECTED), committeeTargets(WAITLISTED));
        assertEquals(EnumSet.noneOf(TalkStatus.class), committeeTargets(ACCEPTED));
        assertEquals(EnumSet.noneOf(TalkStatus.class), committeeTargets(REJECTED));
        assertEquals(EnumSet.noneOf(TalkStatus.class), committeeTargets(WITHDRAWN));
    }

    @Test
    void speakerWithdrawals() {
        Set<TalkStatus> withdrawable = EnumSet.noneOf(TalkStatus.class);
        for (TalkStatus s : TalkStatus.values()) {
            if (s.speakerMayWithdraw()) {
                withdrawable.add(s);
            }
        }
        assertEquals(EnumSet.of(SUBMITTED, WAITLISTED, ACCEPTED), withdrawable);
    }

    private static Set<TalkStatus> committeeTargets(TalkStatus from) {
        Set<TalkStatus> targets = EnumSet.noneOf(TalkStatus.class);
        for (TalkStatus to : TalkStatus.values()) {
            if (from.committeeMayMoveTo(to)) {
                targets.add(to);
            }
        }
        return targets;
    }
}
