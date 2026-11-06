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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The rules of {@code workflow/specs/submission-cap-cr.md}, against the real app on a random port with a throwaway
 * database. Test names cite the rule (R1–R10) they cover.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SubmissionCapTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");
    private static final Pattern CO_SPEAKER_IDS = Pattern.compile("\"coSpeakerIds\":\\[([^\\]]*)\\]");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void r1_r3_coSpeakingCountsTowardsTheCapOnSubmission() throws Exception {
        String speaker = newSpeaker();
        expect(200, "PUT", coSpeakerPath(newTalk(newSpeaker()), speaker));
        expect(200, "PUT", coSpeakerPath(newTalk(newSpeaker()), speaker));
        submit(201, speaker);
        submit(409, speaker);
        assertEquals(1, count(expect(200, "GET", "/speakers/" + speaker + "/talks")));
    }

    @Test
    void r1_r4_aSpeakerAtTheCapCantBeAddedAsCoSpeaker() throws Exception {
        String speaker = newSpeaker();
        submit(201, speaker);
        submit(201, speaker);
        expect(200, "PUT", coSpeakerPath(newTalk(newSpeaker()), speaker));
        String talk = newTalk(newSpeaker());
        expect(409, "PUT", coSpeakerPath(talk, speaker));
        assertEquals("", coSpeakers(expect(200, "GET", "/talks/" + talk)));
    }

    @Test
    void r4_waitlistedAndAcceptedTalksCount() throws Exception {
        String speaker = newSpeaker();
        String waitlisted = submit(201, speaker);
        String accepted = submit(201, speaker);
        submit(201, speaker);
        decide(waitlisted, "WAITLISTED");
        rate(accepted, 1);
        rate(accepted, 2);
        decide(accepted, "ACCEPTED");
        submit(409, speaker);
        expect(409, "PUT", coSpeakerPath(newTalk(newSpeaker()), speaker));
    }

    @Test
    void r5_aRejectedOrWithdrawnTalkTakesASpeakerAtTheCapAndDoesntCount() throws Exception {
        String speaker = newSpeaker();
        String own = submit(201, speaker);
        submit(201, speaker);
        submit(201, speaker);

        String rejected = newTalk(newSpeaker());
        decide(rejected, "REJECTED");
        assertEquals(speaker, coSpeakers(expect(200, "PUT", coSpeakerPath(rejected, speaker))));
        String otherMain = newSpeaker();
        String withdrawn = newTalk(otherMain);
        expect(200, "POST", "/talks/" + withdrawn + "/withdraw", "{\"speakerId\":" + otherMain + "}");
        assertEquals(speaker, coSpeakers(expect(200, "PUT", coSpeakerPath(withdrawn, speaker))));

        expect(200, "POST", "/talks/" + own + "/withdraw", "{\"speakerId\":" + speaker + "}");
        submit(201, speaker);
    }

    @Test
    void r6_rejectingWithdrawingAndRemovingFreeAPlaceStraightAway() throws Exception {
        String speaker = newSpeaker();
        String toWithdraw = submit(201, speaker);
        String toReject = submit(201, speaker);
        String coTalk = newTalk(newSpeaker());
        expect(200, "PUT", coSpeakerPath(coTalk, speaker));
        submit(409, speaker);

        expect(200, "POST", "/talks/" + toWithdraw + "/withdraw", "{\"speakerId\":" + speaker + "}");
        submit(201, speaker);
        submit(409, speaker);

        decide(toReject, "REJECTED");
        expect(200, "PUT", coSpeakerPath(newTalk(newSpeaker()), speaker));
        expect(409, "PUT", coSpeakerPath(newTalk(newSpeaker()), speaker));

        expect(200, "DELETE", coSpeakerPath(coTalk, speaker));
        submit(201, speaker);
    }

    @Test
    void r6_aWithdrawnTalkFreesItsCoSpeakersToo() throws Exception {
        String speaker = newSpeaker();
        submit(201, speaker);
        submit(201, speaker);
        String main = newSpeaker();
        String talk = newTalk(main);
        expect(200, "PUT", coSpeakerPath(talk, speaker));
        submit(409, speaker);
        expect(200, "POST", "/talks/" + talk + "/withdraw", "{\"speakerId\":" + main + "}");
        submit(201, speaker);
    }

    @Test
    void r7_r8_aSpeakerOverTheCapKeepsEverythingButGetsNothingNew() throws Exception {
        String speaker = newSpeaker();
        List<String> own = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            own.add(submit(201, speaker));
        }
        String coTalk = newTalk(newSpeaker());
        // Before the change request co-speaking didn't count, so this was allowed then.
        jdbc.update("INSERT INTO co_speaker (talk_id, speaker_id) VALUES (?, ?)",
                Long.parseLong(coTalk), Long.parseLong(speaker));

        assertEquals(speaker, coSpeakers(expect(200, "PUT", coSpeakerPath(coTalk, speaker))));
        submit(409, speaker);
        expect(409, "PUT", coSpeakerPath(newTalk(newSpeaker()), speaker));
        assertEquals(3, count(expect(200, "GET", "/speakers/" + speaker + "/talks")));

        expect(200, "POST", "/talks/" + own.get(0) + "/withdraw", "{\"speakerId\":" + speaker + "}");
        submit(409, speaker);
        expect(200, "DELETE", coSpeakerPath(coTalk, speaker));
        submit(201, speaker);
    }

    @Test
    void r9_theTalkListShowsOnlyTalksAsMainSpeaker() throws Exception {
        String speaker = newSpeaker();
        String own = submit(201, speaker);
        expect(200, "PUT", coSpeakerPath(newTalk(newSpeaker()), speaker));
        String list = expect(200, "GET", "/speakers/" + speaker + "/talks");
        assertEquals(1, count(list));
        assertTrue(list.contains("\"id\":" + own + ","), list);
        assertEquals(1, count(expect(200, "GET", "/talks?speakerId=" + speaker)));
    }

    @Test
    void r10_concurrentSubmissionsAndInvitationsNeverExceedTheCap() throws Exception {
        for (int round = 0; round < 5; round++) {
            String speaker = newSpeaker();
            List<String> talks = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                talks.add(newTalk(newSpeaker()));
            }
            List<CompletableFuture<HttpResponse<String>>> sent = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                sent.add(http.sendAsync(request("POST", "/talks", talkJson(speaker)),
                        HttpResponse.BodyHandlers.ofString()));
                sent.add(http.sendAsync(request("PUT", coSpeakerPath(talks.get(i), speaker), null),
                        HttpResponse.BodyHandlers.ofString()));
            }
            int succeeded = 0;
            for (CompletableFuture<HttpResponse<String>> f : sent) {
                HttpResponse<String> res = f.join();
                int status = res.statusCode();
                assertTrue(status == 200 || status == 201 || status == 409, status + " " + res.body());
                if (status != 409) {
                    succeeded++;
                }
            }
            assertEquals(3, succeeded);
            int coSpeaking = 0;
            for (String talk : talks) {
                if (coSpeakers(expect(200, "GET", "/talks/" + talk)).equals(speaker)) {
                    coSpeaking++;
                }
            }
            assertEquals(3, count(expect(200, "GET", "/speakers/" + speaker + "/talks")) + coSpeaking);
        }
    }

    private static String coSpeakerPath(String talk, String speaker) {
        return "/talks/" + talk + "/co-speakers/" + speaker;
    }

    private void decide(String talk, String status) throws Exception {
        expect(200, "PUT", "/talks/" + talk + "/status", "{\"status\":\"" + status + "\"}");
    }

    private void rate(String talk, int reviewer) throws Exception {
        expect(200, "PUT", "/talks/" + talk + "/ratings/" + reviewer, "{\"score\":4}");
    }

    private String newSpeaker() throws Exception {
        return id(expect(201, "POST", "/speakers",
                "{\"name\":\"Grace Hopper\",\"email\":\"grace-" + UUID.randomUUID() + "@example.com\"}"));
    }

    private String newTalk(String speaker) throws Exception {
        return submit(201, speaker);
    }

    /** Submits a talk for the speaker and returns its id, or "" when refused. */
    private String submit(int status, String speaker) throws Exception {
        String body = expect(status, "POST", "/talks", talkJson(speaker));
        return status == 201 ? id(body) : "";
    }

    private static String talkJson(String speaker) {
        return "{\"title\":\"Compilers\",\"format\":\"PRESENTATION\",\"level\":\"BEGINNER\",\"speakerId\":" + speaker
                + "}";
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

    private static int count(String list) {
        Matcher m = ID.matcher(list);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    private String expect(int status, String method, String path) throws Exception {
        return expect(status, method, path, null);
    }

    private String expect(int status, String method, String path, String json) throws Exception {
        HttpResponse<String> res = http.send(request(method, path, json), HttpResponse.BodyHandlers.ofString());
        assertEquals(status, res.statusCode(), method + " " + path + ": " + res.body());
        return res.body();
    }

    private HttpRequest request(String method, String path, String json) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json))
                .build();
    }
}
