package com.example

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.*
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

class PreviewActivity : ComponentActivity() {

    private lateinit var projectPath: String
    private lateinit var projectName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Read intent data
        projectName = intent.getStringExtra("PROJECT_NAME") ?: "Aplikasi Pengguna"
        projectPath = intent.getStringExtra("PROJECT_PATH") ?: ""

        if (projectPath.isEmpty()) {
            Toast.makeText(this, "Error: Jalur proyek kosong.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            MyApplicationTheme {
                PreviewRunnerScreen(projectName, projectPath, onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PreviewRunnerScreen(projectName: String, projectPath: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    
    // Developer console logs
    val consoleLogs = remember { mutableStateListOf<String>() }
    
    fun addLog(message: String) {
        consoleLogs.add(message)
    }

    // Material 3 Bottom Sheet scaffold state
    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded)
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    // Manage Screen Keep Awake control based on remote server status
    val activity = LocalContext.current as? ComponentActivity
    LaunchedEffect(Unit) {
        activity?.window?.let { win ->
            if (RemoteEditorService.isServerActive && RemoteEditorService.activeConnections > 0) {
                win.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        RemoteEditorService.serviceUIFlow.collect {
            val shouldKeep = RemoteEditorService.isServerActive && RemoteEditorService.activeConnections > 0
            activity?.window?.let { win ->
                if (shouldKeep) {
                    win.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }

    // Listen to background service messages and pipe them back into executing WebView page
    LaunchedEffect(webViewInstance) {
        if (webViewInstance != null) {
            JsBackgroundService.serviceMessageFlow.collectLatest { message ->
                coroutineScope.launch {
                    val formattedMsg = message.replace("'", "\\'")
                    addLog("📬 Pesan Latar Belakang: $message")
                    webViewInstance?.evaluateJavascript(
                        "if (window.onServiceMessage) { window.onServiceMessage('$formattedMsg'); }", 
                        null
                    )
                }
            }
        }
    }

    // Collect Remote Play trigger for hot-reloading (Instant Run runtime!)
    LaunchedEffect(webViewInstance) {
        if (webViewInstance != null) {
            RemotePlayManager.playTriggerFlow.collect {
                val file = File(projectPath, "main.js")
                val latestScriptContent = if (file.exists()) file.readText() else ""
                
                val escapedJsStr = latestScriptContent
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "")
                
                val hotsyncScript = """
                    (function() {
                        try {
                            const appDiv = document.getElementById('app');
                            if (appDiv) {
                                appDiv.innerHTML = '';
                            }
                            const oldScripts = document.querySelectorAll('script[type="module"]');
                            oldScripts.forEach(s => s.remove());
                            
                            const newScript = document.createElement('script');
                            newScript.type = 'module';
                            newScript.textContent = "$escapedJsStr";
                            document.body.appendChild(newScript);
                            
                            console.log('🔄 Hot reloaded main.js via Instant Run!');
                        } catch(e) {
                            console.error('Instant Run Error:', e);
                        }
                    })();
                """.trimIndent()
                
                webViewInstance?.post {
                    webViewInstance?.evaluateJavascript(hotsyncScript, null)
                    addLog("⚡ Instant Run dipicu: Menyinkronkan dan menjalankan ulang skrip terbaru.")
                }
            }
        }
    }

    // Load custom script
    val userScript = remember {
        val file = File(projectPath, "main.js")
        if (file.exists()) file.readText() else ""
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 64.dp,
        sheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        sheetContainerColor = Color(0xFF1E1B38),
        sheetContentColor = Color.White,
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.List, 
                            contentDescription = "Console", 
                            tint = Color(0xFF03DAC6),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Konsol Log & Debugger", 
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }
                    
                    Row {
                        IconButton(onClick = { consoleLogs.clear() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear", tint = Color(0xFFCF6679))
                        }
                    }
                }
                
                Divider(color = Color(0xFF444466))

                if (consoleLogs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Belum ada log aktivitas console.log()",
                            fontSize = 12.sp,
                            color = Color(0xFF8888AA)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(consoleLogs) { log ->
                            Text(
                                text = log,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = if (log.contains("ERROR") || log.contains("Exception")) Color(0xFFCF6679) else Color(0xFFBB86FC),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0F0C20).copy(alpha = 0.5f))
                                    .padding(6.dp)
                            )
                        }
                    }
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            projectName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Text(
                            "Menjalankan di Runtime Sandbox",
                            fontSize = 11.sp,
                            color = Color(0xFF03DAC6)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F0C20)
                )
            )
        },
        containerColor = Color(0xFF0F0C20)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        settings.allowFileAccessFromFileURLs = true
                        settings.allowUniversalAccessFromFileURLs = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                            WebView.setWebContentsDebuggingEnabled(true)
                        }
                        setBackgroundColor(0) // Transparent background to match theme color seamlessly

                        // Inject custom web Chrome client to capture console.log statements
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                if (consoleMessage != null) {
                                    val level = consoleMessage.messageLevel().name
                                    val msg = "[${level}] ${consoleMessage.message()} (${consoleMessage.sourceId().substringAfterLast("/")}:${consoleMessage.lineNumber()})"
                                    addLog(msg)
                                }
                                return true
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                addLog("✅ Sandbox DOM dimuat sempurna.")
                            }
                            
                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    addLog("❌ WebResourceError [${error?.errorCode}]: ${error?.description}")
                                }
                            }
                        }

                        // Register modern high fidelity Android Bridge APIs
                        addJavascriptInterface(object : Any() {
                            
                            @JavascriptInterface
                            fun showToast(msg: String) {
                                Handler(Looper.getMainLooper()).post {
                                    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                                }
                            }

                            @JavascriptInterface
                            fun vibrate(ms: Long) {
                                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val vibratorManager = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                                    vibratorManager?.defaultVibrator
                                } else {
                                    @Suppress("DEPRECATION")
                                    ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                                }
                                @Suppress("DEPRECATION")
                                vibrator?.vibrate(ms)
                            }

                            @JavascriptInterface
                            fun createAlarm(label: String, hour: Int, minute: Int) {
                                Handler(Looper.getMainLooper()).post {
                                    try {
                                        val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                                        val intent = Intent(ctx, AlarmReceiver::class.java).apply {
                                            putExtra("EXTRA_LABEL", label)
                                        }
                                        
                                        val pendingIntent = PendingIntent.getBroadcast(
                                            ctx,
                                            System.currentTimeMillis().toInt(),
                                            intent,
                                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                        )

                                        val calendar = Calendar.getInstance().apply {
                                            set(Calendar.HOUR_OF_DAY, hour)
                                            set(Calendar.MINUTE, minute)
                                            set(Calendar.SECOND, 0)
                                            if (before(Calendar.getInstance())) {
                                                add(Calendar.DATE, 1)
                                            }
                                        }

                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                            alarmManager.setExactAndAllowWhileIdle(
                                                AlarmManager.RTC_WAKEUP,
                                                calendar.timeInMillis,
                                                pendingIntent
                                            )
                                        } else {
                                            alarmManager.setExact(
                                                AlarmManager.RTC_WAKEUP,
                                                calendar.timeInMillis,
                                                pendingIntent
                                            )
                                        }
                                        addLog("⏰ Alarm diset: $label pada ${String.format("%02d:%02d", hour, minute)}")
                                    } catch (e: Exception) {
                                        addLog("❌ Gagal menyetel Alarm: ${e.message}")
                                    }
                                }
                            }

                            @JavascriptInterface
                            fun startBackgroundService(filename: String) {
                                Handler(Looper.getMainLooper()).post {
                                    try {
                                        val intent = Intent(ctx, JsBackgroundService::class.java).apply {
                                            putExtra("PROJECT_NAME", projectName)
                                            putExtra("SCRIPT_NAME", filename)
                                            putExtra("PROJECT_PATH", projectPath)
                                        }
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            ctx.startForegroundService(intent)
                                        } else {
                                            ctx.startService(intent)
                                        }
                                        addLog("⚙️ Background Service running: $filename")
                                    } catch (e: Exception) {
                                        addLog("❌ Gagal memulai Service Latar Belakang: ${e.message}")
                                    }
                                }
                            }
                            
                            @JavascriptInterface
                            fun logError(err: String) {
                                addLog("❌ JS Runtime Exception:\n$err")
                            }
                        }, "android")

                        webViewInstance = this

                        // Construct safe viewport-responsive HTML template shell
                        val htmlShellContent = """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <meta charset="utf-8">
                                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                                <style>
                                    body {
                                        margin: 0;
                                        padding: 16px;
                                        background-color: #121214;
                                        color: #ffffff;
                                        font-family: system-ui, -apple-system, sans-serif;
                                    }
                                    button {
                                        font-family: inherit;
                                        cursor: pointer;
                                    }
                                </style>
                                <script>
                                    // Setup standard error telemetry
                                    window.addEventListener('error', function(e) {
                                        console.error("Kesalahan: " + e.message + " pada " + e.filename + ":" + e.lineno);
                                        if (window.android && window.android.logError) {
                                            var stack = (e.error && e.error.stack) ? e.error.stack : "";
                                            window.android.logError(e.message + " (" + e.filename.split('/').pop() + ":" + e.lineno + ")\n" + stack);
                                        }
                                    });
                                    window.addEventListener('unhandledrejection', function(e) {
                                        console.error("Unhandled Promise Rejection: " + e.reason);
                                        if (window.android && window.android.logError) {
                                            var reasonStr = e.reason ? (e.reason.message || e.reason) : "unknown";
                                            var stackStr = (e.reason && e.reason.stack) ? e.reason.stack : "";
                                            window.android.logError("Unhandled Rejection: " + reasonStr + "\n" + stackStr);
                                        }
                                    });
                                </script>
                                <script>
                                    ${JsLibrary.EL_JS_CONTENT}
                                </script>
                            </head>
                            <body>
                                <div id="app"></div>
                                <script type="module">
                                    $userScript
                                </script>
                            </body>
                            </html>
                        """.trimIndent()

                        loadDataWithBaseURL(
                            "file://$projectPath/",
                            htmlShellContent,
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("renderer_webview")
            )
        }
    }
}
