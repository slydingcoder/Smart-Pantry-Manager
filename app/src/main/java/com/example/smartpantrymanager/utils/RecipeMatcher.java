package com.example.smartpantrymanager.utils;

import com.example.smartpantrymanager.models.Ingredient;
import com.example.smartpantrymanager.models.Recipe;
import com.example.smartpantrymanager.models.RecipeIngredient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure-Java matching logic (no Android classes, so it is unit-tested on the JVM).
 *
 * STRICT RULE (brief section 2.3): a recipe is suggested only if EVERY ingredient is in the
 * pantry in at least the required quantity. 4 of 5 ingredients = not suggested.
 *
 * On top of that rule this class provides:
 *  - {@link #almostThere}: recipes with exactly ONE unsatisfied ingredient (bonus list)
 *  - {@link #suggestRanked}: the strict list, ordered by how many soon-to-expire ingredients it uses
 *  - {@link #planCook}: what to deduct from the pantry when a recipe is cooked
 */
public class RecipeMatcher {

    /** An ingredient counts as "expiring soon" if it expires today or within this many days. */
    public static final int EXPIRING_SOON_DAYS = 3;

    private static final double EPS = 1e-9;

    // ------------------------------------------------------------------ names

    /**
     * Normalises names so "Tomatoes", " tomato " and "TOMATO" are equal, and so that regional
     * synonyms ("aubergine"/"eggplant", "coriander"/"cilantro") collapse to one canonical name.
     */
    public static String normalise(String name) {
        String n = name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return Synonyms.canonical(singular(n));
    }

    private static String singular(String n) {
        if (n.endsWith("ies") && n.length() > 4) return n.substring(0, n.length() - 3) + "y"; // berries -> berry
        if (n.endsWith("oes")) return n.substring(0, n.length() - 2);                          // tomatoes -> tomato
        if (n.endsWith("s") && !n.endsWith("ss") && n.length() > 3) return n.substring(0, n.length() - 1); // eggs -> egg
        return n;
    }

    private static String key(String name, String unit) {
        return normalise(name) + "|" + Units.family(unit);
    }

    // ------------------------------------------------------------------ stock & needs

    /** Totals the pantry per (ingredient, unit family), in base units. Duplicate rows are summed. */
    public static Map<String, Double> buildStock(List<Ingredient> pantry) {
        Map<String, Double> stock = new HashMap<>();
        for (Ingredient p : pantry) {
            if (p.quantity <= 0) continue;
            String k = key(p.name, p.unit);
            Double current = stock.get(k);
            stock.put(k, (current == null ? 0 : current) + Units.toBase(p.quantity, p.unit));
        }
        return stock;
    }

    /** What a recipe needs per (ingredient, unit family). A name listed twice is summed. */
    private static final class Need {
        final RecipeIngredient first;   // used for display (name + unit)
        double base;                    // total required, base units
        Need(RecipeIngredient first) { this.first = first; }
    }

    private static Map<String, Need> needs(Recipe r) {
        Map<String, Need> m = new LinkedHashMap<>();
        for (RecipeIngredient ri : r.ingredients) {
            String k = key(ri.name, ri.unit);
            Need n = m.get(k);
            if (n == null) { n = new Need(ri); m.put(k, n); }
            n.base += Units.toBase(ri.quantity, ri.unit);
        }
        return m;
    }

    /** One unsatisfied ingredient of a recipe. */
    private static final class Gap {
        final RecipeIngredient ingredient;
        final double shortBy;     // in the ingredient's own unit
        final boolean absent;     // true = not in the pantry at all, false = in pantry but not enough
        Gap(RecipeIngredient i, double s, boolean a) { ingredient = i; shortBy = s; absent = a; }
    }

    private static List<Gap> gaps(Recipe r, Map<String, Double> stock) {
        List<Gap> out = new ArrayList<>();
        for (Map.Entry<String, Need> e : needs(r).entrySet()) {
            Need n = e.getValue();
            Double have = stock.get(e.getKey());
            double haveBase = have == null ? 0 : have;
            if (have == null || haveBase + EPS < n.base) {
                double factor = Units.toBase(1, n.first.unit);
                out.add(new Gap(n.first, (n.base - haveBase) / factor, have == null));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ strict rule

    /** True if the pantry stock covers this single ingredient line (same rule as the strict match). */
    public static boolean hasIngredient(RecipeIngredient ri, Map<String, Double> stock) {
        Double have = stock.get(key(ri.name, ri.unit));
        return have != null && have + EPS >= Units.toBase(ri.quantity, ri.unit);
    }

    public static boolean canMake(Recipe recipe, Map<String, Double> stock) {
        if (recipe.ingredients.isEmpty()) return false;
        return gaps(recipe, stock).isEmpty();
    }

    /** The strict list (unordered, as before). */
    public static List<Recipe> suggest(List<Recipe> all, List<Ingredient> pantry) {
        Map<String, Double> stock = buildStock(pantry);
        List<Recipe> result = new ArrayList<>();
        for (Recipe r : all) if (canMake(r, stock)) result.add(r);
        return result;
    }

    // ------------------------------------------------------------------ expiry usage

    private static final long NO_EXPIRY = Long.MAX_VALUE;

    /** Earliest expiry day per ingredient key, for stock expiring within [today, today + EXPIRING_SOON_DAYS]. */
    private static Map<String, Long> expiringByKey(List<Ingredient> pantry, long today) {
        Map<String, Long> m = new HashMap<>();
        for (Ingredient p : pantry) {
            if (p.quantity <= 0) continue;
            long e = ExpiryDates.parse(p.expiry);
            if (e == ExpiryDates.NO_DATE || e < today || e > today + EXPIRING_SOON_DAYS) continue;
            String k = key(p.name, p.unit);
            Long cur = m.get(k);
            if (cur == null || e < cur) m.put(k, e);
        }
        return m;
    }

    private static final class Usage {
        int count; final List<String> names = new ArrayList<>(); long soonest = NO_EXPIRY;
    }

    private static Usage usage(Recipe r, Map<String, Long> expiring) {
        Usage u = new Usage();
        for (Map.Entry<String, Need> e : needs(r).entrySet()) {
            Long exp = expiring.get(e.getKey());
            if (exp == null) continue;
            u.count++;
            u.names.add(e.getValue().first.name);
            if (exp < u.soonest) u.soonest = exp;
        }
        return u;
    }

    // ------------------------------------------------------------------ expiry-first ranking

    /** A recipe the pantry can fully cover, with how many soon-to-expire ingredients it would use. */
    public static final class Ranked {
        public final Recipe recipe;
        public final int expiringCount;
        public final List<String> expiringNames;
        public final long soonestExpiry;   // epoch day, or Long.MAX_VALUE if none
        Ranked(Recipe r, Usage u) {
            recipe = r; expiringCount = u.count; expiringNames = u.names; soonestExpiry = u.soonest;
        }
    }

    /**
     * Strict list, best first: most soon-to-expire ingredients used, then the one whose
     * earliest-expiring ingredient runs out soonest, then alphabetical.
     *
     * @param today epoch day (see {@link ExpiryDates#today()}); passed in so tests are deterministic
     */
    public static List<Ranked> suggestRanked(List<Recipe> all, List<Ingredient> pantry, long today) {
        Map<String, Double> stock = buildStock(pantry);
        Map<String, Long> expiring = expiringByKey(pantry, today);
        List<Ranked> out = new ArrayList<>();
        for (Recipe r : all) if (canMake(r, stock)) out.add(new Ranked(r, usage(r, expiring)));
        Collections.sort(out, new Comparator<Ranked>() {
            @Override public int compare(Ranked a, Ranked b) {
                if (a.expiringCount != b.expiringCount) return b.expiringCount - a.expiringCount;
                if (a.soonestExpiry != b.soonestExpiry) return a.soonestExpiry < b.soonestExpiry ? -1 : 1;
                return a.recipe.name.compareToIgnoreCase(b.recipe.name);
            }
        });
        return out;
    }

    // ------------------------------------------------------------------ "Almost there"

    /**
     * A recipe that is exactly ONE ingredient away from being cookable. The one gap is either an
     * ingredient that is not in the pantry at all ({@link #absent}) or one you have but not enough of.
     */
    public static final class NearMiss {
        public final Recipe recipe;
        public final RecipeIngredient ingredient;   // the one problem ingredient
        public final double shortBy;                // missing amount, in ingredient.unit
        public final boolean absent;
        public final int expiringCount;
        public final List<String> expiringNames;
        NearMiss(Recipe r, Gap g, Usage u) {
            recipe = r; ingredient = g.ingredient; shortBy = g.shortBy; absent = g.absent;
            expiringCount = u.count; expiringNames = u.names;
        }
    }

    /** Recipes with exactly one unsatisfied ingredient; those using expiring food first, then A-Z. */
    public static List<NearMiss> almostThere(List<Recipe> all, List<Ingredient> pantry, long today) {
        Map<String, Double> stock = buildStock(pantry);
        Map<String, Long> expiring = expiringByKey(pantry, today);
        List<NearMiss> out = new ArrayList<>();
        for (Recipe r : all) {
            if (r.ingredients.isEmpty()) continue;
            List<Gap> g = gaps(r, stock);
            if (g.size() == 1) out.add(new NearMiss(r, g.get(0), usage(r, expiring)));
        }
        Collections.sort(out, new Comparator<NearMiss>() {
            @Override public int compare(NearMiss a, NearMiss b) {
                if (a.expiringCount != b.expiringCount) return b.expiringCount - a.expiringCount;
                return a.recipe.name.compareToIgnoreCase(b.recipe.name);
            }
        });
        return out;
    }

    // ------------------------------------------------------------------ "Cook this"

    /** New state of one pantry row after cooking. {@link #remove()} = it reached zero, delete the row. */
    public static final class PantryChange {
        public final long pantryId;
        public final double newQuantity;   // in the row's own unit
        PantryChange(long id, double q) { pantryId = id; newQuantity = q; }
        public boolean remove() { return newQuantity <= 0; }
    }

    /**
     * Works out which pantry rows change when the recipe is cooked. Units are converted
     * (1 kg bag minus 500 g leaves 0.5 kg), and when several rows hold the same ingredient the
     * one expiring FIRST is used up first. Does not touch the database.
     *
     * @throws IllegalStateException if the pantry cannot fully cover the recipe
     */
    public static List<PantryChange> planCook(Recipe recipe, List<Ingredient> pantry) {
        if (!canMake(recipe, buildStock(pantry))) {
            throw new IllegalStateException("Pantry cannot cover \"" + recipe.name + "\"");
        }
        // group usable pantry rows by ingredient key, soonest expiry first
        Map<String, List<Ingredient>> rows = new HashMap<>();
        for (Ingredient p : pantry) {
            if (p.quantity <= 0) continue;
            String k = key(p.name, p.unit);
            List<Ingredient> l = rows.get(k);
            if (l == null) { l = new ArrayList<>(); rows.put(k, l); }
            l.add(p);
        }
        Comparator<Ingredient> useFirst = new Comparator<Ingredient>() {
            @Override public int compare(Ingredient a, Ingredient b) {
                long ea = expiryOrMax(a), eb = expiryOrMax(b);
                if (ea != eb) return ea < eb ? -1 : 1;
                return a.id < b.id ? -1 : (a.id == b.id ? 0 : 1);
            }
        };

        List<PantryChange> changes = new ArrayList<>();
        for (Map.Entry<String, Need> e : needs(recipe).entrySet()) {
            double remaining = e.getValue().base;
            List<Ingredient> list = rows.get(e.getKey());
            Collections.sort(list, useFirst);
            for (Ingredient p : list) {
                if (remaining <= EPS) break;
                double factor = Units.toBase(1, p.unit);
                double rowBase = Units.toBase(p.quantity, p.unit);
                double take = Math.min(rowBase, remaining);
                remaining -= take;
                double newQty = (rowBase - take) / factor;
                newQty = Math.round(newQty * 1e6) / 1e6;           // kill float dust (0.30000000000000004)
                changes.add(new PantryChange(p.id, newQty < 1e-6 ? 0 : newQty));
            }
        }
        return changes;
    }

    private static long expiryOrMax(Ingredient p) {
        long e = ExpiryDates.parse(p.expiry);
        return e == ExpiryDates.NO_DATE ? NO_EXPIRY : e;
    }
}
