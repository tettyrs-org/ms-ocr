package org.tettyrs.msocr.correction.fieldtype;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DateTypeTest {

    @Test
    void parsesTextFormat() {
        var result = DateType.correct("12 Januari 2026");
        assertEquals("2026-01-12", result.value());
    }

    @Test
    void parsesNumericFormatWithDash() {
        var result = DateType.correct("12-01-2026");
        assertEquals("2026-01-12", result.value());
    }

    @Test
    void parsesNumericFormatWithSlash() {
        var result = DateType.correct("12/01/2026");
        assertEquals("2026-01-12", result.value());
    }

    @Test
    void rejectsInvalidDate() {
        var result = DateType.correct("31-02-2026");
        assertEquals(null, result.value());
        assertEquals(0.30, result.confidence());
    }
}
