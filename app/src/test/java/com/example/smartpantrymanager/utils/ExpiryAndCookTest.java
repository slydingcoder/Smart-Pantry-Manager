package com.example.smartpantrymanager.utils;

import com.example.smartpantrymanager.models.Ingredient;
import com.example.smartpantrymanager.models.Recipe;
import com.example.smartpantrymanager.models.RecipeIngredient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Expiry-first ranking, "Cook this" deduction, pantry search and date parsing. */
public class ExpiryAndCookTest {

    private static final long TODAY = ExpiryDates.parse("2026-10-20");

    private static Ingredient item(long id, String name, double qty, String unit, String expiry) {
        return new Ingredient(id, name, qty, unit, expiry);
    }

    private static Recipe recipe(String name, RecipeIngredient... ings) {
        Recipe r = new Recipe(1, name, "steps");
        r.ingredients.addAll(Arrays.asList(ings));
        return r;
    }

    private static RecipeIngredient ing(String n, double q, String u) { return new RecipeIngredient(n, q, u); }

    private static RecipeMatcher.PantryChange change(List<RecipeMatcher.PantryChange> l, long id) {
        for (RecipeMatcher.PantryChange c : l) if (c.pantryId == id) return c;
        return null;
    }

    // ---------- expiry-first ranking ----------
    @Test public void recipeUsingMoreExpiringIngredients_ranksFirst() {
        Recipe plain = recipe("A Plain", ing("rice", 100, "g"));
        Recipe waste = recipe("Z Waste-saver", ing("milk", 100, "ml"), ing("egg", 1, "pcs"));
        List<Ingredient> pantry = Arrays.asList(
                item(1, "rice", 500, "g", "2027-01-01"),
                item(2, "milk", 1, "l", "2026-10-21"),      // tomorrow
                item(3, "egg", 6, "pcs", "2026-10-22"));    // in 2 days
        List<RecipeMatcher.Ranked> r = RecipeMatcher.suggestRanked(Arrays.asList(plain, waste), pantry, TODAY);
        assertEquals("Z Waste-saver", r.get(0).recipe.name);      // wins despite alphabetical order
        assertEquals(2, r.get(0).expiringCount);
        assertEquals(0, r.get(1).expiringCount);
    }

    @Test public void countBeatsUrgency_twoItemsExpiringLaterOutrankOneExpiringTomorrow() {
        Recipe one = recipe("One urgent", ing("milk", 100, "ml"));
        Recipe two = recipe("Two less urgent", ing("egg", 1, "pcs"), ing("butter", 10, "g"));
        List<Ingredient> pantry = Arrays.asList(
                item(1, "milk", 1, "l", "2026-10-21"),             // tomorrow
                item(2, "egg", 6, "pcs", "2026-10-23"),            // in 3 days (edge of the window)
                item(3, "butter", 250, "g", "2026-10-23"));
        List<RecipeMatcher.Ranked> r = RecipeMatcher.suggestRanked(Arrays.asList(one, two), pantry, TODAY);
        assertEquals("Two less urgent", r.get(0).recipe.name);
        assertEquals(2, r.get(0).expiringCount);
    }

    @Test public void tieOnCount_soonestExpiryWins_thenAlphabetical() {
        Recipe a = recipe("A", ing("milk", 100, "ml"));
        Recipe b = recipe("B", ing("egg", 1, "pcs"));
        List<Ingredient> pantry = Arrays.asList(
                item(1, "milk", 1, "l", "2026-10-22"), item(2, "egg", 6, "pcs", "2026-10-21"));
        List<RecipeMatcher.Ranked> r = RecipeMatcher.suggestRanked(Arrays.asList(a, b), pantry, TODAY);
        assertEquals("B", r.get(0).recipe.name);                  // egg expires first
    }

    @Test public void expiredOrFarAwayOrUndatedItems_doNotCountAsExpiringSoon() {
        Recipe r1 = recipe("Expired", ing("milk", 100, "ml"));
        Recipe r2 = recipe("Far", ing("egg", 1, "pcs"));
        Recipe r3 = recipe("Undated", ing("rice", 10, "g"));
        List<Ingredient> pantry = Arrays.asList(
                item(1, "milk", 1, "l", "2026-10-19"),             // yesterday
                item(2, "egg", 6, "pcs", "2026-10-24"),            // 4 days > window of 3
                item(3, "rice", 1, "kg", null));
        for (RecipeMatcher.Ranked r : RecipeMatcher.suggestRanked(Arrays.asList(r1, r2, r3), pantry, TODAY))
            assertEquals(0, r.expiringCount);
    }

    @Test public void expiringToday_counts_andNamesAreReported() {
        Recipe r = recipe("Eggs", ing("egg", 1, "pcs"));
        List<Ingredient> pantry = Arrays.asList(item(1, "Eggs", 6, "pcs", "2026-10-20"));
        RecipeMatcher.Ranked ranked = RecipeMatcher.suggestRanked(Arrays.asList(r), pantry, TODAY).get(0);
        assertEquals(1, ranked.expiringCount);
        assertEquals("egg", ranked.expiringNames.get(0));
    }

    @Test public void rankingOnlyReordersTheStrictList() {
        Recipe cant = recipe("Cant", ing("milk", 100, "ml"), ing("saffron", 1, "g"));
        List<Ingredient> pantry = Arrays.asList(item(1, "milk", 1, "l", "2026-10-21"));
        assertTrue(RecipeMatcher.suggestRanked(Arrays.asList(cant), pantry, TODAY).isEmpty());
    }

    // ---------- cook this ----------
    @Test public void cook_deductsQuantity() {
        Recipe r = recipe("Eggs", ing("egg", 3, "pcs"));
        List<RecipeMatcher.PantryChange> c = RecipeMatcher.planCook(r, Arrays.asList(item(7, "egg", 10, "pcs", null)));
        assertEquals(1, c.size());
        assertEquals(7.0, change(c, 7).newQuantity, 1e-9);
        assertFalse(change(c, 7).remove());
    }

    @Test public void cook_removesItemsThatReachZero() {
        Recipe r = recipe("Eggs", ing("egg", 3, "pcs"));
        RecipeMatcher.PantryChange ch = RecipeMatcher.planCook(r, Arrays.asList(item(7, "egg", 3, "pcs", null))).get(0);
        assertTrue(ch.remove());
    }

    @Test public void cook_convertsUnits_oneKgMinusFiveHundredGramsLeavesHalfAKilo() {
        Recipe r = recipe("Bread", ing("flour", 500, "g"));
        RecipeMatcher.PantryChange ch = RecipeMatcher.planCook(r, Arrays.asList(item(1, "flour", 1, "kg", null))).get(0);
        assertEquals(0.5, ch.newQuantity, 1e-9);
    }

    @Test public void cook_usesTheEarliestExpiringRowFirst_andSpillsOverToTheNext() {
        Recipe r = recipe("Bread", ing("flour", 300, "g"));
        List<Ingredient> pantry = Arrays.asList(
                item(1, "flour", 500, "g", "2027-03-01"),
                item(2, "flour", 200, "g", "2026-10-22"),          // expires first -> used up first
                item(3, "flour", 900, "g", null));                 // untouched
        List<RecipeMatcher.PantryChange> c = RecipeMatcher.planCook(r, pantry);
        assertTrue(change(c, 2).remove());                         // 200 g fully used
        assertEquals(400.0, change(c, 1).newQuantity, 1e-9);       // remaining 100 g taken from row 1
        assertEquals(null, change(c, 3));
    }

    @Test public void cook_whenExpiryDatesTie_usesTheOlderRowFirst() {
        Recipe r = recipe("Bread", ing("flour", 100, "g"));
        List<Ingredient> pantry = Arrays.asList(
                item(9, "flour", 500, "g", "2026-11-01"), item(4, "flour", 500, "g", "2026-11-01"));
        List<RecipeMatcher.PantryChange> c = RecipeMatcher.planCook(r, pantry);
        assertEquals(1, c.size());
        assertEquals(4L, c.get(0).pantryId);                       // lower id (added earlier) goes first
    }

    @Test public void cook_handlesPluralNamesAndSynonyms() {
        Recipe r = recipe("Salsa", ing("coriander", 2, "tbsp"), ing("tomato", 2, "pcs"));
        List<Ingredient> pantry = Arrays.asList(item(1, "Cilantro", 5, "tbsp", null), item(2, "Tomatoes", 2, "pcs", null));
        List<RecipeMatcher.PantryChange> c = RecipeMatcher.planCook(r, pantry);
        assertEquals(3.0, change(c, 1).newQuantity, 1e-9);
        assertTrue(change(c, 2).remove());
    }

    @Test public void cook_hasNoFloatingPointDust() {
        Recipe r = recipe("X", ing("milk", 100, "ml"));
        RecipeMatcher.PantryChange ch = RecipeMatcher.planCook(r, Arrays.asList(item(1, "milk", 0.4, "l", null))).get(0);
        assertEquals(0.3, ch.newQuantity, 0.0);                    // exactly 0.3, not 0.30000000000000004
    }

    @Test public void cook_refusesWhenPantryCannotCoverRecipe() {
        Recipe r = recipe("Eggs", ing("egg", 3, "pcs"));
        try {
            RecipeMatcher.planCook(r, Arrays.asList(item(1, "egg", 2, "pcs", null)));
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) { /* ok */ }
    }

    // ---------- pantry search ----------
    @Test public void search_isCaseInsensitiveAndMatchesPartialNames() {
        List<Ingredient> p = Arrays.asList(item(1, "Tomatoes", 1, "pcs", null), item(2, "Rice", 1, "kg", null));
        assertEquals(1, PantryFilter.filter(p, "tom").size());
        assertEquals(1, PantryFilter.filter(p, "RICE").size());
        assertEquals(2, PantryFilter.filter(p, "  ").size());
        assertEquals(0, PantryFilter.filter(p, "cheese").size());
    }

    @Test public void search_understandsPluralsAndSynonyms() {
        List<Ingredient> p = Arrays.asList(item(1, "Eggplant", 1, "pcs", null), item(2, "Tomato", 1, "pcs", null));
        assertEquals("Eggplant", PantryFilter.filter(p, "aubergine").get(0).name);
        assertEquals("Tomato", PantryFilter.filter(p, "tomatoes").get(0).name);
    }

    // ---------- dates ----------
    @Test public void dateParsing_acceptsCommonFormats_andRejectsGarbage() {
        long d = ExpiryDates.parse("2026-10-20");
        assertEquals(d, ExpiryDates.parse("2026/10/20"));
        assertEquals(d, ExpiryDates.parse("20/10/2026"));
        assertEquals(d + 1, ExpiryDates.parse("2026-10-21"));
        assertEquals(0L, ExpiryDates.parse("1970-01-01"));
        assertEquals(ExpiryDates.NO_DATE, ExpiryDates.parse(null));
        assertEquals(ExpiryDates.NO_DATE, ExpiryDates.parse(""));
        assertEquals(ExpiryDates.NO_DATE, ExpiryDates.parse("soon"));
        assertEquals(ExpiryDates.NO_DATE, ExpiryDates.parse("2026-02-30"));
    }

    @Test public void dateMath_crossesMonthAndLeapYearBoundaries() {
        assertEquals(1, ExpiryDates.parse("2024-03-01") - ExpiryDates.parse("2024-02-29"));
        assertEquals(366, ExpiryDates.parse("2025-01-01") - ExpiryDates.parse("2024-01-01"));
        assertEquals(-1, ExpiryDates.daysUntil("2026-10-19", TODAY));
    }
}
