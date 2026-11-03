package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.model.AppThemeData
import com.example.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ============================================================================
// 1. LIVE AUTO-SAVE STATUS BADGE & CRASH RECOVERY BANNER
// ============================================================================

@Composable
fun LiveAutoSaveStatusBadge(
    saveState: LiveSaveState,
    lastSavedTimeText: String,
    isInVault: Boolean,
    snapshotsCount: Int,
    onClick: () -> Unit
) {
    val badgeColor = when (saveState) {
        LiveSaveState.SAVED -> Color(0xFF16A34A)
        LiveSaveState.SAVING -> Color(0xFF2563EB)
        LiveSaveState.UNSAVED_CHANGES -> Color(0xFFD97706)
        LiveSaveState.RECOVERY_AVAILABLE -> Color(0xFFDC2626)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = badgeColor.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f)),
        modifier = Modifier.testTag("live_autosave_status_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = when (saveState) {
                    LiveSaveState.SAVED -> Icons.Default.CloudDone
                    LiveSaveState.SAVING -> Icons.Default.Sync
                    LiveSaveState.UNSAVED_CHANGES -> Icons.Default.SaveAs
                    LiveSaveState.RECOVERY_AVAILABLE -> Icons.Default.Restore
                },
                contentDescription = "حالة الحفظ",
                tint = badgeColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = buildString {
                    append("💾 مركز الحفظ والخزنة • ")
                    append(saveState.arabicLabel)
                    if (lastSavedTimeText.isNotBlank() && saveState == LiveSaveState.SAVED) {
                        append(" ($lastSavedTimeText)")
                    }
                    if (isInVault) {
                        append(" • 🛡️ بالخزنة")
                    }
                    if (snapshotsCount > 0) {
                        append(" • v$snapshotsCount")
                    }
                },
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = badgeColor
            )
        }
    }
}

@Composable
fun DocumentCrashRecoveryBanner(
    checkpoint: DocumentRecoveryCheckpoint,
    onRestore: () -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormatted = remember(checkpoint.timestamp) {
        SimpleDateFormat("HH:mm:ss - yyyy/MM/dd", Locale.US).format(Date(checkpoint.timestamp))
    }

    Surface(
        color = Color(0xFFFEF3C7),
        border = BorderStroke(1.dp, Color(0xFFF59E0B)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .testTag("crash_recovery_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.HistoryToggleOff,
                    contentDescription = null,
                    tint = Color(0xFFB45309),
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "⚡ تم العثور على نقطة استعادة تلقائية غير مغلقة ($dateFormatted)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF92400E)
                    )
                    Text(
                        text = checkpoint.summaryLabel.ifBlank { "تتضمن آخر التعديلات والتظليلات المحفوظة تلقائياً" },
                        fontSize = 9.sp,
                        color = Color(0xFFB45309),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onRestore,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("restore_checkpoint_button")
                ) {
                    Text("استعادة الآن", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "تجاهل",
                        tint = Color(0xFF92400E),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

// ============================================================================
// 2. SMART SAVE, VAULT, VERSION HISTORY & .ABDOC BUNDLE CENTER SHEET
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentSaveAndVaultCenterSheet(
    theme: AppThemeData,
    currentDocumentUri: Uri?,
    currentDocumentName: String,
    currentDocumentExtension: String,
    readableTextContent: String,
    highlights: List<DocumentHighlightItem> = emptyList(),
    bookmarks: List<DocumentBookmarkItem> = emptyList(),
    inkStrokes: List<PdfInkStroke> = emptyList(),
    stamps: List<PdfStampItem> = emptyList(),
    redactions: List<PdfRedactionRect> = emptyList(),
    flashcards: List<DocumentFlashcardItem> = emptyList(),
    mindMapNodes: List<DocumentMindMapNode> = emptyList(),
    voiceNotes: List<PageVoiceNoteItem> = emptyList(),
    pageRotations: Map<Int, Float> = emptyMap(),
    deletedPages: Set<Int> = emptySet(),
    initialTab: String = "SAVE_AS", // SAVE_AS, VAULT, VERSIONS, BUNDLE
    onRestoreWorkspaceState: ((RestoredDocumentWorkspaceState) -> Unit)? = null,
    onOpenDocumentFromVaultOrBundle: ((uriString: String, fileName: String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val hasActiveDocument = currentDocumentUri != null && currentDocumentName.isNotBlank()

    var selectedTab by remember {
        mutableStateOf(if (!hasActiveDocument && initialTab == "SAVE_AS") "VAULT" else initialTab)
    }

    // Tab 1: Save As & Profiles state
    var selectedFormat by remember { mutableStateOf(SaveExportFormat.PDF) }
    var selectedPdfProfile by remember { mutableStateOf(PdfSaveProfile.HIGH_RES_PRINT) }
    var isExporting by remember { mutableStateOf(false) }
    var lastExportSummary by remember { mutableStateOf<ExportResultSummary?>(null) }
    var pendingFileForSafSave by remember { mutableStateOf<File?>(null) }

    // Tab 2: Local Document Vault & PIN Lock state
    var vaultItems by remember { mutableStateOf<List<VaultDocumentItem>>(emptyList()) }
    var customVaultTitleInput by remember(currentDocumentName) {
        mutableStateOf(currentDocumentName.substringBeforeLast("."))
    }
    var newVaultPinInput by remember { mutableStateOf("") }
    var vaultCategoryInput by remember { mutableStateOf("مرجع هام") }
    var pinPromptTargetItem by remember { mutableStateOf<VaultDocumentItem?>(null) }
    var pinPromptAction by remember { mutableStateOf("OPEN") } // OPEN, UNLOCK, DELETE
    var enteredPinForPrompt by remember { mutableStateOf("") }
    var lockDialogTargetItem by remember { mutableStateOf<VaultDocumentItem?>(null) }
    var lockDialogNewPin by remember { mutableStateOf("") }

    // Tab 3: Version History & Snapshots state
    val docUriString = currentDocumentUri?.toString() ?: ""
    var snapshots by remember(docUriString) {
        mutableStateOf(
            if (docUriString.isNotBlank()) DocumentVaultAndSaveManager.getDocumentSnapshots(context, docUriString)
            else emptyList()
        )
    }
    var newSnapshotLabelInput by remember { mutableStateOf("") }

    // Tab 4: .abdoc Comprehensive Bundle state
    var isPackagingBundle by remember { mutableStateOf(false) }
    var lastCreatedBundleFile by remember { mutableStateOf<File?>(null) }
    var lastImportedBundleResult by remember { mutableStateOf<AbdocImportResult?>(null) }

    fun refreshVaultAndSnapshots() {
        vaultItems = DocumentVaultAndSaveManager.getVaultDocuments(context)
        if (docUriString.isNotBlank()) {
            snapshots = DocumentVaultAndSaveManager.getDocumentSnapshots(context, docUriString)
        }
    }

    LaunchedEffect(Unit) {
        refreshVaultAndSnapshots()
    }

    // SAF CreateDocument launcher for direct saving to phone folders
    val safSaveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destUri: Uri? ->
        val fileToWrite = pendingFileForSafSave
        pendingFileForSafSave = null
        if (destUri != null && fileToWrite != null) {
            coroutineScope.launch {
                val res = withContext(Dispatchers.IO) {
                    DocumentVaultAndSaveManager.writeExportedFileToSafUri(context, fileToWrite, destUri)
                }
                if (res.isSuccess) {
                    val sizeText = DocumentVaultAndSaveManager.formatSizeArabic(res.getOrDefault(fileToWrite.length()))
                    Toast.makeText(
                        context,
                        "✅ تم حفظ «${fileToWrite.name}» ($sizeText) في مجلد الهاتف بنجاح!",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        "تعذر الحفظ في المجلد: ${res.exceptionOrNull()?.localizedMessage ?: ""}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // SAF OpenDocument launcher to import any external document into the permanent Local Vault
    val importToVaultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { pickedUri: Uri? ->
        if (pickedUri != null) {
            coroutineScope.launch {
                val (name, _) = MultiFormatDocumentEngine.getFileNameAndSize(context, pickedUri)
                val res = withContext(Dispatchers.IO) {
                    DocumentVaultAndSaveManager.saveDocumentToVault(
                        context = context,
                        sourceUri = pickedUri,
                        fileName = name,
                        customTitle = name.substringBeforeLast("."),
                        pinCode = newVaultPinInput,
                        categoryLabel = vaultCategoryInput
                    )
                }
                if (res.isSuccess) {
                    newVaultPinInput = ""
                    refreshVaultAndSnapshots()
                    Toast.makeText(
                        context,
                        "🛡️ تم استيراد «$name» وحفظه نهائياً داخل خزنة المستندات!",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        "تعذر استيراد الملف للخزنة: ${res.exceptionOrNull()?.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // SAF OpenDocument launcher to import a `.abdoc` bundle
    val importAbdocBundleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { bundleUri: Uri? ->
        if (bundleUri != null) {
            coroutineScope.launch {
                isPackagingBundle = true
                val res = withContext(Dispatchers.IO) {
                    DocumentVaultAndSaveManager.importComprehensiveAbdocBundle(context, bundleUri)
                }
                isPackagingBundle = false
                if (res.isSuccess) {
                    val info = res.getOrThrow()
                    lastImportedBundleResult = info
                    refreshVaultAndSnapshots()
                    Toast.makeText(
                        context,
                        "📦 تم استيراد حزمة «${info.vaultItem.title}» مع كافة التظليلات والرسومات والبطاقات!",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        "تعذر استيراد الحزمة: تأكد من اختيار ملف .abdoc أو .zip صالح",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun shareExportedFile(context: Context, file: File, mimeType: String = "*/*") {
        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "مشاركة المستند المحفوظ"))
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر المشاركة: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E3A8A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "مركز الحفظ المتقدم والخزنة الذكية",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = if (hasActiveDocument)
                                "المستند النشط: $currentDocumentName"
                            else
                                "إدارة خزنة المستندات الدائمة، الإصدارات، وحزم .abdoc",
                            fontSize = 11.sp,
                            color = Color(0xFF475569),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color(0xFF64748B))
                }
            }

            // 4-Tab Selector Bar
            val tabs = listOf(
                Triple("SAVE_AS", "💾 الحفظ والضغط", Icons.Default.SaveAs),
                Triple("VAULT", "🛡️ الخزنة (${vaultItems.size})", Icons.Default.EnhancedEncryption),
                Triple("VERSIONS", "🕒 الإصدارات (${snapshots.size})", Icons.Default.History),
                Triple("BUNDLE", "📦 حزمة .abdoc", Icons.Default.FolderZip)
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(tabs) { (tabKey, tabLabel, tabIcon) ->
                    val isSelected = selectedTab == tabKey
                    Surface(
                        onClick = { selectedTab = tabKey },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFF1E3A8A) else Color.White,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFF1E3A8A) else Color(0xFFE2E8F0)
                        ),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("save_center_tab_$tabKey")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                tabIcon,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else Color(0xFF475569),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = tabLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else Color(0xFF1E293B)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFE2E8F0))

            // Tab Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 28.dp)
            ) {
                when (selectedTab) {
                    // ========================================================
                    // TAB 1: DIRECT SAVE AS (SAF) & PDF PROFILES / COMPRESSION
                    // ========================================================
                    "SAVE_AS" -> {
                        if (!hasActiveDocument) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.InsertDriveFile,
                                            contentDescription = null,
                                            tint = theme.primaryColor,
                                            modifier = Modifier.size(38.dp)
                                        )
                                        Text(
                                            "افتح أي مستند أولاً لاستخدام الحفظ المباشر بـ 6 صيغ",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF1E293B),
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            "يمكنك الآن الانتقال إلى تبويب «الخزنة» أو «حزمة .abdoc» لاستيراد ملفاتك المحمية.",
                                            fontSize = 11.sp,
                                            color = Color.Gray,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            // 1. Format Selector Grid
                            item {
                                Text(
                                    "1. اختر صيغة الحفظ والتصدير المباشر:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                            }

                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SaveExportFormat.entries.chunked(2).forEach { rowFormats ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            rowFormats.forEach { fmt ->
                                                val isChosen = selectedFormat == fmt
                                                Surface(
                                                    onClick = { selectedFormat = fmt },
                                                    shape = RoundedCornerShape(14.dp),
                                                    color = if (isChosen) Color(0xFFEFF6FF) else Color.White,
                                                    border = BorderStroke(
                                                        if (isChosen) 2.dp else 1.dp,
                                                        if (isChosen) Color(0xFF2563EB) else Color(0xFFE2E8F0)
                                                    ),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .testTag("format_option_${fmt.extension}")
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
                                                            Surface(
                                                                shape = RoundedCornerShape(6.dp),
                                                                color = if (isChosen) Color(0xFF2563EB) else Color(0xFFF1F5F9)
                                                            ) {
                                                                Text(
                                                                    text = fmt.badgeTitle,
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Black,
                                                                    color = if (isChosen) Color.White else Color(0xFF334155),
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                            if (isChosen) {
                                                                Icon(
                                                                    Icons.Default.CheckCircle,
                                                                    contentDescription = null,
                                                                    tint = Color(0xFF2563EB),
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                        }
                                                        Text(
                                                            text = fmt.arabicLabel,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF0F172A)
                                                        )
                                                        Text(
                                                            text = fmt.description,
                                                            fontSize = 9.sp,
                                                            color = Color(0xFF64748B),
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                            lineHeight = 13.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 2. Quick Save Profiles for PDF (High-Res Print, Compressed, Flattened Protected)
                            if (selectedFormat == SaveExportFormat.PDF) {
                                item {
                                    Text(
                                        "2. ملف التعريف الذكي لحفظ PDF (الضغط والدمج الآمن):",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                }

                                items(PdfSaveProfile.entries) { profile ->
                                    val isSelectedProfile = selectedPdfProfile == profile
                                    val accentColor = when (profile) {
                                        PdfSaveProfile.HIGH_RES_PRINT -> Color(0xFF1D4ED8)
                                        PdfSaveProfile.COMPRESSED_WEB -> Color(0xFF059669)
                                        PdfSaveProfile.FLATTENED_PROTECTED -> Color(0xFFDC2626)
                                    }

                                    Surface(
                                        onClick = { selectedPdfProfile = profile },
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (isSelectedProfile) accentColor.copy(alpha = 0.08f) else Color.White,
                                        border = BorderStroke(
                                            if (isSelectedProfile) 2.dp else 1.dp,
                                            if (isSelectedProfile) accentColor else Color(0xFFE2E8F0)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("pdf_profile_${profile.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            RadioButton(
                                                selected = isSelectedProfile,
                                                onClick = { selectedPdfProfile = profile },
                                                colors = RadioButtonDefaults.colors(selectedColor = accentColor)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = profile.arabicTitle,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF0F172A)
                                                )
                                                Text(
                                                    text = profile.subtitle,
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF475569),
                                                    lineHeight = 14.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 3. Action Buttons: Direct "Save As" to Phone Folder (SAF) + Quick Share
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "سيتم تضمين (${highlights.size}) تظليلات، (${inkStrokes.size}) رسومات حرة، (${stamps.size}) أختام وتوقيعات، و(${flashcards.size}) بطاقات تلقائياً.",
                                            fontSize = 10.sp,
                                            color = Color(0xFF475569)
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // Primary: Direct SAF Save As to Device Folder
                                            Button(
                                                onClick = {
                                                    val uri = currentDocumentUri ?: return@Button
                                                    coroutineScope.launch {
                                                        isExporting = true
                                                        val summary = withContext(Dispatchers.IO) {
                                                            DocumentVaultAndSaveManager.exportDocumentWithFormatAndProfile(
                                                                context = context,
                                                                sourceUri = uri,
                                                                sourceFileName = currentDocumentName,
                                                                sourceExtension = currentDocumentExtension,
                                                                format = selectedFormat,
                                                                pdfProfile = selectedPdfProfile,
                                                                readableTextContent = readableTextContent,
                                                                highlights = highlights,
                                                                bookmarks = bookmarks,
                                                                inkStrokes = inkStrokes,
                                                                stamps = stamps,
                                                                redactions = redactions,
                                                                flashcards = flashcards,
                                                                voiceNotes = voiceNotes,
                                                                pageRotations = pageRotations,
                                                                deletedPages = deletedPages
                                                            )
                                                        }
                                                        isExporting = false
                                                        if (summary != null) {
                                                            lastExportSummary = summary
                                                            pendingFileForSafSave = summary.file
                                                            safSaveDocumentLauncher.launch(summary.file.name)
                                                        } else {
                                                            Toast.makeText(context, "تعذر تجهيز ملف التصدير", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                },
                                                enabled = !isExporting,
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A)),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .weight(1.3f)
                                                    .height(48.dp)
                                                    .testTag("direct_saf_save_as_button")
                                            ) {
                                                if (isExporting) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(16.dp),
                                                        strokeWidth = 2.dp,
                                                        color = Color.White
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("جاري التجهيز...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                } else {
                                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(17.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        "حفظ باسم في الهاتف (${selectedFormat.badgeTitle})",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            // Secondary: Export & Share
                                            OutlinedButton(
                                                onClick = {
                                                    val uri = currentDocumentUri ?: return@OutlinedButton
                                                    coroutineScope.launch {
                                                        isExporting = true
                                                        val summary = withContext(Dispatchers.IO) {
                                                            DocumentVaultAndSaveManager.exportDocumentWithFormatAndProfile(
                                                                context = context,
                                                                sourceUri = uri,
                                                                sourceFileName = currentDocumentName,
                                                                sourceExtension = currentDocumentExtension,
                                                                format = selectedFormat,
                                                                pdfProfile = selectedPdfProfile,
                                                                readableTextContent = readableTextContent,
                                                                highlights = highlights,
                                                                bookmarks = bookmarks,
                                                                inkStrokes = inkStrokes,
                                                                stamps = stamps,
                                                                redactions = redactions,
                                                                flashcards = flashcards,
                                                                voiceNotes = voiceNotes,
                                                                pageRotations = pageRotations,
                                                                deletedPages = deletedPages
                                                            )
                                                        }
                                                        isExporting = false
                                                        if (summary != null) {
                                                            lastExportSummary = summary
                                                            shareExportedFile(context, summary.file, selectedFormat.mimeType)
                                                        }
                                                    }
                                                },
                                                enabled = !isExporting,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .weight(0.8f)
                                                    .height(48.dp)
                                            ) {
                                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("مشاركة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            // Show Last Export Result Card with Compression Metrics
                            lastExportSummary?.let { summary ->
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color(0xFFECFDF5),
                                        border = BorderStroke(1.dp, Color(0xFF10B981)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Verified,
                                                    contentDescription = null,
                                                    tint = Color(0xFF059669),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = "تم تجهيز الملف بنجاح: ${summary.file.name}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF065F46)
                                                )
                                            }
                                            val origSizeStr = DocumentVaultAndSaveManager.formatSizeArabic(summary.originalSizeBytes)
                                            val expSizeStr = DocumentVaultAndSaveManager.formatSizeArabic(summary.exportedSizeBytes)
                                            val savingsText = if (summary.savedPercentage > 0) {
                                                " • توفير ${summary.savedPercentage}% من الحجم!"
                                            } else ""
                                            Text(
                                                text = "الحجم الأصلي: $origSizeStr ← الحجم بعد المعالجة: $expSizeStr$savingsText",
                                                fontSize = 10.sp,
                                                color = Color(0xFF047857)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ========================================================
                    // TAB 2: PERMANENT LOCAL DOCUMENT VAULT & PIN LOCK
                    // ========================================================
                    "VAULT" -> {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Security,
                                            contentDescription = null,
                                            tint = Color(0xFF0D9488),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column {
                                            Text(
                                                "خزنة المستندات المحلية الدائمة (Local Vault & PIN)",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F172A)
                                            )
                                            Text(
                                                "تحفظ نسخة كاملة ومستقلة من المستند وملاحظاته داخل ذاكرة التطبيق المحمية حتى لو حُذف الملف الأصلي من الهاتف.",
                                                fontSize = 10.sp,
                                                color = Color(0xFF64748B),
                                                lineHeight = 14.sp
                                            )
                                        }
                                    }

                                    if (hasActiveDocument) {
                                        OutlinedTextField(
                                            value = customVaultTitleInput,
                                            onValueChange = { customVaultTitleInput = it },
                                            label = { Text("عنوان المستند داخل الخزنة", fontSize = 11.sp) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = newVaultPinInput,
                                            onValueChange = { if (it.length <= 8) newVaultPinInput = it },
                                            label = { Text("رمز PIN للحماية (اختياري)", fontSize = 10.sp) },
                                            placeholder = { Text("مثال: 1234", fontSize = 10.sp) },
                                            visualTransformation = PasswordVisualTransformation(),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        )

                                        OutlinedTextField(
                                            value = vaultCategoryInput,
                                            onValueChange = { vaultCategoryInput = it },
                                            label = { Text("التصنيف", fontSize = 10.sp) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (hasActiveDocument) {
                                            Button(
                                                onClick = {
                                                    val uri = currentDocumentUri ?: return@Button
                                                    coroutineScope.launch {
                                                        val res = withContext(Dispatchers.IO) {
                                                            DocumentVaultAndSaveManager.saveDocumentToVault(
                                                                context = context,
                                                                sourceUri = uri,
                                                                fileName = currentDocumentName,
                                                                customTitle = customVaultTitleInput.ifBlank { currentDocumentName },
                                                                pinCode = newVaultPinInput,
                                                                categoryLabel = vaultCategoryInput
                                                            )
                                                        }
                                                        if (res.isSuccess) {
                                                            newVaultPinInput = ""
                                                            refreshVaultAndSnapshots()
                                                            Toast.makeText(
                                                                context,
                                                                "🛡️ تم حفظ المستند وملاحظاته في الخزنة الدائمة بنجاح!",
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                        } else {
                                                            Toast.makeText(
                                                                context,
                                                                "تعذر الحفظ في الخزنة: ${res.exceptionOrNull()?.localizedMessage}",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(44.dp)
                                                    .testTag("save_active_doc_to_vault_button")
                                            ) {
                                                Icon(Icons.Default.EnhancedEncryption, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("تأمين المستند الحالي بالخزنة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                importToVaultLauncher.launch(arrayOf("*/*"))
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                                .testTag("import_external_to_vault_button")
                                        ) {
                                            Icon(Icons.Default.AddModerator, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("استيراد ملف للخزنة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                text = "المستندات المحفوظة في الخزنة الدائمة (${vaultItems.size}):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        }

                        if (vaultItems.isEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                                        Text("الخزنة فارغة حالياً", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                                        Text(
                                            "قم بتأمين المستند الحالي أو استيراد ملفاتك الهامة لحمايتها برمز PIN والاحتفاظ بنسخة دائمة منها.",
                                            fontSize = 10.sp,
                                            color = Color.Gray,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            items(vaultItems, key = { it.id }) { item ->
                                val dateStr = remember(item.lastModifiedTimestamp) {
                                    SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(item.lastModifiedTimestamp))
                                }
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White,
                                    border = BorderStroke(
                                        1.dp,
                                        if (item.isPinLocked) Color(0xFFF59E0B) else Color(0xFFE2E8F0)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
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
                                                        .size(38.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(
                                                            if (item.isPinLocked) Color(0xFFFEF3C7) else Color(0xFFE0F2FE)
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = if (item.isPinLocked) Icons.Default.Lock else Icons.Default.VerifiedUser,
                                                        contentDescription = null,
                                                        tint = if (item.isPinLocked) Color(0xFFD97706) else Color(0xFF0284C7),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = item.title,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF0F172A),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        if (item.isPinLocked) {
                                                            Surface(
                                                                shape = RoundedCornerShape(4.dp),
                                                                color = Color(0xFFFEF3C7)
                                                            ) {
                                                                Text(
                                                                    text = "🔒 محمي بـ PIN",
                                                                    fontSize = 8.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Color(0xFFB45309),
                                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Text(
                                                        text = "${item.extension.uppercase()} • ${DocumentVaultAndSaveManager.formatSizeArabic(item.sizeBytes)} • التفاعلات: ${item.annotationsCount} • $dateStr",
                                                        fontSize = 9.sp,
                                                        color = Color(0xFF64748B)
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            // Open from Vault
                                            Button(
                                                onClick = {
                                                    if (item.isPinLocked) {
                                                        pinPromptTargetItem = item
                                                        pinPromptAction = "OPEN"
                                                        enteredPinForPrompt = ""
                                                    } else {
                                                        onOpenDocumentFromVaultOrBundle?.invoke(item.localUriString, item.fileName)
                                                        onDismiss()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(34.dp)
                                            ) {
                                                Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("فتح من الخزنة", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }

                                            // Lock / Unlock PIN
                                            OutlinedButton(
                                                onClick = {
                                                    if (item.isPinLocked) {
                                                        pinPromptTargetItem = item
                                                        pinPromptAction = "UNLOCK"
                                                        enteredPinForPrompt = ""
                                                    } else {
                                                        lockDialogTargetItem = item
                                                        lockDialogNewPin = ""
                                                    }
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Icon(
                                                    if (item.isPinLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    if (item.isPinLocked) "إلغاء القفل" else "قفل بـ PIN",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            // Delete from Vault
                                            IconButton(
                                                onClick = {
                                                    if (item.isPinLocked) {
                                                        pinPromptTargetItem = item
                                                        pinPromptAction = "DELETE"
                                                        enteredPinForPrompt = ""
                                                    } else {
                                                        DocumentVaultAndSaveManager.deleteVaultDocument(context, item.id)
                                                        refreshVaultAndSnapshots()
                                                        Toast.makeText(context, "تم حذف النسخة من الخزنة", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ========================================================
                    // TAB 3: VERSION HISTORY & RESTORE POINTS (سجل الإصدارات)
                    // ========================================================
                    "VERSIONS" -> {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.History,
                                            contentDescription = null,
                                            tint = Color(0xFF7C3AED),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column {
                                            Text(
                                                "سجل الإصدارات ونقاط الاستعادة الزمنية (Snapshots)",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F172A)
                                            )
                                            Text(
                                                "احفظ لقطة زمنية كاملة لحالة المستند (التظليلات، الرسومات، الأختام، البطاقات، والملاحظات) للرجوع إليها بضغطة زر.",
                                                fontSize = 10.sp,
                                                color = Color(0xFF64748B),
                                                lineHeight = 14.sp
                                            )
                                        }
                                    }

                                    if (hasActiveDocument) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = newSnapshotLabelInput,
                                                onValueChange = { newSnapshotLabelInput = it },
                                                placeholder = { Text("وصف اللقطة (مثال: قبل المراجعة النهائية)...", fontSize = 11.sp) },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(10.dp)
                                            )

                                            Button(
                                                onClick = {
                                                    val created = DocumentVaultAndSaveManager.createDocumentSnapshot(
                                                        context = context,
                                                        uriString = docUriString,
                                                        documentName = currentDocumentName,
                                                        label = newSnapshotLabelInput.trim(),
                                                        isAutoSnapshot = false,
                                                        highlights = highlights,
                                                        bookmarks = bookmarks,
                                                        inkStrokes = inkStrokes,
                                                        stamps = stamps,
                                                        flashcards = flashcards,
                                                        mindMapNodes = mindMapNodes,
                                                        redactions = redactions,
                                                        voiceNotes = voiceNotes,
                                                        editedTextContent = readableTextContent
                                                    )
                                                    newSnapshotLabelInput = ""
                                                    refreshVaultAndSnapshots()
                                                    Toast.makeText(
                                                        context,
                                                        "📸 تم حفظ اللقطة الزمنية (الإصدار #${created.versionNumber}) بنجاح!",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier
                                                    .height(48.dp)
                                                    .testTag("create_snapshot_button")
                                            ) {
                                                Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(15.dp))
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text("حفظ لقطة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (snapshots.isEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.Update, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                                        Text("لا توجد لقطات زمنية محفوظة لهذا المستند بعد", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                                        Text(
                                            "انقر على «حفظ لقطة» أعلاه لتسجيل نقطة استعادة فورية لكافة تعليقاتك وتعديلاتك.",
                                            fontSize = 10.sp,
                                            color = Color.Gray,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            items(snapshots, key = { it.id }) { snap ->
                                val timeFormatted = remember(snap.timestamp) {
                                    SimpleDateFormat("yyyy/MM/dd • HH:mm:ss", Locale.US).format(Date(snap.timestamp))
                                }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Color(0xFFDDD6FE)),
                                    modifier = Modifier.fillMaxWidth()
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
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color(0xFF7C3AED)
                                                ) {
                                                    Text(
                                                        text = "v${snap.versionNumber}",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                    )
                                                }
                                                Column {
                                                    Text(
                                                        text = snap.label,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF0F172A)
                                                    )
                                                    Text(
                                                        text = timeFormatted + if (snap.isAutoSnapshot) " • حفظ تلقائي" else " • نقطة يدوية",
                                                        fontSize = 9.sp,
                                                        color = Color(0xFF64748B)
                                                    )
                                                }
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Button(
                                                    onClick = {
                                                        val restored = DocumentVaultAndSaveManager.restoreDocumentSnapshot(
                                                            context = context,
                                                            uriString = docUriString,
                                                            snapshot = snap
                                                        )
                                                        onRestoreWorkspaceState?.invoke(restored)
                                                        Toast.makeText(
                                                            context,
                                                            "✅ تمت استعادة الإصدار v${snap.versionNumber} (${snap.label}) بنجاح!",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                        onDismiss()
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("استعادة", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }

                                                IconButton(
                                                    onClick = {
                                                        snapshots = DocumentVaultAndSaveManager.deleteDocumentSnapshot(
                                                            context,
                                                            docUriString,
                                                            snap.id
                                                        )
                                                    },
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }

                                        // Snapshot metrics chips
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            SnapshotStatPill("🖍️ تظليلات: ${snap.highlightsCount}")
                                            SnapshotStatPill("🖌️ رسومات: ${snap.inkStrokesCount}")
                                            SnapshotStatPill("🔖 علامات: ${snap.bookmarksCount}")
                                            SnapshotStatPill("🎴 بطاقات: ${snap.flashcardsCount}")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ========================================================
                    // TAB 4: COMPREHENSIVE .ABDOC BUNDLE EXPORT & IMPORT
                    // ========================================================
                    "BUNDLE" -> {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.FolderZip,
                                            contentDescription = null,
                                            tint = Color(0xFFD97706),
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Column {
                                            Text(
                                                "حزمة المستند الشاملة والتفاعلية (.abdoc Bundle)",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F172A)
                                            )
                                            Text(
                                                "تجمع الملف الأصلي للمستند + كافة التظليلات، الرسومات الحرة، الأختام، البطاقات التعليمية، الخريطة الذهنية، والملاحظات الصوتية في ملف واحد محمول (.abdoc) للنقل أو النسخ الاحتياطي.",
                                                fontSize = 10.sp,
                                                color = Color(0xFF64748B),
                                                lineHeight = 14.sp
                                            )
                                        }
                                    }

                                    if (hasActiveDocument) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    val uri = currentDocumentUri ?: return@Button
                                                    coroutineScope.launch {
                                                        isPackagingBundle = true
                                                        val res = withContext(Dispatchers.IO) {
                                                            DocumentVaultAndSaveManager.exportComprehensiveAbdocBundle(
                                                                context = context,
                                                                sourceUri = uri,
                                                                sourceFileName = currentDocumentName,
                                                                highlights = highlights,
                                                                bookmarks = bookmarks,
                                                                inkStrokes = inkStrokes,
                                                                stamps = stamps,
                                                                flashcards = flashcards,
                                                                mindMapNodes = mindMapNodes,
                                                                redactions = redactions,
                                                                voiceNotes = voiceNotes,
                                                                editedTextContent = readableTextContent
                                                            )
                                                        }
                                                        isPackagingBundle = false
                                                        if (res.isSuccess) {
                                                            val bundle = res.getOrThrow()
                                                            lastCreatedBundleFile = bundle
                                                            pendingFileForSafSave = bundle
                                                            safSaveDocumentLauncher.launch(bundle.name)
                                                        } else {
                                                            Toast.makeText(context, "تعذر إنشاء حزمة .abdoc", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                },
                                                enabled = !isPackagingBundle,
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier
                                                    .weight(1.2f)
                                                    .height(46.dp)
                                                    .testTag("export_abdoc_bundle_button")
                                            ) {
                                                Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("تصدير حزمة .abdoc للهاتف", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    val uri = currentDocumentUri ?: return@OutlinedButton
                                                    coroutineScope.launch {
                                                        isPackagingBundle = true
                                                        val res = withContext(Dispatchers.IO) {
                                                            DocumentVaultAndSaveManager.exportComprehensiveAbdocBundle(
                                                                context = context,
                                                                sourceUri = uri,
                                                                sourceFileName = currentDocumentName,
                                                                highlights = highlights,
                                                                bookmarks = bookmarks,
                                                                inkStrokes = inkStrokes,
                                                                stamps = stamps,
                                                                flashcards = flashcards,
                                                                mindMapNodes = mindMapNodes,
                                                                redactions = redactions,
                                                                voiceNotes = voiceNotes,
                                                                editedTextContent = readableTextContent
                                                            )
                                                        }
                                                        isPackagingBundle = false
                                                        if (res.isSuccess) {
                                                            val bundle = res.getOrThrow()
                                                            lastCreatedBundleFile = bundle
                                                            shareExportedFile(context, bundle, "application/zip")
                                                        }
                                                    }
                                                },
                                                enabled = !isPackagingBundle,
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier
                                                    .weight(0.8f)
                                                    .height(46.dp)
                                            ) {
                                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("مشاركة الحزمة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = Color(0xFFF1F5F9))

                                    Text(
                                        "استيراد حزمة مستند شاملة (.abdoc) من الجهاز:",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )

                                    Button(
                                        onClick = {
                                            importAbdocBundleLauncher.launch(arrayOf("*/*"))
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp)
                                            .testTag("import_abdoc_bundle_button")
                                    ) {
                                        Icon(Icons.Default.Unarchive, contentDescription = null, modifier = Modifier.size(17.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "استيراد ملف حزمة (.abdoc) واستعادة المستند وملاحظاته",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    lastImportedBundleResult?.let { imported ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFFF0FDF4),
                                            border = BorderStroke(1.dp, Color(0xFF22C55E)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    "✅ تم استيراد «${imported.vaultItem.title}» إلى الخزنة بنجاح!",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF15803D)
                                                )
                                                Text(
                                                    "تمت استعادة: ${imported.restoredHighlights} تظليل • ${imported.restoredStrokes} رسمة • ${imported.restoredStamps} ختم • ${imported.restoredFlashcards} بطاقة • ${imported.restoredVoiceNotes} ملاحظة صوتية",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF166534)
                                                )
                                                Button(
                                                    onClick = {
                                                        onOpenDocumentFromVaultOrBundle?.invoke(
                                                            imported.vaultItem.localUriString,
                                                            imported.vaultItem.fileName
                                                        )
                                                        onDismiss()
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D)),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text("فتح المستند المستعاد الآن", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

    // Dialog 1: Verify PIN for locked Vault item
    if (pinPromptTargetItem != null) {
        val target = pinPromptTargetItem!!
        AlertDialog(
            onDismissRequest = { pinPromptTargetItem = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFD97706))
                    Text("مستند محمي برمز PIN", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("أدخل رمز الحماية (PIN) الخاص بالمستند «${target.title}» للمتابعة:", fontSize = 12.sp)
                    OutlinedTextField(
                        value = enteredPinForPrompt,
                        onValueChange = { enteredPinForPrompt = it },
                        label = { Text("رمز PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (DocumentVaultAndSaveManager.verifyVaultItemPin(target, enteredPinForPrompt)) {
                            val act = pinPromptAction
                            pinPromptTargetItem = null
                            when (act) {
                                "OPEN" -> {
                                    onOpenDocumentFromVaultOrBundle?.invoke(target.localUriString, target.fileName)
                                    onDismiss()
                                }
                                "UNLOCK" -> {
                                    DocumentVaultAndSaveManager.setVaultItemPinLock(context, target.id, false, "")
                                    refreshVaultAndSnapshots()
                                    Toast.makeText(context, "🔓 تم إلغاء قفل رمز PIN عن المستند", Toast.LENGTH_SHORT).show()
                                }
                                "DELETE" -> {
                                    DocumentVaultAndSaveManager.deleteVaultDocument(context, target.id)
                                    refreshVaultAndSnapshots()
                                    Toast.makeText(context, "تم حذف المستند من الخزنة", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "❌ رمز PIN غير صحيح!", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("تأكيد")
                }
            },
            dismissButton = {
                TextButton(onClick = { pinPromptTargetItem = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Dialog 2: Set new PIN lock on an unlocked Vault item
    if (lockDialogTargetItem != null) {
        val target = lockDialogTargetItem!!
        AlertDialog(
            onDismissRequest = { lockDialogTargetItem = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.EnhancedEncryption, contentDescription = null, tint = Color(0xFF0D9488))
                    Text("تعيين رمز PIN لحماية المستند", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اختر رمز PIN لقفل المستند «${target.title}» داخل الخزنة:", fontSize = 12.sp)
                    OutlinedTextField(
                        value = lockDialogNewPin,
                        onValueChange = { if (it.length <= 8) lockDialogNewPin = it },
                        label = { Text("رمز PIN الجديد (4 أرقام أو أكثر)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (lockDialogNewPin.trim().length >= 3) {
                            DocumentVaultAndSaveManager.setVaultItemPinLock(
                                context = context,
                                vaultItemId = target.id,
                                enableLock = true,
                                newPinCode = lockDialogNewPin.trim()
                            )
                            lockDialogTargetItem = null
                            refreshVaultAndSnapshots()
                            Toast.makeText(context, "🔒 تم قفل المستند برمز PIN بنجاح!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "يرجى إدخال رمز مكون من 3 خانات على الأقل", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("تفعيل القفل")
                }
            },
            dismissButton = {
                TextButton(onClick = { lockDialogTargetItem = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun SnapshotStatPill(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFFF3E8FF)
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF6D28D9),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
