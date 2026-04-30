package com.example.myapp;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

public class Home extends AppCompatActivity {

    // 하단 네비게이션 버튼
    private Button btnRecruitMatch, btnMyTeam, btnOtherTeams, btnMyProfile, btnChat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home);

        // 하단 네비게이션 버튼들 초기화
        btnRecruitMatch = findViewById(R.id.btnRecruitMatch);
        btnMyTeam       = findViewById(R.id.btnMyTeam);
        btnOtherTeams   = findViewById(R.id.btnOtherTeams);
        btnMyProfile    = findViewById(R.id.btnMyProfile); // ← 새로 추가된 '내 프로필'
        btnChat         = findViewById(R.id.btnChat);

        // 앱 시작 시 기본 프래그먼트: 내 프로필(MyProfile)
        loadFragment(new MyProfile());
        updateBottomNavColor(btnMyProfile);

        // "모집/매치" 버튼 클릭
        btnRecruitMatch.setOnClickListener(v -> {
            loadFragment(new RecruitMatch());
            updateBottomNavColor(btnRecruitMatch);
        });

        // "My팀" 버튼 클릭
        btnMyTeam.setOnClickListener(v -> {
            loadFragment(new MyTeam());
            updateBottomNavColor(btnMyTeam);
        });

        // "팀(다른팀)" 버튼 클릭
        btnOtherTeams.setOnClickListener(v -> {
            loadFragment(new AllTeam());
            updateBottomNavColor(btnOtherTeams);
        });

        // "내 프로필" 버튼 클릭 (기존 '마이페이지'와 동일 기능)
        btnMyProfile.setOnClickListener(v -> {
            loadFragment(new MyProfile());
            updateBottomNavColor(btnMyProfile);
        });

        // "채팅" 버튼 클릭
        btnChat.setOnClickListener(v -> {
            loadFragment(new Chat());
            updateBottomNavColor(btnChat);
        });
    }

    /** 프래그먼트 전환 헬퍼 */
    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    private void updateBottomNavColor(Button selected) {
        Button[] buttons = {
                btnRecruitMatch, btnMyTeam, btnOtherTeams, btnMyProfile, btnChat
        };

        for (Button btn : buttons) {
            if (btn == selected) {
                int selectedColor = Color.parseColor("#42A5F5"); // 🌤 밝은 파랑
                btn.setTextColor(selectedColor);

                // 🔹 아이콘 색도 동일하게 변경 (MaterialButton일 때)
                if (btn instanceof com.google.android.material.button.MaterialButton) {
                    ((com.google.android.material.button.MaterialButton) btn)
                            .setIconTint(ColorStateList.valueOf(selectedColor));
                }

            } else {
                int defaultColor = Color.parseColor("#000000"); // 검정
                btn.setTextColor(defaultColor);

                if (btn instanceof com.google.android.material.button.MaterialButton) {
                    ((com.google.android.material.button.MaterialButton) btn)
                            .setIconTint(ColorStateList.valueOf(defaultColor));
                }
            }
        }
    }


}