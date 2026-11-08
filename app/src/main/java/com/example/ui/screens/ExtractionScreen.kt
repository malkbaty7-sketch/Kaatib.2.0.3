package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.ExtractedText
import com.example.ui.MainViewModel
import java.text.SimpleDateFormat
import android.net.Uri
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.example.util.SmartTextExtractionEngine
import java.util.*

@Composable
fun ExtractionScreen(
    viewModel: MainViewModel,
    onNavigateToEditor: () -> Unit,
    onNavigateToFiles: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val theme by viewModel.currentTheme.collectAsState()
    val files by viewModel.allFiles.collectAsState()
    val savedTexts by viewModel.allExtractedTexts.collectAsState()

    val topicQuery by viewModel.topicQuery.collectAsState()
    val extractionMode by viewModel.extractionMode.collectAsState()
    val isExtracting by viewModel.isExtracting.collectAsState()
    val passages by viewModel.extractedPassages.collectAsState()
    val selectedFileIds by viewModel.selectedFileIdsForExtraction.collectAsState()

    var editingPassageIndex by remember { mutableStateOf<Int?>(null) }
    var editingPassageText by remember { mutableStateOf("") }
    var showArabicConverterDialog by remember { mutableStateOf(false) }
    var showSmartExtractionStudio by remember { mutableStateOf(false) }
    var smartStudioInitialTab by remember { mutableStateOf("AREA_FILTER") }

    val targetSourceCount = if (selectedFileIds.isEmpty()) files.size else selectedFileIds.size
    val extractedWordsCount = remember(passages) {
        passages.sumOf { p -> p.split(Regex("\\s+")).count { it.isNotBlank() } }
    }
    val activeStep = when {
        passages.isNotEmpty() -> 3
        topicQuery.isNotBlank() || isExtracting -> 2
        else -> 1
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.backgroundBrush)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Visual Stepper Workflow & Live Statistics Strip (نظام الخطوات المتسلسلة والإحصائيات)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(theme.elevationDp / 2, RoundedCornerShape(18.dp), spotColor = theme.shadowColor)
                    .border(1.dp, theme.borderColor, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = theme.elevatedCardColor)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 3-Step Visual Workflow Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val steps = listOf(
                            Triple(1, "١. اختيار المصدر", Icons.Default.FolderOpen),
                            Triple(2, "٢. الفلترة والتنظيف", Icons.Default.FilterAlt),
                            Triple(3, "٣. التصدير للمحرر", Icons.Default.Send)
                        )
                        steps.forEachIndexed { idx, (stepNum, label, icon) ->
                            val isDoneOrActive = activeStep >= stepNum
                            val stepColor = if (isDoneOrActive) theme.primaryColor else theme.subtleTextColor.copy(alpha = 0.45f)
                            Surface(
                                onClick = {
                                    when (stepNum) {
                                        1 -> onNavigateToFiles()
                                        2 -> if (topicQuery.isNotBlank()) viewModel.extractTopic()
                                        3 -> if (passages.isNotEmpty()) {
                                            viewModel.saveExtractedPassagesToDbAndCreateProject {
                                                onNavigateToEditor()
                                            }
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (activeStep == stepNum) theme.primaryColor.copy(alpha = 0.15f) else Color.Transparent,
                                border = if (activeStep == stepNum) androidx.compose.foundation.BorderStroke(1.dp, theme.primaryColor) else null
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(icon, contentDescription = null, tint = stepColor, modifier = Modifier.size(15.dp))
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        fontWeight = if (isDoneOrActive) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isDoneOrActive) theme.textColor else theme.subtleTextColor
                                    )
                                }
                            }
                            if (idx < steps.lastIndex) {
                                Icon(
                                    Icons.Default.ChevronLeft,
                                    contentDescription = null,
                                    tint = theme.subtleTextColor.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // 3 Colorful Statistical Summary Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val stats = listOf(
                            Triple("المصادر المحددة", "$targetSourceCount ملف", Color(0xFF0284C7)),
                            Triple("الفقرات المستخلصة", "${passages.size} فقرة", Color(0xFF059669)),
                            Triple("إجمالي الكلمات", "$extractedWordsCount كلمة", Color(0xFFD97706))
                        )
                        stats.forEach { (title, value, accent) ->
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = accent.copy(alpha = 0.1f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.25f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(value, fontSize = 13.sp, fontWeight = FontWeight.Black, color = accent)
                                    Text(title, fontSize = 9.sp, color = theme.subtleTextColor, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Smart Text Extraction & OCR Studio Banner (All 6 New Upgrades)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(theme.elevationDp, RoundedCornerShape(theme.cornerRadiusDp), spotColor = theme.shadowColor),
                shape = RoundedCornerShape(theme.cornerRadiusDp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                smartStudioInitialTab = "AREA_FILTER"
                                showSmartExtractionStudio = true
                            },
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
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF10B981)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                            Column {
                                Text(
                                    text = "مختبر واستوديو استخراج النصوص والـ OCR الذكي (الجيل 6)",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "تحديد بالمنطقة • فلاتر ماسح • تنظيف العربية • جداول CSV • كيانات وتوثيق • استخراج دفعي",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                    }

                    // Quick Launch Chips for each of the 6 Upgrades
                    val upgradeActions = listOf(
                        Triple("AREA_FILTER", "📐 تحديد بالمنطقة وOCR", Color(0xFF059669)),
                        Triple("AREA_FILTER", "🔦 فلاتر تحسين الصفحات", Color(0xFF0284C7)),
                        Triple("POST_PROCESS", "✨ مختبر تنظيف العربية", Color(0xFF7C3AED)),
                        Triple("TABLES", "📊 استخراج الجداول CSV", Color(0xFFD97706)),
                        Triple("ENTITIES", "🏷️ الكيانات والاقتباسات", Color(0xFF2563EB)),
                        Triple("BATCH_PIPELINE", "📑 استخراج دفعي وتصدير فوري", Color(0xFFEC4899))
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(upgradeActions) { (tabId, label, badgeColor) ->
                            Surface(
                                onClick = {
                                    smartStudioInitialTab = tabId
                                    showSmartExtractionStudio = true
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = badgeColor.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Arabic Document Converter & Tashkeel Banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showArabicConverterDialog = true }
                    .shadow(theme.elevationDp, RoundedCornerShape(theme.cornerRadiusDp), spotColor = theme.shadowColor),
                shape = RoundedCornerShape(theme.cornerRadiusDp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF2563EB)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.SyncAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Column {
                            Text("محوّل ومستخرج المستندات العربي (Tashkeel & OCR)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("استخراج ومعالجة PDF · DOCX · TXT · صور مع حفظ التشكيل بالكامل", color = Color(0xFF94A3B8), fontSize = 10.sp)
                        }
                    }
                    Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                }
            }
        }

        // Top Header
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(theme.elevationDp, RoundedCornerShape(theme.cornerRadiusDp), spotColor = theme.shadowColor)
                    .border(1.dp, theme.borderColor, RoundedCornerShape(theme.cornerRadiusDp)),
                shape = RoundedCornerShape(theme.cornerRadiusDp),
                colors = CardDefaults.cardColors(containerColor = theme.elevatedCardColor)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.primaryColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                        }
                        Column {
                            Text(
                                text = "استخلاص المحتوى الذكي",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.textColor
                            )
                            Text(
                                text = "استخراج الأفكار والفقرات ذات الصلة بموضوعك من الملفات المحددة",
                                fontSize = 11.sp,
                                color = theme.subtleTextColor
                            )
                        }
                    }

                    // Topic Input
                    OutlinedTextField(
                        value = topicQuery,
                        onValueChange = { viewModel.topicQuery.value = it },
                        label = { Text("أدخل الموضوع أو الفكرة المطلوب استخلاصها...") },
                        placeholder = { Text("مثال: الذكاء الاصطناعي، الصبر، ريادة الأعمال...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.primaryColor,
                            unfocusedBorderColor = theme.borderColor,
                            focusedContainerColor = theme.cardColor,
                            unfocusedContainerColor = theme.cardColor,
                            focusedTextColor = theme.textColor,
                            unfocusedTextColor = theme.textColor
                        )
                    )

                    // Mode Selection: Offline vs Online
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = extractionMode == "OFFLINE",
                            onClick = { viewModel.extractionMode.value = "OFFLINE" },
                            label = { Text("معالجة محلية (Offline)", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = theme.primaryColor,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        FilterChip(
                            selected = extractionMode == "ONLINE",
                            onClick = { viewModel.extractionMode.value = "ONLINE" },
                            label = { Text("معالجة سحابية ذكية", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = theme.primaryColor,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Target Files Notice
                    val targetCount = if (selectedFileIds.isEmpty()) files.size else selectedFileIds.size
                    Text(
                        text = "البحث في: $targetCount ملفات مصادر ${if (selectedFileIds.isEmpty()) "(جميع الملفات)" else "(الملفات المحددة)"}",
                        fontSize = 11.sp,
                        color = theme.subtleTextColor
                    )

                    Button(
                        onClick = {
                            if (topicQuery.isBlank()) {
                                Toast.makeText(context, "يرجى إدخال الموضوع المراد استخلاصه أولاً", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.extractTopic()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isExtracting
                    ) {
                        if (isExtracting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("جاري فحص الجمل والفقرات...")
                        } else {
                            Icon(Icons.Default.Bolt, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("بدء الاستخلاص الفوري", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Extracted Results Section (FR-06 & FR-07)
        if (passages.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(14.dp))
                        .border(1.dp, theme.borderColor, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.elevatedCardColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "الفقرات المستخلصة (${passages.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = theme.textColor
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val full = passages.joinToString("\n\n---\n\n")
                                    clipboardManager.setText(AnnotatedString(full))
                                    Toast.makeText(context, "تم نسخ جميع الفقرات!", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("نسخ الكل (FR-06)", fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    viewModel.saveExtractedPassagesToDbAndCreateProject {
                                        onNavigateToEditor()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("حفظ واستمرار (FR-07)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Inline Arabic Post-Processing & Smart Analysis Toolbar on Extracted Passages
                    Divider(color = Color.Black.copy(alpha = 0.06f))
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            AssistChip(
                                onClick = {
                                    viewModel.transformAllExtractedPassages { SmartTextExtractionEngine.stripAllTashkeel(it) }
                                    Toast.makeText(context, "تمت إزالة التشكيل من جميع الفقرات المستخلصة ✔", Toast.LENGTH_SHORT).show()
                                },
                                label = { Text("إزالة التشكيل", fontSize = 10.sp) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = {
                                    viewModel.transformAllExtractedPassages { SmartTextExtractionEngine.removeTatweel(it) }
                                    Toast.makeText(context, "تمت إزالة التطويل (الكشيدة) ✔", Toast.LENGTH_SHORT).show()
                                },
                                label = { Text("حذف الكشيدة (ـ)", fontSize = 10.sp) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = {
                                    viewModel.transformAllExtractedPassages { SmartTextExtractionEngine.fixBrokenAndReversedArabicText(it) }
                                    Toast.makeText(context, "تم إصلاح النص العربي المقطّع والمعكوس ✔", Toast.LENGTH_SHORT).show()
                                },
                                label = { Text("إصلاح النص المقطّع", fontSize = 10.sp) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = {
                                    viewModel.transformAllExtractedPassages { SmartTextExtractionEngine.normalizeHamzasAndAlef(it) }
                                    Toast.makeText(context, "تم توحيد الهمزات (أ، إ، آ ← ا) ✔", Toast.LENGTH_SHORT).show()
                                },
                                label = { Text("توحيد الهمزات", fontSize = 10.sp) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = {
                                    viewModel.transformAllExtractedPassages { SmartTextExtractionEngine.convertToEasternArabicNumerals(it) }
                                    Toast.makeText(context, "تم تحويل الأرقام إلى مشرقية (١٢٣) ✔", Toast.LENGTH_SHORT).show()
                                },
                                label = { Text("أرقام (١٢٣)", fontSize = 10.sp) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = {
                                    smartStudioInitialTab = "ENTITIES"
                                    showSmartExtractionStudio = true
                                },
                                label = { Text("🏷️ استخراج الكيانات والجداول", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                }
            }

            // Passages Items (Interactive Extraction Cards with Color Tags & Direct Chapter Insertion)
            itemsIndexed(passages) { index, passage ->
                var selectedTagType by remember(index) {
                    mutableStateOf(
                        when (index % 4) {
                            0 -> "QUOTE"
                            1 -> "DEFINITION"
                            2 -> "IDEA"
                            else -> "REVIEW"
                        }
                    )
                }
                var isExpandedPassage by remember(index) { mutableStateOf(true) }
                var showChapterSendMenu by remember { mutableStateOf(false) }
                val activeChapters by viewModel.activeChapters.collectAsState()

                val (tagLabel, tagColor) = when (selectedTagType) {
                    "QUOTE" -> "🟡 اقتباس هام" to Color(0xFFD97706)
                    "DEFINITION" -> "🔵 تعريف علمي" to Color(0xFF2563EB)
                    "IDEA" -> "🟢 فكرة فصل" to Color(0xFF059669)
                    else -> "🔴 مراجعة وتدقيق" to Color(0xFFDC2626)
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(2.dp, RoundedCornerShape(14.dp))
                        .border(1.dp, tagColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.elevatedCardColor)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        // Colored Side Accent Strip
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .fillMaxHeight()
                                .background(tagColor)
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Top Row: Passage Index + Interactive Color Tag Switcher + Expand/Collapse
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = theme.primaryColor.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = "فقرة #${index + 1}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = theme.primaryColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    // 1-Tap Cycle Color Category Tag
                                    Surface(
                                        onClick = {
                                            selectedTagType = when (selectedTagType) {
                                                "QUOTE" -> "DEFINITION"
                                                "DEFINITION" -> "IDEA"
                                                "IDEA" -> "REVIEW"
                                                else -> "QUOTE"
                                            }
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        color = tagColor.copy(alpha = 0.14f),
                                        border = androidx.compose.foundation.BorderStroke(0.5.dp, tagColor.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = tagLabel,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = tagColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = if (isExpandedPassage) "طي النص ▲" else "توسيع ▼",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.subtleTextColor,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { isExpandedPassage = !isExpandedPassage }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }

                            Text(
                                text = passage,
                                fontSize = 13.sp,
                                lineHeight = 22.sp,
                                color = theme.textColor,
                                maxLines = if (isExpandedPassage) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            HorizontalDivider(color = theme.borderColor)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Direct Insert into Specific Chapter Menu
                                Box {
                                    Surface(
                                        onClick = { showChapterSendMenu = true },
                                        shape = RoundedCornerShape(8.dp),
                                        color = theme.primaryColor.copy(alpha = 0.12f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Default.Send, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size(12.dp))
                                            Text("إدراج في فصل 📥", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.primaryColor)
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = showChapterSendMenu,
                                        onDismissRequest = { showChapterSendMenu = false }
                                    ) {
                                        if (activeChapters.isEmpty()) {
                                            DropdownMenuItem(
                                                text = { Text("إنشاء مشروع جديد بهذه الفقرة 🚀", fontSize = 11.sp) },
                                                onClick = {
                                                    showChapterSendMenu = false
                                                    viewModel.createProject(
                                                        title = "مشروع استخلاص: ${topicQuery.ifBlank { "مقتطفات" }}",
                                                        type = "E_BOOK",
                                                        templateId = "novel_action",
                                                        emotions = listOf("warmth"),
                                                        initialContent = passage,
                                                        onCreated = { onNavigateToEditor() }
                                                    )
                                                }
                                            )
                                        } else {
                                            activeChapters.forEachIndexed { chIdx, chItem ->
                                                DropdownMenuItem(
                                                    text = { Text("إدراج في: ${chItem.tabName}", fontSize = 11.sp) },
                                                    onClick = {
                                                        showChapterSendMenu = false
                                                        viewModel.selectChapter(chIdx)
                                                        viewModel.insertQuoteIntoActiveChapter(
                                                            quoteText = passage,
                                                            sourceFileName = "الاستخلاص الذكي",
                                                            positionLabel = "فقرة #${index + 1}",
                                                            note = tagLabel
                                                        )
                                                        Toast.makeText(context, "📥 تم إدراج الفقرة في «${chItem.tabName}»", Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                                    val ctx = androidx.compose.ui.platform.LocalContext.current

                                    TextButton(
                                        onClick = {
                                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(passage))
                                            android.widget.Toast.makeText(ctx, "تم نسخ الفقرة إلى الحافظة!", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("نسخ", fontSize = 10.sp)
                                    }

                                    TextButton(
                                        onClick = {
                                            editingPassageIndex = index
                                            editingPassageText = passage
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("تعديل", fontSize = 10.sp)
                                    }

                                    TextButton(
                                        onClick = { viewModel.deleteExtractedPassage(index) },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("حذف", fontSize = 10.sp, color = Color(0xFFEF4444))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Saved Extractions History (FR-07: جدول ExtractedTexts)
        if (savedTexts.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "سجل الاستخلاصات المحفوظة في قاعدة البيانات (جدول ExtractedTexts)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = theme.textColor
                )
            }

            itemsIndexed(savedTexts) { _, item ->
                SavedExtractionCard(
                    record = item,
                    theme = theme,
                    onDelete = { viewModel.deleteExtractedText(item.id) },
                    onUseAsProject = {
                        viewModel.createProject(
                            title = "مشروع: ${item.topicQuery}",
                            type = "E_BOOK",
                            templateId = "novel_action",
                            emotions = listOf("warmth", "motivation"),
                            initialContent = item.extractedContent,
                            onCreated = { onNavigateToEditor() }
                        )
                    }
                )
            }
        }
    }

    // Modal: Edit Single Passage
    editingPassageIndex?.let { idx ->
        Dialog(onDismissRequest = { editingPassageIndex = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.7f),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "تعديل الفقرة المستخلصة #${idx + 1}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = theme.textColor
                    )

                    OutlinedTextField(
                        value = editingPassageText,
                        onValueChange = { editingPassageText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { editingPassageIndex = null }) {
                            Text("إلغاء")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.updateExtractedPassage(idx, editingPassageText)
                                editingPassageIndex = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("حفظ التعديل")
                        }
                    }
                }
            }
        }
    }

    if (showArabicConverterDialog) {
        DocumentToolsAndConverterDialog(
            theme = theme,
            onDismiss = { showArabicConverterDialog = false }
        )
    }

    if (showSmartExtractionStudio) {
        val primaryFile = files.firstOrNull { selectedFileIds.contains(it.id) } ?: files.firstOrNull()
        val initialUri = primaryFile?.filePath?.takeIf { it.startsWith("content://") || it.startsWith("file://") }?.let { Uri.parse(it) }
        val initialText = if (passages.isNotEmpty()) {
            passages.joinToString("\n\n")
        } else {
            primaryFile?.extractedText ?: ""
        }
        SmartTextExtractionStudioDialog(
            theme = theme,
            initialUri = initialUri,
            initialFileName = primaryFile?.fileName ?: "مستند مستخلص",
            initialExtractedText = initialText,
            initialTab = smartStudioInitialTab,
            onSendToEditor = { text, noteLabel ->
                viewModel.insertQuoteIntoActiveChapter(
                    quoteText = text,
                    sourceFileName = primaryFile?.fileName ?: "استوديو الاستخراج الذكي",
                    positionLabel = "مختبر الـ OCR",
                    note = noteLabel
                )
            },
            onAddFlashcards = { cards ->
                val formatted = cards.joinToString("\n\n") { "🗂️ السؤال/المصطلح: ${it.termOrQuestion}\n💡 الإجابة: ${it.definitionOrAnswer}" }
                if (formatted.isNotBlank()) {
                    viewModel.insertQuoteIntoActiveChapter(
                        quoteText = formatted,
                        sourceFileName = primaryFile?.fileName ?: "استوديو الاستخراج الذكي",
                        positionLabel = "بطاقات استذكار",
                        note = "بطاقات استذكار مولدة تلقائياً"
                    )
                }
            },
            onAddMindMapNodes = { nodes ->
                val formatted = nodes.joinToString("\n") { "🕸️ [${it.title}] ← ${it.subtitle}" }
                if (formatted.isNotBlank()) {
                    viewModel.insertQuoteIntoActiveChapter(
                        quoteText = formatted,
                        sourceFileName = primaryFile?.fileName ?: "استوديو الاستخراج الذكي",
                        positionLabel = "خريطة ذهنية",
                        note = "عقد خريطة ذهنية مستخرجة"
                    )
                }
            },
            onAddHighlightNote = { hl ->
                viewModel.insertQuoteIntoActiveChapter(
                    quoteText = hl.snippetText,
                    sourceFileName = primaryFile?.fileName ?: "استوديو الاستخراج الذكي",
                    positionLabel = hl.positionLabel,
                    note = hl.note
                )
            },
            onSaveExtractedRecord = { title, content ->
                viewModel.saveCustomExtractedRecord(title, content)
            },
            onDismiss = { showSmartExtractionStudio = false }
        )
    }
}

@Composable
fun SavedExtractionCard(
    record: ExtractedText,
    theme: com.example.data.model.AppThemeData,
    onDelete: () -> Unit,
    onUseAsProject: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(12.dp))
            .border(1.dp, theme.borderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = theme.elevatedCardColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "موضوع: \"${record.topicQuery}\"",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = theme.textColor
                )
                val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                Text(
                    text = dateFormat.format(Date(record.extractionDate)),
                    fontSize = 10.sp,
                    color = theme.subtleTextColor
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                val ctx = androidx.compose.ui.platform.LocalContext.current

                IconButton(
                    onClick = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(record.extractedContent))
                        android.widget.Toast.makeText(ctx, "تم نسخ نص الموضوع بالكامل للحافظة!", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ المحتوى", tint = theme.primaryColor, modifier = Modifier.size(16.dp))
                }

                TextButton(onClick = onUseAsProject) {
                    Text("بدء مشروع", fontSize = 11.sp, color = theme.primaryColor, fontWeight = FontWeight.Bold)
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = theme.subtleTextColor, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
