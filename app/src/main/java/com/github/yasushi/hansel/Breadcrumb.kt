package com.github.yasushi.hansel

import android.arch.persistence.room.Entity
import android.arch.persistence.room.Ignore
import android.arch.persistence.room.PrimaryKey
import android.location.Location
import com.google.android.gms.tasks.OnSuccessListener
import android.support.v4.content.LocalBroadcastManager
import org.jetbrains.annotations.NotNull
import kotlin.concurrent.thread

@Entity
class Breadcrumb (ts: Long, altitude: Double?, latitude: Double?, longitude: Double?) {
    @PrimaryKey
    @NotNull
    var  ts: Long = ts
    var  altitude: Double? = altitude
    var latitude: Double? = latitude
    var longitude: Double? = longitude

    companion object {
        @JvmStatic lateinit var uuid: String
    }

    @Ignore
    constructor(location: Location) : this(location.time, location.altitude, location.latitude, location.longitude)

    fun uploadAndClear(db: BreadcrumbDatabase){
        Firebase.addBreadcrumb(Breadcrumb.uuid, ts, altitude!!, longitude!!, latitude!!)

        thread {
           db.breadcrumbDao().delete(this.ts)
        }
    }
}