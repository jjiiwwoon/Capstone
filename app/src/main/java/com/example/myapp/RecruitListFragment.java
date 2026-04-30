// src/main/java/com/example/myapp/RecruitListFragment.java
package com.example.myapp;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class RecruitListFragment extends Fragment {

    private RecyclerView recyclerRecruit;
    private TextView emptyView;
    private RecruitAdapter adapter;

    private RecruitFilters currentFilters = null;

    private final List<RecruitAdapter.RecruitItem> allItems = new ArrayList<>();

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ListenerRegistration reg;

    public RecruitListFragment(){}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.recruit_list_fragment, container, false);

        recyclerRecruit = v.findViewById(R.id.recyclerRecruit);
        emptyView = v.findViewById(R.id.emptyView);

        recyclerRecruit.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RecruitAdapter();
        recyclerRecruit.setAdapter(adapter);

        attachRealtime();

        return v;
    }

    public void applyExternalFilters(@Nullable RecruitFilters filters) {
        this.currentFilters = filters;
        applyFilters();
    }

    private void attachRealtime() {
        detachRealtime();

        Query q = db.collection("recruitPosts")
                .whereEqualTo("status", "open")
                .limit(100);

        reg = q.addSnapshotListener((snap, e) -> {
            if (!isAdded() || e != null || snap == null) return;

            allItems.clear();
            for (DocumentSnapshot ds : snap.getDocuments()) {
                RecruitAdapter.RecruitItem it = new RecruitAdapter.RecruitItem();
                it.id             = ds.getId();
                it.teamLogoUrl    = ds.getString("teamLogoUrl");
                it.teamName       = ds.getString("teamName");
                it.date           = ds.getString("date");
                it.time           = ds.getString("time");

                String name = nz(ds.getString("stadiumName"));
                if (name.isEmpty()) name = nz(ds.getString("homeStadiumName"));

                String addr = nz(ds.getString("stadiumAddress"));
                if (addr.isEmpty()) addr = nz(ds.getString("stadium"));
                if (addr.isEmpty()) addr = nz(ds.getString("region"));

                it.stadiumName    = name;
                it.stadiumAddress = addr;

                Long sMin = ds.getLong("skillMin");
                Long sMax = ds.getLong("skillMax");
                it.skillMin = sMin == null ? null : sMin.intValue();
                it.skillMax = sMax == null ? null : sMax.intValue();

                List<String> pos = (List<String>) ds.get("positions");
                it.positions = (pos == null) ? new ArrayList<>() : pos;

                it.recruitType = ds.getString("recruitType");

                // 요일 필드가 문서에 있으면 가져오기
                it.weekday = ds.getString("weekday");

                Long createdAtMs = ds.getLong("createdAtMs");
                Timestamp createdAtTs = ds.getTimestamp("createdAt");
                Long postTs = ds.getLong("postTs");
                Long matchTs = ds.getLong("matchTs");

                it.createdAtMs = createdAtMs == null ? 0L : createdAtMs;
                it.createdAt   = createdAtTs == null ? 0L : createdAtTs.toDate().getTime();
                it.postTs      = postTs == null ? 0L : postTs;
                it.matchTs     = matchTs == null ? 0L : matchTs;

                it.relativeTime = ds.getString("relativeTime");

                allItems.add(it);
            }

            Collections.sort(allItems, (a, b) -> {
                long aKey = sortKey(a);
                long bKey = sortKey(b);
                return Long.compare(bKey, aKey);
            });

            applyFilters();
        });
    }

    private long sortKey(RecruitAdapter.RecruitItem it) {
        if (it.createdAtMs > 0) return it.createdAtMs;
        if (it.createdAt   > 0) return it.createdAt;
        if (it.postTs      > 0) return it.postTs;
        return it.matchTs;
    }

    private void detachRealtime() {
        if (reg != null) {
            reg.remove();
            reg = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        detachRealtime();
    }

    private void applyFilters() {
        if (adapter == null) return;

        List<RecruitAdapter.RecruitItem> out = new ArrayList<>();
        for (RecruitAdapter.RecruitItem it : allItems) {
            if (passFilters(it, currentFilters)) out.add(it);
        }

        adapter.submit(out);

        if (emptyView != null) {
            emptyView.setVisibility(out.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private boolean passFilters(RecruitAdapter.RecruitItem it, @Nullable RecruitFilters f) {
        if (f == null) return true;

        // 1) 지역
        String address = nz(it.stadiumAddress);
        if (!isAll(f.common.city) && !address.contains(f.common.city)) return false;
        if (!isAll(f.common.district) && !address.contains(f.common.district)) return false;

        // 2) 실력 범위
        if (f.skillMin != null) {
            int postMax = (it.skillMax != null) ? it.skillMax : Integer.MAX_VALUE;
            if (postMax < f.skillMin) return false;
        }
        if (f.skillMax != null) {
            int postMin = (it.skillMin != null) ? it.skillMin : Integer.MIN_VALUE;
            if (postMin > f.skillMax) return false;
        }

        // 3) 포지션 (여러 개 선택 가능)
        if (!isAll(f.position)) {
            List<String> wantedPositions = splitSelections(f.position); // ["GK","DF"]
            boolean ok = false;
            if (it.positions != null) {
                for (String p : it.positions) {
                    String postPos = nz(p);
                    if (wantedPositions.contains(postPos)) {
                        ok = true;
                        break;
                    }
                }
            }
            if (!ok) return false;
        }

        // 4) 모집 유형
        if (!isAll(f.recruitType)) {
            String now = normalizeRecruitType(it.recruitType);
            String want = normalizeRecruitType(f.recruitType);
            if (!want.equals(now)) return false;
        }

        // 5) 날짜 범위 (문서에 date가 있을 때만 비교)
        if (!isAll(f.dateFrom) || !isAll(f.dateTo)) {
            String postDate = nz(it.date);
            if (!postDate.isEmpty()) {
                if (!isAll(f.dateFrom)) {
                    if (postDate.compareTo(f.dateFrom) < 0) return false;
                }
                if (!isAll(f.dateTo)) {
                    if (postDate.compareTo(f.dateTo) > 0) return false;
                }
            }
        }

        // 6) 시간 범위
        if (!isAll(f.timeFrom) || !isAll(f.timeTo)) {
            String postTime = nz(it.time);
            if (!postTime.isEmpty()) {
                if (!isAll(f.timeFrom)) {
                    if (postTime.compareTo(f.timeFrom) < 0) return false;
                }
                if (!isAll(f.timeTo)) {
                    if (postTime.compareTo(f.timeTo) > 0) return false;
                }
            }
        }

        // 7) 요일 (여러 개 선택 가능)
        if (!isAll(f.weekday)) {
            List<String> wantedDays = splitSelections(f.weekday); // ["월","수","금"]
            String postWeek = nz(it.weekday);                     // 글에 저장된 요일 1개
            if (!wantedDays.contains(postWeek)) {
                return false;
            }
        }

        return true;
    }

    private boolean isAll(String v) {
        if (v == null) return true;
        String t = v.trim();
        return t.isEmpty() || t.equals("전체");
    }

    private String normalizeRecruitType(String raw) {
        String r = nz(raw).toLowerCase(Locale.getDefault());

        if (r.contains("회원") || r.contains("regular") || r.contains("정식")) {
            return "regular";
        }

        if (r.contains("용병") || r.contains("mercenary") || r.contains("일일")) {
            return "mercenary";
        }

        return r;
    }

    private String nz(String s) { return s == null ? "" : s.trim(); }

    /**
     * "GK,DF" → ["GK","DF"]
     * "월,수,금" → ["월","수","금"]
     */
    private List<String> splitSelections(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        String t = raw.trim();
        if (t.isEmpty() || t.equals("전체")) return out;
        String[] parts = t.split(",");
        for (String p : parts) {
            String v = p.trim();
            if (!v.isEmpty()) out.add(v);
        }
        return out;
    }
}
