package org.tettyrs.msocr.correction.fieldtype;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DateType {

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("januari", 1), Map.entry("februari", 2), Map.entry("maret", 3),
            Map.entry("april", 4), Map.entry("mei", 5), Map.entry("juni", 6),
            Map.entry("juli", 7), Map.entry("agustus", 8), Map.entry("september", 9),
            Map.entry("oktober", 10), Map.entry("november", 11), Map.entry("desember", 12),
            Map.entry("jan", 1), Map.entry("feb", 2), Map.entry("mar", 3),
            Map.entry("apr", 4), Map.entry("jun", 6), Map.entry("agu", 8),
            Map.entry("agt", 8), Map.entry("ags", 8), Map.entry("sep", 9),
            Map.entry("sept", 9), Map.entry("okt", 10), Map.entry("nov", 11), Map.entry("des", 12));

    private DateType() {
    }

    public static FieldResult correct(String raw) {
        String normalized = raw.trim();

        Pattern textPattern = Pattern.compile("^(\\d{1,2})\\s+([a-zA-Z]+)\\s+(\\d{4})$");
        Matcher textFormat = textPattern.matcher(normalized);
        if (textFormat.find()) {
            return parseTextFormat(textFormat.group(1), textFormat.group(2), textFormat.group(3), raw);
        }

        Pattern numericPattern = Pattern.compile("^(\\d{1,2})[-/](\\d{1,2})[-/](\\d{4})$");
        Matcher numericFormat = numericPattern.matcher(normalized);
        if (numericFormat.find()) {
            return parseNumericFormat(numericFormat.group(1), numericFormat.group(2), numericFormat.group(3), raw);
        }

        return new FieldResult(null, 0.30, "rule", raw, null, 0, null, java.util.List.of());
    }

    private static FieldResult parseTextFormat(String dayStr, String monthStr, String yearStr, String raw) {
        String monthLower = monthStr.toLowerCase();
        if (!MONTHS.containsKey(monthLower)) {
            return new FieldResult(null, 0.30, "rule", raw, null, 0, null, java.util.List.of());
        }

        int monthNum = MONTHS.get(monthLower);
        return parseAndValidate(dayStr, String.valueOf(monthNum), yearStr, raw,
                monthLower.equals(monthStr) ? null : "lexicon");
    }

    private static FieldResult parseNumericFormat(String dayStr, String monthStr, String yearStr, String raw) {
        return parseAndValidate(dayStr, monthStr, yearStr, raw, null);
    }

    private static FieldResult parseAndValidate(String dayStr, String monthStr, String yearStr, String raw, String correction) {
        try {
            int day = Integer.parseInt(dayStr);
            int month = Integer.parseInt(monthStr);
            int year = Integer.parseInt(yearStr);

            LocalDate date = LocalDate.of(year, month, day);
            String result = date.format(DateTimeFormatter.ISO_DATE);

            double confidence = correction != null ? 0.70 : 1.0;
            return new FieldResult(result, confidence, "rule", raw, correction, 0, null, java.util.List.of());
        } catch (Exception e) {
            return new FieldResult(null, 0.30, "rule", raw, null, 0, null, java.util.List.of());
        }
    }
}
