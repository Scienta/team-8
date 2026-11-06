package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * How {@link RatingSummary} calculates the average a talk shows.
 */
class RatingSummaryTest {

    @Test
    void aTalkNobodyRatedHasNoAverage() {
        assertNull(new RatingSummary(0, 0).average());
    }

    @Test
    void theAverageIsRoundedToOneDecimalWithHalvesUp() {
        assertEquals(4.3, new RatingSummary(4, 17).average());
        assertEquals(3.0, new RatingSummary(5, 15).average());
    }
}
