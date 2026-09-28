package org.tettyrs.msocr.correction.fieldtype;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RegionTypeTest {

    @Test
    void acceptsExactProvince() {
        FieldResult result = RegionType.correct("JAWA BARAT");
        assertEquals("JAWA BARAT", result.value());
        assertEquals(1.0, result.confidence());
        assertNull(result.correction());
    }

    @Test
    void acceptsExactCity() {
        FieldResult result = RegionType.correct("Surabaya");
        assertEquals("Kota Surabaya", result.value());
        assertEquals(1.0, result.confidence());
        assertNull(result.correction());
    }

    @Test
    void correctsTypoWithLexicon() {
        FieldResult result = RegionType.correct("SURABAVA");
        assertEquals("SURABAYA", result.value());
        assertEquals("lexicon", result.correction());
        assertEquals(0.70, result.confidence());
    }

    @Test
    void keepsCaseOfOriginal() {
        FieldResult result = RegionType.correct("Surabava");
        assertEquals("Surabaya", result.value());
        assertEquals("lexicon", result.correction());
        assertEquals(0.70, result.confidence());
    }

    @Test
    void keepsPrefix() {
        FieldResult result = RegionType.correct("Kota Bandung");
        assertEquals("Kota Bandung", result.value());
        assertEquals(1.0, result.confidence());
        assertNull(result.correction());
    }

    @Test
    void correctsTypoWithPrefix() {
        FieldResult result = RegionType.correct("Kab. S1eman");
        assertEquals("Kab. Sleman", result.value());
        assertEquals(0.70, result.confidence());
        assertEquals("lexicon", result.correction());
    }

    @Test
    void aliasRecognizedButValueNotChanged() {
        FieldResult result = RegionType.correct("Solo");
        assertEquals("Kota Surakarta", result.value());
        assertEquals(1.0, result.confidence());
        assertNull(result.correction());
    }

    @Test
    void aliasRecognizedWithPrefix() {
        FieldResult result = RegionType.correct("Kota Solo");
        assertEquals("Kota Surakarta", result.value());
        assertEquals(1.0, result.confidence());
        assertNull(result.correction());
    }

    @Test
    void fullOfficeNameNotMatched() {
        FieldResult result = RegionType.correct("Kanwil DJPb Provinsi Jawa Timur");
        assertEquals("Kanwil DJPb Provinsi Jawa Timur", result.value());
        assertEquals(1.0, result.confidence());
        assertNull(result.correction());
    }

    @Test
    void shortNameNoMatch() {
        FieldResult result = RegionType.correct("Pali");
        assertEquals("Pali", result.value());
        assertEquals(1.0, result.confidence());
        assertNull(result.correction());
    }

    @Test
    void unknownNameKeptAsIs() {
        FieldResult result = RegionType.correct("NamaDaerahTidakDikenal");
        assertEquals("NamaDaerahTidakDikenal", result.value());
        assertEquals(1.0, result.confidence());
        assertNull(result.correction());
    }

    @Test
    void disambiguatesBandungToCityViaKecamatan(){
        FieldResult result = RegionType.correct("Bandung", "CIBEUNYING KALER", null);
        assertEquals("Kota Bandung", result.value());
        assertEquals(1.0, result.confidence());
        assertNull(result.correction());
    }

    @Test
    void disambiguatesBandungToKabViaPostalCode(){
        FieldResult result = RegionType.correct("Bandung", null, "40375");
        assertEquals("Kab. Bandung", result.value());
        assertEquals(1.0, result.confidence());
        assertNull(result.correction());
    }
}