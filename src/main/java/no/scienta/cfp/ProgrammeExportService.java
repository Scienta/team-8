package no.scienta.cfp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the whole programme for export, one row per scheduled talk, in the order the day runs: earliest slot first,
 * and within a slot by room. See {@code workflow/specs/programme-export.md}.
 */
@Service
public class ProgrammeExportService {

    /** The order of the day: by slot, which is by start time, then by room. */
    static final Comparator<ProgrammeRow> DAY_ORDER = Comparator
            .comparing((ProgrammeRow row) -> Slot.valueOf(row.slot))
            .thenComparing(row -> Room.valueOf(row.room));

    private final ProgrammeDao programmeDao;

    public ProgrammeExportService(ProgrammeDao programmeDao) {
        this.programmeDao = programmeDao;
    }

    @Transactional(readOnly = true)
    public List<ProgrammeRow> programme() {
        return inDayOrder(programmeDao.findRows());
    }

    /** The rows sorted in the order of the day, leaving the given list as it was. */
    static List<ProgrammeRow> inDayOrder(List<ProgrammeRow> rows) {
        List<ProgrammeRow> sorted = new ArrayList<>(rows);
        sorted.sort(DAY_ORDER);
        return sorted;
    }
}
