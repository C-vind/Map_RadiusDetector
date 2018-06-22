package com.github.map_radiusdetector;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.support.customtabs.CustomTabsIntent;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Marker;

// Demonstrates customizing the info window and/or its contents.
public class CustomInfoWindowAdapter implements GoogleMap.InfoWindowAdapter {
    // These are both viewgroups containing an ImageView with id "badge" and
    // two TextViews with id "title" and "snippet".
    private Context cont;
    private MapWrapperLayout mapWrapper;
    private GoogleMap map;

    CustomInfoWindowAdapter(Context context, MapWrapperLayout mapWrapperLayout, GoogleMap gMap) {
        cont = context;
        mapWrapper = mapWrapperLayout;
        map = gMap;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View getInfoWindow(Marker marker) {
        if (("9gag Insta").equals(marker.getTitle())) {

            View infoWindow = LayoutInflater.from(cont).inflate(R.layout.custom_info_window, null);

            Button icon = infoWindow.findViewById(R.id.icon);
            icon.setBackground(cont.getDrawable(R.drawable.ic_9gag));
            icon.setOnTouchListener(new CustomOnTouchListener(icon)
            {
                @Override
                protected void onClickConfirmed(View v, Marker marker) {
                    // Perform the action triggered after clicking the button
                    Toast.makeText(cont, "9gag icon is clicked!", Toast.LENGTH_SHORT).show();
                }
            });

            Button insta = infoWindow.findViewById(R.id.insta);
            insta.setBackground(cont.getDrawable(R.drawable.ic_insta));
            insta.setOnTouchListener(new CustomOnTouchListener(insta)
            {
                @Override
                protected void onClickConfirmed(View v, Marker marker) {
                    // Perform the action triggered after clicking the button
                    CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
                    CustomTabsIntent customTabsIntent = builder.build();
                    customTabsIntent.launchUrl(cont, Uri.parse("https://www.instagram.com/9gag"));
                }
            });

            String title = marker.getTitle();
            TextView titleUi = infoWindow.findViewById(R.id.title);
            if (title != null) {
                // Spannable string makes it possible to edit the formatting of the text.
                SpannableString titleText = new SpannableString(title);
                titleText.setSpan(new ForegroundColorSpan(Color.RED), 0, titleText.length(), 0);
                titleUi.setText(titleText);
            } else // title == null
                titleUi.setText("");

            String snippet = marker.getSnippet();
            TextView snippetUi = infoWindow.findViewById(R.id.snippet);
            if (snippet != null) {
                SpannableString snippetText = new SpannableString(snippet);
                snippetText.setSpan(new ForegroundColorSpan(Color.BLUE), 0, snippetText.length(), 0);
                snippetUi.setText(snippetText);
            } else // snippet == null
                snippetUi.setText("");

            // MapWrapperLayout initialization
            mapWrapper.init(map);

            // Set the current marker and infoWindow references to the MapWrapperLayout
            mapWrapper.setMarkerWithInfoWindow(marker, infoWindow);

            return infoWindow;
        } else
            return null;
    }

    @Override
    public View getInfoContents(Marker marker) {
        // This method is not called if getInfoWindow(Marker) does not return null
        return null;
    }
}