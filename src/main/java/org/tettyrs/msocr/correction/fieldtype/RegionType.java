package org.tettyrs.msocr.correction.fieldtype;

import org.tettyrs.msocr.correction.LexiconMatcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegionType {
    private static final LexiconMatcher REGIONS;
    private static final Map<String, String> ALIASES;
    private static final Map<String, Set<String>> AMBIGUOUS_ALIASES = new LinkedHashMap<>();
    private static final Map<String, String> KECAMATAN_TO_REGION = new HashMap<>();
    private static final List<PostalRange> POSTAL_RANGES = new ArrayList<>();
    private static final Pattern PREFIX_PATTERN = Pattern.compile(
            "^(?:Provinsi|Prov\\.?|Kabupaten|Kab\\.?|Kota|Kota\\s+Administrasi)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE
    );

    private static class PostalRange {
        int min;
        int max;
        String region;

        PostalRange(int min, int max, String region) {
            this.min = min;
            this.max = max;
            this.region = region;
        }
    }

    static {
        try (InputStream is = RegionType.class.getClassLoader().getResourceAsStream("correction/regions.txt")) {
            if (is == null) {
                throw new IllegalStateException("Missing regions.txt in resources/correction/");
            }
            REGIONS = new LexiconMatcher(new String(is.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(l -> !l.isBlank() && !l.startsWith("#"))
                    .toList());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load regions.txt");
        }

        ALIASES = new LinkedHashMap<>();
        try (InputStream is = RegionType.class.getClassLoader().getResourceAsStream("correction/region_aliases.txt")) {
            if (is != null) {
                try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8)) {
                    while (scanner.hasNextLine()) {
                        String line = scanner.nextLine().trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        String[] parts = line.split("->", 2);
                        if (parts.length == 2) {
                            String key = parts[0].trim().toUpperCase();
                            String val = parts[1].trim();
                            ALIASES.putIfAbsent(key, val);
                            AMBIGUOUS_ALIASES.computeIfAbsent(key, k2 -> new LinkedHashSet<>()).add(val);
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load region_aliases.txt");
        }

        loadKecamatanMapping();
    }

    private static void loadKecamatanMapping() {
        try (InputStream is = RegionType.class.getClassLoader().getResourceAsStream("correction/kecamatan_mapping.txt")) {
            if (is != null) {
                try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8)) {
                    while (scanner.hasNextLine()) {
                        String line = scanner.nextLine().trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        String[] parts = line.split("->", 2);
                        if (parts.length == 2) {
                            String key = parts[0].trim().toUpperCase();
                            String val = parts[1].trim();
                            if (key.startsWith("KP_RANGE_")) {
                                String[] bounds = key.substring(9).split("-");
                                if (bounds.length == 2) {
                                    try {
                                        int min = Integer.parseInt(bounds[0].trim());
                                        int max = Integer.parseInt(bounds[1].trim());
                                        POSTAL_RANGES.add(new PostalRange(min, max, val));
                                    } catch (NumberFormatException ignored) {}
                                }
                            } else {
                                KECAMATAN_TO_REGION.put(key, val);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Optional file, ignore if missing
        }
    }

    private RegionType() {
    }

    public static FieldResult correct(String raw) {
        return correct(raw, null, null);
    }

    public static FieldResult correct(String raw, String kecamatan, String kodePos) {
        String text = raw.trim();
        String prefix = "";
        String candidate = text;


        Matcher prefixMatcher = PREFIX_PATTERN.matcher(text);
        if (prefixMatcher.matches()) {
            prefix = text.substring(0, prefixMatcher.start(1));
            candidate = prefixMatcher.group(1).trim();
        }

        String upperCandidate = candidate.toUpperCase();
        String resolvedCandidate = candidate;
        boolean isResolved = false;

        if (ALIASES.containsKey(upperCandidate)) {
            Set<String> candidates = AMBIGUOUS_ALIASES.get(upperCandidate);
            if (candidates != null && candidates.size() > 1) {
                // Ambiguous case
                if (kecamatan != null || kodePos != null) {
                    // Ada context → disambiguate, clear prefix to avoid duplication
                    resolvedCandidate = disambiguate(candidates, kecamatan, kodePos);
                    isResolved = true;
                    prefix = "";
                } else {
                    // No context → preserve input as-is
                    return new FieldResult(text, 1.0, "rule", raw, null, 0, null, List.of());
                }
            } else {
                // Single alias → resolve to alias value, clear prefix to avoid duplication
                resolvedCandidate = ALIASES.get(upperCandidate);
                isResolved = true;
                prefix = "";
            }
        }



        Optional<LexiconMatcher.Match> match = REGIONS.match(resolvedCandidate);
        if (match.isPresent()) {
            LexiconMatcher.Match m = match.get();
            String corrected = m.distance() > 0 ? preserveCase(resolvedCandidate, m.entry()) : m.entry();
            // For resolved aliases/disambiguated, convert to title case; for regular matches, preserve case pattern
            if (isResolved) {
                corrected = toTitleCase(corrected);
            }

            // Use matched entry, don't recombine with prefix (match already has full form)
            String value = isResolved ? corrected : (prefix.isEmpty() ? corrected : prefix + corrected);

            String correction = m.distance() > 0 ? "lexicon" : null;
            double confidence = m.distance() > 0 ? 0.70 : 1.0;
            return new FieldResult(value, confidence, "rule", raw, correction, 0, null, List.of());
        }
        return new FieldResult(text, 1.0, "rule", raw, null, 0, null, List.of());
    }

    private static String disambiguate(Set<String> candidates, String kecamatan, String kodePos) {
        if (kecamatan != null && !kecamatan.isBlank()) {
            String upperKec = kecamatan.trim().toUpperCase();
            if (KECAMATAN_TO_REGION.containsKey(upperKec)) {
                String mapped = KECAMATAN_TO_REGION.get(upperKec);
                for (String c : candidates) {
                    if (c.equalsIgnoreCase(mapped)) {
                        return c;
                    }
                }
            }
        }
        if (kodePos != null && !kodePos.isBlank()) {
            String kp = kodePos.trim();
            String mapped = KECAMATAN_TO_REGION.get("KP_" + kp);
            if (mapped == null) {
                try {
                    int kpInt = Integer.parseInt(kp);
                    for (PostalRange range : POSTAL_RANGES) {
                        if (kpInt >= range.min && kpInt <= range.max) {
                            mapped = range.region;
                            break;
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }
            
            if (mapped != null) {
                for (String c : candidates) {
                    if (c.equalsIgnoreCase(mapped)) {
                        return c;
                    }
                }
            }
        }
        return candidates.iterator().next();
    }

    private static String preserveCase(String original, String corrected) {
        if (original == null || corrected == null) {
            return corrected;
        }
        if (original.equals(original.toUpperCase())) {
            return corrected.toUpperCase();
        }
        if (original.equals(original.toLowerCase())) {
            return corrected.toLowerCase();
        }
        if (Character.isUpperCase(original.charAt(0)) && original.substring(1).equals(original.substring(1).toLowerCase())) {
            return Character.toUpperCase(corrected.charAt(0)) + corrected.substring(1).toLowerCase();
        }
        return corrected;
    }

    private static String toTitleCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String[] parts = input.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) result.append(" ");
            String part = parts[i];
            if (part.isEmpty()) continue;
            result.append(Character.toUpperCase(part.charAt(0)))
                  .append(part.substring(1).toLowerCase());
        }
        return result.toString();
    }

}
