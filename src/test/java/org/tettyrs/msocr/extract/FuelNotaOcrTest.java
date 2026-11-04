package org.tettyrs.msocr.extract;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Teks OCR nyata dari foto nota-bensin01.jpeg (struk SPBU). Label dan bulan terbaca dengan salah eja. */
class FuelNotaOcrTest {

    private static final String TEKS = "SPHU GOVEAN\nA. Hari Raya\nTLP, 08123456789\n"
            + "Jum'at, 01 Novenber 2024 14:51:07\nNomor Poapa : 1\nNosor Selang : 3\nNowor Nota 1 29\n"
            + "Jenis BBM = Pertalite\nLiter | 2\nmarga/liter 1 Rp. 10.0%\nTotal i Rp, 20000\n"
            + "Operator titi\nNosor Polisi : AA 1234 BC";

    private static Object value(Map<String, Object> fields, String key) {
        return ((Map<?, ?>) fields.get(key)).get("value");
    }

    @Test
    void nomorNotaDenganNomorAwalDibacaSebagaiNomorStruk() {
        assertEquals("29", value(RuleExtractor.fuel(TEKS), "nomor_struk"));
    }

    @Test
    void bulanDenganSalahEjaDikenali() {
        assertEquals("2024-11-01", RuleExtractor.isoDate("01 Novenber 2024"));
    }

    @Test
    void bulanTidakMiripTetapKosong() {
        // Sad path: kata yang tidak mirip nama bulan tidak boleh ditebak sebagai bulan.
        assertNull(RuleExtractor.isoDate("01 Abcdefg 2024"));
    }

    @Test
    void penerbitKosongMemicuPendampingWalauNomorTerbaca() {
        // Nomor dan liter sudah terbaca, tetapi penerbit SPBU kosong. Pendamping tetap harus dipanggil.
        Map<String, Object> f = RuleExtractor.fuel("No. Trans : 29\nPertalite\nLiter : 2.00\nTotal Rp 20.000");

        assertEquals(true, RuleExtractor.perluPendamping(RuleExtractor.DOC_FUEL, f));
    }

    @Test
    void literTanpaLabelVolDenganAngkaBulatDibaca() {
        assertEquals(2.0, RuleExtractor.liter("Liter | 2").get("value"));
        assertEquals(15.0, RuleExtractor.liter("Liter : 15.00 L").get("value"));
    }

    @Test
    void hargaPerLiterBukanVolume() {
        // Sad path: "Harga/Liter" dan angka yang diikuti "Rp" adalah harga, bukan volume.
        assertNull(RuleExtractor.liter("marga/liter 1 Rp. 10.0%").get("value"));
        assertNull(RuleExtractor.liter("Harga/Liter : Rp 14.950").get("value"));
    }

    @Test
    void notaTransTetapDibaca() {
        assertEquals("1234", value(RuleExtractor.fuel("No. Trans : 1234\nPertalite"), "nomor_struk"));
    }
}
