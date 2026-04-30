// src/main/java/com/example/myapp/TeamMatchCondition.java
package com.example.myapp;

import java.io.Serializable;

/**
 * 팀이 원하는 시합 조건을 저장하는 데이터 클래스
 * - 지역 / 실력 / 날짜 / 시간 / 요일 모두 포함
 */
public class TeamMatchCondition implements Serializable {

    // 지역
    public String regionCity;       // 시/도
    public String regionDistrict;   // 구/군

    // 실력
    public Integer skillMin;
    public Integer skillMax;

    // 요일 ("전체", "월,화,수" 이런 식으로 저장)
    public String weekday;

    // 날짜 범위
    public String dateFrom;   // "YYYY-MM-DD"
    public String dateTo;     // "YYYY-MM-DD"

    // 시간 범위
    public String timeFrom;   // "HH:mm"
    public String timeTo;     // "HH:mm"
}
