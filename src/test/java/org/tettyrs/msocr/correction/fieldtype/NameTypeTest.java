package org.tettyrs.msocr.correction.fieldtype;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NameTypeTest {

    @Test
    void acceptsPlainName() {
        FieldResult result = NameType.correct("Ahmad Hidayat");
        assertEquals("Ahmad Hidayat", result.value());
        assertEquals(1.0, result.confidence());
        assertNull(result.correction());
    }

    @Test
    void acceptsNameWithTitles() {
        FieldResult result = NameType.correct("Dr. Ahmad Hidayat, M.M.");
        assertEquals("Dr. Ahmad Hidayat, M.M.", result.value());
        assertEquals(1.0, result.confidence());
        assertNull(result.correction());
    }

    @Test
    void acceptsNameWithCommonPunctuation() {
        FieldResult result = NameType.correct("Santoso-Santosa, S.E.");
        assertEquals("Santoso-Santosa, S.E.", result.value());
        assertEquals(1.0, result.confidence());
        assertNull(result.correction());
    }

    @Test
    void flagsNameWithDigit() {
        FieldResult result = NameType.correct("Bud1 Santoso");
        assertEquals("Bud1 Santoso", result.value());
        assertEquals(0.50, result.confidence());
        assertNull(result.correction());
        assertEquals(1, result.violations().size());
        assertEquals("nama_karakter_tidak_wajar", result.violations().get(0).rule());
    }

    @Test
    void flagsNameWithSymbol() {
        FieldResult result = NameType.correct("Ahmad@Hidayat");
        assertEquals("Ahmad@Hidayat", result.value());
        assertEquals(0.50, result.confidence());
        assertNull(result.correction());
        assertEquals(1, result.violations().size());
        assertEquals("nama_karakter_tidak_wajar", result.violations().get(0).rule());
    }

    @Test
    void doesNotCorrectName() {
        FieldResult result = NameType.correct("Santosa");
        assertEquals("Santosa", result.value());
        assertEquals(1.0, result.confidence());
        assertNull(result.correction());
    }
}