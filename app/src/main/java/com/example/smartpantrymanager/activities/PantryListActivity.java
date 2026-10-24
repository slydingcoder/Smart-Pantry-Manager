package com.example.smartpantrymanager.activities;

import com.example.smartpantrymanager.R;
import com.example.smartpantrymanager.adapters.IngredientAdapter;
import com.example.smartpantrymanager.database.DatabaseHelper;
import com.example.smartpantrymanager.models.Ingredient;
import com.example.smartpantrymanager.utils.BottomNav;
import com.example.smartpantrymanager.utils.ExpiryScheduler;
import com.example.smartpantrymanager.utils.PantryFilter;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

/** Launcher screen: pantry list (Read) with search, Add / Edit / Delete, and the bottom navigation bar. */
public class PantryListActivity extends AppCompatActivity {
    private DatabaseHelper db;
    private RecyclerView rv;
    private View emptyState;
    private TextView tvEmpty;
    private TextInputEditText etSearch;
    private List<Ingredient> allItems = new ArrayList<>();

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pantry);
        setTitle("My Pantry");
        db = new DatabaseHelper(this);
        rv = findViewById(R.id.rvPantry);
        emptyState = findViewById(R.id.emptyState);
        tvEmpty = findViewById(R.id.tvEmpty);
        etSearch = findViewById(R.id.etSearch);
        rv.setLayoutManager(new LinearLayoutManager(this));
        findViewById(R.id.btnAdd).setOnClickListener(v ->
                startActivity(new Intent(this, AddEditIngredientActivity.class)));

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { showList(); }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    /** Refresh in onResume so edits made on other screens appear when we come back. */
    @Override
    protected void onResume() {
        super.onResume();
        BottomNav.attach(this, R.id.nav_pantry);
        allItems = db.getAllPantry();
        showList();
        ExpiryScheduler.sync(this);          // follow the Settings toggle (on = daily reminder, off = cancelled)
        askForNotificationPermissionOnce();
    }

    private void showList() {
        String query = etSearch.getText() == null ? "" : etSearch.getText().toString();
        List<Ingredient> items = PantryFilter.filter(allItems, query);
        boolean alerts = getSharedPreferences("prefs", MODE_PRIVATE).getBoolean("expiry_alerts", true);
        rv.setAdapter(new IngredientAdapter(items, alerts, new IngredientAdapter.Listener() {
            @Override public void onEdit(Ingredient item) {
                Intent i = new Intent(PantryListActivity.this, AddEditIngredientActivity.class);
                i.putExtra("id", item.id); // pass data via Intent
                startActivity(i);
            }
            @Override public void onDelete(Ingredient item) {
                new AlertDialog.Builder(PantryListActivity.this)
                        .setTitle("Delete " + item.name + "?")
                        .setPositiveButton("Delete", (d, w) -> {
                            db.deletePantry(item.id);
                            allItems = db.getAllPantry();
                            showList();
                        })
                        .setNegativeButton("Cancel", null).show();
            }
        }));
        if (items.isEmpty()) {
            tvEmpty.setText(allItems.isEmpty()
                    ? "Your pantry is empty.\nTap + to add your first ingredient."
                    : "No ingredients match \u201C" + query.trim() + "\u201D.");
        }
        emptyState.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        rv.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** Android 13+ needs a runtime permission before the reminder can show. Ask once, only if alerts are on. */
    private void askForNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        if (!prefs.getBoolean("expiry_alerts", true) || prefs.getBoolean("notif_permission_asked", false)) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;
        prefs.edit().putBoolean("notif_permission_asked", true).apply();
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
    }
}
