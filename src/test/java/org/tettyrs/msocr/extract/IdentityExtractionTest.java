package org.tettyrs.msocr.extract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * KTP FIKTIF. Teks ini dibuat untuk pengujian: NIK, nama, dan alamat tidak menunjuk orang sungguhan.
 * Belum ada sampel KTP sungguhan di repo, jadi akurasi pada gambar KTP belum diukur (lihat spek 14).
 */
class IdentityExtractionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String KTP_FIKTIF = "PROVINSI JAWA TIMUR KOTA CONTOH "
            + "NIK 3271010101900001 "
            + "Nama CONTOH PENGGUNA "
            + "Tempat/Tgl Lahir KOTA CONTOH, 01-01-1990 "
            + "Jenis kelamin LAKI-LAKI Gol. Darah - "
            + "Alamat JL. CONTOH NO. 1 "
            + "RT/RW 001/002 "
            + "Kel/Desa CONTOH "
            + "Kecamatan CONTOH KECIL "
            + "Agama ISLAM "
            + "Status Perkawinan KAWIN "
            + "Pekerjaan KARYAWAN SWASTA "
            + "Kewarganegaraan WNI "
            + "Berlaku Hingga SEUMUR HIDUP";

    @Test
    void fieldKtpTerbacaDariTeksFiktif() throws Exception {
        JsonNode f = MAPPER.valueToTree(RuleExtractor.extractIdentity(KTP_FIKTIF));
        assertEquals("3271010101900001", f.get("nik").get("value").asText());
        assertEquals("CONTOH PENGGUNA", f.get("nama").get("value").asText());
        assertEquals("1990-01-01", f.get("tanggal_lahir").get("value").asText());
        assertEquals("LAKI-LAKI", f.get("jenis_kelamin").get("value").asText());
        assertEquals("001/002", f.get("rt_rw").get("value").asText());
        assertEquals("WNI", f.get("kewarganegaraan").get("value").asText());
        assertEquals("SEUMUR HIDUP", f.get("berlaku_hingga").get("value").asText());
    }

    @Test
    void ktpSampelDenganTeksOcrNyataTerbacaBenar() throws Exception {
        // Teks OCR nyata dari documents/sample/ktp.jpeg (tanpa deskew). Label dan nilai sering terpisah baris.
        String ocr = "PROVINSI DKI JAKARTA JAKARTA BARAT NIK : 3271014567890123 Nama : BUDI SANTOSO "
                + "Tempat/Tgl Lahir 1 JAKARTA, 03-03-1986 Jenis Kelamin : PEREMPUAN Gol. Darah: B : "
                + "Alamat : JL. MELATI B2/10 RTIRW : 007/008 Kel/Desa : KARANG SARI "
                + "Kecamatan : TAMAN SARI ‘Agama : ISLAM Status Perkawinan: KAWIN Pekerjaan PEGAWAI SWASTA "
                + "Kewarganegaraan : WNI JAKARTA BARAT 02-12-2012 Berlaku Hingga 22-02-2017";
        JsonNode f = MAPPER.valueToTree(RuleExtractor.extractIdentity(ocr));
        assertEquals("BUDI SANTOSO", f.get("nama").get("value").asText());
        assertEquals("JAKARTA", f.get("tempat_lahir").get("value").asText());
        assertEquals("1986-03-03", f.get("tanggal_lahir").get("value").asText());
        assertEquals("JL. MELATI B2/10", f.get("alamat").get("value").asText());
        assertEquals("007/008", f.get("rt_rw").get("value").asText());
        assertEquals("TAMAN SARI", f.get("kecamatan").get("value").asText());
        assertEquals("ISLAM", f.get("agama").get("value").asText());
        assertEquals("22-02-2017", f.get("berlaku_hingga").get("value").asText());
    }

    @Test
    void ktpFotoDenganLabelRusakTetapTerisi() throws Exception {
        // Teks OCR nyata dari documents/sample/ktp2.jpg (kartu dipegang tangan). Label "Tempat" dan "Agama" rusak,
        // kartunya berbahasa Inggris, dan nilai sering berada di baris setelah labelnya.
        String ocr = "PROVINSI JAWA BARAT\nKABUPATEN CIANJUR\nNIK\n:\nSITI AMINAH\nra Lahir\nBANDUNG, 01-01-1980 -\n"
                + "Jenis kelamin\nMALE\nGol. Darah\nAlamat\nNO. 40\nJL MERDEKA PERUMAHAN SUKAMAJU\nRT/RW\n002/004\n"
                + "Kei/Desa\nMUKA\nKecamatan\nCIANJUR\nCHRISTIAN\nStatus Perkawinan:\nMARRIED\nCIANJUR\n"
                + "Pekerjaan\nOTHERS.\n17-09-2018\nKewart\nCHINA\nBerlaku Hingga\n12-12-2023";
        JsonNode f = MAPPER.valueToTree(RuleExtractor.extractIdentity(ocr));
        assertEquals("BANDUNG", f.get("tempat_lahir").get("value").asText());
        assertEquals("1980-01-01", f.get("tanggal_lahir").get("value").asText());
        assertEquals("LAKI-LAKI", f.get("jenis_kelamin").get("value").asText());
        assertEquals("JL MERDEKA PERUMAHAN SUKAMAJU NO. 40", f.get("alamat").get("value").asText());
        assertEquals("002/004", f.get("rt_rw").get("value").asText());
        assertEquals("CIANJUR", f.get("kecamatan").get("value").asText());
        assertEquals("KRISTEN", f.get("agama").get("value").asText());
        assertEquals("KAWIN", f.get("status_perkawinan").get("value").asText());
        assertEquals("OTHERS.", f.get("pekerjaan").get("value").asText());
        assertEquals("WNA", f.get("kewarganegaraan").get("value").asText());
        assertEquals("12-12-2023", f.get("berlaku_hingga").get("value").asText());
    }

    @Test
    void pendampingVisionMengisiFieldKosongDanNikTidakValid() throws Exception {
        // Tesseract hanya membaca 12 dari 16 digit NIK pada ktp2.jpg. Hasil Qwen2.5-VL 3B melengkapinya.
        String ocr = "PROVINSI JAWA BARAT\nKABUPATEN CIANJUR\nNIK\n:\n030125037700\nKecamatan\nCIANJUR";
        JsonNode vision = MAPPER.readTree("{\"nik\":\"3271010505900002\",\"nama\":\"SITI AMINAH\","
                + "\"tempat_lahir\":\"BANDUNG\",\"tanggal_lahir\":\"01-01-1980\",\"jenis_kelamin\":\"MALE\","
                + "\"agama\":\"CHRISTIAN\",\"status_perkawinan\":\"MARRIED\",\"kewarganegaraan\":\"CHINA\"}");
        JsonNode f = MAPPER.valueToTree(RuleExtractor.extractIdentity(ocr, null, vision));
        assertEquals("3271010505900002", f.get("nik").get("value").asText());
        assertEquals("llm", f.get("nik").get("source").asText());
        assertEquals("SITI AMINAH", f.get("nama").get("value").asText());
        assertEquals("llm", f.get("nama").get("source").asText());
        assertEquals("BANDUNG", f.get("tempat_lahir").get("value").asText());
        assertEquals("1980-01-01", f.get("tanggal_lahir").get("value").asText());
        assertEquals("LAKI-LAKI", f.get("jenis_kelamin").get("value").asText());
        assertEquals("KRISTEN", f.get("agama").get("value").asText());
        assertEquals("KAWIN", f.get("status_perkawinan").get("value").asText());
        assertEquals("WNA", f.get("kewarganegaraan").get("value").asText());
        // Field yang sudah terbaca dari teks tidak ditimpa pendamping.
        assertEquals("CIANJUR", f.get("kecamatan").get("value").asText());
        assertEquals("rule", f.get("kecamatan").get("source").asText());
    }

    @Test
    void pendampingHanyaDipanggilBilaHasilTeksKurang() {
        // KTP lengkap dari teks: tidak perlu pendamping (menghemat waktu di CPU).
        Map<String, Object> lengkap = RuleExtractor.extractIdentity(KTP_FIKTIF);
        assertFalse(RuleExtractor.perluPendamping("IDENTITY_CARD", lengkap));
        // NIK kosong: perlu pendamping.
        Map<String, Object> kurang = RuleExtractor.extractIdentity("tidak ada apa-apa di sini");
        assertTrue(RuleExtractor.perluPendamping("IDENTITY_CARD", kurang));
    }

    @Test
    void nikTidakValidTetapDitampilkanSebagaiPerluDicek() throws Exception {
        // Bulan "67" tidak valid, tetapi bacaan OCR tetap ditampilkan dan ditandai untuk diperiksa pengguna.
        JsonNode f = MAPPER.valueToTree(RuleExtractor.extractIdentity("NIK 3271014567890123 Nama CONTOH"));
        assertEquals("3271014567890123", f.get("nik").get("value").asText());
        assertEquals("derived", f.get("nik").get("correction").asText());
    }

    @Test
    void nikKurangDari16DigitTidakDiisi() throws Exception {
        JsonNode f = MAPPER.valueToTree(RuleExtractor.extractIdentity("NIK 32710101019000 Nama CONTOH"));
        assertTrue(f.get("nik").get("value").isNull());
        assertEquals(0.0, f.get("nik").get("confidence").asDouble());
    }

    @Test
    void fieldYangTidakAdaTetapKosong() throws Exception {
        JsonNode f = MAPPER.valueToTree(RuleExtractor.extractIdentity("tidak ada apa-apa di sini"));
        assertTrue(f.get("agama").get("value").isNull());
        assertTrue(f.get("tanggal_lahir").get("value").isNull());
    }
}
