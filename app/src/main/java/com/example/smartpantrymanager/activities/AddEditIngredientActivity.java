package com.example.smartpantrymanager.activities;

import com.example.smartpantrymanager.R;
import com.example.smartpantrymanager.database.DatabaseHelper;
import com.example.smartpantrymanager.models.Ingredient;
import com.example.smartpantrymanager.utils.ExpiryDates;
import com.example.smartpantrymanager.utils.Units;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Calendar;
import java.util.Locale;

/**
 * Add a new ingredient, or edit an existing one when the Intent carries an "id" extra.
 * Validates input before it touches the database.
 */
public class AddEditIngredientActivity extends AppCompatActivity {
    private DatabaseHelper db;
    private TextInputLayout tilName, tilQty, tilExpiry;
    private TextInputEditText etName, etQty, etExpiry;
    private AutoCompleteTextView actUnit;
    private long editId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_ingredient);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        db = new DatabaseHelper(this);

        tilName = findViewById(R.id.tilName);
        tilQty = findViewById(R.id.tilQty);
        tilExpiry = findViewById(R.id.tilExpiry);
        etName = findViewById(R.id.etName);
        etQty = findViewById(R.id.etQty);
        etExpiry = findViewById(R.id.etExpiry);
        actUnit = findViewById(R.id.actUnit);
        actUnit.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, Units.CHOICES));
        actUnit.setText(Units.CHOICES[0], false);

        etExpiry.setOnClickListener(v -> pickDate());
        tilExpiry.setEndIconOnClickListener(v -> etExpiry.setText(""));   // clear the date

        editId = getIntent().getLongExtra("id", -1);
        if (editId != -1) {
            Ingredient item = db.getPantry(editId);
            if (item == null) {                       // stale id: leave instead of crashing
                Toast.makeText(this, "Ingredient not found.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            setTitle("Edit ingredient");
            etName.setText(item.name);
            etQty.setText(com.example.smartpantrymanager.utils.Fmt.qty(item.quantity));
            actUnit.setText(item.unit, false);
            etExpiry.setText(item.expiry == null ? "" : item.expiry);
        } else {
            setTitle("Add ingredient");
        }
        findViewById(R.id.btnSave).setOnClickListener(v -> save());
    }

    @Override
    public boolean onSupportNavigateUp() { finish(); return true; }

    private static String text(TextInputEditText e) { return e.getText() == null ? "" : e.getText().toString().trim(); }

    private void pickDate() {
        Calendar c = Calendar.getInstance();
        long existing = ExpiryDates.parse(text(etExpiry));
        if (existing != ExpiryDates.NO_DATE) c.setTimeInMillis(existing * 86_400_000L + 43_200_000L);
        new DatePickerDialog(this, (view, y, m, d) ->
                etExpiry.setText(String.format(Locale.ROOT, "%04d-%02d-%02d", y, m + 1, d)),
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void save() {
        tilName.setError(null);
        tilQty.setError(null);
        tilExpiry.setError(null);
        boolean ok = true;

        String name = text(etName);
        if (name.isEmpty()) { tilName.setError("Enter an ingredient name"); ok = false; }

        double qty = 0;
        String qtyText = text(etQty).replace(',', '.');
        if (qtyText.isEmpty()) { tilQty.setError("Enter an amount"); ok = false; }
        else {
            try {
                qty = Double.parseDouble(qtyText);
                if (qty <= 0 || Double.isNaN(qty) || Double.isInfinite(qty)) throw new NumberFormatException();
            } catch (NumberFormatException e) { tilQty.setError("Enter a number above 0"); ok = false; }
        }

        String unit = actUnit.getText().toString().trim();
        if (unit.isEmpty()) unit = Units.CHOICES[0];

        String expiry = text(etExpiry);
        if (!expiry.isEmpty() && ExpiryDates.parse(expiry) == ExpiryDates.NO_DATE) {
            tilExpiry.setError("Use a real date, e.g. 2026-12-31");
            ok = false;
        }
        if (!ok) return;

        Ingredient item = new Ingredient(editId == -1 ? 0 : editId, name, qty, unit, expiry.isEmpty() ? null : expiry);
        if (editId == -1) db.addPantry(item); else db.updatePantry(item);
        Toast.makeText(this, editId == -1 ? "Ingredient added." : "Ingredient updated.", Toast.LENGTH_SHORT).show();
        finish();
    }
}
