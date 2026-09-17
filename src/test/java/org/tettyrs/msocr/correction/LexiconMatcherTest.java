package org.tettyrs.msocr.correction;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LexiconMatcherTest {

    private static final LexiconMatcher LABELS = new LexiconMatcher(
            List.of("Nama", "NIP", "Tanggal Berangkat", "Tanggal Kembali")
    );

    private static Optional<LexiconMatcher.Match> match(String entry, int distance){
        return Optional.of(new LexiconMatcher.Match(entry, distance));
    }

    @Test
    void matchesExactly(){
        assertEquals(match("Nama", 0), LABELS.match("Nama"));
    }

}