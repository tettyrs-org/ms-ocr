package org.tettyrs.msocr.extract;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ekstraksi field berbasis aturan dari teks OCR (extraction-schema.md bagian 1, 3, 4).
 *
 * Setiap nilai berbentuk objek: value, confidence, source, raw_text, correction, page, bbox.
 * Nilai yang tidak ditemukan diberi value null dan confidence 0. Tidak ada tebakan: bila pola
 * tidak cocok, field dibiarkan kosong. Fungsi ini murni (tanpa I/O) agar bisa diuji dengan golden.
 */
public final class RuleExtractor {

    public static final String DOC_SURAT_TUGAS = "SURAT_TUGAS";
    public static final String DOC_SPD = "SPD";
    public static final String DOC_RECEIPT = "RECEIPT";
    public static final String DOC_FUEL = "REIMBURSEMENT_FUEL";
    public static final String DOC_IDENTITY = "IDENTITY_CARD";

    // Deretan 16 digit, boleh dengan spasi OCR di antaranya, dan tidak menempel ke digit lain.
    private static final Pattern NIK_CANDIDATE = Pattern.compile("(?<!\\d)(?:\\d ?){15}\\d(?!\\d)");

    // Penanda struk BBM: nama SPBU, nama produk, atau label "Nama Produk" dan "Harga/Liter".
    private static final Pattern FUEL_MARKER = Pattern.compile(
            "\\b(PERTAMINA|SPBU|BBM|BENSIN|SOLAR|PERTAMAX|PERTALITE|PERTADEX|DEXLITE|DEX)\\b|NAMA PRODUK|HARGA/LITER");

    static final double CONF_RULE = 0.95;
    static final double CONF_CORRECTED = 0.8;

    private static final Map<String, Integer> BULAN = Map.ofEntries(
            Map.entry("januari", 1), Map.entry("februari", 2), Map.entry("maret", 3),
            Map.entry("april", 4), Map.entry("mei", 5), Map.entry("juni", 6),
            Map.entry("juli", 7), Map.entry("agustus", 8), Map.entry("september", 9),
            Map.entry("oktober", 10), Map.entry("november", 11), Map.entry("desember", 12));

    private static final Set<String> ROMAWI = Set.of(
            "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII");

    // Hari boleh terbaca "2?" akibat OCR. Tanda tanya dibuang saat normalisasi.
    private static final String DATE_RE = "\\d{1,2}\\??\\s+[A-Za-z]+\\s+\\d{4}";
    private static final String NOMOR_RE = "[0-9A-Za-z]+(?:\\s*/\\s*[0-9A-Za-z.\\-]+){2,}";
    private static final String NIP_RE = "([0-9 ]{18,24})";

    private RuleExtractor() {
    }

    /** Hasil ekstraksi: jenis dokumen yang terdeteksi dan objek `fields` untuk callback. */
    public record Result(String docType, Map<String, Object> fields) {
    }

    /** Dokumen reimburse: jenis ditentukan dari kata kunci judul, lalu field dihitung sesuai jenisnya. */
    public static Result extractReimburse(String rawText) {
        String text = collapse(rawText);
        String upper = text.toUpperCase(Locale.ROOT);
        if (upper.contains("SURAT TUGAS")) {
            return new Result(DOC_SURAT_TUGAS, suratTugas(text));
        }
        if (upper.contains("PERJALANAN DINAS") || upper.contains("SPPD")) {
            return new Result(DOC_SPD, spd(text));
        }
        if (FUEL_MARKER.matcher(upper).find()) {
            return new Result(DOC_FUEL, fuel(text));
        }
        return new Result(DOC_RECEIPT, receipt(text));
    }

    // ---------------------------------------------------------------- Struk BBM (extraction-schema.md, REIMBURSEMENT_FUEL)

    /**
     * Struk SPBU. `spbu_penerbit` diisi kode SPBU (mis. 24.316.51), karena nama SPBU di struk Pertamina
     * hanya logo. Label dibaca tanpa membedakan huruf besar-kecil, karena OCR sering menulis "Total" bukan "TOTAL".
     */
    static Map<String, Object> fuel(String t) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("nomor_struk", text(t, "(?i)No\\.?\\s*(?:Trans(?:aksi)?|Struk)\\s*:?\\s*([0-9]+)"));
        f.put("tanggal", anyDate(t));
        f.put("spbu_penerbit", text(t, "(\\d{2}\\.\\d{3}\\.\\d{2})"));
        // Satu atau dua kata (mis. DEXLITE, PERTAMAX TURBO). Kata "Rp" dan label berikutnya bukan bagian nama.
        f.put("jenis_bbm", produkBbm(text(t, "(?i)Nama\\s*Produk\\s*:?\\s*([A-Za-z0-9+]+(?:\\s+(?!Rp\\b|Harga|Marga|Liter|Volume|Total|Pulau|Pompa|Operator|CASH)[A-Za-z0-9+]+)?)")));
        f.put("liter", liter(t));
        f.put("nominal_total", totalHarga(t));
        // OCR sering menyisipkan simbol di antara label dan nilai, jadi yang dilewati adalah non-huruf-angka.
        f.put("nomor_polisi", text(t, "(?i)Plat[^A-Za-z0-9]*([A-Z]{1,2}\\s*\\d{1,4}(?:\\s*[A-Z0-9]{1,3})?)"));
        return f;
    }

    /**
     * Pendamping vision untuk dokumen umum (struk, surat tugas, SPD). Hanya field skalar tingkat atas yang kosong
     * dari teks yang diisi. Nilai tanggal dan angka dinormalkan dengan aturan yang sama dengan teks.
     */
    public static void mergeVisionDokumen(Map<String, Object> fields, JsonNode vision) {
        if (vision == null || !vision.isObject()) return;
        for (String k : List.copyOf(fields.keySet())) {
            if (!kosong(fields, k)) continue;
            JsonNode node = vision.path(k);
            if (!node.isValueNode() || node.isNull()) continue;
            String teks = node.asText("").trim();
            if (teks.isEmpty() || "null".equalsIgnoreCase(teks)) continue;
            Map<String, Object> hasil = null;
            if (k.startsWith("tanggal")) {
                hasil = anyDate(teks);
            } else if (k.equals("liter")) {
                Matcher m = Pattern.compile("\\d+[.,]\\d+").matcher(teks);
                if (m.find()) hasil = leaf(Double.parseDouble(m.group().replace(',', '.')), m.group(), "derived");
            } else if (k.equals("nominal_total")) {
                String digit = teks.replaceAll("[^0-9]", "");
                if (!digit.isEmpty()) hasil = money(digit);
            } else {
                hasil = leaf(teks, teks, "derived");
            }
            if (hasil == null || hasil.get("value") == null) continue;
            hasil.put("source", "llm");
            fields.put(k, hasil);
        }
    }

    /** Produk BBM resmi. OCR sering salah baca satu huruf (DEXLITE terbaca DEKLITE). */
    static final List<String> PRODUK_BBM = List.of(
            "DEXLITE", "PERTAMINA DEX", "PERTALITE", "PERTAMAX", "PERTAMAX TURBO", "PERTAMAX GREEN", "BIOSOLAR");

    /**
     * Nama produk dicocokkan ke daftar produk resmi. Selisih maksimal 2 huruf dikoreksi dan ditandai "derived",
     * supaya tinjauan memeriksanya. Nama yang jauh dari daftar dibiarkan apa adanya.
     */
    static Map<String, Object> produkBbm(Map<String, Object> hasil) {
        if (!(hasil.get("value") instanceof String s)) return hasil;
        String huruf = s.toUpperCase(Locale.ROOT);
        String terdekat = null;
        int jarak = Integer.MAX_VALUE;
        for (String p : PRODUK_BBM) {
            int d = levenshtein(huruf, p);
            if (d < jarak) {
                jarak = d;
                terdekat = p;
            }
        }
        if (terdekat == null || jarak == 0 || jarak > 2) return hasil;
        return leaf(terdekat, s, "derived");
    }

    static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int[] cur = new int[b.length() + 1];
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int biaya = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + biaya);
            }
            prev = cur;
        }
        return prev[b.length()];
    }

    /** Volume dalam liter. Koma desimal Indonesia diubah menjadi titik, dan angka tetap disimpan sebagai number. */
    static Map<String, Object> liter(String t) {
        // Satuan dalam kurung sering terbaca salah: "(l)", "(L)", "(0)", "(1)". Kurung seperti itu dilewati.
        Matcher m = Pattern.compile("(?i)Vol\\w*\\s*:?\\s*(?:\\(\\s*[^()\\s]{0,2}\\s*\\))?\\s*:?\\s*(\\d+[.,]\\d+)").matcher(t);
        if (!m.find()) return empty();
        String raw = m.group(1);
        return leaf(Double.parseDouble(raw.replace(',', '.')), raw, null);
    }

    /** Total pembayaran dari baris "Total Harga Rp ...". Dibedakan dari "Harga/Liter". */
    static Map<String, Object> totalHarga(String t) {
        Matcher m = Pattern.compile("(?i)[ti]otal\\s*Harga\\s*:?\\s*Rp\\.?\\s*([0-9][0-9.,]*)").matcher(t);
        return m.find() ? money(m.group(1)) : empty();
    }

    /** Tanggal dd mm yyyy kalimat, atau dd/mm/yyyy dari struk. Null bila keduanya tidak ada. */
    static Map<String, Object> anyDate(String t) {
        Map<String, Object> textual = dateAfter(t, "");
        if (textual.get("value") != null) return textual;
        Matcher m = Pattern.compile("(\\d{2})/(\\d{2})/(\\d{4})").matcher(t);
        if (!m.find()) return textual;
        int month = Integer.parseInt(m.group(2));
        int day = Integer.parseInt(m.group(1));
        if (month < 1 || month > 12 || day < 1 || day > 31) return leaf(null, m.group(), null);
        String iso = m.group(3) + "-" + m.group(2) + "-" + m.group(1);
        return leaf(iso, m.group(), "derived");
    }

    // ---------------------------------------------------------------- Surat Tugas

    static Map<String, Object> suratTugas(String t) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("nomor_surat", nomorSurat(t));
        f.put("tanggal_surat", dateAfter(t, "Dikeluarkan di [A-Za-z ]+,"));
        f.put("dasar_penugasan", text(t, "Dasar\\s*:\\s*(.+?)\\s*Menugaskan"));
        f.put("pemberi_tugas", penandatanganSuratTugas(t));
        f.put("pegawai", suratTugasPegawai(t));
        f.put("uraian_tugas", text(t, "Uraian Tugas\\s*:\\s*(.+?)\\s+Tempat Tujuan"));
        f.put("tempat_tujuan", text(t, "Tempat Tujuan[^A-Za-z]*(.+?)\\s+Tanggal\\s*:"));
        Matcher range = Pattern.compile("Tanggal\\s*:\\s*(" + DATE_RE + ")\\s*s\\.?\\s*d\\.?\\s*(" + DATE_RE + ")")
                .matcher(t);
        if (range.find()) {
            f.put("tanggal_mulai", dateLeaf(range.group(1)));
            f.put("tanggal_selesai", dateLeaf(range.group(2)));
        } else {
            f.put("tanggal_mulai", empty());
            f.put("tanggal_selesai", empty());
        }
        f.put("sumber_biaya", text(t, "Sumber Biaya\\s*:\\s*(.+?)\\s+Dikeluarkan"));
        return f;
    }

    /** Penanda tangan Surat Tugas: "jabatan, nama" setelah baris tanggal "Dikeluarkan di ...". */
    private static Map<String, Object> penandatanganSuratTugas(String t) {
        Map<String, Object> out = new LinkedHashMap<>();
        Matcher m = Pattern.compile("Dikeluarkan di [A-Za-z ]+,\\s*(?:\\d{1,2}\\??\\s+[A-Za-z]+\\s+\\d{4}|[|\\s]*[A-Za-z]+\\s+\\d{4})?[|\\s]*(.+)$")
                .matcher(t);
        if (m.find()) {
            String tail = clean(m.group(1));
            int comma = tail.indexOf(',');
            if (comma > 0) {
                String jabatan = clean(tail.substring(0, comma));
                String nama = clean(tail.substring(comma + 1));
                out.put("nama", leaf(nama, nama, null));
                out.put("jabatan", leaf(jabatan, jabatan, null));
                return out;
            }
        }
        out.put("nama", empty());
        out.put("jabatan", empty());
        return out;
    }

    private static List<Map<String, Object>> suratTugasPegawai(String t) {
        List<Map<String, Object>> out = new ArrayList<>();
        Matcher block = Pattern.compile("Menugaskan kepada\\s*:?\\s*(.+?)\\s+Uraian Tugas").matcher(t);
        if (!block.find()) return out;
        Matcher m = Pattern.compile(
                "\\d+\\.\\s*Nama\\s*:\\s*(.+?)\\s+NIP\\s*:?\\s*" + NIP_RE + "\\s+Pangkat[^:]{0,10}:\\s*(.+?)\\s+Jabatan\\s*:\\s*(.+?)(?=\\s+\\d+\\.\\s*Nama|$)")
                .matcher(block.group(1));
        while (m.find()) {
            out.add(orang(m.group(1), m.group(2), m.group(3), m.group(4), null));
        }
        return out;
    }

    // ---------------------------------------------------------------- SPD

    static Map<String, Object> spd(String t) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("nomor_spd", nomorSurat(t));
        f.put("tanggal_spd", dateAfter(t, "Tanggal\\s*:"));
        Map<String, Object> pbk = new LinkedHashMap<>();
        pbk.put("nama", text(t, "Pejabat Pembuat Komitmen\\s*,\\s*(.+)$"));
        f.put("pejabat_pembuat_komitmen", pbk);
        List<Map<String, Object>> pegawai = new ArrayList<>();
        Matcher m = Pattern.compile(
                "Nama\\s*:\\s*(.+?)\\s+NIP\\s*:?\\s*" + NIP_RE + "\\s+Pangkat[^:]{0,10}:\\s*(.+?)\\s+Jabatan\\s*:\\s*(.+?)\\s+Tingkat Biaya\\s*:\\s*(\\S+)")
                .matcher(t);
        if (m.find()) {
            pegawai.add(orang(m.group(1), m.group(2), m.group(3), m.group(4), m.group(5)));
        }
        f.put("pegawai", pegawai);
        f.put("maksud_perjalanan", text(t, "Maksud Perjalanan\\s*:\\s*(.+?)\\s+Alat Angkut"));
        f.put("alat_angkut", text(t, "Alat Angkut\\s*:\\s*(.+?)\\s+Tempat Kedudukan"));
        f.put("tempat_kedudukan", text(t, "Tempat Kedudukan\\s*:\\s*(.+?)\\s+Tempat Tujuan"));
        f.put("tempat_tujuan", text(t, "Tempat Tujuan[^A-Za-z]*(.+?)\\s+Tanggal Berangkat"));
        f.put("tanggal_berangkat", dateAfter(t, "Tanggal Berangkat\\s*:"));
        f.put("tanggal_harus_kembali", dateAfter(t, "Tanggal Kembali\\s*:"));
        Matcher lama = Pattern.compile("Lama Perjalanan\\s*:\\s*(\\d+)").matcher(t);
        f.put("lama_perjalanan_hari", lama.find()
                ? leaf(Integer.parseInt(lama.group(1)), lama.group(1), null) : empty());
        f.put("pengikut", pengikut(t));
        Map<String, Object> anggaran = new LinkedHashMap<>();
        anggaran.put("instansi", text(t, "Pembebanan Anggaran\\s*:\\s*(.+?)\\s+Akun"));
        Matcher akun = Pattern.compile("Akun\\s*:?\\s*(\\d{4,})").matcher(t);
        anggaran.put("akun", akun.find() ? leaf(akun.group(1), akun.group(1), null) : empty());
        f.put("pembebanan_anggaran", anggaran);
        f.put("keterangan", text(t, "Keterangan\\s*:\\s*(.+?)\\s*(?:Pejabat Pembuat Komitmen|$)"));
        return f;
    }

    /** Pengikut: nama dan NIP. Kolom hubungan belum terbaca dari formulir sampel. */
    private static List<Map<String, Object>> pengikut(String t) {
        List<Map<String, Object>> out = new ArrayList<>();
        Matcher m = Pattern.compile("Pengikut\\s*:\\s*\\d+\\.\\s*(.+?)\\s+NIP\\s*:?\\s*" + NIP_RE).matcher(t);
        if (m.find()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("nama", leaf(clean(m.group(1)), clean(m.group(1)), null));
            p.put("nip", nip(m.group(2)));
            p.put("hubungan", empty());
            out.add(p);
        }
        return out;
    }

    // ---------------------------------------------------------------- Struk / kwitansi

    static Map<String, Object> receipt(String t) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("tanggal", anyDate(t));
        Matcher pen = Pattern.compile("(?i)^(.+?)\\s+Jl\\.?").matcher(t);
        f.put("penerbit", pen.find() ? leaf(clean(pen.group(1)), clean(pen.group(1)), null) : empty());
        // Hanya label yang jelas sebagai nomor transaksi. "No. 12" pada alamat tidak dipakai.
        f.put("nomor_referensi", text(t, "(?i)(?:No\\.?\\s*Trans(?:aksi)?|Nomor\\s*Transaksi|Referensi)\\s*:?\\s*([0-9A-Za-z\\-/]+)"));
        Matcher total = Pattern.compile("(?i)total\\s*(?:harga)?\\s*:?\\s*(?:Rp\\.?)?\\s*([0-9][0-9.,]*)").matcher(t);
        f.put("nominal_total", total.find() ? money(total.group(1)) : empty());
        f.put("kategori", kategori(t));
        return f;
    }

    static Map<String, Object> kategori(String t) {
        String u = t.toUpperCase(Locale.ROOT);
        String value;
        if (u.matches(".*\\b(BBM|BENSIN|SOLAR|PERTAMAX|PERTALITE|PERTADEX|DEXLITE|DEX)\\b.*")) value = "BBM";
        else if (u.matches(".*\\bPARKIR\\b.*")) value = "PARKIR";
        else if (u.matches(".*\\b(MAKAN|KOPI|ROTI|RESTORAN|WARUNG|MINUM|NASI)\\b.*")) value = "MAKAN";
        else if (u.matches(".*\\b(TAKSI|GRAB|GOJEK|TIKET|TRANSPORT|KERETA|PESAWAT)\\b.*")) value = "TRANSPORT";
        else if (u.matches(".*\\b(HOTEL|PENGINAPAN|INAP)\\b.*")) value = "PENGINAPAN";
        else if (u.matches(".*\\b(ATK|KERTAS|PULPEN|TINTA|MAP)\\b.*")) value = "ATK";
        else value = "LAINNYA";
        // Nilai di luar daftar tidak ditebak: LAINNYA dengan confidence rendah.
        Map<String, Object> l = leaf(value, value, null);
        l.put("confidence", value.equals("LAINNYA") ? 0.5 : CONF_RULE);
        return l;
    }

    // ---------------------------------------------------------------- KTP (OQ-8, 2026-10-15)

    /**
     * Field KTP (extraction-schema.md bagian 4, IDENTITY_CARD). Nilai ditampilkan kepada pemilik, dan
     * disimpan terenkripsi oleh API. NIK harus 16 digit; bila tidak, NIK dibiarkan kosong.
     */
    public static Map<String, Object> extractIdentity(String rawText) {
        return extractIdentity(rawText, null);
    }

    /**
     * `engineNik`: NIK terpilih dari mode identitas ocr-engine (area tetap kartu). Dipakai hanya bila NIK dari
     * teks tidak ditemukan, dan hanya bila strukturnya valid. Ditandai "derived" sehingga tinjauan memeriksanya.
     */
    public static Map<String, Object> extractIdentity(String rawText, String engineNik) {
        return extractIdentity(rawText, engineNik, null);
    }

    /**
     * `vision`: hasil pendamping vision (Qwen2.5-VL) untuk KTP. Hanya mengisi field yang kosong dari teks, atau
     * mengganti NIK bila NIK dari teks tidak valid dan NIK pendamping valid. Field itu ditandai source "llm".
     */
    public static Map<String, Object> extractIdentity(String rawText, String engineNik, JsonNode vision) {
        Map<String, Object> f = extractIdentityTeks(rawText, engineNik);
        mergeVision(f, vision);
        return f;
    }

    static Map<String, Object> extractIdentityTeks(String rawText, String engineNik) {
        String t = collapse(rawText);
        Map<String, Object> f = new LinkedHashMap<>();
        Map<String, Object> nikText = nikFromText(t);
        boolean engineOk = engineNik != null && engineNik.matches("\\d{16}") && validNik(engineNik);
        f.put("nik", nikText.get("value") == null && engineOk ? leaf(engineNik, engineNik, "derived") : nikText);
        Map<String, Object> nama = text(t, "(?i)Nama\\s*:?\\s*(.+?)\\s+Tempat");
        f.put("nama", nama.get("value") != null ? nama : namaFromLines(rawText));
        Matcher ttl = Pattern.compile(
                "(?i)Tempat\\S*\\s*Lahir\\s*:?\\s*[^A-Za-z]{0,3}([A-Za-z .]+?)\\s*,\\s*(\\d{2})[-/. ](\\d{2})[-/. ](\\d{4})").matcher(t);
        if (ttl.find()) {
            String tempat = clean(ttl.group(1));
            f.put("tempat_lahir", leaf(tempat, tempat, null));
            String iso = ttl.group(4) + "-" + ttl.group(3) + "-" + ttl.group(2);
            f.put("tanggal_lahir", leaf(iso, ttl.group(2) + "-" + ttl.group(3) + "-" + ttl.group(4), "derived"));
        } else {
            f.put("tempat_lahir", empty());
            f.put("tanggal_lahir", empty());
        }
        f.put("jenis_kelamin", text(t, "(?i)Jenis\\s*kelamin\\s*:?\\s*(LAKI-LAKI|PEREMPUAN)"));
        // Label KTP sering terbaca rusak dari OCR ("Ke/Desa", hilangnya "RT/RW"). Pola menerima variasi itu.
        f.put("alamat", text(t, "(?i)Alamat\\s*:?\\s*(.+?)\\s+(?:RT\\S{0,2}\\s*R\\S?W|\\d{3}/\\d{3}|Kel\\S{0,2}\\s*/|Ke\\S{0,2}\\s*/)"));
        petakanAlamat(f);
        f.put("rt_rw", text(t, "(?i)RT\\S{0,2}\\s*R\\S?W\\s*:?\\s*(\\d{3}/\\d{3})"));
        f.put("kelurahan_desa", text(t, "(?i)(?:Kel|Ke)\\S{0,2}\\s*/\\s*Desa\\s*:?\\s*(.+?)\\s+Kecamatan"));
        f.put("kecamatan", text(t, "(?i)Kecamatan\\s*:?\\s*(.+?)\\s+\\S{0,2}Agama"));
        f.put("agama", text(t, "(?i)Agama\\s*:?\\s*([A-Z]+)"));
        f.put("status_perkawinan", text(t, "(?i)Status\\s*Perkawinan\\s*:?\\s*(BELUM KAWIN|KAWIN|CERAI HIDUP|CERAI MATI)"));
        f.put("pekerjaan", text(t, "(?i)Pekerjaan\\s*:?\\s*(.+?)\\s+Kewarganegaraan"));
        f.put("kewarganegaraan", text(t, "(?i)Kewarganegaraan\\s*:?\\s*(WNI|WNA)"));
        Matcher berlaku = Pattern.compile("(?i)(?:Berlaku\\s*)?Hingga\\s*:?\\s*(SEUMUR HIDUP|\\d{2}-\\d{2}-\\d{4})").matcher(t);
        f.put("berlaku_hingga", berlaku.find() ? leaf(berlaku.group(1), berlaku.group(1), null) : empty());
        // Nilai pilihan tetap (LAKI-LAKI, AGAMA) ditulis huruf besar agar konsisten, apa pun cara OCR membacanya.
        cadanganIdentitas(f, rawText);
        petakanNilai(f, "agama", Map.of("CHRISTIAN", "KRISTEN", "CATHOLIC", "KATOLIK", "BUDDHIST", "BUDDHA", "CONFUCIAN", "KONGHUCU"));
        for (String k : List.of("jenis_kelamin", "status_perkawinan", "agama", "kewarganegaraan")) upperValue(f, k);
        return f;
    }

    /** Field kunci untuk KTP dan dokumen lain. Pendamping vision dipanggil bila salah satunya masih kosong. */
    // Hanya field inti. Pendamping dipanggil bila salah satunya kosong, supaya tidak berjalan untuk setiap dokumen.
    static final Set<String> KUNCI_KTP = Set.of("nik", "nama");
    static final Set<String> KUNCI_DOKUMEN = Set.of("nomor_struk", "nomor_surat", "nomor_spd", "liter", "nominal_total");

    /**
     * Apakah hasil teks masih kurang dan perlu pendamping vision. Pendamping lambat di CPU, jadi hanya dipanggil bila
     * ada kunci yang kosong. NIK yang tidak lolos validasi juga dianggap kurang.
     */
    public static boolean perluPendamping(String docType, Map<String, Object> fields) {
        boolean ktp = DOC_IDENTITY.equals(docType);
        for (String k : ktp ? KUNCI_KTP : KUNCI_DOKUMEN) {
            if (fields.containsKey(k) && kosong(fields, k)) return true;
        }
        if (ktp) {
            Object nik = nilai(fields, "nik");
            return nik == null || !validNik(String.valueOf(nik).replace(" ", ""));
        }
        return false;
    }

    /**
     * Menggabungkan hasil pendamping vision. Nilainya diubah menjadi baris bergaya KTP lalu dibaca dengan aturan
     * yang sama dengan teks, supaya normalisasi (jenis kelamin, agama, tanggal) tidak ditulis dua kali.
     */
    public static void mergeVision(Map<String, Object> f, JsonNode vision) {
        if (vision == null || !vision.isObject()) return;
        StringBuilder baris = new StringBuilder();
        tambahBaris(baris, "NIK", vision.path("nik"));
        tambahBaris(baris, "Nama", vision.path("nama"));
        String tempat = vision.path("tempat_lahir").asText("").trim();
        String tanggal = vision.path("tanggal_lahir").asText("").trim();
        if (!tempat.isEmpty() || !tanggal.isEmpty()) {
            baris.append("Tempat/Tgl Lahir : ").append(tempat).append(", ").append(tanggal).append('\n');
        }
        tambahBaris(baris, "Jenis Kelamin", vision.path("jenis_kelamin"));
        tambahBaris(baris, "Alamat", vision.path("alamat"));
        tambahBaris(baris, "RT/RW", vision.path("rt_rw"));
        tambahBaris(baris, "Kel/Desa", vision.path("kelurahan_desa"));
        tambahBaris(baris, "Kecamatan", vision.path("kecamatan"));
        tambahBaris(baris, "Agama", vision.path("agama"));
        tambahBaris(baris, "Status Perkawinan", vision.path("status_perkawinan"));
        tambahBaris(baris, "Pekerjaan", vision.path("pekerjaan"));
        tambahBaris(baris, "Kewarganegaraan", vision.path("kewarganegaraan"));
        tambahBaris(baris, "Berlaku Hingga", vision.path("berlaku_hingga"));

        Map<String, Object> v = extractIdentityTeks(baris.toString(), null);

        Object nikV = nilai(v, "nik");
        if (nikV != null && validNik(String.valueOf(nikV).replace(" ", ""))) {
            Object nikF = nilai(f, "nik");
            if (nikF == null || !validNik(String.valueOf(nikF).replace(" ", ""))) {
                f.put("nik", tandaLlm(v, "nik"));
            }
        }
        for (String k : List.copyOf(f.keySet())) {
            if (kosong(f, k) && !kosong(v, k)) f.put(k, tandaLlm(v, k));
        }
        // Alamat sering salah baca dari teks (huruf dan urutan baris). Pembacaan pendamping dipakai bila berbeda.
        if (!kosong(f, "alamat") && !kosong(v, "alamat")
                && !String.valueOf(nilai(f, "alamat")).equalsIgnoreCase(String.valueOf(nilai(v, "alamat")))) {
            f.put("alamat", tandaLlm(v, "alamat"));
        }
    }

    private static void tambahBaris(StringBuilder sb, String label, JsonNode nilai) {
        String teks = nilai.asText("").trim();
        if (teks.isEmpty() || "null".equalsIgnoreCase(teks)) return;
        sb.append(label).append(" : ").append(teks).append('\n');
    }

    private static Object nilai(Map<String, Object> f, String kunci) {
        return f.get(kunci) instanceof Map<?, ?> m ? m.get("value") : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> tandaLlm(Map<String, Object> sumber, String kunci) {
        Map<String, Object> hasil = (Map<String, Object>) sumber.get(kunci);
        hasil.put("source", "llm");
        return hasil;
    }

    /**
     * Cadangan untuk KTP hasil foto: label terbaca rusak, nilai ada di baris lain, atau kartu berbahasa Inggris.
     * Hanya mengisi field yang masih kosong. Hasilnya ditandai "derived" agar diperiksa di tinjauan.
     */
    static void cadanganIdentitas(Map<String, Object> f, String raw) {
        String t = collapse(raw);
        if (kosong(f, "nama")) {
            String nama = nilaiBaris(raw, "Nama");
            if (nama != null && nama.matches("[A-Z][A-Z .'-]{2,}")) f.put("nama", leaf(nama, nama, "derived"));
        }
        if (kosong(f, "tempat_lahir") || kosong(f, "tanggal_lahir")) {
            Matcher m = Pattern.compile(
                    "(?i)Lahir\\s*:?\\s*([A-Za-z]{3,}(?:\\s[A-Za-z]{3,})?)\\s*,\\s*(\\d{2})[-/. ](\\d{2})[-/. ](\\d{4})").matcher(t);
            if (m.find()) {
                String tempat = clean(m.group(1)).toUpperCase(Locale.ROOT);
                if (kosong(f, "tempat_lahir")) f.put("tempat_lahir", leaf(tempat, tempat, "derived"));
                if (kosong(f, "tanggal_lahir")) {
                    String iso = m.group(4) + "-" + m.group(3) + "-" + m.group(2);
                    f.put("tanggal_lahir", leaf(iso, m.group(2) + "-" + m.group(3) + "-" + m.group(4), "derived"));
                }
            }
        }
        if (kosong(f, "jenis_kelamin")) {
            Matcher m = Pattern.compile("(?i)\\b(LAKI-LAKI|PEREMPUAN|MALE|FEMALE)\\b").matcher(t);
            if (m.find()) {
                String v = m.group(1).toUpperCase(Locale.ROOT);
                String baku = Map.of("MALE", "LAKI-LAKI", "FEMALE", "PEREMPUAN").getOrDefault(v, v);
                f.put("jenis_kelamin", leaf(baku, m.group(1), "derived"));
            }
        }
        if (kosong(f, "agama")) {
            Matcher m = Pattern.compile("(?i)\\b(ISLAM|KRISTEN|KATOLIK|HINDU|BUDDHA|KONGHUCU|CHRISTIAN|CATHOLIC|BUDDHIST|CONFUCIAN)\\b").matcher(t);
            if (m.find()) {
                String v = m.group(1).toUpperCase(Locale.ROOT);
                String baku = Map.of("CHRISTIAN", "KRISTEN", "CATHOLIC", "KATOLIK", "BUDDHIST", "BUDDHA", "CONFUCIAN", "KONGHUCU")
                        .getOrDefault(v, v);
                f.put("agama", leaf(baku, m.group(1), "derived"));
            }
        }
        if (kosong(f, "status_perkawinan")) {
            Matcher m = Pattern.compile("(?i)\\b(MARRIED|UNMARRIED|DIVORCED|BELUM KAWIN|CERAI HIDUP|CERAI MATI|KAWIN)\\b").matcher(t);
            if (m.find()) {
                String v = m.group(1).toUpperCase(Locale.ROOT);
                String baku = Map.of("MARRIED", "KAWIN", "UNMARRIED", "BELUM KAWIN", "DIVORCED", "CERAI HIDUP").getOrDefault(v, v);
                f.put("status_perkawinan", leaf(baku, m.group(1), "derived"));
            }
        }
        if (kosong(f, "pekerjaan")) {
            String kerja = nilaiBaris(raw, "Pekerjaan");
            if (kerja != null && kerja.matches("[A-Z][A-Z .'/-]{2,}")) f.put("pekerjaan", leaf(kerja, kerja, "derived"));
        }
        if (kosong(f, "kewarganegaraan")) {
            if (t.matches("(?s).*\\bWNI\\b.*")) {
                f.put("kewarganegaraan", leaf("WNI", "WNI", "derived"));
            } else {
                // Label sering terbaca "Kewart" atau "Kewarganeg", jadi cukup awalan "Kew".
                String warga = nilaiBaris(raw, "Kew\\S*");
                if (warga != null && warga.matches("[A-Z][A-Z .'-]{2,}")) f.put("kewarganegaraan", leaf("WNA", warga, "derived"));
            }
        }
        if (kosong(f, "kecamatan")) {
            String kec = nilaiBaris(raw, "Kecamatan");
            if (kec != null && kec.matches("[A-Z][A-Z .'-]{2,}")) f.put("kecamatan", leaf(kec, kec, "derived"));
        }
        if (kosong(f, "kelurahan_desa")) {
            String kel = nilaiBaris(raw, "Ke\\S{0,2}\\s*/\\s*Desa");
            if (kel != null && kel.matches("[A-Z][A-Z .'-]{2,}")) f.put("kelurahan_desa", leaf(kel, kel, "derived"));
        }
        if (kosong(f, "alamat")) {
            String alamat = alamatBaris(raw);
            if (alamat != null) f.put("alamat", leaf(alamat, alamat, "derived"));
        }
    }

    /** Di kartu, "NO. 40" ditulis di bawah nama jalan, tetapi OCR sering membacanya lebih dulu. Dipindah ke akhir. */
    static String pindahkanNomorRumah(String alamat) {
        if (alamat == null) return null;
        Matcher nomor = Pattern.compile("(?i)\\bNO\\.?\\s*\\d+[A-Z]?\\b").matcher(alamat);
        if (!nomor.find()) return alamat;
        String no = nomor.group();
        return clean(alamat.substring(0, nomor.start()) + " " + alamat.substring(nomor.end()) + " " + no);
    }

    static void petakanAlamat(Map<String, Object> f) {
        if (f.get("alamat") instanceof Map<?, ?> m && m.get("value") instanceof String v) {
            ((Map<String, Object>) m).put("value", pindahkanNomorRumah(v));
        }
    }

    /** Nilai bahasa Inggris pada kartu dua bahasa diganti istilah baku Indonesia. Nilai lain dibiarkan. */
    static void petakanNilai(Map<String, Object> f, String kunci, Map<String, String> peta) {
        if (f.get(kunci) instanceof Map<?, ?> m && m.get("value") instanceof String v) {
            String baku = peta.get(v.toUpperCase(Locale.ROOT));
            if (baku != null) ((Map<String, Object>) m).put("value", baku);
        }
    }

    /** Leaf kosong atau tanpa nilai. Dipakai pengayaan untuk membedakan field yang diisi dan yang diganti. */
    public static boolean kosongLeaf(Object leaf) {
        return !(leaf instanceof Map<?, ?> m) || m.get("value") == null;
    }

    static boolean kosong(Map<String, Object> f, String kunci) {
        return !(f.get(kunci) instanceof Map<?, ?> m) || m.get("value") == null;
    }

    /** Nilai dari baris yang memuat label: sisa baris itu, atau baris berikutnya bila sisanya kosong. */
    static String nilaiBaris(String raw, String label) {
        if (raw == null) return null;
        List<String> baris = raw.lines().map(String::trim).filter(s -> !s.isEmpty()).toList();
        Pattern p = Pattern.compile("(?i)^" + label + "\\s*:?\\s*(?<sisa>.*)$");
        for (int i = 0; i < baris.size(); i++) {
            Matcher m = p.matcher(baris.get(i));
            if (!m.find()) continue;
            String sisa = clean(m.group("sisa"));
            if (sisa.isEmpty() && i + 1 < baris.size()) sisa = clean(baris.get(i + 1));
            return sisa.isEmpty() ? null : sisa;
        }
        return null;
    }

    /** Alamat bisa terbagi beberapa baris. Dikumpulkan sampai baris RT/RW atau Kelurahan/Desa. */
    static String alamatBaris(String raw) {
        if (raw == null) return null;
        StringBuilder sb = new StringBuilder();
        boolean mulai = false;
        for (String baris : raw.lines().map(String::trim).filter(s -> !s.isEmpty()).toList()) {
            if (!mulai) {
                if (Pattern.compile("(?i)^Alamat").matcher(baris).find()) {
                    mulai = true;
                    String sisa = clean(baris.replaceFirst("(?i)^Alamat\\s*:?", ""));
                    if (!sisa.isEmpty()) sb.append(sisa).append(' ');
                }
                continue;
            }
            if (Pattern.compile("(?i)^RT\\b|^RW\\b|^Ke\\S{0,2}\\s*/|\\d{3}/\\d{3}").matcher(baris).find()) break;
            String v = clean(baris);
            if (!v.isEmpty()) sb.append(v).append(' ');
        }
        String hasil = clean(sb.toString());
        hasil = pindahkanNomorRumah(hasil);
        return hasil.isEmpty() ? null : hasil;
    }

    /**
     * NIK: 16 digit dengan struktur valid. Label "NIK" sering terbaca rusak, jadi kandidat dicari dari deretan
     * digit (spasi OCR di tengah digit diabaikan). Kandidat yang lolos validasi diutamakan. Bila tidak ada,
     * kandidat 16 digit pertama dipakai sebagai "derived" (perlu dicek), bukan dikosongkan.
     */
    static Map<String, Object> nikFromText(String t) {
        Matcher m = NIK_CANDIDATE.matcher(t);
        String fallback = null;
        String fallbackRaw = null;
        while (m.find()) {
            String raw = m.group().trim();
            String digits = raw.replace(" ", "");
            if (validNik(digits)) {
                return leaf(digits, raw, digits.equals(raw) ? null : "derived");
            }
            if (fallback == null) {
                fallback = digits;
                fallbackRaw = raw;
            }
        }
        // Tidak ada yang lolos validasi: tetap ditampilkan sebagai nilai yang bisa diedit, ditandai perlu dicek.
        // Pengguna memutuskan; sistem tidak membuang bacaan OCR begitu saja.
        if (fallback != null) {
            return leaf(fallback, fallbackRaw, "derived");
        }
        return empty();
    }

    /** Kode provinsi 11-94, tanggal 01-31 (laki-laki) atau 41-71 (perempuan, +40), dan bulan 01-12. */
    static boolean validNik(String d) {
        if (d.length() != 16) return false;
        int prov = Integer.parseInt(d.substring(0, 2));
        int day = Integer.parseInt(d.substring(6, 8));
        int month = Integer.parseInt(d.substring(8, 10));
        boolean dayOk = (day >= 1 && day <= 31) || (day >= 41 && day <= 71);
        return prov >= 11 && prov <= 94 && dayOk && month >= 1 && month <= 12;
    }

    /**
     * Nama tanpa label "Nama": baris tepat setelah baris yang memuat NIK. Hanya dipakai bila label tidak terbaca
     * dan teks punya baris. Nilai harus huruf kapital; bila tidak, tidak ditebak. Ditandai "derived" agar muncul
     * sebagai perlu dicek di tinjauan.
     */
    static Map<String, Object> namaFromLines(String raw) {
        if (raw == null) return empty();
        List<String> lines = raw.lines().map(String::trim).filter(s -> !s.isEmpty()).toList();
        for (int i = 0; i < lines.size() - 1; i++) {
            if (!NIK_CANDIDATE.matcher(lines.get(i)).find()) continue;
            String next = lines.get(i + 1).replaceFirst("(?i)^nama\\s*:?\\s*", "");
            if (!next.matches("[A-Z][A-Z .'-]{2,}")) return empty();
            String v = clean(next);
            return leaf(v, v, "derived");
        }
        return empty();
    }

    // ---------------------------------------------------------------- Pembantu

    static void upperValue(Map<String, Object> f, String key) {
        Object leaf = f.get(key);
        if (leaf instanceof Map<?, ?> m && m.get("value") instanceof String v) {
            ((Map<String, Object>) m).put("value", v.toUpperCase(Locale.ROOT));
        }
    }

    static String collapse(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }

    /** Membuang artefak OCR: garis tegak "|" dan spasi berlebih di tepi nilai. */
    static String clean(String s) {
        return s.replaceAll("[\\s|]*\\|[\\s|]*", " ").replaceAll("\\s+", " ").replaceAll("^[\\s|,:]+|[\\s|,:]+$", "");
    }

    static Map<String, Object> orang(String nama, String nip, String pangkat, String jabatan, String tingkat) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("nama", leaf(clean(nama), clean(nama), null));
        p.put("nip", nip(nip));
        p.put("pangkat_golongan", pangkat(clean(pangkat)));
        p.put("jabatan", leaf(clean(jabatan), clean(jabatan), null));
        if (tingkat != null) p.put("tingkat_biaya_perjalanan", leaf(clean(tingkat), clean(tingkat), null));
        return p;
    }

    /** Nilai teks dari pola (grup 1). Null bila tidak cocok. */
    static Map<String, Object> text(String t, String regex) {
        Matcher m = Pattern.compile(regex).matcher(t);
        if (!m.find()) return empty();
        String v = clean(m.group(1));
        return v.isEmpty() ? empty() : leaf(v, v, null);
    }

    /** Nomor surat. Kesalahan baca romawi pada bagian bulan diperbaiki (mis. LX menjadi IX) dan dicatat. */
    static Map<String, Object> nomorSurat(String t) {
        Matcher m = Pattern.compile("Nomor\\s*:?\\s*(" + NOMOR_RE + ")").matcher(t);
        if (!m.find()) return empty();
        String raw = m.group(1).replaceAll("\\s*/\\s*", "/");
        String[] parts = raw.split("/");
        boolean changed = false;
        for (int i = 0; i < parts.length; i++) {
            String romawi = parts[i].replace('L', 'I').replace('1', 'I');
            if (!romawi.equals(parts[i]) && ROMAWI.contains(romawi)) {
                parts[i] = romawi;
                changed = true;
            }
        }
        return changed
                ? leaf(String.join("/", parts), raw, "confusion_map")
                : leaf(raw, raw, null);
    }

    /** Pangkat/golongan. OCR sering menambah huruf "l" di dalam romawi: "IlII/a" menjadi "III/a". */
    static Map<String, Object> pangkat(String raw) {
        Matcher m = Pattern.compile("\\(([^()/]+)/([a-z])\\)").matcher(raw);
        if (!m.find()) return leaf(raw, raw, null);
        // "l" di OCR bisa berarti "I" (Il -> II) atau huruf ekstra (IlII -> III). Yang valid sebagai romawi dipakai.
        String asI = m.group(1).replace('l', 'I').replace('|', 'I').replace('1', 'I');
        String dropL = m.group(1).replace("l", "").replace("|", "").replace('1', 'I');
        String romawi = ROMAWI.contains(asI) ? asI : dropL;
        if (!ROMAWI.contains(romawi)) return leaf(raw, raw, null);
        String fixed = raw.substring(0, m.start()) + "(" + romawi + "/" + m.group(2) + ")" + raw.substring(m.end());
        return fixed.equals(raw) ? leaf(raw, raw, null) : leaf(fixed, raw, "confusion_map");
    }

    /** NIP: hanya digit, harus 18 digit. Spasi OCR dihapus; string, bukan angka (extraction-schema bagian 3). */
    static Map<String, Object> nip(String raw) {
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() != 18) {
            return leaf(null, raw.trim(), null);
        }
        return digits.equals(raw.trim()) ? leaf(digits, raw, null) : leaf(digits, raw, "derived");
    }

    static Map<String, Object> dateAfter(String t, String labelRegex) {
        Matcher m = Pattern.compile((labelRegex.isEmpty() ? "" : labelRegex + "\\s*") + "(" + DATE_RE + ")").matcher(t);
        return m.find() ? dateLeaf(m.group(1)) : empty();
    }

    static Map<String, Object> dateLeaf(String raw) {
        String iso = isoDate(raw);
        if (iso == null) return leaf(null, raw, null);
        return leaf(iso, raw, "derived");
    }

    /** "15 September 2026" menjadi "2026-09-15". Null bila hari atau bulan tidak terbaca. */
    static String isoDate(String raw) {
        String[] p = raw.replace("?", "").trim().split("\\s+");
        if (p.length != 3) return null;
        Integer month = BULAN.get(p[1].toLowerCase(Locale.ROOT));
        if (month == null) return null;
        return String.format("%s-%02d-%02d", p[2], month, Integer.parseInt(p[0]));
    }

    static Map<String, Object> money(String raw) {
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return empty();
        long v = Long.parseLong(digits);
        return leaf(v, raw.trim(), raw.trim().equals(String.valueOf(v)) ? null : "derived");
    }

    /** Field tanpa nilai: value null, confidence 0. */
    static Map<String, Object> empty() {
        return leaf(null, null, null);
    }

    /**
     * Daun field. Confidence CONF_RULE bila nilai ada, CONF_CORRECTED bila dikoreksi mesin.
     * `correction` null bila tidak ada perbaikan. raw_text wajib bila ada koreksi (spek bagian 1).
     */
    static Map<String, Object> leaf(Object value, String raw, String correction) {
        boolean present = value != null;
        Map<String, Object> l = new LinkedHashMap<>();
        l.put("value", value);
        l.put("confidence", present ? (correction == null ? CONF_RULE : CONF_CORRECTED) : 0.0);
        l.put("source", "rule");
        boolean differs = raw != null && !String.valueOf(value).equals(raw);
        l.put("raw_text", present && (correction != null || differs) ? raw : null);
        l.put("correction", present ? correction : null);
        l.put("page", 1);
        l.put("bbox", null);
        return l;
    }
}
