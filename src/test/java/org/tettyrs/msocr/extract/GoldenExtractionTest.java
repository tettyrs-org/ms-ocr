package org.tettyrs.msocr.extract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Uji golden: teks OCR dari ocr-engine untuk sampel di documents/sample/golden, dibandingkan dengan
 * nilai kebenaran di manifest.json (dummy). Dokumen bersih harus cocok penuh. Sisanya dilaporkan,
 * bukan dipaksa lulus, agar kualitas aturan terlihat apa adanya.
 */
class GoldenExtractionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String resource(String name) throws IOException {
        try (InputStream in = GoldenExtractionTest.class.getResourceAsStream("/golden/" + name)) {
            assertTrue(in != null, "resource tidak ditemukan: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static JsonNode manifestEntry(String id) throws IOException {
        JsonNode root = MAPPER.readTree(resource("manifest.json"));
        for (JsonNode d : root.get("dokumen")) {
            if (id.equals(d.get("id").asText())) return d;
        }
        throw new IllegalArgumentException("tidak ada di manifest: " + id);
    }

    /** Meratakan nilai kebenaran menjadi path -> nilai skalar. Array memakai indeks [i]. */
    static Map<String, String> flattenExpected(JsonNode node, String path, Map<String, String> out) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                flattenExpected(e.getValue(), path.isEmpty() ? e.getKey() : path + "." + e.getKey(), out);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) flattenExpected(node.get(i), path + "[" + i + "]", out);
        } else if (!node.isNull()) {
            out.put(path, node.asText());
        }
        return out;
    }

    /** Meratakan hasil ekstraksi: objek leaf (value/source) menjadi path -> value. */
    static Map<String, String> flattenActual(JsonNode node, String path, Map<String, String> out) {
        if (node.isObject() && node.has("source") && node.has("value")) {
            JsonNode v = node.get("value");
            if (!v.isNull()) out.put(path, v.asText());
            return out;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                flattenActual(e.getValue(), path.isEmpty() ? e.getKey() : path + "." + e.getKey(), out);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) flattenActual(node.get(i), path + "[" + i + "]", out);
        }
        return out;
    }

    /** Hasil per dokumen: jumlah field benar dan total field kebenaran, plus daftar yang salah. */
    record Score(int correct, int total, List<String> wrong) {
    }

    static Score score(String id, String text) throws IOException {
        JsonNode entry = manifestEntry(id);
        RuleExtractor.Result result = RuleExtractor.extractReimburse(text);
        JsonNode actual = MAPPER.valueToTree(result.fields());
        Map<String, String> expected = flattenExpected(entry.get("nilai"), "", new LinkedHashMap<>());
        Map<String, String> got = flattenActual(actual, "", new LinkedHashMap<>());
        int correct = 0;
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, String> e : expected.entrySet()) {
            String value = got.get(e.getKey());
            if (e.getValue().equals(value)) {
                correct++;
            } else {
                wrong.add(e.getKey() + " harap=" + e.getValue() + " dapat=" + value);
            }
        }
        assertEquals(entry.get("jenis_dokumen").asText(), result.docType(), "jenis dokumen " + id);
        return new Score(correct, expected.size(), wrong);
    }

    @Test
    void suratTugasBersihCocokPenuh() throws IOException {
        Score s = score("GD-01", resource("GD-01_st_scan_bersih.txt"));
        assertTrue(s.wrong().isEmpty(), "salah: " + s.wrong());
    }

    /**
     * Akun anggaran GD-02 (524111) tidak muncul di teks OCR sampel, jadi aturan tidak bisa menemukannya.
     * Dicatat sebagai gap yang diketahui, bukan disembunyikan. Tes gagal bila gap ini berubah.
     */
    @Test
    void spdBersihCocokKecualiAkunYangTidakTerbaca() throws IOException {
        Score s = score("GD-02", resource("GD-02_spd_scan_bersih.txt"));
        assertEquals(1, s.wrong().size(), "salah: " + s.wrong());
        assertTrue(s.wrong().get(0).startsWith("pembebanan_anggaran.akun"), s.wrong().get(0));
    }

    @Test
    void koreksiRomawiNomorSuratDicatat() throws IOException {
        RuleExtractor.Result r = RuleExtractor.extractReimburse(resource("GD-01_st_scan_bersih.txt"));
        JsonNode nomor = MAPPER.valueToTree(r.fields().get("nomor_surat"));
        assertEquals("090/ST/IX/2026", nomor.get("value").asText());
        assertEquals("confusion_map", nomor.get("correction").asText());
        assertEquals("090/ST/LX/2026", nomor.get("raw_text").asText());
    }

    @Test
    void nipSelalu18DigitString() throws IOException {
        RuleExtractor.Result r = RuleExtractor.extractReimburse(resource("GD-01_st_scan_bersih.txt"));
        JsonNode pegawai = MAPPER.valueToTree(r.fields().get("pegawai"));
        assertEquals(2, pegawai.size());
        assertEquals("198503122010011004", pegawai.get(0).get("nip").get("value").asText());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GD-01", "GD-02", "GD-04", "GD-05", "GD-06", "GD-09", "GD-15", "GD-16", "GD-17", "GD-18"})
    void laporanAkurasiPerDokumen(String id) throws IOException {
        String file = manifestEntry(id).get("file").asText();
        Score s = score(id, resource(file.replaceFirst("\\.[a-z]+$", ".txt")));
        System.out.printf("GOLDEN %s %d/%d%n", id, s.correct(), s.total());
        s.wrong().forEach(w -> System.out.println("GOLDEN   " + id + " " + w));
    }
}
