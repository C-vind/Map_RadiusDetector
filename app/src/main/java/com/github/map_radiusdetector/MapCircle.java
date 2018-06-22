package com.github.map_radiusdetector;

import android.graphics.Color;
import android.location.Location;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

class MapCircle {
    private Marker centerMarker;
    private Circle circle;

    MapCircle(GoogleMap map, LatLng center, double radius) {
        int fillColor = Color.HSVToColor(43, new float[]{180, 1, 1});
        int strokeColor = Color.HSVToColor(51, new float[]{120, 1, 1});

        circle = map.addCircle(new CircleOptions()
                .center(center)
                .radius(radius)
                .strokeWidth(20)
                .strokeColor(strokeColor)
                .fillColor(fillColor));

        centerMarker = map.addMarker(new MarkerOptions()
                .position(center));
    }

    void changeCenter(Location location) {
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        circle.setCenter(latLng);
        centerMarker.setPosition(latLng);
    }

    void changeRadius(double Radius) {
        circle.setRadius(Radius + 250);
    }
}
