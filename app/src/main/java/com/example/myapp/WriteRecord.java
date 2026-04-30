// src/main/java/com/example/myapp/WriteRecord.java
package com.example.myapp;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.fragment.app.DialogFragment;

import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// ⚙️ 이벤트(골) 단위 UI에 맞춘 버전
public class WriteRecord extends DialogFragment {

    // ----- StateLayout -----
    private StateLayout state;

    // ----- 스코어보드 -----
    private ShapeableImageView imgHomeLogo, imgAwayLogo;
    private TextView tvHomeName, tvAwayName;
    private EditText editScoreFor, editScoreAgainst;

    // ----- 골 이벤트 UI (한 줄 = 1골) -----
    private LinearLayout goalEventListContainer;    // ⬅️ XML: goalEventListContainer
    private View btnAddGoalEvent;                   // ⬅️ XML: btnAddGoalEvent
    private View addEventRowWrap;                   // ⬅️ XML: addEventRowWrap (래퍼 전체 클릭)

    // ----- 스피너 데이터 -----
    private final List<String> playerNames = new ArrayList<>(); // 표시 닉네임
    private final List<String> playerUids  = new ArrayList<>(); // UID
    private final Set<String> attendeeUids = new HashSet<>();   // 참석자 UID
    private final Set<String> mercUids     = new HashSet<>();   // 용병 UID
    private PlayerSpinnerAdapter scorerAdapter;                 // 득점자용
    private AssistSpinnerAdapter assistAdapter;                 // 도움자용(0: 도움 없음)

    // ----- 저장 버튼 -----
    private View btnSaveRecord;

    // ----- 데이터 컨텍스트 -----
    private FirebaseFirestore db;
    private String myTeamId, eventId;
    // 🆕 추가
    private String ownerTeamId = "";   // 시합글을 쓴 팀
    private boolean isOwner = false;   // 내가 그 팀인지 여부

    // 🆕 오너팀이 기록한 스코어를 가져와서 B팀에 보여줄 때 사용
    private int ownerScoreFor = -1;        // 오너가 넣은 골
    private int ownerScoreAgainst = -1;    // 오너가 먹은 골
    private boolean ownerScoreLoaded = false; // B가 점수 저장 시, 오너 점수가 있는지 체크

    // 이벤트/팀 기본정보(저장용)
    private String myTeamName = "", myTeamLogo = "";
    private String opponentName = "", opponentLogoUrl = "";
    private String date = "", time = "", stadiumName = "", address = "";

    // ----- 기존 기록 로드/적용 상태 -----
    private boolean existingLoaded = false;
    private boolean existingApplied = false;
    private int existingScoreFor = -1;
    private int existingScoreAgainst = -1;

    // 🆕 기존 이벤트 기반 로드용
    private final List<GoalEventData> existingGoalEvents = new ArrayList<>();
    private boolean existingDocExists = false;
    private boolean existingRecorded = false; // finished || score || scorers || goalEvents

    // 참석자 로드 동기화
    private boolean membersFullyLoaded = false;
    private int pendingProfileChunks = 0;

    // 헤더 로드 여부
    private boolean headerLoaded = false;

    // ----- 모델 -----
    private static class GoalEventData {
        String scorerId;
        String scorerNickname;
        @Nullable String assistId;          // null = 도움 없음
        @Nullable String assistNickname;

        GoalEventData(String sId, String sNick, @Nullable String aId, @Nullable String aNick) {
            this.scorerId = sId;
            this.scorerNickname = sNick;
            this.assistId = aId;
            this.assistNickname = aNick;
        }
    }

    private String getMatchDocId() {
        // 경기 하나(eventId) + 내 팀 기준으로 문서 하나씩
        return eventId + "_" + myTeamId;
    }

    // 기존 호환(집계)용
    private static class ScorerAgg {
        String playerId;
        String nickname;
        int goals;
        ScorerAgg(String id, String nick, int g){ playerId=id; nickname=nick; goals=g; }
    }

    public static WriteRecord newInstance(String myTeamId, String eventId){
        WriteRecord f = new WriteRecord();
        Bundle b = new Bundle();
        b.putString("myTeamId", myTeamId);
        b.putString("eventId", eventId);
        f.setArguments(b);
        return f;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.write_record, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        Bundle args = getArguments();
        myTeamId = args != null ? args.getString("myTeamId") : "";
        eventId  = args != null ? args.getString("eventId")  : "";

        state = v.findViewById(R.id.stateLayout);
        if (state != null) state.showLoading();

        // ----- findView -----
        imgHomeLogo = v.findViewById(R.id.imgHomeLogo);
        imgAwayLogo = v.findViewById(R.id.imgAwayLogo);
        tvHomeName  = v.findViewById(R.id.tvHomeName);
        tvAwayName  = v.findViewById(R.id.tvAwayName);
        editScoreFor     = v.findViewById(R.id.editScoreFor);
        editScoreAgainst = v.findViewById(R.id.editScoreAgainst);
        btnSaveRecord    = v.findViewById(R.id.btnSaveRecord);

        goalEventListContainer = v.findViewById(R.id.goalEventListContainer);
        btnAddGoalEvent       = v.findViewById(R.id.btnAddGoalEvent);
        addEventRowWrap       = v.findViewById(R.id.addEventRowWrap);

        db = FirebaseFirestore.getInstance();

        // 어댑터 생성
        scorerAdapter = new PlayerSpinnerAdapter();
        assistAdapter = new AssistSpinnerAdapter(); // 0번: "도움 없음"
        applyAdaptersToAllRows();

        // 이벤트/팀/참석자 로드
        loadEventAndTeam();

        // 기존 기록 → 참석자 로드 후 UI 적용
        if (btnSaveRecord != null) btnSaveRecord.setEnabled(false);
        loadExistingRecord();

        // “+ 추가” - 이전 행의 득점자 선택값을 새 행에도 적용(스코어 자동 변경 없음)
        View.OnClickListener addListener = vv -> {
            String prevScorerUid = getLastSelectedScorerUid(); // 없으면 null 반환
            addGoalEventRow(prevScorerUid, null);              // 도움은 기본 "도움 없음"
            // ❌ 점수 자동 동기화 호출 없음
        };
        if (addEventRowWrap != null) addEventRowWrap.setOnClickListener(addListener);
        if (btnAddGoalEvent != null) btnAddGoalEvent.setOnClickListener(addListener);

        // 저장
        if (btnSaveRecord != null) btnSaveRecord.setOnClickListener(view -> saveRecord());
    }

    @Nullable
    private String getLastSelectedScorerUid() {
        if (!isUiReady() || goalEventListContainer == null) return null;
        int count = goalEventListContainer.getChildCount();
        if (count <= 0) return null;

        View lastRow = goalEventListContainer.getChildAt(count - 1);
        if (lastRow == null) return null;

        Spinner spSc = lastRow.findViewWithTag("scorer");
        if (spSc == null) return null;

        int pos = spSc.getSelectedItemPosition();
        // 득점자 어댑터는 0..N-1 == playerUids 1:1
        if (pos < 0 || pos >= playerUids.size()) return null;

        return playerUids.get(pos);
    }

    // ===== 공통 =====
    private boolean isUiReady() {
        return isAdded() && getView() != null && getContext() != null && !isRemoving();
    }
    private void maybeShowContent() {
        if (state == null) return;
        if (headerLoaded && membersFullyLoaded && existingLoaded) {
            state.showContent();
        }
    }

    // ----- 로고 로드 -----
    private void loadLogo(ImageView target, String urlOrPath) {
        if (!isUiReady()) return;

        if (TextUtils.isEmpty(urlOrPath)) {
            target.setImageResource(R.drawable.ic_launcher_foreground);
            return;
        }
        if (urlOrPath.startsWith("http")) {
            Glide.with(this)
                    .load(urlOrPath)
                    .centerCrop()
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .error(R.drawable.ic_launcher_foreground)
                    .into(target);
            return;
        }

        StorageReference ref = urlOrPath.startsWith("gs://")
                ? FirebaseStorage.getInstance().getReferenceFromUrl(urlOrPath)
                : FirebaseStorage.getInstance().getReference().child(urlOrPath);

        ref.getDownloadUrl()
                .addOnSuccessListener(uri -> {
                    if (!isUiReady()) return;
                    Glide.with(this)
                            .load(uri)
                            .centerCrop()
                            .placeholder(R.drawable.ic_launcher_foreground)
                            .error(R.drawable.ic_launcher_foreground)
                            .into(target);
                })
                .addOnFailureListener(e -> {
                    if (!isUiReady()) return;
                    target.setImageResource(R.drawable.ic_launcher_foreground);
                });
    }

    // WriteRecord.java 안
    private void loadEventAndTeam(){
        db.collection("schedules").document(myTeamId)
                .collection("events").document(eventId)
                .get()
                .addOnSuccessListener(ev -> {
                    // 1) 기본 필드들
                    opponentName    = n(ev.getString("opponentTeamName"));
                    String opponentTeamId = n(ev.getString("opponentTeamId"));
                    opponentLogoUrl = n(ev.getString("opponentLogoUrl"));
                    date            = n(ev.getString("date"));
                    time            = n(ev.getString("time"));
                    stadiumName     = n(ev.getString("stadiumName"));
                    address         = n(ev.getString("stadiumAddress"));

                    // 2) 이 시합글을 실제로 쓴 팀
                    ownerTeamId = n(ev.getString("ownerTeamId"));
                    isOwner = !TextUtils.isEmpty(ownerTeamId) && myTeamId.equals(ownerTeamId);

                    // 3) 상대팀 UI
                    if (isUiReady()) {
                        tvAwayName.setText(opponentName);
                        if (!TextUtils.isEmpty(opponentLogoUrl)) {
                            loadLogo(imgAwayLogo, opponentLogoUrl);
                        } else if (!TextUtils.isEmpty(opponentTeamId)) {
                            db.collection("teams").document(opponentTeamId).get()
                                    .addOnSuccessListener(t -> loadLogo(imgAwayLogo, n(t.getString("logoUrl"))))
                                    .addOnFailureListener(e -> imgAwayLogo.setImageResource(R.drawable.ic_launcher_foreground));
                        } else {
                            imgAwayLogo.setImageResource(R.drawable.ic_launcher_foreground);
                        }
                    }

                    // 4) 내 팀 정보
                    db.collection("teams").document(myTeamId).get()
                            .addOnSuccessListener(team -> {
                                myTeamName = n(team.getString("teamName"));
                                myTeamLogo = n(team.getString("logoUrl"));

                                if (isUiReady()) {
                                    tvHomeName.setText(myTeamName);
                                    loadLogo(imgHomeLogo, myTeamLogo);
                                }

                                // 참석자 로드
                                loadAttendees();
                                headerLoaded = true;

                                // ✅ 이 시점에 이미 기존 기록을 읽어둔 상태라면
                                // 오너인 경우 점수를 한 번 더 강제로 넣어준다.
                                if (existingLoaded && isUiReady() && isOwner) {
                                    if (existingScoreFor >= 0 && editScoreFor != null) {
                                        editScoreFor.setEnabled(true);
                                        editScoreFor.setText(String.valueOf(existingScoreFor));
                                    }
                                    if (existingScoreAgainst >= 0 && editScoreAgainst != null) {
                                        editScoreAgainst.setEnabled(true);
                                        editScoreAgainst.setText(String.valueOf(existingScoreAgainst));
                                    }
                                }

                                maybeShowContent();
                            })
                            .addOnFailureListener(e -> {
                                errorToast("팀 정보 불러오기 실패: " + e.getMessage());
                                showEmptyWithMessage("팀 정보를 불러오지 못했어요.\n잠시 후 다시 시도해 주세요.");
                            });

                    // 5) 나는 작성팀이 아니다 → 작성팀 점수만 보여주고 잠금
                    if (!isOwner && !TextUtils.isEmpty(ownerTeamId)) {
                        db.collection("matches")
                                .document(eventId + "_" + ownerTeamId)
                                .get()
                                .addOnSuccessListener(doc -> {
                                    if (doc != null && doc.exists()) {
                                        Long sf = safeLong(doc.get("scoreFor"));
                                        Long sa = safeLong(doc.get("scoreAgainst"));
                                        ownerScoreFor = sf != null ? sf.intValue() : -1;
                                        ownerScoreAgainst = sa != null ? sa.intValue() : -1;

                                        if (ownerScoreFor >= 0 && ownerScoreAgainst >= 0 && isUiReady()) {
                                            if (editScoreFor != null)
                                                editScoreFor.setText(String.valueOf(ownerScoreAgainst));
                                            if (editScoreAgainst != null)
                                                editScoreAgainst.setText(String.valueOf(ownerScoreFor));
                                            ownerScoreLoaded = true;
                                        }
                                    }
                                });

                        if (isUiReady()) {
                            lockScoreInputs();
                        }
                    }

                    // 6) ownerTeamId 자체가 없으면 안전하게 잠금
                    if (TextUtils.isEmpty(ownerTeamId)) {
                        if (isUiReady()) {
                            lockScoreInputs();
                        }
                    }

                })
                .addOnFailureListener(e -> {
                    errorToast("이벤트 불러오기 실패: " + e.getMessage());
                    showEmptyWithMessage("이벤트 정보를 불러오지 못했어요.\n잠시 후 다시 시도해 주세요.");
                });
    }
    private void lockScoreInputs() {
        if (editScoreFor != null) {
            editScoreFor.setEnabled(false);
            editScoreFor.setFocusable(false);
            editScoreFor.setFocusableInTouchMode(false);
            editScoreFor.setClickable(false);
            editScoreFor.setKeyListener(null);
        }
        if (editScoreAgainst != null) {
            editScoreAgainst.setEnabled(false);
            editScoreAgainst.setFocusable(false);
            editScoreAgainst.setFocusableInTouchMode(false);
            editScoreAgainst.setClickable(false);
            editScoreAgainst.setKeyListener(null);
        }
    }



    // ✅ 테스트 모드: 팀 멤버 전원을 참석자로 간주
    private void loadAttendees() {
        membersFullyLoaded = false;
        attendeeUids.clear();
        mercUids.clear();
        playerUids.clear();
        playerNames.clear();

        db.collection("teams").document(myTeamId)
                .get()
                .addOnSuccessListener(teamDoc -> {
                    List<String> members = (List<String>) teamDoc.get("members");
                    if (members == null) members = new ArrayList<>();
                    attendeeUids.addAll(members);

                    if (members.isEmpty()) {
                        playerUids.add("");
                        playerNames.add("선수 없음(팀에 멤버가 없습니다)");
                        onMembersFullyLoaded();
                        return;
                    }

                    // 프로필 whereIn 10개씩
                    List<List<String>> chunks = new ArrayList<>();
                    for (int i = 0; i < members.size(); i += 10) {
                        chunks.add(members.subList(i, Math.min(i + 10, members.size())));
                    }
                    pendingProfileChunks = chunks.size();

                    for (List<String> c : chunks) {
                        db.collection("profiles")
                                .whereIn(FieldPath.documentId(), c)
                                .get()
                                .addOnSuccessListener(ps -> {
                                    for (DocumentSnapshot p : ps.getDocuments()) {
                                        String uid = p.getId();
                                        String nick = n(p.getString("nickname"));
                                        if (TextUtils.isEmpty(nick)) nick = uid;
                                        playerUids.add(uid);
                                        playerNames.add(nick);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    for (String uid : c) {
                                        if (!playerUids.contains(uid)) {
                                            playerUids.add(uid);
                                            playerNames.add(uid);
                                        }
                                    }
                                })
                                .addOnCompleteListener(t -> {
                                    pendingProfileChunks--;
                                    if (pendingProfileChunks == 0) onMembersFullyLoaded();
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    errorToast("팀 멤버 로드 실패: " + e.getMessage());
                    showEmptyWithMessage("팀 멤버를 불러오지 못했어요.\n잠시 후 다시 시도해 주세요.");
                });
    }

    private void onMembersFullyLoaded() {
        if (!isUiReady()) return;
        if (scorerAdapter != null) scorerAdapter.notifyDataSetChanged();
        if (assistAdapter != null) assistAdapter.notifyDataSetChanged();
        applyAdaptersToAllRows();
        membersFullyLoaded = true;
        maybeApplyExistingRecord();
        maybeShowContent();
    }

    // ----- 어댑터 적용 -----
    private void applyAdaptersToAllRows() {
        if (!isUiReady() || goalEventListContainer == null) return;
        for (int i = 0; i < goalEventListContainer.getChildCount(); i++) {
            View row = goalEventListContainer.getChildAt(i);
            Spinner spScorer = row.findViewWithTag("scorer");
            Spinner spAssist = row.findViewWithTag("assist");
            if (spScorer != null) spScorer.setAdapter(scorerAdapter);
            if (spAssist != null) spAssist.setAdapter(assistAdapter);
        }
    }

    // ----- 행 추가/삭제 -----
    private void addGoalEventRow(@Nullable String scorerId, @Nullable String assistId) {
        if (!isUiReady()) return;

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = dp(10);
        row.setLayoutParams(rowLp);

        // ===== 득점자 =====
        LinearLayout scorerWrap = new LinearLayout(requireContext());
        scorerWrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams scorerWrapLp =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        scorerWrap.setLayoutParams(scorerWrapLp);

        Spinner spScorer = new Spinner(requireContext(), Spinner.MODE_DROPDOWN);
        LinearLayout.LayoutParams spScLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(34));
        spScorer.setLayoutParams(spScLp);
        spScorer.setPadding(dp(10), 0, dp(10), 0);
        spScorer.setBackground(requireContext().getDrawable(R.drawable.bg_score_box));
        spScorer.setTag("scorer");
        spScorer.setAdapter(scorerAdapter);

        scorerWrap.addView(spScorer);

        // 간격
        View spacer = new View(requireContext());
        spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(8), 1));

        // ===== 도움자 =====
        LinearLayout assistWrap = new LinearLayout(requireContext());
        assistWrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams assistWrapLp =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        assistWrap.setLayoutParams(assistWrapLp);

        Spinner spAssist = new Spinner(requireContext(), Spinner.MODE_DROPDOWN);
        LinearLayout.LayoutParams spAsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(34));
        spAssist.setLayoutParams(spAsLp);
        spAssist.setPadding(dp(10), 0, dp(10), 0);
        spAssist.setBackground(requireContext().getDrawable(R.drawable.bg_score_box));
        spAssist.setTag("assist");
        spAssist.setAdapter(assistAdapter);

        assistWrap.addView(spAssist);

        // ===== 삭제 버튼 =====
        AppCompatImageButton rm = new AppCompatImageButton(requireContext());
        LinearLayout.LayoutParams rmLp = new LinearLayout.LayoutParams(dp(18), dp(18));
        rmLp.setMarginStart(dp(6));
        rm.setLayoutParams(rmLp);
        rm.setBackground(null);
        rm.setPadding(0, 0, 0, 0);
        rm.setMinimumWidth(0);
        rm.setMinimumHeight(0);
        rm.setAdjustViewBounds(true);
        rm.setScaleType(ImageView.ScaleType.FIT_CENTER);
        rm.setImageDrawable(AppCompatResources.getDrawable(requireContext(), R.drawable.ic_close_thin));
        rm.setOnClickListener(v -> {
            removeEventRow(row);
            // ❌ 점수 자동 동기화 제거 (득점과 스피너는 독립)
        });

        // ===== 구성요소 추가 =====
        row.addView(scorerWrap);
        row.addView(spacer);
        row.addView(assistWrap);
        row.addView(rm);

        goalEventListContainer.addView(row);

        // ===== 이전 행의 득점자 선택값(prefill) 적용 =====
        if (!TextUtils.isEmpty(scorerId)) {
            int idx = playerUids.indexOf(scorerId);
            spScorer.setSelection(Math.max(idx, 0));
        }

        // ===== 도움은 기본 "도움 없음" =====
        if (!TextUtils.isEmpty(assistId)) {
            int idx = assistAdapter.indexOfUid(assistId);
            spAssist.setSelection(Math.max(idx, 0));
        } else {
            spAssist.setSelection(0);
        }
    }

    private void removeEventRow(View row) {
        if (!isUiReady()) return;
        if (goalEventListContainer.getChildCount() <= 1) {
            // 최소 1행 유지: 선택 초기화 (점수에는 영향 없음)
            View first = goalEventListContainer.getChildAt(0);
            if (first != null) {
                Spinner spSc = first.findViewWithTag("scorer");
                Spinner spAs = first.findViewWithTag("assist");
                if (spSc != null) spSc.setSelection(0);
                if (spAs != null) spAs.setSelection(0);
            }
            return;
        }
        goalEventListContainer.removeView(row);
        // ❌ 점수 자동 동기화 제거
    }


    private void syncScoreForWithRowCount() {
        if (!isUiReady() || editScoreFor == null || goalEventListContainer == null) return;
        editScoreFor.setText(String.valueOf(goalEventListContainer.getChildCount()));
    }

    // WriteRecord.java 안
    private void loadExistingRecord() {
        db.collection("matches").document(getMatchDocId())
                .get()
                .addOnSuccessListener(doc -> {
                    existingGoalEvents.clear();
                    existingScoreFor = -1;
                    existingScoreAgainst = -1;
                    existingRecorded = false;

                    if (doc != null && doc.exists()) {
                        existingDocExists = true;

                        String status = doc.getString("status");
                        boolean hasScore = doc.contains("scoreFor") || doc.contains("scoreAgainst");
                        boolean hasScorers = doc.contains("scorers");
                        boolean hasEvents = doc.contains("goalEvents");
                        existingRecorded = "finished".equals(status) || hasScore || hasScorers || hasEvents;

                        Long sf = safeLong(doc.get("scoreFor"));
                        Long sa = safeLong(doc.get("scoreAgainst"));
                        existingScoreFor = sf != null ? sf.intValue() : -1;
                        existingScoreAgainst = sa != null ? sa.intValue() : -1;

                        // goalEvents 우선
                        List<Map<String, Object>> evs = (List<Map<String, Object>>) doc.get("goalEvents");
                        if (evs != null && !evs.isEmpty()) {
                            for (Map<String, Object> m : evs) {
                                String sid = s(m.get("scorerId"));
                                String snk = s(m.get("scorerNickname"));
                                String aid = s(m.get("assistId"));
                                String ank = s(m.get("assistNickname"));
                                if (TextUtils.isEmpty(snk)) snk = sid;
                                if (!TextUtils.isEmpty(aid) && TextUtils.isEmpty(ank)) ank = aid;
                                existingGoalEvents.add(new GoalEventData(
                                        sid, snk,
                                        TextUtils.isEmpty(aid)? null : aid,
                                        TextUtils.isEmpty(ank)? null : ank
                                ));
                            }
                        } else {
                            // 없으면 옛날 scorers로부터 행으로 펼치기(도움 없음)
                            List<Map<String, Object>> list = (List<Map<String, Object>>) doc.get("scorers");
                            if (list != null) {
                                for (Map<String, Object> m : list) {
                                    String pid = s(m.get("playerId"));
                                    String nick = s(m.get("nickname"));
                                    int goals = i(m.get("goals"));
                                    if (goals <= 0) continue;
                                    for (int k = 0; k < goals; k++) {
                                        existingGoalEvents.add(new GoalEventData(pid, nick, null, null));
                                    }
                                }
                            }
                        }
                    } else {
                        existingDocExists = false;
                        existingRecorded = false;
                    }

                    // ✅ 여기서 바로 점수도 넣어준다.
                    // (단, 입력칸이 활성화되어 있는 경우에만. 비오너로 잠겨 있는 화면은 건드리지 않음)
                    if (isUiReady()) {
                        if (existingScoreFor >= 0 && editScoreFor != null && editScoreFor.isEnabled()) {
                            editScoreFor.setText(String.valueOf(existingScoreFor));
                        }
                        if (existingScoreAgainst >= 0 && editScoreAgainst != null && editScoreAgainst.isEnabled()) {
                            editScoreAgainst.setText(String.valueOf(existingScoreAgainst));
                        }
                    }

                    // 참석자까지 다 불러온 뒤 이벤트 행을 붙일 수 있게
                    maybeApplyExistingRecord();
                })
                .addOnFailureListener(e -> {
                    existingDocExists = false;
                    existingRecorded = false;
                    maybeApplyExistingRecord();
                    errorToast("기존 기록 확인 실패: " + e.getMessage());
                })
                .addOnCompleteListener(t -> {
                    existingLoaded = true;
                    if (btnSaveRecord != null) btnSaveRecord.setEnabled(true);
                    maybeShowContent();
                });
    }


    // 기존 이벤트 UI 적용 — 비참석자 득점은 스킵
    private void maybeApplyExistingRecord() {
        if (!isUiReady() || existingApplied || !existingLoaded || !membersFullyLoaded) return;

        goalEventListContainer.removeAllViews();

        int skipped = 0;
        int applied = 0; // 이번에 실제로 추가된 이벤트 수
        for (GoalEventData ge : existingGoalEvents) {
            if (!attendeeUids.contains(ge.scorerId)) { skipped++; continue; }
            String assistId = (ge.assistId != null && attendeeUids.contains(ge.assistId)) ? ge.assistId : null;
            addGoalEventRow(ge.scorerId, assistId);
            applied++;
        }

        if (applied == 0) {
            // ✅ 작성팀일 때만 스코어를 비워서 hint 보이게 한다.
            // 신청팀일 때는 위에서 작성팀 점수를 가져와서 넣어놨으니까 건드리지 말기
            if (isOwner && editScoreFor != null) {
                editScoreFor.setText(""); // hint("0") 보이도록
            }
        } else {
            // ❌ 기존: syncScoreForWithRowCount() 호출 제거 (스코어는 사용자가 직접 입력/유지)
        }

        if (skipped > 0) infoToast("비참석 득점 이벤트 " + skipped + "개는 제외됐어요.");
        existingApplied = true;
    }

    // ===== 저장 =====
    private void saveRecord(){
        if (!isUiReady()) return;

        if (!existingLoaded) {
            if (btnSaveRecord != null) btnSaveRecord.setEnabled(false);
            db.collection("matches").document(getMatchDocId()).get()
                    .addOnSuccessListener(doc -> {
                        existingGoalEvents.clear();
                        existingScoreFor = -1;
                        existingScoreAgainst = -1;
                        existingRecorded = false;

                        if (doc != null && doc.exists()) {
                            existingDocExists = true;
                            String status = doc.getString("status");
                            boolean hasScore = doc.contains("scoreFor") || doc.contains("scoreAgainst");
                            boolean hasScorers = doc.contains("scorers");
                            boolean hasEvents = doc.contains("goalEvents");
                            existingRecorded = "finished".equals(status) || hasScore || hasScorers || hasEvents;

                            Long sf = safeLong(doc.get("scoreFor"));
                            Long sa = safeLong(doc.get("scoreAgainst"));
                            existingScoreFor = sf != null ? sf.intValue() : -1;
                            existingScoreAgainst = sa != null ? sa.intValue() : -1;

                            List<Map<String, Object>> evs = (List<Map<String, Object>>) doc.get("goalEvents");
                            if (evs != null) {
                                for (Map<String, Object> m : evs) {
                                    existingGoalEvents.add(new GoalEventData(
                                            s(m.get("scorerId")),
                                            s(m.get("scorerNickname")),
                                            s(m.get("assistId")).isEmpty()? null : s(m.get("assistId")),
                                            s(m.get("assistNickname")).isEmpty()? null : s(m.get("assistNickname"))
                                    ));
                                }
                            }
                        } else {
                            existingDocExists = false;
                            existingRecorded = false;
                        }

                        existingLoaded = true;
                        actuallySaveRecord();
                    })
                    .addOnFailureListener(e -> {
                        if (btnSaveRecord != null) btnSaveRecord.setEnabled(true);
                        errorToast("기존 기록 확인 실패: " + e.getMessage());
                    });
            return;
        }

        actuallySaveRecord();
    }

    // WriteRecord.java 안
    private void actuallySaveRecord() {
        if (!isUiReady()) return;

        // 🆕 비오너인데 오너 점수가 아직 없으면 저장 막기
        if (!isOwner && !ownerScoreLoaded) {
            errorToast("아직 상대팀이 스코어를 입력하지 않았어요. 먼저 상대팀이 기록을 완료해야 합니다.");
            return;
        }

        // 1) 현재 UI → goalEvents 수집
        List<GoalEventData> newEvents = new ArrayList<>();
        for (int i = 0; i < goalEventListContainer.getChildCount(); i++) {
            View row = goalEventListContainer.getChildAt(i);
            Spinner spSc = row.findViewWithTag("scorer");
            Spinner spAs = row.findViewWithTag("assist");
            if (spSc == null || spAs == null) continue;

            int posSc = spSc.getSelectedItemPosition();
            if (posSc < 0 || posSc >= playerUids.size()) continue; // 득점자 필수
            String scorerId = playerUids.get(posSc);
            String scorerNick = playerNames.get(posSc);

            // 도움: 0 = 도움 없음
            int posAs = spAs.getSelectedItemPosition();
            String assistId = null, assistNick = null;
            if (posAs > 0) {
                assistId = assistAdapter.uidAt(posAs);
                if (!TextUtils.isEmpty(assistId)) {
                    int baseIdx = playerUids.indexOf(assistId);
                    assistNick = baseIdx >= 0 ? playerNames.get(baseIdx) : assistId;
                } else {
                    assistId = null;
                }
            }

            newEvents.add(new GoalEventData(scorerId, scorerNick, assistId, assistNick));
        }

        // 2) 입력값과 검증
        int enteredScoreFor = parseInt(editScoreFor != null && editScoreFor.getText()!=null
                ? editScoreFor.getText().toString() : "0");
        int newScoreAgainst = parseInt(editScoreAgainst != null && editScoreAgainst.getText()!=null
                ? editScoreAgainst.getText().toString() : "0");

        // ✅ 우리팀 득점과 입력한 득점자 수가 다르면 저장 차단
        if (enteredScoreFor != newEvents.size()) {
            errorToast("우리팀 득점(" + enteredScoreFor + ")과 득점자 수(" + newEvents.size() + ")가 일치하지 않습니다.");
            if (editScoreFor != null) editScoreFor.requestFocus();
            return;
        }

        int newScoreFor = enteredScoreFor;

        // 3) 기존 대비 차액 계산
        int oldSF = (existingRecorded ? Math.max(existingScoreFor, 0) : 0);
        int oldSA = (existingRecorded ? Math.max(existingScoreAgainst, 0) : 0);
        int dGames = existingRecorded ? 0 : 1;

        int dWins=0, dDraws=0, dLosses=0;
        int newRes = Integer.compare(newScoreFor, newScoreAgainst);
        int oldRes = existingRecorded ? Integer.compare(oldSF, oldSA) : 9;
        if (!existingRecorded) {
            if (newRes > 0) dWins = 1; else if (newRes == 0) dDraws = 1; else dLosses = 1;
        } else if (oldRes != newRes) {
            if (oldRes > 0) dWins--; else if (oldRes == 0) dDraws--; else dLosses--;
            if (newRes > 0) dWins++; else if (newRes == 0) dDraws++; else dLosses++;
        }

        int dGF = newScoreFor - oldSF;
        int dGA = newScoreAgainst - oldSA;

        // 4) 득점 집계
        Map<String, Integer> newGoalsByUid = new HashMap<>();
        for (GoalEventData ge : newEvents) {
            if (TextUtils.isEmpty(ge.scorerId)) continue;
            newGoalsByUid.put(ge.scorerId, newGoalsByUid.getOrDefault(ge.scorerId, 0) + 1);
        }
        Map<String, Integer> oldGoalsByUid = new HashMap<>();
        for (GoalEventData ge : existingGoalEvents) {
            if (TextUtils.isEmpty(ge.scorerId)) continue;
            oldGoalsByUid.put(ge.scorerId, oldGoalsByUid.getOrDefault(ge.scorerId, 0) + 1);
        }

        // ✅ 도움 집계
        Map<String, Integer> newAssistsByUid = new HashMap<>();
        for (GoalEventData ge : newEvents) {
            if (!TextUtils.isEmpty(ge.assistId)) {
                newAssistsByUid.put(ge.assistId, newAssistsByUid.getOrDefault(ge.assistId, 0) + 1);
            }
        }
        Map<String, Integer> oldAssistsByUid = new HashMap<>();
        for (GoalEventData ge : existingGoalEvents) {
            if (!TextUtils.isEmpty(ge.assistId)) {
                oldAssistsByUid.put(ge.assistId, oldAssistsByUid.getOrDefault(ge.assistId, 0) + 1);
            }
        }

        // 5) scorers/assists payload
        List<Map<String, Object>> scorersAgg = new ArrayList<>();
        for (Map.Entry<String, Integer> e : newGoalsByUid.entrySet()) {
            String uid = e.getKey();
            int idx = playerUids.indexOf(uid);
            String nick = (idx >= 0 ? playerNames.get(idx) : uid);
            Map<String, Object> m = new HashMap<>();
            m.put("playerId", uid);
            m.put("nickname", nick);
            m.put("goals", e.getValue());
            scorersAgg.add(m);
        }

        List<Map<String, Object>> assistsAgg = new ArrayList<>();
        for (Map.Entry<String, Integer> e : newAssistsByUid.entrySet()) {
            String uid = e.getKey();
            int idx = playerUids.indexOf(uid);
            String nick = (idx >= 0 ? playerNames.get(idx) : uid);
            Map<String, Object> m = new HashMap<>();
            m.put("playerId", uid);
            m.put("nickname", nick);
            m.put("assists", e.getValue());
            assistsAgg.add(m);
        }

        // 6) goalEvents payload
        List<Map<String, Object>> goalEventsPayload = new ArrayList<>();
        for (GoalEventData ge : newEvents) {
            Map<String, Object> m = new HashMap<>();
            m.put("scorerId", ge.scorerId);
            m.put("scorerNickname", ge.scorerNickname);
            if (TextUtils.isEmpty(ge.assistId)) {
                m.put("assistId", null);
                m.put("assistNickname", null);
            } else {
                m.put("assistId", ge.assistId);
                m.put("assistNickname", ge.assistNickname);
            }
            goalEventsPayload.add(m);
        }

        long matchTs = computeMatchTs(date, time);

        // 7) Firestore 커밋
        WriteBatch batch = db.batch();

        // (1) 팀별 match upsert
        DocumentReference matchRef = db.collection("matches").document(getMatchDocId());
        Map<String,Object> match = new HashMap<>();
        match.put("teamId", myTeamId);
        match.put("teamName", myTeamName);
        match.put("teamLogoUrl", myTeamLogo);
        match.put("opponentName", opponentName);
        match.put("opponentLogoUrl", opponentLogoUrl);
        match.put("date", date);
        match.put("time", time);
        match.put("matchTs", matchTs);
        match.put("stadiumName", stadiumName);
        match.put("address", address);
        match.put("scoreFor", newScoreFor);
        match.put("scoreAgainst", newScoreAgainst);
        match.put("scorers", scorersAgg);
        match.put("assists", assistsAgg);
        match.put("goalEvents", goalEventsPayload);
        match.put("createdFromEventId", eventId);
        match.put("status", "finished");
        batch.set(matchRef, match, SetOptions.merge());

        // (2) event 상태 업데이트 (내 팀 이벤트)
        DocumentReference evRef = db.collection("schedules").document(myTeamId)
                .collection("events").document(eventId);
        Map<String,Object> upd = new HashMap<>();
        upd.put("status","finished");
        upd.put("matchId", getMatchDocId());
        upd.put("matchTs", matchTs);
        // ✅ 이 경기 기록을 처음 쓴 팀이 누구인지 저장해둔다 (나중에 다시 열 때 오너로 인식)
        upd.put("ownerTeamId", myTeamId);
        batch.set(evRef, upd, SetOptions.merge());

        // (3) 팀 누적(teamStats) 증감
        DocumentReference statsRef = db.collection("teamStats").document(myTeamId);
        Map<String, Object> statsInc = new HashMap<>();
        if (dGames != 0)  statsInc.put("games", FieldValue.increment(dGames));
        if (dWins  != 0)  statsInc.put("wins", FieldValue.increment(dWins));
        if (dDraws != 0)  statsInc.put("draws", FieldValue.increment(dDraws));
        if (dLosses!= 0)  statsInc.put("losses", FieldValue.increment(dLosses));
        if (dGF    != 0)  statsInc.put("goalsFor", FieldValue.increment(dGF));
        if (dGA    != 0)  statsInc.put("goalsAgainst", FieldValue.increment(dGA));
        statsInc.put("updatedAt", Timestamp.now());
        if (!statsInc.isEmpty()) batch.set(statsRef, statsInc, SetOptions.merge());

        // (4) 팀 누적 득점자 서브컬렉션(차액: 골)
        Set<String> unionGoals = new HashSet<>();
        unionGoals.addAll(newGoalsByUid.keySet());
        unionGoals.addAll(oldGoalsByUid.keySet());
        for (String uid : unionGoals) {
            int delta = newGoalsByUid.getOrDefault(uid,0) - oldGoalsByUid.getOrDefault(uid,0);
            if (delta == 0) continue;
            DocumentReference scRef = statsRef.collection("scorers").document(uid);
            Map<String, Object> scInc = new HashMap<>();
            scInc.put("goals", FieldValue.increment(delta));
            scInc.put("lastUpdated", Timestamp.now());
            int idx = playerUids.indexOf(uid);
            scInc.put("nickname", idx>=0? playerNames.get(idx): uid);
            scInc.put("playerId", uid);
            batch.set(scRef, scInc, SetOptions.merge());
        }

        if (state != null) state.showLoading();
        if (btnSaveRecord != null) btnSaveRecord.setEnabled(false);

        batch.commit()
                .addOnSuccessListener(v -> {
                    successToast("저장 완료!");
                    updateUserStatsAfterSave(
                            !existingRecorded,
                            oldGoalsByUid, newGoalsByUid,
                            oldAssistsByUid, newAssistsByUid
                    );
                    dismissAllowingStateLoss();
                })
                .addOnFailureListener(e -> {
                    if (btnSaveRecord != null) btnSaveRecord.setEnabled(true);
                    if (state != null) state.showContent();
                    errorToast("기록 저장에 실패했어요.\n잠시 후 다시 시도해 주세요.");
                });
    }


    // ✅ 팀원 개인 누적(userStats) 갱신
    // ✅ 팀원 개인 누적(userStats) 갱신
// isFirstSave 가 true 일 때 = 이 매치를 우리 팀이 처음 기록하는 순간
// -> 우리 팀 "모든 멤버"의 teamGames 를 +1 해준다.
    private void updateUserStatsAfterSave(
            boolean isFirstSave,
            Map<String, Integer> oldGoalsByUid,
            Map<String, Integer> newGoalsByUid,
            Map<String, Integer> oldAssistsByUid,
            Map<String, Integer> newAssistsByUid
    ) {
        db.collection("teams").document(myTeamId)
                .get()
                .addOnSuccessListener(teamDoc -> {
                    List<String> members = (List<String>) teamDoc.get("members");
                    if (members == null) members = new ArrayList<>();
                    Set<String> teamUids = new HashSet<>(members);

                    // ===== 득점 차액 계산 (우리팀만) =====
                    Map<String, Integer> deltaTeamGoals = new HashMap<>();
                    Set<String> unionG = new HashSet<>();
                    unionG.addAll(oldGoalsByUid.keySet());
                    unionG.addAll(newGoalsByUid.keySet());
                    for (String uid : unionG) {
                        int delta = newGoalsByUid.getOrDefault(uid, 0) - oldGoalsByUid.getOrDefault(uid, 0);
                        if (delta != 0 && teamUids.contains(uid)) {
                            deltaTeamGoals.put(uid, delta);
                        }
                    }

                    // ===== 도움 차액 계산 (우리팀만) =====
                    Map<String, Integer> deltaTeamAssists = new HashMap<>();
                    Set<String> unionA = new HashSet<>();
                    unionA.addAll(oldAssistsByUid.keySet());
                    unionA.addAll(newAssistsByUid.keySet());
                    for (String uid : unionA) {
                        int delta = newAssistsByUid.getOrDefault(uid, 0) - oldAssistsByUid.getOrDefault(uid, 0);
                        if (delta != 0 && teamUids.contains(uid)) {
                            deltaTeamAssists.put(uid, delta);
                        }
                    }

                    WriteBatch wb = db.batch();

                    // 🟢 여기 핵심: 이 매치를 처음 기록한 경우 → 우리팀 전원 경기수 +1
                    // (골/도움이 없던 사람도 전부 올라감)
                    if (isFirstSave) {
                        for (String uid : teamUids) {
                            DocumentReference ref = db.collection("userStats").document(uid);
                            Map<String, Object> inc = new HashMap<>();
                            inc.put("teamGames", FieldValue.increment(1));
                            inc.put("updatedAt", Timestamp.now());
                            wb.set(ref, inc, SetOptions.merge());
                        }
                    }

                    // 🟢 득점 반영
                    for (Map.Entry<String, Integer> e : deltaTeamGoals.entrySet()) {
                        DocumentReference ref = db.collection("userStats").document(e.getKey());
                        Map<String, Object> inc = new HashMap<>();
                        inc.put("teamGoals", FieldValue.increment(e.getValue()));
                        inc.put("updatedAt", Timestamp.now());
                        wb.set(ref, inc, SetOptions.merge());
                    }

                    // 🟢 도움 반영
                    for (Map.Entry<String, Integer> e : deltaTeamAssists.entrySet()) {
                        DocumentReference ref = db.collection("userStats").document(e.getKey());
                        Map<String, Object> inc = new HashMap<>();
                        inc.put("teamAssists", FieldValue.increment(e.getValue()));
                        inc.put("updatedAt", Timestamp.now());
                        wb.set(ref, inc, SetOptions.merge());
                    }

                    wb.commit();
                })
                .addOnFailureListener(err -> {
                    android.util.Log.w("WriteRecord", "updateUserStatsAfterSave failed: " + err);
                });
    }


    // ===== 유틸 =====
    private long computeMatchTs(String d, String t) {
        try {
            String dateStr = n(d).trim();
            String timeStr = n(t).trim();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                java.time.format.DateTimeFormatter df = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault());
                LocalDate ld = LocalDate.parse(dateStr, df);
                LocalTime lt;
                if (!TextUtils.isEmpty(timeStr)) {
                    java.time.format.DateTimeFormatter tf = timeStr.length() == 5
                            ? java.time.format.DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
                            : java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault());
                    lt = LocalTime.parse(timeStr, tf);
                } else {
                    lt = LocalTime.MIDNIGHT;
                }
                return ld.atTime(lt).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } else {
                String pattern = !TextUtils.isEmpty(timeStr)
                        ? (timeStr.length() == 5 ? "yyyy-MM-dd HH:mm" : "yyyy-MM-dd HH:mm:ss")
                        : "yyyy-MM-dd";
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
                return sdf.parse(TextUtils.isEmpty(timeStr) ? dateStr : (dateStr + " " + timeStr)).getTime();
            }
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private int dp(int v){
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
    private int parseInt(String s){ try{ return Integer.parseInt(s.trim()); }catch(Exception e){ return 0; } }
    private String n(String s){ return s==null? "": s; }
    private String s(Object o){ return o==null? "": String.valueOf(o); }
    private int i(Object o){ try{ return Integer.parseInt(String.valueOf(o)); }catch(Exception e){ return 0; } }
    private Long safeLong(Object o){
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return null; }
    }

    // ----- 토스트 -----
    private void successToast(String msg){ CustomToast.success(requireContext(), msg); }
    private void infoToast(String msg){ CustomToast.info(requireContext(), msg); }
    private void warningToast(String msg){ CustomToast.warning(requireContext(), msg); }
    private void errorToast(String msg){ CustomToast.error(requireContext(), msg); }

    private void showEmptyWithMessage(String message){
        if (state != null) {
            state.showEmpty();
            View root = getView();
            if (root != null) {
                TextView tv = root.findViewById(R.id.txtEmptyMessage);
                if (tv != null) tv.setText(message);
            }
        } else {
            errorToast(message);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog d = getDialog();
        if (d != null && d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            d.getWindow().setDimAmount(0.5f);
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int width = (int) (dm.widthPixels * 0.92f);
            d.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            d.getWindow().setGravity(Gravity.CENTER);
        }
        setCancelable(true);
    }

    // ===============================
    // ✅ 득점자 스피너 어댑터
    // ===============================
    private class PlayerSpinnerAdapter extends BaseAdapter {
        private final LayoutInflater inflater = LayoutInflater.from(requireContext());
        @Override public int getCount() { return playerUids.size(); }
        @Override public Object getItem(int position) { return playerUids.get(position); }
        @Override public long getItemId(int position) { return position; }
        private View buildView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) v = inflater.inflate(R.layout.item_spinner_player, parent, false);
            TextView tvName = v.findViewById(R.id.tvName);
            TextView chip   = v.findViewById(R.id.chipMercenary);
            String uid  = playerUids.get(position);
            String name = playerNames.get(position);
            tvName.setText(name);
            chip.setVisibility(!TextUtils.isEmpty(uid) && mercUids.contains(uid) ? View.VISIBLE : View.GONE);
            return v;
        }
        @Override public View getView(int position, View convertView, ViewGroup parent) { return buildView(position, convertView, parent); }
        @Override public View getDropDownView(int position, View convertView, ViewGroup parent) { return buildView(position, convertView, parent); }
    }

    // ===============================
    // ✅ 도움자 스피너 어댑터 (0: 도움 없음)
    // ===============================
    private class AssistSpinnerAdapter extends BaseAdapter {
        private final LayoutInflater inflater = LayoutInflater.from(requireContext());
        @Override public int getCount() { return playerUids.size() + 1; } // +1 for "도움 없음"
        @Override public Object getItem(int position) { return position == 0 ? "" : playerUids.get(position - 1); }
        @Override public long getItemId(int position) { return position; }

        String uidAt(int position) { return position == 0 ? "" : playerUids.get(position - 1); }
        int indexOfUid(String uid) {
            if (TextUtils.isEmpty(uid)) return 0;
            int idx = playerUids.indexOf(uid);
            return idx < 0 ? 0 : (idx + 1);
        }

        private View buildView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) v = inflater.inflate(R.layout.item_spinner_player, parent, false);
            TextView tvName = v.findViewById(R.id.tvName);
            TextView chip   = v.findViewById(R.id.chipMercenary);

            if (position == 0) {
                tvName.setText("도움 없음");
                chip.setVisibility(View.GONE);
            } else {
                String uid  = playerUids.get(position - 1);
                String name = playerNames.get(position - 1);
                tvName.setText(name);
                chip.setVisibility(!TextUtils.isEmpty(uid) && mercUids.contains(uid) ? View.VISIBLE : View.GONE);
            }
            return v;
        }
        @Override public View getView(int position, View convertView, ViewGroup parent) { return buildView(position, convertView, parent); }
        @Override public View getDropDownView(int position, View convertView, ViewGroup parent) { return buildView(position, convertView, parent); }
    }
}
