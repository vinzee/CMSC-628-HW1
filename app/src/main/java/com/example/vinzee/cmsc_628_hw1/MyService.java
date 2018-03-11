package com.example.vinzee.cmsc_628_hw1;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.Arrays;

import static java.lang.Math.asin;
import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.StrictMath.sin;

public class MyService extends Service implements LocationListener, SensorEventListener {
    private MyBinder myBinder = new MyBinder();

    public LocationManager locationManager;
    public SensorManager sensorManager;
    public Sensor accelerometer, gyroscope;
    public static Handler myHandler = new Handler();

    public double latitude1 = 0.0, longitude1 = 0.0, latitude2 = 0.0, longitude2 = 0.0, distance = 0.0, initialLatitude = 0.0, initialLongitude = 0.0;
    static final float NS2S = 1.0f / 1000000000.0f;
    static final float R = 6371000; // meters

    float[] velocity = new float[3];
    float[] position = new float[3];
    float[] rotation = new float[3];

    public MyService() {
    }

    class MyBinder extends Binder {
        MyService getService() {
            return MyService.this;
        }
    }

    private class AccelWork implements Runnable {
        private float[] accel;
        private long timestamp;
        private long lastTimestamp = 0;

        AccelWork(float[] accel, long timestamp) {
            this.accel = accel;
            this.timestamp = timestamp;
        }

        @Override
        public void run() {
            if (lastTimestamp == 0 || (timestamp - lastTimestamp) > 100) {
                float diffTime = (lastTimestamp == 0) ? 1 : ((timestamp - lastTimestamp) * NS2S);
                Log.d("Calc","diffTime : " + diffTime);

                for(int i = 0; i < 3;++i){
                    if((accel[i] < 0.2) && (accel[i] > -0.2)) { accel[i] = 0; } // Mechanical Filtering (remove some noise)

                    velocity[i] += (accel[i] * diffTime);
                    position[i] += (((velocity[i] * diffTime) + ((accel[i] * diffTime * diffTime) / 2)));
                }

                Log.d("Calc","L.Accel: " + Arrays.toString(accel));
                Log.d("Calc","Velocity : " + Arrays.toString(velocity));
                Log.d("Calc","Position : " + Arrays.toString(position));

//                calculateNewLatLong(position);
                sendLocationToActivity();

                lastTimestamp = timestamp;
            }
        }
    }

    private class GyroWork implements Runnable {
        private float[] gyro;
        private long timestamp;
        private long lastTimestamp = 0;
        private float estimated_error = 0.053f; // 0.33 / 2*PI;

        GyroWork(float[] gyro, long timestamp) {
            this.gyro = gyro;
            this.timestamp = timestamp;
        }

        @Override
        public void run() {
            if (lastTimestamp == 0 || (timestamp - lastTimestamp) > 100) {
                float diffTime = (lastTimestamp == 0) ? 1 : ((timestamp - lastTimestamp) * NS2S);
                Log.d("Calc","diffTime : " + diffTime);

                for(int i = 0; i < 3;++i){
                    gyro[i] -= ( gyro[i] * (1 - estimated_error) ); // Mechanical Filtering (remove some noise)
                    rotation[i] += (gyro[i] * diffTime);
                }

                Log.d("Calc","A.Accel: " + Arrays.toString(gyro));
                Log.d("Calc","Rotation : " + Arrays.toString(rotation));
                sendLocationToActivity();

                lastTimestamp = timestamp;
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return myBinder;
    }

    @Override
    public void onCreate() {
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);

//        Magnetometer not present in my android device !
//        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
//        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_GAME);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d("MyService", "Insufficient permissions");
            return;
        }

        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        locationManager.removeUpdates(this);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        switch (sensorEvent.sensor.getType()) {
            case Sensor.TYPE_LINEAR_ACCELERATION:
                float[] linearAccelerationData = sensorEvent.values.clone();
                AccelWork accelWork = new AccelWork(linearAccelerationData, sensorEvent.timestamp);
                myHandler.post(accelWork);
//                Log.d("onSensorChanged", "Linear Accel: " + Arrays.toString(linearAccelerationData));
                break;
            case Sensor.TYPE_GYROSCOPE:
                float[] gyroscopeData = sensorEvent.values.clone();
                GyroWork gyroWork = new GyroWork(gyroscopeData, sensorEvent.timestamp);
                myHandler.post(gyroWork);
//                Log.d("onSensorChanged", "Raw Gyro: " + Arrays.toString(gyroscopeData));
                break;
            default:
                Log.d("onSensorChanged", "Untracked Sensor event: " + sensorEvent.sensor.getType());
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onLocationChanged(Location location) {
        latitude1 = location.getLatitude();
        longitude1 = location.getLongitude();

        if (initialLatitude == 0.0 && initialLongitude == 0.0){
            initialLatitude = latitude1;
            initialLongitude = longitude1;
        }
//        Log.d("onLocationChanged", latitude1 + " : " + longitude1);

        sendLocationToActivity();
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

    private void sendLocationToActivity () {
        Intent intent = new Intent("locationValues");

        calculateDistance();
        intent.putExtra("latitude1", latitude1);
        intent.putExtra("longitude1", longitude1);
        intent.putExtra("latitude2", latitude2);
        intent.putExtra("longitude2", longitude2);
        intent.putExtra("distance", distance);
        intent.putExtra("velocity", velocity);
        intent.putExtra("position", position);

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void calculateNewLatLong (float[] position) {
//        http://www.movable-type.co.uk/scripts/latlong.html
//        https://stackoverflow.com/questions/8123049/calculate-bearing-between-two-locations-lat-long

        double bearing = atan2(position[0], position[1]) * 180.0 / Math.PI;
        if (bearing < 0.0){
            bearing += 360.0;
        } else if (bearing > 360.0) {
            bearing -= 360;
        }

        Log.d("Calc","Lat2,Lng2: " + latitude2 + "," + longitude2);

//            https://social.msdn.microsoft.com/Forums/sqlserver/en-US/c06aaf77-f6f9-420e-bc86-aed873b35a24/adding-distance-to-a-latitude-longitude-point?forum=sqlspatial
//        https://social.msdn.microsoft.com/Forums/sqlserver/en-US/10829142-b6ae-4aca-aec6-4764d191e516/can-i-add-miles-to-a-point-to-get-another-point-?forum=sqlspatial#2512833f-717d-44c3-812f-04d37ad2ec07

        float cosDist = position[0]; //        cos(distance/R)
        float sinDist = position[1]; //        sin(distance/R)

        latitude2 = asin(
                sin(initialLatitude) * cosDist +
                        cos(initialLatitude) * sinDist*cos(bearing)
        );

        longitude2 = initialLongitude + atan2(
                sin(bearing) * sinDist * cos(initialLatitude),
                cosDist - (sin(initialLatitude) * sin(latitude2))
        );

        Log.d("Calc","Lat2,Lng2: " + latitude2 + "," + longitude2);
    }

    public double calculateDistance () {
        double dlon = toRadians(longitude2) - toRadians(longitude1);
        double dlat = toRadians(latitude2) - toRadians(latitude1);

//        double a = ((Math.sin(dlat/2)) ^ 2) + Math.cos(latitude1) * Math.cos(latitude2) * (Math.sin(dlon/2))^2;
//        double c = (2 * Math.atan2( Math.sqrt(a), Math.sqrt(1-a) ));
//        double d = R * c; // (where R is the radius of the Earth);
//
//        return d;
        return 0.0f;
    }

    private double toDegrees (double radians) {
        return (radians * 180) / (Math.PI);
    }

    private double toRadians (double degrees) {
        return (degrees * Math.PI) / (180);
    }
}
