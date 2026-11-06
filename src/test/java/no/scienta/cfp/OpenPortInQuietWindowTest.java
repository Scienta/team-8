package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@link OpenPortInQuietWindow} and {@link Deployments}: a deploy is recorded once its port opens, and a version that
 * has opened before starts at once. The clock stands on time for the window or late in the minute, so a test that
 * waited would take most of a minute.
 */
@SpringBootTest
class OpenPortInQuietWindowTest {

    private static final QuietWindow WINDOW = new QuietWindow(Duration.ofSeconds(10), Duration.ofSeconds(3));
    private static final Clock ON_TIME = Clock.fixed(Instant.parse("2026-11-06T14:56:11Z"), ZoneOffset.UTC);
    private static final Clock LATE = Clock.fixed(Instant.parse("2026-11-06T14:56:30Z"), ZoneOffset.UTC);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Autowired
    Deployments deployments;

    @Test
    void aDeployIsRecordedWhenItsPortOpens() {
        assertFalse(deployments.hasOpened("new-version"));

        new OpenPortInQuietWindow(deployments, "new-version", true, WINDOW, ON_TIME).start();

        assertTrue(deployments.hasOpened("new-version"));
    }

    @Test
    void aColdStartOpensAtOnce() {
        deployments.recordOpened("opened-before");

        assertOpensWithinASecond(new OpenPortInQuietWindow(deployments, "opened-before", true, WINDOW, LATE));
    }

    @Test
    void whenOffItNeitherWaitsNorRecords() {
        assertOpensWithinASecond(new OpenPortInQuietWindow(deployments, "off-version", false, WINDOW, LATE));

        assertFalse(deployments.hasOpened("off-version"));
    }

    @Test
    void recordingAVersionTwiceIsHarmless() {
        deployments.recordOpened("twice");
        deployments.recordOpened("twice");

        assertTrue(deployments.hasOpened("twice"));
    }

    private static void assertOpensWithinASecond(OpenPortInQuietWindow gate) {
        long started = System.nanoTime();
        gate.start();
        assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(1)) < 0);
    }
}
