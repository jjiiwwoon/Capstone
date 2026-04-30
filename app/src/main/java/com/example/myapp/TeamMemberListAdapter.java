package com.example.myapp;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TeamMemberListAdapter extends RecyclerView.Adapter<TeamMemberListAdapter.VH> {

    public static class Item {
        public final String uid;       // ✅ 역할 판단용
        public final String name;
        public final String position;
        public final String role;      // "주장"/"부주장"/null
        public final String photoUrl;
        public final int goals;

        public Item(String uid, String name, String position, String role, String photoUrl, int goals) {
            this.uid = uid;
            this.name = name;
            this.position = position;
            this.role = role;
            this.photoUrl = photoUrl;
            this.goals = goals;
        }
    }

    private final List<Item> items = new ArrayList<>();

    public void submit(List<Item> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_team_member, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Item it = items.get(position);

        h.tvName.setText(TextUtils.isEmpty(it.name) ? "이름 없음" : it.name);

        String pos = TextUtils.isEmpty(it.position) ? "-" : it.position;
        h.tvPos.setText(pos);
        h.tvPos.setTextColor(colorForPos(pos));

        // 역할: 있으면 표시(주장/부주장)
        if (!TextUtils.isEmpty(it.role)) {
            h.tvPipe2.setVisibility(View.VISIBLE);
            h.tvRole.setVisibility(View.VISIBLE);
            h.tvRole.setText(it.role);
            // 역할 색상은 적당히 강조
            h.tvRole.setTextColor("부주장".equals(it.role) ? 0xFF6D4C41 : 0xFFD32F2F);
        } else {
            h.tvPipe2.setVisibility(View.GONE);
            h.tvRole.setVisibility(View.GONE);
        }

        h.tvGoals.setText(String.format(Locale.getDefault(), "%d골", it.goals));

        if (!TextUtils.isEmpty(it.photoUrl)) {
            Glide.with(h.img.getContext()).load(it.photoUrl).into(h.img);
        } else {
            h.img.setImageResource(R.drawable.default_profile_image);
        }
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView img; TextView tvName, tvPos, tvRole, tvGoals, tvPipe2;
        VH(@NonNull View v) {
            super(v);
            img = v.findViewById(R.id.imgPlayer);
            tvName = v.findViewById(R.id.tvPlayerName);
            tvPos = v.findViewById(R.id.tvPlayerPos);
            tvRole = v.findViewById(R.id.tvPlayerRole);
            tvGoals = v.findViewById(R.id.tvPlayerGoals);
            tvPipe2 = v.findViewById(R.id.tvPipe2);
        }
    }

    private int colorForPos(String pos) {
        if (pos == null) return 0xFF666666;
        switch (pos.toUpperCase(Locale.ROOT)) {
            case "GK": return 0xFF1E88E5;
            case "DF": return 0xFF43A047;
            case "MF": return 0xFFF9A825;
            case "FW": return 0xFFE53935;
            default:   return 0xFF666666;
        }
    }
}
