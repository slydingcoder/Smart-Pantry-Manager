package com.example.smartpantrymanager.database;

import com.example.smartpantrymanager.models.Ingredient;
import com.example.smartpantrymanager.models.Recipe;
import com.example.smartpantrymanager.models.RecipeIngredient;
import com.example.smartpantrymanager.utils.RecipeMatcher;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** SQLite persistence: pantry CRUD, seeded + custom recipes, cooking, versioned migrations. */
public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "pantry.db";

    /**
     * Schema history:
     *  v1 - pantry, recipes, recipe_ingredients
     *  v2 - recipes.prep_minutes (quick-meal filter), recipes.is_custom (user-made recipes),
     *       index on recipe_ingredients.recipe_id
     * To change the schema: bump DB_VERSION, create the new shape in onCreate(), and add a
     * migrateNtoN+1() step + a case in onUpgrade(). User data is never dropped.
     */
    private static final int DB_VERSION = 2;


    public DatabaseHelper(Context c) { super(c, DB_NAME, null, DB_VERSION); }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE pantry (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, quantity REAL NOT NULL, unit TEXT NOT NULL, expiry TEXT)");
        db.execSQL("CREATE TABLE recipes (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, steps TEXT NOT NULL, " +
                "prep_minutes INTEGER NOT NULL DEFAULT 0, is_custom INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE recipe_ingredients (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "recipe_id INTEGER NOT NULL, name TEXT NOT NULL, quantity REAL NOT NULL, unit TEXT NOT NULL, " +
                "FOREIGN KEY(recipe_id) REFERENCES recipes(id))");
        db.execSQL("CREATE INDEX idx_recipe_ingredients_recipe ON recipe_ingredients(recipe_id)");
        RecipeSeeder.seed(db); // pre-load recipes on first run
    }

    /**
     * Runs inside a transaction opened by SQLiteOpenHelper: if any step throws, the whole upgrade
     * rolls back. Steps run in order, so a v1 install upgrades through every later version.
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        for (int v = oldVersion; v < newVersion; v++) {
            switch (v) {
                case 1: migrate1to2(db); break;
                // case 2: migrate2to3(db); break;
                default: throw new IllegalStateException("No migration from DB version " + v);
            }
        }
    }

    private void migrate1to2(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE recipes ADD COLUMN prep_minutes INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE recipes ADD COLUMN is_custom INTEGER NOT NULL DEFAULT 0");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_recipe_ingredients_recipe ON recipe_ingredients(recipe_id)");
        for (Map.Entry<String, Integer> e : RecipeSeeder.PREP_MINUTES.entrySet()) {
            db.execSQL("UPDATE recipes SET prep_minutes = ? WHERE name = ? AND is_custom = 0",
                    new Object[]{e.getValue(), e.getKey()});
        }
    }

    // ---------- PANTRY CRUD ----------
    public long addPantry(Ingredient p) {
        return getWritableDatabase().insert("pantry", null, values(p));
    }

    public int updatePantry(Ingredient p) {
        return getWritableDatabase().update("pantry", values(p), "id=?", new String[]{String.valueOf(p.id)});
    }

    public int deletePantry(long id) {
        return getWritableDatabase().delete("pantry", "id=?", new String[]{String.valueOf(id)});
    }

    public Ingredient getPantry(long id) {
        try (Cursor c = getReadableDatabase().query("pantry", null, "id=?",
                new String[]{String.valueOf(id)}, null, null, null)) {
            return c.moveToFirst() ? fromCursor(c) : null;
        }
    }

    public List<Ingredient> getAllPantry() {
        List<Ingredient> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("pantry", null, null, null, null, null, "name COLLATE NOCASE")) {
            while (c.moveToNext()) list.add(fromCursor(c));
        }
        return list;
    }

    private ContentValues values(Ingredient p) {
        ContentValues v = new ContentValues();
        v.put("name", p.name); v.put("quantity", p.quantity);
        v.put("unit", p.unit); v.put("expiry", p.expiry);
        return v;
    }

    private Ingredient fromCursor(Cursor c) {
        return new Ingredient(c.getLong(c.getColumnIndexOrThrow("id")),
                c.getString(c.getColumnIndexOrThrow("name")),
                c.getDouble(c.getColumnIndexOrThrow("quantity")),
                c.getString(c.getColumnIndexOrThrow("unit")),
                c.getString(c.getColumnIndexOrThrow("expiry")));
    }

    // ---------- RECIPES ----------
    public List<Recipe> getAllRecipes() {
        List<Recipe> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query("recipes", null, null, null, null, null, "name")) {
            while (c.moveToNext()) {
                Recipe r = recipeFromCursor(c);
                loadIngredients(db, r);
                list.add(r);
            }
        }
        return list;
    }

    public Recipe getRecipe(long id) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query("recipes", null, "id=?", new String[]{String.valueOf(id)}, null, null, null)) {
            if (!c.moveToFirst()) return null;
            Recipe r = recipeFromCursor(c);
            loadIngredients(db, r);
            return r;
        }
    }

    private Recipe recipeFromCursor(Cursor c) {
        Recipe r = new Recipe(c.getLong(c.getColumnIndexOrThrow("id")),
                c.getString(c.getColumnIndexOrThrow("name")),
                c.getString(c.getColumnIndexOrThrow("steps")));
        r.prepMinutes = c.getInt(c.getColumnIndexOrThrow("prep_minutes"));
        r.custom = c.getInt(c.getColumnIndexOrThrow("is_custom")) == 1;
        return r;
    }

    /** Inserts a user-made recipe and its ingredients atomically. Returns the new id, or -1 on failure. */
    public long addCustomRecipe(Recipe r) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put("name", r.name.trim());
            v.put("steps", r.steps == null ? "" : r.steps.trim());
            v.put("prep_minutes", r.prepMinutes);
            v.put("is_custom", 1);
            long rid = db.insert("recipes", null, v);
            if (rid == -1) return -1;                       // endTransaction() without success = rollback
            for (RecipeIngredient ri : r.ingredients) RecipeSeeder.insertIngredient(db, rid, ri.name.trim(), ri.quantity, ri.unit);
            db.setTransactionSuccessful();
            return rid;
        } finally {
            db.endTransaction();
        }
    }

    /** Deletes a user-made recipe (built-in recipes are protected). Returns true if a row was removed. */
    public boolean deleteCustomRecipe(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            String[] args = {String.valueOf(id)};
            Cursor c = db.query("recipes", new String[]{"id"}, "id=? AND is_custom=1", args, null, null, null);
            boolean exists;
            try { exists = c.moveToFirst(); } finally { c.close(); }
            if (!exists) return false;
            db.delete("recipe_ingredients", "recipe_id=?", args);   // children first (foreign keys are enforced)
            db.delete("recipes", "id=?", args);
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    // ---------- COOKING ----------
    /** Applies RecipeMatcher.planCook() in one transaction: update quantities, delete rows that hit zero. */
    public void applyCook(List<RecipeMatcher.PantryChange> changes) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (RecipeMatcher.PantryChange ch : changes) {
                String[] args = {String.valueOf(ch.pantryId)};
                if (ch.remove()) {
                    db.delete("pantry", "id=?", args);
                } else {
                    ContentValues v = new ContentValues();
                    v.put("quantity", ch.newQuantity);
                    db.update("pantry", v, "id=?", args);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void loadIngredients(SQLiteDatabase db, Recipe r) {
        try (Cursor c = db.query("recipe_ingredients", null, "recipe_id=?",
                new String[]{String.valueOf(r.id)}, null, null, null)) {
            while (c.moveToNext()) {
                r.ingredients.add(new RecipeIngredient(c.getString(c.getColumnIndexOrThrow("name")),
                        c.getDouble(c.getColumnIndexOrThrow("quantity")),
                        c.getString(c.getColumnIndexOrThrow("unit"))));
            }
        }
    }
}
