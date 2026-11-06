package no.scienta.cfp;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates the average a talk shows from what its reviewers' scores add up to.
 *
 * @param count the number of reviewers who rated the talk
 * @param total the sum of their scores
 */
record RatingSummary(int count, long total) {

    /** The average as the API shows it: rounded to one decimal, halves up (4.25 -> 4.3), or null with no ratings. */
    Double average() {
        if (count == 0) {
            return null;
        }
        return BigDecimal.valueOf(total).divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP).doubleValue();
    }
}
