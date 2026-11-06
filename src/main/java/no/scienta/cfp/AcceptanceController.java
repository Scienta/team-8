package no.scienta.cfp;

import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The HTTP API for the committee's decisions on talks and the speakers' withdrawals.
 */
@RestController
public class AcceptanceController {

    private final AcceptanceService service;
    private final InstanceLocks locks;

    public AcceptanceController(AcceptanceService service, InstanceLocks locks) {
        this.service = service;
        this.locks = locks;
    }

    @PutMapping("/talks/{id}/status")
    public TalkDto decide(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return locks.onTalk(id, () -> service.decide(id, body));
    }

    @PostMapping("/talks/{id}/withdraw")
    public TalkDto withdraw(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return locks.onTalk(id, () -> service.withdraw(id, body));
    }
}
