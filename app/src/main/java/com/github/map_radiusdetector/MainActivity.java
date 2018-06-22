package com.github.map_radiusdetector;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.location.Geocoder;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;
import com.google.android.gms.maps.GoogleMap.OnMyLocationButtonClickListener;
import com.google.android.gms.maps.GoogleMap.OnMyLocationClickListener;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

public class MainActivity extends AppCompatActivity
        implements OnMyLocationButtonClickListener, OnMyLocationClickListener, OnMarkerClickListener,
        OnMapClickListener, SeekBar.OnSeekBarChangeListener, OnMapReadyCallback {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final LatLng DEFAULT_LOCATION = new LatLng(-6.282989, 107.167594);
    private static final float DEFAULT_ZOOM = 15.5f;
    private static final double RADIUS_OF_EARTH_METERS = 6371009;

    private static final int DEFAULT_RADIUS_METERS = 500;
    private static final int MAX_RADIUS = 750;

    private static final String LATITUDE = "latitude";
    private static final String LONGITUDE = "longitude";
    private static final String ZOOM = "zoom";
    private static final String BEARING = "bearing";
    private static final String TILT = "tilt";
    private static final String MAPTYPE = "mapType";
    private static final String STATE_NAME ="mapCameraState";

    private static final String cLATITUDE = "clatitude";
    private static final String cLONGITUDE = "clongitude";
    private static final String cRADIUS = "cradius";

    // The desired interval for location updates. Inexact. Updates may be more or less frequent.
    private static final long UPDATE_INTERVAL = 500;

    // The fastest rate for active location updates. Exact. Updates will never be more frequent than this value.
    private static final long FASTEST_UPDATE_INTERVAL = UPDATE_INTERVAL / 5;

    // Request code for location permission request.
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    // Used as a flag to indicate whether the GPS is turned on for the first time or not.
    private static boolean gpsFirstOn = true;

    private GoogleMap map;

    // The entry point to the Fused Location Provider.
    private FusedLocationProviderClient fusedLocationProvider;

    // Stores parameters for requests to the FusedLocationProviderApi.
    private LocationRequest locationRequest;

    // Callback for Location events.
    private LocationCallback locationCallback;

    /* The geographical location where the device is currently located. That is, the last-known
       location retrieved by the Fused Location Provider. */
    private Location lastKnownLocation;

    private SharedPreferences mapStatePref;

    private MapCircle mCircle;

    // Receiver registered with this activity to get the response from FetchAddressIntentService.
    private ResultReceiver resultReceiver;

    // Keeps track of the selected marker.
    private Marker selectedMarker;

    private Marker marker;

    private SeekBar radiusBar;

    private TextView radiusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fusedLocationProvider = LocationServices.getFusedLocationProviderClient(this);
        mapStatePref = getSharedPreferences(STATE_NAME, Context.MODE_PRIVATE);
        resultReceiver = new ResultReceiver(new Handler()) {
            // Receives data sent from FetchAddressIntentService
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                // Display the address string or an error message sent from the intent service.
                String addressOutput = resultData.getString(Constants.RESULT_DATA_KEY);
                Toast.makeText(getApplicationContext(), addressOutput, Toast.LENGTH_SHORT).show();
            }
        };

        lastKnownLocation = new Location("");
        lastKnownLocation.setLatitude(DEFAULT_LOCATION.latitude);
        lastKnownLocation.setLongitude(DEFAULT_LOCATION.longitude);

        radiusBar = findViewById(R.id.radiusSeekBar);
        radiusBar.setMax(MAX_RADIUS);
        radiusBar.setProgress(250);
        radiusText = findViewById(R.id.radiusText);
        radiusText.setText(Integer.toString(DEFAULT_RADIUS_METERS) + "m");

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null)
                    return;

                for (Location locationUpdate : locationResult.getLocations()) {
                    lastKnownLocation = locationUpdate;
                    mCircle.changeCenter(locationUpdate);

                    if (gpsFirstOn) {
                        gpsFirstOn = false;
                        getDeviceLocation(true);
                    }

                    calculateDistance();
                }
            }
        };

        locationRequest = new LocationRequest();
        locationRequest.setInterval(UPDATE_INTERVAL);
        locationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap gMap) {
        map = gMap;

        MapWrapperLayout mapWrapper = findViewById(R.id.map_layout);

        // Setting an info window adapter to change the both the contents and look of the info window.
        map.setInfoWindowAdapter(new CustomInfoWindowAdapter(this, mapWrapper, map));

        // Set listener for map click event.
        map.setOnMapClickListener(this);

        // Set listener for marker click event.
        map.setOnMarkerClickListener(this);

        map.setOnMyLocationButtonClickListener(this);
        map.setOnMyLocationClickListener(this);
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_LOCATION, DEFAULT_ZOOM));

        radiusBar.setOnSeekBarChangeListener(this);

        mCircle = new MapCircle(map, DEFAULT_LOCATION, DEFAULT_RADIUS_METERS);

        marker = map.addMarker(new MarkerOptions()
                .position(new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude()))
                .title("9gag Insta")
                .snippet("This is 9gag")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));

        if (!checkPermission())
            requestPermission();

        getDeviceLocation(false);
    }

    // Saves the state of the map when the activity is paused.
    @Override
    protected void onPause() {
        super.onPause();

        SharedPreferences.Editor editor = mapStatePref.edit();
        CameraPosition position = map.getCameraPosition();

        editor.putFloat(LATITUDE, (float) position.target.latitude);
        editor.putFloat(LONGITUDE, (float) position.target.longitude);
        editor.putFloat(ZOOM, position.zoom);
        editor.putFloat(TILT, position.tilt);
        editor.putFloat(BEARING, position.bearing);
        editor.putInt(MAPTYPE, map.getMapType());

        editor.putFloat(cLATITUDE, (float) lastKnownLocation.getLatitude());
        editor.putFloat(cLONGITUDE, (float) lastKnownLocation.getLongitude());
        editor.putInt(cRADIUS, radiusBar.getProgress());

        editor.apply();

        // Line below is used if the app is expected to stop updating location when the activity is paused
        /* fusedLocationProvider.removeLocationUpdates(locationCallback); */
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (checkPermission())
            fusedLocationProvider.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());
    }

    @Override
    public boolean onMyLocationButtonClick() {
        getDeviceLocation(true);

        // Return true to consume the event and the default behavior of the button
        return true;
    }

    @Override
    public void onMyLocationClick(@NonNull Location location) {
        // Determine whether a Geocoder is available.
        if (!Geocoder.isPresent())
            showSnackbar(R.string.no_geocoder_available, Snackbar.LENGTH_LONG, 0, null);
        else // Geocoder.isPresent()
            showAddress();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.i(TAG, "onRequestPermissionResult");
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length <= 0)
                // User interaction was interrupted, the permission request is cancelled and receive empty arrays.
                Log.i(TAG, "User interaction was cancelled.");
            else // grantResults.length > 0
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    getDeviceLocation(false);
                else // grantResults[0] != PackageManager.PERMISSION_GRANTED
                    // Provide an additional rationale to the user.
                    showSnackbar(R.string.permission_rationale, Snackbar.LENGTH_INDEFINITE, android.R.string.ok,
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    requestPermission();
                                }
                            });
        }
    }

    // Get the best and most recent location of the device, then positions the map's camera.
    private void getDeviceLocation(final boolean MyLocation) {
        if (!MyLocation)
            RetrieveMapState();

        if (checkPermission()) {
            if (map != null)
                // Enable the my location layer if the fine location permission has been granted.
                map.setMyLocationEnabled(true);

            final Task<Location> locationResult = fusedLocationProvider.getLastLocation();
            locationResult.addOnCompleteListener(this, new OnCompleteListener<Location>() {
                @Override
                public void onComplete(@NonNull Task<Location> task) {
                    if (task.isSuccessful() && task.getResult() != null)
                        lastKnownLocation = task.getResult();
                    else { // !task.isSuccessful() || task.getResult() == null
                        Log.w(TAG, "getLastLocation:exception", task.getException());

                        // Show no location detected message
                        showSnackbar(R.string.no_location_detected, Snackbar.LENGTH_LONG, 0, null);
                    }
                }
            });
        } else // !checkPermission()
            Log.d(TAG, "Current location is null. Permission Denied.");

        if (MyLocation) {
            // Set the map's camera position to the current location of the device.
            LatLng location = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(location, DEFAULT_ZOOM));

            showMarker();
        }
    }

    // Retrieve the state of the map and positions the map's camera.
    private void RetrieveMapState() {
        double latitude = mapStatePref.getFloat(LATITUDE, 0);

        if (latitude != 0) {
            double longitude = mapStatePref.getFloat(LONGITUDE, 0);
            LatLng target = new LatLng(latitude, longitude);

            float zoom = mapStatePref.getFloat(ZOOM, 0);
            float bearing = mapStatePref.getFloat(BEARING, 0);
            float tilt = mapStatePref.getFloat(TILT, 0);

            CameraPosition position = new CameraPosition(target, zoom, tilt, bearing);
            CameraUpdate update = CameraUpdateFactory.newCameraPosition(position);
            map.moveCamera(update);
            map.setMapType(mapStatePref.getInt(MAPTYPE, GoogleMap.MAP_TYPE_NORMAL));

            lastKnownLocation.setLatitude(mapStatePref.getFloat(cLATITUDE, 0));
            lastKnownLocation.setLongitude(mapStatePref.getFloat(cLONGITUDE, 0));

            mCircle.changeCenter(lastKnownLocation);
            radiusBar.setProgress(mapStatePref.getInt(cRADIUS, 0));

            showMarker();
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (progress == MAX_RADIUS)
            radiusText.setText("1km");
        else // circleRadBar.getProgress() != MAX_RADIUS
            radiusText.setText(Integer.toString(progress + 250) + "m");

        calculateDistance();
        mCircle.changeRadius(radiusBar.getProgress());
    }

    private void showMarker() {
        double radiusMeter = 353.5533905933;

        double radiusAngle = Math.toDegrees(radiusMeter / RADIUS_OF_EARTH_METERS) /
                Math.cos(Math.toRadians(lastKnownLocation.getLatitude()));

        marker.setPosition(new LatLng(lastKnownLocation.getLatitude() - radiusAngle,
                lastKnownLocation.getLongitude() - radiusAngle));
    }

    @Override
    public void onMapClick(final LatLng point) {
        // Any showing info window closes when the map is clicked.
        // Clear the currently selected marker.
        selectedMarker = null;
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        // The user has re-tapped on the marker which was already showing an info window.
        if (marker.equals(selectedMarker)) {
            // The showing info window has already been closed,
            // that's the first thing to happen when any marker is clicked.
            // Return true to indicate user has consumed the event and the default behavior
            // is not expected to occur (which is for the camera to move such that the
            // marker is centered and for the marker's info window to open, if it has one).
            selectedMarker = null;
            return true;
        }

        selectedMarker = marker;

        // Return false to indicate that user has not consumed the event
        // and that it is expected for the default behavior to occur.
        return false;
    }

    private void calculateDistance() {
        double latiAngle = marker.getPosition().latitude - lastKnownLocation.getLatitude();
        double longiAngle = marker.getPosition().longitude - lastKnownLocation.getLongitude();

        double latiMeter = Math.toRadians(latiAngle * Math.cos(Math.toRadians(lastKnownLocation.getLatitude()))) *
                RADIUS_OF_EARTH_METERS;
        double longiMeter = Math.toRadians(longiAngle * Math.cos(Math.toRadians(lastKnownLocation.getLatitude()))) *
                RADIUS_OF_EARTH_METERS;

        double markerDistance = latiMeter * latiMeter + longiMeter * longiMeter;
        int circleDistance = (radiusBar.getProgress() + 250) * (radiusBar.getProgress() + 250);

        if (markerDistance <= circleDistance)
            showNotification();
    }

    private void showNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "channelId");

        builder.setVibrate(new long[] {0, 100, 50, 100} );

        builder.setSound(Settings.System.DEFAULT_NOTIFICATION_URI);

        // Set the intent that will fire when the user taps the notification.
        builder.setContentIntent(pendingIntent);

        // Set the notification to auto-cancel. This means that the notification will disappear
        // after the user taps it, rather than remaining until it's explicitly dismissed.
        builder.setAutoCancel(true);

        // Set the icon that will appear in the notification bar.
        // This icon also appears in the lower right hand corner of the notification itself.
        builder.setSmallIcon(R.drawable.ic_notif);

        // Set the large icon, which appears on the left of the notification.
        builder.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_stat_notif));

        // Set the text of the notification.
        builder.setContentTitle("Wow, you're nearby!");
        builder.setContentText("Go Fun The World");
        builder.setSubText("Tap to know more about it");

        // Send the notification. This will immediately display the notification icon in the notification bar.
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (notificationManager != null)
            notificationManager.notify(1, builder.build());
    }

    // Creates an intent, adds location data to it as an extra, and starts the intent service for fetching an address.
    private void showAddress() {
        // Create an intent for passing to the intent service responsible for fetching the address.
        Intent intent = new Intent(this, FetchAddressIntentService.class);

        // Pass the result receiver as an extra to the service.
        intent.putExtra(Constants.RECEIVER, resultReceiver);

        // Pass the location data as an extra to the service.
        intent.putExtra(Constants.LOCATION_DATA_EXTRA, lastKnownLocation);

        /* Start the service. If the service isn't already running, it is instantiated and started
        (creating a process for it if needed); if it is running then it remains running.
        The service kills itself automatically once all intents are processed. */
        startService(intent);
    }

    private void showSnackbar(int textStringId, int length, int actionStringId, View.OnClickListener listener) {
        Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content), textStringId, length);

        if (listener != null)
            snackbar.setAction(actionStringId, listener);

        snackbar.show();
    }

    // Request location permission, to get the location of the device.
    private void requestPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
    }

    // Check whether the location permission has been granted or not
    private boolean checkPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        // Don't do anything here. Needed as part of OnSeekBarChangeListener implementation.
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        // Don't do anything here. Needed as part of OnSeekBarChangeListener implementation.
    }
}
