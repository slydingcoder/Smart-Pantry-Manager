package com.example.smartpantrymanager.activities;

import com.example.smartpantrymanager.R;
import com.example.smartpantrymanager.database.DatabaseHelper;
import com.example.smartpantrymanager.models.Recipe;
import com.example.smartpantrymanager.models.RecipeIngredient;
import com.example.smartpantrymanager.utils.Fmt;
import com.example.smartpantrymanager.utils.RecipeMatcher;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Map;

/**
 * Recipe detail screen: full ingredient list (each marked as in / not in the pantry) and the method.
 * Receives only the recipe id through the launching Intent and loads the recipe from SQLite.
 */
public class RecipeDetailActivity extends AppCompatActivity {
    public static final String EXTRA_RECIPE_ID = "recipe_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe_detail);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        long id = getIntent().getLongExtra(EXTRA_RECIPE_ID, -1);
        DatabaseHelper db = new DatabaseHelper(this);
        Recipe recipe = id == -1 ? null : db.getRecipe(id);
        if (recipe == null) {                                   // bad / stale id: leave instead of crashing
            Toast.makeText(this, "Recipe not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        setTitle(recipe.name);
        ((TextView) findViewById(R.id.tvDetailName)).setText(recipe.name);
        ((TextView) findViewById(R.id.tvDetailMeta)).setText(Fmt.meta(recipe));

        Map<String, Double> stock = RecipeMatcher.buildStock(db.getAllPantry());
        StringBuilder sb = new StringBuilder();
        int have = 0;
        for (RecipeIngredient ri : recipe.ingredients) {
            boolean ok = RecipeMatcher.hasIngredient(ri, stock);
            if (ok) have++;
            sb.append(ok ? "\u2713  " : "\u2717  ").append(Fmt.ingredient(ri)).append('\n');
        }
        ((TextView) findViewById(R.id.tvIngredients)).setText(sb.toString().trim());
        ((TextView) findViewById(R.id.tvPantryStatus)).setText(have == recipe.ingredients.size()
                ? "You have everything for this recipe."
                : "You have " + have + " of " + recipe.ingredients.size() + " ingredients.");
        ((TextView) findViewById(R.id.tvSteps)).setText(
                recipe.steps == null || recipe.steps.trim().isEmpty() ? "No steps added." : recipe.steps);
        db.close();
    }

    @Override
    public boolean onSupportNavigateUp() { finish(); return true; }
}
