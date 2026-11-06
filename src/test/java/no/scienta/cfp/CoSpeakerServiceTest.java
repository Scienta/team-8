package no.scienta.cfp;

import static no.scienta.cfp.CoSpeakerService.AdditionOutcome.ADD;
import static no.scienta.cfp.CoSpeakerService.AdditionOutcome.ALREADY_CO_SPEAKER;
import static no.scienta.cfp.CoSpeakerService.AdditionOutcome.FULL;
import static no.scienta.cfp.CoSpeakerService.AdditionOutcome.MAIN_SPEAKER;
import static no.scienta.cfp.CoSpeakerService.AdditionOutcome.SPEAKER_AT_LIMIT;
import static no.scienta.cfp.CoSpeakerService.additionOutcome;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * What adding a co-speaker decides, as a pure function: {@code workflow/specs/co-speakers.md} and
 * {@code workflow/specs/submission-cap-cr.md}.
 */
class CoSpeakerServiceTest {

    private static final long MAIN = 1;

    @Test
    void aSpeakerBelowTheLimitIsAdded() {
        assertEquals(ADD, additionOutcome(MAIN, List.of(), 7, true, 2));
    }

    @Test
    void aSpeakerAtOrOverTheLimitIsRefusedOnATalkInTheRunning() {
        assertEquals(SPEAKER_AT_LIMIT, additionOutcome(MAIN, List.of(), 7, true, 3));
        assertEquals(SPEAKER_AT_LIMIT, additionOutcome(MAIN, List.of(), 7, true, 4));
    }

    @Test
    void aTalkOutOfTheRunningIgnoresTheLimit() {
        assertEquals(ADD, additionOutcome(MAIN, List.of(), 7, false, 3));
    }

    @Test
    void anExistingCoSpeakerChangesNothingEvenOverTheLimit() {
        assertEquals(ALREADY_CO_SPEAKER, additionOutcome(MAIN, List.of(7L), 7, true, 4));
    }

    @Test
    void theTalksOwnRulesComeBeforeTheLimit() {
        assertEquals(MAIN_SPEAKER, additionOutcome(MAIN, List.of(), MAIN, true, 3));
        assertEquals(FULL, additionOutcome(MAIN, List.of(5L, 6L), 7, true, 3));
    }
}
