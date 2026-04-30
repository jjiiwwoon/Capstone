package com.example.myapp;

public class ApplicantInfo {
    private String teamName;
    private String nickname;
    private int skill;
    private String teamId;   // 팀 문서 ID
    private String userId;   // ✅ 신청자의 UID (채팅 메시지 전송용)

    // Firestore 역직렬화를 위한 기본 생성자
    public ApplicantInfo() {}

    // 전체 필드를 포함한 생성자
    public ApplicantInfo(String teamName, String nickname, int skill, String teamId, String userId) {
        this.teamName = teamName;
        this.nickname = nickname;
        this.skill = skill;
        this.teamId = teamId;
        this.userId = userId;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public int getSkill() {
        return skill;
    }

    public void setSkill(int skill) {
        this.skill = skill;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
