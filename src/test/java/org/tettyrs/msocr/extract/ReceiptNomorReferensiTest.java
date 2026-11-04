package org.tettyrs.msocr.extract;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReceiptNomorReferensiTest {

    private static Object value(Map<String, Object> fields, String key) {
        return ((Map<?, ?>) fields.get(key)).get("value");
    }

    @Test
    void noTrxDipakaiSebagaiNomorReferensi() {
        // Teks OCR nyata dari struk SPBU: nomor transaksi tertulis "No. Trx", bukan "Trans".
        Map<String, Object> f = RuleExtractor.receipt("SPBU SEJAHTERA 31.555.01\nTOTAL : Rp 192.000\nNo. Trx : TRX-7795511");

        assertEquals("TRX-7795511", value(f, "nomor_referensi"));
    }

    @Test
    void noTransTetapDipakai() {
        Map<String, Object> f = RuleExtractor.receipt("TOKO A\nNo. Trans : 4471\nTOTAL Rp 20.000");

        assertEquals("4471", value(f, "nomor_referensi"));
    }

    @Test
    void noFolioHotelDipakaiSebagaiNomorReferensi() {
        // Teks OCR nyata dari struk hotel (RCP-006): nomor referensinya tertulis "No. Folio".
        Map<String, Object> f = RuleExtractor.receipt("HOTEL MELATI INDAH\nNo. Folio : HMI-20261012-044\nTOTAL Rp 1.530.000");

        assertEquals("HMI-20261012-044", value(f, "nomor_referensi"));
    }

    @Test
    void tanpaLabelNomorTetapKosong() {
        // Sad path: tidak ada label nomor, jadi nilainya kosong dan tidak ditebak.
        Map<String, Object> f = RuleExtractor.receipt("TOKO A\nTOTAL Rp 20.000");

        assertNull(value(f, "nomor_referensi"));
    }
}
