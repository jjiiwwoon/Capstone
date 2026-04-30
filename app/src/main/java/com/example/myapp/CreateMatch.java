// src/main/java/com/example/myapp/CreateMatch.java
package com.example.myapp;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CreateMatch extends AppCompatActivity {

    private static final int REQUEST_SELECT_STADIUM = 1001;

    private TextView txtDate, txtTime, txtAddressSearch, txtSelectedAddress;
    private EditText editStadium, editDetails;
    private Button btnSubmit;

    private String selectedDate = "";
    private String selectedStartTime = "";
    private String selectedEndTime = "";
    private String selectedAddress = "";
    private String myTeamId = "";
    private String myTeamName = "";
    private int mySkill = -1;
    private String myTeamLogoUrl = "";
    // ✅ 팀 지역도 같이 저장해서 목록 필터에서 쓰도록
    private String myTeamRegion = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.create_match);

        txtDate = findViewById(R.id.txtDate);
        txtTime = findViewById(R.id.txtTime);
        editStadium = findViewById(R.id.editStadium);
        txtAddressSearch = findViewById(R.id.txtAddressSearch);
        txtSelectedAddress = findViewById(R.id.txtSelectedAddress);
        editDetails = findViewById(R.id.editDetails);
        btnSubmit = findViewById(R.id.btnSubmit);

        loadMyTeamInfo();

        txtDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (view, y, m, d) -> {
                selectedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d);
                txtDate.setText(selectedDate);
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });

        txtTime.setOnClickListener(v -> showTimeRangePicker());

        txtAddressSearch.setOnClickListener(v -> {
            Intent i = new Intent(this, StadiumSearch.class);
            startActivityForResult(i, REQUEST_SELECT_STADIUM);
        });

        btnSubmit.setOnClickListener(v -> submitMatchPost());
    }

    private void showTimeRangePicker() {
        final View dialogView = getLayoutInflater().inflate(R.layout.dialog_time_range_picker, null);

        NumberPicker pickerStartHour = dialogView.findViewById(R.id.pickerStartHour);
        NumberPicker pickerEndHour = dialogView.findViewById(R.id.pickerEndHour);
        NumberPicker pickerStartMinute = dialogView.findViewById(R.id.pickerStartMinute);
        NumberPicker pickerEndMinute = dialogView.findViewById(R.id.pickerEndMinute);

        for (NumberPicker picker : new NumberPicker[]{pickerStartHour, pickerEndHour}) {
            picker.setMinValue(0);
            picker.setMaxValue(23);
            picker.setFormatter(i -> String.format(Locale.getDefault(), "%02d", i));
        }

        String[] minuteSteps = {"00", "10", "20", "30", "40", "50"};
        for (NumberPicker picker : new NumberPicker[]{pickerStartMinute, pickerEndMinute}) {
            picker.setMinValue(0);
            picker.setMaxValue(minuteSteps.length - 1);
            picker.setDisplayedValues(minuteSteps);
        }

        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("확인", (dialog, which) -> {
                    String startHour = String.format(Locale.getDefault(), "%02d", pickerStartHour.getValue());
                    String startMinute = minuteSteps[pickerStartMinute.getValue()];
                    String endHour = String.format(Locale.getDefault(), "%02d", pickerEndHour.getValue());
                    String endMinute = minuteSteps[pickerEndMinute.getValue()];

                    selectedStartTime = startHour + ":" + startMinute;
                    selectedEndTime = endHour + ":" + endMinute;
                    txtTime.setText(selectedStartTime + " ~ " + selectedEndTime);
                })
                .setNegativeButton("취소", null)
                .show();
    }

    // ✅ 결과 수신: 주소/이름 모두 호환 처리, 시합장소는 사용자가 이미 쓴 값이면 보존
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SELECT_STADIUM && resultCode == RESULT_OK && data != null) {

            String name = data.getStringExtra("stadiumName");
            if (name == null) name = data.getStringExtra("stadium_name");

            String addr = data.getStringExtra("stadiumAddress");
            if (addr == null) addr = data.getStringExtra("stadium_address");
            // 🔽 주소 미전달 시, 이름을 주소로 폴백 (호환성↑)
            if (addr == null) addr = name;

            // 시합장소(EditText)는 사용자가 비워둔 경우에만 채움
            if (!isEmpty(name)) {
                String currentStadium = editStadium.getText() != null ? editStadium.getText().toString().trim() : "";
                if (isEmpty(currentStadium)) {
                    editStadium.setText(name);
                }
            }

            // 주소 텍스트뷰 갱신 (별개로 관리)
            if (!isEmpty(addr)) {
                selectedAddress = addr.trim();
                txtSelectedAddress.setText(selectedAddress);
                txtSelectedAddress.setTextColor(0xFF000000);
            }
        }
    }

    private void loadMyTeamInfo() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        FirebaseFirestore.getInstance().collection("profiles").document(uid).get()
                .addOnSuccessListener(profileSnapshot -> {
                    myTeamId = safe(profileSnapshot.getString("myTeam"));
                    if (!myTeamId.isEmpty()) {
                        FirebaseFirestore.getInstance().collection("teams").document(myTeamId).get()
                                .addOnSuccessListener(teamSnapshot -> {
                                    myTeamName = safe(teamSnapshot.getString("teamName"));
                                    Long skillAvg = teamSnapshot.getLong("skillAverage");
                                    myTeamLogoUrl = safe(teamSnapshot.getString("logoUrl"));

                                    // ✅ 지역도 같이 받아두기
                                    myTeamRegion = safe(teamSnapshot.getString("region"));

                                    if (skillAvg != null) mySkill = skillAvg.intValue();
                                });
                    }
                });
    }

    // ✅ 제출: 요일/region/timeStart/timeEnd/endTs까지 저장
    private void submitMatchPost() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            CustomToast.error(this, "로그인이 필요해요.\n다시 로그인해 주세요.");
            return;
        }

        String stadium = editStadium.getText().toString().trim();
        String description = editDetails.getText().toString().trim();

        // 팀 보유 여부
        if (isEmpty(myTeamId) || isEmpty(myTeamName)) {
            CustomToast.warning(this,
                    "현재 소속된 팀이 없어 글을 작성할 수 없습니다");
            return;
        }

        // 필수값 검증
        if (isEmpty(selectedDate) || isEmpty(selectedStartTime) || isEmpty(selectedEndTime)) {
            CustomToast.info(this, "경기 일자와 시간을 선택해 주세요.");
            return;
        }
        if (isEmpty(stadium)) {
            CustomToast.info(this, "시합 장소 이름을 입력해 주세요.");
            return;
        }
        if (isEmpty(selectedAddress)) {
            CustomToast.info(this, "시합 장소 주소를 선택해 주세요.\n'시합 장소'를 눌러 검색할 수 있어요.");
            return;
        }
        if (isEmpty(description)) {
            CustomToast.info(this, "상세 내용을 입력해 주세요.");
            return;
        }
        if (mySkill < 0) {
            CustomToast.warning(this, "팀 실력 정보가 없어요.\n팀 정보를 먼저 등록해 주세요.");
            return;
        }

        long now = System.currentTimeMillis();

        long matchTs = computeMatchTs(selectedDate, selectedStartTime);
        long endTs = computeEndTs(selectedDate, selectedStartTime, selectedEndTime);
        int durationMin = (int) Math.max(10, (endTs - matchTs) / (60 * 1000));

        // ✅ 날짜에서 요일 뽑기
        String weekdayKr = getKoreanWeekday(selectedDate);

        Map<String, Object> match = new HashMap<>();
        match.put("teamId", myTeamId);
        match.put("teamName", myTeamName);
        match.put("uid", FirebaseAuth.getInstance().getCurrentUser().getUid());

        match.put("date", selectedDate);
        match.put("time", selectedStartTime + " ~ " + selectedEndTime);

        // 필터용
        match.put("timeStart", selectedStartTime);
        match.put("timeEnd", selectedEndTime);
        match.put("durationMin", durationMin);
        match.put("matchTs", matchTs);
        match.put("endTs", endTs);
        match.put("weekday", weekdayKr);
        match.put("timestamp", now);

        // 장소/주소
        match.put("stadium", stadium);
        match.put("stadiumName", stadium);
        match.put("address", selectedAddress);
        match.put("stadiumAddress", selectedAddress);

        // ✅ 팀 지역도 같이 저장
        match.put("region", myTeamRegion);

        // 로고
        match.put("logoUrl", myTeamLogoUrl);
        match.put("teamLogoUrl", myTeamLogoUrl);

        match.put("description", description);
        match.put("skill", mySkill);

        // 모집글 상태
        match.put("status", "OPEN");

        FirebaseFirestore.getInstance().collection("matches")
                .add(match)
                .addOnSuccessListener(doc -> {
                    CustomToast.success(this, "시합 모집글을 작성하였습니다");
                    finish();
                })
                .addOnFailureListener(e -> {
                    CustomToast.error(this, "등록에 실패했어요.\n잠시 후 다시 시도해 주세요.");
                });
    }

    // ✅ 시작 ms
    private long computeMatchTs(String date, String hhmm) {
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
            return sdf.parse(date + " " + hhmm).getTime();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    // ✅ 종료 ms (자정 넘김 처리)
    private long computeEndTs(String date, String startHHmm, String endHHmm) {
        long start = computeMatchTs(date, startHHmm);
        long end = computeMatchTs(date, endHHmm);
        if (end <= start) end += 24L * 60L * 60L * 1000L;
        return end;
    }

    // ✅ 날짜 → 요일 한글로
    private String getKoreanWeekday(String ymd) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Calendar c = Calendar.getInstance();
            c.setTime(sdf.parse(ymd));
            int dow = c.get(Calendar.DAY_OF_WEEK); // 1=일~7=토
            switch (dow) {
                case Calendar.MONDAY: return "월";
                case Calendar.TUESDAY: return "화";
                case Calendar.WEDNESDAY: return "수";
                case Calendar.THURSDAY: return "목";
                case Calendar.FRIDAY: return "금";
                case Calendar.SATURDAY: return "토";
                default: return "일";
            }
        } catch (Exception e) {
            return "";
        }
    }

    private boolean isEmpty(String s){ return s == null || s.trim().isEmpty(); }
    private String safe(String s){ return s == null ? "" : s; }
}
