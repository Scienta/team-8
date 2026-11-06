package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * {@link QuietWindow}: how long a deploy waits to open its port, from a given moment.
 */
class QuietWindowTest {

    private final QuietWindow window = new QuietWindow(Duration.ofSeconds(10), Duration.ofSeconds(3));

    @Test
    void onTimeOpensAtOnce() {
        assertEquals(Duration.ZERO, window.waitFrom(Instant.parse("2026-11-06T14:56:10Z")));
        assertEquals(Duration.ZERO, window.waitFrom(Instant.parse("2026-11-06T14:56:12.999Z")));
        assertEquals(Duration.ZERO, window.waitFrom(Instant.parse("2026-11-06T14:56:13Z")));
    }

    @Test
    void earlierInTheMinuteWaitsForTheOpening() {
        assertEquals(Duration.ofSeconds(10), window.waitFrom(Instant.parse("2026-11-06T14:56:00Z")));
        assertEquals(Duration.ofMillis(500), window.waitFrom(Instant.parse("2026-11-06T14:56:09.500Z")));
    }

    @Test
    void lateWaitsForTheNextMinute() {
        assertEquals(Duration.ofMillis(56_999), window.waitFrom(Instant.parse("2026-11-06T14:56:13.001Z")));
        assertEquals(Duration.ofSeconds(13), window.waitFrom(Instant.parse("2026-11-06T14:56:57Z")));
    }
}
