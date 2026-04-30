// src/main/java/com/example/myapp/CreateRecruit.java
package com.example.myapp;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class CreateRecruit extends AppCompatActivity {

    // 공통/용병 뷰
    private TextView txtDate, txtTime, txtAddressSearch, txtSelectedAddress;
    private EditText editStadium, editDetails;
    private Spinner spinnerSkillMin, spinnerSkillMax;
    private Button btnSubmit, btnLoadFromSchedule;
    private RadioGroup radioRecruitType;
    private RadioButton rdoRegular, rdoMercenary;

    // 포지션: 칩 다중 선택
    private ChipGroup chipGroupPositions;
    private Chip chipGK, chipDF, chipMF, chipFW;

    // 회원(정식) 전용(읽기 전용)
    private LinearLayout containerRegular, containerMercenary;
    private TextView tvRegularDate, tvRegularTime, tvRegularStadium, tvRegularAddress;

    private final ArrayList<String> positions = new ArrayList<>();
    private final Set<String> positionSet = new HashSet<>();

    private String selectedDate = "";
    private String selectedStartTime = "";
    private String selectedEndTime = "";
    private String stadiumName  = "";
    private String stadiumAddr  = "";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    private String myUid = "";
    private String myTeamId = "";
    private String myTeamName = "";
    private String myTeamLogo = "";
    private String myRegion = "";             // 팀 활동지역(최후 보정)
    private String myHomeStadiumName = "";    // 팀 장소명칭 (homeStadiumName)
    private String myStadiumAddress  = "";    // 팀 주소 = stadium → 없으면 region
    private String myActivityDay = "";        // 팀 활동 요일 (예: "토")
    private String myTimeStart = "";          // "HH:mm"
    private String myTimeEnd = "";            // "HH:mm"

    private final ActivityResultLauncher<Intent> addressSearchLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    String name = data.getStringExtra("stadiumName");
                    if (name == null) name = data.getStringExtra("stadium_name");
                    String addr = data.getStringExtra("stadiumAddress");
                    if (addr == null) addr = data.getStringExtra("stadium_address");
                    if (addr == null) addr = name;

                    if (!TextUtils.isEmpty(name)) {
                        String current = editStadium.getText() != null ? editStadium.getText().toString().trim() : "";
                        if (TextUtils.isEmpty(current)) {
                            editStadium.setText(name);
                            stadiumName = name;
                        }
                    }
                    if (!TextUtils.isEmpty(addr)) {
                        stadiumAddr = addr.trim();
                        txtSelectedAddress.setText(stadiumAddr);
                        txtSelectedAddress.setTextColor(0xFF000000);
                    }
                }
            });

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.create_recruit);

        bindViews();
        initSpinners();
        setupPositionChips();
        initClicks();

        myUid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "";
        fetchMyProfileThenTeam();
    }

    private void bindViews() {
        radioRecruitType = findViewById(R.id.radioRecruitType);
        rdoRegular = findViewById(R.id.rdoRegular);
        rdoMercenary = findViewById(R.id.rdoMercenary);
        containerRegular = findViewById(R.id.containerRegular);
        containerMercenary = findViewById(R.id.containerMercenary);

        tvRegularDate = findViewById(R.id.tvRegularDate);
        tvRegularTime = findViewById(R.id.tvRegularTime);
        tvRegularStadium = findViewById(R.id.tvRegularStadium);
        tvRegularAddress = findViewById(R.id.tvRegularAddress);

        txtDate = findViewById(R.id.txtDate);
        txtTime = findViewById(R.id.txtTime);
        txtAddressSearch = findViewById(R.id.txtAddressSearch);
        txtSelectedAddress = findViewById(R.id.txtSelectedAddress);
        editStadium = findViewById(R.id.editStadium);
        btnLoadFromSchedule = findViewById(R.id.btnLoadFromSchedule);

        editDetails = findViewById(R.id.editDetails);
        spinnerSkillMin = findViewById(R.id.spinnerSkillMin);
        spinnerSkillMax = findViewById(R.id.spinnerSkillMax);

        chipGroupPositions = findViewById(R.id.chipGroupPositions);
        chipGK = findViewById(R.id.chipGK);
        chipDF = findViewById(R.id.chipDF);
        chipMF = findViewById(R.id.chipMF);
        chipFW = findViewById(R.id.chipFW);

        btnSubmit = findViewById(R.id.btnSubmit);
    }

    private void initSpinners() {
        ArrayList<String> skills = new ArrayList<>();
        for (int i = 0; i <= 10; i++) skills.add(String.valueOf(i));
        ArrayAdapter<String> skillAdapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, skills);
        skillAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSkillMin.setAdapter(skillAdapter);
        spinnerSkillMax.setAdapter(skillAdapter);
        spinnerSkillMin.setSelection(0);
        spinnerSkillMax.setSelection(skills.size() - 1);

        spinnerSkillMin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (spinnerSkillMax.getSelectedItemPosition() < position) {
                    spinnerSkillMax.setSelection(position);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    /** 칩 토글 시 positions/positionSet에 반영 */
    private void setupPositionChips() {
        View.OnClickListener toggle = v -> {
            Chip c = (Chip) v;
            String label = c.getText().toString();
            if (c.isChecked()) {
                if (!positionSet.contains(label)) {
                    positionSet.add(label);
                    positions.add(label);
                }
            } else {
                positionSet.remove(label);
                positions.remove(label);
            }
        };

        chipGK.setOnClickListener(toggle);
        chipDF.setOnClickListener(toggle);
        chipMF.setOnClickListener(toggle);
        chipFW.setOnClickListener(toggle);
    }

    private void initClicks() {
        radioRecruitType.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isRegular = (checkedId == R.id.rdoRegular);
            containerRegular.setVisibility(isRegular ? View.VISIBLE : View.GONE);
            containerMercenary.setVisibility(isRegular ? View.GONE : View.VISIBLE);
            if (isRegular) applyTeamToRegularUI();
        });

        txtDate.setOnClickListener(v -> pickDate());
        txtTime.setOnClickListener(v -> showTimeRangePicker());
        txtAddressSearch.setOnClickListener(v -> addressSearchLauncher.launch(new Intent(this, StadiumSearch.class)));

        // ✅ 바텀시트 스케줄 픽커로 일정 불러오기
        btnLoadFromSchedule.setOnClickListener(v -> {
            if (TextUtils.isEmpty(myTeamId)) {
                CustomToast.error(this, "팀 정보를 불러오지 못했습니다.");
                return;
            }
            SchedulePickerDialog dialog = SchedulePickerDialog.newInstance(myTeamId, myTeamId, myTeamName);
            dialog.setOnScheduleSelected(new SchedulePickerDialog.OnScheduleSelected() {
                @Override
                public void onSelected(DocumentSnapshot d) {
                    String dDate = safeStr(d.getString("date"));
                    String dTime = safeStr(d.getString("time"));
                    String dStad = safeStr(d.getString("stadiumName"));
                    String dAddr = safeStr(d.getString("stadiumAddress"));

                    if (!TextUtils.isEmpty(dDate)) { selectedDate = dDate; txtDate.setText(dDate); }
                    if (!TextUtils.isEmpty(dTime)) {
                        String[] se = splitTimeRange(dTime);
                        selectedStartTime = se[0];
                        selectedEndTime   = se[1];
                        txtTime.setText(TextUtils.isEmpty(dTime) ? (se[0] + " ~ " + se[1]) : dTime);
                    }
                    if (!TextUtils.isEmpty(dStad)) { editStadium.setText(dStad); stadiumName = dStad; }
                    if (!TextUtils.isEmpty(dAddr)) {
                        stadiumAddr = dAddr;
                        txtSelectedAddress.setText(dAddr);
                        txtSelectedAddress.setTextColor(0xFF000000);
                    }
                }
            });
            dialog.show(getSupportFragmentManager(), "SchedulePickerDialog");
        });

        btnSubmit.setOnClickListener(v -> submit());
    }

    /** 프로필 → 팀 문서 로드 */
    private void fetchMyProfileThenTeam() {
        if (TextUtils.isEmpty(myUid)) return;
        db.collection("profiles").document(myUid).get().addOnSuccessListener(pf -> {
            if (!pf.exists()) return;
            myTeamId = safeStr(pf.getString("myTeam"));
            if (TextUtils.isEmpty(myTeamId)) return;

            db.collection("teams").document(myTeamId).get().addOnSuccessListener(ts -> {
                if (!ts.exists()) return;

                myTeamName   = safeStr(ts.getString("teamName"));
                myTeamLogo   = safeStr(ts.getString("logoUrl"));
                myRegion     = safeStr(ts.getString("region"));

                // 장소명칭 / 주소 분리: 주소는 stadium을 우선 사용(없으면 region)
                myHomeStadiumName = safeStr(ts.getString("homeStadiumName"));
                String stadiumFromDoc = safeStr(ts.getString("stadium")); // ← 주소로 쓰던 필드
                myStadiumAddress = TextUtils.isEmpty(stadiumFromDoc) ? myRegion : stadiumFromDoc;

                myActivityDay= safeStr(ts.getString("activityDay"));
                myTimeStart  = safeStr(ts.getString("timeStart"));
                myTimeEnd    = safeStr(ts.getString("timeEnd"));

                applyTeamToRegularUI();
            });
        });
    }

    /** 회원(정식): 팀 정보 바인딩 + 활동요일/시간 자동 채움 */
    private void applyTeamToRegularUI() {
        tvRegularStadium.setText(TextUtils.isEmpty(myHomeStadiumName) ? "주활동구장 미설정" : myHomeStadiumName);
        tvRegularAddress.setText(TextUtils.isEmpty(myStadiumAddress) ? "주소 미설정" : myStadiumAddress);

        stadiumName = myHomeStadiumName;
        stadiumAddr = myStadiumAddress;

        String timeRange = (!TextUtils.isEmpty(myTimeStart) && !TextUtils.isEmpty(myTimeEnd))
                ? (myTimeStart + " ~ " + myTimeEnd) : "";

        if (!TextUtils.isEmpty(myActivityDay)) {
            String next = computeNextDateForDay(myActivityDay, myTimeStart);
            selectedDate = next;
            tvRegularDate.setText(next);
        } else {
            tvRegularDate.setText("활동일 미설정");
        }

        if (!TextUtils.isEmpty(timeRange)) {
            tvRegularTime.setText(timeRange);
            selectedStartTime = myTimeStart;
            selectedEndTime = myTimeEnd;
        } else {
            tvRegularTime.setText("활동시간 미설정");
            selectedStartTime = "";
            selectedEndTime = "";
        }
    }

    private void pickDate() {
        final Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, y, m, d) -> {
            String mm = String.format(Locale.getDefault(), "%02d", m + 1);
            String dd = String.format(Locale.getDefault(), "%02d", d);
            selectedDate = y + "-" + mm + "-" + dd;
            txtDate.setText(selectedDate);
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimeRangePicker() {
        final View dialogView = getLayoutInflater().inflate(R.layout.dialog_time_range_picker, null);
        NumberPicker sh = dialogView.findViewById(R.id.pickerStartHour);
        NumberPicker eh = dialogView.findViewById(R.id.pickerEndHour);
        NumberPicker sm = dialogView.findViewById(R.id.pickerStartMinute);
        NumberPicker em = dialogView.findViewById(R.id.pickerEndMinute);

        for (NumberPicker p : new NumberPicker[]{sh,eh}) { p.setMinValue(0); p.setMaxValue(23); p.setFormatter(i -> String.format(Locale.getDefault(), "%02d", i)); }
        String[] mins = {"00","10","20","30","40","50"};
        for (NumberPicker p : new NumberPicker[]{sm,em}) { p.setMinValue(0); p.setMaxValue(mins.length-1); p.setDisplayedValues(mins); }

        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("확인", (d,w) -> {
                    selectedStartTime = String.format(Locale.getDefault(), "%02d", sh.getValue()) + ":" + mins[sm.getValue()];
                    selectedEndTime   = String.format(Locale.getDefault(), "%02d", eh.getValue()) + ":" + mins[em.getValue()];
                    txtTime.setText(selectedStartTime + " ~ " + selectedEndTime);
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void submit() {
        boolean isRegular = (radioRecruitType.getCheckedRadioButtonId() == R.id.rdoRegular);

        if (isRegular) {
            if (TextUtils.isEmpty(selectedDate)) {
                CustomToast.info(this, "팀 활동일이 설정되지 않았습니다.");
                return;
            }
            if (TextUtils.isEmpty(selectedStartTime) || TextUtils.isEmpty(selectedEndTime)) {
                CustomToast.info(this, "팀 활동시간이 설정되지 않았습니다.");
                return;
            }
            if (TextUtils.isEmpty(stadiumName)) stadiumName = myHomeStadiumName;
            if (TextUtils.isEmpty(stadiumAddr)) stadiumAddr = myStadiumAddress;
        } else {
            stadiumName = editStadium.getText() != null ? editStadium.getText().toString().trim() : "";
            if (TextUtils.isEmpty(selectedDate)) { CustomToast.info(this, "날짜를 선택하세요."); return; }
            if (TextUtils.isEmpty(selectedStartTime) || TextUtils.isEmpty(selectedEndTime)) { CustomToast.info(this, "시간을 선택하세요."); return; }
            if (TextUtils.isEmpty(stadiumName)) { CustomToast.info(this, "시합장소를 입력하세요."); return; }
        }

        String details = editDetails.getText() != null ? editDetails.getText().toString().trim() : "";
        int skillMin = parseInt(String.valueOf(spinnerSkillMin.getSelectedItem()), 0);
        int skillMax = parseInt(String.valueOf(spinnerSkillMax.getSelectedItem()), 10);
        if (skillMin > skillMax) { CustomToast.warning(this, "실력 범위를 올바르게 선택하세요."); return; }

        String recruitType = isRegular ? "regular" : "mercenary";
        String timeRange = selectedStartTime + " ~ " + selectedEndTime;

        long matchTs = combineToMillis(selectedDate, selectedStartTime);
        long endTs   = computeEndTs(selectedDate, selectedStartTime, selectedEndTime);  // ✅ 추가
        long nowMs   = System.currentTimeMillis();

        // ✅ 날짜 → 요일
        String weekdayKr = getKoreanWeekday(selectedDate);

        Map<String,Object> data = new HashMap<>();
        data.put("teamId", myTeamId);
        data.put("teamName", myTeamName);
        data.put("teamLogoUrl", myTeamLogo);
        data.put("region", myRegion);

        data.put("date", selectedDate);
        data.put("time", timeRange);

        // ✅ 필터용으로 추가
        data.put("timeStart", selectedStartTime);
        data.put("timeEnd", selectedEndTime);
        data.put("matchTs", matchTs);
        data.put("endTs", endTs);
        data.put("weekday", weekdayKr);

        data.put("postTs", matchTs);
        data.put("timestamp", nowMs);
        data.put("createdAtMs", nowMs);

        data.put("stadiumName", stadiumName);
        data.put("stadiumAddress", stadiumAddr);

        data.put("skillMin", skillMin);
        data.put("skillMax", skillMax);
        data.put("positions", new ArrayList<>(positions));
        data.put("recruitType", recruitType);

        data.put("intro", details);
        data.put("status", "open");
        data.put("createdBy", myUid);

        data.put("createdAt", com.google.firebase.Timestamp.now());
        data.put("updatedAt", com.google.firebase.Timestamp.now());

        db.collection("recruitPosts").add(data)
                .addOnSuccessListener(r -> {
                    CustomToast.success(this, "모집글이 등록되었습니다.");
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> CustomToast.error(this, "등록 실패: " + e.getMessage()));
    }

    // ===== 유틸 =====
    private String safeStr(String s){ return s==null?"":s.trim(); }
    private int parseInt(String s,int d){ try{return Integer.parseInt(s);}catch(Exception e){return d;} }

    private long combineToMillis(String ymd, String hhmm) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        try { return Objects.requireNonNull(sdf.parse(ymd + " " + hhmm)).getTime(); }
        catch (ParseException e) { return System.currentTimeMillis(); }
    }

    private String[] splitTimeRange(String timeRange) {
        if (TextUtils.isEmpty(timeRange)) return new String[]{"19:00","21:00"};
        String t = timeRange.replace(" ~ ", "~");
        String[] sp = t.split("~");
        if (sp.length == 2) return new String[]{sp[0].trim(), sp[1].trim()};
        return new String[]{"19:00","21:00"};
    }

    /** 활동일(월~일) + 시작시간 기반으로 가장 가까운 yyyy-MM-dd 계산 */
    private String computeNextDateForDay(String activityDay, String startTime) {
        int target = parseKoreanWeekday(activityDay);
        Calendar now = Calendar.getInstance();
        if (target == -1) {
            return String.format(Locale.getDefault(), "%04d-%02d-%02d",
                    now.get(Calendar.YEAR), now.get(Calendar.MONTH)+1, now.get(Calendar.DAY_OF_MONTH));
        }
        int today = mapCalendarDow(now.get(Calendar.DAY_OF_WEEK));
        int add = (target - today + 7) % 7;
        if (add == 0 && !TextUtils.isEmpty(startTime)) {
            try {
                String[] hm = startTime.split(":");
                Calendar start = (Calendar) now.clone();
                start.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hm[0]));
                start.set(Calendar.MINUTE, Integer.parseInt(hm[1]));
                start.set(Calendar.SECOND, 0);
                if (now.after(start)) add = 7;
            } catch (Exception ignore) {}
        }
        Calendar cal = (Calendar) now.clone();
        cal.add(Calendar.DAY_OF_MONTH, add);
        return String.format(Locale.getDefault(), "%04d-%02d-%02d",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH)+1, cal.get(Calendar.DAY_OF_MONTH));
    }

    private int parseKoreanWeekday(String s) {
        if (TextUtils.isEmpty(s)) return -1;
        String t = s.replace("매주","").replace("요일","").trim();
        switch (t) {
            case "월": return 1;
            case "화": return 2;
            case "수": return 3;
            case "목": return 4;
            case "금": return 5;
            case "토": return 6;
            case "일": return 0;
        }
        return -1;
    }

    /** Calendar.DAY_OF_WEEK(1=일~7=토) → 0=일 ~ 6=토 */
    private int mapCalendarDow(int dow) {
        switch (dow) {
            case Calendar.SUNDAY: return 0;
            case Calendar.MONDAY: return 1;
            case Calendar.TUESDAY: return 2;
            case Calendar.WEDNESDAY: return 3;
            case Calendar.THURSDAY: return 4;
            case Calendar.FRIDAY: return 5;
            case Calendar.SATURDAY: return 6;
        }
        return 0;
    }

    // ✅ 날짜 → 요일 저장용
    private String getKoreanWeekday(String ymd) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Calendar c = Calendar.getInstance();
            c.setTime(sdf.parse(ymd));
            int dow = c.get(Calendar.DAY_OF_WEEK);
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

    // ✅ 종료 ms 계산 (자정 넘는 경기 처리)
    private long computeEndTs(String date, String startHHmm, String endHHmm) {
        long start = combineToMillis(date, startHHmm);
        long end = combineToMillis(date, endHHmm);
        if (end <= start) {
            end += 24L * 60L * 60L * 1000L;
        }
        return end;
    }
}
