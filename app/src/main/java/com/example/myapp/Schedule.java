// src/main/java/com/example/myapp/Schedule.java
package com.example.myapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class Schedule extends AppCompatActivity {

    private StateLayout state;

    private com.google.firebase.firestore.ListenerRegistration attendReg; // 다이얼로그용 리스너

    // ▼ “선택된 날짜 일정”
    private TextView tvSelectedDateTitle;
    private ProgressBar progressSelectedDate;
    private LinearLayout selectedDateList; // ✅ 카드들을 담는 컨테이너

    // ▼ 기존 리스트/어댑터는 유지해도 되지만 사용 안 함(원하면 제거 가능)
    // private RecyclerView scheduleRecyclerView;
    // private ScheduleAdapter scheduleAdapter;
    private final List<ScheduleItem> scheduleList = new ArrayList<>();

    // ▼ “다가오는 일정” 카드(view_next_schedule_card.xml) 내부 뷰들
    private ProgressBar scheduleLoading;
    private View scheduleContent;
    private TextView tvNextDateChip;
    private ImageView imgHomeLogo, imgAwayLogo;
    private TextView tvHomeName, tvAwayName, tvPlace, tvAddress;

    // Firebase
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    // 내 팀
    private String myTeamId = "";
    private String myTeamName = "";
    private String myTeamLogoUrl = "";

    // 로딩 동기화
    private boolean profileLoaded = false;
    private boolean teamLoaded = false;
    private boolean nextLoaded = false;
    private boolean firstDateLoaded = false;

    // ▼ “다가오는 일정” 투표 버튼
    private MaterialButton btnVoteAttendance;

    // 투표/목록에 필요
    private ScheduleItem nextScheduleItem;  // 다가오는 일정(투표 대상)

    // 내 프로필(표시용)
    private String myNickname = "";
    private String myProfileImageUrl = "";
    private View nextScheduleCard;
    // === 시간 계산 기본값(경기 길이 미저장 시) ===
    private static final long DEFAULT_MATCH_DURATION_MS = 2L * 60L * 60L * 1000L; // 2시간

    // 다가오는 일정의 시간(시작/종료)을 저장해 투표 버튼 노출 판단에 사용
    private long nextStartMs = -1L;
    private long nextEndMs   = -1L;

    // Doc에서 시작/종료 ms 계산: matchStartTs/matchEndTs 있으면 사용, 없으면 date+time에서 계산
    private long[] computeStartEndMsFromDoc(com.google.firebase.firestore.DocumentSnapshot doc) {
        Long start = null, end = null;
        try { start = doc.getLong("matchStartTs"); } catch (Exception ignore) {}
        try { end   = doc.getLong("matchEndTs");   } catch (Exception ignore) {}

        String d = doc.getString("date");
        String t = doc.getString("time");  // "HH:mm" 가정 (없을 수 있음)

        if (start == null) {
            if (d != null) {
                long s = dateToMs(d); // 자정
                if (t != null && t.length() >= 4) {
                    try {
                        String[] tt = t.split(":");
                        Calendar c = Calendar.getInstance();
                        c.setTimeInMillis(s);
                        c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(tt[0]));
                        c.set(Calendar.MINUTE, Integer.parseInt(tt[1]));
                        c.set(Calendar.SECOND, 0);
                        c.set(Calendar.MILLISECOND, 0);
                        s = c.getTimeInMillis();
                    } catch (Exception ignore) {}
                    start = s;
                } else {
                    // ⛑ time 없음 → 정확한 종료판단 불가. 다가오는 일정에서 제외되지 않게 "아주 먼 미래"로 처리
                    start = FAR_FUTURE - DEFAULT_MATCH_DURATION_MS;
                }
            } else {
                start = FAR_FUTURE - DEFAULT_MATCH_DURATION_MS;
            }
        }

        if (end == null) {
            if (t != null && t.length() >= 4) {
                end = start + DEFAULT_MATCH_DURATION_MS;
            } else {
                // ⛑ time 없음 → 종료시각 판단 불가. "아주 먼 미래"로 둬서 '이미 끝남'으로 오인되지 않게 함
                end = FAR_FUTURE;
            }
        }
        return new long[]{ start, end };
    }
    private static final long FAR_FUTURE = Long.MAX_VALUE / 4; // 안전한 큰 값
    // Item에서 시작/종료 ms 계산: (문서가 없으니) date+time 기준 + 기본 경기 길이
    private long[] computeStartEndMsFromItem(ScheduleItem it) {
        if (isEmpty(it.time)) {
            // ⛑ time 없음 → 종료 판단 불가. 종료 전으로 간주(버튼 노출은 status로 제어)
            return new long[]{ FAR_FUTURE - DEFAULT_MATCH_DURATION_MS, FAR_FUTURE };
        }
        long s = dateToMs(it.date);
        try {
            String[] tt = it.time.split(":");
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(s);
            c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(tt[0]));
            c.set(Calendar.MINUTE, Integer.parseInt(tt[1]));
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            s = c.getTimeInMillis();
        } catch (Exception ignore) {}
        long e = s + DEFAULT_MATCH_DURATION_MS;
        return new long[]{ s, e };
    }

    // 기존: exists() 만 체크 → 잘못된 양성
// 수정: status == finished 이거나 scoreFor/scoreAgainst/scorers 중 하나라도 있으면 "기록됨"
    // Schedule.java 안에 넣을 것
// ✅ 팀별로 저장되는 matches/{eventId}_{teamId} 를 확인하도록 수정한 버전
    private void checkRecorded(String eventId, String teamId, java.util.function.Consumer<Boolean> cb) {
        if (teamId == null || teamId.isEmpty()) {
            cb.accept(false);
            return;
        }

        // 기록 다이얼로그는 matches/{eventId}_{teamId} 형태로 저장하니까 이걸 본다
        String docId = eventId + "_" + teamId;

        db.collection("matches").document(docId).get()
                .addOnSuccessListener(d -> {
                    if (d != null && d.exists()) {
                        String status = d.getString("status");
                        boolean hasScore   = d.contains("scoreFor") || d.contains("scoreAgainst");
                        boolean hasScorers = d.contains("scorers");
                        boolean hasEvents  = d.contains("goalEvents");
                        boolean recorded   = "finished".equals(status) || hasScore || hasScorers || hasEvents;
                        cb.accept(recorded);
                    } else {
                        cb.accept(false);
                    }
                })
                .addOnFailureListener(err -> cb.accept(false));
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.schedule);

        btnVoteAttendance = findViewById(R.id.btnVoteAttendance);
        if (btnVoteAttendance != null) {
            btnVoteAttendance.setOnClickListener(v -> showVoteDialog());
        }

        state = findViewById(R.id.stateLayout);
        if (state != null) state.showLoading();

        // ▼ 선택된 날짜 영역
        tvSelectedDateTitle  = findViewById(R.id.tvSelectedDateTitle);
        progressSelectedDate = findViewById(R.id.progressSelectedDate);
        selectedDateList     = findViewById(R.id.selectedDateList);

        // ▼ 다가오는 일정 카드 내부 뷰 바인딩
        scheduleLoading   = findViewById(R.id.scheduleLoading);
        scheduleContent   = findViewById(R.id.scheduleContent);
        tvNextDateChip    = findViewById(R.id.tvNextDateChip);
        imgHomeLogo       = findViewById(R.id.imgHomeLogo);
        imgAwayLogo       = findViewById(R.id.imgAwayLogo);
        tvHomeName        = findViewById(R.id.tvHomeName);
        tvAwayName        = findViewById(R.id.tvAwayName);
        tvPlace           = findViewById(R.id.tvPlace);
        tvAddress         = findViewById(R.id.tvAddress);

        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        nextScheduleCard = findViewById(R.id.nextScheduleCard);
        btnVoteAttendance = findViewById(R.id.btnVoteAttendance);
        if (btnVoteAttendance != null) {
            btnVoteAttendance.setOnClickListener(v -> showVoteDialog());
        }

        // ▼ 달력 추가
        FrameLayout calendarContainer = findViewById(R.id.calendarContainer);
        CustomCalendarView customCalendar = new CustomCalendarView(this);
        calendarContainer.addView(customCalendar);

        // 날짜 클릭 → 선택된 날짜 일정 로딩(카드들로 렌더링)
        customCalendar.setOnDateClickListener((year, month, day) -> {
            String selectedDate = String.format(Locale.KOREAN, "%04d-%02d-%02d", year, month + 1, day);
            tvSelectedDateTitle.setText("선택된 날짜 일정 (" + selectedDate + ")");
            if (progressSelectedDate != null) progressSelectedDate.setVisibility(View.VISIBLE);
            loadScheduleForDate(selectedDate, () -> {
                if (progressSelectedDate != null) progressSelectedDate.setVisibility(View.GONE);
            });
        });

        // 내 팀 → 팀 정보 → 다가오는 일정 + 오늘 일정
        if (auth.getCurrentUser() != null) {
            String myUid = auth.getCurrentUser().getUid();
            db.collection("profiles").document(myUid).get()
                    .addOnSuccessListener(snapshot -> {
                        myTeamId = snapshot.getString("myTeam");
                        myNickname = snapshot.getString("nickname");                // ✅ 추가
                        myProfileImageUrl = snapshot.getString("profileImageUrl");  // ✅ 추가
                        profileLoaded = true;

                        if (myTeamId == null || myTeamId.isEmpty()) {
                            showEmpty("팀 정보가 없어요.\n먼저 '팀 만들기'로 팀을 생성해 주세요.");
                            return;
                        }

                        db.collection("teams").document(myTeamId).get()
                                .addOnSuccessListener(teamDoc -> {
                                    myTeamName    = safe(teamDoc.getString("teamName"));
                                    myTeamLogoUrl = safe(teamDoc.getString("logoUrl"));
                                    teamLoaded = true;

                                    findNextSchedule(() -> {
                                        nextLoaded = true;

                                        String today = todayString();
                                        tvSelectedDateTitle.setText("선택된 날짜 일정 (" + today + ")");
                                        loadScheduleForDate(today, () -> {
                                            firstDateLoaded = true;
                                            maybeShowContent();
                                        });
                                    });
                                })
                                .addOnFailureListener(e -> {
                                    teamLoaded = true;
                                    CustomToast.warning(this, "팀 정보를 일부 불러오지 못했어요.");
                                    findNextSchedule(() -> {
                                        nextLoaded = true;
                                        String today = todayString();
                                        tvSelectedDateTitle.setText("선택된 날짜 일정 (" + today + ")");
                                        loadScheduleForDate(today, () -> {
                                            firstDateLoaded = true;
                                            maybeShowContent();
                                        });
                                    });
                                });
                    })
                    .addOnFailureListener(e -> {
                        showEmpty("프로필을 불러오지 못했어요.\n잠시 후 다시 시도해 주세요.");
                    });
        } else {
            showEmpty("로그인이 필요해요.\n로그인 후 다시 시도해 주세요.");
        }
    }

    private void showVoteDialog() {
        if (nextScheduleItem == null || isEmpty(nextScheduleItem.eventId) || isEmpty(myTeamId)) {
            CustomToast.warning(this, "투표할 일정이 없어요.");
            return;
        }

        // 가로 버튼 2개 커스텀 다이얼로그
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(android.view.Gravity.CENTER);
        layout.setPadding(dp(24), dp(16), dp(24), dp(16));
        layout.setMinimumWidth(dp(260));

        MaterialButton btnAttend = new MaterialButton(this);
        btnAttend.setText("참석");
        btnAttend.setTextColor(0xFFFFFFFF);
        btnAttend.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF1E88E5)); // 파랑
        btnAttend.setCornerRadius(dp(12));
        btnAttend.setAllCaps(false);
        btnAttend.setPadding(dp(24), dp(10), dp(24), dp(10));
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp1.setMarginEnd(dp(12));
        btnAttend.setLayoutParams(lp1);

        MaterialButton btnAbsent = new MaterialButton(this);
        btnAbsent.setText("불참석");
        btnAbsent.setTextColor(0xFFFFFFFF);
        btnAbsent.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFD32F2F)); // 빨강
        btnAbsent.setCornerRadius(dp(12));
        btnAbsent.setAllCaps(false);
        btnAbsent.setPadding(dp(24), dp(10), dp(24), dp(10));

        layout.addView(btnAttend);
        layout.addView(btnAbsent);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle("경기 참석 투표")
                .setView(layout)
                .setCancelable(true)
                .create();

        btnAttend.setOnClickListener(v -> {
            dialog.dismiss();
            saveVoteAndOpenList("attend");
        });
        btnAbsent.setOnClickListener(v -> {
            dialog.dismiss();
            saveVoteAndOpenList("absent");
        });

        dialog.show();
    }

    private void saveVoteAndOpenList(String status) {
        if (auth.getCurrentUser() == null) {
            CustomToast.error(this, "로그인이 필요해요.");
            return;
        }
        String uid = auth.getCurrentUser().getUid();

        // attendances/{uid} 문서
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("status", status);                    // "attend" | "absent"
        data.put("isMercenary", false);                // 기본값: 팀원
        data.put("updatedAt", System.currentTimeMillis());
        if (!isEmpty(myNickname)) data.put("nickname", myNickname);
        if (!isEmpty(myProfileImageUrl)) data.put("profileImageUrl", myProfileImageUrl);

        db.collection("schedules").document(myTeamId)
                .collection("events").document(nextScheduleItem.eventId)
                .collection("attendances").document(uid)
                .set(data, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    if ("attend".equals(status)) {
                        CustomToast.success(this, "참석");
                    } else {
                        CustomToast.info(this, "불참석");
                    }
                })
                .addOnFailureListener(e -> {
                    CustomToast.error(this, "저장에 실패했어요. 다시 시도해 주세요.");
                });
    }

    private void maybeShowContent() {
        if (state == null) return;
        if (profileLoaded && teamLoaded && nextLoaded && firstDateLoaded) {
            state.showContent();
        }
    }

    /** 선택한 날짜의 일정 목록 로드 → 카드로 렌더링 */
    private void loadScheduleForDate(String selectedDate, Runnable after) {
        if (myTeamId == null || myTeamId.isEmpty()) {
            scheduleList.clear();
            renderSelectedDateCards(); // 빈 상태 렌더
            if (after != null) after.run();
            return;
        }

        db.collection("schedules").document(myTeamId)
                .collection("events")
                .whereEqualTo("date", selectedDate)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    scheduleList.clear();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String eventId  = doc.getId();
                        String date     = doc.getString("date");
                        String status   = doc.getString("status");
                        String matchId  = doc.getString("matchId");

                        String title    = doc.getString("title");
                        String time     = doc.getString("time");
                        String opponent = doc.getString("opponentTeamName");
                        String stadium  = doc.getString("stadiumName");
                        String address  = doc.getString("stadiumAddress");
                        String oppLogo  = doc.getString("opponentLogoUrl");

                        ScheduleItem it = new ScheduleItem(
                                eventId, myTeamId,
                                (date != null ? date : selectedDate),
                                status, matchId,
                                title, time, opponent, stadium, address
                        );
                        it.opponentLogoUrl = oppLogo;
                        scheduleList.add(it);
                    }
                    renderSelectedDateCards();
                })
                .addOnFailureListener(e -> {
                    CustomToast.error(this, "일정을 불러오지 못했어요.\n잠시 후 다시 시도해 주세요.");
                    scheduleList.clear();
                    renderSelectedDateCards();
                })
                .addOnCompleteListener(t -> {
                    if (after != null) after.run();
                });
    }

    /** 선택된 날짜 카드들 그리기 (각 카드 하단: 구분선 + 기록하기 버튼) */
    private void renderSelectedDateCards() {
        if (selectedDateList == null) return;
        selectedDateList.removeAllViews();

        if (scheduleList.isEmpty()) {
            // ✅ 카드 대신 텍스트 메시지 출력
            TextView emptyMsg = new TextView(this);
            emptyMsg.setText("선택된 날짜에 일정이 없습니다.");
            emptyMsg.setTextSize(14f);
            emptyMsg.setTextColor(0xFF6B7280);
            emptyMsg.setGravity(android.view.Gravity.CENTER);
            emptyMsg.setPadding(0, dp(24), 0, dp(24));

            // 부모(LinearLayout)에 가운데 정렬되도록 설정
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            emptyMsg.setLayoutParams(lp);

            selectedDateList.addView(emptyMsg);
            return;
        }

        // ✅ 일정이 있을 때는 기존처럼 카드 렌더링
        for (ScheduleItem it : scheduleList) {
            View card = LayoutInflater.from(this)
                    .inflate(R.layout.view_next_schedule_card, selectedDateList, false);

            ProgressBar loading = card.findViewById(R.id.scheduleLoading);
            View content        = card.findViewById(R.id.scheduleContent);
            TextView chip       = card.findViewById(R.id.tvNextDateChip);
            ImageView ivHome    = card.findViewById(R.id.imgHomeLogo);
            ImageView ivAway    = card.findViewById(R.id.imgAwayLogo);
            TextView tvHome     = card.findViewById(R.id.tvHomeName);
            TextView tvAway     = card.findViewById(R.id.tvAwayName);
            TextView tvP        = card.findViewById(R.id.tvPlace);
            TextView tvA        = card.findViewById(R.id.tvAddress);

            if (loading != null) loading.setVisibility(View.GONE);
            if (content != null) content.setVisibility(View.VISIBLE);

            // 날짜칩
            String dateChip = formatKoreanDate(it.date);
            if (!isEmpty(it.time)) dateChip += " · " + it.time;
            if (chip != null) chip.setText(dateChip);

            // 홈/어웨이
            if (tvHome != null) tvHome.setText(isEmpty(myTeamName) ? "My Team" : myTeamName);
            if (tvAway != null) tvAway.setText(isEmpty(it.opponentName) ? "-" : it.opponentName);

            loadCircle(ivHome, myTeamLogoUrl);
            loadCircle(ivAway, it.opponentLogoUrl);

            // 장소/주소
            if (tvP != null) tvP.setText(isEmpty(it.stadiumName) ? "-" : it.stadiumName);
            if (tvA != null) tvA.setText(isEmpty(it.address) ? "" : it.address);

            // ▼ 하단 구분선 + 기록하기 버튼 추가
            addRecordFooter(card, it);

            selectedDateList.addView(card);
        }
    }

    // Schedule.java 안
    /** 카드 하단: 구분선 + ‘선수목록’(왼쪽) / ‘기록하기 or 기록수정’(오른쪽) */
    private void addRecordFooter(View cardRoot, ScheduleItem it) {
        LinearLayout content = cardRoot.findViewById(R.id.scheduleContent);
        if (content == null) return;

        // 구분선
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        divider.setBackgroundColor(0xFFE0E0E0);
        ((LinearLayout.LayoutParams) divider.getLayoutParams()).topMargin = dp(12);
        content.addView(divider);

        // 버튼 행
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(8);
        rowLp.bottomMargin = dp(4);
        row.setLayoutParams(rowLp);
        row.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        content.addView(row);

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.setMarginEnd(dp(8));

        // 선수목록 버튼
        MaterialButton btnPlayers = new MaterialButton(this);
        btnPlayers.setText("선수목록");
        btnPlayers.setAllCaps(false);
        btnPlayers.setInsetTop(0); btnPlayers.setInsetBottom(0);
        btnPlayers.setLayoutParams(btnLp);
        btnPlayers.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF1E88E5));
        btnPlayers.setTextColor(0xFFFFFFFF);
        btnPlayers.setCornerRadius(dp(16));
        btnPlayers.setPadding(dp(16), dp(10), dp(16), dp(10));
        btnPlayers.setOnClickListener(v -> showAttendanceListDialog(it));
        row.addView(btnPlayers);

        // 기록 버튼(조건부로 추가될 예정)
        MaterialButton btnRecord = new MaterialButton(this);
        btnRecord.setAllCaps(false);
        btnRecord.setInsetTop(0); btnRecord.setInsetBottom(0);
        btnRecord.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF26C6DA));
        btnRecord.setTextColor(0xFFFFFFFF);
        btnRecord.setCornerRadius(dp(16));
        btnRecord.setPadding(dp(16), dp(10), dp(16), dp(10));

        // 종료 판정(now ≥ endMs)
        long[] se = computeStartEndFromDateTimeStrings(it.date, it.time); // "HH:mm ~ HH:mm" 파싱
        long endMs = se[1];
        boolean isPastOrEnded = System.currentTimeMillis() >= endMs;

        if (isPastOrEnded) {
            // ✅ 여기서 팀별 match 문서가 있는지 확인해서 라벨을 기록하기/기록수정 으로 나눈다
            checkRecorded(it.eventId, myTeamId, recorded -> {
                String label = recorded ? "기록수정" : "기록하기";
                btnRecord.setText(label);
                btnRecord.setOnClickListener(v -> {
                    WriteRecord dialog = WriteRecord.newInstance(it.teamId, it.eventId);
                    dialog.show(getSupportFragmentManager(), "WriteRecord");
                });
                row.addView(btnRecord);
            });
        }
    }


    // ✅ "yyyy-MM-dd" + "HH:mm ~ HH:mm" → start/end ms (자정 넘김 처리)
    private long[] computeStartEndFromDateTimeStrings(String date, String timeRange) {
        long start = 0L, end = 0L;
        try {
            String startHHmm = null, endHHmm = null;
            if (timeRange != null && timeRange.contains("~")) {
                String[] p = timeRange.split("~");
                startHHmm = p[0].trim();
                endHHmm   = p[1].trim();
            }
            if (startHHmm == null || endHHmm == null) {
                // time 미지정 시 기본 2시간 가정
                long base = dateToMs(date);
                start = base + 9 * 60 * 60 * 1000L; // 오전 9시 기본
                end   = start + DEFAULT_MATCH_DURATION_MS;
            } else {
                start = computeMatchTs(date, startHHmm);
                end   = computeMatchTs(date, endHHmm);
                if (end <= start) end += 24L * 60L * 60L * 1000L; // 자정 넘김
            }
        } catch (Exception e) {
            long base = System.currentTimeMillis();
            start = base;
            end = base + DEFAULT_MATCH_DURATION_MS;
        }
        return new long[]{ start, end };
    }

    // ⛳ Schedule 안에서 쓰기 위한 간단한 시작 ms 계산기
    private long computeMatchTs(String date, String hhmm) {
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
            return sdf.parse(date + " " + hhmm).getTime();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    /** 선수목록 보기 다이얼로그 (2열 그리드, Firestore 데이터 로딩) */
    private void showAttendanceListDialog(ScheduleItem it) {
        final ScheduleItem item = it;
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_attendance_list, null, false);

        ProgressBar progress = dialogView.findViewById(R.id.progressAttendance);
        TextView tvAttendHeader = dialogView.findViewById(R.id.tvAttendHeader);
        TextView tvAbsentHeader = dialogView.findViewById(R.id.tvAbsentHeader);
        TextView tvNotVotedHeader = dialogView.findViewById(R.id.tvNotVotedHeader);
        TextView tvEmpty = dialogView.findViewById(R.id.tvEmptyAttendance);

        androidx.recyclerview.widget.RecyclerView rvAttend = dialogView.findViewById(R.id.rvAttend);
        androidx.recyclerview.widget.RecyclerView rvAbsent = dialogView.findViewById(R.id.rvAbsent);
        androidx.recyclerview.widget.RecyclerView rvNotVoted = dialogView.findViewById(R.id.rvNotVoted);

        rvAttend.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 2));
        rvAbsent.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 2));
        rvNotVoted.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 2));
        int space = dp(6);
        rvAttend.addItemDecoration(new GridSpacingDecoration(space));
        rvAbsent.addItemDecoration(new GridSpacingDecoration(space));
        rvNotVoted.addItemDecoration(new GridSpacingDecoration(space));

        AttendanceGridAdapter attendAdapter = new AttendanceGridAdapter();
        AttendanceGridAdapter absentAdapter = new AttendanceGridAdapter();
        AttendanceGridAdapter notVotedAdapter = new AttendanceGridAdapter();
        rvAttend.setAdapter(attendAdapter);
        rvAbsent.setAdapter(absentAdapter);
        rvNotVoted.setAdapter(notVotedAdapter);

        progress.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle("선수 목록")
                .setView(dialogView)
                .setPositiveButton("닫기", (d, w) -> d.dismiss())
                .create();

        // 닫힐 때 리스너 해제
        dialog.setOnDismissListener(d -> {
            if (attendReg != null) { attendReg.remove(); attendReg = null; }
        });

        dialog.show();

        // 이벤트 문서 먼저 읽기 (teamMemberIds, mercenaryCandidateIds 탐색)
        // 이벤트 문서 먼저 읽기 (teamMemberIds, mercenaryCandidateIds 탐색)
        db.collection("schedules").document(item.teamId)
                .collection("events").document(item.eventId)
                .get()
                .addOnSuccessListener(eventDoc -> {
                    // 원본 읽기
                    List<String> _teamMemberIds = (List<String>) eventDoc.get("teamMemberIds");
                    List<String> _mercIds       = (List<String>) eventDoc.get("mercenaryCandidateIds");

                    // ❗ 재대입 금지: final 로 확정
                    final List<String> teamMemberIds = (_teamMemberIds != null) ? _teamMemberIds : new ArrayList<>();
                    final List<String> mercIds       = (_mercIds != null) ? _mercIds : new ArrayList<>();

                    if (teamMemberIds.isEmpty()) {
                        db.collection("teams").document(item.teamId).get()
                                .addOnSuccessListener(teamDoc -> {
                                    List<String> fallback = (List<String>) teamDoc.get("members");
                                    final List<String> fallbackMembers = (fallback != null) ? fallback : new ArrayList<>();

                                    startAttendanceListening(
                                            item, progress, attendAdapter, absentAdapter, notVotedAdapter,
                                            tvAttendHeader, tvAbsentHeader, tvNotVotedHeader, tvEmpty,
                                            fallbackMembers, mercIds
                                    );
                                })
                                .addOnFailureListener(e -> {
                                    startAttendanceListening(
                                            item, progress, attendAdapter, absentAdapter, notVotedAdapter,
                                            tvAttendHeader, tvAbsentHeader, tvNotVotedHeader, tvEmpty,
                                            new ArrayList<>(), mercIds
                                    );
                                });
                    } else {
                        startAttendanceListening(
                                item, progress, attendAdapter, absentAdapter, notVotedAdapter,
                                tvAttendHeader, tvAbsentHeader, tvNotVotedHeader, tvEmpty,
                                teamMemberIds, mercIds
                        );
                    }
                })
                .addOnFailureListener(err -> {
                    progress.setVisibility(View.GONE);
                    tvEmpty.setVisibility(View.VISIBLE);
                    CustomToast.error(this, "이벤트 정보를 불러오지 못했어요.");
                });

    }

    /** attendances 실시간 리스닝 시작 + 분류/렌더 */
    private void startAttendanceListening(
            ScheduleItem it,
            ProgressBar progress,
            AttendanceGridAdapter attendAdapter,
            AttendanceGridAdapter absentAdapter,
            AttendanceGridAdapter notVotedAdapter,
            TextView tvAttendHeader,
            TextView tvAbsentHeader,
            TextView tvNotVotedHeader,
            TextView tvEmpty,
            List<String> teamMemberIds,
            List<String> mercIds
    ) {
        // 풀: 기본은 '팀 멤버' 전원
        final java.util.Set<String> poolTeam = new java.util.HashSet<>(teamMemberIds);
        // 선택 사항: 용병 후보도 함께 미투표 대상으로 보고 싶다면 포함
        final java.util.Set<String> poolMerc = new java.util.HashSet<>(mercIds);

        attendReg = db.collection("schedules").document(it.teamId)
                .collection("events").document(it.eventId)
                .collection("attendances")
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        progress.setVisibility(View.GONE);
                        CustomToast.error(this, "참석 현황을 가져오지 못했어요.");
                        return;
                    }
                    List<AttendanceGridAdapter.Item> attends = new ArrayList<>();
                    List<AttendanceGridAdapter.Item> absents = new ArrayList<>();
                    java.util.Set<String> voted = new java.util.HashSet<>();

                    if (snap != null) {
                        for (com.google.firebase.firestore.DocumentSnapshot d : snap.getDocuments()) {
                            String uid = d.getId();
                            String status = d.getString("status");
                            boolean isMerc = Boolean.TRUE.equals(d.getBoolean("isMercenary"));
                            String nick = d.getString("nickname");
                            String img = d.getString("profileImageUrl");

                            voted.add(uid);
                            AttendanceGridAdapter.Item item =
                                    new AttendanceGridAdapter.Item(uid,
                                            (nick == null ? "-" : nick),
                                            img, isMerc);

                            if ("attend".equals(status)) attends.add(item);
                            else if ("absent".equals(status)) absents.add(item);
                        }
                    }

                    // === 미투표자 계산 ===
                    // 팀 미투표: 팀풀 - voted
                    java.util.Map<String, Boolean> notVotedMap = new java.util.LinkedHashMap<>();
                    for (String id : poolTeam) {
                        if (!voted.contains(id)) {
                            notVotedMap.put(id, false); // 팀원: isMerc=false
                        }
                    }
                    // (옵션) 용병 후보도 미투표에 포함하고 싶다면 아래 유지
                    for (String id : poolMerc) {
                        if (!voted.contains(id)) {
                            notVotedMap.put(id, true); // 용병: isMerc=true
                        }
                    }

                    // 프로필 채워서 렌더
                    fetchProfiles(notVotedMap, (notVotedItems) -> {
                        progress.setVisibility(View.GONE);

                        attendAdapter.submitList(attends);
                        absentAdapter.submitList(absents);
                        notVotedAdapter.submitList(notVotedItems);

                        tvAttendHeader.setText("참석자 (" + attends.size() + ")");
                        tvAbsentHeader.setText("불참자 (" + absents.size() + ")");
                        tvNotVotedHeader.setText("미투표자 (" + notVotedItems.size() + ")");

                        tvEmpty.setVisibility(
                                attends.isEmpty() && absents.isEmpty() && notVotedItems.isEmpty()
                                        ? View.VISIBLE : View.GONE
                        );
                    });
                });
    }

    private interface ProfilesCallback {
        void onDone(java.util.List<AttendanceGridAdapter.Item> items);
    }

    /** uid → isMercenary 플래그 맵을 받아 프로필 채워 반환 */
    private void fetchProfiles(java.util.Map<String, Boolean> uidIsMercMap, ProfilesCallback cb) {
        if (uidIsMercMap == null || uidIsMercMap.isEmpty()) {
            cb.onDone(new java.util.ArrayList<>());
            return;
        }
        java.util.List<AttendanceGridAdapter.Item> out = new java.util.ArrayList<>();
        java.util.concurrent.atomic.AtomicInteger left = new java.util.concurrent.atomic.AtomicInteger(uidIsMercMap.size());

        for (java.util.Map.Entry<String, Boolean> entry : uidIsMercMap.entrySet()) {
            final String uid = entry.getKey();
            final boolean isMerc = Boolean.TRUE.equals(entry.getValue());

            db.collection("profiles").document(uid).get()
                    .addOnSuccessListener(doc -> {
                        String nick = doc.getString("nickname");
                        String img = doc.getString("profileImageUrl");
                        out.add(new AttendanceGridAdapter.Item(
                                uid,
                                (nick == null ? "-" : nick),
                                img,
                                isMerc
                        ));
                    })
                    .addOnCompleteListener(t -> {
                        if (left.decrementAndGet() == 0) {
                            cb.onDone(out);
                        }
                    });
        }
    }

    /** 그리드 간격 데코레이션 (경계선 X, 여백만) */
    private static class GridSpacingDecoration extends androidx.recyclerview.widget.RecyclerView.ItemDecoration {
        private final int space;
        GridSpacingDecoration(int spacePx) { this.space = spacePx; }

        @Override
        public void getItemOffsets(android.graphics.Rect outRect, View view,
                                   androidx.recyclerview.widget.RecyclerView parent,
                                   androidx.recyclerview.widget.RecyclerView.State state) {
            int pos = parent.getChildAdapterPosition(view);
            outRect.left = space;
            outRect.right = space;
            outRect.top = space;
            outRect.bottom = space;
        }
    }
    // ✅ 다가오는 일정 1건 선정: endTs 기준 + 진행 중(오늘) 폴백 포함
    // ✅ 다가오는 일정 1건 선정: endTs 기준 + 진행 중(오늘) 포함, Atomic으로 람다 오류 제거
    private void findNextSchedule(Runnable after) {
        setCardLoading(true);

        if (isEmpty(myTeamId)) {
            renderNextScheduleEmpty();
            setCardLoading(false);
            if (after != null) after.run();
            return;
        }

        final long now = System.currentTimeMillis();

        // 1차: endTs 기반 (정상 데이터 경로)
        db.collection("schedules").document(myTeamId)
                .collection("events")
                .whereGreaterThan("endTs", now)
                .orderBy("endTs")
                .limit(1)
                .get()
                .addOnSuccessListener(q1 -> {
                    if (!q1.isEmpty()) {
                        com.google.firebase.firestore.DocumentSnapshot d = q1.getDocuments().get(0);
                        handleChosenUpcomingDoc(d);
                        setCardLoading(false);
                        if (after != null) after.run();
                        return;
                    }

                    // 2차: matchTs 기반(미래 시작) 후보 확보
                    db.collection("schedules").document(myTeamId)
                            .collection("events")
                            .whereGreaterThan("matchTs", now)
                            .orderBy("matchTs")
                            .limit(10)
                            .get()
                            .addOnSuccessListener(q2 -> {
                                // ❗ Atomic으로 변경 가능 상태 보관
                                final java.util.concurrent.atomic.AtomicReference<com.google.firebase.firestore.DocumentSnapshot> bestRef =
                                        new java.util.concurrent.atomic.AtomicReference<>(null);
                                final java.util.concurrent.atomic.AtomicLong bestEndRef =
                                        new java.util.concurrent.atomic.AtomicLong(Long.MAX_VALUE);

                                if (!q2.isEmpty()) {
                                    for (com.google.firebase.firestore.DocumentSnapshot d : q2) {
                                        Long end = safeLong(d.getLong("endTs"));
                                        if (end == null || end <= 0) {
                                            String date = d.getString("date");
                                            String time = d.getString("time");
                                            long[] se = computeStartEndFromDateTimeStrings(date, time);
                                            end = se[1];
                                        }
                                        if (end > now && end < bestEndRef.get()) {
                                            bestEndRef.set(end);
                                            bestRef.set(d);
                                        }
                                    }
                                }

                                // 3차: 오늘 날짜(진행 중 포함)도 합쳐서 end>now 최소 end 선택
                                final String today = todayString();
                                db.collection("schedules").document(myTeamId)
                                        .collection("events")
                                        .whereEqualTo("date", today)
                                        .get()
                                        .addOnSuccessListener(q3 -> {
                                            for (com.google.firebase.firestore.DocumentSnapshot d : q3) {
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
                                                handleChosenUpcomingDoc(bestRef.get());
                                            } else {
                                                renderNextScheduleEmpty();
                                            }
                                        })
                                        .addOnFailureListener(e -> {
                                            // 오늘 쿼리 실패 시 q2 결과만으로 처리
                                            if (bestRef.get() != null) {
                                                handleChosenUpcomingDoc(bestRef.get());
                                            } else {
                                                renderNextScheduleEmpty();
                                            }
                                        })
                                        .addOnCompleteListener(t -> {
                                            setCardLoading(false);
                                            if (after != null) after.run();
                                        });
                            })
                            .addOnFailureListener(e -> {
                                // q2 자체가 실패 → 오늘 날짜만으로라도 시도
                                final String today = todayString();
                                db.collection("schedules").document(myTeamId)
                                        .collection("events")
                                        .whereEqualTo("date", today)
                                        .get()
                                        .addOnSuccessListener(q3 -> {
                                            com.google.firebase.firestore.DocumentSnapshot bestLocal = null;
                                            long bestEndLocal = Long.MAX_VALUE;
                                            for (com.google.firebase.firestore.DocumentSnapshot d : q3) {
                                                String date = d.getString("date");
                                                String time = d.getString("time");
                                                long[] se = computeStartEndFromDateTimeStrings(date, time);
                                                long end = se[1];
                                                if (end > now && end < bestEndLocal) {
                                                    bestEndLocal = end;
                                                    bestLocal = d;
                                                }
                                            }
                                            if (bestLocal != null) handleChosenUpcomingDoc(bestLocal);
                                            else renderNextScheduleEmpty();
                                        })
                                        .addOnFailureListener(e2 -> renderNextScheduleEmpty())
                                        .addOnCompleteListener(t -> {
                                            setCardLoading(false);
                                            if (after != null) after.run();
                                        });
                            });
                })
                .addOnFailureListener(e -> {
                    renderNextScheduleEmpty();
                    setCardLoading(false);
                    if (after != null) after.run();
                });
    }


    // ⛳ 선택된 문서 공통 렌더 + 시작/종료 ms 캐시
    private void handleChosenUpcomingDoc(com.google.firebase.firestore.DocumentSnapshot d) {
        String date     = d.getString("date");
        String title    = d.getString("title");
        String time     = d.getString("time"); // "HH:mm ~ HH:mm"
        String opponent = d.getString("opponentTeamName");
        String stadium  = d.getString("stadiumName");
        String address  = d.getString("stadiumAddress");
        String oppLogo  = d.getString("opponentLogoUrl");

        ScheduleItem it = new ScheduleItem(
                d.getId(), myTeamId, date,
                d.getString("status"),
                d.getString("matchId"),
                title, time, opponent, stadium, address
        );
        it.opponentLogoUrl = oppLogo;

        Long start = safeLong(d.getLong("matchTs"));
        Long end   = safeLong(d.getLong("endTs"));
        if (start == null || start <= 0 || end == null || end <= 0) {
            long[] se = computeStartEndFromDateTimeStrings(date, time);
            start = se[0];
            end   = se[1];
        }
        nextStartMs = start;
        nextEndMs   = end;

        renderNextSchedule(it);
    }

    private Long safeLong(Long v) { return (v == null || v <= 0) ? null : v; }


    /** 상단 카드 렌더 */
    private void renderNextSchedule(ScheduleItem it) {
        // 컨테이너의 '없음' 문구 제거
        LinearLayout parent = findViewById(R.id.nextScheduleContainer);
        if (parent != null) {
            View old = parent.findViewWithTag("emptyUpcomingMsg");
            if (old != null) parent.removeView(old);
        }

        // 카드 보이기
        if (nextScheduleCard != null) nextScheduleCard.setVisibility(View.VISIBLE);

        String dateChip = formatKoreanDate(it.date);
        if (!isEmpty(it.time)) dateChip = dateChip + " · " + it.time;
        tvNextDateChip.setText(dateChip);

        tvHomeName.setText(isEmpty(myTeamName) ? "My Team" : myTeamName);
        tvAwayName.setText(isEmpty(it.opponentName) ? "-" : it.opponentName);

        loadCircle(imgHomeLogo, myTeamLogoUrl);
        loadCircle(imgAwayLogo, it.opponentLogoUrl);

        tvPlace.setText(isEmpty(it.stadiumName) ? "-" : it.stadiumName);
        tvAddress.setText(isEmpty(it.address) ? "" : it.address);

        if (scheduleContent != null) scheduleContent.setVisibility(View.VISIBLE);
        if (btnVoteAttendance != null) {
            long now = System.currentTimeMillis();
            boolean canVote = now < nextStartMs; // 시작 전까지만 투표
            btnVoteAttendance.setVisibility(canVote ? View.VISIBLE : View.GONE);
        }
        nextScheduleItem = it;
    }


    /** 다가오는 일정이 없을 때: 카드 대신 텍스트만 표시 */
    /** 다가오는 일정이 없을 때: 카드 숨기고 텍스트만 표시 */
    private void renderNextScheduleEmpty() {
        // 카드 영역 숨김
        if (nextScheduleCard != null) nextScheduleCard.setVisibility(View.GONE);
        if (scheduleLoading != null) scheduleLoading.setVisibility(View.GONE);

        LinearLayout parent = findViewById(R.id.nextScheduleContainer);
        if (parent != null) {
            // 중복 추가 방지: 기존 '없음' 메시지 제거
            View old = parent.findViewWithTag("emptyUpcomingMsg");
            if (old != null) parent.removeView(old);

            TextView msg = new TextView(this);
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

            parent.addView(msg);
        }

        // 투표 버튼도 숨김
        if (btnVoteAttendance != null) btnVoteAttendance.setVisibility(View.GONE);
    }



    private void setCardLoading(boolean loading) {
        if (scheduleLoading != null) scheduleLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (scheduleContent != null) scheduleContent.setVisibility(loading ? View.GONE : View.VISIBLE);
    }

    private void showEmpty(String message){
        if (state != null) {
            state.showEmpty();
            TextView tv = findViewById(R.id.txtEmptyMessage);
            if (tv != null) tv.setText(message);
        } else {
            CustomToast.info(this, message);
        }
    }

    private String todayString() {
        Calendar c = Calendar.getInstance();
        return String.format(Locale.KOREAN, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH)+1, c.get(Calendar.DAY_OF_MONTH));
    }

    private long dateToMs(String date) {
        try {
            String[] p = date.split("-");
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.YEAR, Integer.parseInt(p[0]));
            cal.set(Calendar.MONTH, Integer.parseInt(p[1]) - 1);
            cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(p[2]));
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            return cal.getTimeInMillis();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private String formatKoreanDate(String yyyyMMdd) {
        if (isEmpty(yyyyMMdd)) return "";
        try {
            String[] p = yyyyMMdd.split("-");
            Calendar cal = Calendar.getInstance();
            cal.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
            String[] weekday = {"일","월","화","수","목","금","토"};
            String w = weekday[cal.get(Calendar.DAY_OF_WEEK) - 1];
            return String.format(Locale.KOREAN, "%s.%s.%s (%s)",
                    p[0],
                    String.format(Locale.KOREAN, "%02d", Integer.parseInt(p[1])),
                    String.format(Locale.KOREAN, "%02d", Integer.parseInt(p[2])),
                    w);
        } catch (Exception e) {
            return yyyyMMdd;
        }
    }

    private void loadCircle(ImageView iv, String url) {
        if (iv == null) return;
        if (isEmpty(url)) {
            iv.setImageResource(R.drawable.ic_placeholder_circle);
            return;
        }
        Glide.with(this)
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .placeholder(R.drawable.ic_placeholder_circle)
                .error(R.drawable.ic_placeholder_circle)
                .into(iv);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private String safe(String s) { return s == null ? "" : s; }
    private boolean isEmpty(String s){ return s == null || s.trim().isEmpty(); }
}
