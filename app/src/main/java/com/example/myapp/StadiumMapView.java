package com.example.myapp;

import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
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

public class StadiumMapView extends AppCompatActivity implements OnMapReadyCallback {

    private MapView mapView;
    private NaverMap naverMap;
    private Marker marker;
    private String stadiumAddress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.stadium_map_view);

        stadiumAddress = getIntent().getStringExtra("address");
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull NaverMap map) {
        naverMap = map;

        if (stadiumAddress != null && !stadiumAddress.isEmpty()) {
            Geocoder geocoder = new Geocoder(this, Locale.KOREA);
            try {
                List<Address> list = geocoder.getFromLocationName(stadiumAddress, 1);
                if (list != null && !list.isEmpty()) {
                    Address address = list.get(0);
                    LatLng latLng = new LatLng(address.getLatitude(), address.getLongitude());
                    marker = new Marker();
                    marker.setPosition(latLng);
                    marker.setCaptionText(stadiumAddress);
                    marker.setMap(naverMap);
                    naverMap.moveCamera(CameraUpdate.scrollTo(latLng));
                } else {
                    Toast.makeText(this, "위치를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
                }
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "지오코딩 실패", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override protected void onStart() { super.onStart(); mapView.onStart(); }
    @Override protected void onResume() { super.onResume(); mapView.onResume(); }
    @Override protected void onPause() { super.onPause(); mapView.onPause(); }
    @Override protected void onStop() { super.onStop(); mapView.onStop(); }
    @Override protected void onDestroy() { super.onDestroy(); mapView.onDestroy(); }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
}
