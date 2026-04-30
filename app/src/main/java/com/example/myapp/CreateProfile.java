package com.example.myapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.method.ScrollingMovementMethod;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class CreateProfile extends AppCompatActivity {

    private EditText nicknameEditText, ageEditText, heightEditText, weightEditText, introEditText;
    private Spinner positionSpinner, skillSpinner, footSpinner, playerLevelSpinner;
    private RadioGroup playerTypeGroup;
    private LinearLayout layoutSelectPlayerLevel;

    private FirebaseFirestore firestore;
    private FirebaseAuth auth;
    private FirebaseStorage storage;
    private StorageReference storageRef;
    private String username;

    private ImageView imageProfile, iconProfileOverlay;
    private TextView textProfileHint;
    private View profileClickableArea;
    private Uri selectedImageUri;

    private MaterialButton saveButton;
    private String saveButtonOriginalText = "프로필 저장하기";

    private View contentContainer;
    private NestedScrollView mainRoot;

    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    imageProfile.setImageURI(selectedImageUri);

                    // 프로필 아이콘 오버레이 숨기기
                    if (iconProfileOverlay != null)
                        iconProfileOverlay.setVisibility(View.GONE);

                    // ❌ 변경하던 코드 제거: 선택해도 라벨은 계속 "프로필 사진"
                    // if (textProfileHint != null) textProfileHint.setText("탭해서 변경하기");
                }
            });

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    openImagePicker();
                } else {
                    CustomToast.warning(this, "사진 권한이 필요합니다.");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.create_profile);

        // 루트/컨테이너
        mainRoot = findViewById(R.id.mainRoot);
        contentContainer = findViewById(R.id.contentContainer);

        // 시스템바 + 키보드(IME) 인셋 반영
        final int baseLeft   = contentContainer.getPaddingLeft();
        final int baseTop    = contentContainer.getPaddingTop();
        final int baseRight  = contentContainer.getPaddingRight();
        final int baseBottom = contentContainer.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(contentContainer, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottomInset = Math.max(sys.bottom, ime.bottom);
            v.setPadding(baseLeft, baseTop + sys.top, baseRight, baseBottom + bottomInset);
            return insets;
        });

        // 바인딩
        nicknameEditText = findViewById(R.id.editNickname);
        ageEditText = findViewById(R.id.editAge);
        heightEditText = findViewById(R.id.editHeight);
        weightEditText = findViewById(R.id.editWeight);
        introEditText = findViewById(R.id.editIntroduction);

        positionSpinner = findViewById(R.id.spinnerPosition);
        skillSpinner = findViewById(R.id.spinnerSkill);
        footSpinner = findViewById(R.id.spinnerFoot);
        playerLevelSpinner = findViewById(R.id.spinnerPlayerLevel);

        playerTypeGroup = findViewById(R.id.radioGroupPlayerType);
        layoutSelectPlayerLevel = findViewById(R.id.layoutSelectPlayerLevel);

        imageProfile = findViewById(R.id.imageProfile);
        iconProfileOverlay = findViewById(R.id.iconProfileOverlay);
        textProfileHint = findViewById(R.id.textProfileHint);
        profileClickableArea = findViewById(R.id.profileClickableArea);
        saveButton = findViewById(R.id.btnSaveProfile);

        if (saveButton.getText() != null) {
            saveButtonOriginalText = saveButton.getText().toString();
        }

        // Firebase
        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();
        storageRef = storage.getReference();

        username = getIntent().getStringExtra("username");

        // 스피너 세팅
        setSpinner(positionSpinner, R.array.position_array);
        setSpinner(skillSpinner, R.array.skill_array);
        setSpinner(footSpinner, R.array.foot_array);
        setSpinner(playerLevelSpinner, R.array.player_level_array);

        // 프로필 이미지 클릭 → 사진 선택
        View.OnClickListener openPicker = v -> checkAndRequestPermission();
        profileClickableArea.setOnClickListener(openPicker);
        imageProfile.setOnClickListener(openPicker);

        // 라디오: 선택에 따라 선출 단계 노출 제어
        playerTypeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            layoutSelectPlayerLevel.setVisibility(checkedId == R.id.radioPro ? View.VISIBLE : View.GONE);
        });

        // 자기소개 스크롤 가능
        introEditText.setVerticalScrollBarEnabled(true);
        introEditText.setMovementMethod(new ScrollingMovementMethod());
        introEditText.setOnTouchListener((v, event) -> {
            if (v.hasFocus()) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                }
            }
            return false;
        });

        // 키보드 위로 보이도록
        introEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.postDelayed(this::forceIntroAboveKeyboard, 120);
            }
        });

        // 키보드 열림 감지 → 강제 스크롤
        final View root = findViewById(android.R.id.content);
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect r = new Rect();
            root.getWindowVisibleDisplayFrame(r);

            int screenHeight = root.getRootView().getHeight();
            int keypadHeight = screenHeight - r.bottom;
            boolean keyboardVisible = keypadHeight > screenHeight * 0.15f;

            if (keyboardVisible && introEditText != null && introEditText.hasFocus()) {
                ensureViewAboveVisibleBottom(introEditText, r.bottom, (int) dp(12));
            }
        });

        // 저장 버튼 클릭
        saveButton.setOnClickListener(v -> {
            tapFlash(saveButton);
            setLoading(true);
            saveProfile();
        });
    }

    /** 자기소개 칸을 강제로 키보드 위로 올림 */
    private void forceIntroAboveKeyboard() {
        final View root = findViewById(android.R.id.content);
        Rect r = new Rect();
        root.getWindowVisibleDisplayFrame(r);
        ensureViewAboveVisibleBottom(introEditText, r.bottom, (int) dp(12));
    }

    /** target을 화면의 보이는 하단 위로 보이도록 스크롤 보정 */
    private void ensureViewAboveVisibleBottom(View target, int visibleBottomOnScreen, int marginPx) {
        if (target == null || mainRoot == null) return;

        int[] loc = new int[2];
        target.getLocationOnScreen(loc);
        int targetBottomOnScreen = loc[1] + target.getHeight();

        int overlap = targetBottomOnScreen + marginPx - visibleBottomOnScreen;
        if (overlap > 0) {
            mainRoot.smoothScrollBy(0, overlap);
        } else {
            int targetTopOnScreen = loc[1];
            int topOverlap = (int) dp(8) - targetTopOnScreen;
            if (topOverlap > 0) {
                mainRoot.smoothScrollBy(0, -topOverlap);
            }
        }
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    /** 버튼 번쩍 효과 */
    private void tapFlash(View target) {
        target.animate().cancel();
        target.setScaleX(1f);
        target.setScaleY(1f);
        target.setAlpha(1f);

        target.animate()
                .alpha(0.6f)
                .scaleX(0.98f)
                .scaleY(0.98f)
                .setDuration(70)
                .withEndAction(() ->
                        target.animate()
                                .alpha(1f)
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(90)
                                .start()
                ).start();
    }

    private void setSpinner(Spinner spinner, int arrayId) {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, arrayId, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void checkAndRequestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES);
            } else {
                openImagePicker();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            } else {
                openImagePicker();
            }
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void saveProfile() {
        String nickname = nicknameEditText.getText().toString().trim();
        String ageStr = ageEditText.getText().toString().trim();
        String heightStr = heightEditText.getText().toString().trim();
        String weightStr = weightEditText.getText().toString().trim();
        String intro = introEditText.getText().toString().trim();

        if (nickname.isEmpty() || ageStr.isEmpty() || heightStr.isEmpty() || weightStr.isEmpty() || intro.isEmpty()) {
            CustomToast.warning(this, "모든 항목을 입력해주세요.");
            setLoading(false);
            return;
        }

        int age, height, weight;
        try {
            age = Integer.parseInt(ageStr);
            height = Integer.parseInt(heightStr);
            weight = Integer.parseInt(weightStr);
        } catch (NumberFormatException e) {
            CustomToast.error(this, "나이/키/몸무게 입력을 확인해주세요.");
            setLoading(false);
            return;
        }

        String position = positionSpinner.getSelectedItem().toString();
        String foot = footSpinner.getSelectedItem().toString();

        int skill;
        try {
            skill = Integer.parseInt(skillSpinner.getSelectedItem().toString());
        } catch (NumberFormatException e) {
            CustomToast.error(this, "실력 값을 다시 선택해주세요.");
            setLoading(false);
            return;
        }

        String playerType = (playerTypeGroup.getCheckedRadioButtonId() == R.id.radioPro) ? "선출" : "비선출";
        String playerLevel = null;
        if ("선출".equals(playerType)) {
            playerLevel = playerLevelSpinner.getSelectedItem().toString();
        }
        final String finalPlayerLevel = playerLevel;

        if (selectedImageUri == null) {
            CustomToast.info(this, "프로필 사진을 선택해주세요.");
            setLoading(false);
            return;
        }

        String uid = auth.getCurrentUser().getUid();
        StorageReference imageRef = storageRef.child("profile_images/" + uid + ".jpg");

        // 이미지 리사이즈 & 압축
        byte[] imageBytes;
        try {
            imageBytes = compressImage(selectedImageUri, 720, 80);
        } catch (IOException e) {
            CustomToast.error(this, "이미지 처리 실패: " + e.getMessage());
            setLoading(false);
            return;
        }

        UploadTask uploadTask = imageRef.putBytes(imageBytes);
        uploadTask
                .addOnSuccessListener(taskSnapshot ->
                        imageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                            String imageUrl = uri.toString();
                            saveProfileToFirestore(uid, nickname, age, height, weight, position, skill,
                                    foot, playerType, finalPlayerLevel, intro, imageUrl);
                        }).addOnFailureListener(e -> {
                            CustomToast.error(this, "URL 획득 실패: " + e.getMessage());
                            setLoading(false);
                        })
                )
                .addOnFailureListener(e -> {
                    CustomToast.error(this, "사진 업로드 실패: " + e.getMessage());
                    setLoading(false);
                });
    }

    private void saveProfileToFirestore(String uid, String nickname, int age, int height, int weight,
                                        String position, int skill, String foot, String playerType,
                                        String playerLevel, String intro, String imageUrl) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("username", username);
        profile.put("nickname", nickname);
        profile.put("age", age);
        profile.put("height", height);
        profile.put("weight", weight);
        profile.put("position", position);
        profile.put("skill", skill);
        profile.put("foot", foot);
        profile.put("playerType", playerType);
        if (playerLevel != null) profile.put("playerLevel", playerLevel);
        profile.put("introduction", intro);
        profile.put("myTeam", null);
        if (imageUrl != null) profile.put("profileImageUrl", imageUrl);

        firestore.collection("profiles")
                .document(uid)
                .set(profile)
                .addOnSuccessListener(aVoid -> {
                    CustomToast.success(this, "프로필 저장 완료!");
                    startActivity(new Intent(CreateProfile.this, Home.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    CustomToast.error(this, "프로필 저장 실패: " + e.getMessage());
                    setLoading(false);
                });
    }

    private void setLoading(boolean loading) {
        saveButton.setEnabled(!loading);
        saveButton.setClickable(!loading);
        saveButton.setText(loading ? "저장 중…" : saveButtonOriginalText);
    }

    private byte[] compressImage(Uri uri, int maxSizePx, int quality) throws IOException {
        Bitmap src;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
            src = ImageDecoder.decodeBitmap(source);
        } else {
            src = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
        }

        int w = src.getWidth();
        int h = src.getHeight();
        int longer = Math.max(w, h);
        float scale = longer > maxSizePx ? (float) maxSizePx / longer : 1f;

        Bitmap resized = (scale < 1f)
                ? Bitmap.createScaledBitmap(src, Math.round(w * scale), Math.round(h * scale), true)
                : src;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        resized.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        return baos.toByteArray();
    }
}
