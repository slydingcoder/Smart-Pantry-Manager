package com.example.smartpantrymanager.models;

/** One pantry item (a row of the pantry table). */
public class Ingredient {
    public long id;
    public String name;
    public double quantity;
    public String unit;
    /** Expiry date as text (yyyy-MM-dd), or null / empty when there is none. */
    public String expiry;

    public Ingredient(long id, String name, double quantity, String unit, String expiry) {
        this.id = id;
        this.name = name;
        this.quantity = quantity;
        this.unit = unit;
        this.expiry = expiry;
    }

    /** For a brand-new item that has no database id yet. */
    public Ingredient(String name, double quantity, String unit, String expiry) {
        this(0, name, quantity, unit, expiry);
    }
}
