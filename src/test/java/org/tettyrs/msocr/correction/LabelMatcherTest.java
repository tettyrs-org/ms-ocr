package org.tettyrs.msocr.correction;

import org.junit.jupiter.api.Test;
import org.tettyrs.msocr.correction.tokens.Line;
import org.tettyrs.msocr.correction.tokens.Token;
import org.tettyrs.msocr.correction.LabelMatcher.LabelMatchResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Test cases 2-6, 10, 15 dari dokumentasi:
 * Tahap B - Label matching dan value extraction
 */
class LabelMatcherTest {

    private static Token token(String text) {
        return new Token(text, List.of(0.0, 0.0, 0.1, 0.1), 0.9, 0);
    }

    private static Line line(String... words) {
        return new Line(List.of(words).stream().map(LabelMatcherTest::token).toList());
    }

    @Test
    void matchesExactLabel() {
        // Case 2: `Dasar — : Surat Undangan …` → Label `Dasar` cocok
        Line testLine = line("Dasar", "-", ":", "Surat", "Undangan");
        LabelMatchResult result = LabelMatcher.match(testLine, "SURAT_TUGAS");

        assertEquals("Dasar", result.label());
        assertEquals(0, result.distance());
        assertEquals(2, result.valueStartIndex()); // value mulai setelah "Dasar" "-" ":"
    }

    @Test
    void matchesLabelWithDistance() {
        // Case 4: `Tanggai Berangkat : 15 September 2026` → Label cocok dengan jarak 1
        Line testLine = line("Tanggai", "Berangkat", ":", "15", "September", "2026");
        LabelMatchResult result = LabelMatcher.match(testLine, "SURAT_TUGAS");

        assertEquals(1, result.distance()); // `Tanggai` typo dari `Tanggal` (jarak 1)
        assertEquals("Tanggal Berangkat", result.correctedLabel());
    }

    @Test
    void consumesSeparatorsAfterLabel() {
        // Case 2, 6: `.` atau `-` atau `:` dikonsumsi sebagai pemisah setelah label
        Line testLine = line("NIP", ":", "198503122010011004");
        LabelMatchResult result = LabelMatcher.match(testLine, "SURAT_TUGAS");

        assertEquals("NIP", result.label());
        assertEquals(2, result.valueStartIndex()); // value mulai di index 2 (setelah "NIP" dan ":")
    }

    @Test
    void rejectsShortLabelWithoutExactMatch() {
        // Case 15: `Nana Santoso` - `Nana` bukan `Nama` (length mismatch, bukan persis)
        Line testLine = line("Nana", "Santoso");
        LabelMatchResult result = LabelMatcher.match(testLine, "SURAT_TUGAS");

        assertNull(result); // Tidak cocok - Nana bukan Nama yang tepat
    }

    @Test
    void doesNotMatchNonLabelLine() {
        // Case 10: `Pembebanan Anggaran : Ditjen Perbendaharaan TA 2026` - tidak cocok label
        Line testLine = line("Ditjen", "Perbendaharaan", "TA", "2026");
        LabelMatchResult result = LabelMatcher.match(testLine, "SURAT_TUGAS");

        assertNull(result); // Tidak ada label yang cocok
    }

}
