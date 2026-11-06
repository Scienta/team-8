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

    public AcceptanceController(AcceptanceService service) {
        this.service = service;
    }

    @PutMapping("/talks/{id}/status")
    public TalkDto decide(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return service.decide(id, body);
    }

    @PostMapping("/talks/{id}/withdraw")
    public TalkDto withdraw(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return service.withdraw(id, body);
    }
}
