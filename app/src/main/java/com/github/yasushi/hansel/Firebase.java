package com.github.yasushi.hansel;


import android.location.Location;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.IgnoreExtraProperties;

public class Firebase {

    private static FirebaseDatabase db = FirebaseDatabase.getInstance();
    private static DatabaseReference ref = db.getReference();

    public static void addBreadcrumb (String uuid, Location location) {
        ref.child("breadcrumbs").child(uuid).child(location.getTime() + "").setValue(new SimpleLocation(location));
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
