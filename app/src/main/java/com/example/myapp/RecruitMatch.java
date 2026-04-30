// src/main/java/com/example/myapp/RecruitMatch.java
package com.example.myapp;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class RecruitMatch extends Fragment
        implements RecruitFilterSheet.OnRecruitFilterApplied,
        MatchFilterSheet.OnMatchFilterApplied {

    private View btnRecruitTab, btnMatchTab;
    private View btnApplicationsList;

    // 🔹 하위 탭: 시합 / 팀
    private View matchModeTabsCard;
    private View btnMatchListTab, btnTeamMatchTab;
    private boolean isTeamMode = false;   // false = 시합글, true = 팀매칭

    // 🔹 팀 매칭용 "팀 찾기" 버튼 (시합|팀 바로 아래)
    private View btnTeamFindGlobal;

    // 🔹 필터줄 전체(버튼 + 칩)
    private View filterBarContainer;

    // 필터 버튼 + 선택된 칩 컨테이너
    private View btnOpenFilter;
    private LinearLayout selectedFilterChips;

    private final Map<String, String> activeFilters = new HashMap<>();

    private boolean isRecruitTab = true;   // 기본 모집 탭

    // ✅ 현재 선수모집 필터 저장
    private RecruitFilters currentRecruitFilters = null;

    // ✅ 현재 매치 필터 저장 (시합글용)
    private MatchFilters currentMatchFilters = null;

    // Speed Dial
    private View scrim;
    private View actions;
    private FloatingActionButton fab;
    private View btnActionRecruit, btnActionMatch;
    private boolean isOpen = false;

    // 팀 보유 여부
    private boolean hasTeam = false;
    // 주장/부주장 여부
    private boolean canWrite = false;

    private ListenerRegistration profileReg;

    // 신청 배지
    private View badgeNew;

    // 🔔 배지 감시용
    private String myTeamId = "";
    private ListenerRegistration matchesReg;
    private ListenerRegistration recruitsReg;
    private ListenerRegistration matchesBadgeReg;

    // 각 글(applicants) 실시간 리스너
    private final Map<String, ListenerRegistration> applicantRegs = new HashMap<>();
    // 각 글별로 “새 신청 있음?” 여부
    private final Map<String, Boolean> postHasNew = new HashMap<>();
    // 각 글별로 가장 최신 신청 timestamp(ms) 캐시
    private final Map<String, Long> latestTsByPost = new HashMap<>();

    // Firebase
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.recruit_match, container, false);

        btnRecruitTab = v.findViewById(R.id.btnRecruitTab);
        btnMatchTab   = v.findViewById(R.id.btnMatchTab);

        // 하위 탭 (시합 / 팀)
        matchModeTabsCard = v.findViewById(R.id.matchModeTabsCard);
        btnMatchListTab   = v.findViewById(R.id.btnMatchListTab);
        btnTeamMatchTab   = v.findViewById(R.id.btnTeamMatchTab);

        // 팀 찾기 버튼 (시합|팀 바로 아래)
        btnTeamFindGlobal = v.findViewById(R.id.btnTeamFindGlobal);

        btnApplicationsList = v.findViewById(R.id.btnApplicationsList);
        badgeNew = v.findViewById(R.id.badgeNewApplications);
        if (btnApplicationsList != null) {
            btnApplicationsList.setOnClickListener(view -> {
                if (badgeNew != null) badgeNew.setVisibility(View.GONE);
                startActivity(new Intent(requireContext(), ApplicationsList.class));
            });
        }

        // 🔹 필터줄 전체
        filterBarContainer = v.findViewById(R.id.filterBarContainer);

        btnOpenFilter = v.findViewById(R.id.btnOpenFilter);
        selectedFilterChips = v.findViewById(R.id.selectedFilterChips);

        if (btnOpenFilter != null) {
            btnOpenFilter.setOnClickListener(view -> openFilterSheet());
        }

        // 기본 탭: 모집
        loadChildFragment(new RecruitListFragment(), true);

        // 상단 탭: 모집
        btnRecruitTab.setOnClickListener(view ->
                loadChildFragment(new RecruitListFragment(), true));

        // 상단 탭: 시합 (기본은 시합글 모드)
        btnMatchTab.setOnClickListener(view -> {
            isTeamMode = false;
            loadChildFragment(new MatchListFragment(), false);
        });

        // 하위 탭: 시합글 (언제 눌러도 "시합글 모드"로)
        if (btnMatchListTab != null) {
            btnMatchListTab.setOnClickListener(view -> {
                isRecruitTab = false;
                isTeamMode = false;
                loadChildFragment(new MatchListFragment(), false);
            });
        }

        // 하위 탭: 팀 매칭 (언제 눌러도 "팀 모드"로)
        if (btnTeamMatchTab != null) {
            btnTeamMatchTab.setOnClickListener(view -> {
                isRecruitTab = false;
                isTeamMode = true;
                loadChildFragment(new TeamMatchFragment(), false);
            });
        }

        // 팀 찾기 버튼 → 현재 child 가 TeamMatchFragment 면 그 안의 다이얼로그 호출
        if (btnTeamFindGlobal != null) {
            btnTeamFindGlobal.setOnClickListener(view -> {
                Fragment current = getChildFragmentManager()
                        .findFragmentById(R.id.recruit_match_container);
                if (current instanceof TeamMatchFragment) {
                    ((TeamMatchFragment) current).openFilterDialog();
                }
            });
        }

        // Speed Dial
        scrim            = v.findViewById(R.id.speedDialScrim);
        actions          = v.findViewById(R.id.speedDialActions);
        fab              = v.findViewById(R.id.fabSpeedDial);
        btnActionRecruit = v.findViewById(R.id.btnActionRecruit);
        btnActionMatch   = v.findViewById(R.id.btnActionMatch);

        fab.setOnClickListener(view -> toggleSpeedDial());
        scrim.setOnClickListener(view -> closeSpeedDial());

        btnActionRecruit.setOnClickListener(view -> {
            if (!ensureCanWrite()) return;
            closeSpeedDial();
            startActivity(new Intent(requireContext(), CreateRecruit.class));
        });

        btnActionMatch.setOnClickListener(view -> {
            if (!ensureCanWrite()) return;
            closeSpeedDial();
            startActivity(new Intent(requireContext(), CreateMatch.class));
        });

        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override public void handleOnBackPressed() {
                        if (isOpen) {
                            closeSpeedDial();
                        } else {
                            setEnabled(false);
                            requireActivity().onBackPressed();
                        }
                    }
                });

        startProfileListen();
        renderFilterChips();

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        recomputeBadgeFromPrefs();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (profileReg != null) {
            profileReg.remove();
            profileReg = null;
        }
        stopBadgeWatch();
    }

    private void loadChildFragment(Fragment fragment, boolean recruitSelected) {
        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.replace(R.id.recruit_match_container, fragment);
        transaction.commit();

        isRecruitTab = recruitSelected;
        if (!recruitSelected) {
            isTeamMode = fragment instanceof TeamMatchFragment;
        }

        updateTabs(recruitSelected);
        updateMatchModeTabs();

        // 모집 탭 → 현재 필터 적용
        if (recruitSelected && fragment instanceof RecruitListFragment) {
            ((RecruitListFragment) fragment).applyExternalFilters(currentRecruitFilters);
        }

        // 시합 탭(시합글) → 현재 매치 필터 적용
        if (!recruitSelected && fragment instanceof MatchListFragment) {
            ((MatchListFragment) fragment).applyExternalFilters(currentMatchFilters);
        }

        // 팀 매칭은 TeamMatchFragment 내부 다이얼로그로만 필터 처리 → 여기선 아무 것도 안 함

        renderFilterChips();

        if (isOpen) closeSpeedDial();
    }

    private void updateTabs(boolean recruitSelected) {
        if (btnRecruitTab instanceof TextView) {
            TextView tv = (TextView) btnRecruitTab;
            tv.setTypeface(null, recruitSelected ? Typeface.BOLD : Typeface.NORMAL);
            tv.setSelected(recruitSelected);
        }
        if (btnMatchTab instanceof TextView) {
            TextView tv = (TextView) btnMatchTab;
            tv.setTypeface(null, recruitSelected ? Typeface.NORMAL : Typeface.BOLD);
            tv.setSelected(!recruitSelected);
        }
    }

    // 하위 탭(시합 / 팀) 상태 업데이트 + 필터줄 / 팀찾기 visibility 제어
    private void updateMatchModeTabs() {
        if (matchModeTabsCard == null) return;

        if (isRecruitTab) {
            matchModeTabsCard.setVisibility(View.GONE);
        } else {
            matchModeTabsCard.setVisibility(View.VISIBLE);
        }

        int blue  = 0xFF2196F3;
        int gray  = 0xFF9E9E9E;

        if (btnMatchListTab instanceof TextView) {
            TextView tv = (TextView) btnMatchListTab;
            boolean selected = !isTeamMode;
            tv.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            tv.setTextColor(selected ? blue : gray);
        }

        if (btnTeamMatchTab instanceof TextView) {
            TextView tv = (TextView) btnTeamMatchTab;
            boolean selected = isTeamMode;
            tv.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            tv.setTextColor(selected ? blue : gray);
        }

        if (filterBarContainer != null) {
            if (isRecruitTab) {
                // 모집 탭: 상단 필터 사용
                filterBarContainer.setVisibility(View.VISIBLE);
            } else {
                // 시합 탭: 시합글 모드에서만 필터 사용
                filterBarContainer.setVisibility(isTeamMode ? View.GONE : View.VISIBLE);
            }
        }

        if (btnTeamFindGlobal != null) {
            // 팀 매칭 모드일 때만 "팀 찾기" 버튼 보이게
            btnTeamFindGlobal.setVisibility(
                    (!isRecruitTab && isTeamMode) ? View.VISIBLE : View.GONE
            );
        }
    }

    private void openFilterSheet() {
        // 팀 모드일 때는 상단 필터 사용 안 함
        if (!isRecruitTab && isTeamMode) return;

        if (isRecruitTab) {
            RecruitFilterSheet sheet = RecruitFilterSheet.newInstance(currentRecruitFilters);
            sheet.show(getChildFragmentManager(), "RecruitFilterSheet");
        } else {
            MatchFilterSheet sheet = MatchFilterSheet.newInstance(currentMatchFilters);
            sheet.show(getChildFragmentManager(), "MatchFilterSheet");
        }
    }

    private void toggleSpeedDial() {
        if (isOpen) {
            closeSpeedDial();
        } else {
            if (!ensureCanWrite()) return;
            openSpeedDial();
        }
    }

    private void openSpeedDial() {
        if (isOpen) return;
        isOpen = true;
        scrim.setVisibility(View.VISIBLE);
        actions.setVisibility(View.VISIBLE);
        actions.setAlpha(0f);
        actions.setTranslationY(40f);
        actions.animate().alpha(1f).translationY(0f).setDuration(160).start();
        fab.animate().rotation(45f).setDuration(160).start();
    }

    private void closeSpeedDial() {
        if (!isOpen) return;
        isOpen = false;
        actions.animate().alpha(0f).translationY(40f).setDuration(140)
                .withEndAction(() -> actions.setVisibility(View.GONE)).start();
        scrim.setVisibility(View.GONE);
        fab.animate().rotation(0f).setDuration(140).start();
    }

    private boolean ensureCanWrite() {
        if (!hasTeam) {
            CustomToast.info(requireContext(),
                    "팀이 없어서 글쓰기를 할 수 없어요.\n먼저 팀을 만들거나 팀에 가입해 주세요.");
            return false;
        }
        if (!canWrite) {
            CustomToast.error(requireContext(),
                    "글쓰기는 주장과 부주장만 가능합니다.");
            return false;
        }
        return true;
    }

    private void startProfileListen() {
        if (auth.getCurrentUser() == null) {
            hasTeam = false;
            myTeamId = "";
            canWrite = false;
            updateActionsEnabledState();
            stopBadgeWatch();
            if (badgeNew != null) badgeNew.setVisibility(View.GONE);
            return;
        }
        String uid = auth.getCurrentUser().getUid();

        if (profileReg != null) {
            profileReg.remove();
            profileReg = null;
        }

        profileReg = db.collection("profiles").document(uid)
                .addSnapshotListener((@Nullable DocumentSnapshot snap,
                                      @Nullable FirebaseFirestoreException e) -> {
                    if (e != null) {
                        hasTeam = false;
                        myTeamId = "";
                        canWrite = false;
                        updateActionsEnabledState();
                        stopBadgeWatch();
                        if (badgeNew != null) badgeNew.setVisibility(View.GONE);
                        return;
                    }
                    if (snap != null && snap.exists()) {
                        String myTeam = snap.getString("myTeam");
                        hasTeam = myTeam != null && !myTeam.trim().isEmpty();
                        myTeamId = hasTeam ? myTeam.trim() : "";
                    } else {
                        hasTeam = false;
                        myTeamId = "";
                    }
                    canWrite = false;
                    updateActionsEnabledState();

                    fetchTeamRole();
                    startBadgeWatch();
                });
    }

    private void fetchTeamRole() {
        if (!hasTeam || isEmpty(myTeamId) || auth.getCurrentUser() == null) {
            canWrite = false;
            updateActionsEnabledState();
            return;
        }
        final String uid = auth.getCurrentUser().getUid();
        db.collection("teams").document(myTeamId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        String captainId = doc.getString("captainUID");
                        String viceId = doc.getString("viceCaptainUID");
                        boolean writable = uid.equals(captainId) || uid.equals(viceId);
                        canWrite = writable;
                    } else {
                        canWrite = false;
                    }
                    updateActionsEnabledState();
                })
                .addOnFailureListener(e -> {
                    canWrite = false;
                    updateActionsEnabledState();
                });
    }

    private void updateActionsEnabledState() {
        boolean enabled = hasTeam && canWrite;
        if (actions != null) {
            actions.setAlpha(enabled ? 1f : 0.3f);
        }
        if (btnActionRecruit != null) {
            btnActionRecruit.setEnabled(enabled);
            btnActionRecruit.setAlpha(enabled ? 1f : 0.3f);
        }
        if (btnActionMatch != null) {
            btnActionMatch.setEnabled(enabled);
            btnActionMatch.setAlpha(enabled ? 1f : 0.3f);
        }
        if (fab != null) {
            fab.setAlpha(enabled ? 1f : 0.4f);
        }
    }

    // ====================== 🔔 배지 로직 ======================

    private void startBadgeWatch() {
        stopBadgeWatch();

        if (isEmpty(myTeamId)) {
            if (badgeNew != null) badgeNew.setVisibility(View.GONE);
            return;
        }

        matchesReg = db.collection("matches")
                .whereEqualTo("teamId", myTeamId)
                .addSnapshotListener((qs, err) -> {
                    if (err != null || qs == null) return;
                    Set<String> current = new HashSet<>();
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        String postId = d.getId();
                        current.add("match:" + postId);
                        watchApplicantsForPost("match", postId);
                    }
                    cleanupMissingApplicantRegs(current);
                });

        recruitsReg = db.collection("recruitPosts")
                .whereEqualTo("teamId", myTeamId)
                .addSnapshotListener((qs, err) -> {
                    if (err != null || qs == null) return;
                    Set<String> current = new HashSet<>();
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        String postId = d.getId();
                        current.add("recruit:" + postId);
                        watchApplicantsForPost("recruit", postId);
                    }
                    cleanupMissingApplicantRegs(current);
                });

        startBadgeAggregateWatch();
    }

    private void stopBadgeWatch() {
        if (matchesReg != null) { matchesReg.remove(); matchesReg = null; }
        if (recruitsReg != null) { recruitsReg.remove(); recruitsReg = null; }
        if (matchesBadgeReg != null) { matchesBadgeReg.remove(); matchesBadgeReg = null; }

        for (ListenerRegistration r : applicantRegs.values()) {
            if (r != null) r.remove();
        }
        applicantRegs.clear();
        postHasNew.clear();
        latestTsByPost.clear();
    }

    private void cleanupMissingApplicantRegs(Set<String> currentKeys) {
        Iterator<Map.Entry<String, ListenerRegistration>> it = applicantRegs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ListenerRegistration> e = it.next();
            if (!currentKeys.contains(e.getKey())) {
                if (e.getValue() != null) e.getValue().remove();
                it.remove();
                postHasNew.remove(e.getKey());
                latestTsByPost.remove(e.getKey());
            }
        }
        updateBadgeVisibility();
    }

    private void watchApplicantsForPost(@NonNull String postType, @NonNull String postId) {
        String key = postType + ":" + postId;

        ListenerRegistration old = applicantRegs.get(key);
        if (old != null) old.remove();

        ListenerRegistration reg = db.collection(postType.equals("match") ? "matches" : "recruitPosts")
                .document(postId)
                .collection("applicants")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener((qs, err) -> {
                    if (err != null || qs == null) return;

                    long latest = 0L;
                    for (DocumentSnapshot a : qs.getDocuments()) {
                        Long t = a.getLong("timestamp");
                        latest = toMillis(t == null ? 0L : t);
                    }

                    latestTsByPost.put(key, latest);

                    long lastSeen = getLastSeenTs(postType, postId);
                    boolean hasNew = (latest > 0L) && (latest > lastSeen);

                    postHasNew.put(key, hasNew);
                    updateBadgeVisibility();
                });

        applicantRegs.put(key, reg);
    }

    private void startBadgeAggregateWatch() {
        if (isEmpty(myTeamId)) {
            if (badgeNew != null) badgeNew.setVisibility(View.GONE);
            return;
        }
        if (matchesBadgeReg != null) { matchesBadgeReg.remove(); matchesBadgeReg = null; }

        matchesBadgeReg = db.collection("matches")
                .whereEqualTo("teamId", myTeamId)
                .orderBy("lastApplicantTs", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener((qs, err) -> {
                    if (err != null || qs == null) return;

                    boolean anyNew = false;
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        Long v = d.getLong("lastApplicantTs");
                        long lastTs = (v == null) ? 0L : (v > 2_000_000_000L ? v : v * 1000L);
                        long lastSeen = getLastSeenTs("match", d.getId());
                        if (lastTs > 0 && lastTs > lastSeen) {
                            anyNew = true;
                            break;
                        }
                    }
                    if (badgeNew != null) badgeNew.setVisibility(anyNew ? View.VISIBLE : View.GONE);
                });
    }

    private void recomputeBadgeFromPrefs() {
        boolean anyNew = false;
        for (Map.Entry<String, Long> e : latestTsByPost.entrySet()) {
            String key = e.getKey();
            long latest = e.getValue() == null ? 0L : e.getValue();
            String[] parts = key.split(":", 2);
            String type = parts.length > 0 ? parts[0] : "";
            String postId = parts.length > 1 ? parts[1] : "";

            long lastSeen = getLastSeenTs(type, postId);
            boolean hasNew = (latest > 0L) && (latest > lastSeen);

            postHasNew.put(key, hasNew);
            if (hasNew) anyNew = true;
        }
        if (badgeNew != null) badgeNew.setVisibility(anyNew ? View.VISIBLE : View.GONE);
    }

    private void updateBadgeVisibility() {
        boolean anyNew = false;
        for (Boolean v : postHasNew.values()) {
            if (Boolean.TRUE.equals(v)) {
                anyNew = true;
                break;
            }
        }
        if (badgeNew != null) badgeNew.setVisibility(anyNew ? View.VISIBLE : View.GONE);
    }

    private long getLastSeenTs(String postType, String postId) {
        String k = buildPostKey(postType, postId);
        return requireContext()
                .getSharedPreferences("applicants_seen", android.content.Context.MODE_PRIVATE)
                .getLong(k, 0L);
    }

    private static String buildPostKey(String postType, String postId) {
        return "last_seen_applicant_ts_" +
                (postType == null ? "" : postType) + "_" +
                (postId == null ? "" : postId);
    }

    private static long toMillis(long v) {
        if (v <= 0L) return 0L;
        return (v > 2_000_000_000L) ? v : v * 1000L;
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    // ===== 필터 콜백 =====

    @Override
    public void onRecruitFilterApplied(@NonNull RecruitFilters filters) {
        this.currentRecruitFilters = filters;

        Fragment current = getChildFragmentManager().findFragmentById(R.id.recruit_match_container);
        if (current instanceof RecruitListFragment) {
            ((RecruitListFragment) current).applyExternalFilters(filters);
        }

        renderFilterChips();
    }

    @Override
    public void onMatchFilterApplied(@NonNull MatchFilters filters) {
        this.currentMatchFilters = filters;

        Fragment current = getChildFragmentManager().findFragmentById(R.id.recruit_match_container);
        if (current instanceof MatchListFragment) {
            ((MatchListFragment) current).applyExternalFilters(filters);
        }
        // TeamMatchFragment 는 자체 다이얼로그 사용 → 여기서는 무시

        renderFilterChips();
    }

    // ===== 칩 렌더링 =====

    private void renderFilterChips() {
        if (!isAdded()) return;
        if (selectedFilterChips == null) return;

        selectedFilterChips.removeAllViews();

        // 팀 모드에서는 상단 필터/칩 안씀
        if (!isRecruitTab && isTeamMode) return;

        if (isRecruitTab) {
            renderRecruitChips();
        } else {
            renderMatchChips();
        }
    }

    private void renderRecruitChips() {
        if (currentRecruitFilters == null) return;
        RecruitFilters f = currentRecruitFilters;

        if (isDefault(f.recruitType)) {
            addFilterChip("정식선수, 용병");
        } else {
            String label;
            switch (f.recruitType) {
                case "regular": label = "정식선수"; break;
                case "mercenary": label = "용병"; break;
                default: label = f.recruitType;
            }
            addFilterChip(label);
        }

        String city = (f.common != null) ? f.common.city : null;
        String district = (f.common != null) ? f.common.district : null;
        if (!isDefault(city) || !isDefault(district)) {
            if (!isDefault(city) && !isDefault(district)) {
                addFilterChip(city + " " + district);
            } else if (!isDefault(city)) {
                addFilterChip(city);
            }
        }

        if (f.skillMin != null || f.skillMax != null) {
            StringBuilder sb = new StringBuilder("실력: ");
            sb.append(f.skillMin != null ? f.skillMin : "전체");
            sb.append("~");
            sb.append(f.skillMax != null ? f.skillMax : "전체");
            addFilterChip(sb.toString());
        }

        if (isDefault(f.position)) {
            addFilterChip("GK, DF, MF, FW");
        } else {
            addFilterChip(f.position);
        }

        if (isDefault(f.weekday)) {
            addFilterChip("월, 화, 수, 목, 금, 토, 일");
        } else {
            addFilterChip(f.weekday);
        }

        if (!isDefault(f.dateFrom) || !isDefault(f.dateTo)) {
            String from = isDefault(f.dateFrom) ? "전체" : f.dateFrom;
            String to   = isDefault(f.dateTo)   ? "전체" : f.dateTo;
            addFilterChip(from + "~" + to);
        }

        if (!isDefault(f.timeFrom) || !isDefault(f.timeTo)) {
            String from = isDefault(f.timeFrom) ? "전체" : f.timeFrom;
            String to   = isDefault(f.timeTo)   ? "전체" : f.timeTo;
            addFilterChip(from + "~" + to);
        }
    }

    private void renderMatchChips() {
        if (currentMatchFilters == null) return;
        MatchFilters f = currentMatchFilters;

        String city = (f.common != null) ? f.common.city : null;
        String district = (f.common != null) ? f.common.district : null;
        if (!isDefault(city) || !isDefault(district)) {
            if (!isDefault(city) && !isDefault(district)) {
                addFilterChip(city + " " + district);
            } else if (!isDefault(city)) {
                addFilterChip(city);
            }
        }

        if (f.skillMin != null || f.skillMax != null) {
            StringBuilder sb = new StringBuilder("실력: ");
            sb.append(f.skillMin != null ? f.skillMin : "전체");
            sb.append("~");
            sb.append(f.skillMax != null ? f.skillMax : "전체");
            addFilterChip(sb.toString());
        }

        if (isDefault(f.weekday)) {
            addFilterChip("월, 화, 수, 목, 금, 토, 일");
        } else {
            addFilterChip(f.weekday);
        }

        if (!isDefault(f.dateFrom) || !isDefault(f.dateTo)) {
            String from = isDefault(f.dateFrom) ? "전체" : f.dateFrom;
            String to   = isDefault(f.dateTo)   ? "전체" : f.dateTo;
            addFilterChip(from + "~" + to);
        }

        if (!isDefault(f.timeFrom) || !isDefault(f.timeTo)) {
            String from = isDefault(f.timeFrom) ? "전체" : f.timeFrom;
            String to   = isDefault(f.timeTo)   ? "전체" : f.timeTo;
            addFilterChip(from + "~" + to);
        }
    }

    private void addFilterChip(@NonNull String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(13f);
        tv.setPadding(26, 10, 26, 10);
        tv.setBackgroundResource(R.drawable.bg_filter_chip);
        tv.setTextColor(0xFF000000);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(8, 0, 0, 0);
        tv.setLayoutParams(lp);

        selectedFilterChips.addView(tv);
    }

    private boolean isDefault(@Nullable String s) {
        if (s == null) return true;
        String t = s.trim();
        return t.isEmpty() || "전체".equals(t);
    }

    public interface FilterAware {
        void onFilterChanged(Map<String, String> filters);
    }
}
