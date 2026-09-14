package org.tettyrs.msocr.correction.fieldtype;

import  java.util.Locale;
public enum Violation {
    NIP_FORMAT_TIDAK_VALID,
    TANGGAL_AMBIGU,
    TERBILANG_TIDAK_COCOK,
    NOMINAL_FORMAT_TIDAK_VALID;

    public String code(){
        return name().toLowerCase(Locale.ROOT);
    }
}
