package com.github.yasushi.hansel;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.IBinder;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 11;
    private static final int REQUEST_VIDEO_CAPTURE = 1;

    private boolean isBound = false;
    private GeolocationService service;

    private GeolocationReceiver receiver;

    private TextView uuidTextView;
    private Button recordingButton;
    private Button recordVideoButton;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            GeolocationService.LocalBinder localBinder = (GeolocationService.LocalBinder) binder;
            service = localBinder.getService();
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            isBound = false;
        }
    };



    /*
    Life cycle stuff
     */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate");

        receiver = new GeolocationReceiver();
        setContentView(R.layout.activity_main);

        recordingButton = findViewById(R.id.startPauseButton);
        uuidTextView = findViewById(R.id.uuidTextView);
        recordVideoButton = findViewById(R.id.recordVideoButton);

        uuidTextView.setText(Utilities.getUUID(this));

        // weird situation, requesting is true, but has not permission (user took put it off)
        boolean isRequesting = Utilities.requestingLocationUpdates(this);

        if(isRequesting) {
            if (!Utilities.checkPermissions(this)) {
                requestPermissions();
            }
        }
        changeButtonState(isRequesting);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");

        recordingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                boolean isRequesting = Utilities.requestingLocationUpdates(MainActivity.this);

                // toggles the requestLocationUpdates
                if(isRequesting) {
                    service.removeLocationUpdates();
                }else{
                    if(!Utilities.checkPermissions(MainActivity.this)){
                        requestPermissions();
                    } else {
                        service.requestLocationUpdates();
                    }
                }

                changeButtonState(isRequesting);
            }
        });

        recordVideoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){
                dispatchTakeVideoIntent();
            }
        });

        // bind the service -> service will cease from being a foreground
        bindService(new Intent(this, GeolocationService.class), serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver,
                new IntentFilter(GeolocationService.ACTION_BROADCAST));
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        if(isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
        super.onStop();
    }

    private void requestPermissions(){
        boolean shouldProvideRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION);

        if(shouldProvideRationale) {
            Snackbar.make(
                    findViewById(R.id.activity_main),
                    R.string.permission_rationale,
                    Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.ok, new View.OnClickListener(){
                            @Override
                            public void onClick(View v) {
                                ActivityCompat.requestPermissions(MainActivity.this,
                                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                        REQUEST_PERMISSIONS_REQUEST_CODE);

                            }
                        }).show();
        } else {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.i(TAG, "onRequestPermissionResult");
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length <= 0) {
                // If user interaction was interrupted, the permission request is cancelled and you
                // receive empty arrays.
                Log.i(TAG, "User interaction was cancelled.");
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission was granted.
                Log.i(TAG, "user granted Permission");
                service.requestLocationUpdates();
            } else {
                // Permission denied.
                changeButtonState(false);
                Snackbar.make(
                        findViewById(R.id.activity_main),
                        R.string.permission_denied_explanation,
                        Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.settings, new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                // Build intent that displays the App settings screen.
                                Intent intent = new Intent();
                                intent.setAction(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                Uri uri = Uri.fromParts("package",
                                        BuildConfig.APPLICATION_ID, null);
                                intent.setData(uri);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                            }
                        })
                        .show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent){
        if (requestCode == REQUEST_VIDEO_CAPTURE && resultCode == RESULT_OK) {
            Uri videoUri = intent.getData(); // location
            Snackbar.make(
                    findViewById(R.id.activity_main),
                    videoUri.toString(),
                    Snackbar.LENGTH_LONG
                ).show();
            Firebase.uploadVideo(videoUri, findViewById(R.id.activity_main));
        }
    }

    private void changeButtonState(boolean isRequesting){
       String buttonText = !isRequesting ? getString(R.string.start_recording_button_text) : getString(R.string.stop_recording_button_text);

       recordingButton.setText(buttonText);

    }
    private void dispatchTakeVideoIntent() {
        Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        if (takeVideoIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takeVideoIntent, REQUEST_VIDEO_CAPTURE);
        }
    }
}
