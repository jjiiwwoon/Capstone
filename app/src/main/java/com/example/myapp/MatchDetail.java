package com.example.myapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * 매치 상세 화면
 * - 라벨 통일: 신청하기 / 신청 완료 / 신청 불가
 * - 우선순위: 본인글 > 내팀글 > (권한없음) > (거절됨=재신청) > 이미신청 > 팀없음 > 허용
 * - 권한 체크: 팀의 주장/부주장만 신청 가능
 * - StateLayout: 모든 데이터 로딩 완료 후에만 showContent()
 */
public class MatchDetail extends AppCompatActivity {

    // ===== 신청 상태 상수 =====
    private static final int APPLY_ALLOWED = 0;
    private static final int APPLY_ALREADY = 1;
    private static final int BLOCK_SELF_AUTHOR = 2;
    private static final int BLOCK_MY_TEAM = 3;
    private static final int BLOCK_NO_PERMISSION = 4; // 주장/부주장 아님
    private static final int BLOCK_NO_TEAM = 5;       // 팀 없음(매치 전용)
    private static final int APPLY_REAPPLY = 6;       // ✅ 거절됨 → 재신청 가능

    private int applyState = APPLY_ALLOWED;

    // StateLayout
    private StateLayout state;

    // 기본 UI
    private ImageView imgTeamLogo;
    private TextView txtTeamName, txtDateTime, txtAddress, txtStadium, txtDescription;
    private Button btnApply;

    private LinearLayout layoutTeamInfo;
    private LinearLayout teamInfoBox;

    // 칩 & 최근 전적(5경기)
    private TextView chipAge, chipRegion, chipSkill;
    private TextView tile1, tile2, tile3, tile4, tile5;
    private TextView tvRecentWin, tvRecentDraw, tvRecentLoss, tvRecentGF, tvRecentGA;

    // 상태 값들
    private String matchId;
    private String matchTeamId;     // 글 작성 팀 ID
    private String matchAuthorUid;  // 글 작성자 UID
    private String teamName;

    private String currentUid;
    private String myNickname;
    private int mySkill;
    private String myTeamName;
    private String myTeamId;
    private String myTeamLogoUrl = "";
    private String myTeamCaptainUid = "";
    private String myTeamViceCaptainUid = "";

    private boolean matchLoaded = false;
    private boolean profileLoaded = false;
    private boolean myTeamLoaded = false; // 팀 없으면 true 처리
    private boolean contentShown = false; // 중복 showContent 방지

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.match_detail);

        // ===== StateLayout =====
        state = findViewById(R.id.state);
        if (state != null) state.showLoading();

        // ===== findViewById =====
        imgTeamLogo    = findViewById(R.id.imgTeamLogo);
        txtTeamName    = findViewById(R.id.txtTeamName);
        txtDateTime    = findViewById(R.id.txtDateTime);
        txtAddress     = findViewById(R.id.txtAddress);
        txtStadium     = findViewById(R.id.txtStadium);
        txtDescription = findViewById(R.id.txtDescription);
        btnApply       = findViewById(R.id.btnApply);

        layoutTeamInfo = findViewById(R.id.layoutTeamInfo);
        teamInfoBox    = findViewById(R.id.teamInfoBox);

        // 칩
        chipAge    = findViewById(R.id.chipAge);
        chipRegion = findViewById(R.id.chipRegion);
        chipSkill  = findViewById(R.id.chipSkill);

        // 최근 전적 타일 + 합계
        tile1 = findViewById(R.id.tile1);
        tile2 = findViewById(R.id.tile2);
        tile3 = findViewById(R.id.tile3);
        tile4 = findViewById(R.id.tile4);
        tile5 = findViewById(R.id.tile5);

        tvRecentWin  = findViewById(R.id.tvRecentWin);
        tvRecentDraw = findViewById(R.id.tvRecentDraw);
        tvRecentLoss = findViewById(R.id.tvRecentLoss);
        tvRecentGF   = findViewById(R.id.tvRecentGF);
        tvRecentGA   = findViewById(R.id.tvRecentGA);

        // 로그인 사용자 (페이지 진입 보장 가정)
        currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // 인텐트 파라미터
        matchId = getIntent().getStringExtra("matchId");
        if (matchId == null || matchId.isEmpty()) {
            if (state != null) state.showEmpty();
            CustomToast.error(this, "잘못된 접근이에요.\n목록에서 다시 들어와 주세요.");
            finish();
            return;
        }

        // 라벨/버튼 초기값
        btnApply.setText("신청하기");
        btnApply.setVisibility(View.VISIBLE);
        btnApply.setEnabled(false); // ✅ 로딩 중엔 잠금

        // 데이터 로드
        loadMyProfile();
        loadMatchDetail();

        // 클릭 동작: 상태별 분기 (재신청 반영)
        btnApply.setOnClickListener(v -> {
            switch (applyState) {
                case APPLY_ALLOWED:
                    showApplyConfirmDialog(false, this::submitFirstApplication);
                    break;
                case APPLY_REAPPLY:
                    showApplyConfirmDialog(true, this::reapplyExistingApplication);
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
                case BLOCK_NO_PERMISSION:
                    CustomToast.info(this, "팀 내 권한이 있는 사용자만 신청이 가능합니다.");
                    break;
                case BLOCK_NO_TEAM:
                    CustomToast.info(this, "현재 소속되어 있는 팀이 없습니다. 팀에 가입하거나 팀을 생성해주세요.");
                    break;
            }
        });

        setupAddressClick();
    }

    // ================== 데이터 로딩 ==================

    private void loadMatchDetail() {
        FirebaseFirestore.getInstance().collection("matches")
                .document(matchId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        if (state != null) state.showEmpty();
                        CustomToast.info(this, "삭제되었거나 존재하지 않는 시합이에요.");
                        return;
                    }
                    bindMatch(doc);
                    matchLoaded = true;
                    // ❌ 여기서 showContent() 금지
                    evaluateApplyEligibility();
                })
                .addOnFailureListener(e -> {
                    if (state != null) state.showEmpty();
                    CustomToast.error(this, "매치를 불러오지 못했어요.\n잠시 후 다시 시도해 주세요.");
                });
    }

    private void bindMatch(DocumentSnapshot doc) {
        teamName = doc.getString("teamName");
        txtTeamName.setText(teamName != null ? teamName : "");

        String date = doc.getString("date");
        String time = doc.getString("time");
        txtDateTime.setText((date != null && time != null) ? (date + " | " + time) : "일정 정보 없음");

        txtAddress.setText(doc.getString("address"));
        String stadium = doc.getString("stadiumName");
        if (stadium == null) stadium = doc.getString("stadium");
        txtStadium.setText(stadium != null ? stadium : "");
        txtDescription.setText(doc.getString("description"));

        matchTeamId    = doc.getString("teamId");
        matchAuthorUid = doc.getString("uid");

        String logoUrl = doc.getString("teamLogoUrl");
        if (logoUrl == null || logoUrl.isEmpty()) logoUrl = doc.getString("logoUrl");
        if (logoUrl != null && !logoUrl.isEmpty()) {
            Glide.with(this).load(logoUrl)
                    .placeholder(R.drawable.ic_placeholder_circle)
                    .into(imgTeamLogo);
        } else {
            imgTeamLogo.setImageResource(R.drawable.ic_placeholder_circle);
        }

        // 팀칩/평균실력
        if (matchTeamId != null && !matchTeamId.isEmpty()) {
            FirebaseFirestore.getInstance().collection("teams")
                    .document(matchTeamId)
                    .get()
                    .addOnSuccessListener(teamDoc -> {
                        if (teamDoc.exists()) {
                            String age  = teamDoc.getString("ageRange");
                            String reg  = teamDoc.getString("region");
                            Long   avg  = teamDoc.getLong("skillAverage");

                            chipAge.setText((age != null && !age.isEmpty()) ? "연령: " + age : "연령: -");
                            chipRegion.setText(reg != null ? reg : "-");
                            chipSkill.setText("실력: " + (avg != null ? avg : "-"));
                            // 여기서 팀 이름 밑에 실력 보여주던 TextView는 XML에서 제거했으므로 코드도 제거
                        }
                    });

            // 최근 5경기 계산
            loadRecentForm(matchTeamId);
        }

        teamInfoBox.setOnClickListener(v -> {
            if (matchTeamId != null && !matchTeamId.isEmpty()) {
                Intent intent = new Intent(this, TeamDetail.class);
                intent.putExtra("teamId", matchTeamId);
                startActivity(intent);
            } else {
                CustomToast.error(this, "팀 정보를 불러올 수 없습니다.\n잠시 후 다시 시도해 주세요.");
            }
        });
    }

    private void loadMyProfile() {
        FirebaseFirestore.getInstance().collection("profiles")
                .document(currentUid)
                .get()
                .addOnSuccessListener(doc -> {
                    myNickname = doc.getString("nickname");
                    Long skillVal = doc.getLong("skill");
                    mySkill = (skillVal != null) ? skillVal.intValue() : -1;

                    String teamIdFromProfile = doc.getString("myTeam");
                    if (teamIdFromProfile != null && !teamIdFromProfile.isEmpty()) {
                        this.myTeamId = teamIdFromProfile;

                        // 내 팀 이름/로고/권한자
                        FirebaseFirestore.getInstance().collection("teams")
                                .document(myTeamId)
                                .get()
                                .addOnSuccessListener(teamDoc -> {
                                    if (teamDoc.exists()) {
                                        myTeamName = teamDoc.getString("teamName");
                                        myTeamLogoUrl = teamDoc.getString("logoUrl");
                                        myTeamCaptainUid = nz(teamDoc.getString("captainUID"), "");
                                        myTeamViceCaptainUid = nz(teamDoc.getString("viceCaptainUID"), "");
                                    }
                                })
                                .addOnCompleteListener(t -> {
                                    myTeamLoaded = true;
                                    profileLoaded = true;
                                    evaluateApplyEligibility();
                                });
                    } else {
                        // 팀이 없음
                        myTeamLoaded = true;
                        profileLoaded = true;
                        evaluateApplyEligibility();
                    }
                })
                .addOnFailureListener(e -> {
                    myTeamLoaded = true;
                    profileLoaded = true;
                    evaluateApplyEligibility();
                });
    }

    // ================== 최근 5경기 계산 ==================

    private void loadRecentForm(String teamId) {
        TextView[] tiles = {tile1, tile2, tile3, tile4, tile5};

        // 초기화
        for (TextView t : tiles) {
            if (t == null) continue;
            t.setText("-");
            t.setTextColor(Color.parseColor("#9E9E9E"));
            t.setBackgroundResource(R.drawable.bg_result_neutral);
        }
        tvRecentWin.setText("승 0");
        tvRecentDraw.setText("  ·  무 0");
        tvRecentLoss.setText("  ·  패 0");
        tvRecentGF.setText("득점 0");
        tvRecentGA.setText(" · 실점 0");

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("matches")
                .whereEqualTo("teamId", teamId)
                .whereEqualTo("status", "finished")
                .orderBy("matchTs", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(5)
                .get()
                .addOnSuccessListener(q -> {
                    int w=0, d=0, l=0, gf=0, ga=0, i=0;

                    for (DocumentSnapshot m : q) {
                        int sf = toInt(m.get("scoreFor"));
                        int sa = toInt(m.get("scoreAgainst"));

                        gf += sf; ga += sa;

                        String r; int color;
                        if (sf > sa)      { r="W"; color = Color.parseColor("#4CAF50"); w++; }
                        else if (sf == sa){ r="D"; color = Color.parseColor("#546E7A"); d++; }
                        else              { r="L"; color = Color.parseColor("#E53935"); l++; }

                        if (i < tiles.length && tiles[i] != null) {
                            tiles[i].setText(r);
                            tiles[i].setTextColor(color);
                            tiles[i].setBackgroundResource(R.drawable.bg_result_neutral);
                            i++;
                        }
                    }

                    tvRecentWin.setText("승 " + w);
                    tvRecentDraw.setText("  ·  무 " + d);
                    tvRecentLoss.setText("  ·  패 " + l);
                    tvRecentGF.setText("득점 " + gf);
                    tvRecentGA.setText(" · 실점 " + ga);
                });
    }

    // ================== 신청 로직/상태 ==================

    /** 우선순위: 본인글 > 내팀글 > 권한없음 > (거절됨=재신청) > 이미신청 > 팀없음 > 허용 */
    private void evaluateApplyEligibility() {
        if (!(matchLoaded && profileLoaded && myTeamLoaded)) return;

        // 본인 글
        if (currentUid.equals(nz(matchAuthorUid, ""))) {
            applyState = BLOCK_SELF_AUTHOR;
            btnApply.setText("신청 불가");
            finishLoadingAndShow();
            return;
        }
        // 내 팀 글(작성자는 아님)
        if (!isEmpty(myTeamId) && myTeamId.equals(nz(matchTeamId, "")) && !currentUid.equals(nz(matchAuthorUid, ""))) {
            applyState = BLOCK_MY_TEAM;
            btnApply.setText("신청 불가");
            finishLoadingAndShow();
            return;
        }
        // 권한 체크(팀이 있을 때만) - 주장/부주장만 가능
        if (!isEmpty(myTeamId)) {
            boolean isCaptain = currentUid.equals(nz(myTeamCaptainUid, ""));
            boolean isVice    = currentUid.equals(nz(myTeamViceCaptainUid, ""));
            if (!(isCaptain || isVice)) {
                applyState = BLOCK_NO_PERMISSION;
                btnApply.setText("신청 불가");
                finishLoadingAndShow();
                return;
            }
        }

        // 이미 신청 문서 확인(내 uid를 문서 ID로 쓰는 현재 스키마)
        FirebaseFirestore.getInstance()
                .collection("matches").document(matchId)
                .collection("applicants").document(currentUid)
                .get()
                .addOnSuccessListener(ds -> {
                    if (ds.exists()) {
                        String st = String.valueOf(ds.get("status"));
                        String s = (st == null) ? "" : st.trim().toLowerCase();
                        if (s.startsWith("rej")) {
                            applyState = APPLY_REAPPLY;      // ✅ 거절됨 → 재신청 허용
                            btnApply.setText("다시 신청");
                        } else {
                            applyState = APPLY_ALREADY;       // pending/accepted 등
                            btnApply.setText("신청 완료");
                        }
                    } else {
                        // 팀 없으면 불가(매치 전용)
                        if (isEmpty(myTeamId)) {
                            applyState = BLOCK_NO_TEAM;
                            btnApply.setText("신청 불가");
                        } else {
                            applyState = APPLY_ALLOWED;
                            btnApply.setText("신청하기");
                        }
                    }
                    finishLoadingAndShow();
                })
                .addOnFailureListener(e -> {
                    // 실패 시에도 팀 없으면 불가, 아니면 허용으로 둠
                    if (isEmpty(myTeamId)) {
                        applyState = BLOCK_NO_TEAM;
                        btnApply.setText("신청 불가");
                    } else {
                        applyState = APPLY_ALLOWED;
                        btnApply.setText("신청하기");
                    }
                    finishLoadingAndShow();
                });
    }

    /** 로딩 → 콘텐츠 전환을 한 번만 수행 */
    private void finishLoadingAndShow() {
        if (contentShown) return;
        contentShown = true;
        if (state != null) state.showContent();
        btnApply.setEnabled(true); // ✅ 이제 입력 허용
    }

    /** 신청/재신청 공용 확인 다이얼로그 (재신청 시 빨간 경고 문구) */
    private void showApplyConfirmDialog(boolean isReapply, Runnable onConfirm) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView msg = new TextView(this);
        msg.setText(isReapply ? "다시 신청하시겠습니까?" : "이 글에 신청하시겠습니까?");
        msg.setTextSize(16f);
        root.addView(msg);

        if (isReapply) {
            TextView warn = new TextView(this);
            warn.setText("상대방이 이미 거절한 글입니다. 재신청하겠습니까?");
            warn.setTextSize(13f);
            warn.setTextColor(0xFFF44336); // 빨강
            warn.setPadding(0, (int)(8 * getResources().getDisplayMetrics().density), 0, 0);
            root.addView(warn);
        }

        new AlertDialog.Builder(this)
                .setTitle(isReapply ? "재신청 확인" : "신청 확인")
                .setView(root)
                .setNegativeButton("취소", null)
                .setPositiveButton("신청", (d, w) -> onConfirm.run())
                .show();
    }

    /** 최초 신청 (새 문서) */
    private void submitFirstApplication() {
        if (applyState != APPLY_ALLOWED) return;

        long now = System.currentTimeMillis();

        Map<String, Object> data = new HashMap<>();
        data.put("userId", currentUid);
        data.put("nickname", myNickname);
        data.put("skill", mySkill);
        data.put("timestamp", now);
        data.put("teamName", myTeamName);
        data.put("teamId", myTeamId);
        data.put("teamLogoUrl", myTeamLogoUrl);
        data.put("status", "pending");
        data.put("responded", false);

        FirebaseFirestore.getInstance()
                .collection("matches").document(matchId)
                .collection("applicants").document(currentUid)
                .set(data)
                .addOnSuccessListener(v -> {
                    // ✅ 부모 매치 문서에 마지막 신청 시각(ms) 반영 → 상단 배지 트리거
                    FirebaseFirestore.getInstance()
                            .collection("matches").document(matchId)
                            .update("lastApplicantTs", now)
                            .addOnFailureListener(e -> {
                                // 배지용 집계 필드 실패는 치명적이지 않으므로 로깅만
                                android.util.Log.w("MatchDetail", "lastApplicantTs update failed: " + e.getMessage());
                            });

                    CustomToast.success(this, "신청이 완료되었습니다.\n상대 팀의 확인을 기다려 주세요.");
                    applyState = APPLY_ALREADY;
                    btnApply.setText("신청 완료");
                })
                .addOnFailureListener(e ->
                        CustomToast.error(this, "신청에 실패했어요.\n잠시 후 다시 시도해 주세요."));
    }

    /** 재신청 (거절 → 대기 전환) */
    private void reapplyExistingApplication() {
        if (applyState != APPLY_REAPPLY) return;

        long now = System.currentTimeMillis();

        Map<String, Object> up = new HashMap<>();
        up.put("status", "pending");
        up.put("timestamp", now);
        up.put("responded", false);
        up.put("reapplyCount", com.google.firebase.firestore.FieldValue.increment(1));
        up.put("lastAction", "reapply");

        FirebaseFirestore.getInstance()
                .collection("matches").document(matchId)
                .collection("applicants").document(currentUid)
                .update(up)
                .addOnSuccessListener(v -> {
                    // ✅ 부모 매치 문서에 마지막 신청 시각(ms) 반영 → 상단 배지 트리거
                    FirebaseFirestore.getInstance()
                            .collection("matches").document(matchId)
                            .update("lastApplicantTs", now)
                            .addOnFailureListener(e ->
                                    android.util.Log.w("MatchDetail", "lastApplicantTs update failed: " + e.getMessage()));

                    CustomToast.success(this, "신청이 완료되었습니다.\n상대 팀의 확인을 기다려 주세요.");
                    applyState = APPLY_ALREADY;
                    btnApply.setText("신청 완료");
                })
                .addOnFailureListener(e ->
                        CustomToast.error(this, "재신청에 실패했어요.\n잠시 후 다시 시도해 주세요."));
    }


    // ================== 기타 ==================

    private void setupAddressClick() {
        View.OnClickListener open = v -> openStadiumMap(txtAddress.getText().toString());
        txtAddress.setOnClickListener(open);
        txtStadium.setOnClickListener(open);
        View addressTap = findViewById(R.id.addressTapArea);
        if (addressTap != null) addressTap.setOnClickListener(open);
    }

    private void openStadiumMap(String address) {
        Intent intent = new Intent(MatchDetail.this, StadiumMapView.class);
        intent.putExtra("address", address);
        startActivity(intent);
    }

    private int toInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return 0; }
    }

    private static boolean isEmpty(String s){ return s == null || s.trim().isEmpty(); }
    private static String nz(String s, String d){ return isEmpty(s) ? d : s; }
}
