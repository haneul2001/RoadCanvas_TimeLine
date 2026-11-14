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
 * 주행 중에는 기본 내비가 화면 앞에 있으므로, 앱이 뒤로 가도 시스템이 프로세스를 멈추지 않게 포그라운드로 유지한다.
 * - 기록: 여행을 기록하는 동안
 * - 대기: '자동 기록'을 켰을 때. 기록은 하지 않고 기어 변화를 놓치지 않으려고 떠 있는다(위치 저장 없음).
 */
class TripService : Service() {

    private val app get() = application as RoadCanvasApp
    private val recorder get() = app.tripRecorder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_RECORD) {
            ACTION_RECORD -> {
                show("달린 길을 기록하고 있어요")
                recorder?.start()
            }
            ACTION_STANDBY -> show(if (recorder?.state?.value?.recording == true) "달린 길을 기록하고 있어요" else STANDBY_TEXT)
            ACTION_END -> {
                recorder?.stop()
                if (app.autoRecord.value) show(STANDBY_TEXT) else stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        recorder?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun show(text: String) {
        startForeground(NOTIFICATION_ID, notification(text), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    private fun notification(text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "여행 기록", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mapmode)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "trip"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_RECORD = "com.carnect.roadcanvas.RECORD"
        private const val ACTION_STANDBY = "com.carnect.roadcanvas.STANDBY"
        private const val ACTION_END = "com.carnect.roadcanvas.END"
        private const val STANDBY_TEXT = "자동 기록 대기 중 · 주행을 시작하면 기록해요"

        private fun send(context: Context, action: String) =
            context.startForegroundService(Intent(context, TripService::class.java).setAction(action))

        /** 여행 기록 시작. */
        fun start(context: Context) = send(context, ACTION_RECORD)

        /** 자동 기록 대기(기록하지 않음). */
        fun standby(context: Context) = send(context, ACTION_STANDBY)

        /** 여행 기록을 끝낸다. 자동 기록을 켜 두었으면 대기로 돌아가고, 아니면 서비스도 내린다. */
        fun stop(context: Context) {
            val app = context.applicationContext as RoadCanvasApp
            if (app.autoRecord.value) send(context, ACTION_END) else context.stopService(Intent(context, TripService::class.java))
        }

        /** 서비스를 완전히 내린다(자동 기록을 끌 때). */
        fun shutdown(context: Context) = context.stopService(Intent(context, TripService::class.java))
    }
}
