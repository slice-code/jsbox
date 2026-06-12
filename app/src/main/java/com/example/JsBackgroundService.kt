package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import java.io.File

class JsBackgroundService : Service() {

    private var headlessWebView: WebView? = null
    
    companion object {
        private const val NOTIFICATION_ID = 9070
        private const val CHANNEL_ID = "js_sandbox_background_service_channel"
        
        // Shared flow for real-time messaging between background JavaScript thread and frontends
        val serviceMessageFlow = MutableSharedFlow<String>(extraBufferCapacity = 100)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val projectName = intent?.getStringExtra("PROJECT_NAME") ?: "Milik Pengguna"
        val scriptName = intent?.getStringExtra("SCRIPT_NAME") ?: "service.js"
        val projectPath = intent?.getStringExtra("PROJECT_PATH") ?: ""
        
        // Setup persistent foreground notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("JS-Sandbox Active: $projectName")
            .setContentText("Skrip $scriptName sedang berjalan")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
            
        startForeground(NOTIFICATION_ID, notification)

        // Secure evaluation on main thread
        Handler(Looper.getMainLooper()).post {
            loadHeadlessJs(projectPath, scriptName)
        }

        return START_NOT_STICKY
    }

    private fun loadHeadlessJs(projectPath: String, scriptName: String) {
        val scriptFile = File(projectPath, scriptName)
        if (!scriptFile.exists()) {
            serviceMessageFlow.tryEmit("Error: Berkas $scriptName tidak ditemukan dalam direktori proyek.")
            stopSelf()
            return
        }

        val scriptContent = scriptFile.readText()
        
        // Instantiate real offscreen WebView to act as our pure V8 DOM-less executor
        val webView = WebView(applicationContext)
        webView.settings.javaScriptEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.domStorageEnabled = true

        // Bind system bindings bridge
        webView.addJavascriptInterface(object : Any() {
            @JavascriptInterface
            fun sendMessageToUI(data: String) {
                serviceMessageFlow.tryEmit(data)
            }
        }, "service")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                
                // Wrap code execution so service.sendMessageToUI matches user expectation
                val wrappedExecution = """
                    (function() {
                        window.sendMessageToUI = function(msg) {
                            if (window.service && window.service.sendMessageToUI) {
                                window.service.sendMessageToUI(msg);
                            } else {
                                console.log('[Headless Service UI Bridge]', msg);
                            }
                        };
                        
                        try {
                            $scriptContent
                        } catch (e) {
                            window.sendMessageToUI('Exception: ' + e.message);
                        }
                    })();
                """.trimIndent()
                
                webView.evaluateJavascript(wrappedExecution, null)
            }
        }

        // Initialize page BaseURL pointing directly to the project directory to support relative module imports
        webView.loadDataWithBaseURL(
            "file://$projectPath/",
            "<html><head></head><body>Headless Background Service</body></html>",
            "text/html",
            "UTF-8",
            null
        )
        
        headlessWebView = webView
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Layanan Latar Belakang JS Sandbox",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menjalankan service.js secara persisten"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        Handler(Looper.getMainLooper()).post {
            headlessWebView?.destroy()
            headlessWebView = null
        }
        super.onDestroy()
    }
}
