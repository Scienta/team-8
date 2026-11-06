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
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The rules and errors of {@code workflow/specs/scheduling.md}, against the real app on a random port with a
 * throwaway database. Test names cite the rule (R1–R11) they cover. The venue is shared, so every test starts with an
 * empty programme.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProgrammeTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");
    private static final String[] ROOMS = {"R1", "R2", "R3"};
    private static final String[] SLOTS = {"S1", "S2", "S3", "S4", "S5"};

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void emptyProgramme() throws Exception {
        for (String room : ROOMS) {
            for (String slot : SLOTS) {
                send("DELETE", place(room, slot), null);
            }
        }
    }

    @Test
    void r2_r3_aTalkGoesIntoARoomAndSlotAndCanBeLookedUp() throws Exception {
        String talk = acceptedTalk("PRESENTATION");
        String expected = "{\"room\":\"R2\",\"slot\":\"S3\",\"start\":\"11:30\",\"end\":\"12:30\",\"talkId\":" + talk
                + "}";
        assertEquals(expected, schedule(200, "R2", "S3", talk));
        assertEquals(expected, expect(200, "GET", place("R2", "S3"), null));
    }

    @Test
    void r1_everySlotShowsItsTimes() throws Exception {
        String[][] times = {{"09:00", "10:00"}, {"10:15", "11:15"}, {"11:30", "12:30"}, {"13:30", "15:30"},
                {"15:45", "16:45"}};
        for (int i = 0; i < SLOTS.length; i++) {
            String body = schedule(200, "R1", SLOTS[i], acceptedTalk("LIGHTNING_20"));
            assertTrue(body.contains("\"start\":\"" + times[i][0] + "\",\"end\":\"" + times[i][1] + "\""), body);
        }
    }

    @Test
    void r4_aTalkTakenOutIsGoneAndCanGoSomewhereElse() throws Exception {
        String talk = acceptedTalk("PRESENTATION");
        schedule(200, "R1", "S1", talk);
        assertEquals("", expect(204, "DELETE", place("R1", "S1"), null));
        expect(404, "GET", place("R1", "S1"), null);
        expect(404, "DELETE", place("R1", "S1"), null);
        schedule(200, "R3", "S5", talk);
    }

    @Test
    void r5_onlyAnAcceptedTalkGoesOnTheProgramme() throws Exception {
        schedule(409, "R1", "S1", newTalk(newSpeaker(), "PRESENTATION"));
        String waitlisted = ratedTalk(newSpeaker(), "PRESENTATION");
        decide(waitlisted, "WAITLISTED");
        schedule(409, "R1", "S1", waitlisted);
        String rejected = ratedTalk(newSpeaker(), "PRESENTATION");
        decide(rejected, "REJECTED");
        schedule(409, "R1", "S1", rejected);
        String speaker = newSpeaker();
        String withdrawn = acceptedTalk(speaker, "PRESENTATION");
        withdraw(withdrawn, speaker);
        schedule(409, "R1", "S1", withdrawn);
        expect(404, "GET", place("R1", "S1"), null);
    }

    @Test
    void r6_aWorkshopNeedsTheLongSlot() throws Exception {
        String workshop = acceptedTalk("WORKSHOP");
        for (String slot : new String[] {"S1", "S2", "S3", "S5"}) {
            schedule(409, "R1", slot, workshop);
        }
        schedule(200, "R1", "S4", workshop);
    }

    @Test
    void r6_aWorkshopThatCanNeverFitIsRefusedBeforeItsStatus() throws Exception {
        schedule(409, "R1", "S1", newTalk(newSpeaker(), "WORKSHOP"));
    }

    @Test
    void r6_everythingElseFitsAnywhereIncludingTheLongSlot() throws Exception {
        schedule(200, "R1", "S4", acceptedTalk("PRESENTATION"));
        schedule(200, "R2", "S4", acceptedTalk("LIGHTNING_10"));
        schedule(200, "R3", "S1", acceptedTalk("LIGHTNING_20"));
    }

    @Test
    void r7_oneTalkPerRoomAndSlot() throws Exception {
        String first = acceptedTalk("PRESENTATION");
        schedule(200, "R1", "S1", first);
        schedule(409, "R1", "S1", acceptedTalk("PRESENTATION"));
        assertTrue(expect(200, "GET", place("R1", "S1"), null).contains("\"talkId\":" + first + "}"));
    }

    @Test
    void r8_aTalkIsOnTheProgrammeOnceAndMovesByBeingTakenOutFirst() throws Exception {
        String talk = acceptedTalk("PRESENTATION");
        String entry = schedule(200, "R1", "S1", talk);
        assertEquals(entry, schedule(200, "R1", "S1", talk));
        schedule(409, "R2", "S2", talk);
        expect(404, "GET", place("R2", "S2"), null);
        expect(204, "DELETE", place("R1", "S1"), null);
        schedule(200, "R2", "S2", talk);
    }

    @Test
    void r9_aMainSpeakerCantBeInTwoRoomsAtOnce() throws Exception {
        String speaker = newSpeaker();
        schedule(200, "R1", "S2", acceptedTalk(speaker, "PRESENTATION"));
        String second = acceptedTalk(speaker, "PRESENTATION");
        schedule(409, "R2", "S2", second);
        schedule(200, "R2", "S3", second);
    }

    @Test
    void r9_aCoSpeakerCantBeInTwoRoomsAtOnce() throws Exception {
        String shared = newSpeaker();
        String first = acceptedTalk("PRESENTATION");
        coSpeaker(first, shared);
        schedule(200, "R1", "S1", first);

        String asMain = acceptedTalk(shared, "PRESENTATION");
        schedule(409, "R2", "S1", asMain);

        String asCoSpeaker = acceptedTalk("PRESENTATION");
        coSpeaker(asCoSpeaker, shared);
        schedule(409, "R3", "S1", asCoSpeaker);
        schedule(200, "R3", "S2", asCoSpeaker);
    }

    @Test
    void r9_theMainSpeakerOfAScheduledTalkCantCoSpeakInTheSameSlot() throws Exception {
        String speaker = newSpeaker();
        schedule(200, "R1", "S5", acceptedTalk(speaker, "PRESENTATION"));
        String other = acceptedTalk("PRESENTATION");
        coSpeaker(other, speaker);
        schedule(409, "R2", "S5", other);
    }

    @Test
    void r9_differentPeopleShareASlot() throws Exception {
        schedule(200, "R1", "S1", acceptedTalk("PRESENTATION"));
        schedule(200, "R2", "S1", acceptedTalk("PRESENTATION"));
        schedule(200, "R3", "S1", acceptedTalk("PRESENTATION"));
    }

    @Test
    void r10_aTalkThatChangesAfterwardsStaysOnTheProgramme() throws Exception {
        String speaker = newSpeaker();
        String talk = acceptedTalk(speaker, "PRESENTATION");
        schedule(200, "R1", "S1", talk);
        String busy = newSpeaker();
        schedule(200, "R2", "S1", acceptedTalk(busy, "PRESENTATION"));
        coSpeaker(talk, busy);
        withdraw(talk, speaker);
        assertTrue(expect(200, "GET", place("R1", "S1"), null).contains("\"talkId\":" + talk + "}"));
        assertTrue(expect(200, "GET", place("R2", "S1"), null).contains("\"talkId\""));
        schedule(200, "R1", "S1", talk);
    }

    @Test
    void errors_aBodyWithoutAnIntegerTalkIdIsABadRequest() throws Exception {
        String talk = acceptedTalk("PRESENTATION");
        expect(400, "PUT", place("R1", "S1"), "{}");
        expect(400, "PUT", place("R1", "S1"), "{\"talkId\":null}");
        expect(400, "PUT", place("R1", "S1"), "{\"talkId\":\"" + talk + "\"}");
        expect(400, "PUT", place("R1", "S1"), "{\"talkId\":1.5}");
        expect(400, "PUT", place("R1", "S1"), "not json");
        expect(400, "PUT", place("R1", "S1"), null);
        expect(404, "GET", place("R1", "S1"), null);
    }

    @Test
    void errors_aRoomOrSlotThatDoesntExistIsNotFound() throws Exception {
        String talk = acceptedTalk("PRESENTATION");
        for (String[] place : new String[][] {{"R4", "S1"}, {"R1", "S6"}, {"r1", "S1"}, {"R1", "s1"}, {"R0", "S0"}}) {
            schedule(404, place[0], place[1], talk);
            expect(404, "GET", place(place[0], place[1]), null);
            expect(404, "DELETE", place(place[0], place[1]), null);
        }
    }

    @Test
    void errors_aRoomOrSlotThatDoesntExistIsNotFoundBeforeTheBodyIsChecked() throws Exception {
        String talk = acceptedTalk("PRESENTATION");
        expect(404, "PUT", place("R2", "slot2"), "{\"talkId\":\"" + talk + "\"}");
        expect(404, "PUT", place("R4", "S1"), "{}");
    }

    @Test
    void errors_aTalkThatDoesntExistIsABadRequest() throws Exception {
        schedule(400, "R1", "S1", "999999");
    }

    @Test
    void errors_anEmptyPlaceIsNotFound() throws Exception {
        expect(404, "GET", place("R3", "S5"), null);
        expect(404, "DELETE", place("R3", "S5"), null);
    }

    /** Several rounds, because a race only shows now and then. */
    @Test
    void r11_aRushNeverPutsTwoTalksInOnePlace() throws Exception {
        for (int round = 0; round < 3; round++) {
            List<String> json = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                json.add(talkId(acceptedTalk("PRESENTATION")));
            }
            assertEquals(1, rush(json, i -> place("R1", "S1")));
            emptyProgramme();
        }
    }

    @Test
    void r11_aRushNeverPutsOneTalkInTwoPlaces() throws Exception {
        for (int round = 0; round < 3; round++) {
            String talk = talkId(acceptedTalk("PRESENTATION"));
            List<String> json = new ArrayList<>();
            for (int i = 0; i < ROOMS.length * SLOTS.length; i++) {
                json.add(talk);
            }
            assertEquals(1, rush(json, i -> place(ROOMS[i % ROOMS.length], SLOTS[i / ROOMS.length])));
            emptyProgramme();
        }
    }

    @Test
    void r11_aRushNeverPutsOneSpeakerInTwoRooms() throws Exception {
        for (int round = 0; round < 5; round++) {
            String speaker = newSpeaker();
            List<String> sameMainSpeaker = List.of(talkId(acceptedTalk(speaker, "PRESENTATION")),
                    talkId(acceptedTalk(speaker, "PRESENTATION")));
            assertEquals(1, rush(sameMainSpeaker, i -> place(ROOMS[i], "S1")));

            String shared = newSpeaker();
            String coSpoken = acceptedTalk("PRESENTATION");
            coSpeaker(coSpoken, shared);
            List<String> mainAndCoSpeaker = List.of(talkId(acceptedTalk(shared, "PRESENTATION")), talkId(coSpoken));
            assertEquals(1, rush(mainAndCoSpeaker, i -> place(ROOMS[i], "S2")));
            emptyProgramme();
        }
    }

    /** Sends all the bodies at once, the i-th to {@code places.apply(i)}; returns how many were scheduled. */
    private int rush(List<String> json, IntFunction<String> places) {
        List<CompletableFuture<HttpResponse<String>>> sent = new ArrayList<>();
        for (int i = 0; i < json.size(); i++) {
            sent.add(http.sendAsync(request("PUT", places.apply(i), json.get(i)),
                    HttpResponse.BodyHandlers.ofString()));
        }
        int scheduled = 0;
        for (CompletableFuture<HttpResponse<String>> f : sent) {
            HttpResponse<String> res = f.join();
            assertTrue(res.statusCode() == 200 || res.statusCode() == 409, res.statusCode() + " " + res.body());
            if (res.statusCode() == 200) {
                scheduled++;
            }
        }
        return scheduled;
    }

    private static String place(String room, String slot) {
        return "/programme/" + room + "/" + slot;
    }

    private static String talkId(String talk) {
        return "{\"talkId\":" + talk + "}";
    }

    private String schedule(int status, String room, String slot, String talk) throws Exception {
        return expect(status, "PUT", place(room, slot), talkId(talk));
    }

    private String acceptedTalk(String format) throws Exception {
        return acceptedTalk(newSpeaker(), format);
    }

    private String acceptedTalk(String speaker, String format) throws Exception {
        String talk = ratedTalk(speaker, format);
        decide(talk, "ACCEPTED");
        return talk;
    }

    private String ratedTalk(String speaker, String format) throws Exception {
        String talk = newTalk(speaker, format);
        expect(200, "PUT", "/talks/" + talk + "/ratings/1", "{\"score\":4}");
        expect(200, "PUT", "/talks/" + talk + "/ratings/2", "{\"score\":5}");
        return talk;
    }

    private void decide(String talk, String status) throws Exception {
        expect(200, "PUT", "/talks/" + talk + "/status", "{\"status\":\"" + status + "\"}");
    }

    private void withdraw(String talk, String speaker) throws Exception {
        expect(200, "POST", "/talks/" + talk + "/withdraw", "{\"speakerId\":" + speaker + "}");
    }

    private void coSpeaker(String talk, String speaker) throws Exception {
        expect(200, "PUT", "/talks/" + talk + "/co-speakers/" + speaker, null);
    }

    private String newSpeaker() throws Exception {
        return id(expect(201, "POST", "/speakers",
                "{\"name\":\"Ada Lovelace\",\"email\":\"ada-" + UUID.randomUUID() + "@example.com\"}"));
    }

    private String newTalk(String speaker, String format) throws Exception {
        return id(expect(201, "POST", "/talks", "{\"title\":\"Engines\",\"format\":\"" + format
                + "\",\"level\":\"BEGINNER\",\"speakerId\":" + speaker + "}"));
    }

    private static String id(String body) {
        Matcher m = ID.matcher(body);
        assertTrue(m.find(), body);
        return m.group(1);
    }

    private String expect(int status, String method, String path, String json) throws Exception {
        HttpResponse<String> res = send(method, path, json);
        assertEquals(status, res.statusCode(), method + " " + path + " " + json + ": " + res.body());
        return res.body();
    }

    private HttpResponse<String> send(String method, String path, String json) throws Exception {
        return http.send(request(method, path, json), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest request(String method, String path, String json) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json))
                .build();
    }
}
