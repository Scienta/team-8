package no.scienta.cfp;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * Holds a deploy back from opening its port until the harness is quiet (see {@link QuietWindow}), so that the
 * hand-over from the old instance falls between two bursts. Runs in the lifecycle phase between
 * {@link WarmUpBeforeTraffic} and the web server, while the old instance keeps serving.
 *
 * <p>Only a deploy waits. A version that has opened its port before (see {@link Deployments}) is starting cold after
 * scaling to zero, with a request waiting for it, and opens at once. So does a start-up whose database can't say.
 *
 * <p>Off unless {@code cfp.quiet-window.enabled}, which the {@code Dockerfile} turns on.
 */
@Component
class OpenPortInQuietWindow implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(OpenPortInQuietWindow.class);

    private final Deployments deployments;
    private final String version;
    private final boolean enabled;
    private final QuietWindow window;
    private final Clock clock;
    private volatile boolean running;

    @Autowired
    OpenPortInQuietWindow(Deployments deployments, @Value("${GIT_SHA:dev}") String version,
                          @Value("${cfp.quiet-window.enabled:false}") boolean enabled,
                          @Value("${cfp.quiet-window.open-at:10s}") Duration openAt,
                          @Value("${cfp.quiet-window.grace:3s}") Duration grace) {
        this(deployments, version, enabled, new QuietWindow(openAt, grace), Clock.systemUTC());
    }

    OpenPortInQuietWindow(Deployments deployments, String version, boolean enabled, QuietWindow window, Clock clock) {
        this.deployments = deployments;
        this.version = version;
        this.enabled = enabled;
        this.window = window;
        this.clock = clock;
    }

    @Override
    public int getPhase() {
        return WarmUpBeforeTraffic.PHASE + 1;
    }

    @Override
    public void start() {
        running = true;
        if (!enabled) {
            return;
        }
        try {
            if (deployments.hasOpened(version)) {
                log.info("Version {} has opened before, so this is a cold start: opening the port now", version);
                return;
            }
            Duration wait = window.waitFrom(clock.instant());
            log.info("Version {} is a deploy: opening the port in {} ms, when the harness is quiet", version,
                    wait.toMillis());
            Thread.sleep(wait);
            deployments.recordOpened(version);
        } catch (DataAccessException e) {
            log.warn("Can't tell whether version {} is a deploy: opening the port now", version, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
