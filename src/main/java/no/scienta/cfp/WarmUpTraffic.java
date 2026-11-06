package no.scienta.cfp;

import java.util.List;

/**
 * The requests that warm the app up before it takes traffic: one for each endpoint the harness calls, aimed at ids
 * that don't exist or with bodies that are refused, so that every request goes through routing, JSON, validation and
 * the database but none of them writes anything. {@code WarmUpTest} checks that they leave the database as it was.
 */
final class WarmUpTraffic {

    /** A request sent while warming up; {@code body} is JSON, or null for none. */
    record WarmUpRequest(String method, String path, String body) {
    }

    /** No row has id 0: the ids are SERIAL, which starts at 1. */
    static final List<WarmUpRequest> REQUESTS = List.of(
            new WarmUpRequest("GET", "/health", null),
            new WarmUpRequest("POST", "/speakers", "{\"name\":\"\",\"email\":\"not an email\",\"bio\":null}"),
            new WarmUpRequest("GET", "/speakers/0", null),
            new WarmUpRequest("PUT", "/speakers/0", "{\"name\":\"Warm-up\",\"email\":\"warm-up@example.test\"}"),
            new WarmUpRequest("GET", "/speakers/0/talks", null),
            new WarmUpRequest("POST", "/talks",
                    "{\"title\":\"Warm-up\",\"format\":\"WORKSHOP\",\"level\":\"BEGINNER\",\"speakerId\":0}"),
            new WarmUpRequest("GET", "/talks/0", null),
            new WarmUpRequest("GET", "/talks?speakerId=0", null),
            new WarmUpRequest("PUT", "/talks/0/ratings/0", "{\"score\":3}"),
            new WarmUpRequest("PUT", "/talks/0/status", "{\"status\":\"ACCEPTED\"}"),
            new WarmUpRequest("POST", "/talks/0/withdraw", "{\"speakerId\":0}"),
            new WarmUpRequest("PUT", "/talks/0/co-speakers/0", null),
            new WarmUpRequest("DELETE", "/talks/0/co-speakers/0", null),
            new WarmUpRequest("POST", "/workshops/0/registrations",
                    "{\"name\":\"Warm-up\",\"email\":\"warm-up@example.test\"}"),
            new WarmUpRequest("GET", "/workshops/0/registrations", null));

    private WarmUpTraffic() {
    }
}
