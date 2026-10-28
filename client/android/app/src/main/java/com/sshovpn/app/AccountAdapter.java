package com.sshovpn.app;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.VH> {

    public interface Callback {
        void onSelect(Account a);
        void onMenu(Account a, View anchor);
    }

    private final List<Account> items = new ArrayList<>();
    private final Callback cb;
    private String activeName;

    public AccountAdapter(Callback cb) { this.cb = cb; }

    public void submit(List<Account> list, String active) {
        items.clear();
        items.addAll(list);
        activeName = active;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_account, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final Account a = items.get(position);
        boolean selected = a.name.equals(activeName);
        h.name.setText(a.name);
        h.info.setText(a.summary());
        h.root.setBackgroundResource(selected ? R.drawable.bg_selected : R.drawable.bg_item);
        h.dot.setBackgroundTintList(ColorStateList.valueOf(
                h.root.getResources().getColor(selected ? R.color.success : R.color.outline, null)));
        h.root.setOnClickListener(v -> cb.onSelect(a));
        h.more.setOnClickListener(v -> cb.onMenu(a, v));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final View root, dot;
        final TextView name, info;
        final ImageButton more;

        VH(@NonNull View v) {
            super(v);
            root = v.findViewById(R.id.itemRoot);
            dot = v.findViewById(R.id.vDot);
            name = v.findViewById(R.id.tvName);
            info = v.findViewById(R.id.tvInfo);
            more = v.findViewById(R.id.btnMore);
        }
    }
}
