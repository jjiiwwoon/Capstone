package com.example.myapp;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecruitAdapter extends RecyclerView.Adapter<RecruitAdapter.VH> {

    private final List<RecruitItem> items = new ArrayList<>();

    public void submit(List<RecruitItem> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.recruit_post_item, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Context ctx = h.itemView.getContext();
        RecruitItem it = items.get(position);

        // ✅ 팀 이름 세팅 (이게 없어서 안 보였던 거)
        h.textTeamName.setText(nz(it.teamName, ""));

        // 팀 로고
        Glide.with(ctx)
                .load(it.teamLogoUrl)
                .placeholder(R.mipmap.ic_launcher)
                .into(h.imageTeamLogo);

        // 🔴 날짜(요일) 세팅
        h.textDate.setText(buildDateWithWeekday(it));

        // 시간
        h.textTime.setText(nz(it.time, "-"));

        // ✅ 주소 라인
        String placeName = safe(it.stadiumName);
        String addr = safe(it.stadiumAddress);
        String addressLine;
        if (!placeName.isEmpty() && !addr.isEmpty()) {
            addressLine = placeName + " | " + addr;
        } else if (!placeName.isEmpty()) {
            addressLine = placeName;
        } else if (!addr.isEmpty()) {
            addressLine = addr;
        } else {
            addressLine = "-";
        }
        h.textStadium.setText(addressLine);

        // 실력 범위
        String skillLeft = it.skillMin == null ? "-" : String.valueOf(it.skillMin);
        String skillRight = it.skillMax == null ? "-" : String.valueOf(it.skillMax);
        h.textSkill.setText(String.format(Locale.getDefault(), "실력 : %s ~ %s", skillLeft, skillRight));

        // 모집 종류 배지
        String raw = nz(it.recruitType, "");
        boolean isMercenary = "mercenary".equalsIgnoreCase(raw);
        String typeLabel = isMercenary ? "용병" : "회원";
        h.textRecruitType.setText(typeLabel);
        h.textRecruitType.setBackgroundResource(
                isMercenary ? R.drawable.bg_badge_mercenary : R.drawable.bg_badge_member
        );
        h.textRecruitType.setTextColor(0xFFFFFFFF);

        // 포지션 칩
        h.chipGroupPositions.removeAllViews();
        if (it.positions != null && !it.positions.isEmpty()) {
            for (String p : it.positions) {
                if (TextUtils.isEmpty(p)) continue;
                Chip c = new Chip(ctx);
                c.setText(p);
                c.setClickable(false);
                c.setCheckable(false);
                stylePositionChip(c, p);
                h.chipGroupPositions.addView(c);
            }
        }

        // 작성된 시간
        long displayEpoch = 0L;
        if (!TextUtils.isEmpty(it.relativeTime)) {
            h.textTimestamp.setText(it.relativeTime);
        } else {
            if (it.createdAtMs > 0) {
                displayEpoch = it.createdAtMs;
            } else if (it.createdAt > 0) {
                displayEpoch = it.createdAt;
            } else if (it.postTs > 0) {
                displayEpoch = it.postTs;
            } else if (it.matchTs > 0) {
                displayEpoch = it.matchTs;
            }
            h.textTimestamp.setText(formatRelativeFromPast(displayEpoch));
        }

        // 클릭 → 상세
        h.itemRoot.setOnClickListener(v -> {
            Intent intent = new Intent(ctx, RecruitDetail.class);
            intent.putExtra("recruitId", it.id);
            ctx.startActivity(intent);
        });
    }


    // 🔧 dp 유틸
    private static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    // ✅ 포지션 칩 스타일
    private void stylePositionChip(@NonNull Chip chip, @NonNull String posRaw) {
        String pos = posRaw.trim().toUpperCase(Locale.ROOT);
        String colorHex;
        switch (pos) {
            case "GK":
                colorHex = "#FFD600";
                break;
            case "DF":
                colorHex = "#2962FF";
                break;
            case "MF":
                colorHex = "#00C853";
                break;
            case "FW":
                colorHex = "#D50000";
                break;
            default:
                colorHex = "#666666";
                break;
        }
        int color = android.graphics.Color.parseColor(colorHex);

        chip.setChipBackgroundColor(
                android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        );
        chip.setTextColor(color);
        chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(color));
        chip.setChipStrokeWidth(dp(chip.getContext(), 1));
        chip.setEnsureMinTouchTargetSize(false);
        chip.setChipMinHeight(dp(chip.getContext(), 24));
        chip.setTextSize(11f);
        chip.setPadding(dp(chip.getContext(), 8),
                dp(chip.getContext(), 2),
                dp(chip.getContext(), 8),
                dp(chip.getContext(), 2));
        chip.setRippleColor(android.content.res.ColorStateList.valueOf(0x1A000000));
        chip.setElevation(0f);
        chip.setClickable(false);
        chip.setCheckable(false);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        View itemRoot;
        ImageView imageTeamLogo;
        TextView textTeamName, textDate, textTime, textStadium, textSkill, textRecruitType, textTimestamp;
        ChipGroup chipGroupPositions;

        VH(@NonNull View v) {
            super(v);
            itemRoot = v.findViewById(R.id.itemRoot);
            imageTeamLogo = v.findViewById(R.id.imageTeamLogo);
            textTeamName = v.findViewById(R.id.textTeamName);
            textDate = v.findViewById(R.id.textDate);
            textTime = v.findViewById(R.id.textTime);
            textStadium = v.findViewById(R.id.textStadium);
            textSkill = v.findViewById(R.id.textSkill);
            textRecruitType = v.findViewById(R.id.textRecruitType);
            textTimestamp = v.findViewById(R.id.textTimestamp);
            chipGroupPositions = v.findViewById(R.id.chipGroupPositions);
        }
    }

    // ===== 날짜(요일) 빌더 =====
    private String buildDateWithWeekday(RecruitItem it) {
        String date = nz(it.date, "-");
        // 1) Firestore에 weekday가 이미 들어있는 경우 ("월", "화"...)
        if (!TextUtils.isEmpty(it.weekday)) {
            return date + "(" + it.weekday + ")";
        }
        // 2) weekday가 없으면 date로 계산
        String w = calcWeekdayFromDate(it.date);
        if (!TextUtils.isEmpty(w)) {
            return date + "(" + w + ")";
        }
        // 3) 둘 다 없으면 그냥 날짜
        return date;
    }

    // yyyy-MM-dd → 요일(월~일)
    @Nullable
    private String calcWeekdayFromDate(@Nullable String dateStr) {
        if (TextUtils.isEmpty(dateStr)) return null;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date d = sdf.parse(dateStr);
            if (d == null) return null;
            // 한국어 요일
            SimpleDateFormat wdf = new SimpleDateFormat("E", Locale.KOREAN);
            // "수" 이런 식으로 나옴
            return wdf.format(d);
        } catch (ParseException e) {
            return null;
        }
    }

    // ===== 유틸 =====
    private static String nz(String s, String d) {
        return s == null || s.isEmpty() ? d : s;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    @Nullable
    private static String formatRelativeFromPast(long epochMs) {
        if (epochMs <= 0) return null;
        long now = System.currentTimeMillis();
        long diff = now - epochMs;

        if (diff < 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            return sdf.format(new Date(epochMs));
        }

        long min = diff / 60_000L;
        if (min < 1) return "방금 전";
        if (min < 60) return min + "분 전";

        long hr = min / 60;
        if (hr < 24) return hr + "시간 전";

        long day = hr / 24;
        if (day < 7) return day + "일 전";

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date(epochMs));
    }

    // 리스트용 DTO
    public static class RecruitItem {
        public String id;
        public String teamLogoUrl;
        public String teamName;
        public String date;
        public String time;
        public String stadiumName;
        public String stadiumAddress;
        public Integer skillMin;
        public Integer skillMax;
        public List<String> positions;
        public String recruitType;
        public String relativeTime;
        public long matchTs;
        public long postTs;
        public long createdAtMs;
        public long createdAt;
        public String weekday;   // "월","화","수" ...
    }
}
