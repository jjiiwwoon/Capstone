package com.example.myapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Layout;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class TeamDetail extends AppCompatActivity {

    // 공용 레이아웃
    private StateLayout state;

    // 상단 정보
    private ImageView teamLogo, teamPhoto, introToggle;
    private TextView teamName, teamIntro, teamRegion, teamSkill, teamAge;
    private TextView teamActivityDay, teamHomeStadiumName, teamHomeStadiumAddress;

    // 기록 섹션
    private LinearLayout recordSection;
    private TextView tvGames, tvWins, tvDraws, tvLosses, tvGF, tvGA, tvWinRate;
    private TextView tvSeeDetails;

    // 멤버 타이틀
    private TextView tvMemberTitle;

    // 멤버(예전 단일 레이아웃 - 안 씀)
    private LinearLayout playerListLayout;

    // 파이어스토어
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private String teamId;
    private String currentUid;

    // 실시간 기록 리스너
    private ListenerRegistration recordListener;

    // 소개 접기
    private boolean isIntroExpanded = false;
    private String introFullText = "";

    // 이미지 표시 시점
    private boolean firstImageDrawn = false;
    private final android.os.Handler uiHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    // 내가 이 팀 소속인지
    private boolean isMyTeam = false;
    // 이 팀 주장/부주장
    private String captainUid;
    private String viceCaptainUid;

    // StateLayout 전환용
    private boolean teamLoaded = false;
    private boolean membersLoaded = false;
    private boolean firstContentShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.team_detail);

        teamId = getIntent().getStringExtra("teamId");
        if (TextUtils.isEmpty(teamId)) {
            CustomToast.error(this, "팀 정보를 불러올 수 없어요.");
            finish();
            return;
        }

        currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        bindViews();
        state.showLoading();

        // 내 프로필 한 번 읽어서 내가 이 팀 소속인지 먼저 체크
        db.collection("profiles").document(currentUid)
                .get()
                .addOnSuccessListener(profile -> {
                    if (profile.exists()) {
                        String myTeam = profile.getString("myTeam");
                        isMyTeam = teamId.equals(myTeam);
                    }
                    // 팀 실제 데이터 로딩
                    getTeamInfo(teamId);
                    loadPlayerList(teamId);
                    bindRecordSummary(teamId);
                })
                .addOnFailureListener(e -> {
                    // 프로필 못 읽어도 팀 정보는 보여줄 거라 그냥 진행
                    getTeamInfo(teamId);
                    loadPlayerList(teamId);
                    bindRecordSummary(teamId);
                });

        // 상세 기록 보기
        tvSeeDetails.setOnClickListener(v -> {
            Intent intent = new Intent(this, Records.class);
            intent.putExtra("myTeamId", teamId);
            startActivity(intent);
        });
    }

    private void bindViews() {
        state = findViewById(R.id.stateLayout);

        teamLogo = findViewById(R.id.teamLogo);
        teamPhoto = findViewById(R.id.teamPhoto);
        introToggle = findViewById(R.id.introToggle);

        teamName = findViewById(R.id.teamName);
        teamIntro = findViewById(R.id.teamIntro);
        teamRegion = findViewById(R.id.teamRegion);
        teamSkill = findViewById(R.id.teamSkill);
        teamAge = findViewById(R.id.teamAge);

        teamActivityDay = findViewById(R.id.teamActivityDay);
        teamHomeStadiumName = findViewById(R.id.teamHomeStadiumName);
        teamHomeStadiumAddress = findViewById(R.id.teamHomeStadiumAddress);

        recordSection = findViewById(R.id.recordSection);
        tvGames = findViewById(R.id.tvGames);
        tvWins = findViewById(R.id.tvWins);
        tvDraws = findViewById(R.id.tvDraws);
        tvLosses = findViewById(R.id.tvLosses);
        tvGF = findViewById(R.id.tvGF);
        tvGA = findViewById(R.id.tvGA);
        tvWinRate = findViewById(R.id.tvWinRate);
        tvSeeDetails = findViewById(R.id.tvSeeDetails);

        tvMemberTitle = findViewById(R.id.tvMemberTitle);
        playerListLayout = findViewById(R.id.playerListLayout);

        // 소개 토글
        introToggle.setOnClickListener(v -> {
            isIntroExpanded = !isIntroExpanded;
            if (isIntroExpanded) {
                teamIntro.setMaxLines(Integer.MAX_VALUE);
                teamIntro.setEllipsize(null);
                introToggle.setImageResource(R.drawable.ic_arrow_up);
            } else {
                teamIntro.setMaxLines(10);
                teamIntro.setEllipsize(TextUtils.TruncateAt.END);
                introToggle.setImageResource(R.drawable.ic_arrow_down);
            }
        });
    }

    private void getTeamInfo(String teamId) {
        final FirebaseFirestore db = FirebaseFirestore.getInstance();
        final DocumentSnapshot[] cacheHolder = new DocumentSnapshot[1];
        firstImageDrawn = false;

        // 캐시 먼저
        db.collection("teams").document(teamId)
                .get(Source.CACHE)
                .addOnSuccessListener(cacheSnap -> {
                    if (cacheSnap != null && cacheSnap.exists()) {
                        cacheHolder[0] = cacheSnap;
                        bindTeamTextFields(cacheSnap);
                        startImageLoads(cacheSnap);
                    }
                });

        // 서버 버전
        db.collection("teams").document(teamId)
                .get(Source.SERVER)
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) {
                        state.setEmptyMessage("팀 정보를 찾을 수 없어요.");
                        state.showEmpty();
                        return;
                    }
                    bindTeamTextFields(doc);
                    startImageLoads(doc);

                    teamLoaded = true;
                    tryShowContent();
                })
                .addOnFailureListener(e -> {
                    CustomToast.error(this, "팀 정보를 불러오지 못했어요.");
                    state.setEmptyMessage("팀 정보를 불러오지 못했어요.");
                    state.showEmpty();
                });
    }

    private void bindTeamTextFields(DocumentSnapshot doc) {
        String teamNameStr = doc.getString("teamName");
        String intro = doc.getString("intro");
        String region = doc.getString("region");
        String skill = doc.getString("skill");
        String ageRange = doc.getString("ageRange");

        String activityDayVal = doc.getString("activityDay");
        String homeStadiumNameVal = doc.getString("homeStadiumName");
        String stadiumAddressVal = doc.getString("stadium");

        String timeStart = doc.getString("timeStart");
        String timeEnd = doc.getString("timeEnd");

        captainUid = doc.getString("captainUID");
        viceCaptainUid = doc.getString("viceCaptainUID");

        teamName.setText(teamNameStr != null ? teamNameStr : "");
        introFullText = (intro != null ? intro : "");
        teamIntro.setText(introFullText);
        applyCollapsedIntroInline();

        teamRegion.setText(!TextUtils.isEmpty(region) ? region : "");
        // skillAverage가 있으면 그걸로 덮어주는 것도 가능
        if (doc.get("skillAverage") instanceof Number) {
            teamSkill.setText(String.valueOf(((Number) doc.get("skillAverage")).intValue()));
        } else {
            teamSkill.setText(!TextUtils.isEmpty(skill) ? skill : "");
        }
        teamAge.setText(!TextUtils.isEmpty(ageRange) ? ageRange : "");

        String activityDisplay = null;
        if (!TextUtils.isEmpty(activityDayVal)) {
            activityDisplay = (!TextUtils.isEmpty(timeStart) && !TextUtils.isEmpty(timeEnd))
                    ? activityDayVal + " | " + timeStart + " ~ " + timeEnd
                    : activityDayVal;
        } else if (!TextUtils.isEmpty(timeStart) && !TextUtils.isEmpty(timeEnd)) {
            activityDisplay = timeStart + " ~ " + timeEnd;
        }

        setTextOrGone(teamActivityDay, activityDisplay);
        setTextOrGone(teamHomeStadiumName, homeStadiumNameVal);
        setTextOrGone(teamHomeStadiumAddress, stadiumAddressVal);

        // 버튼 제거했으므로 여기서 더 이상 초대/탈퇴 관련 처리 없음
    }

    private void startImageLoads(DocumentSnapshot doc) {
        String logoUrl = doc.getString("logoUrl");
        String photoUrl = doc.getString("photoUrl");

        com.bumptech.glide.request.RequestOptions opts =
                new com.bumptech.glide.request.RequestOptions()
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.AUTOMATIC)
                        .dontAnimate();

        if (!TextUtils.isEmpty(photoUrl)) {
            Glide.with(this)
                    .load(photoUrl)
                    .thumbnail(0.25f)
                    .apply(opts)
                    .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                        @Override
                        public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                            if (!firstImageDrawn) {
                                state.showContent();
                                firstImageDrawn = true;
                                ensureIntroClampAfterContent();
                            }
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                            if (!firstImageDrawn) {
                                state.showContent();
                                firstImageDrawn = true;
                                ensureIntroClampAfterContent();
                            }
                            return false;
                        }
                    })
                    .placeholder(R.drawable.default_team_photo)
                    .into(teamPhoto);
        } else {
            teamPhoto.setImageResource(R.drawable.default_team_photo);
        }

        if (!TextUtils.isEmpty(logoUrl)) {
            Glide.with(this)
                    .load(logoUrl)
                    .thumbnail(0.25f)
                    .apply(opts)
                    .placeholder(R.drawable.ic_placeholder_circle)
                    .into(teamLogo);
        } else {
            teamLogo.setImageResource(R.drawable.ic_placeholder_circle);
        }
    }

    // ===== 기록 섹션 =====
    private void bindRecordSummary(String teamId) {
        if (recordListener != null) {
            recordListener.remove();
            recordListener = null;
        }

        db.collection("matches")
                .whereEqualTo("teamId", teamId)
                .whereEqualTo("status", "finished")
                .get(Source.SERVER)
                .addOnSuccessListener(this::renderRecordSummary)
                .addOnFailureListener(e -> recordSection.setVisibility(View.GONE));

        recordListener = db.collection("matches")
                .whereEqualTo("teamId", teamId)
                .whereEqualTo("status", "finished")
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) {
                        recordSection.setVisibility(View.GONE);
                        return;
                    }
                    renderRecordSummary(snap);
                });
    }

    private void renderRecordSummary(QuerySnapshot snap) {
        int games = 0, w = 0, d = 0, l = 0, gf = 0, ga = 0;

        for (DocumentSnapshot doc : snap.getDocuments()) {
            Integer sf = toInt(doc.get("scoreFor"));
            Integer sa = toInt(doc.get("scoreAgainst"));
            if (sf == null || sa == null) continue;

            games++;
            gf += sf;
            ga += sa;

            if (sf > sa) w++;
            else if (sf.equals(sa)) d++;
            else l++;
        }

        tvGames.setText(String.valueOf(games));
        tvWins.setText(String.valueOf(w));
        tvDraws.setText(String.valueOf(d));
        tvLosses.setText(String.valueOf(l));
        tvGF.setText(String.valueOf(gf));
        tvGA.setText(String.valueOf(ga));

        if (games > 0) {
            double winRate = (w * 100.0) / games;
            tvWinRate.setText(String.format(java.util.Locale.KOREAN, "%.1f%%", winRate));

            int color;
            if (winRate > 50.0) {
                color = Color.parseColor("#1E88E5");
            } else if (winRate == 50.0) {
                color = Color.parseColor("#000000");
            } else {
                color = Color.parseColor("#B71C1C");
            }
            tvWinRate.setTextColor(color);
        } else {
            tvWinRate.setText("-");
            tvWinRate.setTextColor(Color.parseColor("#546E7A"));
        }

        // 축구 색상 규칙 유지
        tvWins.setTextColor(Color.parseColor("#2E7D32"));
        tvDraws.setTextColor(Color.parseColor("#546E7A"));
        tvLosses.setTextColor(Color.parseColor("#B71C1C"));
        tvGF.setTextColor(Color.parseColor("#1E88E5"));
        tvGA.setTextColor(Color.parseColor("#D32F2F"));
        tvGames.setTextColor(Color.parseColor("#000000"));

        recordSection.setVisibility(View.VISIBLE);

        membersLoaded = true; // 멤버와는 별개지만 콘텐츠 노출 판단 돕도록
        tryShowContent();
    }

    // ===== 멤버 리스트 (포지션별 GridLayout) =====
    private void loadPlayerList(String teamId) {
        final View root = findViewById(android.R.id.content);
        if (root == null) return;

        final LinearLayout sectionFW = findViewById(R.id.sectionFW);
        final LinearLayout sectionMF = findViewById(R.id.sectionMF);
        final LinearLayout sectionDF = findViewById(R.id.sectionDF);
        final LinearLayout sectionGK = findViewById(R.id.sectionGK);

        final TextView tvFWHeader = findViewById(R.id.tvFWHeader);
        final TextView tvMFHeader = findViewById(R.id.tvMFHeader);
        final TextView tvDFHeader = findViewById(R.id.tvDFHeader);
        final TextView tvGKHeader = findViewById(R.id.tvGKHeader);

        final GridLayout fwGrid = findViewById(R.id.fwGrid);
        final GridLayout mfGrid = findViewById(R.id.mfGrid);
        final GridLayout dfGrid = findViewById(R.id.dfGrid);
        final GridLayout gkGrid = findViewById(R.id.gkGrid);

        if (playerListLayout != null) {
            playerListLayout.removeAllViews();
            playerListLayout.setVisibility(View.GONE);
        }

        if (fwGrid != null) fwGrid.removeAllViews();
        if (mfGrid != null) mfGrid.removeAllViews();
        if (dfGrid != null) dfGrid.removeAllViews();
        if (gkGrid != null) gkGrid.removeAllViews();

        if (sectionFW != null) sectionFW.setVisibility(View.VISIBLE);
        if (sectionMF != null) sectionMF.setVisibility(View.VISIBLE);
        if (sectionDF != null) sectionDF.setVisibility(View.VISIBLE);
        if (sectionGK != null) sectionGK.setVisibility(View.VISIBLE);

        db.collection("teams").document(teamId)
                .get()
                .addOnSuccessListener(teamSnap -> {
                    if (teamSnap == null || !teamSnap.exists()) return;

                    List<String> memberUids = (List<String>) teamSnap.get("members");
                    final String captainUID = teamSnap.getString("captainUID");
                    final String viceCaptainUID = teamSnap.getString("viceCaptainUID");

                    int totalCount = (memberUids == null) ? 0 : memberUids.size();
                    setMemberTitleCount(totalCount);

                    if (memberUids == null || memberUids.isEmpty()) {
                        if (tvFWHeader != null) tvFWHeader.setText("FW (0)");
                        if (tvMFHeader != null) tvMFHeader.setText("MF (0)");
                        if (tvDFHeader != null) tvDFHeader.setText("DF (0)");
                        if (tvGKHeader != null) tvGKHeader.setText("GK (0)");
                        membersLoaded = true;
                        tryShowContent();
                        return;
                    }

                    final List<DocumentSnapshot> fwDocs = new ArrayList<>();
                    final List<DocumentSnapshot> mfDocs = new ArrayList<>();
                    final List<DocumentSnapshot> dfDocs = new ArrayList<>();
                    final List<DocumentSnapshot> gkDocs = new ArrayList<>();

                    final int[] totalSkill = {0};
                    final int targetSize = memberUids.size();
                    final AtomicInteger loaded = new AtomicInteger(0);

                    for (String uid : memberUids) {
                        db.collection("profiles").document(uid)
                                .get()
                                .addOnSuccessListener(p -> {
                                    if (p != null && p.exists()) {
                                        Number sk = (Number) p.get("skill");
                                        if (sk != null) totalSkill[0] += sk.intValue();

                                        String pos = p.getString("position");
                                        String upper = (pos == null) ? "" : pos.trim().toUpperCase();
                                        switch (upper) {
                                            case "FW":
                                                fwDocs.add(p);
                                                break;
                                            case "MF":
                                                mfDocs.add(p);
                                                break;
                                            case "DF":
                                                dfDocs.add(p);
                                                break;
                                            case "GK":
                                                gkDocs.add(p);
                                                break;
                                            default:
                                                mfDocs.add(p);
                                                break;
                                        }
                                    }

                                    if (loaded.incrementAndGet() == targetSize) {
                                        if (teamSkill != null && targetSize > 0) {
                                            int averageSkill = Math.round(totalSkill[0] / (float) targetSize);
                                            teamSkill.setText(String.valueOf(averageSkill));
                                            db.collection("teams").document(teamId)
                                                    .update("skillAverage", averageSkill);
                                        }

                                        final LayoutInflater inflater = LayoutInflater.from(this);

                                        BiConsumer<DocumentSnapshot, GridLayout> addItem =
                                                (doc, grid) -> {
                                                    if (grid == null) return;
                                                    View item = inflater.inflate(R.layout.player_grid_item_plain, grid, false);

                                                    GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                                                    lp.width = 0;
                                                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                                                    lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
                                                    lp.setMargins(dp(6), dp(4), dp(6), dp(4));
                                                    item.setLayoutParams(lp);

                                                    ImageView img = item.findViewById(R.id.playerImage);
                                                    TextView name = item.findViewById(R.id.playerName);

                                                    String uidX = doc.getId();
                                                    String nickname = doc.getString("nickname");
                                                    String imageUrl = doc.getString("profileImageUrl");

                                                    StringBuilder label = new StringBuilder();
                                                    label.append(!TextUtils.isEmpty(nickname) ? nickname : "(이름 없음)");
                                                    if (!TextUtils.isEmpty(captainUID) && captainUID.equals(uidX)) {
                                                        label.append(" | 주장");
                                                    } else if (!TextUtils.isEmpty(viceCaptainUID) && viceCaptainUID.equals(uidX)) {
                                                        label.append(" | 부주장");
                                                    }
                                                    name.setText(label.toString());

                                                    if (!TextUtils.isEmpty(imageUrl)) {
                                                        Glide.with(this).load(imageUrl).into(img);
                                                    } else {
                                                        img.setImageResource(R.drawable.default_profile_image);
                                                    }

                                                    // 내가 주장일 때만 롱클릭 옵션
                                                    if (!TextUtils.isEmpty(captainUID) && currentUid.equals(captainUID) && isMyTeam) {
                                                        item.setOnLongClickListener(v -> {
                                                            String nn = (nickname == null) ? "(이름 없음)" : nickname;
                                                            FirebaseFirestore.getInstance().collection("teams").document(teamId)
                                                                    .get()
                                                                    .addOnSuccessListener(s -> {
                                                                        String latestVice = (s != null) ? s.getString("viceCaptainUID") : viceCaptainUID;
                                                                        showPlayerOptionsDialog(nn, uidX, teamId, latestVice, new TextView(this));
                                                                    });
                                                            return true;
                                                        });
                                                    }

                                                    grid.addView(item);
                                                };

                                        if (tvFWHeader != null) tvFWHeader.setText("FW (" + fwDocs.size() + ")");
                                        if (tvMFHeader != null) tvMFHeader.setText("MF (" + mfDocs.size() + ")");
                                        if (tvDFHeader != null) tvDFHeader.setText("DF (" + dfDocs.size() + ")");
                                        if (tvGKHeader != null) tvGKHeader.setText("GK (" + gkDocs.size() + ")");

                                        if (fwGrid != null) fwGrid.setColumnCount(2);
                                        if (mfGrid != null) mfGrid.setColumnCount(2);
                                        if (dfGrid != null) dfGrid.setColumnCount(2);
                                        if (gkGrid != null) gkGrid.setColumnCount(2);

                                        for (DocumentSnapshot d : fwDocs) addItem.accept(d, fwGrid);
                                        for (DocumentSnapshot d : mfDocs) addItem.accept(d, mfGrid);
                                        for (DocumentSnapshot d : dfDocs) addItem.accept(d, dfGrid);
                                        for (DocumentSnapshot d : gkDocs) addItem.accept(d, gkGrid);

                                        membersLoaded = true;
                                        tryShowContent();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    if (loaded.incrementAndGet() == targetSize) {
                                        membersLoaded = true;
                                        tryShowContent();
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    CustomToast.error(this, "팀 정보를 불러오지 못했어요.");
                    membersLoaded = true;
                    tryShowContent();
                });
    }

    private void showPlayerOptionsDialog(String nickname, String uid, String teamId, String viceCaptainUid, TextView dummy) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_player_options, null);
        TextView txtName = dialogView.findViewById(R.id.txtPlayerName);
        LinearLayout btnAssignVice = dialogView.findViewById(R.id.btnAssignVice);
        LinearLayout btnKickPlayer = dialogView.findViewById(R.id.btnKickPlayer);
        LinearLayout btnAssignCaptain = dialogView.findViewById(R.id.btnAssignCaptain);
        TextView assignViceText = dialogView.findViewById(R.id.txtAssignViceText);

        txtName.setText(nickname);

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.CustomDialog)
                .setView(dialogView)
                .create();

        // 주장 위임
        btnAssignCaptain.setOnClickListener(v -> {
            dialog.dismiss();
            if (currentUid.equals(uid)) {
                CustomToast.warning(this, "자기 자신에게는 위임할 수 없어요.");
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("주장 권한 위임")
                    .setMessage(nickname + " 님에게 주장 권한을 위임하시겠습니까?")
                    .setPositiveButton("예", (d, which) -> {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("captainUID", uid);
                        if (uid.equals(viceCaptainUid)) {
                            updates.put("viceCaptainUID", null);
                        }
                        db.collection("teams").document(teamId)
                                .update(updates)
                                .addOnSuccessListener(aVoid -> {
                                    CustomToast.success(this, "주장 권한이 위임되었어요.");
                                    loadPlayerList(teamId);
                                })
                                .addOnFailureListener(e -> CustomToast.error(this, "위임에 실패했어요."));
                    })
                    .setNegativeButton("아니오", null)
                    .show();
        });

        // 부주장
        if (uid.equals(viceCaptainUid)) {
            assignViceText.setText("부주장 권한 해제");
            btnAssignVice.setOnClickListener(v -> {
                dialog.dismiss();
                new AlertDialog.Builder(this)
                        .setTitle("부주장 권한 해제")
                        .setMessage(nickname + " 님의 부주장 권한을 해제하시겠습니까?")
                        .setPositiveButton("예", (d, which) -> {
                            db.collection("teams").document(teamId)
                                    .update("viceCaptainUID", null)
                                    .addOnSuccessListener(aVoid -> {
                                        CustomToast.success(this, "부주장 권한이 해제되었어요.");
                                        loadPlayerList(teamId);
                                    })
                                    .addOnFailureListener(e -> CustomToast.error(this, "해제에 실패했어요."));
                        })
                        .setNegativeButton("아니오", null)
                        .show();
            });
        } else {
            assignViceText.setText("부주장 권한 부여");
            btnAssignVice.setOnClickListener(v -> {
                dialog.dismiss();
                if (viceCaptainUid != null && !viceCaptainUid.isEmpty()) {
                    CustomToast.warning(this, "이미 부주장이 존재해요.");
                    return;
                }
                new AlertDialog.Builder(this)
                        .setTitle("부주장 권한 부여")
                        .setMessage(nickname + " 님에게 부주장 권한을 부여하시겠습니까?")
                        .setPositiveButton("예", (d, which) -> {
                            db.collection("teams").document(teamId)
                                    .update("viceCaptainUID", uid)
                                    .addOnSuccessListener(aVoid -> {
                                        CustomToast.success(this, "부주장 권한이 부여되었어요.");
                                        loadPlayerList(teamId);
                                    })
                                    .addOnFailureListener(e -> CustomToast.error(this, "권한 부여에 실패했어요."));
                        })
                        .setNegativeButton("아니오", null)
                        .show();
            });
        }

        // 방출
        btnKickPlayer.setOnClickListener(v -> {
            dialog.dismiss();
            if (currentUid.equals(uid)) {
                CustomToast.warning(this, "자기 자신은 방출할 수 없어요.");
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("팀원 방출")
                    .setMessage(nickname + " 님을 팀에서 방출하시겠습니까?")
                    .setPositiveButton("예", (d, which) -> {
                        db.collection("teams").document(teamId)
                                .get()
                                .addOnSuccessListener(snapshot -> {
                                    List<String> members = (List<String>) snapshot.get("members");
                                    if (members != null && members.contains(uid)) {
                                        members.remove(uid);
                                        db.collection("teams").document(teamId)
                                                .update("members", members)
                                                .addOnSuccessListener(aVoid -> {
                                                    db.collection("profiles").document(uid)
                                                            .update("myTeam", null)
                                                            .addOnSuccessListener(unused -> {
                                                                CustomToast.success(this, "방출되었어요.");
                                                                loadPlayerList(teamId);
                                                            })
                                                            .addOnFailureListener(e -> CustomToast.error(this, "프로필 업데이트에 실패했어요."));
                                                })
                                                .addOnFailureListener(e -> CustomToast.error(this, "방출에 실패했어요."));
                                    }
                                })
                                .addOnFailureListener(e -> CustomToast.error(this, "팀 정보를 불러오지 못했어요."));
                    })
                    .setNegativeButton("아니오", null)
                    .show();
        });

        dialog.show();
    }

    private void setMemberTitleCount(int count) {
        if (tvMemberTitle != null) {
            tvMemberTitle.setText("팀 멤버 (" + count + ")");
        }
    }

    private void applyCollapsedIntroInline() {
        isIntroExpanded = false;
        teamIntro.setMaxLines(Integer.MAX_VALUE);
        teamIntro.setEllipsize(null);
        introToggle.setImageResource(R.drawable.ic_arrow_down);
        introToggle.setVisibility(View.GONE);

        if (teamIntro.getLayout() != null) {
            int total = teamIntro.getLayout().getLineCount();
            applyClampOrHide(total);
            return;
        }

        teamIntro.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (teamIntro.getLayout() != null) {
                    int total = teamIntro.getLayout().getLineCount();
                    applyClampOrHide(total);
                    teamIntro.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            }
        });

        uiHandler.postDelayed(() -> {
            if (teamIntro.getLayout() != null) {
                int total = teamIntro.getLayout().getLineCount();
                applyClampOrHide(total);
            }
        }, 300);
    }

    private void applyClampOrHide(int totalLines) {
        if (totalLines > 10) {
            teamIntro.setMaxLines(10);
            teamIntro.setEllipsize(TextUtils.TruncateAt.END);
            introToggle.setVisibility(View.VISIBLE);
            introToggle.setImageResource(R.drawable.ic_arrow_down);
            isIntroExpanded = false;
        } else {
            teamIntro.setMaxLines(Integer.MAX_VALUE);
            teamIntro.setEllipsize(null);
            introToggle.setVisibility(View.GONE);
            isIntroExpanded = false;
        }
    }

    private void ensureIntroClampAfterContent() {
        if (teamIntro == null) return;
        teamIntro.post(() -> {
            Layout layout = teamIntro.getLayout();
            if (layout != null) {
                applyClampOrHide(layout.getLineCount());
            } else {
                uiHandler.postDelayed(() -> {
                    Layout l2 = teamIntro.getLayout();
                    if (l2 != null) applyClampOrHide(l2.getLineCount());
                }, 100);
            }
        });
    }

    private void setTextOrGone(TextView tv, String value) {
        if (tv == null) return;
        if (TextUtils.isEmpty(value)) {
            tv.setVisibility(View.GONE);
        } else {
            tv.setText(value);
            tv.setVisibility(View.VISIBLE);
        }
    }

    private Integer toInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void tryShowContent() {
        if (firstContentShown) return;
        if (teamLoaded && membersLoaded) {
            firstContentShown = true;
            state.showContent();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (recordListener != null) {
            recordListener.remove();
            recordListener = null;
        }
    }
}
