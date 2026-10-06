package ovh.devlabstudy.devlabnetworktoolkit.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.net.InetAddress
import kotlin.concurrent.thread

class NetworkBackgroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val host = intent?.getStringExtra("EXTRA_HOST") ?: "192.168.1.1"

        createNotificationChannel()

        if (action == ACTION_START_PING) {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("DevLab Network Service")
                .setContentText("Monitorowanie hosta: $host w tle...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            startForeground(NOTIFICATION_ID, notification)

            thread {
                try {
                    val reachable = InetAddress.getByName(host).isReachable(2000)
                    val statusText = if (reachable) "Host $host odpowiada (ONLINE)" else "Host $host nie odpowiada (OFFLINE)"
                    showResultNotification(statusText)
                } catch (_: Exception) {
                    showResultNotification("Błąd podczas sprawdzania $host")
                } finally {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                    stopSelf()
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DevLab Network Monitor",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun showResultNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DevLab Monitoring")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(RESULT_NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "devlab_network_channel"
        const val NOTIFICATION_ID = 1001
        const val RESULT_NOTIFICATION_ID = 1002
        const val ACTION_START_PING = "ACTION_START_PING"

        fun startPingMonitor(context: Context, host: String) {
            val intent = Intent(context, NetworkBackgroundService::class.java).apply {
                action = ACTION_START_PING
                putExtra("EXTRA_HOST", host)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}