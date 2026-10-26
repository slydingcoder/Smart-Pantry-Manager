package com.example.smartpantrymanager.activities;

import com.example.smartpantrymanager.R;
import com.example.smartpantrymanager.utils.BottomNav;
import com.example.smartpantrymanager.utils.ExpiryScheduler;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;

/** Settings: the expiring-soon alerts toggle (SharedPreferences "prefs" / "expiry_alerts", default on). */
public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setTitle("Settings");
        SwitchMaterial alerts = findViewById(R.id.switchAlerts);
        alerts.setChecked(getSharedPreferences("prefs", MODE_PRIVATE).getBoolean("expiry_alerts", true));
        alerts.setOnCheckedChangeListener((button, checked) -> {
            getSharedPreferences("prefs", MODE_PRIVATE).edit().putBoolean("expiry_alerts", checked).apply();
            ExpiryScheduler.sync(this);        // schedule or cancel the daily reminder right away
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNav.attach(this, R.id.nav_settings);
    }
}
