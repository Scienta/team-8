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
 * The rules and errors of {@code workflow/specs/acceptance.md}, against the real app on a random port with a throwaway
 * database. Test names cite the rule (R1–R10) they cover.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AcceptanceTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");
    private static final Pattern STATUS = Pattern.compile("\"status\":\"([A-Z]+)\"");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void r1_r2_r7_submittedTalkCanBeWaitlistedThenAcceptedAndShowsItEverywhere() throws Exception {
        String speaker = newSpeaker();
        String talk = ratedTalk(speaker, 2);
        assertEquals("WAITLISTED", status(decide(200, talk, "WAITLISTED")));
        assertEquals("ACCEPTED", status(decide(200, talk, "ACCEPTED")));
        assertEquals("ACCEPTED", status(expect(200, "GET", "/talks/" + talk, null)));
        assertTrue(expect(200, "GET", "/speakers/" + speaker + "/talks", null).contains("\"status\":\"ACCEPTED\""));
        assertTrue(expect(200, "GET", "/talks", null).contains("\"status\":\"ACCEPTED\""));
    }

    @Test
    void r2_submittedTalkCanBeRejectedWithoutRatings() throws Exception {
        assertEquals("REJECTED", status(decide(200, newTalk(newSpeaker()), "REJECTED")));
    }

    @Test
    void r2_waitlistedTalkCanBeRejected() throws Exception {
        String talk = newTalk(newSpeaker());
        decide(200, talk, "WAITLISTED");
        assertEquals("REJECTED", status(decide(200, talk, "REJECTED")));
    }

    @Test
    void r3_r8_acceptedAndRejectedAreFinalForTheCommittee() throws Exception {
        String accepted = ratedTalk(newSpeaker(), 2);
        decide(200, accepted, "ACCEPTED");
        decide(409, accepted, "REJECTED");
        decide(409, accepted, "WAITLISTED");
        assertEquals("ACCEPTED", status(expect(200, "GET", "/talks/" + accepted, null)));

        String rejected = ratedTalk(newSpeaker(), 2);
        decide(200, rejected, "REJECTED");
        decide(409, rejected, "ACCEPTED");
        decide(409, rejected, "WAITLISTED");
        assertEquals("REJECTED", status(expect(200, "GET", "/talks/" + rejected, null)));
    }

    @Test
    void r4_acceptingNeedsTwoDifferentReviewers() throws Exception {
        String talk = ratedTalk(newSpeaker(), 1);
        rate(talk, 1, 5);
        decide(409, talk, "ACCEPTED");
        assertEquals("SUBMITTED", status(expect(200, "GET", "/talks/" + talk, null)));
        rate(talk, 2, 3);
        decide(200, talk, "ACCEPTED");
    }

    @Test
    void r9_aDecisionEqualToTheCurrentStatusIsAConflict() throws Exception {
        String talk = ratedTalk(newSpeaker(), 2);
        decide(200, talk, "WAITLISTED");
        decide(409, talk, "WAITLISTED");
        decide(200, talk, "ACCEPTED");
        decide(409, talk, "ACCEPTED");
        assertEquals("ACCEPTED", status(expect(200, "GET", "/talks/" + talk, null)));
        String rejected = newTalk(newSpeaker());
        decide(200, rejected, "REJECTED");
        decide(409, rejected, "REJECTED");
    }

    @Test
    void r5_r6_theSpeakerCanWithdrawUnlessRejectedOrWithdrawn() throws Exception {
        String speaker = newSpeaker();
        String accepted = ratedTalk(speaker, 2);
        decide(200, accepted, "ACCEPTED");
        assertEquals("WITHDRAWN", status(withdraw(200, accepted, speaker)));
        withdraw(409, accepted, speaker);
        decide(409, accepted, "WAITLISTED");

        String rejected = newTalk(speaker);
        decide(200, rejected, "REJECTED");
        withdraw(409, rejected, speaker);
        assertEquals("REJECTED", status(expect(200, "GET", "/talks/" + rejected, null)));

        assertEquals("WITHDRAWN", status(withdraw(200, newTalk(speaker), speaker)));
    }

    @Test
    void r5_onlyTheMainSpeakerCanWithdraw() throws Exception {
        String talk = newTalk(newSpeaker());
        String co = newSpeaker();
        expect(200, "PUT", "/talks/" + talk + "/co-speakers/" + co, null);
        withdraw(403, talk, co);
        withdraw(403, talk, newSpeaker());
        withdraw(403, talk, "999999");
        assertEquals("SUBMITTED", status(expect(200, "GET", "/talks/" + talk, null)));
    }

    @Test
    void badRequestsAndUnknownTalks() throws Exception {
        String speaker = newSpeaker();
        String talk = newTalk(speaker);
        for (String body : new String[] {"{}", "{\"status\":\"SUBMITTED\"}", "{\"status\":\"WITHDRAWN\"}",
                "{\"status\":\"accepted\"}", "{\"status\":1}", "not json"}) {
            expect(400, "PUT", "/talks/" + talk + "/status", body);
        }
        expect(400, "PUT", "/talks/" + talk + "/status", null);
        for (String body : new String[] {"{}", "{\"speakerId\":\"" + speaker + "\"}", "{\"speakerId\":1.5}"}) {
            expect(400, "POST", "/talks/" + talk + "/withdraw", body);
        }
        expect(400, "PUT", "/talks/abc/status", "{\"status\":\"REJECTED\"}");
        decide(404, "999999", "REJECTED");
        withdraw(404, "999999", speaker);
        assertEquals("SUBMITTED", status(expect(200, "GET", "/talks/" + talk, null)));
    }

    @Test
    void r10_concurrentDecisionsAndWithdrawalsEndInAReachableState() throws Exception {
        for (int round = 0; round < 3; round++) {
            String speaker = newSpeaker();
            String talk = ratedTalk(speaker, 2);
            List<CompletableFuture<HttpResponse<String>>> sent = new ArrayList<>();
            List<String> asked = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                for (String decision : new String[] {"ACCEPTED", "REJECTED"}) {
                    asked.add(decision);
                    sent.add(http.sendAsync(request("PUT", "/talks/" + talk + "/status",
                            "{\"status\":\"" + decision + "\"}"), HttpResponse.BodyHandlers.ofString()));
                }
            }
            List<String> granted = new ArrayList<>();
            for (int i = 0; i < sent.size(); i++) {
                HttpResponse<String> res = sent.get(i).join();
                assertTrue(res.statusCode() == 200 || res.statusCode() == 409, res.statusCode() + " " + res.body());
                if (res.statusCode() == 200) {
                    granted.add(asked.get(i));
                }
            }
            String finalStatus = status(expect(200, "GET", "/talks/" + talk, null));
            for (String g : granted) {
                assertEquals(finalStatus, g, "a " + g + " decision was granted but the talk ended " + finalStatus);
            }
        }
    }

    private String decide(int expected, String talk, String status) throws Exception {
        return expect(expected, "PUT", "/talks/" + talk + "/status", "{\"status\":\"" + status + "\"}");
    }

    private String withdraw(int expected, String talk, String speaker) throws Exception {
        return expect(expected, "POST", "/talks/" + talk + "/withdraw", "{\"speakerId\":" + speaker + "}");
    }

    private void rate(String talk, int reviewer, int score) throws Exception {
        expect(200, "PUT", "/talks/" + talk + "/ratings/" + reviewer, "{\"score\":" + score + "}");
    }

    private String ratedTalk(String speaker, int reviewers) throws Exception {
        String talk = newTalk(speaker);
        for (int r = 1; r <= reviewers; r++) {
            rate(talk, r, 4);
        }
        return talk;
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

    private static String status(String body) {
        Matcher m = STATUS.matcher(body);
        assertTrue(m.find(), body);
        return m.group(1);
    }

    private String expect(int status, String method, String path, String json) throws Exception {
        HttpResponse<String> res = http.send(request(method, path, json), HttpResponse.BodyHandlers.ofString());
        assertEquals(status, res.statusCode(), method + " " + path + " " + json + ": " + res.body());
        return res.body();
    }

    private HttpRequest request(String method, String path, String json) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json))
                .build();
    }
}
