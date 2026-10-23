package com.example.smartpantrymanager.models;

import java.util.ArrayList;
import java.util.List;

/**
 * A recipe. Same public shape as before (id, name, steps, ingredients) plus two additive fields:
 * prepMinutes (0 = unknown) and custom (true = created by the user).
 */
public class Recipe {
    /** "Quick meal" filter threshold, in minutes. */
    public static final int QUICK_MINUTES = 20;

    public long id;
    public String name;
    public String steps;
    public final List<RecipeIngredient> ingredients = new ArrayList<>();
    public int prepMinutes;
    public boolean custom;

    public Recipe(long id, String name, String steps) {
        this.id = id;
        this.name = name;
        this.steps = steps;
    }

    public boolean isQuick() { return prepMinutes > 0 && prepMinutes <= QUICK_MINUTES; }
}
