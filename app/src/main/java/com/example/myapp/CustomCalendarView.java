// app/src/main/java/com/example/myapp/CustomCalendarView.java
package com.example.myapp;

import android.app.AlertDialog;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 커스텀 캘린더 뷰
 * - 오늘: 파란 테두리(bg_rect_today)
 * - 선택: 연한 파랑 배경 + 파란 테두리(bg_rect_selected)
 * - 일정 있음: 셀 하단에 "Match" + 축구공 아이콘 노출
 *
 * 변경점
 * - status 무시
 * - matchTs 초/밀리초 혼재 방어(초 단위면 ×1000)
 * - 종료 판정: endTs > now ? 미래 : 과거
 *   · endTs 없으면 time("HH:mm ~ HH:mm" 또는 "HH:mm")로 종료시각 계산
 *   · time 없으면 종료판단 불가 → FAR_FUTURE로 두어 과거로 오판하지 않게
 */
public class CustomCalendarView extends LinearLayout {

    private TextView tvCurrentMonth, btnPrevMonth, btnNextMonth;
    private RecyclerView calendarRecyclerView;
    private CalendarAdapter adapter;

    /** 외부에서 참조 가능한 현재 달 */
    public static Calendar currentCalendar;

    /** 선택 상태(같은 연/월일 때만 유지) */
    private int selectedYear = -1, selectedMonth = -1, selectedDay = -1;

    // 종료시간 계산 기본값 및 상수
    private static final long DEFAULT_MATCH_DURATION_MS = 2L * 60L * 60L * 1000L; // 2시간
    private static final long FAR_FUTURE = Long.MAX_VALUE / 4; // 과거로 잘못 떨어지지 않게 매우 큰 값

    // ─────────────────────────────────────────────────────────────
    // 클릭 콜백
    public interface OnDateClickListener {
        void onDateClick(int year, int month, int day); // month: 0-based
    }
    private OnDateClickListener onDateClickListener;
    public void setOnDateClickListener(OnDateClickListener listener) { this.onDateClickListener = listener; }
    // ─────────────────────────────────────────────────────────────

    public CustomCalendarView(Context context) {
        super(context);
        init(context);
    }

    public CustomCalendarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_custom_calendar, this, true);

        tvCurrentMonth       = findViewById(R.id.tvCurrentMonth);
        btnPrevMonth         = findViewById(R.id.btnPrevMonth);
        btnNextMonth         = findViewById(R.id.btnNextMonth);
        calendarRecyclerView = findViewById(R.id.calendarRecyclerView);

        calendarRecyclerView.setLayoutManager(new GridLayoutManager(context, 7));
        adapter = new CalendarAdapter();
        calendarRecyclerView.setAdapter(adapter);

        currentCalendar = Calendar.getInstance();
        updateCalendar();

        // 이전/다음 달
        btnPrevMonth.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, -1);
            selectedDay = -1; // 달 변경 시 선택 해제
            updateCalendar();
        });
        btnNextMonth.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, 1);
            selectedDay = -1;
            updateCalendar();
        });

        // 연/월 선택 다이얼로그
        tvCurrentMonth.setOnClickListener(v -> showMonthYearPicker());

        // 날짜 클릭 → 선택 상태 반영 + 외부 콜백
        adapter.setOnItemClickListener((position, calendarDate) -> {
            if (!calendarDate.inThisMonth || onDateClickListener == null) return;

            int y = currentCalendar.get(Calendar.YEAR);
            int m = currentCalendar.get(Calendar.MONTH); // 0-based
            int d = calendarDate.day;

            selectedYear  = y;
            selectedMonth = m;
            selectedDay   = d;

            adapter.setSelectedDay(y, m, d);
            onDateClickListener.onDateClick(y, m, d);
        });
    }

    /** 현재 달의 데이터로 캘린더 갱신 */
    private void updateCalendar() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy년 M월", Locale.KOREAN);
        tvCurrentMonth.setText(sdf.format(currentCalendar.getTime()));

        List<CalendarDate> days = new ArrayList<>();

        // 1) 이전 달 자리
        Calendar temp = (Calendar) currentCalendar.clone();
        temp.set(Calendar.DAY_OF_MONTH, 1);
        int firstDayOfWeek = temp.get(Calendar.DAY_OF_WEEK) - 1; // 0:일 ~ 6:토

        temp.add(Calendar.MONTH, -1);
        int prevMonthLastDay = temp.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int i = firstDayOfWeek - 1; i >= 0; i--) {
            days.add(new CalendarDate(prevMonthLastDay - i, false, false));
        }

        // 2) 이번 달 날짜
        temp = (Calendar) currentCalendar.clone();
        int maxDay = temp.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int i = 1; i <= maxDay; i++) {
            days.add(new CalendarDate(i, true, false));
        }

        // 3) 다음 달 자리(6x7=42개 고정)
        int addCount = 42 - days.size();
        for (int i = 1; i <= addCount; i++) {
            days.add(new CalendarDate(i, false, false));
        }

        // 리스트 바인딩
        adapter.setDays(days);

        // 현재 연/월 전달
        int y = currentCalendar.get(Calendar.YEAR);
        int m = currentCalendar.get(Calendar.MONTH);
        adapter.setYearMonth(y, m);

        // 같은 연/월이면 선택 유지
        if (selectedYear == y && selectedMonth == m) {
            adapter.setSelectedDay(y, m, selectedDay);
        } else {
            adapter.setSelectedDay(y, m, -1);
        }

        // 일정이 있는 날 표시(종료시간 기준으로 과거/미래 판정)
        fetchScheduleDays(days);
    }

    /** 연/월 선택 다이얼로그 */
    private void showMonthYearPicker() {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_select_year_month, null);
        Spinner spinnerYear  = dialogView.findViewById(R.id.spinnerYear);
        Spinner spinnerMonth = dialogView.findViewById(R.id.spinnerMonth);

        List<Integer> years = new ArrayList<>();
        for (int i = 2015; i <= 2035; i++) years.add(i);

        List<String> months = new ArrayList<>();
        for (int i = 1; i <= 12; i++) months.add(i + "월");

        ArrayAdapter<Integer> yearAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, years);
        ArrayAdapter<String> monthAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, months);
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spinnerYear.setAdapter(yearAdapter);
        spinnerMonth.setAdapter(monthAdapter);

        spinnerYear.setSelection(years.indexOf(currentCalendar.get(Calendar.YEAR)));
        spinnerMonth.setSelection(currentCalendar.get(Calendar.MONTH));

        new AlertDialog.Builder(getContext())
                .setTitle("연도 / 월 선택")
                .setView(dialogView)
                .setPositiveButton("확인", (d, which) -> {
                    int selectedY = years.get(spinnerYear.getSelectedItemPosition());
                    int selectedM = spinnerMonth.getSelectedItemPosition();

                    currentCalendar.set(Calendar.YEAR, selectedY);
                    currentCalendar.set(Calendar.MONTH, selectedM);

                    selectedDay = -1;
                    updateCalendar();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    /**
     * Firestore에서 해당 달의 이벤트를 읽어 셀에 hasMatch/isPastMatch 반영
     * - 경로: schedules/{myTeamId}/events
     * - 사용 필드: date("yyyy-MM-dd"), endTs(number, 선택), matchTs(number, 선택), time(string, "HH:mm" / "HH:mm ~ HH:mm")
     * - status는 사용하지 않음
     */
    private void fetchScheduleDays(List<CalendarDate> days) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return;

        String myUid = auth.getCurrentUser().getUid();

        db.collection("profiles").document(myUid).get()
                .addOnSuccessListener(profileSnap -> {
                    String myTeamId = profileSnap.getString("myTeam");
                    if (myTeamId == null) return;

                    db.collection("schedules").document(myTeamId)
                            .collection("events")
                            .get()
                            .addOnSuccessListener(querySnapshot -> {
                                String ym = getCurrentYearMonthString(); // "yyyy-MM"
                                final long now = System.currentTimeMillis();

                                // 오늘(로컬) yyyy-MM-dd (선택유지 로직용 — 종료판정에는 now 사용)
                                Calendar today = Calendar.getInstance();
                                int tY = today.get(Calendar.YEAR);
                                int tM = today.get(Calendar.MONTH);

                                for (QueryDocumentSnapshot doc : querySnapshot) {
                                    String date = doc.getString("date"); // "yyyy-MM-dd"
                                    if (date == null || !date.startsWith(ym)) continue;

                                    // 필드 추출
                                    Long endTs = getNumberAsLong(doc.get("endTs"));
                                    String time = doc.getString("time"); // "HH:mm ~ HH:mm" 또는 "HH:mm" 또는 null

                                    // 종료시각 계산
                                    long endMs;
                                    if (endTs != null) {
                                        endMs = normalizeToMillis(endTs);
                                    } else {
                                        endMs = computeEndMsFrom(date, time);
                                    }

                                    // day 인덱스
                                    int eD;
                                    try {
                                        eD = Integer.parseInt(date.substring(8, 10));
                                    } catch (Exception e) {
                                        continue;
                                    }

                                    // 해당 셀에 반영
                                    for (CalendarDate cd : days) {
                                        if (cd.inThisMonth && cd.day == eD) {
                                            cd.hasMatch = true;
                                            cd.isPastMatch = (endMs <= now);
                                            break;
                                        }
                                    }
                                }

                                // 갱신 반영(선택/연월 유지)
                                adapter.setDays(days);
                                int y = currentCalendar.get(Calendar.YEAR);
                                int m = currentCalendar.get(Calendar.MONTH);
                                adapter.setYearMonth(y, m);
                                if (selectedYear == y && selectedMonth == m) {
                                    adapter.setSelectedDay(y, m, selectedDay);
                                } else {
                                    adapter.setSelectedDay(y, m, -1);
                                }
                            });
                });
    }

    // 숫자(Long/Integer/Double 등) → Long 변환
    private Long getNumberAsLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        return null;
    }

    // 초/밀리초 혼재 방지: 10^12 미만이면 초 → ms로 보정
    private long normalizeToMillis(long ts) {
        return (ts < 1_000_000_000_000L) ? ts * 1000L : ts;
    }

    /** date("yyyy-MM-dd"), time("HH:mm ~ HH:mm" or "HH:mm" or null) → 종료 ms */
    private long computeEndMsFrom(String date, String time) {
        if (time == null || time.trim().isEmpty()) {
            // time 없으면 종료 판단 불가 → 과거로 오판하지 않도록 아주 먼 미래
            return FAR_FUTURE;
        }
        String t = time.trim();
        if (t.contains("~")) {
            // "HH:mm ~ HH:mm" 형태
            String[] p = t.split("~");
            if (p.length < 2) return FAR_FUTURE;
            String startHHmm = p[0].trim();
            String endHHmm   = p[1].trim();
            long start = parseDateTimeMs(date, startHHmm);
            long end   = parseDateTimeMs(date, endHHmm);
            if (end <= start) end += 24L * 60L * 60L * 1000L; // 자정 넘김 보정
            return end;
        } else {
            // "HH:mm" 형태 → 시작 + 2시간
            long start = parseDateTimeMs(date, t);
            return start + DEFAULT_MATCH_DURATION_MS;
        }
    }

    private long parseDateTimeMs(String date, String hhmm) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            return sdf.parse(date + " " + hhmm).getTime();
        } catch (Exception e) {
            // 파싱 실패 시 현재시간 사용(과거로 잘못 판정되지 않도록)
            return System.currentTimeMillis() + DEFAULT_MATCH_DURATION_MS;
        }
    }

    private String getCurrentYearMonthString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM", Locale.KOREAN);
        return sdf.format(currentCalendar.getTime());
    }

    // ─────────────────────────────────────────────────────────────
    // 날짜 모델(기존 설계 + isPastMatch: 종료시간 기준)
    public static class CalendarDate {
        public int day;
        public boolean inThisMonth;
        public boolean hasMatch;
        public boolean isPastMatch; // 종료시간 < 현재시간이면 true

        public CalendarDate(int day, boolean inThisMonth) {
            this(day, inThisMonth, false);
        }
        public CalendarDate(int day, boolean inThisMonth, boolean hasMatch) {
            this.day = day;
            this.inThisMonth = inThisMonth;
            this.hasMatch = hasMatch;
            this.isPastMatch = false;
        }
    }
}
