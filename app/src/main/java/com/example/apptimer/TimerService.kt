package com.example.apptimer

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.CountDownTimer
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class TimerService : Service() {
    private var timer: CountDownTimer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            timer?.cancel()
            stopSelf()
            return START_NOT_STICKY
        }
        val pkg = intent?.getStringExtra(EXTRA_PKG) ?: return START_NOT_STICKY
        val minutes = intent.getIntExtra(EXTRA_MIN, 1)

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Minuterie", NotificationManager.IMPORTANCE_LOW)
        )
        startForeground(1, buildNotif("Démarrage…"))

        timer?.cancel()
        timer = object : CountDownTimer(minutes * 60_000L, 1000) {
            override fun onTick(ms: Long) {
                val s = ms / 1000
                nm.notify(1, buildNotif("Fermeture dans %d:%02d".format(s / 60, s % 60)))
            }
            override fun onFinish() = closeApp(pkg)
        }.start()
        return START_NOT_STICKY
    }

    private fun closeApp(pkg: String) {
        val am = getSystemService(ActivityManager::class.java)
        val svc = CloserAccessibilityService.instance
        if (svc != null) {
            // Forçage de l'arrêt via les réglages (accessibilité), puis nettoyage
            svc.forceStop(pkg)
            Handler(Looper.getMainLooper()).postDelayed({
                am.killBackgroundProcesses(pkg)
                stopSelf()
            }, 8000)
        } else {
            am.killBackgroundProcesses(pkg)
            stopSelf()
        }
    }

    private fun buildNotif(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TimerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("AppTimer")
            .setContentText(text)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Annuler", stop).build())
            .build()
    }

    override fun onDestroy() { timer?.cancel(); super.onDestroy() }

    companion object {
        const val CHANNEL = "timer"
        const val ACTION_STOP = "STOP"
        const val EXTRA_PKG = "pkg"
        const val EXTRA_MIN = "min"
    }
}
