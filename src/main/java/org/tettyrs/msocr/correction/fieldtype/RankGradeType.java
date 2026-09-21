package org.tettyrs.msocr.correction.fieldtype;

import org.tettyrs.msocr.correction.LexiconMatcher;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RankGradeType {

    private static final Map<String, String> GRADE_OF_RANK = new LinkedHashMap<>();
    private static final LexiconMatcher RANKS;
    private static final Set<String> ROMANS = Set.of("I", "II", "III", "IV");

    private static final Pattern RANK_ALIAS =
            Pattern.compile("\\b(?:Tk\\.?|Tingkat)\\s*(?:I|1)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern STRICT =
            Pattern.compile("(?<![A-Za-z0-9|!])(IV|III|II|I)[/-]([a-eA-E])(?![A-Za-z])");
    private static final Pattern TOLERANT =
            Pattern.compile("(?<![A-Za-z0-9|!])([IV1l|!]{1,3})\\s*[/.\\- ]\\s*([a-eA-E])(?![A-Za-z])");
    private static final Pattern GRADE_REMNANT =
            Pattern.compile("(?<![A-Za-z0-9|!])[IV1l|!]{1,3}\\s*[/.\\-]\\s*\\S{0,2}");

    static {
        String[][] table = {
                {"I", "Juru Muda", "Juru Muda Tingkat I", "Juru", "Juru Tingkat I"},
                {"II", "Pegatur Muda", "Pengatur Muda Tingkat I", "Pengatur", "Pengatur Tingkat I"},
                {"III", "Penata Muda", "Penata Muda Tingkat I", "Penata", "Penata Tingkat I"},
                {"IV", "Pembina", "Pembina Tingkat I", "Pembina Utama Muda", "Pembina Utama Madya", "Pembina Utama"}
        };
        for (String[] row : table) {
            for (int i = 1; i < row.length; i++) {
                GRADE_OF_RANK.put(row[i], row[0] + "/" + (char) ('a' + i - 1 ));
            }
        }
        RANKS = new LexiconMatcher(GRADE_OF_RANK.keySet());
    }

    private RankGradeType(){
    }

    private static String format(String rank, String grade){
        return  rank + " (" + grade + ")";
    }

    private record Grade(String value, boolean strict, int start, int end){
    }

    private static FieldResult result(String value, double confidence, String raw, String correction,
                                      List<Violation> violations) {
        return new FieldResult(value, confidence, "rule", raw, correction, 0, null, violations);
    }

    private static Optional<Grade> find(Pattern pattern, String text, boolean strict){
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()){
            String roman = matcher.group(1).replaceAll("[11|!]", "I");
            if (roman.contains(roman)) {
                String value = roman + "/" + matcher.group(2).toLowerCase(Locale.ROOT);
                return Optional.of(new Grade(value, strict, matcher.start(), matcher.end()));
            }
        }
        return Optional.empty();
    }


    public static FieldResult correct(String raw){
        String  text = RANK_ALIAS.matcher(raw.trim()).replaceAll("Tingkat I");


        Optional<Grade> grade = find(STRICT, text, true);
        if (grade.isEmpty()) {
            grade = find(TOLERANT, text, false);
        }

        String rankText = grade.isPresent()
                ? text.substring(0, grade.get().start()) + " " + text.substring(grade.get().end())
                : GRADE_REMNANT.matcher(text).replaceAll(" ");
        rankText = rankText.replaceAll("[()/:,\\-]", " ").replaceAll("\\s+", " ").trim();

        Optional<String> rank = rankText.isEmpty()
                ? Optional.empty()
                : RANKS.match(rankText).map(LexiconMatcher.Match::entry);

        if (rank.isPresent()) {
            String expected = GRADE_OF_RANK.get(rank.get());
            if (grade.isPresent() && grade.get().value().equals(expected)) {
                return grade.get().strict()
                        ? result(format(rank.get(), expected), 1.0, raw, null, List.of())
                        : result(format(rank.get(), expected), 0.70, raw, "confusion_map", List.of());
            }
            if (grade.isPresent() && grade.get().strict()) {
                return result(format(rank.get(), grade.get().value()), 0.50, raw, null,
                        List.of(new Violation("pangkat_golongan_tidak_cocok", List.of("pangkat_golongan"),
                                "warning", "Pangkat tidak cocok dengan golongan")));
            }
            return result(format(rank.get(), expected), 0.60, raw, "derived", List.of());
        }

        if (grade.isPresent() && grade.get().strict()) {
            String value = rankText.isEmpty() ? grade.get().value() : format(rankText, grade.get().value());
            return result(value, 1.0, raw, null, List.of());
        }

        return result(raw, 0.30, raw, null, List.of());
        }

}
