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

import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.StrictMath.sin;

public class MyService extends Service implements LocationListener, SensorEventListener {
    private MyBinder myBinder = new MyBinder();

    public LocationManager locationManager;
    public SensorManager sensorManager;
    public Sensor accelerometer, rotationVectorSensor;
    public static Handler myHandler = new Handler();

    public double latitude1 = 0.0, longitude1 = 0.0, latitude2 = 0.0, longitude2 = 0.0, distance = 0.0, initialLatitude = 0.0, initialLongitude = 0.0;
    static final float NS2S = 1.0f / 1000000000.0f;
    static final float R = 6371000; // meters

    long lastTimestampAccel = 0;
    float[] velocity = new float[3];
    float[] position = new float[3];
    float[] rotationVector = new float[16];
    float[] linearAccelerationData;
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
                double currentAcceleration = 0.0, currentSpeed = 0.0, currentDistance = 0.0;

                for(int i = 0; i < 3;++i){
                    // Mechanical Filtering (remove some noise)
                    if((accel[i] < 0.2) && (accel[i] > -0.2)) {
                        accel[i] = 0;
                    }

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

                lastTimestampAccel = timestamp;

                if(currentAcceleration != prevAcceleration || currentSpeed != prevSpeed || prevDistance != currentDistance) {
                    if (currentDirection != 0 && currentSpeed > 0.2 && initialLatitude != 0.0 && initialLongitude != 0.0) {
                        double initialLatitudeTemp = Math.toRadians(initialLatitude);
                        double initialLongitudeTemp = Math.toRadians(initialLongitude);

                        currentDirection = (float) (Math.PI * currentDirection / 180.0);

                        latitude2 = Math.asin(
                                sin(initialLatitudeTemp) * cos(currentDistance / R) +
                                cos(initialLatitudeTemp) * sin(currentDistance / R) * cos(currentDirection)
                        );

                        longitude2 = initialLongitudeTemp + atan2(
                                sin(currentDirection) * sin(currentDistance / R) * cos(initialLatitudeTemp) ,
                                cos(currentDistance / R)  - sin(initialLatitudeTemp) * sin(latitude2)
                        );

                        latitude2 = Math.toDegrees(latitude2);
                        longitude2 = Math.toDegrees(longitude2);
                        distance = calculateDistance();

//                        Log.d("distance", distance + "");
                    }
                    prevAcceleration = currentAcceleration;
                    prevSpeed = currentSpeed;
                    prevDistance = currentDistance;
                }


                sendLocationToActivity();
            }
        }
    }

    public double calculateDistance () {
        double dlon = Math.toRadians(longitude2) - Math.toRadians(longitude1);
        double dlat = Math.toRadians(latitude2) - Math.toRadians(latitude1);

        double a = Math.pow(Math.sin(dlat/2), 2) + Math.cos(latitude1) * Math.cos(latitude2) * Math.pow((Math.sin(dlon/2)),2);
        double c = (2 * Math.atan2( Math.sqrt(a), Math.sqrt(1-a) ));
        return R * c; // (where R is the radius of the Earth);
    }

    private class CompassWork implements Runnable {
        @Override
        public void run() {
            float[] rotationMatrix = new float[16];
            float[] orientationValues = new float[3];

            SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector);
            SensorManager.getOrientation(rotationMatrix, orientationValues);

            double azimuth = Math.toDegrees(orientationValues[0]);
            currentDirection = (azimuth + 360) % 360;
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

        if (sensorManager == null){
            throw new Error("sensorManager null pointer exception");
        }
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d("MyService", "Insufficient permissions");
            return;
        }

        boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);

        Location lastKnownLocation = null;

        // check GPS signal first, if not found check for network signal
        if (gpsEnabled) {
            // outdoor
            lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        } else if (networkEnabled) {
            // indoor
            lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        } else {
            Toast.makeText(this, "GPS and Network providers not enabled", Toast.LENGTH_SHORT).show();
        }

        if (lastKnownLocation != null) {
            latitude1 = lastKnownLocation.getLatitude();
            longitude1 = lastKnownLocation.getLongitude();

            latitude2 = lastKnownLocation.getLatitude();
            longitude2 = lastKnownLocation.getLongitude();

            initialLatitude = lastKnownLocation.getLatitude();
            initialLongitude = lastKnownLocation.getLongitude();

            sendLocationToActivity();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        locationManager.removeUpdates(this);
        sensorManager.unregisterListener(this, accelerometer);
        sensorManager.unregisterListener(this, rotationVectorSensor);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        switch (sensorEvent.sensor.getType()) {
            case Sensor.TYPE_LINEAR_ACCELERATION:
                linearAccelerationData = sensorEvent.values.clone();
                myHandler.post(new AccelWork(linearAccelerationData, sensorEvent.timestamp));
                break;
            case Sensor.TYPE_ROTATION_VECTOR:
                rotationVector = sensorEvent.values.clone();
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

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}
