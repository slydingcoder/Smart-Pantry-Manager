package com.example.smartpantrymanager.utils;

import com.example.smartpantrymanager.models.Recipe;
import com.example.smartpantrymanager.models.RecipeIngredient;

import java.text.DecimalFormat;
import java.util.List;

/** Small display helpers shared by the list adapters. */
public final class Fmt {
    private static final DecimalFormat NUM = new DecimalFormat("0.##");
    private Fmt() {}

    public static String qty(double q) { return NUM.format(q); }

    public static String ingredient(RecipeIngredient ri) { return qty(ri.quantity) + " " + ri.unit + " " + ri.name; }

    /** Bulleted ingredient list followed by the steps. */
    public static String details(Recipe r) {
        StringBuilder sb = new StringBuilder("Ingredients\n");
        for (RecipeIngredient ri : r.ingredients) sb.append("\u2022 ").append(ingredient(ri)).append('\n');
        sb.append("\nSteps\n").append(r.steps == null || r.steps.trim().isEmpty() ? "No steps added." : r.steps);
        return sb.toString();
    }

    public static String meta(Recipe r) {
        String time = r.prepMinutes > 0 ? r.prepMinutes + " min" : "time not set";
        return r.custom ? time + " \u00B7 My recipe" : time;
    }

    public static String join(List<String> l) { return android.text.TextUtils.join(", ", l); }
}
