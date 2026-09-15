package org.tettyrs.msocr.correction.fieldtype;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.tettyrs.msocr.correction.DigitRepair;


public final class NipType {

    private NipType(){
    }

    public static FieldResult correct(String raw, LocalDate today){
        String compact = raw.replaceAll("\\s+", "");
        if (isDigits(compact)){
            if (hasValidStructure(compact, today)) {
                return new FieldResult(compact, 1.0, "rule", raw, null, 0, null, List.of());
            } else {
                return new FieldResult(compact, 0.50, "rule", raw, null, 0, null,
                    List.of(new Violation("nip_format_tidak_valid", List.of("nip"), "error", "Format NIP tidak valid")));
            }
        }
        Optional<DigitRepair.Result> repaired = DigitRepair.repair(compact, 2, "");
        if (repaired.isEmpty()) {
            return new FieldResult(compact, 0.50, "rule", raw, null, 0, null,
                List.of(new Violation("nip_format_tidak_valid", List.of("nip"), "error", "Format NIP tidak valid")));
        }
        String candidate = repaired.get().value();
        if (hasValidStructure(candidate, today)) {
            return new FieldResult(candidate, 0.70, "rule", raw, "confusion_map", 0, null, List.of());
        } else {
            return new FieldResult(compact, 0.30, "rule", raw, null, 0, null,
                List.of(new Violation("nip_format_tidak_valid", List.of("nip"), "error", "Format NIP tidak valid")));
        }
    }

    static boolean hasValidStructure(String nip, LocalDate today){
        if(nip.length() != 18 || !isDigits(nip)){
            return false;
        }

        LocalDate birth;
        try{
            birth = LocalDate.parse(nip.substring(0,8), DateTimeFormatter.BASIC_ISO_DATE);
        }catch (DateTimeException e){
            return false;
        }
        int tmtYear = Integer.parseInt(nip.substring(8,12));
        int tmtMonth = Integer.parseInt(nip.substring(12,14));
        char gender = nip.charAt(14);

        return birth.getYear() >= 1940
                && birth.getYear() <= today.getYear() - 17
                && tmtMonth >= 1 && tmtMonth <= 12
                && tmtYear >= birth.getYear() + 18
                && !YearMonth.of(tmtYear, tmtMonth).isAfter(YearMonth.from(today))
                && (gender == '1' || gender == '2')
                && !nip.endsWith("000");
    }


    private static boolean isDigits(String text){
        return  !text.isEmpty() && text.chars().allMatch(c -> c >= '0' && c <= '9');
    }

}
