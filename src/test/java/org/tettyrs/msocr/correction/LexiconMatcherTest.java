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

    @Test
    void ignoresCaseAndPunctuation() {
        assertEquals(match("Nama", 0), LABELS.match("NAMA:"));
    }

    @Test
    void correctsMisreadLabels() {
        assertEquals(match("Tanggal Berangkat", 1), LABELS.match("Tanggai Berangkat"));
        assertEquals(match("Tanggal Kembali", 1), LABELS.match("Tanggal Kembati"));
    }

    @Test
    void appliesConfusionPairs() {
        assertEquals(match("Nama", 0), LABELS.match("Narna"));
    }

    @Test
    void requiresExactMatchForShortEntries() {
        assertEquals(Optional.empty(), LABELS.match("NlP"));
    }

    @Test
    void emptyCandidateHasNoMatch() {
        assertEquals(Optional.empty(), LABELS.match(":::"));
    }

    @Test
    void tieHasNoMatch() {
        assertEquals(Optional.empty(), new LexiconMatcher(List.of("Kepada", "Kepala")).match("Kepaba"));
    }

    @Test
    void identicalSpellingsCountOnce() {
        assertEquals(match("Bandung", 1),
                new LexiconMatcher(List.of("Bandung", "BANDUNG")).match("Bandunq"));
    }

    @Test
    void acceptsCustomThreshold() {
        var spelled = new LexiconMatcher(List.of("satu", "ratus"),
                length -> length <= 4 ? 1 : LexiconMatcher.defaultMaxDistance(length));
        assertEquals(match("satu", 1), spelled.match("satv"));
        assertEquals(Optional.empty(), spelled.match("ratu"));
    }

    @Test
    void defaultThresholdFollowsEntryLength() {
        assertEquals(0, LexiconMatcher.defaultMaxDistance(4));
        assertEquals(1, LexiconMatcher.defaultMaxDistance(5));
        assertEquals(1, LexiconMatcher.defaultMaxDistance(8));
        assertEquals(2, LexiconMatcher.defaultMaxDistance(9));
        assertEquals(2, LexiconMatcher.defaultMaxDistance(16));
        assertEquals(3, LexiconMatcher.defaultMaxDistance(17));
    }

    @Test
    void computesLevenshteinDistance() {
        assertEquals(3, LexiconMatcher.levenshtein("kitten", "sitting"));
    }

}