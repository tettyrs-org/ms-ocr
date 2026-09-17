package org.tettyrs.msocr.correction;

import java.util.*;

public class SpelledNumberParser {
    private static final Map<String, Long> VALUES = new LinkedHashMap<>();
    private static final Map<String, Long> SCALES = new LinkedHashMap<>();
    private static final Set<String> MULTIPLIERS = Set.of("belas", "puluh", "ratus");
    private static final Set<String> IGNORED = Set.of("rupiah", "hari");
    private static final LexiconMatcher WORDS;

    static {
        String[] units = {"satu", "dua", "tiga", "empat", "lima", "enam", "tujuh", "delapan", "sembilan"};
        for (int i = 0; i < units.length; i++) {
            VALUES.put(units[i], (long) i + 1 );
        }

        VALUES.put("sepuluh", 10L);
        VALUES.put("sebelas", 11L);
        VALUES.put("seratus", 100L);

        SCALES.put("ribu", 1_000L);
        SCALES.put("juta", 1_000_000L);
        SCALES.put("miliar", 1_000_000_000L);
        SCALES.put("milyar", 1_000_000_000L);
        SCALES.put("triliun", 1_000_000_000_000L);

        List<String> dictionary = new ArrayList<>();
        dictionary.addAll(VALUES.keySet());
        dictionary.addAll(SCALES.keySet());
        dictionary.addAll(MULTIPLIERS);
        dictionary.addAll(IGNORED);
        dictionary.add("seribu");
        WORDS = new LexiconMatcher(dictionary,
                length -> length <= 4 ? 1 : LexiconMatcher.defaultMaxDistance(length));
    }

    private long total;
    private long group;
    private long pending;
    private long lastScale = Long.MAX_VALUE;

    private SpelledNumberParser(){}

    private static boolean isKnown(String word) {
        return VALUES.containsKey(word) || SCALES.containsKey(word)
                || MULTIPLIERS.contains(word) || IGNORED.contains(word) || word.equals("seribu");
    }


    private static Optional<List<String>> split(String word){
        if (word.isEmpty()) {
            return Optional.of(new ArrayList<>());
        }

        for (int end = word.length(); end > 0 ; end--) {
            String  head = word.substring(0, end);
            if (!isKnown(head)) {
                continue;
            }
            Optional<List<String>> rest = split(word.substring(end));
            if (rest.isPresent()) {
                List<String> parts = new ArrayList<>();
                parts.add(head);
                parts.addAll(rest.get());
                return Optional.of(parts);
            }
        }
        return Optional.empty();
    }

    private boolean applyMultiplier(String word) {
        if (pending == 0) {
            return false;
        }
        group += switch (word) {
            case "belas" -> 10 + pending;
            case "puluh" -> pending * 10;
            default -> pending * 100;
        };
        pending = 0;
        return true;
    }

    private boolean applyScale(long chunk, long scale) {
        if (chunk == 0 || scale >= lastScale) {
            return false;
        }
        total += chunk * scale;
        lastScale = scale;
        group = 0;
        pending = 0;
        return true;
    }

    private Optional<Long> evaluate(List<String> words) {
        for (String word : words) {
            if (IGNORED.contains(word)) {
                continue;
            }
            boolean ok;
            if (word.equals("seribu")) {
                ok = pending == 0 && group == 0 && applyScale(1, 1_000L);
            } else if (SCALES.containsKey(word)) {
                ok = applyScale(group + pending, SCALES.get(word));
            } else if (MULTIPLIERS.contains(word)) {
                ok = applyMultiplier(word);
            } else if (VALUES.containsKey(word)) {
                ok = pending == 0;
                if (ok) {
                    if (VALUES.get(word) < 10) {
                        pending = VALUES.get(word);
                    } else {
                        group += VALUES.get(word);
                    }
                }
            } else {
                ok = false;
            }
            if (!ok) {
                return Optional.empty();
            }
        }
        return Optional.of(total + group + pending);
    }

    public static Optional<Long> parse(String text){
        List<String> words = new ArrayList<>();
        for (String raw: text.split("\\s+") ){
            String cleaned = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
            if (cleaned.isEmpty()) {
                continue;
            }
            Optional<LexiconMatcher.Match> match = WORDS.match((cleaned));
            if (match.isPresent()) {
                words.add(match.get().entry());
                continue;
            }
            Optional<List<String>> glued = split(cleaned);
            if (glued.isEmpty()) {
                return Optional.empty();
            }
            words.addAll(glued.get());
        }
        return words.isEmpty() ? Optional.empty() : new SpelledNumberParser().evaluate(words);
    }
}
