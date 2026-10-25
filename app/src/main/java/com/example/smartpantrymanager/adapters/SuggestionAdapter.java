package com.example.smartpantrymanager.adapters;

import com.example.smartpantrymanager.R;
import com.example.smartpantrymanager.models.Recipe;
import com.example.smartpantrymanager.utils.Fmt;
import com.example.smartpantrymanager.utils.RecipeMatcher;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/** One list with three row types: section header, "ready to cook" card, "almost there" card. */
public class SuggestionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onCook(Recipe recipe);
        void onOpen(Recipe recipe);   // card tapped: open the recipe detail screen
    }

    private static final int HEADER = 0, READY = 1, NEAR = 2;

    private static final class Row {
        final int type; final String title; final String subtitle;
        final RecipeMatcher.Ranked ready; final RecipeMatcher.NearMiss near;
        Row(int type, String title, String subtitle, RecipeMatcher.Ranked ready, RecipeMatcher.NearMiss near) {
            this.type = type; this.title = title; this.subtitle = subtitle; this.ready = ready; this.near = near;
        }
        Recipe recipe() { return ready != null ? ready.recipe : near.recipe; }
    }

    private final List<Row> rows = new ArrayList<>();
    private final Listener listener;

    public SuggestionAdapter(List<RecipeMatcher.Ranked> ready, List<RecipeMatcher.NearMiss> near, Listener listener) {
        this.listener = listener;
        if (!ready.isEmpty()) {
            rows.add(new Row(HEADER, "Ready to cook \u00B7 " + ready.size(),
                    "Every ingredient is in your pantry. Food that expires soonest comes first.", null, null));
            for (RecipeMatcher.Ranked r : ready) rows.add(new Row(READY, null, null, r, null));
        }
        if (!near.isEmpty()) {
            rows.add(new Row(HEADER, "Almost there \u00B7 " + near.size(),
                    "Just one ingredient away.", null, null));
            for (RecipeMatcher.NearMiss n : near) rows.add(new Row(NEAR, null, null, null, n));
        }
    }

    @Override public int getItemViewType(int position) { return rows.get(position).type; }
    @Override public int getItemCount() { return rows.size(); }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater in = LayoutInflater.from(parent.getContext());
        if (viewType == HEADER) return new CardHolder(in.inflate(R.layout.item_suggestion_header, parent, false));
        if (viewType == READY) return new CardHolder(in.inflate(R.layout.item_suggestion_ready, parent, false));
        return new CardHolder(in.inflate(R.layout.item_suggestion_near, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        final Row row = rows.get(position);
        View v = holder.itemView;
        if (row.type == HEADER) {
            ((TextView) v.findViewById(R.id.tvHeaderTitle)).setText(row.title);
            ((TextView) v.findViewById(R.id.tvHeaderSubtitle)).setText(row.subtitle);
            return;
        }
        final Recipe r = row.recipe();
        ((TextView) v.findViewById(R.id.tvName)).setText(r.name);
        ((TextView) v.findViewById(R.id.tvMeta)).setText(Fmt.meta(r));

        TextView tvExpiring = v.findViewById(R.id.tvExpiring);
        int count = row.ready != null ? row.ready.expiringCount : row.near.expiringCount;
        List<String> names = row.ready != null ? row.ready.expiringNames : row.near.expiringNames;
        tvExpiring.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        if (count > 0) tvExpiring.setText("Uses food expiring soon: " + Fmt.join(names));

        if (row.near != null) {
            RecipeMatcher.NearMiss n = row.near;
            ((TextView) v.findViewById(R.id.tvMissing)).setText(n.absent
                    ? "Missing: " + Fmt.ingredient(n.ingredient)
                    : "Need " + Fmt.qty(n.shortBy) + " " + n.ingredient.unit + " more " + n.ingredient.name);
        } else {
            MaterialButton cook = v.findViewById(R.id.btnCook);
            cook.setOnClickListener(x -> listener.onCook(r));
        }

        v.setOnClickListener(x -> listener.onOpen(r));   // Intent to RecipeDetailActivity is fired by the activity
    }

    private static final class CardHolder extends RecyclerView.ViewHolder {
        CardHolder(View v) { super(v); }
    }
}
