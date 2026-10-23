package com.example.smartpantrymanager.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps regional / alternative ingredient names onto ONE canonical name, so that a recipe
 * written with "coriander" is satisfied by a pantry item called "cilantro".
 *
 * Keys must already be lower-case and singular (RecipeMatcher.normalise() singularises
 * first, then calls {@link #canonical(String)}). Whole-name matches only: "ground coriander"
 * (a spice) is deliberately NOT mapped to "cilantro" (a herb).
 */
public final class Synonyms {
    private static final Map<String, String> CANONICAL = new HashMap<>();

    private static void alias(String canonical, String... aliases) {
        for (String a : aliases) CANONICAL.put(a, canonical);
    }

    static {
        alias("eggplant", "aubergine", "brinjal");
        alias("cilantro", "coriander", "fresh coriander", "coriander leaf");
        alias("zucchini", "courgette");
        alias("arugula", "rocket");
        alias("bell pepper", "capsicum");
        alias("scallion", "spring onion", "green onion");
        alias("shrimp", "prawn");
        alias("beet", "beetroot");
        alias("rutabaga", "swede");
        alias("cornstarch", "cornflour");
        alias("powdered sugar", "icing sugar");
        alias("chickpea", "garbanzo", "garbanzo bean");
        alias("chili", "chilli");
        alias("chili powder", "chilli powder");
    }

    private Synonyms() {}

    /** @param singular lower-case, trimmed, singularised name */
    public static String canonical(String singular) {
        String c = CANONICAL.get(singular);
        return c != null ? c : singular;
    }
}
