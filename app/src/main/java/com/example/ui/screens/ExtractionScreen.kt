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
    onNavigateToEditor: () -> Unit
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                    .shadow(theme.elevationDp, RoundedCornerShape(theme.cornerRadiusDp), spotColor = theme.shadowColor),
                shape = RoundedCornerShape(theme.cornerRadiusDp),
                colors = CardDefaults.cardColors(containerColor = theme.surfaceColor)
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
                                text = "استخلاص المحتوى الذكي (FR-04..FR-07)",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.textColor
                            )
                            Text(
                                text = "استخراج الأفكار والفقرات ذات الصلة بموضوعك من الملفات المحددة",
                                fontSize = 11.sp,
                                color = theme.textColor.copy(alpha = 0.75f)
                            )
                        }
                    }

                    // Topic Input (FR-04)
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
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
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
                        color = Color.Gray
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
                        .shadow(4.dp, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
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

            // Passages Items
            itemsIndexed(passages) { index, passage ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(2.dp, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = passage,
                            fontSize = 13.sp,
                            lineHeight = 22.sp,
                            color = Color(0xFF2C2C2C)
                        )

                        Divider(color = Color.Black.copy(alpha = 0.05f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "فقرة #${index + 1}", fontSize = 11.sp, color = Color.Gray)

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                                val ctx = androidx.compose.ui.platform.LocalContext.current

                                TextButton(
                                    onClick = {
                                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(passage))
                                        android.widget.Toast.makeText(ctx, "تم نسخ الفقرة إلى الحافظة!", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("نسخ", fontSize = 11.sp)
                                }

                                TextButton(
                                    onClick = {
                                        editingPassageIndex = index
                                        editingPassageText = passage
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("تعديل", fontSize = 11.sp)
                                }

                                TextButton(
                                    onClick = { viewModel.deleteExtractedPassage(index) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color.Red, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("حذف", fontSize = 11.sp, color = Color.Red)
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
            .shadow(2.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
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
                    color = Color.Gray
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
                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
