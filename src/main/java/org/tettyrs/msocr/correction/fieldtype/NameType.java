package org.tettyrs.msocr.correction.fieldtype;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class NameType {

    private static final Set<String> TITLES = Set.of(
            "Dr.", "dr.", "Ir.", "H.", "Hj.", "Drs.", "S.E.", "S.H.", "M.M.",
            "M.Si.", "M.Sc.", "M.Kom.", "M.H.", "M.A.", "M.T.", "M.Pd.", "M.Farm.",
            "M.Kes.", "M.K.M.", "M.Ars.", "M.Des.", "M.Kon.", "M.Sos.", "M.E.",
            "S.Kom.", "S.T.", "S.Pd.", "S.Farm.", "S.Kes.", "S.K.M.", "S.Ars.",
            "S.Des.", "S.Kon.", "S.Sos.", "S.Psi.", "S.Tr.",
            "Prof.", "prof.", "Raden", "R.", "Raden Mas", "R.M.", "Raden Nganten",
            "R.N.", "Raden Ayu", "R.A.", "Raden Roro", "R.R.", "Raden Ajeng",
            "R.Aj.", "Mas", "Mbak", "Bapak", "Bpk.", "Ibu", "Ibu.", "Ny.", "Nyonya"
    );

    private static final Pattern UNUSUAL_CHAR = Pattern.compile("[^A-Za-z\\s.,'\\-]");

    private NameType() {}

    public static FieldResult correct(String raw){
        String text = raw.trim();

        boolean hasUnusual = UNUSUAL_CHAR.matcher(text).find();
        if (hasUnusual) {
            return  new FieldResult(text, 0.50, "rule", raw, null, 0, null,
                    List.of(new Violation("nama_karakter_tidak_wajar", List.of("nama"),
                            "warning", "Nama memuat angka atau simbol tidak wajar")));
        }
        return  new FieldResult(text, 1.0, "rule", raw, null, 0, null, List.of());
    }
}
