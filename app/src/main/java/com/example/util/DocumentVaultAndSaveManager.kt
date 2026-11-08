package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// ============================================================================
// 1. DATA MODELS (الموديلات الخاصة بنظام الحفظ المتقدم والخزنة الذكية)
// ============================================================================

enum class SaveExportFormat(
    val extension: String,
    val mimeType: String,
    val arabicLabel: String,
    val badgeTitle: String,
    val description: String
) {
    PDF(
        extension = "pdf",
        mimeType = "application/pdf",
        arabicLabel = "مستند PDF احترافي",
        badgeTitle = "PDF",
        description = "يدعم دمج التظليلات، الرسومات، الأختام، والملفات التعريفية للضغط أو الطباعة"
    ),
    DOCX(
        extension = "docx",
        mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        arabicLabel = "مستند Microsoft Word",
        badgeTitle = "DOCX",
        description = "ملف وورد قابل للتعديل يدعم المحاذاة العربية RTL والتنسيق القياسي"
    ),
    EPUB(
        extension = "epub",
        mimeType = "application/epub+zip",
        arabicLabel = "كتاب إلكتروني EPUB 3",
        badgeTitle = "EPUB",
        description = "كتاب إلكتروني متدفق مع فهرس فصول وملاحظات القارئ المدمجة"
    ),
    HTML(
        extension = "html",
        mimeType = "text/html",
        arabicLabel = "صفحة ويب تفاعلية RTL",
        badgeTitle = "HTML",
        description = "ملف ويب مستقل بتصميم عربي أنيق مع جدول الملاحظات والبطاقات"
    ),
    MARKDOWN(
        extension = "md",
        mimeType = "text/markdown",
        arabicLabel = "مستند Markdown هيكلي",
        badgeTitle = "MD",
        description = "نص منسق بالعناوين والاقتباسات والبطاقات التعليمية للمحررين والباحثين"
    ),
    TXT(
        extension = "txt",
        mimeType = "text/plain",
        arabicLabel = "نص صافي UTF-8",
        badgeTitle = "TXT",
        description = "نص عربي خالص سريع القراءة متوافق مع كافة الأنظمة"
    )
}

enum class PdfSaveProfile(
    val id: String,
    val arabicTitle: String,
    val subtitle: String,
    val scaleFactor: Float,
    val jpegQuality: Int,
    val flattenAnnotations: Boolean,
    val addProtectionWatermark: Boolean
) {
    HIGH_RES_PRINT(
        id = "HIGH_RES_PRINT",
        arabicTitle = "جودة الطباعة الفائقة (High-Res Print)",
        subtitle = "دقة 300 DPI فائقة الوضوح للنصوص العربية والرسوم للطباعة الورقية",
        scaleFactor = 1.75f,
        jpegQuality = 96,
        flattenAnnotations = true,
        addProtectionWatermark = false
    ),
    COMPRESSED_WEB(
        id = "COMPRESSED_WEB",
        arabicTitle = "حفظ مضغوط للمشاركة السريعة (Compressed)",
        subtitle = "تقليص حجم الملف بنسبة تصل إلى 60% عبر ضغط الطبقات الذكي للبريد وواتساب",
        scaleFactor = 1.0f,
        jpegQuality = 58,
        flattenAnnotations = true,
        addProtectionWatermark = false
    ),
    FLATTENED_PROTECTED(
        id = "FLATTENED_PROTECTED",
        arabicTitle = "حفظ محمي ومدمج (Flattened Read-Only)",
        subtitle = "دمج الأختام والتوقيعات والطمس نهائياً في طبقة الصفحة لمنع التلاعب أو الإزالة",
        scaleFactor = 1.45f,
        jpegQuality = 88,
        flattenAnnotations = true,
        addProtectionWatermark = true
    )
}

enum class LiveSaveState(
    val arabicLabel: String,
    val badgeColorHex: String
) {
    SAVED("✓ محفوظ بأمان", "#16A34A"),
    SAVING("⟳ جاري الحفظ التلقائي...", "#2563EB"),
    UNSAVED_CHANGES("● تغييرات غير محفوظة", "#D97706"),
    RECOVERY_AVAILABLE("⚡ تتوفر نسخة استعادة", "#DC2626")
}

data class VaultDocumentItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val fileName: String,
    val extension: String,
    val localFilePath: String,
    val originalUriString: String,
    val sizeBytes: Long,
    val addedTimestamp: Long = System.currentTimeMillis(),
    val lastModifiedTimestamp: Long = System.currentTimeMillis(),
    val isPinLocked: Boolean = false,
    val pinSha256: String = "",
    val categoryLabel: String = "عام",
    val annotationsCount: Int = 0
) {
    val localUriString: String
        get() = Uri.fromFile(File(localFilePath)).toString()
}

data class DocumentVersionSnapshot(
    val id: String = UUID.randomUUID().toString(),
    val documentUriString: String,
    val documentName: String,
    val versionNumber: Int,
    val label: String,
    val isAutoSnapshot: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val highlightsCount: Int,
    val bookmarksCount: Int,
    val inkStrokesCount: Int,
    val stampsCount: Int,
    val flashcardsCount: Int,
    val voiceNotesCount: Int,
    val redactionsCount: Int,
    val textLength: Int,
    val snapshotPayloadJson: String,
    val backupFilePath: String = ""
)

data class DocumentRecoveryCheckpoint(
    val documentUriString: String,
    val documentName: String,
    val timestamp: Long,
    val summaryLabel: String,
    val payloadJson: String,
    val editedTextContent: String = ""
)

data class RestoredDocumentWorkspaceState(
    val highlights: List<DocumentHighlightItem>,
    val bookmarks: List<DocumentBookmarkItem>,
    val inkStrokes: List<PdfInkStroke>,
    val stamps: List<PdfStampItem>,
    val flashcards: List<DocumentFlashcardItem>,
    val mindMapNodes: List<DocumentMindMapNode>,
    val redactions: List<PdfRedactionRect>,
    val voiceNotes: List<PageVoiceNoteItem>,
    val editedTextContent: String?
)

data class ExportResultSummary(
    val file: File,
    val format: SaveExportFormat,
    val profile: PdfSaveProfile?,
    val originalSizeBytes: Long,
    val exportedSizeBytes: Long
) {
    val savedPercentage: Int
        get() = if (originalSizeBytes > 0 && exportedSizeBytes < originalSizeBytes) {
            (((originalSizeBytes - exportedSizeBytes).toDouble() / originalSizeBytes.toDouble()) * 100.0)
                .toInt()
                .coerceIn(1, 85)
        } else 0
}

data class AbdocImportResult(
    val vaultItem: VaultDocumentItem,
    val restoredHighlights: Int,
    val restoredBookmarks: Int,
    val restoredStrokes: Int,
    val restoredStamps: Int,
    val restoredFlashcards: Int,
    val restoredVoiceNotes: Int
)

// ============================================================================
// 2. SERVICE & ENGINE (مدير الحفظ المتقدم، الخزنة، الإصدارات، وحزم .abdoc)
// ============================================================================

object DocumentVaultAndSaveManager {

    private const val PREFS_NAME = "doc_vault_and_save_prefs"
    private const val KEY_VAULT_ITEMS = "vault_items_json_v1"
    private const val KEY_VAULT_MASTER_PIN = "vault_master_pin_sha256"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getVaultDirectory(context: Context): File {
        return File(context.filesDir, "document_vault").apply {
            if (!exists()) mkdirs()
        }
    }

    private fun getSnapshotsDirectory(context: Context): File {
        return File(context.filesDir, "document_snapshots").apply {
            if (!exists()) mkdirs()
        }
    }

    private fun getExportsDirectory(context: Context): File {
        return File(context.cacheDir, "smart_exports").apply {
            if (!exists()) mkdirs()
        }
    }

    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ------------------------------------------------------------------------
    // FEATURE 1 & 6: MULTI-FORMAT EXPORT & PDF SAVE PROFILES (COMPRESSION/FLATTEN)
    // ------------------------------------------------------------------------

    fun exportDocumentWithFormatAndProfile(
        context: Context,
        sourceUri: Uri,
        sourceFileName: String,
        sourceExtension: String,
        format: SaveExportFormat,
        pdfProfile: PdfSaveProfile = PdfSaveProfile.HIGH_RES_PRINT,
        readableTextContent: String,
        highlights: List<DocumentHighlightItem>,
        bookmarks: List<DocumentBookmarkItem>,
        inkStrokes: List<PdfInkStroke>,
        stamps: List<PdfStampItem>,
        redactions: List<PdfRedactionRect>,
        flashcards: List<DocumentFlashcardItem>,
        voiceNotes: List<PageVoiceNoteItem>,
        pageRotations: Map<Int, Float> = emptyMap(),
        deletedPages: Set<Int> = emptySet()
    ): ExportResultSummary? {
        val baseName = sourceFileName.substringBeforeLast(".")
            .replace(Regex("""[^\w\u0600-\u06FF\- ]"""), "_")
            .trim()
            .ifBlank { "مستند_محفوظ" }

        val originalSize = try {
            MultiFormatDocumentEngine.getFileNameAndSize(context, sourceUri).second
        } catch (_: Exception) {
            0L
        }

        val outDir = getExportsDirectory(context)
        val timestampSuffix = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

        val outFile: File? = when (format) {
            SaveExportFormat.PDF -> {
                val profileTag = when (pdfProfile) {
                    PdfSaveProfile.HIGH_RES_PRINT -> "PrintHQ"
                    PdfSaveProfile.COMPRESSED_WEB -> "Compressed"
                    PdfSaveProfile.FLATTENED_PROTECTED -> "ProtectedFlat"
                }
                val targetFile = File(outDir, "${baseName}_${profileTag}_$timestampSuffix.pdf")
                if (sourceExtension.lowercase() == "pdf") {
                    renderPdfWithSaveProfile(
                        context = context,
                        sourceUri = sourceUri,
                        targetFile = targetFile,
                        documentTitle = baseName,
                        profile = pdfProfile,
                        inkStrokes = inkStrokes,
                        stamps = stamps,
                        redactions = redactions,
                        highlights = highlights,
                        pageRotations = pageRotations,
                        deletedPages = deletedPages
                    )
                } else {
                    renderTextAndAnnotationsToPdf(
                        targetFile = targetFile,
                        documentTitle = baseName,
                        profile = pdfProfile,
                        bodyText = readableTextContent,
                        highlights = highlights,
                        flashcards = flashcards,
                        voiceNotes = voiceNotes
                    )
                }
            }

            SaveExportFormat.DOCX -> {
                val targetFile = File(outDir, "${baseName}_$timestampSuffix.docx")
                exportRichArabicDocx(
                    targetFile = targetFile,
                    title = baseName,
                    bodyText = readableTextContent,
                    highlights = highlights,
                    flashcards = flashcards,
                    voiceNotes = voiceNotes
                )
            }

            SaveExportFormat.EPUB -> {
                val targetFile = File(outDir, "${baseName}_$timestampSuffix.epub")
                exportStandaloneArabicEpub(
                    targetFile = targetFile,
                    title = baseName,
                    bodyText = readableTextContent,
                    highlights = highlights,
                    flashcards = flashcards
                )
            }

            SaveExportFormat.HTML -> {
                val targetFile = File(outDir, "${baseName}_$timestampSuffix.html")
                val html = buildRichArabicHtmlDocument(
                    title = baseName,
                    bodyText = readableTextContent,
                    highlights = highlights,
                    bookmarks = bookmarks,
                    flashcards = flashcards,
                    voiceNotes = voiceNotes
                )
                targetFile.writeText(html, StandardCharsets.UTF_8)
                targetFile
            }

            SaveExportFormat.MARKDOWN -> {
                val targetFile = File(outDir, "${baseName}_$timestampSuffix.md")
                val md = buildStructuredMarkdownDocument(
                    title = baseName,
                    bodyText = readableTextContent,
                    highlights = highlights,
                    bookmarks = bookmarks,
                    flashcards = flashcards,
                    voiceNotes = voiceNotes
                )
                targetFile.writeText(md, StandardCharsets.UTF_8)
                targetFile
            }

            SaveExportFormat.TXT -> {
                val targetFile = File(outDir, "${baseName}_$timestampSuffix.txt")
                val txt = buildCleanPlainTextDocument(
                    title = baseName,
                    bodyText = readableTextContent,
                    highlights = highlights,
                    flashcards = flashcards,
                    voiceNotes = voiceNotes
                )
                targetFile.writeText(txt, StandardCharsets.UTF_8)
                targetFile
            }
        }

        if (outFile == null || !outFile.exists()) return null

        return ExportResultSummary(
            file = outFile,
            format = format,
            profile = if (format == SaveExportFormat.PDF) pdfProfile else null,
            originalSizeBytes = if (originalSize > 0) originalSize else outFile.length(),
            exportedSizeBytes = outFile.length()
        )
    }

    /**
     * Write any exported file directly to a user-chosen SAF destination URI
     */
    fun writeExportedFileToSafUri(
        context: Context,
        sourceFile: File,
        destinationUri: Uri
    ): Result<Long> {
        return try {
            val outStream = context.contentResolver.openOutputStream(destinationUri, "wt")
                ?: context.contentResolver.openOutputStream(destinationUri)
                ?: return Result.failure(IllegalStateException("تعذر فتح مجلد الحفظ المختار على الجهاز"))

            var bytesCopied = 0L
            FileInputStream(sourceFile).use { input ->
                outStream.use { output ->
                    bytesCopied = input.copyTo(output)
                    output.flush()
                }
            }
            Result.success(bytesCopied)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun renderPdfWithSaveProfile(
        context: Context,
        sourceUri: Uri,
        targetFile: File,
        documentTitle: String,
        profile: PdfSaveProfile,
        inkStrokes: List<PdfInkStroke>,
        stamps: List<PdfStampItem>,
        redactions: List<PdfRedactionRect>,
        highlights: List<DocumentHighlightItem>,
        pageRotations: Map<Int, Float>,
        deletedPages: Set<Int>
    ): File? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = if (sourceUri.scheme == "file") {
                ParcelFileDescriptor.open(File(sourceUri.path ?: ""), ParcelFileDescriptor.MODE_READ_ONLY)
            } else {
                context.contentResolver.openFileDescriptor(sourceUri, "r")
            } ?: return null

            renderer = PdfRenderer(pfd)
            val totalPages = renderer.pageCount
            if (totalPages <= 0) return null

            val outPdf = PdfDocument()
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

            var outputPageNumber = 1
            for (pageIdx in 0 until totalPages) {
                if (pageIdx in deletedPages) continue

                val page = renderer.openPage(pageIdx)
                val scale = profile.scaleFactor
                val rawW = (page.width * scale).toInt().coerceIn(420, 2400)
                val rawH = (page.height * scale).toInt().coerceIn(560, 3200)

                val pageBitmap = Bitmap.createBitmap(rawW, rawH, Bitmap.Config.ARGB_8888)
                pageBitmap.eraseColor(Color.WHITE)
                page.render(
                    pageBitmap,
                    null,
                    null,
                    if (profile == PdfSaveProfile.HIGH_RES_PRINT)
                        PdfRenderer.Page.RENDER_MODE_FOR_PRINT
                    else
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )
                page.close()

                val compositeCanvas = Canvas(pageBitmap)

                // Flatten annotations onto the bitmap canvas
                if (profile.flattenAnnotations) {
                    // 1. Redactions (Blackout boxes)
                    val pageRedactions = redactions.filter { it.pageIndex == pageIdx }
                    if (pageRedactions.isNotEmpty()) {
                        val redactPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.FILL
                            color = Color.BLACK
                        }
                        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.WHITE
                            textSize = (rawW * 0.018f).coerceAtLeast(11f)
                            textAlign = Paint.Align.CENTER
                            isFakeBoldText = true
                        }
                        for (r in pageRedactions) {
                            val rect = RectF(
                                r.leftNorm * rawW,
                                r.topNorm * rawH,
                                r.rightNorm * rawW,
                                r.bottomNorm * rawH
                            )
                            compositeCanvas.drawRect(rect, redactPaint)
                            compositeCanvas.drawText(
                                r.label.ifBlank { "محتوى مطموس" },
                                rect.centerX(),
                                rect.centerY() + (labelPaint.textSize * 0.35f),
                                labelPaint
                            )
                        }
                    }

                    // 2. Freehand Ink Strokes
                    val pageStrokes = inkStrokes.filter { it.pageIndex == pageIdx }
                    for (stroke in pageStrokes) {
                        if (stroke.points.size < 2) continue
                        val strokeColor = try {
                            Color.parseColor(stroke.colorHex)
                        } catch (_: Exception) {
                            Color.RED
                        }
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = strokeColor
                            alpha = (stroke.alpha * 255).toInt().coerceIn(30, 255)
                            style = Paint.Style.STROKE
                            strokeWidth = stroke.strokeWidth * scale
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                        }
                        val path = Path()
                        val first = stroke.points.first()
                        path.moveTo(first.xNorm * rawW, first.yNorm * rawH)
                        for (i in 1 until stroke.points.size) {
                            val pt = stroke.points[i]
                            path.lineTo(pt.xNorm * rawW, pt.yNorm * rawH)
                        }
                        compositeCanvas.drawPath(path, paint)
                    }

                    // 3. Official Stamps & Electronic Signatures
                    val pageStamps = stamps.filter { it.pageIndex == pageIdx }
                    for (stamp in pageStamps) {
                        val stampColor = try {
                            Color.parseColor(stamp.colorHex)
                        } catch (_: Exception) {
                            Color.parseColor("#1D4ED8")
                        }
                        val cx = stamp.xNorm * rawW
                        val cy = stamp.yNorm * rawH
                        val boxW = rawW * 0.28f
                        val boxH = rawH * 0.065f
                        val rect = RectF(cx - boxW / 2f, cy - boxH / 2f, cx + boxW / 2f, cy + boxH / 2f)

                        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.FILL
                            color = stampColor
                            alpha = 28
                        }
                        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE
                            color = stampColor
                            strokeWidth = 3f * scale
                        }
                        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = stampColor
                            isFakeBoldText = true
                            textSize = (rawW * 0.022f).coerceAtLeast(14f)
                            textAlign = Paint.Align.CENTER
                        }
                        compositeCanvas.drawRoundRect(rect, 12f, 12f, bgPaint)
                        compositeCanvas.drawRoundRect(rect, 12f, 12f, borderPaint)
                        compositeCanvas.drawText(
                            stamp.label,
                            cx,
                            cy + (textPaint.textSize * 0.35f),
                            textPaint
                        )
                    }
                }

                // 4. Optional Flattened Read-Only Protection Footer & Seal
                if (profile.addProtectionWatermark) {
                    val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#0F172A")
                        alpha = 180
                        textSize = (rawW * 0.016f).coerceAtLeast(10f)
                        textAlign = Paint.Align.CENTER
                        isFakeBoldText = true
                    }
                    compositeCanvas.drawText(
                        "🔒 نسخة مدمجة ومحمية ضد التعديل (Flattened Protected PDF) • $documentTitle • $dateStr",
                        rawW / 2f,
                        rawH - (rawH * 0.018f).coerceAtLeast(12f),
                        footerPaint
                    )
                }

                // 5. Apply compression profile if COMPRESSED_WEB
                val finalBitmapForPdf = if (profile == PdfSaveProfile.COMPRESSED_WEB) {
                    val baos = ByteArrayOutputStream()
                    pageBitmap.compress(Bitmap.CompressFormat.JPEG, profile.jpegQuality, baos)
                    val jpegBytes = baos.toByteArray()
                    pageBitmap.recycle()
                    BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: continue
                } else {
                    pageBitmap
                }

                val pageInfo = PdfDocument.PageInfo.Builder(rawW, rawH, outputPageNumber++).create()
                val pdfPage = outPdf.startPage(pageInfo)
                val rotation = pageRotations[pageIdx] ?: 0f
                if (rotation != 0f) {
                    pdfPage.canvas.save()
                    pdfPage.canvas.rotate(rotation, rawW / 2f, rawH / 2f)
                    pdfPage.canvas.drawBitmap(finalBitmapForPdf, 0f, 0f, null)
                    pdfPage.canvas.restore()
                } else {
                    pdfPage.canvas.drawBitmap(finalBitmapForPdf, 0f, 0f, null)
                }
                outPdf.finishPage(pdfPage)
                finalBitmapForPdf.recycle()
            }

            // Append Highlights & Notes Summary Page if highlights exist
            if (highlights.isNotEmpty()) {
                val summaryPageInfo = PdfDocument.PageInfo.Builder(794, 1123, outputPageNumber).create()
                val summaryPage = outPdf.startPage(summaryPageInfo)
                val c = summaryPage.canvas
                c.drawColor(Color.WHITE)

                val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#1E3A8A")
                    textSize = 22f
                    isFakeBoldText = true
                    textAlign = Paint.Align.RIGHT
                }
                val itemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#1E293B")
                    textSize = 14f
                    textAlign = Paint.Align.RIGHT
                }
                var curY = 65f
                c.drawText("ملحق التظليلات والملاحظات المرجعية (${highlights.size})", 734f, curY, headerPaint)
                curY += 36f
                for ((idx, h) in highlights.take(18).withIndex()) {
                    if (curY > 1040f) break
                    val snippet = h.snippetText.replace("\n", " ").take(85)
                    c.drawText("${idx + 1}. [$h.positionLabel] «$snippet»", 734f, curY, itemPaint)
                    curY += 22f
                    if (h.note.isNotBlank()) {
                        c.drawText("   💡 ملاحظة: ${h.note.take(80)}", 734f, curY, itemPaint)
                        curY += 22f
                    }
                    curY += 8f
                }
                outPdf.finishPage(summaryPage)
            }

            FileOutputStream(targetFile).use { outPdf.writeTo(it) }
            outPdf.close()
            targetFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    private fun renderTextAndAnnotationsToPdf(
        targetFile: File,
        documentTitle: String,
        profile: PdfSaveProfile,
        bodyText: String,
        highlights: List<DocumentHighlightItem>,
        flashcards: List<DocumentFlashcardItem>,
        voiceNotes: List<PageVoiceNoteItem>
    ): File? {
        return try {
            val pageW = if (profile == PdfSaveProfile.COMPRESSED_WEB) 595 else 794
            val pageH = if (profile == PdfSaveProfile.COMPRESSED_WEB) 842 else 1123
            val margin = (pageW * 0.08f).toInt()
            val contentWidth = pageW - (margin * 2)

            val fullTextBuilder = StringBuilder()
            fullTextBuilder.append(documentTitle).append("\n\n")
            fullTextBuilder.append(bodyText.ifBlank { "محتوى المستند المحفوظ: $documentTitle" })

            if (highlights.isNotEmpty()) {
                fullTextBuilder.append("\n\n────────────────────────\n")
                fullTextBuilder.append("التظليلات والاقتباسات المرجعية (${highlights.size}):\n")
                highlights.forEachIndexed { idx, h ->
                    fullTextBuilder.append("${idx + 1}. [${h.positionLabel}] ${h.snippetText}\n")
                    if (h.note.isNotBlank()) {
                        fullTextBuilder.append("   ملاحظة: ${h.note}\n")
                    }
                }
            }

            if (flashcards.isNotEmpty()) {
                fullTextBuilder.append("\n\n────────────────────────\n")
                fullTextBuilder.append("البطاقات التعليمية والمصطلحات (${flashcards.size}):\n")
                flashcards.forEachIndexed { idx, card ->
                    fullTextBuilder.append("${idx + 1}. ${card.termOrQuestion} : ${card.definitionOrAnswer}\n")
                }
            }

            if (voiceNotes.isNotEmpty()) {
                fullTextBuilder.append("\n\n────────────────────────\n")
                fullTextBuilder.append("الملاحظات الصوتية الموثقة (${voiceNotes.size}):\n")
                voiceNotes.forEachIndexed { idx, vn ->
                    fullTextBuilder.append("${idx + 1}. [${vn.positionLabel}] ${vn.title}: ${vn.transcriptOrSummary}\n")
                }
            }

            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                textSize = if (profile == PdfSaveProfile.COMPRESSED_WEB) 12f else 15f
                typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            }

            val fullText = fullTextBuilder.toString()
            val staticLayout = StaticLayout.Builder
                .obtain(fullText, 0, fullText.length, textPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(6f, 1.25f)
                .build()

            val pdfDoc = PdfDocument()
            val lineCount = staticLayout.lineCount
            val usableHeight = pageH - (margin * 2)
            var currentLine = 0
            var pageNum = 1

            while (currentLine < lineCount) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageNum++).create()
                val page = pdfDoc.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)

                val startTop = staticLayout.getLineTop(currentLine)
                var endLine = currentLine
                while (endLine < lineCount && (staticLayout.getLineBottom(endLine) - startTop) <= usableHeight) {
                    endLine++
                }
                if (endLine == currentLine) endLine++

                val endBottom = staticLayout.getLineBottom(endLine - 1)
                canvas.save()
                canvas.translate(margin.toFloat(), margin.toFloat())
                canvas.clipRect(0, 0, contentWidth, endBottom - startTop)
                canvas.translate(0f, -startTop.toFloat())
                staticLayout.draw(canvas)
                canvas.restore()

                if (profile.addProtectionWatermark) {
                    val wmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.GRAY
                        textSize = 10f
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.drawText(
                        "🔒 نسخة محمية ومدمجة • $documentTitle",
                        pageW / 2f,
                        pageH - 18f,
                        wmPaint
                    )
                }

                pdfDoc.finishPage(page)
                currentLine = endLine
            }

            FileOutputStream(targetFile).use { pdfDoc.writeTo(it) }
            pdfDoc.close()
            targetFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun exportRichArabicDocx(
        targetFile: File,
        title: String,
        bodyText: String,
        highlights: List<DocumentHighlightItem>,
        flashcards: List<DocumentFlashcardItem>,
        voiceNotes: List<PageVoiceNoteItem>
    ): File? {
        return try {
            val paragraphs = bodyText.split(Regex("\\n\\s*\\n|\\n"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toMutableList()

            if (highlights.isNotEmpty()) {
                paragraphs.add("────────────────────────")
                paragraphs.add("الاقتباسات والتظليلات المرجعية (${highlights.size}):")
                highlights.forEachIndexed { idx, h ->
                    paragraphs.add("${idx + 1}. [${h.positionLabel}] «${h.snippetText}»" +
                        if (h.note.isNotBlank()) " — ملاحظة: ${h.note}" else "")
                }
            }

            if (flashcards.isNotEmpty()) {
                paragraphs.add("────────────────────────")
                paragraphs.add("البطاقات التعليمية والمصطلحات (${flashcards.size}):")
                flashcards.forEachIndexed { idx, c ->
                    paragraphs.add("${idx + 1}. ${c.termOrQuestion}: ${c.definitionOrAnswer}")
                }
            }

            if (voiceNotes.isNotEmpty()) {
                paragraphs.add("────────────────────────")
                paragraphs.add("الملاحظات الصوتية (${voiceNotes.size}):")
                voiceNotes.forEachIndexed { idx, v ->
                    paragraphs.add("${idx + 1}. [${v.positionLabel}] ${v.title}: ${v.transcriptOrSummary}")
                }
            }

            ZipOutputStream(FileOutputStream(targetFile)).use { zos ->
                addZipTextEntry(
                    zos,
                    "[Content_Types].xml",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
    <Default Extension="xml" ContentType="application/xml"/>
    <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""
                )

                addZipTextEntry(
                    zos,
                    "_rels/.rels",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""
                )

                val sb = StringBuilder()
                sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                sb.append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>""")
                sb.append("""<w:p><w:pPr><w:jc w:val="center"/><w:bidi/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val="40"/><w:rtl/></w:rPr><w:t>${escapeXml(title)}</w:t></w:r></w:p>""")

                for (p in paragraphs) {
                    sb.append("""<w:p><w:pPr><w:jc w:val="both"/><w:bidi/><w:spacing w:line="360" w:lineRule="auto"/></w:pPr><w:r><w:rPr><w:sz w:val="26"/><w:rtl/></w:rPr><w:t>${escapeXml(p)}</w:t></w:r></w:p>""")
                }

                sb.append("""<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr>""")
                sb.append("""</w:body></w:document>""")
                addZipTextEntry(zos, "word/document.xml", sb.toString())
            }
            targetFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun exportStandaloneArabicEpub(
        targetFile: File,
        title: String,
        bodyText: String,
        highlights: List<DocumentHighlightItem>,
        flashcards: List<DocumentFlashcardItem>
    ): File? {
        return try {
            ZipOutputStream(FileOutputStream(targetFile)).use { zos ->
                // 1. Uncompressed mimetype
                val mimeBytes = "application/epub+zip".toByteArray(StandardCharsets.US_ASCII)
                val mimeEntry = ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = mimeBytes.size.toLong()
                    compressedSize = mimeBytes.size.toLong()
                    crc = CRC32().apply { update(mimeBytes) }.value
                }
                zos.putNextEntry(mimeEntry)
                zos.write(mimeBytes)
                zos.closeEntry()

                // 2. container.xml
                addZipTextEntry(
                    zos,
                    "META-INF/container.xml",
                    """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""
                )

                // 3. CSS
                addZipTextEntry(
                    zos,
                    "OEBPS/style.css",
                    """body { direction: rtl; text-align: justify; font-family: 'Amiri', 'Tajawal', serif; line-height: 1.85; margin: 5%; color: #1E293B; }
h1, h2 { color: #1D4ED8; text-align: center; }
.note-box { background: #FEF3C7; border-right: 4px solid #D97706; padding: 10px; margin: 10px 0; border-radius: 6px; }"""
                )

                // 4. Chapter XHTML
                val paragraphsHtml = bodyText.split(Regex("\\n\\s*\\n|\\n"))
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .joinToString("\n") { "<p>${escapeXml(it)}</p>" }

                val notesHtml = if (highlights.isNotEmpty() || flashcards.isNotEmpty()) {
                    buildString {
                        append("<h2>ملحق الملاحظات والبطاقات المرجعية</h2>")
                        highlights.forEach { h ->
                            append("<div class='note-box'><strong>${escapeXml(h.positionLabel)}:</strong> «${escapeXml(h.snippetText)}»")
                            if (h.note.isNotBlank()) append("<br/><em>ملاحظة: ${escapeXml(h.note)}</em>")
                            append("</div>")
                        }
                        flashcards.forEach { c ->
                            append("<div class='note-box'><strong>بطاقة: ${escapeXml(c.termOrQuestion)}</strong><br/>${escapeXml(c.definitionOrAnswer)}</div>")
                        }
                    }
                } else ""

                val chapterXhtml = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="ar" dir="rtl">
<head>
  <title>${escapeXml(title)}</title>
  <link rel="stylesheet" type="text/css" href="style.css"/>
</head>
<body>
  <h1>${escapeXml(title)}</h1>
  $paragraphsHtml
  $notesHtml
</body>
</html>"""
                addZipTextEntry(zos, "OEBPS/chapter1.xhtml", chapterXhtml)

                // 5. content.opf
                val opf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId" version="3.0" xml:lang="ar">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="BookId">urn:uuid:${UUID.randomUUID()}</dc:identifier>
    <dc:title>${escapeXml(title)}</dc:title>
    <dc:language>ar</dc:language>
  </metadata>
  <manifest>
    <item id="css" href="style.css" media-type="text/css"/>
    <item id="ch1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine page-progression-direction="rtl">
    <itemref idref="ch1"/>
  </spine>
</package>"""
                addZipTextEntry(zos, "OEBPS/content.opf", opf)
            }
            targetFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun buildRichArabicHtmlDocument(
        title: String,
        bodyText: String,
        highlights: List<DocumentHighlightItem>,
        bookmarks: List<DocumentBookmarkItem>,
        flashcards: List<DocumentFlashcardItem>,
        voiceNotes: List<PageVoiceNoteItem>
    ): String {
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val paragraphsHtml = bodyText.split(Regex("\\n\\s*\\n|\\n"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n") { "<p>${escapeXml(it)}</p>" }

        return buildString {
            append("""<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1.0"/>
<title>${escapeXml(title)}</title>
<style>
  body { font-family: 'Tajawal', 'Amiri', system-ui, sans-serif; background: #F8FAFC; color: #0F172A; line-height: 1.9; margin: 0; padding: 24px; }
  .container { max-width: 860px; margin: 0 auto; background: #FFFFFF; padding: 36px; border-radius: 18px; box-shadow: 0 8px 30px rgba(0,0,0,0.06); }
  h1 { color: #1E3A8A; border-bottom: 3px solid #DBEAFE; padding-bottom: 12px; }
  h2 { color: #0F766E; margin-top: 28px; }
  .meta { color: #64748B; font-size: 0.9rem; margin-bottom: 24px; }
  .card { background: #FFFBEB; border-right: 4px solid #F59E0B; padding: 12px 16px; border-radius: 8px; margin-bottom: 10px; }
  .flashcard { background: #F5F3FF; border-right: 4px solid #7C3AED; padding: 12px 16px; border-radius: 8px; margin-bottom: 10px; }
</style>
</head>
<body>
<div class="container">
  <h1>${escapeXml(title)}</h1>
  <div class="meta">تاريخ التصدير: $dateStr • التظليلات: ${highlights.size} • العلامات: ${bookmarks.size} • البطاقات: ${flashcards.size}</div>
  <div class="content">
    $paragraphsHtml
  </div>
""")
            if (highlights.isNotEmpty()) {
                append("<h2>التظليلات والاقتباسات المرجعية (${highlights.size})</h2>")
                highlights.forEach { h ->
                    append("<div class='card'><strong>[${escapeXml(h.positionLabel)}]</strong> «${escapeXml(h.snippetText)}»")
                    if (h.note.isNotBlank()) append("<br/><small>💡 ${escapeXml(h.note)}</small>")
                    append("</div>")
                }
            }
            if (flashcards.isNotEmpty()) {
                append("<h2>البطاقات التعليمية والمصطلحات (${flashcards.size})</h2>")
                flashcards.forEach { f ->
                    append("<div class='flashcard'><strong>س/مصطلح: ${escapeXml(f.termOrQuestion)}</strong><br/>ج: ${escapeXml(f.definitionOrAnswer)}</div>")
                }
            }
            if (voiceNotes.isNotEmpty()) {
                append("<h2>الملاحظات الصوتية (${voiceNotes.size})</h2>")
                voiceNotes.forEach { v ->
                    append("<div class='card'><strong>🎙️ ${escapeXml(v.title)} (${escapeXml(v.positionLabel)}):</strong><br/>${escapeXml(v.transcriptOrSummary)}</div>")
                }
            }
            append("</div></body></html>")
        }
    }

    private fun buildStructuredMarkdownDocument(
        title: String,
        bodyText: String,
        highlights: List<DocumentHighlightItem>,
        bookmarks: List<DocumentBookmarkItem>,
        flashcards: List<DocumentFlashcardItem>,
        voiceNotes: List<PageVoiceNoteItem>
    ): String {
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        return buildString {
            append("# $title\n\n")
            append("> **تاريخ الحفظ:** `$dateStr` | **التظليلات:** `${highlights.size}` | **العلامات:** `${bookmarks.size}` | **البطاقات:** `${flashcards.size}`\n\n")
            append("---\n\n")
            append(bodyText.ifBlank { "_(مستند محفوظ)_" })
            append("\n\n")

            if (highlights.isNotEmpty()) {
                append("## 🖍️ التظليلات والاقتباسات المرجعية (${highlights.size})\n\n")
                highlights.forEachIndexed { idx, h ->
                    append("${idx + 1}. **[${h.positionLabel}]**: «${h.snippetText}»\n")
                    if (h.note.isNotBlank()) {
                        append("   - 💡 *ملاحظة:* ${h.note}\n")
                    }
                }
                append("\n")
            }

            if (flashcards.isNotEmpty()) {
                append("## 🎴 البطاقات التعليمية والمصطلحات (${flashcards.size})\n\n")
                flashcards.forEachIndexed { idx, c ->
                    append("- **${idx + 1}. ${c.termOrQuestion}** (${c.positionLabel})\n")
                    append("  - الإجابة/التعريف: ${c.definitionOrAnswer}\n")
                }
                append("\n")
            }

            if (voiceNotes.isNotEmpty()) {
                append("## 🎙️ الملاحظات الصوتية الموثقة (${voiceNotes.size})\n\n")
                voiceNotes.forEachIndexed { idx, v ->
                    append("- **${idx + 1}. ${v.title}** (${v.positionLabel} • ${v.durationSeconds} ثانية): ${v.transcriptOrSummary}\n")
                }
                append("\n")
            }
        }
    }

    private fun buildCleanPlainTextDocument(
        title: String,
        bodyText: String,
        highlights: List<DocumentHighlightItem>,
        flashcards: List<DocumentFlashcardItem>,
        voiceNotes: List<PageVoiceNoteItem>
    ): String {
        return buildString {
            append("========================================\n")
            append("العنوان: $title\n")
            append("========================================\n\n")
            append(bodyText)
            append("\n\n")
            if (highlights.isNotEmpty()) {
                append("----------------------------------------\n")
                append("التظليلات والاقتباسات (${highlights.size}):\n")
                highlights.forEachIndexed { i, h ->
                    append("${i + 1}. [${h.positionLabel}] ${h.snippetText}\n")
                    if (h.note.isNotBlank()) append("   ملاحظة: ${h.note}\n")
                }
            }
            if (flashcards.isNotEmpty()) {
                append("----------------------------------------\n")
                append("البطاقات التعليمية (${flashcards.size}):\n")
                flashcards.forEachIndexed { i, f ->
                    append("${i + 1}. ${f.termOrQuestion} -> ${f.definitionOrAnswer}\n")
                }
            }
            if (voiceNotes.isNotEmpty()) {
                append("----------------------------------------\n")
                append("الملاحظات الصوتية (${voiceNotes.size}):\n")
                voiceNotes.forEachIndexed { i, v ->
                    append("${i + 1}. [${v.positionLabel}] ${v.title}: ${v.transcriptOrSummary}\n")
                }
            }
        }
    }

    // ------------------------------------------------------------------------
    // FEATURE 2: PERMANENT LOCAL DOCUMENT VAULT & PIN PROTECTION
    // ------------------------------------------------------------------------

    fun getVaultDocuments(context: Context): List<VaultDocumentItem> {
        val raw = prefs(context).getString(KEY_VAULT_ITEMS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<VaultDocumentItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val localPath = obj.optString("localFilePath", "")
                if (localPath.isNotBlank() && File(localPath).exists()) {
                    val localUri = Uri.fromFile(File(localPath)).toString()
                    val annCount = countAnnotationsForUri(context, localUri)
                    list.add(
                        VaultDocumentItem(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            title = obj.optString("title", "مستند محفوظ"),
                            fileName = obj.optString("fileName", "document.pdf"),
                            extension = obj.optString("extension", "pdf"),
                            localFilePath = localPath,
                            originalUriString = obj.optString("originalUriString", localUri),
                            sizeBytes = File(localPath).length(),
                            addedTimestamp = obj.optLong("addedTimestamp", System.currentTimeMillis()),
                            lastModifiedTimestamp = obj.optLong("lastModifiedTimestamp", System.currentTimeMillis()),
                            isPinLocked = obj.optBoolean("isPinLocked", false),
                            pinSha256 = obj.optString("pinSha256", ""),
                            categoryLabel = obj.optString("categoryLabel", "خزنة المستندات"),
                            annotationsCount = annCount
                        )
                    )
                }
            }
            list.sortedByDescending { it.lastModifiedTimestamp }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun saveVaultDocumentsList(context: Context, items: List<VaultDocumentItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("fileName", item.fileName)
                put("extension", item.extension)
                put("localFilePath", item.localFilePath)
                put("originalUriString", item.originalUriString)
                put("sizeBytes", item.sizeBytes)
                put("addedTimestamp", item.addedTimestamp)
                put("lastModifiedTimestamp", item.lastModifiedTimestamp)
                put("isPinLocked", item.isPinLocked)
                put("pinSha256", item.pinSha256)
                put("categoryLabel", item.categoryLabel)
            }
            arr.put(obj)
        }
        prefs(context).edit().putString(KEY_VAULT_ITEMS, arr.toString()).apply()
    }

    /**
     * Imports or saves a document permanently into the app's internal `document_vault` folder
     * and migrates/copies all its annotations so they are bound to the permanent Vault URI too.
     */
    fun saveDocumentToVault(
        context: Context,
        sourceUri: Uri,
        fileName: String,
        customTitle: String = fileName.substringBeforeLast("."),
        pinCode: String = "",
        categoryLabel: String = "مرجع محفوظ"
    ): Result<VaultDocumentItem> {
        return try {
            val vaultDir = getVaultDirectory(context)
            val ext = MultiFormatDocumentEngine.getFileExtension(fileName).ifBlank { "pdf" }
            val safeBase = fileName.substringBeforeLast(".")
                .replace(Regex("""[^\w\u0600-\u06FF\- ]"""), "_")
                .trim()
                .ifBlank { "vault_doc" }

            val existingItems = getVaultDocuments(context).toMutableList()
            val existingMatch = existingItems.firstOrNull {
                it.originalUriString == sourceUri.toString() || it.localUriString == sourceUri.toString()
            }

            val destFile = if (existingMatch != null) {
                File(existingMatch.localFilePath)
            } else {
                File(vaultDir, "${safeBase}_${System.currentTimeMillis() % 100000}.$ext")
            }

            // Copy file bytes if source is not already the exact vault file
            val sourcePath = if (sourceUri.scheme == "file") sourceUri.path else null
            if (sourcePath == null || File(sourcePath).canonicalPath != destFile.canonicalPath) {
                val inputStream = if (sourceUri.scheme == "file") {
                    FileInputStream(File(sourceUri.path ?: ""))
                } else {
                    context.contentResolver.openInputStream(sourceUri)
                } ?: return Result.failure(IllegalStateException("تعذر قراءة الملف المصدر لنسخه إلى الخزنة"))

                inputStream.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                }
            }

            val vaultUriString = Uri.fromFile(destFile).toString()
            // Copy all annotations from sourceUri to vaultUriString so nothing is lost
            if (sourceUri.toString() != vaultUriString) {
                copyWorkspaceAnnotationsBetweenUris(context, sourceUri.toString(), vaultUriString)
            }

            val cleanPin = pinCode.trim()
            val pinHash = if (cleanPin.isNotEmpty()) sha256(cleanPin) else (existingMatch?.pinSha256 ?: "")
            val isLocked = cleanPin.isNotEmpty() || (existingMatch?.isPinLocked == true && pinHash.isNotEmpty())

            val newItem = VaultDocumentItem(
                id = existingMatch?.id ?: UUID.randomUUID().toString(),
                title = customTitle.ifBlank { safeBase },
                fileName = destFile.name,
                extension = ext,
                localFilePath = destFile.absolutePath,
                originalUriString = sourceUri.toString(),
                sizeBytes = destFile.length(),
                addedTimestamp = existingMatch?.addedTimestamp ?: System.currentTimeMillis(),
                lastModifiedTimestamp = System.currentTimeMillis(),
                isPinLocked = isLocked,
                pinSha256 = pinHash,
                categoryLabel = categoryLabel,
                annotationsCount = countAnnotationsForUri(context, vaultUriString)
            )

            val updatedList = existingItems.filter { it.id != newItem.id } + newItem
            saveVaultDocumentsList(context, updatedList)
            Result.success(newItem)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    fun setVaultItemPinLock(
        context: Context,
        vaultItemId: String,
        enableLock: Boolean,
        newPinCode: String
    ): Boolean {
        val list = getVaultDocuments(context).toMutableList()
        val index = list.indexOfFirst { it.id == vaultItemId }
        if (index < 0) return false
        val item = list[index]
        val hash = if (enableLock && newPinCode.isNotBlank()) sha256(newPinCode.trim()) else ""
        list[index] = item.copy(
            isPinLocked = enableLock && hash.isNotEmpty(),
            pinSha256 = hash,
            lastModifiedTimestamp = System.currentTimeMillis()
        )
        saveVaultDocumentsList(context, list)
        return true
    }

    fun verifyVaultItemPin(item: VaultDocumentItem, enteredPin: String): Boolean {
        if (!item.isPinLocked || item.pinSha256.isBlank()) return true
        return sha256(enteredPin.trim()) == item.pinSha256
    }

    fun deleteVaultDocument(context: Context, vaultItemId: String): Boolean {
        val list = getVaultDocuments(context).toMutableList()
        val target = list.firstOrNull { it.id == vaultItemId } ?: return false
        try {
            File(target.localFilePath).delete()
        } catch (_: Exception) {}
        val updated = list.filter { it.id != vaultItemId }
        saveVaultDocumentsList(context, updated)
        return true
    }

    fun isDocumentInVault(context: Context, uriString: String): VaultDocumentItem? {
        return getVaultDocuments(context).firstOrNull {
            it.localUriString == uriString || it.originalUriString == uriString
        }
    }

    private fun countAnnotationsForUri(context: Context, uriString: String): Int {
        return MultiFormatDocumentEngine.getDocumentHighlights(context, uriString).size +
            MultiFormatDocumentEngine.getDocumentBookmarks(context, uriString).size +
            MultiFormatDocumentEngine.getPdfInkStrokes(context, uriString).size +
            MultiFormatDocumentEngine.getPdfStamps(context, uriString).size +
            MultiFormatDocumentEngine.getDocumentFlashcards(context, uriString).size +
            MultiFormatDocumentEngine.getPageVoiceNotes(context, uriString).size
    }

    private fun copyWorkspaceAnnotationsBetweenUris(
        context: Context,
        fromUri: String,
        toUri: String
    ) {
        val highlights = MultiFormatDocumentEngine.getDocumentHighlights(context, fromUri)
            .map { it.copy(uriString = toUri) }
        if (highlights.isNotEmpty()) {
            MultiFormatDocumentEngine.saveDocumentHighlights(context, toUri, highlights)
        }

        val bookmarks = MultiFormatDocumentEngine.getDocumentBookmarks(context, fromUri)
            .map { it.copy(uriString = toUri) }
        if (bookmarks.isNotEmpty()) {
            MultiFormatDocumentEngine.saveDocumentBookmarks(context, toUri, bookmarks)
        }

        val strokes = MultiFormatDocumentEngine.getPdfInkStrokes(context, fromUri)
        if (strokes.isNotEmpty()) {
            MultiFormatDocumentEngine.savePdfInkStrokes(context, toUri, strokes)
        }

        val stamps = MultiFormatDocumentEngine.getPdfStamps(context, fromUri)
        if (stamps.isNotEmpty()) {
            MultiFormatDocumentEngine.savePdfStamps(context, toUri, stamps)
        }

        val flashcards = MultiFormatDocumentEngine.getDocumentFlashcards(context, fromUri)
            .map { it.copy(uriString = toUri) }
        if (flashcards.isNotEmpty()) {
            MultiFormatDocumentEngine.saveDocumentFlashcards(context, toUri, flashcards)
        }

        val mindMap = MultiFormatDocumentEngine.getCustomMindMapNodes(context, fromUri)
        if (mindMap.isNotEmpty()) {
            MultiFormatDocumentEngine.saveCustomMindMapNodes(context, toUri, mindMap)
        }

        val redactions = MultiFormatDocumentEngine.getPdfRedactions(context, fromUri)
        if (redactions.isNotEmpty()) {
            MultiFormatDocumentEngine.savePdfRedactions(context, toUri, redactions)
        }

        val voiceNotes = MultiFormatDocumentEngine.getPageVoiceNotes(context, fromUri)
            .map { it.copy(uriString = toUri) }
        if (voiceNotes.isNotEmpty()) {
            MultiFormatDocumentEngine.savePageVoiceNotes(context, toUri, voiceNotes)
        }
    }

    // ------------------------------------------------------------------------
    // FEATURE 3: VERSION HISTORY & RESTORE SNAPSHOTS (سجل الإصدارات واللقطات)
    // ------------------------------------------------------------------------

    private fun snapshotKey(uriString: String) = "snapshots_v1_${uriString.hashCode()}"

    fun getDocumentSnapshots(context: Context, uriString: String): List<DocumentVersionSnapshot> {
        val raw = prefs(context).getString(snapshotKey(uriString), null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<DocumentVersionSnapshot>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    DocumentVersionSnapshot(
                        id = obj.optString("id"),
                        documentUriString = obj.optString("documentUriString", uriString),
                        documentName = obj.optString("documentName", ""),
                        versionNumber = obj.optInt("versionNumber", i + 1),
                        label = obj.optString("label", "نسخة محفوظة"),
                        isAutoSnapshot = obj.optBoolean("isAutoSnapshot", false),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        highlightsCount = obj.optInt("highlightsCount", 0),
                        bookmarksCount = obj.optInt("bookmarksCount", 0),
                        inkStrokesCount = obj.optInt("inkStrokesCount", 0),
                        stampsCount = obj.optInt("stampsCount", 0),
                        flashcardsCount = obj.optInt("flashcardsCount", 0),
                        voiceNotesCount = obj.optInt("voiceNotesCount", 0),
                        redactionsCount = obj.optInt("redactionsCount", 0),
                        textLength = obj.optInt("textLength", 0),
                        snapshotPayloadJson = obj.optString("snapshotPayloadJson", "{}"),
                        backupFilePath = obj.optString("backupFilePath", "")
                    )
                )
            }
            list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun createDocumentSnapshot(
        context: Context,
        uriString: String,
        documentName: String,
        label: String,
        isAutoSnapshot: Boolean = false,
        highlights: List<DocumentHighlightItem>,
        bookmarks: List<DocumentBookmarkItem>,
        inkStrokes: List<PdfInkStroke>,
        stamps: List<PdfStampItem>,
        flashcards: List<DocumentFlashcardItem>,
        mindMapNodes: List<DocumentMindMapNode>,
        redactions: List<PdfRedactionRect>,
        voiceNotes: List<PageVoiceNoteItem>,
        editedTextContent: String = ""
    ): DocumentVersionSnapshot {
        val existing = getDocumentSnapshots(context, uriString).toMutableList()
        val nextVersion = (existing.maxOfOrNull { it.versionNumber } ?: 0) + 1

        val payloadJson = serializeWorkspaceStateToJson(
            uriString = uriString,
            documentName = documentName,
            highlights = highlights,
            bookmarks = bookmarks,
            inkStrokes = inkStrokes,
            stamps = stamps,
            flashcards = flashcards,
            mindMapNodes = mindMapNodes,
            redactions = redactions,
            voiceNotes = voiceNotes,
            editedTextContent = editedTextContent
        )

        val snapshot = DocumentVersionSnapshot(
            id = UUID.randomUUID().toString(),
            documentUriString = uriString,
            documentName = documentName,
            versionNumber = nextVersion,
            label = label.ifBlank { "الإصدار #$nextVersion" },
            isAutoSnapshot = isAutoSnapshot,
            timestamp = System.currentTimeMillis(),
            highlightsCount = highlights.size,
            bookmarksCount = bookmarks.size,
            inkStrokesCount = inkStrokes.size,
            stampsCount = stamps.size,
            flashcardsCount = flashcards.size,
            voiceNotesCount = voiceNotes.size,
            redactionsCount = redactions.size,
            textLength = editedTextContent.length,
            snapshotPayloadJson = payloadJson
        )

        // Keep up to 25 recent snapshots per document
        val updated = (listOf(snapshot) + existing).take(25)
        saveDocumentSnapshotsList(context, uriString, updated)
        return snapshot
    }

    fun restoreDocumentSnapshot(
        context: Context,
        uriString: String,
        snapshot: DocumentVersionSnapshot
    ): RestoredDocumentWorkspaceState {
        val state = deserializeWorkspaceStateFromJson(uriString, snapshot.snapshotPayloadJson)
        // Persist all restored items back into MultiFormatDocumentEngine
        MultiFormatDocumentEngine.saveDocumentHighlights(context, uriString, state.highlights)
        MultiFormatDocumentEngine.saveDocumentBookmarks(context, uriString, state.bookmarks)
        MultiFormatDocumentEngine.savePdfInkStrokes(context, uriString, state.inkStrokes)
        MultiFormatDocumentEngine.savePdfStamps(context, uriString, state.stamps)
        MultiFormatDocumentEngine.saveDocumentFlashcards(context, uriString, state.flashcards)
        MultiFormatDocumentEngine.saveCustomMindMapNodes(context, uriString, state.mindMapNodes)
        MultiFormatDocumentEngine.savePdfRedactions(context, uriString, state.redactions)
        MultiFormatDocumentEngine.savePageVoiceNotes(context, uriString, state.voiceNotes)
        return state
    }

    fun deleteDocumentSnapshot(
        context: Context,
        uriString: String,
        snapshotId: String
    ): List<DocumentVersionSnapshot> {
        val updated = getDocumentSnapshots(context, uriString).filter { it.id != snapshotId }
        saveDocumentSnapshotsList(context, uriString, updated)
        return updated
    }

    private fun saveDocumentSnapshotsList(
        context: Context,
        uriString: String,
        snapshots: List<DocumentVersionSnapshot>
    ) {
        val arr = JSONArray()
        snapshots.forEach { s ->
            val obj = JSONObject().apply {
                put("id", s.id)
                put("documentUriString", s.documentUriString)
                put("documentName", s.documentName)
                put("versionNumber", s.versionNumber)
                put("label", s.label)
                put("isAutoSnapshot", s.isAutoSnapshot)
                put("timestamp", s.timestamp)
                put("highlightsCount", s.highlightsCount)
                put("bookmarksCount", s.bookmarksCount)
                put("inkStrokesCount", s.inkStrokesCount)
                put("stampsCount", s.stampsCount)
                put("flashcardsCount", s.flashcardsCount)
                put("voiceNotesCount", s.voiceNotesCount)
                put("redactionsCount", s.redactionsCount)
                put("textLength", s.textLength)
                put("snapshotPayloadJson", s.snapshotPayloadJson)
                put("backupFilePath", s.backupFilePath)
            }
            arr.put(obj)
        }
        prefs(context).edit().putString(snapshotKey(uriString), arr.toString()).apply()
    }

    // ------------------------------------------------------------------------
    // FEATURE 4: COMPREHENSIVE DOCUMENT BUNDLE (.abdoc ZIP ARCHIVE)
    // ------------------------------------------------------------------------

    /**
     * Packages the original document binary + full JSON manifest of all annotations,
     * drawings, stamps, flashcards, voice notes, redactions, mind maps, and edited text
     * into a portable `.abdoc` bundle file.
     */
    fun exportComprehensiveAbdocBundle(
        context: Context,
        sourceUri: Uri,
        sourceFileName: String,
        highlights: List<DocumentHighlightItem>,
        bookmarks: List<DocumentBookmarkItem>,
        inkStrokes: List<PdfInkStroke>,
        stamps: List<PdfStampItem>,
        flashcards: List<DocumentFlashcardItem>,
        mindMapNodes: List<DocumentMindMapNode>,
        redactions: List<PdfRedactionRect>,
        voiceNotes: List<PageVoiceNoteItem>,
        editedTextContent: String = ""
    ): Result<File> {
        return try {
            val outDir = getExportsDirectory(context)
            val baseName = sourceFileName.substringBeforeLast(".")
                .replace(Regex("""[^\w\u0600-\u06FF\- ]"""), "_")
                .trim()
                .ifBlank { "document_bundle" }

            val bundleFile = File(outDir, "${baseName}_bundle_${System.currentTimeMillis() % 100000}.abdoc")

            val payloadJson = serializeWorkspaceStateToJson(
                uriString = sourceUri.toString(),
                documentName = sourceFileName,
                highlights = highlights,
                bookmarks = bookmarks,
                inkStrokes = inkStrokes,
                stamps = stamps,
                flashcards = flashcards,
                mindMapNodes = mindMapNodes,
                redactions = redactions,
                voiceNotes = voiceNotes,
                editedTextContent = editedTextContent
            )

            ZipOutputStream(FileOutputStream(bundleFile)).use { zos ->
                // 1. Add workspace manifest JSON
                addZipTextEntry(zos, "abdoc_manifest.json", payloadJson)

                // 2. Add original document binary under `document/<sourceFileName>`
                val cleanDocEntryName = "document/${sourceFileName.replace("/", "_")}"
                val inputStream = if (sourceUri.scheme == "file") {
                    FileInputStream(File(sourceUri.path ?: ""))
                } else {
                    context.contentResolver.openInputStream(sourceUri)
                }

                if (inputStream != null) {
                    inputStream.use { input ->
                        val entry = ZipEntry(cleanDocEntryName)
                        zos.putNextEntry(entry)
                        input.copyTo(zos)
                        zos.closeEntry()
                    }
                } else if (editedTextContent.isNotBlank()) {
                    addZipTextEntry(zos, "document/${baseName}.txt", editedTextContent)
                }
            }

            Result.success(bundleFile)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Imports a `.abdoc` bundle from any URI, extracts the embedded document directly into
     * the permanent Local Document Vault (`document_vault`), and restores all its annotations!
     */
    fun importComprehensiveAbdocBundle(
        context: Context,
        bundleUri: Uri
    ): Result<AbdocImportResult> {
        return try {
            val inputStream = if (bundleUri.scheme == "file") {
                FileInputStream(File(bundleUri.path ?: ""))
            } else {
                context.contentResolver.openInputStream(bundleUri)
            } ?: return Result.failure(IllegalStateException("تعذر فتح ملف الحزمة .abdoc"))

            val vaultDir = getVaultDirectory(context)
            var manifestJson: String? = null
            var extractedDocFile: File? = null
            var extractedOriginalName = "restored_document.pdf"

            ZipInputStream(inputStream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory) {
                        if (name == "abdoc_manifest.json" || name.endsWith("manifest.json")) {
                            val baos = ByteArrayOutputStream()
                            zis.copyTo(baos)
                            manifestJson = baos.toString("UTF-8")
                        } else if (name.startsWith("document/")) {
                            extractedOriginalName = name.substringAfter("document/").ifBlank { "document.pdf" }
                            val ext = MultiFormatDocumentEngine.getFileExtension(extractedOriginalName).ifBlank { "pdf" }
                            val safeBase = extractedOriginalName.substringBeforeLast(".")
                                .replace(Regex("""[^\w\u0600-\u06FF\- ]"""), "_")
                                .ifBlank { "bundle_doc" }
                            val outFile = File(vaultDir, "${safeBase}_${System.currentTimeMillis() % 100000}.$ext")
                            FileOutputStream(outFile).use { fos ->
                                zis.copyTo(fos)
                                fos.flush()
                            }
                            extractedDocFile = outFile
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (extractedDocFile == null) {
                return Result.failure(IllegalStateException("لا تحتوي الحزمة على ملف مستند صالح"))
            }

            val vaultUriString = Uri.fromFile(extractedDocFile!!).toString()
            var restoredState = RestoredDocumentWorkspaceState(
                emptyList(), emptyList(), emptyList(), emptyList(),
                emptyList(), emptyList(), emptyList(), emptyList(), null
            )

            if (!manifestJson.isNullOrBlank()) {
                restoredState = deserializeWorkspaceStateFromJson(vaultUriString, manifestJson!!)
                MultiFormatDocumentEngine.saveDocumentHighlights(context, vaultUriString, restoredState.highlights)
                MultiFormatDocumentEngine.saveDocumentBookmarks(context, vaultUriString, restoredState.bookmarks)
                MultiFormatDocumentEngine.savePdfInkStrokes(context, vaultUriString, restoredState.inkStrokes)
                MultiFormatDocumentEngine.savePdfStamps(context, vaultUriString, restoredState.stamps)
                MultiFormatDocumentEngine.saveDocumentFlashcards(context, vaultUriString, restoredState.flashcards)
                MultiFormatDocumentEngine.saveCustomMindMapNodes(context, vaultUriString, restoredState.mindMapNodes)
                MultiFormatDocumentEngine.savePdfRedactions(context, vaultUriString, restoredState.redactions)
                MultiFormatDocumentEngine.savePageVoiceNotes(context, vaultUriString, restoredState.voiceNotes)
            }

            // Register in Vault
            val vaultRes = saveDocumentToVault(
                context = context,
                sourceUri = Uri.fromFile(extractedDocFile!!),
                fileName = extractedOriginalName,
                customTitle = extractedOriginalName.substringBeforeLast("."),
                categoryLabel = "حزمة مستعادة (.abdoc)"
            ).getOrThrow()

            Result.success(
                AbdocImportResult(
                    vaultItem = vaultRes,
                    restoredHighlights = restoredState.highlights.size,
                    restoredBookmarks = restoredState.bookmarks.size,
                    restoredStrokes = restoredState.inkStrokes.size,
                    restoredStamps = restoredState.stamps.size,
                    restoredFlashcards = restoredState.flashcards.size,
                    restoredVoiceNotes = restoredState.voiceNotes.size
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    // ------------------------------------------------------------------------
    // FEATURE 5: SMART AUTO-SAVE & CRASH/SESSION RECOVERY CHECKPOINTS
    // ------------------------------------------------------------------------

    private fun recoveryKey(uriString: String) = "recovery_checkpoint_${uriString.hashCode()}"
    private fun dirtyFlagKey(uriString: String) = "recovery_dirty_${uriString.hashCode()}"

    fun recordAutoSaveCheckpoint(
        context: Context,
        uriString: String,
        documentName: String,
        highlights: List<DocumentHighlightItem>,
        bookmarks: List<DocumentBookmarkItem>,
        inkStrokes: List<PdfInkStroke>,
        stamps: List<PdfStampItem>,
        flashcards: List<DocumentFlashcardItem>,
        mindMapNodes: List<DocumentMindMapNode>,
        redactions: List<PdfRedactionRect>,
        voiceNotes: List<PageVoiceNoteItem>,
        editedTextContent: String = "",
        markSessionClean: Boolean = false
    ) {
        // Persist all live annotations immediately
        MultiFormatDocumentEngine.saveDocumentHighlights(context, uriString, highlights)
        MultiFormatDocumentEngine.saveDocumentBookmarks(context, uriString, bookmarks)
        MultiFormatDocumentEngine.savePdfInkStrokes(context, uriString, inkStrokes)
        MultiFormatDocumentEngine.savePdfStamps(context, uriString, stamps)
        MultiFormatDocumentEngine.saveDocumentFlashcards(context, uriString, flashcards)
        MultiFormatDocumentEngine.saveCustomMindMapNodes(context, uriString, mindMapNodes)
        MultiFormatDocumentEngine.savePdfRedactions(context, uriString, redactions)
        MultiFormatDocumentEngine.savePageVoiceNotes(context, uriString, voiceNotes)

        val payloadJson = serializeWorkspaceStateToJson(
            uriString = uriString,
            documentName = documentName,
            highlights = highlights,
            bookmarks = bookmarks,
            inkStrokes = inkStrokes,
            stamps = stamps,
            flashcards = flashcards,
            mindMapNodes = mindMapNodes,
            redactions = redactions,
            voiceNotes = voiceNotes,
            editedTextContent = editedTextContent
        )

        val summary = "تظليلات: ${highlights.size} • رسومات: ${inkStrokes.size} • أختام: ${stamps.size} • بطاقات: ${flashcards.size}"
        val checkpointObj = JSONObject().apply {
            put("documentUriString", uriString)
            put("documentName", documentName)
            put("timestamp", System.currentTimeMillis())
            put("summaryLabel", summary)
            put("payloadJson", payloadJson)
            put("editedTextContent", editedTextContent)
        }

        prefs(context).edit()
            .putString(recoveryKey(uriString), checkpointObj.toString())
            .putBoolean(dirtyFlagKey(uriString), !markSessionClean)
            .apply()
    }

    fun markSessionCleanOnNormalExit(context: Context, uriString: String) {
        prefs(context).edit().putBoolean(dirtyFlagKey(uriString), false).apply()
    }

    fun markSessionActiveDirty(context: Context, uriString: String) {
        prefs(context).edit().putBoolean(dirtyFlagKey(uriString), true).apply()
    }

    fun checkPendingRecoveryCheckpoint(
        context: Context,
        uriString: String
    ): DocumentRecoveryCheckpoint? {
        val wasDirty = prefs(context).getBoolean(dirtyFlagKey(uriString), false)
        if (!wasDirty) return null
        val raw = prefs(context).getString(recoveryKey(uriString), null) ?: return null
        return try {
            val obj = JSONObject(raw)
            DocumentRecoveryCheckpoint(
                documentUriString = obj.optString("documentUriString", uriString),
                documentName = obj.optString("documentName", ""),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                summaryLabel = obj.optString("summaryLabel", ""),
                payloadJson = obj.optString("payloadJson", "{}"),
                editedTextContent = obj.optString("editedTextContent", "")
            )
        } catch (_: Exception) {
            null
        }
    }

    fun restoreFromRecoveryCheckpoint(
        context: Context,
        uriString: String,
        checkpoint: DocumentRecoveryCheckpoint
    ): RestoredDocumentWorkspaceState {
        val state = deserializeWorkspaceStateFromJson(uriString, checkpoint.payloadJson)
        MultiFormatDocumentEngine.saveDocumentHighlights(context, uriString, state.highlights)
        MultiFormatDocumentEngine.saveDocumentBookmarks(context, uriString, state.bookmarks)
        MultiFormatDocumentEngine.savePdfInkStrokes(context, uriString, state.inkStrokes)
        MultiFormatDocumentEngine.savePdfStamps(context, uriString, state.stamps)
        MultiFormatDocumentEngine.saveDocumentFlashcards(context, uriString, state.flashcards)
        MultiFormatDocumentEngine.saveCustomMindMapNodes(context, uriString, state.mindMapNodes)
        MultiFormatDocumentEngine.savePdfRedactions(context, uriString, state.redactions)
        MultiFormatDocumentEngine.savePageVoiceNotes(context, uriString, state.voiceNotes)
        markSessionCleanOnNormalExit(context, uriString)
        return state
    }

    // ------------------------------------------------------------------------
    // JSON SERIALIZATION / DESERIALIZATION OF WORKSPACE STATE
    // ------------------------------------------------------------------------

    private fun serializeWorkspaceStateToJson(
        uriString: String,
        documentName: String,
        highlights: List<DocumentHighlightItem>,
        bookmarks: List<DocumentBookmarkItem>,
        inkStrokes: List<PdfInkStroke>,
        stamps: List<PdfStampItem>,
        flashcards: List<DocumentFlashcardItem>,
        mindMapNodes: List<DocumentMindMapNode>,
        redactions: List<PdfRedactionRect>,
        voiceNotes: List<PageVoiceNoteItem>,
        editedTextContent: String
    ): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("uriString", uriString)
        root.put("documentName", documentName)
        root.put("timestamp", System.currentTimeMillis())
        root.put("editedTextContent", editedTextContent)

        // Highlights
        val hlArr = JSONArray()
        highlights.forEach { h ->
            hlArr.put(JSONObject().apply {
                put("id", h.id)
                put("snippetText", h.snippetText)
                put("note", h.note)
                put("colorHex", h.colorHex)
                put("positionIndex", h.positionIndex)
                put("positionLabel", h.positionLabel)
                put("timestamp", h.timestamp)
            })
        }
        root.put("highlights", hlArr)

        // Bookmarks
        val bmArr = JSONArray()
        bookmarks.forEach { b ->
            bmArr.put(JSONObject().apply {
                put("id", b.id)
                put("label", b.label)
                put("positionIndex", b.positionIndex)
                put("timestamp", b.timestamp)
            })
        }
        root.put("bookmarks", bmArr)

        // Ink Strokes
        val strokeArr = JSONArray()
        inkStrokes.forEach { s ->
            val ptsArr = JSONArray()
            s.points.forEach { pt ->
                ptsArr.put(JSONObject().apply {
                    put("x", pt.xNorm.toDouble())
                    put("y", pt.yNorm.toDouble())
                })
            }
            strokeArr.put(JSONObject().apply {
                put("id", s.id)
                put("pageIndex", s.pageIndex)
                put("colorHex", s.colorHex)
                put("strokeWidth", s.strokeWidth.toDouble())
                put("alpha", s.alpha.toDouble())
                put("points", ptsArr)
            })
        }
        root.put("inkStrokes", strokeArr)

        // Stamps
        val stampArr = JSONArray()
        stamps.forEach { st ->
            stampArr.put(JSONObject().apply {
                put("id", st.id)
                put("pageIndex", st.pageIndex)
                put("label", st.label)
                put("xNorm", st.xNorm.toDouble())
                put("yNorm", st.yNorm.toDouble())
                put("colorHex", st.colorHex)
                put("isSignature", st.isSignature)
            })
        }
        root.put("stamps", stampArr)

        // Flashcards
        val fcArr = JSONArray()
        flashcards.forEach { fc ->
            fcArr.put(JSONObject().apply {
                put("id", fc.id)
                put("termOrQuestion", fc.termOrQuestion)
                put("definitionOrAnswer", fc.definitionOrAnswer)
                put("positionLabel", fc.positionLabel)
                put("isMastered", fc.isMastered)
                put("timestamp", fc.timestamp)
            })
        }
        root.put("flashcards", fcArr)

        // MindMap Nodes
        val mmArr = JSONArray()
        mindMapNodes.forEach { m ->
            mmArr.put(JSONObject().apply {
                put("id", m.id)
                put("title", m.title)
                put("subtitle", m.subtitle)
                put("category", m.category)
                put("positionIndex", m.positionIndex)
                put("parentId", m.parentId ?: "")
                put("colorHex", m.colorHex)
            })
        }
        root.put("mindMapNodes", mmArr)

        // Redactions
        val redArr = JSONArray()
        redactions.forEach { r ->
            redArr.put(JSONObject().apply {
                put("id", r.id)
                put("pageIndex", r.pageIndex)
                put("leftNorm", r.leftNorm.toDouble())
                put("topNorm", r.topNorm.toDouble())
                put("rightNorm", r.rightNorm.toDouble())
                put("bottomNorm", r.bottomNorm.toDouble())
                put("label", r.label)
            })
        }
        root.put("redactions", redArr)

        // Voice Notes
        val vnArr = JSONArray()
        voiceNotes.forEach { vn ->
            vnArr.put(JSONObject().apply {
                put("id", vn.id)
                put("pageOrSectionIndex", vn.pageOrSectionIndex)
                put("positionLabel", vn.positionLabel)
                put("title", vn.title)
                put("transcriptOrSummary", vn.transcriptOrSummary)
                put("durationSeconds", vn.durationSeconds)
                put("audioFilePath", vn.audioFilePath)
                put("timestamp", vn.timestamp)
            })
        }
        root.put("voiceNotes", vnArr)

        return root.toString()
    }

    private fun deserializeWorkspaceStateFromJson(
        targetUriString: String,
        jsonString: String
    ): RestoredDocumentWorkspaceState {
        return try {
            val root = JSONObject(jsonString)
            val editedText = root.optString("editedTextContent", "").takeIf { it.isNotBlank() }

            val highlights = mutableListOf<DocumentHighlightItem>()
            root.optJSONArray("highlights")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    highlights.add(
                        DocumentHighlightItem(
                            id = o.optString("id", UUID.randomUUID().toString()),
                            uriString = targetUriString,
                            snippetText = o.optString("snippetText"),
                            note = o.optString("note"),
                            colorHex = o.optString("colorHex", "#FDE047"),
                            positionIndex = o.optInt("positionIndex", 0),
                            positionLabel = o.optString("positionLabel", "صفحة 1"),
                            timestamp = o.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }

            val bookmarks = mutableListOf<DocumentBookmarkItem>()
            root.optJSONArray("bookmarks")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    bookmarks.add(
                        DocumentBookmarkItem(
                            id = o.optString("id", UUID.randomUUID().toString()),
                            uriString = targetUriString,
                            label = o.optString("label", "علامة مرجعية"),
                            positionIndex = o.optInt("positionIndex", 0),
                            timestamp = o.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }

            val inkStrokes = mutableListOf<PdfInkStroke>()
            root.optJSONArray("inkStrokes")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val pts = mutableListOf<PdfInkPoint>()
                    o.optJSONArray("points")?.let { pArr ->
                        for (j in 0 until pArr.length()) {
                            val po = pArr.getJSONObject(j)
                            pts.add(
                                PdfInkPoint(
                                    xNorm = po.optDouble("x", 0.0).toFloat(),
                                    yNorm = po.optDouble("y", 0.0).toFloat()
                                )
                            )
                        }
                    }
                    inkStrokes.add(
                        PdfInkStroke(
                            id = o.optString("id", UUID.randomUUID().toString()),
                            pageIndex = o.optInt("pageIndex", 0),
                            points = pts,
                            colorHex = o.optString("colorHex", "#EF4444"),
                            strokeWidth = o.optDouble("strokeWidth", 4.0).toFloat(),
                            alpha = o.optDouble("alpha", 0.85).toFloat()
                        )
                    )
                }
            }

            val stamps = mutableListOf<PdfStampItem>()
            root.optJSONArray("stamps")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    stamps.add(
                        PdfStampItem(
                            id = o.optString("id", UUID.randomUUID().toString()),
                            pageIndex = o.optInt("pageIndex", 0),
                            label = o.optString("label", "معتمد"),
                            xNorm = o.optDouble("xNorm", 0.5).toFloat(),
                            yNorm = o.optDouble("yNorm", 0.8).toFloat(),
                            colorHex = o.optString("colorHex", "#1D4ED8"),
                            isSignature = o.optBoolean("isSignature", false)
                        )
                    )
                }
            }

            val flashcards = mutableListOf<DocumentFlashcardItem>()
            root.optJSONArray("flashcards")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    flashcards.add(
                        DocumentFlashcardItem(
                            id = o.optString("id", UUID.randomUUID().toString()),
                            uriString = targetUriString,
                            termOrQuestion = o.optString("termOrQuestion"),
                            definitionOrAnswer = o.optString("definitionOrAnswer"),
                            positionLabel = o.optString("positionLabel", "صفحة 1"),
                            isMastered = o.optBoolean("isMastered", false),
                            timestamp = o.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }

            val mindMapNodes = mutableListOf<DocumentMindMapNode>()
            root.optJSONArray("mindMapNodes")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    mindMapNodes.add(
                        DocumentMindMapNode(
                            id = o.optString("id", UUID.randomUUID().toString()),
                            title = o.optString("title"),
                            subtitle = o.optString("subtitle"),
                            category = o.optString("category", "CUSTOM"),
                            positionIndex = o.optInt("positionIndex", 0),
                            parentId = o.optString("parentId").takeIf { it.isNotBlank() },
                            colorHex = o.optString("colorHex", "#3B82F6")
                        )
                    )
                }
            }

            val redactions = mutableListOf<PdfRedactionRect>()
            root.optJSONArray("redactions")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    redactions.add(
                        PdfRedactionRect(
                            id = o.optString("id", UUID.randomUUID().toString()),
                            pageIndex = o.optInt("pageIndex", 0),
                            leftNorm = o.optDouble("leftNorm", 0.1).toFloat(),
                            topNorm = o.optDouble("topNorm", 0.1).toFloat(),
                            rightNorm = o.optDouble("rightNorm", 0.5).toFloat(),
                            bottomNorm = o.optDouble("bottomNorm", 0.2).toFloat(),
                            label = o.optString("label", "محتوى مطموس")
                        )
                    )
                }
            }

            val voiceNotes = mutableListOf<PageVoiceNoteItem>()
            root.optJSONArray("voiceNotes")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    voiceNotes.add(
                        PageVoiceNoteItem(
                            id = o.optString("id", UUID.randomUUID().toString()),
                            uriString = targetUriString,
                            pageOrSectionIndex = o.optInt("pageOrSectionIndex", 0),
                            positionLabel = o.optString("positionLabel", "صفحة 1"),
                            title = o.optString("title", "ملاحظة صوتية"),
                            transcriptOrSummary = o.optString("transcriptOrSummary"),
                            durationSeconds = o.optInt("durationSeconds", 5),
                            audioFilePath = o.optString("audioFilePath", ""),
                            timestamp = o.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }

            RestoredDocumentWorkspaceState(
                highlights = highlights,
                bookmarks = bookmarks,
                inkStrokes = inkStrokes,
                stamps = stamps,
                flashcards = flashcards,
                mindMapNodes = mindMapNodes,
                redactions = redactions,
                voiceNotes = voiceNotes,
                editedTextContent = editedText
            )
        } catch (e: Exception) {
            e.printStackTrace()
            RestoredDocumentWorkspaceState(
                emptyList(), emptyList(), emptyList(), emptyList(),
                emptyList(), emptyList(), emptyList(), emptyList(), null
            )
        }
    }

    private fun addZipTextEntry(zos: ZipOutputStream, entryName: String, content: String) {
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        zos.write(bytes, 0, bytes.size)
        zos.closeEntry()
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    fun formatSizeArabic(bytes: Long): String {
        if (bytes <= 0L) return "0 بايت"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format(Locale.US, "%.2f ميجابايت", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f كيلوبايت", kb)
            else -> "$bytes بايت"
        }
    }
}
