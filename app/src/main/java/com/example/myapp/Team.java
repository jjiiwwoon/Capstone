package com.example.myapp;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

import java.util.ArrayList;
import java.util.List;

public class Team {

    // === 필수/주요 필드 (DB 스키마와 일치) ===
    private String teamName;          // teams.teamName
    private String region;            // teams.region
    private String ageRange;          // teams.ageRange
    private String intro;             // teams.intro
    private String logoUrl;           // teams.logoUrl
    private String stadium;           // teams.stadium

    private String captainUID;        // teams.captainUID
    private String viceCaptainUID;    // teams.viceCaptainUID
    private List<String> members;     // teams.members (string 배열)

    private String activityDay;       // teams.activityDay
    private String timeStart;         // teams.timeStart
    private String timeEnd;           // teams.timeEnd

    // === 평균 스킬 — 증분 계산용 필드 ===
    private Long skillSum;            // 누적 합 (증분)
    private Long memberCount;         // 인원수  (증분)
    private Integer skillAverage;     // 화면/쿼리용 즉시 접근값

    // === 로고/문서 버저닝(캐시 무효화 & 경고 제거) ===
    private Timestamp updateAt;       // 문서 갱신 시각 (CreateTeam에서 넣는 필드)
    private String   logoStatus;      // "none" | "uploading" | "ready" | "failed" (선택)
    private Timestamp logoUpdatedAt;  // 로고 버전 타임스탬프 (Glide 캐시 무효화에 사용)

    // === 문서 ID (직렬화 제외) ===
    @Exclude
    private String teamId;

    // Firebase용 빈 생성자
    public Team() {}

    // 편의 생성자
    public Team(String teamName, String region, String ageRange, String intro,
                String logoUrl, String stadium, String captainUID, List<String> members,
                String viceCaptainUID, String activityDay, String timeStart, String timeEnd,
                Long skillSum, Long memberCount, Integer skillAverage,
                Timestamp updateAt, String logoStatus, Timestamp logoUpdatedAt) {

        this.teamName = teamName;
        this.region = region;
        this.ageRange = ageRange;
        this.intro = intro;
        this.logoUrl = logoUrl;
        this.stadium = stadium;

        this.captainUID = captainUID;
        this.members = (members != null) ? members : new ArrayList<>();
        this.viceCaptainUID = (viceCaptainUID != null) ? viceCaptainUID : "";

        this.activityDay = activityDay;
        this.timeStart = timeStart;
        this.timeEnd = timeEnd;

        this.skillSum = (skillSum != null) ? skillSum : 0L;
        this.memberCount = (memberCount != null) ? memberCount : 0L;
        this.skillAverage = (skillAverage != null) ? skillAverage : 0;

        this.updateAt = updateAt;
        this.logoStatus = logoStatus;
        this.logoUpdatedAt = logoUpdatedAt;
    }

    // ===== Getter/Setter =====
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getAgeRange() { return ageRange; }
    public void setAgeRange(String ageRange) { this.ageRange = ageRange; }

    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public String getStadium() { return stadium; }
    public void setStadium(String stadium) { this.stadium = stadium; }

    public String getCaptainUID() { return captainUID; }
    public void setCaptainUID(String captainUID) { this.captainUID = captainUID; }

    public String getViceCaptainUID() { return viceCaptainUID; }
    public void setViceCaptainUID(String viceCaptainUID) { this.viceCaptainUID = viceCaptainUID; }

    public List<String> getMembers() { return members; }
    public void setMembers(List<String> members) { this.members = members; }

    public String getActivityDay() { return activityDay; }
    public void setActivityDay(String activityDay) { this.activityDay = activityDay; }

    public String getTimeStart() { return timeStart; }
    public void setTimeStart(String timeStart) { this.timeStart = timeStart; }

    public String getTimeEnd() { return timeEnd; }
    public void setTimeEnd(String timeEnd) { this.timeEnd = timeEnd; }

    public Long getSkillSum() { return skillSum; }
    public void setSkillSum(Long skillSum) { this.skillSum = skillSum; }

    public Long getMemberCount() { return memberCount; }
    public void setMemberCount(Long memberCount) { this.memberCount = memberCount; }

    public Integer getSkillAverage() { return skillAverage; }
    public void setSkillAverage(Integer skillAverage) { this.skillAverage = skillAverage; }

    public Timestamp getUpdateAt() { return updateAt; }
    public void setUpdateAt(Timestamp updateAt) { this.updateAt = updateAt; }

    public String getLogoStatus() { return logoStatus; }
    public void setLogoStatus(String logoStatus) { this.logoStatus = logoStatus; }

    public Timestamp getLogoUpdatedAt() { return logoUpdatedAt; }
    public void setLogoUpdatedAt(Timestamp logoUpdatedAt) { this.logoUpdatedAt = logoUpdatedAt; }

    @Exclude
    public String getTeamId() { return teamId; }
    @Exclude
    public void setTeamId(String teamId) { this.teamId = teamId; }
}
