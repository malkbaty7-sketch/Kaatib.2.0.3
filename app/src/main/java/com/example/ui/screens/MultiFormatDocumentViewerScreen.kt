package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.geometry.Offset
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
import com.example.util.DocumentFlashcardItem
import com.example.util.DocumentHighlightItem
import com.example.util.DocumentMindMapNode
import com.example.util.DocumentOutlineItem
import com.example.util.DocumentParser
import com.example.util.EpubBookContent
import com.example.util.PageVoiceNoteItem
import com.example.util.MultiFormatDocumentEngine
import com.example.util.PdfDiagnosticReport
import com.example.util.PdfInkStroke
import com.example.util.PdfRedactionRect
import com.example.util.PdfStampItem
import com.example.util.PptxPresentationContent
import com.example.util.SpreadsheetContent
import com.example.util.TtsManager
import com.example.util.TtsPlaybackState
import com.example.util.DocumentRecoveryCheckpoint
import com.example.util.DocumentVaultAndSaveManager
import com.example.util.LiveSaveState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var activeUriOverride by remember(uriString) { mutableStateOf<String?>(null) }
    var activeFileNameOverride by remember(fileName) { mutableStateOf<String?>(null) }
    val effectiveUriString = activeUriOverride ?: uriString
    val effectiveFileName = activeFileNameOverride ?: fileName
    val uri = remember(effectiveUriString) { Uri.parse(effectiveUriString) }
    val extension = remember(effectiveFileName) { MultiFormatDocumentEngine.getFileExtension(effectiveFileName) }

    // Saved Progress state from Room
    val savedProgress by viewModel.getDocumentProgress(effectiveUriString).collectAsState(initial = null)
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

    // Pro Workstation Package 1: PDF Freehand Ink Drawing, Stamps & Electronic Signatures
    var isPdfDrawingMode by remember { mutableStateOf(false) }
    var pdfInkStrokes by remember { mutableStateOf<List<PdfInkStroke>>(emptyList()) }
    var pdfStamps by remember { mutableStateOf<List<PdfStampItem>>(emptyList()) }
    var activeInkColorHex by remember { mutableStateOf("#EF4444") }
    var activeInkStrokeWidth by remember { mutableFloatStateOf(4f) }
    var activeInkAlpha by remember { mutableFloatStateOf(0.9f) }
    var showPdfStampDialog by remember { mutableStateOf(false) }

    // Pro Workstation Package 2: Smart Research Assistant & Instant Translation
    var showSmartResearchSheet by remember { mutableStateOf(false) }

    // Pro Workstation Package 3: Split-Screen Dual Document & Live Quick Scratchpad
    var isSplitScreenActive by remember { mutableStateOf(false) }

    // Pro Workstation Package 4: Academic Citation Generator & Flashcards / Glossary Builder
    var flashcards by remember { mutableStateOf<List<DocumentFlashcardItem>>(emptyList()) }
    var showFlashcardsAndCitationSheet by remember { mutableStateOf(false) }

    // Pro Workstation Package 5: PDF Power Tools (Rotation, Deletion, Watermark, Merge & Live OCR)
    var showPdfPowerToolsDialog by remember { mutableStateOf(false) }
    var pdfPageRotations by remember { mutableStateOf<Map<Int, Float>>(emptyMap()) }
    var pdfDeletedPages by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // Pro Workstation Package 6: Reading Focus Ruler, Pomodoro Timer & Offline Ambient Focus Sounds
    var showFocusStudioDialog by remember { mutableStateOf(false) }
    var showFocusRuler by remember { mutableStateOf(false) }
    var focusRulerYFraction by remember { mutableFloatStateOf(0.42f) }
    var isPomodoroRunning by remember { mutableStateOf(false) }
    var pomodoroRemainingSeconds by remember { mutableIntStateOf(25 * 60) }
    var activeAmbientSoundMode by remember { mutableStateOf("OFF") }
    var readingSpeedWpm by remember { mutableIntStateOf(180) }

    // =========================================================================
    // ROUND 3: NEXT-GEN COGNITIVE & PUBLISHING SUITE STATES
    // =========================================================================
    val activeChapters by viewModel.activeChapters.collectAsState()
    val activeChapterIndex by viewModel.activeChapterIndex.collectAsState()
    val currentEditorChapter = activeChapters.getOrNull(activeChapterIndex)

    // 1. Interactive Document Mind Map
    var showMindMapSheet by remember { mutableStateOf(false) }
    var customMindMapNodes by remember { mutableStateOf<List<DocumentMindMapNode>>(emptyList()) }

    // 2. RSVP Speed Reader (200 - 900 WPM)
    var showRsvpSpeedReaderDialog by remember { mutableStateOf(false) }

    // 3. Ask Document Q&A & Originality / Overlap Checker
    var showAskDocumentAndOriginalitySheet by remember { mutableStateOf(false) }

    // 4. Interactive Charts & Statistical Data Visualizer
    var showChartsAndStatsSheet by remember { mutableStateOf(false) }

    // 5. PDF Blackout Redaction & Digital Forensics Inspector
    var showRedactionAndForensicsSheet by remember { mutableStateOf(false) }
    var isPdfRedactionMode by remember { mutableStateOf(false) }
    var pdfRedactions by remember { mutableStateOf<List<PdfRedactionRect>>(emptyList()) }

    // 6. Smart Magnifier Loupe (2.5x), Auto-Crop White Margins & Presenter Laser Pointer
    var isMagnifierLoupeActive by remember { mutableStateOf(false) }
    var isPdfAutoCropMargins by remember { mutableStateOf(false) }
    var isLaserPointerActive by remember { mutableStateOf(false) }

    // =========================================================================
    // ROUND 4: PRO DOCUMENT & RESEARCH WORKSTATION STATES
    // =========================================================================
    var showPdfVisualOrganizerSheet by remember { mutableStateOf(false) }
    var showArabicLinguisticsSheet by remember { mutableStateOf(false) }
    var showTimelineAndEntitiesSheet by remember { mutableStateOf(false) }
    var showQuizAnd3DFlashcardsSheet by remember { mutableStateOf(false) }
    var showOfficialStampAndCertSheet by remember { mutableStateOf(false) }
    var showVoiceNotesSheet by remember { mutableStateOf(false) }
    var pageVoiceNotes by remember { mutableStateOf<List<PageVoiceNoteItem>>(emptyList()) }
    var showBackOfBookIndexSheet by remember { mutableStateOf(false) }
    var showAlignmentGridOverlay by remember { mutableStateOf(false) }

    // =========================================================================
    // ROUND 5: SMART DOCUMENT SAVING, VAULT, VERSION HISTORY & .ABDOC BUNDLE
    // =========================================================================
    var showSaveAndVaultCenterSheet by remember { mutableStateOf(false) }
    var saveAndVaultInitialTab by remember { mutableStateOf("SAVE_AS") }
    var liveSaveState by remember { mutableStateOf(LiveSaveState.SAVED) }
    var lastAutoSavedTimeText by remember { mutableStateOf("") }
    var pendingRecoveryCheckpoint by remember { mutableStateOf<DocumentRecoveryCheckpoint?>(null) }
    var isDocumentInVault by remember { mutableStateOf(false) }
    var snapshotsCount by remember { mutableIntStateOf(0) }
    var autoSaveInitialized by remember { mutableStateOf(false) }

    // ROUND 6: SMART TEXT EXTRACTION & OCR STUDIO (Area OCR, Pre-OCR Filters, Post-Processing, Tables, Entities, Batch)
    var showSmartExtractionStudio by remember { mutableStateOf(false) }

    // ROUND 7: BROWSER-LIKE CONTINUOUS SCROLL, 2-FINGER PINCH-TO-ZOOM & LONG-PRESS TEXT SELECTION
    var isContinuousBrowserScrollMode by remember { mutableStateOf(true) }
    var isSelectableTextLayerVisible by remember { mutableStateOf(false) }
    var activeSelectablePdfPageDialog by remember { mutableStateOf<Int?>(null) }
    var documentZoomScale by remember { mutableFloatStateOf(1f) }
    var documentPanOffset by remember { mutableStateOf(Offset.Zero) }

    // Format specific states
    // PDF
    var pdfTotalPages by remember { mutableStateOf(0) }
    var pdfCurrentPage by remember { mutableStateOf(0) }
    var pdfBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pdfScale by remember { mutableStateOf(1f) }
    var pdfOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var isPdfContinuousSelectionView by remember { mutableStateOf(false) }
    val pdfLazyListState = rememberLazyListState()

    // EPUB
    var epubBook by remember { mutableStateOf<EpubBookContent?>(null) }
    var epubCurrentChapterIndex by remember { mutableStateOf(0) }
    val epubScrollState = rememberScrollState()
    val epubContinuousListState = rememberLazyListState()

    // PowerPoint (PPTX)
    var pptxPresentation by remember { mutableStateOf<PptxPresentationContent?>(null) }
    var pptxCurrentSlideIndex by remember { mutableStateOf(0) }
    val pptxContinuousListState = rememberLazyListState()

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
        pdfExtractedFullText,
        isContinuousBrowserScrollMode
    ) {
        when (extension) {
            "epub" -> {
                if (isContinuousBrowserScrollMode) {
                    epubBook?.chapters?.joinToString("\n\n") { "${it.title}\n${it.textContent}" } ?: ""
                } else {
                    epubBook?.chapters?.getOrNull(epubCurrentChapterIndex)?.textContent ?: ""
                }
            }
            "pptx", "ppt" -> {
                if (isContinuousBrowserScrollMode) {
                    pptxPresentation?.slides?.joinToString("\n\n") { "${it.title}\n${it.textContent}" } ?: ""
                } else {
                    pptxPresentation?.slides?.getOrNull(pptxCurrentSlideIndex)?.textContent ?: ""
                }
            }
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

    // Auto-scroll effect (Supports smooth continuous browser-like pixel scrolling)
    LaunchedEffect(isAutoScrollActive, autoScrollSpeed, extension, isContinuousBrowserScrollMode) {
        while (isAutoScrollActive) {
            delay(45L)
            val step = autoScrollSpeed * 2
            when (extension) {
                "epub" -> {
                    if (isContinuousBrowserScrollMode) {
                        epubContinuousListState.dispatchRawDelta(step.toFloat())
                    } else if (epubScrollState.value < epubScrollState.maxValue) {
                        epubScrollState.scrollTo((epubScrollState.value + step).coerceAtMost(epubScrollState.maxValue))
                    } else {
                        isAutoScrollActive = false
                    }
                }
                "pdf" -> {
                    if (isContinuousBrowserScrollMode) {
                        val consumed = pdfLazyListState.dispatchRawDelta(step.toFloat())
                        if (consumed == 0f && !pdfLazyListState.canScrollForward) {
                            isAutoScrollActive = false
                        }
                    } else {
                        // For single-page PDF view, advance page every few seconds
                        delay(3500L / autoScrollSpeed.coerceAtLeast(1))
                        if (pdfCurrentPage < pdfTotalPages - 1) {
                            pdfCurrentPage++
                        } else {
                            isAutoScrollActive = false
                        }
                    }
                }
                "pptx", "ppt" -> {
                    if (isContinuousBrowserScrollMode) {
                        pptxContinuousListState.dispatchRawDelta(step.toFloat())
                    } else {
                        delay(3500L / autoScrollSpeed.coerceAtLeast(1))
                        val totalSlides = pptxPresentation?.slides?.size ?: 0
                        if (pptxCurrentSlideIndex < totalSlides - 1) {
                            pptxCurrentSlideIndex++
                        } else {
                            isAutoScrollActive = false
                        }
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
            fileUri = effectiveUriString,
            fileName = effectiveFileName,
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
                    bookUri = effectiveUriString,
                    chapterIndex = epubCurrentChapterIndex,
                    scrollOffset = 0
                )
            }
        }
    }

    // Pomodoro Countdown Timer Effect
    LaunchedEffect(isPomodoroRunning) {
        while (isPomodoroRunning && pomodoroRemainingSeconds > 0) {
            delay(1000L)
            pomodoroRemainingSeconds--
            if (pomodoroRemainingSeconds == 0) {
                isPomodoroRunning = false
                Toast.makeText(context, "انتهت جلسة التركيز (Pomodoro) بنجاح! خذ استراحة قصيرة ☕", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Auto-save on back or disposal & cleanup TTS + Ambient Audio
    DisposableEffect(effectiveUriString) {
        onDispose {
            MultiFormatDocumentEngine.stopAmbientFocusSound()
            ttsManager.shutdown()
            saveCurrentProgress()
            DocumentVaultAndSaveManager.markSessionCleanOnNormalExit(context, effectiveUriString)
        }
    }

    BackHandler {
        when {
            activeSelectablePdfPageDialog != null -> activeSelectablePdfPageDialog = null
            isPdfContinuousSelectionView -> isPdfContinuousSelectionView = false
            isImmersiveFullscreen -> isImmersiveFullscreen = false
            isPdfDrawingMode -> isPdfDrawingMode = false
            isPdfRedactionMode -> isPdfRedactionMode = false
            isSplitScreenActive -> isSplitScreenActive = false
            isSearchActive -> {
                isSearchActive = false
                searchQuery = ""
            }
            documentZoomScale != 1f -> {
                documentZoomScale = 1f
                documentPanOffset = Offset.Zero
            }
            else -> {
                MultiFormatDocumentEngine.stopAmbientFocusSound()
                ttsManager.stop()
                saveCurrentProgress()
                DocumentVaultAndSaveManager.markSessionCleanOnNormalExit(context, effectiveUriString)
                onBack()
            }
        }
    }

    // Load Document Content & Initial Progress & Bookmarks & Highlights & Workstation Data
    LaunchedEffect(effectiveUriString) {
        isLoading = true
        autoSaveInitialized = false
        withContext(Dispatchers.IO) {
            bookmarks = MultiFormatDocumentEngine.getDocumentBookmarks(context, effectiveUriString)
            highlights = MultiFormatDocumentEngine.getDocumentHighlights(context, effectiveUriString)
            flashcards = MultiFormatDocumentEngine.getDocumentFlashcards(context, effectiveUriString)
            pdfInkStrokes = MultiFormatDocumentEngine.getPdfInkStrokes(context, effectiveUriString)
            pdfStamps = MultiFormatDocumentEngine.getPdfStamps(context, effectiveUriString)
            customMindMapNodes = MultiFormatDocumentEngine.getCustomMindMapNodes(context, effectiveUriString)
            pdfRedactions = MultiFormatDocumentEngine.getPdfRedactions(context, effectiveUriString)
            pageVoiceNotes = MultiFormatDocumentEngine.getPageVoiceNotes(context, effectiveUriString)
            pendingRecoveryCheckpoint = DocumentVaultAndSaveManager.checkPendingRecoveryCheckpoint(context, effectiveUriString)
            isDocumentInVault = DocumentVaultAndSaveManager.isDocumentInVault(context, effectiveUriString) != null
            val existingSnaps = DocumentVaultAndSaveManager.getDocumentSnapshots(context, effectiveUriString)
            snapshotsCount = existingSnaps.size
            DocumentVaultAndSaveManager.markSessionActiveDirty(context, effectiveUriString)
            val initialSaved = viewModel.getDocumentProgressSync(effectiveUriString)
            when (extension) {
                "pdf" -> {
                    pdfTotalPages = MultiFormatDocumentEngine.getPdfPageCount(context, uri)
                    val startPage = initialSaved?.lastPageIndex?.coerceIn(0, (pdfTotalPages - 1).coerceAtLeast(0)) ?: 0
                    pdfCurrentPage = startPage
                    val rawBmp = MultiFormatDocumentEngine.renderPdfPage(context, uri, startPage)
                    pdfBitmap = if (isPdfAutoCropMargins && rawBmp != null) {
                        MultiFormatDocumentEngine.cropPdfBitmapWhitespace(rawBmp)
                    } else rawBmp
                    // Extract text in background for TTS / Quotes / Search
                    try {
                        pdfExtractedFullText = DocumentParser.parseUri(context, uri).text
                    } catch (_: Exception) {}
                }
                "epub" -> {
                    val book = MultiFormatDocumentEngine.parseEpub(context, uri)
                    epubBook = book
                    val dataStorePos = com.example.data.preferences.ReaderPreferences(context).getLastReadingPosition(effectiveUriString)
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
                    accessDatabaseInfo = DocmoDocumentBridge.inspectAccessDatabase(context, uri, effectiveFileName)
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
        lastAutoSavedTimeText = SimpleDateFormat("HH:mm", Locale.US).format(Date())
        liveSaveState = if (pendingRecoveryCheckpoint != null) LiveSaveState.RECOVERY_AVAILABLE else LiveSaveState.SAVED
        autoSaveInitialized = true
    }

    // Smart Auto-Save & Crash Recovery Checkpoint Watcher
    LaunchedEffect(
        highlights,
        bookmarks,
        pdfInkStrokes,
        pdfStamps,
        flashcards,
        customMindMapNodes,
        pdfRedactions,
        pageVoiceNotes,
        textDocumentContent
    ) {
        if (!autoSaveInitialized || isLoading) return@LaunchedEffect
        liveSaveState = LiveSaveState.UNSAVED_CHANGES
        delay(900L)
        liveSaveState = LiveSaveState.SAVING
        withContext(Dispatchers.IO) {
            DocumentVaultAndSaveManager.recordAutoSaveCheckpoint(
                context = context,
                uriString = effectiveUriString,
                documentName = effectiveFileName,
                highlights = highlights,
                bookmarks = bookmarks,
                inkStrokes = pdfInkStrokes,
                stamps = pdfStamps,
                flashcards = flashcards,
                mindMapNodes = customMindMapNodes,
                redactions = pdfRedactions,
                voiceNotes = pageVoiceNotes,
                editedTextContent = textDocumentContent,
                markSessionClean = false
            )
            // Create initial auto-snapshot if user made edits and no snapshot exists yet
            val currentSnaps = DocumentVaultAndSaveManager.getDocumentSnapshots(context, effectiveUriString)
            val totalAnnotations = highlights.size + bookmarks.size + pdfInkStrokes.size + pdfStamps.size + flashcards.size + pageVoiceNotes.size
            if (currentSnaps.isEmpty() && totalAnnotations > 0) {
                DocumentVaultAndSaveManager.createDocumentSnapshot(
                    context = context,
                    uriString = effectiveUriString,
                    documentName = effectiveFileName,
                    label = "لقطة حفظ تلقائي ذكي",
                    isAutoSnapshot = true,
                    highlights = highlights,
                    bookmarks = bookmarks,
                    inkStrokes = pdfInkStrokes,
                    stamps = pdfStamps,
                    flashcards = flashcards,
                    mindMapNodes = customMindMapNodes,
                    redactions = pdfRedactions,
                    voiceNotes = pageVoiceNotes,
                    editedTextContent = textDocumentContent
                )
            }
            snapshotsCount = DocumentVaultAndSaveManager.getDocumentSnapshots(context, effectiveUriString).size
            isDocumentInVault = DocumentVaultAndSaveManager.isDocumentInVault(context, effectiveUriString) != null
        }
        delay(250L)
        lastAutoSavedTimeText = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        liveSaveState = LiveSaveState.SAVED
    }

    // Restore text scroll position after content loaded
    LaunchedEffect(isLoading, savedProgress) {
        if (!isLoading && savedProgress != null && savedProgress!!.scrollOffset > 0 && textScrollState.maxValue > 0) {
            textScrollState.scrollTo(savedProgress!!.scrollOffset.coerceAtMost(textScrollState.maxValue))
        }
    }

    // Sync PDF Continuous Browser Scroll list state <-> pdfCurrentPage
    LaunchedEffect(pdfLazyListState.firstVisibleItemIndex, pdfLazyListState.isScrollInProgress) {
        if (extension == "pdf" && isContinuousBrowserScrollMode && pdfLazyListState.isScrollInProgress) {
            val visiblePage = pdfLazyListState.firstVisibleItemIndex.coerceIn(0, (pdfTotalPages - 1).coerceAtLeast(0))
            if (visiblePage != pdfCurrentPage) {
                pdfCurrentPage = visiblePage
            }
        }
    }

    LaunchedEffect(pdfCurrentPage, isLoading, isContinuousBrowserScrollMode) {
        if (!isLoading && extension == "pdf" && isContinuousBrowserScrollMode && pdfTotalPages > 0) {
            if (!pdfLazyListState.isScrollInProgress && pdfLazyListState.firstVisibleItemIndex != pdfCurrentPage) {
                pdfLazyListState.scrollToItem(pdfCurrentPage.coerceIn(0, (pdfTotalPages - 1).coerceAtLeast(0)))
            }
        }
    }

    // Sync EPUB Continuous Browser Scroll list state <-> epubCurrentChapterIndex
    LaunchedEffect(epubContinuousListState.firstVisibleItemIndex, epubContinuousListState.isScrollInProgress) {
        val chCount = epubBook?.chapters?.size ?: 0
        if (extension == "epub" && isContinuousBrowserScrollMode && epubContinuousListState.isScrollInProgress && chCount > 0) {
            val visibleCh = epubContinuousListState.firstVisibleItemIndex.coerceIn(0, chCount - 1)
            if (visibleCh != epubCurrentChapterIndex) {
                epubCurrentChapterIndex = visibleCh
            }
        }
    }

    LaunchedEffect(epubCurrentChapterIndex, isLoading, isContinuousBrowserScrollMode) {
        val chCount = epubBook?.chapters?.size ?: 0
        if (!isLoading && extension == "epub" && isContinuousBrowserScrollMode && chCount > 0) {
            if (!epubContinuousListState.isScrollInProgress && epubContinuousListState.firstVisibleItemIndex != epubCurrentChapterIndex) {
                epubContinuousListState.scrollToItem(epubCurrentChapterIndex.coerceIn(0, chCount - 1))
            }
        }
    }

    // Sync PPTX Continuous Browser Scroll list state <-> pptxCurrentSlideIndex
    LaunchedEffect(pptxContinuousListState.firstVisibleItemIndex, pptxContinuousListState.isScrollInProgress) {
        val slideCount = pptxPresentation?.slides?.size ?: 0
        if (extension in listOf("pptx", "ppt") && isContinuousBrowserScrollMode && pptxContinuousListState.isScrollInProgress && slideCount > 0) {
            val visibleSlide = pptxContinuousListState.firstVisibleItemIndex.coerceIn(0, slideCount - 1)
            if (visibleSlide != pptxCurrentSlideIndex) {
                pptxCurrentSlideIndex = visibleSlide
            }
        }
    }

    LaunchedEffect(pptxCurrentSlideIndex, isLoading, isContinuousBrowserScrollMode) {
        val slideCount = pptxPresentation?.slides?.size ?: 0
        if (!isLoading && extension in listOf("pptx", "ppt") && isContinuousBrowserScrollMode && slideCount > 0) {
            if (!pptxContinuousListState.isScrollInProgress && pptxContinuousListState.firstVisibleItemIndex != pptxCurrentSlideIndex) {
                pptxContinuousListState.scrollToItem(pptxCurrentSlideIndex.coerceIn(0, slideCount - 1))
            }
        }
    }

    // PDF Page Change & Auto-Crop Margins renderer (for single-page mode or active page thumbnail/loupe)
    LaunchedEffect(pdfCurrentPage, isPdfAutoCropMargins) {
        if (extension == "pdf" && pdfTotalPages > 0) {
            withContext(Dispatchers.IO) {
                val rawBmp = MultiFormatDocumentEngine.renderPdfPage(context, uri, pdfCurrentPage)
                pdfBitmap = if (isPdfAutoCropMargins && rawBmp != null) {
                    MultiFormatDocumentEngine.cropPdfBitmapWhitespace(rawBmp)
                } else rawBmp
            }
            if (!isContinuousBrowserScrollMode) {
                pdfScale = 1f
                pdfOffset = androidx.compose.ui.geometry.Offset.Zero
            }
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
                                        text = effectiveFileName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = readerTheme.text
                                    )
                                    val totalWordsCount = remember(currentReadableText) {
                                        currentReadableText.split(Regex("\\s+")).count { it.isNotBlank() }
                                    }
                                    val estMins = (totalWordsCount / readingSpeedWpm.coerceAtLeast(60)).coerceAtLeast(1)
                                    val progressDisplay = when (extension) {
                                        "pdf" -> if (pdfTotalPages > 0) "صفحة ${pdfCurrentPage + 1} / $pdfTotalPages • ~$estMins د" else "PDF"
                                        "epub" -> epubBook?.let { "فصل ${epubCurrentChapterIndex + 1} / ${it.chapters.size} • ~$estMins د" } ?: "EPUB"
                                        "pptx", "ppt" -> pptxPresentation?.let { "شريحة ${pptxCurrentSlideIndex + 1} / ${it.slides.size}" } ?: "PowerPoint"
                                        else -> {
                                            val pct = if (textScrollState.maxValue > 0) {
                                                ((textScrollState.value.toFloat() / textScrollState.maxValue.toFloat()) * 100).toInt()
                                            } else 0
                                            "تقدم: $pct% • ~$estMins د"
                                        }
                                    }
                                    val pomoTag = if (isPomodoroRunning) {
                                        " • ⏱️ ${pomodoroRemainingSeconds / 60}:${String.format(java.util.Locale.US, "%02d", pomodoroRemainingSeconds % 60)}"
                                    } else ""
                                    Text(progressDisplay + pomoTag, fontSize = 8.sp, color = Color.Gray, maxLines = 1)
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

                                // 9. Smart Save, Vault, Version History & .abdoc Bundle Center
                                IconButton(
                                    onClick = {
                                        saveAndVaultInitialTab = "SAVE_AS"
                                        showSaveAndVaultCenterSheet = true
                                    },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CloudDone,
                                        contentDescription = "مركز الحفظ المتقدم والخزنة الذكية",
                                        tint = Color(0xFF16A34A),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                // 10. Converter Studio
                                IconButton(
                                    onClick = { showConverterStudioDialog = true },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.Default.Transform, contentDescription = "تحويل وأمان المستند", tint = theme.primaryColor, modifier = Modifier.size(14.dp))
                                }

                                // 11. Immersive Fullscreen Toggle
                                IconButton(
                                    onClick = { isImmersiveFullscreen = true },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.Default.Fullscreen, contentDescription = "وضع ملء الشاشة الغامر", tint = readerTheme.text, modifier = Modifier.size(15.dp))
                                }

                                // 12. Format Settings
                                IconButton(
                                    onClick = { showSettingsSheet = true },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.Default.Tune, contentDescription = "إعدادات القراءة", tint = readerTheme.text, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    // Pro Workstation Quick Ribbon (All Advanced Upgrade Packages + Smart Save & Vault System)
                    Surface(
                        color = readerTheme.surface,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Live Auto-Save Status Indicator + Quick Open Save Center
                            item {
                                LiveAutoSaveStatusBadge(
                                    saveState = liveSaveState,
                                    lastSavedTimeText = lastAutoSavedTimeText,
                                    isInVault = isDocumentInVault,
                                    snapshotsCount = snapshotsCount,
                                    onClick = {
                                        saveAndVaultInitialTab = "SAVE_AS"
                                        showSaveAndVaultCenterSheet = true
                                    }
                                )
                            }

                            // NEW 1: Continuous Browser-Like Smooth Scroll Toggle Chip
                            if (extension in listOf("pdf", "epub", "pptx", "ppt")) {
                                item {
                                    Surface(
                                        onClick = {
                                            isContinuousBrowserScrollMode = !isContinuousBrowserScrollMode
                                            val msg = if (isContinuousBrowserScrollMode) {
                                                "تم تفعيل التمرير السلس المستمر (مثل القراءة من المتصفح) 📜"
                                            } else {
                                                "تم التبديل إلى وضع عرض صفحة مستقلة 📄"
                                            }
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isContinuousBrowserScrollMode) Color(0xFF0284C7) else Color(0xFF0284C7).copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = if (isContinuousBrowserScrollMode) "📜 تمرير سلس كالمتصفح" else "📄 صفحة بصفحة",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isContinuousBrowserScrollMode) Color.White else Color(0xFF0284C7),
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            // NEW 2: Long-Press Text Selection & Copy Layer Chip
                            item {
                                Surface(
                                    onClick = {
                                        if (extension == "pdf") {
                                            isSelectableTextLayerVisible = !isSelectableTextLayerVisible
                                            val msg = if (isSelectableTextLayerVisible) {
                                                "تم إظهار طبقة النص القابل للتحديد والنسخ بالضغط المطول 🔤"
                                            } else {
                                                "تم إخفاء طبقة النص (يمكنك الضغط مطولاً على أي صفحة لتحديد نصها) 🔤"
                                            }
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "اضغط مطولاً بإصبعك على أي كلمة أو جملة لتحديدها ونسخها فوراً 📋",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (extension != "pdf" || isSelectableTextLayerVisible) {
                                        Color(0xFF16A34A)
                                    } else {
                                        Color(0xFF16A34A).copy(alpha = 0.14f)
                                    }
                                ) {
                                    Text(
                                        text = if (extension == "pdf") {
                                            if (isSelectableTextLayerVisible) "🔤 طبقة تحديد النص: مفعلة" else "🔤 تحديد نص PDF بالضغط المطول"
                                        } else {
                                            "🔤 تحديد ونسخ بالضغط المطول"
                                        },
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (extension != "pdf" || isSelectableTextLayerVisible) Color.White else Color(0xFF16A34A),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // NEW 3: Pinch-to-Zoom Indicator & Quick Zoom Preset Chip
                            item {
                                val activeZoom = if (extension == "pdf" && !isContinuousBrowserScrollMode) pdfScale else documentZoomScale
                                val zoomPct = (activeZoom * 100).toInt()
                                Surface(
                                    onClick = {
                                        val nextZoom = when {
                                            activeZoom < 1.2f -> 1.35f
                                            activeZoom < 1.6f -> 1.75f
                                            activeZoom < 2.2f -> 2.25f
                                            else -> 1f
                                        }
                                        documentZoomScale = nextZoom
                                        pdfScale = nextZoom
                                        if (nextZoom == 1f) {
                                            documentPanOffset = Offset.Zero
                                            pdfOffset = Offset.Zero
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (zoomPct != 100) Color(0xFFEA580C) else Color(0xFFEA580C).copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = "🔍 تكبير الأصابع: $zoomPct%",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (zoomPct != 100) Color.White else Color(0xFFEA580C),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // Quick Vault & PIN Chip
                            item {
                                Surface(
                                    onClick = {
                                        saveAndVaultInitialTab = "VAULT"
                                        showSaveAndVaultCenterSheet = true
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isDocumentInVault) Color(0xFF0D9488) else Color(0xFF0D9488).copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = if (isDocumentInVault) "🛡️ مؤمّن بالخزنة" else "🛡️ حفظ بالخزنة و PIN",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDocumentInVault) Color.White else Color(0xFF0D9488),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // Quick Version History Chip
                            item {
                                Surface(
                                    onClick = {
                                        saveAndVaultInitialTab = "VERSIONS"
                                        showSaveAndVaultCenterSheet = true
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF7C3AED).copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = "🕒 سجل الإصدارات ($snapshotsCount)",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF7C3AED),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // Quick .abdoc Bundle Chip
                            item {
                                Surface(
                                    onClick = {
                                        saveAndVaultInitialTab = "BUNDLE"
                                        showSaveAndVaultCenterSheet = true
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFD97706).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "📦 حزمة .abdoc الشاملة",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFD97706),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // Quick Smart Text Extraction & OCR Studio Chip (All 6 Upgrades)
                            item {
                                Surface(
                                    onClick = { showSmartExtractionStudio = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF059669).copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = "🔍 استوديو الاستخراج والـ OCR الذكي",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF059669),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            // Package 1: PDF Freehand Ink & Stamps
                            if (extension == "pdf") {
                                item {
                                    Surface(
                                        onClick = { isPdfDrawingMode = !isPdfDrawingMode },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isPdfDrawingMode) Color(0xFFDC2626) else Color(0xFFDC2626).copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = "🖌️ قلم وتوقيع PDF",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isPdfDrawingMode) Color.White else Color(0xFFDC2626),
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            // Package 2: Smart Research Assistant & Instant Translation
                            item {
                                Surface(
                                    onClick = { showSmartResearchSheet = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = theme.primaryColor.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = "🧠 تلخيص وترجمة فورية",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = theme.primaryColor,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // Package 3: Split-Screen Reference + Live Scratchpad
                            item {
                                Surface(
                                    onClick = { isSplitScreenActive = !isSplitScreenActive },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSplitScreenActive) Color(0xFF059669) else Color(0xFF059669).copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = if (isSplitScreenActive) "🪟 إغلاق التقسيم" else "🪟 تقسيم الشاشة والمسودة",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSplitScreenActive) Color.White else Color(0xFF059669),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // Package 4: Academic Citations & Flashcards / Glossary
                            item {
                                Surface(
                                    onClick = { showFlashcardsAndCitationSheet = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF7C3AED).copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "🎓 التوثيق والبطاقات (${flashcards.size})",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF7C3AED),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // Package 5: PDF Power Tools & Live OCR
                            if (extension == "pdf") {
                                item {
                                    Surface(
                                        onClick = { showPdfPowerToolsDialog = true },
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF0284C7).copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = "🛠️ هندسة PDF و OCR",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0284C7),
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            // Package 6: Reading Focus Ruler & Pomodoro + Ambient Focus Sounds
                            item {
                                Surface(
                                    onClick = { showFocusRuler = !showFocusRuler },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (showFocusRuler) Color(0xFFD97706) else Color(0xFFD97706).copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "📏 مسطرة التركيز",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (showFocusRuler) Color.White else Color(0xFFD97706),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            item {
                                Surface(
                                    onClick = { showFocusStudioDialog = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isPomodoroRunning || activeAmbientSoundMode != "OFF") Color(0xFF0D9488) else Color(0xFF0D9488).copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = if (activeAmbientSoundMode != "OFF") "🎧 بومودورو وصوت نشط" else "⏱️ بومودورو وأصوات التركيز",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPomodoroRunning || activeAmbientSoundMode != "OFF") Color.White else Color(0xFF0D9488),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // =========================================================
                            // ROUND 3: NEXT-GEN COGNITIVE & PUBLISHING SUITE CHIPS
                            // =========================================================

                            // 1. Interactive Mind Map
                            item {
                                Surface(
                                    onClick = { showMindMapSheet = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF2563EB).copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = "🕸️ الخريطة الذهنية",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2563EB),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // 2. RSVP Speed Reader (200 - 900 WPM)
                            item {
                                Surface(
                                    onClick = { showRsvpSpeedReaderDialog = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFEF4444).copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = "⚡ القراءة السريعة RSVP",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFEF4444),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // 3. Ask Document Q&A & Originality Checker
                            item {
                                Surface(
                                    onClick = { showAskDocumentAndOriginalitySheet = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF8B5CF6).copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = "🤖 اسأل المستند وفحص الأصالة",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF8B5CF6),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // 4. Interactive Charts & Statistical Visualizer
                            item {
                                Surface(
                                    onClick = { showChartsAndStatsSheet = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF0EA5E9).copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = "📊 الرسوم البيانية والإحصائيات",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0EA5E9),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // 5. PDF Blackout Redaction & Digital Forensics
                            item {
                                Surface(
                                    onClick = { showRedactionAndForensicsSheet = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isPdfRedactionMode) Color(0xFFDC2626) else Color(0xFFDC2626).copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = if (isPdfRedactionMode) "🛡️ وضع الطمس نشط (${pdfRedactions.size})" else "🛡️ طمس سري وفحص جنائي",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPdfRedactionMode) Color.White else Color(0xFFDC2626),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // 6. Smart Magnifier Loupe, Auto-Crop Margins & Laser Pointer
                            if (extension == "pdf") {
                                item {
                                    Surface(
                                        onClick = {
                                            isMagnifierLoupeActive = !isMagnifierLoupeActive
                                            if (isMagnifierLoupeActive) {
                                                isPdfDrawingMode = false
                                                isPdfRedactionMode = false
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isMagnifierLoupeActive) Color(0xFF1D4ED8) else Color(0xFF1D4ED8).copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = if (isMagnifierLoupeActive) "🔍 إيقاف العدسة 2.5x" else "🔍 عدسة مكبرة 2.5x",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isMagnifierLoupeActive) Color.White else Color(0xFF1D4ED8),
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                item {
                                    Surface(
                                        onClick = {
                                            isPdfAutoCropMargins = !isPdfAutoCropMargins
                                            Toast.makeText(
                                                context,
                                                if (isPdfAutoCropMargins) "تم تفعيل قص الهوامش البيضاء تلقائياً لتكبير النص ✂️" else "تم إرجاع الهوامش الأصلية للصفحة",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isPdfAutoCropMargins) Color(0xFF059669) else Color(0xFF059669).copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = if (isPdfAutoCropMargins) "✂️ قص الهوامش: مفعّل" else "✂️ قص الهوامش البيضاء",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isPdfAutoCropMargins) Color.White else Color(0xFF059669),
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            if (extension in listOf("pdf", "pptx", "ppt")) {
                                item {
                                    Surface(
                                        onClick = {
                                            isLaserPointerActive = !isLaserPointerActive
                                            if (isLaserPointerActive) {
                                                isPdfDrawingMode = false
                                                isPdfRedactionMode = false
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isLaserPointerActive) Color(0xFFEF4444) else Color(0xFFEF4444).copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = if (isLaserPointerActive) "🔴 إيقاف مؤشر الليزر" else "🔴 مؤشر ليزر مسرحي",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isLaserPointerActive) Color.White else Color(0xFFEF4444),
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            // =========================================================
                            // ROUND 4: PRO DOCUMENT & RESEARCH WORKSTATION CHIPS
                            // =========================================================

                            // 1. PDF Visual Page Organizer & Splitter
                            if (extension == "pdf") {
                                item {
                                    Surface(
                                        onClick = { showPdfVisualOrganizerSheet = true },
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF0284C7).copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = "🧩 منظم صفحات PDF المرئي",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0284C7),
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            // 2. Arabic Linguistics, Auto-Tashkeel & Poetry Prosody Analyzer
                            item {
                                Surface(
                                    onClick = { showArabicLinguisticsSheet = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFD97706).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "🔤 التشكيل الآلي والعَروض",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFD97706),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // 3. Interactive Timeline & Entity Extractor
                            item {
                                Surface(
                                    onClick = { showTimelineAndEntitiesSheet = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF059669).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "⏳ الخط الزمني والأعلام",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF059669),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // 4. 3D Flip Flashcards & Auto MCQ Quiz Studio
                            item {
                                Surface(
                                    onClick = { showQuizAnd3DFlashcardsSheet = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF7C3AED).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "🎴 بطاقات 3D واختبار ذاتي",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF7C3AED),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // 5. Official Digital Stamps & SHA-256 Certificate
                            item {
                                Surface(
                                    onClick = { showOfficialStampAndCertSheet = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF0D9488).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "🔐 الأختام وشهادة التوثيق",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0D9488),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // 6. Page-Pinned Voice & Audio Notes
                            item {
                                Surface(
                                    onClick = { showVoiceNotesSheet = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFE11D48).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "🎙️ ملاحظات صوتية (${pageVoiceNotes.size})",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE11D48),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // 7. Back-of-Book Automated Alphabetical Indexer
                            item {
                                Surface(
                                    onClick = { showBackOfBookIndexSheet = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF2563EB).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "📚 الكشاف الأبجدي (أ-ي)",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2563EB),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // 8. Precision Alignment & Typographic Measurement Grid Overlay
                            item {
                                Surface(
                                    onClick = { showAlignmentGridOverlay = !showAlignmentGridOverlay },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (showAlignmentGridOverlay) Color(0xFF4F46E5) else Color(0xFF4F46E5).copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = if (showAlignmentGridOverlay) "📐 إخفاء شبكة القياس" else "📐 شبكة القياس والمحاذاة",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (showAlignmentGridOverlay) Color.White else Color(0xFF4F46E5),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Crash / Interrupted Session Recovery Banner when available
                    AnimatedVisibility(visible = pendingRecoveryCheckpoint != null) {
                        pendingRecoveryCheckpoint?.let { checkpoint ->
                            DocumentCrashRecoveryBanner(
                                checkpoint = checkpoint,
                                onRestore = {
                                    val restored = DocumentVaultAndSaveManager.restoreFromRecoveryCheckpoint(
                                        context = context,
                                        uriString = effectiveUriString,
                                        checkpoint = checkpoint
                                    )
                                    highlights = restored.highlights
                                    bookmarks = restored.bookmarks
                                    pdfInkStrokes = restored.inkStrokes
                                    pdfStamps = restored.stamps
                                    flashcards = restored.flashcards
                                    customMindMapNodes = restored.mindMapNodes
                                    pdfRedactions = restored.redactions
                                    pageVoiceNotes = restored.voiceNotes
                                    if (!restored.editedTextContent.isNullOrBlank()) {
                                        textDocumentContent = restored.editedTextContent
                                    }
                                    pendingRecoveryCheckpoint = null
                                    liveSaveState = LiveSaveState.SAVED
                                    Toast.makeText(
                                        context,
                                        "✅ تمت استعادة كافة التعديلات والتظليلات من الجلسة السابقة بنجاح!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                },
                                onDismiss = {
                                    DocumentVaultAndSaveManager.markSessionCleanOnNormalExit(context, effectiveUriString)
                                    pendingRecoveryCheckpoint = null
                                    liveSaveState = LiveSaveState.SAVED
                                }
                            )
                        }
                    }

                    // PDF Freehand Ink Drawing Toolbar when active
                    AnimatedVisibility(visible = extension == "pdf" && isPdfDrawingMode) {
                        PdfDrawingToolbar(
                            activeColorHex = activeInkColorHex,
                            activeStrokeWidth = activeInkStrokeWidth,
                            activeAlpha = activeInkAlpha,
                            onColorChange = { activeInkColorHex = it },
                            onToolPresetChange = { w, a ->
                                activeInkStrokeWidth = w
                                activeInkAlpha = a
                            },
                            onUndoLastStroke = {
                                val pageStrokes = pdfInkStrokes.filter { it.pageIndex == pdfCurrentPage }
                                if (pageStrokes.isNotEmpty()) {
                                    val lastId = pageStrokes.last().id
                                    val updated = pdfInkStrokes.filter { it.id != lastId }
                                    pdfInkStrokes = updated
                                    MultiFormatDocumentEngine.savePdfInkStrokes(context, effectiveUriString, updated)
                                }
                            },
                            onClearPageStrokes = {
                                val updatedStrokes = pdfInkStrokes.filter { it.pageIndex != pdfCurrentPage }
                                val updatedStamps = pdfStamps.filter { it.pageIndex != pdfCurrentPage }
                                pdfInkStrokes = updatedStrokes
                                pdfStamps = updatedStamps
                                MultiFormatDocumentEngine.savePdfInkStrokes(context, effectiveUriString, updatedStrokes)
                                MultiFormatDocumentEngine.savePdfStamps(context, effectiveUriString, updatedStamps)
                                Toast.makeText(context, "تم مسح رسومات وأختام هذه الصفحة", Toast.LENGTH_SHORT).show()
                            },
                            onOpenStampDialog = { showPdfStampDialog = true },
                            onExportAnnotatedPdf = {
                                coroutineScope.launch {
                                    val outFile = withContext(Dispatchers.IO) {
                                        MultiFormatDocumentEngine.exportAnnotatedAndModifiedPdf(
                                            context = context,
                                            sourceUri = uri,
                                            sourceFileName = effectiveFileName,
                                            strokes = pdfInkStrokes,
                                            stamps = pdfStamps,
                                            pageRotations = pdfPageRotations,
                                            deletedPages = pdfDeletedPages
                                        )
                                    }
                                    if (outFile != null) {
                                        pendingFileToSave = outFile
                                        Toast.makeText(context, "تم دمج الرسومات والتوقيعات في ملف PDF جديد!", Toast.LENGTH_LONG).show()
                                        saveToDeviceLauncher.launch(outFile.name)
                                    }
                                }
                            },
                            onCloseDrawingMode = { isPdfDrawingMode = false }
                        )
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
                                                when (extension) {
                                                    "epub" -> {
                                                        val totalCh = epubBook?.chapters?.size ?: 0
                                                        if (isContinuousBrowserScrollMode && totalCh > 0) {
                                                            val targetCh = (ratio * totalCh).toInt().coerceIn(0, totalCh - 1)
                                                            epubCurrentChapterIndex = targetCh
                                                            epubContinuousListState.animateScrollToItem(targetCh)
                                                        } else {
                                                            epubScrollState.animateScrollTo((ratio * epubScrollState.maxValue).toInt())
                                                        }
                                                    }
                                                    "pptx", "ppt" -> {
                                                        val totalSlides = pptxPresentation?.slides?.size ?: 0
                                                        if (totalSlides > 0) {
                                                            val targetSlide = (ratio * totalSlides).toInt().coerceIn(0, totalSlides - 1)
                                                            pptxCurrentSlideIndex = targetSlide
                                                            if (isContinuousBrowserScrollMode) {
                                                                pptxContinuousListState.animateScrollToItem(targetSlide)
                                                            }
                                                        }
                                                    }
                                                    "pdf" -> {
                                                        if (pdfTotalPages > 0) {
                                                            val targetPage = (ratio * pdfTotalPages).toInt().coerceIn(0, pdfTotalPages - 1)
                                                            pdfCurrentPage = targetPage
                                                            if (isContinuousBrowserScrollMode) {
                                                                pdfLazyListState.animateScrollToItem(targetPage)
                                                            }
                                                        }
                                                    }
                                                    else -> {
                                                        textScrollState.animateScrollTo((ratio * textScrollState.maxValue).toInt())
                                                    }
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
                                                when (extension) {
                                                    "epub" -> {
                                                        val totalCh = epubBook?.chapters?.size ?: 0
                                                        if (isContinuousBrowserScrollMode && totalCh > 0) {
                                                            val targetCh = (ratio * totalCh).toInt().coerceIn(0, totalCh - 1)
                                                            epubCurrentChapterIndex = targetCh
                                                            epubContinuousListState.animateScrollToItem(targetCh)
                                                        } else {
                                                            epubScrollState.animateScrollTo((ratio * epubScrollState.maxValue).toInt())
                                                        }
                                                    }
                                                    "pptx", "ppt" -> {
                                                        val totalSlides = pptxPresentation?.slides?.size ?: 0
                                                        if (totalSlides > 0) {
                                                            val targetSlide = (ratio * totalSlides).toInt().coerceIn(0, totalSlides - 1)
                                                            pptxCurrentSlideIndex = targetSlide
                                                            if (isContinuousBrowserScrollMode) {
                                                                pptxContinuousListState.animateScrollToItem(targetSlide)
                                                            }
                                                        }
                                                    }
                                                    "pdf" -> {
                                                        if (pdfTotalPages > 0) {
                                                            val targetPage = (ratio * pdfTotalPages).toInt().coerceIn(0, pdfTotalPages - 1)
                                                            pdfCurrentPage = targetPage
                                                            if (isContinuousBrowserScrollMode) {
                                                                pdfLazyListState.animateScrollToItem(targetPage)
                                                            }
                                                        }
                                                    }
                                                    else -> {
                                                        textScrollState.animateScrollTo((ratio * textScrollState.maxValue).toInt())
                                                    }
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
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
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
                                        fileName = effectiveFileName,
                                        initialPageIndex = pdfCurrentPage,
                                        fullExtractedPdfText = pdfExtractedFullText,
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
                                } else if (isContinuousBrowserScrollMode && !isPdfDrawingMode && !isPdfRedactionMode) {
                                    // Smooth Browser-Like Continuous Vertical PDF Scroll + 2-Finger Pinch-to-Zoom + Long-Press Text Selection
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        ContinuousBrowserPdfViewer(
                                            uri = uri,
                                            fileName = effectiveFileName,
                                            totalPages = pdfTotalPages,
                                            currentPage = pdfCurrentPage,
                                            listState = pdfLazyListState,
                                            zoomScale = documentZoomScale,
                                            panOffset = documentPanOffset,
                                            pageRotations = pdfPageRotations,
                                            deletedPages = pdfDeletedPages,
                                            colorFilterMode = effectivePdfColorMode,
                                            isAutoCropMargins = isPdfAutoCropMargins,
                                            fullExtractedPdfText = pdfExtractedFullText,
                                            isSelectableTextLayerVisible = isSelectableTextLayerVisible,
                                            searchQuery = searchQuery,
                                            strokes = pdfInkStrokes,
                                            stamps = pdfStamps,
                                            redactions = pdfRedactions,
                                            primaryColor = theme.primaryColor,
                                            readerTheme = readerTheme,
                                            onZoomAndPanChange = { newScale, newPan ->
                                                documentZoomScale = newScale
                                                documentPanOffset = newPan
                                                pdfScale = newScale
                                                pdfOffset = newPan
                                            },
                                            onSingleTap = {
                                                isImmersiveFullscreen = !isImmersiveFullscreen
                                            },
                                            onLongPressPage = { pageIdx ->
                                                activeSelectablePdfPageDialog = pageIdx
                                                Toast.makeText(
                                                    context,
                                                    "تم فتح طبقة تحديد ونسخ النص للصفحة ${pageIdx + 1} 🔤",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            onQuoteText = { snippet, pageIdx ->
                                                pdfCurrentPage = pageIdx
                                                activeQuoteDraft = snippet
                                                activeQuoteNote = ""
                                            }
                                        )

                                        SmartMagnifierAndLaserOverlay(
                                            pdfBitmap = pdfBitmap,
                                            isMagnifierLoupeActive = isMagnifierLoupeActive,
                                            isLaserPointerActive = isLaserPointerActive
                                        )
                                    }
                                } else {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        PdfPageView(
                                            bitmap = pdfBitmap,
                                            scale = pdfScale,
                                            offset = pdfOffset,
                                            rotationDegrees = pdfPageRotations[pdfCurrentPage] ?: 0f,
                                            colorFilterMode = effectivePdfColorMode,
                                            onTransform = { s, o ->
                                                val updatedScale = (pdfScale * s).coerceIn(0.85f, 4f)
                                                pdfScale = updatedScale
                                                documentZoomScale = updatedScale
                                                if (updatedScale > 1.02f) {
                                                    pdfOffset += o
                                                    documentPanOffset = pdfOffset
                                                } else {
                                                    pdfOffset = Offset.Zero
                                                    documentPanOffset = Offset.Zero
                                                }
                                            },
                                            onResetZoom = {
                                                pdfScale = 1f
                                                pdfOffset = Offset.Zero
                                                documentZoomScale = 1f
                                                documentPanOffset = Offset.Zero
                                            },
                                            onSingleTap = {
                                                if (!isPdfDrawingMode && !isPdfRedactionMode) {
                                                    isImmersiveFullscreen = !isImmersiveFullscreen
                                                }
                                            },
                                            onLongPress = {
                                                if (!isPdfDrawingMode && !isPdfRedactionMode) {
                                                    activeSelectablePdfPageDialog = pdfCurrentPage
                                                    Toast.makeText(
                                                        context,
                                                        "تم فتح طبقة تحديد ونسخ النص للصفحة ${pdfCurrentPage + 1} 🔤",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            },
                                            onSwipeNext = {
                                                if (!isPdfDrawingMode && !isPdfRedactionMode && pdfCurrentPage < pdfTotalPages - 1) {
                                                    pdfCurrentPage++
                                                    saveCurrentProgress()
                                                }
                                            },
                                            onSwipePrevious = {
                                                if (!isPdfDrawingMode && !isPdfRedactionMode && pdfCurrentPage > 0) {
                                                    pdfCurrentPage--
                                                    saveCurrentProgress()
                                                }
                                            }
                                        )

                                        // Freehand Ink Strokes & Electronic Stamps/Signatures Overlay
                                        PdfFreehandInkAndStampOverlay(
                                            pageIndex = pdfCurrentPage,
                                            isDrawingEnabled = isPdfDrawingMode,
                                            activeColorHex = activeInkColorHex,
                                            activeStrokeWidth = activeInkStrokeWidth,
                                            activeAlpha = activeInkAlpha,
                                            strokes = pdfInkStrokes,
                                            stamps = pdfStamps,
                                            onAddStroke = { newStroke ->
                                                val updated = pdfInkStrokes + newStroke
                                                pdfInkStrokes = updated
                                                MultiFormatDocumentEngine.savePdfInkStrokes(context, effectiveUriString, updated)
                                            },
                                            onRemoveStamp = { stampId ->
                                                val updated = pdfStamps.filter { it.id != stampId }
                                                pdfStamps = updated
                                                MultiFormatDocumentEngine.savePdfStamps(context, effectiveUriString, updated)
                                            }
                                        )

                                        // Round 3 Package 5: PDF Blackout Redaction Overlay
                                        PdfRedactionOverlay(
                                            currentPage = pdfCurrentPage,
                                            redactions = pdfRedactions,
                                            isRedactionModeActive = isPdfRedactionMode,
                                            onAddRedaction = { newRedaction ->
                                                val updated = pdfRedactions + newRedaction
                                                pdfRedactions = updated
                                                MultiFormatDocumentEngine.savePdfRedactions(context, effectiveUriString, updated)
                                                Toast.makeText(context, "تم طمس المنطقة المحددة بالأسود السري 🛡️", Toast.LENGTH_SHORT).show()
                                            }
                                        )

                                        // Round 3 Package 6: Smart 2.5x Magnifier Loupe & Presenter Laser Pointer Overlay
                                        SmartMagnifierAndLaserOverlay(
                                            pdfBitmap = pdfBitmap,
                                            isMagnifierLoupeActive = isMagnifierLoupeActive,
                                            isLaserPointerActive = isLaserPointerActive
                                        )
                                    }
                                }
                            }
                            "epub" -> {
                                val chapters = epubBook?.chapters.orEmpty()
                                if (chapters.isNotEmpty()) {
                                    SelectionContainer {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .multiTouchPinchToZoom(
                                                    currentScale = documentZoomScale,
                                                    currentPan = documentPanOffset,
                                                    onZoomAndPanChange = { newScale, newPan ->
                                                        documentZoomScale = newScale
                                                        documentPanOffset = newPan
                                                    }
                                                )
                                                .graphicsLayer(
                                                    scaleX = documentZoomScale,
                                                    scaleY = documentZoomScale,
                                                    translationX = documentPanOffset.x,
                                                    translationY = documentPanOffset.y
                                                )
                                        ) {
                                            if (isContinuousBrowserScrollMode) {
                                                // Browser-like continuous vertical scrolling across all EPUB chapters
                                                LazyColumn(
                                                    state = epubContinuousListState,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(horizontal = 18.dp),
                                                    contentPadding = PaddingValues(vertical = 14.dp),
                                                    verticalArrangement = Arrangement.spacedBy(18.dp)
                                                ) {
                                                    itemsIndexed(chapters, key = { idx, _ -> idx }) { chIdx, chapter ->
                                                        val chParagraphs = remember(chapter.textContent) {
                                                            chapter.textContent.split("\n\n").filter { it.isNotBlank() }
                                                        }
                                                        Card(
                                                            shape = RoundedCornerShape(14.dp),
                                                            colors = CardDefaults.cardColors(containerColor = readerTheme.surface),
                                                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Column(
                                                                modifier = Modifier.padding(16.dp),
                                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Text(
                                                                        text = "${chIdx + 1}. ${chapter.title}",
                                                                        fontSize = (fontSizeSp + 4).sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = theme.primaryColor,
                                                                        modifier = Modifier.weight(1f)
                                                                    )
                                                                    Text(
                                                                        text = "اضغط مطولاً لتحديد نص أو ✍️ للاقتباس",
                                                                        fontSize = 9.sp,
                                                                        color = Color.Gray
                                                                    )
                                                                }
                                                                HorizontalDivider(color = readerTheme.text.copy(alpha = 0.1f))
                                                                chParagraphs.forEach { paraText ->
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
                                                                            epubCurrentChapterIndex = chIdx
                                                                            activeQuoteDraft = paraText
                                                                            activeQuoteNote = matchingHighlight?.note ?: ""
                                                                            activeQuoteColorHex = matchingHighlight?.colorHex ?: "#FDE047"
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            } else {
                                                val currentChapter = chapters.getOrNull(epubCurrentChapterIndex) ?: chapters.first()
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
                                                            text = "اضغط مطولاً لتحديد نص أو ✍️ للاقتباس",
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
                                            }
                                        }
                                    }
                                } else {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("لا توجد فصول متوفرة في الكتاب", color = Color.Gray)
                                    }
                                }
                            }
                            "pptx", "ppt" -> {
                                val slides = pptxPresentation?.slides.orEmpty()
                                if (slides.isNotEmpty()) {
                                    SelectionContainer {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .multiTouchPinchToZoom(
                                                    currentScale = documentZoomScale,
                                                    currentPan = documentPanOffset,
                                                    onZoomAndPanChange = { newScale, newPan ->
                                                        documentZoomScale = newScale
                                                        documentPanOffset = newPan
                                                    }
                                                )
                                                .graphicsLayer(
                                                    scaleX = documentZoomScale,
                                                    scaleY = documentZoomScale,
                                                    translationX = documentPanOffset.x,
                                                    translationY = documentPanOffset.y
                                                )
                                        ) {
                                            if (isContinuousBrowserScrollMode) {
                                                // Smooth continuous vertical scrolling across all slides like a browser
                                                LazyColumn(
                                                    state = pptxContinuousListState,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(horizontal = 16.dp),
                                                    contentPadding = PaddingValues(vertical = 14.dp),
                                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                                ) {
                                                    itemsIndexed(slides, key = { idx, _ -> idx }) { slideIdx, slideItem ->
                                                        Card(
                                                            modifier = Modifier.fillMaxWidth(),
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
                                                                            text = "شريحة ${slideItem.slideNumber} / ${slides.size}",
                                                                            fontSize = 11.sp,
                                                                            color = Color.White,
                                                                            fontWeight = FontWeight.Bold,
                                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                                        )
                                                                    }

                                                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                        Surface(
                                                                            onClick = {
                                                                                copyTextToClipboardHelper(
                                                                                    context,
                                                                                    "${slideItem.title}\n${slideItem.textContent}"
                                                                                )
                                                                            },
                                                                            shape = RoundedCornerShape(6.dp),
                                                                            color = Color(0xFF0284C7).copy(alpha = 0.12f)
                                                                        ) {
                                                                            Text(
                                                                                text = "📋 نسخ",
                                                                                fontSize = 9.sp,
                                                                                fontWeight = FontWeight.Bold,
                                                                                color = Color(0xFF0284C7),
                                                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                                            )
                                                                        }
                                                                        Surface(
                                                                            onClick = {
                                                                                pptxCurrentSlideIndex = slideIdx
                                                                                activeQuoteDraft = "${slideItem.title}\n${slideItem.textContent}"
                                                                                activeQuoteNote = ""
                                                                            },
                                                                            shape = RoundedCornerShape(6.dp),
                                                                            color = theme.primaryColor.copy(alpha = 0.12f)
                                                                        ) {
                                                                            Text(
                                                                                text = "✍️ اقتباس للمحرر",
                                                                                fontSize = 9.sp,
                                                                                fontWeight = FontWeight.Bold,
                                                                                color = theme.primaryColor,
                                                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                                            )
                                                                        }
                                                                    }
                                                                }

                                                                Text(
                                                                    text = slideItem.title,
                                                                    fontSize = (fontSizeSp + 4).sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = readerTheme.text
                                                                )

                                                                HorizontalDivider(color = readerTheme.text.copy(alpha = 0.1f))

                                                                Text(
                                                                    text = buildHighlightedAnnotatedString(slideItem.textContent, searchQuery),
                                                                    fontSize = fontSizeSp.sp,
                                                                    lineHeight = (fontSizeSp * lineSpacing).sp,
                                                                    fontWeight = fontWeight,
                                                                    color = readerTheme.text
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            } else {
                                                val slide = slides.getOrNull(pptxCurrentSlideIndex) ?: slides.first()
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .verticalScroll(rememberScrollState())
                                                        .padding(16.dp),
                                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    Card(
                                                        modifier = Modifier.fillMaxWidth(),
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

                                                                Surface(
                                                                    onClick = {
                                                                        activeQuoteDraft = "${slide.title}\n${slide.textContent}"
                                                                        activeQuoteNote = ""
                                                                    },
                                                                    shape = RoundedCornerShape(6.dp),
                                                                    color = theme.primaryColor.copy(alpha = 0.12f)
                                                                ) {
                                                                    Text(
                                                                        text = "✍️ اقتباس الشريحة للمحرر",
                                                                        fontSize = 9.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = theme.primaryColor,
                                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                                    )
                                                                }
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
                                            }
                                        }
                                    }

                                    // Presenter Laser Pointer Overlay on PPTX Slides
                                    SmartMagnifierAndLaserOverlay(
                                        pdfBitmap = null,
                                        isMagnifierLoupeActive = false,
                                        isLaserPointerActive = isLaserPointerActive
                                    )
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
                                        fileName = effectiveFileName,
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
                                                        baseName = effectiveFileName,
                                                        headers = data.headers,
                                                        rows = data.rows,
                                                        suffix = suffix
                                                    )
                                                }
                                                pendingFileToSave = outFile
                                                saveToDeviceLauncher.launch(outFile.name)
                                            }
                                        },
                                        onOpenChartsStudio = { showChartsAndStatsSheet = true }
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
                                                            DocmoDocumentBridge.createAccessBackupCopy(context, uri, effectiveFileName)
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
                                // DOCX / TXT / HTML / RTF / MD Text View with Continuous Smooth Scroll, 2-Finger Pinch-to-Zoom & Long-Press Text Selection
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .multiTouchPinchToZoom(
                                            currentScale = documentZoomScale,
                                            currentPan = documentPanOffset,
                                            onZoomAndPanChange = { newScale, newPan ->
                                                documentZoomScale = newScale
                                                documentPanOffset = newPan
                                            }
                                        )
                                        .graphicsLayer(
                                            scaleX = documentZoomScale,
                                            scaleY = documentZoomScale,
                                            translationX = documentPanOffset.x,
                                            translationY = documentPanOffset.y
                                        )
                                ) {
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
                                                    text = "💡 اضغط مطولاً لتحديد ونسخ أي نص، أو استخدم أزرار الفقرة للتظليل والاقتباس",
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
                                                                    baseName = effectiveFileName,
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
                                        } else {
                                            SelectionContainer {
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    if (docxParagraphs.isNotEmpty()) {
                                                        docxParagraphs.forEach { para ->
                                                            val matchingHighlight = highlights.firstOrNull {
                                                                it.snippetText.trim() == para.text.trim()
                                                            }
                                                            if (para.isHeading) {
                                                                Row(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .padding(vertical = 6.dp),
                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Text(
                                                                        text = buildHighlightedAnnotatedString(para.text, searchQuery),
                                                                        fontSize = (fontSizeSp + 4).sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = theme.primaryColor,
                                                                        modifier = Modifier.weight(1f)
                                                                    )
                                                                    IconButton(
                                                                        onClick = {
                                                                            activeQuoteDraft = para.text
                                                                            activeQuoteNote = matchingHighlight?.note ?: ""
                                                                        },
                                                                        modifier = Modifier.size(24.dp)
                                                                    ) {
                                                                        Icon(
                                                                            Icons.Default.FormatQuote,
                                                                            contentDescription = "اقتباس العنوان",
                                                                            tint = theme.primaryColor,
                                                                            modifier = Modifier.size(14.dp)
                                                                        )
                                                                    }
                                                                }
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
                                }
                            }
                        }

                        // Floating Pinch-to-Zoom Control Pill when zoomed in/out
                        val currentEffectiveZoom = if (extension == "pdf" && !isContinuousBrowserScrollMode) pdfScale else documentZoomScale
                        if (kotlin.math.abs(currentEffectiveZoom - 1f) > 0.04f) {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = Color(0xFF0F172A).copy(alpha = 0.85f),
                                shadowElevation = 6.dp,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "➖",
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        modifier = Modifier.clickable {
                                            val next = (currentEffectiveZoom - 0.25f).coerceIn(0.85f, 4f)
                                            documentZoomScale = next
                                            pdfScale = next
                                            if (kotlin.math.abs(next - 1f) < 0.08f) {
                                                documentZoomScale = 1f
                                                pdfScale = 1f
                                                documentPanOffset = Offset.Zero
                                                pdfOffset = Offset.Zero
                                            }
                                        }
                                    )
                                    Text(
                                        text = "🔍 ${(currentEffectiveZoom * 100).toInt()}%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8)
                                    )
                                    Text(
                                        text = "➕",
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        modifier = Modifier.clickable {
                                            val next = (currentEffectiveZoom + 0.25f).coerceIn(0.85f, 4f)
                                            documentZoomScale = next
                                            pdfScale = next
                                        }
                                    )
                                    Surface(
                                        onClick = {
                                            documentZoomScale = 1f
                                            pdfScale = 1f
                                            documentPanOffset = Offset.Zero
                                            pdfOffset = Offset.Zero
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFEA580C)
                                    ) {
                                        Text(
                                            text = "إعادة ضبط 100%",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Package 3: Split-Screen Dual Document & Live Quick Scratchpad Pane
                if (isSplitScreenActive) {
                    SplitScreenReferenceAndScratchpadPane(
                        primaryUriString = effectiveUriString,
                        primaryFileName = effectiveFileName,
                        currentPositionLabel = currentPositionLabel,
                        theme = theme,
                        readerTheme = readerTheme,
                        onSendDraftToEditor = { draftText ->
                            viewModel.insertQuoteIntoActiveChapter(
                                quoteText = draftText,
                                sourceFileName = effectiveFileName,
                                positionLabel = currentPositionLabel,
                                note = "مسودة مكتوبة أثناء قراءة المرجع"
                            )
                        },
                        onCloseSplitScreen = { isSplitScreenActive = false },
                        modifier = Modifier
                            .weight(0.85f)
                            .fillMaxWidth()
                    )
                }
            }

            // Package 6: Draggable Reading Focus Ruler / Line Mask Overlay
            if (showFocusRuler) {
                ReadingFocusRulerOverlay(
                    rulerYFraction = focusRulerYFraction,
                    primaryColor = theme.primaryColor,
                    onUpdateYFraction = { focusRulerYFraction = it },
                    onCloseRuler = { showFocusRuler = false }
                )
            }

            // Round 4 Package 8: Precision Alignment & Typographic Measurement Grid Overlay
            if (showAlignmentGridOverlay) {
                PrecisionAlignmentGridOverlay(
                    primaryColor = theme.primaryColor,
                    onCloseGrid = { showAlignmentGridOverlay = false }
                )
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
                                    sourceFileName = effectiveFileName,
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
                                uriString = effectiveUriString,
                                snippetText = snippet,
                                note = activeQuoteNote.trim(),
                                colorHex = activeQuoteColorHex,
                                positionIndex = currentPositionIndex,
                                positionLabel = currentPositionLabel
                            )
                            val updated = highlights.filter { it.snippetText.trim() != snippet } + newHl
                            highlights = updated
                            MultiFormatDocumentEngine.saveDocumentHighlights(context, effectiveUriString, updated)
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
                                                            sourceFileName = effectiveFileName,
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
                                                        MultiFormatDocumentEngine.saveDocumentHighlights(context, effectiveUriString, updated)
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
                                        MultiFormatDocumentEngine.saveDocumentBookmarks(context, effectiveUriString, updated)
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
                                    fileName = effectiveFileName,
                                    bookmarks = bookmarks,
                                    highlights = highlights
                                )
                                viewModel.insertAnnotationsAsNewChapter(
                                    chapterTitle = "ملاحظات واقتباسات: $effectiveFileName",
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
                                    fileName = effectiveFileName,
                                    bookmarks = bookmarks,
                                    highlights = highlights
                                )
                                val outDir = File(context.cacheDir, "conversions").apply { mkdirs() }
                                val outFile = File(outDir, "ملخص_ملاحظات_${effectiveFileName.substringBeforeLast(".")}.txt")
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
                                    sourceFileName = effectiveFileName,
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
            initialDocName = effectiveFileName,
            onOpenConvertedDocument = { convertedUri, convertedName ->
                showConverterStudioDialog = false
                activeUriOverride = convertedUri
                activeFileNameOverride = convertedName
            },
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

    // Pro Workstation Package 1 Dialog: PDF Stamps & Electronic Signature
    if (showPdfStampDialog) {
        PdfStampAndSignatureDialog(
            pageIndex = pdfCurrentPage,
            onAddStamp = { newStamp ->
                val updated = pdfStamps + newStamp
                pdfStamps = updated
                MultiFormatDocumentEngine.savePdfStamps(context, effectiveUriString, updated)
                Toast.makeText(context, "تم إدراج الختم/التوقيع على الصفحة ${pdfCurrentPage + 1} ✍️", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showPdfStampDialog = false }
        )
    }

    // Pro Workstation Package 2 Sheet: Smart Research Assistant, Summarization & Instant Translation
    if (showSmartResearchSheet) {
        SmartResearchAndTranslationSheet(
            fileName = effectiveFileName,
            currentPositionLabel = currentPositionLabel,
            activeReadableText = currentReadableText,
            theme = theme,
            readerTheme = readerTheme,
            onInsertIntoEditor = { quoteText, note ->
                viewModel.insertQuoteIntoActiveChapter(
                    quoteText = quoteText,
                    sourceFileName = effectiveFileName,
                    positionLabel = currentPositionLabel,
                    note = note
                )
            },
            onAddFlashcards = { newCards ->
                val updated = flashcards + newCards
                flashcards = updated
                MultiFormatDocumentEngine.saveDocumentFlashcards(context, effectiveUriString, updated)
            },
            onDismiss = { showSmartResearchSheet = false }
        )
    }

    // Pro Workstation Package 4 Sheet: Academic Citation Generator & Flashcards / Glossary Builder
    if (showFlashcardsAndCitationSheet) {
        FlashcardsAndCitationGeneratorSheet(
            uriString = effectiveUriString,
            fileName = effectiveFileName,
            currentPositionLabel = currentPositionLabel,
            highlights = highlights,
            flashcards = flashcards,
            theme = theme,
            readerTheme = readerTheme,
            onUpdateFlashcards = { updated ->
                flashcards = updated
                MultiFormatDocumentEngine.saveDocumentFlashcards(context, effectiveUriString, updated)
            },
            onInsertCitationIntoEditor = { citationText ->
                viewModel.insertQuoteIntoActiveChapter(
                    quoteText = citationText,
                    sourceFileName = effectiveFileName,
                    positionLabel = currentPositionLabel,
                    note = "توثيق مرجعي أكاديمي"
                )
            },
            onDismiss = { showFlashcardsAndCitationSheet = false }
        )
    }

    // Pro Workstation Package 5 Dialog: PDF Power Tools (Rotation, Deletion, Watermark, Merge & Live OCR)
    if (showPdfPowerToolsDialog && extension == "pdf") {
        PdfPowerToolsAndOcrDialog(
            uri = uri,
            fileName = effectiveFileName,
            currentPageIndex = pdfCurrentPage,
            totalPages = pdfTotalPages,
            currentRotation = pdfPageRotations[pdfCurrentPage] ?: 0f,
            isCurrentPageDeleted = pdfDeletedPages.contains(pdfCurrentPage),
            extractedTextForOcr = pdfExtractedFullText,
            strokes = pdfInkStrokes,
            stamps = pdfStamps,
            pageRotations = pdfPageRotations,
            deletedPages = pdfDeletedPages,
            theme = theme,
            onRotateCurrentPage = { newRot ->
                pdfPageRotations = pdfPageRotations.toMutableMap().apply { put(pdfCurrentPage, newRot) }
                Toast.makeText(context, "تم تدوير الصفحة ${pdfCurrentPage + 1} (${newRot.toInt()}°)", Toast.LENGTH_SHORT).show()
            },
            onToggleDeleteCurrentPage = {
                pdfDeletedPages = if (pdfDeletedPages.contains(pdfCurrentPage)) {
                    pdfDeletedPages - pdfCurrentPage
                } else {
                    pdfDeletedPages + pdfCurrentPage
                }
            },
            onSendOcrTextToEditor = { ocrText ->
                viewModel.insertQuoteIntoActiveChapter(
                    quoteText = ocrText,
                    sourceFileName = effectiveFileName,
                    positionLabel = "صفحة ${pdfCurrentPage + 1} (ماسح OCR)",
                    note = "نص مستخرج بالماسح الضوئي OCR"
                )
            },
            onExportFileReady = { exportedFile ->
                pendingFileToSave = exportedFile
                Toast.makeText(context, "تم تجهيز الملف (${exportedFile.name}) بنجاح! اختر مكان الحفظ", Toast.LENGTH_LONG).show()
                saveToDeviceLauncher.launch(exportedFile.name)
            },
            onDismiss = { showPdfPowerToolsDialog = false }
        )
    }

    // Pro Workstation Package 6 Dialog: Pomodoro Timer, Focus Ruler & Offline Synthesized Ambient Focus Audio
    if (showFocusStudioDialog) {
        val totalWords = remember(currentReadableText) {
            currentReadableText.split(Regex("\\s+")).count { it.isNotBlank() }
        }
        FocusPomodoroAndAmbientDialog(
            isPomodoroRunning = isPomodoroRunning,
            pomodoroRemainingSeconds = pomodoroRemainingSeconds,
            activeAmbientSoundMode = activeAmbientSoundMode,
            showFocusRuler = showFocusRuler,
            readingSpeedWpm = readingSpeedWpm,
            totalWordCount = totalWords,
            theme = theme,
            onTogglePomodoro = { isPomodoroRunning = !isPomodoroRunning },
            onResetPomodoro = { minutes ->
                isPomodoroRunning = false
                pomodoroRemainingSeconds = minutes * 60
            },
            onSelectAmbientSound = { mode ->
                activeAmbientSoundMode = mode
                MultiFormatDocumentEngine.startAmbientFocusSound(mode)
            },
            onToggleFocusRuler = { showFocusRuler = it },
            onUpdateWpm = { readingSpeedWpm = it },
            onDismiss = { showFocusStudioDialog = false }
        )
    }

    // =========================================================================
    // ROUND 3: NEXT-GEN COGNITIVE & PUBLISHING SUITE SHEETS & DIALOGS
    // =========================================================================

    // 1. Interactive Document Mind Map Sheet
    if (showMindMapSheet) {
        InteractiveMindMapSheet(
            documentName = effectiveFileName,
            outlineItems = documentOutline,
            highlights = highlights,
            plainText = currentReadableText,
            customNodes = customMindMapNodes,
            onAddCustomNode = { title, subtitle, category ->
                val newNode = DocumentMindMapNode(
                    title = title,
                    subtitle = subtitle,
                    category = category,
                    positionIndex = currentPositionIndex,
                    parentId = "root_node",
                    colorHex = "#10B981"
                )
                val updated = customMindMapNodes + newNode
                customMindMapNodes = updated
                MultiFormatDocumentEngine.saveCustomMindMapNodes(context, effectiveUriString, updated)
            },
            onDeleteCustomNode = { nodeId ->
                val updated = customMindMapNodes.filter { it.id != nodeId }
                customMindMapNodes = updated
                MultiFormatDocumentEngine.saveCustomMindMapNodes(context, effectiveUriString, updated)
            },
            onJumpToPosition = { posIdx ->
                when (extension) {
                    "pdf" -> pdfCurrentPage = posIdx.coerceIn(0, (pdfTotalPages - 1).coerceAtLeast(0))
                    "epub" -> epubCurrentChapterIndex = posIdx.coerceIn(0, ((epubBook?.chapters?.size ?: 1) - 1).coerceAtLeast(0))
                    "pptx", "ppt" -> pptxCurrentSlideIndex = posIdx.coerceIn(0, ((pptxPresentation?.slides?.size ?: 1) - 1).coerceAtLeast(0))
                    else -> coroutineScope.launch { textScrollState.scrollTo(posIdx.coerceAtLeast(0)) }
                }
            },
            onExportMindMapToEditor = { markdownOutline ->
                viewModel.insertAnnotationsAsNewChapter(
                    chapterTitle = "خريطة ذهنية: ${effectiveFileName.substringBeforeLast(".")}",
                    summaryText = markdownOutline
                )
                Toast.makeText(context, "تم تصدير الخريطة الذهنية كفصل مهيكل في محرر الكتابة 🕸️", Toast.LENGTH_LONG).show()
            },
            onDismiss = { showMindMapSheet = false }
        )
    }

    // 2. RSVP Speed Reader Dialog (200 - 900 WPM)
    if (showRsvpSpeedReaderDialog) {
        RsvpSpeedReaderDialog(
            documentTitle = effectiveFileName,
            plainText = currentReadableText,
            onDismiss = { showRsvpSpeedReaderDialog = false }
        )
    }

    // 3. Ask Document Q&A & Originality / Plagiarism Overlap Checker Sheet
    if (showAskDocumentAndOriginalitySheet) {
        AskDocumentAndOriginalitySheet(
            documentName = effectiveFileName,
            plainText = currentReadableText,
            outlineItems = documentOutline,
            activeEditorChapterTitle = currentEditorChapter?.title ?: "",
            activeEditorChapterContent = currentEditorChapter?.content ?: "",
            onSendSnippetToEditor = { snippet ->
                viewModel.insertQuoteIntoActiveChapter(
                    quoteText = snippet,
                    sourceFileName = effectiveFileName,
                    positionLabel = currentPositionLabel,
                    note = "إجابة مستخرجة عبر مُحاور المستند الذكي"
                )
                Toast.makeText(context, "تم إرسال الإجابة الموثقة إلى محرر الكتابة ✍️", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showAskDocumentAndOriginalitySheet = false }
        )
    }

    // 4. Interactive Charts & Statistical Data Visualizer Sheet
    if (showChartsAndStatsSheet) {
        SpreadsheetChartsAndStatsSheet(
            documentName = effectiveFileName,
            spreadsheet = spreadsheetData,
            plainText = currentReadableText,
            onInsertStatsReportToEditor = { statsReport ->
                viewModel.insertQuoteIntoActiveChapter(
                    quoteText = statsReport,
                    sourceFileName = effectiveFileName,
                    positionLabel = currentPositionLabel,
                    note = "تقرير إحصائي ومخطط بيانات"
                )
                Toast.makeText(context, "تم إدراج التقرير الإحصائي في مسودة محرر الكتابة 📊", Toast.LENGTH_LONG).show()
            },
            onDismiss = { showChartsAndStatsSheet = false }
        )
    }

    // 5. PDF Blackout Redaction & Digital Forensics Sheet
    if (showRedactionAndForensicsSheet) {
        PdfRedactionAndForensicsSheet(
            documentName = effectiveFileName,
            documentUri = uri,
            plainText = currentReadableText,
            isPdfDocument = extension == "pdf",
            isRedactionDrawingMode = isPdfRedactionMode,
            onToggleRedactionDrawingMode = { enabled ->
                isPdfRedactionMode = enabled
                if (enabled) {
                    isPdfDrawingMode = false
                    isMagnifierLoupeActive = false
                    isLaserPointerActive = false
                    Toast.makeText(context, "وضع الطمس السري مفعّل: اسحب بإصبعك فوق النص لطمسه بالأسود 🛡️", Toast.LENGTH_LONG).show()
                }
            },
            redactions = pdfRedactions,
            onClearRedactions = {
                pdfRedactions = emptyList()
                MultiFormatDocumentEngine.savePdfRedactions(context, effectiveUriString, emptyList())
                Toast.makeText(context, "تم مسح جميع مناطق الطمس", Toast.LENGTH_SHORT).show()
            },
            onExportSanitizedPdf = {
                coroutineScope.launch {
                    val outFile = withContext(Dispatchers.IO) {
                        MultiFormatDocumentEngine.exportAnnotatedAndModifiedPdf(
                            context = context,
                            sourceUri = uri,
                            sourceFileName = effectiveFileName,
                            strokes = pdfInkStrokes,
                            stamps = pdfStamps,
                            pageRotations = pdfPageRotations,
                            deletedPages = pdfDeletedPages,
                            redactions = pdfRedactions
                        )
                    }
                    if (outFile != null) {
                        pendingFileToSave = outFile
                        Toast.makeText(context, "تم دمج الطمس السري وتجهيز ملف PDF الآمن!", Toast.LENGTH_LONG).show()
                        saveToDeviceLauncher.launch(outFile.name)
                    }
                }
            },
            onDismiss = { showRedactionAndForensicsSheet = false }
        )
    }

    // =========================================================================
    // ROUND 4: PRO DOCUMENT & RESEARCH WORKSTATION SHEETS & DIALOGS
    // =========================================================================

    // 1. PDF Visual Page Organizer & Splitter Sheet
    if (showPdfVisualOrganizerSheet && extension == "pdf") {
        PdfVisualPageOrganizerSheet(
            documentUri = uri,
            documentName = effectiveFileName,
            totalPages = pdfTotalPages,
            currentPageIndex = pdfCurrentPage,
            initialRotations = pdfPageRotations,
            initialDeletedPages = pdfDeletedPages,
            onUpdateRotationsAndDeleted = { updatedRots, updatedDel ->
                pdfPageRotations = updatedRots
                pdfDeletedPages = updatedDel
            },
            onJumpToPage = { targetPage ->
                pdfCurrentPage = targetPage.coerceIn(0, (pdfTotalPages - 1).coerceAtLeast(0))
            },
            onExportOrganizedPdfReady = { exportedFile ->
                pendingFileToSave = exportedFile
                Toast.makeText(context, "تم تجهيز ملف PDF المنظم (${exportedFile.name}) بنجاح!", Toast.LENGTH_LONG).show()
                saveToDeviceLauncher.launch(exportedFile.name)
            },
            onDismiss = { showPdfVisualOrganizerSheet = false }
        )
    }

    // 2. Arabic Linguistics, Auto-Tashkeel & Poetry Prosody Studio Sheet
    if (showArabicLinguisticsSheet) {
        ArabicLinguisticsAndProsodySheet(
            documentName = effectiveFileName,
            plainText = currentReadableText,
            onSendProcessedTextToEditor = { processedText, noteLabel ->
                viewModel.insertQuoteIntoActiveChapter(
                    quoteText = processedText,
                    sourceFileName = effectiveFileName,
                    positionLabel = currentPositionLabel,
                    note = noteLabel
                )
                Toast.makeText(context, "تم إرسال النص المعالج إلى محرر الكتابة ✍️", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showArabicLinguisticsSheet = false }
        )
    }

    // 3. Interactive Timeline & Entity Extractor Sheet
    if (showTimelineAndEntitiesSheet) {
        InteractiveTimelineAndEntitiesSheet(
            documentName = effectiveFileName,
            plainText = currentReadableText,
            onJumpToPosition = { posIdx ->
                when (extension) {
                    "pdf" -> pdfCurrentPage = posIdx.coerceIn(0, (pdfTotalPages - 1).coerceAtLeast(0))
                    "epub" -> epubCurrentChapterIndex = posIdx.coerceIn(0, ((epubBook?.chapters?.size ?: 1) - 1).coerceAtLeast(0))
                    "pptx", "ppt" -> pptxCurrentSlideIndex = posIdx.coerceIn(0, ((pptxPresentation?.slides?.size ?: 1) - 1).coerceAtLeast(0))
                    else -> coroutineScope.launch {
                        val approxScroll = (posIdx * 180).coerceIn(0, textScrollState.maxValue.coerceAtLeast(0))
                        textScrollState.scrollTo(approxScroll)
                    }
                }
            },
            onSendToEditor = { snippet, note ->
                viewModel.insertQuoteIntoActiveChapter(
                    quoteText = snippet,
                    sourceFileName = effectiveFileName,
                    positionLabel = currentPositionLabel,
                    note = note
                )
                Toast.makeText(context, "تم إرسال الحدث/العَلَم الموثق إلى محرر الكتابة ⏳", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showTimelineAndEntitiesSheet = false }
        )
    }

    // 4. Interactive 3D Flip Flashcards & Auto MCQ Self-Test Quiz Sheet
    if (showQuizAnd3DFlashcardsSheet) {
        InteractiveQuizAnd3DFlashcardsSheet(
            documentName = effectiveFileName,
            plainText = currentReadableText,
            flashcards = flashcards,
            onUpdateFlashcards = { updated ->
                flashcards = updated
                MultiFormatDocumentEngine.saveDocumentFlashcards(context, effectiveUriString, updated)
            },
            onDismiss = { showQuizAnd3DFlashcardsSheet = false }
        )
    }

    // 5. Official Digital Stamp & SHA-256 Verification Certificate Sheet
    if (showOfficialStampAndCertSheet) {
        OfficialDigitalStampAndCertificateSheet(
            documentUri = uri,
            documentName = effectiveFileName,
            isPdfDocument = extension == "pdf",
            plainText = currentReadableText,
            onAddStampToCurrentPdfPage = { labelText, colorHex ->
                val newStamp = PdfStampItem(
                    pageIndex = pdfCurrentPage,
                    label = labelText,
                    xNorm = 0.25f,
                    yNorm = 0.85f,
                    colorHex = colorHex,
                    isSignature = true
                )
                val updated = pdfStamps + newStamp
                pdfStamps = updated
                MultiFormatDocumentEngine.savePdfStamps(context, effectiveUriString, updated)
            },
            onExportCertifiedPdfReady = { exportedFile ->
                pendingFileToSave = exportedFile
                Toast.makeText(context, "تم إصدار ملف PDF الموثق بالشهادة والختم الرسمي!", Toast.LENGTH_LONG).show()
                saveToDeviceLauncher.launch(exportedFile.name)
            },
            onDismiss = { showOfficialStampAndCertSheet = false }
        )
    }

    // 6. Page-Pinned Voice & Audio Notes Sheet
    if (showVoiceNotesSheet) {
        PagePinnedVoiceNotesSheet(
            uriString = effectiveUriString,
            documentName = effectiveFileName,
            currentPositionIndex = currentPositionIndex,
            currentPositionLabel = currentPositionLabel,
            voiceNotes = pageVoiceNotes,
            onUpdateVoiceNotes = { updated ->
                pageVoiceNotes = updated
                MultiFormatDocumentEngine.savePageVoiceNotes(context, effectiveUriString, updated)
            },
            onReadNoteAloud = { textToSpeak ->
                ttsManager.prepareText(textToSpeak)
                ttsManager.play()
                showTtsBar = true
            },
            onSendNoteToEditor = { transcript, label ->
                viewModel.insertQuoteIntoActiveChapter(
                    quoteText = transcript,
                    sourceFileName = effectiveFileName,
                    positionLabel = currentPositionLabel,
                    note = label
                )
                Toast.makeText(context, "تم إرسال الملاحظة الصوتية إلى محرر الكتابة 🎙️", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showVoiceNotesSheet = false }
        )
    }

    // 7. Back-of-Book Automated Alphabetical Indexer Sheet
    if (showBackOfBookIndexSheet) {
        BackOfBookIndexSheet(
            documentName = effectiveFileName,
            plainText = currentReadableText,
            outlineItems = documentOutline,
            onExportIndexToEditor = { markdownIndex ->
                viewModel.insertAnnotationsAsNewChapter(
                    chapterTitle = "الكشاف الأبجدي: ${effectiveFileName.substringBeforeLast(".")}",
                    summaryText = markdownIndex
                )
                Toast.makeText(context, "تم إدراج الكشاف الأبجدي كفصل/ملحق في محرر الكتابة 📚", Toast.LENGTH_LONG).show()
            },
            onDismiss = { showBackOfBookIndexSheet = false }
        )
    }

    // 8. Smart Save, Local Vault, Version History & .abdoc Bundle Center Sheet
    if (showSaveAndVaultCenterSheet) {
        DocumentSaveAndVaultCenterSheet(
            theme = theme,
            currentDocumentUri = uri,
            currentDocumentName = effectiveFileName,
            currentDocumentExtension = extension,
            readableTextContent = currentReadableText,
            highlights = highlights,
            bookmarks = bookmarks,
            inkStrokes = pdfInkStrokes,
            stamps = pdfStamps,
            redactions = pdfRedactions,
            flashcards = flashcards,
            mindMapNodes = customMindMapNodes,
            voiceNotes = pageVoiceNotes,
            pageRotations = pdfPageRotations,
            deletedPages = pdfDeletedPages,
            initialTab = saveAndVaultInitialTab,
            onRestoreWorkspaceState = { restored ->
                highlights = restored.highlights
                bookmarks = restored.bookmarks
                pdfInkStrokes = restored.inkStrokes
                pdfStamps = restored.stamps
                flashcards = restored.flashcards
                customMindMapNodes = restored.mindMapNodes
                pdfRedactions = restored.redactions
                pageVoiceNotes = restored.voiceNotes
                if (!restored.editedTextContent.isNullOrBlank()) {
                    textDocumentContent = restored.editedTextContent
                }
                liveSaveState = LiveSaveState.SAVED
                lastAutoSavedTimeText = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            },
            onOpenDocumentFromVaultOrBundle = { newUriStr, newDocName ->
                activeUriOverride = newUriStr
                activeFileNameOverride = newDocName
            },
            onDismiss = {
                showSaveAndVaultCenterSheet = false
                isDocumentInVault = DocumentVaultAndSaveManager.isDocumentInVault(context, effectiveUriString) != null
                snapshotsCount = DocumentVaultAndSaveManager.getDocumentSnapshots(context, effectiveUriString).size
            }
        )
    }

    // 9. Smart Text Extraction & OCR Studio Dialog (All 6 Upgrades + Direct Pipeline)
    if (showSmartExtractionStudio) {
        SmartTextExtractionStudioDialog(
            theme = theme,
            initialUri = uri,
            initialFileName = effectiveFileName,
            initialPageIndex = currentPositionIndex.coerceAtLeast(0),
            initialTotalPages = pdfTotalPages.coerceAtLeast(1),
            initialExtractedText = currentReadableText,
            bookmarks = bookmarks,
            onSendToEditor = { text, noteLabel ->
                viewModel.insertQuoteIntoActiveChapter(
                    quoteText = text,
                    sourceFileName = effectiveFileName,
                    positionLabel = currentPositionLabel,
                    note = noteLabel
                )
            },
            onAddFlashcards = { newCards ->
                val updated = flashcards + newCards
                flashcards = updated
                MultiFormatDocumentEngine.saveDocumentFlashcards(context, effectiveUriString, updated)
            },
            onAddMindMapNodes = { newNodes ->
                val updated = customMindMapNodes + newNodes
                customMindMapNodes = updated
                MultiFormatDocumentEngine.saveCustomMindMapNodes(context, effectiveUriString, updated)
            },
            onAddHighlightNote = { newHighlight ->
                val updated = highlights + newHighlight
                highlights = updated
                MultiFormatDocumentEngine.saveDocumentHighlights(context, effectiveUriString, updated)
            },
            onSpeakTextAloud = { textToSpeak ->
                ttsManager.prepareText(textToSpeak)
                ttsManager.play()
                showTtsBar = true
            },
            onSaveExtractedRecord = { title, content ->
                viewModel.saveCustomExtractedRecord(title, content)
            },
            onDismiss = { showSmartExtractionStudio = false }
        )
    }

    // Long-Press Selectable PDF Page Text Modal (الضغط مطولاً لتحديد نص معين ونسخه من أي صفحة PDF)
    if (activeSelectablePdfPageDialog != null) {
        val targetPageIdx = activeSelectablePdfPageDialog!!
        val selectablePageText = remember(pdfExtractedFullText, targetPageIdx, pdfTotalPages, effectiveFileName) {
            MultiFormatDocumentEngine.getPdfPageSelectableText(
                fullPdfText = pdfExtractedFullText,
                pageIndex = targetPageIdx,
                totalPages = pdfTotalPages,
                fileName = effectiveFileName
            )
        }
        AlertDialog(
            onDismissRequest = { activeSelectablePdfPageDialog = null },
            title = {
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
                            Icons.Default.TextFields,
                            contentDescription = null,
                            tint = theme.primaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "تحديد ونسخ نص الصفحة ${targetPageIdx + 1}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = theme.primaryColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "تحديد حر بالضغط المطول",
                            fontSize = 10.sp,
                            color = theme.primaryColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "💡 اضغط مطولاً بإصبعك على أي كلمة أو سطر بالأسفل لتحريك مقابض التحديد ونسخ الجزء المطلوب بدقة:",
                        fontSize = 11.sp,
                        color = Color(0xFF475569)
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = readerTheme.bg,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            theme.primaryColor.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 360.dp)
                    ) {
                        SelectionContainer {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = buildHighlightedAnnotatedString(selectablePageText, searchQuery),
                                    fontSize = fontSizeSp.sp,
                                    lineHeight = (fontSizeSp * lineSpacing).sp,
                                    color = readerTheme.text
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                copyTextToClipboardHelper(context, selectablePageText)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("نسخ كامل الصفحة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        FilledTonalButton(
                            onClick = {
                                activeQuoteDraft = selectablePageText
                                activeQuoteNote = ""
                                activeSelectablePdfPageDialog = null
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.FormatQuote, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تظليل واقتباس", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeSelectablePdfPageDialog = null }) {
                    Text("إغلاق", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

internal fun copyTextToClipboardHelper(context: android.content.Context, text: String) {
    try {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("نص مستند منسوخ", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "تم نسخ النص إلى الحافظة بنجاح 📋", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
    }
}

/**
 * Multi-Touch 2-Finger Pinch-to-Zoom & Pan Modifier that lets 1-finger vertical scrolling
 * and 1-finger long-press text selection pass through cleanly without conflict.
 */
private fun Modifier.multiTouchPinchToZoom(
    currentScale: Float,
    currentPan: Offset,
    onZoomAndPanChange: (Float, Offset) -> Unit
): Modifier = this.pointerInput(currentScale, currentPan) {
    awaitEachGesture {
        var scale = currentScale
        var pan = currentPan
        do {
            val event = awaitPointerEvent()
            val pressedCount = event.changes.count { it.pressed }
            if (pressedCount >= 2) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                if (zoomChange != 1f || panChange != Offset.Zero) {
                    scale = (scale * zoomChange).coerceIn(0.85f, 4.0f)
                    pan = if (scale > 1.02f) {
                        pan + panChange
                    } else {
                        Offset.Zero
                    }
                    onZoomAndPanChange(scale, pan)
                    event.changes.forEach { change ->
                        if (change.position != change.previousPosition) {
                            change.consume()
                        }
                    }
                }
            } else if (pressedCount == 1 && scale > 1.08f) {
                // When zoomed in, allow horizontal panning while keeping vertical scrolling smooth
                val panChange = event.calculatePan()
                if (kotlin.math.abs(panChange.x) > kotlin.math.abs(panChange.y) * 1.25f && kotlin.math.abs(panChange.x) > 2f) {
                    pan = Offset(pan.x + panChange.x, pan.y + panChange.y * 0.35f)
                    onZoomAndPanChange(scale, pan)
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

@Composable
private fun ContinuousBrowserPdfViewer(
    uri: Uri,
    fileName: String,
    totalPages: Int,
    currentPage: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    zoomScale: Float,
    panOffset: Offset,
    pageRotations: Map<Int, Float>,
    deletedPages: Set<Int>,
    colorFilterMode: String,
    isAutoCropMargins: Boolean,
    fullExtractedPdfText: String,
    isSelectableTextLayerVisible: Boolean,
    searchQuery: String,
    strokes: List<PdfInkStroke>,
    stamps: List<PdfStampItem>,
    redactions: List<PdfRedactionRect>,
    primaryColor: Color,
    readerTheme: ReaderTheme,
    onZoomAndPanChange: (Float, Offset) -> Unit,
    onSingleTap: () -> Unit,
    onLongPressPage: (Int) -> Unit,
    onQuoteText: (String, Int) -> Unit
) {
    val context = LocalContext.current
    val visiblePageIndices = remember(totalPages, deletedPages) {
        (0 until totalPages).filter { it !in deletedPages }
    }

    val pdfColorFilter = remember(colorFilterMode) {
        when (colorFilterMode) {
            "NIGHT" -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        -0.88f, 0f, 0f, 0f, 240f,
                        0f, -0.88f, 0f, 0f, 242f,
                        0f, 0f, -0.85f, 0f, 248f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            "SEPIA" -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        0.96f, 0.04f, 0f, 0f, 0f,
                        0.02f, 0.92f, 0.02f, 0f, 0f,
                        0f, 0.05f, 0.78f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
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
            .multiTouchPinchToZoom(
                currentScale = zoomScale,
                currentPan = panOffset,
                onZoomAndPanChange = onZoomAndPanChange
            )
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = zoomScale,
                    scaleY = zoomScale,
                    translationX = panOffset.x,
                    translationY = panOffset.y
                ),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(visiblePageIndices.size, key = { idx -> visiblePageIndices[idx] }) { listIdx ->
                val pageIdx = visiblePageIndices[listIdx]
                var pageBmp by remember(uri, pageIdx, isAutoCropMargins) { mutableStateOf<Bitmap?>(null) }
                var isPageTextExpanded by remember(pageIdx, isSelectableTextLayerVisible) {
                    mutableStateOf(isSelectableTextLayerVisible)
                }

                LaunchedEffect(uri, pageIdx, isAutoCropMargins) {
                    val rendered = withContext(Dispatchers.IO) {
                        val raw = MultiFormatDocumentEngine.renderPdfPage(context, uri, pageIdx, scaleFactor = 1.65f)
                        if (raw != null && isAutoCropMargins) {
                            MultiFormatDocumentEngine.cropPdfBitmapWhitespace(raw)
                        } else {
                            raw
                        }
                    }
                    pageBmp = rendered
                }

                val pageSelectableText = remember(fullExtractedPdfText, pageIdx, totalPages, fileName) {
                    MultiFormatDocumentEngine.getPdfPageSelectableText(
                        fullPdfText = fullExtractedPdfText,
                        pageIndex = pageIdx,
                        totalPages = totalPages,
                        fileName = fileName
                    )
                }

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (colorFilterMode == "NIGHT") Color(0xFF1E293B) else Color.White
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    border = if (pageIdx == currentPage) {
                        androidx.compose.foundation.BorderStroke(1.5.dp, primaryColor.copy(alpha = 0.65f))
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Top bar per page in continuous scroll mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (colorFilterMode == "NIGHT") Color(0xFF0F172A) else Color(0xFFF8FAFC)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (pageIdx == currentPage) primaryColor else Color(0xFF64748B)
                                ) {
                                    Text(
                                        text = "صفحة ${pageIdx + 1} / $totalPages",
                                        fontSize = 10.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = "اضغط مطولاً لتحديد نص • قرّب بأصبعيك للتكبير",
                                    fontSize = 9.sp,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    onClick = { isPageTextExpanded = !isPageTextExpanded },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isPageTextExpanded) primaryColor.copy(alpha = 0.16f) else Color.Transparent,
                                    border = androidx.compose.foundation.BorderStroke(
                                        0.8.dp,
                                        primaryColor.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Text(
                                        text = if (isPageTextExpanded) "🔼 إخفاء النص القابل للتحديد" else "🔤 تحديد نص الصفحة",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Surface(
                                    onClick = { copyTextToClipboardHelper(context, pageSelectableText) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = "📋 نسخ",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF059669),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        // Page Image with Long-Press to Select Text & Double-Tap to Zoom
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(pageIdx, zoomScale) {
                                    detectTapGestures(
                                        onTap = { onSingleTap() },
                                        onDoubleTap = {
                                            if (zoomScale > 1.25f) {
                                                onZoomAndPanChange(1f, Offset.Zero)
                                            } else {
                                                onZoomAndPanChange(2.25f, Offset.Zero)
                                            }
                                        },
                                        onLongPress = {
                                            isPageTextExpanded = true
                                            onLongPressPage(pageIdx)
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (pageBmp != null) {
                                val rot = pageRotations[pageIdx] ?: 0f
                                Image(
                                    bitmap = pageBmp!!.asImageBitmap(),
                                    contentDescription = "صفحة ${pageIdx + 1}",
                                    colorFilter = pdfColorFilter,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer(rotationZ = rot),
                                    contentScale = ContentScale.FillWidth
                                )

                                // Render saved Ink Strokes, Stamps & Redactions on this page
                                PdfFreehandInkAndStampOverlay(
                                    pageIndex = pageIdx,
                                    isDrawingEnabled = false,
                                    activeColorHex = "#EF4444",
                                    activeStrokeWidth = 6f,
                                    activeAlpha = 1f,
                                    strokes = strokes,
                                    stamps = stamps,
                                    onAddStroke = {},
                                    onRemoveStamp = {}
                                )
                                PdfRedactionOverlay(
                                    currentPage = pageIdx,
                                    redactions = redactions,
                                    isRedactionModeActive = false,
                                    onAddRedaction = {}
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(420.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                        Text(
                                            text = "جارٍ عرض الصفحة ${pageIdx + 1}...",
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }

                        // Selectable Text Layer for Native Long-Press Word/Sentence Selection & Copy
                        AnimatedVisibility(visible = isPageTextExpanded) {
                            Surface(
                                color = readerTheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "🔤 طبقة النص القابل للتحديد والنسخ بالضغط المطول (صفحة ${pageIdx + 1}):",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = primaryColor
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Surface(
                                                onClick = { onQuoteText(pageSelectableText, pageIdx) },
                                                shape = RoundedCornerShape(6.dp),
                                                color = primaryColor.copy(alpha = 0.12f)
                                            ) {
                                                Text(
                                                    text = "✍️ اقتباس وتظليل",
                                                    fontSize = 9.sp,
                                                    color = primaryColor,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Surface(
                                                onClick = { onLongPressPage(pageIdx) },
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF6366F1).copy(alpha = 0.12f)
                                            ) {
                                                Text(
                                                    text = "🔍 تكبير النص",
                                                    fontSize = 9.sp,
                                                    color = Color(0xFF4F46E5),
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    SelectionContainer {
                                        Text(
                                            text = buildHighlightedAnnotatedString(pageSelectableText, searchQuery),
                                            fontSize = 13.sp,
                                            lineHeight = 21.sp,
                                            color = readerTheme.text,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    readerTheme.bg,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .padding(10.dp)
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
    val context = LocalContext.current
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
            .padding(
                horizontal = if (highlightItem != null) 8.dp else 2.dp,
                vertical = 4.dp
            )
    ) {
        // Text is directly inside SelectionContainer (no parent clickable blocking native long-press selection handles!)
        Text(
            text = buildHighlightedAnnotatedString(text, searchQuery),
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp * lineSpacing).sp,
            fontWeight = fontWeight,
            color = textColor
        )

        // Subtle quick actions row for 1-tap Copy or Highlight/Quote without interfering with Long-Press Text Selection
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (highlightItem != null && highlightItem.note.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFFEF3C7),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = "💡 ${highlightItem.note}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF92400E),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = { copyTextToClipboardHelper(context, text) },
                    shape = RoundedCornerShape(6.dp),
                    color = textColor.copy(alpha = 0.06f)
                ) {
                    Text(
                        text = "📋 نسخ الفقرة",
                        fontSize = 9.sp,
                        color = textColor.copy(alpha = 0.65f),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Surface(
                    onClick = onClick,
                    shape = RoundedCornerShape(6.dp),
                    color = textColor.copy(alpha = 0.06f)
                ) {
                    Text(
                        text = if (highlightItem != null) "🖍️ تعديل التظليل" else "✍️ تظليل واقتباس",
                        fontSize = 9.sp,
                        color = textColor.copy(alpha = 0.65f),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
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
    rotationDegrees: Float = 0f,
    colorFilterMode: String = "NORMAL",
    onTransform: (Float, androidx.compose.ui.geometry.Offset) -> Unit,
    onResetZoom: () -> Unit = {},
    onSingleTap: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onSwipeNext: () -> Unit = {},
    onSwipePrevious: () -> Unit = {}
) {
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
            .pointerInput(scale) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = {
                        if (scale > 1.2f) {
                            onResetZoom()
                        } else {
                            onTransform(2.2f, Offset.Zero)
                        }
                    },
                    onLongPress = { onLongPress() }
                )
            }
            .pointerInput(scale) {
                awaitEachGesture {
                    var dragAccumulatorY = 0f
                    var wasMultiTouch = false
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= 2) {
                            wasMultiTouch = true
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                onTransform(zoomChange, panChange)
                                event.changes.forEach { change ->
                                    if (change.position != change.previousPosition) change.consume()
                                }
                            }
                        } else if (pressedCount == 1 && !wasMultiTouch) {
                            val panChange = event.calculatePan()
                            if (scale > 1.08f) {
                                if (panChange != Offset.Zero) {
                                    onTransform(1f, panChange)
                                    event.changes.forEach { change ->
                                        if (change.position != change.previousPosition) change.consume()
                                    }
                                }
                            } else {
                                dragAccumulatorY += panChange.y
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (!wasMultiTouch && scale <= 1.08f) {
                        if (dragAccumulatorY < -140f) {
                            onSwipeNext()
                        } else if (dragAccumulatorY > 140f) {
                            onSwipePrevious()
                        }
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
                        translationY = offset.y,
                        rotationZ = rotationDegrees
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
    onExportXlsx: (isCsvConversion: Boolean) -> Unit,
    onOpenChartsStudio: () -> Unit = {}
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

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    onClick = onOpenChartsStudio,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.BarChart, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("الرسوم البيانية", fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                        Text("تحويل لـ Excel", fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                        Text("حفظ Excel", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
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
