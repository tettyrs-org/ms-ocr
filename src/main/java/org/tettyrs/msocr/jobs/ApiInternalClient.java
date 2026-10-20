package org.tettyrs.msocr.jobs;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Klien ke endpoint internal API (api-spec bagian 8): mengambil berkas dan mengirim callback hasil.
 * Setiap permintaan membawa X-Internal-Token. Token tidak pernah dicatat di log.
 */
@ApplicationScoped
public class ApiInternalClient {

    static final String TOKEN_HEADER = "X-Internal-Token";

    @ConfigProperty(name = "ocr.api.url")
    String apiUrl;

    @ConfigProperty(name = "internal.token", defaultValue = "")
    String token;

    @Inject
    ObjectMapper mapper;

    // HTTP/1.1 agar tidak mencoba upgrade h2c ke API (lihat OcrEngineHttp).
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public byte[] fetchFile(String documentId) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(apiUrl + "/internal/documents/" + documentId + "/file"))
                .header(TOKEN_HEADER, token)
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() != 200) {
            throw new IOException("API file " + res.statusCode());
        }
        return res.body();
    }

    /** Mengirim hasil ekstraksi. Status HTTP selain 2xx dilempar agar job tidak di-ACK. */
    public void sendExtraction(String documentId, ObjectNode body) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(apiUrl + "/internal/documents/" + documentId + "/extraction"))
                .header(TOKEN_HEADER, token)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IOException("API callback " + res.statusCode());
        }
    }
}
