package org.tettyrs.msocr.correction.fieldtype;

import java.util.List;
public record FieldResult(
        String value,
        Correction correction,
        double confidenceCap,
        List<Violation> violations
) {

    public static final double NO_CAP = 1.0;

    public static FieldResult accepted(String value){
        return new FieldResult(value, Correction.NONE, NO_CAP, List.of());
    }

    public static FieldResult corrected(String value, Correction correction, double cap){
        return new FieldResult(value, correction, cap, List.of());
    }

    public static FieldResult flagged(String value, double cap, Violation... violations){
        return new FieldResult(value, Correction.NONE, cap, List.of(violations));
    }

    public double applyTo(double baseConfidence){
        return Math.min(baseConfidence, confidenceCap);
    }
}
