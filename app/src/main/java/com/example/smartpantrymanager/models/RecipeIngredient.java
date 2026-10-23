package com.example.smartpantrymanager.models;

/** One ingredient line of a recipe, e.g. 200 g flour. */
public class RecipeIngredient {
    public String name;
    public double quantity;
    public String unit;

    public RecipeIngredient(String name, double quantity, String unit) {
        this.name = name;
        this.quantity = quantity;
        this.unit = unit;
    }
}
