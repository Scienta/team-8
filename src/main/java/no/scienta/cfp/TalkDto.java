package no.scienta.cfp;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A talk as the API returns it. Also used internally all over the place.
 */
public class TalkDto {

    public Long id;
    public String title;
    public String format;
    public String level;
    public Long speakerId;
    public Integer durationMinutes;
    public String status;
    /** When it was submitted, as the legacy reporting tools read it; see {@link Utils#reportingTimestamp}. */
    public String submittedAt;
    /** The UTC day of {@link #submittedAt}; see {@link Utils#reportingDate}. */
    public String submittedOn;
    public Double averageRating;
    public int ratingCount;
    public List<Long> coSpeakerIds = new ArrayList<>();
    /** When it was submitted, for our own exports; not part of the API. */
    @JsonIgnore
    public Date submittedTime;
}
