package com.github.yasushi.hansel;

import android.Manifest;
import android.arch.persistence.room.Room;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 11;
    private static final int REQUEST_VIDEO_CAPTURE = 1;

    private TripDatabase tripDB;
    private BreadcrumbDatabase breadDB;

    private String uuid;
    private boolean isBound = false;
    private GeolocationService service;
    private Trip cTrip;

    private GeolocationReceiver geolocationReceiver;
    private NetworkConnectionReceiver networkReceiver;

    private TextView uuidTextView;
    private Button recordingButton;
    private Button recordVideoButton;

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

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

    class Upload implements Runnable {
        @Override
        public void run() {
            // get the list of trips
            List<Trip> trips = tripDB.tripDao().selectAll();
            List<Breadcrumb> breadcrumbs = breadDB.breadcrumbDao().selectAll();

            for(Trip t : trips) {
                t.upload(tripDB);
            }

            for(Breadcrumb b : breadcrumbs) {
                b.uploadAndClear(breadDB);
            }
        }
    }

    class TripInserter implements Runnable {

        private Trip trip;

        TripInserter(Trip t) {
            trip = t;
        }

        @Override
        public void run() {
           tripDB.tripDao().insert(trip);
        }
    }

    private final BroadcastReceiver localReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "local Receiver, on Receive");
            boolean hasWifi = intent.getBooleanExtra(NetworkConnectionReceiver.getEXTRA_IS_WIFI(), false);
            if (hasWifi) {
                // run in a different thread
                new Thread(new Upload()).start();
            } else {
                Log.d(TAG, "no wifi");
            }
        }
    };

    /*
    Life cycle stuff
     */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate");

        geolocationReceiver = new GeolocationReceiver();
        networkReceiver = new NetworkConnectionReceiver();
        setContentView(R.layout.activity_main);

        recordingButton = findViewById(R.id.startPauseButton);
        uuidTextView = findViewById(R.id.uuidTextView);
        recordVideoButton = findViewById(R.id.recordVideoButton);

        this.uuid = Utilities.getUUID(this);
        Breadcrumb.uuid = this.uuid;
        uuidTextView.setText(uuid);

        tripDB = Room.databaseBuilder(this, TripDatabase.class, "tripDatabase").build();
        breadDB = Room.databaseBuilder(this, BreadcrumbDatabase.class, "breadDatabase").build();

        // weird situation, requesting is true, but has not permission (user took it off)
        boolean isRequesting = Utilities.requestingLocationUpdates(this);

        if(isRequesting) {
            if (!Utilities.checkPermissions(this)) {
                requestPermissions();
            }
        }

        int permission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }

        // TODO: is this redundant to change button state in onResume as well?
        // this comes from the fact that when it requests permission, it comes back via onResume
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
                    changeButtonState(false);
                }else{
                    if(!Utilities.checkPermissions(MainActivity.this)){
                        requestPermissions();
                    } else {
                        service.requestLocationUpdates();
                        changeButtonState(true);
                    }
                }

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

        boolean isRequesting = Utilities.requestingLocationUpdates(MainActivity.this);
        changeButtonState(isRequesting);

        LocalBroadcastManager.getInstance(this).registerReceiver(geolocationReceiver,
                new IntentFilter(GeolocationService.ACTION_BROADCAST));
        registerReceiver(networkReceiver, new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"));
        LocalBroadcastManager.getInstance(this).registerReceiver(localReceiver,
                new IntentFilter(NetworkConnectionReceiver.getNOTIFY_NETWORK_CHANGE()));
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(localReceiver);
        unregisterReceiver(networkReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(geolocationReceiver);
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
        Log.d(TAG, "onRequestPermissionResult");
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length <= 0) {
                // If user interaction was interrupted, the permission request is cancelled and you
                // receive empty arrays.
                Log.d(TAG, "User interaction was cancelled.");
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission was granted.
                Log.d(TAG, "user granted Permission");
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
        if (requestCode == REQUEST_VIDEO_CAPTURE){
            service.changeFrequency(GeolocationService.LOCATION_INTERVALS.SLOW);
            if(resultCode == RESULT_OK) {
                Uri videoUri = intent.getData(); // location
                if (videoUri.getScheme().compareTo("content")==0) {
                    Cursor cursor = getContentResolver().query(videoUri, null, null, null, null);
                    if (cursor.moveToFirst()) {
                        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);//Instead of "MediaStore.Images.Media.DATA" can be used "_data"
                        Uri filePathUri = Uri.parse(cursor.getString(column_index));
                        //String file_name = filePathUri.getLastPathSegment();//.toString();
                        String org_file_path = filePathUri.getPath();
                        File fl = new File(org_file_path);
                        if (fl.exists()) {
                            File cameraFolder = new File(
                                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera");
                            File uidFolder = new File(cameraFolder.getPath() + "/" + this.cTrip.getUserId());
                            if(!uidFolder.exists()){
                                uidFolder.mkdir();
                            }
                            String new_file_path = uidFolder.getPath() + "/" + this.cTrip.getId() + ".mp4";
                            Log.d(TAG, "original video path: " + org_file_path + " tid:" + this.cTrip.getId());
                            File rfl = new File(new_file_path);
                            if (fl.renameTo(rfl)) {
                                Log.d(TAG, "video name was changed." + cameraFolder.getPath());
                                // Remove from album db
                                MainActivity.this.getContentResolver().delete(
                                        MediaStore.Files.getContentUri("external"),
                                        MediaStore.Files.FileColumns.DATA + "=?",
                                        new String[]{org_file_path}
                                );
                                // Add to video album db
                                ContentValues contentValues = new ContentValues();
                                ContentResolver contentResolver = MainActivity.this.getContentResolver();
                                contentValues.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                                contentValues.put("_data", new_file_path);
                                contentResolver.insert(
                                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues);
                            } else {
                                Log.d(TAG, "video name was not changed." + cameraFolder.getPath());
                            }
                            Uri tidUri = FileProvider.getUriForFile(MainActivity.this, BuildConfig.APPLICATION_ID + ".fileprovider", rfl);
                            Snackbar.make(
                                    findViewById(R.id.activity_main),
                                    tidUri.toString(),
                                    Snackbar.LENGTH_LONG
                            ).show();
                            Log.d(TAG, "new video uri path: " + tidUri.toString());
                            Log.d(TAG, "video captured, trying to upload");
                            this.cTrip.setClipUri(tidUri);
                            this.cTrip.stop();

                            new Thread(new TripInserter(this.cTrip)).start();
                        }
                    }
                }
            }
/*            Snackbar.make(
                    findViewById(R.id.activity_main),
                    videoUri.toString(),
                    Snackbar.LENGTH_LONG
                ).show();*/
        }
    }

    private void changeButtonState(boolean isRequesting){
       String buttonText = !isRequesting ? getString(R.string.start_recording_button_text) : getString(R.string.stop_recording_button_text);
       recordingButton.setText(buttonText);
    }
    private void dispatchTakeVideoIntent() {

        // change geolocation frequency to fast
        service.changeFrequency(GeolocationService.LOCATION_INTERVALS.FAST);

        // start new trip
        this.cTrip = new Trip(this.uuid);

        Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        if (takeVideoIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takeVideoIntent, REQUEST_VIDEO_CAPTURE);
        }
    }
}
