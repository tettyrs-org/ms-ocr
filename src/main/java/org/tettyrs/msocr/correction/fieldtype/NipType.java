package org.tettyrs.msocr.correction.fieldtype;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import org.tettyrs.msocr.correction.DigitRepair;


public final class NipType {

    private NipType(){
    }

    public static FieldResult correct(String raw, LocalDate today){
        String compact = raw.replaceAll("\\s+", "");
        if (isDigits(compact)){
            return hasValidStructure(compact, today)
                    ? FieldResult.accepted(compact)
                    : FieldResult.flagged(compact, 0.50, Violation.NIP_FORMAT_TIDAK_VALID);
        }
        Optional<DigitRepair.Result> repaired = DigitRepair.repair(compact, 2, "");
        if (repaired.isEmpty()) {
            return  FieldResult.flagged(compact, 0.50, Violation.NIP_FORMAT_TIDAK_VALID);
        }
        String candidate = repaired.get().value();
        return hasValidStructure(candidate, today)
                ? FieldResult.corrected(candidate, Correction.CONFUSION_MAP, 0.70)
                : FieldResult.flagged(compact, 0.30, Violation.NIP_FORMAT_TIDAK_VALID);

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
