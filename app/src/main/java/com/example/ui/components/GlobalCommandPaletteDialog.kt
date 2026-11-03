package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AppThemeData
import com.example.data.model.Project
import com.example.data.model.defaultAppThemes
import com.example.ui.MainViewModel

private data class SpotlightCommandItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: String, // COMMAND, PROJECT, DOCUMENT, SOURCE
    val badgeText: String,
    val badgeColor: Color,
    val icon: ImageVector,
    val onExecute: () -> Unit
)

@Composable
fun GlobalCommandPaletteDialog(
    theme: AppThemeData,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onNavigateToTab: (String) -> Unit,
    onOpenProjectInEditor: (Project) -> Unit,
    onOpenDirectDocument: (uriString: String, fileName: String) -> Unit,
    onTriggerGlobalDialog: (String) -> Unit // "ANALYTICS", "CONVERTER", "BROWSER", "HELP"
) {
    val context = LocalContext.current
    val projects by viewModel.allProjects.collectAsState()
    val files by viewModel.allFiles.collectAsState()
    val allDocProgress by viewModel.allDocumentProgress.collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }

    val allItems = remember(projects, files, allDocProgress, theme) {
        val list = mutableListOf<SpotlightCommandItem>()

        // 1. Quick System & Studio Commands
        list.add(
            SpotlightCommandItem(
                id = "cmd_new_book",
                title = "إنشاء مشروع كتاب إلكتروني جديد",
                subtitle = "بدء مشروع كتاب متكامل مع غلاف وفصول",
                category = "COMMAND",
                badgeText = "أمر فوري ⚡",
                badgeColor = Color(0xFF2563EB),
                icon = Icons.Default.AutoStories,
                onExecute = {
                    viewModel.createProject(
                        title = "كتاب جديد ${projects.size + 1}",
                        type = "E_BOOK",
                        templateId = "novel_action",
                        emotions = listOf("warmth", "motivation"),
                        onCreated = { created ->
                            onDismiss()
                            onOpenProjectInEditor(created)
                        }
                    )
                }
            )
        )
        list.add(
            SpotlightCommandItem(
                id = "cmd_theme_cycle",
                title = "تبديل الثيم والمظهر البصري (${theme.name})",
                subtitle = "التنقل الفوري بين الوضع النهاري، الرق العربي Sepia، والليل الملكي Dark",
                category = "COMMAND",
                badgeText = "مظهر 🎨",
                badgeColor = Color(0xFF9333EA),
                icon = Icons.Default.Palette,
                onExecute = {
                    val idx = defaultAppThemes.indexOfFirst { it.id == theme.id }
                    val nextTheme = defaultAppThemes[(idx + 1) % defaultAppThemes.size]
                    viewModel.currentThemeId.value = nextTheme.id
                    Toast.makeText(context, "🎨 تم التبديل إلى: ${nextTheme.name}", Toast.LENGTH_SHORT).show()
                }
            )
        )
        list.add(
            SpotlightCommandItem(
                id = "cmd_doc_viewer",
                title = "فتح عارض ومدير المستندات الشامل",
                subtitle = "تصفح ملفات PDF, Word, EPUB, Excel وخزنة المستندات",
                category = "COMMAND",
                badgeText = "انتقال 📂",
                badgeColor = Color(0xFF0D9488),
                icon = Icons.Default.FolderSpecial,
                onExecute = {
                    onDismiss()
                    onNavigateToTab("DOCUMENTS")
                }
            )
        )
        list.add(
            SpotlightCommandItem(
                id = "cmd_extraction_studio",
                title = "فتح استوديو الاستخلاص الذكي والـ OCR",
                subtitle = "استخراج النصوص والجداول من الصور والملفات وتنظيفها",
                category = "COMMAND",
                badgeText = "أداة 🔬",
                badgeColor = Color(0xFF059669),
                icon = Icons.Default.AutoAwesome,
                onExecute = {
                    onDismiss()
                    onNavigateToTab("EXTRACTION")
                }
            )
        )
        list.add(
            SpotlightCommandItem(
                id = "cmd_converter_tashkeel",
                title = "محوّل الصيغ والتشكيل العربي الذكي",
                subtitle = "تحويل المستندات، التشكيل التلقائي، وضبط النصوص العربية",
                category = "COMMAND",
                badgeText = "استوديو ⚡",
                badgeColor = Color(0xFFD97706),
                icon = Icons.Default.AutoFixHigh,
                onExecute = {
                    onDismiss()
                    onTriggerGlobalDialog("CONVERTER")
                }
            )
        )
        list.add(
            SpotlightCommandItem(
                id = "cmd_analytics",
                title = "لوحة تحليلات الإنتاجية وسرعة الكتابة",
                subtitle = "عرض إحصائيات الكلمات، أيام الالتزام، والرسوم البيانية",
                category = "COMMAND",
                badgeText = "إحصائيات 📊",
                badgeColor = Color(0xFF0284C7),
                icon = Icons.Default.Insights,
                onExecute = {
                    onDismiss()
                    onTriggerGlobalDialog("ANALYTICS")
                }
            )
        )
        list.add(
            SpotlightCommandItem(
                id = "cmd_research_browser",
                title = "متصفح البحوث والمراجع الأكاديمية",
                subtitle = "البحث عن كتب ومصادر PDF وتنزيلها مباشرة",
                category = "COMMAND",
                badgeText = "بحث 🌐",
                badgeColor = Color(0xFF7C3AED),
                icon = Icons.Default.Public,
                onExecute = {
                    onDismiss()
                    onTriggerGlobalDialog("BROWSER")
                }
            )
        )

        // 2. Authored Projects
        projects.sortedByDescending { it.lastModified }.forEach { proj ->
            list.add(
                SpotlightCommandItem(
                    id = "proj_${proj.id}",
                    title = proj.projectName,
                    subtitle = "مشروع تأليف • مقاس ${proj.paperSize}",
                    category = "PROJECT",
                    badgeText = "مشروع ✍️",
                    badgeColor = theme.primaryColor,
                    icon = Icons.Default.MenuBook,
                    onExecute = {
                        onDismiss()
                        viewModel.loadProjectIntoEditor(proj)
                        onOpenProjectInEditor(proj)
                    }
                )
            )
        }

        // 3. Recent & Saved Documents
        allDocProgress.sortedByDescending { it.lastReadTimestamp }.forEach { doc ->
            val pct = (doc.totalProgressPercentage * 100).toInt().coerceIn(0, 100)
            list.add(
                SpotlightCommandItem(
                    id = "doc_${doc.fileUri}",
                    title = doc.fileName,
                    subtitle = "استئناف من صفحة ${doc.lastPageIndex + 1} • نسبة الإنجاز $pct%",
                    category = "DOCUMENT",
                    badgeText = "${doc.fileType} 📖",
                    badgeColor = Color(0xFFDC2626),
                    icon = Icons.Default.Description,
                    onExecute = {
                        onDismiss()
                        onOpenDirectDocument(doc.fileUri, doc.fileName)
                    }
                )
            )
        }

        // 4. Uploaded Source Files
        files.forEach { file ->
            list.add(
                SpotlightCommandItem(
                    id = "src_${file.id}",
                    title = file.fileName,
                    subtitle = "مصدر محفوظ للاستخلاص (${file.fileType.uppercase()})",
                    category = "SOURCE",
                    badgeText = "مصدر 📂",
                    badgeColor = Color(0xFF059669),
                    icon = Icons.Default.Folder,
                    onExecute = {
                        onDismiss()
                        viewModel.selectedFileIdsForExtraction.value = setOf(file.id)
                        onNavigateToTab("EXTRACTION")
                    }
                )
            )
        }

        list
    }

    val filteredItems = remember(allItems, searchQuery, selectedCategory) {
        allItems.filter { item ->
            val matchesCat = selectedCategory == "ALL" || item.category == selectedCategory
            val matchesQuery = searchQuery.isBlank() ||
                item.title.contains(searchQuery, ignoreCase = true) ||
                item.subtitle.contains(searchQuery, ignoreCase = true) ||
                item.badgeText.contains(searchQuery, ignoreCase = true)
            matchesCat && matchesQuery
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.82f)
                .shadow(20.dp, RoundedCornerShape(24.dp), spotColor = theme.shadowColor)
                .border(1.5.dp, theme.primaryColor.copy(alpha = 0.35f), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = theme.elevatedCardColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Bar
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
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(theme.primaryColor, theme.secondaryColor)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ManageSearch, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Column {
                            Text(
                                text = "مركز الأوامر والبحث الشامل الفوري",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = theme.textColor
                            )
                            Text(
                                text = "ابحث في المشاريع، الكتب، المصادر أو نفّذ أي أمر بلمسة واحدة",
                                fontSize = 10.sp,
                                color = theme.subtleTextColor
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(theme.cardColor)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = theme.textColor, modifier = Modifier.size(16.dp))
                    }
                }

                // Spotlight Search Input Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "اكتب اسم كتاب، مشروع، مصدر، أو أمر سريع...",
                            fontSize = 12.sp,
                            color = theme.subtleTextColor
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = theme.primaryColor)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "مسح", tint = theme.subtleTextColor)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.primaryColor,
                        unfocusedBorderColor = theme.borderColor,
                        focusedContainerColor = theme.cardColor,
                        unfocusedContainerColor = theme.cardColor,
                        focusedTextColor = theme.textColor,
                        unfocusedTextColor = theme.textColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Category Filter Pills
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val cats = listOf(
                        "ALL" to "الكل (${allItems.size})",
                        "COMMAND" to "أوامر سريعة ⚡",
                        "PROJECT" to "المشاريع (${projects.size})",
                        "DOCUMENT" to "الكتب المقروءة (${allDocProgress.size})",
                        "SOURCE" to "المصادر (${files.size})"
                    )
                    items(cats) { (catKey, catLabel) ->
                        val isSelected = selectedCategory == catKey
                        Surface(
                            onClick = { selectedCategory = catKey },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) theme.primaryColor else theme.cardColor,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) theme.primaryColor else theme.borderColor
                            )
                        ) {
                            Text(
                                text = catLabel,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else theme.textColor,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Results List
                if (filteredItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = theme.subtleTextColor,
                                modifier = Modifier.size(42.dp)
                            )
                            Text(
                                text = "لا توجد نتائج مطابقة لـ «$searchQuery»",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.textColor
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredItems, key = { it.id }) { item ->
                            Surface(
                                onClick = item.onExecute,
                                shape = RoundedCornerShape(14.dp),
                                color = theme.cardColor,
                                border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(item.badgeColor.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            item.icon,
                                            contentDescription = null,
                                            tint = item.badgeColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = theme.textColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.subtitle,
                                            fontSize = 10.sp,
                                            color = theme.subtleTextColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = item.badgeColor.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = item.badgeText,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = item.badgeColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpeedDialFabMenu(
    theme: AppThemeData,
    viewModel: MainViewModel,
    onCreateNewProject: (Project) -> Unit,
    onNavigateToDocuments: () -> Unit,
    onNavigateToExtraction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    var showQuickIdeaDialog by remember { mutableStateOf(false) }
    var quickIdeaText by remember { mutableStateOf("") }

    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 45f else 0f,
        label = "FabRotation"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpeedDialActionPill(
                    label = "تدوين فكرة سريعة للمحرر 💡",
                    icon = Icons.Default.Lightbulb,
                    accentColor = Color(0xFFD97706),
                    theme = theme,
                    onClick = {
                        isExpanded = false
                        showQuickIdeaDialog = true
                    }
                )
                SpeedDialActionPill(
                    label = "استخلاص وتحليل ذكي (OCR) 🔬",
                    icon = Icons.Default.AutoAwesome,
                    accentColor = Color(0xFF059669),
                    theme = theme,
                    onClick = {
                        isExpanded = false
                        onNavigateToExtraction()
                    }
                )
                SpeedDialActionPill(
                    label = "استيراد وقراءة مستند 📂",
                    icon = Icons.Default.FolderSpecial,
                    accentColor = Color(0xFF0284C7),
                    theme = theme,
                    onClick = {
                        isExpanded = false
                        onNavigateToDocuments()
                    }
                )
                SpeedDialActionPill(
                    label = "مشروع تأليف جديد 📝",
                    icon = Icons.Default.PostAdd,
                    accentColor = theme.primaryColor,
                    theme = theme,
                    onClick = {
                        isExpanded = false
                        val count = viewModel.allProjects.value.size + 1
                        viewModel.createProject(
                            title = "مشروع إبداعي $count",
                            type = "E_BOOK",
                            templateId = "novel_action",
                            emotions = listOf("warmth", "motivation"),
                            onCreated = { created ->
                                onCreateNewProject(created)
                            }
                        )
                    }
                )
            }
        }

        FloatingActionButton(
            onClick = { isExpanded = !isExpanded },
            containerColor = theme.primaryColor,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .size(50.dp)
                .shadow(10.dp, CircleShape, spotColor = theme.shadowColor)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "إجراءات سريعة",
                modifier = Modifier
                    .size(24.dp)
                    .rotate(rotation)
            )
        }
    }

    if (showQuickIdeaDialog) {
        AlertDialog(
            onDismissRequest = { showQuickIdeaDialog = false },
            containerColor = theme.elevatedCardColor,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color(0xFFD97706))
                    Text(
                        "تدوين فكرة أو اقتباس سريع للمحرر 💡",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textColor
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "اكتب الفكرة أو الفقرة هنا وسيتم إرسالها فوراً إلى آخر مشروع نشط (أو إنشاء مسودة جديدة):",
                        fontSize = 11.sp,
                        color = theme.subtleTextColor
                    )
                    OutlinedTextField(
                        value = quickIdeaText,
                        onValueChange = { quickIdeaText = it },
                        placeholder = { Text("اكتب فكرتك أو خاطرتك الإبداعية هنا...", fontSize = 12.sp) },
                        minLines = 4,
                        maxLines = 7,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val content = quickIdeaText.trim()
                        if (content.isNotBlank()) {
                            val latest = viewModel.allProjects.value.maxByOrNull { it.lastModified }
                            if (latest != null) {
                                viewModel.loadProjectIntoEditor(latest)
                                val chContent = viewModel.activeChapters.value.firstOrNull()?.content ?: ""
                                val updated = if (chContent.isBlank()) content else "$chContent\n\n$content"
                                viewModel.updateCurrentChapterContent(updated)
                                viewModel.saveCurrentProject()
                                Toast.makeText(context, "✨ تمت إضافة الفكرة إلى مشروع «${latest.projectName}»", Toast.LENGTH_SHORT).show()
                                showQuickIdeaDialog = false
                                quickIdeaText = ""
                                onCreateNewProject(latest)
                            } else {
                                viewModel.createProject(
                                    title = "مسودة أفكار إبداعية",
                                    type = "E_BOOK",
                                    templateId = "novel_action",
                                    emotions = listOf("warmth"),
                                    initialContent = content,
                                    onCreated = { created ->
                                        showQuickIdeaDialog = false
                                        quickIdeaText = ""
                                        onCreateNewProject(created)
                                    }
                                )
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("إرسال وفتح بالمحرر 🚀")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickIdeaDialog = false }) {
                    Text("إلغاء", color = theme.subtleTextColor)
                }
            }
        )
    }
}

@Composable
private fun SpeedDialActionPill(
    label: String,
    icon: ImageVector,
    accentColor: Color,
    theme: AppThemeData,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = theme.elevatedCardColor,
        shadowElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = theme.textColor
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
            }
        }
    }
}
