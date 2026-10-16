package com.valhalla.bolt.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.valhalla.bolt.MainActivity
import com.valhalla.bolt.R
import com.valhalla.bolt.model.OperationCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.android.ext.android.inject

class OperationService : Service() {
    private val operations: OperationCoordinator by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel("operations", "Flashing and recovery", NotificationManager.IMPORTANCE_LOW))
        val pending = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun notification(message: String) = NotificationCompat.Builder(this, "operations")
            .setSmallIcon(R.drawable.flash_on).setContentTitle("Bolt operation in progress")
            .setContentText(message).setContentIntent(pending).setOngoing(true).build()
        startForeground(41, notification("Preparing operation"))
        operations.confirmForegroundStarted()
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Bolt:operation").apply { acquire(6 * 60 * 60 * 1000L) }
        scope.launch { operations.status.map { status -> if (status?.running == true) "${status.kind}: ${status.phase}" else null }.distinctUntilChanged().collect { message ->
            if (message != null) manager.notify(41, notification(message)) else stopSelf()
        } }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { operations.confirmForegroundStarted(); return START_NOT_STICKY }
    override fun onTimeout(startId: Int, fgsType: Int) { operations.markTimeout(); stopSelf() }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { scope.cancel(); wakeLock?.takeIf { it.isHeld }?.release(); super.onDestroy() }
}
