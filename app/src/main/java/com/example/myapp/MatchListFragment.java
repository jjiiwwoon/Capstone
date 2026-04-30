// src/main/java/com/example/myapp/MatchListFragment.java
package com.example.myapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * 매치 리스트
 * - matches 신규/레거시 둘 다 붙임
 * - 상위에서 전달한 MatchFilters로 지역/날짜/시간/요일/실력 필터
 * - ✅ status 는 OPEN 만 보이게 수정
 */
public class MatchListFragment extends Fragment {

    private RecyclerView recyclerView;
    private ProgressBar progress;
    private TextView emptyView;

    private MatchPostAdapter adapter;
    private final List<MatchPost> items = new ArrayList<>();

    private ListenerRegistration regNew;
    private ListenerRegistration regLegacy;
    private final LinkedHashMap<String, MatchPost> merged = new LinkedHashMap<>();

    // 상위에서 넘겨주는 필터 (뷰 없어도 저장해둔다)
    @Nullable
    private MatchFilters currentFilters = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.match_list_fragment, container, false);

        recyclerView = v.findViewById(R.id.recyclerMatch);
        progress     = v.findViewById(R.id.progress);
        emptyView    = v.findViewById(R.id.emptyView);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new MatchPostAdapter(requireContext(), items);
        recyclerView.setAdapter(adapter);

        // 여기서 currentFilters 적용된 상태로 붙이기
        attachListeners(currentFilters);

        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        detachListeners();
    }

    // ===== 상위에서 필터 바꿀 때 호출 =====
    public void applyExternalFilters(@Nullable MatchFilters filters) {
        // 1) 값 저장
        this.currentFilters = filters;

        // 2) 뷰 아직 없음 → 끝
        if (adapter == null) {
            return;
        }

        // 3) 뷰 살아있으면 재렌더
        refreshList(filters);
    }

    // ================== Firestore 붙이기 ==================
    private void attachListeners(@Nullable MatchFilters filters) {
        if (progress != null) progress.setVisibility(View.VISIBLE);
        if (emptyView != null) emptyView.setVisibility(View.GONE);

        detachListeners();
        merged.clear();
        items.clear();
        if (adapter != null) adapter.notifyDataSetChanged();

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // ✅ 1) 신규 스키마: status == OPEN 인 것만 가져오게 원래 스타일로 되돌림
        Query qNew = db.collection("matches")
                .whereEqualTo("status", "OPEN")
                .orderBy("matchTs", Query.Direction.DESCENDING);

        regNew = qNew.addSnapshotListener((snap, e) -> {
            if (!isAdded()) return;
            if (e != null || snap == null) {
                refreshList(filters);
                return;
            }

            for (DocumentSnapshot d : snap.getDocuments()) {
                MatchPost p = d.toObject(MatchPost.class);
                if (p == null) continue;
                p.setMatchId(d.getId());
                fillFallbacks(p, d);
                merged.put(d.getId(), p);
            }
            refreshList(filters);
        });

        // 2) 레거시: 옛날 거는 status 없을 수도 있어서 그대로 가져오되, 밑에서 한 번 더 거른다
        Query qLegacy = db.collection("matches")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(100);

        regLegacy = qLegacy.addSnapshotListener((snap, e) -> {
            if (!isAdded()) return;
            if (e != null || snap == null) {
                refreshList(filters);
                return;
            }

            for (DocumentSnapshot d : snap.getDocuments()) {
                MatchPost p = d.toObject(MatchPost.class);
                if (p == null) continue;
                p.setMatchId(d.getId());
                fillFallbacks(p, d);
                merged.put(d.getId(), p);
            }
            refreshList(filters);
        });
    }

    private void detachListeners() {
        if (regNew != null) { regNew.remove(); regNew = null; }
        if (regLegacy != null) { regLegacy.remove(); regLegacy = null; }
    }

    // ================== 리스트로 변환 ==================
    private void refreshList(@Nullable MatchFilters filters) {
        // 어댑터가 없으면 그냥 리턴
        if (adapter == null) {
            return;
        }

        items.clear();

        for (MatchPost post : merged.values()) {
            if (!passClientFilters(post, filters)) continue;
            items.add(post);
        }

        // 최신순
        items.sort((a, b) -> Long.compare(bestTs(b), bestTs(a)));

        adapter.notifyDataSetChanged();

        if (progress != null) progress.setVisibility(View.GONE);
        if (emptyView != null) emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ================== 필터링 ==================
    private boolean passClientFilters(@NonNull MatchPost post, @Nullable MatchFilters f) {
        // ✅ 여기서도 한 번 더: status가 있고 OPEN 아니면 안 보여줌
        String st = post.getStatus();
        if (st != null && !st.trim().isEmpty()) {
            String up = st.trim().toUpperCase(Locale.getDefault());
            if (!up.equals("OPEN")) {
                return false;
            }
        }
        // status 자체가 없는 옛날 문서는 일단 통과시킨다 (아래 필터로만 걸러짐)

        if (f == null) return true;

        // 1) 지역
        String address = firstNonEmpty(
                post.getStadiumAddress(),
                post.getAddress(),
                post.getStadium(),
                post.getStadiumName()
        );
        if (!isAll(f.common.city)) {
            if (isEmpty(address) || !address.contains(f.common.city)) return false;
        }
        if (!isAll(f.common.district)) {
            if (isEmpty(address) || !address.contains(f.common.district)) return false;
        }

        // 2) 실력 범위
        int postSkill = post.getSkill();
        if (f.skillMin != null && postSkill != 0) {
            if (postSkill < f.skillMin) return false;
        }
        if (f.skillMax != null && postSkill != 0) {
            if (postSkill > f.skillMax) return false;
        }

        // 3) 날짜 범위
        String postDate = post.getDate();
        if (!isAll(f.dateFrom) || !isAll(f.dateTo)) {
            if (!isEmpty(postDate)) {
                if (!isAll(f.dateFrom) && postDate.compareTo(f.dateFrom) < 0) return false;
                if (!isAll(f.dateTo)   && postDate.compareTo(f.dateTo)   > 0) return false;
            }
        }

        // 4) 시간 범위
        String postTime = normalizeTime(post.getTime());
        if (!isAll(f.timeFrom) || !isAll(f.timeTo)) {
            if (!isEmpty(postTime)) {
                if (!isAll(f.timeFrom) && postTime.compareTo(f.timeFrom) < 0) return false;
                if (!isAll(f.timeTo)   && postTime.compareTo(f.timeTo)   > 0) return false;
            }
        }

        // 5) 요일
        if (!isAll(f.weekday)) {
            List<String> wanted = splitComma(f.weekday);
            String day = weekdayFromDate(postDate);
            if (!isEmpty(day) && !wanted.contains(day)) {
                return false;
            }
        }

        return true;
    }

    // ================== 유틸 ==================
    private long bestTs(MatchPost p) {
        long m = p.getMatchTs();
        if (m > 0) return m;
        long t = p.getTimestamp();
        if (t > 0) return t;
        return computeTs(p.getDate(), normalizeTime(p.getTime()));
    }

    private void fillFallbacks(MatchPost p, DocumentSnapshot d) {
        if (p.getTeamLogoUrl() == null)    p.setTeamLogoUrl(d.getString("teamLogoUrl"));
        if (p.getStadiumName() == null)    p.setStadiumName(d.getString("stadiumName"));
        if (p.getStadiumAddress() == null) p.setStadiumAddress(d.getString("stadiumAddress"));

        if (p.getTimestamp() == 0) {
            Long ts = d.getLong("timestamp");
            if (ts != null) p.setTimestamp(ts);
        }
        if (p.getMatchTs() == 0) {
            long calc = computeTs(p.getDate(), normalizeTime(p.getTime()));
            p.setMatchTs(calc);
        }
    }

    private long computeTs(String date, String time) {
        try {
            if (date == null) date = "";
            if (time == null) time = "";

            String pattern;
            String val;
            if (time.isEmpty()) {
                pattern = "yyyy-MM-dd";
                val = date;
            } else {
                pattern = "yyyy-MM-dd HH:mm";
                val = date + " " + time;
            }

            SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
            return sdf.parse(val).getTime();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private boolean isAll(String v) {
        if (v == null) return true;
        String t = v.trim();
        return t.isEmpty() || t.equals("전체");
    }

    private boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String firstNonEmpty(String... arr) {
        if (arr == null) return "";
        for (String x : arr) {
            if (x != null && !x.trim().isEmpty()) return x.trim();
        }
        return "";
    }

    private List<String> splitComma(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        String t = raw.trim();
        if (t.isEmpty()) return out;
        String[] parts = t.split(",");
        for (String p : parts) {
            String v = p.trim();
            if (!v.isEmpty()) out.add(v);
        }
        return out;
    }

    private String weekdayFromDate(String ymd) {
        if (isEmpty(ymd)) return "";
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        try {
            Calendar c = Calendar.getInstance();
            c.setTime(sdf.parse(ymd));
            int dow = c.get(Calendar.DAY_OF_WEEK);
            switch (dow) {
                case Calendar.MONDAY:    return "월";
                case Calendar.TUESDAY:   return "화";
                case Calendar.WEDNESDAY: return "수";
                case Calendar.THURSDAY:  return "목";
                case Calendar.FRIDAY:    return "금";
                case Calendar.SATURDAY:  return "토";
                case Calendar.SUNDAY:    return "일";
            }
        } catch (ParseException ignored) {}
        return "";
    }

    // "19:30 ~ 21:00" 이런 식이면 앞부분만 가져오기
    private String normalizeTime(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        if (t.isEmpty()) return "";
        int tilde = t.indexOf("~");
        if (tilde > 0) {
            t = t.substring(0, tilde).trim();
        }
        if (t.length() >= 5) return t.substring(0, 5);
        return t;
    }
}
