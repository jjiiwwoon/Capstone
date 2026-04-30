// src/main/java/com/example/myapp/MatchFilters.java
package com.example.myapp;

import java.io.Serializable;

public class MatchFilters implements Serializable {

    public Common common = new Common();

    // 실력 범위
    public Integer skillMin = null;
    public Integer skillMax = null;

    // 날짜 범위
    public String dateFrom = null;
    public String dateTo   = null;

    // 시간 범위
    public String timeFrom = null;
    public String timeTo   = null;

    // 요일
    public String weekday  = "전체";

    public static class Common implements Serializable {
        public String city = "전체";
        public String district = "전체";
    }
}
