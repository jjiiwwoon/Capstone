// src/main/java/com/example/myapp/MatchPostAdapter.java
package com.example.myapp;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MatchPostAdapter extends RecyclerView.Adapter<MatchPostAdapter.MatchPostViewHolder> {

    private List<MatchPost> postList;
    private final Context context;

    // ★ 추천 바텀시트에서 recommend_match_item을 사용할지 여부
    private final boolean useRecommendLayout;

    // ★ (옵션) 추천 이유 뱃지 데이터: matchId -> reasons(최대 3개 사용)
    private Map<String, List<String>> reasonsMap;

    // 일반 리스트용
    public MatchPostAdapter(Context context, List<MatchPost> postList) {
        this(context, postList, false);
    }

    // 추천 영역용(useRecommendLayout=true)
    public MatchPostAdapter(Context context, List<MatchPost> postList, boolean useRecommendLayout) {
        this.context = context;
        this.postList = postList;
        this.useRecommendLayout = useRecommendLayout;
    }

    // (옵션) 추천 이유 뱃지 세팅
    public void setReasonsMap(Map<String, List<String>> reasonsMap) {
        this.reasonsMap = reasonsMap;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MatchPostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = useRecommendLayout ? R.layout.recommend_match_item : R.layout.match_post_item;
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
        return new MatchPostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MatchPostViewHolder holder, int position) {
        MatchPost post = postList.get(position);

        holder.textTeamName.setText(safe(post.getTeamName()));

        // 🔴 날짜(요일)로 표시
        holder.textDate.setText(buildDateWithWeekday(post));

        // 시간은 그대로
        holder.textTime.setText(safe(post.getTime()));

        String stadium = firstNonEmpty(post.getStadium(), post.getStadiumName());
        String address = firstNonEmpty(post.getAddress(), post.getStadiumAddress());
        holder.textStadium.setText(safe(stadium) + " | " + safe(address));

        holder.textSkill.setText("실력 : " + post.getSkill());

        // ✅ 작성시각 Long이 있으면 우선 사용, 없으면 안전 파서로 계산
        long ts = post.getTimestamp() > 0
                ? post.getTimestamp()
                : computeTsSafe(safe(post.getDate()), safe(post.getTime()));
        holder.textTimestamp.setText(formatTimestamp(ts));

        String logo = firstNonEmpty(post.getLogoUrl(), post.getTeamLogoUrl());
        if (!isEmpty(logo)) {
            Glide.with(context).load(logo).into(holder.imageTeamLogo);
        } else {
            holder.imageTeamLogo.setImageResource(R.drawable.ic_launcher_foreground);
        }

        // 추천 레이아웃인 경우: 이유 뱃지 동적 바인딩(최대 3개)
        if (holder.recoReasonsContainer != null) {
            bindReasons(holder.recoReasonsContainer, post.getMatchId());
        }

        // 클릭 시 상세 진입
        holder.itemRoot.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), MatchDetail.class);
            intent.putExtra("matchId", post.getMatchId());
            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return postList == null ? 0 : postList.size();
    }

    public void setMatchPostList(List<MatchPost> filteredList) {
        this.postList = filteredList;
        notifyDataSetChanged();
    }

    // ===================== 뷰홀더 =====================
    public static class MatchPostViewHolder extends RecyclerView.ViewHolder {
        View itemRoot;
        TextView textTeamName, textDate, textTime, textStadium, textSkill, textTimestamp;
        ImageView imageTeamLogo;

        // ★ 추천 레이아웃에만 존재(일반 레이아웃에선 null)
        LinearLayout recoReasonsContainer;

        public MatchPostViewHolder(@NonNull View itemView) {
            super(itemView);
            itemRoot = itemView.findViewById(R.id.itemRoot);
            imageTeamLogo = itemView.findViewById(R.id.imageTeamLogo);
            textTeamName  = itemView.findViewById(R.id.textTeamName);
            textDate      = itemView.findViewById(R.id.textDate);
            textTime      = itemView.findViewById(R.id.textTime);
            textStadium   = itemView.findViewById(R.id.textStadium);
            textSkill     = itemView.findViewById(R.id.textSkill);
            textTimestamp = itemView.findViewById(R.id.textTimestamp);

            // 추천 레이아웃에만 있는 컨테이너(id가 없으면 null)
            recoReasonsContainer = itemView.findViewById(R.id.recoReasonsContainer);
        }
    }

    // ===================== 날짜(요일) 빌더 =====================
    private String buildDateWithWeekday(MatchPost post) {
        String date = safe(post.getDate());
        if (isEmpty(date)) return "";

        // MatchPost 모델에 weekday 필드를 따로 안 썼으니까 date로 계산
        String w = calcWeekdayFromDate(date);
        if (!isEmpty(w)) {
            return date + "(" + w + ")";
        }
        return date;
    }

    // yyyy-MM-dd → "월"/"화" ...
    private String calcWeekdayFromDate(String dateStr) {
        if (isEmpty(dateStr)) return "";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date d = sdf.parse(dateStr);
            if (d == null) return "";
            // 한국어 요일 한 글자
            SimpleDateFormat wdf = new SimpleDateFormat("E", Locale.KOREAN);
            return wdf.format(d); // 예: "수"
        } catch (ParseException e) {
            return "";
        }
    }

    // ===================== 유틸 =====================
    private boolean isEmpty(String s){ return s == null || s.trim().isEmpty(); }
    private String safe(String s){ return s == null ? "" : s; }
    private String firstNonEmpty(String... arr){
        if (arr == null) return "";
        for (String x : arr) if (!isEmpty(x)) return x;
        return "";
    }

    private String formatTimestamp(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        if (diff < 0) { // 미래 시점이면 날짜로 표기
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }

        long minutes = diff / (60 * 1000);
        long hours = diff / (60 * 60 * 1000);

        if (minutes < 1) {
            return "방금 전";
        } else if (minutes < 60) {
            return minutes + "분 전";
        } else if (hours < 24) {
            return hours + "시간 전";
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }
    }

    // ===================== 날짜/시간 안전 파서 =====================
    // 시간 범위/오전·오후/HHmm 등 다양한 포맷을 안전하게 처리
    private long computeTsSafe(String date, String time) {
        try {
            // 1) 시간 범위면 시작시간만 추출 (예: "19:00~21:00" → "19:00")
            String startTime = extractStartTime(time); // "HH:mm" 또는 ""

            // 2) 파싱 패턴/값 구성
            final String val = isEmpty(startTime) ? date : (date + " " + startTime);
            final String pattern = isEmpty(startTime) ? "yyyy-MM-dd" : "yyyy-MM-dd HH:mm";

            java.text.SimpleDateFormat sdf =
                    new java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault());
            return sdf.parse(val).getTime();
        } catch (Exception ignore) {
            // 최후 폴백: 현재시각
            return System.currentTimeMillis();
        }
    }

    /** "19:00~21:00" / "오전 9:10 ~ 11:00" / "7:10 PM" 등에서 시작시간만 "HH:mm"으로 추출 */
    private String extractStartTime(String raw) {
        if (isEmpty(raw)) return "";

        String s = raw.trim();

        // 1) 틸드/대시 기준으로 앞쪽만
        String firstPart = s.split("[~\\-–—]")[0].trim();

        // 2) 한글 오전/오후 → AM/PM 변환 후 24시간 "HH:mm"으로
        if (firstPart.contains("오전") || firstPart.contains("오후")) {
            try {
                String normalized = firstPart.replaceAll("\\s+", " ")
                        .replace("오전", "AM")
                        .replace("오후", "PM");
                java.text.SimpleDateFormat ap = new java.text.SimpleDateFormat("a H:mm", Locale.getDefault());
                Date d = ap.parse(normalized);
                java.text.SimpleDateFormat hhmm = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault());
                return hhmm.format(d);
            } catch (Exception ignored) { /* 다음 케이스로 진행 */ }
        }

        // 3) 영문 AM/PM ("7:10 PM")
        String upper = firstPart.toUpperCase(Locale.ROOT);
        if (upper.contains("AM") || upper.contains("PM")) {
            try {
                java.text.SimpleDateFormat ap = new java.text.SimpleDateFormat("h:mm a", Locale.US);
                Date d = ap.parse(upper);
                java.text.SimpleDateFormat hhmm = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault());
                return hhmm.format(d);
            } catch (Exception ignored) { /* 다음 케이스로 진행 */ }
        }

        // 4) 첫 "HH:mm" 패턴 추출
        Matcher m = Pattern.compile("(\\d{1,2}:\\d{2})").matcher(firstPart);
        if (m.find()) {
            String hhmm = m.group(1);
            if (hhmm.length() == 4) hhmm = "0" + hhmm; // "9:30" → "09:30"
            return hhmm;
        }

        // 5) "HHmm" 보정
        m = Pattern.compile("\\b(\\d{3,4})\\b").matcher(firstPart);
        if (m.find()) {
            String digits = m.group(1);
            if (digits.length() == 3) digits = "0" + digits; // 930 → 0930
            return digits.substring(0, 2) + ":" + digits.substring(2);
        }

        return "";
    }

    // ===================== 뱃지 바인딩(추천 레이아웃 전용) =====================
    private void bindReasons(LinearLayout container, String matchId) {
        if (container == null) return;

        // 이유 맵이 없거나, 해당 매치에 이유가 없으면 숨김
        if (reasonsMap == null || matchId == null || !reasonsMap.containsKey(matchId)) {
            container.setVisibility(View.GONE);
            container.removeAllViews();
            return;
        }

        List<String> reasons = reasonsMap.get(matchId);
        if (reasons == null || reasons.isEmpty()) {
            container.setVisibility(View.GONE);
            container.removeAllViews();
            return;
        }

        container.setVisibility(View.VISIBLE);
        container.removeAllViews();

        int shown = 0;
        for (String r : reasons) {
            if (isEmpty(r)) continue;
            container.addView(makeChip(r.trim()));
            shown++;
            if (shown == 3) break; // 최대 3개
        }

        if (shown == 0) container.setVisibility(View.GONE);
    }

    // 칩(TextView) 프로그램적 생성 – bg_filter_chip 재사용
    private View makeChip(String text) {
        TextView tv = new TextView(context);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(28)
        );
        lp.setMarginEnd(dp(6));
        lp.gravity = android.view.Gravity.CENTER_VERTICAL;
        tv.setLayoutParams(lp);

        tv.setText(text);
        tv.setIncludeFontPadding(false);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        tv.setTextColor(Color.parseColor("#111111"));
        tv.setPadding(dp(10), 0, dp(10), 0);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setBackgroundResource(R.drawable.bg_filter_chip);
        return tv;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()
        );
    }
}
