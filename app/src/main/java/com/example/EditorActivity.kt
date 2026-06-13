package com.example

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme
import io.github.rosemoe.sora.widget.CodeEditor
import java.io.File

class EditorActivity : ComponentActivity() {

    private lateinit var projectPath: String
    private lateinit var projectName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        projectName = intent.getStringExtra("PROJECT_NAME") ?: "Project Editor"
        projectPath = intent.getStringExtra("PROJECT_PATH") ?: ""

        if (projectPath.isEmpty()) {
            Toast.makeText(this, "Error: Invalid project path.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            MyApplicationTheme {
                WorkspaceEditorScreen(projectName, projectPath, onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceEditorScreen(projectName: String, projectPath: String, onBack: () -> Unit) {
    val context = LocalContext.current
    
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

    var filesList by remember { mutableStateOf<List<File>>(emptyList()) }
    var activeFile by remember { mutableStateOf<File?>(null) }
    
    // Editor references
    var codeEditorRef by remember { mutableStateOf<CodeEditor?>(null) }
    
    // State to toggle sidebar on mobile viewport
    var isMobileSidebarOpen by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }

    fun refreshFileList() {
        val root = File(projectPath)
        if (root.exists() && root.isDirectory) {
            val list = root.listFiles()?.filter { it.isFile && !it.name.startsWith(".") } ?: emptyList()
            filesList = list.sortedBy { it.name }
            
            // Set main.js as default active file if none is chosen yet
            if (activeFile == null && list.isNotEmpty()) {
                val mainJs = list.find { it.name == "main.js" }
                activeFile = mainJs ?: list.first()
            }
        }
    }

    // Save active editor editorText back to file
    fun saveActiveFileContent() {
        val currentFile = activeFile ?: return
        val currentEditor = codeEditorRef ?: return
        try {
            val code = currentEditor.text.toString()
            currentFile.writeText(code)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to save file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Trigger file change with automatic background buffer save (Autosave!)
    fun switchActiveFile(newFile: File) {
        if (activeFile == newFile) return
        
        // Save current active progress in background first (Autosave principle!)
        saveActiveFileContent()
        
        // Transition to new file
        activeFile = newFile
        codeEditorRef?.setText(newFile.readText())
        Toast.makeText(context, "File loaded: autosave complete", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(projectPath) {
        refreshFileList()
    }

    // Load active editor content once target file changes or editor initializes
    LaunchedEffect(activeFile, codeEditorRef) {
        val file = activeFile
        val editor = codeEditorRef
        if (file != null && editor != null) {
            editor.setText(file.readText())
        }
    }

    // Calculate viewport bounds
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val isLandscapeTablet = screenWidth >= 600

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = projectName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Active File: ${activeFile?.name ?: "None"}",
                            fontSize = 11.sp,
                            color = Color(0xFF03DAC6),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        // Save current changes on back exit
                        saveActiveFileContent()
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    // Mobile sidebar toggle button
                    if (!isLandscapeTablet) {
                        IconButton(onClick = { isMobileSidebarOpen = !isMobileSidebarOpen }) {
                            Icon(Icons.Default.Menu, contentDescription = "File List", tint = Color.White)
                        }
                    }

                    // Explicit Save Button
                    IconButton(onClick = {
                        saveActiveFileContent()
                        Toast.makeText(context, "File saved!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Save", tint = Color(0xFF03DAC6))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F0C20)
                )
            )
        },
        containerColor = Color(0xFF0F0C20)
    ) { paddingValues ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // SIDE PANEL (FILE MANAGER): Permanent on Tablet/Landscape, Sliding Overlay on Mobile/Portrait
            if (isLandscapeTablet) {
                Box(
                    modifier = Modifier
                        .width(260.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF15122A))
                ) {
                    FileManagerPane(
                        projectPath = projectPath,
                        projectName = projectName,
                        files = filesList,
                        activeFile = activeFile,
                        onFileClick = { f -> switchActiveFile(f) },
                        onCreateFileRequest = { showCreateFileDialog = true },
                        onDeleteFileRequest = { f ->
                            f.delete()
                            if (activeFile == f) {
                                activeFile = null
                            }
                            refreshFileList()
                        }
                    )
                }
            }

            // MAIN EDITOR AREA
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                AndroidView(
                    factory = { ctx ->
                        CodeEditor(ctx).apply {
                            // Specifications configuration
                            isWordwrap = false
                            setPinLineNumber(true)
                            
                            // Visuals
                            setTextSize(14f)
                            
                            codeEditorRef = this
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("sora_code_editor")
                )

                // Mobile overlay panel (Sidebar)
                if (!isLandscapeTablet && isMobileSidebarOpen) {
                    Box(
                        modifier = Modifier
                            .width(260.dp)
                            .fillMaxHeight()
                            .background(Color(0xFF15122A))
                            .align(Alignment.CenterStart)
                            .padding(end = 4.dp)
                    ) {
                        FileManagerPane(
                            projectPath = projectPath,
                            projectName = projectName,
                            files = filesList,
                            activeFile = activeFile,
                            onFileClick = { f ->
                                switchActiveFile(f)
                                isMobileSidebarOpen = false
                            },
                            onCreateFileRequest = { showCreateFileDialog = true },
                            onDeleteFileRequest = { f ->
                                f.delete()
                                if (activeFile == f) {
                                    activeFile = null
                                }
                                refreshFileList()
                            }
                        )
                    }
                }
            }
        }
    }

    // Modal dialog to create code files
    if (showCreateFileDialog) {
        var newFileName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text("Create New File", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column {
                    Text("Create a JavaScript script (.js) or JSON data file in this project.", fontSize = 12.sp, color = Color(0xFFBEBED0))
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        placeholder = { Text("utils.js, etc.") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF03DAC6),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newFileName.trim()
                        if (name.isEmpty()) return@Button
                        
                        val newFile = File(projectPath, name)
                        if (newFile.exists()) {
                            Toast.makeText(context, "File already exists!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        try {
                            newFile.createNewFile()
                            if (name.endsWith(".json")) {
                                newFile.writeText("{\n  \n}")
                            } else {
                                newFile.writeText("// New file: $name\n")
                            }
                            showCreateFileDialog = false
                            refreshFileList()
                            switchActiveFile(newFile)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to create file: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC6))
                ) {
                    Text("Create", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                    Text("Cancel")
                }
            },
            containerColor = Color(0xFF1B182E),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun RemoteControlPanel(projectPath: String, projectName: String) {
    val context = LocalContext.current
    var isServerActive by remember { mutableStateOf(RemoteEditorService.isServerActive) }
    var ipAddress by remember { mutableStateOf(RemoteEditorService.hostIpAddress) }
    var activeConnections by remember { mutableStateOf(RemoteEditorService.activeConnections) }

    LaunchedEffect(Unit) {
        RemoteEditorService.serviceUIFlow.collect {
            isServerActive = RemoteEditorService.isServerActive
            ipAddress = RemoteEditorService.hostIpAddress
            activeConnections = RemoteEditorService.activeConnections
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .testTag("remote_control_card"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B182E)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E2A4E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (isServerActive) Color(0xFF03DAC6) else Color(0xFFCF6679))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Remote Editor",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Switch(
                    checked = isServerActive,
                    onCheckedChange = { checked ->
                        val serviceIntent = Intent(context, RemoteEditorService::class.java).apply {
                            if (checked) {
                                putExtra("PROJECT_PATH", projectPath)
                                putExtra("PROJECT_NAME", projectName)
                            } else {
                                action = "STOP_SERVER"
                            }
                        }
                        if (checked) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                        } else {
                            context.startService(serviceIntent)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF0F0C20),
                        checkedTrackColor = Color(0xFF03DAC6),
                        uncheckedThumbColor = Color(0xFFBEBED0),
                        uncheckedTrackColor = Color(0xFF15122A)
                    ),
                    modifier = Modifier.scale(0.8f).testTag("remote_server_switch")
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (isServerActive) {
                val serverUrl = "http://$ipAddress:5009"
                Column {
                    Text(
                        text = "Access your Wi-Fi browser at:",
                        fontSize = 10.sp,
                        color = Color(0xFFBEBED0)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = serverUrl,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFBB86FC),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("URL Remote Editor", serverUrl)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Remote URL copied!", Toast.LENGTH_SHORT).show()
                            }
                    )
                    if (activeConnections > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "⚡ Connected: $activeConnections PC",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF03DAC6)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "💤 Waiting for PC connection...",
                            fontSize = 11.sp,
                            color = Color(0xFFBEBED0)
                        )
                    }
                }
            } else {
                Text(
                    text = "Server inactive. Turn on the switch to edit this project via your laptop/PC browser.",
                    fontSize = 10.sp,
                    color = Color(0xFF8888AA),
                    lineHeight = 1.4.sp
                )
            }
        }
    }
}

@Composable
fun FileManagerPane(
    projectPath: String,
    projectName: String,
    files: List<File>,
    activeFile: File?,
    onFileClick: (File) -> Unit,
    onCreateFileRequest: () -> Unit,
    onDeleteFileRequest: (File) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        RemoteControlPanel(projectPath = projectPath, projectName = projectName)

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "FILES",
                fontSize = 12.sp,
                color = Color(0xFF8888AA),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            IconButton(onClick = onCreateFileRequest, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.AddCircle, contentDescription = "Add File", tint = Color(0xFF03DAC6))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(files) { file ->
                val isActive = activeFile == file
                val fileIcon = when {
                    file.name.endsWith(".js") -> Icons.Default.Build
                    file.name.endsWith(".json") -> Icons.Default.Settings
                    else -> Icons.Default.Info
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) Color(0xFF03DAC6).copy(alpha = 0.15f) else Color.Transparent)
                        .clickable { onFileClick(file) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = fileIcon,
                        contentDescription = file.name,
                        tint = if (isActive) Color(0xFF03DAC6) else Color(0xFFBEBED0),
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = file.name,
                        fontSize = 13.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) Color(0xFF03DAC6) else Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Provide safety checks: do not let developer delete critical ecosystem entrypoints
                    if (file.name != "main.js" && file.name != "config.json") {
                        IconButton(
                            onClick = { onDeleteFileRequest(file) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Delete File",
                                tint = Color(0xFFCF6679).copy(alpha = 0.8f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
