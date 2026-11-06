package no.scienta.cfp;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Registers attendees for accepted workshops, enforcing that a workshop takes at most {@value #CAPACITY} attendees and
 * each email once. See {@code workflow/specs/workshop-registration.md}.
 */
@Service
public class WorkshopRegistrationService {

    static final int CAPACITY = 20;
    static final String WORKSHOP = "WORKSHOP";

    private final TalkDao talkDao;
    private final RegistrationDao registrationDao;

    public WorkshopRegistrationService(TalkDao talkDao, RegistrationDao registrationDao) {
        this.talkDao = talkDao;
        this.registrationDao = registrationDao;
    }

    /**
     * Registers the attendee last on the workshop's list. The talk row stays locked until commit, so concurrent
     * registrations for one workshop can't both see a free seat or both miss the same email.
     */
    @Transactional
    public Registration register(long workshopId, Map<String, Object> body) {
        String name = requiredText(body, "name");
        String email = requiredText(body, "email");
        if (!Utils.isValidEmail(email.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is not an email address");
        }
        TalkDto workshop = talkDao.findByIdForUpdate(workshopId);
        if (workshop == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "workshop not found");
        }
        if (!WORKSHOP.equals(workshop.format)) {
            throw conflict("a " + workshop.format + " talk isn't a workshop and doesn't take registrations");
        }
        String emailKey = emailKey(email);
        RegistrationDao.Tally tally = registrationDao.tally(workshopId, emailKey);
        switch (registrationOutcome(workshop.status, tally.emailTaken(), tally.registrations())) {
            case NOT_ACCEPTED:
                throw conflict("a " + workshop.status + " workshop doesn't take registrations");
            case ALREADY_REGISTERED:
                throw conflict("this email is already registered for the workshop");
            case FULL:
                throw conflict("the workshop is full: it has room for " + CAPACITY);
            default:
                break;
        }
        try {
            return registrationDao.insert(workshopId, name, email, emailKey);
        } catch (DuplicateKeyException e) {
            throw conflict("this email is already registered for the workshop");
        }
    }

    /**
     * The talk's registrations in the order they were made, whatever its status. A talk that isn't a workshop has none,
     * so its list is empty.
     */
    @Transactional(readOnly = true)
    public List<Registration> registrations(long workshopId) {
        if (talkDao.findById(workshopId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "workshop not found");
        }
        return registrationDao.findByWorkshop(workshopId);
    }

    enum RegistrationOutcome { REGISTER, NOT_ACCEPTED, ALREADY_REGISTERED, FULL }

    /**
     * Decides whether a workshop in the given status, with the given number of registrations, takes a registration
     * from someone who is or isn't registered already.
     */
    static RegistrationOutcome registrationOutcome(String status, boolean alreadyRegistered, int registrations) {
        if (!TalkStatus.ACCEPTED.name().equals(status)) {
            return RegistrationOutcome.NOT_ACCEPTED;
        }
        if (alreadyRegistered) {
            return RegistrationOutcome.ALREADY_REGISTERED;
        }
        if (registrations >= CAPACITY) {
            return RegistrationOutcome.FULL;
        }
        return RegistrationOutcome.REGISTER;
    }

    /** The email as it identifies a person: {@code ada@example.com} and {@code " ADA@Example.com"} are one. */
    static String emailKey(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** The body's value for {@code field} if it is a string with something in it; 400 otherwise. */
    static String requiredText(Map<String, Object> body, String field) {
        Object value = body == null ? null : body.get(field);
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return (String) value;
    }

    private static ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }
}
