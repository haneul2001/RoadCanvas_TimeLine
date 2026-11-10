package com.carnect.roadcanvas.trip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.carnect.roadcanvas.MainActivity
import com.carnect.roadcanvas.R
import com.carnect.roadcanvas.RoadCanvasApp

/**
 * 주행 중에는 기본 내비가 화면 앞에 있으므로, 앱이 뒤로 가도 시스템이 프로세스를 멈추지 않게
 * 여행 기록 중에만 포그라운드로 유지한다.
 */
class TripService : Service() {

    private val recorder get() = (application as RoadCanvasApp).tripRecorder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        recorder?.start()
        return START_STICKY
    }

    override fun onDestroy() {
        recorder?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "여행 기록", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mapmode)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("달린 길을 기록하고 있어요")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "trip"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) = context.startForegroundService(Intent(context, TripService::class.java))
        fun stop(context: Context) = context.stopService(Intent(context, TripService::class.java))
    }
}
