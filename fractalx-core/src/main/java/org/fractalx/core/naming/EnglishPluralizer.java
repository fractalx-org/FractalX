package org.fractalx.core.naming;

import java.util.Map;

/**
 * Pluralization and singularization for English field names used during
 * collection-to-ID field mapping (e.g. {@code children} → {@code child} → {@code childIds}).
 *
 * <p>Consults the irregular-plural map from {@link NamingConventions} first,
 * then falls back to standard English morphology rules.
 */
public final class EnglishPluralizer {

    private final Map<String, String> irregularPlurals;   // plural  → singular
    private final Map<String, String> irregularSingulars;  // singular → plural (derived)

    public EnglishPluralizer(Map<String, String> irregularPlurals) {
        this.irregularPlurals = Map.copyOf(irregularPlurals);
        Map<String, String> inv = new java.util.HashMap<>();
        irregularPlurals.forEach((plural, singular) -> inv.put(singular, plural));
        this.irregularSingulars = Map.copyOf(inv);
    }

    /**
     * Converts a plural English word to its singular form.
     * Preserves leading capitalization of the input.
     */
    public String singularize(String word) {
        if (word == null || word.isBlank()) return word;
        String lower = word.toLowerCase();
        // 1. Irregular
        if (irregularPlurals.containsKey(lower)) {
            return matchCase(word, irregularPlurals.get(lower));
        }
        // 2. -ies → -y  (stories → story), guard against short words
        if (lower.endsWith("ies") && lower.length() > 3) {
            return matchCase(word, lower.substring(0, lower.length() - 3) + "y");
        }
        // 3. -ves → -f  (knives → knife)
        if (lower.endsWith("ves") && lower.length() > 3) {
            return matchCase(word, lower.substring(0, lower.length() - 3) + "f");
        }
        // 4. -es after sibilants
        if ((lower.endsWith("ses") || lower.endsWith("xes") || lower.endsWith("zes")
                || lower.endsWith("ches") || lower.endsWith("shes")) && lower.length() > 3) {
            return matchCase(word, lower.substring(0, lower.length() - 2));
        }
        // 5. Plain -s  (not -ss)
        if (lower.endsWith("s") && !lower.endsWith("ss") && lower.length() > 1) {
            return matchCase(word, lower.substring(0, lower.length() - 1));
        }
        return word;  // already singular
    }

    /**
     * Converts a singular English word to its plural form.
     * Preserves leading capitalization of the input.
     */
    public String pluralize(String word) {
        if (word == null || word.isBlank()) return word;
        String lower = word.toLowerCase();
        // 1. Irregular
        if (irregularSingulars.containsKey(lower)) {
            return matchCase(word, irregularSingulars.get(lower));
        }
        // 2. Consonant + y → ies
        if (lower.endsWith("y") && lower.length() > 1 && !isVowel(lower.charAt(lower.length() - 2))) {
            return matchCase(word, lower.substring(0, lower.length() - 1) + "ies");
        }
        // 3. -f → -ves
        if (lower.endsWith("f") && lower.length() > 1) {
            return matchCase(word, lower.substring(0, lower.length() - 1) + "ves");
        }
        // 4. Sibilants → -es
        if (lower.endsWith("s") || lower.endsWith("x") || lower.endsWith("z")
                || lower.endsWith("ch") || lower.endsWith("sh")) {
            return matchCase(word, lower + "es");
        }
        // 5. Default -s
        return matchCase(word, lower + "s");
    }

    private static boolean isVowel(char c) {
        return "aeiou".indexOf(c) >= 0;
    }

    /** Preserves the leading capitalization of {@code original} in the result. */
    private static String matchCase(String original, String result) {
        if (original.isEmpty() || result.isEmpty()) return result;
        return Character.isUpperCase(original.charAt(0))
                ? Character.toUpperCase(result.charAt(0)) + result.substring(1)
                : result;
    }
}
