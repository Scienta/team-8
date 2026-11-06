package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
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
 * The rules and errors of {@code workflow/specs/co-speakers.md}, against the real app on a random port with a throwaway
 * database. Test names cite the rule (R1–R12) they cover. R9 was replaced by the submission cap, see
 * {@code SubmissionCapTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CoSpeakersTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");
    private static final Pattern CO_SPEAKER_IDS = Pattern.compile("\"coSpeakerIds\":\\[([^\\]]*)\\]");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void r6_newTalkHasNoCoSpeakers() throws Exception {
        String talk = newTalk(newSpeaker());
        assertEquals("", coSpeakers(send("GET", "/talks/" + talk).body()));
    }

    @Test
    void r2_r6_coSpeakersAreListedInTheOrderTheyWereAdded() throws Exception {
        String talk = newTalk(newSpeaker());
        String b = newSpeaker();
        String a = newSpeaker();
        assertEquals(b, coSpeakers(expect(200, "PUT", coSpeakerPath(talk, b))));
        assertEquals(b + "," + a, coSpeakers(expect(200, "PUT", coSpeakerPath(talk, a))));
        assertEquals(b + "," + a, coSpeakers(expect(200, "GET", "/talks/" + talk)));
        assertTrue(expect(200, "GET", "/talks").contains("\"coSpeakerIds\":[" + b + "," + a + "]"));
    }

    @Test
    void r7_addingAnExistingCoSpeakerChangesNothingEvenWhenFull() throws Exception {
        String talk = newTalk(newSpeaker());
        String a = newSpeaker();
        String b = newSpeaker();
        expect(200, "PUT", coSpeakerPath(talk, a));
        expect(200, "PUT", coSpeakerPath(talk, b));
        assertEquals(a + "," + b, coSpeakers(expect(200, "PUT", coSpeakerPath(talk, a))));
    }

    @Test
    void r4_aThirdCoSpeakerIsAConflict() throws Exception {
        String talk = newTalk(newSpeaker());
        expect(200, "PUT", coSpeakerPath(talk, newSpeaker()));
        expect(200, "PUT", coSpeakerPath(talk, newSpeaker()));
        expect(409, "PUT", coSpeakerPath(talk, newSpeaker()));
    }

    @Test
    void r5_theMainSpeakerCantCoSpeakTheirOwnTalk() throws Exception {
        String main = newSpeaker();
        String talk = newTalk(main);
        expect(400, "PUT", coSpeakerPath(talk, main));
        assertEquals("", coSpeakers(expect(200, "GET", "/talks/" + talk)));
    }

    @Test
    void r3_r10_removedCoSpeakerIsGoneAndGoesLastWhenReAdded() throws Exception {
        String talk = newTalk(newSpeaker());
        String a = newSpeaker();
        String b = newSpeaker();
        expect(200, "PUT", coSpeakerPath(talk, a));
        expect(200, "PUT", coSpeakerPath(talk, b));
        assertEquals(b, coSpeakers(expect(200, "DELETE", coSpeakerPath(talk, a))));
        assertEquals(b + "," + a, coSpeakers(expect(200, "PUT", coSpeakerPath(talk, a))));
    }

    @Test
    void r11_removingSomeoneWhoIsNotACoSpeakerIsNotFoundAndChangesNothing() throws Exception {
        String talk = newTalk(newSpeaker());
        String a = newSpeaker();
        String b = newSpeaker();
        expect(200, "PUT", coSpeakerPath(talk, a));
        expect(200, "PUT", coSpeakerPath(talk, b));
        expect(200, "DELETE", coSpeakerPath(talk, a));
        expect(404, "DELETE", coSpeakerPath(talk, a));
        expect(404, "DELETE", coSpeakerPath(talk, newSpeaker()));
        assertEquals(b, coSpeakers(expect(200, "GET", "/talks/" + talk)));
    }

    @Test
    void r8_aSpeakersTalkListShowsOnlyTheirOwnTalks() throws Exception {
        String co = newSpeaker();
        String talk = newTalk(newSpeaker());
        expect(200, "PUT", coSpeakerPath(talk, co));
        assertEquals("[]", expect(200, "GET", "/speakers/" + co + "/talks"));
        assertEquals("[]", expect(200, "GET", "/talks?speakerId=" + co));
    }

    @Test
    void unknownTalkOrSpeakerIsNotFound() throws Exception {
        String speaker = newSpeaker();
        String talk = newTalk(newSpeaker());
        expect(404, "PUT", coSpeakerPath("999999", speaker));
        expect(404, "PUT", coSpeakerPath(talk, "999999"));
        expect(404, "DELETE", coSpeakerPath("999999", speaker));
        expect(404, "DELETE", coSpeakerPath(talk, "999999"));
    }

    @Test
    void nonNumericIdsAreBadRequests() throws Exception {
        String talk = newTalk(newSpeaker());
        expect(400, "PUT", coSpeakerPath(talk, "abc"));
        expect(400, "PUT", coSpeakerPath("abc", newSpeaker()));
    }

    @Test
    void r12_concurrentAdditionsNeverExceedTwoCoSpeakers() throws Exception {
        String talk = newTalk(newSpeaker());
        List<String> speakers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            speakers.add(newSpeaker());
        }
        List<CompletableFuture<HttpResponse<String>>> sent = new ArrayList<>();
        for (String speaker : speakers) {
            sent.add(http.sendAsync(request("PUT", coSpeakerPath(talk, speaker), null),
                    HttpResponse.BodyHandlers.ofString()));
        }
        int added = 0;
        for (CompletableFuture<HttpResponse<String>> f : sent) {
            HttpResponse<String> res = f.join();
            assertTrue(res.statusCode() == 200 || res.statusCode() == 409, res.statusCode() + " " + res.body());
            if (res.statusCode() == 200) {
                added++;
            }
        }
        assertEquals(2, added);
        assertEquals(2, coSpeakers(expect(200, "GET", "/talks/" + talk)).split(",").length);
    }

    private static String coSpeakerPath(String talk, String speaker) {
        return "/talks/" + talk + "/co-speakers/" + speaker;
    }

    private String newSpeaker() throws Exception {
        return id(expect(201, "POST", "/speakers",
                "{\"name\":\"Ada Lovelace\",\"email\":\"ada-" + UUID.randomUUID() + "@example.com\"}"));
    }

    private String newTalk(String speaker) throws Exception {
        return id(expect(201, "POST", "/talks",
                "{\"title\":\"Engines\",\"format\":\"PRESENTATION\",\"level\":\"BEGINNER\",\"speakerId\":" + speaker + "}"));
    }

    private static String id(String body) {
        Matcher m = ID.matcher(body);
        assertTrue(m.find(), body);
        return m.group(1);
    }

    private static String coSpeakers(String body) {
        Matcher m = CO_SPEAKER_IDS.matcher(body);
        assertTrue(m.find(), body);
        return m.group(1);
    }

    private String expect(int status, String method, String path) throws Exception {
        return expect(status, method, path, null);
    }

    private String expect(int status, String method, String path, String json) throws Exception {
        HttpResponse<String> res = http.send(request(method, path, json), HttpResponse.BodyHandlers.ofString());
        assertEquals(status, res.statusCode(), method + " " + path + ": " + res.body());
        return res.body();
    }

    private HttpResponse<String> send(String method, String path) throws Exception {
        return http.send(request(method, path, null), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest request(String method, String path, String json) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json))
                .build();
    }
}
