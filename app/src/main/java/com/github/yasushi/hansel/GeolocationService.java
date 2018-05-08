package com.github.yasushi.hansel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.UtteranceProgressListener;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

public class GeolocationService extends Service{
    private static final String TAG = GeolocationService.class.getSimpleName();
    private static final String PACKAGE_NAME = "com.github.yasushi.hansel";

    // private static final long LOCATION_INTERVAL = 60 * 60 * 1000;
    private static final long LOCATION_INTERVAL = 60 * 60 * 1000; // only responsible for every 30 min

    // private static final long LOCATION_FASTEST_INTERVAL = 30 * 60 * 1000;
    private static final long LOCATION_FASTEST_INTERVAL = 5 * 60 * 1000; // fastest one min
    public static final String ACTION_BROADCAST = PACKAGE_NAME + ".broadcast";
    public static final String EXTRA_LOCATION = "location";
    public static final String EXTRA_FROM_NOTIFICATION = "from_notification";
    private static final float MINIMUM_DISTANCE_METERS = 10;

    private Location lastLocation = null;
    private FusedLocationProviderClient locClient;
    private LocationCallback locCallback;
    private LocationRequest locRequest;

    private final IBinder binder = new LocalBinder();

    private Handler serviceHandler;

    private static final String CHANNEL_ID = "channel_01";
    private static final int NOTIFICATION_ID = 123;
    private NotificationManager notificationManager;
    private Notification notification;

    @Override
    public void onCreate() {

        locClient = LocationServices.getFusedLocationProviderClient(this);

        locCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                // super.onLocationResult(locationResult);
                // Log.d(TAG, "new Location");
                for(Location location: locationResult.getLocations()) {

                    // if(lastLocation == null || lastLocation.distanceTo(location) > MINIMUM_DISTANCE_METERS) {
                        if(location != null) {
                            lastLocation = location;
                            Log.d(TAG, Utilities.formattedGeolocation(lastLocation));
                            Firebase.addBreadcrumb(Utilities.getUUID(GeolocationService.this), lastLocation);
                        }
                        // return;
                    // }

                }
            }
        };

        createLocationRequest();
        getLastLocation();

        // this is the thread for this service
        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();

        serviceHandler = new Handler(handlerThread.getLooper());

        // notifications to be the foreground service
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        // Oreo needs a notification channel
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Location Updates ForegroundService";
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // return super.onStartCommand(intent, flags, startId);
        Log.i(TAG, "Service started");
        boolean startedFromNotification = intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false);

        if(startedFromNotification) {
            removeLocationUpdates();
            stopSelf(); // why are we stopping?? when remove Location Updates already has stopSelf??
        }

        return Service.START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind");

        // stops being a foreground service because it's bound to the activity
        stopForeground(true);

        return binder;
    }


    @Override
    public void onRebind(Intent intent) {
        Log.i(TAG, "onRebind");
        stopForeground(true);

        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if(Utilities.requestingLocationUpdates(this)) {

            if(Build.VERSION.SDK_INT == Build.VERSION_CODES.O) {
                startForegroundService(new Intent(this, GeolocationService.class));

            } else {
                startForeground(NOTIFICATION_ID, getNotification());
            }
        }
            return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void getLastLocation() {
        try {
            locClient.getLastLocation()
                    .addOnCompleteListener(new OnCompleteListener<Location>() {
                        @Override
                        public void onComplete(@NonNull Task<Location> task) {
                            if(task.isSuccessful() && task.getResult() != null) {
                                // Location location = task.getResult();
                                lastLocation = task.getResult();
                                Log.d(TAG, "got location " + Utilities.formattedGeolocation(lastLocation));
                                Firebase.addBreadcrumb(Utilities.getUUID(GeolocationService.this), lastLocation);
                            } else {
                                Log.w(TAG, "location was null, failed to get last location");
                            }
                        }
                    });
        } catch (SecurityException e) {
            // security should be handled by now...
        }
    }

    private void createLocationRequest(){
        locRequest = new LocationRequest();
        locRequest.setInterval(LOCATION_INTERVAL);
        locRequest.setFastestInterval(LOCATION_FASTEST_INTERVAL);
        locRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    /*
    requestLocationUpdates are called from the MainActivities
     */
    public void requestLocationUpdates(){
        Log.i(TAG, "requesting location updates");
        Utilities.setRequestingLocationUpdates(this, true);
        startService(new Intent(getApplicationContext(), GeolocationService.class));
        try{
            locClient.requestLocationUpdates(locRequest, locCallback, Looper.myLooper());
        } catch (SecurityException e){
            Utilities.setRequestingLocationUpdates(this, false);
            Log.e(TAG, "Location permission lost." + e);
        }
    }

    public void removeLocationUpdates(){
        Log.i(TAG, "removing location updates");
        try{
            locClient.removeLocationUpdates(locCallback);
            Utilities.setRequestingLocationUpdates(this, false);
            stopSelf();
        } catch (SecurityException e){
            Utilities.setRequestingLocationUpdates(this, true);
            // we couldn't remove the location update request
            Log.e(TAG, "Location permission lost." + e);
        }
    }

    public Notification getNotification() {

        Intent intent = new Intent(this, GeolocationService.class);
        String text = Utilities.formattedGeolocation(lastLocation);

        intent.putExtra(EXTRA_FROM_NOTIFICATION, true);

        // this deletes the service
        PendingIntent servicePendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        // this launches the main activity
        PendingIntent activityPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder = new Notification.Builder(this)
                .addAction(R.drawable.ic_launch, "launch app", activityPendingIntent)
                .addAction(R.drawable.ic_cancel, "stop breadcrumbing", servicePendingIntent)
                .setContentText(text)
                .setContentTitle(Utilities.fomattedtime(lastLocation.getTime()))
                .setOngoing(true) //?
                .setTicker(text)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.mipmap.ic_launcher);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ID);
        }

        return builder.build();
    }

    public class LocalBinder extends Binder{
        public GeolocationService getService(){
            return GeolocationService.this;
        }

    }


}
