package com.github.yasushi.hansel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityTransitionEvent
import com.google.android.gms.location.ActivityTransitionResult

class ActivityTransitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if(ActivityTransitionResult.hasResult(intent)) {
            val result = ActivityTransitionResult.extractResult(intent)!!
            for(event: ActivityTransitionEvent in result.transitionEvents) {
                Log.i("ActvtyTrnstnRcvr",event.activityType.toString() + "")
            }
        }

    }
}