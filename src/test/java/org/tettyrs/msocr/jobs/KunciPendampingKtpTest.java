package org.tettyrs.msocr.jobs;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class KunciPendampingKtpTest {

    private static Map<String, Object> leaf(String value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", value);
        return m;
    }

    private static Map<String, Object> kosong() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", null);
        return m;
    }

    @Test
    void fieldRawanSelaluDiminta() {
        // Agama dan pekerjaan sering salah dibaca aturan, jadi diminta walau sudah terisi.
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("tempat_lahir", leaf("RAROWATU"));
        fields.put("agama", leaf("HISLAM"));

        assertTrue(JobConsumer.kunciPendampingKtp(fields).contains("agama"));
    }

    @Test
    void fieldKosongDiluarDaftarIkutDiminta() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("tempat_lahir", kosong());

        assertTrue(JobConsumer.kunciPendampingKtp(fields).contains("tempat_lahir"));
    }

    @Test
    void fieldIntiValidHanyaMintaAlamatDanPekerjaan() {
        // Hasil teks sudah lengkap dan lolos validasi: alamat dan pekerjaan tetap dibaca vision (Tesseract sering salah).
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("nik", leaf("3271010101900001"));
        fields.put("nama", leaf("BUDI SANTOSO"));
        fields.put("alamat", leaf("JL MERDEKA 1"));
        fields.put("rt_rw", leaf("001/002"));
        fields.put("kelurahan_desa", leaf("MENTENG"));
        fields.put("kecamatan", leaf("MENTENG"));
        fields.put("agama", leaf("ISLAM"));
        fields.put("pekerjaan", leaf("KARYAWAN SWASTA"));

        assertEquals(List.of("alamat", "pekerjaan"), JobConsumer.kunciPendampingKtp(fields));
    }

    @Test
    void nikTidakValidDiminta() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("nik", leaf("3271019999999999"));
        fields.put("nama", leaf("BUDI SANTOSO"));

        assertTrue(JobConsumer.kunciPendampingKtp(fields).contains("nik"));
        assertFalse(JobConsumer.kunciPendampingKtp(fields).contains("nama"));
    }

    @Test
    void fieldTerisiDiluarDaftarTidakDiminta() {
        // Sad path: field yang sudah benar dari aturan dan tidak rawan tidak perlu dibaca ulang (lebih cepat).
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("tanggal_lahir", leaf("1994-12-08"));

        assertFalse(JobConsumer.kunciPendampingKtp(fields).contains("tanggal_lahir"));
    }
}
