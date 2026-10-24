package com.example.smartpantrymanager.utils;

import com.example.smartpantrymanager.R;
import com.example.smartpantrymanager.activities.PantryListActivity;
import com.example.smartpantrymanager.activities.RecipesActivity;
import com.example.smartpantrymanager.activities.SettingsActivity;
import com.example.smartpantrymanager.activities.SuggestedRecipesActivity;

import android.app.Activity;
import android.content.Intent;

import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Wires the shared bottom navigation bar (R.id.bottomNav) in any activity whose layout contains it.
 * PantryListActivity is the root; the other tabs replace each other and Back returns to the pantry.
 */
public final class BottomNav {
    private BottomNav() {}

    public static void attach(final Activity activity, final int selectedId) {
        BottomNavigationView nav = activity.findViewById(R.id.bottomNav);
        if (nav == null) return;
        nav.setOnItemSelectedListener(null);
        nav.setSelectedItemId(selectedId);               // set BEFORE the listener so it doesn't fire
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == selectedId) return true;
            Class<?> target;
            if (id == R.id.nav_pantry) target = PantryListActivity.class;
            else if (id == R.id.nav_suggestions) target = SuggestedRecipesActivity.class;
            else if (id == R.id.nav_recipes) target = RecipesActivity.class;
            else target = SettingsActivity.class;

            Intent i = new Intent(activity, target);
            if (target == PantryListActivity.class) {
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                activity.startActivity(i);
            } else {
                activity.startActivity(i);
                if (!(activity instanceof PantryListActivity)) activity.finish();
            }
            activity.overridePendingTransition(0, 0);
            return true;
        });
    }
}
