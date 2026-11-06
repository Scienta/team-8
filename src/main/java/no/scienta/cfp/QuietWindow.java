package no.scienta.cfp;

import java.time.Duration;
import java.time.Instant;

/**
 * Knows when the harness is quiet, so a deploy can open its port then. The harness sends one burst of requests a
 * minute, starting 56–57 s past the minute (UTC) and lasting 10–16 s, and nothing the rest of the minute. A deploy
 * hands traffic from the old instance to the new one over the 35–45 s after the new one opens its port, and a burst
 * during that hand-over loses responses: whole ticks failed that way. Opening the port {@code openAt} past the minute,
 * just after a burst, lets the hand-over finish before the next one.
 *
 * @param openAt how far past the minute to open the port
 * @param grace  how late past {@code openAt} still counts as on time, rather than waiting for the next minute
 */
record QuietWindow(Duration openAt, Duration grace) {

    private static final long MINUTE = Duration.ofMinutes(1).toNanos();

    /** How long to wait from {@code now} to open the port: zero if on time, else until the next opening. */
    Duration waitFrom(Instant now) {
        long intoMinute = Math.floorMod(now.getEpochSecond(), 60L) * 1_000_000_000L + now.getNano();
        long sinceOpening = Math.floorMod(intoMinute - openAt.toNanos(), MINUTE);
        return sinceOpening <= grace.toNanos() ? Duration.ZERO : Duration.ofNanos(MINUTE - sinceOpening);
    }
}
