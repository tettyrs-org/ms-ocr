package org.tettyrs.msocr.extract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Nilai placeholder dari pendamping (nol semua) tidak boleh mengisi field. */
class PlaceholderVisionTest {

    @Test
    void hanyaKosongDanNullYangDianggapPlaceholder() {
        assertTrue(RuleExtractor.placeholder(""));
        assertTrue(RuleExtractor.placeholder("null"));
        // RT/RW "000/000" bisa memang tertulis di kartu, jadi tidak boleh ditolak.
        assertFalse(RuleExtractor.placeholder("000/000"));
        assertFalse(RuleExtractor.placeholder("001/002"));
        assertFalse(RuleExtractor.placeholder("40,13"));
    }

    @Test
    void volumeNolDariPendampingTidakMengisiLiter() throws Exception {
        // Sad path: pendamping mengisi "0,00" untuk field yang tidak terbaca. Liter harus tetap kosong.
        Map<String, Object> fields = RuleExtractor.fuel("Nama Produk: DEXLITE Harga/Liter: Rp. 14,950");
        JsonNode vision = new ObjectMapper().readTree("{\"liter\":\"0,00\"}");

        RuleExtractor.mergeVisionDokumen(fields, vision);

        assertNull(((Map<?, ?>) fields.get("liter")).get("value"));
    }
}
