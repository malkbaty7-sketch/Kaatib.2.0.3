package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.SavedBooksViewModel
import com.example.util.AppLanguage
import com.example.util.AppLocalization

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    booksViewModel: SavedBooksViewModel? = null,
    onStartProjectFromBook: ((String, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val theme by viewModel.currentTheme.collectAsState()
    val emotionGlobalActive by viewModel.emotionSuggestionsGlobalActive.collectAsState()
    val files by viewModel.allFiles.collectAsState()
    val projects by viewModel.allProjects.collectAsState()
    val extractions by viewModel.allExtractedTexts.collectAsState()
    val currentLang by viewModel.appLanguage.collectAsState()
    val isVip by viewModel.isVipUser.collectAsState()
    val isLoggedIn by viewModel.isUserLoggedIn.collectAsState()
    val parentalConfig by viewModel.parentalControlConfig.collectAsState()
    val syncMeta by viewModel.syncMetadata.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()

    // Sub-branches inside Settings
    var showThemesBranch by remember { mutableStateOf(false) }
    var showSavedBooks by remember { mutableStateOf(false) }
    var showCoverStudio by remember { mutableStateOf(false) }
    val savedBooksList by booksViewModel?.allBooks?.collectAsState() ?: remember { mutableStateOf(emptyList()) }

    BackHandler(enabled = showThemesBranch || showSavedBooks || showCoverStudio) {
        when {
            showThemesBranch -> showThemesBranch = false
            showCoverStudio -> showCoverStudio = false
            showSavedBooks -> showSavedBooks = false
        }
    }

    if (showThemesBranch) {
        ThemesScreen(
            viewModel = viewModel,
            onBack = { showThemesBranch = false }
        )
    } else if (showCoverStudio) {
        BookCoversScreen(
            viewModel = viewModel,
            theme = theme,
            onBack = { showCoverStudio = false }
        )
    } else if (showSavedBooks && booksViewModel != null) {
        SavedBooksScreen(
            booksViewModel = booksViewModel,
            theme = theme,
            onBack = { showSavedBooks = false },
            onStartProjectFromBook = { title, author ->
                showSavedBooks = false
                onStartProjectFromBook?.invoke(title, author)
            }
        )
    } else {
        // Dialog trigger states
        var showHelpDialog by remember { mutableStateOf(false) }
        var helpInitialTab by remember { mutableStateOf("FAQ") }
        var showBrowserDialog by remember { mutableStateOf(false) }
        var showProfileDialog by remember { mutableStateOf(false) }
        var showSubscriptionDialog by remember { mutableStateOf(false) }
        var showParentalDialog by remember { mutableStateOf(false) }
        var showAdminDialog by remember { mutableStateOf(false) }
        var showAnalyticsDialog by remember { mutableStateOf(false) }
        var showPreflightDialog by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Compact Settings Header Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(theme.elevationDp, RoundedCornerShape(theme.cornerRadiusDp), spotColor = theme.shadowColor),
                shape = RoundedCornerShape(theme.cornerRadiusDp),
                colors = CardDefaults.cardColors(containerColor = theme.surfaceColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.primaryColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = AppLocalization.string(currentLang, "app_name") + " - " + AppLocalization.string(currentLang, "settings_title"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.textColor
                        )
                        Text(
                            text = "إعدادات المظهر والثيمات، الحساب، أدوات التأليف، والنظام",
                            fontSize = 10.sp,
                            color = theme.textColor.copy(alpha = 0.75f)
                        )
                    }
                }
            }

            // =========================================================================
            // 1. قسم المظهر والثيمات والتخصيص البصري (Appearance, 3D Themes Branch & Language)
            // =========================================================================
            SettingsSectionHeader(
                title = "١. المظهر والثيمات واللغة",
                icon = Icons.Default.Palette,
                color = theme.primaryColor
            )

            // 1.A Branch: 3D Themes (مدمجة كفرع رئيسي داخل الإعدادات)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(12.dp))
                    .border(1.dp, theme.primaryColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .clickable { showThemesBranch = true },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.primaryColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "فرع الثيمات والتخصيص البصري 3D",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = theme.textColor
                                )
                                Surface(
                                    color = theme.primaryColor.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = theme.name,
                                        fontSize = 9.sp,
                                        color = theme.primaryColor,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "تخصيص ألوان التطبيق، إضاءة الأوراق الدافئة، وعمق الظلال ثلاثية الأبعاد",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(theme.primaryColor)
                        )
                        Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // 1.B Branch: Book Cover Templates Studio
            SettingsBranchRowCard(
                title = "فرع استوديو نماذج أغلفة الكتب",
                subtitle = "تصفح النماذج الجاهزة، رفع صور من الجوال، وتخصيص عناوين الأغلفة",
                icon = Icons.Default.PhotoLibrary,
                iconBg = Color(0xFF854D0E),
                textColor = theme.textColor,
                onClick = { showCoverStudio = true }
            )

            // 1.C Language Selection (Compact)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(AppLocalization.string(currentLang, "language_selection"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = theme.textColor)
                        Text(
                            text = if (currentLang.layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) "RTL (يمين لليسار)" else "LTR (Left-to-Right)",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = currentLang == AppLanguage.ARABIC,
                            onClick = { viewModel.setAppLanguage(AppLanguage.ARABIC) },
                            label = { Text("🇸🇦 العربية", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f).height(30.dp)
                        )
                        FilterChip(
                            selected = currentLang == AppLanguage.ENGLISH,
                            onClick = { viewModel.setAppLanguage(AppLanguage.ENGLISH) },
                            label = { Text("🇺🇸 English", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f).height(30.dp)
                        )
                        FilterChip(
                            selected = currentLang == AppLanguage.SPANISH,
                            onClick = { viewModel.setAppLanguage(AppLanguage.SPANISH) },
                            label = { Text("🇪🇸 Español", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f).height(30.dp)
                        )
                        FilterChip(
                            selected = currentLang == AppLanguage.FRENCH,
                            onClick = { viewModel.setAppLanguage(AppLanguage.FRENCH) },
                            label = { Text("🇫🇷 Français", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f).height(30.dp)
                        )
                    }
                }
            }

            // =========================================================================
            // 2. قسم المكتبة وأدوات الكاتب والإنتاجية (Library, Writing Tools & Analytics)
            // =========================================================================
            SettingsSectionHeader(
                title = "٢. المكتبة وأدوات التأليف والإنتاجية",
                icon = Icons.Default.AutoStories,
                color = Color(0xFF0F766E)
            )

            // 2.A Saved Books Library Branch
            SettingsBranchRowCard(
                title = "مكتبة الكتب المحفوظة",
                subtitle = "إدارة وحفظ المراجع وقوائم القراءة والتقييمات محلياً بدون إنترنت",
                badgeText = "${savedBooksList.size} كتاب",
                badgeColor = Color(0xFF0F766E),
                icon = Icons.Default.MenuBook,
                iconBg = Color(0xFF0F766E),
                textColor = theme.textColor,
                onClick = { showSavedBooks = true }
            )

            // 2.B Writing Analytics & Productivity
            SettingsBranchRowCard(
                title = "إحصائيات وتحليلات الإنتاجية 📊",
                subtitle = "سرعة التأليف (WPM)، أيام الالتزام المتتالية 🔥، ورسوم بيانية أسبوعية",
                badgeText = "مباشر",
                badgeColor = Color(0xFF0D9488),
                icon = Icons.Default.Insights,
                iconBg = Color(0xFF0D9488),
                textColor = theme.textColor,
                onClick = { showAnalyticsDialog = true }
            )

            // 2.C Pre-flight Publishing Check
            SettingsBranchRowCard(
                title = "فحص الجودة والامتثال للنشر (Pre-flight) ✈️",
                subtitle = "مطابقة معايير Amazon KDP، فحص دقة 300 DPI، والهوامش الحية",
                icon = Icons.Default.Verified,
                iconBg = Color(0xFF2563EB),
                textColor = theme.textColor,
                onClick = { showPreflightDialog = true }
            )

            // 2.D Research & PDF Browser
            SettingsBranchRowCard(
                title = "متصفح البحوث والكتب الإلكترونية PDF",
                subtitle = "بحث متقدم عن كتب ومراجع PDF وتنزيلها مباشرة داخل التطبيق",
                icon = Icons.Default.Public,
                iconBg = Color(0xFF8B5CF6),
                textColor = theme.textColor,
                onClick = { showBrowserDialog = true }
            )

            // 2.E Global Emotion Engine Toggle
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color(0xFFEC4899)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Text(
                                text = "محرك المشاعر الذكي (FR-ES-04)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.textColor
                            )
                            Text(
                                text = "إظهار الاقتراحات الشعورية وبالونات الإلهام داخل شاشة الكتابة",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    Switch(
                        checked = emotionGlobalActive,
                        onCheckedChange = { viewModel.emotionSuggestionsGlobalActive.value = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = theme.primaryColor)
                    )
                }
            }

            // =========================================================================
            // 3. قسم الحساب والمزامنة السحابية والاشتراكات (Account, Cloud Sync & VIP)
            // =========================================================================
            SettingsSectionHeader(
                title = "٣. الحساب والمزامنة السحابية والعضوية",
                icon = Icons.Default.CloudSync,
                color = Color(0xFF0284C7)
            )

            // 3.A User Profile & Login
            SettingsBranchRowCard(
                title = "الملف الشخصي وتسجيل الدخول",
                subtitle = if (isLoggedIn) "مسجل الدخول • إدارة أعمالك المنشورة وإتاحتها للعامة" else "تسجيل الدخول عبر Google أو الهاتف أو البريد مع OTP",
                badgeText = if (isLoggedIn) "متصل" else null,
                badgeColor = Color(0xFF10B981),
                icon = Icons.Default.Person,
                iconBg = if (isLoggedIn) Color(0xFF10B981) else Color(0xFF64748B),
                textColor = theme.textColor,
                onClick = { showProfileDialog = true }
            )

            // 3.B VIP Subscription
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(12.dp))
                    .clickable { showSubscriptionDialog = true },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = if (isVip) Color(0xFFFEF3C7) else Color.White)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color(0xFFD97706)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("الاشتراكات المميزة (Katib VIP) 💳", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = theme.textColor)
                                if (isVip) {
                                    Surface(color = Color(0xFFD97706), shape = RoundedCornerShape(4.dp)) {
                                        Text("مُفعل", fontSize = 9.sp, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                    }
                                }
                            }
                            Text("إزالة الإعلانات، وتوليد صور بالذكاء الاصطناعي بدون حدود", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                    Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                }
            }

            // 3.C Google Drive Cloud Sync Card (Compact)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(Color(0xFF0284C7)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            Column {
                                Text(
                                    text = "المزامنة السحابية (Google Drive API)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = theme.textColor
                                )
                                Text(
                                    text = "الحساب: ${syncMeta.connectedAccountEmail ?: "غير متصل"} • ${syncMeta.lastSyncMessage}",
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        if (syncStatus == com.example.data.sync.SyncStatus.SYNCING) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = theme.primaryColor)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("مزامنة تلقائية", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Switch(
                                checked = syncMeta.autoSyncEnabled,
                                onCheckedChange = { viewModel.updateAutoSyncConfig(it, syncMeta.syncOnlyOnWifi) }
                            )
                        }
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("عبر Wi-Fi فقط", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Switch(
                                checked = syncMeta.syncOnlyOnWifi,
                                onCheckedChange = { viewModel.updateAutoSyncConfig(syncMeta.autoSyncEnabled, it) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.performDriveSync(forceManual = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(34.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("مزامنة الآن", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.restoreFromDriveBackup() },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(34.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(14.dp), tint = theme.primaryColor)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("استعادة النسخة", fontSize = 11.sp)
                        }
                    }
                }
            }

            // =========================================================================
            // 4. قسم الأمان والإدارة والدعم الفني (Security, Support & System Info)
            // =========================================================================
            SettingsSectionHeader(
                title = "٤. الحماية والإدارة والدعم الفني",
                icon = Icons.Default.Security,
                color = Color(0xFF475569)
            )

            // 4.A Parental Control
            SettingsBranchRowCard(
                title = "الرقابة الأبوية وحماية المحتوى",
                subtitle = if (parentalConfig.isEnabled) "مُفعلة • حظر المحتوى غير المناسب وقفل PIN" else "تعطيل أو تفعيل الرقابة الأبوية برمز PIN",
                badgeText = if (parentalConfig.isEnabled) "نشط" else null,
                badgeColor = Color(0xFF0284C7),
                icon = Icons.Default.FamilyRestroom,
                iconBg = Color(0xFF0284C7),
                textColor = theme.textColor,
                onClick = { showParentalDialog = true }
            )

            // 4.B Help & Support
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "المساعدة والدعم:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = theme.textColor,
                        modifier = Modifier.weight(0.8f)
                    )
                    OutlinedButton(
                        onClick = {
                            helpInitialTab = "FAQ"
                            showHelpDialog = true
                        },
                        modifier = Modifier.weight(1f).height(34.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.HelpOutline, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("التعليمات (?)", fontSize = 10.sp)
                    }
                    Button(
                        onClick = {
                            helpInitialTab = "SUPPORT"
                            showHelpDialog = true
                        },
                        modifier = Modifier.weight(1f).height(34.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("فريق الدعم", fontSize = 10.sp)
                    }
                }
            }

            // 4.C Admin Dashboard
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(12.dp))
                    .clickable { showAdminDialog = true },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color(0xFF38BDF8).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("لوحة تحكم المدير ومحرر الأكواد", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(13.dp))
                            }
                            Text("إدارة المستخدمين والصلاحيات ومراقبة النشاط", fontSize = 10.sp, color = Color(0xFF94A3B8))
                        }
                    }
                    Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(18.dp))
                }
            }

            // 4.D Combined Database Stats & About Application Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "بيانات التخزين المحلي ومعلومات الإصدار",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = theme.textColor
                        )
                        Text("الإصدار 2.0.3", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.primaryColor)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        StatItem(title = "المصادر", value = "${files.size}", theme = theme)
                        StatItem(title = "الكتب", value = "${savedBooksList.size}", theme = theme)
                        StatItem(title = "المشاريع", value = "${projects.size}", theme = theme)
                        StatItem(title = "الاستخلاصات", value = "${extractions.size}", theme = theme)
                    }
                }
            }
        }

        // Dialogs
        if (showHelpDialog) {
            HelpAndSupportDialog(
                theme = theme,
                viewModel = viewModel,
                initialTab = helpInitialTab,
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

        if (showProfileDialog) {
            UserProfileAndAuthDialog(
                theme = theme,
                viewModel = viewModel,
                onDismiss = { showProfileDialog = false }
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

        if (showPreflightDialog) {
            val report = remember { viewModel.runPreflightValidation(context) }
            com.example.ui.components.PreflightValidationDialog(
                report = report,
                theme = theme,
                onDismiss = { showPreflightDialog = false },
                onProceedExportEpub = {
                    val f = viewModel.exportToEpub(context)
                    if (f != null) {
                        android.widget.Toast.makeText(context, "تم تصدير حزمة EPUB3 بنجاح: ${f.name}", android.widget.Toast.LENGTH_LONG).show()
                    }
                },
                onProceedExportPdf = {
                    val f = viewModel.exportToPdf(context)
                    if (f != null) {
                        android.widget.Toast.makeText(context, "تم تصدير ملف PDF بنجاح: ${f.name}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            )
        }

        if (showParentalDialog) {
            ParentalControlDialog(
                theme = theme,
                viewModel = viewModel,
                onDismiss = { showParentalDialog = false }
            )
        }

        if (showAdminDialog) {
            AdminDashboardDialog(
                theme = theme,
                viewModel = viewModel,
                onDismiss = { showAdminDialog = false }
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    title: String,
    icon: ImageVector,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f).padding(start = 6.dp),
            color = color.copy(alpha = 0.2f)
        )
    }
}

@Composable
private fun SettingsBranchRowCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBg: Color,
    textColor: Color,
    badgeText: String? = null,
    badgeColor: Color = iconBg,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = textColor
                        )
                        if (badgeText != null) {
                            Surface(
                                color = badgeColor.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = badgeText,
                                    fontSize = 9.sp,
                                    color = badgeColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = subtitle,
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
            Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun StatItem(title: String, value: String, theme: com.example.data.model.AppThemeData) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = theme.primaryColor)
        Text(title, fontSize = 10.sp, color = Color.Gray)
    }
}
