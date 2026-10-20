package org.tettyrs.msocr.extract;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** NIK dari mode identitas ocr-engine: dipakai hanya bila teks tidak punya NIK, dan harus valid. Teks fiktif. */
class IdentityEngineNikTest {

    private static Object value(Map<String, Object> fields, String key) {
        return ((Map<?, ?>) fields.get(key)).get("value");
    }

    @Test
    void engineNikIsUsedWhenTextHasNone() {
        Map<String, Object> f = RuleExtractor.extractIdentity("nama CONTOH PENGGUNA agama ISLAM", "3271010101900001");

        assertEquals("3271010101900001", value(f, "nik"));
    }

    @Test
    void textNikWinsOverEngineNik() {
        Map<String, Object> f = RuleExtractor.extractIdentity("NIK 3271010101900001", "7406045512940003");

        assertEquals("3271010101900001", value(f, "nik"));
    }

    @Test
    void invalidEngineNikIsIgnored() {
        // Sad path: provinsi 10 tidak valid, jadi NIK dari engine tidak dipakai.
        Map<String, Object> f = RuleExtractor.extractIdentity("nama CONTOH PENGGUNA", "1034010101900001");

        assertNull(value(f, "nik"));
    }

    @Test
    void malformedEngineNikIsIgnored() {
        Map<String, Object> f = RuleExtractor.extractIdentity("nama CONTOH PENGGUNA", "327101010190000");

        assertNull(value(f, "nik"));
    }
}
