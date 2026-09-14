package org.tettyrs.msocr.correction.tokens;

import java.util.List;
import java.util.stream.Collectors;
public record Line(List<Token> tokens) {
    public String text(){
        return tokens.stream().map(Token::text).collect(Collectors.joining(" "));
    }
}
