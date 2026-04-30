// src/main/java/com/example/myapp/MatchPost.java
package com.example.myapp;

import java.util.List;

public class MatchPost {
    private String matchId;
    private String uid;
    private String teamId;
    private String teamName;

    // 리크루팅 문서 기본 필드
    private String logoUrl;          // 모집글에서 쓰는 로고
    private String date;             // "yyyy-MM-dd"
    private String time;             // "HH:mm ~ HH:mm" 또는 "HH:mm"
    private String stadium;          // 경기장 이름
    private String address;          // 경기장 주소
    private int skill;               // 실력
    private long timestamp;          // 게시 시각(없을 수 있음)

    // ✅ 추가: 현재 스키마 호환 필드
    private String description;      // 모집 글 본문
    private long matchTs;            // 정렬/필터용 에포크(ms)
    private String status;           // 예: "OPEN"

    // ✅ 종료된 경기 문서 폴백용 필드(어댑터에서 firstNonEmpty로 사용)
    private String teamLogoUrl;      // 결과 문서의 로고
    private String stadiumName;      // 결과 문서의 경기장 이름
    private String stadiumAddress;   // 결과 문서의 경기장 주소

    // ✅ 신청자 리스트(작성자가 올린 글에서만 사용)
    private List<ApplicantInfo> applicants;

    public MatchPost() {
        // Firestore 직렬화용 기본 생성자
    }

    // ===== Getter & Setter =====
    public String getMatchId() { return matchId; }
    public void setMatchId(String matchId) { this.matchId = matchId; }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public String getStadium() { return stadium; }
    public void setStadium(String stadium) { this.stadium = stadium; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public int getSkill() { return skill; }
    public void setSkill(int skill) { this.skill = skill; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public long getMatchTs() { return matchTs; }
    public void setMatchTs(long matchTs) { this.matchTs = matchTs; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<ApplicantInfo> getApplicants() { return applicants; }
    public void setApplicants(List<ApplicantInfo> applicants) { this.applicants = applicants; }

    // ===== 폴백용 추가 필드 게터/세터 =====
    public String getTeamLogoUrl() { return teamLogoUrl; }
    public void setTeamLogoUrl(String teamLogoUrl) { this.teamLogoUrl = teamLogoUrl; }

    public String getStadiumName() { return stadiumName; }
    public void setStadiumName(String stadiumName) { this.stadiumName = stadiumName; }

    public String getStadiumAddress() { return stadiumAddress; }
    public void setStadiumAddress(String stadiumAddress) { this.stadiumAddress = stadiumAddress; }
}
