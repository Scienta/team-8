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
 * The rules and errors of {@code workflow/specs/workshop-registration.md}, against the real app on a random port with
 * a throwaway database. Test names cite the rule (R1–R11) they cover.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkshopRegistrationsTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");
    private static final Pattern EMAIL = Pattern.compile("\"email\":\"([^\"]*)\"");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void r1_anAttendeeRegistersForAnAcceptedWorkshop() throws Exception {
        String workshop = acceptedWorkshop();
        String body = register(201, workshop, "Grace Hopper", "Grace@Example.com");
        String id = id(body);
        assertEquals("{\"id\":" + id + ",\"workshopId\":" + workshop
                + ",\"name\":\"Grace Hopper\",\"email\":\"Grace@Example.com\"}", body);
    }

    @Test
    void r2_nameAndEmailAreRequired() throws Exception {
        String workshop = acceptedWorkshop();
        expect(400, "POST", registrations(workshop), "{\"email\":\"ada@example.com\"}");
        expect(400, "POST", registrations(workshop), "{\"name\":\"Ada\"}");
        expect(400, "POST", registrations(workshop), "{\"name\":\"  \",\"email\":\"ada@example.com\"}");
        expect(400, "POST", registrations(workshop), "{\"name\":\"Ada\",\"email\":\"\"}");
        expect(400, "POST", registrations(workshop), "{\"name\":42,\"email\":\"ada@example.com\"}");
        expect(400, "POST", registrations(workshop), "{\"name\":\"Ada\",\"email\":null}");
        expect(400, "POST", registrations(workshop), "{}");
        expect(400, "POST", registrations(workshop), "not json");
        expect(400, "POST", registrations(workshop), null);
        assertEquals("[]", expect(200, "GET", registrations(workshop), null));
    }

    @Test
    void r3_anEmailHasToLookLikeOne() throws Exception {
        expect(400, "POST", registrations(acceptedWorkshop()), "{\"name\":\"Ada\",\"email\":\"ada\"}");
    }

    @Test
    void r4_aTalkThatIsntAWorkshopIsAConflictAndAMissingOneNotFound() throws Exception {
        String talk = acceptedTalk("PRESENTATION");
        register(409, talk, "Ada", "ada@example.com");
        expect(404, "GET", registrations(talk), null);
        register(404, "999999", "Ada", "ada@example.com");
        expect(404, "GET", registrations("999999"), null);
        expect(400, "GET", registrations("abc"), null);
        register(400, "abc", "Ada", "ada@example.com");
    }

    @Test
    void r5_onlyAnAcceptedWorkshopTakesRegistrations() throws Exception {
        String submitted = workshop();
        register(409, submitted, "Ada", "ada@example.com");
        assertEquals("[]", expect(200, "GET", registrations(submitted), null));

        String waitlisted = workshop();
        decide(waitlisted, "WAITLISTED");
        register(409, waitlisted, "Ada", "ada@example.com");

        String rejected = workshop();
        decide(rejected, "REJECTED");
        register(409, rejected, "Ada", "ada@example.com");
    }

    @Test
    void r6_aWithdrawnWorkshopKeepsItsListButTakesNoMore() throws Exception {
        String speaker = newSpeaker();
        String workshop = acceptedTalk(speaker, "WORKSHOP");
        register(201, workshop, "Ada", "ada@example.com");
        expect(200, "POST", "/talks/" + workshop + "/withdraw", "{\"speakerId\":" + speaker + "}");
        register(409, workshop, "Grace", "grace@example.com");
        assertEquals(List.of("ada@example.com"), emails(expect(200, "GET", registrations(workshop), null)));
    }

    @Test
    void r7_aWorkshopTakesTwentyAndNoMore() throws Exception {
        String workshop = acceptedWorkshop();
        for (int i = 0; i < 20; i++) {
            register(201, workshop, "Attendee " + i, "a" + i + "@example.com");
        }
        register(409, workshop, "One Too Many", "late@example.com");
        assertEquals(20, emails(expect(200, "GET", registrations(workshop), null)).size());
    }

    @Test
    void r8_oneRegistrationPerEmailWhateverTheCase() throws Exception {
        String workshop = acceptedWorkshop();
        register(201, workshop, "Ada", "ada@example.com");
        register(409, workshop, "Ada again", "ADA@Example.com");
        register(409, workshop, "Ada with spaces", " ada@example.com ");
        assertEquals(List.of("ada@example.com"), emails(expect(200, "GET", registrations(workshop), null)));
    }

    @Test
    void r8_aTakenEmailIsAConflictEvenWhenTheWorkshopIsFull() throws Exception {
        String workshop = acceptedWorkshop();
        for (int i = 0; i < 20; i++) {
            register(201, workshop, "Attendee " + i, "a" + i + "@example.com");
        }
        register(409, workshop, "Attendee 0", "A0@example.com");
    }

    @Test
    void r9_theSameEmailCanRegisterForSeveralWorkshops() throws Exception {
        register(201, acceptedWorkshop(), "Ada", "ada@example.com");
        register(201, acceptedWorkshop(), "Ada", "ADA@example.com");
    }

    @Test
    void r10_theListIsInTheOrderPeopleRegisteredWithEmailsAsTyped() throws Exception {
        String workshop = acceptedWorkshop();
        assertEquals("[]", expect(200, "GET", registrations(workshop), null));
        register(201, workshop, "Zed", "Zed@Example.com");
        register(201, workshop, "Ada", "ada@example.com");
        register(201, workshop, "Max", "MAX@example.com");
        assertEquals(List.of("Zed@Example.com", "ada@example.com", "MAX@example.com"),
                emails(expect(200, "GET", registrations(workshop), null)));
    }

    @Test
    void r10_aWorkshopNotYetAcceptedHasAnEmptyList() throws Exception {
        assertEquals("[]", expect(200, "GET", registrations(workshop()), null));
    }

    /** Five rushes, because a single one without the row lock only goes over twenty now and then. */
    @Test
    void r11_aRushNeverTakesMoreThanTwentyOrAnEmailTwice() throws Exception {
        for (int round = 0; round < 5; round++) {
            rush(acceptedWorkshop());
        }
    }

    /** 60 registrations at once: 50 people, 10 of whom register twice with their email in other letter case. */
    private void rush(String workshop) {
        List<CompletableFuture<HttpResponse<String>>> sent = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            String email = i < 50 ? "rush" + i + "@example.com" : "RUSH" + (i - 50) + "@Example.com";
            String json = "{\"name\":\"Rusher " + i + "\",\"email\":\"" + email + "\"}";
            sent.add(http.sendAsync(request("POST", registrations(workshop), json),
                    HttpResponse.BodyHandlers.ofString()));
        }
        int registered = 0;
        for (CompletableFuture<HttpResponse<String>> f : sent) {
            HttpResponse<String> res = f.join();
            assertTrue(res.statusCode() == 201 || res.statusCode() == 409, res.statusCode() + " " + res.body());
            if (res.statusCode() == 201) {
                registered++;
            }
        }
        assertEquals(20, registered);
        List<String> emails = emails(expectQuietly(200, "GET", registrations(workshop)));
        assertEquals(20, emails.size());
        assertEquals(20, emails.stream().map(String::toLowerCase).distinct().count());
    }

    private String expectQuietly(int status, String method, String path) {
        try {
            return expect(status, method, path, null);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String registrations(String workshop) {
        return "/workshops/" + workshop + "/registrations";
    }

    private String register(int status, String workshop, String name, String email) throws Exception {
        return expect(status, "POST", registrations(workshop),
                "{\"name\":\"" + name + "\",\"email\":\"" + email + "\"}");
    }

    private String acceptedWorkshop() throws Exception {
        return acceptedTalk("WORKSHOP");
    }

    private String acceptedTalk(String format) throws Exception {
        return acceptedTalk(newSpeaker(), format);
    }

    private String acceptedTalk(String speaker, String format) throws Exception {
        String talk = newTalk(speaker, format);
        expect(200, "PUT", "/talks/" + talk + "/ratings/1", "{\"score\":4}");
        expect(200, "PUT", "/talks/" + talk + "/ratings/2", "{\"score\":5}");
        decide(talk, "ACCEPTED");
        return talk;
    }

    private String workshop() throws Exception {
        return newTalk(newSpeaker(), "WORKSHOP");
    }

    private void decide(String talk, String status) throws Exception {
        expect(200, "PUT", "/talks/" + talk + "/status", "{\"status\":\"" + status + "\"}");
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

    private static List<String> emails(String body) {
        List<String> emails = new ArrayList<>();
        Matcher m = EMAIL.matcher(body);
        while (m.find()) {
            emails.add(m.group(1));
        }
        return emails;
    }

    private String expect(int status, String method, String path, String json) throws Exception {
        HttpResponse<String> res = http.send(request(method, path, json), HttpResponse.BodyHandlers.ofString());
        assertEquals(status, res.statusCode(), method + " " + path + " " + json + ": " + res.body());
        return res.body();
    }

    private HttpRequest request(String method, String path, String json) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json))
                .build();
    }
}
