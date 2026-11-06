package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The rules of {@code workflow/specs/programme-export.md}, against the real app on a random port with a throwaway
 * database. Test names cite the rule (R1–R11) they cover. The venue is shared, so every test starts with an empty
 * programme.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProgrammeExportTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");
    private static final String HEADER = "slot,start,end,room,talkId,title,format,speakers\n";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void emptyProgramme() throws Exception {
        for (Room room : Room.values()) {
            for (Slot slot : Slot.values()) {
                send("DELETE", "/programme/" + room + "/" + slot, null, null);
            }
        }
    }

    @Test
    void r10_anEmptyProgrammeIsAnEmptyListOrJustTheHeader() throws Exception {
        assertEquals("[]", get("/programme", "application/json").body());
        assertEquals(HEADER, get("/programme.csv", null).body());
    }

    @Test
    void r1_r2_r4_r5_r6_bothViewsListTheTalksInTheOrderOfTheDay() throws Exception {
        String grace = newSpeaker("Grace Hopper");
        String ada = newSpeaker("Ada Lovelace");
        String alan = newSpeaker("Alan Turing");
        String late = acceptedTalk(grace, "Late talk", "LIGHTNING_10");
        String workshop = acceptedTalk(ada, "Compilers", "WORKSHOP");
        String early = acceptedTalk(alan, "Early talk", "PRESENTATION");
        coSpeaker(workshop, grace);
        coSpeaker(workshop, alan);
        schedule("R1", "S5", late);
        schedule("R3", "S4", workshop);
        schedule("R2", "S1", early);

        assertEquals("[" + json("S1", "09:00", "10:00", "R2", early, "Early talk", "PRESENTATION", "\"Alan Turing\"")
                + "," + json("S4", "13:30", "15:30", "R3", workshop, "Compilers", "WORKSHOP",
                        "\"Ada Lovelace\",\"Grace Hopper\",\"Alan Turing\"")
                + "," + json("S5", "15:45", "16:45", "R1", late, "Late talk", "LIGHTNING_10", "\"Grace Hopper\"")
                + "]", get("/programme", null).body());
        assertEquals(HEADER
                + "S1,09:00,10:00,R2," + early + ",Early talk,PRESENTATION,Alan Turing\n"
                + "S4,13:30,15:30,R3," + workshop + ",Compilers,WORKSHOP,Ada Lovelace; Grace Hopper; Alan Turing\n"
                + "S5,15:45,16:45,R1," + late + ",Late talk,LIGHTNING_10,Grace Hopper\n",
                get("/programme.csv", null).body());
    }

    @Test
    void r4_withinASlotByRoom() throws Exception {
        String r3 = acceptedTalk(newSpeaker("Three"), "Third", "PRESENTATION");
        String r1 = acceptedTalk(newSpeaker("One"), "First", "PRESENTATION");
        String r2 = acceptedTalk(newSpeaker("Two"), "Second", "PRESENTATION");
        schedule("R3", "S2", r3);
        schedule("R1", "S2", r1);
        schedule("R2", "S2", r2);

        String csv = get("/programme.csv", null).body();
        assertEquals(HEADER + "S2,10:15,11:15,R1," + r1 + ",First,PRESENTATION,One\n"
                + "S2,10:15,11:15,R2," + r2 + ",Second,PRESENTATION,Two\n"
                + "S2,10:15,11:15,R3," + r3 + ",Third,PRESENTATION,Three\n", csv);
        String json = get("/programme", null).body();
        assertTrue(json.indexOf("\"talkId\":" + r1) < json.indexOf("\"talkId\":" + r2)
                && json.indexOf("\"talkId\":" + r2) < json.indexOf("\"talkId\":" + r3), json);
    }

    @Test
    void r3_r5_aTalkShowsAsItIsNowAndStaysAfterBeingWithdrawn() throws Exception {
        String main = newSpeaker("Before Rename");
        String talk = acceptedTalk(main, "Engines", "PRESENTATION");
        schedule("R1", "S1", talk);
        expect(200, "PUT", "/speakers/" + main,
                "{\"name\":\"After Rename\",\"email\":\"renamed-" + UUID.randomUUID() + "@example.com\"}");
        coSpeaker(talk, newSpeaker("Late Joiner"));
        expect(200, "POST", "/talks/" + talk + "/withdraw", "{\"speakerId\":" + main + "}");

        assertEquals(HEADER + "S1,09:00,10:00,R1," + talk + ",Engines,PRESENTATION,After Rename; Late Joiner\n",
                get("/programme.csv", null).body());
        assertEquals("[" + json("S1", "09:00", "10:00", "R1", talk, "Engines", "PRESENTATION",
                "\"After Rename\",\"Late Joiner\"") + "]", get("/programme", null).body());
    }

    @Test
    void r3_aTalkTakenOffTheProgrammeIsGoneFromTheExport() throws Exception {
        String talk = acceptedTalk(newSpeaker("Ada Lovelace"), "Engines", "PRESENTATION");
        schedule("R1", "S1", talk);
        expect(204, "DELETE", "/programme/R1/S1", null);
        assertEquals("[]", get("/programme", null).body());
        assertEquals(HEADER, get("/programme.csv", null).body());
    }

    @Test
    void r7_theCsvIsUtf8TextCsvWhateverTheClientAccepts() throws Exception {
        String talk = acceptedTalk(newSpeaker("Åse Ødegård"), "Smørbrød på norsk", "PRESENTATION");
        schedule("R1", "S1", talk);
        for (String accept : new String[] {null, "application/json", "text/csv", "*/*", "text/html"}) {
            HttpResponse<byte[]> res = http.send(request("GET", "/programme.csv", null, accept),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, res.statusCode(), "Accept: " + accept);
            assertEquals("text/csv;charset=UTF-8", res.headers().firstValue("Content-Type").orElse(""),
                    "Accept: " + accept);
            assertEquals(HEADER + "S1,09:00,10:00,R1," + talk + ",Smørbrød på norsk,PRESENTATION,Åse Ødegård\n",
                    new String(res.body(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void r9_fieldsAreQuotedOnlyWhenTheyHaveTo() throws Exception {
        String comma = acceptedTalk(newSpeaker("Hopper, Grace"), "Kotlin, \"really\" fast", "PRESENTATION");
        String breaks = acceptedTalk(newSpeaker("Ada; Lovelace"), "Two\nlines", "LIGHTNING_20");
        schedule("R1", "S1", comma);
        schedule("R1", "S2", breaks);

        assertEquals(HEADER
                + "S1,09:00,10:00,R1," + comma + ",\"Kotlin, \"\"really\"\" fast\",PRESENTATION,\"Hopper, Grace\"\n"
                + "S2,10:15,11:15,R1," + breaks + ",\"Two\nlines\",LIGHTNING_20,Ada; Lovelace\n",
                get("/programme.csv", null).body());
        assertTrue(get("/programme", null).body().contains("\"title\":\"Kotlin, \\\"really\\\" fast\""));
    }

    @Test
    void errors_aTrailingSlashIsServedAsWithoutOne() throws Exception {
        assertEquals("[]", get("/programme/", null).body());
    }

    private static String json(String slot, String start, String end, String room, String talk, String title,
                               String format, String speakers) {
        return "{\"slot\":\"" + slot + "\",\"start\":\"" + start + "\",\"end\":\"" + end + "\",\"room\":\"" + room
                + "\",\"talkId\":" + talk + ",\"title\":\"" + title + "\",\"format\":\"" + format
                + "\",\"speakers\":[" + speakers + "]}";
    }

    private void schedule(String room, String slot, String talk) throws Exception {
        expect(200, "PUT", "/programme/" + room + "/" + slot, "{\"talkId\":" + talk + "}");
    }

    private String acceptedTalk(String speaker, String title, String format) throws Exception {
        String talk = id(expect(201, "POST", "/talks", "{\"title\":" + jsonString(title) + ",\"format\":\"" + format
                + "\",\"level\":\"BEGINNER\",\"speakerId\":" + speaker + "}"));
        expect(200, "PUT", "/talks/" + talk + "/ratings/1", "{\"score\":4}");
        expect(200, "PUT", "/talks/" + talk + "/ratings/2", "{\"score\":5}");
        expect(200, "PUT", "/talks/" + talk + "/status", "{\"status\":\"ACCEPTED\"}");
        return talk;
    }

    private void coSpeaker(String talk, String speaker) throws Exception {
        expect(200, "PUT", "/talks/" + talk + "/co-speakers/" + speaker, null);
    }

    private String newSpeaker(String name) throws Exception {
        return id(expect(201, "POST", "/speakers",
                "{\"name\":" + jsonString(name) + ",\"email\":\"s-" + UUID.randomUUID() + "@example.com\"}"));
    }

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private static String id(String body) {
        Matcher m = ID.matcher(body);
        assertTrue(m.find(), body);
        return m.group(1);
    }

    private HttpResponse<String> get(String path, String accept) throws Exception {
        HttpResponse<String> res = send("GET", path, null, accept);
        assertEquals(200, res.statusCode(), "GET " + path + ": " + res.body());
        return res;
    }

    private String expect(int status, String method, String path, String json) throws Exception {
        HttpResponse<String> res = send(method, path, json, null);
        assertEquals(status, res.statusCode(), method + " " + path + " " + json + ": " + res.body());
        return res.body();
    }

    private HttpResponse<String> send(String method, String path, String json, String accept) throws Exception {
        return http.send(request(method, path, json, accept),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpRequest request(String method, String path, String json, String accept) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json));
        if (accept != null) {
            builder.header("Accept", accept);
        }
        return builder.build();
    }
}
