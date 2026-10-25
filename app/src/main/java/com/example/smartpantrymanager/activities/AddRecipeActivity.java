package com.example.smartpantrymanager.activities;

import com.example.smartpantrymanager.R;
import com.example.smartpantrymanager.database.DatabaseHelper;
import com.example.smartpantrymanager.models.Recipe;
import com.example.smartpantrymanager.models.RecipeIngredient;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/** Form for a user-made recipe: name, prep time, steps and any number of ingredient rows. */
public class AddRecipeActivity extends AppCompatActivity {
    /** Units the matcher understands (see Units). Keep in sync with Units.java. */
    private static final String[] UNITS = {"g", "kg", "ml", "l", "cup", "tbsp", "tsp", "pcs"};

    private TextInputLayout tilName, tilPrep;
    private TextInputEditText etName, etPrep, etSteps;
    private LinearLayout container;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_recipe);
        setTitle("New recipe");
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        tilName = findViewById(R.id.tilName);
        tilPrep = findViewById(R.id.tilPrep);
        etName = findViewById(R.id.etName);
        etPrep = findViewById(R.id.etPrep);
        etSteps = findViewById(R.id.etSteps);
        container = findViewById(R.id.ingredientContainer);
        findViewById(R.id.btnAddIngredient).setOnClickListener(v -> addRow());
        findViewById(R.id.btnSave).setOnClickListener(v -> save());
        addRow();   // start with one empty ingredient row
    }

    @Override
    public boolean onSupportNavigateUp() { finish(); return true; }

    private void addRow() {
        final View row = LayoutInflater.from(this).inflate(R.layout.row_recipe_ingredient, container, false);
        AutoCompleteTextView unit = row.findViewById(R.id.actUnit);
        unit.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, UNITS));
        unit.setText(UNITS[0], false);
        row.findViewById(R.id.btnRemoveRow).setOnClickListener(v -> container.removeView(row));
        container.addView(row);
    }

    private static String text(TextInputEditText e) { return e.getText() == null ? "" : e.getText().toString().trim(); }

    private void save() {
        boolean ok = true;
        tilName.setError(null);
        tilPrep.setError(null);

        String name = text(etName);
        if (name.isEmpty()) { tilName.setError("Give the recipe a name"); ok = false; }

        int prep = 0;
        String prepText = text(etPrep);
        if (!prepText.isEmpty()) {
            try { prep = Integer.parseInt(prepText); if (prep < 0) throw new NumberFormatException(); }
            catch (NumberFormatException e) { tilPrep.setError("Enter whole minutes, e.g. 20"); ok = false; }
        }

        Recipe recipe = new Recipe(0, name, text(etSteps));
        recipe.prepMinutes = prep;
        recipe.custom = true;

        for (int i = 0; i < container.getChildCount(); i++) {
            View row = container.getChildAt(i);
            TextInputLayout tilIng = row.findViewById(R.id.tilIngName);
            TextInputLayout tilQty = row.findViewById(R.id.tilQty);
            String ing = text((TextInputEditText) row.findViewById(R.id.etIngName));
            String qtyText = text((TextInputEditText) row.findViewById(R.id.etQty));
            String unit = ((AutoCompleteTextView) row.findViewById(R.id.actUnit)).getText().toString().trim();
            tilIng.setError(null);
            tilQty.setError(null);
            if (ing.isEmpty() && qtyText.isEmpty()) continue;           // ignore a completely blank row
            if (ing.isEmpty()) { tilIng.setError("Name?"); ok = false; continue; }
            double qty;
            try { qty = Double.parseDouble(qtyText.replace(',', '.')); }
            catch (NumberFormatException e) { tilQty.setError("Amount?"); ok = false; continue; }
            if (qty <= 0) { tilQty.setError("> 0"); ok = false; continue; }
            if (unit.isEmpty()) unit = UNITS[0];
            recipe.ingredients.add(new RecipeIngredient(ing, qty, unit));
        }
        if (ok && recipe.ingredients.isEmpty()) {
            Toast.makeText(this, "Add at least one ingredient.", Toast.LENGTH_SHORT).show();
            ok = false;
        }
        if (!ok) return;

        DatabaseHelper db = new DatabaseHelper(this);
        if (db.addCustomRecipe(recipe) == -1) {
            Toast.makeText(this, "Could not save the recipe.", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "Recipe saved.", Toast.LENGTH_SHORT).show();
        finish();
    }
}
