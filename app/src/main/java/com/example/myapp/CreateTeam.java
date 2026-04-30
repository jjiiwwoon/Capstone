// src/main/java/com/example/myapp/CreateTeam.java
package com.example.myapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.ArrayRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;

public class CreateTeam extends AppCompatActivity {

    private ImageView imageTeamLogo;
    private Button btnSearchStadium, btnSelectLogo, btnSaveTeam;
    private EditText editTeamName, editTeamIntro;

    // 주소 안내/표시 텍스트(기존 ID 유지)
    private TextView editStadium;

    // ★ 신규: 팀 주활동 구장 “명칭” 입력란
    private EditText editStadiumName;

    private Spinner spinnerCity, spinnerDistrict, spinnerAgeStart, spinnerAgeEnd;

    private Uri selectedImageUri;

    // ★ 선택된 주소/명칭 보관
    private String selectedAddress = "";
    private String selectedPlaceName = ""; // StadiumSearch에서 넘어온 place name(있으면)

    // Firebase
    private FirebaseStorage storage;
    private StorageReference storageRef;
    private FirebaseFirestore firestore;
    private FirebaseAuth auth;

    // 행정구역
    private Map<String, List<String>> districtMap;

    // ===== Activity Results =====
    private final ActivityResultLauncher<Intent> stadiumSearchLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == RESULT_OK && r.getData() != null) {
                    Intent data = r.getData();

                    // ★ 주소 키 폴백 처리 (address → stadium_address → stadium_name)
                    String addr = data.getStringExtra("address");
                    if (TextUtils.isEmpty(addr)) addr = data.getStringExtra("stadium_address");
                    if (TextUtils.isEmpty(addr)) addr = data.getStringExtra("stadium_name"); // 일부 구현의 최후 폴백

                    // ★ 장소명(있으면 사용)
                    String name = data.getStringExtra("stadium_name");

                    if (!TextUtils.isEmpty(addr)) {
                        selectedAddress = addr;
                        // 안내 문구 → 주소로 교체
                        editStadium.setText(addr);
                        editStadium.setTextColor(0xFF333333);
                        editStadium.setSingleLine(true);
                        editStadium.setEllipsize(android.text.TextUtils.TruncateAt.END);
                        editStadium.setVisibility(View.VISIBLE);
                    } else {
                        CustomToast.warning(this, "주소를 불러오지 못했어요. 다시 시도해 주세요.");
                    }

                    if (!TextUtils.isEmpty(name)) {
                        selectedPlaceName = name;
                        // 명칭 입력칸이 비어있다면 편의상 채워줌(이미 사용자가 적었으면 유지)
                        if (editStadiumName != null && TextUtils.isEmpty(editStadiumName.getText().toString().trim())) {
                            editStadiumName.setText(name);
                        }
                    }
                }
            });

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) openImagePicker();
                else CustomToast.warning(this, "사진 권한이 필요합니다.");
            });

    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == RESULT_OK && r.getData() != null) {
                    selectedImageUri = r.getData().getData();
                    imageTeamLogo.setImageURI(selectedImageUri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.create_team);

        bindViews();
        initFirebase();
        initIntroScrollBlock();
        initDistrictMap();
        setupSpinners();

        btnSelectLogo.setOnClickListener(v -> checkAndRequestPermission());
        btnSearchStadium.setOnClickListener(v -> stadiumSearchLauncher.launch(new Intent(this, StadiumSearch.class)));
        btnSaveTeam.setOnClickListener(v -> saveTeam());
    }

    private void bindViews() {
        imageTeamLogo    = findViewById(R.id.imageTeamLogo);
        btnSelectLogo    = findViewById(R.id.btnSelectLogo);
        btnSaveTeam      = findViewById(R.id.btnSaveTeam);
        editTeamName     = findViewById(R.id.editTeamName);
        editTeamIntro    = findViewById(R.id.editTeamIntro);
        editStadium      = findViewById(R.id.editStadium);       // (기존) 주소 안내/표시
        editStadiumName  = findViewById(R.id.editStadiumName);   // (신규) 명칭 입력
        btnSearchStadium = findViewById(R.id.btnSearchStadium);
        spinnerCity      = findViewById(R.id.spinnerCity);
        spinnerDistrict  = findViewById(R.id.spinnerDistrict);
        spinnerAgeStart  = findViewById(R.id.spinnerAgeStart);
        spinnerAgeEnd    = findViewById(R.id.spinnerAgeEnd);
    }

    private void initFirebase() {
        storage   = FirebaseStorage.getInstance();
        storageRef= storage.getReference();
        firestore = FirebaseFirestore.getInstance();
        auth      = FirebaseAuth.getInstance();
    }

    private void initIntroScrollBlock() {
        editTeamIntro.setVerticalScrollBarEnabled(true);
        editTeamIntro.setMovementMethod(new ScrollingMovementMethod());
        editTeamIntro.setOnTouchListener((v, e) -> {
            if (v.hasFocus()) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
                if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                }
            }
            return false;
        });
    }

    // ===== Permissions / Picker =====
    private void checkAndRequestPermission() {
        final String perm = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(perm);
        } else {
            openImagePicker();
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    // ===== 저장 플로우 (문서 먼저 → 로고 나중) + 즉시표시/프리로드 =====
    private void saveTeam() {
        final String teamName = editTeamName.getText().toString().trim();
        final String intro    = editTeamIntro.getText().toString().trim();

        // ★ 주소/명칭 분리
        final String stadiumAddress = !TextUtils.isEmpty(selectedAddress)
                ? selectedAddress
                : String.valueOf(editStadium.getText()).trim(); // 혹시 모를 기존 값

        // 사용자가 직접 입력한 값 우선, 없으면 StadiumSearch에서 온 place name 보조 사용
        final String homeStadiumName = (editStadiumName != null)
                ? editStadiumName.getText().toString().trim()
                : "";
        final String finalHomeStadiumName = !TextUtils.isEmpty(homeStadiumName) ? homeStadiumName : selectedPlaceName;

        if (TextUtils.isEmpty(teamName)) {
            CustomToast.warning(this, "팀 이름을 입력해 주세요.");
            return;
        }

        final String city     = getItem(spinnerCity);
        final String district = getItem(spinnerDistrict);
        final String ageStart = getItem(spinnerAgeStart);
        final String ageEnd   = getItem(spinnerAgeEnd);
        final String ageRange = joinRange(ageStart, ageEnd);
        final String region   = (city + " " + district).trim();

        Spinner spDay   = findViewById(R.id.spinnerActivityDay);
        Spinner spStart = findViewById(R.id.spinnerTimeStart);
        Spinner spEnd   = findViewById(R.id.spinnerTimeEnd);
        final String activityDay = sel(spDay, "요일");
        final String timeStart   = sel(spStart, "시작");
        final String timeEnd     = sel(spEnd, "종료");

        final String uid = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : "";
        if (TextUtils.isEmpty(uid)) {
            CustomToast.error(this, "로그인이 필요합니다.");
            return;
        }

        firestore.collection("profiles").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String existingTeamId = doc.getString("myTeam");
                    if (!TextUtils.isEmpty(existingTeamId)) {
                        CustomToast.info(this, "이미 다른 팀에 소속되어 있습니다.");
                        return;
                    }

                    Long mySkill = doc.getLong("skill");
                    long initSkill = (mySkill != null) ? mySkill : 0L;

                    Map<String, Object> team = new HashMap<>();
                    team.put("teamName", teamName);
                    team.put("region", region);
                    team.put("ageRange", ageRange);
                    team.put("intro", intro);

                    // ★ 스키마: 주소(stadium) / 팀 주활동 구장 명칭(homeStadiumName)
                    if (!TextUtils.isEmpty(stadiumAddress))         team.put("stadium", stadiumAddress);           // 주소(기존 유지)
                    if (!TextUtils.isEmpty(finalHomeStadiumName))   team.put("homeStadiumName", finalHomeStadiumName); // ✅ 새 필드명

                    team.put("captainUID", uid);
                    team.put("viceCaptainUID", "");
                    team.put("members", Collections.singletonList(uid));
                    if (!TextUtils.isEmpty(activityDay)) team.put("activityDay", activityDay);
                    if (!TextUtils.isEmpty(timeStart))   team.put("timeStart", timeStart);
                    if (!TextUtils.isEmpty(timeEnd))     team.put("timeEnd", timeEnd);

                    team.put("skillAverage", (int) initSkill);
                    team.put("updateAt", Timestamp.now());

                    // (선택) 집계용
                    team.put("skillSum", initSkill);
                    team.put("memberCount", 1L);

                    // 로고 상태
                    team.put("logoStatus", (selectedImageUri != null) ? "uploading" : "none");
                    team.put("logoUpdatedAt", null);

                    firestore.collection("teams").add(team)
                            .addOnSuccessListener(ref -> {
                                String teamId = ref.getId();

                                // 낙관적 즉시표시
                                if (selectedImageUri != null) {
                                    TempImageCache.put(teamId, selectedImageUri);
                                }

                                // 내 프로필에 팀 연결
                                firestore.collection("profiles").document(uid)
                                        .update("myTeam", teamId)
                                        .addOnSuccessListener(v -> {
                                            CustomToast.success(this, "팀이 생성되었습니다!");
                                            finish();

                                            // 로고 업로드
                                            if (selectedImageUri != null) {
                                                byte[] bytes = compressImage(selectedImageUri, 1080, 1080, 80);
                                                if (bytes != null) {
                                                    StorageReference imageRef = storageRef.child(
                                                            "team_logos/" + teamName + "_" + System.currentTimeMillis() + ".jpg"
                                                    );
                                                    imageRef.putBytes(bytes)
                                                            .continueWithTask(t -> imageRef.getDownloadUrl())
                                                            .addOnSuccessListener(uri -> {
                                                                Map<String, Object> upd = new HashMap<>();
                                                                upd.put("logoUrl", uri.toString());
                                                                upd.put("logoStatus", "ready");
                                                                upd.put("logoUpdatedAt", FieldValue.serverTimestamp());

                                                                // 업로드 직후 프리로드
                                                                Glide.with(getApplicationContext())
                                                                        .load(uri.toString())
                                                                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                                                                        .preload();

                                                                ref.update(upd);
                                                                TempImageCache.remove(ref.getId());
                                                            })
                                                            .addOnFailureListener(e -> {
                                                                ref.update("logoStatus", "failed");
                                                                CustomToast.error(this, "로고 업로드 실패: " + e.getMessage());
                                                            });
                                                } else {
                                                    ref.update("logoStatus", "failed");
                                                    CustomToast.error(this, "이미지 처리 실패");
                                                }
                                            }
                                        })
                                        .addOnFailureListener(e ->
                                                CustomToast.error(this, "프로필 업데이트 실패: " + e.getMessage()));
                            })
                            .addOnFailureListener(e ->
                                    CustomToast.error(this, "팀 저장 실패: " + e.getMessage()));
                })
                .addOnFailureListener(e -> CustomToast.error(this, "사용자 정보 로드 실패"));
    }

    // ===== Spinners =====
    private void setupSpinners() {
        setSpinnerWithHint(spinnerAgeStart, R.array.age_array, "시작나이");
        setSpinnerWithHint(spinnerAgeEnd,   R.array.age_array, "끝나이");

        List<String> cities = arrayToList(R.array.city_array);
        spinnerCity.setAdapter(makeAdapter(cities, "시/도"));

        spinnerCity.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String city = getItem(spinnerCity);
                List<String> districts = districtMap.get(city);
                if (districts == null) districts = new ArrayList<>();
                spinnerDistrict.setAdapter(makeAdapter(districts, "시/구/군"));
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        Spinner spDay   = findViewById(R.id.spinnerActivityDay);
        Spinner spStart = findViewById(R.id.spinnerTimeStart);
        Spinner spEnd   = findViewById(R.id.spinnerTimeEnd);
        spDay.setAdapter(makeAdapter(arrayToList(R.array.day_array),   "요일"));
        spStart.setAdapter(makeAdapter(arrayToList(R.array.time_array), "시작"));
        spEnd.setAdapter(makeAdapter(arrayToList(R.array.time_array),   "종료"));
    }

    // ===== Helpers =====
    private ArrayAdapter<String> makeAdapter(List<String> base, String hint) {
        List<String> list = new ArrayList<>();
        list.add(hint);
        list.addAll(base);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, list);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return ad;
    }

    private void setSpinnerWithHint(Spinner spinner, @ArrayRes int arrayRes, String hint) {
        spinner.setAdapter(makeAdapter(arrayToList(arrayRes), hint));
    }

    private List<String> arrayToList(@ArrayRes int arrayRes) {
        ArrayAdapter<CharSequence> base = ArrayAdapter.createFromResource(this, arrayRes, android.R.layout.simple_spinner_item);
        List<String> list = new ArrayList<>();
        for (int i = 0; i < base.getCount(); i++) list.add(base.getItem(i).toString());
        return list;
    }

    private String getItem(Spinner s) {
        Object o = s.getSelectedItem();
        return o == null ? "" : String.valueOf(o);
    }

    private String sel(Spinner s, String hint) {
        String v = getItem(s);
        return hint.equals(v) ? "" : v;
    }

    private String joinRange(String a, String b) {
        if (a.isEmpty() && b.isEmpty()) return "";
        if (a.isEmpty()) return "~" + b;
        if (b.isEmpty()) return a + "~";
        return a + "~" + b;
    }

    // =========================
    // 이미지 압축(+EXIF 회전/뒤집기 적용)
    // =========================
    private byte[] compressImage(Uri uri, int maxW, int maxH, int quality) {
        try {
            // 1) 원본 크기만 읽고 샘플링 비율 산정
            android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                android.graphics.BitmapFactory.decodeStream(is, null, bounds);
            }

            int inSample = 1;
            int w = bounds.outWidth, h = bounds.outHeight;
            while ((w / inSample) > maxW || (h / inSample) > maxH) inSample *= 2;

            // 2) 실제 디코드
            android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
            opts.inSampleSize = inSample;
            android.graphics.Bitmap bmp;
            try (InputStream is2 = getContentResolver().openInputStream(uri)) {
                bmp = android.graphics.BitmapFactory.decodeStream(is2, null, opts);
            }
            if (bmp == null) return null;

            // 3) EXIF에서 회전/뒤집기 행렬 구하기
            android.graphics.Matrix exifMatrix = getExifMatrixFromUri(uri);

            // 4) 우선 목표 크기로 스케일
            float ratio = Math.min((float) maxW / bmp.getWidth(), (float) maxH / bmp.getHeight());
            int tw = Math.max(1, Math.round(bmp.getWidth() * ratio));
            int th = Math.max(1, Math.round(bmp.getHeight() * ratio));
            android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(bmp, tw, th, true);
            if (scaled != bmp) bmp.recycle();

            // 5) EXIF 회전/뒤집기 실제 적용
            android.graphics.Bitmap rotated = android.graphics.Bitmap.createBitmap(
                    scaled, 0, 0, scaled.getWidth(), scaled.getHeight(), exifMatrix, true);
            if (rotated != scaled) scaled.recycle();

            // 6) JPEG 압축 → byte[]
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            rotated.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, bos);
            rotated.recycle();
            return bos.toByteArray();

        } catch (Exception e) {
            return null;
        }
    }

    // EXIF ORIENTATION을 읽어 실제 회전/미러를 적용할 Matrix 반환
    private android.graphics.Matrix getExifMatrixFromUri(Uri uri) {
        android.graphics.Matrix m = new android.graphics.Matrix();
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            // API 24+ 에서 지원 (하위는 그냥 0도 처리)
            androidx.exifinterface.media.ExifInterface exif =
                    new androidx.exifinterface.media.ExifInterface(is);
            int orientation = exif.getAttributeInt(
                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL);

            switch (orientation) {
                case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90:
                    m.postRotate(90);
                    break;
                case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180:
                    m.postRotate(180);
                    break;
                case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270:
                    m.postRotate(270);
                    break;
                case androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    m.postScale(-1, 1);
                    break;
                case androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    m.postScale(1, -1);
                    break;
                case androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSPOSE: // flip + rotate
                    m.postScale(-1, 1);
                    m.postRotate(270);
                    break;
                case androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSVERSE:
                    m.postScale(-1, 1);
                    m.postRotate(90);
                    break;
                default:
                    // ORIENTATION_NORMAL: no-op
                    break;
            }
        } catch (Exception ignore) {
            // 하위 API 또는 읽기 실패 시 0도 유지
        }
        return m;
    }

    // 행정구역 맵 (리소스로 분리 가능)
    private void initDistrictMap() {
        districtMap = new HashMap<>();
        districtMap.put("서울", Arrays.asList("강남구","강동구","강북구","강서구","관악구","광진구","구로구","금천구","노원구","도봉구","동대문구","동작구","마포구","서대문구","서초구","성동구","성북구","송파구","양천구","영등포구","용산구","은평구","종로구","중구","중랑구"));
        districtMap.put("부산", Arrays.asList("강서구","금정구","기장군","남구","동구","동래구","부산진구","북구","사상구","사하구","서구","수영구","연제구","영도구","중구","해운대구"));
        districtMap.put("대구", Arrays.asList("남구","달서구","달성군","동구","북구","서구","수성구","중구"));
        districtMap.put("인천", Arrays.asList("강화군","계양구","남동구","동구","미추홀구","부평구","서구","연수구","옹진군","중구"));
        districtMap.put("광주", Arrays.asList("광산구","남구","동구","북구","서구"));
        districtMap.put("대전", Arrays.asList("대덕구","동구","서구","유성구","중구"));
        districtMap.put("울산", Arrays.asList("남구","동구","북구","중구","울주군"));
        districtMap.put("세종", Arrays.asList("세종시"));
        districtMap.put("경기도", Arrays.asList("가평군","고양시","과천시","광명시","광주시","구리시","군포시","김포시","남양주시","동두천시","부천시","성남시","수원시","시흥시","안산시","안성시","안양시","양주시","양평군","여주시","연천군","오산시","용인시","의왕시","의정부시","이천시","파주시","평택시","포천시","하남시","화성시"));
        districtMap.put("강원도", Arrays.asList("강릉시","고성군","동해시","삼척시","속초시","양구군","양양군","영월군","원주시","인제군","정선군","철원군","춘천시","태백시","평창군","홍천군","화천군","횡성군"));
        districtMap.put("충북", Arrays.asList("괴산군","단양군","보은군","영동군","옥천군","음성군","제천시","증평군","진천군","청주시","충주시"));
        districtMap.put("충남", Arrays.asList("계룡시","공주시","금산군","논산시","당진시","보령시","부여군","서산시","서천군","아산시","예산군","천안시","청양군","태안군","홍성군"));
        districtMap.put("전북", Arrays.asList("고창군","군산시","김제시","남원시","무주군","부안군","순창군","완주군","익산시","임실군","장수군","전주시","정읍시","진안군"));
        districtMap.put("전남", Arrays.asList("강진군","고흥군","곡성군","광양시","구례군","나주시","담양군","목포시","무안군","보성군","순천시","신안군","여수시","영광군","영암군","완도군","장성군","장흥군","진도군","함평군","해남군","화순군"));
        districtMap.put("경북", Arrays.asList("경산시","경주시","고령군","구미시","군위군","김천시","문경시","봉화군","상주시","성주군","안동시","영덕군","영양군","영주시","영천시","예천군","울릉군","울진군","의성군","청도군","청송군","칠곡군","포항시"));
        districtMap.put("경남", Arrays.asList("거제시","거창군","고성군","김해시","남해군","밀양시","사천시","산청군","양산시","의령군","진주시","창녕군","창원시","통영시","하동군","함안군","함양군","합천군"));
        districtMap.put("제주도", Arrays.asList("서귀포시","제주시"));
    }
}
