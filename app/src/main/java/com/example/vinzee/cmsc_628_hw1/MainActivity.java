package com.example.vinzee.cmsc_628_hw1;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
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

import java.util.Arrays;

public class MainActivity extends AppCompatActivity implements LocationListener, SensorEventListener {
    EditText latitudeEditText, longitudeEditText, latitude2EditText, longitude2EditText, distanceEditText;

    public LocationManager locationManager;
    public SensorManager sensorManager;
    public Sensor accelerometer, gyroscope;
    public static Handler myHandler = new Handler();
    public float[] mAccelerometerData = null, mMagnetometerData = null, gyroscopeData = null;
    public long lastUpdate;
    float[] lastAccel = new float[] {0.0f, 0.0f, 0.0f};

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

    private class AccelWork implements Runnable {
        private float[] accel;

        public AccelWork(float[] accel){
            this.accel = accel;
        }

        @Override
        public void run() {
            calculateVelocity(accel);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);


        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        latitudeEditText = findViewById(R.id.lat);
        longitudeEditText = findViewById(R.id.lng);
        latitude2EditText = findViewById(R.id.lat2);
        longitude2EditText = findViewById(R.id.lng2);
        distanceEditText = findViewById(R.id.distance);
    }

    @Override
    protected void onPause() {
        super.onPause();
        locationManager.removeUpdates(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        locationManager.removeUpdates(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 99);
            return;
        }

        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL); // SENSOR_DELAY_GAME
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
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

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        switch (sensorEvent.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                mAccelerometerData = sensorEvent.values.clone();

                Log.d("onSensorChanged", "Raw Accel: " + Arrays.toString(mAccelerometerData));

                float gravity[] = new float[3];
                float linear_acceleration[] = new float[3];

                final float alpha = 0.8f;

                // Isolate the force of gravity with the low-pass filter.
                gravity[0] = alpha * gravity[0] + (1 - alpha) * mAccelerometerData[0];
                gravity[1] = alpha * gravity[1] + (1 - alpha) * mAccelerometerData[1];
                gravity[2] = alpha * gravity[2] + (1 - alpha) * mAccelerometerData[2];

                // Remove the gravity contribution with the high-pass filter.
                linear_acceleration[0] = mAccelerometerData[0] - gravity[0];
                linear_acceleration[1] = mAccelerometerData[1] - gravity[1];
                linear_acceleration[2] = mAccelerometerData[2] - gravity[2];

                Log.d("MySensorApp", "Gravity: " + Arrays.toString(gravity));
                Log.d("MySensorApp", "Linear accel: " + Arrays.toString(linear_acceleration));

                AccelWork accelWork = new AccelWork(linear_acceleration);
                myHandler.post(accelWork);

                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                mMagnetometerData = sensorEvent.values.clone();

                Log.d("onSensorChanged", "Raw Gyro: " + Arrays.toString(mMagnetometerData));

                break;
            case Sensor.TYPE_GYROSCOPE:
                gyroscopeData = sensorEvent.values.clone();

                Log.d("onSensorChanged", "Raw Gyro: " + Arrays.toString(gyroscopeData));

                break;
            default:
                return;
        }

        // A rotation matrix is a linear algebra term that translates the sensor data from one coordinate system
        // to another—in this case, from the device's coordinate system to the Earth's coordinate system.
        // That matrix is an array of nine float values, because each point (on all three axes) is expressed as a 3D vector.
        // { Scale X, Skew X, Transform X
        // Skew Y, Scale Y, Transform Y
        // Perspective 0, Perspective 1, Perspective 2 }

        if(mAccelerometerData != null && mMagnetometerData != null){
            float[] rotationMatrix = new float[9];
            boolean rotationOK = SensorManager.getRotationMatrix(rotationMatrix, null, mAccelerometerData, mMagnetometerData);

            Log.d("onSensorChanged", "rotationMatrix: " + Arrays.toString(rotationMatrix) + "rotationOK: " + rotationOK);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

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

    public void calculateVelocity(float[] accel){
        long curTime = System.currentTimeMillis();

        if ((curTime - lastUpdate) > 100) {
            long diffTime = (curTime - lastUpdate);
            float velocity = Math.abs(accel[0] + accel[1] + accel[2] - lastAccel[0] - lastAccel[1] - lastAccel[2]) / diffTime * 10000;
            float distance = (velocity * diffTime) / 1000;  // milliseconds to seconds

            Log.d("calculateVelocity","Velocity : " + velocity);
            Log.d("calculateVelocity","Distance : " + distance);

            lastAccel = accel;
            lastUpdate = curTime;
        }
    }

}
