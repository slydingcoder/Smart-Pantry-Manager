package com.example.smartpantrymanager.database;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import java.util.LinkedHashMap;
import java.util.Map;

/** Built-in recipe data. Kept apart from DatabaseHelper so the schema code stays small and readable. */
public final class RecipeSeeder {
    private RecipeSeeder() {}

    /** Prep time (minutes) of each seeded recipe: single source for fresh installs AND the v1->v2 migration. */
    public static final Map<String, Integer> PREP_MINUTES = new LinkedHashMap<>();
    static {
        PREP_MINUTES.put("Scrambled Eggs", 10);
        PREP_MINUTES.put("Tomato Pasta", 25);
        PREP_MINUTES.put("Cheese Omelette", 10);
        PREP_MINUTES.put("Fried Rice", 20);
        PREP_MINUTES.put("Pancakes", 20);
        PREP_MINUTES.put("Mashed Potatoes", 30);
        PREP_MINUTES.put("Garlic Bread", 15);
        PREP_MINUTES.put("Tomato Soup", 35);
        PREP_MINUTES.put("Cheese Toastie", 10);
        PREP_MINUTES.put("Chicken Rice Bowl", 30);
        PREP_MINUTES.put("Bean Chilli", 35);
        PREP_MINUTES.put("Banana Pancakes", 15);
        PREP_MINUTES.put("Potato Omelette", 25);
        PREP_MINUTES.put("Mac and Cheese", 25);
        PREP_MINUTES.put("Garlic Butter Chicken", 25);
        PREP_MINUTES.put("Veggie Stir Fry", 20);
    }

    public static void insertIngredient(SQLiteDatabase db, long recipeId, String name, double qty, String unit) {
        ContentValues iv = new ContentValues();
        iv.put("recipe_id", recipeId); iv.put("name", name);
        iv.put("quantity", qty); iv.put("unit", unit);
        db.insert("recipe_ingredients", null, iv);
    }

    /** ingredients format: "name:quantity:unit" */
    private static void addRecipe(SQLiteDatabase db, String name, String steps, String... ings) {
        ContentValues v = new ContentValues();
        v.put("name", name); v.put("steps", steps);
        Integer prep = PREP_MINUTES.get(name);
        v.put("prep_minutes", prep == null ? 0 : prep);
        v.put("is_custom", 0);
        long rid = db.insert("recipes", null, v);
        for (String s : ings) {
            String[] p = s.split(":");
            insertIngredient(db, rid, p[0], Double.parseDouble(p[1]), p[2]);
        }
    }

    /** Pre-loads the built-in recipes. Called once, from DatabaseHelper.onCreate(). */
    public static void seed(SQLiteDatabase db) {
        addRecipe(db, "Scrambled Eggs", "1. Whisk eggs with salt.\n2. Melt butter in a pan.\n3. Stir eggs gently until set.",
                "egg:3:pcs", "butter:10:g", "salt:1:tsp");
        addRecipe(db, "Tomato Pasta", "1. Boil pasta.\n2. Fry onion in oil, add chopped tomatoes.\n3. Simmer 10 min and toss with pasta.",
                "pasta:200:g", "tomato:3:pcs", "onion:1:pcs", "olive oil:1:tbsp");
        addRecipe(db, "Cheese Omelette", "1. Beat eggs.\n2. Cook in buttered pan.\n3. Add cheese, fold and serve.",
                "egg:2:pcs", "cheese:50:g", "butter:10:g");
        addRecipe(db, "Fried Rice", "1. Fry onion.\n2. Add cooked rice and scrambled egg.\n3. Season with soy sauce.",
                "rice:200:g", "egg:2:pcs", "onion:1:pcs", "soy sauce:2:tbsp");
        addRecipe(db, "Pancakes", "1. Mix flour, sugar, milk and egg.\n2. Fry ladlefuls until golden both sides.",
                "flour:150:g", "milk:250:ml", "egg:1:pcs", "sugar:1:tbsp");
        addRecipe(db, "Mashed Potatoes", "1. Boil peeled potatoes.\n2. Mash with butter, milk and salt.",
                "potato:4:pcs", "butter:30:g", "milk:50:ml", "salt:1:tsp");
        addRecipe(db, "Garlic Bread", "1. Mix butter with crushed garlic.\n2. Spread on bread.\n3. Bake 10 min at 180C.",
                "bread:4:pcs", "butter:40:g", "garlic:2:pcs");
        addRecipe(db, "Tomato Soup", "1. Fry onion and garlic.\n2. Add tomatoes and stock.\n3. Simmer 20 min and blend.",
                "tomato:6:pcs", "onion:1:pcs", "garlic:2:pcs", "vegetable stock:500:ml");
        addRecipe(db, "Cheese Toastie", "1. Butter bread outsides.\n2. Fill with cheese.\n3. Toast until golden.",
                "bread:2:pcs", "cheese:40:g", "butter:10:g");
        addRecipe(db, "Chicken Rice Bowl", "1. Season and pan-fry chicken.\n2. Cook rice.\n3. Serve with fried onion.",
                "chicken:300:g", "rice:200:g", "onion:1:pcs", "salt:1:tsp");
        addRecipe(db, "Bean Chilli", "1. Fry onion.\n2. Add beans, tomatoes and chilli powder.\n3. Simmer 20 min.",
                "beans:400:g", "tomato:3:pcs", "onion:1:pcs", "chili powder:1:tsp");
        addRecipe(db, "Banana Pancakes", "1. Mash bananas.\n2. Mix with eggs and flour.\n3. Fry small pancakes.",
                "banana:2:pcs", "egg:2:pcs", "flour:50:g");
        addRecipe(db, "Potato Omelette", "1. Fry sliced potato and onion in oil.\n2. Pour in beaten eggs.\n3. Cook both sides.",
                "potato:2:pcs", "egg:4:pcs", "onion:1:pcs", "olive oil:2:tbsp");
        addRecipe(db, "Mac and Cheese", "1. Boil pasta.\n2. Melt butter, add milk and cheese.\n3. Stir through pasta.",
                "pasta:250:g", "cheese:150:g", "milk:200:ml", "butter:20:g");
        addRecipe(db, "Garlic Butter Chicken", "1. Melt butter with garlic.\n2. Cook chicken until golden and done.",
                "chicken:400:g", "garlic:3:pcs", "butter:30:g");
        addRecipe(db, "Veggie Stir Fry", "1. Slice carrot and onion.\n2. Stir-fry, add soy sauce.\n3. Serve on rice.",
                "carrot:2:pcs", "onion:1:pcs", "soy sauce:2:tbsp", "rice:150:g");
    }
}
