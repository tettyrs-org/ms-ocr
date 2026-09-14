package org.tettyrs.msocr.correction.tokens;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
class LineBuilderTest {
    private static Token token(String text, double x0, double centerY){
        return new Token(text, List.of(x0, centerY - 0.005, x0 + 0.05, centerY + 0.005), 0.9, 0);
    }

    private static List<String> lineTexts(List<Token> tokens){
        return LineBuilder.build(tokens).stream().map(Line::text).toList();
    }

    @Test
    void groupTokenIntoLinesFromTopToBottom(){
        assertEquals(List.of("Nama : Budi", "NIP : 1985"), lineTexts(List.of(
                token("NIP", 0.1, 0.31), token("1985", 0.3, 0.31), token(":", 0.2, 0.31),
                token("Budi", 0.3, 0.29), token("Nama", 0.1, 0.29), token(":", 0.2, 0.29))));
    }

    @Test
    void startNewLineWhenGapExceedsHalfHeight(){
        assertEquals(List.of("atas", "bawah"),
                lineTexts(List.of(token("atas", 0.1, 0.500), token("bawah", 0.2, 0.506))));
    }

    @Test
    void emptyInputHasNoLines(){
        assertEquals(List.of(), LineBuilder.build(List.of()));
    }
}