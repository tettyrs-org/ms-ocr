package org.tettyrs.msocr.correction.fieldtype;

import java.util.List;

public class JumlahRincian {

    public static JumlahRincianResult validate(List<String> rincian, String total) {
        return validate(rincian, total, false);
    }

    public static JumlahRincianResult validate(List<String> rincian, String total, boolean rincianCorrected) {
        if (rincian == null || rincian.isEmpty()) {
            return new JumlahRincianResult(true, null, total, null, 1.0);
        }

        long sum = rincian.stream()
                .mapToLong(r -> {
                    try {
                        return Long.parseLong(r);
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                })
                .sum();

        // Jika total tidak ada
        if (total == null || total.isEmpty()) {
            if (rincianCorrected) {
                // Jangan derive jika rincian ada yang dikoreksi
                return new JumlahRincianResult(false, null, null, null, 0.30);
            }
            // Derive total dari rincian
            return new JumlahRincianResult(true, "derived", String.valueOf(sum), null, 0.60);
        }

        // Parse total
        long totalValue;
        try {
            totalValue = Long.parseLong(total);
        } catch (NumberFormatException e) {
            return new JumlahRincianResult(false, null, total, null, 0.30);
        }

        // Check if total matches sum
        if (totalValue == sum) {
            return new JumlahRincianResult(true, null, total, null, 1.0);
        } else {
            // Mismatch: flag tapi jangan ubah
            return new JumlahRincianResult(false, null, total, "jumlah_rincian_tidak_cocok", 0.50);
        }
    }

    public static class JumlahRincianResult {
        private final boolean valid;
        private final String correction;
        private final String totalValue;
        private final String violation;
        private final double confidence;

        public JumlahRincianResult(boolean valid, String correction, String totalValue, String violation, double confidence) {
            this.valid = valid;
            this.correction = correction;
            this.totalValue = totalValue;
            this.violation = violation;
            this.confidence = confidence;
        }

        public boolean isValid() {
            return valid;
        }

        public String correction() {
            return correction;
        }

        public String totalValue() {
            return totalValue;
        }

        public String violation() {
            return violation;
        }

        public double confidence() {
            return confidence;
        }

        public boolean canDerive() {
            return valid && "derived".equals(correction);
        }
    }
}
