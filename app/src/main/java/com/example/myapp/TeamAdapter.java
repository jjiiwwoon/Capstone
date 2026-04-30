package com.example.myapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class TeamAdapter extends RecyclerView.Adapter<TeamAdapter.TeamViewHolder> {

    private Context context;
    private List<Team> teamList;

    // 클릭 리스너 인터페이스 정의
    public interface OnItemClickListener {
        void onItemClick(Team team);
    }

    private OnItemClickListener listener;

    // 클릭 리스너 등록 메서드
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public TeamAdapter(Context context, List<Team> teamList) {
        this.context = context;
        this.teamList = teamList;
    }

    @NonNull
    @Override
    public TeamViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.team_item, parent, false);
        return new TeamViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TeamViewHolder holder, int position) {
        Team team = teamList.get(position);
        holder.teamName.setText(team.getTeamName());
        holder.teamRegion.setText("활동 지역: " + team.getRegion());

        String ageRange = team.getAgeRange() != null ? team.getAgeRange() : "-";
        holder.teamSkillAge.setText("실력: " + team.getSkillAverage() + "  |  나이: " + ageRange);

        if (team.getLogoUrl() != null && !team.getLogoUrl().isEmpty()) {
            Glide.with(context)
                    .load(team.getLogoUrl())
                    .placeholder(R.drawable.default_profile_image)
                    .into(holder.teamLogo);
        } else {
            holder.teamLogo.setImageResource(R.drawable.default_profile_image);
        }

        // 클릭 리스너 동작
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(team);
            }
        });
    }

    @Override
    public int getItemCount() {
        return teamList.size();
    }

    public static class TeamViewHolder extends RecyclerView.ViewHolder {
        ImageView teamLogo;
        TextView teamName, teamRegion, teamSkillAge;

        public TeamViewHolder(@NonNull View itemView) {
            super(itemView);
            teamLogo = itemView.findViewById(R.id.teamLogo);
            teamName = itemView.findViewById(R.id.teamName);
            teamRegion = itemView.findViewById(R.id.teamRegion);
            teamSkillAge = itemView.findViewById(R.id.teamSkillAge);
        }
    }
}
