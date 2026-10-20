package org.tettyrs.msocr.extract;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Struk BBM (nota Pertamina). Teks dari keluaran ocr-engine untuk foto nota-bensin.jpg, dengan nilai
 * kebenaran dari gambar. Nilai yang OCR-nya salah dibaca ditandai, bukan dipaksa lulus.
 */
class FuelExtractionTest {

    private static String nota() throws IOException {
        try (InputStream in = FuelExtractionTest.class.getResourceAsStream("/golden/nota-bensin-pertamina.txt")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Object value(Map<String, Object> fields, String key) {
        Object leaf = fields.get(key);
        return ((Map<?, ?>) leaf).get("value");
    }

    @Test
    void notaPertaminaDetectedAsFuelReimbursement() throws IOException {
        RuleExtractor.Result r = RuleExtractor.extractReimburse(nota());

        assertEquals(RuleExtractor.DOC_FUEL, r.docType());
    }

    @Test
    void readsTransactionDateProductAndTotal() throws IOException {
        Map<String, Object> f = RuleExtractor.extractReimburse(nota()).fields();

        assertEquals("1234567", value(f, "nomor_struk"));
        assertEquals("2025-02-28", value(f, "tanggal"));
        assertEquals("DEXLITE", value(f, "jenis_bbm"));
        assertEquals(600000L, value(f, "nominal_total"));
    }

    @Test
    void readsPlateAsOcrPrintedIt() throws IOException {
        Map<String, Object> f = RuleExtractor.extractReimburse(nota()).fields();

        // Di gambar tertulis "BG 1234 AB". OCR membaca "O)", jadi huruf terakhir ikut terpotong.
        assertEquals("BG 1234 O", value(f, "nomor_polisi"));
    }

    @Test
    @Disabled("OCR membaca 40,13 sebagai 30,13. Parser sudah benar; menunggu OCR membaca angka ini.")
    void readsVolumeInLiters() throws IOException {
        Map<String, Object> f = RuleExtractor.extractReimburse(nota()).fields();

        assertEquals(40.13, value(f, "liter"));
    }

    @Test
    void volumeCommaDecimalBecomesNumber() {
        assertEquals(40.13, RuleExtractor.liter("Volume : (L) 40,13").get("value"));
    }

    @Test
    void volumeWithMisreadUnitInParenthesesIsRead() {
        // Teks OCR nyata dari nota-bensin.jpg: satuan "(l)" terbaca "(0)" dan tanpa titik dua.
        assertEquals(40.13, RuleExtractor.liter("volume (0) 40,13").get("value"));
    }

    @Test
    void jenisBbmTidakIkutLabelHargaYangTerbacaMarga() {
        // Teks OCR nyata: label "Harga/Liter" terbaca "Marga/Liter" dan tidak boleh masuk ke nama produk.
        Map<String, Object> f = RuleExtractor.fuel("Nama Produk: DEKLITE Marga/Liter: Rp. 14,950 volume (0) 40,13");
        assertEquals("DEXLITE", value(f, "jenis_bbm"));
    }

    @Test
    void produkSalahBacaSatuHurufDikoreksiKeDaftarResmi() {
        // OCR membaca X sebagai K pada nota nyata. Koreksi ditandai "derived" agar diperiksa di tinjauan.
        Map<String, Object> f = RuleExtractor.fuel("Nama Produk: DEKLITE Harga/Liter: Rp. 14,950");
        assertEquals("DEXLITE", value(f, "jenis_bbm"));
        assertEquals("derived", ((Map<?, ?>) f.get("jenis_bbm")).get("correction"));
    }

    @Test
    void produkJauhDariDaftarTidakDikoreksi() {
        Map<String, Object> f = RuleExtractor.fuel("Nama Produk: ZZZZZZ Harga/Liter: Rp. 14,950");
        assertEquals("ZZZZZZ", value(f, "jenis_bbm"));
    }

    @Test
    void pendampingVisionMengisiFieldStrukYangKosong() throws Exception {
        // Teks tanpa volume dan tanggal. Pendamping mengisi keduanya, dan nominal yang berformat "Rp 600.000".
        Map<String, Object> fields = RuleExtractor.fuel("Nama Produk: DEXLITE Harga/Liter: Rp. 14,950");
        JsonNode vision = new ObjectMapper().readTree(
                "{\"liter\":\"40,13\",\"tanggal\":\"28/02/2025\",\"nominal_total\":\"Rp 600.000\"}");
        RuleExtractor.mergeVisionDokumen(fields, vision);
        assertEquals(40.13, value(fields, "liter"));
        assertEquals("2025-02-28", value(fields, "tanggal"));
        assertEquals(600000L, value(fields, "nominal_total"));
        assertEquals("llm", ((Map<?, ?>) fields.get("liter")).get("source"));
        assertEquals("DEXLITE", value(fields, "jenis_bbm"));
    }

    @Test
    void dateWithoutMatchingLabelStaysEmpty() {
        // Sad path: tanggal numerik yang bulannya tidak valid tidak boleh ditebak.
        assertNull(RuleExtractor.anyDate("Waktu: 28/13/2025").get("value"));
    }

    @Test
    void nonFuelReceiptIsNotClassifiedAsFuel() throws IOException {
        try (InputStream in = FuelExtractionTest.class.getResourceAsStream("/golden/GD-19_struk_belanja.txt")) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            assertEquals(RuleExtractor.DOC_RECEIPT, RuleExtractor.extractReimburse(text).docType());
        }
    }
}
