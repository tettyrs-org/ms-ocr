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
       assertEquals(FieldResult.accepted("198503122010011004"),
               NipType.correct("198503122010011004", TODAY));
   }

   @Test
    void removeSpacesBetweenGroups(){
       assertEquals(FieldResult.accepted("198503122010011004"),
               NipType.correct("1985 0312 201001 1 004", TODAY));
   }

    @Test
    void repairsLookalikeCharacters(){
        assertEquals(
                new FieldResult("198503122010011004", Correction.CONFUSION_MAP, 0.70, List.of()),
                NipType.correct("1985O3122O10011004", TODAY));
    }

    @Test
    void keepsOriginalWhenRepairBreaksStructure() {
        assertEquals(
                new FieldResult("19851312201001l004", Correction.NONE, 0.30,
                        List.of(Violation.NIP_FORMAT_TIDAK_VALID)),
                NipType.correct("19851312201001l004", TODAY));
    }

   @Test
    void refusesRepairBeyondSubtitutions(){
       assertEquals(
               new FieldResult("1985O3122O10011O4X", Correction.NONE, 0.50,
                       List.of(Violation.NIP_FORMAT_TIDAK_VALID)),
               NipType.correct("1985O3122O10011O4X", TODAY));
   }

    @Test
    void flagsInvalidStructureWithoutRepair(){
        assertEquals(
                new FieldResult("198513122010011004", Correction.NONE, 0.50,
                        List.of(Violation.NIP_FORMAT_TIDAK_VALID)),
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