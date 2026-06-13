package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableSharedFlow

class RemoteEditorService : Service() {

    private var server: RemoteEditorServer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val TAG = "RemoteEditorService"
        private const val NOTIFICATION_ID = 9190
        private const val CHANNEL_ID = "remote_editor_server_notification_channel"

        var isServerActive = false
        var hostIpAddress = ""
        var hostPort = 5009
        var activeProjectPath = ""
        var activeProjectName = ""
        var activeConnections = 0

        val serviceUIFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 16)

        fun getLocalIpAddress(): String {
            try {
                val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val intf = interfaces.nextElement()
                    val addrs = intf.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                            val host = addr.hostAddress ?: ""
                            if (host.isNotEmpty() && host != "127.0.0.1") {
                                return host
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return "127.0.0.1"
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val path = intent?.getStringExtra("PROJECT_PATH") ?: ""
        val name = intent?.getStringExtra("PROJECT_NAME") ?: "JSBox Project"
        val action = intent?.action

        if (action == "STOP_SERVER" || path.isEmpty()) {
            stopServerInternal()
            stopSelf()
            return START_NOT_STICKY
        }

        activeProjectPath = path
        activeProjectName = name
        hostIpAddress = getLocalIpAddress()

        // Acquire CPU WakeLock to prevent dropping connection during sleeps
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JSBox:RemoteServerWS").apply {
                acquire(10 * 60 * 1000L /*10 minutes*/)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock: ${e.message}")
        }

        // Start Foreground Notification
        val notificationUrl = "http://$hostIpAddress:$hostPort"
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Remote Editor Active: $name")
            .setContentText("Access editor via PC browser: $notificationUrl")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        // Instantiate Server
        server?.stop()
        server = RemoteEditorServer(applicationContext, path, hostPort).apply {
            listener = object : RemoteEditorServer.ServerListener {
                override fun onClientConnected() {
                    activeConnections++
                    serviceUIFlow.tryEmit(Unit)
                    Log.d(TAG, "New client connected. Total: $activeConnections")
                }

                override fun onClientDisconnected() {
                    activeConnections = maxOf(0, activeConnections - 1)
                    serviceUIFlow.tryEmit(Unit)
                    Log.d(TAG, "Client disconnected. Total: $activeConnections")
                }
            }
            start()
        }

        isServerActive = true
        serviceUIFlow.tryEmit(Unit)

        return START_STICKY
    }

    private fun stopServerInternal() {
        isServerActive = false
        activeConnections = 0
        try {
            server?.stop()
            server = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serviceUIFlow.tryEmit(Unit)
    }

    override fun onDestroy() {
        stopServerInternal()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Remote Editor Server Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for JSBox remote developer server notifications"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
