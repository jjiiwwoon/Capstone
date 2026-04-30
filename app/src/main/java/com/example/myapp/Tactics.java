package com.example.myapp;

import android.graphics.Color;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.*;

public class Tactics extends AppCompatActivity {

    private Spinner spinnerFormation;
    private Button btnSaveTactics;
    private RelativeLayout fieldLayout;
    private LinearLayout playerListLayout;
    private Map<String, List<FormationData.Position>> formationMap;

    private Map<String, PlayerInfo> playerInfoMap = new HashMap<>();
    private static class PlayerInfo {
        String nickname;
        String position;
        String imageUrl;
        PlayerInfo(String nickname, String position, String imageUrl) {
            this.nickname = nickname;
            this.position = position;
            this.imageUrl = imageUrl;
        }
    }
    // 포지션 영역 정의 클래스
    public static class PositionZone {
        public String name;
        public float xMin, xMax, yMin, yMax;
        public PositionZone(String name, float xMin, float xMax, float yMin, float yMax) {
            this.name = name;
            this.xMin = xMin;
            this.xMax = xMax;
            this.yMin = yMin;
            this.yMax = yMax;
        }

        public boolean contains(float xRatio, float yRatio) {
            return xRatio >= xMin && xRatio <= xMax && yRatio >= yMin && yRatio <= yMax;
        }
    }

    // 포지션 영역 리스트
    private List<PositionZone> zones = Arrays.asList(
            // 공격수
            new PositionZone("ST", 0.35f, 0.65f, 0.05f, 0.20f),
            new PositionZone("CF", 0.4f, 0.6f, 0.10f, 0.25f),
            new PositionZone("LF", 0.15f, 0.35f, 0.10f, 0.25f),
            new PositionZone("RF", 0.65f, 0.85f, 0.10f, 0.25f),

            // 공격형 미드필더
            new PositionZone("CAM", 0.35f, 0.65f, 0.21f, 0.33f),
            new PositionZone("LAM", 0.15f, 0.35f, 0.21f, 0.33f),
            new PositionZone("RAM", 0.65f, 0.85f, 0.21f, 0.33f),

            // 중앙 미드필더
            new PositionZone("CM", 0.3f, 0.7f, 0.34f, 0.45f),
            new PositionZone("LCM", 0.15f, 0.35f, 0.34f, 0.45f),
            new PositionZone("RCM", 0.65f, 0.85f, 0.34f, 0.45f),

            // 수비형 미드필더
            new PositionZone("CDM", 0.3f, 0.7f, 0.46f, 0.58f),
            new PositionZone("LDM", 0.15f, 0.35f, 0.46f, 0.58f),
            new PositionZone("RDM", 0.65f, 0.85f, 0.46f, 0.58f),

            // 측면 미드필더
            new PositionZone("LM", 0.05f, 0.25f, 0.30f, 0.55f),
            new PositionZone("RM", 0.75f, 0.95f, 0.30f, 0.55f),

            // 수비수
            new PositionZone("LCB", 0.25f, 0.5f, 0.59f, 0.75f),
            new PositionZone("RCB", 0.5f, 0.75f, 0.59f, 0.75f),
            new PositionZone("LB", 0.05f, 0.25f, 0.62f, 0.75f),
            new PositionZone("RB", 0.75f, 0.95f, 0.62f, 0.75f),

            // 골키퍼
            new PositionZone("GK", 0.4f, 0.6f, 0.85f, 1.0f)
    );


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tactics);

        spinnerFormation = findViewById(R.id.spinnerFormation);
        btnSaveTactics = findViewById(R.id.btnSaveTactics);
        fieldLayout = findViewById(R.id.fieldLayout);
        playerListLayout = findViewById(R.id.playerListLayout);

        formationMap = FormationData.getFormations();

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.formations,
                R.layout.custom_spinner_selected_item  // ✅ 선택된 항목 모양
        );
        adapter.setDropDownViewResource(R.layout.custom_spinner_item); // ✅ 펼쳐졌을 때 항목 모양
        spinnerFormation.setAdapter(adapter);


        spinnerFormation.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                drawFormation(spinnerFormation.getSelectedItem().toString());
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnSaveTactics.setOnClickListener(v ->
                Toast.makeText(this, "전술 저장 기능은 아직 미구현", Toast.LENGTH_SHORT).show());

        FirebaseFirestore.getInstance().collection("profiles")
                .document(FirebaseAuth.getInstance().getCurrentUser().getUid())
                .get()
                .addOnSuccessListener(profileSnapshot -> {
                    String teamId = profileSnapshot.getString("myTeam");
                    if (teamId != null) {
                        loadTeamPlayers(teamId);
                    }
                });

        // ✅ 레이아웃 사이즈가 잡힌 후 drawFormation 실행 (GK 안 보이는 문제 해결)
        fieldLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                fieldLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                drawFormation(spinnerFormation.getSelectedItem().toString());
            }
        });
    }
    private void loadTeamPlayers(String teamId) {
        FirebaseFirestore.getInstance().collection("teams")
                .document(teamId)
                .get()
                .addOnSuccessListener(teamSnapshot -> {
                    if (teamSnapshot.exists()) {
                        List<String> memberUids = (List<String>) teamSnapshot.get("members");
                        if (memberUids != null) {
                            playerListLayout.removeAllViews();  // 기존 리스트 초기화
                            for (String uid : memberUids) {
                                FirebaseFirestore.getInstance().collection("profiles")
                                        .document(uid)
                                        .get()
                                        .addOnSuccessListener(profile -> {
                                            if (profile.exists()) {
                                                String nickname = profile.getString("nickname");
                                                String position = profile.getString("position");
                                                String imageUrl = profile.getString("profileImageUrl");

                                                // ✅ 선수 정보 저장 (복원용)
                                                playerInfoMap.put(nickname, new PlayerInfo(nickname, position, imageUrl));

                                                // ✅ XML로 정의한 player_field_item을 inflate
                                                View playerView = getLayoutInflater().inflate(R.layout.player_field_item, playerListLayout, false);

                                                ImageView imageView = playerView.findViewById(R.id.playerImage);
                                                TextView tvName = playerView.findViewById(R.id.playerName);
                                                TextView tvPos = playerView.findViewById(R.id.playerPosition);

                                                Glide.with(this)
                                                        .load(imageUrl)
                                                        .placeholder(R.drawable.default_profile_image)
                                                        .circleCrop()
                                                        .into(imageView);

                                                tvName.setText(nickname);
                                                tvPos.setText(position);

                                                // ✅ 드래그 태그 설정 (닉네임 / 포지션)
                                                String dragData = nickname + " / " + position;
                                                playerView.setTag(dragData);

                                                // ✅ ScrollView 충돌 방지용 터치 리스너
                                                playerView.setOnTouchListener((v, event) -> {
                                                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                                                        v.getParent().requestDisallowInterceptTouchEvent(true);
                                                    } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                                                        v.getParent().requestDisallowInterceptTouchEvent(false);
                                                    }
                                                    return false;
                                                });

                                                // ✅ 드래그 시작 (롱클릭 시)
                                                playerView.setOnLongClickListener(v -> {
                                                    View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(v);
                                                    v.startDragAndDrop(null, shadowBuilder, v.getTag(), 0);
                                                    return true;
                                                });

                                                // ✅ 드래그 가능하게 설정
                                                playerView.setClickable(true);

                                                playerListLayout.addView(playerView);
                                            }
                                        });
                            }
                        }
                    }
                });
    }


    private void drawFormation(String formationKey) {
        fieldLayout.removeAllViews();
        List<FormationData.Position> positions = formationMap.get(formationKey);
        if (positions == null) return;

        fieldLayout.post(() -> {
            int layoutWidth = fieldLayout.getWidth();
            int layoutHeight = fieldLayout.getHeight();
            int size = 120;

            for (FormationData.Position pos : positions) {
                TextView tv = new TextView(this);
                tv.setText(pos.name);
                tv.setTextColor(Color.WHITE);
                tv.setBackgroundResource(R.drawable.circle_background);
                tv.setGravity(Gravity.CENTER);
                tv.setTextSize(12);

                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(size, size);
                params.leftMargin = (int)(pos.x * layoutWidth) - size / 2;
                params.topMargin = (int)(pos.y * layoutHeight) - size / 2;

                tv.setTag(pos.name); // 초기 포지션 이름 저장

                // ✅ 더블탭 시 포지션 초기화 + 팀원 목록 복원
                tv.setOnTouchListener(new View.OnTouchListener() {
                    float dX, dY;
                    private long lastTapTime = 0;

                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                long currentTime = System.currentTimeMillis();
                                if (currentTime - lastTapTime < 300) {
                                    if (v instanceof TextView && v.getTag() != null) {
                                        TextView t = (TextView) v;
                                        String text = t.getText().toString();
                                        if (text.contains("(")) {
                                            String name = text.substring(0, text.indexOf("("));
                                            String originalPos = t.getTag().toString();
                                            t.setText(originalPos);

                                            // ✅ playerInfoMap에서 정보 가져와 복원
                                            PlayerInfo info = playerInfoMap.get(name);
                                            if (info != null) {
                                                addPlayerBackToList(info.nickname, info.position, info.imageUrl);
                                            }
                                        }
                                    }
                                }
                                lastTapTime = currentTime;

                                dX = v.getX() - event.getRawX();
                                dY = v.getY() - event.getRawY();
                                return true;

                            case MotionEvent.ACTION_MOVE:
                                float newX = event.getRawX() + dX;
                                float newY = event.getRawY() + dY;
                                if (newX >= 0 && newX <= layoutWidth - v.getWidth()
                                        && newY >= 0 && newY <= layoutHeight - v.getHeight()) {
                                    v.setX(newX);
                                    v.setY(newY);
                                }
                                return true;

                            case MotionEvent.ACTION_UP:
                                float centerX = v.getX() + v.getWidth() / 2f;
                                float centerY = v.getY() + v.getHeight() / 2f;
                                float ratioX = centerX / layoutWidth;
                                float ratioY = centerY / layoutHeight;

                                for (PositionZone zone : zones) {
                                    if (zone.contains(ratioX, ratioY)) {
                                        TextView t = (TextView) v;
                                        String currentText = t.getText().toString();
                                        if (currentText.contains("(")) {
                                            String name = currentText.substring(0, currentText.indexOf("("));
                                            t.setText(name + "(" + zone.name + ")");
                                        } else {
                                            t.setText(zone.name);
                                        }
                                        break;
                                    }
                                }
                                return true;
                        }
                        return false;
                    }
                });

                // ✅ 드래그된 선수 드롭 시 팀원 목록 제거 + 중복 제거
                tv.setOnDragListener((v, event) -> {
                    switch (event.getAction()) {
                        case DragEvent.ACTION_DRAG_STARTED:
                            return true;
                        case DragEvent.ACTION_DROP:
                            String draggedText = (String) event.getLocalState();  // "닉네임 / 포지션"
                            String[] parts = draggedText.split(" / ");
                            if (parts.length == 2) {
                                String name = parts[0];

                                // 기존 포지션에서 이 선수 제거
                                for (int i = 0; i < fieldLayout.getChildCount(); i++) {
                                    View child = fieldLayout.getChildAt(i);
                                    if (child instanceof TextView) {
                                        TextView other = (TextView) child;
                                        String txt = other.getText().toString();
                                        if (txt.contains(name + "(")) {
                                            if (other.getTag() != null) {
                                                other.setText(other.getTag().toString());
                                            }
                                        }
                                    }
                                }

                                // 팀원 목록에서 이 선수 제거
                                for (int i = 0; i < playerListLayout.getChildCount(); i++) {
                                    View child = playerListLayout.getChildAt(i);
                                    if (child.getTag() instanceof String && ((String) child.getTag()).startsWith(name + " /")) {
                                        playerListLayout.removeView(child);
                                        break;
                                    }
                                }

                                // 현재 포지션에 선수 배치
                                TextView target = (TextView) v;
                                String posName = target.getTag().toString();
                                target.setText(name + "(" + posName + ")");
                            }
                            return true;
                    }
                    return true;
                });

                fieldLayout.addView(tv, params);
            }
        });
    }


    private void addPlayerBackToList(String nickname, String position, String imageUrl) {
        View playerView = getLayoutInflater().inflate(R.layout.player_field_item, playerListLayout, false);

        ImageView imageView = playerView.findViewById(R.id.playerImage);
        TextView tvName = playerView.findViewById(R.id.playerName);
        TextView tvPos = playerView.findViewById(R.id.playerPosition);

        Glide.with(this)
                .load(imageUrl)
                .placeholder(R.drawable.default_profile_image)
                .circleCrop()
                .into(imageView);

        tvName.setText(nickname);
        tvPos.setText(position);

        String dragData = nickname + " / " + position;
        playerView.setTag(dragData);

        playerView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });

        playerView.setOnLongClickListener(v -> {
            View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(v);
            v.startDragAndDrop(null, shadowBuilder, v.getTag(), 0);
            return true;
        });

        playerView.setClickable(true);

        playerListLayout.addView(playerView);
    }
    
}
