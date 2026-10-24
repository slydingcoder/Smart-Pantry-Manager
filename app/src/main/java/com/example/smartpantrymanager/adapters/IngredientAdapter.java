package com.example.smartpantrymanager.adapters;

import com.example.smartpantrymanager.R;
import com.example.smartpantrymanager.models.Ingredient;
import com.example.smartpantrymanager.utils.ExpiryDates;
import com.example.smartpantrymanager.utils.Fmt;
import com.example.smartpantrymanager.utils.RecipeMatcher;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/** Pantry list rows: name, quantity + unit, expiry status, and a delete button. Tapping a row edits it. */
public class IngredientAdapter extends RecyclerView.Adapter<IngredientAdapter.Holder> {

    public interface Listener {
        void onEdit(Ingredient item);
        void onDelete(Ingredient item);
    }

    private final List<Ingredient> items;
    private final boolean highlightExpiring;
    private final Listener listener;

    /** @param highlightExpiring true = colour the expiry line when food is expiring soon / expired */
    public IngredientAdapter(List<Ingredient> items, boolean highlightExpiring, Listener listener) {
        this.items = items;
        this.highlightExpiring = highlightExpiring;
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ingredient, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        final Ingredient item = items.get(position);
        h.name.setText(item.name);
        h.qty.setText(Fmt.qty(item.quantity) + " " + item.unit);

        long days = ExpiryDates.daysUntil(item.expiry, ExpiryDates.today());
        if (days == ExpiryDates.NO_DATE) {
            h.expiry.setText("No expiry date");
            h.expiry.setTextColor(h.secondaryColor);
        } else {
            String text;
            boolean warn = false;
            if (days < 0) { text = "Expired " + item.expiry; warn = true; }
            else if (days == 0) { text = "Expires today"; warn = true; }
            else if (days == 1) { text = "Expires tomorrow"; warn = true; }
            else if (days <= RecipeMatcher.EXPIRING_SOON_DAYS) { text = "Expires in " + days + " days"; warn = true; }
            else text = "Expires " + item.expiry;
            h.expiry.setText(text);
            h.expiry.setTextColor(highlightExpiring && warn ? h.warnColor : h.secondaryColor);
        }
        h.delete.setOnClickListener(v -> listener.onDelete(item));
        h.itemView.setOnClickListener(v -> listener.onEdit(item));
    }

    @Override public int getItemCount() { return items.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView name, qty, expiry; final View delete;
        final int secondaryColor, warnColor;
        Holder(View v) {
            super(v);
            name = v.findViewById(R.id.tvName);
            qty = v.findViewById(R.id.tvQty);
            expiry = v.findViewById(R.id.tvExpiry);
            delete = v.findViewById(R.id.btnDelete);
            secondaryColor = expiry.getTextColors().getDefaultColor();
            warnColor = v.getResources().getColor(R.color.expiry_warning, v.getContext().getTheme());
        }
    }
}
