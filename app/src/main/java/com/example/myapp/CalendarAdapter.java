// app/src/main/java/com/example/myapp/CalendarAdapter.java
package com.example.myapp;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.DayViewHolder> {

    private List<CustomCalendarView.CalendarDate> days = new ArrayList<>();

    // 현재 표시 중인 연/월(0-based)과 선택된 일
    private int year = -1, month = -1, selectedDay = -1;

    // 오늘 정보 캐시
    private final Calendar today = Calendar.getInstance();
    private final int todayYear  = today.get(Calendar.YEAR);
    private final int todayMonth = today.get(Calendar.MONTH);
    private final int todayDay   = today.get(Calendar.DAY_OF_MONTH);

    // 클릭 리스너
    public interface OnItemClickListener {
        void onItemClick(int position, CustomCalendarView.CalendarDate date);
    }
    private OnItemClickListener listener;
    public void setOnItemClickListener(OnItemClickListener listener) { this.listener = listener; }

    public void setDays(List<CustomCalendarView.CalendarDate> newDays) {
        this.days = newDays;
        notifyDataSetChanged();
    }

    /** CustomCalendarView.updateCalendar()에서 현재 연/월 전달 */
    public void setYearMonth(int year, int month) {
        this.year = year;
        this.month = month; // 0-based
        notifyDataSetChanged();
    }

    /** 날짜 선택 갱신(같은 연/월일 때만 유지) */
    public void setSelectedDay(int year, int month, int day) {
        if (this.year == year && this.month == month) {
            this.selectedDay = day;   // 1~31
        } else {
            this.selectedDay = -1;    // 다른 달이면 해제
        }
        notifyDataSetChanged();
    }

    @Override public int getItemCount() { return days.size(); }

    @Override
    public DayViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.calendar_day_item, parent, false);

        int totalWidth = parent.getMeasuredWidth();
        if (totalWidth == 0) totalWidth = parent.getResources().getDisplayMetrics().widthPixels;

        int margin = (int) (parent.getResources().getDisplayMetrics().density * 2);  // 2dp 마진
        int itemWidth = (totalWidth - margin * 8) / 7;
        int itemHeight = itemWidth + (int)(parent.getResources().getDisplayMetrics().density * 20);

        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(itemWidth, itemHeight);
        view.setLayoutParams(params);

        return new DayViewHolder(view);
    }

    @Override
    public void onBindViewHolder(DayViewHolder holder, int position) {
        CustomCalendarView.CalendarDate item = days.get(position);

        // 날짜 숫자
        holder.tvDay.setText(String.valueOf(item.day));
        holder.tvDay.setAlpha(item.inThisMonth ? 1f : 0.35f);
        holder.tvDay.setTextColor(Color.BLACK);

        // 기본 배경
        holder.dayContainer.setBackgroundResource(R.drawable.bg_rect_default);

        // 오늘 표시
        boolean isToday = item.inThisMonth
                && year == todayYear && month == todayMonth && item.day == todayDay;
        if (isToday) {
            holder.dayContainer.setBackgroundResource(R.drawable.bg_rect_today);
        }

        // 선택 표시(선택 > 오늘 우선)
        boolean isSelected = item.inThisMonth && (item.day == selectedDay);
        if (isSelected) {
            holder.dayContainer.setBackgroundResource(R.drawable.bg_rect_selected);
        }

        // 매치 표시 (당월 + hasMatch)
        boolean showMatch = item.inThisMonth && item.hasMatch;
        holder.matchIndicator.setVisibility(showMatch ? View.VISIBLE : View.GONE);

        if (showMatch) {
            final int COLOR_PAST   = 0xFF9E9E9E; // 회색
            final int COLOR_FUTURE = 0xFFFF5722; // 빨강(주황빨강)
            holder.textMatch.setTextColor(item.isPastMatch ? COLOR_PAST : COLOR_FUTURE);

            // 아이콘은 원래 모습 그대로(네모 방지)
            holder.iconMatch.setImageTintList(null);
            holder.iconMatch.setImageResource(R.drawable.ic_soccer_ball);
        }

        // 클릭 이벤트
        holder.itemView.setOnClickListener(v -> {
            if (listener != null && item.inThisMonth) {
                listener.onItemClick(position, item);
            }
        });
    }

    static class DayViewHolder extends RecyclerView.ViewHolder {
        TextView tvDay;
        View dayContainer;
        View matchIndicator;
        TextView textMatch;   // "Match" 텍스트
        ImageView iconMatch;  // 축구공 아이콘

        DayViewHolder(View itemView) {
            super(itemView);
            tvDay          = itemView.findViewById(R.id.tvDay);
            dayContainer   = itemView.findViewById(R.id.dayContainer);
            matchIndicator = itemView.findViewById(R.id.matchIndicator);
            textMatch      = itemView.findViewById(R.id.textMatch);
            iconMatch      = itemView.findViewById(R.id.iconMatch);
        }
    }
}
