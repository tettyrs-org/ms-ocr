package org.tettyrs.msocr.correction.tokens;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

import org.tettyrs.msocr.ocr.OcrResponse.Word;

public class TokenCleaner {

    private TokenCleaner() {
    }

    public static List<Token> clean(List<Word> words){
        List<Token> tokens = new ArrayList<>();
        for (int i = 0; i < words.size(); i++){
            Word word = words.get(i);
            String text = normalizeCharacters(word.text());
            if (isGarbage(text)){
                continue;
            }
            tokens.addAll(splitColons(new Token(text, word.bbox(), word.confidence(), i)));
        }
        return tokens;
    }

    static String normalizeCharacters(String text){
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replaceAll("[\u201C\u201D\u201E\u2033\u00AB\u00BB]", "\"")
                .replaceAll("[\u2018\u2019\u201A\u2032]", "'")
                .replaceAll("[\u2014\u2013\u2012\u2212]", "-");
    }

    static boolean isGarbage(String text){
        return text.length() >= 3 && text.codePoints().noneMatch(Character::isLetterOrDigit);
    }

    static List<Token> splitColons(Token token){
        String text = token.text();
        int start = 0;
        while (start < text.length() && isColon(text.charAt(start))){
            start++;
        }
        int end = text.length();
        while (end > start && isColon(text.charAt(end - 1))){
            end--;
        }
        if(start == text.length() || (start == 0 && end == text.length())){
            return List.of(token);
        }

        List<Token> parts = new ArrayList<>(3);
        if(start > 0){
            parts.add(token.withText(text.substring(0, start)));
        }
        parts.add(token.withText(text.substring(start, end)));
        if (end < text.length()) {
            parts.add(token.withText(text.substring(end)));
        }
        return parts;
    }

    private static boolean isColon(char c){
        return c == ':' || c == ';';
    }

}
