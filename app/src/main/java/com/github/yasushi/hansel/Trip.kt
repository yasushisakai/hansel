package com.github.yasushi.hansel

import android.os.SystemClock
import java.util.*

class Trip (id: String?, userId: String?, clipLocalFileName: String?, start: Long?, duration: Long?) {

    private var id = if (id == null) Firebase.getNewTripId(userId) else id
    private var userId = userId
    private var clipLocalFileName = clipLocalFileName
    private var start = if (start == null) System.currentTimeMillis() else start

    // don't use currentTimeMillis for both start and end when delta is important
    private var startElapsed = SystemClock.elapsedRealtime()
    private var duration = duration

    constructor(userId: String) : this(null, userId, null, null, null)

    private fun toMap(): Map<String, Any> {
        var trip = HashMap<String, Any>()

        trip.put("clipLocalFileName", this.clipLocalFileName!!)
        trip.put("start", this.start)
        trip.put("duration", this.duration!!)

        return trip
    }

    private fun stop () {
        this.duration = SystemClock.elapsedRealtime() - this.startElapsed
    }

    fun setClipLocalFileName (clipId: String) {
        this.clipLocalFileName = clipId
    }

    fun getId(): String {
        return this.id
    }

    fun addRecord(){
        this.stop()
        Firebase.addTrip(this.userId, this.id, this.toMap())
    }

}