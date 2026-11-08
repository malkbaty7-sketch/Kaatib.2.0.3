package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AppThemeData
import com.example.util.DocmoDocumentBridge
import com.example.util.DocumentBookmarkItem
import com.example.util.DocumentFlashcardItem
import com.example.util.DocumentHighlightItem
import com.example.util.DocumentMindMapNode
import com.example.util.DocumentParser
import com.example.util.MultiFormatDocumentEngine
import com.example.util.SmartTextExtractionEngine
import com.example.util.TtsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * استوديو ومختبر استخراج النصوص والتعرف الضوئي الذكي (Smart Text Extraction & OCR Studio - الجيل السادس)
 * يجمع الترقيات الست المتقدمة في واجهة عربية RTL متكاملة:
 * 1. التحديد الذكي بالمنطقة (Smart Area / Lasso Selection OCR)
 * 2. مختبر تنظيف ومعالجة النص العربي (Arabic Post-Processing & Normalization Lab)
 * 3. استخراج الجداول والبيانات المهيكلة (Table & Structured Data Extractor -> CSV / Markdown)
 * 4. المستخرج الذكي للكيانات والاقتباسات والمصطلحات (Smart Entity & Citation Extractor)
 * 5. فلاتر تحسين الصفحات الممسوحة قبل الاستخراج (Pre-OCR Scanner Enhancement Filters)
 * 6. الاستخراج الدفعي الموجه ومسار التصدير الفوري (Batch Page Range & Direct Action Pipeline)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartTextExtractionStudioDialog(
    theme: AppThemeData,
    initialUri: Uri? = null,
    initialFileName: String = "مستند",
    initialPageIndex: Int = 0,
    initialTotalPages: Int = 1,
    initialExtractedText: String = "",
    bookmarks: List<DocumentBookmarkItem> = emptyList(),
    initialTab: String = "AREA_FILTER", // AREA_FILTER, POST_PROCESS, TABLES, ENTITIES, BATCH_PIPELINE
    onSendToEditor: (text: String, noteLabel: String) -> Unit = { _, _ -> },
    onAddFlashcards: (List<DocumentFlashcardItem>) -> Unit = {},
    onAddMindMapNodes: (List<DocumentMindMapNode>) -> Unit = {},
    onAddHighlightNote: (DocumentHighlightItem) -> Unit = {},
    onSpeakTextAloud: ((String) -> Unit)? = null,
    onSaveExtractedRecord: ((topicTitle: String, content: String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    // Local TTS fallback if caller doesn't provide a custom TTS callback
    val localTtsManager = remember { TtsManager(context) }
    DisposableEffect(Unit) {
        onDispose {
            localTtsManager.stop()
        }
    }

    var activeTab by remember { mutableStateOf(initialTab) }
    var selectedUri by remember { mutableStateOf(initialUri) }
    var selectedFileName by remember { mutableStateOf(initialFileName) }
    var currentPageIndex by remember { mutableIntStateOf(initialPageIndex.coerceAtLeast(0)) }
    var totalPages by remember { mutableIntStateOf(initialTotalPages.coerceAtLeast(1)) }

    // Core Extracted Text state (shared across all 6 tools!)
    var originalExtractedText by remember { mutableStateOf(initialExtractedText) }
    var workingExtractedText by remember { mutableStateOf(initialExtractedText) }
    var isBusy by remember { mutableStateOf(false) }
    var statusBannerText by remember { mutableStateOf("") }

    // -------------------------------------------------------------------------
    // Feature 1 & 5 States: Area Selection OCR & Pre-OCR Scanner Filters
    // -------------------------------------------------------------------------
    var selectedPreOcrFilter by remember { mutableStateOf(SmartTextExtractionEngine.PreOcrFilter.ORIGINAL) }
    var deskewAngle by remember { mutableFloatStateOf(0f) }
    var selectedAreaPreset by remember { mutableStateOf<SmartTextExtractionEngine.AreaPreset?>(SmartTextExtractionEngine.AreaPreset.FULL_PAGE) }
    var selectionRectNorm by remember { mutableStateOf(RectF(0.05f, 0.08f, 0.95f, 0.92f)) }
    var useAiForAreaOcr by remember { mutableStateOf(true) }
    var rawPageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewEnhancedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // -------------------------------------------------------------------------
    // Feature 3 States: Table & Structured Data Extractor
    // -------------------------------------------------------------------------
    var extractedTables by remember { mutableStateOf<List<SmartTextExtractionEngine.ExtractedTableData>>(emptyList()) }
    var activeTableIndex by remember { mutableIntStateOf(0) }
    var editingTableCell by remember { mutableStateOf<Triple<Int, Int, String>?>(null) }
    var pendingCsvFileToSave by remember { mutableStateOf<File?>(null) }

    // -------------------------------------------------------------------------
    // Feature 4 States: Smart Entity & Citation Extractor
    // -------------------------------------------------------------------------
    var extractedEntities by remember { mutableStateOf<List<SmartTextExtractionEngine.ExtractedEntityItem>>(emptyList()) }
    var selectedEntityFilter by remember { mutableStateOf<SmartTextExtractionEngine.EntityCategory?>(null) }
    var citationStyle by remember { mutableStateOf("ARABIC") } // ARABIC, APA, MLA

    // -------------------------------------------------------------------------
    // Feature 6 States: Batch Page Range & Direct Action Pipeline
    // -------------------------------------------------------------------------
    var batchRangeMode by remember { mutableStateOf(SmartTextExtractionEngine.BatchRangeMode.CUSTOM_RANGE) }
    var batchStartPageInput by remember { mutableStateOf((currentPageIndex + 1).toString()) }
    var batchEndPageInput by remember { mutableStateOf(min(currentPageIndex + 5, totalPages.coerceAtLeast(1)).toString()) }
    var batchProgress by remember { mutableStateOf<SmartTextExtractionEngine.BatchProgressInfo?>(null) }
    var batchAutoFixArabic by remember { mutableStateOf(true) }
    var batchAutoUnwrapLines by remember { mutableStateOf(false) }
    var batchStripTashkeel by remember { mutableStateOf(false) }

    // Interactive Session Pipeline Outputs (visible immediately in Tab 5 across all screens)
    var generatedSessionFlashcards by remember { mutableStateOf<List<DocumentFlashcardItem>>(emptyList()) }
    var generatedSessionMindMapNodes by remember { mutableStateOf<List<DocumentMindMapNode>>(emptyList()) }
    var generatedSessionHighlights by remember { mutableStateOf<List<DocumentHighlightItem>>(emptyList()) }

    // SAF Picker to select or change document/image
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            val (name, _) = MultiFormatDocumentEngine.getFileNameAndSize(context, uri)
            selectedFileName = name
            currentPageIndex = 0
            coroutineScope.launch {
                isBusy = true
                statusBannerText = "جاري قراءة المستند وتحميل المعاينة..."
                val parsedText = withContext(Dispatchers.IO) {
                    DocumentParser.parseUri(context, uri).text
                }
                val pdfCnt = withContext(Dispatchers.IO) {
                    MultiFormatDocumentEngine.getPdfPageCount(context, uri)
                }
                val pageCnt = if (pdfCnt > 0) {
                    pdfCnt
                } else {
                    ((parsedText.lines().count { it.isNotBlank() } / 22) + 1).coerceAtLeast(1)
                }
                totalPages = pageCnt
                batchStartPageInput = "1"
                batchEndPageInput = min(5, pageCnt).toString()

                originalExtractedText = parsedText
                workingExtractedText = parsedText
                statusBannerText = "تم تحميل $name ($pageCnt صفحة) بنجاح ✔"
                isBusy = false
            }
        }
    }

    // SAF Launcher for saving exported CSV Table
    val saveCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { destUri: Uri? ->
        val file = pendingCsvFileToSave
        pendingCsvFileToSave = null
        if (destUri != null && file != null) {
            coroutineScope.launch {
                val res = withContext(Dispatchers.IO) {
                    DocmoDocumentBridge.saveFileToDestinationUri(context, destUri, file)
                }
                if (res.isSuccess) {
                    Toast.makeText(context, "تم حفظ الجدول بصيغة CSV المتوافقة مع Excel بنجاح 📊", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "تعذر حفظ ملف CSV", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Load page bitmap when URI, pageIndex, or initial text changes (supports PDF, Images, DOCX/TXT/EPUB, and raw text)
    LaunchedEffect(selectedUri, currentPageIndex, originalExtractedText) {
        val uri = selectedUri
        withContext(Dispatchers.IO) {
            if (uri != null && workingExtractedText.isBlank() && originalExtractedText.isBlank()) {
                val parsed = DocumentParser.parseUri(context, uri).text
                originalExtractedText = parsed
                workingExtractedText = parsed
            }
            val textForFallback = originalExtractedText.ifBlank { workingExtractedText }
            val pdfCnt = if (uri != null) MultiFormatDocumentEngine.getPdfPageCount(context, uri) else 0
            val computedPages = if (pdfCnt > 0) {
                pdfCnt
            } else if (textForFallback.isNotBlank()) {
                ((textForFallback.lines().count { it.isNotBlank() } / 22) + 1).coerceAtLeast(1)
            } else {
                totalPages.coerceAtLeast(1)
            }
            totalPages = computedPages

            val bmp = SmartTextExtractionEngine.renderDocumentPageOrImage(
                context = context,
                uri = uri,
                pageIndex = currentPageIndex,
                scaleFactor = 1.6f,
                fallbackText = textForFallback,
                documentTitle = selectedFileName
            )
            rawPageBitmap = bmp
        }
    }

    // Recompute live preview bitmap when filter or deskew angle changes
    LaunchedEffect(rawPageBitmap, selectedPreOcrFilter, deskewAngle) {
        val base = rawPageBitmap ?: run {
            previewEnhancedBitmap = null
            return@LaunchedEffect
        }
        withContext(Dispatchers.Default) {
            previewEnhancedBitmap = SmartTextExtractionEngine.applyPreOcrEnhancement(
                source = base,
                filter = selectedPreOcrFilter,
                deskewDegrees = deskewAngle,
                cropRectNorm = null // Show full page with filter so selection box overlays on top
            )
        }
    }

    // Automatically refresh Tables & Entities whenever workingExtractedText changes
    LaunchedEffect(workingExtractedText) {
        if (workingExtractedText.isNotBlank()) {
            withContext(Dispatchers.Default) {
                extractedTables = SmartTextExtractionEngine.extractStructuredTablesFromText(
                    text = workingExtractedText,
                    documentTitle = selectedFileName
                )
                if (activeTableIndex >= extractedTables.size) {
                    activeTableIndex = 0
                }
                extractedEntities = SmartTextExtractionEngine.extractEntitiesAndCitations(workingExtractedText)
            }
        } else {
            extractedTables = emptyList()
            extractedEntities = emptyList()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .fillMaxHeight(0.93f)
                    .clip(RoundedCornerShape(22.dp)),
                color = Color(0xFFF8FAFC)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // =========================================================
                    // 1. Top Header Bar
                    // =========================================================
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
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1D4ED8)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.DocumentScanner,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "استوديو استخراج النصوص والـ OCR الذكي",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "تحديد بالمنطقة • فلاتر ماسح • تنظيف عربي • جداول CSV • كيانات وتوثيق • استخراج دفعي",
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { documentPickerLauncher.launch(arrayOf("*/*")) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("smart_ocr_pick_file_button")
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (selectedUri == null) "فتح ملف" else "تغيير الملف", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.testTag("smart_ocr_close_button")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "إغلاق")
                            }
                        }
                    }

                    // =========================================================
                    // 2. Navigation Tabs for the 6 Upgrades
                    // =========================================================
                    val studioTabs = listOf(
                        Triple("AREA_FILTER", "📐 تحديد وفلاتر", Icons.Default.CropFree),
                        Triple("POST_PROCESS", "✨ تنظيف النص", Icons.Default.AutoFixHigh),
                        Triple("TABLES", "📊 الجداول (${extractedTables.size})", Icons.Default.TableChart),
                        Triple("ENTITIES", "🏷️ الكيانات (${extractedEntities.size})", Icons.Default.Category),
                        Triple("BATCH_PIPELINE", "📑 دفعي وتصدير", Icons.Default.DynamicFeed)
                    )

                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFE2E8F0))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(studioTabs) { (tabId, tabTitle, _) ->
                            val isSelected = activeTab == tabId
                            Surface(
                                onClick = { activeTab = tabId },
                                shape = RoundedCornerShape(9.dp),
                                color = if (isSelected) Color.White else Color.Transparent,
                                shadowElevation = if (isSelected) 2.dp else 0.dp,
                                modifier = Modifier.testTag("smart_ocr_tab_$tabId")
                            ) {
                                Text(
                                    text = tabTitle,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFF1D4ED8) else Color(0xFF475569),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    // Status Banner if active
                    if (isBusy || statusBannerText.isNotBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isBusy) Color(0xFFEFF6FF) else Color(0xFFECFDF5)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF1D4ED8)
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF059669),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = statusBannerText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isBusy) Color(0xFF1E40AF) else Color(0xFF065F46),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // =========================================================
                    // 3. Main Tab Content Area
                    // =========================================================
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (activeTab) {
                            // -------------------------------------------------
                            // TAB 1: Smart Area Selection OCR + Pre-OCR Scanner Filters
                            // -------------------------------------------------
                            "AREA_FILTER" -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Pre-OCR Scanner Enhancement Filters Card (Upgrade 5)
                                    item {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.White)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                                        Icon(
                                                            Icons.Default.FilterBAndW,
                                                            contentDescription = null,
                                                            tint = Color(0xFF1D4ED8),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Text(
                                                            text = "فلاتر تحسين الصفحات الممسوحة قبل الـ OCR",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp,
                                                            color = Color(0xFF0F172A)
                                                        )
                                                    }
                                                    Text(
                                                        text = selectedPreOcrFilter.titleAr,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF1D4ED8)
                                                    )
                                                }

                                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    items(SmartTextExtractionEngine.PreOcrFilter.values().toList()) { filter ->
                                                        val isSelected = selectedPreOcrFilter == filter
                                                        FilterChip(
                                                            selected = isSelected,
                                                            onClick = { selectedPreOcrFilter = filter },
                                                            label = { Text(filter.titleAr, fontSize = 10.sp) },
                                                            colors = FilterChipDefaults.filterChipColors(
                                                                selectedContainerColor = Color(0xFF1D4ED8),
                                                                selectedLabelColor = Color.White
                                                            )
                                                        )
                                                    }
                                                }

                                                // Deskew Rotation Adjustment Slider
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = "تصحيح الميلان (${deskewAngle.toInt()}°):",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF475569)
                                                    )
                                                    Slider(
                                                        value = deskewAngle,
                                                        onValueChange = { deskewAngle = it },
                                                        valueRange = -15f..15f,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    TextButton(
                                                        onClick = { deskewAngle = 0f },
                                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text("تصفير", fontSize = 10.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Smart Area / Lasso Selection Card (Upgrade 1)
                                    item {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.White)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                                        Icon(
                                                            Icons.Default.Crop,
                                                            contentDescription = null,
                                                            tint = Color(0xFF059669),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Text(
                                                            text = "التحديد الذكي بالمنطقة (اسحب المربع على الصفحة)",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp,
                                                            color = Color(0xFF0F172A)
                                                        )
                                                    }

                                                    // Page Switcher if multi-page PDF
                                                    if (totalPages > 1) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            IconButton(
                                                                onClick = {
                                                                    if (currentPageIndex > 0) currentPageIndex--
                                                                },
                                                                enabled = currentPageIndex > 0,
                                                                modifier = Modifier.size(26.dp)
                                                            ) {
                                                                Icon(Icons.Default.ChevronRight, contentDescription = "الصفحة السابقة", modifier = Modifier.size(18.dp))
                                                            }
                                                            Text(
                                                                text = "ص ${currentPageIndex + 1} / $totalPages",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(0xFF1D4ED8)
                                                            )
                                                            IconButton(
                                                                onClick = {
                                                                    if (currentPageIndex < totalPages - 1) currentPageIndex++
                                                                },
                                                                enabled = currentPageIndex < totalPages - 1,
                                                                modifier = Modifier.size(26.dp)
                                                            ) {
                                                                Icon(Icons.Default.ChevronLeft, contentDescription = "الصفحة التالية", modifier = Modifier.size(18.dp))
                                                            }
                                                        }
                                                    }
                                                }

                                                // Quick Region Presets (الأعمدة، الحواشي، النصف العلوي...)
                                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    items(SmartTextExtractionEngine.AreaPreset.values().toList()) { preset ->
                                                        val isPresetSelected = selectedAreaPreset == preset
                                                        AssistChip(
                                                            onClick = {
                                                                selectedAreaPreset = preset
                                                                selectionRectNorm = RectF(
                                                                    preset.rectNorm.left,
                                                                    preset.rectNorm.top,
                                                                    preset.rectNorm.right,
                                                                    preset.rectNorm.bottom
                                                                )
                                                            },
                                                            label = { Text(preset.titleAr, fontSize = 10.sp) },
                                                            colors = AssistChipDefaults.assistChipColors(
                                                                containerColor = if (isPresetSelected) Color(0xFF059669).copy(alpha = 0.15f) else Color(0xFFF1F5F9),
                                                                labelColor = if (isPresetSelected) Color(0xFF065F46) else Color(0xFF334155)
                                                            )
                                                        )
                                                    }
                                                }

                                                // Interactive Page & Selection Box Canvas
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(270.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(Color(0xFF1E293B))
                                                        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(12.dp)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    val displayBmp = previewEnhancedBitmap ?: rawPageBitmap
                                                    if (displayBmp != null) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxHeight()
                                                                .aspectRatio(
                                                                    (displayBmp.width.toFloat() / displayBmp.height.coerceAtLeast(1)).coerceIn(0.45f, 1.8f)
                                                                )
                                                        ) {
                                                            Image(
                                                                bitmap = displayBmp.asImageBitmap(),
                                                                contentDescription = "معاينة الصفحة والتحديد",
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentScale = ContentScale.FillBounds
                                                            )

                                                            // Interactive Lasso / Rectangle Drag Overlay
                                                            var dragStartNorm by remember { mutableStateOf<Offset?>(null) }
                                                            Canvas(
                                                                modifier = Modifier
                                                                    .fillMaxSize()
                                                                    .pointerInput(Unit) {
                                                                        detectDragGestures(
                                                                            onDragStart = { offset ->
                                                                                val nx = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0.02f, 0.98f)
                                                                                val ny = (offset.y / size.height.coerceAtLeast(1)).coerceIn(0.02f, 0.98f)
                                                                                dragStartNorm = Offset(nx, ny)
                                                                                selectedAreaPreset = null
                                                                            },
                                                                            onDrag = { change, _ ->
                                                                                change.consume()
                                                                                val start = dragStartNorm ?: return@detectDragGestures
                                                                                val curX = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0.02f, 0.98f)
                                                                                val curY = (change.position.y / size.height.coerceAtLeast(1)).coerceIn(0.02f, 0.98f)
                                                                                val l = min(start.x, curX)
                                                                                val t = min(start.y, curY)
                                                                                val r = max(start.x, curX).coerceAtLeast(l + 0.08f)
                                                                                val b = max(start.y, curY).coerceAtLeast(t + 0.08f)
                                                                                selectionRectNorm = RectF(l, t, r.coerceAtMost(0.99f), b.coerceAtMost(0.99f))
                                                                            }
                                                                        )
                                                                    }
                                                            ) {
                                                                val leftPx = selectionRectNorm.left * size.width
                                                                val topPx = selectionRectNorm.top * size.height
                                                                val rectW = (selectionRectNorm.right - selectionRectNorm.left) * size.width
                                                                val rectH = (selectionRectNorm.bottom - selectionRectNorm.top) * size.height

                                                                // Semi-transparent green highlight inside selection area
                                                                drawRect(
                                                                    color = Color(0x3310B981),
                                                                    topLeft = Offset(leftPx, topPx),
                                                                    size = Size(rectW, rectH)
                                                                )

                                                                // Dashed border
                                                                drawRect(
                                                                    color = Color(0xFF10B981),
                                                                    topLeft = Offset(leftPx, topPx),
                                                                    size = Size(rectW, rectH),
                                                                    style = Stroke(
                                                                        width = 3.dp.toPx(),
                                                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f)
                                                                    )
                                                                )

                                                                // Corner handles
                                                                val handleRadius = 6.dp.toPx()
                                                                val corners = listOf(
                                                                    Offset(leftPx, topPx),
                                                                    Offset(leftPx + rectW, topPx),
                                                                    Offset(leftPx, topPx + rectH),
                                                                    Offset(leftPx + rectW, topPx + rectH)
                                                                )
                                                                corners.forEach { pt ->
                                                                    drawCircle(
                                                                        color = Color.White,
                                                                        radius = handleRadius + 2f,
                                                                        center = pt
                                                                    )
                                                                    drawCircle(
                                                                        color = Color(0xFF059669),
                                                                        radius = handleRadius,
                                                                        center = pt
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    } else {
                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                                            modifier = Modifier.padding(16.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.ImageSearch,
                                                                contentDescription = null,
                                                                tint = Color(0xFF94A3B8),
                                                                modifier = Modifier.size(36.dp)
                                                            )
                                                            Text(
                                                                text = if (selectedUri == null) "اختر ملف PDF أو صورة ممسوحة لعرض الصفحة وتحديد منطقة الاستخراج"
                                                                else "مستند نصي مباشر — يمكنك استخدام قوالب المنطقة أو الانتقال لتبويب تنظيف النص",
                                                                fontSize = 11.sp,
                                                                color = Color(0xFFCBD5E1),
                                                                textAlign = TextAlign.Center
                                                            )
                                                        }
                                                    }
                                                }

                                                // AI vs Local toggle + Extract Region Button
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.clickable { useAiForAreaOcr = !useAiForAreaOcr }
                                                    ) {
                                                        Checkbox(
                                                            checked = useAiForAreaOcr,
                                                            onCheckedChange = { useAiForAreaOcr = it }
                                                        )
                                                        Text(
                                                            text = "استخدام الذكاء الاصطناعي OCR مع التشكيل",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            color = Color(0xFF334155)
                                                        )
                                                    }

                                                    Text(
                                                        text = "النطاق: ${(selectionRectNorm.width() * 100).toInt()}% × ${(selectionRectNorm.height() * 100).toInt()}%",
                                                        fontSize = 10.sp,
                                                        color = Color(0xFF64748B)
                                                    )
                                                }

                                                Button(
                                                    onClick = {
                                                        val uri = selectedUri
                                                        if (uri == null && workingExtractedText.isBlank() && originalExtractedText.isBlank()) {
                                                            Toast.makeText(context, "يرجى اختيار ملف أو إدخال نص أولاً", Toast.LENGTH_SHORT).show()
                                                            return@Button
                                                        }
                                                        coroutineScope.launch {
                                                            isBusy = true
                                                            statusBannerText = "جاري قص المنطقة المحددة وتطبيق فلتر (${selectedPreOcrFilter.titleAr}) واستخراج النص..."
                                                            val res = SmartTextExtractionEngine.extractTextFromSelectedArea(
                                                                context = context,
                                                                uri = uri,
                                                                fileName = selectedFileName,
                                                                pageIndex = currentPageIndex,
                                                                areaRectNorm = selectionRectNorm,
                                                                preOcrFilter = selectedPreOcrFilter,
                                                                deskewDegrees = deskewAngle,
                                                                useAiOcr = useAiForAreaOcr,
                                                                fallbackFullText = originalExtractedText.ifBlank { workingExtractedText }
                                                            )
                                                            if (res.isSuccess) {
                                                                val extracted = res.getOrDefault("")
                                                                workingExtractedText = extracted
                                                                if (originalExtractedText.isBlank()) {
                                                                    originalExtractedText = extracted
                                                                }
                                                                statusBannerText = "تم استخراج نص المنطقة المحددة بنجاح (${extracted.length} حرف) ✔"
                                                                activeTab = "POST_PROCESS"
                                                            } else {
                                                                statusBannerText = "تعذر الاستخراج: ${res.exceptionOrNull()?.localizedMessage}"
                                                            }
                                                            isBusy = false
                                                        }
                                                    },
                                                    enabled = !isBusy,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(48.dp)
                                                        .testTag("extract_selected_area_button"),
                                                    shape = RoundedCornerShape(12.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                                                ) {
                                                    Icon(Icons.Default.CropFree, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "استخراج النص من المنطقة المحددة الآن",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // -------------------------------------------------
                            // TAB 2: Arabic Text Post-Processing & Normalization Lab (Upgrade 2)
                            // -------------------------------------------------
                            "POST_PROCESS" -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Instant Arabic Cleaning & Normalization Action Chips
                                    item {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.White)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                                        Icon(
                                                            Icons.Default.AutoFixHigh,
                                                            contentDescription = null,
                                                            tint = Color(0xFF7C3AED),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Text(
                                                            text = "مختبر تنظيف ومعالجة النص العربي (بنقرة واحدة)",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp,
                                                            color = Color(0xFF0F172A)
                                                        )
                                                    }

                                                    TextButton(
                                                        onClick = {
                                                            workingExtractedText = originalExtractedText
                                                            statusBannerText = "تمت استعادة النص الأصلي قبل التعديلات"
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                    ) {
                                                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text("استعادة الأصل", fontSize = 10.sp)
                                                    }
                                                }

                                                // Row 1: Tashkeel & Tatweel & Hamzas
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    FilledTonalButton(
                                                        onClick = {
                                                            workingExtractedText = SmartTextExtractionEngine.stripAllTashkeel(workingExtractedText)
                                                            statusBannerText = "تمت إزالة التشكيل والحركات بالكامل ✔"
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                                    ) {
                                                        Text("إزالة التشكيل", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }

                                                    FilledTonalButton(
                                                        onClick = {
                                                            workingExtractedText = SmartTextExtractionEngine.keepBasicTashkeelOnly(workingExtractedText)
                                                            statusBannerText = "تم إبقاء التشكيل الأساسي وإزالة الرموز الزخرفية ✔"
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                                    ) {
                                                        Text("تشكيل أساسي فقط", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }

                                                    FilledTonalButton(
                                                        onClick = {
                                                            workingExtractedText = SmartTextExtractionEngine.removeTatweel(workingExtractedText)
                                                            statusBannerText = "تم حذف التطويل (الكشيدة ـ) بالكامل ✔"
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                                    ) {
                                                        Text("حذف الكشيدة (ـ)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }

                                                // Row 2: Broken Arabic PDF fix, Smart Unwrap, Normalize Hamzas
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Button(
                                                        onClick = {
                                                            workingExtractedText = SmartTextExtractionEngine.fixBrokenAndReversedArabicText(workingExtractedText)
                                                            statusBannerText = "تم إصلاح الحروف المقطّعة والكلمات المعكوسة وتوحيد محارف العربية ✔"
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                                    ) {
                                                        Text("إصلاح النص المقطّع/المعكوس", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }

                                                    Button(
                                                        onClick = {
                                                            workingExtractedText = SmartTextExtractionEngine.smartUnwrapParagraphs(workingExtractedText)
                                                            statusBannerText = "تم دمج الأسطر المكسورة في فقرات متصلة ذكية ✔"
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                                    ) {
                                                        Text("دمج الأسطر الذكي (Unwrap)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }

                                                // Row 3: Hamzas, Eastern Numerals, Western Numerals, Clean Punctuation
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            workingExtractedText = SmartTextExtractionEngine.normalizeHamzasAndAlef(workingExtractedText)
                                                            statusBannerText = "تم توحيد الهمزات (أ، إ، آ ← ا) ✔"
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                                    ) {
                                                        Text("توحيد الهمزات", fontSize = 10.sp)
                                                    }

                                                    OutlinedButton(
                                                        onClick = {
                                                            workingExtractedText = SmartTextExtractionEngine.convertToEasternArabicNumerals(workingExtractedText)
                                                            statusBannerText = "تم تحويل الأرقام إلى مشرقية (٠١٢٣٤٥٦٧٨٩) ✔"
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                                    ) {
                                                        Text("أرقام (١٢٣)", fontSize = 10.sp)
                                                    }

                                                    OutlinedButton(
                                                        onClick = {
                                                            workingExtractedText = SmartTextExtractionEngine.convertToWesternNumerals(workingExtractedText)
                                                            statusBannerText = "تم تحويل الأرقام إلى غربية (0123456789) ✔"
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                                    ) {
                                                        Text("أرقام (123)", fontSize = 10.sp)
                                                    }

                                                    OutlinedButton(
                                                        onClick = {
                                                            workingExtractedText = SmartTextExtractionEngine.cleanSpacesAndArabicPunctuation(workingExtractedText)
                                                            statusBannerText = "تم ضبط المسافات وعلامات الترقيم ✔"
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                                    ) {
                                                        Text("ضبط الترقيم", fontSize = 10.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Editable Text Box & Live Metrics
                                    item {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.White)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                val wordCount = remember(workingExtractedText) {
                                                    DocumentParser.countWords(workingExtractedText)
                                                }
                                                val lineCount = remember(workingExtractedText) {
                                                    if (workingExtractedText.isBlank()) 0 else workingExtractedText.lines().size
                                                }

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "النص المستخرج والمعالج (قابل للتعديل المباشر):",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = Color(0xFF0F172A)
                                                    )
                                                    Text(
                                                        text = "$wordCount كلمة • ${workingExtractedText.length} حرف • $lineCount سطر",
                                                        fontSize = 10.sp,
                                                        color = Color(0xFF64748B)
                                                    )
                                                }

                                                OutlinedTextField(
                                                    value = workingExtractedText,
                                                    onValueChange = { workingExtractedText = it },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(220.dp)
                                                        .testTag("post_process_text_field"),
                                                    textStyle = TextStyle(
                                                        fontSize = 14.sp,
                                                        lineHeight = 23.sp
                                                    ),
                                                    placeholder = {
                                                        Text("سيظهر النص المستخرج هنا لمعالجته وتنظيفه أو تعديله يدوياً...")
                                                    },
                                                    shape = RoundedCornerShape(10.dp)
                                                )

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            clipboardManager.setText(AnnotatedString(workingExtractedText))
                                                            Toast.makeText(context, "تم نسخ النص المعالج إلى الحافظة 📋", Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        shape = RoundedCornerShape(10.dp)
                                                    ) {
                                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("نسخ النص", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }

                                                    Button(
                                                        onClick = {
                                                            onSendToEditor(workingExtractedText, "نص مستخرج ومعالج عبر مختبر العربية")
                                                            Toast.makeText(context, "تم إرسال النص المعالج إلى محرر الكتابة ✍️", Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor),
                                                        shape = RoundedCornerShape(10.dp)
                                                    ) {
                                                        Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("إدراج في المحرر", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // -------------------------------------------------
                            // TAB 3: Table & Structured Data Extractor (Upgrade 3)
                            // -------------------------------------------------
                            "TABLES" -> {
                                val currentTable = extractedTables.getOrNull(activeTableIndex)
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                                    Icon(
                                                        Icons.Default.TableChart,
                                                        contentDescription = null,
                                                        tint = Color(0xFF059669),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Text(
                                                        text = "مستخرج الجداول والبيانات المهيكلة (CSV / Markdown)",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = Color(0xFF0F172A)
                                                    )
                                                }

                                                if (extractedTables.size > 1) {
                                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        extractedTables.forEachIndexed { idx, _ ->
                                                            FilterChip(
                                                                selected = activeTableIndex == idx,
                                                                onClick = { activeTableIndex = idx },
                                                                label = { Text("جدول ${idx + 1}", fontSize = 10.sp) }
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            if (currentTable != null) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Button(
                                                        onClick = {
                                                            val csvFile = SmartTextExtractionEngine.exportTableToCsvFile(
                                                                context = context,
                                                                table = currentTable,
                                                                baseFileName = selectedFileName
                                                            )
                                                            pendingCsvFileToSave = csvFile
                                                            saveCsvLauncher.launch(csvFile.name)
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                                    ) {
                                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("تصدير CSV (Excel)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }

                                                    FilledTonalButton(
                                                        onClick = {
                                                            val md = SmartTextExtractionEngine.formatTableAsMarkdown(currentTable)
                                                            clipboardManager.setText(AnnotatedString(md))
                                                            Toast.makeText(context, "تم نسخ الجدول بصيغة Markdown Table 📋", Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                                    ) {
                                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("نسخ Markdown", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }

                                                    OutlinedButton(
                                                        onClick = {
                                                            val md = SmartTextExtractionEngine.formatTableAsMarkdown(currentTable)
                                                            onSendToEditor(md, "جدول مهيكل مستخرج")
                                                            Toast.makeText(context, "تم إدراج الجدول في محرر الكتابة 📊", Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                                    ) {
                                                        Text("إرسال للمحرر", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Interactive Table Preview Grid
                                    if (currentTable != null) {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                            shape = RoundedCornerShape(14.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.White)
                                        ) {
                                            Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "${currentTable.title} (${currentTable.rows.size} صف × ${currentTable.headers.size} عمود) • اضغط على أي خلية لتعديلها",
                                                        fontSize = 10.sp,
                                                        color = Color(0xFF64748B)
                                                    )

                                                    TextButton(
                                                        onClick = {
                                                            val emptyRow = List(currentTable.headers.size) { "بيان جديد" }
                                                            val updatedTable = currentTable.copy(rows = currentTable.rows + listOf(emptyRow))
                                                            extractedTables = extractedTables.toMutableList().apply {
                                                                set(activeTableIndex, updatedTable)
                                                            }
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        Text("إضافة صف", fontSize = 10.sp)
                                                    }
                                                }

                                                val hScroll = rememberScrollState()
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .horizontalScroll(hScroll)
                                                ) {
                                                    LazyColumn(modifier = Modifier.fillMaxHeight()) {
                                                        // Header Row
                                                        item {
                                                            Row(
                                                                modifier = Modifier
                                                                    .background(Color(0xFF1E293B), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                                            ) {
                                                                Text(
                                                                    text = "#",
                                                                    color = Color.White,
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 11.sp,
                                                                    textAlign = TextAlign.Center,
                                                                    modifier = Modifier.width(38.dp).padding(8.dp)
                                                                )
                                                                currentTable.headers.forEach { hdr ->
                                                                    Text(
                                                                        text = hdr,
                                                                        color = Color.White,
                                                                        fontWeight = FontWeight.Bold,
                                                                        fontSize = 11.sp,
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis,
                                                                        modifier = Modifier
                                                                            .width(150.dp)
                                                                            .border(0.5.dp, Color.White.copy(alpha = 0.2f))
                                                                            .padding(8.dp)
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        // Data Rows
                                                        itemsIndexed(currentTable.rows) { rIdx, row ->
                                                            Row(
                                                                modifier = Modifier
                                                                    .background(if (rIdx % 2 == 0) Color(0xFFF8FAFC) else Color.White)
                                                                    .border(0.5.dp, Color(0xFFE2E8F0))
                                                            ) {
                                                                Text(
                                                                    text = "${rIdx + 1}",
                                                                    color = Color.Gray,
                                                                    fontSize = 10.sp,
                                                                    textAlign = TextAlign.Center,
                                                                    modifier = Modifier.width(38.dp).padding(8.dp)
                                                                )
                                                                currentTable.headers.indices.forEach { cIdx ->
                                                                    val cellText = row.getOrElse(cIdx) { "" }
                                                                    Text(
                                                                        text = cellText,
                                                                        color = Color(0xFF1E293B),
                                                                        fontSize = 11.sp,
                                                                        maxLines = 2,
                                                                        overflow = TextOverflow.Ellipsis,
                                                                        modifier = Modifier
                                                                            .width(150.dp)
                                                                            .clickable {
                                                                                editingTableCell = Triple(rIdx, cIdx, cellText)
                                                                            }
                                                                            .border(0.5.dp, Color(0xFFE2E8F0))
                                                                            .padding(8.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.White)
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Default.TableRows, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(36.dp))
                                                Text("لا يوجد نص مستخرج بعد لبناء الجداول المهيكلة.", fontSize = 12.sp, color = Color.Gray)
                                            }
                                        }
                                    }
                                }
                            }

                            // -------------------------------------------------
                            // TAB 4: Smart Entity & Citation Extractor (Upgrade 4)
                            // -------------------------------------------------
                            "ENTITIES" -> {
                                val filteredEntities = remember(extractedEntities, selectedEntityFilter) {
                                    if (selectedEntityFilter == null) extractedEntities
                                    else extractedEntities.filter { it.category == selectedEntityFilter }
                                }

                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "المستخرج الذكي للكيانات والاقتباسات والمصطلحات",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF0F172A)
                                                )

                                                // Citation style selector
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    listOf("ARABIC" to "توثيق عربي", "APA" to "APA", "MLA" to "MLA").forEach { (st, lbl) ->
                                                        Surface(
                                                            onClick = { citationStyle = st },
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = if (citationStyle == st) Color(0xFF1D4ED8) else Color(0xFFF1F5F9)
                                                        ) {
                                                            Text(
                                                                text = lbl,
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (citationStyle == st) Color.White else Color(0xFF475569),
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            // Category filter chips
                                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                item {
                                                    FilterChip(
                                                        selected = selectedEntityFilter == null,
                                                        onClick = { selectedEntityFilter = null },
                                                        label = { Text("الكل (${extractedEntities.size})", fontSize = 10.sp) }
                                                    )
                                                }
                                                items(SmartTextExtractionEngine.EntityCategory.values().toList()) { cat ->
                                                    val count = extractedEntities.count { it.category == cat }
                                                    FilterChip(
                                                        selected = selectedEntityFilter == cat,
                                                        onClick = { selectedEntityFilter = cat },
                                                        label = { Text("${cat.titleAr} ($count)", fontSize = 10.sp) }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Entities List
                                    LazyColumn(
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(filteredEntities, key = { it.id }) { entity ->
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color.White)
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = Color(0xFF1D4ED8).copy(alpha = 0.1f)
                                                        ) {
                                                            Text(
                                                                text = "${entity.category.titleAr} • ${entity.subTypeLabel}",
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(0xFF1D4ED8),
                                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                            )
                                                        }
                                                    }

                                                    Text(
                                                        text = entity.text,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = Color(0xFF0F172A)
                                                    )

                                                    if (entity.contextSnippet.isNotBlank() && entity.contextSnippet != entity.text) {
                                                        Text(
                                                            text = "السياق: ...${entity.contextSnippet}...",
                                                            fontSize = 11.sp,
                                                            color = Color(0xFF64748B),
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }

                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        OutlinedButton(
                                                            onClick = {
                                                                val cited = SmartTextExtractionEngine.formatAcademicCitation(
                                                                    quoteOrEntity = entity.text,
                                                                    sourceDocumentName = selectedFileName,
                                                                    pageLabel = "ص ${currentPageIndex + 1}",
                                                                    style = citationStyle
                                                                )
                                                                clipboardManager.setText(AnnotatedString(cited))
                                                                Toast.makeText(context, "تم نسخ التوثيق الأكاديمي ($citationStyle) 📋", Toast.LENGTH_SHORT).show()
                                                            },
                                                            shape = RoundedCornerShape(8.dp),
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Icon(Icons.Default.FormatQuote, contentDescription = null, modifier = Modifier.size(13.dp))
                                                            Spacer(modifier = Modifier.width(3.dp))
                                                            Text("نسخ موثق ($citationStyle)", fontSize = 9.sp)
                                                        }

                                                        FilledTonalButton(
                                                            onClick = {
                                                                val card = DocumentFlashcardItem(
                                                                    uriString = selectedUri?.toString() ?: "extracted_doc",
                                                                    termOrQuestion = "ما دلالة أو سياق: «${entity.text.take(45)}»؟",
                                                                    definitionOrAnswer = entity.contextSnippet.ifBlank { entity.text },
                                                                    positionLabel = "ص ${currentPageIndex + 1}"
                                                                )
                                                                onAddFlashcards(listOf(card))
                                                                Toast.makeText(context, "تمت الإضافة إلى بطاقات الاستذكار 🗂️", Toast.LENGTH_SHORT).show()
                                                            },
                                                            shape = RoundedCornerShape(8.dp),
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Icon(Icons.Default.Style, contentDescription = null, modifier = Modifier.size(13.dp))
                                                            Spacer(modifier = Modifier.width(3.dp))
                                                            Text("كبطاقة استذكار", fontSize = 9.sp)
                                                        }

                                                        Button(
                                                            onClick = {
                                                                val cited = SmartTextExtractionEngine.formatAcademicCitation(
                                                                    quoteOrEntity = entity.text,
                                                                    sourceDocumentName = selectedFileName,
                                                                    pageLabel = "ص ${currentPageIndex + 1}",
                                                                    style = citationStyle
                                                                )
                                                                onSendToEditor(cited, entity.subTypeLabel)
                                                                Toast.makeText(context, "تم إرسال الكيان الموثق إلى المحرر ✍️", Toast.LENGTH_SHORT).show()
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor),
                                                            shape = RoundedCornerShape(8.dp),
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Text("إلى المحرر", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // -------------------------------------------------
                            // TAB 5: Batch Range Extraction & Direct Pipeline (Upgrade 6)
                            // -------------------------------------------------
                            "BATCH_PIPELINE" -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Batch Page Range Selector Card
                                    item {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.White)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.LibraryBooks,
                                                        contentDescription = null,
                                                        tint = Color(0xFF1D4ED8),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Text(
                                                        text = "الاستخراج الدفعي الموجه (نطاق صفحات أو علامات مرجعية)",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = Color(0xFF0F172A)
                                                    )
                                                }

                                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    items(SmartTextExtractionEngine.BatchRangeMode.values().toList()) { mode ->
                                                        FilterChip(
                                                            selected = batchRangeMode == mode,
                                                            onClick = { batchRangeMode = mode },
                                                            label = {
                                                                val suffix = if (mode == SmartTextExtractionEngine.BatchRangeMode.BOOKMARKED_ONLY) {
                                                                    " (${bookmarks.size})"
                                                                } else ""
                                                                Text(mode.titleAr + suffix, fontSize = 10.sp)
                                                            }
                                                        )
                                                    }
                                                }

                                                if (batchRangeMode == SmartTextExtractionEngine.BatchRangeMode.CUSTOM_RANGE) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        OutlinedTextField(
                                                            value = batchStartPageInput,
                                                            onValueChange = { batchStartPageInput = it.filter { ch -> ch.isDigit() } },
                                                            label = { Text("من صفحة", fontSize = 10.sp) },
                                                            singleLine = true,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        OutlinedTextField(
                                                            value = batchEndPageInput,
                                                            onValueChange = { batchEndPageInput = it.filter { ch -> ch.isDigit() } },
                                                            label = { Text("إلى صفحة (الإجمالي: $totalPages)", fontSize = 10.sp) },
                                                            singleLine = true,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                }

                                                // Auto post-process options during batch run
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    FilterChip(
                                                        selected = batchAutoFixArabic,
                                                        onClick = { batchAutoFixArabic = !batchAutoFixArabic },
                                                        label = { Text("إصلاح العربية تلقائياً", fontSize = 10.sp) }
                                                    )
                                                    FilterChip(
                                                        selected = batchAutoUnwrapLines,
                                                        onClick = { batchAutoUnwrapLines = !batchAutoUnwrapLines },
                                                        label = { Text("دمج الأسطر الذكي", fontSize = 10.sp) }
                                                    )
                                                    FilterChip(
                                                        selected = batchStripTashkeel,
                                                        onClick = { batchStripTashkeel = !batchStripTashkeel },
                                                        label = { Text("إزالة التشكيل", fontSize = 10.sp) }
                                                    )
                                                }

                                                // Progress bar if batch running
                                                batchProgress?.let { prog ->
                                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        LinearProgressIndicator(
                                                            progress = { prog.percentage },
                                                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                                            color = Color(0xFF1D4ED8)
                                                        )
                                                        Text(
                                                            text = prog.statusMessage,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF1D4ED8)
                                                        )
                                                    }
                                                }

                                                Button(
                                                    onClick = {
                                                        val uri = selectedUri
                                                        if (uri == null && workingExtractedText.isBlank() && originalExtractedText.isBlank()) {
                                                            Toast.makeText(context, "يرجى اختيار ملف أو تحميل نص أولاً", Toast.LENGTH_SHORT).show()
                                                            return@Button
                                                        }
                                                        val startP = (batchStartPageInput.toIntOrNull() ?: 1).coerceIn(1, totalPages.coerceAtLeast(1))
                                                        val endP = (batchEndPageInput.toIntOrNull() ?: startP).coerceIn(startP, totalPages.coerceAtLeast(1))
                                                        val postCfg = SmartTextExtractionEngine.PostProcessConfig(
                                                            stripAllTashkeel = batchStripTashkeel,
                                                            fixBrokenArabic = batchAutoFixArabic,
                                                            smartUnwrapLines = batchAutoUnwrapLines,
                                                            removeTatweel = true
                                                        )

                                                        coroutineScope.launch {
                                                            isBusy = true
                                                            val res = SmartTextExtractionEngine.extractBatchPagesRange(
                                                                context = context,
                                                                uri = uri,
                                                                fileName = selectedFileName,
                                                                rangeMode = batchRangeMode,
                                                                customStartPageOneIndexed = startP,
                                                                customEndPageOneIndexed = endP,
                                                                bookmarkedPageIndicesZeroBased = bookmarks.map { it.positionIndex },
                                                                preOcrFilter = selectedPreOcrFilter,
                                                                postProcessConfig = postCfg,
                                                                useAiOcrWhenAvailable = useAiForAreaOcr,
                                                                fallbackFullText = originalExtractedText.ifBlank { workingExtractedText },
                                                                onProgress = { info ->
                                                                    batchProgress = info
                                                                    statusBannerText = info.statusMessage
                                                                }
                                                            )
                                                            if (res.isSuccess) {
                                                                val batchOut = res.getOrDefault("")
                                                                workingExtractedText = batchOut
                                                                if (originalExtractedText.isBlank()) {
                                                                    originalExtractedText = batchOut
                                                                }
                                                                statusBannerText = "تم استخراج ومعالجة الدفعة بنجاح (${DocumentParser.countWords(batchOut)} كلمة) ✔"
                                                            } else {
                                                                statusBannerText = "تعذر الاستخراج الدفعي: ${res.exceptionOrNull()?.localizedMessage}"
                                                            }
                                                            isBusy = false
                                                        }
                                                    },
                                                    enabled = !isBusy,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(46.dp)
                                                        .testTag("start_batch_extraction_button"),
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8))
                                                ) {
                                                    Icon(Icons.Default.DynamicFeed, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("بدء الاستخراج الدفعي الآن", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }

                                    // Direct 1-Click Action Pipeline Card (مسار التصدير الفوري)
                                    item {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.White)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Bolt,
                                                        contentDescription = null,
                                                        tint = Color(0xFFD97706),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Column {
                                                        Text(
                                                            text = "مسار التصدير والإجراءات الفورية (Direct Pipeline)",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp,
                                                            color = Color(0xFF0F172A)
                                                        )
                                                        Text(
                                                            text = "حوّل النص المستخرج بنقرة واحدة إلى بطاقات استذكار، خريطة ذهنية، ملاحظات، أو قراءة صوتية",
                                                            fontSize = 10.sp,
                                                            color = Color(0xFF64748B)
                                                        )
                                                    }
                                                }

                                                val docPersistenceKey = selectedUri?.toString() ?: "studio_${selectedFileName}"

                                                // Grid of 1-Click Direct Pipeline Actions
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    // 1. To Flashcards
                                                    ElevatedCard(
                                                        onClick = {
                                                            if (workingExtractedText.isBlank()) {
                                                                Toast.makeText(context, "لا يوجد نص مستخرج لتحويله", Toast.LENGTH_SHORT).show()
                                                                return@ElevatedCard
                                                            }
                                                            val cards = SmartTextExtractionEngine.generateFlashcardsFromExtractedText(
                                                                uriString = docPersistenceKey,
                                                                extractedText = workingExtractedText,
                                                                positionLabel = "ص ${currentPageIndex + 1}"
                                                            )
                                                            val existing = MultiFormatDocumentEngine.getDocumentFlashcards(context, docPersistenceKey)
                                                            MultiFormatDocumentEngine.saveDocumentFlashcards(context, docPersistenceKey, existing + cards)
                                                            generatedSessionFlashcards = (cards + generatedSessionFlashcards).distinctBy { it.termOrQuestion + it.definitionOrAnswer }
                                                            onAddFlashcards(cards)
                                                            val formattedCards = cards.joinToString("\n\n") { "🗂️ السؤال/المصطلح: ${it.termOrQuestion}\n💡 الإجابة: ${it.definitionOrAnswer}" }
                                                            onSaveExtractedRecord?.invoke("بطاقات استذكار: $selectedFileName", formattedCards)
                                                            statusBannerText = "تم توليد وحفظ ${cards.size} بطاقات استذكار (Flashcards) من النص المستخرج 🗂️"
                                                            Toast.makeText(context, "تم توليد وحفظ ${cards.size} بطاقات استذكار بنجاح 🗂️", Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.weight(1f).testTag("pipeline_to_flashcards_button"),
                                                        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFF5F3FF))
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.padding(12.dp),
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Icon(Icons.Default.Style, contentDescription = null, tint = Color(0xFF7C3AED))
                                                            Text("إلى بطاقات استذكار", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF5B21B6))
                                                            Text("توليد أسئلة وأجوبة تلقائي", fontSize = 9.sp, color = Color(0xFF6D28D9))
                                                        }
                                                    }

                                                    // 2. To Mind Map Nodes
                                                    ElevatedCard(
                                                        onClick = {
                                                            if (workingExtractedText.isBlank()) {
                                                                Toast.makeText(context, "لا يوجد نص مستخرج لتحويله", Toast.LENGTH_SHORT).show()
                                                                return@ElevatedCard
                                                            }
                                                            val nodes = SmartTextExtractionEngine.generateMindMapNodesFromExtractedText(
                                                                extractedText = workingExtractedText,
                                                                positionIndex = currentPageIndex
                                                            )
                                                            val existing = MultiFormatDocumentEngine.getCustomMindMapNodes(context, docPersistenceKey)
                                                            MultiFormatDocumentEngine.saveCustomMindMapNodes(context, docPersistenceKey, existing + nodes)
                                                            generatedSessionMindMapNodes = (nodes + generatedSessionMindMapNodes).distinctBy { it.title + it.subtitle }
                                                            onAddMindMapNodes(nodes)
                                                            val formattedNodes = nodes.joinToString("\n") { "🕸️ [${it.title}] ← ${it.subtitle}" }
                                                            onSaveExtractedRecord?.invoke("خريطة ذهنية: $selectedFileName", formattedNodes)
                                                            statusBannerText = "تم تحويل الأفكار المستخرجة إلى ${nodes.size} عقد في الخريطة الذهنية 🕸️"
                                                            Toast.makeText(context, "تمت إضافة وحفظ ${nodes.size} عقد في الخريطة الذهنية 🕸️", Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.weight(1f).testTag("pipeline_to_mindmap_button"),
                                                        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFEFF6FF))
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.padding(12.dp),
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Icon(Icons.Default.AccountTree, contentDescription = null, tint = Color(0xFF2563EB))
                                                            Text("إلى الخريطة الذهنية", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF1E40AF))
                                                            Text("تحويل الفقرات لعقد بصرية", fontSize = 9.sp, color = Color(0xFF1D4ED8))
                                                        }
                                                    }
                                                }

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    // 3. To Notes & Highlights
                                                    ElevatedCard(
                                                        onClick = {
                                                            if (workingExtractedText.isBlank()) {
                                                                Toast.makeText(context, "لا يوجد نص مستخرج لحفظه", Toast.LENGTH_SHORT).show()
                                                                return@ElevatedCard
                                                            }
                                                            val hl = DocumentHighlightItem(
                                                                uriString = docPersistenceKey,
                                                                snippetText = workingExtractedText.take(500),
                                                                note = "مستخرج عبر الاستوديو الذكي ($selectedFileName)",
                                                                colorHex = "#38BDF8",
                                                                positionIndex = currentPageIndex,
                                                                positionLabel = "ص ${currentPageIndex + 1}"
                                                            )
                                                            val existing = MultiFormatDocumentEngine.getDocumentHighlights(context, docPersistenceKey)
                                                            MultiFormatDocumentEngine.saveDocumentHighlights(context, docPersistenceKey, existing + hl)
                                                            generatedSessionHighlights = listOf(hl) + generatedSessionHighlights
                                                            onAddHighlightNote(hl)
                                                            onSaveExtractedRecord?.invoke(selectedFileName, workingExtractedText)
                                                            statusBannerText = "تم حفظ النص في دفتر الملاحظات وسجل الاستخلاصات 📝"
                                                            Toast.makeText(context, "تم الحفظ في دفتر الملاحظات بنجاح 📝", Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.weight(1f).testTag("pipeline_to_notes_button"),
                                                        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFFEF3C7))
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.padding(12.dp),
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Icon(Icons.Default.BookmarkAdded, contentDescription = null, tint = Color(0xFFD97706))
                                                            Text("حفظ في الملاحظات", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF92400E))
                                                            Text("إضافة لدفتر الاقتباسات", fontSize = 9.sp, color = Color(0xFFB45309))
                                                        }
                                                    }

                                                    // 4. To Voice Reader (TTS)
                                                    ElevatedCard(
                                                        onClick = {
                                                            if (workingExtractedText.isBlank()) {
                                                                Toast.makeText(context, "لا يوجد نص مستخرج لقراءته صوتياً", Toast.LENGTH_SHORT).show()
                                                                return@ElevatedCard
                                                            }
                                                            if (onSpeakTextAloud != null) {
                                                                onSpeakTextAloud(workingExtractedText)
                                                            } else {
                                                                localTtsManager.prepareText(workingExtractedText)
                                                                localTtsManager.play()
                                                            }
                                                            statusBannerText = "تم تشغيل القارئ الصوتي الذكي (TTS) للنص المستخرج 🔊"
                                                            Toast.makeText(context, "جاري قراءة النص المستخرج صوتياً 🔊", Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.weight(1f).testTag("pipeline_to_tts_button"),
                                                        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFECFDF5))
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.padding(12.dp),
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = Color(0xFF059669))
                                                            Text("القارئ الصوتي (TTS)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF065F46))
                                                            Text("استماع فوري للنص المستخرج", fontSize = 9.sp, color = Color(0xFF047857))
                                                        }
                                                    }
                                                }

                                                // 5. Full Export to Editor Button
                                                Button(
                                                    onClick = {
                                                        if (workingExtractedText.isBlank()) {
                                                            Toast.makeText(context, "لا يوجد نص لإرساله للمحرر", Toast.LENGTH_SHORT).show()
                                                            return@Button
                                                        }
                                                        onSendToEditor(workingExtractedText, "استخراج دفعي ذكي من $selectedFileName")
                                                        Toast.makeText(context, "تم إرسال النص الكامل إلى محرر الكتابة ✍️", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(46.dp)
                                                        .testTag("pipeline_to_editor_button"),
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor)
                                                ) {
                                                    Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("إدراج النص المستخرج بالكامل في محرر الكتابة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }

                                    // Generated Session Outputs Preview Card (Flashcards, Mind Map Nodes, Saved Notes)
                                    if (generatedSessionFlashcards.isNotEmpty() || generatedSessionMindMapNodes.isNotEmpty() || generatedSessionHighlights.isNotEmpty()) {
                                        item {
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(14.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "✨ المخرجات المولّدة في هذه الجلسة",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp,
                                                            color = Color(0xFF0F172A)
                                                        )
                                                        TextButton(
                                                            onClick = {
                                                                val sb = StringBuilder()
                                                                if (generatedSessionFlashcards.isNotEmpty()) {
                                                                    sb.append("### 🗂️ بطاقات الاستذكار المولّدة:\n")
                                                                    generatedSessionFlashcards.forEachIndexed { i, c ->
                                                                        sb.append("${i + 1}. **${c.termOrQuestion}**\n   - ${c.definitionOrAnswer}\n")
                                                                    }
                                                                    sb.append("\n")
                                                                }
                                                                if (generatedSessionMindMapNodes.isNotEmpty()) {
                                                                    sb.append("### 🕸️ عقد الخريطة الذهنية:\n")
                                                                    generatedSessionMindMapNodes.forEach { n ->
                                                                        sb.append("- **${n.title}**: ${n.subtitle}\n")
                                                                    }
                                                                    sb.append("\n")
                                                                }
                                                                if (generatedSessionHighlights.isNotEmpty()) {
                                                                    sb.append("### 📝 الملاحظات المحفوظة:\n")
                                                                    generatedSessionHighlights.forEach { h ->
                                                                        sb.append("> ${h.snippetText}\n")
                                                                    }
                                                                }
                                                                val allText = sb.toString().trim()
                                                                clipboardManager.setText(AnnotatedString(allText))
                                                                onSendToEditor(allText, "مخرجات مسار التصدير الفوري ($selectedFileName)")
                                                                Toast.makeText(context, "تم نسخ المخرجات وإرسالها إلى المحرر بنجاح ✔", Toast.LENGTH_SHORT).show()
                                                            }
                                                        ) {
                                                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text("نسخ وإرسال الكل للمحرر", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }

                                                    if (generatedSessionFlashcards.isNotEmpty()) {
                                                        Text(
                                                            text = "🗂️ بطاقات الاستذكار (${generatedSessionFlashcards.size}):",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 11.sp,
                                                            color = Color(0xFF6D28D9)
                                                        )
                                                        generatedSessionFlashcards.take(5).forEach { card ->
                                                            Surface(
                                                                shape = RoundedCornerShape(8.dp),
                                                                color = Color(0xFFF5F3FF),
                                                                modifier = Modifier.fillMaxWidth()
                                                            ) {
                                                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                                    Text("س: ${card.termOrQuestion}", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF4C1D95))
                                                                    Text("ج: ${card.definitionOrAnswer}", fontSize = 10.sp, color = Color(0xFF5B21B6))
                                                                }
                                                            }
                                                        }
                                                    }

                                                    if (generatedSessionMindMapNodes.isNotEmpty()) {
                                                        Text(
                                                            text = "🕸️ عقد الخريطة الذهنية (${generatedSessionMindMapNodes.size}):",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 11.sp,
                                                            color = Color(0xFF1D4ED8)
                                                        )
                                                        generatedSessionMindMapNodes.take(5).forEach { node ->
                                                            Surface(
                                                                shape = RoundedCornerShape(8.dp),
                                                                color = Color(0xFFEFF6FF),
                                                                modifier = Modifier.fillMaxWidth()
                                                            ) {
                                                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                                    Text("• ${node.title}", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF1E3A8A))
                                                                    Text(node.subtitle, fontSize = 10.sp, color = Color(0xFF1E40AF))
                                                                }
                                                            }
                                                        }
                                                    }

                                                    if (generatedSessionHighlights.isNotEmpty()) {
                                                        Text(
                                                            text = "📝 الملاحظات والاقتباسات المحفوظة (${generatedSessionHighlights.size}):",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 11.sp,
                                                            color = Color(0xFFB45309)
                                                        )
                                                        generatedSessionHighlights.take(3).forEach { hl ->
                                                            Surface(
                                                                shape = RoundedCornerShape(8.dp),
                                                                color = Color(0xFFFEF3C7),
                                                                modifier = Modifier.fillMaxWidth()
                                                            ) {
                                                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                                    Text(hl.snippetText, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 10.sp, color = Color(0xFF78350F))
                                                                    Text(hl.note, fontSize = 9.sp, color = Color(0xFFB45309))
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
                        }
                    }
                }
            }
        }

        // Cell Edit Modal for Table Extractor
        editingTableCell?.let { (rIdx, cIdx, initialVal) ->
            var cellInput by remember(editingTableCell) { mutableStateOf(initialVal) }
            AlertDialog(
                onDismissRequest = { editingTableCell = null },
                title = { Text("تعديل خلية الجدول (صف ${rIdx + 1} • عمود ${cIdx + 1})", fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = cellInput,
                        onValueChange = { cellInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("محتوى الخلية") }
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val currentTable = extractedTables.getOrNull(activeTableIndex)
                            if (currentTable != null && rIdx in currentTable.rows.indices) {
                                val updatedRows = currentTable.rows.toMutableList()
                                val updatedRow = updatedRows[rIdx].toMutableList()
                                if (cIdx in updatedRow.indices) {
                                    updatedRow[cIdx] = cellInput
                                    updatedRows[rIdx] = updatedRow
                                    val updatedTable = currentTable.copy(rows = updatedRows)
                                    extractedTables = extractedTables.toMutableList().apply {
                                        set(activeTableIndex, updatedTable)
                                    }
                                }
                            }
                            editingTableCell = null
                        }
                    ) {
                        Text("حفظ الخلية")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editingTableCell = null }) {
                        Text("إلغاء")
                    }
                }
            )
        }
    }
}
