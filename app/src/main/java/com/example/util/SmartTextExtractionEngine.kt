package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import com.example.data.ai.GeminiAiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * محرك استخراج النصوص والتعرف الضوئي الذكي المتقدم (الجيل السادس)
 * يضم 6 ترقيات متكاملة:
 * 1. التحديد الذكي بالمنطقة (Smart Area / Lasso Selection OCR)
 * 2. مختبر تنظيف ومعالجة النص العربي (Arabic Post-Processing & Normalization Lab)
 * 3. استخراج الجداول والبيانات المهيكلة (Table & Structured Data Extractor -> CSV / Markdown)
 * 4. المستخرج الذكي للكيانات والاقتباسات والمصطلحات (Smart Entity & Citation Extractor)
 * 5. فلاتر تحسين الصفحات الممسوحة قبل الاستخراج (Pre-OCR Scanner Enhancement Filters)
 * 6. الاستخراج الدفعي الموجه ومسار التصدير الفوري (Batch Page Range & Direct Action Pipeline)
 */
object SmartTextExtractionEngine {

    // =========================================================================
    // 1. نماذج البيانات (Data Models)
    // =========================================================================

    enum class PreOcrFilter(val titleAr: String, val subtitleAr: String) {
        ORIGINAL("الأصل بدون فلتر", "الصورة الأصلية للصفحة كما هي"),
        ADAPTIVE_BINARIZE("إزالة الظلال وتبييض", "تحويل عالي التباين (أبيض وأسود) لإزالة ظلال التصوير"),
        CONTRAST_SHARPEN("تعزيز التباين والحدة", "توضيح الحروف الباهتة والتشكيل الدقيق"),
        GRAYSCALE_CLEAN("رمادي نقي", "إزالة التشويش اللوني وتوحيد الإضاءة"),
        INVERT_CLEAN("عكس الإضاءة الداكنة", "للصفحات ذات الخلفيات الداكنة أو المقلوبة")
    }

    enum class AreaPreset(val titleAr: String, val rectNorm: RectF) {
        FULL_PAGE("كامل الصفحة", RectF(0.02f, 0.02f, 0.98f, 0.98f)),
        RIGHT_COLUMN("العمود الأيمن", RectF(0.50f, 0.04f, 0.97f, 0.95f)),
        LEFT_COLUMN("العمود الأيسر", RectF(0.03f, 0.04f, 0.50f, 0.95f)),
        TOP_HALF("النصف العلوي", RectF(0.04f, 0.04f, 0.96f, 0.50f)),
        BOTTOM_HALF("النصف السفلي", RectF(0.04f, 0.50f, 0.96f, 0.96f)),
        FOOTNOTES("الحواشي والهامش السفلي", RectF(0.04f, 0.72f, 0.96f, 0.97f)),
        HEADER_TITLE("العنوان والترويسة", RectF(0.05f, 0.03f, 0.95f, 0.24f))
    }

    enum class EntityCategory(val titleAr: String, val badgeColorHex: String) {
        QUOTES_CITATIONS("الاقتباسات والمراجع", "#D97706"),
        DATES_STATS("التواريخ والأرقام والإحصائيات", "#0284C7"),
        LINKS_CONTACTS("الروابط وجهات الاتصال", "#059669"),
        KEY_TERMS("المصطلحات والمفاهيم المفتاحية", "#7C3AED")
    }

    data class ExtractedEntityItem(
        val id: String = "ent_${System.nanoTime()}",
        val text: String,
        val contextSnippet: String,
        val category: EntityCategory,
        val subTypeLabel: String,
        val frequency: Int = 1
    )

    data class ExtractedTableData(
        val title: String = "جدول مستخرج",
        val headers: List<String>,
        val rows: List<List<String>>
    )

    enum class BatchRangeMode(val titleAr: String) {
        CUSTOM_RANGE("نطاق صفحات مخصص"),
        BOOKMARKED_ONLY("الصفحات المرجعية (Bookmarks)"),
        ALL_PAGES("جميع صفحات المستند"),
        ODD_PAGES("الصفحات الفردية فقط"),
        EVEN_PAGES("الصفحات الزوجية فقط")
    }

    data class BatchProgressInfo(
        val currentPage: Int,
        val totalPagesToProcess: Int,
        val statusMessage: String,
        val percentage: Float
    )

    data class PostProcessConfig(
        val stripAllTashkeel: Boolean = false,
        val keepBasicTashkeelOnly: Boolean = false,
        val removeTatweel: Boolean = true,
        val normalizeHamzas: Boolean = false,
        val fixBrokenArabic: Boolean = true,
        val smartUnwrapLines: Boolean = false,
        val numeralMode: String = "KEEP", // "KEEP", "EASTERN", "WESTERN"
        val cleanPunctuationSpaces: Boolean = true
    )

    // =========================================================================
    // 2. فلاتر تحسين الصفحات الممسوحة قبل الاستخراج (Pre-OCR Scanner Filters)
    // =========================================================================

    /**
     * يطبق فلتر التحسين المسبق وتصحيح الميلان (Deskew) وقص المنطقة المحددة على Bitmap الصفحة
     */
    fun applyPreOcrEnhancement(
        source: Bitmap,
        filter: PreOcrFilter = PreOcrFilter.ORIGINAL,
        deskewDegrees: Float = 0f,
        cropRectNorm: RectF? = null
    ): Bitmap {
        // 1. Crop first if normalized rect is provided
        val cropped = if (cropRectNorm != null) {
            cropBitmapByNormalizedRect(source, cropRectNorm)
        } else {
            source
        }

        // 2. Apply Deskew rotation if non-zero
        val rotated = if (kotlin.math.abs(deskewDegrees) > 0.1f) {
            val matrix = Matrix().apply {
                postRotate(deskewDegrees, cropped.width / 2f, cropped.height / 2f)
            }
            val rotBmp = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(rotBmp)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(cropped, matrix, paint)
            rotBmp
        } else {
            cropped
        }

        // 3. Apply Scanner Enhancement Filter
        return when (filter) {
            PreOcrFilter.ORIGINAL -> rotated

            PreOcrFilter.GRAYSCALE_CLEAN -> {
                val out = Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(out)
                val cm = ColorMatrix().apply { setSaturation(0f) }
                // Slight contrast boost for clean grayscale
                val contrast = 1.18f
                val translate = (-0.5f * contrast + 0.5f) * 255f
                cm.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            contrast, 0f, 0f, 0f, translate,
                            0f, contrast, 0f, 0f, translate,
                            0f, 0f, contrast, 0f, translate,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
                val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
                canvas.drawBitmap(rotated, 0f, 0f, paint)
                out
            }

            PreOcrFilter.CONTRAST_SHARPEN -> {
                val out = Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(out)
                val contrast = 1.55f
                val brightness = 12f
                val translate = (-0.5f * contrast + 0.5f) * 255f + brightness
                val cm = ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
                canvas.drawBitmap(rotated, 0f, 0f, paint)
                out
            }

            PreOcrFilter.ADAPTIVE_BINARIZE -> {
                binarizeRemoveShadows(rotated)
            }

            PreOcrFilter.INVERT_CLEAN -> {
                val out = Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(out)
                val cm = ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
                canvas.drawBitmap(rotated, 0f, 0f, paint)
                out
            }
        }
    }

    /**
     * خوارزمية إزالة الظلال وتبييض الخلفية للمستندات الممسوحة ضوئياً (Adaptive Binarization)
     */
    private fun binarizeRemoveShadows(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) return src
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        // Compute average luminance to adapt threshold
        var lumSum = 0L
        for (px in pixels) {
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            lumSum += (r * 299 + g * 587 + b * 114) / 1000
        }
        val avgLum = (lumSum / pixels.size.coerceAtLeast(1)).toInt()
        val threshold = (avgLum * 0.86f).roundToInt().coerceIn(115, 205)

        for (i in pixels.indices) {
            val px = pixels[i]
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            val lum = (r * 299 + g * 587 + b * 114) / 1000
            pixels[i] = if (lum < threshold) {
                Color.rgb(20, 24, 33) // Deep crisp ink
            } else {
                Color.WHITE // Pure white background
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * قص Bitmap بناءً على إحداثيات معيارية (0f..1f)
     */
    fun cropBitmapByNormalizedRect(source: Bitmap, rectNorm: RectF): Bitmap {
        val left = (min(rectNorm.left, rectNorm.right).coerceIn(0f, 0.95f) * source.width).roundToInt()
        val top = (min(rectNorm.top, rectNorm.bottom).coerceIn(0f, 0.95f) * source.height).roundToInt()
        val right = (max(rectNorm.left, rectNorm.right).coerceIn(0.05f, 1f) * source.width).roundToInt()
        val bottom = (max(rectNorm.top, rectNorm.bottom).coerceIn(0.05f, 1f) * source.height).roundToInt()

        val cropW = (right - left).coerceAtLeast(16).coerceAtMost(source.width - left)
        val cropH = (bottom - top).coerceAtLeast(16).coerceAtMost(source.height - top)

        return try {
            Bitmap.createBitmap(source, left, top, cropW, cropH)
        } catch (_: Exception) {
            source
        }
    }

    /**
     * توليد صفحة معاينة نقطية (Synthetic A4 Page Bitmap) للمستندات النصية وملفات Word/EPUB/TXT
     * لتمكين التحديد بالمنطقة (Lasso Crop) وتجربة فلاتر الماسح الضوئي وتصحيح الميلان على كافة الصيغ
     */
    fun renderSyntheticTextPageBitmap(
        text: String,
        documentTitle: String = "مستند نصي",
        pageIndex: Int = 0,
        totalPages: Int = 1
    ): Bitmap {
        val width = 840
        val height = 1160
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Warm paper background so Pre-OCR Scanner Filters (Binarization, Shadow Removal, Contrast, Invert) show realistic visual differences
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(248, 246, 240)
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Subtle simulated scanner edge shadow so AUTO_SHADOW_REMOVAL / BINARIZATION visibly clean it
        val borderShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(226, 222, 212)
            style = Paint.Style.STROKE
            strokeWidth = 18f
        }
        canvas.drawRect(9f, 9f, width - 9f, height - 9f, borderShadowPaint)

        // Header bar
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 20f
            textAlign = Paint.Align.RIGHT
            isFakeBoldText = true
        }
        val safeTitle = documentTitle.take(42)
        canvas.drawText(
            "📄 $safeTitle — صفحة ${pageIndex + 1} من ${totalPages.coerceAtLeast(1)}",
            width - 48f,
            52f,
            headerPaint
        )

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 2f
        }
        canvas.drawLine(48f, 68f, width - 48f, 68f, linePaint)

        // Prepare wrapped lines for the requested virtual page
        val rawLines = text.ifBlank {
            "المستند جاهز للاستخراج الذكي.\nاستخدم إطار التحديد بالمنطقة أو فلاتر الماسح الضوئي أو مختبر تنظيف النص العربي."
        }.lines().map { it.trim() }.filter { it.isNotBlank() }

        val wrappedLines = mutableListOf<String>()
        for (paragraph in rawLines) {
            val words = paragraph.split(Regex("\\s+"))
            var currentLine = StringBuilder()
            for (w in words) {
                if (currentLine.length + w.length + 1 > 54) {
                    wrappedLines.add(currentLine.toString())
                    currentLine = StringBuilder(w)
                } else {
                    if (currentLine.isNotEmpty()) currentLine.append(" ")
                    currentLine.append(w)
                }
            }
            if (currentLine.isNotEmpty()) {
                wrappedLines.add(currentLine.toString())
            }
        }

        val linesPerPage = 22
        val startLine = (pageIndex.coerceAtLeast(0) * linesPerPage).coerceAtMost((wrappedLines.size - 1).coerceAtLeast(0))
        val pageLines = wrappedLines.drop(startLine).take(linesPerPage).ifEmpty { wrappedLines.take(linesPerPage) }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 26f
            textAlign = Paint.Align.RIGHT
        }

        var y = 118f
        for (line in pageLines) {
            if (y > height - 64f) break
            canvas.drawText(line, width - 52f, y, textPaint)
            y += 44f
        }

        return bmp
    }

    /**
     * تصيير صفحة PDF أو صورة أو مستند نصي إلى Bitmap للمعاينة والقص
     */
    suspend fun renderDocumentPageOrImage(
        context: Context,
        uri: Uri?,
        pageIndex: Int = 0,
        scaleFactor: Float = 1.8f,
        fallbackText: String = "",
        documentTitle: String = "مستند"
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (uri != null) {
            // 1. Try PDF first
            val pdfBmp = MultiFormatDocumentEngine.renderPdfPage(context, uri, pageIndex, scaleFactor)
            if (pdfBmp != null) return@withContext pdfBmp

            // 2. Try decoding as Image
            val imgBmp = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (_: Exception) {
                null
            }
            if (imgBmp != null) return@withContext imgBmp

            // 3. Non-PDF/Non-Image document (e.g. DOCX, TXT, EPUB, MD, HTML): parse text and synthesize page bitmap
            val docText = fallbackText.ifBlank {
                try {
                    DocumentParser.parseUri(context, uri).text
                } catch (_: Exception) {
                    ""
                }
            }
            val virtualTotalPages = ((docText.lines().count { it.isNotBlank() } / 22) + 1).coerceAtLeast(1)
            return@withContext renderSyntheticTextPageBitmap(
                text = docText,
                documentTitle = documentTitle,
                pageIndex = pageIndex,
                totalPages = virtualTotalPages
            )
        }

        // 4. When opened directly with extracted text (no file URI)
        if (fallbackText.isNotBlank()) {
            val virtualTotalPages = ((fallbackText.lines().count { it.isNotBlank() } / 22) + 1).coerceAtLeast(1)
            return@withContext renderSyntheticTextPageBitmap(
                text = fallbackText,
                documentTitle = documentTitle,
                pageIndex = pageIndex,
                totalPages = virtualTotalPages
            )
        }
        null
    }

    // =========================================================================
    // 3. التحديد الذكي بالمنطقة (Smart Area / Region OCR Extraction)
    // =========================================================================

    /**
     * يستخرج النص من المنطقة المحددة (Crop Rect) مع تطبيق فلتر الماسح الضوئي
     * يدعم الاستخراج الذكي بالذكاء الاصطناعي (Gemini OCR) والاستخراج المكاني المحلي الذكي لكافة الصيغ
     */
    suspend fun extractTextFromSelectedArea(
        context: Context,
        uri: Uri?,
        fileName: String,
        pageIndex: Int,
        areaRectNorm: RectF,
        preOcrFilter: PreOcrFilter,
        deskewDegrees: Float = 0f,
        useAiOcr: Boolean = true,
        fallbackFullText: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val isRealPdfOrImage = uri != null && (
                MultiFormatDocumentEngine.getPdfPageCount(context, uri) > 0 ||
                    fileName.lowercase().substringAfterLast('.', "") in listOf("png", "jpg", "jpeg", "webp", "bmp")
                )

            if (isRealPdfOrImage && uri != null) {
                val baseBmp = renderDocumentPageOrImage(
                    context = context,
                    uri = uri,
                    pageIndex = pageIndex,
                    scaleFactor = 2.0f,
                    fallbackText = fallbackFullText,
                    documentTitle = fileName
                )
                if (baseBmp != null && useAiOcr && GeminiAiService.isConfigured()) {
                    val processedBmp = applyPreOcrEnhancement(
                        source = baseBmp,
                        filter = preOcrFilter,
                        deskewDegrees = deskewDegrees,
                        cropRectNorm = areaRectNorm
                    )
                    val base64 = bitmapToBase64(processedBmp)
                    val areaPrompt = """
                        انسخ النص العربي والإنجليزي الموجود داخل هذه المنطقة المقصوصة بدقة تامة.
                        حافظ على التشكيل العربي والأرقام وعلامات الترقيم وترتيب الفقرات.
                        إذا كانت المنطقة تحتوي على جدول، فرتبه بوضوح باستخدام الفاصل العمودي |.
                        أخرج النص المستخرج فقط بدون أي مقدمات.
                    """.trimIndent()
                    val aiResult = GeminiAiService.extractDocumentOcr(base64, "image/jpeg", areaPrompt)
                    if (aiResult.isSuccess && aiResult.getOrNull()?.isNotBlank() == true) {
                        return@withContext Result.success(
                            ArabicDocumentProcessor.fixArabic(aiResult.getOrThrow().trim())
                        )
                    }
                }
            }

            // Local spatial region extraction on the document or page text
            val fullText = fallbackFullText.ifBlank {
                if (uri != null) DocumentParser.parseUri(context, uri).text else ""
            }
            val lines = fullText.lines().map { it.trim() }.filter { it.isNotBlank() }
            if (lines.isEmpty()) {
                return@withContext Result.success("لم يتم العثور على طبقة نصية في هذه المنطقة. فعّل وضع (ذكاء اصطناعي OCR) لقراءة الصور الممسوحة.")
            }

            val pdfPages = if (uri != null) MultiFormatDocumentEngine.getPdfPageCount(context, uri) else 0
            val totalPages = if (pdfPages > 0) pdfPages else ((lines.size / 22) + 1).coerceAtLeast(1)
            val linesPerPage = if (pdfPages > 0) {
                (lines.size.toFloat() / totalPages).coerceAtLeast(4f).roundToInt()
            } else {
                22
            }
            val pageStartLine = (pageIndex.coerceAtLeast(0) * linesPerPage).coerceIn(0, (lines.size - 1).coerceAtLeast(0))
            val pageLines = lines.drop(pageStartLine).take(linesPerPage).ifEmpty { lines }

            val normTop = min(areaRectNorm.top, areaRectNorm.bottom).coerceIn(0f, 0.95f)
            val normBottom = max(areaRectNorm.top, areaRectNorm.bottom).coerceIn(0.05f, 1f)
            val normLeft = min(areaRectNorm.left, areaRectNorm.right).coerceIn(0f, 0.95f)
            val normRight = max(areaRectNorm.left, areaRectNorm.right).coerceIn(0.05f, 1f)

            val startIdx = (normTop * pageLines.size).toInt().coerceIn(0, (pageLines.size - 1).coerceAtLeast(0))
            val endIdx = (normBottom * pageLines.size).roundToInt().coerceIn(startIdx + 1, pageLines.size)
            val verticalSlice = pageLines.subList(startIdx, endIdx)

            // Apply horizontal column filtering if narrow selection (e.g., right or left column)
            val widthNorm = (normRight - normLeft).coerceIn(0.1f, 1f)
            val centerX = (normLeft + normRight) / 2f
            val extractedLines = if (widthNorm < 0.65f) {
                val isRightSide = centerX >= 0.5f
                verticalSlice.map { line ->
                    val columns = line.split(Regex("""\t+|\s{2,}|\|""")).map { it.trim() }.filter { it.isNotBlank() }
                    if (columns.size >= 2) {
                        if (isRightSide) columns.first() else columns.last()
                    } else {
                        val words = line.split(Regex("""\s+""")).filter { it.isNotBlank() }
                        if (words.size >= 4) {
                            val half = words.size / 2
                            if (isRightSide) words.take(half).joinToString(" ")
                            else words.drop(half).joinToString(" ")
                        } else line
                    }
                }
            } else {
                verticalSlice
            }

            val rawRegionText = extractedLines.joinToString("\n").ifBlank { pageLines.joinToString("\n") }
            val finalRegionText = if (preOcrFilter != PreOcrFilter.ORIGINAL) {
                cleanSpacesAndArabicPunctuation(fixBrokenAndReversedArabicText(removeTatweel(rawRegionText)))
            } else {
                ArabicDocumentProcessor.fixArabic(rawRegionText)
            }
            Result.success(finalRegionText)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // 4. مختبر تنظيف ومعالجة النص العربي (Arabic Post-Processing & Normalization Lab)
    // =========================================================================

    private val ALL_TASHKEEL_REGEX = Regex("""[\u064B-\u065F\u0670\u06D6-\u06ED]""")
    private val ORNAMENTAL_TASHKEEL_REGEX = Regex("""[\u0653-\u065F\u0670\u06D6-\u06ED]""")
    private val TATWEEL_REGEX = Regex("""\u0640+""")
    private val PRESENTATION_FORMS_REGEX = Regex("""[\uFB50-\uFDFF\uFE70-\uFEFF]""")

    /**
     * 1. إزالة جميع الحركات والتشكيل العربي
     */
    fun stripAllTashkeel(text: String): String {
        return text.replace(ALL_TASHKEEL_REGEX, "")
    }

    /**
     * 2. إبقاء التشكيل الأساسي فقط (الفتحة، الضمة، الكسرة، السكون، الشدة، التنوين) وإزالة الرموز القرآنية والزخرفية
     */
    fun keepBasicTashkeelOnly(text: String): String {
        return text.replace(ORNAMENTAL_TASHKEEL_REGEX, "")
    }

    /**
     * 3. إزالة التطويل / الكشيدة (ـ)
     */
    fun removeTatweel(text: String): String {
        return text.replace(TATWEEL_REGEX, "")
    }

    /**
     * 4. توحيد الهمزات والألف (أ، إ، آ، ٱ -> ا) لتسهيل الفهرسة والبحث
     */
    fun normalizeHamzasAndAlef(text: String): String {
        return text
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ٱ', 'ا')
    }

    /**
     * 5. إصلاح النصوص العربية المقطّعة أو المعكوسة الناتجة عن ملفات PDF القديمة
     */
    fun fixBrokenAndReversedArabicText(text: String): String {
        if (text.isBlank()) return text

        // Normalize presentation forms (NFKC)
        var out = if (PRESENTATION_FORMS_REGEX.containsMatchIn(text)) {
            Normalizer.normalize(text, Normalizer.Form.NFKC)
        } else {
            text
        }

        // Fix spaced-out single Arabic letters (e.g., "ا ل ك ت ا ب" -> "الكتاب") when 4+ isolated Arabic chars appear in sequence
        val spacedArabicLettersRegex = Regex("""(?<=^|\s)([\u0621-\u064A]\s){3,}[\u0621-\u064A](?=\s|$|[.،؛؟!])""")
        out = spacedArabicLettersRegex.replace(out) { match ->
            match.value.replace(" ", "")
        }

        // Fix reversed common prefixes like "لا" written as "ال" at word end or reversed words with "ة" at the start of a word
        // In Arabic, 'ة' (Taa Marbuta) and 'ى' (Alef Maksura) NEVER appear at the beginning of a valid Arabic word!
        // If a token starts with 'ة' or 'ى' and has 3+ Arabic letters, it was extracted in reverse visual order!
        out = out.lines().joinToString("\n") { line ->
            val words = line.split(" ")
            val reversedWordsCount = words.count { w ->
                val clean = w.trim('.', '،', '؛', ':', '!', '؟', '(', ')', '"', '«', '»')
                clean.length >= 3 && (clean.startsWith('ة') || clean.startsWith('ى'))
            }
            if (words.isNotEmpty() && reversedWordsCount.toFloat() / words.size >= 0.25f) {
                // Reverse the entire line or reverse individual reversed words
                words.reversed().joinToString(" ") { w ->
                    if (w.any { it in '\u0600'..'\u06FF' }) w.reversed() else w
                }
            } else {
                words.joinToString(" ") { w ->
                    val clean = w.trim('.', '،', '؛', ':', '!', '؟', '(', ')', '"', '«', '»')
                    if (clean.length >= 3 && (clean.startsWith('ة') || clean.startsWith('ى'))) {
                        w.reversed()
                    } else {
                        w
                    }
                }
            }
        }

        return out
    }

    /**
     * 6. دمج الأسطر الذكي (Smart Unwrap): يدمج فواصل الأسطر العشوائية داخل الفقرة الواحدة مع الحفاظ على العناوين والقوائم
     */
    fun smartUnwrapParagraphs(text: String): String {
        if (text.isBlank()) return text
        val rawLines = text.lines()
        val sb = StringBuilder()

        for (i in rawLines.indices) {
            val line = rawLines[i].trim()
            if (line.isEmpty()) {
                sb.append("\n\n")
                continue
            }

            val isListItemOrHeading = line.startsWith("-") ||
                line.startsWith("•") ||
                line.startsWith("*") ||
                line.startsWith("#") ||
                line.matches(Regex("""^([0-9٠-٩]+[.\-)]|أولاً|ثانياً|ثالثاً|رابعاً|خامساً|الفصل|المبحث|المطلب).*"""))

            val endsWithSentenceTerminator = line.endsWith(".") ||
                line.endsWith("؟") ||
                line.endsWith("!") ||
                line.endsWith(":") ||
                line.endsWith("»")

            if (sb.isNotEmpty() && !sb.endsWith("\n")) {
                if (isListItemOrHeading) {
                    sb.append("\n").append(line)
                } else {
                    sb.append(" ").append(line)
                }
            } else {
                sb.append(line)
            }

            if (endsWithSentenceTerminator && line.length < 65) {
                sb.append("\n")
            }
        }

        return sb.toString()
            .replace(Regex("""\n{3,}"""), "\n\n")
            .replace(Regex("""[ \t]{2,}"""), " ")
            .trim()
    }

    /**
     * 7. تحويل الأرقام إلى الأرقام العربية المشرقية (٠١٢٣٤٥٦٧٨٩)
     */
    fun convertToEasternArabicNumerals(text: String): String {
        val western = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
        val eastern = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val idx = western.indexOf(ch)
            if (idx >= 0) sb.append(eastern[idx]) else sb.append(ch)
        }
        return sb.toString()
    }

    /**
     * 8. تحويل الأرقام إلى الأرقام العربية المغربية/الغربية (0123456789)
     */
    fun convertToWesternNumerals(text: String): String {
        val eastern = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        val persian = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val idx1 = eastern.indexOf(ch)
            val idx2 = persian.indexOf(ch)
            when {
                idx1 >= 0 -> sb.append(('0'.code + idx1).toChar())
                idx2 >= 0 -> sb.append(('0'.code + idx2).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * 9. تنظيف المسافات الزائدة وضبط علامات الترقيم العربية
     */
    fun cleanSpacesAndArabicPunctuation(text: String): String {
        return text
            .replace(Regex("""[ \t]+"""), " ")
            .replace(Regex("""\s+([،؛؟!.:»)])"""), "$1")
            .replace(Regex("""([«(])\s+"""), "$1")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }

    /**
     * تطبيق حزمة إعدادات المعالجة البعدية دفعة واحدة
     */
    fun applyPostProcessConfig(text: String, config: PostProcessConfig): String {
        var result = text
        if (config.fixBrokenArabic) result = fixBrokenAndReversedArabicText(result)
        if (config.removeTatweel) result = removeTatweel(result)
        if (config.stripAllTashkeel) {
            result = stripAllTashkeel(result)
        } else if (config.keepBasicTashkeelOnly) {
            result = keepBasicTashkeelOnly(result)
        }
        if (config.normalizeHamzas) result = normalizeHamzasAndAlef(result)
        if (config.smartUnwrapLines) result = smartUnwrapParagraphs(result)
        when (config.numeralMode) {
            "EASTERN" -> result = convertToEasternArabicNumerals(result)
            "WESTERN" -> result = convertToWesternNumerals(result)
        }
        if (config.cleanPunctuationSpaces) result = cleanSpacesAndArabicPunctuation(result)
        return result
    }

    // =========================================================================
    // 5. استخراج الجداول والبيانات المهيكلة (Table & Structured Data Extractor)
    // =========================================================================

    /**
     * يكتشف ويستخرج الجداول والبيانات المهيكلة من النص المستخرج (يدعم |، \t، الفواصل، والمسافات المتعددة، وبيانات مفتاح: قيمة)
     */
    fun extractStructuredTablesFromText(text: String, documentTitle: String = "جدول البيانات"): List<ExtractedTableData> {
        if (text.isBlank()) return emptyList()
        val tables = mutableListOf<ExtractedTableData>()
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        // 1. Check for pipe-delimited tables (| col1 | col2 |)
        val pipeLines = lines.filter { it.contains("|") && !it.matches(Regex("""^[\s|\-:]+$""")) }
        if (pipeLines.size >= 2) {
            val parsedRows = pipeLines.map { line ->
                line.trim('|').split("|").map { it.trim() }.filter { it.isNotEmpty() }
            }.filter { it.size >= 2 }

            if (parsedRows.size >= 2) {
                val maxCols = parsedRows.maxOf { it.size }.coerceAtLeast(2)
                val headers = parsedRows.first().let { h ->
                    if (h.size < maxCols) h + (h.size + 1..maxCols).map { "عمود $it" } else h
                }
                val body = parsedRows.drop(1).map { r ->
                    if (r.size < maxCols) r + List(maxCols - r.size) { "" } else r.take(maxCols)
                }
                tables.add(
                    ExtractedTableData(
                        title = "جدول مهيكل مستخرج ($documentTitle)",
                        headers = headers,
                        rows = body
                    )
                )
            }
        }

        // 2. Check for Tab or Multi-Space aligned rows
        val multiColLines = lines.mapNotNull { line ->
            val parts = when {
                line.contains("\t") -> line.split(Regex("""\t+""")).map { it.trim() }
                line.contains(Regex("""\s{3,}""")) -> line.split(Regex("""\s{3,}""")).map { it.trim() }
                line.count { it == '،' || it == ',' || it == ';' || it == '؛' } >= 2 && line.length < 140 ->
                    line.split(Regex("""[,،;؛]""")).map { it.trim() }
                else -> emptyList()
            }.filter { it.isNotBlank() }
            if (parts.size >= 2) parts else null
        }

        if (multiColLines.size >= 2 && tables.isEmpty()) {
            val maxCols = multiColLines.maxOf { it.size }.coerceIn(2, 8)
            val headers = multiColLines.first().take(maxCols).let { h ->
                if (h.size < maxCols) h + (h.size + 1..maxCols).map { "عمود $it" } else h
            }
            val body = multiColLines.drop(1).map { r ->
                val trimmed = r.take(maxCols)
                if (trimmed.size < maxCols) trimmed + List(maxCols - trimmed.size) { "" } else trimmed
            }
            tables.add(
                ExtractedTableData(
                    title = "جدول بيانات مصنف ($documentTitle)",
                    headers = headers,
                    rows = body
                )
            )
        }

        // 3. Check for Key: Value structured pairs (البند : القيمة / الملاحظة)
        val kvPairs = lines.mapNotNull { line ->
            val colonIdx = line.indexOf(':')
            if (colonIdx in 2..45 && colonIdx < line.length - 2) {
                val key = line.substring(0, colonIdx).trim().trimStart('-', '•', '*', '#')
                val value = line.substring(colonIdx + 1).trim()
                if (key.isNotBlank() && value.isNotBlank()) listOf(key, value) else null
            } else null
        }

        if (kvPairs.size >= 2) {
            tables.add(
                ExtractedTableData(
                    title = "جدول البنود والمؤشرات (${kvPairs.size} بند)",
                    headers = listOf("البند / المصطلح", "البيان / القيمة المستخرجة"),
                    rows = kvPairs
                )
            )
        }

        // 4. Fallback: Generate a structured analytical summary table of sentences/paragraphs with numbers or key findings
        if (tables.isEmpty()) {
            val numberedOrKeyLines = lines.mapIndexedNotNull { idx, line ->
                val numMatch = Regex("""([0-9٠-٩]+([.,][0-9٠-٩]+)?%?)""").find(line)?.value ?: "${idx + 1}"
                val words = line.split(Regex("""\s+""")).filter { it.isNotBlank() }
                if (words.size >= 3) {
                    val topic = words.take(3).joinToString(" ")
                    val detail = words.drop(3).joinToString(" ").ifBlank { line }
                    listOf(numMatch, topic, detail)
                } else null
            }.take(25)

            if (numberedOrKeyLines.isNotEmpty()) {
                tables.add(
                    ExtractedTableData(
                        title = "جدول هيكلة الفقرات والبيانات الرقمية",
                        headers = listOf("الرقم / المؤشر", "المحور / البند", "التفاصيل المستخرجة"),
                        rows = numberedOrKeyLines
                    )
                )
            }
        }

        return tables
    }

    /**
     * تحويل جدول مستخرج إلى صيغة Markdown Table جاهزة للنسخ أو الإدراج في المحرر
     */
    fun formatTableAsMarkdown(table: ExtractedTableData): String {
        val sb = StringBuilder()
        sb.append("### ").append(table.title).append("\n\n")
        sb.append("| ").append(table.headers.joinToString(" | ")).append(" |\n")
        sb.append("| ").append(table.headers.joinToString(" | ") { "---" }).append(" |\n")
        for (row in table.rows) {
            val padded = if (row.size < table.headers.size) {
                row + List(table.headers.size - row.size) { "" }
            } else {
                row.take(table.headers.size)
            }
            sb.append("| ").append(padded.joinToString(" | ") { it.replace("|", "،").replace("\n", " ") }).append(" |\n")
        }
        return sb.toString()
    }

    /**
     * تصدير الجدول المستخرج إلى ملف CSV متوافق مع Excel (بترميز UTF-8 مع BOM لدعم العربية)
     */
    fun exportTableToCsvFile(context: Context, table: ExtractedTableData, baseFileName: String): File {
        val outDir = File(context.cacheDir, "extracted_tables").apply { mkdirs() }
        val cleanName = baseFileName.substringBeforeLast(".").replace(Regex("""[^\p{L}\p{N}_\-]"""), "_").ifBlank { "table" }
        val outFile = File(outDir, "${cleanName}_table_${System.currentTimeMillis()}.csv")

        FileOutputStream(outFile).use { fos ->
            // Write UTF-8 BOM so Microsoft Excel recognizes Arabic encoding automatically
            fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            val csvBuilder = StringBuilder()
            csvBuilder.append(table.headers.joinToString(",") { escapeCsvCell(it) }).append("\r\n")
            for (row in table.rows) {
                csvBuilder.append(row.joinToString(",") { escapeCsvCell(it) }).append("\r\n")
            }
            fos.write(csvBuilder.toString().toByteArray(StandardCharsets.UTF_8))
        }
        return outFile
    }

    private fun escapeCsvCell(value: String): String {
        val clean = value.replace("\"", "\"\"").replace("\n", " ")
        return "\"$clean\""
    }

    // =========================================================================
    // 6. المستخرج الذكي للكيانات والاقتباسات والمصطلحات (Smart Entity & Citation Extractor)
    // =========================================================================

    private val ARABIC_STOP_WORDS = setOf(
        "في", "من", "إلى", "الى", "على", "عن", "مع", "هذا", "هذه", "ذلك", "تلك",
        "الذي", "التي", "الذين", "اللذين", "اللاتي", "كان", "كانت", "يكون", "تكون",
        "أن", "إن", "ان", "لا", "لم", "لن", "قد", "لقد", "ما", "هو", "هي", "هم",
        "هن", "نحن", "أنا", "أنت", "انتم", "بين", "حيث", "كل", "بعض", "غير", "أو",
        "او", "ثم", "بل", "حتى", "إذا", "اذا", "لو", "لولا", "كيف", "متى", "أين",
        "اي", "أي", "عبر", "خلال", "حول", "دون", "بدل", "نحو", "لدى", "عند", "منذ"
    )

    /**
     * يحلل النص المستخرج ويستنبط 4 فئات من الكيانات:
     * 1. الاقتباسات والشواهد والتوثيقات (QUOTES_CITATIONS)
     * 2. التواريخ والأرقام والإحصائيات (DATES_STATS)
     * 3. الروابط، البريد، الهواتف، والمعرفات (LINKS_CONTACTS)
     * 4. المصطلحات والمفاهيم المفتاحية الأكثر تردداً (KEY_TERMS)
     */
    fun extractEntitiesAndCitations(text: String): List<ExtractedEntityItem> {
        if (text.isBlank()) return emptyList()
        val entities = mutableListOf<ExtractedEntityItem>()

        // 1. Quotes & Citations («...», "...", (...))
        val quoteRegex = Regex("""«([^»]{6,260})»|"([^"]{6,260})"|“([^”]{6,260})”""")
        for (m in quoteRegex.findAll(text)) {
            val content = m.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.trim() ?: continue
            entities.add(
                ExtractedEntityItem(
                    text = "«$content»",
                    contextSnippet = extractSurroundingContext(text, m.range.first, m.range.last),
                    category = EntityCategory.QUOTES_CITATIONS,
                    subTypeLabel = "نص مقتبس"
                )
            )
        }

        // Academic Citations & References (e.g., [1], (ابن خلدون، 2020), رواه..., انظر:...)
        val refRegex = Regex("""\(([^()\n]{3,60}[،,]\s*[0-9٠-٩]{3,4}[هـم]?)\)|\[([0-9٠-٩]{1,3})\]|((?:انظر|راجع|المصدر|رواه|أخرجه)\s*:?\s*[^\n.،]{5,80})""")
        for (m in refRegex.findAll(text)) {
            val content = m.value.trim()
            if (content.length >= 3) {
                entities.add(
                    ExtractedEntityItem(
                        text = content,
                        contextSnippet = extractSurroundingContext(text, m.range.first, m.range.last),
                        category = EntityCategory.QUOTES_CITATIONS,
                        subTypeLabel = "إحالة مرجعية"
                    )
                )
            }
        }

        // 2. Dates, Years, Percentages, and Financial / Statistical Numbers
        val dateAndStatRegex = Regex(
            """(\b[0-9٠-٩]{1,2}[/\-][0-9٠-٩]{1,2}[/\-][0-9٠-٩]{2,4}\b)|""" +
            """(\b[0-9٠-٩]{4}[/\-][0-9٠-٩]{1,2}[/\-][0-9٠-٩]{1,2}\b)|""" +
            """(\b(?:عام|سنة)\s+[0-9٠-٩]{3,4}\s*(?:هـ|م|للهجرة|ميلادي)?\b)|""" +
            """(\b[0-9٠-٩]{3,4}\s*(?:هـ|م)\b)|""" +
            """(\b[0-9٠-٩]+(?:[.,][0-9٠-٩]+)?\s*[%٪]\b)|""" +
            """(\b[0-9٠-٩]+(?:[.,][0-9٠-٩]+)?\s*(?:مليون|مليار|ألف|ريال|دولار|دينار|جنيه|درهم|كيلومتر|متر|طن|ساعة|يوم|صفحة)\b)"""
        )
        val seenStats = mutableSetOf<String>()
        for (m in dateAndStatRegex.findAll(text)) {
            val value = m.value.trim()
            if (seenStats.add(value)) {
                val subType = when {
                    value.contains("%") || value.contains("٪") -> "نسبة مئوية"
                    value.contains("هـ") || value.contains("م") || value.contains("/") || value.contains("عام") || value.contains("سنة") -> "تاريخ / سنة"
                    else -> "مؤشر رقمي / إحصائي"
                }
                entities.add(
                    ExtractedEntityItem(
                        text = value,
                        contextSnippet = extractSurroundingContext(text, m.range.first, m.range.last),
                        category = EntityCategory.DATES_STATS,
                        subTypeLabel = subType
                    )
                )
            }
        }

        // 3. URLs, Emails, Phone Numbers, ISBN/DOI
        val urlRegex = Regex("""https?://[^\s"<>()]+|www\.[^\s"<>()]+""")
        for (m in urlRegex.findAll(text)) {
            entities.add(
                ExtractedEntityItem(
                    text = m.value.trimEnd('.', '،', ')'),
                    contextSnippet = extractSurroundingContext(text, m.range.first, m.range.last),
                    category = EntityCategory.LINKS_CONTACTS,
                    subTypeLabel = "رابط إلكتروني (URL)"
                )
            )
        }

        val emailRegex = Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}""")
        for (m in emailRegex.findAll(text)) {
            entities.add(
                ExtractedEntityItem(
                    text = m.value,
                    contextSnippet = extractSurroundingContext(text, m.range.first, m.range.last),
                    category = EntityCategory.LINKS_CONTACTS,
                    subTypeLabel = "بريد إلكتروني"
                )
            )
        }

        val phoneRegex = Regex("""(?:\+?[0-9٠-٩]{1,3}[\s\-]?)?(?:\(?[0-9٠-٩]{2,4}\)?[\s\-]?)[0-9٠-٩]{3,4}[\s\-]?[0-9٠-٩]{3,4}""")
        for (m in phoneRegex.findAll(text)) {
            val digitsCount = m.value.count { it.isDigit() }
            if (digitsCount in 8..15) {
                entities.add(
                    ExtractedEntityItem(
                        text = m.value.trim(),
                        contextSnippet = extractSurroundingContext(text, m.range.first, m.range.last),
                        category = EntityCategory.LINKS_CONTACTS,
                        subTypeLabel = "رقم هاتف / تواصل"
                    )
                )
            }
        }

        // 4. Key Terms & Concepts (High-value Arabic & Latin terms by frequency)
        val cleanTextForTerms = stripAllTashkeel(removeTatweel(text))
        val wordCounts = mutableMapOf<String, Int>()
        val tokenRegex = Regex("""[\u0621-\u064A]{4,}|[a-zA-Z]{4,}""")
        for (m in tokenRegex.findAll(cleanTextForTerms)) {
            val word = m.value.trim()
            val normalized = word.lowercase()
            if (normalized !in ARABIC_STOP_WORDS && normalized.removePrefix("ال") !in ARABIC_STOP_WORDS) {
                wordCounts[word] = (wordCounts[word] ?: 0) + 1
            }
        }

        val topTerms = wordCounts.entries
            .sortedByDescending { it.value }
            .take(15)

        for ((term, count) in topTerms) {
            val firstIdx = cleanTextForTerms.indexOf(term).coerceAtLeast(0)
            entities.add(
                ExtractedEntityItem(
                    text = term,
                    contextSnippet = extractSurroundingContext(text, firstIdx, (firstIdx + term.length).coerceAtMost(text.length)),
                    category = EntityCategory.KEY_TERMS,
                    subTypeLabel = "تردد: $count مرات",
                    frequency = count
                )
            )
        }

        // Ensure if no quotes were wrapped in «», we still provide top impactful sentences as citable quotes
        if (entities.none { it.category == EntityCategory.QUOTES_CITATIONS }) {
            val sentences = DocumentParser.splitIntoSentences(text).take(4)
            sentences.forEachIndexed { idx, s ->
                entities.add(
                    ExtractedEntityItem(
                        text = "«${s.take(160)}»",
                        contextSnippet = s,
                        category = EntityCategory.QUOTES_CITATIONS,
                        subTypeLabel = "فقرة مرجعية #${idx + 1}"
                    )
                )
            }
        }

        return entities
    }

    /**
     * توليد توثيق مرجعي أكاديمي فوري لأي اقتباس أو كيان مستخرج
     */
    fun formatAcademicCitation(
        quoteOrEntity: String,
        sourceDocumentName: String,
        pageLabel: String,
        style: String = "ARABIC" // "ARABIC", "APA", "MLA"
    ): String {
        val cleanDoc = sourceDocumentName.substringBeforeLast(".")
        val year = "2025"
        val cleanQuote = quoteOrEntity.trim('«', '»', '"', ' ')
        return when (style) {
            "APA" -> "«$cleanQuote» ($cleanDoc, $year, $pageLabel)."
            "MLA" -> "«$cleanQuote» ($cleanDoc, $pageLabel)."
            else -> "«$cleanQuote» — [المصدر: $cleanDoc، $pageLabel]."
        }
    }

    private fun extractSurroundingContext(fullText: String, start: Int, end: Int): String {
        val from = (start - 45).coerceAtLeast(0)
        val to = (end + 55).coerceAtMost(fullText.length)
        return fullText.substring(from, to).replace("\n", " ").trim()
    }

    // =========================================================================
    // 7. الاستخراج الدفعي الموجه ومسار التصدير الفوري (Batch Range & Direct Pipeline)
    // =========================================================================

    /**
     * يستخرج النصوص دفعة واحدة لنطاق صفحات مخصص أو الصفحات المرجعية (Bookmarks)
     * مع دعم الفلاتر المسبقة والمعالجة البعدية العربية وتحديث شريط التقدم لحظياً
     */
    suspend fun extractBatchPagesRange(
        context: Context,
        uri: Uri?,
        fileName: String,
        rangeMode: BatchRangeMode,
        customStartPageOneIndexed: Int,
        customEndPageOneIndexed: Int,
        bookmarkedPageIndicesZeroBased: List<Int>,
        preOcrFilter: PreOcrFilter,
        postProcessConfig: PostProcessConfig,
        useAiOcrWhenAvailable: Boolean,
        fallbackFullText: String = "",
        onProgress: (BatchProgressInfo) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val totalPdfPages = if (uri != null) MultiFormatDocumentEngine.getPdfPageCount(context, uri) else 0

            // Pre-parse full text once for fast offline fallback or virtual pagination on non-PDFs
            val fullFallbackText = fallbackFullText.ifBlank {
                if (uri != null) {
                    try {
                        DocumentParser.parseUri(context, uri).text
                    } catch (_: Exception) {
                        ""
                    }
                } else ""
            }
            val fallbackLines = fullFallbackText.lines().filter { it.isNotBlank() }

            val effectiveTotalPages = if (totalPdfPages > 0) {
                totalPdfPages
            } else {
                ((fallbackLines.size / 22) + 1).coerceAtLeast(1)
            }

            val linesPerPage = if (totalPdfPages > 0) {
                (fallbackLines.size.toFloat() / totalPdfPages.coerceAtLeast(1)).coerceAtLeast(5f).roundToInt()
            } else {
                22
            }

            // Determine 0-based page indices to extract
            val targetPagesZeroBased: List<Int> = when (rangeMode) {
                BatchRangeMode.CUSTOM_RANGE -> {
                    val start = (customStartPageOneIndexed - 1).coerceIn(0, effectiveTotalPages - 1)
                    val end = (customEndPageOneIndexed - 1).coerceIn(start, effectiveTotalPages - 1)
                    (start..end).toList()
                }
                BatchRangeMode.BOOKMARKED_ONLY -> {
                    bookmarkedPageIndicesZeroBased
                        .filter { it in 0 until effectiveTotalPages }
                        .distinct()
                        .sorted()
                        .ifEmpty { listOf(0) }
                }
                BatchRangeMode.ODD_PAGES -> {
                    (0 until effectiveTotalPages).filter { (it + 1) % 2 == 1 }.ifEmpty { listOf(0) }
                }
                BatchRangeMode.EVEN_PAGES -> {
                    (0 until effectiveTotalPages).filter { (it + 1) % 2 == 0 }.ifEmpty { listOf(0) }
                }
                BatchRangeMode.ALL_PAGES -> {
                    (0 until effectiveTotalPages).toList()
                }
            }

            val resultBuilder = StringBuilder()
            val totalToProcess = targetPagesZeroBased.size.coerceAtLeast(1)

            targetPagesZeroBased.forEachIndexed { stepIdx, pageIdx ->
                val pct = (stepIdx.toFloat() / totalToProcess).coerceIn(0.05f, 0.95f)
                onProgress(
                    BatchProgressInfo(
                        currentPage = stepIdx + 1,
                        totalPagesToProcess = totalToProcess,
                        statusMessage = "جاري معالجة واستخراج الصفحة ${pageIdx + 1} (${stepIdx + 1} من $totalToProcess)...",
                        percentage = pct
                    )
                )

                var pageExtractedText = ""

                // If AI OCR is requested & configured on a real PDF
                if (totalPdfPages > 0 && uri != null && useAiOcrWhenAvailable && GeminiAiService.isConfigured() && totalToProcess <= 12) {
                    val rawBmp = MultiFormatDocumentEngine.renderPdfPage(context, uri, pageIdx, scaleFactor = 1.7f)
                    if (rawBmp != null) {
                        val enhancedBmp = applyPreOcrEnhancement(rawBmp, preOcrFilter)
                        val base64 = bitmapToBase64(enhancedBmp)
                        val aiRes = GeminiAiService.extractDocumentOcr(base64, "image/jpeg")
                        if (aiRes.isSuccess) {
                            pageExtractedText = aiRes.getOrDefault("")
                        }
                    }
                }

                if (pageExtractedText.isBlank()) {
                    val startLine = (pageIdx * linesPerPage).coerceIn(0, (fallbackLines.size - 1).coerceAtLeast(0))
                    val slice = fallbackLines.drop(startLine).take(linesPerPage)
                    pageExtractedText = if (slice.isNotEmpty()) {
                        slice.joinToString("\n")
                    } else {
                        "【محتوى الصفحة ${pageIdx + 1} من $fileName】"
                    }
                }

                val cleanedPageText = applyPostProcessConfig(pageExtractedText, postProcessConfig)
                resultBuilder.append("═══【 صفحة ${pageIdx + 1} 】═══\n")
                resultBuilder.append(cleanedPageText.trim()).append("\n\n")
            }

            onProgress(
                BatchProgressInfo(
                    currentPage = totalToProcess,
                    totalPagesToProcess = totalToProcess,
                    statusMessage = "اكتمل استخراج $totalToProcess صفحة بنجاح ✔",
                    percentage = 1.0f
                )
            )

            Result.success(resultBuilder.toString().trim())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * توليد بطاقات استذكار (Flashcards) تلقائياً من النص المستخرج لتغذية مسار التصدير الفوري
     */
    fun generateFlashcardsFromExtractedText(
        uriString: String,
        extractedText: String,
        positionLabel: String
    ): List<DocumentFlashcardItem> {
        if (extractedText.isBlank()) return emptyList()
        val cards = mutableListOf<DocumentFlashcardItem>()

        // 1. Check for colon-separated definitions (المفهوم : التعريف)
        val lines = extractedText.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("═══") }
        for (line in lines) {
            val colonIdx = line.indexOf(':')
            if (colonIdx in 3..55 && colonIdx < line.length - 5) {
                val term = line.substring(0, colonIdx).trim().trimStart('-', '•', '*')
                val def = line.substring(colonIdx + 1).trim()
                cards.add(
                    DocumentFlashcardItem(
                        uriString = uriString,
                        termOrQuestion = "ما المقصود بـ: $term؟",
                        definitionOrAnswer = def,
                        positionLabel = positionLabel
                    )
                )
            }
        }

        // 2. Sentence-based key concept cards if fewer than 3 definition cards were found
        if (cards.size < 3) {
            val sentences = DocumentParser.splitIntoSentences(extractedText)
                .filter { !it.startsWith("═══") && it.length >= 20 }
                .take(5)
            sentences.forEachIndexed { idx, sentence ->
                val words = sentence.split(Regex("""\s+""")).filter { it.isNotBlank() }
                val focusTopic = words.take(4).joinToString(" ")
                cards.add(
                    DocumentFlashcardItem(
                        uriString = uriString,
                        termOrQuestion = "وضح الفكرة المستخلصة حول: «$focusTopic...» (#${idx + 1})",
                        definitionOrAnswer = sentence,
                        positionLabel = positionLabel
                    )
                )
            }
        }

        return cards.take(8)
    }

    /**
     * توليد عقد خريطة ذهنية (Mind Map Nodes) تلقائياً من النص المستخرج لتغذية مسار التصدير الفوري
     */
    fun generateMindMapNodesFromExtractedText(
        extractedText: String,
        positionIndex: Int
    ): List<DocumentMindMapNode> {
        if (extractedText.isBlank()) return emptyList()
        val paragraphs = extractedText
            .split(Regex("""\n+"""))
            .map { it.trim() }
            .filter { it.length >= 10 && !it.startsWith("═══") }
            .take(6)

        val palette = listOf("#2563EB", "#059669", "#7C3AED", "#D97706", "#0284C7", "#EC4899")
        return paragraphs.mapIndexed { idx, para ->
            val words = para.split(Regex("""\s+""")).filter { it.isNotBlank() }
            val title = words.take(5).joinToString(" ")
            val subtitle = words.drop(5).take(14).joinToString(" ").ifBlank { para.take(80) }
            DocumentMindMapNode(
                title = title,
                subtitle = subtitle,
                category = "مستخرج ذكي",
                positionIndex = positionIndex,
                parentId = "root_node",
                colorHex = palette[idx % palette.size]
            )
        }
    }

    private fun bitmapToBase64(bmp: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
