package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

/**
 * How {@link RatingSummary} calculates the average a talk shows (R1–R3 of {@code workflow/specs/ratings-cr.md}).
 */
class RatingSummaryTest {

    @Test
    void aTalkNobodyRatedHasNoAverage() {
        assertNull(of().average());
    }

    @Test
    void r1_withFewerThanFiveRatingsEveryScoreCounts() {
        assertEquals(3.0, of(3).average());
        assertEquals(3.0, of(1, 5).average());
        assertEquals(4.25, of(4, 4, 4, 5).average());
        assertEquals(2.75, of(1, 1, 4, 5).average());
    }

    @Test
    void r2_fromFiveRatingsOneLowestAndOneHighestAreLeftOut() {
        assertEquals(3.0, of(1, 2, 3, 4, 5).average());
        assertEquals(4.0, of(1, 4, 4, 4, 5).average());
        assertEquals(3.75, of(1, 2, 3, 5, 5, 5).average());
    }

    @Test
    void r2_exactlyOneOfEachIsLeftOutWhenSeveralGaveTheSameTopOrBottomScore() {
        assertEquals(3.67, of(1, 1, 5, 5, 5).average());
        assertEquals(5.0, of(5, 5, 5, 5, 5).average());
        assertEquals(1.0, of(1, 1, 1, 1, 1).average());
    }

    @Test
    void r3_roundedToTwoDecimals() {
        // 10 / 3, 11 / 3, and (13 - 1 - 2) / 7
        assertEquals(3.33, new RatingSummary(3, 10, 3, 4).average());
        assertEquals(3.67, new RatingSummary(3, 11, 3, 4).average());
        assertEquals(1.43, new RatingSummary(9, 13, 1, 2).average());
    }

    @Test
    void r3_tiesAtTheThirdDecimalGoToTheEvenNeighbour() {
        // after leaving out the 3 and the 4: 25 / 8, 27 / 8, 121 / 40 and 123 / 40
        assertEquals(3.12, new RatingSummary(10, 32, 3, 4).average());
        assertEquals(3.38, new RatingSummary(10, 34, 3, 4).average());
        assertEquals(3.02, new RatingSummary(42, 128, 3, 4).average());
        assertEquals(3.08, new RatingSummary(42, 130, 3, 4).average());
    }

    @Test
    void r3_aTieFromRealScoresAfterTrimming() {
        // 10 ratings: without the 1 and one 5, the eight left add up to 25: 3.125 -> 3.12
        assertEquals(3.12, of(1, 5, 2, 3, 3, 3, 3, 3, 3, 5).average());
        // without the 1 and one 5, the eight left add up to 27: 3.375 -> 3.38
        assertEquals(3.38, of(1, 5, 2, 3, 3, 3, 3, 3, 5, 5).average());
    }

    @Test
    void theCountIsEveryReviewerIncludingTheOnesLeftOut() {
        assertEquals(5, of(1, 2, 3, 4, 5).count());
    }

    private static RatingSummary of(int... scores) {
        return new RatingSummary(scores.length, IntStream.of(scores).sum(),
                IntStream.of(scores).min().orElse(0), IntStream.of(scores).max().orElse(0));
    }
}
