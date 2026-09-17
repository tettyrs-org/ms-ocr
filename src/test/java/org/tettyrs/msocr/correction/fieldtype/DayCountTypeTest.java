package org.tettyrs.msocr.correction.fieldtype;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;


class DayCountTypeTest {

    @Test
    void  acceptMatchingNumberAndSpelled(){
        FieldResult fieldResult = DayCountType.correct("4 (empat) hari");
        assertEquals("4", fieldResult.value());
        assertEquals(1.0, fieldResult.confidence());
        assertNull(fieldResult.correction());
        assertEquals(List.of(), fieldResult.violations());
    }

    @Test
    void acceptsNumberWithoutSpelled(){
        FieldResult fieldResult = DayCountType.correct("4 hari");
        assertEquals("4", fieldResult.value());
        assertEquals(List.of(), fieldResult.violations());
    }

    @Test
    void readsMisreadParenthesis(){
        FieldResult fieldResult = DayCountType.correct("4 Cempat) hari");
        assertEquals("4", fieldResult.value());
        assertEquals(List.of(), fieldResult.violations());
    }

    @Test
    void flagsMismatchWithoutOverwriting() {
        FieldResult fieldResult = DayCountType.correct("4 (lima) hari");
        assertEquals("4", fieldResult.value());
        assertEquals(0.50, fieldResult.confidence());
        assertEquals("terbilang_tidak_cocok", fieldResult.violations().get(0).rule());
    }

    @Test
    void fallsBackToSpelledWhenNumberMissing(){
        FieldResult fieldResult = DayCountType.correct("(empat) hari");
        assertEquals("4", fieldResult.value());
        assertEquals("lexicon", fieldResult.correction());
        assertEquals(0.70, fieldResult.confidence());
    }

    @Test
    void repairsNumberThroughConfusionMap(){
        FieldResult fieldResult = DayCountType.correct("1O (sepuluh) hari");
        assertEquals("10", fieldResult.value());
        assertEquals("confusion_map", fieldResult.correction());
        assertEquals(0.70, fieldResult.confidence());
    }

    @Test
    void refusesOutRange(){
        FieldResult fieldResult = DayCountType.correct("400 hari");
        assertNull(fieldResult.value());
        assertEquals("400 hari", fieldResult.rawText());
        assertEquals(0.30, fieldResult.confidence());
    }

    @Test
    void refusesZeroDays(){
        assertEquals(0.30, DayCountType.correct("0 hari").confidence());
    }

}