package com.example.myapp;

import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.MapView;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.Marker;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class StadiumSearch extends AppCompatActivity implements OnMapReadyCallback {

    private MapView mapView;
    private NaverMap naverMap;
    private Marker selectedMarker;
    private Button btnSelectStadium, btnSearch;
    private EditText editSearchKeyword;

    private LatLng selectedLocation;
    private String selectedStadiumName = "선택된 장소";
    private String selectedAddress = ""; // ✅ 주소 별도 보관

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.stadium_search);

        mapView = findViewById(R.id.mapView);
        btnSelectStadium = findViewById(R.id.btnSelectStadium);
        btnSearch = findViewById(R.id.btnSearch);
        editSearchKeyword = findViewById(R.id.editSearchKeyword);

        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        // 장소 선택 완료
        btnSelectStadium.setOnClickListener(v -> {
            Intent resultIntent = new Intent();
            // ✅ 호환 키 모두 세팅
            resultIntent.putExtra("stadiumName", selectedStadiumName);
            resultIntent.putExtra("stadium_name", selectedStadiumName);
            resultIntent.putExtra("stadiumAddress", selectedAddress.isEmpty() ? selectedStadiumName : selectedAddress);
            resultIntent.putExtra("stadium_address", selectedAddress.isEmpty() ? selectedStadiumName : selectedAddress);
            setResult(RESULT_OK, resultIntent);
            finish();
        });

        // 키워드 검색
        btnSearch.setOnClickListener(v -> {
            String keyword = editSearchKeyword.getText().toString().trim();
            if (!keyword.isEmpty()) {
                searchLocationByKeyword(keyword);
            } else {
                Toast.makeText(this, "키워드를 입력해주세요", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onMapReady(@NonNull NaverMap map) {
        naverMap = map;
        naverMap.setOnMapClickListener((pointF, latLng) -> {
            selectedLocation = latLng;
            showMarkerAt(latLng);
            getAddressFromGeocoder(latLng);
        });
    }

    private void showMarkerAt(LatLng latLng) {
        if (selectedMarker != null) selectedMarker.setMap(null);
        selectedMarker = new Marker();
        selectedMarker.setPosition(latLng);
        selectedMarker.setMap(naverMap);
    }

    // 좌표 -> 주소
    private void getAddressFromGeocoder(LatLng latLng) {
        Geocoder geocoder = new Geocoder(this, Locale.KOREA);
        try {
            List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                String fullAddress = address.getAddressLine(0);

                // ✅ 주소/이름 업데이트 (지금은 주소를 이름처럼도 사용)
                selectedAddress = fullAddress;
                selectedStadiumName = fullAddress;

                runOnUiThread(() -> {
                    if (selectedMarker != null) selectedMarker.setCaptionText(fullAddress);
                    Log.d("Geocoder", "주소: " + fullAddress);
                });
            } else {
                runOnUiThread(() ->
                        Toast.makeText(this, "주소를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                );
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "주소 변환 오류", Toast.LENGTH_SHORT).show();
        }
    }

    // 키워드 -> 주소/좌표
    private void searchLocationByKeyword(String keyword) {
        Geocoder geocoder = new Geocoder(this, Locale.KOREA);
        try {
            List<Address> addresses = geocoder.getFromLocationName(keyword, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                LatLng latLng = new LatLng(address.getLatitude(), address.getLongitude());

                selectedLocation = latLng;
                String fullAddress = address.getAddressLine(0);
                selectedAddress = fullAddress;
                selectedStadiumName = fullAddress;

                runOnUiThread(() -> {
                    showMarkerAt(latLng);
                    if (selectedMarker != null) selectedMarker.setCaptionText(fullAddress);
                    naverMap.moveCamera(CameraUpdate.scrollTo(latLng));
                    Toast.makeText(this, "장소를 찾았습니다", Toast.LENGTH_SHORT).show();
                });
            } else {
                Toast.makeText(this, "해당 키워드로 장소를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "검색 오류 발생", Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onStart() { super.onStart(); mapView.onStart(); }
    @Override protected void onResume() { super.onResume(); mapView.onResume(); }
    @Override protected void onPause() { super.onPause(); mapView.onPause(); }
    @Override protected void onStop() { super.onStop(); mapView.onStop(); }
    @Override protected void onDestroy() { super.onDestroy(); mapView.onDestroy(); }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
}
