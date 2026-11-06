package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The programme committee's exports, {@code GET /admin/stats} and {@code GET /admin/talks.csv}. Runs the real app on a
 * random port with a throwaway database. The tests share it, so the statistics are checked by how much they grow.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminReportsTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void theStatisticsCountSpeakersWithTalksOnce() throws Exception {
        String before = expect(200, "GET", "/admin/stats", null);
        String ada = newSpeaker("Ada");
        String grace = newSpeaker("Grace");
        newSpeaker("Idle");
        expect(201, "POST", "/talks", talk("One", "WORKSHOP", ada));
        expect(201, "POST", "/talks", talk("Two", "PRESENTATION", ada));
        expect(201, "POST", "/talks", talk("Three", "PRESENTATION", grace));
        String after = expect(200, "GET", "/admin/stats", null);

        assertEquals(3, number(after, "speakers") - number(before, "speakers"), after);
        assertEquals(2, number(after, "speakersWithTalks") - number(before, "speakersWithTalks"), after);
        assertEquals(3, number(after, "talks") - number(before, "talks"), after);
        assertEquals(1, number(after, "WORKSHOP") - number(before, "WORKSHOP"), after);
        assertEquals(2, number(after, "PRESENTATION") - number(before, "PRESENTATION"), after);
    }

    @Test
    void theCsvHasARowPerTalkWithItsSpeakersName() throws Exception {
        String ada = newSpeaker("Ada; Lovelace");
        String grace = newSpeaker("Grace Hopper");
        String first = id(expect(201, "POST", "/talks", talk("Engines", "PRESENTATION", ada)));
        String second = id(expect(201, "POST", "/talks", talk("Compilers", "LIGHTNING_10", grace)));
        expect(200, "PUT", "/talks/" + first + "/ratings/1", "{\"score\":4}");
        expect(200, "PUT", "/talks/" + first + "/ratings/2", "{\"score\":5}");

        List<String> lines = Arrays.asList(expect(200, "GET", "/admin/talks.csv", null).split("\n"));
        assertEquals("id;title;format;minutes;level;speaker;status;submitted;rating;ratings", lines.get(0));
        String firstRow = row(lines, first);
        assertTrue(firstRow.startsWith(first + ";Engines;Presentation;45;Beginner;\"Ada; Lovelace\";SUBMITTED;"),
                firstRow);
        assertTrue(firstRow.endsWith(";4,5;2"), firstRow);
        String secondRow = row(lines, second);
        assertTrue(secondRow.startsWith(second + ";Compilers;Lightning 10;10;Beginner;Grace Hopper;SUBMITTED;"),
                secondRow);
        assertTrue(secondRow.endsWith(";;0"), secondRow);
    }

    // ---------------------------------------------------------------- helpers

    private String newSpeaker(String name) throws Exception {
        return id(expect(201, "POST", "/speakers",
                "{\"name\":\"" + name + "\",\"email\":\"admin-" + UUID.randomUUID() + "@example.com\"}"));
    }

    private static String talk(String title, String format, String speaker) {
        return "{\"title\":\"" + title + "\",\"format\":\"" + format + "\",\"level\":\"BEGINNER\",\"speakerId\":"
                + speaker + "}";
    }

    private static String row(List<String> lines, String talkId) {
        List<String> rows = lines.stream().filter(l -> l.startsWith(talkId + ";")).collect(Collectors.toList());
        assertEquals(1, rows.size(), lines.toString());
        return rows.get(0);
    }

    /** The whole number after the first {@code "field":} in the body, 0 if the field isn't there. */
    private static int number(String body, String field) {
        Matcher m = Pattern.compile("\"" + field + "\":(\\d+)").matcher(body);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private String expect(int status, String method, String path, String json) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (json == null) {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(json));
        }
        HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(status, res.statusCode(), method + " " + path + ": " + res.body());
        return res.body();
    }

    private static String id(String body) {
        Matcher m = ID.matcher(body);
        assertTrue(m.find(), body);
        return m.group(1);
    }
}
