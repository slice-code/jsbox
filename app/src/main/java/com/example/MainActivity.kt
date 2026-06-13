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
    var searchQuery by remember { mutableStateOf("") }

    val filteredProjects = remember(projects, searchQuery) {
        if (searchQuery.isBlank()) {
            projects
        } else {
            projects.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.description.contains(searchQuery, ignoreCase = true)
            }
        }
    }

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
                    var projDesc = "Custom JavaScript application"
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
    val columns = if (screenWidth < 600) 2 else 4

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
                            "Your Sandbox Project List",
                            fontSize = 12.sp,
                            color = Color(0xFF8888AA)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D0B18)
                ),
                actions = {
                    IconButton(
                        onClick = { refreshProjects() },
                        modifier = Modifier.testTag("refresh_dashboard")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
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
                Icon(Icons.Default.Add, contentDescription = "Add New Project", modifier = Modifier.size(28.dp))
            }
        },
        containerColor = Color(0xFF0D0B18)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Bar Area
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search project name or description...", color = Color(0xFF7F7A9B)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search icon",
                        tint = Color(0xFF03DAC6)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = Color(0xFFBEBED0)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF03DAC6),
                    unfocusedBorderColor = Color(0xFF221E42),
                    focusedContainerColor = Color(0xFF14112B),
                    unfocusedContainerColor = Color(0xFF14112B),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )

            // Dynamic HUD/Stats Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (searchQuery.isEmpty()) {
                        "Total: ${projects.size} Projects"
                    } else {
                        "Search results: ${filteredProjects.size} of ${projects.size}"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFBB86FC)
                )

                Text(
                    text = "Tap Icon (⋮) for options",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Content Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (filteredProjects.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (searchQuery.isEmpty()) Icons.Default.Info else Icons.Default.Search,
                            contentDescription = "Empty",
                            tint = Color(0xFF444466),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) "No Projects Yet" else "Project Not Found",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) {
                                "Tap the (+) button below to create your first JavaScript sandbox project."
                            } else {
                                "No projects matched the keyword '$searchQuery'."
                            },
                            fontSize = 14.sp,
                            color = Color(0xFFBEBED0),
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(bottom = 80.dp, start = 16.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredProjects) { project ->
                            ProjectItemCard(
                                project = project,
                                onClicked = {
                                    val intent = Intent(context, PreviewActivity::class.java).apply {
                                        putExtra("PROJECT_NAME", project.name)
                                        putExtra("PROJECT_PATH", project.path)
                                    }
                                    context.startActivity(intent)
                                },
                                onLongClicked = {
                                    selectedProjectForOptions = project
                                },
                                onMoreOptionsClicked = {
                                    selectedProjectForOptions = project
                                }
                            )
                        }
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
                            Icon(Icons.Default.PlayArrow, contentDescription = "Run", tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Run Sandbox", color = Color.Black, fontWeight = FontWeight.Bold)
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
                            Text("Add Home Screen Shortcut", color = Color.White)
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
                            Text("Rename", color = Color.White)
                        }

                        // Option 5: Delete
                        Button(
                            onClick = {
                                selectedProjectForOptions = null
                                val dir = File(project.path)
                                if (dir.exists()) {
                                    dir.deleteRecursively()
                                }
                                Toast.makeText(context, "Project deleted.", Toast.LENGTH_SHORT).show()
                                refreshProjects()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCF6679)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Delete", color = Color.White)
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
            title = { Text("Create New Project", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = newProjName,
                        onValueChange = { newProjName = it },
                        label = { Text("Project Name") },
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
                        label = { Text("Short Description") },
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
                    Text("Choose Icon / Upload Image:", color = Color.White, fontSize = 14.sp)
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
                            "Custom image selected!",
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
                            Toast.makeText(context, "Project name is required!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        // Create folder and files
                        val formattedFolder = newProjName.trim().replace(" ", "_")
                        val baseDir = ProjectStorage.getBaseDir(context)
                        val projectDir = File(baseDir, formattedFolder)
                        if (projectDir.exists()) {
                            Toast.makeText(context, "A project with that folder already exists!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        projectDir.mkdirs()
                        
                        // config.json
                        val conf = File(projectDir, "config.json")
                        conf.writeText("""
                            {
                              "name": "${newProjName.trim()}",
                              "icon": "$newProjIcon",
                              "description": "${newProjDesc.trim().ifEmpty { "Custom JS sandbox app" }}"
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
                                .text('Hello, Welcome!')
                                .css({
                                    color: '#03dac6',
                                    margin: '0 0 12px 0',
                                    fontSize: '22px'
                                });

                            const description = el('p')
                                .text('Your new custom workspace with globally built-in el.js DOM library.')
                                .css({
                                    color: '#cccccc',
                                    margin: '0 0 20px 0',
                                    fontSize: '14px',
                                    lineHeight: '1.5'
                                });

                            const testBtn = el('button')
                                .text('Vibrate Device 📳')
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
                                        window.android.showToast('Device vibrated!');
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
                        Toast.makeText(context, "Project Created Successfully!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC6))
                ) {
                    Text("Create", color = Color.Black, fontWeight = FontWeight.Bold)
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
                    Text("Cancel")
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
            title = { Text("Edit Project Details", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = renameName,
                        onValueChange = { renameName = it },
                        label = { Text("New Name") },
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
                        label = { Text("New Description") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF03DAC6),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Pick Icon row
                    Text("Change Icon / Upload Image:", color = Color.White, fontSize = 14.sp)
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
                            "New custom image selected!",
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
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                    Text("Cancel")
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
    onLongClicked: () -> Unit,
    onMoreOptionsClicked: () -> Unit
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
            containerColor = Color(0xFF14112A)
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            Color(0xFF2B2554)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .combinedClickable(
                onClick = onClicked,
                onLongClick = onLongClicked
            )
            .testTag("project_card_${project.folderName}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Row: Icon Container & Action indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF8B5CF6).copy(alpha = 0.15f))
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
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    // Small option menu button on top-right of card for easy reachability
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.04f))
                            .combinedClickable(
                                onClick = onMoreOptionsClicked
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = Color(0xFFBEBED0),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Project Details block
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = project.description.ifEmpty { "Custom JS sandbox app" },
                        fontSize = 11.sp,
                        color = Color(0xFF9E9EB2),
                        maxLines = 2,
                        lineHeight = 14.sp,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Call to action inline row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Run Icon",
                        tint = Color(0xFF03DAC6),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "RUN SANDBOX",
                        color = Color(0xFF03DAC6),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
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
            Toast.makeText(context, "Shortcut '${project.name}' pinned to Home Screen!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Pinning shortcuts is not supported on this device.", Toast.LENGTH_SHORT).show()
        }
    } else {
        Toast.makeText(context, "OS version requires Android 8.0 Oreo or above.", Toast.LENGTH_SHORT).show()
    }
}
