package org.tettyrs.msocr.correction;

import java.util.Optional;

public final class DigitRepair {

    private static final String CONFUSABLE = "OoQDIl|i![]ZzSs$GbTBgq";
    private static final String DIGIT_FOR = "0000111111122555667899";

    private DigitRepair() {
    }

    public static Optional<Result> repair(String text, int maxSubstitutions, String keep) {
        if (text.isEmpty()) {
            return Optional.empty();
        }

        StringBuilder value = new StringBuilder(text.length());
        int substitutions = 0;
        for (char c : text.toCharArray()) {
            int confusable = CONFUSABLE.indexOf(c);
            if ((c >= '0' && c <= '9') || keep.indexOf(c) >= 0) {
                value.append(c);
            } else if (confusable >= 0 && ++substitutions <= maxSubstitutions) {
                value.append(DIGIT_FOR.charAt(confusable));
            } else {
                return Optional.empty();
            }
        }
        return Optional.of(new Result(value.toString(), substitutions));
    }

    public record Result(String value, int substitutions) {
    }
}
