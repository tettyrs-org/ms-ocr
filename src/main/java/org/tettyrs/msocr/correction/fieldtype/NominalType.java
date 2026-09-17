package org.tettyrs.msocr.correction.fieldtype;

import org.tettyrs.msocr.correction.DigitRepair;
import org.tettyrs.msocr.correction.SpelledNumberParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NominalType {
    private static final Pattern PREFIX = Pattern.compile("^(?:r\\s*p|ro|idr)\\.?", Pattern.CASE_INSENSITIVE);
    private static final Pattern INDONESIAN = Pattern.compile("^(\\d+|\\d{1,3}(?:\\.\\d{3})+)(?:,(\\d{1,2}))?$");
    private static final Pattern WESTERN = Pattern.compile("^\\d{1,3}(?:,\\d{3})+$");
    private static final int MAX_DIGITS = 15;

    private NominalType(){
    }

    private  static  Violation invalidFormat(){
        return new Violation("nominal_format_tidak_valid", List.of("nominal"), "error",
                "Format nominal tidak valid");
    }

    private static FieldResult readable(String raw, long amount, String decimals, boolean repaired,
                                        Optional<Long> spelled){
        String correction = repaired ? "confusion_map": null;
        double confidence = repaired ? 0.70 : 1.0;
        List<Violation> violations = new ArrayList<>();

        if (!decimals.chars().allMatch(c -> c == '0')) {
            confidence = 0.50;
            violations.add(invalidFormat());
        }
        if (spelled.isPresent() && spelled.get() != amount) {
            confidence = 0.50;
            violations.add(new Violation("terbilang_tidak_cocok", List.of("nominal"), "warning",
                    "Terbilang tidak cocok dengan angka"));
        }
        return  new FieldResult(String.valueOf(amount), confidence, "rule", raw, correction, 0, null,
                List.copyOf(violations));
    }

    public static FieldResult correct(String raw, String spelledText) {
        Optional<Long> spelled = spelledText == null
                ? Optional.empty()
                : SpelledNumberParser.parse(spelledText).filter(v -> v > 0);

        String text = PREFIX.matcher(raw.replaceAll("\\s+", "")).replaceFirst("");
        if (text.endsWith(",-")) {
            text = text.substring(0, text.length() - 2);
        }

        Optional<DigitRepair.Result> repaired = DigitRepair.repair(text, 1, ".,");
        if (repaired.isPresent()) {
            String value = repaired.get().value();
            String integerPart = null;
            String decimals = "";

            Matcher indonesian = INDONESIAN.matcher(value);
            if (indonesian.matches()) {
                integerPart = indonesian.group(1).replace(".", "");
                decimals = indonesian.group(2) == null ? "" : indonesian.group(2);
            } else if (WESTERN.matcher(value).matches()) {
                integerPart = value.replace(",", "");
            }

            if (integerPart != null && integerPart.length() <= MAX_DIGITS) {
                return readable(raw, Long.parseLong(integerPart), decimals,
                        repaired.get().substitutions() > 0, spelled);
            }
        }

        if (spelled.isPresent()) {
            return new FieldResult(String.valueOf(spelled.get()), 0.70, "rule", raw, "lexicon", 0, null, List.of());
        }
        return new FieldResult(null, 0.30, "rule", raw, null, 0, null, List.of(invalidFormat()));
    }

    public static FieldResult correct(String raw) {
        return correct(raw, null);
    }
}
