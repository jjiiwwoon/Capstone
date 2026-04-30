package com.example.myapp;

import java.util.*;

public class FormationData {

    public static class Position {
        public String name;
        public float x, y;

        public Position(String name, float x, float y) {
            this.name = name;
            this.x = x;
            this.y = y;
        }
    }

    public static Map<String, List<Position>> getFormations() {
        Map<String, List<Position>> map = new HashMap<>();

        // 🔽 여기 아래에 포메이션들을 붙여넣으세요
        // 예시:
        // map.put("4-3-3", Arrays.asList(
        //     new Position("GK", 0.5f, 0.95f),
        //     new Position("LB", 0.15f, 0.8f),
        //     new Position("CB1", 0.35f, 0.8f),
        //     ...
        // ));
        map.put("4-1-2-1-2", Arrays.asList(
                new Position("GK", 0.5f, 0.93f),

                new Position("LB", 0.15f, 0.75f),
                new Position("LCB", 0.35f, 0.78f),
                new Position("RCB", 0.65f, 0.78f),
                new Position("RB", 0.85f, 0.75f),

                new Position("CDM", 0.5f, 0.63f),

                new Position("LCM", 0.35f, 0.49f),
                new Position("RCM", 0.65f, 0.49f),

                new Position("CAM", 0.5f, 0.35f),

                new Position("ST1", 0.35f, 0.12f),
                new Position("ST2", 0.65f, 0.12f)
        ));

        map.put("4-1-3-2", Arrays.asList(
                // 공격수
                new Position("ST1", 0.4f, 0.12f),
                new Position("ST2", 0.6f, 0.12f),

                // 미드필더
                new Position("LM", 0.2f, 0.3225f),
                new Position("CM", 0.5f, 0.3225f),
                new Position("RM", 0.8f, 0.3225f),

                // 수비형 미드필더
                new Position("CDM", 0.5f, 0.525f),

                // 수비수
                new Position("LB", 0.15f, 0.7275f),
                new Position("LCB", 0.35f, 0.7275f),
                new Position("RCB", 0.65f, 0.7275f),
                new Position("RB", 0.85f, 0.7275f),

                // 골키퍼
                new Position("GK", 0.5f, 0.93f)
        ));


        map.put("4-1-4-1", Arrays.asList(
                new Position("GK", 0.5f, 0.95f),
                new Position("LB", 0.15f, 0.8f),
                new Position("CB1", 0.35f, 0.8f),
                new Position("CB2", 0.65f, 0.8f),
                new Position("RB", 0.85f, 0.8f),
                new Position("CDM", 0.5f, 0.7f),
                new Position("LM", 0.15f, 0.55f),
                new Position("LCM", 0.35f, 0.55f),
                new Position("RCM", 0.65f, 0.55f),
                new Position("RM", 0.85f, 0.55f),
                new Position("ST", 0.5f, 0.3f)
        ));

        map.put("4-2-2-2", Arrays.asList(
                new Position("GK", 0.5f, 0.95f),
                new Position("LB", 0.15f, 0.8f),
                new Position("CB1", 0.35f, 0.8f),
                new Position("CB2", 0.65f, 0.8f),
                new Position("RB", 0.85f, 0.8f),
                new Position("CDM1", 0.35f, 0.65f),
                new Position("CDM2", 0.65f, 0.65f),
                new Position("LAM", 0.35f, 0.5f),
                new Position("RAM", 0.65f, 0.5f),
                new Position("ST1", 0.4f, 0.3f),
                new Position("ST2", 0.6f, 0.3f)
        ));

        map.put("4-2-3-1", Arrays.asList(
                new Position("GK", 0.5f, 0.95f),
                new Position("LB", 0.15f, 0.8f),
                new Position("CB1", 0.35f, 0.8f),
                new Position("CB2", 0.65f, 0.8f),
                new Position("RB", 0.85f, 0.8f),
                new Position("CDM1", 0.35f, 0.65f),
                new Position("CDM2", 0.65f, 0.65f),
                new Position("LAM", 0.25f, 0.5f),
                new Position("CAM", 0.5f, 0.5f),
                new Position("RAM", 0.75f, 0.5f),
                new Position("ST", 0.5f, 0.3f)
        ));

        map.put("4-2-4", Arrays.asList(
                new Position("GK", 0.5f, 0.95f),
                new Position("LB", 0.15f, 0.8f),
                new Position("CB1", 0.35f, 0.8f),
                new Position("CB2", 0.65f, 0.8f),
                new Position("RB", 0.85f, 0.8f),
                new Position("CM1", 0.35f, 0.65f),
                new Position("CM2", 0.65f, 0.65f),
                new Position("LW", 0.2f, 0.45f),
                new Position("RW", 0.8f, 0.45f),
                new Position("ST1", 0.4f, 0.3f),
                new Position("ST2", 0.6f, 0.3f)
        ));

        map.put("4-3-1-2", Arrays.asList(
                new Position("GK", 0.5f, 0.95f),
                new Position("LB", 0.15f, 0.8f),
                new Position("CB1", 0.35f, 0.8f),
                new Position("CB2", 0.65f, 0.8f),
                new Position("RB", 0.85f, 0.8f),
                new Position("LCM", 0.3f, 0.65f),
                new Position("CM", 0.5f, 0.65f),
                new Position("RCM", 0.7f, 0.65f),
                new Position("CAM", 0.5f, 0.5f),
                new Position("ST1", 0.4f, 0.3f),
                new Position("ST2", 0.6f, 0.3f)
        ));

        map.put("4-3-2-1", Arrays.asList(
                new Position("GK", 0.5f, 0.95f),
                new Position("LB", 0.15f, 0.8f),
                new Position("CB1", 0.35f, 0.8f),
                new Position("CB2", 0.65f, 0.8f),
                new Position("RB", 0.85f, 0.8f),
                new Position("LCM", 0.3f, 0.65f),
                new Position("CM", 0.5f, 0.65f),
                new Position("RCM", 0.7f, 0.65f),
                new Position("LAM", 0.35f, 0.5f),
                new Position("RAM", 0.65f, 0.5f),
                new Position("ST", 0.5f, 0.3f)
        ));

        map.put("4-3-3", Arrays.asList(
                new Position("GK", 0.5f, 0.93f),
                new Position("LB", 0.1f, 0.75f),
                new Position("CB1", 0.3f, 0.78f),
                new Position("CB2", 0.7f, 0.78f),
                new Position("RB", 0.9f, 0.75f),
                new Position("LCM", 0.25f, 0.55f),
                new Position("CM", 0.5f, 0.52f),
                new Position("RCM", 0.75f, 0.55f),
                new Position("LW", 0.15f, 0.28f),
                new Position("ST", 0.5f, 0.2f),
                new Position("RW", 0.85f, 0.28f)
        ));

        map.put("4-4-1-1", Arrays.asList(
                new Position("GK", 0.5f, 0.95f),
                new Position("LB", 0.15f, 0.8f),
                new Position("CB1", 0.35f, 0.8f),
                new Position("CB2", 0.65f, 0.8f),
                new Position("RB", 0.85f, 0.8f),
                new Position("LM", 0.2f, 0.65f),
                new Position("LCM", 0.4f, 0.65f),
                new Position("RCM", 0.6f, 0.65f),
                new Position("RM", 0.8f, 0.65f),
                new Position("CF", 0.5f, 0.45f),
                new Position("ST", 0.5f, 0.3f)
        ));

        map.put("4-4-2", Arrays.asList(
                new Position("GK", 0.5f, 0.95f),
                new Position("LB", 0.15f, 0.8f),
                new Position("CB1", 0.35f, 0.8f),
                new Position("CB2", 0.65f, 0.8f),
                new Position("RB", 0.85f, 0.8f),
                new Position("LM", 0.2f, 0.65f),
                new Position("LCM", 0.4f, 0.65f),
                new Position("RCM", 0.6f, 0.65f),
                new Position("RM", 0.8f, 0.65f),
                new Position("ST1", 0.4f, 0.3f),
                new Position("ST2", 0.6f, 0.3f)
        ));

        map.put("4-5-1", Arrays.asList(
                new Position("GK", 0.5f, 0.95f),
                new Position("LB", 0.15f, 0.8f),
                new Position("CB1", 0.35f, 0.8f),
                new Position("CB2", 0.65f, 0.8f),
                new Position("RB", 0.85f, 0.8f),
                new Position("LM", 0.2f, 0.65f),
                new Position("LCM", 0.35f, 0.65f),
                new Position("CM", 0.5f, 0.6f),
                new Position("RCM", 0.65f, 0.65f),
                new Position("RM", 0.8f, 0.65f),
                new Position("ST", 0.5f, 0.3f)
        ));

        map.put("3-1-4-2", Arrays.asList(
                new Position("GK", 0.5f, 0.95f),
                new Position("CB1", 0.3f, 0.8f),
                new Position("CB2", 0.5f, 0.8f),
                new Position("CB3", 0.7f, 0.8f),
                new Position("CDM", 0.5f, 0.7f),
                new Position("LM", 0.2f, 0.6f),
                new Position("LCM", 0.4f, 0.6f),
                new Position("RCM", 0.6f, 0.6f),
                new Position("RM", 0.8f, 0.6f),
                new Position("ST1", 0.4f, 0.3f),
                new Position("ST2", 0.6f, 0.3f)
        ));

        map.put("3-4-1-2", Arrays.asList(
                new Position("GK", 0.5f, 0.95f),
                new Position("CB1", 0.25f, 0.8f),
                new Position("CB2", 0.5f, 0.8f),
                new Position("CB3", 0.75f, 0.8f),
                new Position("LM", 0.2f, 0.65f),
                new Position("LCM", 0.4f, 0.65f),
                new Position("RCM", 0.6f, 0.65f),
                new Position("RM", 0.8f, 0.65f),
                new Position("CAM", 0.5f, 0.5f),
                new Position("ST1", 0.4f, 0.3f),
                new Position("ST2", 0.6f, 0.3f)
        ));

        map.put("3-4-2-1", Arrays.asList(
                new Position("GK", 0.5f, 0.95f),
                new Position("CB1", 0.25f, 0.8f),
                new Position("CB2", 0.5f, 0.8f),
                new Position("CB3", 0.75f, 0.8f),
                new Position("LM", 0.2f, 0.65f),
                new Position("LCM", 0.4f, 0.65f),
                new Position("RCM", 0.6f, 0.65f),
                new Position("RM", 0.8f, 0.65f),
                new Position("LAM", 0.35f, 0.5f),
                new Position("RAM", 0.65f, 0.5f),
                new Position("ST", 0.5f, 0.3f)
        ));

        map.put("3-4-3", Arrays.asList(
                new Position("GK", 0.5f, 0.95f),
                new Position("CB1", 0.25f, 0.8f),
                new Position("CB2", 0.5f, 0.8f),
                new Position("CB3", 0.75f, 0.8f),
                new Position("LM", 0.2f, 0.65f),
                new Position("LCM", 0.4f, 0.65f),
                new Position("RCM", 0.6f, 0.65f),
                new Position("RM", 0.8f, 0.65f),
                new Position("LW", 0.25f, 0.4f),
                new Position("ST", 0.5f, 0.3f),
                new Position("RW", 0.75f, 0.4f)
        ));

        map.put("3-5-1-1", Arrays.asList(
                new Position("GK", 0.5f, 0.95f),
                new Position("CB1", 0.25f, 0.8f),
                new Position("CB2", 0.5f, 0.8f),
                new Position("CB3", 0.75f, 0.8f),
                new Position("LM", 0.2f, 0.65f),
                new Position("LCM", 0.35f, 0.65f),
                new Position("CM", 0.5f, 0.6f),
                new Position("RCM", 0.65f, 0.65f),
                new Position("RM", 0.8f, 0.65f),
                new Position("CF", 0.5f, 0.45f),
                new Position("ST", 0.5f, 0.3f)
        ));

        map.put("3-5-2", Arrays.asList(
                new Position("GK", 0.5f, 0.95f),
                new Position("CB1", 0.25f, 0.8f),
                new Position("CB2", 0.5f, 0.8f),
                new Position("CB3", 0.75f, 0.8f),
                new Position("LM", 0.15f, 0.65f),
                new Position("LCM", 0.35f, 0.65f),
                new Position("CM", 0.5f, 0.6f),
                new Position("RCM", 0.65f, 0.65f),
                new Position("RM", 0.85f, 0.65f),
                new Position("ST1", 0.4f, 0.3f),
                new Position("ST2", 0.6f, 0.3f)
        ));

        map.put("5-2-1-2", Arrays.asList(
                new Position("GK", 0.5f, 0.95f),
                new Position("LB", 0.1f, 0.8f),
                new Position("CB1", 0.3f, 0.8f),
                new Position("CB2", 0.5f, 0.8f),
                new Position("CB3", 0.7f, 0.8f),
                new Position("RB", 0.9f, 0.8f),
                new Position("LCM", 0.35f, 0.65f),
                new Position("RCM", 0.65f, 0.65f),
                new Position("CAM", 0.5f, 0.5f),
                new Position("ST1", 0.4f, 0.3f),
                new Position("ST2", 0.6f, 0.3f)
        ));

        map.put("5-2-2-1", Arrays.asList(
                new Position("GK", 0.5f, 0.95f),
                new Position("LB", 0.1f, 0.8f),
                new Position("CB1", 0.3f, 0.8f),
                new Position("CB2", 0.5f, 0.8f),
                new Position("CB3", 0.7f, 0.8f),
                new Position("RB", 0.9f, 0.8f),
                new Position("LCM", 0.35f, 0.65f),
                new Position("RCM", 0.65f, 0.65f),
                new Position("LAM", 0.35f, 0.5f),
                new Position("RAM", 0.65f, 0.5f),
                new Position("ST", 0.5f, 0.3f)
        ));

        map.put("5-2-3", Arrays.asList(
                new Position("GK", 0.5f, 0.95f),
                new Position("LB", 0.1f, 0.8f),
                new Position("CB1", 0.3f, 0.8f),
                new Position("CB2", 0.5f, 0.8f),
                new Position("CB3", 0.7f, 0.8f),
                new Position("RB", 0.9f, 0.8f),
                new Position("CM1", 0.35f, 0.65f),
                new Position("CM2", 0.65f, 0.65f),
                new Position("LW", 0.25f, 0.45f),
                new Position("ST", 0.5f, 0.3f),
                new Position("RW", 0.75f, 0.45f)
        ));

        map.put("5-3-1-1", Arrays.asList(
                new Position("GK", 0.5f, 0.95f),
                new Position("LB", 0.1f, 0.8f),
                new Position("CB1", 0.3f, 0.8f),
                new Position("CB2", 0.5f, 0.8f),
                new Position("CB3", 0.7f, 0.8f),
                new Position("RB", 0.9f, 0.8f),
                new Position("LCM", 0.3f, 0.65f),
                new Position("CM", 0.5f, 0.65f),
                new Position("RCM", 0.7f, 0.65f),
                new Position("CF", 0.5f, 0.5f),
                new Position("ST", 0.5f, 0.3f)
        ));

        map.put("5-3-2", Arrays.asList(
                new Position("GK", 0.5f, 0.95f),
                new Position("LB", 0.1f, 0.8f),
                new Position("LCB", 0.3f, 0.8f),
                new Position("RCB", 0.5f, 0.8f),
                new Position("CB3", 0.7f, 0.8f),
                new Position("RB", 0.9f, 0.8f),
                new Position("LCM", 0.35f, 0.65f),
                new Position("CM", 0.5f, 0.65f),
                new Position("RCM", 0.65f, 0.65f),
                new Position("ST1", 0.4f, 0.3f),
                new Position("ST2", 0.6f, 0.3f)
        ));

        map.put("5-4-1", Arrays.asList(
                new Position("GK", 0.5f, 0.95f),
                new Position("LB", 0.1f, 0.8f),
                new Position("LCB", 0.3f, 0.8f),
                new Position("RCB", 0.5f, 0.8f),
                new Position("CB3", 0.7f, 0.8f),
                new Position("RB", 0.9f, 0.8f),
                new Position("LM", 0.2f, 0.65f),
                new Position("LCM", 0.4f, 0.65f),
                new Position("RCM", 0.6f, 0.65f),
                new Position("RM", 0.8f, 0.65f),
                new Position("ST", 0.5f, 0.3f)
        ));

        map.put("3-6-1", Arrays.asList(
                new Position("GK", 0.5f, 0.95f),
                new Position("CB1", 0.3f, 0.8f),
                new Position("CB2", 0.5f, 0.8f),
                new Position("CB3", 0.7f, 0.8f),
                new Position("LM", 0.2f, 0.65f),
                new Position("LCM", 0.35f, 0.65f),
                new Position("CM", 0.5f, 0.6f),
                new Position("RCM", 0.65f, 0.65f),
                new Position("RM", 0.8f, 0.65f),
                new Position("CAM", 0.5f, 0.5f),
                new Position("ST", 0.5f, 0.3f)
        ));

        map.put("4-1-5", Arrays.asList(
                new Position("GK", 0.5f, 0.95f),
                new Position("LB", 0.15f, 0.8f),
                new Position("CB1", 0.35f, 0.8f),
                new Position("CB2", 0.65f, 0.8f),
                new Position("RB", 0.85f, 0.8f),
                new Position("CDM", 0.5f, 0.7f),
                new Position("LWF", 0.15f, 0.5f),
                new Position("LM", 0.3f, 0.5f),
                new Position("CAM", 0.5f, 0.45f),
                new Position("RM", 0.7f, 0.5f),
                new Position("RWF", 0.85f, 0.5f)
        ));

        map.put("2-5-3", Arrays.asList(
                new Position("GK", 0.5f, 0.95f),
                new Position("CB1", 0.35f, 0.85f),
                new Position("CB2", 0.65f, 0.85f),
                new Position("LM", 0.15f, 0.65f),
                new Position("LCM", 0.35f, 0.65f),
                new Position("CM", 0.5f, 0.6f),
                new Position("RCM", 0.65f, 0.65f),
                new Position("RM", 0.85f, 0.65f),
                new Position("LW", 0.2f, 0.4f),
                new Position("ST", 0.5f, 0.3f),
                new Position("RW", 0.8f, 0.4f)
        ));

        return map;
    }
}
