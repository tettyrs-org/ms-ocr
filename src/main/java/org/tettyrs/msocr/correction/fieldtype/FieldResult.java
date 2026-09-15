package org.tettyrs.msocr.correction.fieldtype;

import java.util.List;

public record FieldResult(
        String value,
        double confidence,
        String source,
        String rawText,
        String correction,
        int page,
        List<Double> bbox,
        List<Violation> violations
) {
}
