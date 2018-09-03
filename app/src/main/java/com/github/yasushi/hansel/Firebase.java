package com.github.yasushi.hansel;

import android.location.Location;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.view.View;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.Map;

public class Firebase {
    private static FirebaseDatabase db = FirebaseDatabase.getInstance();
    private static DatabaseReference ref = db.getReference();
    private static FirebaseStorage storage = FirebaseStorage.getInstance();
    private static StorageReference storageRef = storage.getReference();

    public static void addBreadcrumb (String uuid, Location location) {
        ref.child("breadcrumbs").child(uuid).child(location.getTime() + "").setValue(new SimpleLocation(location));
    }

    public static String getNewTripId (String uuid) {
        DatabaseReference r = ref.child("trips").child(uuid).push();
        return r.getKey();
    }

    public static void addTrip (String uuid, String tripId, Map<String, Object> trip) {
        ref.child("trips").child(uuid).child(tripId).setValue(trip);
    }

    public static void uploadVideo(Uri uri, final Trip trip, final View v){
       StorageReference r = storageRef.child("clips/" + trip.getId());
       trip.setClipUri(uri);
       UploadTask task = r.putFile(uri);

       task.addOnFailureListener(new OnFailureListener() {
           @Override
           public void onFailure(@NonNull Exception e) {
               Snackbar.make(v, "Failed to upload clip: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
           }
       }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
           @Override
           public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
               trip.addRecord();
               Snackbar.make(v, "Successfully uploaded clip", Snackbar.LENGTH_LONG).show();
           }
       });
    }

    @IgnoreExtraProperties
    private static class SimpleLocation {

        public double latitude;
        public double longitude;
        public double altitude;

        public SimpleLocation() {
        }

        public SimpleLocation(Location location) {
            this.latitude = location.getLatitude();
            this.longitude = location.getLongitude();
            this.altitude = location.getAltitude();
        }

    }


}
