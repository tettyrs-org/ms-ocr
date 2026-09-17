package org.tettyrs.msocr.correction.fieldtype;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class NominalTypeTest {

    private static final String SATU_JUTA_DUA_RATUS_LIMA_PULUH_RIBU =
            "satu juta dua ratus lima puluh ribu rupiah";

    @Test
    void readsIndonesianFormatWithPrefixAndZeroDecimals() {
        FieldResult result = NominalType.correct("Rp 1.250.000,00");
        assertEquals("1250000", result.value());
        assertEquals(1.0, result.confidence());
        assertNull(result.correction());
        assertEquals(List.of(), result.violations());
    }

    @Test
    void readsClosingDash() {
        assertEquals("1250000", NominalType.correct("Rp. 1.250.000,-").value());
    }

    @Test
    void joinsSpaceSeparatedTokens() {
        assertEquals("1250000", NominalType.correct("R p 1 250 000").value());
    }

    @Test
    void readsWesternFormat() {
        assertEquals("1250000", NominalType.correct("IDR 1,250,000").value());
    }

    @Test
    void refusesInconsistentSeparators() {
        FieldResult result = NominalType.correct("1.250,000");
        assertNull(result.value());
        assertEquals("1.250,000", result.rawText());
        assertEquals("nominal_format_tidak_valid", result.violations().get(0).rule());
    }

    @Test
    void refusesGroupsThatAreNotThreeDigits() {
        assertNull(NominalType.correct("12.50.000").value());
    }

    @Test
    void repairsOneCharacter() {
        FieldResult result = NominalType.correct("Rp 1.25O.000");
        assertEquals("1250000", result.value());
        assertEquals("confusion_map", result.correction());
        assertEquals(0.70, result.confidence());
    }

    @Test
    void refusesMoreThanOneRepair() {
        assertNull(NominalType.correct("Rp 1.25O.O00").value());
    }

    @Test
    void flagsNonZeroDecimals() {
        FieldResult result = NominalType.correct("1.250.000,50");
        assertEquals("1250000", result.value());
        assertEquals(0.50, result.confidence());
        assertEquals("nominal_format_tidak_valid", result.violations().get(0).rule());
    }

    @Test
    void acceptsMatchingSpelled() {
        FieldResult result = NominalType.correct("Rp 1.250.000", SATU_JUTA_DUA_RATUS_LIMA_PULUH_RIBU);
        assertEquals("1250000", result.value());
        assertEquals(1.0, result.confidence());
        assertEquals(List.of(), result.violations());
    }

    @Test
    void flagsSpelledMismatchWithoutOverwriting() {
        FieldResult result = NominalType.correct("Rp 1.250.000", "satu juta dua ratus ribu rupiah");
        assertEquals("1250000", result.value());
        assertEquals(0.50, result.confidence());
        assertEquals("terbilang_tidak_cocok", result.violations().get(0).rule());
    }

    @Test
    void fallsBackToSpelledWhenAmountIsInvalid() {
        FieldResult result = NominalType.correct("12.50.000", SATU_JUTA_DUA_RATUS_LIMA_PULUH_RIBU);
        assertEquals("1250000", result.value());
        assertEquals("lexicon", result.correction());
        assertEquals(0.70, result.confidence());
        assertEquals(List.of(), result.violations());
    }
}
