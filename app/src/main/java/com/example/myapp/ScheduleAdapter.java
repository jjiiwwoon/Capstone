// src/main/java/com/example/myapp/ScheduleAdapter.java
package com.example.myapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.List;

public class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.ScheduleViewHolder> {

    public interface OnScheduleActionListener {
        void onWriteRecord(ScheduleItem item);
    }

    private final List<ScheduleItem> scheduleList;
    private final OnScheduleActionListener listener;

    public ScheduleAdapter(List<ScheduleItem> scheduleList) {
        this(scheduleList, null);
    }

    public ScheduleAdapter(List<ScheduleItem> scheduleList, OnScheduleActionListener listener) {
        this.scheduleList = scheduleList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ScheduleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.schedule_item, parent, false);
        return new ScheduleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ScheduleViewHolder holder, int position) {
        ScheduleItem item = scheduleList.get(position);

        holder.tvTitle.setText(safe(item.title));
        holder.tvTime.setText("시간: " + safe(item.time));
        holder.tvOpponent.setText("상대 팀: " + safe(item.opponentName));
        holder.tvStadium.setText("장소: " + safe(item.stadiumName));   // ✅ 주내용(장소)
        holder.tvAddress.setText(safe(item.address));                  // ✅ 보조(주소, 작은 글씨)

        // ✅ 테스트 모드: 버튼은 항상 보이도록 고정 (재활용 이슈 방지)
        holder.btnWriteRecord.setVisibility(View.VISIBLE);

        holder.btnWriteRecord.setOnClickListener(v -> {
            if (listener != null) {
                // ✅ 테스트: 시간/상태 무시하고 항상 콜백 호출
                listener.onWriteRecord(item);

                /* ─────────────────────────────────────────────────────────────
                   🔒 운영용(정상 동작) 예시 코드 — 나중에 테스트 끝나면 이 로직으로 교체
                   long now = System.currentTimeMillis();
                   boolean timePassed = (item.matchTs != null) && (now >= item.matchTs);
                   boolean finished   = "finished".equals(item.status);

                   if (timePassed || finished) {
                       listener.onWriteRecord(item);
                   } else {
                       // CustomToast 사용 시 (프로젝트 공통 규칙)
                       // CustomToast.show(v.getContext(), "시합이 아직 끝나지 않았습니다.", CustomToast.Type.INFO);

                       // 또는 기본 Toast
                       // Toast.makeText(v.getContext(), "시합이 아직 끝나지 않았습니다.", Toast.LENGTH_SHORT).show();
                   }
                   ───────────────────────────────────────────────────────────── */
                return;
            }

            // ✅ 테스트: 시간/상태 무시하고 항상 기록 다이얼로그 열기
            AppCompatActivity act = (AppCompatActivity) v.getContext();
            WriteRecord dialog = WriteRecord.newInstance(item.teamId, item.eventId);
            dialog.show(act.getSupportFragmentManager(), "WriteRecord");

            /* ─────────────────────────────────────────────────────────────
               🔒 운영용(정상 동작) 예시 코드 — 나중에 테스트 끝나면 이 로직으로 교체
               long now = System.currentTimeMillis();
               boolean timePassed = (item.matchTs != null) && (now >= item.matchTs);
               boolean finished   = "finished".equals(item.status);

               if (timePassed || finished) {
                   WriteRecord dialog = WriteRecord.newInstance(item.teamId, item.eventId);
                   dialog.show(act.getSupportFragmentManager(), "WriteRecord");
               } else {
                   // CustomToast 사용 시 (프로젝트 공통 규칙)
                   // CustomToast.show(act, "시합이 아직 끝나지 않았습니다.", CustomToast.Type.INFO);

                   // 또는 기본 Toast
                   // Toast.makeText(act, "시합이 아직 끝나지 않았습니다.", Toast.LENGTH_SHORT).show();
               }
               ───────────────────────────────────────────────────────────── */
        });
    }

    @Override
    public int getItemCount() {
        return scheduleList.size();
    }

    static class ScheduleViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvTime, tvOpponent, tvStadium, tvAddress;
        MaterialButton btnWriteRecord;

        public ScheduleViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle        = itemView.findViewById(R.id.tvTitle);
            tvTime         = itemView.findViewById(R.id.tvTime);
            tvOpponent     = itemView.findViewById(R.id.tvOpponent);
            tvStadium      = itemView.findViewById(R.id.tvStadium);
            tvAddress      = itemView.findViewById(R.id.tvAddress);
            btnWriteRecord = itemView.findViewById(R.id.btnWriteRecord);
        }
    }

    // ✅ NPE/문자열 "null" 방지용
    private static String safe(String s) {
        return s == null ? "" : s;
    }
}