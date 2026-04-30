package com.example.myapp;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;

import java.util.HashMap;
import java.util.Map;

public final class TeamCounters {

    private TeamCounters() {}

    /**
     * 멤버 가입 시 호출: teams/{teamId}
     * - profiles/{uid}.skill 읽고 skillSum += skill, memberCount += 1, skillAverage 재계산
     * - members 배열에 uid 추가(이미 있으면 무시)
     */
    public static Task<Void> onMemberJoined(@NonNull String teamId, @NonNull String uid) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference teamRef = db.collection("teams").document(teamId);
        DocumentReference profileRef = db.collection("profiles").document(uid);

        return db.runTransaction((Transaction.Function<Void>) transaction -> {
            Long skill = transaction.get(profileRef).getLong("skill");
            long delta = (skill != null) ? skill : 0L;

            // members 배열에 uid 추가 (중복 방어)
            transaction.update(teamRef, "members", FieldValue.arrayUnion(uid));

            // 증분 적용
            transaction.update(teamRef, new HashMap<String, Object>() {{
                put("skillSum", FieldValue.increment(delta));
                put("memberCount", FieldValue.increment(1L));
            }});

            // 평균 재계산을 위해 현재 값 읽기
            Long sumAfter = transaction.get(teamRef).getLong("skillSum");
            Long cntAfter = transaction.get(teamRef).getLong("memberCount");

            int avg = 0;
            if (sumAfter != null && cntAfter != null && cntAfter > 0) {
                avg = (int) (sumAfter / cntAfter);
            }
            transaction.update(teamRef, "skillAverage", avg);

            return null;
        });
    }

    /**
     * 멤버 탈퇴 시 호출:
     * - profiles/{uid}.skill 읽고 skillSum -= skill, memberCount -= 1, 평균 재계산
     * - members 배열에서 uid 제거
     */
    public static Task<Void> onMemberLeft(@NonNull String teamId, @NonNull String uid) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference teamRef = db.collection("teams").document(teamId);
        DocumentReference profileRef = db.collection("profiles").document(uid);

        return db.runTransaction((Transaction.Function<Void>) transaction -> {
            Long skill = transaction.get(profileRef).getLong("skill");
            long delta = (skill != null) ? skill : 0L;

            transaction.update(teamRef, "members", FieldValue.arrayRemove(uid));
            transaction.update(teamRef, new HashMap<String, Object>() {{
                put("skillSum", FieldValue.increment(-delta));
                put("memberCount", FieldValue.increment(-1L));
            }});

            Long sumAfter = transaction.get(teamRef).getLong("skillSum");
            Long cntAfter = transaction.get(teamRef).getLong("memberCount");
            int avg = 0;
            if (sumAfter != null && cntAfter != null && cntAfter > 0) {
                avg = (int) (sumAfter / cntAfter);
            }
            transaction.update(teamRef, "skillAverage", avg);

            return null;
        });
    }

    /**
     * 개인 프로필에서 skill 값이 바뀔 때 팀 평균 반영.
     * - 호출 시 oldSkill, newSkill를 알고 있으면 델타 기반으로 즉시 반영.
     */
    public static Task<Void> onMemberSkillChanged(@NonNull String teamId, long oldSkill, long newSkill) {
        long delta = newSkill - oldSkill;
        if (delta == 0) return Tasks.forResult(null);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference teamRef = db.collection("teams").document(teamId);

        return db.runTransaction((Transaction.Function<Void>) transaction -> {
            transaction.update(teamRef, "skillSum", FieldValue.increment(delta));

            Long sumAfter = transaction.get(teamRef).getLong("skillSum");
            Long cntAfter = transaction.get(teamRef).getLong("memberCount");
            int avg = 0;
            if (sumAfter != null && cntAfter != null && cntAfter > 0) {
                avg = (int) (sumAfter / cntAfter);
            }
            transaction.update(teamRef, "skillAverage", avg);
            return null;
        });
    }
}
