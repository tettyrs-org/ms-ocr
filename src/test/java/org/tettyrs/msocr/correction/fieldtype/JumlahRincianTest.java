package org.tettyrs.msocr.correction.fieldtype;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.tettyrs.msocr.correction.fieldtype.JumlahRincian.JumlahRincianResult;

class JumlahRincianTest {

    @Test
    void flagsMismatchWhenAllValuesPresent() {
        // Case 21: Rincian 500.000 + 750.000 = 1.250.000, tapi total 1.350.000 (salah)
        JumlahRincianResult result = JumlahRincian.validate(
                List.of("500000", "750000"),  // rincian
                "1350000"                      // total (salah)
        );
        assertEquals(false, result.isValid());
        assertEquals("jumlah_rincian_tidak_cocok", result.violation());
        assertEquals("1350000", result.totalValue());  // tidak diubah
    }

    @Test
    void derivesTotalWhenMissing() {
        // Case 22: Rincian 500.000 + 750.000 = 1.250.000, total kosong
        JumlahRincianResult result = JumlahRincian.validate(
                List.of("500000", "750000"),  // rincian
                null                          // total missing
        );
        assertEquals(true, result.isValid());
        assertEquals("1250000", result.totalValue());  // derived
        assertEquals("derived", result.correction());
        assertEquals(0.60, result.confidence());
    }

    @Test
    void passesWhenTotalMatches() {
        // Total cocok dengan rincian
        JumlahRincianResult result = JumlahRincian.validate(
                List.of("500000", "750000"),
                "1250000"
        );
        assertEquals(true, result.isValid());
        assertNull(result.violation());
    }

    @Test
    void refusesDerivationWhenRincianHasCorrections() {
        // Tidak derive jika ada rincian yang punya correction
        JumlahRincianResult result = JumlahRincian.validate(
                List.of("500000"),  // satu rincian (sudah dikoreksi)
                null,               // total missing
                true                // rincian_corrected
        );
        assertEquals(false, result.canDerive());
    }

}
