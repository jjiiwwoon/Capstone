// src/main/java/com/example/myapp/ApplicationsList.java
package com.example.myapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ApplicationsList extends AppCompatActivity {

    private static final String TAG = "ApplicationsList";

    public static final String POST_MATCH = "match";
    public static final String POST_RECRUIT = "recruit";

    private TextView btnSubjectMine, btnSubjectApplied;
    private TextView chipTypeAll, chipTypeRecruit, chipTypeMatch;
    private RecyclerView recycler;
    private ApplicationsAdapter adapter;

    private boolean mineSelected = true;   // 내가 작성한 글(true) / 내가 신청한 글(false)
    private String typeFilter = "all";     // all | recruit | match

    private String currentUid = "";
    private String myTeamId = "";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // 이번 세션 동안 각 글에서 관측된 최대 신청 ts
    private final java.util.Map<String, Long> sessionMaxApplicantTs = new java.util.HashMap<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.applications_list);

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentUid = auth.getCurrentUser().getUid();

        // 뷰
        btnSubjectMine = findViewById(R.id.btnSubjectMine);
        btnSubjectApplied = findViewById(R.id.btnSubjectApplied);
        chipTypeAll = findViewById(R.id.chipTypeAll);
        chipTypeRecruit = findViewById(R.id.chipTypeRecruit);
        chipTypeMatch = findViewById(R.id.chipTypeMatch);
        recycler = findViewById(R.id.recyclerApplications);

        // 리사이클러
        if (recycler != null) {
            recycler.setLayoutManager(new LinearLayoutManager(this));
            adapter = new ApplicationsAdapter();
            recycler.setAdapter(adapter);

            // 카드 클릭/수락/거절/채팅 콜백
            adapter.setOnItemClickListener(new ApplicationsAdapter.OnItemClickListener() {
                @Override
                public void onPostClicked(@NonNull ApplicationsAdapter.Item item) {
                    if (POST_RECRUIT.equalsIgnoreCase(item.postType)) {
                        Intent i = new Intent(ApplicationsList.this, RecruitDetail.class);
                        i.putExtra("recruitId", item.postId);
                        startActivity(i);
                    } else {
                        Intent i = new Intent(ApplicationsList.this, MatchDetail.class);
                        i.putExtra("matchId", item.postId);
                        startActivity(i);
                    }
                }

                @Override
                public void onApplicantAccept(@NonNull ApplicationsAdapter.Item post,
                                              @NonNull ApplicationsAdapter.Applicant applicant) {
                    if (POST_RECRUIT.equalsIgnoreCase(post.postType)) {
                        // 모집쪽은 채팅만 사용(필요 시 유지 가능)
                        acceptRecruitApplicant(post, applicant);
                    } else {
                        acceptApplicant(post, applicant);
                    }
                }

                @Override
                public void onApplicantReject(@NonNull ApplicationsAdapter.Item post,
                                              @NonNull ApplicationsAdapter.Applicant applicant) {
                    if (POST_RECRUIT.equalsIgnoreCase(post.postType)) {
                        // 모집쪽은 채팅만 사용(필요 시 유지 가능)
                        rejectRecruitApplicant(post, applicant);
                    } else {
                        rejectApplicant(post, applicant);
                    }
                }

                @Override
                public void onApplicantChat(@NonNull ApplicationsAdapter.Item post,
                                            @NonNull ApplicationsAdapter.Applicant applicant) {
                    // ✅ 모집 신청자 1:1 채팅 열기
                    openChatWithApplicant(applicant);
                }
            });
        }

        // 클릭
        btnSubjectMine.setOnClickListener(v -> { mineSelected = true; setSubjectSelected(true); loadData(); });
        btnSubjectApplied.setOnClickListener(v -> { mineSelected = false; setSubjectSelected(false); loadData(); });
        chipTypeAll.setOnClickListener(v -> { typeFilter = "all"; setTypeSelected("all"); loadData(); });
        chipTypeRecruit.setOnClickListener(v -> { typeFilter = "recruit"; setTypeSelected("recruit"); loadData(); });
        chipTypeMatch.setOnClickListener(v -> { typeFilter = "match"; setTypeSelected("match"); loadData(); });

        // 내 팀 ID 로드 → 초기 데이터
        loadMyTeamIdThenInit();
    }

    private void loadMyTeamIdThenInit() {
        db.collection("profiles").document(currentUid).get()
                .addOnSuccessListener(p -> {
                    myTeamId = safe(p.getString("myTeam"));
                    Log.d(TAG, "myTeamId = " + myTeamId);
                    setSubjectSelected(true);
                    setTypeSelected("all");
                    loadData();
                })
                .addOnFailureListener(e -> {
                    logAndToast("프로필을 불러올 수 없습니다: " + e.getMessage(), e);
                });
    }

    private void setSubjectSelected(boolean mine) {
        if (mine) {
            btnSubjectMine.setTextColor(0xFF2196F3);
            btnSubjectMine.setBackgroundColor(0xFFFFFFFF);
            btnSubjectApplied.setTextColor(0xFF666666);
            btnSubjectApplied.setBackgroundColor(0xFFE9F0FF);
        } else {
            btnSubjectApplied.setTextColor(0xFF2196F3);
            btnSubjectApplied.setBackgroundColor(0xFFFFFFFF);
            btnSubjectMine.setTextColor(0xFF666666);
            btnSubjectMine.setBackgroundColor(0xFFE9F0FF);
        }
    }

    private void setTypeSelected(String t) {
        chipTypeAll.setTextColor(0xFF666666);
        chipTypeAll.setBackgroundColor(0xFFFFFFFF);
        chipTypeRecruit.setTextColor(0xFF666666);
        chipTypeRecruit.setBackgroundColor(0xFFFFFFFF);
        chipTypeMatch.setTextColor(0xFF666666);
        chipTypeMatch.setBackgroundColor(0xFFFFFFFF);

        if ("all".equals(t)) chipTypeAll.setTextColor(0xFF2196F3);
        else if ("recruit".equals(t)) chipTypeRecruit.setTextColor(0xFF2196F3);
        else chipTypeMatch.setTextColor(0xFF2196F3);
    }

    /** 전체 탭 로직: 매치+모집 병합 */
    private void loadData() {
        if (adapter == null) return;

        if (mineSelected) {
            if ("match".equals(typeFilter)) {
                loadMineMatches();
            } else if ("recruit".equals(typeFilter)) {
                loadMineRecruits();
            } else { // all
                loadMineAll();
            }
        } else {
            if ("match".equals(typeFilter)) {
                loadAppliedMatches();
            } else if ("recruit".equals(typeFilter)) {
                loadAppliedRecruits();
            } else { // all
                loadAppliedAll();
            }
        }
    }

    /** 내가 작성한 글: matches where teamId == myTeamId  (✅ 신청자 로고 폴백 로드 포함) */
    private void loadMineMatches() {
        if (isEmpty(myTeamId)) {
            Log.w(TAG, "loadMineMatches: myTeamId is empty");
            adapter.setItems(java.util.Collections.emptyList(), ApplicationsAdapter.TYPE_MINE);
            return;
        }

        db.collection("matches")
                .whereEqualTo("teamId", myTeamId)
                .orderBy("matchTs", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(sn -> {
                    if (sn.isEmpty()) {
                        adapter.setItems(java.util.Collections.emptyList(), ApplicationsAdapter.TYPE_MINE);
                        return;
                    }
                    java.util.List<ApplicationsAdapter.Item> list = new java.util.ArrayList<>();
                    java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger(sn.size());

                    for (DocumentSnapshot m : sn.getDocuments()) {
                        ApplicationsAdapter.Item it = new ApplicationsAdapter.Item();
                        it.postId = m.getId();
                        it.postType = POST_MATCH;
                        it.teamName = safe(m.getString("teamName"));
                        it.teamLogoUrl = firstNonEmpty(m.getString("teamLogoUrl"), m.getString("logoUrl"));
                        it.date = safe(m.getString("date"));
                        it.time = safe(m.getString("time"));
                        it.stadium = firstNonEmpty(m.getString("stadiumAddress"), m.getString("address"));
                        it.skill = safeInt(m.getLong("skill"), -1);
                        it.matchTs   = safeLong(m.getLong("matchTs"), 0L);
                        it.timestamp = safeLong(m.getLong("timestamp"), 0L);

                        db.collection("matches").document(m.getId())
                                .collection("applicants")
                                .orderBy("timestamp", Query.Direction.DESCENDING)
                                .get()
                                .addOnSuccessListener(appSn -> {
                                    java.util.List<ApplicationsAdapter.Applicant> applicants = new java.util.ArrayList<>();
                                    java.util.List<Task<DocumentSnapshot>> teamGets = new java.util.ArrayList<>();

                                    for (DocumentSnapshot a : appSn.getDocuments()) {
                                        ApplicationsAdapter.Applicant ap = new ApplicationsAdapter.Applicant();
                                        ap.applicantDocId  = a.getId();
                                        ap.teamId          = safe(a.getString("teamId"));
                                        ap.teamName        = safe(a.getString("teamName"));
                                        ap.nickname        = safe(a.getString("nickname"));
                                        ap.skill           = safeInt(a.getLong("skill"), -1);
                                        ap.logoUrl         = safe(a.getString("teamLogoUrl"));
                                        ap.applicantUserId = safe(a.getString("userId"));
                                        ap.status          = mapStatus(safe(a.getString("status")));

                                        // 🔴 변경: 타임스탬프를 ms로 정규화
                                        ap.timestamp       = toMillis(safeLong(a.getLong("timestamp"), 0L));

                                        applicants.add(ap);

                                        if (isEmpty(ap.logoUrl) && !isEmpty(ap.teamId)) {
                                            Task<DocumentSnapshot> t = db.collection("teams")
                                                    .document(ap.teamId)
                                                    .get()
                                                    .addOnSuccessListener(td -> {
                                                        if (td != null && td.exists()) {
                                                            String lu = safe(td.getString("logoUrl"));
                                                            if (!isEmpty(lu)) ap.logoUrl = lu;
                                                            if (isEmpty(ap.teamName)) ap.teamName = safe(td.getString("teamName"));
                                                        }
                                                    });
                                            teamGets.add(t);
                                        }
                                    }

                                    Task<?> waitTeams = teamGets.isEmpty()
                                            ? Tasks.forResult(null)
                                            : Tasks.whenAllComplete(teamGets);

                                    waitTeams.addOnCompleteListener(xx -> {
                                        it.applicants = applicants;
                                        list.add(it);
                                        if (pending.decrementAndGet() == 0) {
                                            list.sort((x, y) -> Long.compare(y.matchTs, x.matchTs));
                                            // ✅ 세션 NEW 계산 → 어댑터 주입
                                            computeSessionBadgesAndApply(list);
                                            adapter.setItems(list, ApplicationsAdapter.TYPE_MINE);
                                        }
                                    });
                                })
                                .addOnFailureListener(e -> {
                                    logAndToast("신청자 조회 실패: " + e.getMessage(), e);
                                    list.add(it);
                                    if (pending.decrementAndGet() == 0) {
                                        list.sort((x, y) -> Long.compare(y.matchTs, x.matchTs));
                                        computeSessionBadgesAndApply(list); // ✅
                                        adapter.setItems(list, ApplicationsAdapter.TYPE_MINE);
                                    }
                                });

                    }
                })
                .addOnFailureListener(e -> {
                    logAndToast("내가 작성한 글 목록 조회 실패: " + e.getMessage(), e);
                    adapter.setItems(java.util.Collections.emptyList(), ApplicationsAdapter.TYPE_MINE);
                });
    }


    /** 내가 작성한 선수모집글: recruitPosts where teamId == myTeamId (작성시각 보강 + 초/ms 정규화) */
    private void loadMineRecruits() {
        if (isEmpty(myTeamId)) {
            adapter.setItems(java.util.Collections.emptyList(), ApplicationsAdapter.TYPE_MINE);
            return;
        }

        db.collection("recruitPosts")
                .whereEqualTo("teamId", myTeamId)
                .orderBy("postTs", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(sn -> {
                    if (sn.isEmpty()) {
                        adapter.setItems(java.util.Collections.emptyList(), ApplicationsAdapter.TYPE_MINE);
                        return;
                    }
                    java.util.List<ApplicationsAdapter.Item> list = new java.util.ArrayList<>();
                    java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger(sn.size());

                    for (DocumentSnapshot m : sn.getDocuments()) {
                        ApplicationsAdapter.Item it = new ApplicationsAdapter.Item();
                        it.postId = m.getId();
                        it.postType = POST_RECRUIT;
                        it.teamName = safe(m.getString("teamName"));
                        it.teamLogoUrl = firstNonEmpty(m.getString("teamLogoUrl"), m.getString("logoUrl"));
                        it.date = safe(m.getString("date"));
                        it.time = safe(m.getString("time"));
                        it.stadium = firstNonEmpty(m.getString("stadiumAddress"), m.getString("address"));
                        it.recruitType = safe(m.getString("recruitType"));
                        it.skillMin = m.getLong("skillMin") == null ? null : m.getLong("skillMin").intValue();
                        it.skillMax = m.getLong("skillMax") == null ? null : m.getLong("skillMax").intValue();
                        it.positions = (java.util.List<String>) m.get("positions");

                        long postTs = toMillis(safeLong(m.getLong("postTs"), 0L));
                        if (postTs == 0L) {
                            long t = toMillis(safeLong(m.getLong("timestamp"), 0L));
                            if (t == 0L) {
                                com.google.firebase.Timestamp ct = m.getTimestamp("createdAt");
                                if (ct != null) t = ct.toDate().getTime();
                            }
                            postTs = t;
                        }
                        it.matchTs = postTs;
                        it.timestamp = createdTsOf(m);

                        db.collection("recruitPosts").document(m.getId())
                                .collection("applicants")
                                .orderBy("timestamp", Query.Direction.DESCENDING)
                                .get()
                                .addOnSuccessListener(appSn -> {
                                    java.util.List<ApplicationsAdapter.Applicant> applicants = new java.util.ArrayList<>();
                                    java.util.List<Task<DocumentSnapshot>> profGets = new java.util.ArrayList<>();

                                    for (DocumentSnapshot a : appSn.getDocuments()) {
                                        ApplicationsAdapter.Applicant ap = new ApplicationsAdapter.Applicant();
                                        ap.applicantDocId  = a.getId();
                                        ap.teamId          = safe(a.getString("teamId"));
                                        ap.teamName        = safe(a.getString("teamName"));
                                        ap.nickname        = safe(a.getString("nickname"));
                                        ap.skill           = safeInt(a.getLong("skill"), -1);
                                        ap.logoUrl         = safe(a.getString("teamLogoUrl"));
                                        ap.applicantUserId = safe(a.getString("userId"));
                                        ap.status          = mapStatus(safe(a.getString("status")));

                                        // 🔴 변경: 타임스탬프를 ms로 정규화
                                        ap.timestamp       = toMillis(safeLong(a.getLong("timestamp"), 0L));

                                        applicants.add(ap);

                                        if (!isEmpty(ap.applicantUserId)) {
                                            Task<DocumentSnapshot> t = db.collection("profiles")
                                                    .document(ap.applicantUserId)
                                                    .get()
                                                    .addOnSuccessListener(p -> {
                                                        if (p != null && p.exists()) {
                                                            ap.profileImageUrl = safe(p.getString("profileImageUrl"));
                                                            ap.position        = safe(p.getString("position"));
                                                            if (isEmpty(ap.nickname)) ap.nickname = safe(p.getString("nickname"));
                                                            if (ap.skill < 0) ap.skill = safeInt(p.getLong("skill"), -1);
                                                        }
                                                    });
                                            profGets.add(t);
                                        }
                                    }

                                    (profGets.isEmpty() ? Tasks.forResult(null) : Tasks.whenAllComplete(profGets))
                                            .addOnCompleteListener(xx -> {
                                                it.applicants = applicants;
                                                list.add(it);
                                                if (pending.decrementAndGet() == 0) {
                                                    list.sort((x, y) -> Long.compare(y.matchTs, x.matchTs));
                                                    // ✅ 세션 NEW 계산 → 어댑터 주입
                                                    computeSessionBadgesAndApply(list);
                                                    adapter.setItems(list, ApplicationsAdapter.TYPE_MINE);
                                                }
                                            });
                                })
                                .addOnFailureListener(e -> {
                                    it.applicants = new java.util.ArrayList<>();
                                    list.add(it);
                                    if (pending.decrementAndGet() == 0) {
                                        list.sort((x, y) -> Long.compare(y.matchTs, x.matchTs));
                                        computeSessionBadgesAndApply(list); // ✅
                                        adapter.setItems(list, ApplicationsAdapter.TYPE_MINE);
                                    }
                                });

                    }
                })
                .addOnFailureListener(e -> {
                    logAndToast("내가 작성한 선수모집 글 조회 실패: " + e.getMessage(), e);
                    adapter.setItems(java.util.Collections.emptyList(), ApplicationsAdapter.TYPE_MINE);
                });
    }

    /** 초/밀리초 혼용 정규화: 초(<=2e9)면 ms로 변환 */
    private static long toMillis(long v) {
        if (v <= 0L) return 0L;
        return (v > 2_000_000_000L) ? v : v * 1000L;
    }


    /** 내가 신청한 매치글 */
    private void loadAppliedMatches() {
        if (adapter == null) return;

        Map<String, ApplicationsAdapter.Item> dedup = new LinkedHashMap<>();
        List<Task<DocumentSnapshot>> matchGets = new ArrayList<>();

        Task<QuerySnapshot> tUser = db.collectionGroup("applicants")
                .whereEqualTo("userId", currentUid)
                .get()
                .addOnSuccessListener(qs -> collectApplicantsIntoFiltered(qs, dedup, matchGets, "matches"))
                .addOnFailureListener(e -> Log.w(TAG, "userId 쿼리 실패: " + e.getMessage()));

        Task<QuerySnapshot> tTeam = null;
        if (!isEmpty(myTeamId)) {
            tTeam = db.collectionGroup("applicants")
                    .whereEqualTo("teamId", myTeamId)
                    .get()
                    .addOnSuccessListener(qs -> collectApplicantsIntoFiltered(qs, dedup, matchGets, "matches"))
                    .addOnFailureListener(e -> Log.w(TAG, "teamId 쿼리 실패: " + e.getMessage()));
        }

        List<Task<?>> waits = new ArrayList<>();
        waits.add(tUser);
        if (tTeam != null) waits.add(tTeam);

        Tasks.whenAllComplete(waits)
                .addOnSuccessListener(done -> {
                    if (matchGets.isEmpty()) {
                        adapter.setItems(Collections.emptyList(), ApplicationsAdapter.TYPE_APPLIED);
                        Toast.makeText(this, "신청한 글이 없습니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Tasks.whenAllSuccess(matchGets)
                            .addOnSuccessListener(ms -> {
                                for (Object o : ms) {
                                    DocumentSnapshot m = (DocumentSnapshot) o;
                                    if (!m.exists()) continue;

                                    ApplicationsAdapter.Item it = dedup.get(m.getId());
                                    if (it == null) continue;

                                    it.postType = POST_MATCH;
                                    it.teamName = safe(m.getString("teamName"));
                                    it.teamLogoUrl = firstNonEmpty(m.getString("teamLogoUrl"), m.getString("logoUrl"));
                                    it.date = safe(m.getString("date"));
                                    it.time = safe(m.getString("time"));
                                    it.stadium = firstNonEmpty(m.getString("stadiumAddress"), m.getString("address"));
                                    it.skill = safeInt(m.getLong("skill"), -1);

                                    it.matchTs = safeLong(m.getLong("matchTs"), 0L);

                                    // ✅ 작성 시각 보강
                                    long ts = safeLong(m.getLong("timestamp"), 0L);
                                    if (ts == 0L) ts = safeLong(m.getLong("postTs"), 0L);
                                    if (ts == 0L) {
                                        com.google.firebase.Timestamp ct = m.getTimestamp("createdAt");
                                        if (ct != null) ts = ct.toDate().getTime();
                                    }
                                    it.timestamp = ts;
                                }

                                List<ApplicationsAdapter.Item> list = new ArrayList<>(dedup.values());
                                list.sort((a, b) -> Long.compare(b.matchTs, a.matchTs));
                                adapter.setItems(list, ApplicationsAdapter.TYPE_APPLIED);
                            })
                            .addOnFailureListener(e -> {
                                logAndToast("매치 본문 조회 실패: " + e.getMessage(), e);
                                adapter.setItems(Collections.emptyList(), ApplicationsAdapter.TYPE_APPLIED);
                            });
                })
                .addOnFailureListener(e -> {
                    logAndToast("신청 목록 조회 실패: " + e.getMessage(), e);
                    adapter.setItems(Collections.emptyList(), ApplicationsAdapter.TYPE_APPLIED);
                });
    }

    /** 내가 신청한 선수모집글 */
    private void loadAppliedRecruits() {
        if (adapter == null) return;

        Map<String, ApplicationsAdapter.Item> dedup = new LinkedHashMap<>();
        List<Task<DocumentSnapshot>> postGets = new ArrayList<>();

        Task<QuerySnapshot> tUser = db.collectionGroup("applicants")
                .whereEqualTo("userId", currentUid)
                .get()
                .addOnSuccessListener(qs -> collectApplicantsIntoFiltered(qs, dedup, postGets, "recruitPosts"))
                .addOnFailureListener(e -> Log.w(TAG, "userId 쿼리 실패: " + e.getMessage()));

        Task<QuerySnapshot> tTeam = null;
        if (!isEmpty(myTeamId)) {
            tTeam = db.collectionGroup("applicants")
                    .whereEqualTo("teamId", myTeamId)
                    .get()
                    .addOnSuccessListener(qs -> collectApplicantsIntoFiltered(qs, dedup, postGets, "recruitPosts"))
                    .addOnFailureListener(e -> Log.w(TAG, "teamId 쿼리 실패: " + e.getMessage()));
        }

        List<Task<?>> waits = new ArrayList<>();
        waits.add(tUser);
        if (tTeam != null) waits.add(tTeam);

        Tasks.whenAllComplete(waits)
                .addOnSuccessListener(done -> {
                    if (postGets.isEmpty()) {
                        adapter.setItems(Collections.emptyList(), ApplicationsAdapter.TYPE_APPLIED);
                        Toast.makeText(this, "신청한 글이 없습니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Tasks.whenAllSuccess(postGets)
                            .addOnSuccessListener(ms -> {
                                for (Object o : ms) {
                                    DocumentSnapshot m = (DocumentSnapshot) o;
                                    if (!m.exists()) continue;

                                    ApplicationsAdapter.Item it = dedup.get(m.getId());
                                    if (it == null) continue;

                                    it.postType = POST_RECRUIT;
                                    it.teamName = safe(m.getString("teamName"));
                                    it.teamLogoUrl = firstNonEmpty(m.getString("teamLogoUrl"), m.getString("logoUrl"));
                                    it.date = safe(m.getString("date"));
                                    it.time = safe(m.getString("time"));
                                    it.stadium = firstNonEmpty(m.getString("stadiumAddress"), m.getString("address"));

                                    // 정렬 기준(상단 정렬용)
                                    it.matchTs = safeLong(m.getLong("postTs"), 0L);

                                    // ✅ 표시용(작성 후 시간): 작성시각으로 통일
                                    it.timestamp = createdTsOf(m);

                                    it.recruitType = safe(m.getString("recruitType"));
                                    it.skillMin = m.getLong("skillMin") == null ? null : m.getLong("skillMin").intValue();
                                    it.skillMax = m.getLong("skillMax") == null ? null : m.getLong("skillMax").intValue();
                                    it.positions = (List<String>) m.get("positions");
                                }

                                List<ApplicationsAdapter.Item> list = new ArrayList<>(dedup.values());
                                list.sort((a, b) -> Long.compare(b.matchTs, a.matchTs));
                                adapter.setItems(list, ApplicationsAdapter.TYPE_APPLIED);
                            })
                            .addOnFailureListener(e -> {
                                logAndToast("모집 본문 조회 실패: " + e.getMessage(), e);
                                adapter.setItems(Collections.emptyList(), ApplicationsAdapter.TYPE_APPLIED);
                            });
                })
                .addOnFailureListener(e -> {
                    logAndToast("신청 목록 조회 실패: " + e.getMessage(), e);
                    adapter.setItems(Collections.emptyList(), ApplicationsAdapter.TYPE_APPLIED);
                });
    }



    /** applicants 스냅샷에서 부모가 targetCollection인 것만 모아 상위 문서 fetch를 쌓는다. */
    private void collectApplicantsIntoFiltered(QuerySnapshot qs,
                                               Map<String, ApplicationsAdapter.Item> dedup,
                                               List<Task<DocumentSnapshot>> parentGets,
                                               String targetCollection) {
        for (DocumentSnapshot a : qs.getDocuments()) {
            DocumentReference parent = a.getReference().getParent().getParent();
            if (parent == null) continue;
            if (!targetCollection.equals(parent.getParent().getId())) continue;

            String parentId = parent.getId();
            String status = mapStatus(safe(a.getString("status"))); // 없으면 pending

            ApplicationsAdapter.Item exist = dedup.get(parentId);
            if (exist == null) {
                ApplicationsAdapter.Item it = new ApplicationsAdapter.Item();
                it.postId = parentId;
                it.status = status;
                dedup.put(parentId, it);
                parentGets.add(parent.get());
            } else {
                if ("accepted".equals(status)) exist.status = "accepted";
                else if (exist.status == null || "pending".equals(exist.status)) exist.status = status;
            }
        }
    }

    private String mapStatus(String s) {
        if (s == null) return "pending";
        s = s.toUpperCase(Locale.ROOT);
        if (s.startsWith("ACC")) return "accepted";
        if (s.startsWith("REJ")) return "rejected";
        return "pending";
    }

    // -------------------- 전체 병합: 내 글 --------------------
    // -------------------- 전체 병합: 내 글 --------------------
    private void loadMineAll() {
        Task<List<ApplicationsAdapter.Item>> tMatches = collectMineMatches();
        Task<List<ApplicationsAdapter.Item>> tRecruits = collectMineRecruits();

        Tasks.whenAllSuccess(tMatches, tRecruits)
                .addOnSuccessListener(res -> {
                    List<ApplicationsAdapter.Item> merged = new ArrayList<>();
                    merged.addAll(tMatches.getResult());
                    merged.addAll(tRecruits.getResult());
                    merged.sort((a, b) -> Long.compare(b.matchTs, a.matchTs));

                    // ✅ 세션 NEW 계산(행 NEW 뱃지) 추가
                    computeSessionBadgesAndApply(merged);

                    adapter.setItems(merged, ApplicationsAdapter.TYPE_MINE);
                })
                .addOnFailureListener(e -> {
                    logAndToast("전체(내 글) 병합 실패: " + e.getMessage(), e);
                    adapter.setItems(Collections.emptyList(), ApplicationsAdapter.TYPE_MINE);
                });
    }

    // -------------------- 전체 병합: 내가 신청한 글 --------------------
    private void loadAppliedAll() {
        Task<List<ApplicationsAdapter.Item>> tMatches = collectAppliedMatches();
        Task<List<ApplicationsAdapter.Item>> tRecruits = collectAppliedRecruits();

        Tasks.whenAllSuccess(tMatches, tRecruits)
                .addOnSuccessListener(res -> {
                    List<ApplicationsAdapter.Item> merged = new ArrayList<>();
                    merged.addAll(tMatches.getResult());
                    merged.addAll(tRecruits.getResult());
                    merged.sort((a, b) -> Long.compare(b.matchTs, a.matchTs));
                    adapter.setItems(merged, ApplicationsAdapter.TYPE_APPLIED);
                })
                .addOnFailureListener(e -> {
                    logAndToast("전체(신청 글) 병합 실패: " + e.getMessage(), e);
                    adapter.setItems(Collections.emptyList(), ApplicationsAdapter.TYPE_APPLIED);
                });
    }

    // -------------------- 수집용(내 글 - 매치)  ✅ 신청자 로고 폴백 포함 --------------------
    // -------------------- 수집용(내 글 - 매치)  ✅ 신청자 로고 폴백 + 타임스탬프 포함 --------------------
    private Task<List<ApplicationsAdapter.Item>> collectMineMatches() {
        TaskCompletionSource<List<ApplicationsAdapter.Item>> tcs = new TaskCompletionSource<>();
        if (isEmpty(myTeamId)) { tcs.setResult(Collections.emptyList()); return tcs.getTask(); }

        db.collection("matches")
                .whereEqualTo("teamId", myTeamId)
                .orderBy("matchTs", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(sn -> {
                    if (sn.isEmpty()) { tcs.setResult(Collections.emptyList()); return; }

                    List<ApplicationsAdapter.Item> list = new ArrayList<>();
                    AtomicInteger pending = new AtomicInteger(sn.size());

                    for (DocumentSnapshot m : sn.getDocuments()) {
                        ApplicationsAdapter.Item it = new ApplicationsAdapter.Item();
                        it.postId = m.getId();
                        it.postType = POST_MATCH;
                        it.teamName = safe(m.getString("teamName"));
                        it.teamLogoUrl = firstNonEmpty(m.getString("teamLogoUrl"), m.getString("logoUrl"));
                        it.date = safe(m.getString("date"));
                        it.time = safe(m.getString("time"));
                        it.stadium = firstNonEmpty(m.getString("stadiumAddress"), m.getString("address"));
                        it.skill = safeInt(m.getLong("skill"), -1);
                        it.matchTs   = safeLong(m.getLong("matchTs"), 0L);
                        it.timestamp = createdTsOf(m); // ✅ 작성시각 보강 통일

                        db.collection("matches").document(m.getId())
                                .collection("applicants")
                                .orderBy("timestamp", Query.Direction.DESCENDING)
                                .get()
                                .addOnSuccessListener(appSn -> {
                                    List<ApplicationsAdapter.Applicant> applicants = new ArrayList<>();
                                    List<Task<DocumentSnapshot>> teamGets = new ArrayList<>();

                                    for (DocumentSnapshot a : appSn.getDocuments()) {
                                        ApplicationsAdapter.Applicant ap = new ApplicationsAdapter.Applicant();
                                        ap.applicantDocId  = a.getId();
                                        ap.teamId          = safe(a.getString("teamId"));
                                        ap.teamName        = safe(a.getString("teamName"));
                                        ap.nickname        = safe(a.getString("nickname"));
                                        ap.skill           = safeInt(a.getLong("skill"), -1);
                                        ap.logoUrl         = safe(a.getString("teamLogoUrl"));
                                        ap.applicantUserId = safe(a.getString("userId"));
                                        ap.status          = mapStatus(safe(a.getString("status")));

                                        // ✅ 세션 NEW 계산용 타임스탬프(초/밀리초 혼용 대비)
                                        ap.timestamp       = toMillis(safeLong(a.getLong("timestamp"), 0L));

                                        applicants.add(ap);

                                        // ✅ 팀 로고 폴백
                                        if (isEmpty(ap.logoUrl) && !isEmpty(ap.teamId)) {
                                            Task<DocumentSnapshot> t = db.collection("teams")
                                                    .document(ap.teamId)
                                                    .get()
                                                    .addOnSuccessListener(td -> {
                                                        if (td != null && td.exists()) {
                                                            String lu = safe(td.getString("logoUrl"));
                                                            if (!isEmpty(lu)) ap.logoUrl = lu;
                                                            if (isEmpty(ap.teamName)) ap.teamName = safe(td.getString("teamName"));
                                                        }
                                                    });
                                            teamGets.add(t);
                                        }
                                    }

                                    (teamGets.isEmpty() ? Tasks.forResult(null) : Tasks.whenAllComplete(teamGets))
                                            .addOnCompleteListener(xx -> {
                                                it.applicants = applicants;
                                                list.add(it);
                                                if (pending.decrementAndGet() == 0) {
                                                    list.sort((x, y) -> Long.compare(y.matchTs, x.matchTs));
                                                    tcs.setResult(list);
                                                }
                                            });
                                })
                                .addOnFailureListener(e -> {
                                    Log.w(TAG, "신청자 조회 실패: " + e.getMessage());
                                    it.applicants = new ArrayList<>();
                                    list.add(it);
                                    if (pending.decrementAndGet() == 0) {
                                        list.sort((x, y) -> Long.compare(y.matchTs, x.matchTs));
                                        tcs.setResult(list);
                                    }
                                });
                    }
                })
                .addOnFailureListener(tcs::setException);

        return tcs.getTask();
    }

    private Task<List<ApplicationsAdapter.Item>> collectMineRecruits() {
        TaskCompletionSource<List<ApplicationsAdapter.Item>> tcs = new TaskCompletionSource<>();
        if (isEmpty(myTeamId)) { tcs.setResult(Collections.emptyList()); return tcs.getTask(); }

        db.collection("recruitPosts")
                .whereEqualTo("teamId", myTeamId)
                .orderBy("postTs", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(sn -> {
                    if (sn.isEmpty()) { tcs.setResult(Collections.emptyList()); return; }

                    List<ApplicationsAdapter.Item> list = new ArrayList<>();
                    AtomicInteger pending = new AtomicInteger(sn.size());

                    for (DocumentSnapshot m : sn.getDocuments()) {
                        ApplicationsAdapter.Item it = new ApplicationsAdapter.Item();
                        it.postId = m.getId();
                        it.postType = POST_RECRUIT;
                        it.teamName = safe(m.getString("teamName"));
                        it.teamLogoUrl = firstNonEmpty(m.getString("teamLogoUrl"), m.getString("logoUrl"));
                        it.date = safe(m.getString("date"));
                        it.time = safe(m.getString("time"));
                        it.stadium = firstNonEmpty(m.getString("stadiumAddress"), m.getString("address"));
                        it.recruitType = safe(m.getString("recruitType"));
                        it.skillMin = m.getLong("skillMin") == null ? null : m.getLong("skillMin").intValue();
                        it.skillMax = m.getLong("skillMax") == null ? null : m.getLong("skillMax").intValue();
                        it.positions = (List<String>) m.get("positions");

                        long postTs = toMillis(safeLong(m.getLong("postTs"), 0L));
                        if (postTs == 0L) {
                            long t = toMillis(safeLong(m.getLong("timestamp"), 0L));
                            if (t == 0L) {
                                com.google.firebase.Timestamp ct = m.getTimestamp("createdAt");
                                if (ct != null) t = ct.toDate().getTime();
                            }
                            postTs = t;
                        }
                        it.matchTs = postTs;

                        // ✅ 작성시각(표시용) 통일
                        it.timestamp = createdTsOf(m);

                        db.collection("recruitPosts").document(m.getId())
                                .collection("applicants")
                                .orderBy("timestamp", Query.Direction.DESCENDING)
                                .get()
                                .addOnSuccessListener(appSn -> {
                                    List<ApplicationsAdapter.Applicant> applicants = new ArrayList<>();
                                    List<Task<DocumentSnapshot>> profGets = new ArrayList<>();
                                    for (DocumentSnapshot a : appSn.getDocuments()) {
                                        ApplicationsAdapter.Applicant ap = new ApplicationsAdapter.Applicant();
                                        ap.applicantDocId  = a.getId();
                                        ap.teamId          = safe(a.getString("teamId"));
                                        ap.teamName        = safe(a.getString("teamName"));
                                        ap.nickname        = safe(a.getString("nickname"));
                                        ap.skill           = safeInt(a.getLong("skill"), -1);
                                        ap.logoUrl         = safe(a.getString("teamLogoUrl"));
                                        ap.applicantUserId = safe(a.getString("userId"));
                                        ap.status          = mapStatus(safe(a.getString("status")));

                                        // ✅ 세션 NEW 계산용 타임스탬프
                                        ap.timestamp       = toMillis(safeLong(a.getLong("timestamp"), 0L));

                                        applicants.add(ap);

                                        if (!isEmpty(ap.applicantUserId)) {
                                            Task<DocumentSnapshot> t = db.collection("profiles").document(ap.applicantUserId)
                                                    .get()
                                                    .addOnSuccessListener(p -> {
                                                        if (p != null && p.exists()) {
                                                            ap.profileImageUrl = safe(p.getString("profileImageUrl"));
                                                            ap.position        = safe(p.getString("position"));
                                                            if (isEmpty(ap.nickname)) ap.nickname = safe(p.getString("nickname"));
                                                            if (ap.skill < 0) ap.skill = safeInt(p.getLong("skill"), -1);
                                                        }
                                                    });
                                            profGets.add(t);
                                        }
                                    }

                                    (profGets.isEmpty() ? Tasks.forResult(null) : Tasks.whenAllComplete(profGets))
                                            .addOnCompleteListener(xx -> {
                                                it.applicants = applicants;
                                                list.add(it);
                                                if (pending.decrementAndGet() == 0) {
                                                    list.sort((x, y) -> Long.compare(y.matchTs, x.matchTs));
                                                    tcs.setResult(list);
                                                }
                                            });
                                })
                                .addOnFailureListener(e -> {
                                    it.applicants = new ArrayList<>();
                                    list.add(it);
                                    if (pending.decrementAndGet() == 0) {
                                        list.sort((x, y) -> Long.compare(y.matchTs, x.matchTs));
                                        tcs.setResult(list);
                                    }
                                });
                    }
                })
                .addOnFailureListener(tcs::setException);

        return tcs.getTask();
    }

    private Task<List<ApplicationsAdapter.Item>> collectAppliedMatches() {
        TaskCompletionSource<List<ApplicationsAdapter.Item>> tcs = new TaskCompletionSource<>();
        Map<String, ApplicationsAdapter.Item> dedup = new LinkedHashMap<>();
        List<Task<DocumentSnapshot>> matchGets = new ArrayList<>();

        Task<QuerySnapshot> tUser = db.collectionGroup("applicants")
                .whereEqualTo("userId", currentUid)
                .get()
                .addOnSuccessListener(qs -> collectApplicantsIntoFiltered(qs, dedup, matchGets, "matches"))
                .addOnFailureListener(tcs::setException);

        Task<QuerySnapshot> tTeam = null;
        if (!isEmpty(myTeamId)) {
            tTeam = db.collectionGroup("applicants")
                    .whereEqualTo("teamId", myTeamId)
                    .get()
                    .addOnSuccessListener(qs -> collectApplicantsIntoFiltered(qs, dedup, matchGets, "matches"))
                    .addOnFailureListener(tcs::setException);
        }

        List<Task<?>> waits = new ArrayList<>();
        waits.add(tUser);
        if (tTeam != null) waits.add(tTeam);

        Tasks.whenAllComplete(waits)
                .addOnSuccessListener(done -> {
                    if (matchGets.isEmpty()) { tcs.setResult(Collections.emptyList()); return; }

                    Tasks.whenAllSuccess(matchGets)
                            .addOnSuccessListener(ms -> {
                                for (Object o : ms) {
                                    DocumentSnapshot m = (DocumentSnapshot) o;
                                    if (!m.exists()) continue;

                                    ApplicationsAdapter.Item it = dedup.get(m.getId());
                                    if (it == null) continue;

                                    it.postType = POST_MATCH;
                                    it.teamName = safe(m.getString("teamName"));
                                    it.teamLogoUrl = firstNonEmpty(m.getString("teamLogoUrl"), m.getString("logoUrl"));
                                    it.date = safe(m.getString("date"));
                                    it.time = safe(m.getString("time"));
                                    it.stadium = firstNonEmpty(m.getString("stadiumAddress"), m.getString("address"));
                                    it.skill = safeInt(m.getLong("skill"), -1);

                                    it.matchTs = safeLong(m.getLong("matchTs"), 0L);

                                    long ts = safeLong(m.getLong("timestamp"), 0L);
                                    if (ts == 0L) ts = safeLong(m.getLong("postTs"), 0L);
                                    if (ts == 0L) {
                                        com.google.firebase.Timestamp ct = m.getTimestamp("createdAt");
                                        if (ct != null) ts = ct.toDate().getTime();
                                    }
                                    it.timestamp = ts;
                                }
                                List<ApplicationsAdapter.Item> list = new ArrayList<>(dedup.values());
                                list.sort((a, b) -> Long.compare(b.matchTs, a.matchTs));
                                tcs.setResult(list);
                            })
                            .addOnFailureListener(tcs::setException);
                })
                .addOnFailureListener(tcs::setException);

        return tcs.getTask();
    }
    // ApplicationsList.java — collectAppliedRecruits() 만 교체
    private Task<List<ApplicationsAdapter.Item>> collectAppliedRecruits() {
        TaskCompletionSource<List<ApplicationsAdapter.Item>> tcs = new TaskCompletionSource<>();
        Map<String, ApplicationsAdapter.Item> dedup = new LinkedHashMap<>();
        List<Task<DocumentSnapshot>> postGets = new ArrayList<>();

        Task<QuerySnapshot> tUser = db.collectionGroup("applicants")
                .whereEqualTo("userId", currentUid)
                .get()
                .addOnSuccessListener(qs -> collectApplicantsIntoFiltered(qs, dedup, postGets, "recruitPosts"))
                .addOnFailureListener(tcs::setException);

        Task<QuerySnapshot> tTeam = null;
        if (!isEmpty(myTeamId)) {
            tTeam = db.collectionGroup("applicants")
                    .whereEqualTo("teamId", myTeamId)
                    .get()
                    .addOnSuccessListener(qs -> collectApplicantsIntoFiltered(qs, dedup, postGets, "recruitPosts"))
                    .addOnFailureListener(tcs::setException);
        }

        List<Task<?>> waits = new ArrayList<>();
        waits.add(tUser);
        if (tTeam != null) waits.add(tTeam);

        Tasks.whenAllComplete(waits)
                .addOnSuccessListener(done -> {
                    if (postGets.isEmpty()) { tcs.setResult(Collections.emptyList()); return; }

                    Tasks.whenAllSuccess(postGets)
                            .addOnSuccessListener(ms -> {
                                for (Object o : ms) {
                                    DocumentSnapshot m = (DocumentSnapshot) o;
                                    if (!m.exists()) continue;

                                    ApplicationsAdapter.Item it = dedup.get(m.getId());
                                    if (it == null) continue;

                                    it.postType = POST_RECRUIT;
                                    it.teamName = safe(m.getString("teamName"));
                                    it.teamLogoUrl = firstNonEmpty(m.getString("teamLogoUrl"), m.getString("logoUrl"));
                                    it.date = safe(m.getString("date"));
                                    it.time = safe(m.getString("time"));
                                    it.stadium = firstNonEmpty(m.getString("stadiumAddress"), m.getString("address"));

                                    // 정렬 기준(화면 상단 정렬용)
                                    it.matchTs = safeLong(m.getLong("postTs"), 0L);

                                    // ✅ 작성시각 보강(매치와 동일 로직)
                                    long ts = safeLong(m.getLong("timestamp"), 0L);
                                    if (ts == 0L) ts = safeLong(m.getLong("postTs"), 0L);
                                    if (ts == 0L) {
                                        com.google.firebase.Timestamp ct = m.getTimestamp("createdAt");
                                        if (ct != null) ts = ct.toDate().getTime();
                                    }
                                    it.timestamp = ts;  // ← 여기 빠져있어서 "방금 전"이 뜸

                                    it.recruitType = safe(m.getString("recruitType"));
                                    it.skillMin = m.getLong("skillMin") == null ? null : m.getLong("skillMin").intValue();
                                    it.skillMax = m.getLong("skillMax") == null ? null : m.getLong("skillMax").intValue();
                                    it.positions = (List<String>) m.get("positions");
                                }
                                List<ApplicationsAdapter.Item> list = new ArrayList<>(dedup.values());
                                list.sort((a, b) -> Long.compare(b.matchTs, a.matchTs));
                                tcs.setResult(list);
                            })
                            .addOnFailureListener(tcs::setException);
                })
                .addOnFailureListener(tcs::setException);

        return tcs.getTask();
    }



    // ---- 유틸 ----
    private static boolean isEmpty(String s){ return s==null || s.trim().isEmpty(); }
    private static String safe(String s){ return s==null?"":s; }
    private static int safeInt(Long l, int def){ return l==null?def:l.intValue(); }
    private static long safeLong(Long l, long def){ return l==null?def:l; }
    private static String firstNonEmpty(String a, String b){ return !isEmpty(a)?a: safe(b); }

    private void logAndToast(String msg, Throwable e) {
        Log.e(TAG, msg, e);
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    // ✅ 매치 신청 수락
    // ✅ 매치 신청 수락
    private void acceptApplicant(@NonNull ApplicationsAdapter.Item post,
                                 @NonNull ApplicationsAdapter.Applicant applicant) {
        if (isEmpty(post.postId) || isEmpty(applicant.applicantDocId) || isEmpty(applicant.teamId)) {
            Toast.makeText(this, "신청 데이터가 올바르지 않습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        DocumentReference matchRef = db.collection("matches").document(post.postId);
        DocumentReference applicantRef = matchRef.collection("applicants").document(applicant.applicantDocId);

        db.runTransaction(tr -> {
            DocumentSnapshot matchSnap = tr.get(matchRef);
            if (!matchSnap.exists()) throw new IllegalStateException("매치 문서를 찾을 수 없습니다.");
            String status = safe(matchSnap.getString("status"));
            if ("confirmed".equalsIgnoreCase(status)) throw new IllegalStateException("이미 다른 팀으로 확정된 매치입니다.");

            DocumentSnapshot apSnap = tr.get(applicantRef);
            if (!apSnap.exists()) throw new IllegalStateException("신청 문서를 찾을 수 없습니다.");
            String apStatus = safe(apSnap.getString("status"));
            if ("accepted".equalsIgnoreCase(apStatus) || "rejected".equalsIgnoreCase(apStatus))
                throw new IllegalStateException("이미 처리된 신청입니다.");

            String oppTeamId = safe(apSnap.getString("teamId"));
            String oppTeamName = safe(apSnap.getString("teamName"));
            String oppLogo = safe(apSnap.getString("teamLogoUrl"));

            Map<String, Object> apUpdate = new java.util.LinkedHashMap<>();
            apUpdate.put("status", "accepted");
            apUpdate.put("updatedAt", FieldValue.serverTimestamp());
            tr.update(applicantRef, apUpdate);

            Map<String, Object> matchUpdate = new java.util.LinkedHashMap<>();
            matchUpdate.put("status", "confirmed");
            matchUpdate.put("opponentTeamId", oppTeamId);
            matchUpdate.put("opponentName", oppTeamName);
            matchUpdate.put("opponentLogoUrl", oppLogo);
            matchUpdate.put("updatedAt", FieldValue.serverTimestamp());
            tr.update(matchRef, matchUpdate);

            return null;
        }).addOnSuccessListener(v -> {
            Tasks.whenAllSuccess(
                    ensureChatAndNotifyAccepted(post, applicant),   // 주장 ↔ 주장
                    createSchedulesForBothTeams(post, applicant)    // 양 팀 일정 등록
            ).addOnSuccessListener(done -> {
                // ✅ 내가 수락한 팀은 바로 UI 반영
                CustomToast.success(this, safe(applicant.teamName) + "의 시합신청을 수락하였습니다.");
                if (adapter != null) {
                    adapter.updateApplicantStatus(post.postId, applicant.applicantDocId, "accepted");
                }

                // ✅ 여기서 같은 매치의 나머지 신청들을 전부 자동 거절
                autoRejectOtherApplicants(post, applicant)
                        .addOnFailureListener(e -> Log.w(TAG, "자동 거절 중 일부 실패: " + e.getMessage()));
            }).addOnFailureListener(e -> {
                logAndToast("수락 후 후속 처리 실패: " + e.getMessage(), e);
                loadData(); // 보정용 재로딩
            });
        }).addOnFailureListener(e -> logAndToast(e.getMessage(), e));
    }

    /**
     * 어떤 매치에서 한 신청을 수락하면,
     * 같은 매치에 달려있던 나머지 신청들은 전부 자동으로 거절 처리 + 거절 메시지 전송
     */
    private Task<Void> autoRejectOtherApplicants(@NonNull ApplicationsAdapter.Item post,
                                                 @NonNull ApplicationsAdapter.Applicant accepted) {
        if (isEmpty(post.postId)) {
            return Tasks.forResult(null);
        }

        // 해당 매치의 모든 신청 문서를 가져온다.
        return db.collection("matches")
                .document(post.postId)
                .collection("applicants")
                .get()
                .onSuccessTask(snap -> {
                    List<Task<?>> tasks = new ArrayList<>();

                    for (DocumentSnapshot d : snap.getDocuments()) {
                        String docId = d.getId();
                        // 내가 방금 수락한 신청은 건너뜀
                        if (docId.equals(accepted.applicantDocId)) continue;

                        String status = mapStatus(safe(d.getString("status")));
                        // 이미 처리된 신청은 건너뜀
                        if ("accepted".equals(status) || "rejected".equals(status)) continue;

                        // 거절 업데이트
                        DocumentReference ar = d.getReference();
                        Map<String, Object> up = new LinkedHashMap<>();
                        up.put("status", "rejected");
                        up.put("updatedAt", FieldValue.serverTimestamp());
                        Task<Void> updateTask = ar.update(up);

                        // 채팅으로도 거절 안내 보내야 하니, applicant 객체 하나 만들어서 재사용
                        ApplicationsAdapter.Applicant otherAp = new ApplicationsAdapter.Applicant();
                        otherAp.applicantDocId  = docId;
                        otherAp.teamId          = safe(d.getString("teamId"));
                        otherAp.teamName        = safe(d.getString("teamName"));
                        otherAp.logoUrl         = safe(d.getString("teamLogoUrl"));
                        otherAp.applicantUserId = safe(d.getString("userId"));
                        otherAp.status          = "rejected";

                        // 업데이트 → 메시지 순서로 묶어줌
                        Task<Void> chain = updateTask.onSuccessTask(v ->
                                ensureChatAndNotifyRejected(post, otherAp)
                        );

                        tasks.add(chain);

                        // UI도 있으면 바로바로 반영
                        if (adapter != null) {
                            adapter.updateApplicantStatus(post.postId, docId, "rejected");
                        }
                    }

                    if (tasks.isEmpty()) {
                        return Tasks.forResult(null);
                    } else {
                        return Tasks.whenAll(tasks);
                    }
                });
    }


    // ✅ 매치 신청 거절
    private void rejectApplicant(@NonNull ApplicationsAdapter.Item post,
                                 @NonNull ApplicationsAdapter.Applicant applicant) {
        if (isEmpty(post.postId) || isEmpty(applicant.applicantDocId)) {
            Toast.makeText(this, "신청 데이터가 올바르지 않습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        DocumentReference matchRef = db.collection("matches").document(post.postId);
        DocumentReference applicantRef = matchRef.collection("applicants").document(applicant.applicantDocId);

        Map<String, Object> apUpdate = new java.util.LinkedHashMap<>();
        apUpdate.put("status", "rejected");
        apUpdate.put("updatedAt", FieldValue.serverTimestamp());

        applicantRef.update(apUpdate)
                .addOnSuccessListener(x -> {
                    ensureChatAndNotifyRejected(post, applicant)  // 주장 ↔ 주장
                            .addOnSuccessListener(done -> {
                                // ✅ CustomToast + 즉시 UI 반영
                                CustomToast.warning(this, safe(applicant.teamName) + "의 시합신청을 거절하였습니다.");
                                if (adapter != null) {
                                    adapter.updateApplicantStatus(post.postId, applicant.applicantDocId, "rejected");
                                }
                            })
                            .addOnFailureListener(e -> {
                                logAndToast("거절 알림 실패: " + e.getMessage(), e);
                                loadData(); // 보정용
                            });
                })
                .addOnFailureListener(e -> logAndToast("거절 실패: " + e.getMessage(), e));
    }


    // ✅ 모집 신청 수락 (용병은 채팅방 이동 없음, 정식선수는 채팅 열기 유지)
//    + '기존 일정에 참석(용병)'만 반영 (새 이벤트 생성 금지)
    private void acceptRecruitApplicant(@NonNull ApplicationsAdapter.Item post,
                                        @NonNull ApplicationsAdapter.Applicant applicant) {
        if (isEmpty(post.postId) || isEmpty(applicant.applicantDocId)) {
            Toast.makeText(this, "신청 데이터가 올바르지 않습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        DocumentReference postRef = db.collection("recruitPosts").document(post.postId);
        DocumentReference applicantRef = postRef.collection("applicants").document(applicant.applicantDocId);

        db.runTransaction(tr -> {
            DocumentSnapshot apSnap = tr.get(applicantRef);
            if (!apSnap.exists()) throw new IllegalStateException("신청 문서를 찾을 수 없습니다.");
            String apStatus = safe(apSnap.getString("status"));
            if ("accepted".equalsIgnoreCase(apStatus) || "rejected".equalsIgnoreCase(apStatus))
                throw new IllegalStateException("이미 처리된 신청입니다.");

            Map<String, Object> apUpdate = new java.util.LinkedHashMap<>();
            apUpdate.put("status", "accepted");
            apUpdate.put("updatedAt", FieldValue.serverTimestamp());
            tr.update(applicantRef, apUpdate);
            return null;
        }).addOnSuccessListener(v -> {
            ensureRecruitChatAccepted(post, applicant)
                    .addOnSuccessListener(done -> {
                        // 기존 이벤트에 '용병 참석'만 반영 (없으면 실패로 알려만 줌)
                        addMercenaryAttendanceToExistingEvent(post, applicant)
                                .addOnSuccessListener(x -> {
                                    if (isMercenary(post)) {
                                        CustomToast.success(this,
                                                safe(applicant.nickname) + "의 용병신청을 수락했습니다. (기존 일정에 참석 반영)");
                                    } else {
                                        openChatWithApplicant(applicant);
                                        CustomToast.success(this,
                                                "수락되었습니다. 채팅으로 안내했고 대화방을 열었어요.");
                                    }
                                    // ✅ 즉시 UI 반영
                                    if (adapter != null) {
                                        adapter.updateApplicantStatus(post.postId, applicant.applicantDocId, "accepted");
                                    }
                                })
                                .addOnFailureListener(err -> {
                                    CustomToast.warning(this,
                                            "수락은 완료했지만 기존 일정 매칭을 못했어요.\n" +
                                                    "일정 날짜/시간을 확인해 주세요: " + safe(err.getMessage()));
                                    if (adapter != null) {
                                        adapter.updateApplicantStatus(post.postId, applicant.applicantDocId, "accepted");
                                    }
                                });
                    })
                    .addOnFailureListener(e -> {
                        logAndToast("수락 알림 실패: " + e.getMessage(), e);
                        loadData(); // 보정용
                    });
        }).addOnFailureListener(e -> logAndToast(e.getMessage(), e));
    }


    /** 용병 수락 시: 새 이벤트 생성 없이, 기존 이벤트를 찾아 '참석(용병)'으로만 반영 */
    private Task<Void> addMercenaryAttendanceToExistingEvent(
            @NonNull ApplicationsAdapter.Item post,
            @NonNull ApplicationsAdapter.Applicant applicant) {

        if (isEmpty(myTeamId)) {
            return Tasks.forException(new IllegalStateException("팀 정보가 없습니다."));
        }

        return resolveTargetEventId(myTeamId, post).onSuccessTask(eventId -> {
            if (isEmpty(eventId)) {
                // ❌ 새 카드 생성 금지: 여기서 실패로 돌려 사용자에게만 토스트
                return Tasks.forException(new IllegalStateException("해당 날짜/시간의 기존 일정을 찾지 못했습니다."));
            }

            DocumentReference evRef = db.collection("schedules").document(myTeamId)
                    .collection("events").document(eventId);

            // ⛳ 후보 풀에 포함해두면 목록 일관성↑ (있으면 유지, 없어도 무해)
            Task<Void> tPool = evRef.update("mercenaryCandidateIds",
                    FieldValue.arrayUnion(safe(applicant.applicantUserId)));

            // ✅ attendances/{uid}는 '투표 없이 참석' + 용병 칩
            Map<String, Object> att = new java.util.LinkedHashMap<>();
            att.put("status",          "attend");
            att.put("isMercenary",     true);
            att.put("updatedAt",       System.currentTimeMillis());
            if (!isEmpty(applicant.nickname))        att.put("nickname", applicant.nickname);
            if (!isEmpty(applicant.profileImageUrl)) att.put("profileImageUrl", applicant.profileImageUrl);

            Task<Void> tAttend = evRef.collection("attendances")
                    .document(safe(applicant.applicantUserId))
                    .set(att, SetOptions.merge());

            return Tasks.whenAll(tPool, tAttend);
        });
    }

    /** 기존 이벤트 식별: 날짜/시간/장소를 이용해 '기존 카드'의 eventId를 탐색 */
    private Task<String> resolveTargetEventId(@NonNull String teamId,
                                              @NonNull ApplicationsAdapter.Item post) {
        // 1) 날짜는 필수 기준
        final String targetDate = safe(post.date);
        if (isEmpty(targetDate)) {
            return Tasks.forResult("");
        }

        // (선호 키) recruitPost에 eventId나 matchId가 실려 있으면 그대로 우선 사용
        // - 필요시: Item에 eventId/matchId 필드가 들어오면 아래 주석 해제해서 우선 반환
        // if (!isEmpty(post.eventId)) return Tasks.forResult(post.eventId);

        return db.collection("schedules").document(teamId)
                .collection("events")
                .whereEqualTo("date", targetDate)
                .get()
                .onSuccessTask(qs -> {
                    // 동일 날짜 내에서 가장 '잘 맞는' 이벤트를 점수로 선정
                    // 기준: (시간 완전일치 +2), (장소명 일치 +1)
                    String bestId = "";
                    int bestScore = -1;

                    String targetTime   = safe(post.time);                // "HH:mm" 또는 비어있을 수 있음
                    String targetStadium= safe(post.stadium);             // stadiumName/Address 중 우리가 세팅해둔 값

                    for (QueryDocumentSnapshot d : qs) {
                        String eid   = d.getId();
                        String time  = safe(d.getString("time"));
                        String name  = safe(d.getString("stadiumName"));
                        String addr  = safe(d.getString("stadiumAddress"));
                        String title = safe(d.getString("title"));

                        int score = 0;
                        if (!isEmpty(targetTime) && targetTime.equals(time)) {
                            score += 2;
                        }
                        // stadiumName 또는 address 어느 한쪽이라도 텍스트가 같으면 +1
                        if (!isEmpty(targetStadium) &&
                                (targetStadium.equals(name) || targetStadium.equals(addr))) {
                            score += 1;
                        }


                        // 동일 점수면 문서 생성시각이 빠르거나 matchTs가 가까운 걸 선택해도 됨(필요시 확장)
                        if (score > bestScore) {
                            bestScore = score;
                            bestId = eid;
                        }
                    }

                    // 점수 0이라도 동일 날짜에서 하나 선택됐을 수 있음.
                    // 점수 0이고 여러 개라면 오동작 가능 → 이 경우엔 빈값을 반환해서 사용자에게 경고 토스트 유도
                    if (bestScore <= 0) {
                        // 날짜만 같고 매칭 신뢰도가 낮음 → 안전하게 실패 처리
                        return Tasks.forResult("");
                    } else {
                        return Tasks.forResult(bestId);
                    }
                });
    }

    // ✅ 모집 신청 거절 (용병은 채팅방 이동 없음, 정식선수는 채팅 열기 유지)
    private void rejectRecruitApplicant(@NonNull ApplicationsAdapter.Item post,
                                        @NonNull ApplicationsAdapter.Applicant applicant) {
        if (isEmpty(post.postId) || isEmpty(applicant.applicantDocId)) {
            Toast.makeText(this, "신청 데이터가 올바르지 않습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        DocumentReference postRef = db.collection("recruitPosts").document(post.postId);
        DocumentReference applicantRef = postRef.collection("applicants").document(applicant.applicantDocId);

        Map<String, Object> apUpdate = new java.util.LinkedHashMap<>();
        apUpdate.put("status", "rejected");
        apUpdate.put("updatedAt", FieldValue.serverTimestamp());

        applicantRef.update(apUpdate)
                .addOnSuccessListener(x -> {
                    ensureRecruitChatRejected(post, applicant)   // 안내 메시지 전송만
                            .addOnSuccessListener(done -> {
                                if (isMercenary(post)) {
                                    CustomToast.warning(this,
                                            safe(applicant.nickname) + "의 용병신청을 거절하였습니다.");
                                } else {
                                    openChatWithApplicant(applicant);
                                    CustomToast.warning(this,
                                            "거절 처리되었습니다. 채팅으로 안내했고 대화방을 열었어요.");
                                }
                                // ✅ 즉시 UI 반영
                                if (adapter != null) {
                                    adapter.updateApplicantStatus(post.postId, applicant.applicantDocId, "rejected");
                                }
                            })
                            .addOnFailureListener(e -> {
                                logAndToast("거절 알림 실패: " + e.getMessage(), e);
                                loadData(); // 보정용
                            });
                })
                .addOnFailureListener(e -> logAndToast("거절 실패: " + e.getMessage(), e));
    }

    private Task<DocumentSnapshot> fetchTeamDoc(String teamId) {
        return db.collection("teams").document(teamId).get();
    }

    /** 구버전 룸ID 규칙: "chat_" + 정렬(uidA, uidB) */
// ✅ 룸ID 규칙 통일: MyTeam과 동일(정렬 + 접두어 없음)
    private String buildRoomId(String uidA, String uidB) {
        if (uidA == null) uidA = "";
        if (uidB == null) uidB = "";
        String a = uidA.compareTo(uidB) < 0 ? uidA : uidB;
        String b = uidA.compareTo(uidB) < 0 ? uidB : uidA;
        return a + "_" + b;
    }


    /** 모집 신청자와 1:1 채팅방 보장 후 열기 */
    // (참고) 이미 1:1 채팅 열기 버튼은 올바르게 구현됨. 필요 시 그대로 사용.
    private void openChatWithApplicant(@NonNull ApplicationsAdapter.Applicant applicant) {
        String otherUid = safe(applicant.applicantUserId);
        if (isEmpty(otherUid)) {
            Toast.makeText(this, "신청자의 사용자 정보가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        String roomId = buildRoomId(currentUid, otherUid);
        long now = System.currentTimeMillis();

        DocumentReference roomRef = db.collection("chatRooms").document(roomId);
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("participants", Arrays.asList(currentUid, otherUid));
        base.put("lastMessage", "");
        base.put("lastTimestamp", now);

        roomRef.get()
                .onSuccessTask(snap -> snap.exists() ? roomRef.update("lastTimestamp", now) : roomRef.set(base))
                .addOnSuccessListener(v -> {
                    Intent i = new Intent(ApplicationsList.this, ChatRoom.class);
                    i.putExtra("roomId", roomId);
                    i.putExtra("otherUid", otherUid);
                    startActivity(i);
                })
                .addOnFailureListener(e -> logAndToast("채팅방 준비 실패: " + e.getMessage(), e));
    }


    /** 매치 수락 시: 주장 ↔ 주장 */
    // ✅ 매치 수락 알림: "나(currentUid) ↔ 신청자(applicantUserId)" 고정
    private Task<Void> ensureChatAndNotifyAccepted(ApplicationsAdapter.Item post,
                                                   ApplicationsAdapter.Applicant applicant) {

        String maybeOther = safe(applicant.applicantUserId);

        Task<String> otherUidTask = !isEmpty(maybeOther)
                ? Tasks.forResult(maybeOther)
                : fetchTeamDoc(applicant.teamId).onSuccessTask(doc ->
                Tasks.forResult(safe(doc.getString("captainUID"))));

        return otherUidTask.onSuccessTask(otherUid -> {
            if (isEmpty(otherUid)) otherUid = currentUid;
            String roomId = buildRoomId(currentUid, otherUid);

            // 팀 이름 확보 (우리팀=post.teamName; 상대팀=applicant.teamName)
            String myTeamName  = safe(post.teamName);
            String oppTeamName = safe(applicant.teamName);

            // 줄바꿈/구분 기호까지 포함한 "기본 문자열" (레거시 호환용)
            String fallbackText =
                    "✅ 매치가 수락되었습니다.\n" +
                            "• 경기일시: " + post.date + " " + post.time + "\n" +
                            "• 장소: " + post.stadium + "\n" +
                            "• 매치업: " + myTeamName + " vs " + oppTeamName;

            // ✨ 메타(구조화) — 채팅 화면에서 관점에 맞게 재조립용
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("type", "match_accept_notice");
            meta.put("teamAId",   myTeamId);
            meta.put("teamAName", myTeamName);
            meta.put("teamBId",   applicant.teamId);
            meta.put("teamBName", oppTeamName);
            meta.put("date",  post.date);
            meta.put("time",  post.time);
            meta.put("place", post.stadium); // stadiumAddress까지 원하면 같이 넣어도 OK

            return ensureRoomAndSendRichMessage(roomId, currentUid, otherUid, fallbackText, meta);
        });
    }

    private Task<Void> ensureRoomAndSendRichMessage(String roomId, String uidA, String uidB,
                                                    String fallbackText, Map<String, Object> meta) {
        DocumentReference roomRef = db.collection("chatRooms").document(roomId);
        long now = System.currentTimeMillis();

        Map<String, Object> base = new LinkedHashMap<>();
        base.put("participants", Arrays.asList(uidA, uidB));
        base.put("lastMessage", fallbackText);
        base.put("lastTimestamp", now);

        Task<Void> upsert = roomRef.get().onSuccessTask(snap -> {
            if (snap.exists()) {
                return roomRef.update("lastMessage", fallbackText, "lastTimestamp", now);
            } else {
                return roomRef.set(base);
            }
        });

        return upsert.onSuccessTask(v -> {
            Map<String,Object> msg = new LinkedHashMap<>();
            msg.put("senderId", currentUid);
            msg.put("content",  fallbackText);     // 레거시 호환
            msg.put("messageType", "text");        // 그대로 두되,
            msg.put("timestamp", now);
            msg.put("teamId", myTeamId);
            msg.put("responded", true);

            // ✨ 추가: 구조화 메타
            msg.put("meta", meta);

            return roomRef.collection("messages").add(msg).onSuccessTask(doc -> Tasks.forResult(null));
        });
    }

    // ✅ 매치 거절 알림: "❌ 신청한 시합을 거절하였습니다." 로 고정
    private Task<Void> ensureChatAndNotifyRejected(ApplicationsAdapter.Item post,
                                                   ApplicationsAdapter.Applicant applicant) {

        String maybeOther = safe(applicant.applicantUserId);

        Task<String> otherUidTask = !isEmpty(maybeOther)
                ? Tasks.forResult(maybeOther)
                : fetchTeamDoc(applicant.teamId).onSuccessTask(doc ->
                Tasks.forResult(safe(doc.getString("captainUID")))); // 최후 폴백

        return otherUidTask.onSuccessTask(otherUid -> {
            if (isEmpty(otherUid)) otherUid = currentUid;
            String roomId = buildRoomId(currentUid, otherUid);

            // 🔁 변경된 안내문
            String content = "❌ 신청한 시합을 거절하였습니다.";

            return ensureRoomAndSendMessage(roomId, currentUid, otherUid, content);
        });
    }


    // ✅ 모집 수락 안내문 (용병 전용 헤드 문구 적용)
    private Task<Void> ensureRecruitChatAccepted(ApplicationsAdapter.Item post,
                                                 ApplicationsAdapter.Applicant applicant) {

        String maybeOther = safe(applicant.applicantUserId);

        Task<String> otherUidTask = !isEmpty(maybeOther)
                ? Tasks.forResult(maybeOther)
                : fetchTeamDoc(applicant.teamId).onSuccessTask(doc ->
                Tasks.forResult(safe(doc.getString("captainUID")))); // 최후 폴백

        return otherUidTask.onSuccessTask(otherUid -> {
            if (isEmpty(otherUid)) otherUid = currentUid;
            String roomId = buildRoomId(currentUid, otherUid);

            // 🔁 변경: 용병이면 용병 전용 문구
            String head = isMercenary(post)
                    ? "✅ 용병신청이 수락되었습니다."
                    : "✅ 선수모집 신청이 수락되었습니다.";

            String content = head + "\n" +
                    "일시: " + post.date + " " + post.time + "\n" +
                    "장소: " + post.stadium + "\n" +
                    "확정 관련해서 채팅으로 조율해 주세요.";

            return ensureRoomAndSendMessage(roomId, currentUid, otherUid, content);
        });
    }


    private Task<Void> ensureRecruitChatRejected(ApplicationsAdapter.Item post,
                                                 ApplicationsAdapter.Applicant applicant) {

        String maybeOther = safe(applicant.applicantUserId);

        Task<String> otherUidTask = !isEmpty(maybeOther)
                ? Tasks.forResult(maybeOther)
                : fetchTeamDoc(applicant.teamId).onSuccessTask(doc ->
                Tasks.forResult(safe(doc.getString("captainUID")))); // 최후 폴백

        return otherUidTask.onSuccessTask(otherUid -> {
            if (isEmpty(otherUid)) otherUid = currentUid;
            String roomId = buildRoomId(currentUid, otherUid);

            // 🔁 변경: 용병 전용 거절 문구
            String content = isMercenary(post)
                    ? "❌ 신청한 용병모집을 거절하였습니다."
                    : "❌ 이번 선수모집 신청은 진행하지 않게 되었습니다. 다음에 함께해요!";

            return ensureRoomAndSendMessage(roomId, currentUid, otherUid, content);
        });
    }

    /**
     * 구버전 스키마:
     * - 컬렉션: chatRooms
     * - participants: [uidA, uidB]
     * - lastTimestamp: Long(숫자)만
     * - 메시지: messageType="text", timestamp=long
     * - lastRead 갱신은 채팅 화면(ChatRoom)에서 처리
     */
    private Task<Void> ensureRoomAndSendMessage(String roomId, String uidA, String uidB, String content) {
        DocumentReference roomRef = db.collection("chatRooms").document(roomId);
        long now = System.currentTimeMillis();

        Map<String, Object> base = new LinkedHashMap<>();
        base.put("participants", Arrays.asList(uidA, uidB));
        base.put("lastMessage", content);
        base.put("lastTimestamp", now);

        Task<Void> upsert = roomRef.get().onSuccessTask(snap -> {
            if (snap.exists()) {
                return roomRef.update("lastMessage", content, "lastTimestamp", now);
            } else {
                return roomRef.set(base);
            }
        });

        return upsert.onSuccessTask(v -> roomRef.collection("messages")
                .add(new LinkedHashMap<String, Object>() {{
                    put("senderId", currentUid);
                    put("content", content);
                    put("messageType", "text");
                    put("timestamp", now);

                    // ✅ 추가
                    put("teamId", myTeamId);           // 이 메시지를 보낸 ‘나의 팀’ ID
                    put("responded", true);            // 수락/거절 안내는 추가 응답 불필요 → true
                }})
                .onSuccessTask(doc -> Tasks.forResult(null)));
    }

    // -------------------- 일정 생성 --------------------

    /** 매치 수락 후 양쪽 팀 일정에 등록 */
    private Task<Void> createSchedulesForBothTeams(ApplicationsAdapter.Item post,
                                                   ApplicationsAdapter.Applicant applicant) {
        // 기본 검증
        if (isEmpty(post.postId) || isEmpty(myTeamId) || isEmpty(applicant.teamId)) {
            return Tasks.forResult(null);
        }

        // 매치 본문 먼저 가져옴
        DocumentReference matchRef = db.collection("matches").document(post.postId);
        Task<DocumentSnapshot> matchGet = matchRef.get();

        return matchGet.continueWithTask(task -> {
            DocumentSnapshot m = task.getResult();
            if (m == null || !m.exists()) return Tasks.forResult(null);

            // 글 작성팀(=우리팀) 기준 정보
            String myName = safe(m.getString("teamName"));
            String myLogo = firstNonEmpty(m.getString("teamLogoUrl"), m.getString("logoUrl"));

            // 신청팀(=상대팀) 기준 정보
            String oppId   = applicant.teamId;
            String oppName = applicant.teamName;
            String oppLogo = applicant.logoUrl;

            // 공통(경기 정보)
            String date = safe(m.getString("date"));
            String time = safe(m.getString("time"));
            long matchTs = safeLong(m.getLong("matchTs"), 0L);
            String stadiumName    = firstNonEmpty(m.getString("stadiumName"), m.getString("stadium"));
            String stadiumAddress = firstNonEmpty(m.getString("stadiumAddress"), m.getString("address"));

            // ✅ 이 매치글의 ‘작성팀’을 owner로 고정
            String ownerId = myTeamId;

            // ===== 우리팀 일정 문서 =====
            Map<String, Object> evMine = new LinkedHashMap<>();
            evMine.put("date", date);
            evMine.put("time", time);
            evMine.put("matchId", post.postId);
            evMine.put("matchTs", matchTs);
            evMine.put("opponentTeamId", oppId);
            evMine.put("opponentTeamName", oppName);
            evMine.put("opponentLogoUrl", oppLogo);
            evMine.put("stadiumName", stadiumName);
            evMine.put("stadiumAddress", stadiumAddress);
            evMine.put("status", "confirmed");
            evMine.put("title", myName + " vs " + oppName);
            // 🔴 점수 입력 권한용
            evMine.put("ownerTeamId", ownerId);

            // ===== 상대팀 일정 문서 =====
            Map<String, Object> evOpp = new LinkedHashMap<>();
            evOpp.put("date", date);
            evOpp.put("time", time);
            evOpp.put("matchId", post.postId);
            evOpp.put("matchTs", matchTs);
            evOpp.put("opponentTeamId", myTeamId);
            evOpp.put("opponentTeamName", myName);
            evOpp.put("opponentLogoUrl", myLogo);
            evOpp.put("stadiumName", stadiumName);
            evOpp.put("stadiumAddress", stadiumAddress);
            evOpp.put("status", "confirmed");
            evOpp.put("title", oppName + " vs " + myName);
            // 🔴 상대팀 일정에도 같은 owner 넣어줌
            evOpp.put("ownerTeamId", ownerId);

            // 실제 쓰기
            Task<Void> t1 = db.collection("schedules").document(myTeamId)
                    .collection("events").document(post.postId)
                    .set(evMine, SetOptions.merge());

            Task<Void> t2 = db.collection("schedules").document(oppId)
                    .collection("events").document(post.postId)
                    .set(evOpp, SetOptions.merge());

            return Tasks.whenAll(t1, t2);
        });
    }
    // ---- 유틸 아래쪽에 추가 ----
// ---- 유틸 아래쪽에 추가 ----
    private long createdTsOf(DocumentSnapshot m) {
        long ts = safeLong(m.getLong("timestamp"), 0L); // 숫자 timestamp 우선
        if (ts == 0L) {
            com.google.firebase.Timestamp ct = m.getTimestamp("createdAt");
            if (ct != null) ts = ct.toDate().getTime(); // createdAt(Timestamp) 보조
        }
        if (ts == 0L) ts = safeLong(m.getLong("postTs"), 0L); // 과거 호환
        if (ts == 0L) ts = System.currentTimeMillis();
        return ts;
    }

    @Override
    protected void onPause() {
        super.onPause();
        persistLastSeenForThisSession(); // 돌아가기 직전에 확실히 저장
    }

    /** 세션 중 본 ‘각 게시글의 최대 신청 ts’를 SharedPreferences에 밀리초 단위로 저장 */
    private void persistLastSeenForThisSession() {
        if (sessionMaxApplicantTs.isEmpty()) return;

        android.content.SharedPreferences sp =
                getSharedPreferences("applicants_seen", MODE_PRIVATE);
        android.content.SharedPreferences.Editor ed = sp.edit();

        for (java.util.Map.Entry<String, Long> e : sessionMaxApplicantTs.entrySet()) {
            String prefKey = e.getKey();     // 이미 buildPostKey(...) 형태로 들어있음
            long tsMs = normalizeToMillis(e.getValue());
            ed.putLong(prefKey, tsMs);
        }
        ed.apply();
    }

    /** 초 단위가 섞일 위험 방지: 무조건 밀리초로 정규화 */
    private static long normalizeToMillis(Long v) {
        if (v == null || v <= 0) return 0L;
        return (v > 2_000_000_000L) ? v : v * 1000L; // 2e9 초 기준으로 보정
    }


    // SharedPreferences 저장 키(게시글 단위)
    private static String buildPostKey(String postType, String postId) {
        return "last_seen_applicant_ts_" + postType + "_" + postId;
    }

    // 어댑터 쪽과 동일 키 생성(행 단위)
    private static String buildApplicantKey(String postType, String postId, String applicantDocId) {
        String t = (postType == null) ? "" : postType.toLowerCase(java.util.Locale.ROOT);
        String p = (postId == null) ? "" : postId;
        String a = (applicantDocId == null) ? "" : applicantDocId;
        return t + ":" + p + ":" + a;
    }

    // 마지막 본 ts 읽기
    private long getLastSeenTs(String postType, String postId) {
        String k = buildPostKey(postType, postId);
        return getSharedPreferences("applicants_seen", MODE_PRIVATE).getLong(k, 0L);
    }

    // ✅ 세션 NEW 계산 후 어댑터에 주입 + 세션 중 최대 ts 누적
    private void computeSessionBadgesAndApply(@NonNull List<ApplicationsAdapter.Item> list) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        if (list.isEmpty()) {
            if (adapter != null) adapter.setSessionNewApplicantKeys(keys);
            return;
        }

        for (ApplicationsAdapter.Item it : list) {
            String postType = (it.postType == null) ? "" : it.postType.toLowerCase(java.util.Locale.ROOT);
            String postId   = it.postId == null ? "" : it.postId;

            long lastSeenTs = getLastSeenTs(postType, postId);
            long thisMaxTs  = lastSeenTs;
            boolean hasNew  = false; // ✅ 헤더 NEW 판단용

            if (it.applicants != null) {
                for (ApplicationsAdapter.Applicant a : it.applicants) {
                    long ts = (a.timestamp <= 0) ? 0 : a.timestamp;
                    if (ts > lastSeenTs) {
                        // 세션 동안 NEW로 보일 행
                        keys.add(buildApplicantKey(postType, postId, a.applicantDocId));
                        hasNew = true; // ✅ 헤더 NEW
                    }
                    if (ts > thisMaxTs) thisMaxTs = ts;
                }
            }

            // ✅ 글 단위 헤더 NEW 플래그 반영
            it.hasSessionNew = hasNew;

            // 세션 중 본 최대값 메모리에 누적 → onStop에서 일괄 저장
            if (thisMaxTs > 0) {
                String k = buildPostKey(postType, postId);
                sessionMaxApplicantTs.put(k, Math.max(sessionMaxApplicantTs.getOrDefault(k, 0L), thisMaxTs));
            }
        }

        if (adapter != null) adapter.setSessionNewApplicantKeys(keys);
    }

    // 파일 내 하단 유틸 근처에 추가
    private static boolean isMercenary(@Nullable ApplicationsAdapter.Item post) {
        if (post == null) return false;
        String rt = post.recruitType == null ? "" : post.recruitType.trim();
        // 데이터에 따라 "용병", "mercenary" 둘 다 대응
        return "용병".equals(rt) || "mercenary".equalsIgnoreCase(rt);
    }


}
