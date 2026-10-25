package com.example.smartpantrymanager.activities;

import com.example.smartpantrymanager.R;
import com.example.smartpantrymanager.adapters.SuggestionAdapter;
import com.example.smartpantrymanager.database.DatabaseHelper;
import com.example.smartpantrymanager.models.Ingredient;
import com.example.smartpantrymanager.models.Recipe;
import com.example.smartpantrymanager.models.RecipeIngredient;
import com.example.smartpantrymanager.utils.BottomNav;
import com.example.smartpantrymanager.utils.ExpiryDates;
import com.example.smartpantrymanager.utils.ExpiryWorker;
import com.example.smartpantrymanager.utils.Fmt;
import com.example.smartpantrymanager.utils.RecipeMatcher;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

/**
 * Suggestions: the strict list (ranked expiry-first, each with a "Cook this" button) and, separately,
 * the "Almost there" list. Filter chips: quick meals, uses expiring food, my recipes.
 */
public class SuggestedRecipesActivity extends AppCompatActivity {
    private DatabaseHelper db;
    private RecyclerView rv;
    private View emptyState;
    private TextView tvEmpty;
    private Chip chipQuick, chipExpiring, chipMine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_suggested_recipes);
        setTitle("Suggestions");
        db = new DatabaseHelper(this);
        rv = findViewById(R.id.rvSuggestions);
        emptyState = findViewById(R.id.emptyState);
        tvEmpty = findViewById(R.id.tvEmpty);
        chipQuick = findViewById(R.id.chipQuick);
        chipExpiring = findViewById(R.id.chipExpiring);
        chipMine = findViewById(R.id.chipMine);
        rv.setLayoutManager(new LinearLayoutManager(this));

        // opened from the daily reminder notification: start with the "uses expiring food" filter on
        chipExpiring.setChecked(getIntent().getBooleanExtra(ExpiryWorker.EXTRA_FILTER_EXPIRING, false));
        chipQuick.setOnCheckedChangeListener((b, c) -> load());
        chipExpiring.setOnCheckedChangeListener((b, c) -> load());
        chipMine.setOnCheckedChangeListener((b, c) -> load());
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNav.attach(this, R.id.nav_suggestions);
        load();
    }

    private void load() {
        List<Recipe> recipes = db.getAllRecipes();
        List<Ingredient> pantry = db.getAllPantry();
        long today = ExpiryDates.today();

        List<RecipeMatcher.Ranked> ready = new ArrayList<>();
        for (RecipeMatcher.Ranked r : RecipeMatcher.suggestRanked(recipes, pantry, today))
            if (passes(r.recipe, r.expiringCount)) ready.add(r);
        List<RecipeMatcher.NearMiss> near = new ArrayList<>();
        for (RecipeMatcher.NearMiss n : RecipeMatcher.almostThere(recipes, pantry, today))
            if (passes(n.recipe, n.expiringCount)) near.add(n);

        rv.setAdapter(new SuggestionAdapter(ready, near, new SuggestionAdapter.Listener() {
            @Override public void onCook(Recipe r) { confirmCook(r); }
            @Override public void onOpen(Recipe r) {
                Intent i = new Intent(SuggestedRecipesActivity.this, RecipeDetailActivity.class);
                i.putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, r.id);   // pass the id; the detail screen reloads from the DB
                startActivity(i);
            }
        }));

        boolean empty = ready.isEmpty() && near.isEmpty();
        if (empty) {
            tvEmpty.setText(filtersActive()
                    ? "No recipes match these filters."
                    : "No recipes match your pantry yet - add more ingredients");
        }
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        rv.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private boolean filtersActive() { return chipQuick.isChecked() || chipExpiring.isChecked() || chipMine.isChecked(); }

    private boolean passes(Recipe r, int expiringCount) {
        if (chipQuick.isChecked() && !r.isQuick()) return false;
        if (chipMine.isChecked() && !r.custom) return false;
        if (chipExpiring.isChecked() && expiringCount == 0) return false;
        return true;
    }

    private void confirmCook(final Recipe recipe) {
        StringBuilder sb = new StringBuilder("This takes the following out of your pantry. Anything that runs out is removed.\n\n");
        for (RecipeIngredient ri : recipe.ingredients) sb.append("\u2022 ").append(Fmt.ingredient(ri)).append('\n');
        new AlertDialog.Builder(this)
                .setTitle("Cook " + recipe.name + "?")
                .setMessage(sb.toString().trim())
                .setPositiveButton("Cook this", (d, w) -> cook(recipe))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void cook(Recipe recipe) {
        try {
            db.applyCook(RecipeMatcher.planCook(recipe, db.getAllPantry()));   // re-read pantry: it is the source of truth
            Toast.makeText(this, "Enjoy your " + recipe.name + "! Pantry updated.", Toast.LENGTH_LONG).show();
        } catch (IllegalStateException e) {
            Toast.makeText(this, "Not enough ingredients any more.", Toast.LENGTH_LONG).show();
        }
        load();
    }
}
