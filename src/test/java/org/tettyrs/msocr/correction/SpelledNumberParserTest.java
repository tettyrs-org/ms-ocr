package org.tettyrs.msocr.correction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SpelledNumberParserTest {

    @Test
    void readsSingleUnit() {
        assertEquals(Optional.of(4L), SpelledNumberParser.parse("empat"));
    }

    @Test
    void readsTeens() {
        assertEquals(Optional.of(12L), SpelledNumberParser.parse("dua belas"));
        assertEquals(Optional.of(11L), SpelledNumberParser.parse("sebelas"));
    }

    @Test
    void readsGroupBelowThousand() {
        assertEquals(Optional.of(123L), SpelledNumberParser.parse("seratus dua puluh tiga"));
    }

    @Test
    void readsScaledNumber() {
        assertEquals(Optional.of(1_250_000L),
                SpelledNumberParser.parse("satu juta dua ratus lima puluh ribu rupiah"));
    }

    @Test
    void readsSeribu() {
        assertEquals(Optional.of(1_000L), SpelledNumberParser.parse("seribu"));
    }

    @Test
    void ignoresClosingWords() {
        assertEquals(Optional.of(4L), SpelledNumberParser.parse("empat hari"));
    }

    @Test
    void repairsSpellingThroughLexicon() {
        assertEquals(Optional.of(4L), SpelledNumberParser.parse("Cempat)"));
    }

    @Test
    void splitsGluedWords() {
        assertEquals(Optional.of(200L), SpelledNumberParser.parse("duaratus"));
    }

    @Test
    void refusesAmbiguousSpelling() {
        assertEquals(Optional.empty(), SpelledNumberParser.parse("ratu"));
    }

    @Test
    void refusesAscendingScales() {
        assertEquals(Optional.empty(), SpelledNumberParser.parse("dua ribu tiga juta"));
    }

    @Test
    void refusesUnknownWord() {
        assertEquals(Optional.empty(), SpelledNumberParser.parse("empat xyz"));
    }
}
