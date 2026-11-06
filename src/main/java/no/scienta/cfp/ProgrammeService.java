package no.scienta.cfp;

import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Puts accepted talks into rooms and slots and takes them out again, enforcing that every talk fits its slot, each
 * room and slot holds one talk, each talk is on the programme once and nobody speaks in two rooms at the same time.
 * See {@code workflow/specs/scheduling.md}.
 */
@Service
public class ProgrammeService {

    private final TalkDao talkDao;
    private final ProgrammeDao programmeDao;

    public ProgrammeService(TalkDao talkDao, ProgrammeDao programmeDao) {
        this.talkDao = talkDao;
        this.programmeDao = programmeDao;
    }

    /**
     * Puts the talk in the room and slot. The talk's row and then the slot's row stay locked until commit, always in
     * that order, so concurrent scheduling can't put two talks in one place, one talk in two places, or one speaker
     * in two rooms at once.
     */
    @Transactional
    public ProgrammeEntry schedule(String roomName, String slotName, Map<String, Object> body) {
        long talkId = requiredTalkId(body);
        Room room = room(roomName);
        Slot slot = slot(slotName);
        TalkDto talk = talkDao.findByIdForUpdate(talkId);
        if (talk == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "talk " + talkId + " does not exist");
        }
        programmeDao.lockSlot(slot);
        switch (schedulingOutcome(slot, talk.format, talk.status, programmeDao.isScheduled(talkId),
                programmeDao.findTalkId(room, slot) != null, programmeDao.hasSpeakerInSlot(talkId, slot))) {
            case DOES_NOT_FIT:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "a workshop needs the long slot, " + Slot.LONG);
            case NOT_ACCEPTED:
                throw conflict("only accepted talks go on the programme, this one is " + talk.status);
            case ALREADY_SCHEDULED:
                throw conflict("the talk is on the programme already: take it out first to move it");
            case PLACE_TAKEN:
                throw conflict(room + " " + slot + " has a talk already");
            case SPEAKER_BUSY:
                throw conflict("a speaker of this talk is speaking in another room in " + slot);
            default:
                break;
        }
        try {
            programmeDao.insert(room, slot, talkId);
        } catch (DuplicateKeyException e) {
            throw conflict("the talk or the room and slot is taken");
        }
        return new ProgrammeEntry(room, slot, talkId);
    }

    /** The talk in the room and slot; 404 if it's free. */
    @Transactional(readOnly = true)
    public ProgrammeEntry entry(String roomName, String slotName) {
        Room room = room(roomName);
        Slot slot = slot(slotName);
        Long talkId = programmeDao.findTalkId(room, slot);
        if (talkId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, room + " " + slot + " has no talk");
        }
        return new ProgrammeEntry(room, slot, talkId);
    }

    /** Takes the talk out of the room and slot; 404 if it's free. */
    @Transactional
    public void unschedule(String roomName, String slotName) {
        Room room = room(roomName);
        Slot slot = slot(slotName);
        if (!programmeDao.remove(room, slot)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, room + " " + slot + " has no talk");
        }
    }

    enum SchedulingOutcome { SCHEDULE, DOES_NOT_FIT, NOT_ACCEPTED, ALREADY_SCHEDULED, PLACE_TAKEN, SPEAKER_BUSY }

    /**
     * Decides whether a talk of the given format and status goes into a room and slot, given whether the talk is on
     * the programme already, whether the place is taken and whether one of its speakers speaks in that slot already.
     * Refusals that can never change come before conflicts with the current state.
     */
    static SchedulingOutcome schedulingOutcome(Slot slot, String format, String status, boolean alreadyScheduled,
                                               boolean placeTaken, boolean speakerBusy) {
        if (!slot.fits(format)) {
            return SchedulingOutcome.DOES_NOT_FIT;
        }
        if (!TalkStatus.ACCEPTED.name().equals(status)) {
            return SchedulingOutcome.NOT_ACCEPTED;
        }
        if (alreadyScheduled) {
            return SchedulingOutcome.ALREADY_SCHEDULED;
        }
        if (placeTaken) {
            return SchedulingOutcome.PLACE_TAKEN;
        }
        if (speakerBusy) {
            return SchedulingOutcome.SPEAKER_BUSY;
        }
        return SchedulingOutcome.SCHEDULE;
    }

    /** The body's {@code talkId} if it is a whole number; 400 otherwise. */
    static long requiredTalkId(Map<String, Object> body) {
        Object value = body == null ? null : body.get("talkId");
        if (!(value instanceof Integer || value instanceof Long)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "talkId is required and must be an integer");
        }
        return ((Number) value).longValue();
    }

    private static Room room(String name) {
        return Room.named(name).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "no room " + name + ": the rooms are R1, R2 and R3"));
    }

    private static Slot slot(String name) {
        return Slot.named(name).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "no slot " + name + ": the slots are S1 to S5"));
    }

    private static ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }
}
