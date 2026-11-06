package no.scienta.cfp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Warms the app up before the web server opens its port. Right after start-up the JVM is still loading classes and
 * compiling, and the first burst of harness requests took 4–6 s each, past the harness's timeout. The platform sends
 * traffic to a new instance as soon as its port accepts connections, so this runs in the lifecycle phase just before
 * the web server starts: it opens a private connector on a random loopback port, sends {@link WarmUpTraffic} through
 * the whole stack (Tomcat, filters, Spring MVC, JSON, validation, the database) for a number of rounds, and closes it.
 * Meanwhile the old instance keeps serving.
 *
 * <p>Bounded by {@code cfp.warm-up.rounds} and {@code cfp.warm-up.timeout}. A failure is logged and doesn't stop the
 * app from starting.
 */
@Component
class WarmUpBeforeTraffic implements SmartLifecycle {

    /** Marks warm-up requests, so that {@link RequestLoggingFilter} leaves them out of the logs. */
    static final String HEADER = "X-Warm-Up";

    private static final Logger log = LoggerFactory.getLogger(WarmUpBeforeTraffic.class);

    private final ApplicationContext context;
    private final int rounds;
    private final Duration timeout;
    private volatile boolean running;

    WarmUpBeforeTraffic(ApplicationContext context, @Value("${cfp.warm-up.rounds:40}") int rounds,
                        @Value("${cfp.warm-up.timeout:20s}") Duration timeout) {
        this.context = context;
        this.rounds = rounds;
        this.timeout = timeout;
    }

    /** Two phases before the web server, leaving the phase in between for {@link OpenPortInQuietWindow}. */
    static final int PHASE = WebServerApplicationContext.START_STOP_LIFECYCLE_PHASE - 2;

    @Override
    public int getPhase() {
        return PHASE;
    }

    @Override
    public void start() {
        running = true;
        if (rounds <= 0 || !(context instanceof WebServerApplicationContext web)
                || !(web.getWebServer() instanceof TomcatWebServer tomcat)) {
            return;
        }
        long started = System.nanoTime();
        Service service = tomcat.getTomcat().getService();
        Connector connector = new Connector();
        connector.setPort(0);
        connector.setProperty("address", "127.0.0.1");
        try {
            service.addConnector(connector);
            Map<String, Integer> answers = send(connector.getLocalPort(), rounds, timeout);
            log.info("Warmed up in {} ms, answers by status: {}",
                    Duration.ofNanos(System.nanoTime() - started).toMillis(), answers);
        } catch (RuntimeException e) {
            log.warn("Warm-up failed after {} ms, starting anyway",
                    Duration.ofNanos(System.nanoTime() - started).toMillis(), e);
        } finally {
            service.removeConnector(connector);
            destroy(connector);
        }
    }

    /**
     * Sends {@link WarmUpTraffic} to {@code localhost:port}, all requests of a round at once, until {@code rounds}
     * rounds are done or {@code timeout} has passed. Returns how many answers had each status ("none" for no answer).
     */
    static Map<String, Integer> send(int port, int rounds, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        Map<String, Integer> answers = new TreeMap<>();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        for (int round = 0; round < rounds && System.nanoTime() < deadline; round++) {
            List<CompletableFuture<String>> statuses = WarmUpTraffic.REQUESTS.stream()
                    .map(r -> http.sendAsync(request(port, r), HttpResponse.BodyHandlers.discarding())
                            .handle((res, e) -> res == null ? "none" : String.valueOf(res.statusCode())))
                    .toList();
            statuses.forEach(s -> answers.merge(s.join(), 1, Integer::sum));
        }
        return answers;
    }

    private static HttpRequest request(int port, WarmUpTraffic.WarmUpRequest r) {
        HttpRequest.BodyPublisher body = r.body() == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(r.body());
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + r.path()))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header(HEADER, "true")
                .method(r.method(), body)
                .build();
    }

    private static void destroy(Connector connector) {
        try {
            connector.destroy();
        } catch (Exception e) {
            log.warn("Could not close the warm-up connector", e);
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
