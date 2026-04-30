package com.example.myapp;

import android.content.Context;
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

public class TeamMemberAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // 뷰 타입 구분 (헤더 vs 선수 카드)
    public static final int TYPE_HEADER = 0;
    public static final int TYPE_PLAYER = 1;

    private List<MemberItem> itemList = new ArrayList<>();
    private String captainUid;
    private String viceCaptainUid;
    private String currentUid;
    private OnPlayerLongClickListener longClickListener;

    // 아이템 클릭 인터페이스 (MyTeam.java로 다이얼로그 띄우기 요청을 전달)
    public interface OnPlayerLongClickListener {
        void onLongClick(String nickname, String uid);
    }

    public TeamMemberAdapter(String captainUid, String viceCaptainUid, String currentUid, OnPlayerLongClickListener listener) {
        this.captainUid = captainUid;
        this.viceCaptainUid = viceCaptainUid;
        this.currentUid = currentUid;
        this.longClickListener = listener;
    }

    public void setItems(List<MemberItem> items) {
        this.itemList = items;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return itemList.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            // 헤더용 간단한 텍스트뷰 생성 (XML 없이 코드로 즉석 생성)
            TextView headerView = new TextView(parent.getContext());
            headerView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            headerView.setPadding(dp(parent.getContext(), 16), dp(parent.getContext(), 16), dp(parent.getContext(), 16), dp(parent.getContext(), 8));
            headerView.setTextSize(16f);
            headerView.setTextColor(android.graphics.Color.BLACK);
            headerView.setTypeface(null, android.graphics.Typeface.BOLD);
            return new HeaderViewHolder(headerView);
        } else {
            // 기존에 쓰시던 선수 그리드 아이템 재사용
            View view = inflater.inflate(R.layout.player_grid_item_plain, parent, false);
            return new PlayerViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        MemberItem item = itemList.get(position);

        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).tvHeader.setText(item.headerTitle);
        } else if (holder instanceof PlayerViewHolder) {
            PlayerViewHolder pvt = (PlayerViewHolder) holder;
            Context ctx = pvt.itemView.getContext();

            StringBuilder label = new StringBuilder();
            label.append(!TextUtils.isEmpty(item.nickname) ? item.nickname : "(이름 없음)");

            if (!TextUtils.isEmpty(captainUid) && captainUid.equals(item.uid)) {
                label.append(" | 주장");
            } else if (!TextUtils.isEmpty(viceCaptainUid) && viceCaptainUid.equals(item.uid)) {
                label.append(" | 부주장");
            }
            pvt.tvName.setText(label.toString());

            if (!TextUtils.isEmpty(item.imageUrl)) {
                Glide.with(ctx).load(item.imageUrl).into(pvt.imgProfile);
            } else {
                pvt.imgProfile.setImageResource(R.drawable.default_profile_image);
            }

            // 주장인 경우에만 롱클릭 허용
            if (!TextUtils.isEmpty(captainUid) && currentUid.equals(captainUid)) {
                pvt.itemView.setOnLongClickListener(v -> {
                    if (longClickListener != null) {
                        longClickListener.onLongClick(item.nickname, item.uid);
                    }
                    return true;
                });
            } else {
                pvt.itemView.setOnLongClickListener(null); // 권한 없으면 무시
            }
        }
    }

    @Override
    public int getItemCount() {
        return itemList.size();
    }

    // dp 변환 유틸
    private int dp(Context context, int v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }

    // ===== 뷰홀더 (ViewHolder) =====
    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvHeader;
        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvHeader = (TextView) itemView;
        }
    }

    static class PlayerViewHolder extends RecyclerView.ViewHolder {
        ImageView imgProfile;
        TextView tvName;
        public PlayerViewHolder(@NonNull View itemView) {
            super(itemView);
            imgProfile = itemView.findViewById(R.id.playerImage);
            tvName = itemView.findViewById(R.id.playerName);
        }
    }

    // ===== 데이터 모델 =====
    public static class MemberItem {
        public int type; // 0: Header, 1: Player
        public String headerTitle;

        public String uid;
        public String nickname;
        public String imageUrl;

        // 헤더용 생성자
        public MemberItem(String headerTitle) {
            this.type = TYPE_HEADER;
            this.headerTitle = headerTitle;
        }

        // 선수용 생성자
        public MemberItem(String uid, String nickname, String imageUrl) {
            this.type = TYPE_PLAYER;
            this.uid = uid;
            this.nickname = nickname;
            this.imageUrl = imageUrl;
        }
    }
}