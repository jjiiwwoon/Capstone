// src/main/java/com/example/myapp/MyTeam.java
package com.example.myapp;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MyTeam extends Fragment {

    // StateLayout
    private StateLayout state;

    // 상단 정보
    private ImageView teamLogo, teamPhoto, introToggle;
    private TextView teamName, teamIntro, teamRegion, teamSkill, teamAge;
    // 일정 카드 전용 로딩/콘텐츠 컨테이너
    private View scheduleContent;
    private View scheduleLoading;

    private TextView tvMemberTitle;
    // 활동 날짜/구장
    private TextView teamActivityDay, teamHomeStadiumName, teamHomeStadiumAddress;

    // 하단 버튼
    private Button btnInvite, btnLeaveTeam;

    // 멤버 리스트 (이제 안 쓰지만 변수는 유지)
    private LinearLayout playerListLayout;

    // 기록 섹션
    private LinearLayout recordSection;
    private TextView tvGames, tvWins, tvDraws, tvLosses, tvGF, tvGA, tvSeeDetails;

    // ✅ 일정 섹션(미리보기) - 재사용 카드(view_next_schedule_card.xml)를 include로 사용
    private View nextScheduleCard;
    private TextView btnSeeAllSchedule;
    private TextView tvNextDateChip, tvScore, tvHomeName, tvAwayName, tvPlace, tvAddress;
    private ImageView imgHomeLogo, imgAwayLogo;

    private TextView tvWinRate;
    private String teamId;

    private static final int REQUEST_SELECT_TEAM_PHOTO = 101;
    private Uri selectedTeamPhotoUri;
    private boolean isIntroExpanded = false;
    private String introFullText = "";

    // 실시간 리스너
    private ListenerRegistration recordListener;

    // 상단에 필드 추가
    private LinearLayout nextScheduleContainer;

    // 다가오는 일정 로딩 상태 캐시
    private boolean upcomingLoadedOnce = false;
    private DocumentSnapshot upcomingCache = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.my_team, container, false);

        // onCreateView() 안에 바인딩 추가
        nextScheduleContainer = view.findViewById(R.id.nextScheduleContainer);

        // ===== StateLayout =====
        state = view.findViewById(R.id.stateLayout);
        state.showLoading();
        scheduleContent = view.findViewById(R.id.scheduleContent);
        scheduleLoading = view.findViewById(R.id.scheduleLoading);

        tvMemberTitle = view.findViewById(R.id.tvMemberTitle);

        // ===== 뷰 바인딩 =====
        teamLogo     = view.findViewById(R.id.teamLogo);
        teamPhoto    = view.findViewById(R.id.teamPhoto);
        introToggle  = view.findViewById(R.id.introToggle);

        teamName     = view.findViewById(R.id.teamName);
        teamIntro    = view.findViewById(R.id.teamIntro);
        teamRegion   = view.findViewById(R.id.teamRegion);
        teamSkill    = view.findViewById(R.id.teamSkill);
        teamAge      = view.findViewById(R.id.teamAge);

        teamActivityDay        = view.findViewById(R.id.teamActivityDay);
        teamHomeStadiumName    = view.findViewById(R.id.teamHomeStadiumName);
        teamHomeStadiumAddress = view.findViewById(R.id.teamHomeStadiumAddress);

        btnInvite    = view.findViewById(R.id.btnInvite);
        btnLeaveTeam = view.findViewById(R.id.btnLeaveTeam);

        playerListLayout = view.findViewById(R.id.playerListLayout);

        recordSection = view.findViewById(R.id.recordSection);
        tvGames  = view.findViewById(R.id.tvGames);
        tvWins   = view.findViewById(R.id.tvWins);
        tvDraws  = view.findViewById(R.id.tvDraws);
        tvLosses = view.findViewById(R.id.tvLosses);
        tvGF     = view.findViewById(R.id.tvGF);
        tvGA     = view.findViewById(R.id.tvGA);
        tvSeeDetails = view.findViewById(R.id.tvSeeDetails);
        tvWinRate = view.findViewById(R.id.tvWinRate);

        // ✅ 재사용 일정 카드(included) 바인딩
        nextScheduleCard = view.findViewById(R.id.nextScheduleCard);
        btnSeeAllSchedule = view.findViewById(R.id.btnSeeAllSchedule);
        tvNextDateChip = view.findViewById(R.id.tvNextDateChip);
        tvScore        = view.findViewById(R.id.tvScore);
        tvHomeName     = view.findViewById(R.id.tvHomeName);
        tvAwayName     = view.findViewById(R.id.tvAwayName);
        tvPlace        = view.findViewById(R.id.tvPlace);
        tvAddress      = view.findViewById(R.id.tvAddress);
        imgHomeLogo    = view.findViewById(R.id.imgHomeLogo);
        imgAwayLogo    = view.findViewById(R.id.imgAwayLogo);

        if (nextScheduleCard != null) nextScheduleCard.setVisibility(View.GONE); // ✅ 초기 숨김

        // 상세보기 → Records
        tvSeeDetails.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), Records.class);
            intent.putExtra("myTeamId", teamId);
            startActivity(intent);
        });

        // 소개 접기/펼치기 (기본 10줄 기준)
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

        // 팀 사진 변경
        teamPhoto.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_SELECT_TEAM_PHOTO);
        });

        // ===== 팀 여부 판별 =====
        String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore.getInstance().collection("profiles").document(currentUid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        teamId = documentSnapshot.getString("myTeam");
                        if (!TextUtils.isEmpty(teamId)) {
                            getTeamInfo(teamId);
                        } else {
                            state.setEmptyMessage("소속되어 있는 팀이 없습니다.\n팀에 가입하거나 팀을 생성하세요");
                            state.showEmpty();
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    CustomToast.error(requireContext(),
                            "프로필을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.");
                    state.setEmptyMessage("불러오기에 실패했어요.");
                    state.showEmpty();
                });

        // ===== 버튼 동작 =====
        // ‘전체보기’ & 미리보기 카드 → Schedule
        View.OnClickListener goSchedule = v -> startActivity(new Intent(getContext(), Schedule.class));
        if (btnSeeAllSchedule != null) btnSeeAllSchedule.setOnClickListener(goSchedule);
        if (nextScheduleCard != null) nextScheduleCard.setOnClickListener(goSchedule);

        btnInvite.setOnClickListener(v -> {
            View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_invite_user, null);
            EditText editNickname = dialogView.findViewById(R.id.editNickname);
            Button btnSendInvite = dialogView.findViewById(R.id.btnSendInvite);
            AlertDialog dialog = new AlertDialog.Builder(getContext()).setView(dialogView).create();

            btnSendInvite.setOnClickListener(v2 -> {
                String nickname = editNickname.getText().toString().trim();
                if (nickname.isEmpty()) {
                    CustomToast.warning(requireContext(), "닉네임을 입력해 주세요.");
                    return;
                }
                FirebaseFirestore.getInstance().collection("profiles")
                        .whereEqualTo("nickname", nickname)
                        .get()
                        .addOnSuccessListener(query -> {
                            if (!query.isEmpty()) {
                                String receiverUid = query.getDocuments().get(0).getId();
                                sendInviteMessage(receiverUid);
                                dialog.dismiss();
                            } else {
                                CustomToast.warning(requireContext(), "해당 닉네임의 유저가 없어요.");
                            }
                        })
                        .addOnFailureListener(err -> {
                            CustomToast.error(requireContext(), "검색에 실패했어요. 다시 시도해 주세요.");
                        });
            });
            dialog.show();
        });

        btnLeaveTeam.setOnClickListener(v -> onClickLeaveTeam());

        return view;
    }

    private void showScheduleLoading(boolean show) {
        if (scheduleLoading != null) scheduleLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        if (scheduleContent != null) scheduleContent.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    // ✅ 자동 새로고침(30초 간격) — 화면이 켜져 있을 때만
    private void startUpcomingAutoRefresh() {
        uiHandler.removeCallbacks(upcomingRefreshRunnable);
        uiHandler.postDelayed(upcomingRefreshRunnable, 30_000);
    }

    private void stopUpcomingAutoRefresh() {
        uiHandler.removeCallbacks(upcomingRefreshRunnable);
    }

    // 클래스 내부에 위치(필드로 추가되는 Runnable)
    private final Runnable upcomingRefreshRunnable = new Runnable() {
        @Override public void run() {
            if (isAdded() && !TextUtils.isEmpty(teamId)) {
                loadUpcomingSchedule(teamId);            // now 기준 재평가
                uiHandler.postDelayed(this, 30_000);    // 30초 주기
            }
        }
    };

    @Override
    public void onStart() {
        super.onStart();
        if (!TextUtils.isEmpty(teamId)) {
            // 기록 요약은 그대로
            bindRecordSummary(teamId);

            // ✅ 다가오는 일정 즉시 1회 로드
            loadUpcomingSchedule(teamId);

            // ✅ 화면 열려있는 동안 30초마다 재평가(문서 값이 안 바뀌어도 now 기준으로 자동 전환)
            startUpcomingAutoRefresh();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        // ✅ 자동 새로고침 중단
        stopUpcomingAutoRefresh();
    }

    @Override
    public void onDestroyView() {
        if (recordListener != null) {
            recordListener.remove();
            recordListener = null;
        }
        super.onDestroyView();
    }

    private void setMemberTitleCount(int count) {
        if (tvMemberTitle != null) {
            tvMemberTitle.setText("팀 멤버 (" + count + ")");
        }
    }

    // ===== 사진 업로드 =====
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SELECT_TEAM_PHOTO && resultCode == getActivity().RESULT_OK && data != null) {
            selectedTeamPhotoUri = data.getData();
            if (selectedTeamPhotoUri != null) {
                teamPhoto.setImageURI(selectedTeamPhotoUri);
                uploadTeamPhotoToFirebase(selectedTeamPhotoUri);
            }
        }
    }

    private void uploadTeamPhotoToFirebase(Uri imageUri) {
        if (teamId == null || imageUri == null) return;

        ProgressDialog progressDialog = new ProgressDialog(getContext());
        progressDialog.setMessage("사진 업로드 중...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(requireContext().getContentResolver(), imageUri);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
            byte[] imageData = baos.toByteArray();

            StorageReference storageRef = FirebaseStorage.getInstance().getReference()
                    .child("team_photos/" + teamId + "_photo.jpg");

            storageRef.putBytes(imageData)
                    .addOnSuccessListener(taskSnapshot -> storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        String photoUrl = uri.toString();

                        FirebaseFirestore.getInstance().collection("teams").document(teamId)
                                .update("photoUrl", photoUrl)
                                .addOnSuccessListener(aVoid -> {
                                    progressDialog.dismiss();
                                    CustomToast.success(requireContext(), "팀 사진이 변경되었어요.");
                                    Glide.with(getContext()).load(photoUrl).into(teamPhoto);
                                })
                                .addOnFailureListener(e -> {
                                    progressDialog.dismiss();
                                    CustomToast.error(requireContext(), "사진 URL 저장에 실패했어요.");
                                });
                    }))
                    .addOnFailureListener(e -> {
                        progressDialog.dismiss();
                        CustomToast.error(requireContext(), "사진 업로드에 실패했어요.");
                    });

        } catch (IOException e) {
            progressDialog.dismiss();
            CustomToast.error(requireContext(), "이미지 처리에 실패했어요.");
        }
    }

    // ===== 초대 메시지 =====
    private void sendInviteMessage(String receiverUid) {
        String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        List<String> uids = new ArrayList<>();
        uids.add(currentUid);
        uids.add(receiverUid);
        Collections.sort(uids);
        String roomId = uids.get(0) + "_" + uids.get(1);

        Map<String, Object> message = new HashMap<>();
        message.put("senderId", currentUid);
        message.put("content", "⚽ 우리 팀에 초대합니다!\n수락하시겠어요?");
        message.put("messageType", "invite");
        message.put("timestamp", System.currentTimeMillis());
        message.put("teamId", teamId);

        Map<String, Object> chatRoom = new HashMap<>();
        chatRoom.put("participants", uids);
        chatRoom.put("lastMessage", "[초대 메시지]");
        chatRoom.put("lastTimestamp", System.currentTimeMillis());

        db.collection("chatRooms").document(roomId).set(chatRoom);

        db.collection("chatRooms").document(roomId)
                .collection("messages")
                .add(message)
                .addOnSuccessListener(doc -> {
                    CustomToast.success(requireContext(), "초대 메시지를 보냈어요.");
                })
                .addOnFailureListener(e -> {
                    CustomToast.error(requireContext(), "초대 메시지 전송에 실패했어요.");
                });
    }

    // ===== 팀 탈퇴 처리 =====
    private void onClickLeaveTeam() {
        String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        new AlertDialog.Builder(getContext())
                .setTitle("팀 탈퇴 확인")
                .setMessage("정말 팀에서 탈퇴하시겠습니까?")
                .setPositiveButton("예", (dialogInterface, i) -> {
                    FirebaseFirestore db = FirebaseFirestore.getInstance();
                    db.collection("profiles").document(currentUid)
                            .get()
                            .addOnSuccessListener(profileSnapshot -> {
                                String tid = profileSnapshot.getString("myTeam");
                                if (TextUtils.isEmpty(tid)) {
                                    CustomToast.info(requireContext(), "소속된 팀이 없어요.");
                                    return;
                                }
                                db.collection("teams").document(tid)
                                        .get()
                                        .addOnSuccessListener(teamSnapshot -> {
                                            List<String> members = (List<String>) teamSnapshot.get("members");
                                            String captainUid = teamSnapshot.getString("captainUID");
                                            String viceCaptainUid = teamSnapshot.getString("viceCaptainUID");

                                            if (currentUid.equals(captainUid)) {
                                                CustomToast.warning(requireContext(),
                                                        "팀장은 탈퇴할 수 없어요.\n먼저 '주장 위임'을 해 주세요.");
                                                return;
                                            }
                                            if (members != null && members.contains(currentUid)) {
                                                members.remove(currentUid);
                                                Map<String, Object> updateMap = new HashMap<>();
                                                updateMap.put("members", members);
                                                if (currentUid.equals(viceCaptainUid)) {
                                                    updateMap.put("viceCaptainUID", null);
                                                }
                                                db.collection("teams").document(tid)
                                                        .update(updateMap)
                                                        .addOnSuccessListener(aVoid -> db.collection("profiles").document(currentUid)
                                                                .update("myTeam", null)
                                                                .addOnSuccessListener(unused -> {
                                                                    CustomToast.success(requireContext(), "팀에서 탈퇴되었어요.");
                                                                    teamId = null;

                                                                    if (nextScheduleCard != null) nextScheduleCard.setVisibility(View.GONE);
                                                                    state.setEmptyMessage("팀이 없어서 마이팀 화면을 보여줄 수 없어요.\n먼저 '팀 만들기'로 팀을 생성해 주세요.");
                                                                    state.showEmpty();
                                                                })
                                                                .addOnFailureListener(e -> {
                                                                    CustomToast.error(requireContext(), "프로필 업데이트에 실패했어요.");
                                                                }))
                                                        .addOnFailureListener(e -> {
                                                            CustomToast.error(requireContext(), "팀 정보 업데이트에 실패했어요.");
                                                        });
                                            }
                                        })
                                        .addOnFailureListener(e -> {
                                            CustomToast.error(requireContext(), "팀 정보를 불러오지 못했어요.");
                                        });
                            })
                            .addOnFailureListener(e -> {
                                CustomToast.error(requireContext(), "프로필 정보를 불러오지 못했어요.");
                            });
                })
                .setNegativeButton("아니오", null)
                .show();
    }

    private void getTeamInfo(String teamId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        final long now = System.currentTimeMillis();
        firstImageDrawn = false; // 새 진입 시 초기화

        // 팀 문서 참조
        final com.google.firebase.firestore.DocumentReference teamRef =
                db.collection("teams").document(teamId);

        // 0) 캐시 먼저 → 이미지 프리로드/바인딩을 최대한 빨리 시작
        teamRef.get(Source.CACHE)
                .addOnSuccessListener(cacheSnap -> {
                    if (!isAdded() || cacheSnap == null || !cacheSnap.exists()) return;
                    // 텍스트·보조 필드 먼저 바인딩
                    bindTeamTextFields(cacheSnap);
                    // 이미지 프리로드 및 바인딩 시작 (화면은 아직 showContent 안 함)
                    startImageLoads(cacheSnap);
                });

        // 1) 서버 최신 → 최종 텍스트/이미지 갱신
        teamRef.get(Source.SERVER)
                .addOnSuccessListener(documentSnapshot -> {
                    if (!isAdded() || documentSnapshot == null || !documentSnapshot.exists()) {
                        state.setEmptyMessage("팀 정보를 찾을 수 없어요.");
                        state.showEmpty();
                        return;
                    }

                    // 텍스트 필드 최신으로 갱신
                    bindTeamTextFields(documentSnapshot);

                    // 이미지 로딩 (listener에서 첫 이미지가 그려지면 showContent())
                    startImageLoads(documentSnapshot);

                    // 서브 데이터 병렬 시작 (이미지와 동시에 진행)
                    loadPlayerList(teamId);
                    bindRecordSummary(teamId);
                    loadUpcomingSchedule(teamId);

                    // 안전망: 600ms 내에 이미지 콜백이 안 오면 일단 화면 표시
                    uiHandler.postDelayed(() -> {
                        if (!firstImageDrawn && isAdded()) {
                            state.showContent();
                        }
                    }, 600);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    CustomToast.error(requireContext(), "팀 정보를 불러오지 못했어요.");
                    state.setEmptyMessage("팀 정보를 불러오지 못했어요.");
                    state.showEmpty();
                });
    }


    // ===== 기록 섹션 =====
    private void bindRecordSummary(String teamId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        if (recordListener != null) {
            recordListener.remove();
            recordListener = null;
        }

        db.collection("matches")
                .whereEqualTo("teamId", teamId)
                .whereEqualTo("status", "finished")
                .get(Source.SERVER)
                .addOnSuccessListener(this::renderRecordSummary)
                .addOnFailureListener(e -> {
                    if (isAdded()) recordSection.setVisibility(View.GONE);
                });

        recordListener = db.collection("matches")
                .whereEqualTo("teamId", teamId)
                .whereEqualTo("status", "finished")
                .addSnapshotListener((snap, e) -> {
                    if (!isAdded()) return;
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

        // ✅ 승률 계산: (승 / 경기수) * 100
        if (tvWinRate != null) {
            if (games > 0) {
                double winRate = (w * 100.0) / games;
                tvWinRate.setText(String.format(java.util.Locale.KOREAN, "%.1f%%", winRate));

                // ✅ 색상 조건
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
                tvWinRate.setTextColor(Color.parseColor("#546E7A")); // 회색 (경기 없음)
            }
        }

        // 기존 색상 유지
        tvWins.setTextColor(Color.parseColor("#2E7D32"));
        tvDraws.setTextColor(Color.parseColor("#546E7A"));
        tvLosses.setTextColor(Color.parseColor("#B71C1C"));
        tvGF.setTextColor(Color.parseColor("#1E88E5"));
        tvGA.setTextColor(Color.parseColor("#D32F2F"));
        tvGames.setTextColor(Color.parseColor("#000000"));

        recordSection.setVisibility(View.VISIBLE);
    }

    private Integer toInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return null; }
    }

    // ===== 멤버 리스트 (RecyclerView + 메모리 최적화 버전) =====
    private void loadPlayerList(String teamId) {
        if (!isAdded()) return;
        final View root = getView();
        if (root == null) return;

        // 1. 기존에 쓰던 무거운 XML 뷰들을 화면에서 완전히 숨김 (안전 장치)
        View oldFw = root.findViewById(R.id.sectionFW); if(oldFw != null) oldFw.setVisibility(View.GONE);
        View oldMf = root.findViewById(R.id.sectionMF); if(oldMf != null) oldMf.setVisibility(View.GONE);
        View oldDf = root.findViewById(R.id.sectionDF); if(oldDf != null) oldDf.setVisibility(View.GONE);
        View oldGk = root.findViewById(R.id.sectionGK); if(oldGk != null) oldGk.setVisibility(View.GONE);
        if (playerListLayout != null) playerListLayout.setVisibility(View.GONE);

        // 2. 방금 xml에 추가한 똑똑한 RecyclerView 찾기
        androidx.recyclerview.widget.RecyclerView recyclerView = root.findViewById(R.id.recyclerTeamMembers);
        if (recyclerView == null) return;

        FirebaseFirestore.getInstance().collection("teams").document(teamId)
                .get()
                .addOnSuccessListener(teamSnap -> {
                    if (!isAdded() || teamSnap == null || !teamSnap.exists()) return;

                    List<String> memberUids = (List<String>) teamSnap.get("members");
                    final String captainUid = teamSnap.getString("captainUID");
                    final String viceCaptainUid = teamSnap.getString("viceCaptainUID");
                    final String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

                    int totalCount = (memberUids == null) ? 0 : memberUids.size();
                    setMemberTitleCount(totalCount);

                    // 멤버가 0명일 때: 빈 데이터로 헤더만 만들어서 어댑터에 넘김
                    if (memberUids == null || memberUids.isEmpty()) {
                        TeamMemberAdapter adapter = new TeamMemberAdapter(captainUid, viceCaptainUid, currentUid, null);
                        List<TeamMemberAdapter.MemberItem> emptyItems = new ArrayList<>();
                        emptyItems.add(new TeamMemberAdapter.MemberItem("FW (0)"));
                        emptyItems.add(new TeamMemberAdapter.MemberItem("MF (0)"));
                        emptyItems.add(new TeamMemberAdapter.MemberItem("DF (0)"));
                        emptyItems.add(new TeamMemberAdapter.MemberItem("GK (0)"));
                        setupRecyclerView(recyclerView, adapter, emptyItems);
                        return;
                    }

                    // N+1 문제 해결을 위한 10개 단위 청킹(Chunking)
                    List<com.google.android.gms.tasks.Task<QuerySnapshot>> tasks = new ArrayList<>();
                    for (int i = 0; i < memberUids.size(); i += 10) {
                        List<String> chunk = memberUids.subList(i, Math.min(i + 10, memberUids.size()));
                        tasks.add(FirebaseFirestore.getInstance().collection("profiles")
                                .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                                .get());
                    }

                    com.google.android.gms.tasks.Tasks.whenAllSuccess(tasks).addOnSuccessListener(results -> {
                        if (!isAdded()) return;

                        int totalSkill = 0;
                        int validSkillCount = 0;

                        List<DocumentSnapshot> fwDocs = new ArrayList<>();
                        List<DocumentSnapshot> mfDocs = new ArrayList<>();
                        List<DocumentSnapshot> dfDocs = new ArrayList<>();
                        List<DocumentSnapshot> gkDocs = new ArrayList<>();

                        for (Object result : results) {
                            QuerySnapshot snap = (QuerySnapshot) result;
                            for (DocumentSnapshot p : snap.getDocuments()) {
                                Number sk = (Number) p.get("skill");
                                if (sk != null) {
                                    totalSkill += sk.intValue();
                                    validSkillCount++;
                                }
                                String pos = p.getString("position");
                                String upper = (pos == null) ? "" : pos.trim().toUpperCase();
                                switch (upper) {
                                    case "FW": fwDocs.add(p); break;
                                    case "MF": mfDocs.add(p); break;
                                    case "DF": dfDocs.add(p); break;
                                    case "GK": gkDocs.add(p); break;
                                    default:   mfDocs.add(p); break;
                                }
                            }
                        }

                        // 평균 실력 업데이트
                        if (teamSkill != null && validSkillCount > 0) {
                            int averageSkill = Math.round((float) totalSkill / validSkillCount);
                            teamSkill.setText(String.valueOf(averageSkill));
                            FirebaseFirestore.getInstance().collection("teams").document(teamId)
                                    .update("skillAverage", averageSkill);
                        }

                        // ✅ 기존의 복잡한 UI 생성 코드 대신, 데이터를 규격에 맞춰 조립만 합니다.
                        List<TeamMemberAdapter.MemberItem> items = new ArrayList<>();

                        items.add(new TeamMemberAdapter.MemberItem("FW (" + fwDocs.size() + ")"));
                        for (DocumentSnapshot d : fwDocs) items.add(new TeamMemberAdapter.MemberItem(d.getId(), d.getString("nickname"), d.getString("profileImageUrl")));

                        items.add(new TeamMemberAdapter.MemberItem("MF (" + mfDocs.size() + ")"));
                        for (DocumentSnapshot d : mfDocs) items.add(new TeamMemberAdapter.MemberItem(d.getId(), d.getString("nickname"), d.getString("profileImageUrl")));

                        items.add(new TeamMemberAdapter.MemberItem("DF (" + dfDocs.size() + ")"));
                        for (DocumentSnapshot d : dfDocs) items.add(new TeamMemberAdapter.MemberItem(d.getId(), d.getString("nickname"), d.getString("profileImageUrl")));

                        items.add(new TeamMemberAdapter.MemberItem("GK (" + gkDocs.size() + ")"));
                        for (DocumentSnapshot d : gkDocs) items.add(new TeamMemberAdapter.MemberItem(d.getId(), d.getString("nickname"), d.getString("profileImageUrl")));

                        // 어댑터 생성 및 롱클릭(권한 위임/강퇴 다이얼로그) 리스너 연결
                        TeamMemberAdapter adapter = new TeamMemberAdapter(captainUid, viceCaptainUid, currentUid, (nickname, uid) -> {
                            FirebaseFirestore.getInstance().collection("teams").document(teamId)
                                    .get()
                                    .addOnSuccessListener(s -> {
                                        String latestVice = (s != null) ? s.getString("viceCaptainUID") : viceCaptainUid;
                                        showPlayerOptionsDialog(nickname, uid, teamId, latestVice, new TextView(requireContext()));
                                    });
                        });

                        // 3. 만들어진 어댑터와 데이터를 RecyclerView에 장착!
                        setupRecyclerView(recyclerView, adapter, items);

                    }).addOnFailureListener(e -> {
                        if (isAdded()) CustomToast.error(requireContext(), "선수 정보를 불러오지 못했어요.");
                    });
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) CustomToast.error(requireContext(), "팀 정보를 불러오지 못했어요.");
                });
    }

    private void setupRecyclerView(androidx.recyclerview.widget.RecyclerView recyclerView, TeamMemberAdapter adapter, List<TeamMemberAdapter.MemberItem> items) {
        adapter.setItems(items);
        androidx.recyclerview.widget.GridLayoutManager layoutManager = new androidx.recyclerview.widget.GridLayoutManager(requireContext(), 2);

        layoutManager.setSpanSizeLookup(new androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter.getItemViewType(position) == TeamMemberAdapter.TYPE_HEADER ? 2 : 1;
            }
        });

        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
    }

    private void showPlayerOptionsDialog(String nickname, String uid, String teamId, String viceCaptainUid, TextView playerViceTag) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_player_options, null);
        TextView txtName = dialogView.findViewById(R.id.txtPlayerName);
        LinearLayout btnAssignVice = dialogView.findViewById(R.id.btnAssignVice);
        LinearLayout btnKickPlayer = dialogView.findViewById(R.id.btnKickPlayer);
        LinearLayout btnAssignCaptain = dialogView.findViewById(R.id.btnAssignCaptain);
        TextView assignViceText = dialogView.findViewById(R.id.txtAssignViceText);

        txtName.setText(nickname);

        AlertDialog dialog = new AlertDialog.Builder(getContext(), R.style.CustomDialog)
                .setView(dialogView)
                .create();

        String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // 주장 권한 위임
        btnAssignCaptain.setVisibility(View.VISIBLE);
        btnAssignCaptain.setOnClickListener(v -> {
            dialog.dismiss();
            if (currentUid.equals(uid)) {
                CustomToast.warning(requireContext(), "자기 자신에게는 위임할 수 없어요.");
                return;
            }

            new AlertDialog.Builder(getContext())
                    .setTitle("주장 권한 위임")
                    .setMessage(nickname + " 님에게 주장 권한을 위임하시겠습니까?")
                    .setPositiveButton("예", (dialog1, which) -> {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("captainUID", uid);
                        if (uid.equals(viceCaptainUid)) {
                            updates.put("viceCaptainUID", null);
                        }

                        FirebaseFirestore.getInstance().collection("teams").document(teamId)
                                .update(updates)
                                .addOnSuccessListener(aVoid -> {
                                    CustomToast.success(requireContext(), "주장 권한이 위임되었어요.");
                                    loadPlayerList(teamId); // 뷰 지우기 대신 다시 로딩만 호출
                                })
                                .addOnFailureListener(e -> {
                                    CustomToast.error(requireContext(), "위임에 실패했어요.");
                                });
                    })
                    .setNegativeButton("아니오", null)
                    .show();
        });

        // 부주장 권한 설정/해제
        if (uid.equals(viceCaptainUid)) {
            assignViceText.setText("부주장 권한 해제");

            btnAssignVice.setOnClickListener(v -> {
                dialog.dismiss();

                if (uid.equals(currentUid)) {
                    CustomToast.warning(requireContext(), "자기 자신에게는 권한을 해제할 수 없어요.");
                    return;
                }

                new AlertDialog.Builder(getContext())
                        .setTitle("부주장 권한 해제")
                        .setMessage(nickname + " 님의 부주장 권한을 해제하시겠습니까?")
                        .setPositiveButton("예", (dialog1, which) -> {
                            FirebaseFirestore.getInstance().collection("teams").document(teamId)
                                    .update("viceCaptainUID", null)
                                    .addOnSuccessListener(aVoid -> {
                                        CustomToast.success(requireContext(), "부주장 권한이 해제되었어요.");
                                        playerViceTag.setVisibility(View.GONE);
                                        loadPlayerList(teamId);
                                    })
                                    .addOnFailureListener(e -> {
                                        CustomToast.error(requireContext(), "해제에 실패했어요.");
                                    });
                        })
                        .setNegativeButton("아니오", null)
                        .show();
            });

        } else {
            assignViceText.setText("부주장 권한 부여");

            btnAssignVice.setOnClickListener(v -> {
                dialog.dismiss();

                if (uid.equals(currentUid)) {
                    CustomToast.warning(requireContext(), "자기 자신에게는 권한을 부여할 수 없어요.");
                    return;
                }

                if (viceCaptainUid != null && !viceCaptainUid.isEmpty()) {
                    CustomToast.warning(requireContext(), "이미 부주장이 존재해요.");
                    return;
                }

                new AlertDialog.Builder(getContext())
                        .setTitle("부주장 권한 부여")
                        .setMessage(nickname + " 님에게 부주장 권한을 부여하시겠습니까?")
                        .setPositiveButton("예", (dialog1, which) -> {
                            FirebaseFirestore.getInstance().collection("teams").document(teamId)
                                    .update("viceCaptainUID", uid)
                                    .addOnSuccessListener(aVoid -> {
                                        CustomToast.success(requireContext(), "부주장 권한이 부여되었어요.");
                                        playerViceTag.setVisibility(View.VISIBLE);
                                        playerViceTag.setText("부주장");
                                    })
                                    .addOnFailureListener(e -> {
                                        CustomToast.error(requireContext(), "권한 부여에 실패했어요.");
                                    });
                        })
                        .setNegativeButton("아니오", null)
                        .show();
            });
        }

        // 팀원 방출
        btnKickPlayer.setOnClickListener(v -> {
            dialog.dismiss();

            if (currentUid.equals(uid)) {
                CustomToast.warning(requireContext(), "자기 자신은 방출할 수 없어요.");
                return;
            }

            new AlertDialog.Builder(getContext())
                    .setTitle("팀원 방출")
                    .setMessage(nickname + " 님을 팀에서 방출하시겠습니까?")
                    .setPositiveButton("예", (dialog1, which) -> {
                        FirebaseFirestore.getInstance().collection("teams").document(teamId)
                                .get()
                                .addOnSuccessListener(snapshot -> {
                                    List<String> members = (List<String>) snapshot.get("members");

                                    if (members != null && members.contains(uid)) {
                                        members.remove(uid);
                                        FirebaseFirestore.getInstance().collection("teams").document(teamId)
                                                .update("members", members)
                                                .addOnSuccessListener(aVoid -> {
                                                    FirebaseFirestore.getInstance().collection("profiles").document(uid)
                                                            .update("myTeam", null)
                                                            .addOnSuccessListener(unused -> {
                                                                CustomToast.success(requireContext(), "방출되었어요.");
                                                                loadPlayerList(teamId);
                                                            })
                                                            .addOnFailureListener(e -> {
                                                                CustomToast.error(requireContext(), "프로필 업데이트에 실패했어요.");
                                                            });
                                                })
                                                .addOnFailureListener(e -> {
                                                    CustomToast.error(requireContext(), "방출에 실패했어요.");
                                                });
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    CustomToast.error(requireContext(), "팀 정보를 불러오지 못했어요.");
                                });
                    })
                    .setNegativeButton("아니오", null)
                    .show();
        });

        dialog.show();
    }

    // 10줄 접기/펼치기 - 레이아웃 준비 후에만 줄수 측정(1회성)
    private void applyCollapsedIntroInline() {
        if (!isAdded()) return;

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
            @Override public void onGlobalLayout() {
                if (!isAdded()) {
                    teamIntro.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    return;
                }
                if (teamIntro.getLayout() != null) {
                    int total = teamIntro.getLayout().getLineCount();
                    applyClampOrHide(total);
                    teamIntro.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            }
        });

        uiHandler.postDelayed(() -> {
            if (!isAdded()) return;
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

    private void loadUpcomingSchedule(@NonNull String teamId) {
        if (nextScheduleCard == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        final long now = System.currentTimeMillis();

        boolean needInitialSpinner = (!upcomingLoadedOnce && upcomingCache == null);
        if (needInitialSpinner) {
            if (nextScheduleCard.getVisibility() != View.VISIBLE) nextScheduleCard.setVisibility(View.VISIBLE);
            showScheduleLoading(true);
            tvScore.setText("VS");
        }

        db.collection("schedules").document(teamId)
                .collection("events")
                .orderBy("matchTs")
                .limit(10)
                .get(Source.CACHE)
                .addOnSuccessListener(cacheSnap -> {
                    if (!isAdded()) return;

                    if (!cacheSnap.isEmpty()) {
                        DocumentSnapshot bestLocal = null;
                        long bestEndLocal = Long.MAX_VALUE;

                        for (DocumentSnapshot d : cacheSnap.getDocuments()) {
                            String date = d.getString("date");
                            String time = d.getString("time");
                            long[] se = computeStartEndFromDateTimeStrings(date, time);
                            long end = se[1];
                            if (end > now && end < bestEndLocal) {
                                bestEndLocal = end;
                                bestLocal = d;
                            }
                        }
                        if (bestLocal != null) {
                            bindUpcomingCard(bestLocal);
                            upcomingCache = bestLocal;
                            upcomingLoadedOnce = true;
                        } else {
                            upcomingCache = null;
                        }
                    } else {
                        upcomingCache = null;
                    }
                });

        db.collection("schedules").document(teamId)
                .collection("events")
                .orderBy("matchTs")
                .limit(10)
                .get(Source.SERVER)
                .addOnSuccessListener(snap -> {
                    if (!isAdded()) return;

                    final java.util.concurrent.atomic.AtomicReference<DocumentSnapshot> bestRef =
                            new java.util.concurrent.atomic.AtomicReference<>(null);
                    final java.util.concurrent.atomic.AtomicLong bestEndRef =
                            new java.util.concurrent.atomic.AtomicLong(Long.MAX_VALUE);

                    if (!snap.isEmpty()) {
                        for (DocumentSnapshot d : snap.getDocuments()) {
                            String date = d.getString("date");
                            String time = d.getString("time");
                            long[] se = computeStartEndFromDateTimeStrings(date, time);
                            long end = se[1];
                            if (end > now && end < bestEndRef.get()) {
                                bestEndRef.set(end);
                                bestRef.set(d);
                            }
                        }
                    }

                    if (bestRef.get() == null) {
                        final String today = todayString();
                        db.collection("schedules").document(teamId)
                                .collection("events")
                                .whereEqualTo("date", today)
                                .get(Source.SERVER)
                                .addOnSuccessListener(todaySnap -> {
                                    for (DocumentSnapshot d : todaySnap) {
                                        String date = d.getString("date");
                                        String time = d.getString("time");
                                        long[] se = computeStartEndFromDateTimeStrings(date, time);
                                        long end = se[1];
                                        if (end > now && end < bestEndRef.get()) {
                                            bestEndRef.set(end);
                                            bestRef.set(d);
                                        }
                                    }

                                    if (bestRef.get() != null) {
                                        bindUpcomingCard(bestRef.get());
                                        upcomingCache = bestRef.get();
                                        upcomingLoadedOnce = true;
                                        showScheduleLoading(false);
                                        if (nextScheduleCard.getVisibility() != View.VISIBLE)
                                            nextScheduleCard.setVisibility(View.VISIBLE);
                                    } else {
                                        showScheduleLoading(false);
                                        renderUpcomingEmpty();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    showScheduleLoading(false);
                                    if (upcomingCache == null) renderUpcomingEmpty();
                                });
                    } else {
                        bindUpcomingCard(bestRef.get());
                        upcomingCache = bestRef.get();
                        upcomingLoadedOnce = true;
                        showScheduleLoading(false);
                        if (nextScheduleCard.getVisibility() != View.VISIBLE)
                            nextScheduleCard.setVisibility(View.VISIBLE);
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    showScheduleLoading(false);
                    if (upcomingCache == null) renderUpcomingEmpty();
                });
    }

    private static final long DEFAULT_MATCH_DURATION_MS = 2L * 60L * 60L * 1000L;

    private long[] computeStartEndFromDateTimeStrings(String date, String timeRange) {
        long start = 0L, end = 0L;
        try {
            String startHHmm = null, endHHmm = null;
            if (timeRange != null && timeRange.contains("~")) {
                String[] p = timeRange.split("~");
                startHHmm = p[0].trim();
                endHHmm   = p[1].trim();
            }
            if (startHHmm == null || endHHmm == null || startHHmm.isEmpty() || endHHmm.isEmpty()) {
                long base = dateToMs(date);
                start = base + 9 * 60 * 60 * 1000L;
                end   = start + DEFAULT_MATCH_DURATION_MS;
            } else {
                start = computeMatchTs(date, startHHmm);
                end   = computeMatchTs(date, endHHmm);
                if (end <= start) end += 24L * 60L * 60L * 1000L;
            }
        } catch (Exception e) {
            long base = System.currentTimeMillis();
            start = base;
            end   = base + DEFAULT_MATCH_DURATION_MS;
        }
        return new long[]{ start, end };
    }

    private long computeMatchTs(String date, String hhmm) {
        try {
            java.text.SimpleDateFormat sdf =
                    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
            return sdf.parse(date + " " + hhmm).getTime();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private long dateToMs(String date) {
        try {
            String[] p = date.split("-");
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(java.util.Calendar.YEAR, Integer.parseInt(p[0]));
            cal.set(java.util.Calendar.MONTH, Integer.parseInt(p[1]) - 1);
            cal.set(java.util.Calendar.DAY_OF_MONTH, Integer.parseInt(p[2]));
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            return cal.getTimeInMillis();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private String todayString() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        return String.format(java.util.Locale.KOREAN, "%04d-%02d-%02d",
                c.get(java.util.Calendar.YEAR),
                c.get(java.util.Calendar.MONTH) + 1,
                c.get(java.util.Calendar.DAY_OF_MONTH));
    }

    private void bindUpcomingCard(DocumentSnapshot ev) {
        clearUpcomingEmptyText();

        String date    = ev.getString("date");
        String timeStr = ev.getString("time");
        String oppNm   = ev.getString("opponentTeamName");
        String oppLogo = ev.getString("opponentLogoUrl");
        String stadNm  = ev.getString("stadiumName");
        String addr    = ev.getString("stadiumAddress");
        String matchId = ev.getString("matchId");

        tvNextDateChip.setText(formatDateChipRange24(date, timeStr));
        tvAwayName.setText(!TextUtils.isEmpty(oppNm) ? oppNm : "");
        tvPlace.setText(!TextUtils.isEmpty(stadNm) ? stadNm : "");
        tvAddress.setText(!TextUtils.isEmpty(addr) ? addr : "");
        if (!TextUtils.isEmpty(oppLogo)) {
            Glide.with(requireContext())
                    .load(oppLogo)
                    .placeholder(R.drawable.ic_placeholder_circle)
                    .into(imgAwayLogo);
        } else {
            imgAwayLogo.setImageResource(R.drawable.ic_placeholder_circle);
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        final com.google.android.gms.tasks.Task<DocumentSnapshot> tTeam =
                db.collection("teams").document(teamId).get();
        final com.google.android.gms.tasks.Task<DocumentSnapshot> tMatch =
                TextUtils.isEmpty(matchId) ? null
                        : db.collection("matches").document(matchId).get();

        java.util.List<com.google.android.gms.tasks.Task<?>> waits = new java.util.ArrayList<>();
        waits.add(tTeam);
        if (tMatch != null) waits.add(tMatch);

        com.google.android.gms.tasks.Tasks.whenAllComplete(waits)
                .addOnCompleteListener(done -> {
                    if (!isAdded()) return;

                    if (tTeam.isSuccessful()) {
                        DocumentSnapshot team = tTeam.getResult();
                        String myName = team.getString("teamName");
                        String myLogo = team.getString("logoUrl");
                        tvHomeName.setText(myName != null ? myName : "");
                        if (!TextUtils.isEmpty(myLogo)) {
                            Glide.with(requireContext())
                                    .load(myLogo)
                                    .placeholder(R.drawable.ic_placeholder_circle)
                                    .into(imgHomeLogo);
                        } else {
                            imgHomeLogo.setImageResource(R.drawable.ic_placeholder_circle);
                        }
                    } else {
                        tvHomeName.setText("");
                        imgHomeLogo.setImageResource(R.drawable.ic_placeholder_circle);
                    }

                    if (tMatch != null && tMatch.isSuccessful()) {
                        DocumentSnapshot mt = tMatch.getResult();
                        String status = mt.getString("status");
                        Number sf = (Number) mt.get("scoreFor");
                        Number sa = (Number) mt.get("scoreAgainst");
                        if ("finished".equalsIgnoreCase(status) && sf != null && sa != null) {
                            tvScore.setText(sf.intValue() + " : " + sa.intValue());
                        } else {
                            tvScore.setText("VS");
                        }
                    } else {
                        tvScore.setText("VS");
                    }

                    upcomingCache = ev;
                    upcomingLoadedOnce = true;
                });
    }

    private String formatDateChipRange24(String date, String timeRange) {
        try {
            java.time.LocalDate d = java.time.LocalDate.parse(date);
            java.time.format.TextStyle ts = java.time.format.TextStyle.SHORT;
            java.util.Locale ko = java.util.Locale.KOREAN;
            String dow = d.getDayOfWeek().getDisplayName(ts, ko);

            String datePart = date.replace("-", ".");

            if (TextUtils.isEmpty(timeRange)) {
                return datePart + " (" + dow + ")";
            }

            String norm = timeRange.replace("~", " ~ ").replaceAll("\\s+", " ").trim();
            return datePart + " (" + dow + ") · " + norm;

        } catch (Exception e) {
            return (date == null ? "" : date) + (TextUtils.isEmpty(timeRange) ? "" : " · " + timeRange);
        }
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

    private void renderUpcomingEmpty() {
        if (!isAdded() || nextScheduleContainer == null) return;

        if (nextScheduleCard != null) nextScheduleCard.setVisibility(View.GONE);

        View old = nextScheduleContainer.findViewWithTag("emptyUpcomingMsg");
        if (old != null) nextScheduleContainer.removeView(old);

        TextView msg = new TextView(requireContext());
        msg.setTag("emptyUpcomingMsg");
        msg.setText("다가오는 일정이 없습니다.");
        msg.setTextSize(14f);
        msg.setTextColor(0xFF6B7280);
        msg.setGravity(android.view.Gravity.CENTER);
        msg.setPadding(0, dp(24), 0, dp(24));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        msg.setLayoutParams(lp);

        nextScheduleContainer.addView(msg);
    }

    private void clearUpcomingEmptyText() {
        if (nextScheduleContainer == null) return;
        View old = nextScheduleContainer.findViewWithTag("emptyUpcomingMsg");
        if (old != null) nextScheduleContainer.removeView(old);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void startImageLoads(DocumentSnapshot doc) {
        if (!isAdded()) return;

        String logoUrl  = doc.getString("logoUrl");
        String photoUrl = doc.getString("photoUrl");

        com.bumptech.glide.request.RequestOptions opts =
                new com.bumptech.glide.request.RequestOptions()
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.AUTOMATIC)
                        .dontAnimate();

        if (!TextUtils.isEmpty(photoUrl)) {
            Glide.with(requireContext())
                    .load(photoUrl)
                    .thumbnail(0.25f)
                    .apply(opts)
                    .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                        @Override
                        public boolean onLoadFailed(
                                com.bumptech.glide.load.engine.GlideException e,
                                Object model,
                                com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                boolean isFirstResource
                        ) {
                            if (!firstImageDrawn && isAdded()) {
                                state.showContent();
                                firstImageDrawn = true;
                                ensureIntroClampAfterContent();
                            }
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(
                                android.graphics.drawable.Drawable resource,
                                Object model,
                                com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                com.bumptech.glide.load.DataSource dataSource,
                                boolean isFirstResource
                        ) {
                            if (!firstImageDrawn && isAdded()) {
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
            Glide.with(requireContext())
                    .load(logoUrl)
                    .thumbnail(0.25f)
                    .apply(opts)
                    .placeholder(R.drawable.ic_placeholder_circle)
                    .into(teamLogo);
        } else {
            teamLogo.setImageResource(R.drawable.ic_placeholder_circle);
        }
    }

    private void bindTeamTextFields(DocumentSnapshot doc) {
        String teamNameStr = doc.getString("teamName");
        String intro       = doc.getString("intro");
        String region      = doc.getString("region");
        String skill       = doc.getString("skill");
        String ageRange    = doc.getString("ageRange");

        String activityDayVal     = doc.getString("activityDay");
        String homeStadiumNameVal = doc.getString("homeStadiumName");
        String stadiumAddressVal  = doc.getString("stadium");

        String timeStart = doc.getString("timeStart");
        String timeEnd   = doc.getString("timeEnd");

        teamName.setText(teamNameStr != null ? teamNameStr : "");
        introFullText = (intro != null ? intro : "");
        teamIntro.setText(introFullText);
        applyCollapsedIntroInline();

        teamRegion.setText(!TextUtils.isEmpty(region) ? region : "");
        teamSkill.setText(!TextUtils.isEmpty(skill) ? skill : "");
        teamAge.setText(!TextUtils.isEmpty(ageRange) ? ageRange : "");

        String activityDisplay = null;
        if (!TextUtils.isEmpty(activityDayVal)) {
            activityDisplay = (!TextUtils.isEmpty(timeStart) && !TextUtils.isEmpty(timeEnd))
                    ? activityDayVal + " | " + timeStart + " ~ " + timeEnd
                    : activityDayVal;
        } else if (!TextUtils.isEmpty(timeStart) && !TextUtils.isEmpty(timeEnd)) {
            activityDisplay = timeStart + " ~ " + timeEnd;
        }

        setTextOrGone(teamActivityDay,        activityDisplay);
        setTextOrGone(teamHomeStadiumName,    homeStadiumNameVal);
        setTextOrGone(teamHomeStadiumAddress, stadiumAddressVal);

        String captainUid = doc.getString("captainUID");
        String viceCaptainUid = doc.getString("viceCaptainUID");
        String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        btnInvite.setVisibility(
                (currentUid.equals(captainUid) || currentUid.equals(viceCaptainUid)) ? View.VISIBLE : View.GONE
        );
    }

    private final android.os.Handler uiHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean firstImageDrawn = false;

    private void ensureIntroClampAfterContent() {
        if (!isAdded() || teamIntro == null) return;

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
}