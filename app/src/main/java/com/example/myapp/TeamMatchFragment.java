// src/main/java/com/example/myapp/TeamMatchFragment.java
package com.example.myapp;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TeamMatchFragment extends Fragment
        implements TeamMatchFindDialog.OnTeamMatchFilterSelected {

    private RecyclerView recyclerView;
    private TextView textInfo;

    // 🔹 이 프래그먼트 전용 어댑터
    private TeamMatchAdapter adapter;
    private final List<Team> teamList = new ArrayList<>();

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    private String myTeamId;
    private TeamMatchCondition myCondition = new TeamMatchCondition();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.team_match, container, false);

        recyclerView = v.findViewById(R.id.recyclerTeams);
        textInfo = v.findViewById(R.id.textInfo);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        // ✅ TeamMatchFragment 전용 어댑터 사용
        adapter = new TeamMatchAdapter(
                requireContext(),
                teamList,
                this::onProposeMatchClicked   // 시합 제안 버튼 클릭 콜백
        );
        recyclerView.setAdapter(adapter);

        // 처음에는 안내 텍스트 숨김
        textInfo.setVisibility(View.GONE);

        loadMyTeamAndPref();
        return v;
    }

    // 부모(RecruitMatch)에서 호출해서 다이얼로그 띄우는 용도
    public void openFilterDialog() {
        TeamMatchFindDialog dialog = TeamMatchFindDialog.newInstance(myCondition);
        dialog.show(getChildFragmentManager(), "TeamMatchFindDialog");
    }

    private void loadMyTeamAndPref() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            textInfo.setVisibility(View.VISIBLE);
            textInfo.setText("로그인이 필요합니다.");
            return;
        }

        db.collection("profiles").document(user.getUid())
                .get()
                .addOnSuccessListener(p -> {
                    String teamId = p.getString("myTeam");
                    if (TextUtils.isEmpty(teamId)) {
                        textInfo.setVisibility(View.VISIBLE);
                        textInfo.setText("팀이 없습니다.\n팀을 먼저 생성하거나 가입해 주세요.");
                        return;
                    }
                    myTeamId = teamId;

                    db.collection("teams").document(myTeamId)
                            .get()
                            .addOnSuccessListener(doc -> {
                                myCondition = readConditionFromDoc(doc);
                                runTeamQuery();
                            });
                });
    }

    private TeamMatchCondition readConditionFromDoc(DocumentSnapshot d) {
        TeamMatchCondition c = new TeamMatchCondition();

        c.regionCity = getOrNull(d, "matchPref_regionCity");
        c.regionDistrict = getOrNull(d, "matchPref_regionDistrict");

        Long s1 = d.getLong("matchPref_skillMin");
        Long s2 = d.getLong("matchPref_skillMax");
        if (s1 != null) c.skillMin = s1.intValue();
        if (s2 != null) c.skillMax = s2.intValue();

        c.weekday = getOrNull(d, "matchPref_weekday");
        c.dateFrom = getOrNull(d, "matchPref_dateFrom");
        c.dateTo = getOrNull(d, "matchPref_dateTo");
        c.timeFrom = getOrNull(d, "matchPref_timeFrom");
        c.timeTo = getOrNull(d, "matchPref_timeTo");

        return c;
    }

    private String getOrNull(DocumentSnapshot d, String key) {
        String v = d.getString(key);
        if (v == null) return null;
        v = v.trim();
        return v.isEmpty() ? null : v;
    }

    private void runTeamQuery() {
        textInfo.setVisibility(View.VISIBLE);
        textInfo.setText("조건에 맞는 팀을 찾는 중...");
        teamList.clear();
        adapter.notifyDataSetChanged();

        Query q = db.collection("teams");

        if (!empty(myCondition.regionCity))
            q = q.whereEqualTo("matchPref_regionCity", myCondition.regionCity);

        if (!empty(myCondition.regionDistrict))
            q = q.whereEqualTo("matchPref_regionDistrict", myCondition.regionDistrict);

        if (myCondition.skillMin != null)
            q = q.whereEqualTo("matchPref_skillMin", myCondition.skillMin);

        if (myCondition.skillMax != null)
            q = q.whereEqualTo("matchPref_skillMax", myCondition.skillMax);

        if (!empty(myCondition.weekday) && !"전체".equals(myCondition.weekday))
            q = q.whereEqualTo("matchPref_weekday", myCondition.weekday);

        if (!empty(myCondition.dateFrom))
            q = q.whereEqualTo("matchPref_dateFrom", myCondition.dateFrom);

        if (!empty(myCondition.dateTo))
            q = q.whereEqualTo("matchPref_dateTo", myCondition.dateTo);

        if (!empty(myCondition.timeFrom))
            q = q.whereEqualTo("matchPref_timeFrom", myCondition.timeFrom);

        if (!empty(myCondition.timeTo))
            q = q.whereEqualTo("matchPref_timeTo", myCondition.timeTo);

        q.get().addOnSuccessListener(qs -> {
            teamList.clear();
            for (DocumentSnapshot d : qs) {
                if (d.getId().equals(myTeamId)) continue;   // 내 팀 제외
                Team t = d.toObject(Team.class);
                if (t != null) {
                    // 🔹 Team 모델 안에 id 필드가 따로 없다고 가정하고, 여기서는 그냥 목록만 사용
                    teamList.add(t);
                }
            }
            adapter.notifyDataSetChanged();

            if (teamList.isEmpty()) {
                textInfo.setVisibility(View.VISIBLE);
                textInfo.setText("조건에 맞는 팀이 없습니다.");
            } else {
                textInfo.setVisibility(View.GONE);
            }
        }).addOnFailureListener(e -> {
            textInfo.setVisibility(View.VISIBLE);
            textInfo.setText("팀 목록을 불러오는 중 오류가 발생했습니다.");
        });
    }

    private boolean empty(String s) {
        return s == null || s.trim().isEmpty();
    }

    @Override
    public void onTeamMatchFilterSelected(@Nullable TeamMatchCondition condition) {
        if (condition != null) myCondition = condition;
        saveConditionToTeam();
        runTeamQuery();
    }

    private void saveConditionToTeam() {
        if (TextUtils.isEmpty(myTeamId)) return;

        Map<String, Object> m = new HashMap<>();
        m.put("matchPref_regionCity", norm(myCondition.regionCity));
        m.put("matchPref_regionDistrict", norm(myCondition.regionDistrict));
        m.put("matchPref_skillMin", myCondition.skillMin);
        m.put("matchPref_skillMax", myCondition.skillMax);

        m.put("matchPref_weekday",
                (empty(myCondition.weekday) || "전체".equals(myCondition.weekday))
                        ? null : myCondition.weekday);

        m.put("matchPref_dateFrom", norm(myCondition.dateFrom));
        m.put("matchPref_dateTo", norm(myCondition.dateTo));
        m.put("matchPref_timeFrom", norm(myCondition.timeFrom));
        m.put("matchPref_timeTo", norm(myCondition.timeTo));

        db.collection("teams")
                .document(myTeamId)
                .update(m)
                .addOnFailureListener(e ->
                        CustomToast.error(requireContext(), "조건 저장에 실패했습니다."));
    }

    private String norm(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    // 🔹 시합 제안 버튼 클릭 시 호출되는 콜백
    private void onProposeMatchClicked(@NonNull Team team) {
        // TODO: 여기서 CreateMatch로 이동하거나, 매치 제안 다이얼로그 띄우면 됨.
        String name;
        try {
            name = team.getTeamName();
        } catch (Exception e) {
            name = "상대팀";
        }
        if (TextUtils.isEmpty(name)) name = "상대팀";

        CustomToast.info(requireContext(), name + " 팀에 시합 제안을 보냅니다. (추후 연결 예정)");
    }

    // ====================== ▼ 이 프래그먼트 전용 어댑터 ======================

    public static class TeamMatchAdapter extends RecyclerView.Adapter<TeamMatchAdapter.VH> {

        public interface OnTeamActionListener {
            void onProposeMatch(Team team);
        }

        private final Context context;
        private final List<Team> items;
        private final OnTeamActionListener listener;

        public TeamMatchAdapter(Context context,
                                List<Team> items,
                                OnTeamActionListener listener) {
            this.context = context;
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.team_item, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Team item = items.get(position);
            holder.bind(context, item, listener);
        }

        @Override
        public int getItemCount() {
            return items == null ? 0 : items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            ImageView teamLogo;
            TextView teamName;
            TextView teamRegion;
            TextView teamSkillAge;
            Button btnProposeMatch;

            VH(@NonNull View itemView) {
                super(itemView);
                teamLogo = itemView.findViewById(R.id.teamLogo);
                teamName = itemView.findViewById(R.id.teamName);
                teamRegion = itemView.findViewById(R.id.teamRegion);
                teamSkillAge = itemView.findViewById(R.id.teamSkillAge);
                btnProposeMatch = itemView.findViewById(R.id.btnProposeMatch);
            }

            void bind(Context context,
                      Team item,
                      OnTeamActionListener listener) {

                // 🔸 팀 이름
                String name = null;
                try {
                    name = item.getTeamName();
                } catch (Exception ignore) {}
                if (TextUtils.isEmpty(name)) name = "이름 없는 팀";
                teamName.setText(name);

                // 🔸 지역
                String region = null;
                try {
                    region = item.getRegion();
                } catch (Exception ignore) {}
                if (TextUtils.isEmpty(region)) region = "지역 미정";
                teamRegion.setText(region);

                // 🔸 실력
                Integer avg = null;
                try {
                    avg = item.getSkillAverage();
                } catch (Exception ignore) {}

                Long sumLong = null;
                Long countLong = null;

                try {
                    sumLong = item.getSkillSum();       // Long
                    countLong = item.getMemberCount(); // Long
                } catch (Exception ignore) {}

                Integer sum = (sumLong == null ? null : sumLong.intValue());
                Integer memberCount = (countLong == null ? null : countLong.intValue());


                String skillStr;
                if (avg != null && avg > 0) {
                    skillStr = String.valueOf(avg);
                } else if (sum != null && memberCount != null && memberCount > 0) {
                    skillStr = String.valueOf(sum / memberCount);
                } else {
                    skillStr = "-";
                }

                // 🔸 나이대
                String ageRange = null;
                try {
                    ageRange = item.getAgeRange();
                } catch (Exception ignore) {}
                if (TextUtils.isEmpty(ageRange)) ageRange = "미정";

                teamSkillAge.setText("실력: " + skillStr + "   나이: " + ageRange);

                // 🔸 로고
                String logoUrl = null;
                try {
                    logoUrl = item.getLogoUrl();
                } catch (Exception ignore) {}
                if (!TextUtils.isEmpty(logoUrl)) {
                    Glide.with(context)
                            .load(logoUrl)
                            .placeholder(R.drawable.bg_round_image)
                            .error(R.drawable.bg_round_image)
                            .into(teamLogo);
                } else {
                    teamLogo.setImageResource(R.drawable.bg_round_image);
                }

                // ✅ 이 화면에서는 항상 '시합 제안' 버튼을 보이게
                btnProposeMatch.setVisibility(View.VISIBLE);
                btnProposeMatch.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onProposeMatch(item);
                    }
                });
            }
        }
    }
}
