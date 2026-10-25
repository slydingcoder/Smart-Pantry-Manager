package com.example.smartpantrymanager.activities;

import com.example.smartpantrymanager.R;
import com.example.smartpantrymanager.adapters.RecipeAdapter;
import com.example.smartpantrymanager.database.DatabaseHelper;
import com.example.smartpantrymanager.models.Recipe;
import com.example.smartpantrymanager.utils.BottomNav;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** The recipe collection: built-in recipes plus the user's own. "+" opens the add-recipe form. */
public class RecipesActivity extends AppCompatActivity {
    private DatabaseHelper db;
    private RecyclerView rv;
    private View emptyState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipes);
        setTitle("Recipes");
        db = new DatabaseHelper(this);
        rv = findViewById(R.id.rvRecipes);
        emptyState = findViewById(R.id.emptyState);
        rv.setLayoutManager(new LinearLayoutManager(this));
        findViewById(R.id.btnAddRecipe).setOnClickListener(v ->
                startActivity(new Intent(this, AddRecipeActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNav.attach(this, R.id.nav_recipes);
        load();
    }

    private void load() {
        List<Recipe> all = db.getAllRecipes();
        List<Recipe> ordered = new ArrayList<>();                 // the user's own recipes first
        for (Recipe r : all) if (r.custom) ordered.add(r);
        for (Recipe r : all) if (!r.custom) ordered.add(r);
        rv.setAdapter(new RecipeAdapter(ordered, new RecipeAdapter.Listener() {
            @Override public void onOpen(Recipe r) {
                Intent i = new Intent(RecipesActivity.this, RecipeDetailActivity.class);
                i.putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, r.id);
                startActivity(i);
            }
            @Override public void onDelete(Recipe r) {
                new AlertDialog.Builder(RecipesActivity.this).setTitle("Delete " + r.name + "?")
                        .setPositiveButton("Delete", (d, w) -> { db.deleteCustomRecipe(r.id); load(); })
                        .setNegativeButton("Cancel", null).show();
            }
        }));
        emptyState.setVisibility(ordered.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
