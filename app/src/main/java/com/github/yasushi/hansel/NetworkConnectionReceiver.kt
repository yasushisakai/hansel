package com.github.yasushi.hansel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.support.v4.content.LocalBroadcastManager
import android.util.Log


class NetworkConnectionReceiver(context: Context?) : BroadcastReceiver() {
    companion object {
        private val TAG: String = "NtwrkCnnctnRcvr"
        @JvmStatic val NOTIFY_NETWORK_CHANGE: String = "NOTIFY_NETWORK_CONNECTION"
        @JvmStatic val EXTRA_IS_CONNECTED: String = "EXTRA_IS_CONNECTED"
    }

    constructor(): this(null)

    private var context: Context? = context

    override fun onReceive(c: Context?, i: Intent?) {
        this.context = c
        val localIntent = Intent(NOTIFY_NETWORK_CHANGE)
        localIntent.putExtra(EXTRA_IS_CONNECTED, isOnline())
        LocalBroadcastManager.getInstance(c!!).sendBroadcast(localIntent)
    }

    private fun isOnline() : Boolean {
        val connMgr = this.context!!.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        var info: NetworkInfo
        try {
            info = connMgr.activeNetworkInfo!!
        } catch (e: NullPointerException){
            return false
        }
        return info.isConnected
    }

}

