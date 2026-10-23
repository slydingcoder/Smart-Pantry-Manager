package com.example.smartpantrymanager.utils;

import com.example.smartpantrymanager.models.Ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Search box logic for the pantry list. Understands plurals and synonyms ("aubergine" finds "Eggplant"). */
public final class PantryFilter {
    private PantryFilter() {}

    public static List<Ingredient> filter(List<Ingredient> items, String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return items;
        String nq = RecipeMatcher.normalise(q);
        List<Ingredient> out = new ArrayList<>();
        for (Ingredient p : items) {
            String raw = p.name.toLowerCase(Locale.ROOT);
            if (raw.contains(q) || RecipeMatcher.normalise(p.name).contains(nq)) out.add(p);
        }
        return out;
    }
}
