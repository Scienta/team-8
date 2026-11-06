package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
 * Ratings ({@code ratings} base spec): one score per reviewer and talk, the average and count on every talk, and the
 * committee's ranking. Runs the real app on a random port with a throwaway database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RatingsTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");
    private static final Pattern RATED_AT_DISPLAY = Pattern.compile("\"ratedAtDisplay\":\"([^\"]*)\"");

    /** US short: no leading zeros on month, day and hour, two-digit year, 12-hour clock, one ordinary space. */
    private static final String US_SHORT = "(1[0-2]|[1-9])/([1-9]|[12]\\d|3[01])/\\d{2}, (1[0-2]|[1-9]):[0-5]\\d (AM|PM)";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    // ---------------------------------------------------------------- PUT /talks/{id}/ratings/{reviewerId}

    @Test
    void aRatingIsEchoedWithTheLegacyDisplayTime() throws Exception {
        String talk = newTalk(newSpeaker());
        String rating = expect(200, "PUT", "/talks/" + talk + "/ratings/7", "{\"score\":4}");
        assertTrue(rating.contains("\"talkId\":" + talk), rating);
        assertTrue(rating.contains("\"reviewerId\":7"), rating);
        assertTrue(rating.contains("\"score\":4"), rating);
        String display = group(RATED_AT_DISPLAY, rating);
        assertTrue(display.matches(US_SHORT), "[" + display + "]");
    }

    @Test
    void everyScoreFromOneToFiveIsAccepted() throws Exception {
        String talk = newTalk(newSpeaker());
        for (int score = 1; score <= 5; score++) {
            expect(200, "PUT", "/talks/" + talk + "/ratings/" + score, "{\"score\":" + score + "}");
        }
        String found = expect(200, "GET", "/talks/" + talk, null);
        assertTrue(found.contains("\"ratingCount\":5"), found);
        assertTrue(found.contains("\"averageRating\":3.0"), found);
    }

    @Test
    void ratingAgainReplacesTheOldScore() throws Exception {
        String talk = newTalk(newSpeaker());
        rate(talk, 1, 2);
        rate(talk, 1, 5);
        String found = expect(200, "GET", "/talks/" + talk, null);
        assertTrue(found.contains("\"ratingCount\":1"), found);
        assertTrue(found.contains("\"averageRating\":5.0"), found);
    }

    @Test
    void theAverageIsRoundedToOneDecimalWithHalvesUp() throws Exception {
        String speaker = newSpeaker();
        String talk = newTalk(speaker);
        rate(talk, 1, 4);
        rate(talk, 2, 4);
        rate(talk, 3, 4);
        rate(talk, 4, 5);
        String found = expect(200, "GET", "/talks/" + talk, null);
        assertTrue(found.contains("\"ratingCount\":4"), found);
        assertTrue(found.contains("\"averageRating\":4.3"), found);
        assertTrue(expect(200, "GET", "/speakers/" + speaker + "/talks", null).contains("\"averageRating\":4.3"));
        assertTrue(expect(200, "GET", "/talks?speakerId=" + speaker, null).contains("\"averageRating\":4.3"));
    }

    @Test
    void aScoreOutsideOneToFiveOrNotAWholeNumberIsABadRequest() throws Exception {
        String talk = newTalk(newSpeaker());
        for (String body : Arrays.asList(
                "{\"score\":0}", "{\"score\":6}", "{\"score\":-1}", "{\"score\":3.5}", "{\"score\":3.0}",
                "{\"score\":\"3\"}", "{\"score\":null}", "{\"score\":true}", "{\"score\":99999999999}", "{}")) {
            expect(400, "PUT", "/talks/" + talk + "/ratings/1", body);
        }
        String found = expect(200, "GET", "/talks/" + talk, null);
        assertTrue(found.contains("\"ratingCount\":0"), found);
        assertTrue(found.contains("\"averageRating\":null"), found);
    }

    @Test
    void aReviewerIsAPositiveWholeNumber() throws Exception {
        String talk = newTalk(newSpeaker());
        for (String reviewer : Arrays.asList("0", "-1", "abc", "1.5")) {
            expect(400, "PUT", "/talks/" + talk + "/ratings/" + reviewer, "{\"score\":3}");
        }
        assertTrue(expect(200, "GET", "/talks/" + talk, null).contains("\"ratingCount\":0"));
    }

    @Test
    void aBodyThatIsNotJsonIsABadRequest() throws Exception {
        String talk = newTalk(newSpeaker());
        expect(400, "PUT", "/talks/" + talk + "/ratings/1", "{\"score\":");
        expect(400, "PUT", "/talks/" + talk + "/ratings/1", null);
    }

    @Test
    void ratingAnUnknownTalkIsNotFound() throws Exception {
        expect(404, "PUT", "/talks/2147483000/ratings/1", "{\"score\":3}");
        expect(400, "PUT", "/talks/abc/ratings/1", "{\"score\":3}");
    }

    @Test
    void concurrentRatingsAreAllCountedAndADoubleClickCountsOnce() throws Exception {
        String talk = newTalk(newSpeaker());
        List<CompletableFuture<HttpResponse<String>>> sent = new ArrayList<>();
        for (int reviewer = 1; reviewer <= 20; reviewer++) {
            for (int click = 0; click < 2; click++) {
                sent.add(http.sendAsync(request("PUT", "/talks/" + talk + "/ratings/" + reviewer, "{\"score\":5}"),
                        HttpResponse.BodyHandlers.ofString()));
            }
        }
        for (CompletableFuture<HttpResponse<String>> f : sent) {
            HttpResponse<String> res = f.join();
            assertEquals(200, res.statusCode(), res.body());
        }
        String found = expect(200, "GET", "/talks/" + talk, null);
        assertTrue(found.contains("\"ratingCount\":20"), found);
        assertTrue(found.contains("\"averageRating\":5.0"), found);
    }

    // ---------------------------------------------------------------- GET /talks?sort=rating

    @Test
    void theRankingIsByAverageThenByCountThenOldestWithUnratedLast() throws Exception {
        String speaker = newSpeaker();
        String unrated = newTalk(speaker);
        String fewer = newTalk(speaker);
        String more = newTalk(speaker);
        String other = newSpeaker();
        String best = newTalk(other);
        String tiedOlder = newTalk(other);
        String tiedNewer = newTalk(other);

        rate(fewer, 1, 5);
        rate(fewer, 2, 4);
        rate(more, 1, 5);
        rate(more, 2, 4);
        rate(more, 3, 5);
        rate(more, 4, 4);
        rate(best, 1, 5);
        rate(tiedNewer, 1, 3);
        rate(tiedOlder, 1, 3);

        assertEquals(Arrays.asList(more, fewer, unrated), ids(expect(200, "GET", "/talks?sort=rating&speakerId=" + speaker, null)));
        assertEquals(Arrays.asList(best, tiedOlder, tiedNewer), ids(expect(200, "GET", "/talks?sort=rating&speakerId=" + other, null)));

        List<String> all = ids(expect(200, "GET", "/talks?sort=rating", null));
        assertTrue(all.indexOf(best) < all.indexOf(more), all.toString());
        assertTrue(all.indexOf(more) < all.indexOf(fewer), all.toString());
        assertTrue(all.indexOf(fewer) < all.indexOf(tiedOlder), all.toString());
        assertTrue(all.indexOf(tiedNewer) < all.indexOf(unrated), all.toString());
    }

    @Test
    void theRankingUsesTheRoundedAverage() throws Exception {
        String speaker = newSpeaker();
        String older = newTalk(speaker);
        String newer = newTalk(speaker);
        // older: 4, 4, 5 = 4.333..., shown as 4.3; newer: 4, 4, 4, 5 = 4.25, shown as 4.3 with one more reviewer
        rate(older, 1, 4);
        rate(older, 2, 4);
        rate(older, 3, 5);
        rate(newer, 1, 4);
        rate(newer, 2, 4);
        rate(newer, 3, 4);
        rate(newer, 4, 5);
        assertEquals(Arrays.asList(newer, older), ids(expect(200, "GET", "/talks?sort=rating&speakerId=" + speaker, null)));
    }

    // ---------------------------------------------------------------- helpers

    private String newSpeaker() throws Exception {
        return id(expect(201, "POST", "/speakers",
                "{\"name\":\"Ada Lovelace\",\"email\":\"ada-" + UUID.randomUUID() + "@example.com\"}"));
    }

    private String newTalk(String speaker) throws Exception {
        return id(expect(201, "POST", "/talks",
                "{\"title\":\"Engines\",\"format\":\"PRESENTATION\",\"level\":\"BEGINNER\",\"speakerId\":" + speaker + "}"));
    }

    private void rate(String talk, int reviewer, int score) throws Exception {
        expect(200, "PUT", "/talks/" + talk + "/ratings/" + reviewer, "{\"score\":" + score + "}");
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

    private static List<String> ids(String body) {
        List<String> ids = new ArrayList<>();
        Matcher m = ID.matcher(body);
        while (m.find()) {
            ids.add(m.group(1));
        }
        return ids;
    }

    private HttpRequest request(String method, String path, String json) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json))
                .build();
    }
}
