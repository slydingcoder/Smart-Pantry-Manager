package com.example.smartpantrymanager.utils;

import java.util.Locale;

/**
 * Unit families and conversion (pure Java).
 *
 * Quantities are only ever compared within one family, and are converted to that family's base unit:
 *   mass   -> grams       (g, kg)
 *   volume -> millilitres (ml, l, cup, tbsp, tsp)
 *   count  -> pieces      (pcs)
 * Mass, volume and count are never mixed. An unknown unit gets a family of its own, so it only
 * matches the identical unit.
 */
public final class Units {
    private Units() {}

    public static final String MASS = "mass";
    public static final String VOLUME = "volume";
    public static final String COUNT = "count";

    /** The units offered in the dropdowns (all of them are understood by {@link #family}). */
    public static final String[] CHOICES = {"g", "kg", "ml", "l", "cup", "tbsp", "tsp", "pcs"};

    private static String clean(String unit) {
        String u = unit == null ? "" : unit.trim().toLowerCase(Locale.ROOT);
        switch (u) {
            case "gram": case "grams": case "gr": return "g";
            case "kilogram": case "kilograms": case "kgs": return "kg";
            case "milliliter": case "milliliters": case "millilitre": case "millilitres": case "mls": return "ml";
            case "liter": case "liters": case "litre": case "litres": case "ls": return "l";
            case "cups": return "cup";
            case "tablespoon": case "tablespoons": case "tbs": case "tbsps": return "tbsp";
            case "teaspoon": case "teaspoons": case "tsps": return "tsp";
            case "pc": case "piece": case "pieces": case "unit": case "units": case "whole": return "pcs";
            default: return u;
        }
    }

    /** The unit family, used as part of the matching key. */
    public static String family(String unit) {
        switch (clean(unit)) {
            case "g": case "kg": return MASS;
            case "ml": case "l": case "cup": case "tbsp": case "tsp": return VOLUME;
            case "pcs": return COUNT;
            default: return "other:" + clean(unit);
        }
    }

    /** Converts a quantity to its family's base unit (g, ml or pcs). Unknown units are returned unchanged. */
    public static double toBase(double quantity, String unit) {
        switch (clean(unit)) {
            case "kg": return quantity * 1000.0;
            case "l": return quantity * 1000.0;
            case "cup": return quantity * 240.0;
            case "tbsp": return quantity * 15.0;
            case "tsp": return quantity * 5.0;
            default: return quantity;     // g, ml, pcs and unknown units are already base units
        }
    }
}
