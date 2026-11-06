package no.scienta.cfp;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates the average a talk shows from its reviewers' scores (R1–R3 of {@code workflow/specs/ratings-cr.md}).
 *
 * @param count the number of reviewers who rated the talk
 * @param total the sum of their scores
 * @param lowest the lowest score, 0 with no ratings
 * @param highest the highest score, 0 with no ratings
 */
record RatingSummary(int count, long total, int lowest, int highest) {

    /** From this many ratings on, one lowest and one highest score are left out of the average. */
    static final int TRIMMED_FROM = 5;

    /**
     * The average as the API shows it, or null with no ratings: with {@value #TRIMMED_FROM} or more ratings without one
     * lowest and one highest score, rounded to two decimals with ties to the even neighbour (3.125 -> 3.12).
     */
    Double average() {
        if (count == 0) {
            return null;
        }
        boolean trimmed = count >= TRIMMED_FROM;
        long counted = trimmed ? total - lowest - highest : total;
        int counting = trimmed ? count - 2 : count;
        return BigDecimal.valueOf(counted).divide(BigDecimal.valueOf(counting), 2, RoundingMode.HALF_EVEN)
                .doubleValue();
    }
}
