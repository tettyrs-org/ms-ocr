package org.tettyrs.msocr.correction.fieldtype;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

class DateTypeTest {

    @Test
    void readsTextForm(){
        FieldResult result = DateType.correct("12 januari 2026");
        assertEquals("2026-01-12", result.value());
        assertEquals(1.0, result.confidence());
        assertNull(result.correction());
        assertEquals(List.of(), result.violations());
    }
    @Test
    void readsExactAbbreviation(){
        assertEquals("2026-12-12", DateType.correct("12 Des 2026").value());
    }

    @Test
    void repairsMonthThroughLexicon(){
        FieldResult result = DateType.correct("12 Januar1 2026");
        assertEquals("2026-01-12", result.value());
        assertEquals("lexicon", result.correction());
        assertEquals(0.70, result.confidence());
    }

    @Test
    void refuseMisspeleedAbbreviation(){
        FieldResult result = DateType.correct("12 Dec 2026");
        assertEquals("12 Dec 2026", result.value());
        assertEquals(0.30, result.confidence());
    }

    @Test
    void repairsDigitThroughConfusionMap(){
        FieldResult result = DateType.correct("l2 Januari 2O26");
        assertEquals("2026-01-12", result.value());
        assertEquals("confusion_map", result.correction());
        assertEquals(0.70, result.confidence());
    }

    @Test
    void readsNumericFormDayFirst(){
        assertEquals("2026-12-31", DateType.correct("31/12/2026").value());
        assertEquals("2026-12-31", DateType.correct("31-12-2026").value());
    }

    @Test
    void flagsAmbiguousNumericForm(){
        FieldResult result = DateType.correct("01/12/2026");
        assertEquals("2026-12-01", result.value());
        assertEquals(1, result.violations().size());
        assertEquals("tanggal_ambigu", result.violations().get(0).rule());
    }

    @Test
    void doesNotFlagUnambiguousNumericForm(){
        assertTrue(DateType.correct("31/12/2026").violations().isEmpty());
    }

    @Test
    void refuseMixedSeperators(){
        assertEquals("12-01/2026", DateType.correct("12-01/2026").value());
    }

    @Test
    void keepsRawTextWhenDateIsInvalid(){
        FieldResult result = DateType.correct("31-02-2026");
        assertEquals("31-02-2026", result.value());
        assertEquals(0.30, result.confidence());
        assertEquals("31-02-2026", result.rawText());
    }

    @Test
    void refuseUnknownForm(){
        assertEquals(0.30, DateType.correct("kemarin").confidence());
    }
}
