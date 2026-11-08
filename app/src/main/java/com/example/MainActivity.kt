package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.defaultAppThemes
import com.example.ui.MainViewModel
import com.example.ui.SavedBooksViewModel
import com.example.ui.components.GlobalCommandPaletteDialog
import com.example.ui.components.SpeedDialFabMenu
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
                val activeChapters by viewModel.activeChapters.collectAsState()
                val toastMessage by viewModel.saveToastMessage.collectAsState()
                val isVip by viewModel.isVipUser.collectAsState()
                val ttsPlaybackState by viewModel.ttsManager.playbackState.collectAsState()
                val ttsCurrentSentence by viewModel.ttsManager.currentSentenceText.collectAsState()
                val ttsSpeechRate by viewModel.ttsManager.speechRate.collectAsState()

                var currentTab by remember { mutableStateOf("HOME") } // HOME, FILES, EXTRACTION, PROJECTS, DOCUMENTS, SETTINGS
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
                var showCommandPalette by remember { mutableStateOf(false) }

                BackHandler(enabled = viewingDocUri != null || showDeviceDocsScreen || isEditingProject || currentTab != "HOME") {
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
                                    color = theme.cardColor.copy(alpha = 0.92f),
                                    shadowElevation = 4.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .statusBarsPadding()
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(RoundedCornerShape(9.dp))
                                                    .background(
                                                        Brush.linearGradient(
                                                            listOf(theme.primaryColor, theme.secondaryColor)
                                                        )
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.AutoStories,
                                                    contentDescription = null,
                                                    tint = if (theme.isDark) Color(0xFF101827) else Color.White,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                            Column {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = AppLocalization.string(currentLang, "app_name"),
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 14.sp,
                                                        color = theme.textColor
                                                    )
                                                    if (isVip) {
                                                        Surface(
                                                            color = Color(0xFFD97706),
                                                            shape = RoundedCornerShape(5.dp)
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
                                                Text(
                                                    text = "استوديو التأليف والقراءة الذكية",
                                                    fontSize = 8.sp,
                                                    color = theme.subtleTextColor
                                                )
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            // Global Spotlight Command Palette Button (⌘K)
                                            Surface(
                                                onClick = { showCommandPalette = true },
                                                shape = RoundedCornerShape(16.dp),
                                                color = theme.primaryColor.copy(alpha = 0.14f),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, theme.primaryColor.copy(alpha = 0.3f)),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Search,
                                                        contentDescription = "البحث والأوامر الفورية",
                                                        tint = theme.primaryColor,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Text(
                                                        text = "أوامر",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = theme.primaryColor
                                                    )
                                                }
                                            }

                                            // Instant Theme Cycle Button (Light / Sepia / Dark)
                                            Surface(
                                                onClick = {
                                                    val idx = defaultAppThemes.indexOfFirst { it.id == theme.id }
                                                    val nextTheme = defaultAppThemes[(idx + 1) % defaultAppThemes.size]
                                                    viewModel.currentThemeId.value = nextTheme.id
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        "🎨 الثيم: ${nextTheme.name}",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                },
                                                shape = CircleShape,
                                                color = theme.primaryColor.copy(alpha = 0.12f),
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = if (theme.isDark) Icons.Default.DarkMode else Icons.Default.Palette,
                                                        contentDescription = "تبديل الثيم",
                                                        tint = theme.primaryColor,
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                }
                                            }

                                            // Standalone Document Viewer Quick Switch
                                            Surface(
                                                onClick = { currentTab = "DOCUMENTS" },
                                                shape = CircleShape,
                                                color = theme.primaryColor.copy(alpha = 0.12f),
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.FolderSpecial, contentDescription = "عارض المستندات", tint = theme.primaryColor, modifier = Modifier.size(17.dp))
                                                }
                                            }

                                            // Writing & Productivity Analytics Quick Access
                                            Surface(
                                                onClick = { showAnalyticsDialog = true },
                                                shape = CircleShape,
                                                color = theme.primaryColor.copy(alpha = 0.12f),
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.Insights, contentDescription = "إحصائيات وتحليلات الإنتاجية", tint = theme.primaryColor, modifier = Modifier.size(17.dp))
                                                }
                                            }

                                            // Arabic Document Converter & Tashkeel Studio
                                            Surface(
                                                onClick = { showArabicConverterStudio = true },
                                                shape = CircleShape,
                                                color = theme.primaryColor.copy(alpha = 0.12f),
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.AutoFixHigh, contentDescription = "محوّل المستندات والتشكيل العربي", tint = theme.primaryColor, modifier = Modifier.size(17.dp))
                                                }
                                            }

                                            // Research & PDF Browser Quick Access
                                            Surface(
                                                onClick = { showBrowserDialog = true },
                                                shape = CircleShape,
                                                color = theme.primaryColor.copy(alpha = 0.12f),
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.Public, contentDescription = "متصفح البحوث", tint = theme.primaryColor, modifier = Modifier.size(17.dp))
                                                }
                                            }

                                            // Help & FAQ (?)
                                            Surface(
                                                onClick = { showHelpDialog = true },
                                                shape = CircleShape,
                                                color = theme.primaryColor.copy(alpha = 0.16f),
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text("?", fontWeight = FontWeight.Black, fontSize = 14.sp, color = theme.primaryColor)
                                                }
                                            }
                                        }
                                    }

                                    // Interactive Breadcrumb & Workspace Status Bar (شريط مسار العمل والحفظ الفوري)
                                    if (activeProject != null) {
                                        HorizontalDivider(color = theme.borderColor.copy(alpha = 0.6f), thickness = 0.5.dp)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(theme.primaryColor.copy(alpha = 0.06f))
                                                .padding(horizontal = 14.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .clickable { isEditingProject = true }
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(7.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF10B981))
                                                )
                                                Text(
                                                    text = "📌 النشط: ${activeProject!!.projectName} (${activeChapters.size} فصول)",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = theme.textColor,
                                                    maxLines = 1
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFF10B981).copy(alpha = 0.14f)
                                                ) {
                                                    Text(
                                                        text = "✓ محفوظ",
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF059669),
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Surface(
                                                    onClick = {
                                                        val textToRead = activeChapters.firstOrNull()?.content ?: ""
                                                        if (textToRead.isNotBlank()) {
                                                            viewModel.startTtsReading(textToRead)
                                                        } else {
                                                            Toast.makeText(this@MainActivity, "الفصل الحالي فارغ من النصوص", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = theme.primaryColor.copy(alpha = 0.14f)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                    ) {
                                                        Icon(Icons.Default.Headphones, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size(11.dp))
                                                        Text("استماع", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.primaryColor)
                                                    }
                                                }

                                                Surface(
                                                    onClick = { isEditingProject = true },
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = theme.primaryColor
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                    ) {
                                                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                                                        Text("متابعة التحرير", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    }
                                }
                            },
                            floatingActionButton = {
                                if (currentTab == "PROJECTS" || currentTab == "EXTRACTION") {
                                    SpeedDialFabMenu(
                                        theme = theme,
                                        viewModel = viewModel,
                                        onCreateNewProject = { isEditingProject = true },
                                        onNavigateToDocuments = { currentTab = "DOCUMENTS" },
                                        onNavigateToExtraction = { currentTab = "EXTRACTION" }
                                    )
                                }
                            },
                            bottomBar = {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .navigationBarsPadding()
                                ) {
                                    // Global Persistent Mini-Audio Bar (المشغل الصوتي العائم المستمر عبر التطبيق)
                                    if (ttsPlaybackState != com.example.util.TtsPlaybackState.STOPPED) {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 3.dp)
                                                .shadow(6.dp, RoundedCornerShape(14.dp)),
                                            shape = RoundedCornerShape(14.dp),
                                            color = Color(0xFF0F172A),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, theme.primaryColor.copy(alpha = 0.45f))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .clip(CircleShape)
                                                            .background(theme.primaryColor.copy(alpha = 0.25f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            Icons.Default.GraphicEq,
                                                            contentDescription = null,
                                                            tint = Color(0xFF34D399),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = "🎧 القارئ الصوتي الذكي قيد التشغيل",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF34D399)
                                                        )
                                                        Text(
                                                            text = ttsCurrentSentence.ifBlank { activeProject?.projectName ?: "قراءة صوتية مستمرة..." },
                                                            fontSize = 10.sp,
                                                            color = Color.White,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Surface(
                                                        onClick = {
                                                            val nextSpeed = when {
                                                                ttsSpeechRate < 1.15f -> 1.25f
                                                                ttsSpeechRate < 1.4f -> 1.5f
                                                                else -> 1.0f
                                                            }
                                                            viewModel.ttsManager.setSpeed(nextSpeed)
                                                        },
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = Color.White.copy(alpha = 0.14f)
                                                    ) {
                                                        Text(
                                                            text = "${ttsSpeechRate}x",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFFFDE047),
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                            if (ttsPlaybackState == com.example.util.TtsPlaybackState.PLAYING) {
                                                                viewModel.ttsManager.pause()
                                                            } else {
                                                                viewModel.ttsManager.play()
                                                            }
                                                        },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (ttsPlaybackState == com.example.util.TtsPlaybackState.PLAYING) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                                                            contentDescription = "تشغيل/إيقاف",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                    }

                                                    IconButton(
                                                        onClick = { viewModel.ttsManager.seekForward() },
                                                        modifier = Modifier.size(26.dp)
                                                    ) {
                                                        Icon(Icons.Default.SkipNext, contentDescription = "التالي", tint = Color.White, modifier = Modifier.size(18.dp))
                                                    }

                                                    IconButton(
                                                        onClick = { viewModel.stopTtsReading() },
                                                        modifier = Modifier.size(26.dp)
                                                    ) {
                                                        Icon(Icons.Default.Close, contentDescription = "إغلاق المشغل", tint = Color(0xFFF87171), modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Slim Ad Banner (Displayed only when !isVip)
                                    if (!isVip) {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 2.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable { showSubscriptionDialog = true },
                                            color = Color(0xFF1E293B)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Surface(color = Color(0xFFF59E0B), shape = RoundedCornerShape(4.dp)) {
                                                        Text("VIP", fontSize = 8.sp, color = Color.Black, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                                    }
                                                    Text("تجربة احترافية خالية من الإعلانات مع كاتب VIP", fontSize = 10.sp, color = Color.White)
                                                }
                                                Text("ترقية الآن", fontSize = 10.sp, color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    // Adaptive Glassmorphic Navigation Bar
                                    NavigationBar(
                                        modifier = Modifier
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                            .height(64.dp)
                                            .shadow(10.dp, RoundedCornerShape(20.dp), spotColor = theme.shadowColor)
                                            .clip(RoundedCornerShape(20.dp))
                                            .border(1.dp, theme.borderColor, RoundedCornerShape(20.dp)),
                                        containerColor = theme.elevatedCardColor,
                                        tonalElevation = 6.dp
                                    ) {
                                        val navItemColors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = theme.primaryColor,
                                            selectedTextColor = theme.primaryColor,
                                            unselectedIconColor = theme.subtleTextColor,
                                            unselectedTextColor = theme.subtleTextColor,
                                            indicatorColor = theme.primaryColor.copy(alpha = 0.16f)
                                        )

                                        NavigationBarItem(
                                            selected = currentTab == "HOME",
                                            onClick = { currentTab = "HOME" },
                                            icon = { Icon(Icons.Default.Home, contentDescription = "الرئيسية", modifier = Modifier.size(20.dp)) },
                                            label = { Text("الرئيسية", fontSize = 10.sp, fontWeight = if (currentTab == "HOME") FontWeight.Bold else FontWeight.Medium) },
                                            colors = navItemColors
                                        )

                                        NavigationBarItem(
                                            selected = currentTab == "FILES",
                                            onClick = { currentTab = "FILES" },
                                            icon = { Icon(Icons.Default.Folder, contentDescription = "المصادر", modifier = Modifier.size(20.dp)) },
                                            label = { Text(AppLocalization.string(currentLang, "tab_files"), fontSize = 10.sp, fontWeight = if (currentTab == "FILES") FontWeight.Bold else FontWeight.Medium) },
                                            colors = navItemColors
                                        )

                                        NavigationBarItem(
                                            selected = currentTab == "EXTRACTION",
                                            onClick = { currentTab = "EXTRACTION" },
                                            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "الاستخلاص", modifier = Modifier.size(20.dp)) },
                                            label = { Text(AppLocalization.string(currentLang, "tab_extraction"), fontSize = 10.sp, fontWeight = if (currentTab == "EXTRACTION") FontWeight.Bold else FontWeight.Medium) },
                                            colors = navItemColors
                                        )

                                        NavigationBarItem(
                                            selected = currentTab == "PROJECTS",
                                            onClick = { currentTab = "PROJECTS" },
                                            icon = { Icon(Icons.Default.LibraryBooks, contentDescription = "المشاريع", modifier = Modifier.size(20.dp)) },
                                            label = { Text(AppLocalization.string(currentLang, "tab_projects"), fontSize = 10.sp, fontWeight = if (currentTab == "PROJECTS") FontWeight.Bold else FontWeight.Medium) },
                                            colors = navItemColors
                                        )

                                        NavigationBarItem(
                                            selected = currentTab == "DOCUMENTS",
                                            onClick = { currentTab = "DOCUMENTS" },
                                            icon = { Icon(Icons.Default.FolderSpecial, contentDescription = "عارض المستندات", modifier = Modifier.size(20.dp)) },
                                            label = { Text(AppLocalization.string(currentLang, "tab_documents"), fontSize = 10.sp, fontWeight = if (currentTab == "DOCUMENTS") FontWeight.Bold else FontWeight.Medium) },
                                            colors = navItemColors
                                        )

                                        NavigationBarItem(
                                            selected = currentTab == "SETTINGS",
                                            onClick = { currentTab = "SETTINGS" },
                                            icon = { Icon(Icons.Default.Settings, contentDescription = "الإعدادات", modifier = Modifier.size(20.dp)) },
                                            label = { Text(AppLocalization.string(currentLang, "tab_settings"), fontSize = 10.sp, fontWeight = if (currentTab == "SETTINGS") FontWeight.Bold else FontWeight.Medium) },
                                            colors = navItemColors
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
                                AnimatedContent(
                                    targetState = currentTab,
                                    transitionSpec = {
                                        fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                                    },
                                    label = "MainTabTransition"
                                ) { targetTab ->
                                    when (targetTab) {
                                        "HOME" -> DashboardScreen(
                                            viewModel = viewModel,
                                            theme = theme,
                                            onOpenFiles = { currentTab = "FILES" },
                                            onOpenExtraction = { currentTab = "EXTRACTION" },
                                            onOpenProjects = { currentTab = "PROJECTS" },
                                            onOpenDocuments = { currentTab = "DOCUMENTS" },
                                            onOpenConverter = { showArabicConverterStudio = true },
                                            onOpenBrowser = { showBrowserDialog = true },
                                            onOpenAnalytics = { showAnalyticsDialog = true },
                                            onOpenSubscription = { showSubscriptionDialog = true },
                                            onOpenSettings = { currentTab = "SETTINGS" },
                                            onStartAudio = {
                                                val textToRead = activeChapters.firstOrNull()?.content ?: ""
                                                if (textToRead.isNotBlank()) viewModel.startTtsReading(textToRead)
                                                else Toast.makeText(this@MainActivity, "لا يوجد نص جاهز للقراءة الصوتية", Toast.LENGTH_SHORT).show()
                                            }
                                        )

                                        "FILES" -> FilesScreen(
                                            viewModel = viewModel,
                                            onNavigateToExtraction = { currentTab = "EXTRACTION" },
                                            onOpenDeviceDocuments = { currentTab = "DOCUMENTS" }
                                        )
                                        "EXTRACTION" -> ExtractionScreen(
                                            viewModel = viewModel,
                                            onNavigateToEditor = { isEditingProject = true },
                                            onNavigateToFiles = { currentTab = "FILES" }
                                        )
                                        "PROJECTS" -> ProjectsScreen(
                                            viewModel = viewModel,
                                            onOpenEditor = {
                                                isEditingProject = true
                                            },
                                            onOpenDeviceDocuments = {
                                                currentTab = "DOCUMENTS"
                                            },
                                            onOpenDirectDocument = { uriStr, docName ->
                                                viewingDocUri = uriStr
                                                viewingDocName = docName
                                            },
                                            onNavigateToFiles = {
                                                currentTab = "FILES"
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

                if (showCommandPalette) {
                    GlobalCommandPaletteDialog(
                        theme = theme,
                        viewModel = viewModel,
                        onDismiss = { showCommandPalette = false },
                        onNavigateToTab = { tab -> currentTab = tab },
                        onOpenProjectInEditor = { isEditingProject = true },
                        onOpenDirectDocument = { uriStr, docName ->
                            viewingDocUri = uriStr
                            viewingDocName = docName
                        },
                        onTriggerGlobalDialog = { dialogType ->
                            when (dialogType) {
                                "ANALYTICS" -> showAnalyticsDialog = true
                                "CONVERTER" -> showArabicConverterStudio = true
                                "BROWSER" -> showBrowserDialog = true
                                "HELP" -> showHelpDialog = true
                            }
                        }
                    )
                }
            }
        }
    }
}
