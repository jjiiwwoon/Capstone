package com.example.myapp;

/**
 * 일정 아이템 데이터 모델
 * schedules/{teamId}/events/{eventId} 문서 구조 기반
 */
public class ScheduleItem {

    // 📌 기본 필드
    public String eventId;       // schedules/{teamId}/events 문서 ID
    public String teamId;        // 내 팀 ID
    public String date;          // "YYYY-MM-DD"
    public String status;        // "scheduled" | "finished"
    public String matchId;       // 기록 저장 후 eventId와 동일하게 세팅

    // 📌 일정 정보
    public String title;         // 경기 제목
    public String time;          // 경기 시간
    public String opponentName;  // 상대 팀 이름
    public String stadiumName;   // 경기장 이름
    public String address;       // 경기장 주소

    // 📌 추가 필드 (다가오는 일정 카드에서 필요)
    public String opponentLogoUrl; // 상대 팀 로고 URL

    public ScheduleItem(String eventId, String teamId, String date, String status, String matchId,
                        String title, String time, String opponentName, String stadiumName, String address) {
        this.eventId = safe(eventId);
        this.teamId = safe(teamId);
        this.date = safe(date);
        this.status = safe(status);
        this.matchId = safe(matchId);
        this.title = safe(title);
        this.time = safe(time);
        this.opponentName = safe(opponentName);
        this.stadiumName = safe(stadiumName);
        this.address = safe(address);
        this.opponentLogoUrl = ""; // 기본값 (필드 누락 방지)
    }

    /** null 방지용 헬퍼 */
    private String safe(String s) {
        return (s == null) ? "" : s;
    }
}
