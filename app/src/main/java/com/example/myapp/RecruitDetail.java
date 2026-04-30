package com.example.myapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 선수모집 상세
 * - 라벨 통일: 신청하기 / 신청 완료 / 신청 불가 / 다시 신청
 * - 우선순위(절대 보장): 본인글 > 내팀글 > (거절=재신청) > 이미신청 > (정식선수 & 팀있음) > 허용
 * - 레이스 방지: eligibilityVersion 으로 비동기 응답 덮어쓰기 차단
 * - StateLayout: 모든 판단 완료 후에만 showContent()
 * - 🔹요청 반영:
 *      1) 용병(mercenary)일 때 장소 라벨을 "시합 장소"로 변경
 *      2) 용병(mercenary)일 때 시간 라벨을 "시합 시간"으로 변경
 *      3) XML에서 모집유형 칩을 제목 아래로 옮겼으므로 id 그대로(tvRecruitType) 사용
 *      4) 모집 영역에 있는 희망실력(tvRecruitSkill)을 실제 skillMin/Max로 채우도록 수정
 */
public class RecruitDetail extends AppCompatActivity {

    private static final String TAG = "RecruitDetail";

    // 상태 상수
    private static final int APPLY_ALLOWED = 0;
    private static final int APPLY_ALREADY = 1;
    private static final int BLOCK_SELF_AUTHOR = 2;
    private static final int BLOCK_MY_TEAM = 3;
    private static final int BLOCK_REGULAR_HAS_TEAM = 4;
    private static final int APPLY_REAPPLY = 5; // ✅ 거절됨 → 재신청 가능

    private int applyState = APPLY_ALLOWED;

    // StateLayout
    private StateLayout state;

    // 팀 정보 영역
    private ImageView imgTeamLogo;
    private TextView tvTeamName;
    private TextView tvSkillRange;   // 팀 카드 위쪽에 있는 "실력 4 ~ 10"
    private TextView tvDate, tvTime, tvStadium;
    private TextView tvTimeLabel;    // 활동 시간 / 시합 시간
    private TextView tvStadiumLabel; // 주 활동 장소 / 시합 장소

    // 모집 영역
    private TextView tvRecruitType;   // 제목 아래로 옮긴 칩 (색 유지)
    private TextView tvRecruitSkill;  // "4 ~ 10" 같은 모집 희망 실력
    private TextView tvIntro;
    private ChipGroup chipGroupPositions;

    private Button btnApply;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private String recruitId = "";

    // 내 정보
    private String currentUid = "";
    private String myTeamId = "";
    private String myTeamName = "";
    private String myTeamLogo = "";
    private String myNickname = "";
    private int mySkill = -1;

    // 모집글 정보
    private String recruitTypeRaw = ""; // regular / mercenary / (기타)
    private String postTeamId = "";     // 글 작성 팀 ID
    private String postAuthorUid = "";  // 글 작성자 UID

    // 로딩 플래그
    private boolean loadedRecruit = false;
    private boolean loadedProfile = false;

    // 레이스 방지 토큰
    private int eligibilityVersion = 0;

    // 콘텐츠 전환 1회 보장
    private boolean contentShown = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.recruit_detail);

        state = findViewById(R.id.stateLayout);
        if (state != null) state.showLoading();

        // 바인딩
        imgTeamLogo    = findViewById(R.id.imgTeamLogo);
        tvTeamName     = findViewById(R.id.tvTeamName);
        tvSkillRange   = findViewById(R.id.tvSkillRange);
        tvDate         = findViewById(R.id.tvDate);
        tvTime         = findViewById(R.id.tvTime);
        tvStadium      = findViewById(R.id.tvStadium);
        tvRecruitType  = findViewById(R.id.tvRecruitType);   // 제목 아래 칩
        tvRecruitSkill = findViewById(R.id.tvRecruitSkill);  // 희망실력 값
        tvIntro        = findViewById(R.id.tvIntro);
        tvTimeLabel    = findViewById(R.id.tvTimeLabel);
        tvStadiumLabel = findViewById(R.id.tvStadiumLabel);
        chipGroupPositions = findViewById(R.id.chipGroupPositions);
        btnApply       = findViewById(R.id.btnApply);

        // ✅ 팀 정보 영역 클릭 시 팀 상세 이동
        View teamInfoBox = findViewById(R.id.teamInfoBox);
        if (teamInfoBox != null) {
            teamInfoBox.setOnClickListener(v -> openTeamDetail());
        }

        // 로그인 사용자
        currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // 기본 버튼 상태
        btnApply.setText("신청하기");
        btnApply.setEnabled(false);

        // 클릭 시 상태별 처리
        btnApply.setOnClickListener(v -> {
            switch (applyState) {
                case APPLY_ALLOWED:
                    showApplyConfirmDialog(false, this::applyOrReapplyToRecruit);
                    break;
                case APPLY_REAPPLY:
                    showApplyConfirmDialog(true, this::applyOrReapplyToRecruit);
                    break;
                case APPLY_ALREADY:
                    CustomToast.info(this, "이미 신청한 글입니다. 상대 팀의 확인을 기다려주세요.");
                    break;
                case BLOCK_SELF_AUTHOR:
                    CustomToast.warning(this, "본인이 올린 글에는 신청할 수 없어요.");
                    break;
                case BLOCK_MY_TEAM:
                    CustomToast.warning(this, "내 팀이 올린 글에는 신청할 수 없어요.");
                    break;
                case BLOCK_REGULAR_HAS_TEAM:
                    CustomToast.info(this, "현재 소속된 팀이 있습니다. 팀이 없는 사용자만 신청할 수 있습니다.");
                    break;
            }
        });

        // 인텐트 파라미터
        recruitId = getIntent().getStringExtra("recruitId");
        Log.d(TAG, "recruitId=" + recruitId);
        if (TextUtils.isEmpty(recruitId)) {
            CustomToast.warning(this, "잘못된 접근입니다.");
            if (state != null) {
                state.setEmptyMessage("잘못된 접근입니다.");
                state.showEmpty();
            }
            return;
        }

        // 데이터 로드
        loadRecruitAndMyInfo();
    }

    private void openTeamDetail() {
        if (postTeamId == null || postTeamId.trim().isEmpty()) {
            CustomToast.info(this, "팀 정보를 불러오는 중입니다.");
            return;
        }
        Intent intent = new Intent(this, TeamDetail.class);
        intent.putExtra("teamId", postTeamId);
        startActivity(intent);
    }

    private void loadRecruitAndMyInfo() {
        Task<DocumentSnapshot> tRecruit = db.collection("recruitPosts").document(recruitId).get();
        Task<DocumentSnapshot> tProfile = db.collection("profiles").document(currentUid).get();

        Tasks.whenAllSuccess(tRecruit, tProfile)
                .addOnSuccessListener(list -> {
                    DocumentSnapshot dsRecruit = (DocumentSnapshot) list.get(0);
                    DocumentSnapshot dsProfile = (DocumentSnapshot) list.get(1);

                    // 모집글 바인딩 (없으면 레거시 'recruits'로 폴백)
                    if (dsRecruit != null && dsRecruit.exists()) {
                        bindRecruitDoc(dsRecruit);
                    } else {
                        db.collection("recruits").document(recruitId).get()
                                .addOnSuccessListener(old -> {
                                    if (old != null && old.exists()) bindRecruitDoc(old);
                                    else {
                                        CustomToast.info(this, "삭제된 글입니다.");
                                        if (state != null) {
                                            state.setEmptyMessage("삭제된 글입니다.");
                                            state.showEmpty();
                                        }
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    CustomToast.error(this, "불러오기 실패");
                                    if (state != null) {
                                        state.setEmptyMessage("불러오기에 실패했어요. 잠시 후 다시 시도해 주세요.");
                                        state.showEmpty();
                                    }
                                });
                    }

                    // 내 기본 정보
                    myNickname = nz(dsProfile.getString("nickname"), "");
                    mySkill = safeInt(dsProfile.getLong("skill"), -1);
                    myTeamId = nz(dsProfile.getString("myTeam"), "");
                    loadedProfile = true;

                    if (!isEmpty(myTeamId)) {
                        db.collection("teams").document(myTeamId).get()
                                .addOnSuccessListener(team -> {
                                    myTeamName = nz(team.getString("teamName"), "");
                                    myTeamLogo = nz(team.getString("logoUrl"),
                                            nz(team.getString("teamLogoUrl"), ""));
                                    recomputeApplyEligibility();
                                })
                                .addOnFailureListener(e -> recomputeApplyEligibility());
                    } else {
                        recomputeApplyEligibility();
                    }
                })
                .addOnFailureListener(e -> {
                    CustomToast.error(this, "불러오기 실패");
                    if (state != null) {
                        state.setEmptyMessage("불러오기에 실패했어요. 잠시 후 다시 시도해 주세요.");
                        state.showEmpty();
                    }
                });
    }

    private void bindRecruitDoc(DocumentSnapshot ds) {
        if (ds == null || !ds.exists()) {
            CustomToast.info(this, "삭제된 글입니다.");
            if (state != null) {
                state.setEmptyMessage("삭제된 글입니다.");
                state.showEmpty();
            }
            return;
        }
        String teamLogo = ds.getString("teamLogoUrl");
        String teamName = ds.getString("teamName");
        String date     = ds.getString("date");
        String time     = ds.getString("time");
        String stadium  = nz(ds.getString("stadiumName"),
                nz(ds.getString("stadiumAddress"), nz(ds.getString("address"), "")));
        String recruitType = ds.getString("recruitType");
        recruitTypeRaw = recruitType == null ? "" : recruitType;

        postTeamId = nz(ds.getString("teamId"), "");
        // 다양한 키 중 하나라도 있으면 우선 사용
        postAuthorUid = firstNonEmpty(
                ds.getString("authorUid"),
                firstNonEmpty(ds.getString("writerUid"), ds.getString("uid"))
        );

        Long sMinL = ds.getLong("skillMin");
        Long sMaxL = ds.getLong("skillMax");
        List<String> positions = (List<String>) ds.get("positions");
        String intro  = ds.getString("intro");

        Glide.with(this).load(teamLogo)
                .placeholder(R.drawable.ic_placeholder_circle)
                .into(imgTeamLogo);
        tvTeamName.setText(nz(teamName, "-"));
        tvDate.setText(nz(date, "-"));
        tvTime.setText(nz(time, "-"));
        tvStadium.setText(nz(stadium, "-"));

        String norm = normalizeRecruitType(recruitTypeRaw);

        // 모집유형 라벨 표시 (색은 XML에서 유지)
        String label;
        if ("mercenary".equals(norm)) label = "용병";
        else if ("regular".equals(norm)) label = "회원";
        else label = isEmpty(recruitTypeRaw) ? "" : recruitTypeRaw;
        tvRecruitType.setText(label);
        tvRecruitType.setVisibility(isEmpty(label) ? View.GONE : View.VISIBLE);

        // 장소 라벨 동적 변경
        if (tvStadiumLabel != null) {
            tvStadiumLabel.setText("mercenary".equals(norm) ? "시합 장소" : "주 활동 장소");
        }

        // 시간 라벨 동적 변경
        if (tvTimeLabel != null) {
            tvTimeLabel.setText("mercenary".equals(norm) ? "시합 시간" : "활동 시간");
        }

        // 모집글의 희망실력은 모집 박스에 표시
        if (tvRecruitSkill != null) {
            String minStr = (sMinL == null) ? "-" : String.valueOf(sMinL.intValue());
            String maxStr = (sMaxL == null) ? "-" : String.valueOf(sMaxL.intValue());
            tvRecruitSkill.setText(minStr + " ~ " + maxStr);
        }

        // 팀 카드 위쪽 실력(tvSkillRange)은 문서에 없으면 숨김
        if (tvSkillRange != null) {
            if (sMinL != null && sMaxL != null) {
                // 여기서 팀 실력 값이 따로 있으면 그걸로 바꿔도 됨
                tvSkillRange.setText("실력 " + sMinL.intValue() + " ~ " + sMaxL.intValue());
                tvSkillRange.setVisibility(View.VISIBLE);
            } else {
                tvSkillRange.setVisibility(View.GONE);
            }
        }

        tvIntro.setText(nz(intro, ""));

        chipGroupPositions.removeAllViews();
        if (positions != null) {
            for (String p : positions) {
                if (isEmpty(p)) continue;
                Chip c = new Chip(this);
                c.setText(p);
                c.setClickable(false);
                c.setCheckable(false);
                chipGroupPositions.addView(c);
            }
        }

        loadedRecruit = true;
        // 판단 완료 후 한번만 콘텐츠로 전환
        recomputeApplyEligibility();
    }

    /** 우선순위(절대): 본인글 > 내팀글 > (거절=재신청) > 이미신청 > (정식선수 & 팀있음) > 허용  */
    private void recomputeApplyEligibility() {
        if (!(loadedRecruit && loadedProfile)) {
            btnApply.setText("신청하기");
            return;
        }

        final int ver = ++eligibilityVersion; // 이 호출의 버전

        // 1) 본인 글 — 즉시 확정 & 전환
        if (safeEquals(currentUid, postAuthorUid)) {
            updateEligibilityAndShow(BLOCK_SELF_AUTHOR, "신청 불가");
            return;
        }

        // 2) 내 팀 글(작성자는 아님) — 즉시 확정 & 전환
        if (!isEmpty(myTeamId) && safeEquals(myTeamId, postTeamId)) {
            updateEligibilityAndShow(BLOCK_MY_TEAM, "신청 불가");
            return;
        }

        // 3) 내 신청 문서 상태 확인(신규/레거시 모두)
        DocumentReference newRef = db.collection("recruitPosts").document(recruitId)
                .collection("applicants").document(currentUid);
        DocumentReference oldRef = db.collection("recruits").document(recruitId)
                .collection("applicants").document(currentUid);

        Tasks.whenAllSuccess(newRef.get(), oldRef.get())
                .addOnSuccessListener(list -> {
                    if (ver != eligibilityVersion) return; // 레이스 방지

                    DocumentSnapshot found = null;
                    for (Object o : list) {
                        DocumentSnapshot ds = (DocumentSnapshot) o;
                        if (ds != null && ds.exists()) { found = ds; break; }
                    }

                    if (found != null) {
                        String st = String.valueOf(found.get("status"));
                        String s = (st == null) ? "" : st.trim().toLowerCase();
                        if (s.startsWith("rej")) {
                            updateEligibilityAndShow(APPLY_REAPPLY, "다시 신청");
                        } else {
                            updateEligibilityAndShow(APPLY_ALREADY, "신청 완료");
                        }
                    } else {
                        String norm = normalizeRecruitType(recruitTypeRaw);
                        if ("regular".equals(norm) && !isEmpty(myTeamId)) {
                            updateEligibilityAndShow(BLOCK_REGULAR_HAS_TEAM, "신청 불가");
                        } else {
                            updateEligibilityAndShow(APPLY_ALLOWED, "신청하기");
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (ver != eligibilityVersion) return; // 레이스 방지
                    String norm = normalizeRecruitType(recruitTypeRaw);
                    if ("regular".equals(norm) && !isEmpty(myTeamId)) {
                        updateEligibilityAndShow(BLOCK_REGULAR_HAS_TEAM, "신청 불가");
                    } else {
                        updateEligibilityAndShow(APPLY_ALLOWED, "신청하기");
                    }
                });
    }

    /** 상태/라벨 반영 + 로딩 종료 시 콘텐츠 1회 전환 */
    private void updateEligibilityAndShow(int stateCode, String label) {
        this.applyState = stateCode;
        btnApply.setText(label);
        finishLoadingAndShow();
    }

    /** 로딩 → 콘텐츠 전환을 한 번만 수행 */
    private void finishLoadingAndShow() {
        if (contentShown) return;
        contentShown = true;
        if (state != null) state.showContent();
        btnApply.setEnabled(true);
    }

    /** 신청/재신청 공용 확인 다이얼로그 */
    private void showApplyConfirmDialog(boolean isReapply, Runnable onConfirm) {
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        android.widget.TextView msg = new android.widget.TextView(this);
        msg.setText(isReapply ? "다시 신청하시겠습니까?" : "이 글에 신청하시겠습니까?");
        msg.setTextSize(16f);
        root.addView(msg);

        if (isReapply) {
            android.widget.TextView warn = new android.widget.TextView(this);
            warn.setText("상대방이 이미 거절한 글입니다. 재신청하겠습니까?");
            warn.setTextSize(13f);
            warn.setTextColor(0xFFF44336);
            warn.setPadding(0, (int)(8 * getResources().getDisplayMetrics().density), 0, 0);
            root.addView(warn);
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(isReapply ? "재신청 확인" : "신청 확인")
                .setView(root)
                .setNegativeButton("취소", null)
                .setPositiveButton("신청", (d, w) -> onConfirm.run())
                .show();
    }

    /** 신청 또는 재신청(거절 → 대기) */
    private void applyOrReapplyToRecruit() {
        if (isEmpty(recruitId)) { CustomToast.warning(this, "잘못된 글입니다."); return; }

        btnApply.setEnabled(false);

        db.runTransaction(tr -> {
            // 본문(신규/레거시) 결정
            DocumentReference postRef = db.collection("recruitPosts").document(recruitId);
            DocumentSnapshot postSnap = tr.get(postRef);
            if (!postSnap.exists()) {
                postRef = db.collection("recruits").document(recruitId);
                postSnap = tr.get(postRef);
                if (!postSnap.exists()) throw new IllegalStateException("삭제된 글입니다.");
            }

            DocumentReference profRef = db.collection("profiles").document(currentUid);
            DocumentSnapshot profSnap = tr.get(profRef);

            String myTeamIdTx = nz(profSnap.getString("myTeam"), "");
            String postTeamIdTx = nz(postSnap.getString("teamId"), "");
            String recruitTypeTx = normalizeRecruitType(nz(postSnap.getString("recruitType"), ""));
            String authorUidTx = firstNonEmpty(
                    postSnap.getString("authorUid"),
                    firstNonEmpty(postSnap.getString("writerUid"), postSnap.getString("uid"))
            );

            if (!isEmpty(authorUidTx) && safeEquals(authorUidTx, currentUid))
                throw new IllegalStateException("본인이 올린 글에는 신청할 수 없습니다.");
            if (!isEmpty(myTeamIdTx) && !isEmpty(postTeamIdTx) && safeEquals(myTeamIdTx, postTeamIdTx))
                throw new IllegalStateException("내 팀이 올린 글에는 신청할 수 없습니다.");
            if ("regular".equals(recruitTypeTx) && !isEmpty(myTeamIdTx))
                throw new IllegalStateException("현재 소속된 팀이 있습니다. 팀이 없는 사용자만 신청할 수 있습니다.");

            // 내 신청 문서
            DocumentReference apRef = postRef.collection("applicants").document(currentUid);
            DocumentSnapshot apSnap = tr.get(apRef);

            long now = System.currentTimeMillis();

            if (apSnap.exists()) {
                String st = String.valueOf(apSnap.get("status"));
                String s = (st == null) ? "" : st.trim().toLowerCase();

                if (s.startsWith("rej")) {
                    // ✅ 재신청: 거절 → 대기
                    Map<String, Object> up = new LinkedHashMap<>();
                    up.put("status", "pending");
                    up.put("timestamp", now);
                    up.put("responded", false);
                    up.put("reapplyCount", com.google.firebase.firestore.FieldValue.increment(1));
                    up.put("lastAction", "reapply");
                    tr.update(apRef, up);
                } else {
                    // 이미 pending/accepted 등
                    throw new IllegalStateException("이미 신청한 글입니다. 상대 팀의 확인을 기다려주세요.");
                }
            } else {
                // 최초 신청
                String nicknameTx = nz(profSnap.getString("nickname"), "");
                int skillTx = profSnap.getLong("skill") == null ? -1 : profSnap.getLong("skill").intValue();

                String teamNameTx = "";
                String teamLogoTx = "";
                if (!isEmpty(myTeamIdTx)) {
                    DocumentSnapshot teamSnap = tr.get(db.collection("teams").document(myTeamIdTx));
                    teamNameTx = nz(teamSnap.getString("teamName"), "");
                    teamLogoTx = nz(teamSnap.getString("logoUrl"),
                            nz(teamSnap.getString("teamLogoUrl"), ""));
                }

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("userId", currentUid);
                data.put("teamId", myTeamIdTx);
                data.put("teamName", teamNameTx);
                data.put("teamLogoUrl", teamLogoTx);
                data.put("nickname", nicknameTx);
                data.put("skill", skillTx < 0 ? null : skillTx);
                data.put("timestamp", now);
                data.put("status", "pending");
                data.put("responded", false);

                tr.set(apRef, data, SetOptions.merge());
            }
            return null;
        }).addOnSuccessListener(v -> {
            CustomToast.success(this, "신청이 완료되었습니다.");
            applyState = APPLY_ALREADY;
            btnApply.setText("신청 완료");
            btnApply.setEnabled(true);
        }).addOnFailureListener(e -> {
            String msg = e.getMessage();
            if (msg == null) msg = "신청에 실패했습니다. 잠시 후 다시 시도해주세요.";
            CustomToast.error(this, msg);

            if (msg.contains("본인이 올린")) {
                applyState = BLOCK_SELF_AUTHOR; btnApply.setText("신청 불가");
            } else if (msg.contains("내 팀이 올린")) {
                applyState = BLOCK_MY_TEAM; btnApply.setText("신청 불가");
            } else if (msg.contains("팀이 없는 사용자만") || msg.contains("현재 소속된 팀이")) {
                applyState = BLOCK_REGULAR_HAS_TEAM; btnApply.setText("신청 불가");
            } else if (msg.contains("이미 신청한")) {
                applyState = APPLY_ALREADY; btnApply.setText("신청 완료");
            } else {
                applyState = APPLY_ALLOWED; btnApply.setText("신청하기");
            }
            btnApply.setEnabled(true);
        });
    }

    // ---------- 유틸 ----------
    private static String nz(String s, String d) { return (s == null || s.isEmpty()) ? d : s; }
    private static boolean isEmpty(String s){ return s == null || s.trim().isEmpty(); }
    private static int safeInt(Long l, int def){ return l==null?def:l.intValue(); }
    private static String firstNonEmpty(String a, String b){ return !isEmpty(a) ? a : nz(b, ""); }

    private static boolean safeEquals(String a, String b){
        if (a == null || b == null) return false;
        return a.trim().equals(b.trim());
    }

    private String normalizeRecruitType(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase();
        if (s.contains("regular") || s.contains("정식")) return "regular";
        if (s.contains("mercenary") || s.contains("용병") || s.contains("일일")) return "mercenary";
        return "";
    }
}
