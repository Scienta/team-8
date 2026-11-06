package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Talk submission and lookup ({@code talks} base spec), and the committee's list of talks. Runs the real app on a
 * random port with a throwaway database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TalksTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");
    private static final Pattern SUBMITTED_AT = Pattern.compile("\"submittedAt\":\"([^\"]*)\"");
    private static final Pattern SUBMITTED_ON = Pattern.compile("\"submittedOn\":\"([^\"]*)\"");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    // ---------------------------------------------------------------- POST /talks, GET /talks/{id}

    @Test
    void theFormatDecidesTheDuration() throws Exception {
        Map<String, Integer> minutes = new LinkedHashMap<>();
        minutes.put("LIGHTNING_10", 10);
        minutes.put("LIGHTNING_20", 20);
        minutes.put("PRESENTATION", 45);
        minutes.put("WORKSHOP", 120);
        for (Map.Entry<String, Integer> e : minutes.entrySet()) {
            String speaker = newSpeaker();
            String created = expect(201, "POST", "/talks", talk("Talk", e.getKey(), "BEGINNER", speaker));
            assertTrue(created.contains("\"durationMinutes\":" + e.getValue()), created);
        }
    }

    @Test
    void theClientCannotChooseTheDuration() throws Exception {
        String speaker = newSpeaker();
        String created = expect(201, "POST", "/talks",
                "{\"title\":\"Short\",\"format\":\"WORKSHOP\",\"level\":\"BEGINNER\",\"durationMinutes\":5,\"speakerId\":"
                        + speaker + "}");
        assertTrue(created.contains("\"durationMinutes\":120"), created);
    }

    @Test
    void aSubmittedTalkIsLookedUpAsSubmitted() throws Exception {
        String speaker = newSpeaker();
        String created = expect(201, "POST", "/talks", talk("Engines", "LIGHTNING_20", "ADVANCED", speaker));
        String found = expect(200, "GET", "/talks/" + id(created), null);
        for (String body : Arrays.asList(created, found)) {
            assertTrue(body.contains("\"title\":\"Engines\""), body);
            assertTrue(body.contains("\"format\":\"LIGHTNING_20\""), body);
            assertTrue(body.contains("\"level\":\"ADVANCED\""), body);
            assertTrue(body.contains("\"speakerId\":" + speaker), body);
            assertTrue(body.contains("\"status\":\"SUBMITTED\""), body);
            assertTrue(body.contains("\"ratingCount\":0"), body);
            assertTrue(body.contains("\"averageRating\":null"), body);
        }
    }

    @Test
    void submittedAtAndSubmittedOnHaveTheLegacyFormats() throws Exception {
        Instant before = Instant.now().minusSeconds(1);
        String created = expect(201, "POST", "/talks", talk("Clock", "PRESENTATION", "BEGINNER", newSpeaker()));
        Instant after = Instant.now().plusSeconds(1);

        String at = group(SUBMITTED_AT, created);
        assertTrue(at.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\+00:00"), at);
        Instant submitted = OffsetDateTime.parse(at).toInstant();
        assertTrue(!submitted.isBefore(before) && !submitted.isAfter(after), at);

        String on = group(SUBMITTED_ON, created);
        assertEquals(submitted.atOffset(ZoneOffset.UTC).toLocalDate().toString(), on);
        assertEquals(created, expect(200, "GET", "/talks/" + id(created), null));
    }

    @Test
    void aMissingOrBlankTitleIsABadRequest() throws Exception {
        String speaker = newSpeaker();
        expect(400, "POST", "/talks",
                "{\"format\":\"PRESENTATION\",\"level\":\"BEGINNER\",\"speakerId\":" + speaker + "}");
        expect(400, "POST", "/talks", talk("", "PRESENTATION", "BEGINNER", speaker));
        expect(400, "POST", "/talks", talk("   ", "PRESENTATION", "BEGINNER", speaker));
        assertEquals("[]", expect(200, "GET", "/speakers/" + speaker + "/talks", null));
    }

    @Test
    void onlyTheFourFormatsInCapitalsAreFormats() throws Exception {
        String speaker = newSpeaker();
        for (String format : Arrays.asList("KEYNOTE", "presentation", "Presentation", "LIGHTNING_15", "")) {
            expect(400, "POST", "/talks", talk("Talk", format, "BEGINNER", speaker));
        }
        expect(400, "POST", "/talks", "{\"title\":\"Talk\",\"level\":\"BEGINNER\",\"speakerId\":" + speaker + "}");
        assertEquals("[]", expect(200, "GET", "/speakers/" + speaker + "/talks", null));
    }

    @Test
    void onlyTheThreeLevelsInCapitalsAreLevels() throws Exception {
        String speaker = newSpeaker();
        for (String level : Arrays.asList("EXPERT", "beginner", "Beginner", "")) {
            expect(400, "POST", "/talks", talk("Talk", "PRESENTATION", level, speaker));
        }
        expect(400, "POST", "/talks", "{\"title\":\"Talk\",\"format\":\"PRESENTATION\",\"speakerId\":" + speaker + "}");
        for (String level : Arrays.asList("BEGINNER", "INTERMEDIATE", "ADVANCED")) {
            expect(201, "POST", "/talks", talk("Talk", "PRESENTATION", level, speaker));
        }
    }

    @Test
    void aTalkNeedsARegisteredSpeaker() throws Exception {
        expect(400, "POST", "/talks", "{\"title\":\"Talk\",\"format\":\"PRESENTATION\",\"level\":\"BEGINNER\"}");
        expect(400, "POST", "/talks", talk("Talk", "PRESENTATION", "BEGINNER", "2147483000"));
    }

    @Test
    void aBodyThatIsNotJsonIsABadRequest() throws Exception {
        expect(400, "POST", "/talks", "{\"title\":");
        expect(400, "POST", "/talks", null);
        expect(400, "POST", "/talks", "{\"title\":\"Talk\",\"format\":\"PRESENTATION\",\"level\":\"BEGINNER\",\"speakerId\":\"abc\"}");
    }

    @Test
    void aFourthTalkIsAConflict() throws Exception {
        String speaker = newSpeaker();
        for (int i = 0; i < 3; i++) {
            expect(201, "POST", "/talks", talk("Talk " + i, "PRESENTATION", "BEGINNER", speaker));
        }
        expect(409, "POST", "/talks", talk("Talk 3", "PRESENTATION", "BEGINNER", speaker));
        assertEquals(3, count(expect(200, "GET", "/speakers/" + speaker + "/talks", null)));
    }

    @Test
    void concurrentSubmissionsNeverExceedThreeTalks() throws Exception {
        String speaker = newSpeaker();
        List<CompletableFuture<HttpResponse<String>>> sent = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            sent.add(http.sendAsync(request("POST", "/talks", talk("Talk " + i, "PRESENTATION", "BEGINNER", speaker)),
                    HttpResponse.BodyHandlers.ofString()));
        }
        int created = 0;
        for (CompletableFuture<HttpResponse<String>> f : sent) {
            HttpResponse<String> res = f.join();
            assertTrue(res.statusCode() == 201 || res.statusCode() == 409, res.statusCode() + " " + res.body());
            if (res.statusCode() == 201) {
                created++;
            }
        }
        assertEquals(3, created);
        assertEquals(3, count(expect(200, "GET", "/speakers/" + speaker + "/talks", null)));
    }

    @Test
    void anUnknownTalkIsNotFound() throws Exception {
        expect(404, "GET", "/talks/2147483000", null);
    }

    @Test
    void aNonNumericTalkIdIsABadRequest() throws Exception {
        expect(400, "GET", "/talks/abc", null);
    }

    // ---------------------------------------------------------------- GET /speakers/{id}/talks, GET /talks

    @Test
    void aSpeakersTalksAreListedOldestFirst() throws Exception {
        String speaker = newSpeaker();
        String first = id(expect(201, "POST", "/talks", talk("First", "PRESENTATION", "BEGINNER", speaker)));
        String second = id(expect(201, "POST", "/talks", talk("Second", "WORKSHOP", "BEGINNER", speaker)));
        String third = id(expect(201, "POST", "/talks", talk("Third", "LIGHTNING_10", "BEGINNER", speaker)));
        List<String> expected = Arrays.asList(first, second, third);
        assertEquals(expected, ids(expect(200, "GET", "/speakers/" + speaker + "/talks", null)));
        assertEquals(expected, ids(expect(200, "GET", "/talks?speakerId=" + speaker, null)));
    }

    @Test
    void theCommitteeListsAllTalksOldestFirst() throws Exception {
        String first = id(expect(201, "POST", "/talks", talk("First", "PRESENTATION", "BEGINNER", newSpeaker())));
        String second = id(expect(201, "POST", "/talks", talk("Second", "PRESENTATION", "BEGINNER", newSpeaker())));
        List<String> all = ids(expect(200, "GET", "/talks", null));
        assertTrue(all.indexOf(first) >= 0 && all.indexOf(first) < all.indexOf(second), all.toString());
    }

    @Test
    void anUnknownSortOrANonNumericSpeakerIdIsABadRequest() throws Exception {
        expect(400, "GET", "/talks?sort=title", null);
        expect(400, "GET", "/talks?speakerId=abc", null);
    }

    // ---------------------------------------------------------------- helpers

    private String newSpeaker() throws Exception {
        return id(expect(201, "POST", "/speakers",
                "{\"name\":\"Grace Hopper\",\"email\":\"grace-" + UUID.randomUUID() + "@example.com\"}"));
    }

    private static String talk(String title, String format, String level, String speaker) {
        return "{\"title\":\"" + title + "\",\"format\":\"" + format + "\",\"level\":\"" + level
                + "\",\"speakerId\":" + speaker + "}";
    }

    private String expect(int status, String method, String path, String json) throws Exception {
        HttpResponse<String> res = http.send(request(method, path, json), HttpResponse.BodyHandlers.ofString());
        assertEquals(status, res.statusCode(), method + " " + path + ": " + res.body());
        return res.body();
    }

    private static String id(String body) {
        return group(ID, body);
    }

    private static String group(Pattern pattern, String body) {
        Matcher m = pattern.matcher(body);
        assertTrue(m.find(), body);
        return m.group(1);
    }

    /** The talk ids in a list, in order. A talk's own "id" comes before any other field named "id". */
    private static List<String> ids(String body) {
        List<String> ids = new ArrayList<>();
        Matcher m = ID.matcher(body);
        while (m.find()) {
            ids.add(m.group(1));
        }
        return ids;
    }

    private static int count(String body) {
        return ids(body).size();
    }

    private HttpRequest request(String method, String path, String json) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json))
                .build();
    }
}
