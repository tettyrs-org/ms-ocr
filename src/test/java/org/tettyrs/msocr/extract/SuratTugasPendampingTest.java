package org.tettyrs.msocr.extract;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pemicu pendamping vision untuk surat tugas dan SPD. Teks memakai keluaran OCR nyata dari sampel surat tugas. */
class SuratTugasPendampingTest {

    @Test
    void suratTugasDenganTujuanTerbacaKurangMemicuPendamping() {
        // Nomor terbaca, tetapi "Tempat Tujuan" dan tanggal tidak. Pendamping harus dipanggil.
        String teks = "KEMENTERIAN KEUANGAN REPUBLIK INDONESIA\nSURAT TUGAS\nNomor: 090/ST/IX/2026\n"
                + "Dasar : Surat Undangan Nomor UND-45/PB. 1/2026 tanggal\n1 September 2026 perihal Rapat Koordinasi.\n"
                + "Menugaskan kepada:\n1. Nama : Budi Santoso\nNIP :198503122010011004\n"
                + "Tempat Tujuan : SURABAYA\nTanggarnerangkat : 15 September 2026";
        Map<String, Object> fields = RuleExtractor.extractReimburse(teks).fields();

        assertTrue(RuleExtractor.perluPendamping(RuleExtractor.DOC_SURAT_TUGAS, fields));
    }

    @Test
    void suratTugasLengkapTidakMemicuPendamping() {
        String teks = "SURAT TUGAS\nNomor: 090/ST/IX/2026\nDasar : Surat Undangan Nomor UND-45/PB.1/2026 tanggal\n"
                + "1 September 2026 perihal Rapat.\nMenugaskan kepada:\n1. Nama : Budi Santoso\n"
                + "NIP : 198503122010011004\nUraian Tugas : Rapat koordinasi\nTempat Tujuan : SURABAYA\n"
                + "Tanggal : 15 September 2026 s.d. 18 September 2026\nSumber Biaya : DIPA\n"
                + "Dikeluarkan di Jakarta, 10 September 2026\nPejabat Pembuat Komitmen, Dr. Ahmad Hidayat";
        Map<String, Object> fields = RuleExtractor.extractReimburse(teks).fields();

        assertFalse(RuleExtractor.perluPendamping(RuleExtractor.DOC_SURAT_TUGAS, fields));
    }

    @Test
    void namaPegawaiTerbacaDariBlokMenugaskanKepada() {
        // Teks OCR nyata dari sampel. Blok pegawai berakhir di "Maksud Perjalanan", bukan "Uraian Tugas".
        String teks = "SURAT TUGAS\nNomor: 090/ST/IX/2026\nMenugaskan kepada:\n1. Nama : Budi Santoso\n"
                + "NIP :198503122010011004\nPangkat/Golongan : Penata Muda Tingkat I / 1I-b\n"
                + "Jabatan : Analis Perbendaharaan\n2. Nama : Siti Rahayu\nNIP + 199007152015022003\n"
                + "Pangkat/Golongan : Penata / III-c\nJabatan : Pelaksana Verifikasi\n"
                + "Maksud Perjalanan : Rapat Koordinasi Penyusunan Laporan Keuangan\nTempat Tujuan : SURABAYA";
        Map<String, Object> fields = RuleExtractor.extractReimburse(teks).fields();

        java.util.List<?> pegawai = (java.util.List<?>) fields.get("pegawai");
        assertEquals(2, pegawai.size());
        assertEquals("Budi Santoso", value(pegawai.get(0), "nama"));
        assertEquals("Siti Rahayu", value(pegawai.get(1), "nama"));
    }

    private static Object value(Object orang, String kunci) {
        return ((Map<?, ?>) ((Map<?, ?>) orang).get(kunci)).get("value");
    }

    @Test
    void fieldSuratTugasTerisiTidakMemicuPendamping() {
        // Sad path: bila semua kunci surat tugas sudah berisi, pendamping tidak dipanggil.
        Map<String, Object> fields = Map.of("nomor_surat", Map.of("value", "090/ST/IX/2026"));

        assertFalse(RuleExtractor.perluPendamping(RuleExtractor.DOC_SURAT_TUGAS, fields));
    }
}
