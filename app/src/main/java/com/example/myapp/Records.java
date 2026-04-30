// src/main/java/com/example/myapp/Records.java
package com.example.myapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.Typeface;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// 프로젝트 공통 토스트
import com.example.myapp.CustomToast;

@SuppressWarnings("unchecked")
public class Records extends AppCompatActivity {

    // =========================================================
    // 0) 상수 / 열거형
    // =========================================================
    private static final int PAGE_SIZE = 20;
    private static final int INITIAL_VISIBLE = 2;
    private static final int LOAD_STEP = 2;

    public enum SortKey { GAMES, GOALS, ASSISTS, ATTACK_P }
    public enum SortDir { DESC, ASC }

    // =========================================================
    // 1) UI 참조 (상단 요약 / 포디움 / 도움 TOP3 / 이전시합 / 개인기록)
    // =========================================================
    // --- 팀기록(상단 요약)
    private TextView tvGames, tvWins, tvDraws, tvLosses, tvWinRate;
    private TextView tvTotalGoalsFor, tvTotalGoalsAgainst, tvGoalDiff;

    // --- 득점자 TOP3(포디움)
    private LinearLayout top1Row, top2Row, top3Row;
    private ImageView imageTop1, imageTop2, imageTop3;
    private TextView tvTop1Name, tvTop2Name, tvTop3Name;
    private TextView tvTop1Goals, tvTop2Goals, tvTop3Goals;

    // --- (구) 더보기 토글 흔적(null-safe)
    private View layoutOtherToggle;
    private ImageView iconOtherToggle;
    private LinearLayout listOtherScorers;

    // --- 도움 TOP3 카드
    private LinearLayout assistTop1Row, assistTop2Row, assistTop3Row;
    private ImageView imageAssistTop1, imageAssistTop2, imageAssistTop3;
    private TextView tvAssistTop1Name, tvAssistTop2Name, tvAssistTop3Name;
    private TextView tvAssistTop1Count, tvAssistTop2Count, tvAssistTop3Count;

    // --- 이전 시합 영역
    private View layoutLoadMore;
    private RecyclerView recordsRecycler;

    // --- 개인기록 헤더 & 리스트
    private LinearLayout hGames, hGoals, hAssists, hAttackP;
    private TextView tvHdrGames, tvHdrGoals, tvHdrAssists, tvHdrAttackP;
    private ImageView icHdrGames, icHdrGoals, icHdrAssists, icHdrAttackP;
    private RecyclerView rvPersonalLeft, rvPersonalRight;

    // --- StateLayout
    private StateLayout state;

    // =========================================================
    // 2) 파이어베이스
    // =========================================================
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    // =========================================================
    // 3) 상태/캐시/페이징
    // =========================================================
    private String myTeamId = ""; // Intent 혹은 profiles.myTeam
    private DocumentSnapshot lastDoc = null;
    private boolean isLoading = false;
    private boolean noMore = false;
    private int visibleCount = INITIAL_VISIBLE;

    // --- 래치(로딩 상태 종합)
    private boolean latchMembers = false;
    private boolean latchMatches = false;
    private boolean latchPersonal = false;

    // --- 실시간 리스너
    private ListenerRegistration statsReg, scorersReg;

    // --- teamStats 폴백 요약
    private int fallbackGames=0, fallbackWins=0, fallbackDraws=0, fallbackLosses=0, fallbackGF=0, fallbackGA=0;
    private long lastLoadedMaxMatchTs = 0L;

    // =========================================================
    // 4) 데이터 컨테이너(이전 시합 / 팀원 / 개인기록)
    // =========================================================
    // 이전 시합 카드
    private final List<RecordItem> allItems = new ArrayList<>();
    private final List<RecordItem> items = new ArrayList<>();
    private RecordsAdapter adapter;

    // 팀원 정보(0득점 포함용)
    private boolean membersLoaded = false;
    private final Map<String, Member> members = new HashMap<>();
    private final Map<String, String> nickToImage = new HashMap<>();
    private final Map<String, String> nickToUid   = new HashMap<>();

    // 개인기록
    private final List<PlayerStat> personalData = new ArrayList<>();
    private SortKey currentKey = SortKey.GOALS;   // 초기 정렬: 득점
    private SortDir currentDir = SortDir.DESC;    // 초기 방향: 높은순
    private FixedColumnAdapter leftAdapter;
    private MetricsAdapter rightAdapter;
    private boolean assistTop3Locked = false;     // personalData로 채운 이후 잠금

    // =========================================================
    // 5) 생명주기
    // =========================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.records);

        // StateLayout
        state = findViewById(R.id.stateLayout);
        if (state != null) state.showLoading();

        // Firebase
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // UI findViewById
        bindSummaryViews();
        bindGoalTop3Views();
        bindAssistTop3Views();
        bindPreviousMatchesViews();
        bindPersonalViews();

        // 팀 ID → 리스너/로드 시작
        String extraTeamId = getIntent().getStringExtra("myTeamId");
        if (!TextUtils.isEmpty(extraTeamId)) {
            myTeamId = extraTeamId;
            startStatsListeners();
            loadTeamMembers();     // 완료→ latchMembers=true
            resetAndLoad(true);    // 첫 페이지 → latchMatches=true
        } else {
            String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "";
            if (TextUtils.isEmpty(uid)) return;
            db.collection("profiles").document(uid).get()
                    .addOnSuccessListener(p -> {
                        myTeamId = n(p.getString("myTeam"));
                        if (!TextUtils.isEmpty(myTeamId)) {
                            startStatsListeners();
                            loadTeamMembers();
                            resetAndLoad(true);
                        } else {
                            showEmptyWithMessage("팀 정보를 찾지 못했어요.");
                        }
                    })
                    .addOnFailureListener(e -> showEmptyWithMessage("프로필 불러오기 실패: " + e.getMessage()));
        }
    }

    @Override
    protected void onStop() {
        if (statsReg != null) { statsReg.remove(); statsReg = null; }
        if (scorersReg != null) { scorersReg.remove(); scorersReg = null; }
        super.onStop();
    }

    // =========================================================
    // 6) 바인딩(뷰 참조 정리)
    // =========================================================
    private void bindSummaryViews() {
        tvGames  = findViewById(R.id.tvGames);
        tvWins   = findViewById(R.id.tvWins);
        tvDraws  = findViewById(R.id.tvDraws);
        tvLosses = findViewById(R.id.tvLosses);
        tvWinRate= findViewById(R.id.tvWinRate);
        tvTotalGoalsFor     = findViewById(R.id.tvTotalGoalsFor);
        tvTotalGoalsAgainst = findViewById(R.id.tvTotalGoalsAgainst);
        tvGoalDiff          = findViewById(R.id.tvGoalDiff);
    }

    private void bindGoalTop3Views() {
        top1Row = findViewById(R.id.top1Row);
        top2Row = findViewById(R.id.top2Row);
        top3Row = findViewById(R.id.top3Row);
        imageTop1 = findViewById(R.id.imageTop1);
        imageTop2 = findViewById(R.id.imageTop2);
        imageTop3 = findViewById(R.id.imageTop3);
        tvTop1Name = findViewById(R.id.tvTop1Name);
        tvTop2Name = findViewById(R.id.tvTop2Name);
        tvTop3Name = findViewById(R.id.tvTop3Name);
        tvTop1Goals = findViewById(R.id.tvTop1Goals);
        tvTop2Goals = findViewById(R.id.tvTop2Goals);
        tvTop3Goals = findViewById(R.id.tvTop3Goals);
    }

    private void bindAssistTop3Views() {
        assistTop1Row = findViewById(R.id.assistTop1Row);
        assistTop2Row = findViewById(R.id.assistTop2Row);
        assistTop3Row = findViewById(R.id.assistTop3Row);
        imageAssistTop1 = findViewById(R.id.imageAssistTop1);
        imageAssistTop2 = findViewById(R.id.imageAssistTop2);
        imageAssistTop3 = findViewById(R.id.imageAssistTop3);
        tvAssistTop1Name = findViewById(R.id.tvAssistTop1Name);
        tvAssistTop2Name = findViewById(R.id.tvAssistTop2Name);
        tvAssistTop3Name = findViewById(R.id.tvAssistTop3Name);
        tvAssistTop1Count = findViewById(R.id.tvAssistTop1Count);
        tvAssistTop2Count = findViewById(R.id.tvAssistTop2Count);
        tvAssistTop3Count = findViewById(R.id.tvAssistTop3Count);
    }

    private void bindPreviousMatchesViews() {
        layoutLoadMore = findViewById(R.id.layoutLoadMore);
        if (layoutLoadMore != null) layoutLoadMore.setOnClickListener(v -> onClickLoadMore());

        recordsRecycler = findViewById(R.id.recordsRecycler);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        recordsRecycler.setLayoutManager(lm);
        adapter = new RecordsAdapter(items);
        recordsRecycler.setAdapter(adapter);
        recordsRecycler.setNestedScrollingEnabled(false);

        // ✅ 카드 사이 중앙 점 추가 데코레이터
        recordsRecycler.addItemDecoration(new DotDividerDecoration(this));
    }

    /** 카드 사이 중앙 점 구분선 */
    private static class DotDividerDecoration extends RecyclerView.ItemDecoration {
        private final Paint paint;
        private final int radius;
        private final int space;

        DotDividerDecoration(Context ctx) {
            paint = new Paint();
            paint.setColor(Color.parseColor("#CCCCCC")); // ✅ 직접 코드로 색 지정 (회색 점)
            paint.setAntiAlias(true);
            radius = (int) (ctx.getResources().getDisplayMetrics().density * 2); // 6dp 지름 정도
            space = (int) (ctx.getResources().getDisplayMetrics().density * 10); // 카드 사이 간격
        }

        @Override
        public void onDrawOver(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            int childCount = parent.getChildCount();
            int width = parent.getWidth();

            for (int i = 0; i < childCount - 1; i++) { // 마지막 카드엔 점 X
                View child = parent.getChildAt(i);
                float x = width / 2f;
                float y = child.getBottom() + space / 2f;
                c.drawCircle(x, y, radius, paint);
            }
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                   @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            int pos = parent.getChildAdapterPosition(view);
            if (pos < parent.getAdapter().getItemCount() - 1) {
                outRect.bottom = space; // 점 표시 공간 확보
            }
        }
    }

    private void bindPersonalViews() {
        hGames = findViewById(R.id.hGames);
        hGoals = findViewById(R.id.hGoals);
        hAssists = findViewById(R.id.hAssists);
        hAttackP = findViewById(R.id.hAttackP);
        tvHdrGames = findViewById(R.id.tvHdrGames);
        tvHdrGoals = findViewById(R.id.tvHdrGoals);
        tvHdrAssists = findViewById(R.id.tvHdrAssists);
        tvHdrAttackP = findViewById(R.id.tvHdrAttackP);
        icHdrGames = findViewById(R.id.icHdrGames);
        icHdrGoals = findViewById(R.id.icHdrGoals);
        icHdrAssists = findViewById(R.id.icHdrAssists);
        icHdrAttackP = findViewById(R.id.icHdrAttackP);
        rvPersonalLeft  = findViewById(R.id.rvPersonalLeft);
        rvPersonalRight = findViewById(R.id.rvPersonalRight);

        if (rvPersonalLeft != null && rvPersonalRight != null) {
            rvPersonalLeft.setLayoutManager(new LinearLayoutManager(this));
            rvPersonalRight.setLayoutManager(new LinearLayoutManager(this));
            leftAdapter  = new FixedColumnAdapter();
            rightAdapter = new MetricsAdapter();
            rvPersonalLeft.setAdapter(leftAdapter);
            rvPersonalRight.setAdapter(rightAdapter);

            // 초기 정렬 헤더 비주얼
            applyPersonalHeaderVisual(SortKey.GOALS, SortDir.DESC);

            // 헤더 클릭
            if (hGames != null)   hGames.setOnClickListener(v -> onPersonalHeaderClick(SortKey.GAMES));
            if (hGoals != null)   hGoals.setOnClickListener(v -> onPersonalHeaderClick(SortKey.GOALS));
            if (hAssists != null) hAssists.setOnClickListener(v -> onPersonalHeaderClick(SortKey.ASSISTS));
            if (hAttackP != null) hAttackP.setOnClickListener(v -> onPersonalHeaderClick(SortKey.ATTACK_P));
        }
    }

    // =========================================================
    // 7) 로딩/리스너 시작
    // =========================================================
    private void startStatsListeners() {
        if (TextUtils.isEmpty(myTeamId)) return;

        // teamStats 요약
        if (statsReg != null) { statsReg.remove(); statsReg = null; }
        statsReg = db.collection("teamStats").document(myTeamId)
                .addSnapshotListener((doc, e) -> {
                    if (e != null || doc == null || !doc.exists()) return;

                    int games = safeInt(doc.get("games"));
                    int wins  = safeInt(doc.get("wins"));
                    int draws = safeInt(doc.get("draws"));
                    int losses= safeInt(doc.get("losses"));
                    int gf    = safeInt(doc.get("goalsFor"));
                    int ga    = safeInt(doc.get("goalsAgainst"));

                    boolean looksEmpty = (games==0 && wins==0 && draws==0 && losses==0 && gf==0 && ga==0);
                    long updated = 0L;
                    try {
                        com.google.firebase.Timestamp ts = doc.getTimestamp("updatedAt");
                        if (ts != null) updated = ts.toDate().getTime();
                    } catch (Exception ignore) {}

                    // 폴백보다 오래된 업데이트/빈 데이터면 무시
                    if ((looksEmpty && fallbackGames > 0) ||
                            (updated > 0 && updated < lastLoadedMaxMatchTs)) {
                        return;
                    }

                    tvGames.setText(String.valueOf(games));
                    tvWins.setText(String.valueOf(wins));
                    tvDraws.setText(String.valueOf(draws));
                    tvLosses.setText(String.valueOf(losses));
                    applyWinRateStyle(games, wins);

                    tvTotalGoalsFor.setText(String.valueOf(gf));
                    tvTotalGoalsAgainst.setText(String.valueOf(ga));
                    applyGoalDiffStyle(gf, ga);
                });

        // 득점자 Top3 컬렉션(표시는 로컬 재계산으로)
        if (scorersReg != null) { scorersReg.remove(); scorersReg = null; }
        scorersReg = db.collection("teamStats").document(myTeamId)
                .collection("scorers")
                .orderBy("goals", Query.Direction.DESCENDING)
                .limit(3)
                .addSnapshotListener((snap, e) -> { /* no-op */ });
    }

    private void loadTeamMembers() {
        if (TextUtils.isEmpty(myTeamId)) return;
        if (state != null) state.showLoading();

        db.collection("profiles").whereEqualTo("myTeam", myTeamId).get()
                .addOnSuccessListener(snap -> {
                    members.clear();
                    nickToImage.clear();
                    nickToUid.clear();

                    for (DocumentSnapshot d : snap.getDocuments()) {
                        String uid  = d.getId();
                        String nick = n(d.getString("nickname"));
                        String img  = n(d.getString("profileImageUrl"));
                        if (!TextUtils.isEmpty(nick)) {
                            members.put(uid, new Member(uid, nick, img));
                            if (!TextUtils.isEmpty(img)) nickToImage.put(nick, img);
                            nickToUid.put(nick, uid);
                        }
                    }
                    membersLoaded = true;

                    // 멤버 기반 포디움/도움 초기 업데이트
                    updateTop3FromItems();
                    updateAssistTop3FromItems();
                    updateAssistTop3FromPersonalData();

                    // 개인기록 시작
                    loadPersonalStats(myTeamId);

                    // 래치 ON
                    latchMembers = true;
                    maybeShowContent();
                })
                .addOnFailureListener(e -> showEmptyWithMessage("팀원 로드 실패: " + e.getMessage()));
    }

    private void resetAndLoad(boolean initial) {
        visibleCount = INITIAL_VISIBLE;
        allItems.clear();
        items.clear();
        if (adapter != null) adapter.notifyDataSetChanged();

        lastDoc = null;
        noMore = false;

        // 폴백 초기화
        fallbackGames=fallbackWins=fallbackDraws=fallbackLosses=fallbackGF=fallbackGA=0;
        lastLoadedMaxMatchTs = 0L;

        loadMatches(initial);
    }

    private void loadMatches(boolean initial) {
        if (TextUtils.isEmpty(myTeamId) || isLoading) return;
        isLoading = true;
        if (state != null && initial) state.showLoading();

        Query q = db.collection("matches")
                .whereEqualTo("teamId", myTeamId)
                .whereEqualTo("status", "finished")
                .orderBy("matchTs", Query.Direction.DESCENDING)
                .limit(PAGE_SIZE);
        if (lastDoc != null) q = q.startAfter(lastDoc);

        q.get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) { noMore = true; }

                    int gamesAdd = 0, winsAdd = 0, drawsAdd = 0, lossesAdd = 0, gfAdd = 0, gaAdd = 0;
                    long pageMaxTs = 0L;
                    List<RecordItem> pageItems = new ArrayList<>();

                    for (DocumentSnapshot d : snap.getDocuments()) {
                        int sf = safeInt(d.get("scoreFor"));
                        int sa = safeInt(d.get("scoreAgainst"));
                        String date     = n(d.getString("date"));
                        String time     = n(d.getString("time"));
                        String stadium  = n(d.getString("stadiumName"));
                        String stadiumAddress = n(d.getString("stadiumAddress"));
                        String teamName        = n(d.getString("teamName"));
                        String teamLogoUrl     = n(d.getString("teamLogoUrl"));
                        String opponentName    = n(d.getString("opponentName"));
                        String opponentLogoUrl = n(d.getString("opponentLogoUrl"));

                        // 득점자 리스트(좌: 홈, 우: 어웨이)
                        List<Map<String, Object>> scorers = (List<Map<String, Object>>) d.get("scorers");

                        Map<String,Integer> homeMap = new HashMap<>();
                        Map<String,Integer> awayMap = new HashMap<>();
                        if (scorers != null) {
                            for (Map<String,Object> m : scorers) {
                                String nick = n((String) m.get("nickname"));
                                int g = safeInt(m.get("goals"));
                                if (g <= 0 || TextUtils.isEmpty(nick)) continue;
                                boolean isHome = isHomeScorer(m);
                                if (isHome) homeMap.put(nick, homeMap.getOrDefault(nick, 0) + g);
                                else awayMap.put(nick, awayMap.getOrDefault(nick, 0) + g);
                            }
                        }
                        List<ScorerEntry> homeList = toEntries(homeMap);
                        List<ScorerEntry> awayList = toEntries(awayMap);
                        if (homeList.isEmpty() && awayList.isEmpty() && scorers != null) {
                            Map<String,Integer> tmp = new HashMap<>();
                            for (Map<String,Object> m : scorers) {
                                String nick = n((String) m.get("nickname"));
                                int g = safeInt(m.get("goals"));
                                if (g <= 0 || TextUtils.isEmpty(nick)) continue;
                                tmp.put(nick, tmp.getOrDefault(nick, 0) + g);
                            }
                            homeList = toEntries(tmp);
                        }

                        long ts = d.contains("matchTs") ? safeLong(d.get("matchTs")) : computeTs(date, time);
                        if (ts > pageMaxTs) pageMaxTs = ts;

                        pageItems.add(new RecordItem(
                                teamName, teamLogoUrl,
                                opponentName, opponentLogoUrl,
                                date, time, stadium,
                                stadiumAddress,
                                sf, sa,
                                homeList, awayList, false,
                                ts
                        ));

                        gamesAdd++; gfAdd += sf; gaAdd += sa;
                        if (sf > sa) winsAdd++; else if (sf == sa) drawsAdd++; else lossesAdd++;
                    }

                    allItems.addAll(pageItems);
                    allItems.sort((a, b) -> Long.compare(b.sortTs, a.sortTs));

                    updateTop3FromItems();
                    updateAssistTop3FromItems();

                    if (initial) {
                        fallbackGames  = gamesAdd;
                        fallbackWins   = winsAdd;
                        fallbackDraws  = drawsAdd;
                        fallbackLosses = lossesAdd;
                        fallbackGF     = gfAdd;
                        fallbackGA     = gaAdd;
                        lastLoadedMaxMatchTs = pageMaxTs;

                        tvGames.setText(String.valueOf(fallbackGames));
                        tvWins.setText(String.valueOf(fallbackWins));
                        tvDraws.setText(String.valueOf(fallbackDraws));
                        tvLosses.setText(String.valueOf(fallbackLosses));
                        applyWinRateStyle(fallbackGames, fallbackWins);
                        tvTotalGoalsFor.setText(String.valueOf(fallbackGF));
                        tvTotalGoalsAgainst.setText(String.valueOf(fallbackGA));
                        applyGoalDiffStyle(fallbackGF, fallbackGA);
                    }

                    if (!snap.isEmpty()) lastDoc = snap.getDocuments().get(snap.size() - 1);

                    applyVisibleWindow();
                    updateLoadMoreVisibility();

                    isLoading = false;

                    // 래치 ON
                    if (initial) latchMatches = true;
                    maybeShowContent();
                })
                .addOnFailureListener(e -> {
                    isLoading = false;
                    showEmptyWithMessage("경기 기록을 불러오지 못했어요.\n잠시 후 다시 시도해 주세요.");
                });
    }

    /** 개인기록 로딩: matches.goalEvents 기반(폴백: scorers/assists 배열) + 팀원 0기록 제로-필
     *  🔁 수정 포인트: 각 경기마다 우리팀 전원에게 그 경기ID를 gamesByPlayer에 넣어서
     *                 득점/도움이 없어도 games가 올라가게 한다.
     */
    private void loadPersonalStats(@NonNull String teamId) {
        if (state != null) state.showLoading();

        db.collection("matches")
                .whereEqualTo("teamId", teamId)
                .whereEqualTo("status", "finished")
                .orderBy("matchTs", Query.Direction.DESCENDING)
                .limit(300)
                .get()
                .addOnSuccessListener(snap -> {
                    // 우리팀 멤버 UID 세트 (팀원만 집계)
                    Set<String> teamMemberUids = new HashSet<>(members.keySet());

                    // playerId -> 누적 스탯
                    Map<String, PlayerStat> map = new HashMap<>();
                    // playerId -> 이 선수가 뛴 경기 id 들
                    Map<String, Set<String>> gamesByPlayer = new HashMap<>();

                    for (DocumentSnapshot d : snap.getDocuments()) {
                        String matchId = d.getId();

                        // ✅ 0) 이 경기에는 우리팀원 전원이 뛴 걸로 본다 (네가 말한 테스트 모드랑 맞춤)
                        for (String uid : teamMemberUids) {
                            // set 만들어 넣기
                            gamesByPlayer
                                    .computeIfAbsent(uid, k -> new HashSet<>())
                                    .add(matchId);

                            // 이때 플레이어 객체도 없으면 만들어놓자 (나중에 닉/이미지 보정됨)
                            if (!map.containsKey(uid)) {
                                PlayerStat ps = new PlayerStat(uid);
                                // 닉/이미지는 아래에서 한 번 더 보정하니까 여기선 비워도 됨
                                map.put(uid, ps);
                            }
                        }

                        // 1) goalEvents 기반 집계 (한 이벤트 = 1득점, assistId 있으면 1도움)
                        List<Map<String, Object>> events = (List<Map<String, Object>>) d.get("goalEvents");
                        if (events != null) {
                            for (Map<String, Object> ev : events) {
                                String scorerId   = n((String) ev.get("scorerId"));
                                String scorerNick = n((String) ev.get("scorerNickname"));
                                String assistId   = n((String) ev.get("assistId"));
                                String assistNick = n((String) ev.get("assistNickname"));

                                // 득점자
                                if (!TextUtils.isEmpty(scorerId) && teamMemberUids.contains(scorerId)) {
                                    PlayerStat ps = map.get(scorerId);
                                    if (ps == null) {
                                        ps = new PlayerStat(scorerId);
                                        ps.nickname = TextUtils.isEmpty(scorerNick) ? scorerId : scorerNick;
                                        Member m = members.get(scorerId);
                                        ps.profileImageUrl = (m != null && !TextUtils.isEmpty(m.imageUrl))
                                                ? m.imageUrl : nickToImage.get(ps.nickname);
                                        map.put(scorerId, ps);
                                    } else if (TextUtils.isEmpty(ps.nickname)) {
                                        ps.nickname = TextUtils.isEmpty(scorerNick) ? scorerId : scorerNick;
                                    }
                                    ps.goals += 1;
                                    // 경기 추가는 위에서 이미 한 번 넣었음
                                }

                                // 도움자
                                if (!TextUtils.isEmpty(assistId) && teamMemberUids.contains(assistId)) {
                                    PlayerStat ps = map.get(assistId);
                                    if (ps == null) {
                                        ps = new PlayerStat(assistId);
                                        ps.nickname = TextUtils.isEmpty(assistNick) ? assistId : assistNick;
                                        Member m = members.get(assistId);
                                        ps.profileImageUrl = (m != null && !TextUtils.isEmpty(m.imageUrl))
                                                ? m.imageUrl : nickToImage.get(ps.nickname);
                                        map.put(assistId, ps);
                                    } else if (TextUtils.isEmpty(ps.nickname)) {
                                        ps.nickname = TextUtils.isEmpty(assistNick) ? assistId : assistNick;
                                    }
                                    ps.assists += 1;
                                    // 경기 추가는 위에서 이미 한 번 넣었음
                                }
                            }
                        }

                        // 2) 폴백: goalEvents 없을 때 scorers / assists 배열 사용
                        if (events == null) {
                            // 득점 폴백
                            List<Map<String, Object>> scorers = (List<Map<String, Object>>) d.get("scorers");
                            if (scorers != null) {
                                for (Map<String, Object> s : scorers) {
                                    String pid  = n((String) s.get("playerId"));
                                    String nick = n((String) s.get("nickname"));
                                    int goals   = safeInt(s.get("goals"));
                                    if (TextUtils.isEmpty(pid) || !teamMemberUids.contains(pid) || goals <= 0) continue;

                                    PlayerStat ps = map.get(pid);
                                    if (ps == null) {
                                        ps = new PlayerStat(pid);
                                        ps.nickname = TextUtils.isEmpty(nick) ? pid : nick;
                                        Member m = members.get(pid);
                                        ps.profileImageUrl = (m != null && !TextUtils.isEmpty(m.imageUrl))
                                                ? m.imageUrl : nickToImage.get(ps.nickname);
                                        map.put(pid, ps);
                                    } else if (TextUtils.isEmpty(ps.nickname)) {
                                        ps.nickname = TextUtils.isEmpty(nick) ? pid : nick;
                                    }
                                    ps.goals += goals;
                                    // 경기 추가는 위에서 이미 했음
                                }
                            }

                            // 도움 폴백
                            List<Map<String, Object>> assistsArr =
                                    (List<Map<String, Object>>) (d.get("assists") != null ? d.get("assists") : d.get("assistList"));
                            if (assistsArr != null) {
                                for (Map<String, Object> a : assistsArr) {
                                    String pid  = n((String) a.get("playerId"));
                                    String nick = n((String) a.get("nickname"));
                                    int cnt     = safeInt(a.get("assists"));
                                    if (cnt == 0) cnt = safeInt(a.get("count"));
                                    if (TextUtils.isEmpty(pid) || !teamMemberUids.contains(pid) || cnt <= 0) continue;

                                    PlayerStat ps = map.get(pid);
                                    if (ps == null) {
                                        ps = new PlayerStat(pid);
                                        ps.nickname = TextUtils.isEmpty(nick) ? pid : nick;
                                        Member m = members.get(pid);
                                        ps.profileImageUrl = (m != null && !TextUtils.isEmpty(m.imageUrl))
                                                ? m.imageUrl : nickToImage.get(ps.nickname);
                                        map.put(pid, ps);
                                    } else if (TextUtils.isEmpty(ps.nickname)) {
                                        ps.nickname = TextUtils.isEmpty(nick) ? pid : nick;
                                    }
                                    ps.assists += cnt;
                                    // 경기 추가는 위에서 이미 했음
                                }
                            }
                        }
                    }

                    // ✅ 모든 팀원 제로-필(기록 없어도 목록에 나오게) + 닉/이미지 보정
                    for (Member m : members.values()) {
                        if (!map.containsKey(m.uid)) {
                            PlayerStat ps = new PlayerStat(m.uid);
                            ps.nickname = m.nickname;
                            ps.profileImageUrl = m.imageUrl;
                            ps.games = 0; ps.goals = 0; ps.assists = 0;
                            map.put(m.uid, ps);
                        } else {
                            PlayerStat ps = map.get(m.uid);
                            if (TextUtils.isEmpty(ps.nickname)) ps.nickname = m.nickname;
                            if (TextUtils.isEmpty(ps.profileImageUrl)) ps.profileImageUrl = m.imageUrl;
                        }
                    }

                    // personalData 확정
                    personalData.clear();
                    for (PlayerStat ps : map.values()) {
                        Set<String> set = gamesByPlayer.get(ps.playerId);
                        // 🔁 여기서 이제 “경기마다 전원 넣은” 게 적용된다
                        ps.games = (set != null) ? set.size() : 0;
                        personalData.add(ps);
                    }

                    // 초기 정렬: 득점 DESC
                    currentKey = SortKey.GOALS;
                    currentDir = SortDir.DESC;
                    applyPersonalHeaderVisual(currentKey, currentDir);
                    sortAndRenderPersonal();
                    updateAssistTop3FromPersonalData();

                    // 래치 ON
                    latchPersonal = true;
                    maybeShowContent();
                })
                .addOnFailureListener(e -> showEmptyWithMessage("개인기록 로드 실패: " + e.getMessage()));
    }


    // =========================================================
    // 8) UI 동작(더보기/헤더정렬) + 렌더링
    // =========================================================
    private void onClickLoadMore() {
        visibleCount += LOAD_STEP;

        if (visibleCount <= allItems.size()) {
            applyVisibleWindow();
            updateLoadMoreVisibility();
            return;
        }
        if (noMore) {
            visibleCount = Math.min(visibleCount, allItems.size());
            applyVisibleWindow();
            updateLoadMoreVisibility();
            return;
        }
        loadMatches(false);
    }

    private void applyVisibleWindow() {
        items.clear();
        int take = Math.min(visibleCount, allItems.size());
        for (int i = 0; i < take; i++) items.add(allItems.get(i));
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void updateLoadMoreVisibility() {
        if (layoutLoadMore == null) return;
        boolean canShowMore = (visibleCount < allItems.size()) || (!noMore);
        layoutLoadMore.setVisibility(canShowMore ? View.VISIBLE : View.GONE);
    }

    private void onPersonalHeaderClick(SortKey key) {
        if (currentKey == key) {
            currentDir = (currentDir == SortDir.DESC) ? SortDir.ASC : SortDir.DESC;
        } else {
            currentKey = key;
            currentDir = SortDir.DESC;
        }
        applyPersonalHeaderVisual(currentKey, currentDir);
        sortAndRenderPersonal();
    }

    private void applyPersonalHeaderVisual(SortKey key, SortDir dir) {
        resetPersonalHeader(hGames,   tvHdrGames,   icHdrGames);
        resetPersonalHeader(hGoals,   tvHdrGoals,   icHdrGoals);
        resetPersonalHeader(hAssists, tvHdrAssists, icHdrAssists);
        resetPersonalHeader(hAttackP, tvHdrAttackP, icHdrAttackP);

        LinearLayout box;
        TextView tv;
        ImageView ic;
        switch (key) {
            case GAMES:    box = hGames;    tv = tvHdrGames;    ic = icHdrGames;    break;
            case GOALS:    box = hGoals;    tv = tvHdrGoals;    ic = icHdrGoals;    break;
            case ASSISTS:  box = hAssists;  tv = tvHdrAssists;  ic = icHdrAssists;  break;
            case ATTACK_P:
            default:       box = hAttackP;  tv = tvHdrAttackP;  ic = icHdrAttackP;  break;
        }

        if (tv != null) tv.setTextColor(ContextCompat.getColor(this, R.color.goal_for));
        if (ic != null) {
            ic.setAlpha(1.0f);
            ic.setImageResource(dir == SortDir.DESC ? R.drawable.ic_arrow_up : R.drawable.ic_arrow_down);
        }
        if (rightAdapter != null) rightAdapter.setActive(key, dir);
    }

    private void resetPersonalHeader(LinearLayout box, TextView tv, ImageView ic) {
        if (tv != null) tv.setTextColor(0xFF424242);
        if (ic != null) ic.setAlpha(0.0f);
    }

    private void sortAndRenderPersonal() {
        java.util.Comparator<PlayerStat> cmp = (a, b) -> {
            int va, vb;
            switch (currentKey) {
                case GAMES:   va = a.games;          vb = b.games;          break;
                case GOALS:   va = a.goals;          vb = b.goals;          break;
                case ASSISTS: va = a.assists;        vb = b.assists;        break;
                case ATTACK_P:
                default:      va = a.attackP();      vb = b.attackP();      break;
            }
            int c = Integer.compare(va, vb);
            if (currentDir == SortDir.DESC) c = -c;
            if (c != 0) return c;

            // ✅ 동률일 때: 닉네임 사전순(항상 오름차순)
            return KOREAN.compare(n(a.nickname), n(b.nickname));
        };

        personalData.sort(cmp);

        if (leftAdapter != null)  leftAdapter.submitList(personalData);
        if (rightAdapter != null) rightAdapter.submitList(personalData);
    }


    // =========================================================
    // 9) 포디움/도움 TOP3 계산
    // =========================================================
    private void updateTop3FromItems() {
        // 닉네임 세트(용병 제외)
        final Set<String> memberNick = new HashSet<>();
        for (Member m : members.values()) if (!TextUtils.isEmpty(m.nickname)) memberNick.add(m.nickname);

        // 닉네임별 총 득점
        final Map<String,Integer> total = new HashMap<>();
        for (RecordItem it : allItems) {
            if (it.homeScorers == null) continue;
            for (ScorerEntry e : it.homeScorers) {
                if (TextUtils.isEmpty(e.name) || !memberNick.contains(e.name)) continue;
                total.put(e.name, total.getOrDefault(e.name, 0) + e.goals);
            }
        }
        // 팀원 0골도 포함(빈 카드 방지)
        if (membersLoaded) for (Member m : members.values())
            if (!TextUtils.isEmpty(m.nickname)) total.putIfAbsent(m.nickname, 0);

        List<Map.Entry<String,Integer>> list = new ArrayList<>(total.entrySet());
        list.sort((a,b) -> {
            int c = Integer.compare(b.getValue(), a.getValue());           // 득점 내림차순
            return (c != 0) ? c : KOREAN.compare(a.getKey(), b.getKey());  // 동률 → 이름순
        });
        list = padTop3(list); // 항상 3칸

        // 모든 슬롯 항상 보이게
        // 항상 3칸 보이게
        setRowVisible(top1Row, true);
        setRowVisible(top2Row, true);
        setRowVisible(top3Row, true);

// rank 전달(1~3)
        bindRankRow(imageTop1, tvTop1Name, tvTop1Goals,
                list.get(0).getKey(), list.get(0).getValue(), false, 1);
        bindRankRow(imageTop2, tvTop2Name, tvTop2Goals,
                list.get(1).getKey(), list.get(1).getValue(), false, 2);
        bindRankRow(imageTop3, tvTop3Name, tvTop3Goals,
                list.get(2).getKey(), list.get(2).getValue(), false, 3);

    }

    private void updateAssistTop3FromItems() {
        if (assistTop3Locked) return;

        // 현재 matches에서 명시적 도움 집계가 없다면 비워둠
        final Map<String, Integer> assistByName = new HashMap<>();
        List<Map.Entry<String,Integer>> list = new ArrayList<>(assistByName.entrySet());
        list.sort((a,b) -> {
            int c = Integer.compare(b.getValue(), a.getValue());
            return (c != 0) ? c : a.getKey().compareToIgnoreCase(b.getKey());
        });

        boolean hasData = list.size() > 0 && list.get(0).getValue() > 0;

        setRowVisible(assistTop1Row, false);
        setRowVisible(assistTop2Row, false);
        setRowVisible(assistTop3Row, false);
        View assistsCard = findViewById(R.id.assistsTop3Card);

        if (!hasData) {
            if (assistsCard != null) assistsCard.setVisibility(View.GONE);
            return;
        } else if (assistsCard != null) {
            assistsCard.setVisibility(View.VISIBLE);
        }

        if (list.size() > 0) bindAssistRowFromPair(assistTop1Row, imageAssistTop1, tvAssistTop1Name, tvAssistTop1Count, list.get(0));
        if (list.size() > 1) bindAssistRowFromPair(assistTop2Row, imageAssistTop2, tvAssistTop2Name, tvAssistTop2Count, list.get(1));
        if (list.size() > 2) bindAssistRowFromPair(assistTop3Row, imageAssistTop3, tvAssistTop3Name, tvAssistTop3Count, list.get(2));
    }

    private void updateAssistTop3FromPersonalData() {
        // personalData에서 도움 내림차순, 동률이면 이름 사전순
        List<PlayerStat> list = new ArrayList<>(personalData);
        // 팀원 한 명도 못 불러온 경우 대비: 비어 있으면 더미 3명으로 채움
        if (list.isEmpty() && membersLoaded) {
            for (Member m : members.values()) {
                PlayerStat ps = new PlayerStat(m.uid);
                ps.nickname = m.nickname;
                ps.assists = 0; ps.games = 0; ps.goals = 0;
                list.add(ps);
            }
        }
        list.sort((a,b) -> {
            int c = Integer.compare(b.assists, a.assists);
            return (c != 0) ? c : KOREAN.compare(n(a.nickname), n(b.nickname));
        });

        // 항상 3칸으로
        List<PlayerStat> top = new ArrayList<>();
        for (int i=0; i<Math.min(3, list.size()); i++) top.add(list.get(i));
        while (top.size() < 3) { PlayerStat p = new PlayerStat(""); p.nickname=""; p.assists=0; top.add(p); }

        // 카드 숨기지 말 것
        // 카드 항상 노출
        View assistsCard = findViewById(R.id.assistsTop3Card);
        if (assistsCard != null) assistsCard.setVisibility(View.VISIBLE);
        setRowVisible(assistTop1Row, true);
        setRowVisible(assistTop2Row, true);
        setRowVisible(assistTop3Row, true);

// rank 전달(1~3)
        bindRankRow(imageAssistTop1, tvAssistTop1Name, tvAssistTop1Count,
                n(top.get(0).nickname), top.get(0).assists, true, 1);
        bindRankRow(imageAssistTop2, tvAssistTop2Name, tvAssistTop2Count,
                n(top.get(1).nickname), top.get(1).assists, true, 2);
        bindRankRow(imageAssistTop3, tvAssistTop3Name, tvAssistTop3Count,
                n(top.get(2).nickname), top.get(2).assists, true, 3);

        assistTop3Locked = true; // 다른 경로에서 덮어쓰지 않게
    }


    private void bindAssistRowFromPair(LinearLayout row, ImageView img, TextView tvName, TextView tvCnt,
                                       Map.Entry<String,Integer> pair) {
        setRowVisible(row, true);
        String name = pair.getKey();
        int cnt = pair.getValue();
        tvName.setText(TextUtils.isEmpty(name) ? "—" : name);
        tvCnt.setText(cnt + "도움");
        loadProfileImage(img, name);
    }

    private void bindAssistRowFromPS(LinearLayout row, ImageView img, TextView tvName, TextView tvCnt,
                                     PlayerStat p) {
        setRowVisible(row, true);
        tvName.setText(TextUtils.isEmpty(p.nickname) ? "—" : p.nickname);
        tvCnt.setText(p.assists + "도움");
        loadProfileImage(img, p.nickname);
    }

    // =========================================================
    // 10) 스타일/State/유틸
    // =========================================================
    private void maybeShowContent() {
        if (state == null) return;
        if (latchMembers && latchMatches && latchPersonal) state.showContent();
        else state.showLoading();
    }

    private void showEmptyWithMessage(String msg) {
        if (state != null) {
            state.showEmpty();
            TextView tv = findViewById(R.id.txtEmptyMessage);
            if (tv != null) tv.setText(msg);
        } else {
            CustomToast.error(this, msg);
        }
    }

    private void setResultChip(TextView chip, int sf, int sa) {
        String label; int bgColor;
        if (sf > sa) { label = "승"; bgColor = 0xFF2E7D32; }
        else if (sf == sa) { label = "무"; bgColor = 0xFF546E7A; }
        else { label = "패"; bgColor = 0xFFB71C1C; }
        chip.setText(label);
        chip.setBackgroundResource(R.drawable.bg_chip_round);
        chip.getBackground().setTint(bgColor);
    }

    private void applyWinRateStyle(int games, int wins) {
        double rate = (games == 0) ? 0.0 : (wins * 100.0) / games;
        String rateText = String.format(Locale.getDefault(), "%.0f%%", rate);
        tvWinRate.setText(rateText);

        int colorRes;
        if (rate > 50.0) colorRes = R.color.win;
        else if (games > 0 && (2 * wins == games)) colorRes = R.color.draw;
        else if (rate == 50.0) colorRes = R.color.draw;
        else colorRes = R.color.loss;

        tvWinRate.setTextColor(ContextCompat.getColor(this, colorRes));
    }

    private void applyGoalDiffStyle(int gf, int ga) {
        int diff = gf - ga;
        tvGoalDiff.setText(String.valueOf(diff));
        int color;
        if (diff > 0) color = ContextCompat.getColor(this, R.color.goal_for);
        else if (diff < 0) color = ContextCompat.getColor(this, R.color.goal_against);
        else color = 0xFF111111;
        tvGoalDiff.setTextColor(color);
    }

    private void setRowVisible(LinearLayout row, boolean visible) {
        if (row == null) return;
        row.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void loadProfileImage(ImageView iv, String nickname) {
        String url = nickToImage.get(nickname);
        int radius = dp(8);
        if (!TextUtils.isEmpty(url)) {
            Glide.with(iv.getContext())
                    .load(url)
                    .transform(new CenterCrop(), new RoundedCorners(radius))
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .error(R.drawable.ic_launcher_foreground)
                    .into(iv);
        } else {
            Glide.with(iv.getContext())
                    .load(R.drawable.ic_launcher_foreground)
                    .transform(new CenterCrop(), new RoundedCorners(radius))
                    .into(iv);
        }
    }

    private boolean isHomeScorer(Map<String,Object> m) {
        String sTeamId = n((String) m.get("teamId"));
        if (!TextUtils.isEmpty(sTeamId) && !TextUtils.isEmpty(myTeamId)) {
            return sTeamId.equals(myTeamId);
        }
        String side = n((String) m.get("side"));
        if (!TextUtils.isEmpty(side)) return "home".equalsIgnoreCase(side);
        String teamSide = n((String) m.get("team"));
        if (!TextUtils.isEmpty(teamSide)) return "home".equalsIgnoreCase(teamSide);

        Object oIsHome = m.get("isHome");
        if (oIsHome instanceof Boolean) return (Boolean) oIsHome;
        Object oIsOpp = m.get("isOpponent");
        if (oIsOpp instanceof Boolean) return !((Boolean) oIsOpp);

        return true; // 알 수 없으면 홈
    }

    private List<ScorerEntry> toEntries(Map<String,Integer> map) {
        List<ScorerEntry> out = new ArrayList<>();
        for (Map.Entry<String,Integer> e : map.entrySet()) {
            out.add(new ScorerEntry(e.getKey(), e.getValue()));
        }
        out.sort((a,b) -> {
            int c = Integer.compare(b.goals, a.goals);
            return c != 0 ? c : a.name.compareToIgnoreCase(b.name);
        });
        return out;
    }

    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return Math.round(v * d);
    }

    private String safe(String s) { return TextUtils.isEmpty(s) ? "" : s; }
    private String n(String s){ return s==null? "": s; }

    private int safeInt(Object o) {
        if (o instanceof Number) return ((Number)o).intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return 0; }
    }

    private long safeLong(Object o) {
        if (o instanceof Number) return ((Number)o).longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return 0L; }
    }

    /** date("yyyy-MM-dd"), time("HH:mm" or "HH:mm:ss") → epoch millis 폴백 */
    private long computeTs(String date, String time) {
        String pattern = TextUtils.isEmpty(time) ? "yyyy-MM-dd" :
                (time.length() == 5 ? "yyyy-MM-dd HH:mm" : "yyyy-MM-dd HH:mm:ss");
        String val = TextUtils.isEmpty(time) ? date : (date + " " + time);
        try {
            return new SimpleDateFormat(pattern, Locale.getDefault()).parse(val).getTime();
        } catch (ParseException e) {
            return 0L;
        }
    }

    // =========================================================
    // 11) 어댑터/모델/내부클래스 (한 곳에 모아 정리)
    // =========================================================
    /** 팀원 */
    private static class Member {
        String uid, nickname, imageUrl;
        Member(String uid, String nickname, String imageUrl){
            this.uid=uid; this.nickname=nickname; this.imageUrl=imageUrl;
        }
    }

    /** 득점자 표시용 */
    private static class ScorerEntry {
        final String name;
        final int goals;
        ScorerEntry(String n, int g){ this.name=n; this.goals=g; }
    }

    /** 이전 시합 카드 아이템 */
    private static class RecordItem {
        final String teamName;
        final String teamLogoUrl;
        final String opponentName;
        final String opponentLogoUrl;
        final String date, time, stadium;
        final String stadiumAddress;
        final int scoreFor, scoreAgainst;
        final List<ScorerEntry> homeScorers, awayScorers;
        boolean expanded;
        final long sortTs;

        RecordItem(String teamName, String teamLogoUrl,
                   String opponentName, String opponentLogoUrl,
                   String date, String time, String stadium,
                   String stadiumAddress,
                   int scoreFor, int scoreAgainst,
                   List<ScorerEntry> homeScorers, List<ScorerEntry> awayScorers,
                   boolean expanded,
                   long sortTs) {
            this.teamName = teamName;
            this.teamLogoUrl = teamLogoUrl;
            this.opponentName = opponentName;
            this.opponentLogoUrl = opponentLogoUrl;
            this.date = date;
            this.time = time;
            this.stadium = stadium;
            this.stadiumAddress = stadiumAddress;
            this.scoreFor = scoreFor;
            this.scoreAgainst = scoreAgainst;
            this.homeScorers = homeScorers;
            this.awayScorers = awayScorers;
            this.expanded = expanded;
            this.sortTs = sortTs;
        }
    }

    /** 개인기록 한 줄 */
    private static class PlayerStat {
        String playerId;
        String nickname;
        String profileImageUrl;
        int games;
        int goals;
        int assists;
        int attackP() { return goals + assists; }

        PlayerStat(String playerId) { this.playerId = playerId; }
    }

    /** 이전 시합 RecyclerView 어댑터 */
    private class RecordsAdapter extends RecyclerView.Adapter<RecordsAdapter.VH> {
        private final List<RecordItem> data;
        RecordsAdapter(List<RecordItem> data) { this.data = data; }

        class VH extends RecyclerView.ViewHolder {
            ImageView imgHomeLogo, imgAwayLogo;
            TextView tvHomeName, tvAwayName, tvScoreFor, tvScoreAgainst;
            TextView tvResultChip, tvStadium;
            TextView tvDateChip;
            TextView tvStadiumAddress;

            View layoutToggleScorers;
            ImageView iconToggleArrow;
            View scorersSection;
            LinearLayout listHomeScorers, listAwayScorers;
            TextView tvHomeHeader, tvAwayHeader;

            VH(@NonNull View itemView) {
                super(itemView);
                imgHomeLogo    = itemView.findViewById(R.id.imgHomeLogo);
                imgAwayLogo    = itemView.findViewById(R.id.imgAwayLogo);
                tvHomeName     = itemView.findViewById(R.id.tvHomeName);
                tvAwayName     = itemView.findViewById(R.id.tvAwayName);
                tvScoreFor     = itemView.findViewById(R.id.tvScoreFor);
                tvScoreAgainst = itemView.findViewById(R.id.tvScoreAgainst);
                tvResultChip   = itemView.findViewById(R.id.tvResultChip);
                tvStadium      = itemView.findViewById(R.id.tvStadium);
                tvStadiumAddress = itemView.findViewById(R.id.tvStadiumAddress);
                tvDateChip     = itemView.findViewById(R.id.tvDateChip);
                layoutToggleScorers = itemView.findViewById(R.id.layoutToggleScorers);
                iconToggleArrow     = itemView.findViewById(R.id.iconToggleArrow);
                scorersSection      = itemView.findViewById(R.id.scorersSection);
                listHomeScorers     = itemView.findViewById(R.id.listHomeScorers);
                listAwayScorers     = itemView.findViewById(R.id.listAwayScorers);
                tvHomeHeader        = itemView.findViewById(R.id.tvHomeHeader);
                tvAwayHeader        = itemView.findViewById(R.id.tvAwayHeader);
            }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.record_item, parent, false);
            return new VH(v);
        }

        private String formatDateWithWeekday(String date) {
            if (TextUtils.isEmpty(date)) return "";
            try {
                SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
                SimpleDateFormat out = new SimpleDateFormat("yyyy.MM.dd (E)", Locale.KOREAN);
                return out.format(in.parse(date));
            } catch (Exception e) {
                return date;
            }
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            RecordItem it = data.get(position);

            String dateLabel = formatDateWithWeekday(it.date);
            String timeLabel = safe(it.time);
            h.tvDateChip.setText(TextUtils.isEmpty(timeLabel) ? dateLabel : (dateLabel + " · " + timeLabel));

            // 팀/스코어
            h.tvHomeName.setText(safe(it.teamName));
            h.tvAwayName.setText(safe(it.opponentName));
            h.tvScoreFor.setText(String.valueOf(it.scoreFor));
            h.tvScoreAgainst.setText(String.valueOf(it.scoreAgainst));
            setResultChip(h.tvResultChip, it.scoreFor, it.scoreAgainst);

            // 메타
            h.tvStadium.setText(safe(it.stadium));
            h.tvStadiumAddress.setText(safe(it.stadiumAddress));
            h.tvStadiumAddress.setVisibility(
                    TextUtils.isEmpty(it.stadiumAddress) ? View.GONE : View.VISIBLE
            );

            // 헤더(팀명)
            h.tvHomeHeader.setText(safe(it.teamName));
            h.tvAwayHeader.setText(safe(it.opponentName));

            // 로고
            if (!TextUtils.isEmpty(it.teamLogoUrl)) {
                Glide.with(h.imgHomeLogo.getContext()).load(it.teamLogoUrl)
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .error(R.drawable.ic_launcher_foreground)
                        .into(h.imgHomeLogo);
            } else h.imgHomeLogo.setImageResource(R.drawable.ic_launcher_foreground);

            if (!TextUtils.isEmpty(it.opponentLogoUrl)) {
                Glide.with(h.imgAwayLogo.getContext()).load(it.opponentLogoUrl)
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .error(R.drawable.ic_launcher_foreground)
                        .into(h.imgAwayLogo);
            } else h.imgAwayLogo.setImageResource(R.drawable.ic_launcher_foreground);

            // 펼침 상태
            h.scorersSection.setVisibility(it.expanded ? View.VISIBLE : View.GONE);
            h.iconToggleArrow.setImageResource(it.expanded ? R.drawable.ic_arrow_up : R.drawable.ic_arrow_down);

            // 득점자
            bindScorersList(h.listHomeScorers, it.homeScorers, /*isLeft*/ true);
            bindScorersList(h.listAwayScorers, it.awayScorers, /*isLeft*/ false);
            bindAwaySummary(h.listAwayScorers, it.scoreAgainst);

            // 토글
            h.layoutToggleScorers.setOnClickListener(v -> {
                it.expanded = !it.expanded;
                notifyItemChanged(h.getAdapterPosition());
            });
        }

        @Override public int getItemCount() { return data.size(); }

        private void bindAwaySummary(LinearLayout container, int opponentGoals) {
            if (container.getChildCount() > 0) return;

            LinearLayout row = new LinearLayout(container.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setPadding(0, dp(4), 0, dp(4));
            row.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);

            ImageView icon = new ImageView(container.getContext());
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(16), dp(16));
            icon.setLayoutParams(ip);
            icon.setImageResource(R.drawable.ic_soccer_ball);
            row.addView(icon);

            View s1 = new View(container.getContext());
            s1.setLayoutParams(new LinearLayout.LayoutParams(dp(6), 1));
            row.addView(s1);

            TextView tvGoals = new TextView(container.getContext());
            tvGoals.setText(opponentGoals + "골");
            tvGoals.setTextColor(0xFF424242);
            tvGoals.setTextSize(13);
            row.addView(tvGoals);

            View s2 = new View(container.getContext());
            s2.setLayoutParams(new LinearLayout.LayoutParams(dp(6), 1));
            row.addView(s2);

            TextView tvLabel = new TextView(container.getContext());
            tvLabel.setText("상대팀");
            tvLabel.setTextColor(0xFF111111);
            tvLabel.setTextSize(13);
            row.addView(tvLabel);

            container.addView(row);
        }

        private void bindScorersList(LinearLayout container, List<ScorerEntry> list, boolean isLeft) {
            container.removeAllViews();
            if (list == null || list.isEmpty()) return;
            for (ScorerEntry e : list) {
                container.addView(makeScorerRow(container, e, isLeft));
            }
        }

        private View makeScorerRow(ViewGroup parent, ScorerEntry e, boolean isLeft) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setPadding(0, dp(4), 0, dp(4));
            row.setGravity((isLeft ? android.view.Gravity.END : android.view.Gravity.START)
                    | android.view.Gravity.CENTER_VERTICAL);

            TextView name = new TextView(parent.getContext());
            name.setText(e.name);
            name.setTextColor(0xFF212121);
            name.setTextSize(13);
            row.addView(name);

            View s1 = new View(parent.getContext());
            s1.setLayoutParams(new LinearLayout.LayoutParams(dp(6), 1));
            row.addView(s1);

            TextView goals = new TextView(parent.getContext());
            goals.setText(e.goals + "골");
            goals.setTextColor(0xFF424242);
            goals.setTextSize(13);
            row.addView(goals);

            View s2 = new View(parent.getContext());
            s2.setLayoutParams(new LinearLayout.LayoutParams(dp(6), 1));
            row.addView(s2);

            ImageView icon = new ImageView(parent.getContext());
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(16), dp(16));
            icon.setLayoutParams(ip);
            icon.setImageResource(R.drawable.ic_soccer_ball);
            row.addView(icon);

            return row;
        }
    }

    /** 개인기록: 왼쪽 고정 열(순위/프로필/닉네임) */
    private static class FixedColumnAdapter extends RecyclerView.Adapter<FixedColumnAdapter.VH> {
        private final List<PlayerStat> items = new ArrayList<>();

        void submitList(List<PlayerStat> list) {
            items.clear();
            if (list != null) items.addAll(list);
            notifyDataSetChanged();
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvRank, tvNickname;
            ShapeableImageView imgProfile;
            VH(@NonNull View v) {
                super(v);
                tvRank     = v.findViewById(R.id.tvRank);
                imgProfile = v.findViewById(R.id.imgProfile);
                tvNickname = v.findViewById(R.id.tvNickname);
            }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.personal_record_item_left, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            PlayerStat p = items.get(pos);
            h.tvRank.setText((pos + 1) + ".");
            h.tvRank.setTextColor(0xFF424242);
            h.tvNickname.setText(TextUtils.isEmpty(p.nickname) ? "—" : p.nickname);

            if (!TextUtils.isEmpty(p.profileImageUrl)) {
                Glide.with(h.imgProfile.getContext()).load(p.profileImageUrl).centerCrop().into(h.imgProfile);
            } else {
                h.imgProfile.setImageDrawable(null);
            }
        }

        @Override public int getItemCount() { return items.size(); }
    }

    /** 개인기록: 오른쪽 스크롤 열(경기/득점/도움/공격P) */
    private static class MetricsAdapter extends RecyclerView.Adapter<MetricsAdapter.VH> {
        private final List<PlayerStat> items = new ArrayList<>();
        private SortKey activeKey = SortKey.GOALS;
        private SortDir activeDir = SortDir.DESC;

        void submitList(List<PlayerStat> list) {
            items.clear();
            if (list != null) items.addAll(list);
            notifyDataSetChanged();
        }

        void setActive(SortKey key, SortDir dir) {
            activeKey = key; activeDir = dir;
            notifyDataSetChanged();
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvGames, tvGoals, tvAssists, tvAttackP;
            VH(@NonNull View v) {
                super(v);
                tvGames   = v.findViewById(R.id.tvGames);
                tvGoals   = v.findViewById(R.id.tvGoals);
                tvAssists = v.findViewById(R.id.tvAssists);
                tvAttackP = v.findViewById(R.id.tvAttackP);
            }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.personal_record_item_right, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            PlayerStat p = items.get(pos);
            h.tvGames.setText(String.valueOf(p.games));
            h.tvGoals.setText(String.valueOf(p.goals));
            h.tvAssists.setText(String.valueOf(p.assists));
            h.tvAttackP.setText(String.valueOf(p.attackP()));

            // 기본 스타일 리셋
            int normal = 0xFF424242;
            h.tvGames.setTextColor(normal);
            h.tvGoals.setTextColor(normal);
            h.tvAssists.setTextColor(normal);
            h.tvAttackP.setTextColor(normal);

            h.tvGames.setTypeface(null, Typeface.NORMAL);
            h.tvGoals.setTypeface(null, Typeface.NORMAL);
            h.tvAssists.setTypeface(null, Typeface.NORMAL);
            h.tvAttackP.setTypeface(null, Typeface.NORMAL);

            // 활성 컬럼 하이라이트
            switch (activeKey) {
                case GAMES:    activate(h.tvGames);    break;
                case GOALS:    activate(h.tvGoals);    break;
                case ASSISTS:  activate(h.tvAssists);  break;
                case ATTACK_P: activate(h.tvAttackP);  break;
            }
        }

        private void activate(@NonNull TextView tv) {
            tv.setTextColor(tv.getContext().getColor(R.color.goal_for)); // 파랑
            tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        }

        @Override public int getItemCount() { return items.size(); }
    }
    // 한글/영문 사전순
    private static final java.text.Collator KOREAN = java.text.Collator.getInstance(Locale.KOREAN);
    static { KOREAN.setStrength(java.text.Collator.PRIMARY); }

    // 엔트리 3칸으로 패딩
    private List<Map.Entry<String,Integer>> padTop3(List<Map.Entry<String,Integer>> src) {
        List<Map.Entry<String,Integer>> out = new ArrayList<>(src);
        while (out.size() < 3) out.add(new java.util.AbstractMap.SimpleEntry<>("", 0));
        if (out.size() > 3) out = out.subList(0, 3);
        return out;
    }

    // 공통 바인딩(값 없으면 플레이스홀더)
    // 슬롯 바인딩: 비어있으면 empty_player + "n등" 노출
    private void bindRankRow(ImageView img, TextView tvName, TextView tvCnt,
                             String name, int cnt, boolean isAssist, int rank) {

        // 패딩된 빈 슬롯은 name이 빈 문자열("")로 들어옴
        boolean isEmptySlot = TextUtils.isEmpty(name);

        String displayName = isEmptySlot ? (rank + "등") : name;
        tvName.setText(displayName);
        tvCnt.setText(cnt + (isAssist ? "도움" : "골"));

        int radius = dp(8);
        if (isEmptySlot) {
            // ✅ 기본 이미지
            Glide.with(img.getContext())
                    .load(R.drawable.empty_player)
                    .transform(new CenterCrop(), new RoundedCorners(radius))
                    .into(img);
        } else {
            // 닉네임으로 프로필 찾기 → 없으면 기본 이미지
            String url = nickToImage.get(displayName);
            if (!TextUtils.isEmpty(url)) {
                Glide.with(img.getContext())
                        .load(url)
                        .transform(new CenterCrop(), new RoundedCorners(radius))
                        .placeholder(R.drawable.empty_player)
                        .error(R.drawable.empty_player)
                        .into(img);
            } else {
                Glide.with(img.getContext())
                        .load(R.drawable.empty_player)
                        .transform(new CenterCrop(), new RoundedCorners(radius))
                        .into(img);
            }
        }
    }


}
