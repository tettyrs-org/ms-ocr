package org.tettyrs.msocr.correction.tokens;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.tettyrs.msocr.ocr.OcrResponse.Word;

class TokenCleanerTest {
    private static final List<Double> BOX = List.of(0.1, 0.2, 0.2, 0.21);

    private static List<String> texts(String... words){
        List<Word> input = Arrays.stream(words).map(w -> new Word(w, BOX, 0.9)).toList();
        return TokenCleaner.clean(input).stream().map(Token::text).toList();
    }

    @Test
    void dropsLetterheadRule(){
        assertEquals(List.of("SURAT"), texts("____\u2014\u2014___", "SURAT"));
    }

    @Test
    void keepsShortPunctuationAsHyphen(){
        assertEquals(List.of("Dasar", "-", ":"), texts("Dasar", "\u2014", ":"));
    }

    @Test
    void normalizesTyppgraphicQuotes(){
        assertEquals(List.of("\"4"), texts("\u201C4"));
    }

    @Test
    void splitColonsStuckToWords(){
        assertEquals(List.of(":", "198503122010011004", "kepada", ":"),
                texts(":198503122010011004", "kepada:"));
    }

    @Test
    void keepsDotsAndLoneColon(){
        assertEquals(List.of("Dr.", "M.M", "1.", ":"), texts("Dr.", "M.M", "1.", ":"));
    }

    @Test
    void normalizesNonBreakingSpaceThroughNfkc(){
        assertEquals(List.of("a b"), texts("a\u00A0b"));
    }

    @Test
    void keepReferenceToOriginalWord(){
        List<Token> tokens = TokenCleaner.clean(List.of(
                new Word("____", BOX, 0.5),
                new Word("kepada:", BOX, 0.9)));
        assertEquals(List.of(1,1), tokens.stream().map(Token::sourceIndex).toList());
    }
}