package com.example.vinzee.cmsc_628_hw1;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements LocationListener {
    private LocationManager locationManager;
    EditText latitudeEditText, longitudeEditText, latitude2EditText, longitude2EditText, distanceEditText;
    public static Handler myHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        latitudeEditText = (EditText) findViewById(R.id.lat);
        longitudeEditText = (EditText) findViewById(R.id.lng);
        latitude2EditText = (EditText) findViewById(R.id.lat2);
        longitude2EditText = (EditText) findViewById(R.id.lng2);
        distanceEditText = (EditText) findViewById(R.id.distance);
    }

    @Override
    protected void onPause() {
        super.onPause();
        locationManager.removeUpdates(this);
    }

    @Override
    protected void onStop() {
        super.onPause();
        locationManager.removeUpdates(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 99);

            return;
        }

        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 99) {
            // Request for location permission.
            if (grantResults.length == 2 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                // Permission has been granted.
                Toast.makeText(this, "GPS Location permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "GPS Location permission not granted", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class LocationWork implements Runnable {
        private double latitude, longitude;

        public LocationWork (double _latitude, double _longitude) {
            latitude = _latitude;
            longitude = _longitude;
        }

        @Override
        public void run() {
            latitudeEditText.setText(new Double(latitude).toString());
            longitudeEditText.setText(new Double(longitude).toString());

            Toast.makeText(getApplicationContext(), "GPS Location Updated", Toast.LENGTH_SHORT).show();
            Log.d("LocationWork", "Location Updated : " + latitude + " , " + longitude);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        LocationWork locationWork = new LocationWork(latitude, longitude);
        myHandler.post(locationWork);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

//    public int calculateHeight() {
//        dlon = lon2 - lon1
//        dlat = lat2 - lat1
//        a = (sin(dlat/2))^2 + cos(lat1) * cos(lat2) * (sin(dlon/2))^2
//        c = 2 * atan2( sqrt(a), sqrt(1-a) )
//        d = R * c (where R is the radius of the Earth)
//    }
}
