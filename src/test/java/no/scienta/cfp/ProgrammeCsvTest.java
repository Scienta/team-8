package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The print shop's CSV format of {@link ProgrammeCsv} (R6, R8–R10 of {@code workflow/specs/programme-export.md}).
 */
class ProgrammeCsvTest {

    @Test
    void r10_anEmptyProgrammeIsJustTheHeader() {
        assertEquals("slot,start,end,room,talkId,title,format,speakers\n", ProgrammeCsv.write(List.of()));
    }

    @Test
    void r8_oneLinePerRowEachEndingInANewline() {
        String csv = ProgrammeCsv.write(List.of(
                new ProgrammeRow(Slot.S1, Room.R1, 7, "Engines", "PRESENTATION", List.of("Ada Lovelace")),
                new ProgrammeRow(Slot.S4, Room.R3, 8, "Compilers", "WORKSHOP",
                        List.of("Grace Hopper", "Ada Lovelace"))));
        assertEquals("slot,start,end,room,talkId,title,format,speakers\n"
                + "S1,09:00,10:00,R1,7,Engines,PRESENTATION,Ada Lovelace\n"
                + "S4,13:30,15:30,R3,8,Compilers,WORKSHOP,Grace Hopper; Ada Lovelace\n", csv);
    }

    @Test
    void r9_aFieldWithACommaOrQuoteIsQuotedWithItsQuotesDoubled() {
        assertEquals("\"Kotlin, \"\"really\"\" fast\"", ProgrammeCsv.field("Kotlin, \"really\" fast"));
        assertEquals("\"\"\"Quoted\"\"\"", ProgrammeCsv.field("\"Quoted\""));
        assertEquals("\"a,b\"", ProgrammeCsv.field("a,b"));
    }

    @Test
    void r9_aFieldWithALineBreakIsQuotedAndKeepsIt() {
        assertEquals("\"two\nlines\"", ProgrammeCsv.field("two\nlines"));
        assertEquals("\"two\r\nlines\"", ProgrammeCsv.field("two\r\nlines"));
        assertEquals("\"carriage\rreturn\"", ProgrammeCsv.field("carriage\rreturn"));
    }

    @Test
    void r9_everythingElseIsWrittenAsItIs() {
        assertEquals("Grace Hopper; Ada Lovelace", ProgrammeCsv.field("Grace Hopper; Ada Lovelace"));
        assertEquals("Åse Ødegård's talk", ProgrammeCsv.field("Åse Ødegård's talk"));
        assertEquals(" spaced ", ProgrammeCsv.field(" spaced "));
        assertEquals("", ProgrammeCsv.field(""));
    }

    @Test
    void r6_speakersShareOneFieldQuotedOnlyIfANameNeedsIt() {
        String csv = ProgrammeCsv.write(List.of(new ProgrammeRow(Slot.S2, Room.R2, 1, "Talk", "LIGHTNING_10",
                List.of("Hopper, Grace", "Ada Lovelace"))));
        assertEquals("S2,10:15,11:15,R2,1,Talk,LIGHTNING_10,\"Hopper, Grace; Ada Lovelace\"\n",
                csv.substring(csv.indexOf('\n') + 1));
    }
}
