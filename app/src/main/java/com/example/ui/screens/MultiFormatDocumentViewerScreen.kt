package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppThemeData
import com.example.data.model.DocumentProgressEntity
import com.example.data.model.ReaderSettingsState
import com.example.data.model.ReaderTheme
import com.example.ui.MainViewModel
import com.example.ui.components.ReaderSettingsBar
import com.example.ui.components.ReaderSettingsBottomSheet
import com.example.ui.components.TtsPlayerBar
import com.example.util.AccessDatabaseInfo
import com.example.util.DocmoDocumentBridge
import com.example.util.DocumentBookmarkItem
import com.example.util.DocumentHighlightItem
import com.example.util.DocumentOutlineItem
import com.example.util.DocumentParser
import com.example.util.EpubBookContent
import com.example.util.MultiFormatDocumentEngine
import com.example.util.PdfDiagnosticReport
import com.example.util.PptxPresentationContent
import com.example.util.SpreadsheetContent
import com.example.util.TtsManager
import com.example.util.TtsPlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiFormatDocumentViewerScreen(
    uriString: String,
    fileName: String,
    theme: AppThemeData,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uri = remember(uriString) { Uri.parse(uriString) }
    val extension = remember(fileName) { MultiFormatDocumentEngine.getFileExtension(fileName) }

    // Saved Progress state from Room
    val savedProgress by viewModel.getDocumentProgress(uriString).collectAsState(initial = null)
    var initialProgressLoaded by remember { mutableStateOf(false) }

    // Reading settings state (حالة إعدادات القراءة الشاملة)
    var readerSettings by remember {
        mutableStateOf(
            ReaderSettingsState(
                theme = ReaderTheme.LIGHT,
                fontSizeSp = 16f,
                lineHeightMultiplier = 1.6f
            )
        )
    }
    val readerTheme = readerSettings.theme
    val fontSizeSp = readerSettings.fontSizeSp
    val lineSpacing = readerSettings.lineHeightMultiplier
    val fontWeight = readerSettings.fontWeight

    // UI Sheets & Dialogs
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showOutlineSheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showConverterStudioDialog by remember { mutableStateOf(false) }
    var showExtractPdfRangeDialog by remember { mutableStateOf(false) }

    // Upgrade 1: Highlights, Notes & Send-to-Editor Dialog
    var highlights by remember { mutableStateOf<List<DocumentHighlightItem>>(emptyList()) }
    var bookmarks by remember { mutableStateOf<List<DocumentBookmarkItem>>(emptyList()) }
    var activeQuoteDraft by remember { mutableStateOf<String?>(null) }
    var activeQuoteNote by remember { mutableStateOf("") }
    var activeQuoteColorHex by remember { mutableStateOf("#FDE047") }

    // Upgrade 2: Smart TTS Voice Reader inside Document Viewer
    val ttsManager = remember { TtsManager(context) }
    val ttsPlaybackState by ttsManager.playbackState.collectAsState()
    val ttsCurrentSentence by ttsManager.currentSentenceText.collectAsState()
    val ttsCurrentSentenceIndex by ttsManager.currentSentenceIndex.collectAsState()
    val ttsSpeechRate by ttsManager.speechRate.collectAsState()
    var showTtsBar by remember { mutableStateOf(false) }

    // Upgrade 3: Immersive Fullscreen, Auto-Scroll, PDF Night/Sepia & Thumbnails Strip
    var isImmersiveFullscreen by remember { mutableStateOf(false) }
    var isAutoScrollActive by remember { mutableStateOf(false) }
    var autoScrollSpeed by remember { mutableIntStateOf(1) } // 1, 2, 3
    var showPdfThumbnailsStrip by remember { mutableStateOf(false) }
    var pdfColorFilterOverride by remember { mutableStateOf<String?>(null) } // null = follow readerTheme, "NORMAL", "NIGHT", "SEPIA"

    // Upgrade 4: Interactive Search with Next/Previous match navigation
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var currentSearchMatchIndex by remember { mutableIntStateOf(0) }
    var pdfExtractedFullText by remember { mutableStateOf("") }

    // Format specific states
    // PDF
    var pdfTotalPages by remember { mutableStateOf(0) }
    var pdfCurrentPage by remember { mutableStateOf(0) }
    var pdfBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pdfScale by remember { mutableStateOf(1f) }
    var pdfOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var isPdfContinuousSelectionView by remember { mutableStateOf(false) }

    // EPUB
    var epubBook by remember { mutableStateOf<EpubBookContent?>(null) }
    var epubCurrentChapterIndex by remember { mutableStateOf(0) }
    val epubScrollState = rememberScrollState()

    // PowerPoint (PPTX)
    var pptxPresentation by remember { mutableStateOf<PptxPresentationContent?>(null) }
    var pptxCurrentSlideIndex by remember { mutableStateOf(0) }

    // Text (DOCX / TXT / HTML)
    var textDocumentContent by remember { mutableStateOf("") }
    var docxParagraphs by remember { mutableStateOf<List<com.example.util.DocxParagraph>>(emptyList()) }
    val textScrollState = rememberScrollState()

    // Spreadsheet (XLSX / CSV)
    var spreadsheetData by remember { mutableStateOf<SpreadsheetContent?>(null) }

    // DOCMO Integrated States (Access DB, PDF Diagnostics, DOCX Interactive Edit, SAF Save)
    var accessDatabaseInfo by remember { mutableStateOf<AccessDatabaseInfo?>(null) }
    var pdfDiagnosticReport by remember { mutableStateOf<PdfDiagnosticReport?>(null) }
    var showPdfDiagnosticDialog by remember { mutableStateOf(false) }
    var isDocxEditingMode by remember { mutableStateOf(false) }
    var editableDocxParagraphs by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingFileToSave by remember { mutableStateOf<File?>(null) }

    val saveToDeviceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destUri: Uri? ->
        val srcFile = pendingFileToSave
        pendingFileToSave = null
        if (destUri != null && srcFile != null) {
            coroutineScope.launch {
                val res = withContext(Dispatchers.IO) {
                    DocmoDocumentBridge.saveFileToDestinationUri(context, destUri, srcFile)
                }
                if (res.isSuccess) {
                    Toast.makeText(context, "تم حفظ ${srcFile.name} في الموقع المختار بنجاح", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, res.exceptionOrNull()?.message ?: "تعذر حفظ الملف", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    var isLoading by remember { mutableStateOf(true) }

    // Determine current position index & label for bookmarking and highlights
    val currentPositionIndex = when (extension) {
        "pdf" -> pdfCurrentPage
        "epub" -> epubCurrentChapterIndex
        "pptx", "ppt" -> pptxCurrentSlideIndex
        else -> textScrollState.value
    }

    val currentPositionLabel = when (extension) {
        "pdf" -> "صفحة ${pdfCurrentPage + 1}"
        "epub" -> epubBook?.chapters?.getOrNull(epubCurrentChapterIndex)?.title ?: "فصل ${epubCurrentChapterIndex + 1}"
        "pptx", "ppt" -> "شريحة ${pptxCurrentSlideIndex + 1}"
        else -> {
            val pct = if (textScrollState.maxValue > 0) ((textScrollState.value.toFloat() / textScrollState.maxValue) * 100).toInt() else 0
            "موضع $pct%"
        }
    }

    val isCurrentPositionBookmarked = remember(bookmarks, currentPositionIndex) {
        bookmarks.any { it.positionIndex == currentPositionIndex }
    }

    // Active readable text for TTS, Search, and Quotes
    val currentReadableText = remember(
        extension,
        epubBook,
        epubCurrentChapterIndex,
        pptxPresentation,
        pptxCurrentSlideIndex,
        textDocumentContent,
        pdfExtractedFullText
    ) {
        when (extension) {
            "epub" -> epubBook?.chapters?.getOrNull(epubCurrentChapterIndex)?.textContent ?: ""
            "pptx", "ppt" -> pptxPresentation?.slides?.getOrNull(pptxCurrentSlideIndex)?.textContent ?: ""
            "pdf" -> pdfExtractedFullText
            else -> textDocumentContent
        }
    }

    // Auto Outline / Table of Contents
    val documentOutline = remember(
        extension,
        epubBook,
        pptxPresentation,
        docxParagraphs,
        textDocumentContent,
        pdfTotalPages
    ) {
        MultiFormatDocumentEngine.extractDocumentOutline(
            extension = extension,
            epubBook = epubBook,
            pptxPresentation = pptxPresentation,
            docxParagraphs = docxParagraphs,
            textDocumentContent = textDocumentContent,
            pdfTotalPages = pdfTotalPages
        )
    }

    // Search match positions
    val searchMatchOffsets = remember(currentReadableText, searchQuery) {
        if (searchQuery.isBlank() || currentReadableText.isBlank()) {
            emptyList()
        } else {
            val regex = Regex(Regex.escape(searchQuery.trim()), RegexOption.IGNORE_CASE)
            regex.findAll(currentReadableText).map { it.range.first }.toList()
        }
    }

    LaunchedEffect(searchQuery) {
        currentSearchMatchIndex = 0
    }

    // Auto-scroll effect
    LaunchedEffect(isAutoScrollActive, autoScrollSpeed, extension) {
        while (isAutoScrollActive) {
            delay(45L)
            val step = autoScrollSpeed * 2
            when (extension) {
                "epub" -> {
                    if (epubScrollState.value < epubScrollState.maxValue) {
                        epubScrollState.scrollTo((epubScrollState.value + step).coerceAtMost(epubScrollState.maxValue))
                    } else {
                        isAutoScrollActive = false
                    }
                }
                "pdf" -> {
                    // For PDF page view, advance page every few seconds
                    delay(3500L / autoScrollSpeed.coerceAtLeast(1))
                    if (pdfCurrentPage < pdfTotalPages - 1) {
                        pdfCurrentPage++
                    } else {
                        isAutoScrollActive = false
                    }
                }
                else -> {
                    if (textScrollState.value < textScrollState.maxValue) {
                        textScrollState.scrollTo((textScrollState.value + step).coerceAtMost(textScrollState.maxValue))
                    } else {
                        isAutoScrollActive = false
                    }
                }
            }
        }
    }

    // Save progress helper
    fun saveCurrentProgress() {
        val totalPct = when {
            extension == "pdf" && pdfTotalPages > 0 -> ((pdfCurrentPage + 1).toFloat() / pdfTotalPages.toFloat()).coerceIn(0f, 1f)
            extension == "epub" && epubBook != null && epubBook!!.chapters.isNotEmpty() ->
                ((epubCurrentChapterIndex + 1).toFloat() / epubBook!!.chapters.size.toFloat()).coerceIn(0f, 1f)
            extension in listOf("pptx", "ppt") && pptxPresentation != null && pptxPresentation!!.slides.isNotEmpty() ->
                ((pptxCurrentSlideIndex + 1).toFloat() / pptxPresentation!!.slides.size.toFloat()).coerceIn(0f, 1f)
            textDocumentContent.isNotBlank() && textScrollState.maxValue > 0 ->
                (textScrollState.value.toFloat() / textScrollState.maxValue.toFloat()).coerceIn(0f, 1f)
            spreadsheetData != null -> 1f
            else -> 0f
        }

        val lastPageIdx = when (extension) {
            "epub" -> epubCurrentChapterIndex
            "pptx", "ppt" -> pptxCurrentSlideIndex
            else -> pdfCurrentPage
        }

        val entity = DocumentProgressEntity(
            fileUri = uriString,
            fileName = fileName,
            fileType = extension.uppercase(),
            lastPageIndex = lastPageIdx,
            scrollOffset = textScrollState.value,
            lastReadTimestamp = System.currentTimeMillis(),
            totalProgressPercentage = totalPct
        )
        viewModel.saveDocumentProgress(entity)

        if (extension == "epub") {
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                com.example.data.preferences.ReaderPreferences(context).saveLastReadingPosition(
                    bookUri = uriString,
                    chapterIndex = epubCurrentChapterIndex,
                    scrollOffset = 0
                )
            }
        }
    }

    // Auto-save on back or disposal & cleanup TTS
    DisposableEffect(Unit) {
        onDispose {
            ttsManager.shutdown()
            saveCurrentProgress()
        }
    }

    BackHandler {
        if (isImmersiveFullscreen) {
            isImmersiveFullscreen = false
        } else {
            ttsManager.stop()
            saveCurrentProgress()
            onBack()
        }
    }

    // Load Document Content & Initial Progress & Bookmarks & Highlights
    LaunchedEffect(uriString) {
        isLoading = true
        withContext(Dispatchers.IO) {
            bookmarks = MultiFormatDocumentEngine.getDocumentBookmarks(context, uriString)
            highlights = MultiFormatDocumentEngine.getDocumentHighlights(context, uriString)
            val initialSaved = viewModel.getDocumentProgressSync(uriString)
            when (extension) {
                "pdf" -> {
                    pdfTotalPages = MultiFormatDocumentEngine.getPdfPageCount(context, uri)
                    val startPage = initialSaved?.lastPageIndex?.coerceIn(0, (pdfTotalPages - 1).coerceAtLeast(0)) ?: 0
                    pdfCurrentPage = startPage
                    pdfBitmap = MultiFormatDocumentEngine.renderPdfPage(context, uri, startPage)
                    // Extract text in background for TTS / Quotes / Search
                    try {
                        pdfExtractedFullText = DocumentParser.parseUri(context, uri).text
                    } catch (_: Exception) {}
                }
                "epub" -> {
                    val book = MultiFormatDocumentEngine.parseEpub(context, uri)
                    epubBook = book
                    val dataStorePos = com.example.data.preferences.ReaderPreferences(context).getLastReadingPosition(uriString)
                    val startCh = if (dataStorePos.chapterIndex >= 0) {
                        dataStorePos.chapterIndex.coerceIn(0, (book.chapters.size - 1).coerceAtLeast(0))
                    } else {
                        initialSaved?.lastPageIndex?.coerceIn(0, (book.chapters.size - 1).coerceAtLeast(0)) ?: 0
                    }
                    epubCurrentChapterIndex = startCh
                }
                "pptx", "ppt" -> {
                    val pres = MultiFormatDocumentEngine.parsePptx(context, uri)
                    pptxPresentation = pres
                    val startSlide = initialSaved?.lastPageIndex?.coerceIn(0, (pres.slides.size - 1).coerceAtLeast(0)) ?: 0
                    pptxCurrentSlideIndex = startSlide
                }
                "xlsx", "xls", "csv", "tsv" -> {
                    spreadsheetData = MultiFormatDocumentEngine.parseSpreadsheet(context, uri, extension)
                }
                "mdb", "accdb" -> {
                    accessDatabaseInfo = DocmoDocumentBridge.inspectAccessDatabase(context, uri, fileName)
                }
                "docx" -> {
                    val paragraphs = com.example.util.DocxExtractor.extractParagraphs(context, uri)
                    docxParagraphs = paragraphs
                    textDocumentContent = if (paragraphs.isNotEmpty()) {
                        paragraphs.joinToString("\n\n") { it.text }
                    } else {
                        MultiFormatDocumentEngine.parseTextDocument(context, uri, extension)
                    }
                    editableDocxParagraphs = if (paragraphs.isNotEmpty()) {
                        paragraphs.map { it.text }
                    } else {
                        textDocumentContent.split("\n\n").filter { it.isNotBlank() }
                    }
                }
                else -> {
                    // TXT / HTML / RTF / MD
                    textDocumentContent = MultiFormatDocumentEngine.parseTextDocument(context, uri, extension)
                }
            }
        }
        isLoading = false

        savedProgress?.let { prog ->
            if (!initialProgressLoaded && prog.totalProgressPercentage > 0.05f) {
                val pct = (prog.totalProgressPercentage * 100).toInt()
                Toast.makeText(context, "تم استئناف القراءة تلقائياً: $pct%", Toast.LENGTH_SHORT).show()
                initialProgressLoaded = true
            }
        }
    }

    // Restore text scroll position after content loaded
    LaunchedEffect(isLoading, savedProgress) {
        if (!isLoading && savedProgress != null && savedProgress!!.scrollOffset > 0 && textScrollState.maxValue > 0) {
            textScrollState.scrollTo(savedProgress!!.scrollOffset.coerceAtMost(textScrollState.maxValue))
        }
    }

    // PDF Page Change renderer
    LaunchedEffect(pdfCurrentPage) {
        if (extension == "pdf" && pdfTotalPages > 0) {
            withContext(Dispatchers.IO) {
                pdfBitmap = MultiFormatDocumentEngine.renderPdfPage(context, uri, pdfCurrentPage)
            }
            pdfScale = 1f
            pdfOffset = androidx.compose.ui.geometry.Offset.Zero
            saveCurrentProgress()
        }
    }

    // Determine effective PDF Color Mode (Normal, Night Dark Inversion, Sepia Warm Paper)
    val effectivePdfColorMode = remember(pdfColorFilterOverride, readerTheme) {
        pdfColorFilterOverride ?: when (readerTheme) {
            ReaderTheme.DARK -> "NIGHT"
            ReaderTheme.SEPIA -> "SEPIA"
            else -> "NORMAL"
        }
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(visible = !isImmersiveFullscreen) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        color = readerTheme.surface,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                IconButton(
                                    onClick = {
                                        ttsManager.stop()
                                        saveCurrentProgress()
                                        onBack()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "رجوع",
                                        tint = readerTheme.text,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = theme.primaryColor.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = extension.uppercase(),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = theme.primaryColor,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = fileName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = readerTheme.text
                                    )
                                    val progressDisplay = when (extension) {
                                        "pdf" -> if (pdfTotalPages > 0) "صفحة ${pdfCurrentPage + 1} / $pdfTotalPages" else "PDF"
                                        "epub" -> epubBook?.let { "فصل ${epubCurrentChapterIndex + 1} / ${it.chapters.size}" } ?: "EPUB"
                                        "pptx", "ppt" -> pptxPresentation?.let { "شريحة ${pptxCurrentSlideIndex + 1} / ${it.slides.size}" } ?: "PowerPoint"
                                        else -> {
                                            val pct = if (textScrollState.maxValue > 0) {
                                                ((textScrollState.value.toFloat() / textScrollState.maxValue.toFloat()) * 100).toInt()
                                            } else 0
                                            "تقدم: $pct%"
                                        }
                                    }
                                    Text(progressDisplay, fontSize = 8.sp, color = Color.Gray)
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                // 1. Add Highlight / Quote / Send to Editor button
                                IconButton(
                                    onClick = {
                                        val sampleSnippet = when (extension) {
                                            "pdf" -> {
                                                val lines = pdfExtractedFullText.lines().filter { it.isNotBlank() }
                                                lines.drop((pdfCurrentPage * 4).coerceAtMost((lines.size - 1).coerceAtLeast(0)))
                                                    .take(4)
                                                    .joinToString(" ")
                                                    .ifBlank { "اقتباس من $fileName (صفحة ${pdfCurrentPage + 1})" }
                                            }
                                            "epub" -> epubBook?.chapters?.getOrNull(epubCurrentChapterIndex)?.textContent?.take(220) ?: ""
                                            "pptx", "ppt" -> pptxPresentation?.slides?.getOrNull(pptxCurrentSlideIndex)?.textContent?.take(220) ?: ""
                                            else -> textDocumentContent.take(220)
                                        }
                                        activeQuoteDraft = sampleSnippet.ifBlank { "اقتباس مرجعي من $fileName" }
                                        activeQuoteNote = ""
                                    },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        Icons.Default.FormatQuote,
                                        contentDescription = "تظليل واقتباس للمحرر",
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                // 2. Smart Voice Reader (TTS)
                                IconButton(
                                    onClick = {
                                        if (showTtsBar && ttsPlaybackState == TtsPlaybackState.PLAYING) {
                                            ttsManager.pause()
                                        } else {
                                            val textToRead = currentReadableText.ifBlank {
                                                "مستند $fileName، $currentPositionLabel"
                                            }
                                            ttsManager.prepareText(textToRead)
                                            ttsManager.play()
                                            showTtsBar = true
                                        }
                                    },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        Icons.Default.RecordVoiceOver,
                                        contentDescription = "القارئ الصوتي الذكي",
                                        tint = if (showTtsBar) Color(0xFF16A34A) else readerTheme.text,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                // 3. Auto-Scroll toggle
                                IconButton(
                                    onClick = {
                                        if (!isAutoScrollActive) {
                                            isAutoScrollActive = true
                                            autoScrollSpeed = 1
                                            Toast.makeText(context, "تم تفعيل التمرير التلقائي (السرعة 1x) ✨", Toast.LENGTH_SHORT).show()
                                        } else if (autoScrollSpeed < 3) {
                                            autoScrollSpeed++
                                            Toast.makeText(context, "سرعة التمرير التلقائي: ${autoScrollSpeed}x", Toast.LENGTH_SHORT).show()
                                        } else {
                                            isAutoScrollActive = false
                                            Toast.makeText(context, "تم إيقاف التمرير التلقائي", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        Icons.Default.SwipeVertical,
                                        contentDescription = "التمرير التلقائي",
                                        tint = if (isAutoScrollActive) theme.primaryColor else readerTheme.text,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                // 4. Bookmark toggle button
                                IconButton(
                                    onClick = {
                                        val currentIdx = currentPositionIndex
                                        val updated = if (isCurrentPositionBookmarked) {
                                            bookmarks.filter { it.positionIndex != currentIdx }
                                        } else {
                                            bookmarks + DocumentBookmarkItem(
                                                uriString = uriString,
                                                label = currentPositionLabel,
                                                positionIndex = currentIdx
                                            )
                                        }
                                        bookmarks = updated
                                        MultiFormatDocumentEngine.saveDocumentBookmarks(context, uriString, updated)
                                        Toast.makeText(
                                            context,
                                            if (isCurrentPositionBookmarked) "تمت إزالة العلامة المرجعية" else "تمت إضافة علامة مرجعية 🔖",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isCurrentPositionBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = "علامة مرجعية",
                                        tint = if (isCurrentPositionBookmarked) Color(0xFFF59E0B) else readerTheme.text,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                // 5. Notebook (Highlights + Notes + Bookmarks) Sheet
                                IconButton(
                                    onClick = { showBookmarksSheet = true },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Bookmarks,
                                        contentDescription = "دفتر الملاحظات والاقتباسات",
                                        tint = if (highlights.isNotEmpty() || bookmarks.isNotEmpty()) theme.primaryColor else readerTheme.text,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                // 6. Search in Document
                                IconButton(
                                    onClick = { isSearchActive = !isSearchActive },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = "بحث في المستند",
                                        tint = if (isSearchActive) theme.primaryColor else readerTheme.text,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                // 7. Auto Outline / Table of Contents (TOC)
                                if (documentOutline.isNotEmpty()) {
                                    IconButton(
                                        onClick = { showOutlineSheet = true },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Toc,
                                            contentDescription = "فهرس المحتويات والعناوين",
                                            tint = readerTheme.text,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }

                                // 8. PDF Specific Actions (Selection mode / Page Range Extract)
                                if (extension == "pdf") {
                                    IconButton(
                                        onClick = { isPdfContinuousSelectionView = !isPdfContinuousSelectionView },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isPdfContinuousSelectionView) Icons.Default.ViewAgenda else Icons.Default.FormatPaint,
                                            contentDescription = "تبديل وضع التحديد والتظليل",
                                            tint = if (isPdfContinuousSelectionView) theme.primaryColor else readerTheme.text,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { showExtractPdfRangeDialog = true },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCut,
                                            contentDescription = "استخراج صفحات محددة كملف PDF",
                                            tint = Color(0xFF0284C7),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                // 9. Converter Studio
                                IconButton(
                                    onClick = { showConverterStudioDialog = true },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.Default.Transform, contentDescription = "تحويل وأمان المستند", tint = theme.primaryColor, modifier = Modifier.size(14.dp))
                                }

                                // 10. Immersive Fullscreen Toggle
                                IconButton(
                                    onClick = { isImmersiveFullscreen = true },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.Default.Fullscreen, contentDescription = "وضع ملء الشاشة الغامر", tint = readerTheme.text, modifier = Modifier.size(15.dp))
                                }

                                // 11. Format Settings
                                IconButton(
                                    onClick = { showSettingsSheet = true },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.Default.Tune, contentDescription = "إعدادات القراءة", tint = readerTheme.text, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    // Interactive Search Bar with Next/Previous Match & Counter
                    AnimatedVisibility(visible = isSearchActive) {
                        Surface(
                            color = readerTheme.surface,
                            shadowElevation = 3.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = readerTheme.bg,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, theme.primaryColor.copy(alpha = 0.35f)),
                                    modifier = Modifier.weight(1f).height(30.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Search, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size(14.dp))
                                        androidx.compose.foundation.text.BasicTextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            singleLine = true,
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = readerTheme.text),
                                            modifier = Modifier.weight(1f),
                                            decorationBox = { inner ->
                                                if (searchQuery.isEmpty()) {
                                                    Text("ابحث عن كلمة أو عبارة داخل المستند...", fontSize = 10.sp, color = Color.Gray)
                                                }
                                                inner()
                                            }
                                        )
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                                Icon(Icons.Default.Clear, contentDescription = "مسح", tint = Color.Gray, modifier = Modifier.size(12.dp))
                                            }
                                        }
                                    }
                                }

                                // Match counter badge
                                val totalMatches = searchMatchOffsets.size
                                val displayIdx = if (totalMatches > 0) (currentSearchMatchIndex % totalMatches) + 1 else 0
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (totalMatches > 0) theme.primaryColor.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "$displayIdx / $totalMatches",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (totalMatches > 0) theme.primaryColor else Color.Gray,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }

                                // Previous Match
                                IconButton(
                                    onClick = {
                                        if (totalMatches > 0) {
                                            currentSearchMatchIndex = (currentSearchMatchIndex - 1 + totalMatches) % totalMatches
                                            val charPos = searchMatchOffsets[currentSearchMatchIndex]
                                            val ratio = charPos.toFloat() / currentReadableText.length.coerceAtLeast(1)
                                            coroutineScope.launch {
                                                if (extension == "epub") {
                                                    epubScrollState.animateScrollTo((ratio * epubScrollState.maxValue).toInt())
                                                } else if (extension == "pdf" && pdfTotalPages > 0) {
                                                    pdfCurrentPage = (ratio * pdfTotalPages).toInt().coerceIn(0, pdfTotalPages - 1)
                                                } else {
                                                    textScrollState.animateScrollTo((ratio * textScrollState.maxValue).toInt())
                                                }
                                            }
                                        }
                                    },
                                    enabled = totalMatches > 0,
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "النتيجة السابقة", tint = readerTheme.text, modifier = Modifier.size(16.dp))
                                }

                                // Next Match
                                IconButton(
                                    onClick = {
                                        if (totalMatches > 0) {
                                            currentSearchMatchIndex = (currentSearchMatchIndex + 1) % totalMatches
                                            val charPos = searchMatchOffsets[currentSearchMatchIndex]
                                            val ratio = charPos.toFloat() / currentReadableText.length.coerceAtLeast(1)
                                            coroutineScope.launch {
                                                if (extension == "epub") {
                                                    epubScrollState.animateScrollTo((ratio * epubScrollState.maxValue).toInt())
                                                } else if (extension == "pdf" && pdfTotalPages > 0) {
                                                    pdfCurrentPage = (ratio * pdfTotalPages).toInt().coerceIn(0, pdfTotalPages - 1)
                                                } else {
                                                    textScrollState.animateScrollTo((ratio * textScrollState.maxValue).toInt())
                                                }
                                            }
                                        }
                                    },
                                    enabled = totalMatches > 0,
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "النتيجة التالية", tint = readerTheme.text, modifier = Modifier.size(16.dp))
                                }

                                IconButton(
                                    onClick = {
                                        isSearchActive = false
                                        searchQuery = ""
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "إغلاق البحث", tint = Color.Gray, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(visible = !isImmersiveFullscreen) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Floating TTS Player Bar when active
                    if (showTtsBar) {
                        TtsPlayerBar(
                            playbackState = ttsPlaybackState,
                            currentSentence = ttsCurrentSentence,
                            currentSentenceIndex = ttsCurrentSentenceIndex,
                            currentSpeed = ttsSpeechRate,
                            theme = theme,
                            onPlay = { ttsManager.play() },
                            onPause = { ttsManager.pause() },
                            onSeekForward = { ttsManager.seekForward() },
                            onSeekBackward = { ttsManager.seekBackward() },
                            onSpeedChange = { ttsManager.setSpeed(it) },
                            onClose = {
                                ttsManager.stop()
                                showTtsBar = false
                            }
                        )
                    }

                    // PDF Page Thumbnails Strip & Fast Seekbar
                    if (extension == "pdf" && pdfTotalPages > 1 && showPdfThumbnailsStrip && !isPdfContinuousSelectionView) {
                        Surface(
                            color = readerTheme.surface,
                            tonalElevation = 6.dp,
                            shadowElevation = 4.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "شريط مصغرات الصفحات والتنقل السريع (${pdfCurrentPage + 1} / $pdfTotalPages)",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = theme.primaryColor
                                    )
                                    Text(
                                        text = "إخفاء ▼",
                                        fontSize = 9.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.clickable { showPdfThumbnailsStrip = false }
                                    )
                                }

                                // Fast Page Seekbar Slider
                                Slider(
                                    value = pdfCurrentPage.toFloat(),
                                    onValueChange = { newVal ->
                                        pdfCurrentPage = newVal.toInt().coerceIn(0, pdfTotalPages - 1)
                                    },
                                    valueRange = 0f..(pdfTotalPages - 1).coerceAtLeast(1).toFloat(),
                                    modifier = Modifier.fillMaxWidth().height(22.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = theme.primaryColor,
                                        activeTrackColor = theme.primaryColor
                                    )
                                )

                                // Horizontal Page Thumbnail Cards
                                val pagesList = remember(pdfTotalPages) { (0 until pdfTotalPages).toList() }
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(pagesList, key = { it }) { pageIdx ->
                                        val isCurrent = pageIdx == pdfCurrentPage
                                        PdfPageThumbnailCard(
                                            uri = uri,
                                            pageIndex = pageIdx,
                                            isSelected = isCurrent,
                                            primaryColor = theme.primaryColor,
                                            onClick = {
                                                pdfCurrentPage = pageIdx
                                                saveCurrentProgress()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Compact Bottom Controller Bar
                    if (extension == "pdf" && pdfTotalPages > 0 && !isPdfContinuousSelectionView) {
                        Surface(
                            color = readerTheme.surface,
                            tonalElevation = 4.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        if (pdfCurrentPage > 0) {
                                            pdfCurrentPage--
                                            saveCurrentProgress()
                                        }
                                    },
                                    enabled = pdfCurrentPage > 0,
                                    colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(26.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "الصفحة السابقة", modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("السابقة", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Clickable Page Badge that toggles Thumbnails & Slider Strip
                                    Surface(
                                        onClick = { showPdfThumbnailsStrip = !showPdfThumbnailsStrip },
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (showPdfThumbnailsStrip) theme.primaryColor else theme.primaryColor.copy(alpha = 0.12f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.GridOn,
                                                contentDescription = "مصغرات الصفحات",
                                                tint = if (showPdfThumbnailsStrip) Color.White else theme.primaryColor,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = "صفحة ${pdfCurrentPage + 1} / $pdfTotalPages",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (showPdfThumbnailsStrip) Color.White else theme.primaryColor
                                            )
                                        }
                                    }

                                    // Smart PDF Night / Sepia Mode Toggle Chip
                                    Surface(
                                        onClick = {
                                            pdfColorFilterOverride = when (effectivePdfColorMode) {
                                                "NORMAL" -> "NIGHT"
                                                "NIGHT" -> "SEPIA"
                                                else -> "NORMAL"
                                            }
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        color = when (effectivePdfColorMode) {
                                            "NIGHT" -> Color(0xFF1E293B)
                                            "SEPIA" -> Color(0xFFFEF3C7)
                                            else -> Color(0xFFF1F5F9)
                                        },
                                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.3f))
                                    ) {
                                        val modeLabel = when (effectivePdfColorMode) {
                                            "NIGHT" -> "🌙 ليلي"
                                            "SEPIA" -> "📜 دافئ"
                                            else -> "☀️ عادي"
                                        }
                                        val modeTextColor = when (effectivePdfColorMode) {
                                            "NIGHT" -> Color.White
                                            "SEPIA" -> Color(0xFF92400E)
                                            else -> Color(0xFF334155)
                                        }
                                        Text(
                                            text = modeLabel,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = modeTextColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        if (pdfCurrentPage < pdfTotalPages - 1) {
                                            pdfCurrentPage++
                                            saveCurrentProgress()
                                        }
                                    },
                                    enabled = pdfCurrentPage < pdfTotalPages - 1,
                                    colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(26.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("التالية", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "الصفحة التالية", modifier = Modifier.size(13.dp))
                                }
                            }
                        }
                    } else if (extension == "epub" && epubBook != null && epubBook!!.chapters.isNotEmpty()) {
                        Surface(
                            color = readerTheme.surface,
                            tonalElevation = 4.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        if (epubCurrentChapterIndex > 0) {
                                            epubCurrentChapterIndex--
                                            saveCurrentProgress()
                                        }
                                    },
                                    enabled = epubCurrentChapterIndex > 0,
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor),
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("السابق", fontSize = 10.sp)
                                }

                                Surface(
                                    onClick = { showOutlineSheet = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = theme.primaryColor.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "فصل ${epubCurrentChapterIndex + 1} / ${epubBook!!.chapters.size}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = theme.primaryColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (epubCurrentChapterIndex < epubBook!!.chapters.size - 1) {
                                            epubCurrentChapterIndex++
                                            saveCurrentProgress()
                                        }
                                    },
                                    enabled = epubCurrentChapterIndex < epubBook!!.chapters.size - 1,
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor),
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("التالي", fontSize = 10.sp)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    } else if (extension in listOf("pptx", "ppt") && pptxPresentation != null && pptxPresentation!!.slides.isNotEmpty()) {
                        Surface(
                            color = readerTheme.surface,
                            tonalElevation = 4.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        if (pptxCurrentSlideIndex > 0) {
                                            pptxCurrentSlideIndex--
                                            saveCurrentProgress()
                                        }
                                    },
                                    enabled = pptxCurrentSlideIndex > 0,
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("السابقة", fontSize = 10.sp)
                                }

                                Surface(
                                    onClick = { showOutlineSheet = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFD97706).copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "شريحة ${pptxCurrentSlideIndex + 1} / ${pptxPresentation!!.slides.size}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = Color(0xFFD97706),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (pptxCurrentSlideIndex < pptxPresentation!!.slides.size - 1) {
                                            pptxCurrentSlideIndex++
                                            saveCurrentProgress()
                                        }
                                    },
                                    enabled = pptxCurrentSlideIndex < pptxPresentation!!.slides.size - 1,
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("التالية", fontSize = 10.sp)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    } else if (extension !in listOf("pdf", "xlsx", "xls", "csv")) {
                        ReaderSettingsBar(
                            settingsState = readerSettings,
                            onSettingsChanged = { readerSettings = it },
                            onOpenFullSettings = { showSettingsSheet = true }
                        )
                    }
                }
            }
        },
        containerColor = readerTheme.bg
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(color = theme.primaryColor)
                        Text("جارٍ تحميل ومعالجة المستند...", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            } else {
                when (extension) {
                    "pdf" -> {
                        if (isPdfContinuousSelectionView) {
                            com.example.ui.screens.PdfReaderWithTextSelectionScreen(
                                pdfUri = uri,
                                fileName = fileName,
                                initialPageIndex = pdfCurrentPage,
                                onBack = { isPdfContinuousSelectionView = false },
                                onCopyText = { selectedText ->
                                    activeQuoteDraft = selectedText
                                    activeQuoteNote = ""
                                },
                                onSaveProgress = { pageIndex, _ ->
                                    pdfCurrentPage = pageIndex
                                    saveCurrentProgress()
                                }
                            )
                        } else {
                            PdfPageView(
                                bitmap = pdfBitmap,
                                scale = pdfScale,
                                offset = pdfOffset,
                                colorFilterMode = effectivePdfColorMode,
                                onTransform = { s, o ->
                                    pdfScale = (pdfScale * s).coerceIn(1f, 4f)
                                    pdfOffset += o
                                },
                                onSingleTap = {
                                    isImmersiveFullscreen = !isImmersiveFullscreen
                                },
                                onSwipeNext = {
                                    if (pdfCurrentPage < pdfTotalPages - 1) {
                                        pdfCurrentPage++
                                        saveCurrentProgress()
                                    }
                                },
                                onSwipePrevious = {
                                    if (pdfCurrentPage > 0) {
                                        pdfCurrentPage--
                                        saveCurrentProgress()
                                    }
                                }
                            )
                        }
                    }
                    "epub" -> {
                        val currentChapter = epubBook?.chapters?.getOrNull(epubCurrentChapterIndex)
                        if (currentChapter != null) {
                            val paragraphs = remember(currentChapter.textContent) {
                                currentChapter.textContent.split("\n\n").filter { it.isNotBlank() }
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(epubScrollState)
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = currentChapter.title,
                                        fontSize = (fontSizeSp + 5).sp,
                                        fontWeight = FontWeight.Bold,
                                        color = readerTheme.text,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "انقر على أي فقرة للتظليل أو الإرسال للمحرر ✍️",
                                        fontSize = 9.sp,
                                        color = theme.primaryColor
                                    )
                                }
                                HorizontalDivider(color = readerTheme.text.copy(alpha = 0.1f))

                                paragraphs.forEach { paraText ->
                                    val matchingHighlight = highlights.firstOrNull {
                                        it.snippetText.trim() == paraText.trim()
                                    }
                                    InteractiveParagraphView(
                                        text = paraText,
                                        searchQuery = searchQuery,
                                        highlightItem = matchingHighlight,
                                        fontSizeSp = fontSizeSp,
                                        lineSpacing = lineSpacing,
                                        fontWeight = fontWeight,
                                        textColor = readerTheme.text,
                                        onClick = {
                                            activeQuoteDraft = paraText
                                            activeQuoteNote = matchingHighlight?.note ?: ""
                                            activeQuoteColorHex = matchingHighlight?.colorHex ?: "#FDE047"
                                        }
                                    )
                                }
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("لا توجد فصول متوفرة في الكتاب", color = Color.Gray)
                            }
                        }
                    }
                    "pptx", "ppt" -> {
                        val pres = pptxPresentation
                        val slide = pres?.slides?.getOrNull(pptxCurrentSlideIndex)
                        if (slide != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            activeQuoteDraft = "${slide.title}\n${slide.textContent}"
                                            activeQuoteNote = ""
                                        },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = readerTheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                color = Color(0xFFD97706),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "شريحة ${slide.slideNumber}",
                                                    fontSize = 11.sp,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }

                                            Text(
                                                text = "انقر على الشريحة لاقتباسها للمحرر ✍️",
                                                fontSize = 9.sp,
                                                color = theme.primaryColor
                                            )
                                        }

                                        Text(
                                            text = slide.title,
                                            fontSize = (fontSizeSp + 4).sp,
                                            fontWeight = FontWeight.Bold,
                                            color = readerTheme.text
                                        )

                                        HorizontalDivider(color = readerTheme.text.copy(alpha = 0.1f))

                                        Text(
                                            text = buildHighlightedAnnotatedString(slide.textContent, searchQuery),
                                            fontSize = fontSizeSp.sp,
                                            lineHeight = (fontSizeSp * lineSpacing).sp,
                                            fontWeight = fontWeight,
                                            color = readerTheme.text
                                        )
                                    }
                                }
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("لا توجد شرائح متوفرة في العرض التقديمي", color = Color.Gray)
                            }
                        }
                    }
                    "xlsx", "xls", "csv", "tsv" -> {
                        val data = spreadsheetData
                        if (data != null && data.headers.isNotEmpty()) {
                            SpreadsheetGridView(
                                data = data,
                                extension = extension,
                                fileName = fileName,
                                searchQuery = searchQuery,
                                theme = theme,
                                readerTheme = readerTheme,
                                onUpdateCell = { rowIdx, colIdx, newValue ->
                                    val updatedRows = data.rows.mapIndexed { rIdx, row ->
                                        if (rIdx == rowIdx) {
                                            val padded = (0 until maxOf(row.size, colIdx + 1)).map { cIdx ->
                                                if (cIdx == colIdx) newValue else row.getOrElse(cIdx) { "" }
                                            }
                                            padded
                                        } else row
                                    }
                                    spreadsheetData = data.copy(rows = updatedRows)
                                },
                                onExportXlsx = { isCsvConversion ->
                                    coroutineScope.launch {
                                        val suffix = if (isCsvConversion) "" else "-edited"
                                        val outFile = withContext(Dispatchers.IO) {
                                            DocmoDocumentBridge.exportRowsToXlsx(
                                                context = context,
                                                baseName = fileName,
                                                headers = data.headers,
                                                rows = data.rows,
                                                suffix = suffix
                                            )
                                        }
                                        pendingFileToSave = outFile
                                        saveToDeviceLauncher.launch(outFile.name)
                                    }
                                }
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("جدول البيانات فارغ أو تعذر تحليله.", color = Color.Gray)
                            }
                        }
                    }
                    "mdb", "accdb" -> {
                        val info = accessDatabaseInfo
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = readerTheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "معلومات قاعدة بيانات Microsoft Access",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = readerTheme.text
                                    )
                                    Text("الصيغة: ${info?.format ?: extension.uppercase()}", fontSize = 13.sp, color = readerTheme.text)
                                    Text("الحجم: ${String.format(java.util.Locale.US, "%.1f", (info?.sizeBytes ?: 0L) / 1024f)} كيلوبايت", fontSize = 13.sp, color = readerTheme.text)
                                    Text("ترويسة الملف: ${info?.signature ?: "غير معروف"}", fontSize = 13.sp, color = theme.primaryColor, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "فحص الترويسة: ${if (info?.recognized == true) "تطابق مبدئي" else "لم يتم تأكيد الصيغة"}",
                                        fontSize = 13.sp,
                                        color = if (info?.recognized == true) Color(0xFF059669) else Color(0xFFD97706),
                                        fontWeight = FontWeight.Bold
                                    )

                                    Surface(
                                        color = Color(0xFFFEF3C7),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "تفحص هذه الأداة ترويسة ملف قاعدة البيانات محليًا على الجهاز دون تعديل الملف الأصلي. يمكنك حفظ نسخة احتياطية مطابقة من ملف Access.",
                                            fontSize = 12.sp,
                                            color = Color(0xFF92400E),
                                            modifier = Modifier.padding(14.dp)
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                val copyFile = withContext(Dispatchers.IO) {
                                                    DocmoDocumentBridge.createAccessBackupCopy(context, uri, fileName)
                                                }
                                                pendingFileToSave = copyFile
                                                saveToDeviceLauncher.launch(copyFile.name)
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                                    ) {
                                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("حفظ نسخة من ملف Access", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        // DOCX / TXT / HTML / RTF Text View with Interactive Paragraphs
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(textScrollState)
                                .padding(horizontal = 18.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (extension == "docx") {
                                    FilterChip(
                                        selected = isDocxEditingMode,
                                        onClick = { isDocxEditingMode = !isDocxEditingMode },
                                        label = {
                                            Text(
                                                if (isDocxEditingMode) "وضع تعديل فقرات Word مفعّل ✏️" else "تعديل فقرات Word",
                                                fontSize = 10.sp
                                            )
                                        }
                                    )
                                } else {
                                    Text(
                                        text = "💡 انقر على أي فقرة للتظليل الملون أو إرسالها كاقتباس لشاشة الكتابة",
                                        fontSize = 9.sp,
                                        color = theme.primaryColor,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                if (extension == "docx" && isDocxEditingMode) {
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                val outFile = withContext(Dispatchers.IO) {
                                                    DocmoDocumentBridge.exportEditedDocxParagraphs(
                                                        context = context,
                                                        baseName = fileName,
                                                        paragraphs = editableDocxParagraphs
                                                    )
                                                }
                                                textDocumentContent = editableDocxParagraphs.joinToString("\n\n")
                                                pendingFileToSave = outFile
                                                saveToDeviceLauncher.launch(outFile.name)
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("حفظ نسخة Word", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            if (extension == "docx" && isDocxEditingMode) {
                                editableDocxParagraphs.forEachIndexed { idx, paraText ->
                                    OutlinedTextField(
                                        value = paraText,
                                        onValueChange = { updated ->
                                            editableDocxParagraphs = editableDocxParagraphs.toMutableList().also {
                                                it[idx] = updated
                                            }
                                        },
                                        label = { Text("الفقرة ${idx + 1}", fontSize = 10.sp) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            } else if (docxParagraphs.isNotEmpty()) {
                                docxParagraphs.forEach { para ->
                                    val matchingHighlight = highlights.firstOrNull {
                                        it.snippetText.trim() == para.text.trim()
                                    }
                                    if (para.isHeading) {
                                        Text(
                                            text = buildHighlightedAnnotatedString(para.text, searchQuery),
                                            fontSize = (fontSizeSp + 4).sp,
                                            fontWeight = FontWeight.Bold,
                                            color = theme.primaryColor,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    activeQuoteDraft = para.text
                                                    activeQuoteNote = matchingHighlight?.note ?: ""
                                                }
                                                .padding(vertical = 6.dp)
                                        )
                                    } else {
                                        InteractiveParagraphView(
                                            text = para.text,
                                            searchQuery = searchQuery,
                                            highlightItem = matchingHighlight,
                                            fontSizeSp = fontSizeSp,
                                            lineSpacing = lineSpacing,
                                            fontWeight = fontWeight,
                                            textColor = readerTheme.text,
                                            onClick = {
                                                activeQuoteDraft = para.text
                                                activeQuoteNote = matchingHighlight?.note ?: ""
                                                activeQuoteColorHex = matchingHighlight?.colorHex ?: "#FDE047"
                                            }
                                        )
                                    }
                                }
                            } else {
                                val blocks = remember(textDocumentContent) {
                                    textDocumentContent.split("\n\n").filter { it.isNotBlank() }
                                }
                                blocks.forEach { blockText ->
                                    val matchingHighlight = highlights.firstOrNull {
                                        it.snippetText.trim() == blockText.trim()
                                    }
                                    InteractiveParagraphView(
                                        text = blockText,
                                        searchQuery = searchQuery,
                                        highlightItem = matchingHighlight,
                                        fontSizeSp = fontSizeSp,
                                        lineSpacing = lineSpacing,
                                        fontWeight = fontWeight,
                                        textColor = readerTheme.text,
                                        onClick = {
                                            activeQuoteDraft = blockText
                                            activeQuoteNote = matchingHighlight?.note ?: ""
                                            activeQuoteColorHex = matchingHighlight?.colorHex ?: "#FDE047"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Floating Exit Fullscreen Pill when Immersive Fullscreen is active
            if (isImmersiveFullscreen) {
                Surface(
                    onClick = { isImmersiveFullscreen = false },
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.FullscreenExit, contentDescription = "إنهاء ملء الشاشة", tint = Color.White, modifier = Modifier.size(14.dp))
                        Text(currentPositionLabel, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Upgrade 1 Dialog: Interactive Quote, Highlight Color, Margin Note & Send to Editor
    if (activeQuoteDraft != null) {
        val colorOptions = listOf(
            "#FDE047" to "أصفر",
            "#86EFAC" to "أخضر",
            "#93C5FD" to "أزرق",
            "#F9A8D4" to "وردي",
            "#FDBA74" to "برتقالي"
        )
        AlertDialog(
            onDismissRequest = { activeQuoteDraft = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.FormatQuote, contentDescription = null, tint = theme.primaryColor)
                    Text("تظليل واقتباس وإرسال للمحرر ✍️", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = activeQuoteDraft ?: "",
                        onValueChange = { activeQuoteDraft = it },
                        label = { Text("النص المقتبس أو المظلل", fontSize = 11.sp) },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Text("لون التظليل:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        colorOptions.forEach { (hex, label) ->
                            val parsedColor = parseHexColorSafe(hex)
                            val isSelected = activeQuoteColorHex == hex
                            Surface(
                                onClick = { activeQuoteColorHex = hex },
                                shape = RoundedCornerShape(8.dp),
                                color = parsedColor,
                                border = androidx.compose.foundation.BorderStroke(
                                    if (isSelected) 2.dp else 0.5.dp,
                                    if (isSelected) Color.Black else Color.Gray
                                ),
                                modifier = Modifier.weight(1f).height(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(label, fontSize = 9.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = Color.Black)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = activeQuoteNote,
                        onValueChange = { activeQuoteNote = it },
                        label = { Text("ملاحظة هامشية أو تعليق شخصي (اختياري)", fontSize = 11.sp) },
                        placeholder = { Text("اكتب فكرتك أو تعليقك حول هذه الفقرة...", fontSize = 11.sp) },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Direct Send to Editor Button
                    Button(
                        onClick = {
                            val quoteText = activeQuoteDraft ?: ""
                            if (quoteText.isNotBlank()) {
                                viewModel.insertQuoteIntoActiveChapter(
                                    quoteText = quoteText,
                                    sourceFileName = fileName,
                                    positionLabel = currentPositionLabel,
                                    note = activeQuoteNote
                                )
                                Toast.makeText(context, "تم إرسال الاقتباس مباشرة إلى الفصل الحالي في شاشة الكتابة ✍️", Toast.LENGTH_LONG).show()
                                activeQuoteDraft = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إرسال مباشر إلى شاشة الكتابة (المحرر) ✍️", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val snippet = activeQuoteDraft?.trim() ?: ""
                        if (snippet.isNotBlank()) {
                            val newHl = DocumentHighlightItem(
                                uriString = uriString,
                                snippetText = snippet,
                                note = activeQuoteNote.trim(),
                                colorHex = activeQuoteColorHex,
                                positionIndex = currentPositionIndex,
                                positionLabel = currentPositionLabel
                            )
                            val updated = highlights.filter { it.snippetText.trim() != snippet } + newHl
                            highlights = updated
                            MultiFormatDocumentEngine.saveDocumentHighlights(context, uriString, updated)
                            Toast.makeText(context, "تم حفظ التظليل والملاحظة 🖍️", Toast.LENGTH_SHORT).show()
                        }
                        activeQuoteDraft = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor)
                ) {
                    Text("حفظ التظليل 🖍️", fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { activeQuoteDraft = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Settings Bottom Sheet
    if (showSettingsSheet) {
        ReaderSettingsBottomSheet(
            settingsState = readerSettings,
            onSettingsChanged = { readerSettings = it },
            onDismissRequest = { showSettingsSheet = false }
        )
    }

    // Upgraded Notebook Sheet: Highlights, Margin Notes, Bookmarks & Export Summary
    if (showBookmarksSheet) {
        var selectedTab by remember { mutableIntStateOf(0) } // 0 = Highlights & Quotes, 1 = Bookmarks
        ModalBottomSheet(
            onDismissRequest = { showBookmarksSheet = false },
            containerColor = readerTheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("دفتر التظليلات والملاحظات والمرجعيات 📑", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = readerTheme.text)
                    Text("${highlights.size + bookmarks.size} عنصر", fontSize = 11.sp, color = Color.Gray)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        label = { Text("🖍️ التظليلات والاقتباسات (${highlights.size})", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        label = { Text("🔖 العلامات المرجعية (${bookmarks.size})", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(color = readerTheme.text.copy(alpha = 0.1f))

                if (selectedTab == 0) {
                    if (highlights.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("لا توجد تظليلات أو اقتباسات محفوظة بعد. انقر على أي فقرة أو زر ❝ في الأعلى لإضافة اقتباس.", fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(highlights, key = { it.id }) { hl ->
                                val bgColor = parseHexColorSafe(hl.colorHex).copy(alpha = 0.35f)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = bgColor,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, parseHexColorSafe(hl.colorHex)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            when (extension) {
                                                "pdf" -> pdfCurrentPage = hl.positionIndex
                                                "epub" -> epubCurrentChapterIndex = hl.positionIndex
                                                "pptx", "ppt" -> pptxCurrentSlideIndex = hl.positionIndex
                                                else -> coroutineScope.launch { textScrollState.scrollTo(hl.positionIndex) }
                                            }
                                            showBookmarksSheet = false
                                        }
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
                                            Text(hl.positionLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.primaryColor)
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                // Send single highlight to editor
                                                IconButton(
                                                    onClick = {
                                                        viewModel.insertQuoteIntoActiveChapter(
                                                            quoteText = hl.snippetText,
                                                            sourceFileName = fileName,
                                                            positionLabel = hl.positionLabel,
                                                            note = hl.note
                                                        )
                                                        Toast.makeText(context, "تم إرسال الاقتباس لشاشة الكتابة ✍️", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(Icons.Default.Send, contentDescription = "إرسال للمحرر", tint = Color(0xFF059669), modifier = Modifier.size(15.dp))
                                                }
                                                IconButton(
                                                    onClick = {
                                                        val updated = highlights.filter { it.id != hl.id }
                                                        highlights = updated
                                                        MultiFormatDocumentEngine.saveDocumentHighlights(context, uriString, updated)
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Gray, modifier = Modifier.size(15.dp))
                                                }
                                            }
                                        }
                                        Text("«${hl.snippetText}»", fontSize = 12.sp, color = readerTheme.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                        if (hl.note.isNotBlank()) {
                                            Text("💡 ملاحظة: ${hl.note}", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF92400E))
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (bookmarks.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("لا توجد علامات مرجعية محفوظة بعد.", fontSize = 12.sp, color = Color.Gray)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                            items(bookmarks, key = { it.id }) { bm ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            when (extension) {
                                                "pdf" -> pdfCurrentPage = bm.positionIndex
                                                "epub" -> epubCurrentChapterIndex = bm.positionIndex
                                                "pptx", "ppt" -> pptxCurrentSlideIndex = bm.positionIndex
                                                else -> coroutineScope.launch { textScrollState.scrollTo(bm.positionIndex) }
                                            }
                                            showBookmarksSheet = false
                                        }
                                        .padding(vertical = 8.dp, horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.Bookmark, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                                        Text(bm.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = readerTheme.text)
                                    }
                                    IconButton(onClick = {
                                        val updated = bookmarks.filter { it.id != bm.id }
                                        bookmarks = updated
                                        MultiFormatDocumentEngine.saveDocumentBookmarks(context, uriString, updated)
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Export / Insert All Annotations Buttons
                if (highlights.isNotEmpty() || bookmarks.isNotEmpty()) {
                    HorizontalDivider(color = readerTheme.text.copy(alpha = 0.1f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val summary = MultiFormatDocumentEngine.formatAnnotationsSummary(
                                    fileName = fileName,
                                    bookmarks = bookmarks,
                                    highlights = highlights
                                )
                                viewModel.insertAnnotationsAsNewChapter(
                                    chapterTitle = "ملاحظات واقتباسات: $fileName",
                                    summaryText = summary
                                )
                                showBookmarksSheet = false
                                Toast.makeText(context, "تم إدراج جميع الملاحظات والاقتباسات كفصل جديد في مشروعك 📚", Toast.LENGTH_LONG).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.LibraryAdd, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إدراج كفصل بالمشروع 📚", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val summary = MultiFormatDocumentEngine.formatAnnotationsSummary(
                                    fileName = fileName,
                                    bookmarks = bookmarks,
                                    highlights = highlights
                                )
                                val outDir = File(context.cacheDir, "conversions").apply { mkdirs() }
                                val outFile = File(outDir, "ملخص_ملاحظات_${fileName.substringBeforeLast(".")}.txt")
                                outFile.writeText(summary, Charsets.UTF_8)
                                pendingFileToSave = outFile
                                showBookmarksSheet = false
                                saveToDeviceLauncher.launch(outFile.name)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تصدير الملخص كملف 💾", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Auto Table of Contents / Outline Bottom Sheet (Works for EPUB, DOCX, TXT, HTML, PPTX, PDF)
    if (showOutlineSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOutlineSheet = false },
            containerColor = readerTheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("فهرس المحتويات والعناوين الذكي 📑", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = readerTheme.text)
                    Text("${documentOutline.size} قسم", fontSize = 11.sp, color = Color.Gray)
                }
                HorizontalDivider(color = readerTheme.text.copy(alpha = 0.1f))
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(documentOutline) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    when (extension) {
                                        "epub" -> epubCurrentChapterIndex = item.positionIndex
                                        "pptx", "ppt" -> pptxCurrentSlideIndex = item.positionIndex
                                        "pdf" -> pdfCurrentPage = item.positionIndex
                                        "docx" -> {
                                            val ratio = item.positionIndex.toFloat() / docxParagraphs.size.coerceAtLeast(1)
                                            coroutineScope.launch {
                                                textScrollState.animateScrollTo((ratio * textScrollState.maxValue).toInt())
                                            }
                                        }
                                        else -> {
                                            val ratio = item.positionIndex.toFloat() / textDocumentContent.length.coerceAtLeast(1)
                                            coroutineScope.launch {
                                                textScrollState.animateScrollTo((ratio * textScrollState.maxValue).toInt())
                                            }
                                        }
                                    }
                                    showOutlineSheet = false
                                    saveCurrentProgress()
                                }
                                .padding(
                                    start = if (item.level > 1) 18.dp else 8.dp,
                                    end = 8.dp,
                                    top = 9.dp,
                                    bottom = 9.dp
                                ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    fontSize = 13.sp,
                                    color = readerTheme.text,
                                    fontWeight = if (item.level == 1) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (item.subtitle.isNotBlank()) {
                                    Text(item.subtitle, fontSize = 10.sp, color = Color.Gray)
                                }
                            }
                            Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }

    // Upgrade 5 Dialog: Extract Specific PDF Page Range into Standalone PDF
    if (showExtractPdfRangeDialog && extension == "pdf" && pdfTotalPages > 0) {
        var fromPageInput by remember { mutableStateOf((pdfCurrentPage + 1).toString()) }
        var toPageInput by remember { mutableStateOf(minOf(pdfCurrentPage + 5, pdfTotalPages).toString()) }
        var isExtractingPages by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isExtractingPages) showExtractPdfRangeDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.ContentCut, contentDescription = null, tint = theme.primaryColor)
                    Text("استخراج نطاق صفحات PDF كملف مستقل ✂️", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "حدد أرقام الصفحات التي تريد فصلها من هذا المستند (الإجمالي: $pdfTotalPages صفحة):",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = fromPageInput,
                            onValueChange = { fromPageInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("من صفحة", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = toPageInput,
                            onValueChange = { toPageInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("إلى صفحة", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (isExtractingPages) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("جارٍ استخراج الصفحات وبناء ملف PDF جديد...", fontSize = 11.sp, color = theme.primaryColor)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val startP = (fromPageInput.toIntOrNull() ?: 1).coerceIn(1, pdfTotalPages)
                        val endP = (toPageInput.toIntOrNull() ?: startP).coerceIn(startP, pdfTotalPages)
                        isExtractingPages = true
                        coroutineScope.launch {
                            val extractedFile = withContext(Dispatchers.IO) {
                                MultiFormatDocumentEngine.extractPdfPageRange(
                                    context = context,
                                    sourceUri = uri,
                                    sourceFileName = fileName,
                                    startPageOneIndexed = startP,
                                    endPageOneIndexed = endP
                                )
                            }
                            isExtractingPages = false
                            showExtractPdfRangeDialog = false
                            if (extractedFile != null) {
                                pendingFileToSave = extractedFile
                                Toast.makeText(context, "تم استخراج الصفحات ($startP - $endP) بنجاح! اختر مكان الحفظ", Toast.LENGTH_LONG).show()
                                saveToDeviceLauncher.launch(extractedFile.name)
                            } else {
                                Toast.makeText(context, "تعذر استخراج الصفحات المطلوبة", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isExtractingPages,
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor)
                ) {
                    Text("استخراج وحفظ PDF")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showExtractPdfRangeDialog = false },
                    enabled = !isExtractingPages
                ) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Converter & Security Studio Dialog (Direct from Viewer)
    if (showConverterStudioDialog) {
        DocumentToolsAndConverterDialog(
            theme = theme,
            initialDocUri = uri,
            initialDocName = fileName,
            onDismiss = { showConverterStudioDialog = false }
        )
    }

    // DOCMO Local PDF Diagnostic Report Dialog
    if (showPdfDiagnosticDialog && pdfDiagnosticReport != null) {
        val rep = pdfDiagnosticReport!!
        AlertDialog(
            onDismissRequest = { showPdfDiagnosticDialog = false },
            title = {
                Text("فحص PDF محلي (DOCMO) 🔍", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "الملف: ${rep.fileName}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.primaryColor
                    )
                    rep.findings.forEach { finding ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(finding, fontSize = 12.sp)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "هذا الفحص يتم محليًا 100% على جهازك دون رفع الملف إلى أي خادم.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showPdfDiagnosticDialog = false }) {
                    Text("إغلاق")
                }
            }
        )
    }
}

@Composable
private fun InteractiveParagraphView(
    text: String,
    searchQuery: String,
    highlightItem: DocumentHighlightItem?,
    fontSizeSp: Float,
    lineSpacing: Float,
    fontWeight: FontWeight,
    textColor: Color,
    onClick: () -> Unit
) {
    val bgHighlightColor = remember(highlightItem?.colorHex) {
        if (highlightItem != null) {
            parseHexColorSafe(highlightItem.colorHex).copy(alpha = 0.35f)
        } else {
            Color.Transparent
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgHighlightColor)
            .clickable { onClick() }
            .padding(
                horizontal = if (highlightItem != null) 8.dp else 2.dp,
                vertical = 4.dp
            )
    ) {
        Text(
            text = buildHighlightedAnnotatedString(text, searchQuery),
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp * lineSpacing).sp,
            fontWeight = fontWeight,
            color = textColor
        )
        if (highlightItem != null && highlightItem.note.isNotBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFFFEF3C7)
            ) {
                Text(
                    text = "💡 ${highlightItem.note}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF92400E),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

private fun buildHighlightedAnnotatedString(text: String, query: String): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    return buildAnnotatedString {
        val regex = Regex(Regex.escape(query.trim()), RegexOption.IGNORE_CASE)
        var lastIndex = 0
        regex.findAll(text).forEach { matchResult ->
            val start = matchResult.range.first
            val end = matchResult.range.last + 1
            if (start > lastIndex) {
                append(text.substring(lastIndex, start))
            }
            withStyle(
                style = SpanStyle(
                    background = Color(0xFFFDE047),
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            ) {
                append(text.substring(start, end))
            }
            lastIndex = end
        }
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}

private fun parseHexColorSafe(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        Color(0xFFFDE047)
    }
}

@Composable
private fun PdfPageThumbnailCard(
    uri: Uri,
    pageIndex: Int,
    isSelected: Boolean,
    primaryColor: Color,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var thumbBitmap by remember(uri, pageIndex) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri, pageIndex) {
        thumbBitmap = withContext(Dispatchers.IO) {
            MultiFormatDocumentEngine.renderPdfPage(context, uri, pageIndex, scaleFactor = 0.28f)
        }
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = if (isSelected) primaryColor.copy(alpha = 0.15f) else Color.White,
        border = androidx.compose.foundation.BorderStroke(
            if (isSelected) 2.dp else 0.5.dp,
            if (isSelected) primaryColor else Color.LightGray
        ),
        modifier = Modifier.width(48.dp).height(64.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (thumbBitmap != null) {
                Image(
                    bitmap = thumbBitmap!!.asImageBitmap(),
                    contentDescription = "مصغرة صفحة ${pageIndex + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Surface(
                color = if (isSelected) primaryColor else Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(topStart = 4.dp),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Text(
                    text = "${pageIndex + 1}",
                    fontSize = 8.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun PdfPageView(
    bitmap: Bitmap?,
    scale: Float,
    offset: androidx.compose.ui.geometry.Offset,
    colorFilterMode: String = "NORMAL",
    onTransform: (Float, androidx.compose.ui.geometry.Offset) -> Unit,
    onSingleTap: () -> Unit = {},
    onSwipeNext: () -> Unit = {},
    onSwipePrevious: () -> Unit = {}
) {
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    val pdfColorFilter = remember(colorFilterMode) {
        when (colorFilterMode) {
            "NIGHT" -> {
                // Smart Dark Inversion Matrix for comfortable night PDF reading
                ColorFilter.colorMatrix(
                    ColorMatrix(
                        floatArrayOf(
                            -0.88f, 0f, 0f, 0f, 240f,
                            0f, -0.88f, 0f, 0f, 242f,
                            0f, 0f, -0.85f, 0f, 248f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            "SEPIA" -> {
                // Warm Vintage Sepia Paper Matrix
                ColorFilter.colorMatrix(
                    ColorMatrix(
                        floatArrayOf(
                            0.96f, 0.04f, 0f, 0f, 0f,
                            0.02f, 0.92f, 0.02f, 0f, 0f,
                            0f, 0.05f, 0.78f, 0f, 0f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            else -> null
        }
    }

    val containerBg = when (colorFilterMode) {
        "NIGHT" -> Color(0xFF0F172A)
        "SEPIA" -> Color(0xFFF5E6D3)
        else -> Color(0xFFE2E8F0)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(containerBg)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onSingleTap() }
                )
            }
            .pointerInput(scale) {
                if (scale <= 1.15f) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            dragAccumulator += dragAmount
                        },
                        onDragEnd = {
                            if (dragAccumulator < -60f) {
                                onSwipeNext()
                            } else if (dragAccumulator > 60f) {
                                onSwipePrevious()
                            }
                            dragAccumulator = 0f
                        },
                        onDragCancel = { dragAccumulator = 0f }
                    )
                } else {
                    detectTransformGestures { _, pan, zoom, _ ->
                        onTransform(zoom, pan)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "صفحة PDF",
                colorFilter = pdfColorFilter,
                modifier = Modifier
                    .fillMaxSize(0.99f)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    ),
                contentScale = ContentScale.Fit
            )
        } else {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun SpreadsheetGridView(
    data: SpreadsheetContent,
    extension: String,
    fileName: String,
    searchQuery: String,
    theme: AppThemeData,
    readerTheme: ReaderTheme,
    onUpdateCell: (rowIndex: Int, colIndex: Int, newValue: String) -> Unit,
    onExportXlsx: (isCsvConversion: Boolean) -> Unit
) {
    val horizontalScrollState = rememberScrollState()
    var editingCell by remember { mutableStateOf<Triple<Int, Int, String>?>(null) }

    val indexedFilteredRows = remember(searchQuery, data.rows) {
        val indexed = data.rows.mapIndexed { idx, row -> idx to row }
        if (searchQuery.isBlank()) {
            indexed
        } else {
            val q = searchQuery.trim().lowercase()
            indexed.filter { (_, row) -> row.any { it.lowercase().contains(q) } }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(readerTheme.surface)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(data.sheetName, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = theme.primaryColor)
                Text(
                    "إجمالي: ${indexedFilteredRows.size} صف • ${data.headers.size} عمود • اضغط على أي خلية لتعديلها",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }

            if (extension in listOf("csv", "tsv")) {
                Button(
                    onClick = { onExportXlsx(true) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تحويل CSV إلى Excel وحفظه", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = { onExportXlsx(false) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("حفظ نسخة Excel جديدة", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .horizontalScroll(horizontalScrollState)
        ) {
            LazyColumn(modifier = Modifier.fillMaxHeight()) {
                item {
                    Row(
                        modifier = Modifier
                            .background(theme.primaryColor.copy(alpha = 0.9f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "#",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .width(44.dp)
                                .padding(8.dp),
                            textAlign = TextAlign.Center
                        )
                        data.headers.forEach { header ->
                            Text(
                                text = header,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .width(130.dp)
                                    .padding(8.dp)
                                    .border(0.5.dp, Color.White.copy(alpha = 0.2f)),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                itemsIndexed(indexedFilteredRows) { displayIdx, (originalRowIdx, row) ->
                    val isEven = displayIdx % 2 == 0
                    Row(
                        modifier = Modifier
                            .background(if (isEven) readerTheme.bg else readerTheme.surface)
                            .border(0.5.dp, Color.LightGray.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "${originalRowIdx + 1}",
                            fontSize = 10.sp,
                            color = Color.Gray,
                            modifier = Modifier
                                .width(44.dp)
                                .padding(8.dp),
                            textAlign = TextAlign.Center
                        )
                        data.headers.indices.forEach { colIdx ->
                            val cellValue = row.getOrNull(colIdx) ?: ""
                            Text(
                                text = buildHighlightedAnnotatedString(cellValue, searchQuery),
                                fontSize = 11.sp,
                                color = readerTheme.text,
                                modifier = Modifier
                                    .width(130.dp)
                                    .clickable {
                                        editingCell = Triple(originalRowIdx, colIdx, cellValue)
                                    }
                                    .padding(8.dp)
                                    .border(0.5.dp, Color.LightGray.copy(alpha = 0.2f)),
                                textAlign = TextAlign.Start,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    if (editingCell != null) {
        val (rIdx, cIdx, initialVal) = editingCell!!
        var currentInput by remember(editingCell) { mutableStateOf(initialVal) }
        val colName = data.headers.getOrElse(cIdx) { "العمود ${cIdx + 1}" }

        AlertDialog(
            onDismissRequest = { editingCell = null },
            title = {
                Text("تعديل الخلية (الصف ${rIdx + 1} • $colName)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = currentInput,
                    onValueChange = { currentInput = it },
                    label = { Text("قيمة الخلية") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateCell(rIdx, cIdx, currentInput)
                    editingCell = null
                }) {
                    Text("تحديث الخلية")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCell = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
