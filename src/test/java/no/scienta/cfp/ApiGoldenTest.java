package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.SpringVersion;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Guards "nothing else changes for our users" across framework upgrades. Sends a fixed script of requests, including
 * odd ones, to the real app with an empty database, and compares every status, content type and normalised body with
 * {@code src/test/resources/api-golden.txt}. Timestamps are reduced to their shape, and the running versions on
 * {@code /actuator/info} are checked against the libraries actually loaded before they are masked.
 *
 * <p>To re-record after reviewing a difference: {@code ./mvnw test -Dtest=ApiGoldenTest -Dgolden.record=true}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGoldenTest {

    private static final Path GOLDEN = Paths.get("src/test/resources/api-golden.txt");
    private static final Pattern ISO_DATE_TIME =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?([+-]\\d{2}:?\\d{2}|Z)?");
    private static final Pattern ISO_DATE = Pattern.compile("\"\\d{4}-\\d{2}-\\d{2}\"");
    private static final Pattern US_SHORT = Pattern.compile("\\d{1,2}/\\d{1,2}/\\d{2}, \\d{1,2}:\\d{2}(.)(AM|PM)");
    private static final Pattern NORWEGIAN_DAY = Pattern.compile("\\d{2}\\.\\d{2}\\.\\d{4}");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final StringBuilder transcript = new StringBuilder();

    @Test
    void theApiAnswersExactlyAsRecorded() throws Exception {
        speakers();
        talks();
        ratings();
        coSpeakersAndAcceptance();
        platform();

        String actual = transcript.toString();
        if (Boolean.getBoolean("golden.record")) {
            Files.createDirectories(GOLDEN.getParent());
            Files.write(GOLDEN, actual.getBytes(StandardCharsets.UTF_8));
        }
        String expected = new String(Files.readAllBytes(GOLDEN), StandardCharsets.UTF_8);
        assertEquals(expected, actual);
    }

    private void speakers() throws Exception {
        send("POST", "/speakers", "{\"name\":\"Ada Lovelace\",\"email\":\"ada@example.com\",\"bio\":\"Analyst\"}");
        send("POST", "/speakers/", "{\"name\":\"Grace Hopper\",\"email\":\"grace@example.com\"}");
        send("POST", "/speakers", "{\"name\":\"Barbara Liskov\",\"email\":\"barbara@example.com\",\"bio\":null}");
        send("POST", "/speakers", "{\"name\":\"Ada Again\",\"email\":\"ADA@Example.com\"}");
        send("POST", "/speakers", "{\"name\":\"\",\"email\":\"blank@example.com\"}");
        send("POST", "/speakers", "{\"name\":\"   \",\"email\":\"spaces@example.com\"}");
        send("POST", "/speakers", "{\"name\":\"No Email\"}");
        send("POST", "/speakers", "{\"name\":\"Bad Email\",\"email\":\"not-an-email\"}");
        send("POST", "/speakers", "{\"name\":\"Long Bio\",\"email\":\"long@example.com\",\"bio\":\"" + "x".repeat(501) + "\"}");
        send("POST", "/speakers", "{\"name\":\"Max Bio\",\"email\":\"max@example.com\",\"bio\":\"" + "x".repeat(500) + "\"}");
        send("POST", "/speakers", "{\"name\":\"Commented\",\"email\":\"c@example.com\" /* legacy client */, \"twitter\":\"@c\"}");
        send("POST", "/speakers", "{\"name\":42,\"email\":\"number@example.com\"}");
        send("POST", "/speakers", "not json");
        send("POST", "/speakers", "");
        sendAs("POST", "/speakers", "{\"name\":\"Plain\",\"email\":\"plain@example.com\"}", "text/plain");
        send("GET", "/speakers/1", null);
        send("GET", "/speakers/1/", null);
        send("GET", "/speakers/3", null);
        send("GET", "/speakers/999", null);
        send("GET", "/speakers/abc", null);
        send("GET", "/speakers/-1", null);
        send("GET", "/speakers", null);
    }

    private void talks() throws Exception {
        send("POST", "/talks", talk("Characterise first", "WORKSHOP", "BEGINNER", "1"));
        send("POST", "/talks", talk("Lightning", "LIGHTNING_10", "INTERMEDIATE", "1"));
        send("POST", "/talks", talk("Twenty", "LIGHTNING_20", "ADVANCED", "2"));
        send("POST", "/talks", talk("Talk", "PRESENTATION", "BEGINNER", "2"));
        send("POST", "/talks", talk("Third", "PRESENTATION", "BEGINNER", "2"));
        send("POST", "/talks", talk("Fourth", "PRESENTATION", "BEGINNER", "2"));
        send("POST", "/talks/", talk("Slash", "PRESENTATION", "BEGINNER", "3"));
        send("POST", "/talks", talk("Lower", "workshop", "BEGINNER", "3"));
        send("POST", "/talks", talk("Keynote", "KEYNOTE", "BEGINNER", "3"));
        send("POST", "/talks", talk("Level", "WORKSHOP", "EXPERT", "3"));
        send("POST", "/talks", talk("  ", "WORKSHOP", "BEGINNER", "3"));
        send("POST", "/talks", talk("Ghost", "WORKSHOP", "BEGINNER", "999"));
        send("POST", "/talks", talk("String id", "WORKSHOP", "BEGINNER", "\"3\""));
        send("POST", "/talks", talk("Float id", "WORKSHOP", "BEGINNER", "3.0"));
        send("POST", "/talks", talk("Fraction id", "WORKSHOP", "BEGINNER", "3.7"));
        send("POST", "/talks", "{\"title\":\"No speaker\",\"format\":\"WORKSHOP\",\"level\":\"BEGINNER\"}");
        send("POST", "/talks", "{\"title\":\"Own length\",\"format\":\"WORKSHOP\",\"level\":\"BEGINNER\",\"speakerId\":3,"
                + "\"durationMinutes\":5,\"status\":\"ACCEPTED\"}");
        send("GET", "/talks/1", null);
        send("GET", "/talks/1/", null);
        send("GET", "/talks/999", null);
        send("GET", "/talks/abc", null);
        send("GET", "/speakers/2/talks", null);
        send("GET", "/speakers/2/talks/", null);
        send("GET", "/speakers/999/talks", null);
    }

    private void ratings() throws Exception {
        send("PUT", "/talks/1/ratings/1", "{\"score\":4}");
        send("PUT", "/talks/1/ratings/2", "{\"score\":4}");
        send("PUT", "/talks/1/ratings/3", "{\"score\":4}");
        send("PUT", "/talks/1/ratings/4", "{\"score\":5}");
        send("PUT", "/talks/1/ratings/4", "{\"score\":5}");
        send("PUT", "/talks/2/ratings/1", "{\"score\":3}");
        send("PUT", "/talks/2/ratings/1", "{\"score\":5}");
        send("PUT", "/talks/3/ratings/7", "{\"score\":1}");
        for (String bad : new String[] {"{\"score\":0}", "{\"score\":6}", "{\"score\":3.5}", "{\"score\":3.0}",
                "{\"score\":\"3\"}", "{\"score\":null}", "{}", "not json", "{\"score\":4,\"comment\":\"ok\"}"}) {
            send("PUT", "/talks/3/ratings/8", bad);
        }
        send("PUT", "/talks/3/ratings/0", "{\"score\":3}");
        send("PUT", "/talks/3/ratings/-1", "{\"score\":3}");
        send("PUT", "/talks/3/ratings/abc", "{\"score\":3}");
        send("PUT", "/talks/999/ratings/1", "{\"score\":3}");
        send("GET", "/talks/1", null);
        send("GET", "/talks", null);
        send("GET", "/talks?sort=rating", null);
        send("GET", "/talks?sort=title", null);
        send("GET", "/talks?speakerId=2", null);
        send("GET", "/talks?speakerId=999", null);
    }

    private void coSpeakersAndAcceptance() throws Exception {
        send("PUT", "/talks/1/co-speakers/2", null);
        send("PUT", "/talks/1/co-speakers/3", null);
        send("PUT", "/talks/1/co-speakers/2", null);
        send("DELETE", "/talks/1/co-speakers/3", null);
        send("PUT", "/talks/1/status", "{\"status\":\"WAITLISTED\"}");
        send("PUT", "/talks/1/status", "{\"status\":\"ACCEPTED\"}");
        send("PUT", "/talks/2/status", "{\"status\":\"ACCEPTED\"}");
        send("PUT", "/talks/2/status", "{\"status\":\"accepted\"}");
        send("POST", "/talks/1/withdraw", "{\"speakerId\":2}");
        send("POST", "/talks/1/withdraw", "{\"speakerId\":1}");
        send("GET", "/talks/1", null);
    }

    private void platform() throws Exception {
        send("GET", "/health", null);
        String info = body("GET", "/actuator/info");
        assertEquals("{\"versions\":{\"java\":\"" + System.getProperty("java.version") + "\",\"springBoot\":\""
                + SpringBootVersion.getVersion() + "\",\"springFramework\":\"" + SpringVersion.getVersion() + "\"}}",
                info, "/actuator/info must report the versions actually running");
        send("GET", "/no/such/path", null);
        send("DELETE", "/speakers/1", null);
    }

    private static String talk(String title, String format, String level, String speakerId) {
        return "{\"title\":\"" + title + "\",\"format\":\"" + format + "\",\"level\":\"" + level + "\",\"speakerId\":"
                + speakerId + "}";
    }

    private void send(String method, String path, String json) throws Exception {
        sendAs(method, path, json, "application/json");
    }

    private void sendAs(String method, String path, String body, String contentType) throws Exception {
        HttpResponse<String> res = http.send(request(method, path, body, contentType),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        transcript.append(method).append(' ').append(path);
        if (body != null) {
            transcript.append(' ').append(body.length() > 120 ? body.substring(0, 120) + "…" : body);
        }
        transcript.append("\n  -> ").append(res.statusCode()).append(' ')
                .append(res.headers().firstValue("Content-Type").orElse("-")).append('\n')
                .append("  ").append(normalise(res.body())).append("\n\n");
    }

    private String body(String method, String path) throws Exception {
        return http.send(request(method, path, null, "application/json"),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
    }

    private HttpRequest request(String method, String path, String body, String contentType) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", contentType)
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }

    /** Reduces timestamps to their shape (digits become 9), keeping separators and offsets, so format changes show. */
    static String normalise(String body) {
        String s = replaceDigits(body, ISO_DATE_TIME);
        s = replaceDigits(s, ISO_DATE);
        s = replaceDigits(s, NORWEGIAN_DAY);
        Matcher m = US_SHORT.matcher(s);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String space = m.group(1).equals(" ") ? " " : String.format("<U+%04X>", (int) m.group(1).charAt(0));
            m.appendReplacement(out, Matcher.quoteReplacement("M/d/yy, h:mm" + space + m.group(2)));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Turns every digit of each match into 9, except in a time-zone offset (group 2), which is part of the format. */
    private static String replaceDigits(String s, Pattern p) {
        Matcher m = p.matcher(s);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String offset = m.groupCount() >= 2 && m.group(2) != null ? m.group(2) : "";
            String shape = m.group().substring(0, m.group().length() - offset.length()).replaceAll("\\d", "9");
            m.appendReplacement(out, Matcher.quoteReplacement(shape + offset));
        }
        m.appendTail(out);
        return out.toString();
    }
}
