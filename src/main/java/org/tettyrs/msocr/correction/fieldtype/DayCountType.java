package org.tettyrs.msocr.correction.fieldtype;

import org.tettyrs.msocr.correction.DigitRepair;
import org.tettyrs.msocr.correction.SpelledNumberParser;

import java.util.List;
import java.util.Optional;

public class DayCountType {
    private static final long MIN_DAYS = 1;
    private static final long MAX_DAYS = 365;

    private DayCountType() {
    }

    private static boolean inRange(long days){
        return  days >= MIN_DAYS && days <= MAX_DAYS;
    }

    public static FieldResult correct(String raw){
        String text = raw.trim();
        String[] parts = text.split("\\s+", 2);
        boolean hasNumberSlot = parts[0].chars().anyMatch(c -> c >= '0' && c <= '9')
                || DigitRepair.repair(parts[0], 1, "" ).isPresent();

        String spelledText = hasNumberSlot ? (parts.length > 1 ? parts[1] : "") : text;

        Optional<DigitRepair.Result> number = hasNumberSlot
                ? DigitRepair.repair(parts[0], 1, "" )
                .filter(r -> r.value().length() <= 3 && inRange(Long.parseLong(r.value())))
                : Optional.empty();
        Optional<Long> spelled = SpelledNumberParser.parse(spelledText).filter(DayCountType::inRange);

        if (number.isPresent()) {
            String days = String.valueOf(Long.parseLong(number.get().value()));
            boolean repaired = number.get().substitutions() > 0;
            String correction = repaired ? "confusion_map" : null;
            if (spelled.isPresent() && spelled.get() !=  Long.parseLong((days))) {
                return new FieldResult(days, 0.50, "rule", raw, correction, 0, null,
                        List.of(new Violation("terbilang_tidak_cocok", List.of("lama_perjalanan_hari"),
                                "warning", "Terbilang tidak cocok dengan angka")));
            }
            return new FieldResult(days, repaired ? 0.70 :1.0, "rule", raw, correction, 0, null, List.of());
        }

        if (spelled.isPresent()) {
            return new FieldResult(String.valueOf(spelled.get()), 0.70, "rule", raw, "lexicon", 0, null, List.of());
        }
        return new FieldResult(null, 0.30, "rule", raw, null, 0, null, List.of());
    }
}
