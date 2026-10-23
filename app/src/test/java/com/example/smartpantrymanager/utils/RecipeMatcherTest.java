package com.example.smartpantrymanager.utils;

import com.example.smartpantrymanager.models.Ingredient;
import com.example.smartpantrymanager.models.Recipe;
import com.example.smartpantrymanager.models.RecipeIngredient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Strict rule, plurals, unit conversion, synonyms and the "Almost there" list. */
public class RecipeMatcherTest {

    private static final long TODAY = ExpiryDates.parse("2026-10-20");

    // ---------- helpers ----------
    private static Ingredient item(long id, String name, double qty, String unit) {
        return new Ingredient(id, name, qty, unit, null);
    }

    private static Recipe recipe(String name, RecipeIngredient... ings) {
        Recipe r = new Recipe(1, name, "steps");
        r.ingredients.addAll(Arrays.asList(ings));
        return r;
    }

    private static RecipeIngredient ing(String name, double qty, String unit) {
        return new RecipeIngredient(name, qty, unit);
    }

    private static boolean suggested(Recipe r, Ingredient... pantry) {
        List<Recipe> all = new ArrayList<>();
        all.add(r);
        return !RecipeMatcher.suggest(all, Arrays.asList(pantry)).isEmpty();
    }

    private static final Recipe FIVE = recipe("Five",
            ing("egg", 2, "pcs"), ing("butter", 10, "g"), ing("cheese", 50, "g"),
            ing("milk", 100, "ml"), ing("salt", 1, "tsp"));

    // ---------- strict rule ----------
    @Test public void allFiveIngredientsPresent_isSuggested() {
        assertTrue(suggested(FIVE, item(1, "egg", 6, "pcs"), item(2, "butter", 250, "g"),
                item(3, "cheese", 200, "g"), item(4, "milk", 1, "l"), item(5, "salt", 5, "tsp")));
    }

    @Test public void fourOfFiveIngredients_isNotSuggested() {
        assertFalse(suggested(FIVE, item(1, "egg", 6, "pcs"), item(2, "butter", 250, "g"),
                item(3, "cheese", 200, "g"), item(4, "milk", 1, "l")));   // no salt
    }

    @Test public void ingredientPresentButNotEnough_isNotSuggested() {
        assertFalse(suggested(recipe("Omelette", ing("egg", 4, "pcs")), item(1, "egg", 3, "pcs")));
    }

    @Test public void exactQuantity_isEnough() {
        assertTrue(suggested(recipe("Omelette", ing("egg", 3, "pcs")), item(1, "egg", 3, "pcs")));
    }

    @Test public void emptyPantry_suggestsNothing() {
        assertFalse(suggested(FIVE));
    }

    @Test public void recipeWithNoIngredients_isNeverSuggested() {
        assertFalse(suggested(recipe("Empty"), item(1, "egg", 6, "pcs")));
    }

    @Test public void duplicatePantryRows_areSummed() {
        Recipe r = recipe("Pancakes", ing("flour", 150, "g"));
        assertTrue(suggested(r, item(1, "flour", 100, "g"), item(2, "flour", 100, "g")));
    }

    // ---------- plurals / case / whitespace ----------
    @Test public void normalise_handlesPluralsCaseAndWhitespace() {
        assertEquals("tomato", RecipeMatcher.normalise("Tomatoes"));
        assertEquals("tomato", RecipeMatcher.normalise("  TOMATO "));
        assertEquals("egg", RecipeMatcher.normalise("eggs"));
        assertEquals("berry", RecipeMatcher.normalise("berries"));
        assertEquals("olive oil", RecipeMatcher.normalise("Olive   Oil"));
    }

    @Test public void pluralPantryName_matchesSingularRecipeName() {
        assertTrue(suggested(recipe("Soup", ing("tomato", 3, "pcs")), item(1, "Tomatoes", 5, "pcs")));
    }

    @Test public void singularPantryName_matchesPluralRecipeName() {
        assertTrue(suggested(recipe("Omelette", ing("eggs", 2, "pcs")), item(1, "egg", 2, "pcs")));
    }

    // ---------- unit conversion ----------
    @Test public void oneKgSatisfiesFiveHundredGrams() {
        assertTrue(suggested(recipe("Bread", ing("flour", 500, "g")), item(1, "flour", 1, "kg")));
    }

    @Test public void fourHundredGramsDoesNotSatisfyHalfAKilo() {
        assertFalse(suggested(recipe("Bread", ing("flour", 0.5, "kg")), item(1, "flour", 400, "g")));
    }

    @Test public void litreSatisfiesMillilitres() {
        assertTrue(suggested(recipe("Pancakes", ing("milk", 250, "ml")), item(1, "milk", 1, "l")));
    }

    @Test public void unitsFromDifferentFamilies_doNotMix() {
        // 1 kg of flour can't be "3 pcs" of flour, and grams can't stand in for millilitres.
        assertFalse(suggested(recipe("X", ing("flour", 3, "pcs")), item(1, "flour", 1, "kg")));
        assertFalse(suggested(recipe("Y", ing("milk", 100, "ml")), item(1, "milk", 500, "g")));
    }

    // ---------- per-ingredient check (recipe detail screen ticks) ----------
    @Test public void hasIngredient_usesSameRuleAsStrictMatch() {
        java.util.Map<String, Double> stock = RecipeMatcher.buildStock(
                Arrays.asList(item(1, "Flour", 1, "kg"), item(2, "Eggs", 2, "pcs")));
        assertTrue(RecipeMatcher.hasIngredient(ing("flour", 500, "g"), stock));
        assertFalse(RecipeMatcher.hasIngredient(ing("flour", 1500, "g"), stock));
        assertTrue(RecipeMatcher.hasIngredient(ing("egg", 2, "pcs"), stock));
        assertFalse(RecipeMatcher.hasIngredient(ing("milk", 1, "l"), stock));
    }

    // ---------- synonyms ----------
    @Test public void aubergineMatchesEggplant() {
        assertEquals(RecipeMatcher.normalise("aubergine"), RecipeMatcher.normalise("Eggplant"));
        assertTrue(suggested(recipe("Bake", ing("aubergine", 1, "pcs")), item(1, "eggplant", 2, "pcs")));
    }

    @Test public void corianderMatchesCilantro_evenWhenPlural() {
        assertTrue(suggested(recipe("Salsa", ing("coriander", 1, "tbsp")), item(1, "Cilantro", 3, "tbsp")));
        assertEquals("cilantro", RecipeMatcher.normalise("Corianders"));
    }

    @Test public void synonymsDoNotCauseFalseMatches() {
        assertFalse(suggested(recipe("Bake", ing("aubergine", 1, "pcs")), item(1, "zucchini", 2, "pcs")));
    }

    // ---------- Almost there ----------
    @Test public void oneMissingIngredient_isInAlmostThere_notInStrict() {
        List<Recipe> all = Arrays.asList(FIVE);
        List<Ingredient> pantry = Arrays.asList(item(1, "egg", 6, "pcs"), item(2, "butter", 250, "g"),
                item(3, "cheese", 200, "g"), item(4, "milk", 1, "l"));          // salt missing
        assertTrue(RecipeMatcher.suggest(all, pantry).isEmpty());
        List<RecipeMatcher.NearMiss> near = RecipeMatcher.almostThere(all, pantry, TODAY);
        assertEquals(1, near.size());
        assertEquals("salt", near.get(0).ingredient.name);
        assertTrue(near.get(0).absent);
        assertEquals(1.0, near.get(0).shortBy, 1e-9);
    }

    @Test public void twoMissingIngredients_isNotInAlmostThere() {
        List<Ingredient> pantry = Arrays.asList(item(1, "egg", 6, "pcs"), item(2, "butter", 250, "g"),
                item(3, "cheese", 200, "g"));                                   // milk + salt missing
        assertTrue(RecipeMatcher.almostThere(Arrays.asList(FIVE), pantry, TODAY).isEmpty());
    }

    @Test public void fullyCookableRecipe_isNotInAlmostThere() {
        List<Ingredient> pantry = Arrays.asList(item(1, "egg", 6, "pcs"));
        Recipe r = recipe("Boiled egg", ing("egg", 2, "pcs"));
        assertTrue(RecipeMatcher.almostThere(Arrays.asList(r), pantry, TODAY).isEmpty());
    }

    @Test public void oneIngredientShort_reportsHowMuchIsNeeded() {
        Recipe r = recipe("Pancakes", ing("flour", 150, "g"), ing("egg", 1, "pcs"));
        List<Ingredient> pantry = Arrays.asList(item(1, "flour", 100, "g"), item(2, "egg", 3, "pcs"));
        List<RecipeMatcher.NearMiss> near = RecipeMatcher.almostThere(Arrays.asList(r), pantry, TODAY);
        assertEquals(1, near.size());
        assertFalse(near.get(0).absent);
        assertEquals(50.0, near.get(0).shortBy, 1e-9);
    }

    @Test public void shortfallIsReportedInTheRecipesOwnUnit() {
        Recipe r = recipe("Bread", ing("flour", 1, "kg"));
        List<Ingredient> pantry = Arrays.asList(item(1, "flour", 400, "g"));
        assertEquals(0.6, RecipeMatcher.almostThere(Arrays.asList(r), pantry, TODAY).get(0).shortBy, 1e-9);
    }
}
