package com.example.vinzee.cmsc_628_hw1;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    EditText latitudeEditText, longitudeEditText, latitude2EditText, longitude2EditText, distanceEditText;

    private MyService myService;
    public static Handler myHandler = new Handler();

    private class LocationWork implements Runnable {
        private double latitude1, longitude1, latitude2, longitude2, distance;

        public LocationWork (double latitude1, double longitude1, double latitude2, double longitude2, double distance) {
            this.latitude1 = latitude1;
            this.longitude1 = longitude1;
            this.latitude2 = latitude2;
            this.longitude2 = longitude2;
            this.distance = distance;
        }

        @Override
        public void run() {
            latitudeEditText.setText(Double.valueOf(this.latitude1).toString());
            longitudeEditText.setText(Double.valueOf(this.longitude1).toString());
            latitude2EditText.setText(String.format("%.10f", this.latitude2));
            longitude2EditText.setText(String.format("%.10f", this.longitude2));
            distanceEditText.setText(Double.valueOf(this.distance).toString());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        latitudeEditText = findViewById(R.id.lat);
        longitudeEditText = findViewById(R.id.lng);
        latitude2EditText = findViewById(R.id.lat2);
        longitude2EditText = findViewById(R.id.lng2);
        distanceEditText = findViewById(R.id.distance);

        latitudeEditText.setEnabled(false);
        longitudeEditText.setEnabled(false);
        longitude2EditText.setEnabled(false);
        latitude2EditText.setEnabled(false);
        distanceEditText.setEnabled(false);

        Button button = findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                Intent myIntent = new Intent(MainActivity.this, MyService.class);
                bindService(myIntent, myServiceConnection, BIND_AUTO_CREATE);

                IntentFilter intentFIlter = new IntentFilter();
                intentFIlter.addAction("locationValues");
                LocalBroadcastManager.getInstance(MainActivity.this).registerReceiver(mMessageReceiver, intentFIlter);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 99);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private ServiceConnection myServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder iBinder) {
            myService = ((MyService.MyBinder) iBinder).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, int[] grantResults) {
        if (requestCode == 99) {
            // Request for location permission.
            if (grantResults.length == 2 && (grantResults[0] == PackageManager.PERMISSION_GRANTED || grantResults[1] == PackageManager.PERMISSION_GRANTED)) {
                // Permission has been granted.
                Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Location permission not granted", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            LocationWork locationWork;

            switch(action){
                case "locationValues":
                    locationWork = new LocationWork(
                            intent.getDoubleExtra("latitude1", 0),
                            intent.getDoubleExtra("longitude1", 0),
                            intent.getDoubleExtra("latitude2", 0),
                            intent.getDoubleExtra("longitude2", 0),
                            intent.getDoubleExtra("distance", 0)
                    );

                    myHandler.post(locationWork);

                    break;
                default:
                    Log.d("BroadcastReceiver", "No matching case found for action: " + action);
            }
        }
    };

}
