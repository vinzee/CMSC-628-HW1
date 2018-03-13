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
import android.widget.Toast;

import java.util.Arrays;

import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.StrictMath.sin;

public class MyService extends Service implements LocationListener, SensorEventListener {
    private MyBinder myBinder = new MyBinder();

    public LocationManager locationManager;
    public SensorManager sensorManager;
    public Sensor accelerometer, gyroscope, rotationVectorSensor, magnetometer;
    public static Handler myHandler = new Handler();

    public double latitude1 = 0.0, longitude1 = 0.0, latitude2 = 0.0, longitude2 = 0.0, distance = 0.0, initialLatitude = 0.0, initialLongitude = 0.0;
    static final float NS2S = 1.0f / 1000000000.0f;
    static final float R = 6371000; // meters

    long lastTimestampAccel = 0;
//    long lastTimestampGyro = 0;
    float[] velocity = new float[3];
    float[] position = new float[3];
//    float[] rotation = new float[3];
    float[] rotationVector = new float[16];
    float[] linearAccelerationData, accelData; // , compassData;
    double currentDirection;
    double prevAcceleration = 0.0, prevSpeed = 0.0, prevDistance = 0.0;

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

        AccelWork(float[] accel, long timestamp) {
            this.accel = accel;
            this.timestamp = timestamp;
        }

        @Override
        public void run() {
            if (lastTimestampAccel == 0 || (timestamp - lastTimestampAccel) > 100) {
                float diffTime = (lastTimestampAccel == 0) ? 1 : ((timestamp - lastTimestampAccel) * NS2S);
                Log.d("Calc","diffTime : " + diffTime + " : " + lastTimestampAccel + " : " + timestamp);

                double currentAcceleration = 0.0, currentSpeed = 0.0, currentDistance = 0.0;
                for(int i = 0; i < 3;++i){
                    if((accel[i] < 0.2) && (accel[i] > -0.2)) { accel[i] = 0; } // Mechanical Filtering (remove some noise)

                    velocity[i] += (accel[i] * diffTime);
                    position[i] += (((velocity[i] * diffTime) + ((accel[i] * diffTime * diffTime) / 2)));

                    currentAcceleration += Math.pow(accel[i], 2);
                    currentSpeed += Math.pow(velocity[i], 2);
                    currentDistance += Math.pow(position[i], 2);
                }

                currentAcceleration =  (float) Math.sqrt(currentAcceleration);
                currentSpeed =  (float) Math.sqrt(currentSpeed);
                currentDistance =  (float) Math.sqrt(Math.pow(accel[0], 2) + Math.pow(accel[1], 2) + Math.pow(accel[2], 2));
                currentDistance = currentDistance / 1000;

                Log.d("Calc","L.Accel: " + Arrays.toString(accel) + " $$ " + currentAcceleration);
                Log.d("Calc","Velocity : " + Arrays.toString(velocity) + " $$ " + currentSpeed);
                Log.d("Calc","Distance : " + Arrays.toString(position) + " $$ " + currentDistance);

                lastTimestampAccel = timestamp;

                if(currentAcceleration != prevAcceleration || currentSpeed != prevSpeed || prevDistance != currentDistance) {
                    if (currentDirection != 0 && currentSpeed > 0.2 && initialLatitude != 0.0 && initialLongitude != 0.0) {
                        double initialLatitudeTemp = Math.toRadians(initialLatitude);
                        double initialLongitudeTemp = Math.toRadians(initialLongitude);

                        currentDirection = (float) (Math.PI * currentDirection / 180.0);

                        latitude2 = Math.asin(sin(initialLatitudeTemp) * cos(currentDistance / R) +
                                cos(initialLatitudeTemp) * sin(currentDistance / R) * cos(currentDirection));

                        longitude2 = initialLongitudeTemp
                                + atan2(
                                        sin(currentDirection) * sin(currentDistance / R) * cos(initialLatitudeTemp) ,
                                        cos(currentDistance / R)  - sin(initialLatitudeTemp) * sin(latitude2)
                                );

                        latitude2 = Math.toDegrees(latitude2);
                        longitude2 = Math.toDegrees(longitude2);
                        distance = calculateDistance();

                        Log.d("distance", distance + "");
                    }
                    prevAcceleration = currentAcceleration;
                    prevSpeed = currentSpeed;
                    prevDistance = currentDistance;
                }


                sendLocationToActivity();
            }
        }
    }

//    private class GyroWork implements Runnable {
//        private float[] gyro;
//        private long timestamp;
//        private float estimated_error = 0.053f; // 0.33 / 2*PI;
//
//        GyroWork(float[] gyro, long timestamp) {
//            this.gyro = gyro;
//            this.timestamp = timestamp;
//        }
//
//        @Override
//        public void run() {
//            if (lastTimestampGyro == 0 || (timestamp - lastTimestampGyro) > 100) {
//                float diffTime = (lastTimestampGyro == 0) ? 1 : ((timestamp - lastTimestampGyro) * NS2S);
//                Log.d("Calc","diffTime : " + diffTime + " : " + lastTimestampGyro + " : " + timestamp);
//
//                for(int i = 0; i < 3;++i){
////                    gyro[i] -= ( gyro[i] * (1 - estimated_error) ); // Mechanical Filtering (remove some noise)
//                    rotation[i] += (gyro[i] * diffTime);
//                }
//
//                Log.d("Calc","A.Accel: " + Arrays.toString(gyro));
//                Log.d("Calc","Rotation : " + Arrays.toString(rotation));
//                sendLocationToActivity();
//
//                lastTimestampGyro = timestamp;
//            }
//        }
//    }

    private class CompassWork implements Runnable {
        @Override
        public void run() {
//          https://stackoverflow.com/questions/17603873/type-rotation-vector-type-orientation-give-different-results-that-too-with-devi

            float[] rotationMatrix = new float[16];
            float[] orientationValues = new float[3];

            SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector);
            SensorManager.getOrientation(rotationMatrix, orientationValues);

            double azimuth = Math.toDegrees(orientationValues[0]);
//            double pitch = Math.toDegrees(orientationValues[1]);
//            double roll = Math.toDegrees(orientationValues[2]);
            currentDirection = (azimuth + 360) % 360;

            Log.d("Calc", "Orientation: " + azimuth + " , currentDirection: " + currentDirection); //  + " , " + pitch + " , " + roll
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
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
//        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
//        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME);
//        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
//        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_GAME);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d("MyService", "Insufficient permissions");
            return;
        }


        boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        if (gpsEnabled) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        } else if (networkEnabled){
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
        } else {
            Toast.makeText(this, "GPS and Network providers not enabled", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        locationManager.removeUpdates(this);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        switch (sensorEvent.sensor.getType()) {
//            case Sensor.TYPE_ACCELEROMETER:
//                accelData = sensorEvent.values.clone();
//                Log.d("onSensorChanged", "Linear Accel: " + Arrays.toString(accelData));
////            break;
            case Sensor.TYPE_LINEAR_ACCELERATION:
                linearAccelerationData = sensorEvent.values.clone();
                AccelWork accelWork = new AccelWork(linearAccelerationData, sensorEvent.timestamp);
                myHandler.post(accelWork);
//                Log.d("onSensorChanged", "Linear Accel: " + Arrays.toString(linearAccelerationData));
                break;
//            case Sensor.TYPE_MAGNETIC_FIELD:
//                compassData = sensorEvent.values.clone();
////                Log.d("onSensorChanged", "Compass: " + Arrays.toString(compassData));
//                break;
//            case Sensor.TYPE_GYROSCOPE:
//                float[] gyroscopeData = sensorEvent.values.clone();
//                GyroWork gyroWork = new GyroWork(gyroscopeData, sensorEvent.timestamp);
//                myHandler.post(gyroWork);
////                Log.d("onSensorChanged", "Raw Gyro: " + Arrays.toString(gyroscopeData));
//                break;
            case Sensor.TYPE_ROTATION_VECTOR:
                rotationVector = sensorEvent.values.clone();
//                Log.d("onSensorChanged", "rotationVector: " + Arrays.toString(rotationVector));
                break;
        }

        if(rotationVector != null && linearAccelerationData != null){
            myHandler.post(new CompassWork());
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onLocationChanged(Location location) {
        if (location.getLatitude() != 0.0 && location.getLongitude() != 0.0) {
            latitude1 = location.getLatitude();
            longitude1 = location.getLongitude();
//        Log.d("onLocationChanged", latitude1 + " : " + longitude1);

            if (initialLatitude == 0.0 && initialLongitude == 0.0){
                initialLatitude = latitude1;
                initialLongitude = longitude1;
            }

            sendLocationToActivity();
        }
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

        intent.putExtra("latitude1", latitude1);
        intent.putExtra("longitude1", longitude1);
        intent.putExtra("latitude2", latitude2);
        intent.putExtra("longitude2", longitude2);
        intent.putExtra("distance", distance);
        intent.putExtra("velocity", velocity);
        intent.putExtra("position", position);

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }


    public double calculateDistance () {
        double dlon = Math.toRadians(longitude2) - Math.toRadians(longitude1);
        double dlat = Math.toRadians(latitude2) - Math.toRadians(latitude1);

        double a = Math.pow(Math.sin(dlat/2), 2) + Math.cos(latitude1) * Math.cos(latitude2) * Math.pow((Math.sin(dlon/2)),2);
        double c = (2 * Math.atan2( Math.sqrt(a), Math.sqrt(1-a) ));
        double d = R * c; // (where R is the radius of the Earth);

        return d;
    }
}
