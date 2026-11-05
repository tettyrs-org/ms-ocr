package org.tettyrs.msocr.jobs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.stream.StreamCommands;
import io.quarkus.redis.datasource.stream.StreamMessage;
import io.quarkus.redis.datasource.stream.XGroupCreateArgs;
import io.quarkus.redis.datasource.stream.XReadGroupArgs;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.tettyrs.msocr.extract.RuleExtractor;
import org.tettyrs.msocr.ocr.OcrEngineHttp;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Consumer antrian job dokumen dari API (api-spec bagian 8).
 *
 * Pengiriman at-least-once: job bisa datang lebih dari sekali, sehingga callback ke API dibuat idempoten
 * di sisi API. Job di-ACK hanya setelah API menerima hasil (atau kegagalan permanen dari ocr-engine).
 * Kegagalan sementara (jaringan, API tidak siap) tidak di-ACK dan akan dicoba ulang.
 */
@ApplicationScoped
public class JobConsumer {

    static final String STREAM = "ocr:jobs";
    static final String GROUP = "ms-ocr";
    // Jeda sebelum mencoba ulang job pending agar kegagalan sementara tidak memutar loop tanpa henti.
    static final long RETRY_DELAY_MS = 2000;
    // Batas percobaan untuk kegagalan sementara, lalu job pindah ke dead letter.
    static final int MAX_ATTEMPTS = 5;
    static final String DEAD = "ocr:jobs:dead";
    static final String ATTEMPTS_PREFIX = "ocr:jobs:attempts:";
    static final java.time.Duration ATTEMPTS_TTL = Duration.ofDays(7);

    private static final Logger LOG = Logger.getLogger(JobConsumer.class);

    @Inject
    RedisDataSource redis;

    @Inject
    OcrEngineHttp engine;

    @Inject
    ApiInternalClient api;

    @Inject
    ObjectMapper mapper;

    @ConfigProperty(name = "ocr.consumer-name", defaultValue = "ms-ocr-1")
    String consumerName;

    private volatile boolean running;
    private Thread worker;

    void onStart(@Observes StartupEvent ev) {
        ensureGroup();
        running = true;
        worker = new Thread(this::loop, "ocr-job-consumer");
        worker.setDaemon(true);
        worker.start();
        LOG.infof("consumer job dimulai sebagai %s", consumerName);
    }

    void onStop(@Observes ShutdownEvent ev) {
        running = false;
    }

    private void ensureGroup() {
        try {
            // MKSTREAM: stream dibuat bila API belum pernah mengirim job (tanpa ini XREADGROUP gagal NOGROUP).
            commands().xgroupCreate(STREAM, GROUP, "0", new XGroupCreateArgs().mkstream());
        } catch (RuntimeException e) {
            // Grup sudah ada (BUSYGROUP) atau Redis belum siap; loop akan mencoba lagi bila masih gagal.
            LOG.debugf("grup %s: %s", GROUP, e.getMessage());
        }
    }

    private StreamCommands<String, String, String> commands() {
        return redis.stream(String.class);
    }

    private void loop() {
        while (running) {
            try {
                // Job yang belum di-ACK (gagal sementara) tertahan di pending list. Dibaca ulang lebih dulu
                // (id "0"), lalu job baru (">"). Tanpa ini, job gagal tidak pernah dicoba lagi.
                List<StreamMessage<String, String, String>> pending = commands().xreadgroup(
                        GROUP, consumerName, Map.of(STREAM, "0"), new XReadGroupArgs().count(10));
                if (pending != null && !pending.isEmpty()) {
                    for (StreamMessage<String, String, String> msg : pending) {
                        handle(msg);
                    }
                    sleep(RETRY_DELAY_MS);
                }

                List<StreamMessage<String, String, String>> messages = commands().xreadgroup(
                        GROUP, consumerName, Map.of(STREAM, ">"),
                        new XReadGroupArgs().count(1).block(Duration.ofSeconds(5)));
                if (messages == null) continue;
                for (StreamMessage<String, String, String> msg : messages) {
                    handle(msg);
                }
            } catch (RuntimeException e) {
                LOG.warnf("gagal membaca antrian: %s", e.getMessage());
                sleep(2000);
            }
        }
    }

    /** Pemulihan traceparent dari payload job. Konteks dari api dilanjutkan, bukan dimulai baru. */
    private static final TextMapGetter<Map<String, String>> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier == null ? null : carrier.get(key);
        }
    };
    private static final Tracer TRACER = GlobalOpenTelemetry.getTracer("ms-ocr");

    /** Satu job dalam satu span yang merupakan anak dari span api. Isi dokumen tidak masuk ke span. */
    void handle(StreamMessage<String, String, String> msg) {
        Context induk = GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
                .extract(Context.root(), msg.payload(), GETTER);
        Span span = TRACER.spanBuilder("process job").setParent(induk)
                .setAttribute("messaging.message.id", msg.id()).startSpan();
        try (Scope ignored = span.makeCurrent()) {
            prosesJob(msg);
        } catch (RuntimeException e) {
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    void prosesJob(StreamMessage<String, String, String> msg) {
        String documentId = msg.payload().get("document_id");
        String category = msg.payload().getOrDefault("category", "other");
        if (documentId == null || !documentId.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            // Bukan UUID: tidak akan pernah berhasil, jadi dibuang dan di-ACK (tidak macetkan antrian).
            LOG.warnf("job %s dengan document_id tidak valid dibuang", msg.id());
            ack(msg.id());
            return;
        }
        try {
            // Waktu tiap tahap dicatat (ms), supaya tahap yang lambat terlihat di log.
            long t0 = System.nanoTime();
            byte[] file = api.fetchFile(documentId);
            long tUnduh = ms(t0);
            String effective = category;
            OcrEngineHttp.EngineText result;
            long tTeks = 0;
            long tIdentitas = 0;
            if ("auto".equals(category)) {
                // Jenis belum diketahui: baca teks dulu, tentukan kategori dari isinya. KTP dibaca ulang dengan mode
                // identitas (voting NIK). Dokumen lain tidak memakai mode itu, supaya tidak menambah waktu proses.
                long s = System.nanoTime();
                result = engine.extractText(file, OcrEngineHttp.MODE_TEXT);
                tTeks = ms(s);
                effective = RuleExtractor.kategoriAuto(result.text());
                if ("identity".equals(effective)) {
                    s = System.nanoTime();
                    result = engine.extractText(file, OcrEngineHttp.MODE_IDENTITY);
                    tIdentitas = ms(s);
                }
                LOG.infof("dokumen %s dikenali sebagai kategori %s", documentId, effective);
            } else {
                long s = System.nanoTime();
                result = engine.extractText(file, modeFor(category));
                tTeks = ms(s);
            }
            // Hasil teks dikirim dulu supaya dokumen bisa langsung ditinjau. Pendamping vision menyusul sebagai pengayaan.
            ObjectNode body = extracted(effective, result.text(), result.identityNik(), kind -> null);
            long tKirim;
            long tPendamping;
            if ("identity".equals(effective)) {
                // KTP: pendamping dijalankan dulu, lalu hasil akhir dikirim sekali. Field tidak berubah setelah tampil.
                long s = System.nanoTime();
                mergePendampingSebelumKirim(documentId, body, file);
                tPendamping = ms(s);
                s = System.nanoTime();
                api.sendExtraction(documentId, body);
                tKirim = ms(s);
            } else {
                // Struk dan dokumen lain: pendamping dijalankan dulu, lalu hasil akhir dikirim sekali. Halaman tinjau
                // tidak menunggu pengayaan terpisah.
                long s = System.nanoTime();
                mergePendampingDokumenSebelumKirim(documentId, body, file);
                tPendamping = ms(s);
                s = System.nanoTime();
                api.sendExtraction(documentId, body);
                tKirim = ms(s);
            }
            LOG.infof("dokumen %s selesai: unduh %d ms, teks %d ms, identitas %d ms, kirim %d ms, pendamping %d ms, total %d ms",
                    documentId, tUnduh, tTeks, tIdentitas, tKirim, tPendamping, ms(t0));
            ack(msg.id());
        } catch (OcrEngineHttp.OcrException e) {
            // Kegagalan permanen dari ocr-engine: laporkan dan ACK, jangan diulang tanpa henti.
            report(documentId, e.getMessage());
            ack(msg.id());
        } catch (IOException e) {
            // Sementara (API atau jaringan). Tidak di-ACK, sehingga job bisa dicoba lagi.
            retryOrDeadLetter(msg, documentId, e);
        } catch (RuntimeException e) {
            // Exception tak terduga (mis. data OCR di luar dugaan). Diperlakukan seperti gagal sementara supaya
            // pesan tidak tertahan di pending list dan menghentikan job lain; setelah batas, pindah ke dead letter.
            retryOrDeadLetter(msg, documentId, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void retryOrDeadLetter(StreamMessage<String, String, String> msg, String documentId, Exception e) {
        int attempts = countAttempt(msg.id());
        if (attempts < MAX_ATTEMPTS) {
            LOG.warnf("job dokumen %s ditunda (percobaan %d/%d): %s", documentId, attempts, MAX_ATTEMPTS, e.getMessage());
            return;
        }
        // Batas percobaan habis: laporkan gagal dan pindahkan ke dead letter agar tidak diulang tanpa henti.
        LOG.errorf("job dokumen %s gagal %d kali, dipindah ke %s", documentId, attempts, DEAD);
        report(documentId, "RETRY_EXHAUSTED");
        deadLetter(msg, documentId);
        ack(msg.id());
    }

    /** Menambah penghitung percobaan untuk satu pesan. Disimpan di Redis agar bertahan saat restart. */
    private int countAttempt(String messageId) {
        try {
            String key = ATTEMPTS_PREFIX + messageId;
            long n = redis.value(Long.class).incr(key);
            redis.key().expire(key, ATTEMPTS_TTL);
            return (int) n;
        } catch (RuntimeException e) {
            LOG.warnf("gagal menghitung percobaan %s: %s", messageId, e.getMessage());
            return 1;
        }
    }

    private void deadLetter(StreamMessage<String, String, String> msg, String documentId) {
        try {
            commands().xadd(DEAD, Map.of(
                    "document_id", documentId,
                    "original_id", msg.id(),
                    "reason", "RETRY_EXHAUSTED"));
        } catch (RuntimeException e) {
            LOG.errorf("gagal menulis dead letter untuk %s: %s", msg.id(), e.getMessage());
        }
    }

    private void ack(String id) {
        try {
            commands().xack(STREAM, GROUP, id);
        } catch (RuntimeException e) {
            LOG.warnf("gagal ACK %s: %s", id, e.getMessage());
        }
    }

    private void report(String documentId, String errorCode) {
        ObjectNode body = mapper.createObjectNode();
        body.put("status", "failed");
        body.put("error_code", errorCode);
        body.put("error_message", "Ekstraksi gagal: " + errorCode);
        try {
            api.sendExtraction(documentId, body);
        } catch (IOException e) {
            LOG.warnf("gagal melapor kegagalan dokumen %s: %s", documentId, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Callback sukses. Reimburse diekstrak dengan aturan (Surat Tugas, SPD, struk); lainnya tanpa field. */
    ObjectNode extracted(String category, String text) {
        return extracted(category, text, null);
    }

    /** Kunci field yang masih kosong. Dipakai untuk meminta pendamping hanya pada field itu. */
    static List<String> kunciKosong(Map<String, Object> fields) {
        List<String> kunci = new java.util.ArrayList<>();
        for (Map.Entry<String, Object> e : fields.entrySet()) {
            if (e.getValue() instanceof Map<?, ?> leaf && RuleExtractor.kosongLeaf(leaf)) kunci.add(e.getKey());
        }
        return kunci;
    }

    /**
     * Struk dan dokumen lain: pendamping digabungkan ke field sebelum hasil dikirim. Hanya dipanggil bila jenis
     * dokumen didukung pendamping dan ada field inti yang kosong. Gagal atau kosong: hasil teks tetap dikirim.
     */
    void mergePendampingDokumenSebelumKirim(String documentId, ObjectNode body, byte[] file)
            throws IOException, InterruptedException {
        String docType = body.path("doc_type").asText("");
        String kind = kindPendamping(docType);
        if (kind == null) return;
        Map<String, Object> fields = mapper.convertValue(body.path("fields"), new TypeReference<Map<String, Object>>() {});
        if (fields == null || !RuleExtractor.perluPendamping(docType, fields)) return;
        // Hanya field yang masih kosong yang diminta. Keluaran yang lebih pendek membuat balasan lebih cepat di CPU.
        JsonNode vision = engine.extractVision(file, kind, kunciKosong(fields));
        if (vision == null || !vision.isObject() || vision.isEmpty()) {
            LOG.warnf("pendamping vision tidak menghasilkan field untuk dokumen %s (kind %s)", documentId, kind);
            return;
        }
        RuleExtractor.mergeVisionDokumen(fields, vision);
        body.set("fields", mapper.valueToTree(fields));
    }

    /**
     * Field KTP yang sering salah dibaca aturan (OCR). Pendamping diminta membacanya hanya bila field itu lemah
     * (lihat RuleExtractor.fieldKtpLemah), dan aturan penggantian di mergeVision memutuskan apakah nilainya dipakai.
     */
    static final List<String> KUNCI_KTP_RAWAN = List.of(
            "nik", "nama", "alamat", "rt_rw", "kelurahan_desa", "kecamatan", "agama", "pekerjaan");

    /**
     * Kunci yang diminta ke pendamping untuk KTP. Alamat dan pekerjaan selalu diminta: Tesseract sering salah membaca
     * keduanya (mis. tulisan stempel ikut terbaca) walau polanya tampak wajar. Field rawan lain hanya diminta bila
     * lemah (lihat fieldKtpLemah), dan field kosong di luar daftar ikut diminta.
     */
    static List<String> kunciPendampingKtp(Map<String, Object> fields) {
        List<String> kunci = new java.util.ArrayList<>(List.of("alamat", "pekerjaan"));
        for (String k : KUNCI_KTP_RAWAN) {
            if (!kunci.contains(k) && RuleExtractor.fieldKtpLemah(k, fields)) kunci.add(k);
        }
        for (Map.Entry<String, Object> e : fields.entrySet()) {
            if (e.getValue() instanceof Map<?, ?> leaf && RuleExtractor.kosongLeaf(leaf) && !kunci.contains(e.getKey())) {
                kunci.add(e.getKey());
            }
        }
        return kunci;
    }

    /**
     * KTP: pendamping digabungkan ke field sebelum hasil dikirim, supaya hasil akhir tampil sekaligus.
     * Gagal atau kosong dari pendamping: body tetap berisi hasil teks saja.
     */
    void mergePendampingSebelumKirim(String documentId, ObjectNode body, byte[] file)
            throws IOException, InterruptedException {
        Map<String, Object> fields = mapper.convertValue(body.path("fields"), new TypeReference<Map<String, Object>>() {});
        if (fields == null) return;
        JsonNode vision = engine.extractVision(file, "identity", kunciPendampingKtp(fields));
        if (vision == null || !vision.isObject() || vision.isEmpty()) {
            LOG.warnf("pendamping vision tidak menghasilkan field untuk dokumen %s", documentId);
            return;
        }
        RuleExtractor.mergeVision(fields, vision);
        body.set("fields", mapper.valueToTree(fields));
    }

    /** Jenis pendamping vision untuk jenis dokumen. Null bila jenis tidak didukung. */
    static String kindPendamping(String docType) {
        return switch (docType) {
            case RuleExtractor.DOC_FUEL -> "fuel";
            case RuleExtractor.DOC_SURAT_TUGAS -> "surat_tugas";
            case RuleExtractor.DOC_SPD -> "spd";
            case RuleExtractor.DOC_RECEIPT -> "receipt";
            default -> null;
        };
    }

    /** `identityNik` = NIK terpilih dari engine (mode identitas). Null bila engine tidak memilih. */
    /**
     * Ekstraksi untuk demo: OCR lalu aturan ekstraksi, tanpa antrean, callback ke API, atau penyimpanan.
     * Hasilnya langsung dikembalikan ke pemanggil dan tidak ditulis di mana pun. `tahap` dipanggil sebelum tiap
     * tahap ("teks", "identitas", "pendamping") supaya pemanggil bisa menampilkan kemajuan.
     */
    public ObjectNode ekstrakDemo(byte[] file, String category, Consumer<String> tahap) throws Exception {
        String effective = category;
        OcrEngineHttp.EngineText result;
        tahap.accept("teks");
        if ("auto".equals(category)) {
            result = engine.extractText(file, OcrEngineHttp.MODE_TEXT);
            effective = RuleExtractor.kategoriAuto(result.text());
            if ("identity".equals(effective)) {
                tahap.accept("identitas");
                result = engine.extractText(file, OcrEngineHttp.MODE_IDENTITY);
            }
        } else {
            if ("identity".equals(category)) tahap.accept("identitas");
            result = engine.extractText(file, modeFor(category));
        }
        ObjectNode body = extracted(effective, result.text(), result.identityNik(), kind -> null);
        // KTP: pendamping vision mengisi NIK dan nama yang tidak terbaca OCR. Hanya fungsi baca, tanpa penyimpanan.
        if ("identity".equals(effective)) {
            tahap.accept("pendamping");
            mergePendampingSebelumKirim("demo", body, file);
        }
        return body;
    }

    ObjectNode extracted(String category, String text, String identityNik) {
        return extracted(category, text, identityNik, kind -> null);
    }

    /**
     * Ekstraksi dari teks. Pendamping vision (`pendamping`) dipanggil hanya bila hasil teks masih kurang, dan hanya
     * mengisi field yang kosong (ditandai source "llm").
     */
    ObjectNode extracted(String category, String text, String identityNik, Function<String, JsonNode> pendamping) {
        String docType;
        Map<String, Object> fields;
        if ("reimburse".equals(category)) {
            RuleExtractor.Result result = RuleExtractor.extractReimburse(text);
            docType = result.docType();
            fields = result.fields();
            String kind = kindPendamping(docType);
            if (kind != null && RuleExtractor.perluPendamping(docType, fields)) {
                RuleExtractor.mergeVisionDokumen(fields, pendamping.apply(kind));
            }
        } else if ("identity".equals(category)) {
            // KTP (OQ-8, 2026-10-15): field diekstrak di sini, lalu dienkripsi oleh API sebelum disimpan.
            docType = "IDENTITY_CARD";
            fields = RuleExtractor.extractIdentity(text, identityNik);
            if (RuleExtractor.perluPendamping(docType, fields)) {
                RuleExtractor.mergeVision(fields, pendamping.apply("identity"));
            }
        } else {
            docType = docTypeFor(category);
            fields = Map.of();
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("status", "extracted");
        body.put("doc_type", docType);
        body.put("ocr_source", "ocr");
        body.put("extraction_path", "rule");
        body.put("schema_version", "1.0");
        body.put("ocr_text", text);
        body.set("fields", mapper.valueToTree(fields));
        return body;
    }

    /** Lama (ms) sejak waktu nano `mulai`. */
    static long ms(long mulai) {
        return (System.nanoTime() - mulai) / 1_000_000;
    }

    /** Mode engine untuk kategori yang sudah diketahui. */
    static String modeFor(String category) {
        return switch (category) {
            case "identity" -> OcrEngineHttp.MODE_IDENTITY;
            case "reimburse" -> OcrEngineHttp.MODE_REIMBURSE;
            default -> OcrEngineHttp.MODE_TEXT;
        };
    }

    static String docTypeFor(String category) {
        return switch (category) {
            case "identity" -> "IDENTITY_CARD";
            case "other" -> "OTHER";
            default -> "RECEIPT";
        };
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
