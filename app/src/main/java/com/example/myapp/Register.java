package com.example.myapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.HashMap;
import java.util.Map;

public class Register extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

    private FirebaseUser currentUser;
    // ✅ 이메일 인증을 생략하므로 기본값을 true로 설정하거나 체크 로직을 우회합니다.
    private boolean isEmailVerified = false;

    private EditText usernameEditText, emailEditText, passwordEditText;
    private Button verifyEmailButton, registerButton;
    private TextView emailStatusTextView;

    // 인증 시 실제로 인증메일을 보냈던 이메일을 기억해둔다 (주석 처리된 기능용)
    private String verifiedEmail = "";
    // 인증용으로 임시로 계정을 만들 때 쓸 임시 비밀번호 (주석 처리된 기능용)
    private String tempPassword = "TempPassword123!";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.register);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        usernameEditText = findViewById(R.id.editTextUsername);
        emailEditText    = findViewById(R.id.editTextEmail);
        passwordEditText = findViewById(R.id.editTextPassword);
        verifyEmailButton= findViewById(R.id.buttonVerifyEmail);
        registerButton   = findViewById(R.id.buttonRegister);
        emailStatusTextView = findViewById(R.id.textViewEmailStatus);

        // ✅ [수정] 현재 환경 에러로 인해 이메일 인증 버튼과 상태 메시지는 숨깁니다.
        verifyEmailButton.setVisibility(View.GONE);
        emailStatusTextView.setVisibility(View.GONE);

        // ✅ 회원가입 버튼은 처음부터 바로 활성화합니다.
        registerButton.setEnabled(true);

        // 원래 있던 리스너 (주석 처리 유지)
        // verifyEmailButton.setOnClickListener(v -> sendVerificationEmail());

        registerButton.setOnClickListener(v -> registerUser());
    }

    private void registerUser() {
        // (1) 입력값 읽기
        String username = usernameEditText.getText().toString().trim();
        String email    = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        // (2) 필드 검증 (유효성 검사)
        if (username.isEmpty()) {
            CustomToast.warning(this, "아이디를 입력해주세요.");
            return;
        }
        if (email.isEmpty()) {
            CustomToast.warning(this, "이메일을 입력해주세요.");
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            CustomToast.warning(this, "올바른 이메일 형식이 아닙니다.");
            return;
        }
        if (password.isEmpty()) {
            CustomToast.warning(this, "비밀번호를 입력해주세요.");
            return;
        }
        if (password.length() < 6) {
            CustomToast.warning(this, "비밀번호는 6자 이상이어야 합니다.");
            return;
        }

        // (3) Username 중복 체크 및 계정 생성
        firestore.collection("users")
                .whereEqualTo("username", username)
                .get()
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        CustomToast.error(this, "이미 사용 중인 사용자 이름입니다.");
                    } else {
                        // 중복이 없으면 실제 Firebase Auth 계정 생성 단계로 이동
                        createNewAccount(username, email, password);
                    }
                })
                .addOnFailureListener(e -> {
                    CustomToast.error(this, "중복 확인 중 오류가 발생했습니다: " + e.getMessage());
                });
    }

    // ✅ 인증 절차 없이 바로 계정을 생성하는 핵심 함수
    private void createNewAccount(String username, String email, String password) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = auth.getCurrentUser();
                    if (user != null) {
                        // Auth 계정 생성 성공 시 Firestore에 유저 정보 저장
                        saveUserToFirestore(user.getUid(), username, email);
                    }
                })
                .addOnFailureListener(e -> {
                    if (e instanceof FirebaseAuthUserCollisionException) {
                        CustomToast.error(this, "이미 사용 중인 이메일입니다.");
                    } else {
                        CustomToast.error(this, "계정 생성 실패: " + e.getMessage());
                    }
                });
    }

    // ✅ Firestore 'users' 컬렉션에 정보 저장
    private void saveUserToFirestore(String uid, String username, String email) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("username", username);
        userMap.put("email", email);

        firestore.collection("users")
                .document(uid)
                .set(userMap)
                .addOnSuccessListener(v -> {
                    CustomToast.success(this, "회원가입이 완료되었습니다.");
                    navigateToLogin();
                })
                .addOnFailureListener(e -> {
                    CustomToast.error(this, "정보 저장 실패: " + e.getMessage());
                });
    }

    private void navigateToLogin() {
        Intent intent = new Intent(this, Login.class);
        startActivity(intent);
        finish();
    }

    // =========================================================================
    // ⬇️ 아래는 기존에 사용하던 이메일 인증 관련 로직입니다 (현재는 사용하지 않음) ⬇️
    // =========================================================================

    /*
    private void sendVerificationEmail() {
        String email = emailEditText.getText().toString().trim();

        if (email.isEmpty()) {
            CustomToast.info(this, "이메일을 입력하세요.");
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            CustomToast.warning(this, "올바른 이메일 형식이 아닙니다.");
            return;
        }

        // 인증용 임시 계정 생성
        auth.createUserWithEmailAndPassword(email, tempPassword)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        currentUser = auth.getCurrentUser();
                        verifiedEmail = email;

                        if (currentUser != null) {
                            currentUser.sendEmailVerification()
                                    .addOnCompleteListener(emailTask -> {
                                        if (emailTask.isSuccessful()) {
                                            CustomToast.info(this, "이메일이 전송되었습니다. 인증을 완료해주세요.");
                                            emailStatusTextView.setText("이메일 인증 중...");
                                            emailStatusTextView.setTextColor(
                                                    ContextCompat.getColor(this, android.R.color.holo_orange_dark));
                                            checkEmailVerification(currentUser);
                                        } else {
                                            CustomToast.error(this, "이메일 전송 실패: " + emailTask.getException().getMessage());
                                        }
                                    });
                        }
                    } else {
                        CustomToast.error(this, "계정 생성 실패: " + task.getException().getMessage());
                    }
                });
    }

    private void checkEmailVerification(FirebaseUser user) {
        Handler handler = new Handler();
        handler.postDelayed(() -> {
            user.reload().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    if (user.isEmailVerified()) {
                        isEmailVerified = true;
                        emailStatusTextView.setText("이메일 인증이 완료되었습니다.");
                        emailStatusTextView.setTextColor(
                                ContextCompat.getColor(this, android.R.color.holo_green_dark));
                        registerButton.setEnabled(true);
                    } else {
                        checkEmailVerification(user);
                    }
                } else {
                    // CustomToast.error(this, "사용자 정보를 불러오는 데 실패했습니다.");
                }
            });
        }, 2000);
    }
    */
}