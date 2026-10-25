package com.example.smartpantrymanager.adapters;

import com.example.smartpantrymanager.R;
import com.example.smartpantrymanager.models.Recipe;
import com.example.smartpantrymanager.utils.Fmt;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/** All recipes (built-in + custom). Custom ones show a delete button. */
public class RecipeAdapter extends RecyclerView.Adapter<RecipeAdapter.Holder> {

    public interface Listener {
        void onOpen(Recipe recipe);
        void onDelete(Recipe recipe);
    }

    private final List<Recipe> recipes;
    private final Listener listener;

    public RecipeAdapter(List<Recipe> recipes, Listener listener) {
        this.recipes = recipes;
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recipe, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        final Recipe r = recipes.get(position);
        h.name.setText(r.name);
        int n = r.ingredients.size();
        h.meta.setText(Fmt.meta(r) + " \u00B7 " + n + (n == 1 ? " ingredient" : " ingredients"));
        h.delete.setVisibility(r.custom ? View.VISIBLE : View.GONE);
        h.delete.setOnClickListener(v -> listener.onDelete(r));
        h.itemView.setOnClickListener(v -> listener.onOpen(r));
    }

    @Override public int getItemCount() { return recipes.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView name, meta; final View delete;
        Holder(View v) {
            super(v);
            name = v.findViewById(R.id.tvName);
            meta = v.findViewById(R.id.tvMeta);
            delete = v.findViewById(R.id.btnDelete);
        }
    }
}
