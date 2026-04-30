package com.example.myapp;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView; // 아이콘 id 유지용
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 일정 선택 리스트 어댑터
 * - item_schedule_row.xml 바인딩
 * - "우리팀 vs 상대팀", "yyyy-MM-dd | HH:mm ~ HH:mm", "장소명", "주소" 표시
 * - matchTs 기준 상태칩: D-n / 오늘 / 종료
 */
class EventPickAdapter extends RecyclerView.Adapter<EventPickAdapter.VH> {

    interface OnPick { void onPick(DocumentSnapshot d); }

    private final List<DocumentSnapshot> list;
    private final OnPick onPick;

    // 우리 팀 기준 정렬을 위해 주입
    private final String myTeamId;
    private final String myTeamName;

    EventPickAdapter(List<DocumentSnapshot> list, String myTeamId, String myTeamName, OnPick onPick){
        this.list = list;
        this.myTeamId = safe(myTeamId);
        this.myTeamName = safe(myTeamName);
        this.onPick = onPick;
    }

    static class VH extends RecyclerView.ViewHolder {
        View card, stripe, divider;
        TextView textTeams, chipStatus, textDateTime, textStadiumName, textStadiumAddress;
        ImageView iconCalendar, iconPin; // 필요시 tint 변경 등에 사용 가능

        VH(@NonNull View v){
            super(v);
            card = v.findViewById(R.id.card);
            stripe = v.findViewById(R.id.stripe);
            divider = v.findViewById(R.id.divider);
            textTeams = v.findViewById(R.id.textTeams);
            chipStatus = v.findViewById(R.id.chipStatus);
            textDateTime = v.findViewById(R.id.textDateTime);
            textStadiumName = v.findViewById(R.id.textStadiumName);
            textStadiumAddress = v.findViewById(R.id.textStadiumAddress);
            iconCalendar = v.findViewById(R.id.iconCalendar);
            iconPin = v.findViewById(R.id.iconPin);
        }
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_schedule_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        DocumentSnapshot d = list.get(pos);

        // ───────── 팀명 계산 (왼쪽=우리팀, 오른쪽=상대팀) ─────────
        String teamIdA = firstNonEmpty(
                d.getString("homeTeamId"),
                d.getString("teamId"),          // 단일 teamId만 있는 스키마
                d.getString("myTeamId")
        );
        String teamIdB = firstNonEmpty(
                d.getString("awayTeamId"),
                d.getString("opponentTeamId")
        );

        String nameA = firstNonEmpty(
                d.getString("homeTeamName"),
                d.getString("teamName"),        // 단일 teamName만 있는 스키마
                d.getString("myTeamName")
        );
        String nameB = firstNonEmpty(
                d.getString("awayTeamName"),
                d.getString("opponentTeamName")
        );

        // 우리팀 기준으로 좌/우 정렬
        String leftTeam, rightTeam;
        if (!isEmpty(myTeamId) && equalsId(myTeamId, teamIdA)) {
            leftTeam = firstNonEmpty(nameA, myTeamName, "우리팀");
            rightTeam = firstNonEmpty(nameB, "상대 미정");
        } else if (!isEmpty(myTeamId) && equalsId(myTeamId, teamIdB)) {
            leftTeam = firstNonEmpty(nameB, myTeamName, "우리팀");
            rightTeam = firstNonEmpty(nameA, "상대 미정");
        } else {
            // teamId 매칭 안되면 teamName 비교로 보정
            if (!isEmpty(myTeamName) && equalsText(myTeamName, nameA)) {
                leftTeam = nameA;
                rightTeam = firstNonEmpty(nameB, "상대 미정");
            } else if (!isEmpty(myTeamName) && equalsText(myTeamName, nameB)) {
                leftTeam = nameB;
                rightTeam = firstNonEmpty(nameA, "상대 미정");
            } else {
                // 최후 보정: 문서에 teamName만 있으면 left=teamName / right=상대 미정
                leftTeam = firstNonEmpty(nameA, myTeamName, "우리팀");
                rightTeam = firstNonEmpty(nameB, "상대 미정");
            }
        }
        h.textTeams.setText(leftTeam + " vs " + rightTeam);

        // ───────── 날짜 | 시간 ─────────
        String date = safe(d.getString("date"));        // "yyyy-MM-dd"
        String time = safe(d.getString("time"));        // "HH:mm ~ HH:mm"
        long matchTs = longOr(d.getLong("matchTs"), -1);

        if (isEmpty(date) && matchTs > 0) {
            date = format(matchTs, "yyyy-MM-dd");
        }
        if (isEmpty(time) && matchTs > 0) {
            String hhmm = format(matchTs, "HH:mm");
            time = hhmm + " ~ " + plusHours(hhmm, 2);   // 종료시각 미존재 시 2시간 가정
        }
        h.textDateTime.setText(joinWithDivider(date, time, "  |  "));

        // ───────── 장소명 / 주소 ─────────
        String stadium = firstNonEmpty(d.getString("stadiumName"), "장소 미정");
        String address = safe(d.getString("stadiumAddress"));
        h.textStadiumName.setText(stadium);
        if (isEmpty(address)) {
            h.textStadiumAddress.setVisibility(View.GONE);
        } else {
            h.textStadiumAddress.setVisibility(View.VISIBLE);
            h.textStadiumAddress.setText(address);
        }

        // ───────── 상태칩 (D-day) ─────────
        if (matchTs > 0) {
            String chip = buildDDayText(matchTs);
            h.chipStatus.setText(chip);
            h.chipStatus.setVisibility(View.VISIBLE);
        } else {
            h.chipStatus.setVisibility(View.GONE);
        }

        // 클릭 시 선택 콜백
        h.itemView.setOnClickListener(v -> {
            if (onPick != null) onPick.onPick(d);
        });

        // 마지막 아이템이면 구분선 숨김
        h.divider.setVisibility(pos == getItemCount() - 1 ? View.GONE : View.VISIBLE);
    }

    @Override
    public int getItemCount() { return list.size(); }

    // ───────── 유틸 ─────────
    private static String safe(String s){ return s == null ? "" : s.trim(); }
    private static boolean isEmpty(String s){ return s == null || s.trim().isEmpty(); }
    private static long longOr(Long v, long def){ return v == null ? def : v; }

    private static String firstNonEmpty(String... arr){
        if (arr == null) return "";
        for (String s : arr) if (!isEmpty(s)) return s.trim();
        return "";
    }

    private static boolean equalsId(String a, String b){
        return !isEmpty(a) && !isEmpty(b) && a.trim().equals(b.trim());
    }
    private static boolean equalsText(String a, String b){
        return !isEmpty(a) && !isEmpty(b) && a.trim().equalsIgnoreCase(b.trim());
    }

    private static String format(long millis, String pattern){
        SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
        return sdf.format(millis);
    }

    private static String plusHours(String hhmm, int hours){
        try {
            SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.getDefault());
            Calendar c = Calendar.getInstance();
            c.setTime(f.parse(hhmm));
            c.add(Calendar.HOUR_OF_DAY, hours);
            return f.format(c.getTime());
        } catch (Exception e) { return hhmm; }
    }

    private static String joinWithDivider(String a, String b, String divider){
        if (isEmpty(a) && isEmpty(b)) return "";
        if (isEmpty(a)) return b;
        if (isEmpty(b)) return a;
        return a + divider + b;
    }

    private static String buildDDayText(long matchTs){
        Calendar today = Calendar.getInstance();
        zeroTime(today);

        Calendar match = Calendar.getInstance();
        match.setTimeInMillis(matchTs);
        zeroTime(match);

        long diff = (match.getTimeInMillis() - today.getTimeInMillis()) / (24L*60*60*1000);
        if (diff == 0)  return "오늘";
        if (diff > 0)   return "D-" + diff;
        return "종료";
    }

    private static void zeroTime(Calendar c){
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }
}
