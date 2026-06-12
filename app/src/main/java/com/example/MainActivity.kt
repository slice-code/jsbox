package com.example

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import org.json.JSONObject
import java.io.File

data class Project(
    val folderName: String,
    val name: String,
    val description: String,
    val iconName: String,
    val path: String
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                DashboardScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedProjectForOptions by remember { mutableStateOf<Project?>(null) }
    var showRenameDialog by remember { mutableStateOf<Project?>(null) }
    
    var selectedIconUri by remember { mutableStateOf<Uri?>(null) }
    var newProjIcon by remember { mutableStateOf("folder") }

    val copyUriToFile = { uri: Uri, destFile: java.io.File ->
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedIconUri = uri
            newProjIcon = "custom"
        }
    }
    
    // Load projects list
    fun refreshProjects() {
        val baseDir = ProjectStorage.getBaseDir(context)
        val list = mutableListOf<Project>()
        if (baseDir.exists() && baseDir.isDirectory) {
            baseDir.listFiles()?.forEach { file ->
                if (file.isDirectory && !file.name.startsWith(".")) {
                    val configFile = File(file, "config.json")
                    var projName = file.name
                    var projDesc = "Aplikasi kustom JavaScript"
                    var projIcon = "folder"
                    
                    if (configFile.exists()) {
                        try {
                            val json = JSONObject(configFile.readText())
                            projName = json.optString("name", file.name)
                            projDesc = json.optString("description", projDesc)
                            projIcon = json.optString("icon", "folder")
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    list.add(
                        Project(
                            folderName = file.name,
                            name = projName,
                            description = projDesc,
                            iconName = projIcon,
                            path = file.absolutePath
                        )
                    )
                }
            }
        }
        projects = list.sortedBy { it.name }
    }

    LaunchedEffect(Unit) {
        refreshProjects()
    }

    // Responsive Grid computation
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val columns = if (screenWidth < 600) 3 else 6

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "JS-Sandbox Workspace",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                        Text(
                            "Daftar Proyek Sandbox Anda",
                            fontSize = 12.sp,
                            color = Color(0xFF8888AA)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F0C20)
                ),
                actions = {
                    IconButton(
                        onClick = { refreshProjects() },
                        modifier = Modifier.testTag("refresh_dashboard")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Segarkan", tint = Color.White)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = Color(0xFF03DAC6),
                contentColor = Color(0xFF121212),
                shape = CircleShape,
                modifier = Modifier
                    .padding(8.dp)
                    .testTag("add_project_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Tambah Proyek Baru", modifier = Modifier.size(28.dp))
            }
        },
        containerColor = Color(0xFF0F0C20)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (projects.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Kosong",
                        tint = Color(0xFF444466),
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Belum Ada Proyek",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Klik tombol (+) di bawah untuk membuat proyek web sandbox JavaScript pertama Anda.",
                        fontSize = 14.sp,
                        color = Color(0xFFBEBED0),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(projects) { project ->
                        ProjectItemCard(
                            project = project,
                            onClicked = {
                                // Default action: run it
                                val intent = Intent(context, PreviewActivity::class.java).apply {
                                    putExtra("PROJECT_NAME", project.name)
                                    putExtra("PROJECT_PATH", project.path)
                                }
                                context.startActivity(intent)
                            },
                            onLongClicked = {
                                selectedProjectForOptions = project
                            }
                        )
                    }
                }
            }
        }
    }

    // Modal Sheet / Dialog for Options
    if (selectedProjectForOptions != null) {
        val project = selectedProjectForOptions!!
        AlertDialog(
            onDismissRequest = { selectedProjectForOptions = null },
            title = {
                Text(
                    project.name,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    project.description,
                    color = Color(0xFFBEBED0),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Option 1: Run App
                    Button(
                        onClick = {
                            selectedProjectForOptions = null
                            val intent = Intent(context, PreviewActivity::class.java).apply {
                                putExtra("PROJECT_NAME", project.name)
                                putExtra("PROJECT_PATH", project.path)
                            }
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC6)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Mainkan", tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Jalankan Sandbox", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Option 2: Edit Project Code
                    Button(
                        onClick = {
                            selectedProjectForOptions = null
                            val intent = Intent(context, EditorActivity::class.java).apply {
                                putExtra("PROJECT_NAME", project.name)
                                putExtra("PROJECT_PATH", project.path)
                            }
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Edit Source Code", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Option 3: Create Shortcut on Desktop Launcher
                    Button(
                        onClick = {
                            selectedProjectForOptions = null
                            createHomeScreenShortcut(context, project)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1B38)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Share, contentDescription = "Shortcut", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Tambahkan Shortcut Beranda", color = Color.White)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Option 4: Rename
                        OutlinedButton(
                            onClick = {
                                showRenameDialog = project
                                selectedProjectForOptions = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Ubah Nama", color = Color.White)
                        }

                        // Option 5: Delete
                        Button(
                            onClick = {
                                selectedProjectForOptions = null
                                val dir = File(project.path)
                                if (dir.exists()) {
                                    dir.deleteRecursively()
                                }
                                Toast.makeText(context, "Proyek dihapus.", Toast.LENGTH_SHORT).show()
                                refreshProjects()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCF6679)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Hapus", color = Color.White)
                        }
                    }
                }
            },
            dismissButton = null,
            containerColor = Color(0xFF1B182E),
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Modal / Dialog for creating Project
    if (showCreateDialog) {
        var newProjName by remember { mutableStateOf("") }
        var newProjDesc by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { 
                showCreateDialog = false 
                selectedIconUri = null
                newProjIcon = "folder"
            },
            title = { Text("Buat Proyek Baru", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = newProjName,
                        onValueChange = { newProjName = it },
                        label = { Text("Nama Proyek") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF03DAC6),
                            unfocusedLabelColor = Color(0xFF8888AA),
                            focusedLabelColor = Color(0xFF03DAC6),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newProjDesc,
                        onValueChange = { newProjDesc = it },
                        label = { Text("Deskripsi Singkat") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF03DAC6),
                            unfocusedLabelColor = Color(0xFF8888AA),
                            focusedLabelColor = Color(0xFF03DAC6),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Pick Icon row
                    Text("Pilih Ikon / Unggah Gambar:", color = Color.White, fontSize = 14.sp)
                    Row(
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            "folder" to Icons.Default.Settings,
                            "alarm" to Icons.Default.Notifications,
                            "vibration" to Icons.Default.Star,
                            "code" to Icons.Default.Build
                        ).forEach { (name, icon) ->
                            val isSelected = newProjIcon == name
                            IconButton(
                                onClick = { 
                                    newProjIcon = name 
                                    selectedIconUri = null
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color(0xFF03DAC6) else Color.Transparent)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = name,
                                    tint = if (isSelected) Color.Black else Color.White
                                )
                            }
                        }

                        // Custom Image Upload icon selector
                        val isCustomSelected = newProjIcon == "custom"
                        IconButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isCustomSelected) Color(0xFF03DAC6) else Color.Transparent)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCircle,
                                contentDescription = "custom",
                                tint = if (isCustomSelected) Color.Black else Color.White
                            )
                        }
                    }

                    if (selectedIconUri != null) {
                        Text(
                            "Gambar kustom dipilih!",
                            color = Color(0xFF03DAC6),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newProjName.isBlank()) {
                            Toast.makeText(context, "Nama proyek wajib diisi!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        // Create folder and files
                        val formattedFolder = newProjName.trim().replace(" ", "_")
                        val baseDir = ProjectStorage.getBaseDir(context)
                        val projectDir = File(baseDir, formattedFolder)
                        if (projectDir.exists()) {
                            Toast.makeText(context, "Proyek dengan folder tersebut sudah ada!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        projectDir.mkdirs()
                        
                        // config.json
                        val conf = File(projectDir, "config.json")
                        conf.writeText("""
                            {
                              "name": "${newProjName.trim()}",
                              "icon": "$newProjIcon",
                              "description": "${newProjDesc.trim().ifEmpty { "Aplikasi sandbox JS kustom" }}"
                            }
                        """.trimIndent())

                        // Copy selection if upload was chosen
                        if (newProjIcon == "custom" && selectedIconUri != null) {
                            val destFile = File(projectDir, "project_icon.png")
                            copyUriToFile(selectedIconUri!!, destFile)
                        }

                        // Write el.js inside the newly created project folder so it lists in the editor
                        val libraryFile = File(projectDir, "el.js")
                        libraryFile.writeText(JsLibrary.EL_JS_CONTENT)

                        // main.js
                        val main = File(projectDir, "main.js")
                        main.writeText("""
                            // Custom main entry file for ${newProjName.trim()}
                            // Built with el.js lightweight DOM library (slice-code.com)
                            
                            const app = el(document.getElementById('app'));
                            
                            // Let's create a beautiful greeting card using the global 'el' library
                            const container = el('div')
                                .css({
                                    padding: '24px',
                                    textAlign: 'center',
                                    background: 'linear-gradient(135deg, #1e1e2e, #121214)',
                                    borderRadius: '16px',
                                    border: '1px solid #333344',
                                    boxShadow: '0 8px 16px rgba(0,0,0,0.5)',
                                    marginTop: '20px'
                                });

                            const title = el('h2')
                                .text('Halo, Selamat Datang!')
                                .css({
                                    color: '#03dac6',
                                    margin: '0 0 12px 0',
                                    fontSize: '22px'
                                });

                            const description = el('p')
                                .text('Workspace custom baru Anda dengan library DOM el.js bawaan secara global.')
                                .css({
                                    color: '#cccccc',
                                    margin: '0 0 20px 0',
                                    fontSize: '14px',
                                    lineHeight: '1.5'
                                });

                            const testBtn = el('button')
                                .text('Getarkan Perangkat 📳')
                                .css({
                                    padding: '12px 24px',
                                    backgroundColor: '#03dac6',
                                    color: '#121212',
                                    border: 'none',
                                    borderRadius: '8px',
                                    fontSize: '15px',
                                    fontWeight: 'bold',
                                    transition: 'transform 0.2s'
                                })
                                .click(() => {
                                    if (window.android && window.android.vibrate) {
                                        window.android.vibrate(200);
                                        window.android.showToast('Perangkat bergetar!');
                                    } else {
                                        console.log('Vibrate triggered');
                                    }
                                });

                            container.child([title, description, testBtn]);
                            app.child(container);
                        """.trimIndent())

                        showCreateDialog = false
                        selectedIconUri = null
                        newProjIcon = "folder"
                        refreshProjects()
                        Toast.makeText(context, "Proyek Berhasil Dibuat!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC6))
                ) {
                    Text("Buat", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showCreateDialog = false 
                        selectedIconUri = null
                        newProjIcon = "folder"
                    }, 
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                ) {
                    Text("Batal")
                }
            },
            containerColor = Color(0xFF1B182E),
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Modal / Dialog for rename
    if (showRenameDialog != null) {
        val proj = showRenameDialog!!
        var renameName by remember { mutableStateOf(proj.name) }
        var renameDesc by remember { mutableStateOf(proj.description) }
        var renameIcon by remember { mutableStateOf(proj.iconName) }
        var renameIconUri by remember { mutableStateOf<Uri?>(null) }

        val editGalleryLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                renameIconUri = uri
                renameIcon = "custom"
            }
        }

        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Ubah Detail Proyek", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = renameName,
                        onValueChange = { renameName = it },
                        label = { Text("Nama Baru") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF03DAC6),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = renameDesc,
                        onValueChange = { renameDesc = it },
                        label = { Text("Deskripsi Baru") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF03DAC6),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Pick Icon row
                    Text("Ubah Ikon / Unggah Gambar:", color = Color.White, fontSize = 14.sp)
                    Row(
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            "folder" to Icons.Default.Settings,
                            "alarm" to Icons.Default.Notifications,
                            "vibration" to Icons.Default.Star,
                            "code" to Icons.Default.Build
                        ).forEach { (name, icon) ->
                            val isSelected = renameIcon == name
                            IconButton(
                                onClick = { 
                                    renameIcon = name 
                                    renameIconUri = null
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color(0xFF03DAC6) else Color.Transparent)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = name,
                                    tint = if (isSelected) Color.Black else Color.White
                                )
                            }
                        }

                        // Custom Image Upload icon selector
                        val isCustomSelected = renameIcon == "custom"
                        IconButton(
                            onClick = { editGalleryLauncher.launch("image/*") },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isCustomSelected) Color(0xFF03DAC6) else Color.Transparent)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCircle,
                                contentDescription = "custom",
                                tint = if (isCustomSelected) Color.Black else Color.White
                            )
                        }
                    }

                    if (renameIconUri != null) {
                        Text(
                            "Gambar kustom baru dipilih!",
                            color = Color(0xFF03DAC6),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameName.isBlank()) return@Button

                        // Copy selection if custom icon is chosen
                        if (renameIcon == "custom" && renameIconUri != null) {
                            val destFile = File(proj.path, "project_icon.png")
                            copyUriToFile(renameIconUri!!, destFile)
                        } else if (renameIcon != "custom") {
                            val destFile = File(proj.path, "project_icon.png")
                            if (destFile.exists()) {
                                destFile.delete()
                            }
                        }

                        val configFile = File(proj.path, "config.json")
                        configFile.writeText("""
                            {
                              "name": "${renameName.trim()}",
                              "icon": "${renameIcon}",
                              "description": "${renameDesc.trim()}"
                            }
                        """.trimIndent())
                        showRenameDialog = null
                        refreshProjects()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC6))
                ) {
                    Text("Simpan", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                    Text("Batal")
                }
            },
            containerColor = Color(0xFF1B182E),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProjectItemCard(
    project: Project,
    onClicked: () -> Unit,
    onLongClicked: () -> Unit
) {
    val customIconFile = remember(project.path) { File(project.path, "project_icon.png") }
    val bitmap = remember(project.iconName, customIconFile) {
        if (project.iconName == "custom" && customIconFile.exists()) {
            try {
                BitmapFactory.decodeFile(customIconFile.absolutePath)
            } catch (e: Exception) {
                null
            }
        } else null
    }

    val iconVector = when (project.iconName) {
        "alarm" -> Icons.Default.Notifications
        "vibration" -> Icons.Default.Star
        "code" -> Icons.Default.Build
        else -> Icons.Default.Settings
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1B38)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .combinedClickable(
                onClick = onClicked,
                onLongClick = onLongClicked
            )
            .testTag("project_card_${project.folderName}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF6200EE).copy(alpha = 0.25f))
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = project.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = project.name,
                        tint = Color(0xFF03DAC6),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = project.name,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun createHomeScreenShortcut(context: Context, project: Project) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java)
        if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {
            
            // Build intent that targets PreviewActivity directly
            val shortcutIntent = Intent(context, PreviewActivity::class.java).apply {
                action = Intent.ACTION_RUN
                putExtra("PROJECT_NAME", project.name)
                putExtra("PROJECT_PATH", project.path)
            }

            val customIconFile = File(project.path, "project_icon.png")
            val shortcutIcon = if (project.iconName == "custom" && customIconFile.exists()) {
                try {
                    val bmp = BitmapFactory.decodeFile(customIconFile.absolutePath)
                    if (bmp != null) Icon.createWithBitmap(bmp) else null
                } catch (e: Exception) {
                    null
                }
            } else null

            val iconToUse = shortcutIcon ?: run {
                val iconRes = when (project.iconName) {
                    "alarm" -> android.R.drawable.ic_lock_idle_alarm
                    "vibration" -> android.R.drawable.stat_sys_phone_call
                    else -> android.R.drawable.ic_menu_save
                }
                Icon.createWithResource(context, iconRes)
            }

            val shortcutInfo = ShortcutInfo.Builder(context, "shortcut_${project.folderName}")
                .setShortLabel(project.name)
                .setLongLabel(project.name)
                .setIcon(iconToUse)
                .setIntent(shortcutIntent)
                .build()

            val pinnedShortcutCallbackIntent = shortcutManager.createShortcutResultIntent(shortcutInfo)
            val successPendingIntent = PendingIntent.getBroadcast(
                context, 0, pinnedShortcutCallbackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            shortcutManager.requestPinShortcut(shortcutInfo, successPendingIntent.intentSender)
            Toast.makeText(context, "Shortcut '${project.name}' disematkan di Beranda!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Penyematan shortcut tidak didukung di perangkat ini.", Toast.LENGTH_SHORT).show()
        }
    } else {
        Toast.makeText(context, "Versi OS membutuhkan Android 8.0 Oreo ke atas.", Toast.LENGTH_SHORT).show()
    }
}
