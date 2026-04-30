// src/main/java/com/example/myapp/MyProfile.java
package com.example.myapp;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;

import java.util.concurrent.atomic.AtomicInteger;

public class MyProfile extends Fragment {

    // ====== 설정: 이미지까지 기다릴지 여부 ======
    private static final boolean WAIT_IMAGES = true;

    // ✅ 프로필 사진 선택 요청 코드
    private static final int REQUEST_SELECT_PROFILE_IMAGE = 201;

    // StateLayout
    private StateLayout state;

    private ImageView profileImageView;
    private TextView textNickname, textAge, textPositionBox, textSkill, textFoot, textIntroContent;
    private TextView textHeight, textWeight, textPlayerType, textTeam;
    private ImageView teamLogo, toggleIntroArrow;
    private LinearLayout profileLayout;

    // 나의 기록(팀/용병)
    private TextView statsTeamGames, statsTeamGoals, statsMercGames, statsMercGoals;
    // ✅ 추가: 도움
    private TextView statsTeamAssists, statsMercAssists;

    private boolean isIntroExpanded = false;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    // 현재 사용자/팀
    private String currentUid = null;
    private String myTeamId = null;

    // ---- 동기화: 모든 필수 작업 끝나면 한 번에 공개 ----
    private final AtomicInteger pendingOps = new AtomicInteger(0);
    private volatile boolean contentShown = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.my_profile, container, false);

        // StateLayout
        state = view.findViewById(R.id.state);
        if (state != null) state.showLoading();

        // 🔗 XML 요소
        profileLayout    = view.findViewById(R.id.profileLayout);
        profileImageView = view.findViewById(R.id.profileImageView);
        textNickname     = view.findViewById(R.id.textNickname);
        textAge          = view.findViewById(R.id.textAge);
        textPositionBox  = view.findViewById(R.id.textPositionBox);
        textSkill        = view.findViewById(R.id.textSkill);
        textFoot         = view.findViewById(R.id.textFoot);
        textHeight       = view.findViewById(R.id.textHeight);
        textWeight       = view.findViewById(R.id.textWeight);
        textPlayerType   = view.findViewById(R.id.textPlayerType);
        textIntroContent = view.findViewById(R.id.textIntroContent);
        toggleIntroArrow = view.findViewById(R.id.toggleIntroArrow);
        textTeam         = view.findViewById(R.id.textTeam);
        teamLogo         = view.findViewById(R.id.teamLogo);

        statsTeamGames = view.findViewById(R.id.statsTeamGames);
        statsTeamGoals = view.findViewById(R.id.statsTeamGoals);
        statsMercGames = view.findViewById(R.id.statsMercGames);
        statsMercGoals = view.findViewById(R.id.statsMercGoals);
        // ✅ 추가 바인딩
        statsTeamAssists = view.findViewById(R.id.statsTeamAssists);
        statsMercAssists = view.findViewById(R.id.statsMercAssists);

        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // 소개 토글
        toggleIntroArrow.setRotation(0f);
        toggleIntroArrow.setOnClickListener(v -> toggleIntro());

        // 팀 보기: 로고/팀명 공통 클릭 리스너
        View.OnClickListener teamClick = v -> openTeamOrWarn();
        teamLogo.setOnClickListener(teamClick);
        textTeam.setOnClickListener(teamClick);

        // ✅ 프로필 사진 클릭 → 갤러리 열기
        profileImageView.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_SELECT_PROFILE_IMAGE);
        });

        loadUserProfile();
        return view;
    }

    // ====== 도우미: 공개 시점 제어 ======
    private void addWait(int count) {
        if (count > 0) pendingOps.addAndGet(count);
    }

    private void doneOne() {
        int left = pendingOps.decrementAndGet();
        maybeShowContent(left);
    }

    private void maybeShowContent(int left) {
        if (!contentShown && left <= 0) {
            contentShown = true;
            if (isUiSafe() && state != null) state.showContent();
        }
    }

    private void tryShowImmediatelyIfNoWait() {
        maybeShowContent(pendingOps.get());
    }

    // ====== 기타 ======
    private void toggleIntro() {
        if (isIntroExpanded) {
            textIntroContent.setMaxLines(2);
            toggleIntroArrow.animate().rotation(0f).setDuration(200).start();
        } else {
            textIntroContent.setMaxLines(Integer.MAX_VALUE);
            toggleIntroArrow.animate().rotation(180f).setDuration(200).start();
        }
        isIntroExpanded = !isIntroExpanded;
    }

    private boolean isUiSafe() {
        return isAdded() && getView() != null && getContext() != null;
    }

    private void openTeamOrWarn() {
        if (!isUiSafe()) return;
        Context ctx = getContext();
        if (ctx == null) return;

        if (!TextUtils.isEmpty(myTeamId)) {
            Intent intent = new Intent(ctx, TeamDetail.class);
            intent.putExtra("teamId", myTeamId);
            startActivity(intent);
        } else {
            CustomToast.info(ctx, "소속된 팀이 없어요.\n먼저 '팀 만들기'로 팀을 생성해 주세요.");
        }
    }

    // MyProfile.java 내부
    private void loadUserProfile() {
        if (auth.getCurrentUser() == null) {
            if (state != null) state.showEmpty();
            if (isUiSafe()) CustomToast.error(getContext(), "로그인이 필요해요.\n로그인 후 이용해 주세요.");
            return;
        }

        currentUid = auth.getCurrentUser().getUid();

        // 시작: 프로필 1건 대기
        contentShown = false;
        pendingOps.set(0);
        addWait(1);

        Task<DocumentSnapshot> profileTask =
                db.collection("profiles").document(currentUid).get();

        profileTask
                .addOnSuccessListener(doc -> {
                    if (!isUiSafe()) return;

                    if (!doc.exists()) {
                        if (state != null) state.showEmpty();
                        CustomToast.info(getContext(), "프로필 정보가 없어요.\n'프로필 만들기'에서 먼저 작성해 주세요.");
                        doneOne();
                        return;
                    }

                    // ---- 기본 필드 바인딩(텍스트만) ----
                    String nickname        = doc.getString("nickname");
                    Long ageLong           = doc.getLong("age");
                    String position        = doc.getString("position");
                    Long skillLong         = doc.getLong("skill");
                    String foot            = doc.getString("foot");
                    String introduction    = doc.getString("introduction");
                    String profileImageUrl = doc.getString("profileImageUrl");
                    myTeamId               = doc.getString("myTeam");

                    String age    = (ageLong   != null) ? ageLong + "세" : "-";
                    String skill  = (skillLong != null) ? String.valueOf(skillLong) : "-";

                    textNickname.setText(!TextUtils.isEmpty(nickname) ? nickname : "닉네임 없음");
                    textAge.setText(age);
                    textPositionBox.setText(!TextUtils.isEmpty(position) ? position : "-");
                    textSkill.setText(skill);
                    textFoot.setText(!TextUtils.isEmpty(foot) ? foot : "-");

                    Long h = doc.getLong("height");
                    Long w = doc.getLong("weight");
                    textHeight.setText(h != null ? h + "cm" : "-");
                    textWeight.setText(w != null ? w + "kg" : "-");

                    String playerType  = doc.getString("playerType");
                    String playerLevel = doc.getString("playerLevel");
                    if ("비선출".equals(playerType)) {
                        textPlayerType.setText("비선출");
                    } else if ("선출".equals(playerType) && !TextUtils.isEmpty(playerLevel)) {
                        textPlayerType.setText(playerLevel);
                    } else if ("선출".equals(playerType)) {
                        textPlayerType.setText("선출");
                    } else {
                        textPlayerType.setText("-");
                    }

                    textIntroContent.setText(!TextUtils.isEmpty(introduction) ? introduction : "자기소개 없음");
                    textIntroContent.setMaxLines(2);
                    isIntroExpanded = false;
                    toggleIntroArrow.setRotation(0f);

                    // ---- 프로필 이미지 (옵션: 기다릴지 선택) ----
                    if (!TextUtils.isEmpty(profileImageUrl)) {
                        if (WAIT_IMAGES) addWait(1);
                        Glide.with(this)
                                .load(profileImageUrl)
                                .placeholder(R.drawable.ic_person_placeholder)
                                .listener(new RequestListener<Drawable>() {
                                    @Override
                                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                                        if (WAIT_IMAGES) doneOne();
                                        return false;
                                    }
                                    @Override
                                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                                        if (WAIT_IMAGES) doneOne();
                                        return false;
                                    }
                                })
                                .into(profileImageView);
                    } else {
                        profileImageView.setImageResource(R.drawable.ic_person_placeholder);
                    }

                    // ─────────────────────────────────────────────────────────
                    // 팀/개인 기록 로딩
                    // ─────────────────────────────────────────────────────────

                    // (a) 팀 문서 (팀명/로고) — 팀이 있을 때만 1건 대기
                    if (!TextUtils.isEmpty(myTeamId)) {
                        addWait(1);
                        db.collection("teams").document(myTeamId)
                                .get()
                                .addOnSuccessListener(teamDoc -> {
                                    if (!isUiSafe()) { doneOne(); return; }
                                    if (teamDoc.exists()) {
                                        String teamName = teamDoc.getString("teamName");
                                        String logoUrl  = teamDoc.getString("logoUrl");

                                        textTeam.setText(!TextUtils.isEmpty(teamName) ? teamName : "-");
                                        teamLogo.setVisibility(View.VISIBLE); // 팀 존재 → 로고 보이기

                                        if (!TextUtils.isEmpty(logoUrl)) {
                                            if (WAIT_IMAGES) addWait(1);
                                            Glide.with(this)
                                                    .load(logoUrl)
                                                    .placeholder(R.drawable.ic_shield_gray)
                                                    .listener(new RequestListener<Drawable>() {
                                                        @Override
                                                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                                                            if (WAIT_IMAGES) doneOne();
                                                            return false;
                                                        }
                                                        @Override
                                                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                                                            if (WAIT_IMAGES) doneOne();
                                                            return false;
                                                        }
                                                    })
                                                    .into(teamLogo);
                                        } else {
                                            teamLogo.setImageResource(R.drawable.ic_shield_gray);
                                        }
                                    } else {
                                        // 팀 문서 없음 → 플레이스홀더
                                        showNoTeamPlaceholder();
                                    }
                                    doneOne();
                                })
                                .addOnFailureListener(e -> {
                                    if (!isUiSafe()) return;
                                    showNoTeamPlaceholder();
                                    doneOne();
                                });
                    } else {
                        // 팀 없음 → 플레이스홀더
                        showNoTeamPlaceholder();
                    }

                    // (b) 개인 누적(userStats) 1건: 팀/용병 경기·골·도움
                    addWait(1);
                    db.collection("userStats").document(currentUid)
                            .get()
                            .addOnSuccessListener(us -> {
                                if (!isUiSafe()) { doneOne(); return; }
                                long teamGames = 0, teamGoals = 0, teamAssists = 0;
                                long mercGames = 0, mercGoals = 0, mercAssists = 0;
                                if (us.exists()) {
                                    Long tg  = us.getLong("teamGames");    if (tg  != null) teamGames = tg;
                                    Long tgo = us.getLong("teamGoals");    if (tgo != null) teamGoals = tgo;
                                    Long ta  = us.getLong("teamAssists");  if (ta  != null) teamAssists = ta;

                                    Long mg  = us.getLong("mercGames");    if (mg  != null) mercGames = mg;
                                    Long mgo = us.getLong("mercGoals");    if (mgo != null) mercGoals = mgo;
                                    Long ma  = us.getLong("mercAssists");  if (ma  != null) mercAssists = ma;
                                }
                                // 좌측 카드(팀)
                                statsTeamGames.setText(String.valueOf(teamGames));
                                statsTeamGoals.setText(String.valueOf(teamGoals));
                                if (statsTeamAssists != null) statsTeamAssists.setText(String.valueOf(teamAssists));
                                // 우측 카드(용병)
                                if (statsMercGames != null)   statsMercGames.setText(String.valueOf(mercGames));
                                if (statsMercGoals != null)   statsMercGoals.setText(String.valueOf(mercGoals));
                                if (statsMercAssists != null) statsMercAssists.setText(String.valueOf(mercAssists));
                                doneOne();
                            })
                            .addOnFailureListener(e -> {
                                if (!isUiSafe()) return;
                                statsTeamGames.setText("0");
                                statsTeamGoals.setText("0");
                                if (statsTeamAssists != null) statsTeamAssists.setText("0");
                                if (statsMercGames != null)   statsMercGames.setText("0");
                                if (statsMercGoals != null)   statsMercGoals.setText("0");
                                if (statsMercAssists != null) statsMercAssists.setText("0");
                                doneOne();
                            });

                    // 프로필 문서 자체 완료
                    doneOne();

                    // 혹시 추가 대기 없으면 바로 공개
                    tryShowImmediatelyIfNoWait();
                })
                .addOnFailureListener(e -> {
                    if (!isUiSafe()) return;
                    if (state != null) state.showEmpty();
                    CustomToast.error(getContext(), "프로필 불러오기 실패했어요.\n잠시 후 다시 시도해 주세요.");
                    doneOne(); // 실패해도 카운터 정리
                });
    }

    private void showNoTeamPlaceholder() {
        textTeam.setText("소속팀 없음");
        teamLogo.setVisibility(View.VISIBLE);
        teamLogo.setImageResource(R.drawable.ic_team_placeholder);
    }

    @Override
    public void onResume() {
        super.onResume();
        // 기록 저장하고 돌아왔을 때 최신 userStats(특히 teamAssists/mercAssists) 반영
        reloadUserStatsOnly();
    }

    private void reloadUserStatsOnly() {
        if (!isUiSafe() || auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        db.collection("userStats").document(uid)
                .get()
                .addOnSuccessListener(us -> {
                    long teamGames = 0, teamGoals = 0, teamAssists = 0;
                    long mercGames = 0, mercGoals = 0, mercAssists = 0;

                    if (us.exists()) {
                        Long tg  = us.getLong("teamGames");    if (tg  != null) teamGames = tg;
                        Long tgo = us.getLong("teamGoals");    if (tgo != null) teamGoals = tgo;
                        Long ta  = us.getLong("teamAssists");  if (ta  != null) teamAssists = ta;  // ✅
                        Long mg  = us.getLong("mercGames");    if (mg  != null) mercGames = mg;
                        Long mgo = us.getLong("mercGoals");    if (mgo != null) mercGoals = mgo;
                        Long ma  = us.getLong("mercAssists");  if (ma  != null) mercAssists = ma;  // ✅
                    }

                    // 좌측 카드(팀)
                    statsTeamGames.setText(String.valueOf(teamGames));
                    statsTeamGoals.setText(String.valueOf(teamGoals));
                    if (statsTeamAssists != null) statsTeamAssists.setText(String.valueOf(teamAssists));

                    // 우측 카드(용병)
                    if (statsMercGames != null)   statsMercGames.setText(String.valueOf(mercGames));
                    if (statsMercGoals != null)   statsMercGoals.setText(String.valueOf(mercGoals));
                    if (statsMercAssists != null) statsMercAssists.setText(String.valueOf(mercAssists));
                })
                .addOnFailureListener(e -> {
                    // 실패 시에는 기존 표시 유지
                });
    }

    private void uploadProfileImageToFirebase(Uri imageUri) {
        if (!isUiSafe() || auth == null || auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        com.google.firebase.storage.StorageReference ref =
                FirebaseStorage.getInstance()
                        .getReference()
                        .child("profile_images/" + uid + ".jpg");

        ref.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot ->
                        ref.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                            // Firestore 에 반영
                            db.collection("profiles").document(uid)
                                    .update("profileImageUrl", downloadUri.toString())
                                    .addOnSuccessListener(aVoid -> {
                                        // ✅ 여기서 프래그먼트가 아직 살아있는지 다시 체크
                                        if (!isUiSafe()) {
                                            return;
                                        }

                                        CustomToast.success(getContext(), "프로필 사진이 변경되었어요.");

                                        // ✅ 안전하게 컨텍스트로 호출
                                        Glide.with(getContext())
                                                .load(downloadUri)
                                                .placeholder(R.drawable.ic_person_placeholder)
                                                .into(profileImageView);
                                    })
                                    .addOnFailureListener(e -> {
                                        if (isUiSafe())
                                            CustomToast.error(getContext(), "프로필 정보 저장에 실패했어요.");
                                    });
                        })
                )
                .addOnFailureListener(e -> {
                    if (isUiSafe())
                        CustomToast.error(getContext(), "사진 업로드에 실패했어요.");
                });
    }

    // ✅ 갤러리 결과 처리
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SELECT_PROFILE_IMAGE
                && resultCode == getActivity().RESULT_OK
                && data != null
                && data.getData() != null) {

            Uri imageUri = data.getData();
            // 미리보기
            profileImageView.setImageURI(imageUri);
            // 업로드
            uploadProfileImageToFirebase(imageUri);
        }
    }
}
