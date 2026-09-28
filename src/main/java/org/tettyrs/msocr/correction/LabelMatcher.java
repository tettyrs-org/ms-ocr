package org.tettyrs.msocr.correction;

import org.tettyrs.msocr.correction.tokens.Line;
import org.tettyrs.msocr.correction.tokens.Token;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class LabelMatcher {
    private static final Map<String, Set<String>> LABELS_BY_DOCTYPE = new HashMap<>();

    static {
        // Kamus label untuk SURAT_TUGAS
        LABELS_BY_DOCTYPE.put("SURAT_TUGAS", Set.of(
                "Nomor", "Dasar", "Menimbang", "Menugaskan kepada", "Kepada",
                "Nama", "NIP", "Pangkat/Golongan", "Jabatan", "Untuk",
                "Maksud Perjalanan", "Tempat Tujuan", "Alat Angkut",
                "Tanggal Berangkat", "Tanggal Kembali", "Lama Perjalanan",
                "Pembebanan Anggaran"
        ));
    }

    public static LabelMatchResult match(Line line, String docType) {
        if (line == null || line.tokens().isEmpty()) {
            return null;
        }

        Set<String> labels = LABELS_BY_DOCTYPE.getOrDefault(docType, Set.of());
        if (labels.isEmpty()) {
            return null;
        }

        // Coba match label di awal baris
        // Label bisa 1-3 kata, dengan M2 matching
        for (int tokenCount = 1; tokenCount <= Math.min(3, line.tokens().size()); tokenCount++) {
            String candidate = buildCandidate(line, tokenCount);

            for (String label : labels) {
                int distance = levenshteinDistance(candidate.toUpperCase(), label.toUpperCase());

                // Short labels (panjang <= 4) perlu exact match; lebih panjang bisa fuzzy
                boolean isShortLabel = candidate.length() <= 4;
                int maxDistance = isShortLabel ? 0 : 1;

                // Match jika exact atau jarak <= maxDistance
                if (distance <= maxDistance) {
                    return new LabelMatchResult(label, candidate, distance, tokenCount);
                }
            }
        }

        return null;
    }

    private static String buildCandidate(Line line, int tokenCount) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokenCount && i < line.tokens().size(); i++) {
            if (i > 0) sb.append(" ");
            sb.append(line.tokens().get(i).text());
        }
        return sb.toString();
    }

    private static int levenshteinDistance(String a, String b) {
        int[] costs = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            costs[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            int nw = costs[0];
            costs[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int c = Math.min(1 + Math.min(costs[j], costs[j - 1]),
                        a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = c;
            }
        }
        return costs[b.length()];
    }

    public static class LabelMatchResult {
        private final String label;
        private final String candidate;
        private final int distance;
        private final int tokenCount;

        public LabelMatchResult(String label, String candidate, int distance, int tokenCount) {
            this.label = label;
            this.candidate = candidate;
            this.distance = distance;
            this.tokenCount = tokenCount;
        }

        public String label() {
            return label;
        }

        public String correctedLabel() {
            return label;
        }

        public int distance() {
            return distance;
        }

        public int valueStartIndex() {
            // Value mulai setelah label tokens dan separators yang dikonsumsi
            int idx = tokenCount;

            // Konsumsi separator jika ada (-, :, ., dll)
            if (idx < 10) { // safety check
                idx++; // skip one separator
            }

            return idx;
        }
    }
}
