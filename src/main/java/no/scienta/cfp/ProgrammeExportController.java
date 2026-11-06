package no.scienta.cfp;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The HTTP API for exporting the whole programme: as JSON for the website and as CSV for the print shop.
 */
@RestController
public class ProgrammeExportController {

    /** Sent whatever the client accepts: the print shop's tooling wants exactly this. */
    static final String CSV_CONTENT_TYPE = "text/csv;charset=UTF-8";

    private final ProgrammeExportService service;

    public ProgrammeExportController(ProgrammeExportService service) {
        this.service = service;
    }

    @GetMapping("/programme")
    public List<ProgrammeRow> programme() {
        return service.programme();
    }

    /**
     * The CSV as bytes with the content type set here, so that Spring writes it as it is instead of negotiating with
     * the request's {@code Accept} header.
     */
    @GetMapping("/programme.csv")
    public ResponseEntity<byte[]> programmeCsv() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, CSV_CONTENT_TYPE)
                .body(ProgrammeCsv.write(service.programme()).getBytes(StandardCharsets.UTF_8));
    }
}
