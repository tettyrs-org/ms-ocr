package org.tettyrs.msocr.correction.fieldtype;

import java.util.List;

public record Violation(
        String rule,
        List<String> fields,
        String severity,
        String message) {
}
