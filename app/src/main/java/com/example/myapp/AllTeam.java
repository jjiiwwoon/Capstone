package com.example.myapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AllTeam extends Fragment {

    private static final String TAG = "AllTeam";
    private static final String ALL = "전체";

    private LinearLayout btnCity, btnDistrict, btnSkill, btnAgeStart, btnAgeEnd;
    private TextView textCity, textDistrict, textSkill, textAgeStart, textAgeEnd;
    private RecyclerView recyclerViewTeams;
    private TeamAdapter teamAdapter;
    private final List<Team> teamList = new ArrayList<>();
    private FirebaseFirestore firestore;
    private Map<String, List<String>> districtMap;

    private EditText editTeamSearch;
    private FloatingActionButton btnCreateTeam;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.all_team, container, false);
        initViews(view);

        initDistrictMap();
        setupPopupMenus();

        firestore = FirebaseFirestore.getInstance();
        teamAdapter = new TeamAdapter(getContext(), teamList);
        recyclerViewTeams.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewTeams.setAdapter(teamAdapter);

        loadTeamsFromFirestore();

        editTeamSearch.addTextChangedListener(new TextWatcher() {
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterTeams(); }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
        });

        setupCreateTeamButton();

        teamAdapter.setOnItemClickListener(team -> {
            Intent intent = new Intent(getActivity(), TeamDetail.class);
            intent.putExtra("teamId", team.getTeamId());
            startActivity(intent);
        });

        return view;
    }

    private void initViews(View view) {
        btnCity = view.findViewById(R.id.btnCity);
        btnDistrict = view.findViewById(R.id.btnDistrict);
        btnSkill = view.findViewById(R.id.btnSkill);
        btnAgeStart = view.findViewById(R.id.btnAgeStart);
        btnAgeEnd = view.findViewById(R.id.btnAgeEnd);

        textCity = view.findViewById(R.id.textCity);
        textDistrict = view.findViewById(R.id.textDistrict);
        textSkill = view.findViewById(R.id.textSkill);
        textAgeStart = view.findViewById(R.id.textAgeStart);
        textAgeEnd = view.findViewById(R.id.textAgeEnd);

        recyclerViewTeams = view.findViewById(R.id.recyclerViewTeams);
        editTeamSearch = view.findViewById(R.id.editTeamSearch);
        btnCreateTeam = view.findViewById(R.id.btnCreateTeam);
    }

    private void setupPopupMenus() {
        String[] cities = getResources().getStringArray(R.array.city_array);
        String[] skills = getResources().getStringArray(R.array.skill_filter_array);
        String[] ages = getResources().getStringArray(R.array.age_array);

        btnCity.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(getContext(), btnCity);
            for (String item : cities) popup.getMenu().add(item);
            popup.setOnMenuItemClickListener(menuItem -> {
                String selected = menuItem.getTitle().toString();
                textCity.setText(selected);
                textDistrict.setText(ALL);
                updateDistrictPopup(selected);
                filterTeams();
                return true;
            });
            popup.show();
        });

        setupPopupMenu(btnDistrict, textDistrict, new String[]{ALL}, selected -> {
            textDistrict.setText(selected);
            filterTeams();
        });

        setupPopupMenu(btnSkill, textSkill, skills, selected -> {
            textSkill.setText(selected);
            filterTeams();
        });

        setupPopupMenu(btnAgeStart, textAgeStart, ages, selected -> {
            textAgeStart.setText(selected);
            filterTeams();
        });

        setupPopupMenu(btnAgeEnd, textAgeEnd, ages, selected -> {
            textAgeEnd.setText(selected);
            filterTeams();
        });
    }

    private void updateDistrictPopup(String city) {
        List<String> districts = districtMap.get(city);
        if (districts != null) {
            setupPopupMenu(btnDistrict, textDistrict, districts.toArray(new String[0]), selected -> {
                textDistrict.setText(selected);
                filterTeams();
            });
        }
    }

    private void setupPopupMenu(View btnLayout, TextView textView, String[] items, OnPopupItemSelected listener) {
        btnLayout.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(getContext(), btnLayout);
            for (String item : items) popup.getMenu().add(item);
            popup.setOnMenuItemClickListener(menuItem -> {
                listener.onSelected(menuItem.getTitle().toString());
                return true;
            });
            popup.show();
        });
    }

    interface OnPopupItemSelected { void onSelected(String selected); }

    private void loadTeamsFromFirestore() {
        firestore.collection("teams")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.w(TAG, "팀 목록 수신 실패", e);
                        return;
                    }
                    if (snapshots == null) return;

                    teamList.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        try {
                            Team team = doc.toObject(Team.class);
                            if (team == null) continue;
                            team.setTeamId(doc.getId());
                            teamList.add(team);
                        } catch (Exception ex) {
                            Log.e(TAG, "팀 파싱 실패: " + doc.getId(), ex);
                        }
                    }
                    // 원본 리스트 갱신 후 현재 필터 적용
                    filterTeams();
                });
    }

    private void filterTeams() {
        String keyword = safeLower(editTeamSearch.getText());
        String selectedCity = safeText(textCity);
        String selectedDistrict = safeText(textDistrict);
        String selectedSkill = safeText(textSkill);
        String ageStartStr = safeText(textAgeStart).replace("~", "");
        String ageEndStr = safeText(textAgeEnd).replace("~", "");

        // 나이 필터
        int ageStart = 0;
        int ageEnd = 100;
        boolean filterByAge = !(ALL.equals(ageStartStr) || ALL.equals(ageEndStr) || (TextUtils.isEmpty(ageStartStr) && TextUtils.isEmpty(ageEndStr)));
        if (filterByAge) {
            ageStart = parseIntOrDefault(ageStartStr, 0);
            ageEnd = TextUtils.isEmpty(ageEndStr) ? 100 : parseIntOrDefault(ageEndStr, 100);
            if (ageStart > ageEnd) { // 사용자가 역순으로 선택했을 때 방어
                int tmp = ageStart; ageStart = ageEnd; ageEnd = tmp;
            }
        }

        // 스킬(=skillAverage) 필터
        boolean filterBySkill = !ALL.equals(selectedSkill);
        int skillTarget = filterBySkill ? parseIntOrDefault(selectedSkill, -1) : -1;

        List<Team> filteredList = new ArrayList<>();

        for (Team team : teamList) {
            if (team == null) continue;

            String name = team.getTeamName() == null ? "" : team.getTeamName();
            String region = team.getRegion() == null ? "" : team.getRegion();
            String ageRangeStr = team.getAgeRange() == null ? "" : team.getAgeRange();

            boolean matchKeyword = name.toLowerCase().contains(keyword);
            boolean matchCity = ALL.equals(selectedCity) || region.contains(selectedCity);
            boolean matchDistrict = ALL.equals(selectedDistrict) || region.contains(selectedDistrict);

            boolean matchSkill = true;
            if (filterBySkill) {
                matchSkill = (team.getSkillAverage() == skillTarget);
            }

            boolean matchAge = true;
            if (filterByAge) {
                try {
                    String[] ageRange = ageRangeStr.split("~");
                    int teamAgeStart = parseIntOrDefault(ageRange[0], 0);
                    int teamAgeEnd = (ageRange.length > 1) ? parseIntOrDefault(ageRange[1], 100) : 100;
                    // 팀의 나이 범위가 선택한 범위에 "완전히 포함"되는 조건 (기존 로직 유지)
                    matchAge = (teamAgeStart >= ageStart && teamAgeEnd <= ageEnd);
                } catch (Exception ex) {
                    Log.e(TAG, "팀 ageRange 파싱 실패: " + ageRangeStr);
                    matchAge = false;
                }
            }

            if (matchKeyword && matchCity && matchDistrict && matchSkill && matchAge) {
                filteredList.add(team);
            }
        }

        // 어댑터 갱신 (어댑터에 setItems가 없으면 재생성)
        if (teamAdapter instanceof UpdatableTeamAdapter) {
            ((UpdatableTeamAdapter) teamAdapter).setItems(filteredList);
        } else {
            teamAdapter = new TeamAdapter(getContext(), filteredList);
            teamAdapter.setOnItemClickListener(team -> {
                Intent intent = new Intent(getActivity(), TeamDetail.class);
                intent.putExtra("teamId", team.getTeamId());
                startActivity(intent);
            });
            recyclerViewTeams.setAdapter(teamAdapter);
        }
    }

    private void setupCreateTeamButton() {
        btnCreateTeam.setOnClickListener(v -> {
            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                Toast.makeText(getContext(), "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
                return;
            }
            String currentUserUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            FirebaseFirestore.getInstance().collection("profiles").document(currentUserUid)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        String teamId = documentSnapshot.getString("myTeam");
                        if (teamId != null && !teamId.isEmpty()) {
                            CustomToast.warning(getContext(), "이미 다른 팀에 소속되어 있습니다.");

                        } else {
                            startActivity(new Intent(getActivity(), CreateTeam.class));
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(getContext(), "사용자 정보 확인 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        });
    }

    private String safeLower(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim().toLowerCase();
    }

    private String safeText(TextView tv) {
        return tv == null ? "" : String.valueOf(tv.getText()).trim();
    }

    private int parseIntOrDefault(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

    private void initDistrictMap() {
        districtMap = new HashMap<>();
        districtMap.put(ALL, Arrays.asList(ALL));
        districtMap.put("서울", Arrays.asList(ALL,"강남구", "강동구", "강북구", "강서구", "관악구", "광진구", "구로구", "금천구", "노원구", "도봉구", "동대문구", "동작구", "마포구", "서대문구", "서초구", "성동구", "성북구", "송파구", "양천구", "영등포구", "용산구", "은평구", "종로구", "중구", "중랑구"));
        districtMap.put("부산", Arrays.asList(ALL,"강서구", "금정구", "기장군", "남구", "동구", "동래구", "부산진구", "북구", "사상구", "사하구", "서구", "수영구", "연제구", "영도구", "중구", "해운대구"));
        districtMap.put("대구", Arrays.asList(ALL,"남구", "달서구", "달성군", "동구", "북구", "서구", "수성구", "중구"));
        districtMap.put("인천", Arrays.asList(ALL,"강화군", "계양구", "남동구", "동구", "미추홀구", "부평구", "서구", "연수구", "옹진군", "중구"));
        districtMap.put("광주", Arrays.asList(ALL,"광산구", "남구", "동구", "북구", "서구"));
        districtMap.put("대전", Arrays.asList(ALL,"대덕구", "동구", "서구", "유성구", "중구"));
        districtMap.put("울산", Arrays.asList(ALL,"남구", "동구", "북구", "중구", "울주군"));
        districtMap.put("세종", Arrays.asList(ALL,"세종시"));
        districtMap.put("경기도", Arrays.asList(ALL,"가평군", "고양시", "과천시", "광명시", "광주시", "구리시", "군포시", "김포시", "남양주시", "동두천시", "부천시", "성남시", "수원시", "시흥시", "안산시", "안성시", "안양시", "양주시", "양평군", "여주시", "연천군", "오산시", "용인시", "의왕시", "의정부시", "이천시", "파주시", "평택시", "포천시", "하남시", "화성시"));
        districtMap.put("강원도", Arrays.asList(ALL,"강릉시", "고성군", "동해시", "삼척시", "속초시", "양구군", "양양군", "영월군", "원주시", "인제군", "정선군", "철원군", "춘천시", "태백시", "평창군", "홍천군", "화천군", "횡성군"));
        districtMap.put("충북", Arrays.asList(ALL,"괴산군", "단양군", "보은군", "영동군", "옥천군", "음성군", "제천시", "증평군", "진천군", "청주시", "충주시"));
        districtMap.put("충남", Arrays.asList(ALL,"계룡시", "공주시", "금산군", "논산시", "당진시", "보령시", "부여군", "서산시", "서천군", "아산시", "예산군", "천안시", "청양군", "태안군", "홍성군"));
        districtMap.put("전북", Arrays.asList(ALL,"고창군", "군산시", "김제시", "남원시", "무주군", "부안군", "순창군", "완주군", "익산시", "임실군", "장수군", "전주시", "정읍시", "진안군"));
        districtMap.put("전남", Arrays.asList(ALL,"강진군", "고흥군", "곡성군", "광양시", "구례군", "나주시", "담양군", "목포시", "무안군", "보성군", "순천시", "신안군", "여수시", "영광군", "영암군", "완도군", "장성군", "장흥군", "진도군", "함평군", "해남군", "화순군"));
        districtMap.put("경북", Arrays.asList(ALL,"경산시", "경주시", "고령군", "구미시", "군위군", "김천시", "문경시", "봉화군", "상주시", "성주군", "안동시", "영덕군", "영양군", "영주시", "영천시", "예천군", "울릉군", "울진군", "의성군", "청도군", "청송군", "칠곡군", "포항시"));
        districtMap.put("경남", Arrays.asList(ALL,"거제시", "거창군", "고성군", "김해시", "남해군", "밀양시", "사천시", "산청군", "양산시", "의령군", "진주시", "창녕군", "창원시", "통영시", "하동군", "함안군", "함양군", "합천군"));
        districtMap.put("제주도", Arrays.asList(ALL,"서귀포시", "제주시"));
    }

    /** 어댑터가 내부 리스트 갱신 API를 가진 경우 사용하고 싶다면 선택적으로 구현 */
    public interface UpdatableTeamAdapter {
        void setItems(List<Team> items);
    }
}
