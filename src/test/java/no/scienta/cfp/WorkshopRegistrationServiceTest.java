package no.scienta.cfp;

import static no.scienta.cfp.WorkshopRegistrationService.RegistrationOutcome.ALREADY_REGISTERED;
import static no.scienta.cfp.WorkshopRegistrationService.RegistrationOutcome.FULL;
import static no.scienta.cfp.WorkshopRegistrationService.RegistrationOutcome.NOT_ACCEPTED;
import static no.scienta.cfp.WorkshopRegistrationService.RegistrationOutcome.REGISTER;
import static no.scienta.cfp.WorkshopRegistrationService.emailKey;
import static no.scienta.cfp.WorkshopRegistrationService.registrationOutcome;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The pure decisions of {@link WorkshopRegistrationService}: who gets a seat and when two emails are one person.
 */
class WorkshopRegistrationServiceTest {

    @Test
    void anAcceptedWorkshopWithRoomRegistersSomeoneNew() {
        assertEquals(REGISTER, registrationOutcome("ACCEPTED", false, 0));
        assertEquals(REGISTER, registrationOutcome("ACCEPTED", false, 19));
    }

    @Test
    void theTwentyFirstIsRefused() {
        assertEquals(FULL, registrationOutcome("ACCEPTED", false, 20));
    }

    @Test
    void aTakenEmailWinsOverAFullWorkshop() {
        assertEquals(ALREADY_REGISTERED, registrationOutcome("ACCEPTED", true, 20));
    }

    @Test
    void onlyAcceptedWorkshopsTakeRegistrations() {
        for (TalkStatus status : TalkStatus.values()) {
            if (status != TalkStatus.ACCEPTED) {
                assertEquals(NOT_ACCEPTED, registrationOutcome(status.name(), false, 0), status.name());
            }
        }
    }

    @Test
    void emailsAreOnePersonWhateverTheCaseOrSurroundingSpaces() {
        assertEquals("ada@example.com", emailKey("ada@example.com"));
        assertEquals("ada@example.com", emailKey("ADA@Example.com"));
        assertEquals("ada@example.com", emailKey("  Ada@Example.COM "));
    }
}
