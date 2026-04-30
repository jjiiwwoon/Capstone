package com.example.myapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.ArrayList;
import java.util.List;

/** 2열 그리드용: 프로필 + 닉네임 (+ 용병칩) */
public class AttendanceGridAdapter extends RecyclerView.Adapter<AttendanceGridAdapter.VH> {

    public static class Item {
        public String uid;
        public String nickname;
        public String profileImageUrl;
        public boolean isMercenary;

        public Item(String uid, String nickname, String profileImageUrl, boolean isMercenary) {
            this.uid = uid;
            this.nickname = nickname;
            this.profileImageUrl = profileImageUrl;
            this.isMercenary = isMercenary;
        }
    }

    private final List<Item> items = new ArrayList<>();

    public void submitList(List<Item> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_attendance_user, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Item it = items.get(pos);
        h.tvNickname.setText(it.nickname == null ? "-" : it.nickname);

        if (it.isMercenary) {
            h.chipMercenary.setVisibility(View.VISIBLE);
        } else {
            h.chipMercenary.setVisibility(View.GONE);
        }

        if (it.profileImageUrl == null || it.profileImageUrl.trim().isEmpty()) {
            h.ivProfile.setImageResource(R.drawable.ic_placeholder_circle);
        } else {
            Glide.with(h.ivProfile.getContext())
                    .load(it.profileImageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .placeholder(R.drawable.ic_placeholder_circle)
                    .error(R.drawable.ic_placeholder_circle)
                    .circleCrop()
                    .into(h.ivProfile);
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivProfile;
        TextView tvNickname;
        TextView chipMercenary;
        VH(@NonNull View v) {
            super(v);
            ivProfile = v.findViewById(R.id.ivProfile);
            tvNickname = v.findViewById(R.id.tvNickname);
            chipMercenary = v.findViewById(R.id.chipMercenary);
        }
    }
}
