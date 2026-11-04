package org.tettyrs.msocr.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Ekstraksi demo tanpa database: berkas dibaca, dikenali, lalu hasilnya dikembalikan sebagai NDJSON.
 * Setiap tahap dikirim sebagai baris {"tahap": ...} agar pengguna melihat kemajuan. Baris terakhir berisi
 * {"hasil": ...} atau {"galat": ...}. Tidak ada yang ditulis: tidak ada Redis, tidak ada API, tidak ada penyimpanan berkas.
 * Dilindungi X-Internal-Token (sama dengan INTERNAL_TOKEN), dipanggil hanya dari BFF.
 */
@Path("/demo")
public class DemoExtractResource {

    private static final Logger LOG = Logger.getLogger(DemoExtractResource.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    JobConsumer consumer;

    @ConfigProperty(name = "internal.token", defaultValue = "")
    String token;

    @POST
    @Path("/extract")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces("application/x-ndjson")
    public Response extract(
            byte[] file,
            @HeaderParam("X-Internal-Token") String provided,
            @HeaderParam("X-Category") String category) throws Exception {
        if (token.isBlank() || provided == null
                || !MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            return Response.status(401).entity("{\"error\":\"UNAUTHORIZED\"}").build();
        }
        if (file == null || file.length == 0) {
            return Response.status(400).entity("{\"error\":\"NO_FILE\"}").build();
        }
        String kategori = (category == null || category.isBlank()) ? "auto" : category;
        if (!java.util.Set.of("auto", "identity", "reimburse", "other").contains(kategori)) {
            return Response.status(400).entity("{\"error\":\"BAD_CATEGORY\"}").build();
        }
        StreamingOutput aliran = out -> {
            try {
                ObjectNode hasil = consumer.ekstrakDemo(file, kategori, tahap -> tulis(out, "tahap", tahap));
                ObjectNode akhir = MAPPER.createObjectNode();
                akhir.set("hasil", hasil);
                out.write(baris(akhir));
            } catch (Exception e) {
                // Status 200 sudah terkirim, jadi galat dikirim sebagai baris. Isi berkas tidak dicatat.
                LOG.errorf("ekstraksi demo gagal: %s", e.getClass().getSimpleName());
                ObjectNode galat = MAPPER.createObjectNode();
                galat.put("galat", "OCR_FAILED");
                out.write(baris(galat));
            }
            out.flush();
        };
        return Response.ok(aliran).build();
    }

    private static void tulis(OutputStream out, String kunci, String nilai) {
        try {
            ObjectNode baris = MAPPER.createObjectNode();
            baris.put(kunci, nilai);
            out.write(baris(baris));
            out.flush();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static byte[] baris(ObjectNode node) throws IOException {
        return (MAPPER.writeValueAsString(node) + "\n").getBytes(StandardCharsets.UTF_8);
    }
}
