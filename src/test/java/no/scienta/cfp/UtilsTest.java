package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class UtilsTest {

    @Test
    void isEmpty() {
        assertTrue(Utils.isEmpty(null));
        assertTrue(Utils.isEmpty(""));
        assertTrue(Utils.isEmpty("   "));
        assertFalse(Utils.isEmpty("a"));
    }

    @Test
    void isNumeric() {
        assertTrue(Utils.isNumeric("123"));
        assertFalse(Utils.isNumeric("12a"));
        assertFalse(Utils.isNumeric(""));
    }

    @Test
    void capitalize() {
        assertEquals("Workshop", Utils.capitalize("WORKSHOP"));
    }

    // Averages are shown with one decimal, cut off (CFP-61)
    @Test
    void roundsRatingToOneDecimal() {
        assertEquals(4.2, Utils.roundRating(4.25));
        assertEquals(3.6, Utils.roundRating(3.6666));
        assertEquals(4.5, Utils.roundRating(4.5));
    }

    @Test
    void usShortIsTheReviewerUiFormatWithAnOrdinarySpaceOnAnyJava() throws Exception {
        SimpleDateFormat utc = new SimpleDateFormat(Utils.DB_DATE_TIME);
        utc.setTimeZone(TimeZone.getTimeZone("UTC"));
        assertEquals("11/4/26, 1:05 PM", Utils.usShort(utc.parse("2026-11-04 13:05:59")));
        assertEquals("11/4/26, 12:00 AM", Utils.usShort(utc.parse("2026-11-04 00:00:00")));
        assertEquals("1/15/27, 11:59 AM", Utils.usShort(utc.parse("2027-01-15 11:59:00")));
    }

    @Test
    void reportingFormatsAreUtcWithMillisecondsAndAWrittenOutOffset() {
        long millis = 1793795405123L;  // 2026-11-04T12:30:05.123Z
        assertEquals("2026-11-04T12:30:05.123+00:00", Utils.reportingTimestamp(millis));
        assertEquals("2026-11-04", Utils.reportingDate(millis));
        assertEquals("1970-01-01T00:00:00.000+00:00", Utils.reportingTimestamp(0));
        assertEquals("2026-11-04", Utils.reportingDate(1793836799999L));  // the day's last millisecond, 23:59:59.999Z
    }

    @Disabled("flaky on Jenkins")
    @Test
    void nowIsCurrentTime() {
        SimpleDateFormat f = new SimpleDateFormat(Utils.DB_DATE_TIME);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        assertEquals(f.format(new Date()), Utils.now());
    }
}
