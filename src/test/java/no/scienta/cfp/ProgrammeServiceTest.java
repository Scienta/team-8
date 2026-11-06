package no.scienta.cfp;

import static no.scienta.cfp.ProgrammeService.SchedulingOutcome.ALREADY_SCHEDULED;
import static no.scienta.cfp.ProgrammeService.SchedulingOutcome.DOES_NOT_FIT;
import static no.scienta.cfp.ProgrammeService.SchedulingOutcome.NOT_ACCEPTED;
import static no.scienta.cfp.ProgrammeService.SchedulingOutcome.PLACE_TAKEN;
import static no.scienta.cfp.ProgrammeService.SchedulingOutcome.SCHEDULE;
import static no.scienta.cfp.ProgrammeService.SchedulingOutcome.SPEAKER_BUSY;
import static no.scienta.cfp.ProgrammeService.schedulingOutcome;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The pure decision of {@link ProgrammeService}: whether a talk goes into a room and slot, and which refusal wins.
 */
class ProgrammeServiceTest {

    @Test
    void anAcceptedTalkGoesIntoAFreePlace() {
        assertEquals(SCHEDULE, schedulingOutcome(Slot.S1, "PRESENTATION", "ACCEPTED", false, false, false));
        assertEquals(SCHEDULE, schedulingOutcome(Slot.S4, "WORKSHOP", "ACCEPTED", false, false, false));
    }

    @Test
    void eachRefusal() {
        assertEquals(DOES_NOT_FIT, schedulingOutcome(Slot.S1, "WORKSHOP", "ACCEPTED", false, false, false));
        assertEquals(NOT_ACCEPTED, schedulingOutcome(Slot.S1, "PRESENTATION", "SUBMITTED", false, false, false));
        assertEquals(ALREADY_SCHEDULED, schedulingOutcome(Slot.S1, "PRESENTATION", "ACCEPTED", true, false, false));
        assertEquals(PLACE_TAKEN, schedulingOutcome(Slot.S1, "PRESENTATION", "ACCEPTED", false, true, false));
        assertEquals(SPEAKER_BUSY, schedulingOutcome(Slot.S1, "PRESENTATION", "ACCEPTED", false, false, true));
    }

    @Test
    void aSlotThatCanNeverFitWinsOverTheTalksState() {
        assertEquals(DOES_NOT_FIT, schedulingOutcome(Slot.S2, "WORKSHOP", "SUBMITTED", true, true, true));
    }

    @Test
    void aTalkThatIsntAcceptedIsRefusedBeforeLookingAtTheProgramme() {
        assertEquals(NOT_ACCEPTED, schedulingOutcome(Slot.S1, "PRESENTATION", "WITHDRAWN", true, true, true));
    }

    @Test
    void aTalkOnTheProgrammeAlreadyIsRefusedEvenWhereItIs() {
        assertEquals(ALREADY_SCHEDULED, schedulingOutcome(Slot.S1, "PRESENTATION", "ACCEPTED", true, true, true));
    }
}
