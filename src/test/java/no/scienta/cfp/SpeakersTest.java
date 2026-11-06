package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
 * Speaker registration and lookup ({@code speakers} base spec), the admin update, and the old admin search. Runs the
 * real app on a random port with a throwaway database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpeakersTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    // ---------------------------------------------------------------- POST /speakers, GET /speakers/{id}

    @Test
    void aSpeakerIsLookedUpExactlyAsRegistered() throws Exception {
        String email = uniqueEmail("Ada");
        String created = expect(201, "POST", "/speakers",
                "{\"name\":\"Ada Lovelace\",\"email\":\"" + email + "\",\"bio\":\"Wrote the first program.\"}");
        String id = id(created);
        String found = expect(200, "GET", "/speakers/" + id, null);
        for (String body : Arrays.asList(created, found)) {
            assertTrue(body.contains("\"name\":\"Ada Lovelace\""), body);
            assertTrue(body.contains("\"email\":\"" + email + "\""), body);
            assertTrue(body.contains("\"bio\":\"Wrote the first program.\""), body);
        }
    }

    @Test
    void theBioIsOptional() throws Exception {
        String id = id(expect(201, "POST", "/speakers", speaker("Grace Hopper", uniqueEmail("grace"))));
        assertTrue(expect(200, "GET", "/speakers/" + id, null).contains("\"bio\":null"));
    }

    @Test
    void aBioOfExactlyFiveHundredCharactersIsAccepted() throws Exception {
        expect(201, "POST", "/speakers", speakerWithBio(uniqueEmail("max"), repeat('x', 500)));
    }

    @Test
    void aBioLongerThanFiveHundredCharactersIsABadRequest() throws Exception {
        String email = uniqueEmail("long");
        expect(400, "POST", "/speakers", speakerWithBio(email, repeat('x', 501)));
        expect(201, "POST", "/speakers", speaker("Long Bio", email));
    }

    @Test
    void aMissingOrBlankNameOrEmailIsABadRequest() throws Exception {
        String email = uniqueEmail("blank");
        for (String body : Arrays.asList(
                "{\"email\":\"" + email + "\"}",
                "{\"name\":\"\",\"email\":\"" + email + "\"}",
                "{\"name\":\"   \",\"email\":\"" + email + "\"}",
                "{\"name\":\"Ada Lovelace\"}",
                "{\"name\":\"Ada Lovelace\",\"email\":\"\"}",
                "{\"name\":\"Ada Lovelace\",\"email\":\"   \"}")) {
            expect(400, "POST", "/speakers", body);
        }
        expect(201, "POST", "/speakers", speaker("Ada Lovelace", email));
    }

    @Test
    void aBodyThatIsNotJsonIsABadRequest() throws Exception {
        expect(400, "POST", "/speakers", "{\"name\":");
        expect(400, "POST", "/speakers", null);
    }

    @Test
    void anEmailRegistersOnceRegardlessOfLetterCase() throws Exception {
        String email = uniqueEmail("grace");
        assertEquals(201, register("Grace Hopper", email).statusCode());
        assertEquals(409, register("Grace Hopper", email.toUpperCase()).statusCode());
    }

    @Test
    void concurrentRegistrationsOfOneEmailHaveOneWinner() throws Exception {
        String email = uniqueEmail("racer");
        List<CompletableFuture<HttpResponse<String>>> sent = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String variant = i % 2 == 0 ? email : email.toUpperCase();
            sent.add(http.sendAsync(request("POST", "/speakers", speaker("Racer " + i, variant)),
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
        assertEquals(1, created);
    }

    @Test
    void urlsWithATrailingSlashWorkLikeUrlsWithout() throws Exception {
        String id = id(expect(201, "POST", "/speakers/", speaker("Legacy Client", uniqueEmail("legacy"))));
        assertTrue(expect(200, "GET", "/speakers/" + id + "/", null).contains("\"name\":\"Legacy Client\""));
        expect(404, "GET", "/speakers/2147483000/", null);
    }

    @Test
    void anUnknownSpeakerIsNotFound() throws Exception {
        expect(404, "GET", "/speakers/2147483000", null);
    }

    @Test
    void aNonNumericSpeakerIdIsABadRequest() throws Exception {
        expect(400, "GET", "/speakers/abc", null);
    }

    // ---------------------------------------------------------------- GET /speakers/{id}/talks

    @Test
    void aNewSpeakerHasNoTalks() throws Exception {
        String id = id(expect(201, "POST", "/speakers", speaker("No Talks", uniqueEmail("none"))));
        assertEquals("[]", expect(200, "GET", "/speakers/" + id + "/talks", null));
    }

    @Test
    void theTalksOfAnUnknownSpeakerAreNotFound() throws Exception {
        expect(404, "GET", "/speakers/2147483000/talks", null);
        expect(400, "GET", "/speakers/abc/talks", null);
    }

    // ---------------------------------------------------------------- PUT /speakers/{id} (admin)

    @Test
    void theAdminCanCorrectASpeaker() throws Exception {
        String id = id(expect(201, "POST", "/speakers", speaker("Ada Lovelase", uniqueEmail("typo"))));
        String email = uniqueEmail("fixed");
        String updated = expect(200, "PUT", "/speakers/" + id,
                "{\"name\":\"Ada Lovelace\",\"email\":\"" + email + "\",\"bio\":\"Fixed.\"}");
        assertTrue(updated.contains("\"name\":\"Ada Lovelace\""), updated);
        String found = expect(200, "GET", "/speakers/" + id, null);
        assertTrue(found.contains("\"name\":\"Ada Lovelace\""), found);
        assertTrue(found.contains("\"email\":\"" + email + "\""), found);
        assertTrue(found.contains("\"bio\":\"Fixed.\""), found);
    }

    @Test
    void anUpdateWithAMissingNameOrInvalidEmailIsABadRequestAndChangesNothing() throws Exception {
        String email = uniqueEmail("keep");
        String id = id(expect(201, "POST", "/speakers", speaker("Kept Name", email)));
        expect(400, "PUT", "/speakers/" + id, "{\"email\":\"" + uniqueEmail("other") + "\"}");
        expect(400, "PUT", "/speakers/" + id, "{\"name\":\"\",\"email\":\"" + uniqueEmail("other") + "\"}");
        expect(400, "PUT", "/speakers/" + id, "{\"name\":\"New Name\",\"email\":\"not-an-email\"}");
        expect(400, "PUT", "/speakers/" + id, "{\"name\":\"New Name\"}");
        String found = expect(200, "GET", "/speakers/" + id, null);
        assertTrue(found.contains("\"name\":\"Kept Name\""), found);
        assertTrue(found.contains("\"email\":\"" + email + "\""), found);
    }

    @Test
    void anUpdateToAnotherSpeakersEmailIsAConflict() throws Exception {
        String taken = uniqueEmail("taken");
        expect(201, "POST", "/speakers", speaker("First", taken));
        String id = id(expect(201, "POST", "/speakers", speaker("Second", uniqueEmail("second"))));
        expect(409, "PUT", "/speakers/" + id, "{\"name\":\"Second\",\"email\":\"" + taken.toUpperCase() + "\"}");
    }

    @Test
    void anUpdateOfAnUnknownSpeakerIsNotFound() throws Exception {
        expect(404, "PUT", "/speakers/2147483000", "{\"name\":\"Nobody\",\"email\":\"" + uniqueEmail("nobody") + "\"}");
    }

    // ---------------------------------------------------------------- GET /speakers/search (admin)

    @Test
    void searchMatchesNamesRegardlessOfCase() throws Exception {
        String name = "Lovelace" + UUID.randomUUID().toString().substring(0, 8);
        assertEquals(201, register("Ada " + name, uniqueEmail("ada")).statusCode());

        String found = expect(200, "GET", "/speakers/search?q=" + name.toLowerCase(), null);
        assertTrue(found.contains("\"name\":\"Ada " + name + "\""), found);
    }

    @Test
    void searchWithNoMatchIsAnEmptyList() throws Exception {
        assertEquals("[]", expect(200, "GET", "/speakers/search?q=nobody" + UUID.randomUUID(), null));
    }

    @Test
    void searchFindsANameWithAnApostrophe() throws Exception {
        String name = "O'Brien" + UUID.randomUUID().toString().substring(0, 8);
        assertEquals(201, register(name, uniqueEmail("obrien")).statusCode());

        String found = expect(200, "GET", "/speakers/search?q=" + encoded(name), null);
        assertTrue(found.contains("\"name\":\"" + name + "\""), found);
    }

    @Test
    void searchTreatsTheQueryAsTextAndNotAsSql() throws Exception {
        assertEquals(201, register("Ada Lovelace", uniqueEmail("ada")).statusCode());

        assertEquals("[]", expect(200, "GET", "/speakers/search?q=" + encoded("' OR 'a'='a' OR name ILIKE '"), null));
    }

    @Test
    void searchWithoutAQueryIsABadRequest() throws Exception {
        expect(400, "GET", "/speakers/search", null);
    }

    // ---------------------------------------------------------------- helpers

    private HttpResponse<String> register(String name, String email) throws Exception {
        return http.send(request("POST", "/speakers", speaker(name, email)), HttpResponse.BodyHandlers.ofString());
    }

    private String expect(int status, String method, String path, String json) throws Exception {
        HttpResponse<String> res = http.send(request(method, path, json), HttpResponse.BodyHandlers.ofString());
        assertEquals(status, res.statusCode(), method + " " + path + ": " + res.body());
        return res.body();
    }

    private static String encoded(String query) {
        return URLEncoder.encode(query, StandardCharsets.UTF_8);
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private static String speaker(String name, String email) {
        return "{\"name\":\"" + name + "\",\"email\":\"" + email + "\"}";
    }

    private static String speakerWithBio(String email, String bio) {
        return "{\"name\":\"Bio Writer\",\"email\":\"" + email + "\",\"bio\":\"" + bio + "\"}";
    }

    private static String repeat(char c, int n) {
        char[] chars = new char[n];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    private static String id(String body) {
        Matcher m = ID.matcher(body);
        assertTrue(m.find(), body);
        return m.group(1);
    }

    private HttpRequest request(String method, String path, String json) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json))
                .build();
    }
}
