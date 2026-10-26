package com.example.smartpantrymanager;

import com.example.smartpantrymanager.activities.PantryListActivity;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/** Launcher entry point: hands straight over to the pantry list (the home screen) and closes itself. */
public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startActivity(new Intent(this, PantryListActivity.class));
        finish();   // so Back from the pantry list leaves the app instead of returning here
    }
}
