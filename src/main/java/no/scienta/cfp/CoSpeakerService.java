package no.scienta.cfp;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Adds and removes a talk's co-speakers, enforcing that a talk has at most two, that the main speaker isn't one of
 * them and that a co-speaker stays within the limit of talks per speaker. See {@code workflow/specs/co-speakers.md} and
 * {@code workflow/specs/submission-cap-cr.md}.
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
     * Adds the speaker as the talk's last co-speaker. The talk row and then the speaker row stay locked until commit,
     * so concurrent additions to one talk can't both see room on it, and concurrent additions and submissions for one
     * speaker can't both see room under their limit of talks.
     */
    @Transactional
    public TalkDto add(long talkId, long speakerId) {
        TalkDto talk = talkDao.findByIdForUpdate(talkId);
        if (talk == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "talk not found");
        }
        if (speakerDao.lockForUpdate(speakerId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "speaker not found");
        }
        List<Long> current = coSpeakerDao.findSpeakerIds(talkId);
        boolean inTheRunning = TalkStatus.valueOf(talk.status).isInTheRunning();
        switch (additionOutcome(talk.speakerId, current, speakerId, inTheRunning, speakerDao.load(speakerId))) {
            case MAIN_SPEAKER:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "the main speaker can't be a co-speaker of their own talk");
            case FULL:
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "a talk has at most " + MAX_CO_SPEAKERS + " co-speakers");
            case SPEAKER_AT_LIMIT:
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "a speaker presents at most " + ConferenceService.MAX_TALKS_PER_SPEAKER + " talks");
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

    enum AdditionOutcome { ADD, ALREADY_CO_SPEAKER, MAIN_SPEAKER, FULL, SPEAKER_AT_LIMIT }

    /**
     * Decides what adding {@code candidate}, whose load is {@code candidateLoad} talks, to a talk with the given main
     * speaker and co-speakers should do. A talk out of the running doesn't add to the load, so it ignores the limit.
     */
    static AdditionOutcome additionOutcome(long mainSpeakerId, List<Long> coSpeakerIds, long candidate,
                                           boolean talkInTheRunning, int candidateLoad) {
        if (candidate == mainSpeakerId) {
            return AdditionOutcome.MAIN_SPEAKER;
        }
        if (coSpeakerIds.contains(candidate)) {
            return AdditionOutcome.ALREADY_CO_SPEAKER;
        }
        if (coSpeakerIds.size() >= MAX_CO_SPEAKERS) {
            return AdditionOutcome.FULL;
        }
        if (talkInTheRunning && candidateLoad >= ConferenceService.MAX_TALKS_PER_SPEAKER) {
            return AdditionOutcome.SPEAKER_AT_LIMIT;
        }
        return AdditionOutcome.ADD;
    }
}
