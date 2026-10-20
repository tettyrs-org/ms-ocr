package org.tettyrs.msocr.extract;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** KTP dengan variasi dari OCR. Teks fiktif. */
class IdentityOcrVariantsTest {

    private static Object value(Map<String, Object> fields, String key) {
        return ((Map<?, ?>) fields.get(key)).get("value");
    }

    @Test
    void readsFieldsWhenOcrLowercasesLabelsAndValues() {
        String text = "NIK 3271010101900001 nama CONTOH PENGGUNA tempat/tgl lahir KOTA CONTOH, 01-01-1990 "
                + "jenis Kelamin Laki-laki alamat JL CONTOH RT/RW 001/002 kel/desa CONTOH kecamatan CONTOH "
                + "agama Islam status perkawinan Belum Kawin pekerjaan PELAJAR kewarganegaraan wni "
                + "berlaku hingga SEUMUR HIDUP";

        Map<String, Object> f = RuleExtractor.extractIdentity(text);

        assertEquals("3271010101900001", value(f, "nik"));
        assertEquals("CONTOH PENGGUNA", value(f, "nama"));
        assertEquals("LAKI-LAKI", value(f, "jenis_kelamin"));
        assertEquals("ISLAM", value(f, "agama"));
        assertEquals("BELUM KAWIN", value(f, "status_perkawinan"));
        assertEquals("WNI", value(f, "kewarganegaraan"));
    }

    @Test
    void readsFieldsWhenOcrGarblesLabels() {
        // "TempatTgi Lahir", "Ke/Desa", dan "RT/RW" yang hilang adalah bentuk yang tercatat dari OCR KTP nyata.
        String text = "NIK 3271010101900001 Nama CONTOH PENGGUNA TempatTgi Lahir KOTA CONTOH, 01-01-1990 "
                + "Jenis kelamin PEREMPUAN Alamat JL CONTOH 000/000 Ke/Desa CONTOH Kecamatan CONTOH "
                + "Agama ISLAM Hingga SEUMUR HIDUP";

        Map<String, Object> f = RuleExtractor.extractIdentity(text);

        assertEquals("KOTA CONTOH", value(f, "tempat_lahir"));
        assertEquals("1990-01-01", value(f, "tanggal_lahir"));
        assertEquals("PEREMPUAN", value(f, "jenis_kelamin"));
        assertEquals("JL CONTOH", value(f, "alamat"));
        assertEquals("CONTOH", value(f, "kelurahan_desa"));
        assertEquals("SEUMUR HIDUP", value(f, "berlaku_hingga"));
    }

    @Test
    void nikIsFoundWithoutLabelAndAllowsOcrSpaces() {
        // Tanpa label "NIK", tetapi 16 digit dengan struktur valid (perempuan).
        Map<String, Object> f = RuleExtractor.extractIdentity("PROVINSI CONTOH 7406 0455 1294 0003 KABUPATEN");

        assertEquals("7406045512940003", value(f, "nik"));
    }

    @Test
    void invalidSixteenDigitNumberIsSkipped() {
        // Kandidat pertama memakai tanggal 34, yang tidak mungkin. Nomor lain yang valid dipakai.
        Map<String, Object> f = RuleExtractor.extractIdentity("NOMOR 1234567890123456 NIK 3271010101900001");

        assertEquals("3271010101900001", value(f, "nik"));
    }

    @Test
    void nameIsReadFromLineAfterNikWhenLabelMissing() {
        String raw = "NIK 3271010101900001\nCONTOH PENGGUNA\nKOTA CONTOH, 01-01-1990\n";

        Map<String, Object> f = RuleExtractor.extractIdentity(raw);

        assertEquals("CONTOH PENGGUNA", value(f, "nama"));
    }

    @Test
    void nameFromLineIsNotGuessedWhenNotUppercase() {
        // Sad path: baris setelah NIK tidak berhuruf kapital, jadi tidak dianggap nama.
        String raw = "NIK 3271010101900001\nContoh Pengguna\nKOTA CONTOH, 01-01-1990\n";

        Map<String, Object> f = RuleExtractor.extractIdentity(raw);

        assertNull(value(f, "nama"));
    }

    @Test
    void nikStaysEmptyWhenNotReadable() {
        // Sad path: tanpa deretan 16 digit, NIK tidak boleh ditebak.
        Map<String, Object> f = RuleExtractor.extractIdentity("nama CONTOH PENGGUNA agama ISLAM");

        assertNull(value(f, "nik"));
        // Nama butuh label "Tempat" sebagai batas, atau baris setelah NIK. Tanpa keduanya, tidak ditebak.
        assertNull(value(f, "nama"));
    }
}
