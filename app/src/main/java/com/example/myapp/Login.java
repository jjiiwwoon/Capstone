package com.example.myapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class Login extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.login);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        Button loginButton = findViewById(R.id.button);
        loginButton.setOnClickListener(v -> loginUser());

        Button registerButton = findViewById(R.id.button2);
        registerButton.setOnClickListener(v -> {
            Intent intent = new Intent(Login.this, Register.class);
            startActivity(intent);
        });
    }

    private void loginUser() {
        EditText usernameEditText = findViewById(R.id.Username);
        EditText passwordEditText = findViewById(R.id.Password);

        String username = usernameEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            CustomToast.warning(this, "아이디와 비밀번호를 입력해주세요.");
            return;
        }

        // username으로 email 찾기
        firestore.collection("users")
                .whereEqualTo("username", username)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        String email = querySnapshot.getDocuments().get(0).getString("email");

                        // 이메일/비밀번호 로그인
                        auth.signInWithEmailAndPassword(email, password)
                                .addOnCompleteListener(this, task -> {
                                    if (task.isSuccessful()) {
                                        FirebaseUser user = auth.getCurrentUser();

                                        // (배포 시 이메일 인증 체크를 다시 활성화)
                                        /*
                                        if (user != null && user.isEmailVerified()) {
                                            // 통과
                                        } else {
                                            CustomToast.info(this, "이메일 인증이 필요합니다. 이메일을 확인해주세요.");
                                            FirebaseAuth.getInstance().signOut();
                                            return;
                                        }
                                        */

                                        if (user != null) {
                                            // 프로필 존재 여부 확인 후 분기
                                            firestore.collection("profiles")
                                                    .whereEqualTo("username", username)
                                                    .limit(1)
                                                    .get()
                                                    .addOnSuccessListener(profileSnapshot -> {
                                                        if (!profileSnapshot.isEmpty()) {
                                                            // ✅ 성공 시 토스트 없이 바로 이동
                                                            startActivity(new Intent(Login.this, Home.class));
                                                            finish();
                                                        } else {
                                                            CustomToast.info(this, "프로필을 만들어주세요.");
                                                            Intent intent = new Intent(Login.this, CreateProfile.class);
                                                            intent.putExtra("username", username);
                                                            startActivity(intent);
                                                            finish();
                                                        }
                                                    })
                                                    .addOnFailureListener(e -> {
                                                        CustomToast.error(this, "프로필 확인 중 오류 발생");
                                                    });
                                        }
                                    } else {
                                        CustomToast.error(this, "비밀번호를 확인해주세요.");
                                    }
                                });
                    } else {
                        CustomToast.error(this, "아이디를 확인해주세요.");
                    }
                })
                .addOnFailureListener(e -> {
                    CustomToast.error(this, "로그인 중 오류 발생");
                });
    }
}
