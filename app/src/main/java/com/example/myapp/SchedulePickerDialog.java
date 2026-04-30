// src/main/java/com/example/myapp/SchedulePickerDialog.java
package com.example.myapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.List;

/**
 * 일정 선택 바텀시트 다이얼로그
 * - arguments: teamId(일정 소유 팀), myTeamId/Name(우리팀 기준 표시용)
 * - 레이아웃: R.layout.dialog_schedule_picker
 * - 아이템: R.layout.item_schedule_row
 */
public class SchedulePickerDialog extends BottomSheetDialogFragment {

    @FunctionalInterface
    public interface OnScheduleSelected {
        void onSelected(DocumentSnapshot doc);
    }

    private static final String ARG_TEAM_ID      = "teamId";
    private static final String ARG_MY_TEAM_ID   = "myTeamId";
    private static final String ARG_MY_TEAM_NAME = "myTeamName";

    private OnScheduleSelected listener;

    public static SchedulePickerDialog newInstance(String teamId, String myTeamId, String myTeamName){
        SchedulePickerDialog f = new SchedulePickerDialog();
        Bundle b = new Bundle();
        b.putString(ARG_TEAM_ID, teamId);
        b.putString(ARG_MY_TEAM_ID, myTeamId);
        b.putString(ARG_MY_TEAM_NAME, myTeamName);
        f.setArguments(b);
        return f;
    }

    public void setOnScheduleSelected(OnScheduleSelected l){ this.listener = l; }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState){
        View v = inf.inflate(R.layout.dialog_schedule_picker, container, false);

        RecyclerView rv = v.findViewById(R.id.recycler);
        ProgressBar pb  = v.findViewById(R.id.progress);
        TextView empty  = v.findViewById(R.id.empty);

        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        pb.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);

        String teamId     = safe(getArguments()!=null ? getArguments().getString(ARG_TEAM_ID) : "");
        String myTeamId   = safe(getArguments()!=null ? getArguments().getString(ARG_MY_TEAM_ID) : "");
        String myTeamName = safe(getArguments()!=null ? getArguments().getString(ARG_MY_TEAM_NAME) : "");

        // 🔥 지금 시점(ms)
        long nowMs = System.currentTimeMillis();

        // ✅ 앞으로 있을(matchTs >= now) 일정만 가져오도록 필터 추가
        FirebaseFirestore.getInstance()
                .collection("schedules").document(teamId).collection("events")
                .whereGreaterThanOrEqualTo("matchTs", nowMs)   // ← 여기 추가
                .orderBy("matchTs", Query.Direction.ASCENDING)
                .limit(100)
                .get()
                .addOnSuccessListener(qs -> {
                    pb.setVisibility(View.GONE);
                    List<DocumentSnapshot> docs = qs.getDocuments();
                    if (docs.isEmpty()){
                        empty.setVisibility(View.VISIBLE);
                        empty.setText("불러올 일정이 없습니다.");
                        return;
                    }

                    rv.setAdapter(new EventPickAdapter(docs, myTeamId, myTeamName, new EventPickAdapter.OnPick() {
                        @Override
                        public void onPick(DocumentSnapshot d) {
                            if (listener != null) listener.onSelected(d);
                            dismiss();
                        }
                    }));
                })
                .addOnFailureListener(e -> {
                    pb.setVisibility(View.GONE);
                    empty.setVisibility(View.VISIBLE);
                    empty.setText("불러오기 실패: " + e.getMessage());
                });

        return v;
    }

    private static String safe(String s){ return s==null ? "" : s.trim(); }
}
