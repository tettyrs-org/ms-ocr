package org.tettyrs.msocr.correction;

import org.junit.jupiter.api.Test;
import org.tettyrs.msocr.correction.fieldtype.*;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests untuk full pipeline Surat Tugas.
 * Menguji: Tahap A (cleanup), Tahap B (label matching), Tahap C (field type correction)
 */
class SuratTugasIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);

    @Test
    void correctsMultipleFieldsInSuratTugas() {
        // Test case gabungan dari dokumentasi: NIP + Pangkat + Tanggal

        // Case 3: NIP (clean value setelah label dipisah)
        FieldResult nipResult = NipType.correct("198503122010011004", TODAY);
        assertEquals("198503122010011004", nipResult.value());
        assertNull(nipResult.correction());

        // Case 4: Tanggal dengan typo label
        FieldResult dateResult = DateType.correct("15 September 2026");
        assertEquals("2026-09-15", dateResult.value());
        assertNull(dateResult.correction());

        // Case 7: Pangkat/Golongan misread
        FieldResult rankResult = RankGradeType.correct("Penata Muda Tingkat I / 1II-D");
        assertEquals("Penata Muda Tingkat I (III/b)", rankResult.value());
        assertEquals("derived", rankResult.correction());
    }

    @Test
    void processesRegionWithDisambiguation() {
        // Case: Bandung (ambigu - bisa KOTA atau KAB)

        // Tanpa context → tetap as-is
        FieldResult resultNoContext = RegionType.correct("Bandung");
        assertEquals("Bandung", resultNoContext.value());
        assertNull(resultNoContext.correction());

        // Dengan kecamatan context → disambiguate ke KOTA BANDUNG
        FieldResult resultWithKec = RegionType.correct("Bandung", "CIBEUNYING KALER", null);
        assertEquals("Kota Bandung", resultWithKec.value());
        assertNull(resultWithKec.correction());

        // Dengan kodepos context → disambiguate ke KAB BANDUNG
        FieldResult resultWithKp = RegionType.correct("Bandung", null, "40375");
        assertEquals("Kab. Bandung", resultWithKp.value());
    }

    @Test
    void flagsViolationsAcrossFields() {
        // Integration test: detect violations di multiple fields

        // NIP dengan struktur invalid (bulan 13)
        FieldResult nipInvalid = NipType.correct("198513122010011004", TODAY);
        assertEquals(0.50, nipInvalid.confidence());
        assertEquals(1, nipInvalid.violations().size());
        assertEquals("nip_format_tidak_valid", nipInvalid.violations().get(0).rule());

        // Pangkat/Golongan mismatch
        FieldResult rankMismatch = RankGradeType.correct("Penata Muda Tingkat I / III-d");
        assertEquals(0.50, rankMismatch.confidence());
        assertEquals(1, rankMismatch.violations().size());
        assertEquals("pangkat_golongan_tidak_cocok", rankMismatch.violations().get(0).rule());
    }

    @Test
    void derivesFieldsWhenPossible() {
        // Case 22: Derive total dari rincian
        JumlahRincian.JumlahRincianResult result = JumlahRincian.validate(
                List.of("500000", "750000"),
                null
        );
        assertEquals("derived", result.correction());
        assertEquals("1250000", result.totalValue());
        assertEquals(0.60, result.confidence());
    }

    @Test
    void preservesConfidenceHierarchy() {
        // Confidence harus < 1.0 ketika ada koreksi

        // Exact match: confidence 1.0
        FieldResult exact = RegionType.correct("Surabaya");
        assertEquals("Kota Surabaya", exact.value());
        assertEquals(1.0, exact.confidence());

        // Fuzzy match (typo correction): confidence 0.70
        FieldResult typo = RegionType.correct("SURABAVA");
        assertEquals("SURABAYA", typo.value()); // Preserves input case (uppercase)
        assertEquals(0.70, typo.confidence());
        assertEquals("lexicon", typo.correction());
    }

}
