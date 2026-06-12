package com.example

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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

        projectName = intent.getStringExtra("PROJECT_NAME") ?: "Editor Proyek"
        projectPath = intent.getStringExtra("PROJECT_PATH") ?: ""

        if (projectPath.isEmpty()) {
            Toast.makeText(this, "Error: Jalur proyek tidak valid.", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "Gagal menyimpan berkas: ${e.message}", Toast.LENGTH_SHORT).show()
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
        Toast.makeText(context, "Berkas dimuat: sisa autosave aman", Toast.LENGTH_SHORT).show()
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
                            text = "Berkas Aktif: ${activeFile?.name ?: "Tidak ada"}",
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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                    }
                },
                actions = {
                    // Mobile sidebar toggle button
                    if (!isLandscapeTablet) {
                        IconButton(onClick = { isMobileSidebarOpen = !isMobileSidebarOpen }) {
                            Icon(Icons.Default.Menu, contentDescription = "Daftar Berkas", tint = Color.White)
                        }
                    }

                    // Explicit Save Button
                    IconButton(onClick = {
                        saveActiveFileContent()
                        Toast.makeText(context, "Berkas disimpan!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Simpan", tint = Color(0xFF03DAC6))
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
            title = { Text("Buat Berkas Baru", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column {
                    Text("Buat berkas skrip JavaScript (.js) atau data JSON dalam proyek ini.", fontSize = 12.sp, color = Color(0xFFBEBED0))
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        placeholder = { Text("utils.js, dll.") },
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
                            Toast.makeText(context, "Berkas sudah ada!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        try {
                            newFile.createNewFile()
                            if (name.endsWith(".json")) {
                                newFile.writeText("{\n  \n}")
                            } else {
                                newFile.writeText("// Berkas baru: $name\n")
                            }
                            showCreateFileDialog = false
                            refreshFileList()
                            switchActiveFile(newFile)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Gagal membuat berkas: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC6))
                ) {
                    Text("Buat", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                    Text("Batal")
                }
            },
            containerColor = Color(0xFF1B182E),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun FileManagerPane(
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "BERKAS",
                fontSize = 12.sp,
                color = Color(0xFF8888AA),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            IconButton(onClick = onCreateFileRequest, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.AddCircle, contentDescription = "Tambah Berkas", tint = Color(0xFF03DAC6))
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
                                contentDescription = "Hapus Berkas",
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
