package no.scienta.cfp;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Applies the committee's decisions and the speakers' withdrawals to talks, following {@link TalkStatus}. Each change
 * locks the talk row first, so concurrent changes to one talk are applied one at a time.
 */
@Service
public class AcceptanceService {

    static final int REVIEWERS_NEEDED_TO_ACCEPT = 2;

    private final TalkDao talkDao;
    private final RatingDao ratingDao;
    private final ConferenceService conferenceService;

    public AcceptanceService(TalkDao talkDao, RatingDao ratingDao, ConferenceService conferenceService) {
        this.talkDao = talkDao;
        this.ratingDao = ratingDao;
        this.conferenceService = conferenceService;
    }

    @Transactional
    public TalkDto decide(long talkId, Map<String, Object> body) {
        TalkStatus decision = decisionFrom(body);
        TalkStatus current = TalkStatus.valueOf(lockedTalk(talkId).status);
        if (!current.committeeMayMoveTo(decision)) {
            throw conflict("a " + current + " talk can't be " + decision);
        }
        if (decision == TalkStatus.ACCEPTED && ratingDao.reviewerCount(talkId) < REVIEWERS_NEEDED_TO_ACCEPT) {
            throw conflict("a talk needs ratings from " + REVIEWERS_NEEDED_TO_ACCEPT + " reviewers to be accepted");
        }
        talkDao.updateStatus(talkId, decision.name());
        return conferenceService.getTalk(talkId);
    }

    @Transactional
    public TalkDto withdraw(long talkId, Map<String, Object> body) {
        long speakerId = speakerIdFrom(body);
        TalkDto talk = lockedTalk(talkId);
        if (talk.speakerId != speakerId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only the talk's speaker can withdraw it");
        }
        if (!TalkStatus.valueOf(talk.status).speakerMayWithdraw()) {
            throw conflict("a " + talk.status + " talk can't be withdrawn");
        }
        talkDao.updateStatus(talkId, TalkStatus.WITHDRAWN.name());
        return conferenceService.getTalk(talkId);
    }

    private TalkDto lockedTalk(long talkId) {
        TalkDto talk = talkDao.findByIdForUpdate(talkId);
        if (talk == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "talk not found");
        }
        return talk;
    }

    static TalkStatus decisionFrom(Map<String, Object> body) {
        Object status = body == null ? null : body.get("status");
        for (TalkStatus decision : TalkStatus.DECISIONS) {
            if (decision.name().equals(status)) {
                return decision;
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be one of " + TalkStatus.DECISIONS);
    }

    static long speakerIdFrom(Map<String, Object> body) {
        Object speakerId = body == null ? null : body.get("speakerId");
        if (speakerId instanceof Integer || speakerId instanceof Long) {
            return ((Number) speakerId).longValue();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "speakerId must be a whole number");
    }

    private static ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }
}
