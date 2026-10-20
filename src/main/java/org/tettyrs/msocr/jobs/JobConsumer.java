package org.tettyrs.msocr.jobs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

    void handle(StreamMessage<String, String, String> msg) {
        String documentId = msg.payload().get("document_id");
        String category = msg.payload().getOrDefault("category", "other");
        if (documentId == null) {
            LOG.warnf("job %s tanpa document_id dibuang", msg.id());
            ack(msg.id());
            return;
        }
        try {
            byte[] file = api.fetchFile(documentId);
            String mode = switch (category) {
                case "identity" -> OcrEngineHttp.MODE_IDENTITY;
                case "reimburse" -> OcrEngineHttp.MODE_REIMBURSE;
                default -> OcrEngineHttp.MODE_TEXT;
            };
            OcrEngineHttp.EngineText result = engine.extractText(file, mode);
            // Hasil teks dikirim dulu supaya dokumen bisa langsung ditinjau. Pendamping vision menyusul sebagai pengayaan.
            ObjectNode body = extracted(category, result.text(), result.identityNik(), kind -> null);
            api.sendExtraction(documentId, body);
            enrich(documentId, category, body, file);
            ack(msg.id());
        } catch (OcrEngineHttp.OcrException e) {
            // Kegagalan permanen dari ocr-engine: laporkan dan ACK, jangan diulang tanpa henti.
            report(documentId, e.getMessage());
            ack(msg.id());
        } catch (IOException e) {
            // Sementara (API atau jaringan). Tidak di-ACK, sehingga job bisa dicoba lagi.
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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

    /**
     * Pengayaan dari pendamping vision setelah hasil teks tersimpan. KTP selalu dilengkapi. Dokumen hanya bila
     * field inti masih kosong. Hanya field yang berubah yang dikirim. Gagal di sini tidak mengubah hasil teks.
     */
    void enrich(String documentId, String category, ObjectNode body, byte[] file) throws IOException, InterruptedException {
        String docType = body.path("doc_type").asText("");
        String kind = "identity".equals(category) ? "identity" : kindPendamping(docType);
        if (kind == null) return;
        Map<String, Object> fields = mapper.convertValue(body.path("fields"), new TypeReference<Map<String, Object>>() {});
        if (fields == null) return;
        if (!"identity".equals(category) && !RuleExtractor.perluPendamping(docType, fields)) return;

        JsonNode vision = engine.extractVision(file, kind);
        if (vision == null || !vision.isObject() || vision.isEmpty()) return;

        Map<String, Object> hasil = new java.util.LinkedHashMap<>(fields);
        if ("identity".equals(category)) {
            RuleExtractor.mergeVision(hasil, vision);
        } else {
            RuleExtractor.mergeVisionDokumen(hasil, vision);
        }

        Map<String, Object> perubahan = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> e : hasil.entrySet()) {
            if (e.getValue() == fields.get(e.getKey())) continue; // tidak berubah
            if (!(e.getValue() instanceof Map<?, ?> leaf)) continue;
            Map<String, Object> baru = new java.util.LinkedHashMap<>((Map<String, Object>) leaf);
            // Nilai teks yang sudah ada digantikan (mis. NIK tidak valid, alamat berbeda). Field kosong hanya diisi.
            if (!RuleExtractor.kosongLeaf(fields.get(e.getKey()))) baru.put("replace", true);
            perubahan.put(e.getKey(), baru);
        }
        if (perubahan.isEmpty()) return;

        ObjectNode pengayaan = mapper.createObjectNode();
        pengayaan.put("status", "extracted");
        pengayaan.put("doc_type", docType);
        pengayaan.put("enrichment", true);
        pengayaan.set("fields", mapper.valueToTree(perubahan));
        api.sendExtraction(documentId, pengayaan);
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
