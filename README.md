# Smart Pantry Manager

A Java Android app that helps reduce food waste. The user records the ingredients they actually have at home
(their "pantry") and the app suggests recipes they can cook using **only** those ingredients: no shopping trip
required, and no recipe is suggested unless the pantry genuinely covers everything it needs.

> Author: 402312692
> Language: Java (no Kotlin) - IDE: Android Studio - Min SDK 24

## Features

| Area | What it does |
|------|--------------|
| Pantry management | Add, edit and delete ingredients (name, quantity, unit, optional expiry date) with input validation |
| Pantry list | RecyclerView with a custom adapter bound to SQLite, plus a live search box |
| Recipe collection | 16 seeded recipes loaded on first run, plus user-created recipes (add / view / delete) |
| **Suggested Recipes** | Runs the **strict-matching rule** against the current pantry and lists only recipes that can be made right now |
| Almost There (bonus) | A separate section for recipes missing exactly one ingredient, clearly apart from the strict list |
| Expiry-first ranking | Suggestions are ordered by how many soon-to-expire ingredients they use |
| Cook this | Deducts a recipe's quantities from the pantry and removes items that reach zero |
| Filters | Quick meals (20 min or less), uses expiring food, my recipes |
| Recipe detail | Full ingredient list (each marked as in / not in the pantry) and the method |
| Settings | Toggle for expiring-soon alerts |
| Expiry reminder | Daily notification via WorkManager, controlled by the Settings toggle |
| Navigation | Bottom navigation bar (Pantry, Suggestions, Recipes, Settings) |
| Empty states | "No recipes match your pantry yet - add more ingredients" (and an illustration) instead of a blank screen |

The app does **not** use Google Maps, any mapping SDK, or location/GPS services.

## The strict-matching rule

A recipe is shown as suggested only if **every** ingredient it needs is in the pantry in at least the required
quantity. 4 of 5 ingredients means the recipe is **not** suggested (see `RecipeMatcher`).
Matching is robust to everyday messiness:

* **Names:** case, spacing and plurals are normalised (`Tomatoes` = `tomato`, `berries` = `berry`).
* **Synonyms:** regional names map to one canonical name (`aubergine` = `eggplant`, `coriander` = `cilantro`).
* **Units:** quantities are converted within a unit family (1 kg satisfies 500 g; 1 l satisfies 250 ml). Mass, volume and
  count units are never mixed.
* **Duplicates:** the same ingredient stored in several pantry rows is summed.

The logic is plain Java with no Android classes, so it is covered by JVM unit tests (see below).

## Database: SQLite (`SQLiteOpenHelper`)

**Why SQLite:** the app's data (a pantry and a recipe book) is personal, small and relational, and it must work offline
in a kitchen with no signal. SQLite is on-device, needs no account, server or network, persists across restarts, and is
the approach covered in the module's persistent-data chapter. Firebase and PostgreSQL would add sign-in, hosting and
connectivity requirements with no benefit for a single-user app.

**Schema (version 2)**

```
pantry              recipes                      recipe_ingredients
------              -------                      ------------------
id  PK              id  PK                       id  PK
name                name                         recipe_id  FK -> recipes.id
quantity (REAL)     steps                        name
unit                prep_minutes (0 = unknown)   quantity (REAL)
expiry (TEXT)       is_custom (0 / 1)            unit
```
`recipes 1 --- * recipe_ingredients`. `pantry` is independent: it is matched against recipes in code, not by foreign key.

**Migrations:** `DatabaseHelper.onUpgrade()` is a real versioned migration (v1 to v2 adds `prep_minutes`, `is_custom` and an index)
that keeps all user data. To change the schema later: bump `DB_VERSION`, update `onCreate()`, and add a `migrateNtoN+1()` step.

**CRUD:** Create (add ingredient / recipe), Read (pantry list, recipe lists), Update (edit ingredient, cook-this deduction),
Delete (delete ingredient / custom recipe). Data persists after the app is closed and reopened.

## Setup and run

1. Install **Android Studio** (Hedgehog or newer) with an Android SDK (API 34 recommended) and a JDK 17 (bundled).
2. Clone the repository and open the project folder in Android Studio:
   ```
   git clone <your-repository-url>
   ```
3. Wait for the first **Gradle sync** to finish (it downloads Gradle 8.2 and the dependencies, so it needs internet the first time).
   The project already includes the Gradle wrapper, `build.gradle` files, `AndroidManifest.xml` (notification permission, all
   screens registered, one launcher activity) and a Material theme (`Theme.MaterialComponents.Light.DarkActionBar`).
   Dependencies used: Material 1.11.0, AppCompat 1.6.1, WorkManager 2.9.0, RecyclerView 1.3.2, JUnit 4.13.2.
4. Choose an emulator or a physical device (Android 7.0 / API 24 or higher) and press **Run**.
   On first launch the database is created and the recipes are seeded automatically.

### Run the unit tests
```
./gradlew testDebugUnitTest
```
or right-click `app/src/test` in Android Studio and choose **Run Tests**. They are plain JVM tests (no emulator needed)
and cover: 4 of 5 ingredients not suggested, plural names, 1 kg satisfying 500 g, synonyms, the Almost There list,
expiry-first ranking, cook-this deduction and removal, pantry search, and date parsing.

## Project structure

```
SmartPantryManager/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/smartpantrymanager/
│   │   │   ├── MainActivity.java              launcher: opens the pantry list and finishes
│   │   │   ├── activities/
│   │   │   │   ├── PantryListActivity.java        pantry list, search, add/edit/delete entry point
│   │   │   │   ├── AddEditIngredientActivity.java add / edit an ingredient (with validation)
│   │   │   │   ├── SuggestedRecipesActivity.java  strict list + Almost There, filters, Cook this
│   │   │   │   ├── RecipeDetailActivity.java      ingredients (in pantry or not) and method
│   │   │   │   ├── SettingsActivity.java          expiring-soon alerts toggle
│   │   │   │   ├── RecipesActivity.java           recipe collection (built-in + custom)
│   │   │   │   └── AddRecipeActivity.java         create a custom recipe (with validation)
│   │   │   ├── adapters/
│   │   │   │   ├── IngredientAdapter.java         pantry list rows
│   │   │   │   ├── SuggestionAdapter.java         suggestion cards (ready / almost there)
│   │   │   │   └── RecipeAdapter.java             recipe collection rows
│   │   │   ├── database/
│   │   │   │   ├── DatabaseHelper.java            SQLite schema, versioned migrations, CRUD
│   │   │   │   └── RecipeSeeder.java              the built-in recipes
│   │   │   ├── models/
│   │   │   │   ├── Ingredient.java                a pantry item
│   │   │   │   ├── Recipe.java
│   │   │   │   └── RecipeIngredient.java          one ingredient line of a recipe
│   │   │   └── utils/
│   │   │       ├── RecipeMatcher.java             strict matching, Almost There, ranking, cook plan
│   │   │       ├── Units.java                     unit families and conversion
│   │   │       ├── Synonyms.java, ExpiryDates.java, PantryFilter.java, Fmt.java
│   │   │       ├── BottomNav.java                 shared bottom navigation
│   │   │       └── ExpiryScheduler.java, ExpiryWorker.java   daily reminder (WorkManager)
│   │   └── res/
│   │       ├── layout/
│   │       ├── menu/
│   │       ├── values/
│   │       └── drawable/
│   ├── src/test/java/com/example/smartpantrymanager/utils/
│   │   └── RecipeMatcherTest.java, ExpiryAndCookTest.java
│   └── build.gradle
└── README.md
```

## Design decisions

* **Almost There** treats an ingredient as "the one gap" if it is absent *or* present but short (the card says which).
* **Expiring soon** means expiring today up to 3 days ahead; items already past their date are not promoted.
* **Cook this** uses the earliest-expiring pantry row first when an ingredient is stored in several rows, and converts units.
* `MainActivity` is a thin launcher that opens `PantryListActivity` and closes itself.
* Screens pass only ids through Intents (for example `recipe_id`) and reload from the database, so they always show current data.
