// src/main/java/com/example/myapp/RecruitFilters.java
package com.example.myapp;

import java.io.Serializable;

public class RecruitFilters implements Serializable {

    public Common common = new Common();

    // 모집 유형: "전체" / "regular" / "mercenary"
    public String recruitType = "전체";

    // 포지션: "전체" / "GK" / "DF" / "MF" / "FW"
    public String position = "전체";

    // 실력 범위
    public Integer skillMin = null;   // null이면 제한 없음
    public Integer skillMax = null;

    // 날짜 범위 (yyyy-MM-dd 형식으로 들어온다고 가정)
    public String dateFrom = null;
    public String dateTo   = null;

    // 시간 범위 (HH:mm 형식으로 들어온다고 가정)
    public String timeFrom = null;
    public String timeTo   = null;

    // 요일: "전체" / "월" / "화" ... "일"
    public String weekday  = "전체";

    public static class Common implements Serializable {
        public String city = "전체";
        public String district = "전체";
    }
}
