// src/main/java/com/example/myapp/TeamMatchFindDialog.java
package com.example.myapp;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TeamMatchFindDialog extends DialogFragment {

    private static final String ARG_CONDITION = "arg_team_match_condition";
    private static final String ALL = "전체";

    public interface OnTeamMatchFilterSelected {
        void onTeamMatchFilterSelected(@Nullable TeamMatchCondition condition);
    }

    private TeamMatchCondition current;

    // 지역
    private LinearLayout btnCity, btnDistrict;
    private TextView textCity, textDistrict;

    // 실력
    private LinearLayout btnSkillMin, btnSkillMax;
    private TextView textSkillMin, textSkillMax;

    // 날짜
    private LinearLayout btnDateFrom, btnDateTo;
    private TextView textDateFrom, textDateTo;

    // 시간
    private LinearLayout btnTimeFrom, btnTimeTo;
    private TextView textTimeFrom, textTimeTo;

    // 요일 칩들
    private TextView chipWeekAll, chipWeekMon, chipWeekTue, chipWeekWed,
            chipWeekThu, chipWeekFri, chipWeekSat, chipWeekSun;

    // 버튼
    private TextView btnReset, btnApply, btnClose;

    private Map<String, List<String>> districtMap;

    public static TeamMatchFindDialog newInstance(@Nullable TeamMatchCondition cond) {
        TeamMatchFindDialog f = new TeamMatchFindDialog();
        Bundle b = new Bundle();
        if (cond != null) {
            b.putSerializable(ARG_CONDITION, cond);
        }
        f.setArguments(b);
        return f;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {

        View root = LayoutInflater.from(requireContext())
                .inflate(R.layout.sheet_filter_match, null, false);

        // 인자에서 기존 조건 복원
        if (getArguments() != null) {
            Serializable s = getArguments().getSerializable(ARG_CONDITION);
            if (s instanceof TeamMatchCondition) {
                current = (TeamMatchCondition) s;
            }
        }
        if (current == null) current = new TeamMatchCondition();

        bindViews(root);
        initDistrictMap();
        restoreFromCurrent();
        setupListeners();

        return new AlertDialog.Builder(requireContext())
                .setView(root)
                .create();
    }

    private void bindViews(View root) {
        // 제목 변경: "매치 필터" → "팀 찾기"
        TextView tvTitle = root.findViewById(R.id.tvTitle);
        tvTitle.setText("팀 찾기");

        // 지역
        btnCity = root.findViewById(R.id.btnCity);
        btnDistrict = root.findViewById(R.id.btnDistrict);
        textCity = root.findViewById(R.id.textCity);
        textDistrict = root.findViewById(R.id.textDistrict);

        // 실력
        btnSkillMin = root.findViewById(R.id.btnSkillMin);
        btnSkillMax = root.findViewById(R.id.btnSkillMax);
        textSkillMin = root.findViewById(R.id.textSkillMin);
        textSkillMax = root.findViewById(R.id.textSkillMax);

        // 날짜
        btnDateFrom = root.findViewById(R.id.btnDateFrom);
        btnDateTo = root.findViewById(R.id.btnDateTo);
        textDateFrom = root.findViewById(R.id.textDateFrom);
        textDateTo = root.findViewById(R.id.textDateTo);

        // 시간
        btnTimeFrom = root.findViewById(R.id.btnTimeFrom);
        btnTimeTo = root.findViewById(R.id.btnTimeTo);
        textTimeFrom = root.findViewById(R.id.textTimeFrom);
        textTimeTo = root.findViewById(R.id.textTimeTo);

        // 요일 칩
        chipWeekAll = root.findViewById(R.id.chipWeekAll);
        chipWeekMon = root.findViewById(R.id.chipWeekMon);
        chipWeekTue = root.findViewById(R.id.chipWeekTue);
        chipWeekWed = root.findViewById(R.id.chipWeekWed);
        chipWeekThu = root.findViewById(R.id.chipWeekThu);
        chipWeekFri = root.findViewById(R.id.chipWeekFri);
        chipWeekSat = root.findViewById(R.id.chipWeekSat);
        chipWeekSun = root.findViewById(R.id.chipWeekSun);

        // 버튼
        btnReset = root.findViewById(R.id.btnReset);
        btnApply = root.findViewById(R.id.btnApply);
        btnClose = root.findViewById(R.id.btnClose);
    }

    private void restoreFromCurrent() {
        // 지역
        textCity.setText(current.regionCity == null ? ALL : current.regionCity);
        textDistrict.setText(current.regionDistrict == null ? ALL : current.regionDistrict);

        // 실력
        textSkillMin.setText(current.skillMin == null ? ALL : String.valueOf(current.skillMin));
        textSkillMax.setText(current.skillMax == null ? ALL : String.valueOf(current.skillMax));

        // 날짜
        textDateFrom.setText(TextUtils.isEmpty(current.dateFrom) ? ALL : current.dateFrom);
        textDateTo.setText(TextUtils.isEmpty(current.dateTo) ? ALL : current.dateTo);

        // 시간
        textTimeFrom.setText(TextUtils.isEmpty(current.timeFrom) ? ALL : current.timeFrom);
        textTimeTo.setText(TextUtils.isEmpty(current.timeTo) ? ALL : current.timeTo);

        // 요일 칩
        restoreWeekdayChipsFromCurrent();
    }

    private void setupListeners() {

        // ===== 지역 =====
        btnCity.setOnClickListener(v -> {
            List<String> cities = new ArrayList<>(districtMap.keySet());
            if (!cities.contains(ALL)) cities.add(0, ALL);

            showPopup(btnCity, cities, selected -> {
                textCity.setText(selected);
                current.regionCity = ALL.equals(selected) ? null : selected;

                // 시/도 바뀌면 구/군 초기화
                textDistrict.setText(ALL);
                current.regionDistrict = null;
            });
        });

        btnDistrict.setOnClickListener(v -> {
            String city = textCity.getText().toString();
            List<String> districts = districtMap.get(city);
            if (districts == null) districts = districtMap.get(ALL);

            showPopup(btnDistrict, districts, selected -> {
                textDistrict.setText(selected);
                current.regionDistrict = ALL.equals(selected) ? null : selected;
            });
        });

        // ===== 실력 =====
        List<String> skills = new ArrayList<>();
        skills.add(ALL);
        for (int i = 1; i <= 10; i++) skills.add(String.valueOf(i));

        btnSkillMin.setOnClickListener(v ->
                showPopup(btnSkillMin, skills, selected -> {
                    textSkillMin.setText(selected);
                    current.skillMin = parseSkill(selected);
                })
        );

        btnSkillMax.setOnClickListener(v ->
                showPopup(btnSkillMax, skills, selected -> {
                    textSkillMax.setText(selected);
                    current.skillMax = parseSkill(selected);
                })
        );

        // ===== 날짜 =====
        btnDateFrom.setOnClickListener(v -> pickSingleDate(true));
        btnDateTo.setOnClickListener(v -> pickSingleDate(false));

        // ===== 시간 (10분 단위) =====
        btnTimeFrom.setOnClickListener(v -> showTenMinuteTimePicker(true));
        btnTimeTo.setOnClickListener(v -> showTenMinuteTimePicker(false));

        // ===== 요일 =====
        chipWeekAll.setOnClickListener(v -> selectWeekdayChip("전체"));
        chipWeekMon.setOnClickListener(v -> selectWeekdayChip("월"));
        chipWeekTue.setOnClickListener(v -> selectWeekdayChip("화"));
        chipWeekWed.setOnClickListener(v -> selectWeekdayChip("수"));
        chipWeekThu.setOnClickListener(v -> selectWeekdayChip("목"));
        chipWeekFri.setOnClickListener(v -> selectWeekdayChip("금"));
        chipWeekSat.setOnClickListener(v -> selectWeekdayChip("토"));
        chipWeekSun.setOnClickListener(v -> selectWeekdayChip("일"));

        // 닫기
        btnClose.setOnClickListener(v -> dismiss());

        // 초기화
        btnReset.setOnClickListener(v -> {
            current = new TeamMatchCondition();
            restoreFromCurrent();
        });

        // 적용
        btnApply.setOnClickListener(v -> {
            // 지역
            String c = textCity.getText().toString();
            String d = textDistrict.getText().toString();
            current.regionCity = ALL.equals(c) ? null : c;
            current.regionDistrict = ALL.equals(d) ? null : d;

            // 실력
            current.skillMin = parseSkill(textSkillMin.getText().toString());
            current.skillMax = parseSkill(textSkillMax.getText().toString());

            // 날짜
            String df = textDateFrom.getText().toString();
            String dt = textDateTo.getText().toString();
            current.dateFrom = ALL.equals(df) ? null : df;
            current.dateTo = ALL.equals(dt) ? null : dt;

            // 시간
            String tf = textTimeFrom.getText().toString();
            String tt = textTimeTo.getText().toString();
            current.timeFrom = ALL.equals(tf) ? null : tf;
            current.timeTo = ALL.equals(tt) ? null : tt;

            // 요일 current.weekday 는 selectWeekdayChip / restoreWeekdayChipsFromCurrent 에서 유지

            if (getParentFragment() instanceof OnTeamMatchFilterSelected) {
                ((OnTeamMatchFilterSelected) getParentFragment())
                        .onTeamMatchFilterSelected(current);
            }
            dismiss();
        });
    }

    // ========= 공통 팝업 =========
    private interface OnItemSelected {
        void select(String s);
    }

    private void showPopup(View anchor, List<String> items, OnItemSelected listener) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        for (String item : items) {
            popup.getMenu().add(item);
        }
        popup.setOnMenuItemClickListener(menuItem -> {
            listener.select(menuItem.getTitle().toString());
            return true;
        });
        popup.show();
    }

    // ========= 날짜 선택 =========
    private void pickSingleDate(boolean from) {
        final Calendar cal = Calendar.getInstance();
        DatePickerDialog dlg = new DatePickerDialog(requireContext(),
                (view, year, month, dayOfMonth) -> {
                    String val = String.format(Locale.getDefault(),
                            "%04d-%02d-%02d", year, month + 1, dayOfMonth);
                    if (from) {
                        current.dateFrom = val;
                        textDateFrom.setText(val);
                    } else {
                        current.dateTo = val;
                        textDateTo.setText(val);
                    }
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH));
        dlg.show();
    }

    // ========= 10분 단위 시간 =========
    private void showTenMinuteTimePicker(boolean from) {
        int initHour = 0;
        int initMinuteIndex = 0;
        String base = from ? current.timeFrom : current.timeTo;
        final String[] minuteSteps = {"00", "10", "20", "30", "40", "50"};

        if (!TextUtils.isEmpty(base) && base.contains(":")) {
            try {
                String[] sp = base.split(":");
                initHour = Integer.parseInt(sp[0]);
                String mm = sp[1];
                for (int i = 0; i < minuteSteps.length; i++) {
                    if (minuteSteps[i].equals(mm)) {
                        initMinuteIndex = i;
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }

        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setPadding(32, 32, 32, 8);

        final NumberPicker pickerHour = new NumberPicker(requireContext());
        pickerHour.setMinValue(0);
        pickerHour.setMaxValue(23);
        pickerHour.setFormatter(i -> String.format(Locale.getDefault(), "%02d", i));
        pickerHour.setValue(initHour);

        final NumberPicker pickerMinute = new NumberPicker(requireContext());
        pickerMinute.setMinValue(0);
        pickerMinute.setMaxValue(minuteSteps.length - 1);
        pickerMinute.setDisplayedValues(minuteSteps);
        pickerMinute.setValue(initMinuteIndex);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        container.addView(pickerHour, lp);
        container.addView(pickerMinute, lp);

        new AlertDialog.Builder(requireContext())
                .setTitle("시간 선택")
                .setView(container)
                .setPositiveButton("확인", (dialog, which) -> {
                    String h = String.format(Locale.getDefault(), "%02d", pickerHour.getValue());
                    String m = minuteSteps[pickerMinute.getValue()];
                    String val = h + ":" + m;
                    if (from) {
                        current.timeFrom = val;
                        textTimeFrom.setText(val);
                    } else {
                        current.timeTo = val;
                        textTimeTo.setText(val);
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    // ========= 요일 칩 =========
    private void restoreWeekdayChipsFromCurrent() {
        clearChip(chipWeekAll);
        clearChip(chipWeekMon);
        clearChip(chipWeekTue);
        clearChip(chipWeekWed);
        clearChip(chipWeekThu);
        clearChip(chipWeekFri);
        clearChip(chipWeekSat);
        clearChip(chipWeekSun);

        String raw = current.weekday;
        if (raw == null || raw.trim().isEmpty() || raw.trim().equals(ALL)) {
            setChip(chipWeekAll);
            current.weekday = ALL;
            return;
        }

        String[] parts = raw.split(",");
        for (String d : parts) {
            String v = d.trim();
            switch (v) {
                case "월": setChip(chipWeekMon); break;
                case "화": setChip(chipWeekTue); break;
                case "수": setChip(chipWeekWed); break;
                case "목": setChip(chipWeekThu); break;
                case "금": setChip(chipWeekFri); break;
                case "토": setChip(chipWeekSat); break;
                case "일": setChip(chipWeekSun); break;
            }
        }
    }

    private void selectWeekdayChip(String day) {
        String raw = current.weekday == null ? "" : current.weekday.trim();
        List<String> selected = new ArrayList<>();
        if (!raw.isEmpty() && !raw.equals(ALL)) {
            selected.addAll(Arrays.asList(raw.split(",")));
        }

        if (ALL.equals(day)) {
            selected.clear();
        } else {
            if (selected.contains(day)) {
                selected.remove(day);
            } else {
                selected.add(day);
            }
        }

        if (selected.isEmpty()) {
            current.weekday = ALL;
        } else {
            current.weekday = TextUtils.join(",", selected);
        }

        restoreWeekdayChipsFromCurrent();
    }

    private void setChip(TextView tv) {
        if (tv == null) return;
        tv.setBackgroundResource(R.drawable.bg_filter_chip_selected);
        tv.setTextColor(requireContext().getColor(android.R.color.white));
    }

    private void clearChip(TextView tv) {
        if (tv == null) return;
        tv.setBackgroundResource(R.drawable.bg_filter_chip);
        tv.setTextColor(requireContext().getColor(android.R.color.black));
    }

    private Integer parseSkill(String s) {
        if (TextUtils.isEmpty(s) || ALL.equals(s)) return null;
        try { return Integer.parseInt(s); }
        catch (Exception e) { return null; }
    }

    // ========= 지역 데이터 =========
    private void initDistrictMap() {
        districtMap = new HashMap<>();
        districtMap.put(ALL, Arrays.asList(ALL));
        districtMap.put("서울", Arrays.asList(ALL,"강남구", "강동구", "강북구", "강서구", "관악구", "광진구",
                "구로구", "금천구", "노원구", "도봉구", "동대문구", "동작구", "마포구", "서대문구",
                "서초구", "성동구", "성북구", "송파구", "양천구", "영등포구", "용산구", "은평구",
                "종로구", "중구", "중랑구"));
        districtMap.put("부산", Arrays.asList(ALL,"강서구", "금정구", "기장군", "남구", "동구", "동래구",
                "부산진구", "북구", "사상구", "사하구", "서구", "수영구", "연제구", "영도구",
                "중구", "해운대구"));
        districtMap.put("대구", Arrays.asList(ALL,"남구", "달서구", "달성군", "동구", "북구", "서구",
                "수성구", "중구"));
        districtMap.put("인천", Arrays.asList(ALL,"강화군", "계양구", "남동구", "동구", "미추홀구",
                "부평구", "서구", "연수구", "옹진군", "중구"));
        districtMap.put("광주", Arrays.asList(ALL,"광산구", "남구", "동구", "북구", "서구"));
        districtMap.put("대전", Arrays.asList(ALL,"대덕구", "동구", "서구", "유성구", "중구"));
        districtMap.put("울산", Arrays.asList(ALL,"남구", "동구", "북구", "중구", "울주군"));
        districtMap.put("세종", Arrays.asList(ALL,"세종시"));
        districtMap.put("경기도", Arrays.asList(ALL,"가평군", "고양시", "과천시", "광명시", "광주시",
                "구리시", "군포시", "김포시", "남양주시", "동두천시", "부천시", "성남시",
                "수원시", "시흥시", "안산시", "안성시", "안양시", "양주시", "양평군", "여주시",
                "연천군", "오산시", "용인시", "의왕시", "의정부시", "이천시", "파주시", "평택시",
                "포천시", "하남시", "화성시"));
        districtMap.put("강원도", Arrays.asList(ALL,"강릉시", "고성군", "동해시", "삼척시", "속초시",
                "양구군", "양양군", "영월군", "원주시", "인제군", "정선군", "철원군", "춘천시",
                "태백시", "평창군", "홍천군", "화천군", "횡성군"));
        districtMap.put("충북", Arrays.asList(ALL,"괴산군", "단양군", "보은군", "영동군", "옥천군",
                "음성군", "제천시", "증평군", "진천군", "청주시", "충주시"));
        districtMap.put("충남", Arrays.asList(ALL,"계룡시", "공주시", "금산군", "논산시", "당진시",
                "보령시", "부여군", "서산시", "서천군", "아산시", "예산군", "천안시",
                "청양군", "태안군", "홍성군"));
        districtMap.put("전북", Arrays.asList(ALL,"고창군", "군산시", "김제시", "남원시", "무주군",
                "부안군", "순창군", "완주군", "익산시", "임실군", "장수군", "전주시",
                "정읍시", "진안군"));
        districtMap.put("전남", Arrays.asList(ALL,"강진군", "고흥군", "곡성군", "광양시", "구례군",
                "나주시", "담양군", "목포시", "무안군", "보성군", "순천시", "신안군",
                "여수시", "영광군", "영암군", "완도군", "장성군", "장흥군", "진도군",
                "함평군", "해남군", "화순군"));
        districtMap.put("경북", Arrays.asList(ALL,"경산시", "경주시", "고령군", "구미시", "군위군",
                "김천시", "문경시", "봉화군", "상주시", "성주군", "안동시", "영덕군",
                "영양군", "영주시", "영천시", "예천군", "울릉군", "울진군", "의성군",
                "청도군", "청송군", "칠곡군", "포항시"));
        districtMap.put("경남", Arrays.asList(ALL,"거제시", "거창군", "고성군", "김해시", "남해군",
                "밀양시", "사천시", "산청군", "양산시", "의령군", "진주시", "창녕군",
                "창원시", "통영시", "하동군", "함안군", "함양군", "합천군"));
        districtMap.put("제주도", Arrays.asList(ALL,"서귀포시", "제주시"));
    }
}
