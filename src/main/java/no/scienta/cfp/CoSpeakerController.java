package no.scienta.cfp;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The HTTP API for a talk's co-speakers.
 */
@RestController
public class CoSpeakerController {

    private final CoSpeakerService service;
    private final InstanceLocks locks;

    public CoSpeakerController(CoSpeakerService service, InstanceLocks locks) {
        this.service = service;
        this.locks = locks;
    }

    @PutMapping("/talks/{id}/co-speakers/{speakerId}")
    public TalkDto add(@PathVariable long id, @PathVariable long speakerId) {
        return locks.onTalkAndSpeaker(id, speakerId, () -> service.add(id, speakerId));
    }

    @DeleteMapping("/talks/{id}/co-speakers/{speakerId}")
    public TalkDto remove(@PathVariable long id, @PathVariable long speakerId) {
        return service.remove(id, speakerId);
    }
}
