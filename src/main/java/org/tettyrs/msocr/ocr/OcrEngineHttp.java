package org.tettyrs.msocr.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import org.jboss.logging.Logger;

/**
 * Pemanggilan ocr-engine `POST /ocr` (api-spec bagian 9). Mengembalikan teks gabungan semua halaman.
 */
@ApplicationScoped
public class OcrEngineHttp {
    private static final Logger LOG = Logger.getLogger(OcrEngineHttp.class);


    @ConfigProperty(name = "ocr.engine.url")
    String engineUrl;

    @Inject
    ObjectMapper mapper;

    // HTTP/1.1 wajib: klien default mengirim upgrade h2c yang tidak didukung uvicorn, sehingga body multipart rusak (422).
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** Kegagalan dari ocr-engine (bukan jaringan). Dicatat sebagai callback `failed`. */
    public static class OcrException extends Exception {
        public OcrException(String message) {
            super(message);
        }
    }

    /** Hasil ekstraksi: teks gabungan semua halaman, dan NIK yang dipilih engine bila mode identitas (null bila tidak). */
    public static final String MODE_TEXT = "text";
    public static final String MODE_IDENTITY = "identity";
    public static final String MODE_REIMBURSE = "reimburse";

    public record EngineText(String text, String identityNik) {
    }

    public String extractText(byte[] file) throws IOException, InterruptedException, OcrException {
        return extractText(file, false).text();
    }

    /** `identity` = true meminta engine membaca NIK KTP dari area tetap kartu (ocr-engine, mode=identity). */
    public EngineText extractText(byte[] file, boolean identity) throws IOException, InterruptedException, OcrException {
        return extractText(file, identity ? MODE_IDENTITY : MODE_TEXT);
    }

    /** Mode: "text" (dokumen umum), "identity" (KTP), atau "reimburse" (struk BBM, surat tugas, SPD). */
    public EngineText extractText(byte[] file, String mode) throws IOException, InterruptedException, OcrException {
        boolean identity = MODE_IDENTITY.equals(mode);
        boolean berat = !MODE_TEXT.equals(mode); // mode berpendamping vision: beban lebih berat, batas waktu lebih longgar
        String boundary = "----ocr" + UUID.randomUUID();
        String contentType = detectContentType(file);
        byte[] head = ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"berkas\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream body = new ByteArrayOutputStream(head.length + file.length + tail.length);
        body.write(head);
        body.write(file);
        body.write(tail);

        String url = engineUrl + "/ocr" + (MODE_TEXT.equals(mode) ? "" : "?mode=" + mode);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                // KTP memanggil pendamping vision di CPU (sampai ~3 menit saat model dimuat), jadi batasnya lebih longgar.
                .timeout(Duration.ofSeconds(berat ? 300 : 120))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new OcrException("OCR_ENGINE_" + res.statusCode());
        }

        JsonNode root = mapper.readTree(res.body());
        StringBuilder text = new StringBuilder();
        for (JsonNode page : root.path("pages")) {
            String pageText = page.path("text").asText("");
            if (!pageText.isBlank()) {
                if (text.length() > 0) text.append("\n\n");
                text.append(pageText.trim());
            }
        }
        // NIK dari engine hanya diteruskan bila kosong atau tidak null. Validasinya dilakukan di RuleExtractor.
        JsonNode nik = root.path("identity").path("nik");
        String identityNik = nik.isTextual() ? nik.asText() : null;
        // Pembacaan pendamping vision (KTP saja). Hilang atau kosong bila layanan tidak aktif.
        return new EngineText(text.toString(), identityNik);
    }

    /**
     * Pendamping vision lewat ocr-engine (/vision). Hanya dipanggil bila hasil teks masih kurang. Bila engine gagal
     * atau pendamping mati, hasilnya objek kosong dan ekstraksi tetap memakai teks.
     */
    public JsonNode extractVision(byte[] file, String kind) {
        try {
            String boundary = "----ocr" + UUID.randomUUID();
            String contentType = detectContentType(file);
            byte[] head = ("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"berkas\"\r\n"
                    + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
            byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream body = new ByteArrayOutputStream(head.length + file.length + tail.length);
            body.write(head);
            body.write(file);
            body.write(tail);
            HttpRequest req = HttpRequest.newBuilder(URI.create(engineUrl + "/vision?kind=" + kind))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(Duration.ofSeconds(300))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                LOG.warnf("pendamping vision gagal: HTTP %d", res.statusCode());
                return mapper.createObjectNode();
            }
            JsonNode root = mapper.readTree(res.body());
            return root.isObject() ? root : mapper.createObjectNode();
        } catch (IOException e) {
            LOG.warnf("pendamping vision tidak terjangkau: %s", e.getClass().getSimpleName());
            return mapper.createObjectNode();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return mapper.createObjectNode();
        }
    }

    /** Jenis berkas dari magic bytes, sama dengan aturan API. */
    static String detectContentType(byte[] b) {
        if (b.length >= 4 && b[0] == 0x25 && b[1] == 0x50 && b[2] == 0x44 && b[3] == 0x46) return "application/pdf";
        if (b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47) return "image/png";
        return "image/jpeg";
    }
}
