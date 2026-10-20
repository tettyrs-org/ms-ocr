package org.tettyrs.msocr.correction;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RomanMonthRepair {

    private static final List<String> ROMAN_MONTHS = Arrays.asList(
            "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII");
    private static final Pattern SEGMENT = Pattern.compile("(?<=/)([IVX1]+)(?=/)");

    private RomanMonthRepair() {
    }

    public static Optional<String> repair(String nomor) {
        Matcher m = SEGMENT.matcher(nomor);
        while (m.find()) {
            String segment = m.group(1);
            if (!segment.matches(".*[IVX].*")) {
                continue;
            }
            String fixed = segment.replace('1', 'I');
            if (!fixed.equals(segment) && ROMAN_MONTHS.contains(fixed)) {
                return Optional.of(nomor.substring(0, m.start(1)) + fixed + nomor.substring(m.end(1)));
            }
        }
        return Optional.empty();
    }
}
