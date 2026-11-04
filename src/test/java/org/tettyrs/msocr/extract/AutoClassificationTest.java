package org.tettyrs.msocr.extract;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Kategori unggahan auto dari teks OCR. Contoh surat tugas memakai keluaran OCR nyata dari sampel. */
class AutoClassificationTest {

    @Test
    void suratTugasDenganTeksOcrNyataMasukReimburse() {
        String teks = "KEMENTERIAN KEUANGAN REPUBLIK INDONESIA\nSURAT TUGAS\nNomor: 090/ST/IX/2026\n"
                + "Menugaskan kepada:\n1. Nama : Budi Santoso\nNIP :198503122010011004\n"
                + "Tempat Tujuan : SURABAYA";
        assertEquals("reimburse", RuleExtractor.kategoriAuto(teks));
        assertEquals(RuleExtractor.DOC_SURAT_TUGAS, RuleExtractor.extractReimburse(teks).docType());
    }

    @Test
    void ktpDenganLabelNikMasukIdentitas() {
        String teks = "PROVINSI JAWA BARAT\nKABUPATEN BOGOR\nNIK 3201011234560001\nNama : BUDI SANTOSO";
        assertEquals("identity", RuleExtractor.kategoriAuto(teks));
    }

    @Test
    void ktpDenganJudulKartuTandaPendudukMasukIdentitas() {
        assertEquals("identity", RuleExtractor.kategoriAuto("KARTU TANDA PENDUDUK\nProvinsi JAWA BARAT"));
    }

    @Test
    void ktpTanpaLabelNikTetapMasukIdentitas() {
        // Teks OCR nyata dari ktp-org.jpg: label "NIK" terbaca rusak dan nomornya 17 digit, tetapi penanda KTP lengkap.
        String teks = "= : KABUPATEN\nms 1 21065014812940003\nNama SHS HASMAWALK\nTempat/Tgi Lahir— RAROWATU, 08-12-1994\n"
                + "Jenis kelamin \"PEREMPUAN — Gol. Darah ——\nAlamat JEND-SUDIRMAN\nRTRW 2900/6000\nKecamatan —=RUMBIA—\n"
                + "Status Perkawinan: BELUM KAWIN\nKewarganegaraan: WNI\nSerlaku Hingga — SEUMUR HIDUP";
        assertEquals("identity", RuleExtractor.kategoriAuto(teks));
    }

    @Test
    void penandaKtpKurangDariTigaTidakCukupUntukIdentitas() {
        // Sad path: dua penanda kebetulan ada di dokumen lain tidak boleh membuatnya dianggap KTP.
        assertEquals("other", RuleExtractor.kategoriAuto("Kabupaten Bogor\nPekerjaan: Guru"));
    }

    @Test
    void notaBbmMasukReimburse() {
        assertEquals("reimburse", RuleExtractor.kategoriAuto("SPBU 24.316.51\nNama Produk: PERTALITE\nTotal Rp 20.000"));
    }

    @Test
    void strukUmumMasukReimburse() {
        assertEquals("reimburse", RuleExtractor.kategoriAuto("Toko Maju Jaya\nTOTAL Rp 45.000\nTerima kasih"));
    }

    @Test
    void dokumenTanpaPenandaMasukLainnya() {
        // Sad path: dokumen yang tidak dikenali tidak boleh dipaksa menjadi struk atau KTP.
        assertEquals("other", RuleExtractor.kategoriAuto("Daftar hadir rapat koordinasi\nRuang Sidang Lantai 2"));
    }

    @Test
    void nipTidakDianggapNik() {
        // NIP 18 digit tidak boleh membuat dokumen dikenali sebagai KTP.
        assertEquals("reimburse", RuleExtractor.kategoriAuto("SURAT TUGAS\nNIP 198503122010011004"));
    }
}
