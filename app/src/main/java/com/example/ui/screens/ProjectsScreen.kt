package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Project
import com.example.data.model.defaultWritingTemplates
import com.example.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

private data class QuickStarterTemplateCard(
    val id: String,
    val title: String,
    val subtitle: String,
    val projectType: String,
    val templateId: String,
    val badgeText: String,
    val gradientColors: List<Color>,
    val icon: ImageVector,
    val initialText: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    viewModel: MainViewModel,
    onOpenEditor: (Project) -> Unit,
    onOpenDeviceDocuments: () -> Unit = {},
    onOpenDirectDocument: (uriString: String, fileName: String) -> Unit = { _, _ -> },
    onNavigateToFiles: () -> Unit = {}
) {
    val context = LocalContext.current
    val projects by viewModel.allProjects.collectAsState()
    val files by viewModel.allFiles.collectAsState()
    val extractions by viewModel.allExtractedTexts.collectAsState()
    val allDocProgress by viewModel.allDocumentProgress.collectAsState(initial = emptyList())
    val activeChapters by viewModel.activeChapters.collectAsState()
    val theme by viewModel.currentTheme.collectAsState()
    var showNewProjectDialog by remember { mutableStateOf(false) }

    // UI States: Analytics Hub Toggle, Quick Starter Templates, Bookshelf Grid View Toggle, Smart Filter & Quick-Peek Sheet
    var showAnalyticsStrip by remember { mutableStateOf(true) }
    var showStarterTemplatesRow by remember { mutableStateOf(true) }
    var isBookshelfGridView by remember { mutableStateOf(false) }
    var selectedTypeFilter by remember { mutableStateOf("ALL") } // ALL, E_BOOK, RESEARCH_PAPER, ARTICLE
    var quickPeekProject by remember { mutableStateOf<Project?>(null) }

    val quickStarterTemplates = remember {
        listOf(
            QuickStarterTemplateCard(
                id = "starter_novel",
                title = "رواية أدبية متكاملة",
                subtitle = "حبكة سردية وفصول مجهزة للانطلاق",
                projectType = "E_BOOK",
                templateId = "novel_action",
                badgeText = "٥ فصول جاهزة 📖",
                gradientColors = listOf(Color(0xFF1E3A8A), Color(0xFF3B82F6)),
                icon = Icons.Default.MenuBook,
                initialText = "الفصل الأول: البداية\n\nفي تلك الليلة الهادئة، بدأت ملامح الحكاية تتشكل بين السطور..."
            ),
            QuickStarterTemplateCard(
                id = "starter_research",
                title = "بحث جامعي محكّم",
                subtitle = "مقدمة، منهجية، نتائج، ومراجع",
                projectType = "RESEARCH_PAPER",
                templateId = "academic_paper",
                badgeText = "هيكل أكاديمي 🎓",
                gradientColors = listOf(Color(0xFF065F46), Color(0xFF10B981)),
                icon = Icons.Default.School,
                initialText = "ملخص البحث (Abstract):\nيتناول هذا البحث دراسة تحليلية معمقة حول...\n\n١. المقدمة ومشكلة البحث:\n\n٢. المنهجية المتبعة:\n\n٣. النتائج والتوصيات:"
            ),
            QuickStarterTemplateCard(
                id = "starter_manuscript",
                title = "كتاب تراثي ومخطوطة",
                subtitle = "تنسيق كلاسيكي للأدب والتاريخ",
                projectType = "E_BOOK",
                templateId = "historical_book",
                badgeText = "طابع تراثي 📜",
                gradientColors = listOf(Color(0xFF78350F), Color(0xFFD97706)),
                icon = Icons.Default.HistoryEdu,
                initialText = "بسم الله الرحمن الرحيم\n\nمقدمة المؤلف:\nالحمد لله الذي علم بالقلم، أما بعد فإن هذا الكتاب يجمع درر الفوائد..."
            ),
            QuickStarterTemplateCard(
                id = "starter_journal",
                title = "مذكرات وتأملات فكرية",
                subtitle = "تدوين يومي للمقالات والخواطر",
                projectType = "ARTICLE",
                templateId = "daily_journal",
                badgeText = "انطلاق سريع 📓",
                gradientColors = listOf(Color(0xFF581C87), Color(0xFF9333EA)),
                icon = Icons.Default.EditNote,
                initialText = "تأملات اليوم:\n\nالفكرة الرئيسية التي تشغل البال اليوم هي..."
            )
        )
    }

    val latestProject = remember(projects) {
        projects.maxByOrNull { it.lastModified }
    }
    val latestReadDoc = remember(allDocProgress) {
        allDocProgress.maxByOrNull { it.lastReadTimestamp }
    }

    val filteredProjects = remember(projects, selectedTypeFilter) {
        if (selectedTypeFilter == "ALL") projects
        else projects.filter { it.projectType == selectedTypeFilter }
    }

    // Productivity & Reading Goal Metrics for the Canvas Ring
    val avgReadingProgress = remember(allDocProgress) {
        if (allDocProgress.isEmpty()) 0f
        else allDocProgress.map { it.totalProgressPercentage }.average().toFloat().coerceIn(0f, 1f)
    }
    val dailyGoalFraction = remember(projects.size, extractions.size, avgReadingProgress) {
        val projectBoost = (projects.size * 0.18f).coerceAtMost(0.55f)
        val extractBoost = (extractions.size * 0.08f).coerceAtMost(0.25f)
        (projectBoost + extractBoost + (avgReadingProgress * 0.4f)).coerceIn(0.12f, 1f)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Smart Hero Dashboard + Visual Productivity & Reading Analytics Hub
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(theme.elevationDp, RoundedCornerShape(20.dp), spotColor = theme.shadowColor),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = if (theme.isDark) {
                                listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                            } else {
                                listOf(theme.primaryColor, theme.secondaryColor)
                            }
                        )
                    )
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.AutoStories,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(23.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "مساحة التأليف والقراءة الذكية",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Text(
                                    text = "${projects.size} مشاريع • ${files.size} مصادر • ${allDocProgress.size} كتب قيد القراءة",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(
                                onClick = { showAnalyticsStrip = !showAnalyticsStrip },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.16f))
                            ) {
                                Icon(
                                    imageVector = if (showAnalyticsStrip) Icons.Default.ExpandLess else Icons.Default.DonutLarge,
                                    contentDescription = "تحليلات الإنجاز",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Button(
                                onClick = { showNewProjectDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = if (theme.isDark) Color(0xFF0F172A) else theme.primaryColor
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("مشروع جديد", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Visual Productivity & Reading Analytics Hub (Collapsible)
                    AnimatedVisibility(visible = showAnalyticsStrip) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.Black.copy(alpha = 0.22f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Daily Goal Ring (Canvas Drawn)
                                Box(
                                    modifier = Modifier.size(50.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawArc(
                                            color = Color.White.copy(alpha = 0.2f),
                                            startAngle = 0f,
                                            sweepAngle = 360f,
                                            useCenter = false,
                                            style = Stroke(width = 5.5.dp.toPx(), cap = StrokeCap.Round)
                                        )
                                        drawArc(
                                            color = Color(0xFFFDE047),
                                            startAngle = -90f,
                                            sweepAngle = 360f * dailyGoalFraction,
                                            useCenter = false,
                                            style = Stroke(width = 5.5.dp.toPx(), cap = StrokeCap.Round)
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "${(dailyGoalFraction * 100).toInt()}%",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "إنجاز",
                                            fontSize = 7.sp,
                                            color = Color(0xFFFDE047)
                                        )
                                    }
                                }

                                // Distribution Bar & Breakdown
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "مؤشر النشاط وتوزيع المكتبة الإبداعية 🔥",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "${extractions.size} فقرة مستخلصة",
                                            fontSize = 9.sp,
                                            color = Color(0xFF6EE7B7),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    val totalCount = (projects.size + files.size + allDocProgress.size).coerceAtLeast(1)
                                    val pWeight = (projects.size.toFloat() / totalCount).coerceAtLeast(0.15f)
                                    val fWeight = (files.size.toFloat() / totalCount).coerceAtLeast(0.15f)
                                    val dWeight = (allDocProgress.size.toFloat() / totalCount).coerceAtLeast(0.15f)

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(pWeight)
                                                .fillMaxHeight()
                                                .background(Color(0xFFFDE047))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .weight(fWeight)
                                                .fillMaxHeight()
                                                .background(Color(0xFF38BDF8))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .weight(dWeight)
                                                .fillMaxHeight()
                                                .background(Color(0xFF34D399))
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("🟡 مشاريع (${projects.size})", fontSize = 8.sp, color = Color.White.copy(alpha = 0.9f))
                                        Text("🔵 مصادر (${files.size})", fontSize = 8.sp, color = Color.White.copy(alpha = 0.9f))
                                        Text("🟢 قراءة (${allDocProgress.size})", fontSize = 8.sp, color = Color.White.copy(alpha = 0.9f))
                                    }
                                }
                            }
                        }
                    }

                    // Instant Resume Strip (for Latest Project or Latest Document)
                    if (latestProject != null || latestReadDoc != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (latestProject != null) {
                                Surface(
                                    onClick = {
                                        viewModel.loadProjectIntoEditor(latestProject)
                                        onOpenEditor(latestProject)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White.copy(alpha = 0.15f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.EditNote, contentDescription = null, tint = Color(0xFFFDE047), modifier = Modifier.size(18.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("متابعة الكتابة فوراً", fontSize = 8.sp, color = Color.White.copy(alpha = 0.8f))
                                            Text(
                                                text = latestProject.projectName,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }

                            if (latestReadDoc != null) {
                                val pct = (latestReadDoc.totalProgressPercentage * 100).toInt().coerceIn(1, 100)
                                Surface(
                                    onClick = {
                                        onOpenDirectDocument(latestReadDoc.fileUri, latestReadDoc.fileName)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White.copy(alpha = 0.15f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = Color(0xFF6EE7B7), modifier = Modifier.size(17.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("استئناف القراءة ($pct%)", fontSize = 8.sp, color = Color.White.copy(alpha = 0.8f))
                                            Text(
                                                text = latestReadDoc.fileName,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. 1-Tap Starter Templates Gallery (قوالب الانطلاق الفوري بلمسة واحدة)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = theme.elevatedCardColor,
            border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size(14.dp))
                        Text(
                            text = "قوالب الانطلاق الفوري بلمسة واحدة 🚀",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.textColor
                        )
                    }
                    Text(
                        text = if (showStarterTemplatesRow) "طي ▲" else "عرض القوالب (٤) ▼",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.primaryColor,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { showStarterTemplatesRow = !showStarterTemplatesRow }
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }

                AnimatedVisibility(visible = showStarterTemplatesRow) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(quickStarterTemplates, key = { it.id }) { tpl ->
                            Surface(
                                onClick = {
                                    viewModel.createProject(
                                        title = "${tpl.title} ${projects.size + 1}",
                                        type = tpl.projectType,
                                        templateId = tpl.templateId,
                                        emotions = listOf("warmth", "motivation"),
                                        initialContent = tpl.initialText,
                                        onCreated = { created ->
                                            Toast.makeText(context, "🚀 تم تجهيز قالب «${tpl.title}» بنجاح!", Toast.LENGTH_SHORT).show()
                                            onOpenEditor(created)
                                        }
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Transparent,
                                modifier = Modifier
                                    .width(164.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Brush.linearGradient(tpl.gradientColors))
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(tpl.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color.White.copy(alpha = 0.22f)
                                        ) {
                                            Text(
                                                text = tpl.badgeText,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = tpl.title,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = tpl.subtitle,
                                        fontSize = 9.sp,
                                        color = Color.White.copy(alpha = 0.85f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Section Header + Bookshelf Grid / List Toggle + Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.CollectionsBookmark, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size(18.dp))
                Text(
                    text = "رفوف المشاريع والكتب المؤلفة (${filteredProjects.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.textColor
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Visual Bookshelf Grid vs List Toggle
                Surface(
                    onClick = { isBookshelfGridView = !isBookshelfGridView },
                    shape = RoundedCornerShape(8.dp),
                    color = theme.elevatedCardColor,
                    border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderColor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isBookshelfGridView) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "تبديل العرض",
                            tint = theme.primaryColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isBookshelfGridView) "قائمة" else "رفوف 3D",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.primaryColor
                        )
                    }
                }

                Surface(
                    onClick = onOpenDeviceDocuments,
                    shape = RoundedCornerShape(8.dp),
                    color = theme.primaryColor.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size(14.dp))
                        Text("عارض المستندات", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.primaryColor)
                    }
                }
            }
        }

        // Smart Project Type Filter Chips
        if (projects.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val filters = listOf(
                    "ALL" to "الكل (${projects.size})",
                    "E_BOOK" to "📖 كتب إلكترونية (${projects.count { it.projectType == "E_BOOK" }})",
                    "RESEARCH_PAPER" to "🎓 بحوث علمية (${projects.count { it.projectType == "RESEARCH_PAPER" }})",
                    "ARTICLE" to "📝 مقالات ومذكرات (${projects.count { it.projectType != "E_BOOK" && it.projectType != "RESEARCH_PAPER" }})"
                )
                items(filters) { (key, label) ->
                    val isSelected = selectedTypeFilter == key
                    Surface(
                        onClick = { selectedTypeFilter = key },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) theme.primaryColor else theme.elevatedCardColor,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) theme.primaryColor else theme.borderColor
                        )
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else theme.textColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // Projects List, 3D Bookshelf Grid, or Rich Empty State
        if (filteredProjects.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .shadow(theme.elevationDp / 2, RoundedCornerShape(20.dp), spotColor = theme.shadowColor)
                    .border(1.dp, theme.borderColor, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = theme.elevatedCardColor)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(theme.primaryColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(theme.primaryColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AutoStories,
                                contentDescription = null,
                                modifier = Modifier.size(34.dp),
                                tint = theme.primaryColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "ابدأ رحلتك الإبداعية والبحثية الآن",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = theme.textColor,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "اختر أحد قوالب الانطلاق الفوري أعلاه، أو أنشئ كتاباً إلكترونياً بأغلفة ثلاثية الأبعاد.",
                        fontSize = 12.sp,
                        color = theme.subtleTextColor,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showNewProjectDialog = true },
                            modifier = Modifier.weight(1f).height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تأليف كتاب / مشروع", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onNavigateToFiles,
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, theme.primaryColor)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("رفع مصادر للتحليل", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = theme.primaryColor)
                        }
                    }
                }
            }
        } else if (isBookshelfGridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredProjects, key = { it.id }) { project ->
                    ProjectBookshelfGridCard(
                        project = project,
                        theme = theme,
                        onOpen = {
                            viewModel.loadProjectIntoEditor(project)
                            onOpenEditor(project)
                        },
                        onQuickPeek = {
                            viewModel.loadProjectIntoEditor(project)
                            quickPeekProject = project
                        },
                        onDelete = { viewModel.deleteProject(project.id) }
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredProjects, key = { it.id }) { project ->
                    ProjectCardItem(
                        project = project,
                        theme = theme,
                        onOpen = {
                            viewModel.loadProjectIntoEditor(project)
                            onOpenEditor(project)
                        },
                        onQuickPeek = {
                            viewModel.loadProjectIntoEditor(project)
                            quickPeekProject = project
                        },
                        onDelete = { viewModel.deleteProject(project.id) }
                    )
                }
            }
        }
    }

    // Project Quick-Peek & Instant Export Bottom Sheet (معاينة المشروع السريعة دون فتح المحرر)
    quickPeekProject?.let { proj ->
        ModalBottomSheet(
            onDismissRequest = { quickPeekProject = null },
            containerColor = theme.elevatedCardColor,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            val totalWords = remember(activeChapters) {
                activeChapters.sumOf { ch ->
                    if (ch.content.isBlank()) 0 else ch.content.trim().split(Regex("\\s+")).size
                }
            }
            val firstSnippet = remember(activeChapters) {
                activeChapters.firstOrNull { it.content.isNotBlank() }?.content?.take(220) ?: "لا يوجد نص مكتوب في هذا المشروع بعد..."
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.primaryColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MenuBook, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size(24.dp))
                        }
                        Column {
                            Text(
                                text = proj.projectName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = theme.textColor
                            )
                            Text(
                                text = "${activeChapters.size} فصول • $totalWords كلمة • ورق ${proj.paperSize}",
                                fontSize = 11.sp,
                                color = theme.subtleTextColor
                            )
                        }
                    }

                    IconButton(onClick = { quickPeekProject = null }) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = theme.subtleTextColor)
                    }
                }

                // Chapters Pills Strip
                if (activeChapters.isNotEmpty()) {
                    Text(
                        text = "فهرس فصول المشروع (${activeChapters.size}):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.primaryColor
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(activeChapters) { ch ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = theme.cardColor,
                                border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderColor)
                            ) {
                                Text(
                                    text = "📄 ${ch.tabName}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = theme.textColor,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                // First Chapter Excerpt Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = theme.cardColor,
                    border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "مقتطف سريع من المحتوى:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.subtleTextColor
                        )
                        Text(
                            text = firstSnippet,
                            fontSize = 12.sp,
                            lineHeight = 19.sp,
                            color = theme.textColor,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Instant Actions Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val target = proj
                            quickPeekProject = null
                            onOpenEditor(target)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.3f).height(42.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("فتح بالمحرر ✍️", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val textToRead = activeChapters.joinToString("\n\n") { it.content }
                            if (textToRead.isNotBlank()) {
                                viewModel.startTtsReading(textToRead)
                                Toast.makeText(context, "🎧 بدأ الاستماع الصوتي للمشروع", Toast.LENGTH_SHORT).show()
                                quickPeekProject = null
                            } else {
                                Toast.makeText(context, "المشروع فارغ حالياً", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, theme.primaryColor),
                        modifier = Modifier.weight(1f).height(42.dp)
                    ) {
                        Icon(Icons.Default.Headphones, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("استماع 🎧", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.primaryColor)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        onClick = {
                            val pdfFile = viewModel.exportToPdf(context)
                            if (pdfFile != null) {
                                Toast.makeText(context, "📄 تم تصدير PDF بنجاح: ${pdfFile.name}", Toast.LENGTH_LONG).show()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFDC2626).copy(alpha = 0.12f),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("📄 تصدير PDF فوري", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                        }
                    }

                    Surface(
                        onClick = {
                            val epubFile = viewModel.exportToEpub(context)
                            if (epubFile != null) {
                                Toast.makeText(context, "📚 تم تصدير EPUB3 بنجاح: ${epubFile.name}", Toast.LENGTH_LONG).show()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF059669).copy(alpha = 0.12f),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("📚 تصدير EPUB3 فوري", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                        }
                    }

                    Surface(
                        onClick = {
                            val combined = activeChapters.joinToString("\n\n") { it.content }
                            viewModel.createProject(
                                title = "${proj.projectName} (نسخة)",
                                type = proj.projectType,
                                templateId = proj.writingTemplateId,
                                emotions = listOf("warmth"),
                                initialContent = combined,
                                onCreated = {
                                    Toast.makeText(context, "📋 تم إنشاء نسخة مطابقة من المشروع", Toast.LENGTH_SHORT).show()
                                    quickPeekProject = null
                                }
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = theme.primaryColor.copy(alpha = 0.12f),
                        modifier = Modifier.weight(0.9f).height(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("📋 نسخ المشروع", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.primaryColor)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }

    if (showNewProjectDialog) {
        NewProjectDialog(
            theme = theme,
            onDismiss = { showNewProjectDialog = false },
            onCreate = { title, type, templateId, emotions ->
                showNewProjectDialog = false
                viewModel.createProject(
                    title = title,
                    type = type,
                    templateId = templateId,
                    emotions = emotions,
                    onCreated = { created ->
                        onOpenEditor(created)
                    }
                )
            }
        )
    }
}

@Composable
private fun ProjectBookshelfGridCard(
    project: Project,
    theme: com.example.data.model.AppThemeData,
    onOpen: () -> Unit,
    onQuickPeek: () -> Unit,
    onDelete: () -> Unit
) {
    val (typeLabel, coverGradient, typeIcon) = when (project.projectType) {
        "E_BOOK" -> Triple(
            "كتاب إلكتروني",
            listOf(Color(0xFF1E3A8A), Color(0xFF3B82F6)),
            Icons.Default.MenuBook
        )
        "RESEARCH_PAPER" -> Triple(
            "بحث علمي",
            listOf(Color(0xFF065F46), Color(0xFF10B981)),
            Icons.Default.School
        )
        else -> Triple(
            "مستند ومقال",
            listOf(Color(0xFF7C2D12), Color(0xFFF97316)),
            Icons.Default.Article
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 3D Standing Book Cover on the Shelf
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .height(168.dp)
                .shadow(10.dp, RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 14.dp, bottomEnd = 14.dp))
                .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 14.dp, bottomEnd = 14.dp))
                .background(Brush.linearGradient(coverGradient))
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 14.dp, bottomEnd = 14.dp))
        ) {
            // 3D Spine Crease
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(12.dp)
                    .background(Color.Black.copy(alpha = 0.32f))
                    .align(Alignment.CenterStart)
            )

            // Top Ribbon & Quick-Peek / Delete Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 6.dp, top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.28f)
                ) {
                    Text(
                        text = typeLabel,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onQuickPeek,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Visibility,
                            contentDescription = "معاينة سريعة",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "حذف",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Center Book Title & Ornamental Emblem
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(start = 18.dp, end = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(typeIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Text(
                    text = project.projectName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Bottom Paper Size & Open Badge
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 10.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ورق ${project.paperSize}",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.White.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "تحرير ✍️",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Wooden / Glass Shelf Ledge Under the Book
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .shadow(4.dp, RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
                .background(
                    Brush.horizontalGradient(
                        if (theme.isDark) {
                            listOf(Color(0xFF334155), Color(0xFF475569), Color(0xFF334155))
                        } else {
                            listOf(Color(0xFF92400E), Color(0xFFB45309), Color(0xFF92400E))
                        }
                    )
                )
        )
    }
}

@Composable
fun ProjectCardItem(
    project: Project,
    theme: com.example.data.model.AppThemeData,
    onOpen: () -> Unit,
    onQuickPeek: () -> Unit = {},
    onDelete: () -> Unit
) {
    val template = defaultWritingTemplates.firstOrNull { it.id == project.writingTemplateId }

    val (typeLabel, coverGradient, typeIcon) = when (project.projectType) {
        "E_BOOK" -> Triple(
            "كتاب إلكتروني",
            listOf(Color(0xFF1E3A8A), Color(0xFF3B82F6)),
            Icons.Default.MenuBook
        )
        "RESEARCH_PAPER" -> Triple(
            "بحث علمي",
            listOf(Color(0xFF065F46), Color(0xFF10B981)),
            Icons.Default.School
        )
        else -> Triple(
            "مستند ومقال",
            listOf(Color(0xFF7C2D12), Color(0xFFF97316)),
            Icons.Default.Article
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(theme.elevationDp / 2, RoundedCornerShape(18.dp), spotColor = theme.shadowColor)
            .border(1.dp, theme.borderColor, RoundedCornerShape(18.dp))
            .clickable { onOpen() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = theme.elevatedCardColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 3D Miniature Book Cover with Spine Effect
            Box(
                modifier = Modifier
                    .width(62.dp)
                    .height(84.dp)
                    .shadow(6.dp, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 10.dp, bottomEnd = 10.dp))
                    .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 10.dp, bottomEnd = 10.dp))
                    .background(Brush.linearGradient(coverGradient))
            ) {
                // Book Spine Highlight Strip
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(7.dp)
                        .background(Color.Black.copy(alpha = 0.28f))
                        .align(Alignment.CenterStart)
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        typeIcon,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = project.paperSize,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            // Project Info & Actions Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(coverGradient.last().copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = typeLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = coverGradient.last()
                            )
                        }

                        if (template != null) {
                            Text(
                                text = "• ${template.name}",
                                fontSize = 10.sp,
                                color = theme.subtleTextColor,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "حذف",
                            tint = Color(0xFFEF4444).copy(alpha = 0.8f),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                Text(
                    text = project.projectName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = theme.textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = theme.subtleTextColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = dateFormat.format(Date(project.lastModified)),
                            fontSize = 10.sp,
                            color = theme.subtleTextColor
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            onClick = onQuickPeek,
                            shape = RoundedCornerShape(10.dp),
                            color = theme.primaryColor.copy(alpha = 0.12f),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = "معاينة سريعة", tint = theme.primaryColor, modifier = Modifier.size(13.dp))
                                Text("معاينة", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.primaryColor)
                            }
                        }

                        Button(
                            onClick = onOpen,
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("فتح المحرر", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
