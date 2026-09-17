package org.tettyrs.msocr.correction.fieldtype;

import org.tettyrs.msocr.correction.DigitRepair;
import org.tettyrs.msocr.correction.LexiconMatcher;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DateType {

    private static final Map<String, Integer> MONTHS = new LinkedHashMap<>();
    private static final LexiconMatcher MONTH_NAMES;
    private static final Pattern TEXT_FORM = Pattern.compile("^(\\S+)\\s+([A-Za-z0-9]+)\\s+(\\S+)$");
    private static final Pattern NUMERIC_FORM = Pattern.compile("^(\\S+)([-/])(\\d{1,2})\\2(\\S+)$");

    static  {
        String[] names = { "Januari", "Februari", "Maret", "April", "Mei", "Juni",
                "Juli", "Agustus", "September", "Oktober", "November", "Desember"};
        for (int i = 0; i < names.length; i++) {
            MONTHS.put(names[i], i+1);
        }
        MONTHS.putAll(Map.of("Jan", 1, "Feb", 2, "Mar", 3, "Apr", 4, "Jun", 6,
                "Jul", 7, "Agu", 8, "Agt", 8, "Ags", 8, "Sep", 9));
        MONTHS.putAll(Map.of("Sept", 9, "Okt", 10, "Nov", 11, "Des", 12));
        MONTH_NAMES = new LexiconMatcher(MONTHS.keySet());
    }

    private DateType(){
    }

    private static boolean isAmbiguous(boolean numeric, int day, int month){
        return numeric && day <= 12 && month <= 12 && day != month;
    }

    private static FieldResult unreadable(String raw){
        return  new FieldResult(raw, 0.30, "rule", raw, null, 0, null, List.of());
    }

    private static FieldResult build(String raw, String dayText, int month, String yearText,
                                     boolean monthCorrected, boolean numeric){
        Optional<DigitRepair.Result> day = DigitRepair.repair(dayText, 1, "");
        Optional<DigitRepair.Result> year = DigitRepair.repair(yearText, 1, "");
        if (day.isEmpty() || year.isEmpty()
                || day.get().value().length() > 2 || year.get().value().length() != 4) {
            return unreadable(raw);
        }

        LocalDate date;
        try {
            date = LocalDate.of(Integer.parseInt(year.get().value()), month,
                    Integer.parseInt((day.get().value())));
        } catch (DateTimeException e){
            return unreadable(raw);
        }

        boolean repaired = day.get().substitutions() + year.get().substitutions() > 0;
        String correction = monthCorrected ? "lexicon" : repaired ? "confusion_map"  : null;
        double confidence = correction == null ? 1.0 : 0.70;
        List<Violation> violations = isAmbiguous(numeric, date.getDayOfMonth(), month)
                ? List.of(new Violation("tanggal_ambigu", List.of("tanggal"),
                "warning", "Bentuk tanggal dapat dibaca terbalik"))
                : List.of();
        return new FieldResult(date.toString(), confidence, "rule", raw, correction, 0, null, violations);
    }

    public static FieldResult correct(String raw){
        String text = raw.trim();

        Matcher textForm = TEXT_FORM.matcher(text);
        if (textForm.matches()) {
            Optional<LexiconMatcher.Match> month = MONTH_NAMES.match(textForm.group(2));
            if (month.isEmpty()) {
                return unreadable(raw);
            }
            return build(raw, textForm.group(1), MONTHS.get(month.get().entry()),
                    textForm.group(3), month.get().distance() >0, false);
        }

        Matcher numericForm = NUMERIC_FORM.matcher(text);
        if (numericForm.matches()) {
            return build(raw, numericForm.group(1), Integer.parseInt(numericForm.group(3)),
                    numericForm.group(4), false, true );
        }

        return unreadable(raw);
    }
}
