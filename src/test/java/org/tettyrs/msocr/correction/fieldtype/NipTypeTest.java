package org.tettyrs.msocr.correction.fieldtype;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NipTypeTest {

   private static final LocalDate TODAY = LocalDate.of(2026,9, 14);

   @Test
    void acceptsValidNip(){
       assertEquals(new FieldResult("198503122010011004", 1.0, "rule", "198503122010011004", null, 0, null, List.of()),
               NipType.correct("198503122010011004", TODAY));
   }

   @Test
    void removeSpacesBetweenGroups(){
       assertEquals(new FieldResult("198503122010011004", 1.0, "rule", "1985 0312 201001 1 004", null, 0, null, List.of()),
               NipType.correct("1985 0312 201001 1 004", TODAY));
   }

    @Test
    void repairsLookalikeCharacters(){
        assertEquals(
                new FieldResult("198503122010011004", 0.70, "rule", "1985O3122O10011004", "confusion_map", 0, null, List.of()),
                NipType.correct("1985O3122O10011004", TODAY));
    }

    @Test
    void keepsOriginalWhenRepairBreaksStructure() {
        assertEquals(
                new FieldResult("19851312201001l004", 0.30, "rule", "19851312201001l004", null, 0, null,
                        List.of(new Violation("nip_format_tidak_valid", List.of("nip"), "error", "Format NIP tidak valid"))),
                NipType.correct("19851312201001l004", TODAY));
    }

   @Test
    void refusesRepairBeyondSubtitutions(){
       assertEquals(
               new FieldResult("1985O3122O10011O4X", 0.50, "rule", "1985O3122O10011O4X", null, 0, null,
                       List.of(new Violation("nip_format_tidak_valid", List.of("nip"), "error", "Format NIP tidak valid"))),
               NipType.correct("1985O3122O10011O4X", TODAY));
   }

    @Test
    void flagsInvalidStructureWithoutRepair(){
        assertEquals(
                new FieldResult("198513122010011004", 0.50, "rule", "198513122010011004", null, 0, null,
                        List.of(new Violation("nip_format_tidak_valid", List.of("nip"), "error", "Format NIP tidak valid"))),
                NipType.correct("198513122010011004", TODAY));
    }


    @Test
    void checkEveryStructureRule(){
       assertFalse(NipType.hasValidStructure("19850312201001100", TODAY), "17 digit");
       assertFalse(NipType.hasValidStructure("198502302010011004", TODAY), "30 Februari");
       assertFalse(NipType.hasValidStructure("193503122010011004", TODAY), "lahir sebelum 1940");
       assertFalse(NipType.hasValidStructure("201001012026011004", TODAY), "lahir terlalu muda");
       assertFalse(NipType.hasValidStructure("198503122010131004", TODAY), "bulan TMT 13");
       assertFalse(NipType.hasValidStructure("198503122002011004", TODAY), "TMT sebelum usia 18");
       assertFalse(NipType.hasValidStructure("198503122027011004", TODAY), "TMT di masa depan");
       assertFalse(NipType.hasValidStructure("198503122010013004", TODAY), "jenis kelamin 3");
       assertFalse(NipType.hasValidStructure("198503122010011000", TODAY), "nomor urut 000");
   }
}