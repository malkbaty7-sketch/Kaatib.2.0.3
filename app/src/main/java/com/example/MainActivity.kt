package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.SavedBooksViewModel
import com.example.ui.screens.*
import com.example.util.AppLanguage
import com.example.util.AppLocalization

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val savedBooksViewModel: SavedBooksViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val currentLang by viewModel.appLanguage.collectAsState()
            val layoutDirection = currentLang.layoutDirection

            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                val theme by viewModel.currentTheme.collectAsState()
                val activeProject by viewModel.activeProject.collectAsState()
                val toastMessage by viewModel.saveToastMessage.collectAsState()
                val isVip by viewModel.isVipUser.collectAsState()

                var currentTab by remember { mutableStateOf("PROJECTS") } // FILES, EXTRACTION, PROJECTS, THEMES, SETTINGS
                var isEditingProject by remember { mutableStateOf(false) }
                var showDeviceDocsScreen by remember { mutableStateOf(false) }
                var viewingDocUri by remember { mutableStateOf<String?>(null) }
                var viewingDocName by remember { mutableStateOf("") }

                // Quick Global Dialogs
                var showHelpDialog by remember { mutableStateOf(false) }
                var showBrowserDialog by remember { mutableStateOf(false) }
                var showSubscriptionDialog by remember { mutableStateOf(false) }
                var showAnalyticsDialog by remember { mutableStateOf(false) }
                var showArabicConverterStudio by remember { mutableStateOf(false) }

                BackHandler(enabled = viewingDocUri != null || showDeviceDocsScreen || isEditingProject || currentTab != "PROJECTS") {
                    if (viewingDocUri != null) {
                        viewingDocUri = null
                    } else if (showDeviceDocsScreen) {
                        showDeviceDocsScreen = false
                    } else if (isEditingProject) {
                        viewModel.saveCurrentProject()
                        isEditingProject = false
                    } else if (currentTab != "PROJECTS") {
                        currentTab = "PROJECTS"
                    }
                }

                LaunchedEffect(toastMessage) {
                    toastMessage?.let { msg ->
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                        viewModel.saveToastMessage.value = null
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = theme.surfaceColor
                ) {
                    if (viewingDocUri != null) {
                        com.example.ui.screens.MultiFormatDocumentViewerScreen(
                            uriString = viewingDocUri!!,
                            fileName = viewingDocName,
                            theme = theme,
                            viewModel = viewModel,
                            onBack = { viewingDocUri = null }
                        )
                    } else if (showDeviceDocsScreen) {
                        com.example.ui.screens.DeviceDocumentsScreen(
                            theme = theme,
                            viewModel = viewModel,
                            onBack = { showDeviceDocsScreen = false },
                            onOpenDocument = { uriStr, docName ->
                                viewingDocUri = uriStr
                                viewingDocName = docName
                            }
                        )
                    } else if (isEditingProject && activeProject != null) {
                        EditorScreen(
                            viewModel = viewModel,
                            onBack = {
                                viewModel.saveCurrentProject()
                                isEditingProject = false
                            }
                        )
                    } else {
                        Scaffold(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(theme.backgroundBrush),
                            topBar = {
                                Surface(
                                    color = Color.Transparent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .statusBarsPadding()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = AppLocalization.string(currentLang, "app_name"),
                                                fontWeight = FontWeight.Black,
                                                fontSize = 16.sp,
                                                color = theme.textColor
                                            )
                                            if (isVip) {
                                                Surface(
                                                    color = Color(0xFFD97706),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "VIP",
                                                        fontSize = 9.sp,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            // Standalone Document Viewer Quick Switch
                                            IconButton(
                                                onClick = { currentTab = "DOCUMENTS" },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.FolderSpecial, contentDescription = "عارض المستندات", tint = theme.primaryColor, modifier = Modifier.size(17.dp))
                                            }

                                            // Writing & Productivity Analytics Quick Access
                                            IconButton(
                                                onClick = { showAnalyticsDialog = true },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Insights, contentDescription = "إحصائيات وتحليلات الإنتاجية", tint = theme.primaryColor, modifier = Modifier.size(17.dp))
                                            }

                                            // Arabic Document Converter & Tashkeel Studio
                                            IconButton(
                                                onClick = { showArabicConverterStudio = true },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.AutoFixHigh, contentDescription = "محوّل المستندات والتشكيل العربي", tint = theme.primaryColor, modifier = Modifier.size(17.dp))
                                            }

                                            // Research & PDF Browser Quick Access
                                            IconButton(
                                                onClick = { showBrowserDialog = true },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Public, contentDescription = "متصفح البحوث", tint = theme.primaryColor, modifier = Modifier.size(17.dp))
                                            }

                                            // Help & FAQ (?)
                                            IconButton(
                                                onClick = { showHelpDialog = true },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(22.dp)
                                                        .clip(CircleShape)
                                                        .background(theme.primaryColor.copy(alpha = 0.15f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("?", fontWeight = FontWeight.Black, fontSize = 13.sp, color = theme.primaryColor)
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            bottomBar = {
                                Column {
                                    // Slim Ad Banner (Displayed only when !isVip)
                                    if (!isVip) {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { showSubscriptionDialog = true },
                                            color = Color(0xFF1E293B)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Surface(color = Color(0xFFF59E0B), shape = RoundedCornerShape(3.dp)) {
                                                        Text("إعلان", fontSize = 8.sp, color = Color.Black, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp))
                                                    }
                                                    Text("تجربة خالية من الإعلانات مع كاتب VIP", fontSize = 10.sp, color = Color.White)
                                                }
                                                Text("إزالة الإعلانات", fontSize = 10.sp, color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    NavigationBar(
                                        modifier = Modifier
                                            .height(58.dp)
                                            .shadow(6.dp, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                                            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
                                        containerColor = Color.White,
                                        tonalElevation = 6.dp
                                    ) {
                                        NavigationBarItem(
                                            selected = currentTab == "FILES",
                                            onClick = { currentTab = "FILES" },
                                            icon = { Icon(Icons.Default.Folder, contentDescription = "المصادر", modifier = Modifier.size(18.dp)) },
                                            label = { Text(AppLocalization.string(currentLang, "tab_files"), fontSize = 10.sp) },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = theme.primaryColor,
                                                indicatorColor = theme.primaryColor.copy(alpha = 0.15f)
                                            )
                                        )

                                        NavigationBarItem(
                                            selected = currentTab == "EXTRACTION",
                                            onClick = { currentTab = "EXTRACTION" },
                                            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "الاستخلاص", modifier = Modifier.size(18.dp)) },
                                            label = { Text(AppLocalization.string(currentLang, "tab_extraction"), fontSize = 10.sp) },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = theme.primaryColor,
                                                indicatorColor = theme.primaryColor.copy(alpha = 0.15f)
                                            )
                                        )

                                        NavigationBarItem(
                                            selected = currentTab == "PROJECTS",
                                            onClick = { currentTab = "PROJECTS" },
                                            icon = { Icon(Icons.Default.LibraryBooks, contentDescription = "المشاريع", modifier = Modifier.size(18.dp)) },
                                            label = { Text(AppLocalization.string(currentLang, "tab_projects"), fontSize = 10.sp) },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = theme.primaryColor,
                                                indicatorColor = theme.primaryColor.copy(alpha = 0.15f)
                                            )
                                        )

                                        NavigationBarItem(
                                            selected = currentTab == "DOCUMENTS",
                                            onClick = { currentTab = "DOCUMENTS" },
                                            icon = { Icon(Icons.Default.FolderSpecial, contentDescription = "عارض المستندات", modifier = Modifier.size(18.dp)) },
                                            label = { Text(AppLocalization.string(currentLang, "tab_documents"), fontSize = 10.sp) },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = theme.primaryColor,
                                                indicatorColor = theme.primaryColor.copy(alpha = 0.15f)
                                            )
                                        )

                                        NavigationBarItem(
                                            selected = currentTab == "SETTINGS",
                                            onClick = { currentTab = "SETTINGS" },
                                            icon = { Icon(Icons.Default.Settings, contentDescription = "الإعدادات", modifier = Modifier.size(18.dp)) },
                                            label = { Text(AppLocalization.string(currentLang, "tab_settings"), fontSize = 10.sp) },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = theme.primaryColor,
                                                indicatorColor = theme.primaryColor.copy(alpha = 0.15f)
                                            )
                                        )
                                    }
                                }
                            }
                        ) { paddingValues ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(paddingValues)
                                    .background(theme.backgroundBrush)
                            ) {
                                when (currentTab) {
                                    "FILES" -> FilesScreen(
                                        viewModel = viewModel,
                                        onNavigateToExtraction = { currentTab = "EXTRACTION" },
                                        onOpenDeviceDocuments = { currentTab = "DOCUMENTS" }
                                    )
                                    "EXTRACTION" -> ExtractionScreen(
                                        viewModel = viewModel,
                                        onNavigateToEditor = { isEditingProject = true }
                                    )
                                    "PROJECTS" -> ProjectsScreen(
                                        viewModel = viewModel,
                                        onOpenEditor = {
                                            isEditingProject = true
                                        },
                                        onOpenDeviceDocuments = {
                                            currentTab = "DOCUMENTS"
                                        }
                                    )
                                    "DOCUMENTS" -> DeviceDocumentsScreen(
                                        theme = theme,
                                        viewModel = viewModel,
                                        onBack = { currentTab = "PROJECTS" },
                                        onOpenDocument = { uriStr, docName ->
                                            viewingDocUri = uriStr
                                            viewingDocName = docName
                                        },
                                        isMainTab = true
                                    )
                                    else -> SettingsScreen(
                                        viewModel = viewModel,
                                        booksViewModel = savedBooksViewModel,
                                        onStartProjectFromBook = { bookTitle, bookAuthor ->
                                            viewModel.createProject(
                                                title = "كتاب: $bookTitle",
                                                type = "E_BOOK",
                                                templateId = "novel_action",
                                                emotions = listOf("warmth", "motivation"),
                                                initialContent = "مستوحى من كتاب «$bookTitle» للمؤلف $bookAuthor.",
                                                onCreated = {
                                                    isEditingProject = true
                                                }
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Global Dialogs
                if (showHelpDialog) {
                    HelpAndSupportDialog(
                        theme = theme,
                        viewModel = viewModel,
                        initialTab = "FAQ",
                        onDismiss = { showHelpDialog = false }
                    )
                }

                if (showBrowserDialog) {
                    ResearchBrowserDialog(
                        theme = theme,
                        viewModel = viewModel,
                        onDismiss = { showBrowserDialog = false }
                    )
                }

                if (showSubscriptionDialog) {
                    PaywallScreen(
                        theme = theme,
                        viewModel = viewModel,
                        onDismiss = { showSubscriptionDialog = false }
                    )
                }

                if (showAnalyticsDialog) {
                    AnalyticsDashboardDialog(
                        theme = theme,
                        viewModel = viewModel,
                        onDismiss = { showAnalyticsDialog = false }
                    )
                }

                if (showArabicConverterStudio) {
                    com.example.ui.screens.DocumentToolsAndConverterDialog(
                        theme = theme,
                        onDismiss = { showArabicConverterStudio = false },
                        onOpenConvertedDocument = { uriStr, docName ->
                            showArabicConverterStudio = false
                            viewingDocUri = uriStr
                            viewingDocName = docName
                        }
                    )
                }
            }
        }
    }
}
