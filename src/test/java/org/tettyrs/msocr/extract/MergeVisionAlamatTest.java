package org.tettyrs.msocr.extract;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MergeVisionAlamatTest {

    @Test
    void alamatPendampingTidakIkutLabelBerikutnya() throws Exception {
        // Pendamping hanya mengirim alamat dan pekerjaan (tanpa RT/RW). Alamat tidak boleh terbawa "Pekerjaan : ...".
        String teks = "NIK\n: 3171234567890123\nAlamat\n: JL. PASTI CEPAT A7/66\nPekerjaan\nPEGAWAI SWASTA";
        Map<String, Object> f = RuleExtractor.extractIdentity(teks, null);
        var vision = new ObjectMapper().readTree("{\"alamat\":\"JL. PASTI CEPAT A7/66\",\"pekerjaan\":\"PEGAWAI SWASTA\"}");

        RuleExtractor.mergeVision(f, vision);

        assertEquals("JL. PASTI CEPAT A7/66", ((Map<?, ?>) f.get("alamat")).get("value"));
    }
}
