package org.tettyrs.msocr.correction;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntUnaryOperator;

public final class LexiconMatcher {

    private final Map<String, String> entries = new LinkedHashMap<>();
    private final IntUnaryOperator maxDistance;


    public static int defaultMaxDistance(int length) {
        if (length <= 4) {
            return 0;
        }
        if (length <= 8) {
            return 1;
        }
        return length <= 16 ? 2 : 3;
    }

    static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "")
                .replace("rn", "m")
                .replace("cl", "d")
                .replace("vv", "w");
    }
    public LexiconMatcher(Collection<String> entries, IntUnaryOperator maxDistance) {
        for (String entry : entries) {
            String key = normalize(entry);
            if (!key.isEmpty()) {
                this.entries.putIfAbsent(key, entry);
            }
        }
        this.maxDistance = maxDistance;
    }

    public LexiconMatcher(Collection<String> entries) {
        this(entries, LexiconMatcher::defaultMaxDistance);
    }

    static int levenshtein(String a, String b){
        int[] prev = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j =0; j <= b.length(); j++){
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++){
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i-1) == b.charAt(j-1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j-1]+1, prev[j]+1), prev[j-1]+cost);
            }
            int[] swap = prev;
            prev = current;
            current = swap;
        }
        return prev[b.length()];
    }

    public record Match(String entry, int distance){
    }
    public Optional<Match> match(String candidate) {
        String needle = normalize(candidate);
        if (needle.isEmpty()) {
            return Optional.empty();
        }

        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        boolean tie = false;
        for (String key : entries.keySet()) {
            int distance = levenshtein(needle, key);
            if (distance < bestDistance) {
                best = key;
                bestDistance = distance;
                tie = false;
            } else if (distance == bestDistance) {
                tie = true;
            }
        }

        if (best == null || tie || bestDistance > maxDistance.applyAsInt(best.length())) {
            return Optional.empty();
        }
        return Optional.of(new Match(entries.get(best), bestDistance));
    }


}
