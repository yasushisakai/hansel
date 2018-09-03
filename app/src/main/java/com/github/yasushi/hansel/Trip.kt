package com.github.yasushi.hansel

import android.arch.persistence.room.Entity
import android.arch.persistence.room.Ignore
import android.arch.persistence.room.PrimaryKey
import android.os.SystemClock
import android.net.Uri
import org.jetbrains.annotations.NotNull
import java.util.*

@Entity
class Trip (id: String?, userId: String?, clipUri: Uri?, start: Long?, duration: Long?) {
    @PrimaryKey
    @NotNull
    var id = id ?: Firebase.getNewTripId(userId)
    var userId = userId
    var clipUri = clipUri
    var start = start ?: System.currentTimeMillis()

    // don't use currentTimeMillis for both start and end when delta is important
    @Ignore
    var startElapsed = SystemClock.elapsedRealtime()
    var duration = duration

    @Ignore
    constructor(userId: String) : this(null, userId, null, null, null)

    fun toMap(): Map<String, Any> {
        var trip = HashMap<String, Any>()

        trip.put("id", this.id!!)
        trip.put("userId", this.userId!!)
        trip.put("clipUri", UriToStringConverter().uriToString(this.clipUri!!))
        trip.put("start", this.start)
        trip.put("duration", this.duration!!)

        return trip
    }

    private fun stop () {
        this.duration = SystemClock.elapsedRealtime() - this.startElapsed
    }

    fun addRecord(){
        this.stop()
        Firebase.addTrip(this.userId, this.id, this.toMap())
    }

}