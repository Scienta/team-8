package no.scienta.cfp;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Adds and removes a talk's co-speakers, enforcing that a talk has at most two and that the main speaker isn't one of
 * them. See {@code workflow/specs/co-speakers.md}.
 */
@Service
public class CoSpeakerService {

    static final int MAX_CO_SPEAKERS = 2;

    private final TalkDao talkDao;
    private final SpeakerDao speakerDao;
    private final CoSpeakerDao coSpeakerDao;
    private final ConferenceService conferenceService;

    public CoSpeakerService(TalkDao talkDao, SpeakerDao speakerDao, CoSpeakerDao coSpeakerDao,
                            ConferenceService conferenceService) {
        this.talkDao = talkDao;
        this.speakerDao = speakerDao;
        this.coSpeakerDao = coSpeakerDao;
        this.conferenceService = conferenceService;
    }

    /**
     * Adds the speaker as the talk's last co-speaker. The talk row stays locked until commit, so concurrent additions
     * to one talk can't both see room under the limit.
     */
    @Transactional
    public TalkDto add(long talkId, long speakerId) {
        TalkDto talk = talkDao.findByIdForUpdate(talkId);
        if (talk == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "talk not found");
        }
        requireSpeaker(speakerId);
        List<Long> current = coSpeakerDao.findSpeakerIds(talkId);
        switch (additionOutcome(talk.speakerId, current, speakerId)) {
            case MAIN_SPEAKER:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "the main speaker can't be a co-speaker of their own talk");
            case FULL:
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "a talk has at most " + MAX_CO_SPEAKERS + " co-speakers");
            case ADD:
                coSpeakerDao.add(talkId, speakerId);
                break;
            default:
                break;
        }
        return conferenceService.getTalk(talkId);
    }

    /** Removes the speaker from the talk's co-speakers; 404 if they aren't one. */
    @Transactional
    public TalkDto remove(long talkId, long speakerId) {
        if (!talkDao.exists(talkId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "talk not found");
        }
        requireSpeaker(speakerId);
        if (!coSpeakerDao.remove(talkId, speakerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "speaker is not a co-speaker of this talk");
        }
        return conferenceService.getTalk(talkId);
    }

    private void requireSpeaker(long speakerId) {
        if (!speakerDao.exists(speakerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "speaker not found");
        }
    }

    enum AdditionOutcome { ADD, ALREADY_CO_SPEAKER, MAIN_SPEAKER, FULL }

    /** Decides what adding {@code candidate} to a talk with the given main speaker and co-speakers should do. */
    static AdditionOutcome additionOutcome(long mainSpeakerId, List<Long> coSpeakerIds, long candidate) {
        if (candidate == mainSpeakerId) {
            return AdditionOutcome.MAIN_SPEAKER;
        }
        if (coSpeakerIds.contains(candidate)) {
            return AdditionOutcome.ALREADY_CO_SPEAKER;
        }
        if (coSpeakerIds.size() >= MAX_CO_SPEAKERS) {
            return AdditionOutcome.FULL;
        }
        return AdditionOutcome.ADD;
    }
}
