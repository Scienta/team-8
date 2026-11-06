package no.scienta.cfp;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The HTTP API for registering attendees for workshops and listing who registered.
 */
@RestController
public class WorkshopRegistrationController {

    private final WorkshopRegistrationService service;
    private final InstanceLocks locks;

    public WorkshopRegistrationController(WorkshopRegistrationService service, InstanceLocks locks) {
        this.service = service;
        this.locks = locks;
    }

    @PostMapping("/workshops/{id}/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    public Registration register(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return locks.onTalk(id, () -> service.register(id, body));
    }

    @GetMapping("/workshops/{id}/registrations")
    public List<Registration> registrations(@PathVariable long id) {
        return service.registrations(id);
    }
}
