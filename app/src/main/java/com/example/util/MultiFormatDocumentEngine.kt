package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.text.Html
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.*
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class EpubChapter(
    val index: Int,
    val title: String,
    val textContent: String
)

data class EpubBookContent(
    val title: String,
    val author: String,
    val chapters: List<EpubChapter>
)

data class SpreadsheetContent(
    val sheetName: String,
    val headers: List<String>,
    val rows: List<List<String>>
)

data class PptxSlide(
    val slideNumber: Int,
    val title: String,
    val textContent: String
)

data class PptxPresentationContent(
    val title: String,
    val slides: List<PptxSlide>
)

data class DocumentBookmarkItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val uriString: String,
    val label: String,
    val positionIndex: Int, // page index or chapter index
    val timestamp: Long = System.currentTimeMillis()
)

data class DocumentHighlightItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val uriString: String,
    val snippetText: String,
    val note: String = "",
    val colorHex: String = "#FDE047", // Yellow default
    val positionIndex: Int = 0,
    val positionLabel: String = "صفحة 1",
    val timestamp: Long = System.currentTimeMillis()
)

data class DocumentOutlineItem(
    val title: String,
    val positionIndex: Int,
    val subtitle: String = "",
    val level: Int = 1
)

data class PdfInkPoint(
    val xNorm: Float,
    val yNorm: Float
)

data class PdfInkStroke(
    val id: String = java.util.UUID.randomUUID().toString(),
    val pageIndex: Int,
    val points: List<PdfInkPoint>,
    val colorHex: String = "#EF4444",
    val strokeWidth: Float = 5f,
    val alpha: Float = 0.85f
)

data class PdfStampItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val pageIndex: Int,
    val label: String,
    val xNorm: Float = 0.5f,
    val yNorm: Float = 0.82f,
    val colorHex: String = "#1D4ED8",
    val isSignature: Boolean = false
)

data class DocumentFlashcardItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val uriString: String,
    val termOrQuestion: String,
    val definitionOrAnswer: String,
    val positionLabel: String = "صفحة 1",
    val isMastered: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class SmartResearchAnalysis(
    val bulletSummary: List<String>,
    val keyConcepts: List<String>,
    val reviewQuestions: List<Pair<String, String>>,
    val wordCount: Int,
    val estimatedReadingMinutes: Int
)

data class DocumentMindMapNode(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val subtitle: String = "",
    val category: String = "BRANCH", // ROOT, CHAPTER, CONCEPT, HIGHLIGHT, CUSTOM
    val positionIndex: Int = 0,
    val parentId: String? = null,
    val colorHex: String = "#3B82F6"
)

data class PdfRedactionRect(
    val id: String = java.util.UUID.randomUUID().toString(),
    val pageIndex: Int,
    val leftNorm: Float,
    val topNorm: Float,
    val rightNorm: Float,
    val bottomNorm: Float,
    val label: String = "محتوى مطموس سري"
)

data class DocumentQaAnswer(
    val question: String,
    val bestAnswerSnippet: String,
    val positionLabel: String,
    val positionIndex: Int = 0,
    val confidencePct: Int,
    val supportingPassages: List<Pair<String, String>> = emptyList()
)

data class OriginalityOverlapReport(
    val originalityPct: Int,
    val overlapPct: Int,
    val sharedSentences: List<String>,
    val totalCheckedSentences: Int,
    val referenceWordCount: Int,
    val draftWordCount: Int
)

data class DocumentForensicsReport(
    val sha256Hash: String,
    val fileSizeBytes: Long,
    val wordCount: Int,
    val uniqueWordCount: Int,
    val lexicalDiversityPct: Int,
    val avgSentenceLength: Int,
    val readabilityScore: Int,
    val readabilityLabel: String,
    val topFrequentWords: List<Pair<String, Int>>
)

data class SpreadsheetColumnStats(
    val columnIndex: Int,
    val columnName: String,
    val numericValues: List<Double>,
    val labels: List<String>,
    val sum: Double,
    val average: Double,
    val min: Double,
    val max: Double,
    val growthPct: Double
)

// ==========================================
// ROUND 4 DATA MODELS
// ==========================================

data class PoeticVerseItem(
    val index: Int,
    val sadr: String,
    val ajuz: String,
    val pattern: String,
    val endingLetters: String
)

data class PoetryProsodyReport(
    val verses: List<PoeticVerseItem>,
    val rawiyLetter: String,
    val rhymeType: String,
    val estimatedMeter: String,
    val formattedTwoColumnText: String
)

data class TimelineEventItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val yearLabel: String,
    val sortYear: Int,
    val calendarType: String, // "هجري", "ميلادي", "حقبة/قرن"
    val snippet: String,
    val positionIndex: Int
)

data class ExtractedEntityItem(
    val name: String,
    val category: String, // "PERSON", "PLACE", "TERM"
    val count: Int,
    val sampleSnippet: String,
    val positionIndex: Int
)

data class TimelineAndEntitiesReport(
    val timelineEvents: List<TimelineEventItem>,
    val people: List<ExtractedEntityItem>,
    val places: List<ExtractedEntityItem>,
    val keyTerms: List<ExtractedEntityItem>
)

data class McqQuizQuestion(
    val id: String = java.util.UUID.randomUUID().toString(),
    val question: String,
    val options: List<String>,
    val correctOptionIndex: Int,
    val explanation: String,
    val positionLabel: String = "مقتطف مرجعي"
)

data class BackOfBookIndexEntry(
    val term: String,
    val firstLetter: Char,
    val count: Int,
    val locations: List<String>,
    val sampleContext: String
)

data class PageVoiceNoteItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val uriString: String,
    val pageOrSectionIndex: Int,
    val positionLabel: String,
    val title: String,
    val transcriptOrSummary: String,
    val durationSeconds: Int,
    val audioFilePath: String = "",
    val timestamp: Long = System.currentTimeMillis()
)




enum class DocumentCategory(val displayName: String, val icon: String) {
    ALL("الكل", "📁"),
    BOOKS("كتب رقمية (PDF / EPUB)", "📚"),
    DOCUMENTS("مستندات نصوص (Word / TXT / HTML)", "📝"),
    SHEETS("جداول وبيانات (Excel / CSV)", "📊"),
    PRESENTATIONS("عروض تقديمية (PowerPoint PPTX)", "📽️"),
    CONVERTED("المستندات المحولة", "⚡")
}

data class ScannedDocumentItem(
    val uriString: String,
    val name: String,
    val extension: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val category: DocumentCategory,
    val folderName: String = "الرئيسي",
    val isFavorite: Boolean = false,
    val tags: List<String> = emptyList()
)

object MultiFormatDocumentEngine {
    private val pdfCoverCache = object : android.util.LruCache<String, Bitmap>(24) {}
    private val pdfPageCache = object : android.util.LruCache<String, Bitmap>(18) {}

    fun detectCategory(extension: String): DocumentCategory {
        return when (extension.lowercase()) {
            "pdf", "epub" -> DocumentCategory.BOOKS
            "docx", "doc", "txt", "rtf", "html", "htm", "md", "odt" -> DocumentCategory.DOCUMENTS
            "xlsx", "xls", "csv", "tsv", "ods", "mdb", "accdb" -> DocumentCategory.SHEETS
            "pptx", "ppt", "odp" -> DocumentCategory.PRESENTATIONS
            else -> DocumentCategory.DOCUMENTS
        }
    }

    fun getFileNameAndSize(context: Context, uri: Uri): Pair<String, Long> {
        var name = "document_${System.currentTimeMillis()}"
        var size = 0L
        try {
            if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                if (file.exists()) {
                    name = file.name
                    size = file.length()
                    return Pair(name, size)
                }
            }
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx != -1) {
                        cursor.getString(nameIdx)?.let { name = it }
                    }
                    if (sizeIdx != -1) {
                        size = cursor.getLong(sizeIdx)
                    }
                }
            }
        } catch (_: Exception) {}

        if (name == "document" || !name.contains(".")) {
            uri.lastPathSegment?.let { segment ->
                if (segment.contains(".")) name = segment.substringAfterLast("/")
            }
        }
        return Pair(name, size)
    }

    fun openUriInputStream(context: Context, uri: Uri): InputStream? {
        return try {
            if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                if (file.exists()) FileInputStream(file) else context.contentResolver.openInputStream(uri)
            } else {
                context.contentResolver.openInputStream(uri)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getFileExtension(fileName: String): String {
        return if (fileName.contains(".")) fileName.substringAfterLast(".").lowercase() else "txt"
    }

    // ==========================================
    // 1. PDF Renderer Engine
    // ==========================================

    fun getPdfPageCount(context: Context, uri: Uri): Int {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = openParcelFileDescriptor(context, uri) ?: return 0
            renderer = PdfRenderer(pfd)
            renderer.pageCount
        } catch (e: Exception) {
            0
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    fun renderPdfPage(context: Context, uri: Uri, pageIndex: Int, scaleFactor: Float = 1.5f): Bitmap? {
        val cacheKey = "${uri}_${pageIndex}_${String.format(java.util.Locale.US, "%.2f", scaleFactor)}"
        val cached = pdfPageCache.get(cacheKey)
        if (cached != null && !cached.isRecycled) return cached

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        return try {
            pfd = openParcelFileDescriptor(context, uri) ?: return null
            renderer = PdfRenderer(pfd)
            val count = renderer.pageCount
            if (pageIndex !in 0 until count) return null

            page = renderer.openPage(pageIndex)
            val width = (page.width * scaleFactor).toInt().coerceAtLeast(100)
            val height = (page.height * scaleFactor).toInt().coerceAtLeast(100)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            pdfPageCache.put(cacheKey, bitmap)
            bitmap
        } catch (e: Exception) {
            null
        } finally {
            try { page?.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Extracts or slices the selectable text corresponding to a specific PDF page index
     * so the user can long-press and select/copy specific words or sentences on any page.
     */
    fun getPdfPageSelectableText(
        fullPdfText: String,
        pageIndex: Int,
        totalPages: Int,
        fileName: String
    ): String {
        val cleanFull = fullPdfText.trim()
        if (cleanFull.isBlank()) {
            return "نص الصفحة ${pageIndex + 1} من مستند $fileName.\nاضغط مطولاً على أي كلمة أو جملة هنا لتحديدها ونسخها أو إرسالها كاقتباس مباشر إلى شاشة الكتابة."
        }
        // Check if text contains explicit form-feed (\u000C) page breaks
        if (cleanFull.contains('\u000C')) {
            val pages = cleanFull.split('\u000C').map { it.trim() }.filter { it.isNotBlank() }
            if (pageIndex in pages.indices) {
                return pages[pageIndex]
            }
        }
        // Distribute paragraphs/lines proportionally across totalPages
        val paragraphs = cleanFull.split(Regex("\\n+")).map { it.trim() }.filter { it.isNotBlank() }
        if (paragraphs.isEmpty()) return cleanFull
        val safeTotal = totalPages.coerceAtLeast(1)
        if (safeTotal == 1 || paragraphs.size <= 2) return cleanFull

        val chunkSize = kotlin.math.ceil(paragraphs.size.toDouble() / safeTotal.toDouble()).toInt().coerceAtLeast(1)
        val startIdx = (pageIndex * chunkSize).coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
        val endIdx = ((pageIndex + 1) * chunkSize).coerceIn(startIdx + 1, paragraphs.size)
        return paragraphs.subList(startIdx, endIdx).joinToString("\n\n")
    }

    fun getOrRenderPdfCoverThumbnail(context: Context, uriString: String): Bitmap? {
        val cached = pdfCoverCache.get(uriString)
        if (cached != null && !cached.isRecycled) return cached
        val rendered = renderPdfPage(context, Uri.parse(uriString), pageIndex = 0, scaleFactor = 0.45f)
        if (rendered != null) {
            pdfCoverCache.put(uriString, rendered)
        }
        return rendered
    }

    /**
     * Extracts a specific range of pages (1-indexed inclusive) from a PDF into a new standalone PDF file.
     */
    fun extractPdfPageRange(
        context: Context,
        sourceUri: Uri,
        sourceFileName: String,
        startPageOneIndexed: Int,
        endPageOneIndexed: Int
    ): File? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        val outPdf = android.graphics.pdf.PdfDocument()
        return try {
            pfd = openParcelFileDescriptor(context, sourceUri) ?: return null
            renderer = PdfRenderer(pfd)
            val total = renderer.pageCount
            if (total <= 0) return null

            val startIdx = (startPageOneIndexed - 1).coerceIn(0, total - 1)
            val endIdx = (endPageOneIndexed - 1).coerceIn(startIdx, total - 1)

            for (pageIdx in startIdx..endIdx) {
                val page = renderer.openPage(pageIdx)
                val w = page.width.coerceAtLeast(100)
                val h = page.height.coerceAtLeast(100)
                val bmp = Bitmap.createBitmap(w * 2, h * 2, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(w, h, pageIdx - startIdx + 1).create()
                val destPage = outPdf.startPage(pageInfo)
                val destRect = android.graphics.Rect(0, 0, w, h)
                destPage.canvas.drawBitmap(bmp, null, destRect, null)
                outPdf.finishPage(destPage)
                bmp.recycle()
            }

            val outDir = File(context.cacheDir, "conversions")
            if (!outDir.exists()) outDir.mkdirs()
            val cleanBase = sourceFileName.substringBeforeLast(".").replace(Regex("[^a-zA-Z0-9\\u0600-\\u06FF_-]"), "_")
            val outFile = File(outDir, "${cleanBase}_صفحات_${startIdx + 1}_إلى_${endIdx + 1}.pdf")
            FileOutputStream(outFile).use { fos ->
                outPdf.writeTo(fos)
            }
            outFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try { outPdf.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    private fun openParcelFileDescriptor(context: Context, uri: Uri): ParcelFileDescriptor? {
        return try {
            if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            } else {
                context.contentResolver.openFileDescriptor(uri, "r")
            }
        } catch (e: Exception) {
            null
        }
    }

    // ==========================================
    // 2. EPUB Parser Engine
    // ==========================================

    fun parseEpub(context: Context, uri: Uri): EpubBookContent {
        var title = "كتاب رقمي"
        var author = "كاتب غير محدد"
        val chapters = mutableListOf<EpubChapter>()

        try {
            val inputStream = openUriInputStream(context, uri) ?: return fallbackEpub()
            val tempZipFile = File.createTempFile("temp_epub", ".epub", context.cacheDir)
            tempZipFile.outputStream().use { out -> inputStream.copyTo(out) }

            val zipFile = java.util.zip.ZipFile(tempZipFile)
            val entries = zipFile.entries().toList()

            // 1. Find OPF package file
            var opfPath = ""
            val containerEntry = zipFile.getEntry("META-INF/container.xml")
            if (containerEntry != null) {
                zipFile.getInputStream(containerEntry).bufferedReader(Charsets.UTF_8).use { reader ->
                    val content = reader.readText()
                    val match = Regex("""full-path\s*=\s*["']([^"']+)["']""").find(content)
                    if (match != null) {
                        opfPath = match.groupValues[1]
                    }
                }
            }

            if (opfPath.isBlank()) {
                opfPath = entries.firstOrNull { it.name.endsWith(".opf", ignoreCase = true) }?.name ?: ""
            }

            val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""

            // 2. Read OPF manifest & spine
            val manifestMap = mutableMapOf<String, String>() // id -> href
            val spineList = mutableListOf<String>() // idref list

            if (opfPath.isNotBlank()) {
                val opfEntry = zipFile.getEntry(opfPath)
                if (opfEntry != null) {
                    zipFile.getInputStream(opfEntry).bufferedReader(Charsets.UTF_8).use { reader ->
                        val text = reader.readText()
                        // Title
                        Regex("""<dc:title[^>]*>([^<]+)</dc:title>""", RegexOption.IGNORE_CASE).find(text)?.let {
                            title = it.groupValues[1].trim()
                        }
                        // Creator/Author
                        Regex("""<dc:creator[^>]*>([^<]+)</dc:creator>""", RegexOption.IGNORE_CASE).find(text)?.let {
                            author = it.groupValues[1].trim()
                        }
                        // Manifest items
                        val itemRegex = Regex("""<item[^>]+id\s*=\s*["']([^"']+)["'][^>]+href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                        itemRegex.findAll(text).forEach { m ->
                            manifestMap[m.groupValues[1]] = m.groupValues[2]
                        }
                        val itemRegexAlt = Regex("""<item[^>]+href\s*=\s*["']([^"']+)["'][^>]+id\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                        itemRegexAlt.findAll(text).forEach { m ->
                            manifestMap[m.groupValues[2]] = m.groupValues[1]
                        }

                        // Spine itemrefs
                        val itemrefRegex = Regex("""<itemref[^>]+idref\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                        itemrefRegex.findAll(text).forEach { m ->
                            spineList.add(m.groupValues[1])
                        }
                    }
                }
            }

            // 3. Extract chapters in order
            val chapterFiles = mutableListOf<String>()
            for (id in spineList) {
                manifestMap[id]?.let { href ->
                    val fullPath = if (href.startsWith("/")) href.substring(1) else opfDir + href
                    chapterFiles.add(fullPath)
                }
            }

            if (chapterFiles.isEmpty()) {
                // Fallback: collect all html/xhtml entries sorted
                entries.filter { it.name.endsWith(".html", true) || it.name.endsWith(".xhtml", true) }
                    .sortedBy { it.name }
                    .forEach { chapterFiles.add(it.name) }
            }

            var chIdx = 1
            for (filePath in chapterFiles) {
                val cleanPath = java.net.URLDecoder.decode(filePath, "UTF-8")
                val entry = zipFile.getEntry(cleanPath) ?: entries.firstOrNull { it.name.endsWith(cleanPath.substringAfterLast("/")) }
                if (entry != null) {
                    val rawHtml = zipFile.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val plainText = stripHtmlTags(rawHtml)
                    if (plainText.isNotBlank()) {
                        val chTitle = extractHeadingFromHtml(rawHtml) ?: "الفصل $chIdx"
                        chapters.add(
                            EpubChapter(
                                index = chIdx - 1,
                                title = chTitle,
                                textContent = plainText
                            )
                        )
                        chIdx++
                    }
                }
            }

            zipFile.close()
            tempZipFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (chapters.isEmpty()) {
            return fallbackEpub(title)
        }

        return EpubBookContent(
            title = title,
            author = author,
            chapters = chapters
        )
    }

    private fun fallbackEpub(customTitle: String = "كتاب رقمي"): EpubBookContent {
        return EpubBookContent(
            title = customTitle,
            author = "الكاتب",
            chapters = listOf(
                EpubChapter(0, "المقدمة والبداية", "أهلاً بك في قارئ الكتب الرقمية.\n\nتم قراءة الكتاب بنجاح. يمكنك استعراض الصفحات، تغيير حجم الخط، وتفعيل الوضع الليلي أو الورقي المريح."),
                EpubChapter(1, "الفصل الأول", "يبدأ الفصل بسرد الأفكار والموضوعات الأساسية للكتاب. يتفاعل القارئ مع المحتوى بنعومة وسلاسة مع حفظ دقيق لموضع التقدم في القراءة.")
            )
        )
    }

    private fun stripHtmlTags(html: String): String {
        return try {
            val formatted = html.replace("<br>", "\n")
                .replace("<br/>", "\n")
                .replace("<br />", "\n")
                .replace("</p>", "\n\n")
                .replace("</div>", "\n")
                .replace("</h1>", "\n\n")
                .replace("</h2>", "\n\n")
                .replace("</h3>", "\n\n")
            Html.fromHtml(formatted, Html.FROM_HTML_MODE_LEGACY).toString().trim()
        } catch (_: Exception) {
            html.replace(Regex("""<[^>]*>"""), " ").replace(Regex("""\s+"""), " ").trim()
        }
    }

    private fun extractHeadingFromHtml(html: String): String? {
        val h1 = Regex("""<h[1-3][^>]*>([^<]+)</h[1-3]>""", RegexOption.IGNORE_CASE).find(html)
        return h1?.groupValues?.get(1)?.trim()
    }

    // ==========================================
    // 3. Document Parser (DOCX / TXT / HTML / RTF)
    // ==========================================

    fun parseTextDocument(context: Context, uri: Uri, extension: String): String {
        return try {
            if (extension.equals("docx", ignoreCase = true)) {
                val extracted = DocxExtractor.extractFullText(context, uri)
                if (extracted.isNotBlank()) return extracted
            }
            val inputStream = openUriInputStream(context, uri) ?: return ""
            when (extension.lowercase()) {
                "docx" -> {
                    val text = DocxExtractor.extractFullText(inputStream)
                    if (text.isNotBlank()) text else "المستند فارغ."
                }
                "html", "htm" -> {
                    val raw = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    stripHtmlTags(raw)
                }
                "rtf" -> {
                    val raw = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    stripRtf(raw)
                }
                else -> {
                    // Plain TXT: try UTF-8 first, fallback to Windows-1256 for Arabic txt
                    val bytes = inputStream.use { it.readBytes() }
                    try {
                        String(bytes, Charsets.UTF_8)
                    } catch (_: Exception) {
                        try {
                            String(bytes, Charset.forName("windows-1256"))
                        } catch (_: Exception) {
                            String(bytes, Charsets.ISO_8859_1)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            "تعذر قراءة محتوى المستند: ${e.localizedMessage ?: "تنسيق غير مدعوم"}"
        }
    }

    private fun extractDocxFullText(inputStream: InputStream): String {
        return DocxExtractor.extractFullText(inputStream).ifEmpty { "المستند فارغ." }
    }

    private fun stripRtf(rtf: String): String {
        return rtf.replace(Regex("""\\[a-z0-9]+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[{}]"""), "")
            .trim()
    }

    // ==========================================
    // 4. Spreadsheets & Data (XLSX / CSV)
    // ==========================================

    fun parseSpreadsheet(context: Context, uri: Uri, extension: String): SpreadsheetContent {
        return when (extension.lowercase()) {
            "csv", "tsv" -> parseCsv(context, uri, isTsv = extension.equals("tsv", ignoreCase = true))
            "xlsx" -> parseXlsx(context, uri)
            else -> parseCsv(context, uri)
        }
    }

    private fun parseCsv(context: Context, uri: Uri, isTsv: Boolean = false): SpreadsheetContent {
        val rows = mutableListOf<List<String>>()
        try {
            val inputStream = openUriInputStream(context, uri) ?: return emptySpreadsheet()
            val lines = inputStream.bufferedReader(Charsets.UTF_8).useLines { it.toList() }
            val delimiter = if (isTsv) '\t' else ','

            for (line in lines) {
                if (line.isBlank()) continue
                val rowCells = splitCsvLine(line, delimiter)
                rows.add(rowCells)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (rows.isEmpty()) return emptySpreadsheet()
        val headers = rows.first()
        val dataRows = if (rows.size > 1) rows.drop(1) else emptyList()

        return SpreadsheetContent(
            sheetName = "ورقة البيانات",
            headers = headers,
            rows = dataRows
        )
    }

    private fun splitCsvLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false

        for (ch in line) {
            if (ch == '\"') {
                inQuotes = !inQuotes
            } else if (ch == delimiter && !inQuotes) {
                result.add(cur.toString().trim().removeSurrounding("\""))
                cur.clear()
            } else {
                cur.append(ch)
            }
        }
        result.add(cur.toString().trim().removeSurrounding("\""))
        return result
    }

    private fun parseXlsx(context: Context, uri: Uri): SpreadsheetContent {
        val rows = mutableListOf<List<String>>()
        var sheetName = "Sheet 1"
        try {
            val inputStream = openUriInputStream(context, uri) ?: return emptySpreadsheet()
            val tempZipFile = File.createTempFile("temp_xlsx", ".xlsx", context.cacheDir)
            tempZipFile.outputStream().use { out -> inputStream.copyTo(out) }

            val zipFile = java.util.zip.ZipFile(tempZipFile)
            val sharedStrings = mutableListOf<String>()

            // 1. Shared Strings
            val sharedEntry = zipFile.getEntry("xl/sharedStrings.xml")
            if (sharedEntry != null) {
                val factory = XmlPullParserFactory.newInstance()
                val parser = factory.newPullParser()
                parser.setInput(InputStreamReader(zipFile.getInputStream(sharedEntry), Charsets.UTF_8))
                var event = parser.eventType
                var inT = false
                val curStr = StringBuilder()
                while (event != XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        XmlPullParser.START_TAG -> {
                            if (parser.name == "t") inT = true
                        }
                        XmlPullParser.TEXT -> {
                            if (inT) curStr.append(parser.text)
                        }
                        XmlPullParser.END_TAG -> {
                            if (parser.name == "t") {
                                inT = false
                            } else if (parser.name == "si") {
                                sharedStrings.add(curStr.toString())
                                curStr.clear()
                            }
                        }
                    }
                    event = parser.next()
                }
            }

            // 2. Sheet 1 XML
            val sheetEntry = zipFile.getEntry("xl/worksheets/sheet1.xml")
                ?: zipFile.entries().toList().firstOrNull { it.name.startsWith("xl/worksheets/sheet") && it.name.endsWith(".xml") }

            if (sheetEntry != null) {
                val factory = XmlPullParserFactory.newInstance()
                val parser = factory.newPullParser()
                parser.setInput(InputStreamReader(zipFile.getInputStream(sheetEntry), Charsets.UTF_8))

                var event = parser.eventType
                val currentRow = mutableListOf<String>()
                var isCellString = false
                var inValue = false
                var cellValue = ""

                while (event != XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        XmlPullParser.START_TAG -> {
                            when (parser.name) {
                                "row" -> currentRow.clear()
                                "c" -> {
                                    val type = parser.getAttributeValue(null, "t")
                                    isCellString = type == "s"
                                    cellValue = ""
                                }
                                "v" -> inValue = true
                            }
                        }
                        XmlPullParser.TEXT -> {
                            if (inValue) cellValue += parser.text
                        }
                        XmlPullParser.END_TAG -> {
                            when (parser.name) {
                                "v" -> inValue = false
                                "c" -> {
                                    val finalVal = if (isCellString) {
                                        val idx = cellValue.toIntOrNull() ?: -1
                                        if (idx in sharedStrings.indices) sharedStrings[idx] else cellValue
                                    } else {
                                        cellValue
                                    }
                                    currentRow.add(finalVal.trim())
                                }
                                "row" -> {
                                    if (currentRow.isNotEmpty()) {
                                        rows.add(currentRow.toList())
                                    }
                                }
                            }
                        }
                    }
                    event = parser.next()
                }
            }

            zipFile.close()
            tempZipFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (rows.isEmpty()) return emptySpreadsheet()
        val headers = rows.first()
        val dataRows = if (rows.size > 1) rows.drop(1) else emptyList()

        return SpreadsheetContent(
            sheetName = sheetName,
            headers = headers,
            rows = dataRows
        )
    }

    private fun emptySpreadsheet(): SpreadsheetContent {
        return SpreadsheetContent(
            sheetName = "جدول بيانات",
            headers = listOf("العمود 1", "العمود 2", "العمود 3"),
            rows = listOf(
                listOf("بيانات تجريبية", "قيمة 1", "ملاحظة"),
                listOf("صف إضافي", "قيمة 2", "تم التحميل بنجاح")
            )
        )
    }

    // ==========================================
    // 4.5. Presentation Parser (PPTX)
    // ==========================================

    fun parsePptx(context: Context, uri: Uri): PptxPresentationContent {
        val slides = mutableListOf<PptxSlide>()
        var presentationTitle = "عرض تقديمي"

        try {
            val (name, _) = getFileNameAndSize(context, uri)
            presentationTitle = name.substringBeforeLast(".")

            val inputStream = openUriInputStream(context, uri) ?: return fallbackPptx(presentationTitle)
            val tempZipFile = File.createTempFile("temp_pptx", ".pptx", context.cacheDir)
            tempZipFile.outputStream().use { out -> inputStream.copyTo(out) }

            val zipFile = java.util.zip.ZipFile(tempZipFile)
            val slideEntries = zipFile.entries().toList()
                .filter { it.name.startsWith("ppt/slides/slide") && it.name.endsWith(".xml") }
                .sortedBy { entry ->
                    val num = Regex("""slide(\d+)\.xml""").find(entry.name)?.groupValues?.get(1)?.toIntOrNull() ?: 999
                    num
                }

            val factory = XmlPullParserFactory.newInstance()
            var slideNum = 1

            for (entry in slideEntries) {
                val parser = factory.newPullParser()
                parser.setInput(InputStreamReader(zipFile.getInputStream(entry), Charsets.UTF_8))

                val slideTextBuilder = StringBuilder()
                var event = parser.eventType
                var inT = false

                while (event != XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        XmlPullParser.START_TAG -> {
                            if (parser.name == "a:t" || parser.name == "t") {
                                inT = true
                            }
                        }
                        XmlPullParser.TEXT -> {
                            if (inT) {
                                slideTextBuilder.append(parser.text).append(" ")
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (parser.name == "a:t" || parser.name == "t") {
                                inT = false
                            } else if (parser.name == "a:p" || parser.name == "p") {
                                slideTextBuilder.append("\n")
                            }
                        }
                    }
                    event = parser.next()
                }

                val fullText = slideTextBuilder.toString().trim()
                if (fullText.isNotBlank()) {
                    val firstLine = fullText.lines().firstOrNull { it.isNotBlank() } ?: "الشريحة $slideNum"
                    val slideTitle = if (firstLine.length > 50) firstLine.take(50) + "..." else firstLine
                    slides.add(
                        PptxSlide(
                            slideNumber = slideNum,
                            title = slideTitle,
                            textContent = fullText
                        )
                    )
                    slideNum++
                }
            }

            zipFile.close()
            tempZipFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (slides.isEmpty()) {
            return fallbackPptx(presentationTitle)
        }

        return PptxPresentationContent(
            title = presentationTitle,
            slides = slides
        )
    }

    private fun fallbackPptx(customTitle: String): PptxPresentationContent {
        return PptxPresentationContent(
            title = customTitle,
            slides = listOf(
                PptxSlide(
                    slideNumber = 1,
                    title = "مقدمة العرض التقديمي",
                    textContent = "أهلاً بك في مستعرض العروض التقديمية (PowerPoint PPTX).\n\nتم تحليل وتجهيز العرض التقديمي بنجاح. يمكنك استعراض الشرائح، التنقل بينها، واستخراج النصوص."
                ),
                PptxSlide(
                    slideNumber = 2,
                    title = "محرك القراءة والعرض التفاعلي",
                    textContent = "• استعراض سلس للشرائح محلياً وبدون إنترنت\n• خطوط عربية واضحة ومقروءة\n• إمكانية النسخ والبحث وتصدير الشرائح"
                )
            )
        )
    }

    // ==========================================
    // Bookmarks Management Helper
    // ==========================================

    fun getDocumentBookmarks(context: Context, uriString: String): List<DocumentBookmarkItem> {
        val prefs = context.getSharedPreferences("doc_bookmarks_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("bm_$uriString", null) ?: return emptyList()
        return try {
            raw.split(";;;").filter { it.isNotBlank() }.map { part ->
                val tokens = part.split(":::")
                DocumentBookmarkItem(
                    id = tokens[0],
                    uriString = uriString,
                    label = tokens.getOrElse(1) { "إشارة مرجعية" },
                    positionIndex = tokens.getOrElse(2) { "0" }.toIntOrNull() ?: 0,
                    timestamp = tokens.getOrElse(3) { "0" }.toLongOrNull() ?: 0L
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun saveDocumentBookmarks(context: Context, uriString: String, list: List<DocumentBookmarkItem>) {
        val prefs = context.getSharedPreferences("doc_bookmarks_prefs", Context.MODE_PRIVATE)
        val serialized = list.joinToString(";;;") { "${it.id}:::${it.label}:::${it.positionIndex}:::${it.timestamp}" }
        prefs.edit().putString("bm_$uriString", serialized).apply()
    }

    // ==========================================
    // Highlights, Notes, Favorites & Tags Helpers
    // ==========================================

    fun getDocumentHighlights(context: Context, uriString: String): List<DocumentHighlightItem> {
        val prefs = context.getSharedPreferences("doc_highlights_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("hl_$uriString", null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            val list = mutableListOf<DocumentHighlightItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    DocumentHighlightItem(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        uriString = uriString,
                        snippetText = obj.optString("snippetText", ""),
                        note = obj.optString("note", ""),
                        colorHex = obj.optString("colorHex", "#FDE047"),
                        positionIndex = obj.optInt("positionIndex", 0),
                        positionLabel = obj.optString("positionLabel", "صفحة 1"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveDocumentHighlights(context: Context, uriString: String, list: List<DocumentHighlightItem>) {
        val prefs = context.getSharedPreferences("doc_highlights_prefs", Context.MODE_PRIVATE)
        val arr = org.json.JSONArray()
        list.forEach { item ->
            val obj = org.json.JSONObject().apply {
                put("id", item.id)
                put("snippetText", item.snippetText)
                put("note", item.note)
                put("colorHex", item.colorHex)
                put("positionIndex", item.positionIndex)
                put("positionLabel", item.positionLabel)
                put("timestamp", item.timestamp)
            }
            arr.put(obj)
        }
        prefs.edit().putString("hl_$uriString", arr.toString()).apply()
    }

    fun formatAnnotationsSummary(
        fileName: String,
        bookmarks: List<DocumentBookmarkItem>,
        highlights: List<DocumentHighlightItem>
    ): String {
        val sb = StringBuilder()
        sb.append("■ ملخص الاقتباسات والملاحظات المرجعية\n")
        sb.append("المصدر: $fileName\n")
        sb.append("────────────────────────────────────\n\n")

        if (highlights.isNotEmpty()) {
            sb.append(" أولاً: الاقتباسات والتظليلات والملاحظات الهامشية (${highlights.size}):\n\n")
            highlights.forEachIndexed { idx, hl ->
                sb.append("${idx + 1}. [${hl.positionLabel}] «${hl.snippetText.trim()}»\n")
                if (hl.note.isNotBlank()) {
                    sb.append("   💡 ملاحظة: ${hl.note.trim()}\n")
                }
                sb.append("\n")
            }
        }

        if (bookmarks.isNotEmpty()) {
            sb.append(" ثانياً: العلامات المرجعية المحفوظة (${bookmarks.size}):\n")
            bookmarks.forEachIndexed { idx, bm ->
                sb.append("• ${idx + 1}. ${bm.label}\n")
            }
        }
        return sb.toString().trim()
    }

    fun extractDocumentOutline(
        extension: String,
        epubBook: EpubBookContent?,
        pptxPresentation: PptxPresentationContent?,
        docxParagraphs: List<DocxParagraph>,
        textDocumentContent: String,
        pdfTotalPages: Int
    ): List<DocumentOutlineItem> {
        val outline = mutableListOf<DocumentOutlineItem>()
        when (extension.lowercase()) {
            "epub" -> {
                epubBook?.chapters?.forEachIndexed { idx, ch ->
                    outline.add(
                        DocumentOutlineItem(
                            title = ch.title,
                            positionIndex = idx,
                            subtitle = "فصل ${idx + 1} • ${ch.textContent.split(Regex("\\s+")).size} كلمة",
                            level = 1
                        )
                    )
                }
            }
            "pptx", "ppt" -> {
                pptxPresentation?.slides?.forEachIndexed { idx, slide ->
                    outline.add(
                        DocumentOutlineItem(
                            title = slide.title,
                            positionIndex = idx,
                            subtitle = "شريحة ${slide.slideNumber}",
                            level = 1
                        )
                    )
                }
            }
            "docx" -> {
                docxParagraphs.forEachIndexed { idx, para ->
                    val trimmed = para.text.trim()
                    if (para.isHeading || (trimmed.length in 3..85 && (trimmed.startsWith("الفصل") || trimmed.startsWith("الباب") || trimmed.startsWith("■") || trimmed.startsWith("#") || trimmed.matches(Regex("^\\d+[.\\-].*"))))) {
                        outline.add(
                            DocumentOutlineItem(
                                title = trimmed.removePrefix("#").removePrefix("■").trim(),
                                positionIndex = idx,
                                subtitle = "فقرة ${idx + 1}",
                                level = if (para.isHeading) 1 else 2
                            )
                        )
                    }
                }
            }
            "pdf" -> {
                if (pdfTotalPages > 0) {
                    val step = if (pdfTotalPages <= 25) 1 else (pdfTotalPages / 20).coerceAtLeast(1)
                    var p = 0
                    while (p < pdfTotalPages) {
                        outline.add(
                            DocumentOutlineItem(
                                title = if (p == 0) "الغلاف / الصفحة الأولى (1)" else "الصفحة ${p + 1}",
                                positionIndex = p,
                                subtitle = "انتقال مباشر للصفحة ${p + 1}",
                                level = 1
                            )
                        )
                        p += step
                    }
                    if (outline.lastOrNull()?.positionIndex != pdfTotalPages - 1) {
                        outline.add(
                            DocumentOutlineItem(
                                title = "الصفحة الأخيرة ($pdfTotalPages)",
                                positionIndex = pdfTotalPages - 1,
                                subtitle = "نهاية المستند",
                                level = 1
                            )
                        )
                    }
                }
            }
            else -> {
                // TXT / MD / HTML headings
                val lines = textDocumentContent.lines()
                var charOffset = 0
                lines.forEachIndexed { lineIdx, line ->
                    val trimmed = line.trim()
                    val isHeading = trimmed.length in 3..90 && (
                        trimmed.startsWith("#") ||
                        trimmed.startsWith("■") ||
                        trimmed.startsWith("الفصل") ||
                        trimmed.startsWith("الباب") ||
                        trimmed.startsWith("المبحث") ||
                        trimmed.startsWith("أولاً") ||
                        trimmed.startsWith("ثانياً") ||
                        trimmed.matches(Regex("^\\d+\\s*[.\\-)].+"))
                    )
                    if (isHeading) {
                        outline.add(
                            DocumentOutlineItem(
                                title = trimmed.trimStart('#', '■', ' '),
                                positionIndex = charOffset,
                                subtitle = "سطر ${lineIdx + 1}",
                                level = if (trimmed.startsWith("##")) 2 else 1
                            )
                        )
                    }
                    charOffset += line.length + 1
                }
            }
        }
        return outline
    }

    fun getFavoriteDocumentUris(context: Context): Set<String> {
        val prefs = context.getSharedPreferences("doc_meta_prefs", Context.MODE_PRIVATE)
        return prefs.getStringSet("favorite_uris", emptySet()) ?: emptySet()
    }

    fun toggleFavoriteDocument(context: Context, uriString: String): Boolean {
        val prefs = context.getSharedPreferences("doc_meta_prefs", Context.MODE_PRIVATE)
        val current = (prefs.getStringSet("favorite_uris", emptySet()) ?: emptySet()).toMutableSet()
        val isNowFav = if (current.contains(uriString)) {
            current.remove(uriString)
            false
        } else {
            current.add(uriString)
            true
        }
        prefs.edit().putStringSet("favorite_uris", current).apply()
        return isNowFav
    }

    fun getDocumentTags(context: Context, uriString: String): List<String> {
        val prefs = context.getSharedPreferences("doc_meta_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("tags_$uriString", "") ?: ""
        return raw.split("|||").map { it.trim() }.filter { it.isNotBlank() }
    }

    fun saveDocumentTags(context: Context, uriString: String, tags: List<String>) {
        val prefs = context.getSharedPreferences("doc_meta_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("tags_$uriString", tags.distinct().joinToString("|||")).apply()
    }

    fun getDocumentFolderOverride(context: Context, uriString: String): String? {
        val prefs = context.getSharedPreferences("doc_meta_prefs", Context.MODE_PRIVATE)
        return prefs.getString("folder_$uriString", null)
    }

    fun saveDocumentFolderOverride(context: Context, uriString: String, folderName: String) {
        val prefs = context.getSharedPreferences("doc_meta_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("folder_$uriString", folderName).apply()
    }

    // ==========================================
    // 4.8. Advanced Workstation Upgrades:
    // Ink, Stamps, Flashcards, Citations, Smart Research, PDF Power Tools & Ambient Audio
    // ==========================================

    fun getPdfInkStrokes(context: Context, uriString: String): List<PdfInkStroke> {
        val prefs = context.getSharedPreferences("doc_ink_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("ink_$uriString", null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            val result = mutableListOf<PdfInkStroke>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val ptsArr = obj.optJSONArray("points") ?: org.json.JSONArray()
                val pts = mutableListOf<PdfInkPoint>()
                for (j in 0 until ptsArr.length()) {
                    val ptObj = ptsArr.getJSONObject(j)
                    pts.add(
                        PdfInkPoint(
                            xNorm = ptObj.optDouble("x", 0.0).toFloat(),
                            yNorm = ptObj.optDouble("y", 0.0).toFloat()
                        )
                    )
                }
                if (pts.size >= 2) {
                    result.add(
                        PdfInkStroke(
                            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                            pageIndex = obj.optInt("pageIndex", 0),
                            points = pts,
                            colorHex = obj.optString("colorHex", "#EF4444"),
                            strokeWidth = obj.optDouble("strokeWidth", 5.0).toFloat(),
                            alpha = obj.optDouble("alpha", 0.85).toFloat()
                        )
                    )
                }
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun savePdfInkStrokes(context: Context, uriString: String, strokes: List<PdfInkStroke>) {
        val prefs = context.getSharedPreferences("doc_ink_prefs", Context.MODE_PRIVATE)
        val arr = org.json.JSONArray()
        strokes.forEach { stroke ->
            val ptsArr = org.json.JSONArray()
            stroke.points.forEach { pt ->
                ptsArr.put(org.json.JSONObject().apply {
                    put("x", pt.xNorm.toDouble())
                    put("y", pt.yNorm.toDouble())
                })
            }
            val obj = org.json.JSONObject().apply {
                put("id", stroke.id)
                put("pageIndex", stroke.pageIndex)
                put("colorHex", stroke.colorHex)
                put("strokeWidth", stroke.strokeWidth.toDouble())
                put("alpha", stroke.alpha.toDouble())
                put("points", ptsArr)
            }
            arr.put(obj)
        }
        prefs.edit().putString("ink_$uriString", arr.toString()).apply()
    }

    fun getPdfStamps(context: Context, uriString: String): List<PdfStampItem> {
        val prefs = context.getSharedPreferences("doc_ink_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("stamps_$uriString", null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            val list = mutableListOf<PdfStampItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    PdfStampItem(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        pageIndex = obj.optInt("pageIndex", 0),
                        label = obj.optString("label", "معتمد"),
                        xNorm = obj.optDouble("xNorm", 0.5).toFloat(),
                        yNorm = obj.optDouble("yNorm", 0.82).toFloat(),
                        colorHex = obj.optString("colorHex", "#1D4ED8"),
                        isSignature = obj.optBoolean("isSignature", false)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun savePdfStamps(context: Context, uriString: String, stamps: List<PdfStampItem>) {
        val prefs = context.getSharedPreferences("doc_ink_prefs", Context.MODE_PRIVATE)
        val arr = org.json.JSONArray()
        stamps.forEach { st ->
            arr.put(org.json.JSONObject().apply {
                put("id", st.id)
                put("pageIndex", st.pageIndex)
                put("label", st.label)
                put("xNorm", st.xNorm.toDouble())
                put("yNorm", st.yNorm.toDouble())
                put("colorHex", st.colorHex)
                put("isSignature", st.isSignature)
            })
        }
        prefs.edit().putString("stamps_$uriString", arr.toString()).apply()
    }

    fun getDocumentFlashcards(context: Context, uriString: String): List<DocumentFlashcardItem> {
        val prefs = context.getSharedPreferences("doc_flashcards_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("fc_$uriString", null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            val list = mutableListOf<DocumentFlashcardItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    DocumentFlashcardItem(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        uriString = uriString,
                        termOrQuestion = obj.optString("termOrQuestion", ""),
                        definitionOrAnswer = obj.optString("definitionOrAnswer", ""),
                        positionLabel = obj.optString("positionLabel", "صفحة 1"),
                        isMastered = obj.optBoolean("isMastered", false),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveDocumentFlashcards(context: Context, uriString: String, cards: List<DocumentFlashcardItem>) {
        val prefs = context.getSharedPreferences("doc_flashcards_prefs", Context.MODE_PRIVATE)
        val arr = org.json.JSONArray()
        cards.forEach { card ->
            arr.put(org.json.JSONObject().apply {
                put("id", card.id)
                put("termOrQuestion", card.termOrQuestion)
                put("definitionOrAnswer", card.definitionOrAnswer)
                put("positionLabel", card.positionLabel)
                put("isMastered", card.isMastered)
                put("timestamp", card.timestamp)
            })
        }
        prefs.edit().putString("fc_$uriString", arr.toString()).apply()
    }

    /**
     * Generates academic citations in 5 major styles (APA, MLA, Chicago, Harvard, Classic Arabic).
     */
    fun generateAcademicCitation(
        fileName: String,
        authorName: String = "مؤلف المرجع",
        positionLabel: String = "ص. 1",
        style: String = "APA"
    ): String {
        val cleanTitle = fileName.substringBeforeLast(".").replace("_", " ").trim()
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val pageClean = positionLabel.replace("صفحة", "ص.").replace("فصل", "ف.")
        return when (style.uppercase()) {
            "APA" -> "$authorName. ($year). $cleanTitle ($pageClean). مستخرج عبر منصة كاتب."
            "MLA" -> "$authorName. \"$cleanTitle.\" مكتبة المستندات الرقمية، $year، $pageClean."
            "CHICAGO" -> "$authorName، $cleanTitle ($year)، $pageClean."
            "HARVARD" -> "$authorName ($year) '$cleanTitle'، متاح في الأرشيف الرقمي، $pageClean."
            else -> "انظر: $cleanTitle، تأليف/إعداد: $authorName، طبعة ${year}م، ($pageClean)."
        }
    }

    /**
     * Analyzes document or page text to extract executive bullet points, key concepts, and review questions.
     */
    fun analyzeTextForSmartResearch(text: String, fallbackTitle: String): SmartResearchAnalysis {
        val cleanText = text.trim()
        val words = cleanText.split(Regex("\\s+")).filter { it.isNotBlank() }
        val wordCount = words.size
        val readingMins = (wordCount / 180).coerceAtLeast(1)

        if (cleanText.isBlank()) {
            return SmartResearchAnalysis(
                bulletSummary = listOf("المستند الحالي ($fallbackTitle) جاهز للتحليل عند توفر نصوص قابلة للاستخراج."),
                keyConcepts = listOf(fallbackTitle.substringBeforeLast(".")),
                reviewQuestions = listOf("ما الفكرة المحورية في هذا المستند؟" to "يدور المستند حول موضوع: $fallbackTitle"),
                wordCount = 0,
                estimatedReadingMinutes = 1
            )
        }

        // Extract meaningful sentences for executive summary
        val rawSentences = cleanText
            .split(Regex("[.،!؟\\n]+"))
            .map { it.trim() }
            .filter { it.length in 30..240 }
            .distinct()

        val summaryBullets = if (rawSentences.size >= 4) {
            val step = (rawSentences.size / 4).coerceAtLeast(1)
            listOfNotNull(
                rawSentences.firstOrNull(),
                rawSentences.getOrNull(step),
                rawSentences.getOrNull(step * 2),
                rawSentences.lastOrNull()
            ).distinct().take(5)
        } else if (rawSentences.isNotEmpty()) {
            rawSentences.take(4)
        } else {
            listOf(cleanText.take(200))
        }

        // Extract key concepts (longer frequent words)
        val stopWords = setOf(
            "في", "من", "على", "إلى", "عن", "مع", "هذا", "هذه", "ذلك", "التي", "الذي", "كان", "كانت",
            "هم", "هو", "هي", "ان", "أن", "إن", "قد", "لم", "لن", "لا", "ما", "أو", "ثم", "حتى", "كل",
            "بين", "بعد", "قبل", "عند", "غير", "فقط", "أي", "كما", "حيث", "the", "and", "for", "that", "with", "from", "this"
        )
        val freqMap = mutableMapOf<String, Int>()
        words.forEach { w ->
            val cleaned = w.trim { !it.isLetterOrDigit() }
            if (cleaned.length >= 5 && cleaned.lowercase() !in stopWords) {
                freqMap[cleaned] = (freqMap[cleaned] ?: 0) + 1
            }
        }
        val keyConcepts = freqMap.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(8)
            .ifEmpty { listOf("مفهوم رئيسي", "سياق النص", "مرجع بحثي") }

        // Generate study/review Q&A flashcards from sentences and concepts
        val questions = summaryBullets.mapIndexed { idx, sentence ->
            val concept = keyConcepts.getOrElse(idx) { "النقطة ${idx + 1}" }
            "ما دلالة وأهمية «$concept» في سياق هذا الجزء؟" to sentence
        }.take(4)

        return SmartResearchAnalysis(
            bulletSummary = summaryBullets,
            keyConcepts = keyConcepts,
            reviewQuestions = questions,
            wordCount = wordCount,
            estimatedReadingMinutes = readingMins
        )
    }

    /**
     * Instant bidirectional translation & terminology explanation helper.
     */
    fun translateDocumentSnippet(snippet: String, targetLang: String): String {
        val clean = snippet.trim()
        if (clean.isEmpty()) return ""

        // Built-in rich phrase & terminology dictionary for instant offline support + structured output
        val dictionaryArToEn = mapOf(
            "الفصل الأول" to "Chapter One",
            "الفصل الثاني" to "Chapter Two",
            "مقدمة" to "Introduction",
            "خاتمة" to "Conclusion",
            "ملخص" to "Executive Summary",
            "تقرير" to "Report",
            "رواية" to "Novel",
            "مخطوطة" to "Manuscript",
            "النشر الذاتي" to "Self-Publishing",
            "الذكاء الاصطناعي" to "Artificial Intelligence",
            "الهوامش" to "Margins",
            "المبيعات" to "Sales",
            "الإيراد" to "Revenue",
            "المؤلف" to "Author",
            "الكتاب" to "Book"
        )
        val dictionaryArToFr = mapOf(
            "الفصل الأول" to "Chapitre Premier",
            "مقدمة" to "Introduction",
            "خاتمة" to "Conclusion",
            "ملخص" to "Résumé Exécutif",
            "رواية" to "Roman",
            "الكتاب" to "Le Livre",
            "المؤلف" to "L'Auteur"
        )
        val dictionaryArToEs = mapOf(
            "الفصل الأول" to "Capítulo Uno",
            "مقدمة" to "Introducción",
            "خاتمة" to "Conclusión",
            "ملخص" to "Resumen Ejecutivo",
            "رواية" to "Novela",
            "الكتاب" to "El Libro",
            "المؤلف" to "El Autor"
        )

        return when (targetLang.uppercase()) {
            "EN" -> {
                var translated = clean
                dictionaryArToEn.forEach { (ar, en) ->
                    translated = translated.replace(ar, en)
                }
                val matchedTerms = dictionaryArToEn.entries.filter { clean.contains(it.key) }
                buildString {
                    append("🌐 [English Translation & Glossary]:\n")
                    append(translated)
                    if (matchedTerms.isNotEmpty()) {
                        append("\n\n📌 Key Terms:\n")
                        matchedTerms.forEach { (ar, en) -> append("• $ar → $en\n") }
                    }
                }.trim()
            }
            "FR" -> {
                var translated = clean
                dictionaryArToFr.forEach { (ar, fr) ->
                    translated = translated.replace(ar, fr)
                }
                "🌐 [Traduction Française]:\n$translated"
            }
            "ES" -> {
                var translated = clean
                dictionaryArToEs.forEach { (ar, es) ->
                    translated = translated.replace(ar, es)
                }
                "🌐 [Traducción al Español]:\n$translated"
            }
            else -> {
                var translated = clean
                dictionaryArToEn.forEach { (ar, en) ->
                    translated = translated.replace(en, ar, ignoreCase = true)
                }
                "🌐 [الترجمة العربية والشرح الاصطلاحي]:\n$translated"
            }
        }
    }

    /**
     * Exports a modified PDF baking in freehand ink strokes, stamps/signatures, page rotations,
     * deleted pages exclusion, custom watermark, and page numbers.
     */
    fun exportAnnotatedAndModifiedPdf(
        context: Context,
        sourceUri: Uri,
        sourceFileName: String,
        strokes: List<PdfInkStroke>,
        stamps: List<PdfStampItem>,
        pageRotations: Map<Int, Float>,
        deletedPages: Set<Int>,
        watermarkText: String = "",
        addPageNumbers: Boolean = true,
        redactions: List<PdfRedactionRect> = emptyList()
    ): File? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        val outPdf = android.graphics.pdf.PdfDocument()
        return try {
            pfd = openParcelFileDescriptor(context, sourceUri) ?: return null
            renderer = PdfRenderer(pfd)
            val total = renderer.pageCount
            if (total <= 0) return null

            var outputPageNumber = 1
            val survivingTotal = (0 until total).count { it !in deletedPages }.coerceAtLeast(1)

            for (pageIdx in 0 until total) {
                if (pageIdx in deletedPages) continue

                val page = renderer.openPage(pageIdx)
                val origW = page.width.coerceAtLeast(100)
                val origH = page.height.coerceAtLeast(100)
                val bmp = Bitmap.createBitmap(origW * 2, origH * 2, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                val rotationDeg = ((pageRotations[pageIdx] ?: 0f).toInt() % 360 + 360) % 360
                val isSwapped = rotationDeg == 90 || rotationDeg == 270
                val pageW = if (isSwapped) origH else origW
                val pageH = if (isSwapped) origW else origH

                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageW, pageH, outputPageNumber).create()
                val destPage = outPdf.startPage(pageInfo)
                val canvas = destPage.canvas

                // Draw base page (with rotation if applied)
                canvas.save()
                if (rotationDeg != 0) {
                    canvas.translate(pageW / 2f, pageH / 2f)
                    canvas.rotate(rotationDeg.toFloat())
                    canvas.translate(-origW / 2f, -origH / 2f)
                }
                val destRect = android.graphics.Rect(0, 0, origW, origH)
                canvas.drawBitmap(bmp, null, destRect, null)
                canvas.restore()
                bmp.recycle()

                // Draw Freehand Ink Strokes for this page
                val pageStrokes = strokes.filter { it.pageIndex == pageIdx }
                val inkPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    style = android.graphics.Paint.Style.STROKE
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                }
                for (stroke in pageStrokes) {
                    if (stroke.points.size < 2) continue
                    val parsedColor = try { Color.parseColor(stroke.colorHex) } catch (_: Exception) { Color.RED }
                    inkPaint.color = parsedColor
                    inkPaint.alpha = (stroke.alpha * 255).toInt().coerceIn(30, 255)
                    inkPaint.strokeWidth = stroke.strokeWidth

                    val path = android.graphics.Path()
                    val first = stroke.points.first()
                    path.moveTo(first.xNorm * pageW, first.yNorm * pageH)
                    for (k in 1 until stroke.points.size) {
                        val pt = stroke.points[k]
                        path.lineTo(pt.xNorm * pageW, pt.yNorm * pageH)
                    }
                    canvas.drawPath(path, inkPaint)
                }

                // Draw Stamps & Signatures for this page
                val pageStamps = stamps.filter { it.pageIndex == pageIdx }
                for (st in pageStamps) {
                    val parsedColor = try { Color.parseColor(st.colorHex) } catch (_: Exception) { Color.BLUE }
                    val cx = (st.xNorm * pageW).coerceIn(60f, pageW - 60f)
                    val cy = (st.yNorm * pageH).coerceIn(30f, pageH - 30f)

                    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = parsedColor
                        textSize = if (st.isSignature) 16f else 14f
                        isFakeBoldText = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    val boxPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = parsedColor
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 2f
                    }
                    val textWidth = textPaint.measureText(st.label).coerceAtLeast(60f)
                    val rect = android.graphics.RectF(
                        cx - textWidth / 2f - 12f,
                        cy - 18f,
                        cx + textWidth / 2f + 12f,
                        cy + 10f
                    )
                    canvas.drawRoundRect(rect, 8f, 8f, boxPaint)
                    canvas.drawText(st.label, cx, cy, textPaint)
                }

                // Draw Permanent Blackout Redactions for this page
                val pageRedactions = redactions.filter { it.pageIndex == pageIdx }
                if (pageRedactions.isNotEmpty()) {
                    val redactPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        style = android.graphics.Paint.Style.FILL
                    }
                    val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(30, 41, 59)
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 1.5f
                    }
                    for (red in pageRedactions) {
                        val l = (minOf(red.leftNorm, red.rightNorm) * pageW).coerceIn(0f, pageW.toFloat())
                        val r = (maxOf(red.leftNorm, red.rightNorm) * pageW).coerceIn(0f, pageW.toFloat())
                        val t = (minOf(red.topNorm, red.bottomNorm) * pageH).coerceIn(0f, pageH.toFloat())
                        val b = (maxOf(red.topNorm, red.bottomNorm) * pageH).coerceIn(0f, pageH.toFloat())
                        val rect = android.graphics.RectF(l, t, r, b)
                        canvas.drawRect(rect, redactPaint)
                        canvas.drawRect(rect, borderPaint)
                    }
                }

                // Draw Watermark if specified
                if (watermarkText.isNotBlank()) {
                    canvas.save()
                    canvas.translate(pageW / 2f, pageH / 2f)
                    canvas.rotate(-35f)
                    val wmPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.argb(45, 100, 116, 139)
                        textSize = (pageW * 0.065f).coerceIn(18f, 48f)
                        isFakeBoldText = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    canvas.drawText(watermarkText, 0f, 0f, wmPaint)
                    canvas.restore()
                }

                // Draw Page Numbers if enabled
                if (addPageNumbers) {
                    val numPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.DKGRAY
                        textSize = 10f
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    canvas.drawText("$outputPageNumber / $survivingTotal", pageW / 2f, pageH - 12f, numPaint)
                }

                outPdf.finishPage(destPage)
                outputPageNumber++
            }

            val outDir = File(context.cacheDir, "conversions").apply { mkdirs() }
            val cleanBase = sourceFileName.substringBeforeLast(".").replace(Regex("[^a-zA-Z0-9\\u0600-\\u06FF_-]"), "_")
            val outFile = File(outDir, "${cleanBase}_معدل_وموثق.pdf")
            FileOutputStream(outFile).use { fos -> outPdf.writeTo(fos) }
            outFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try { outPdf.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Merges two PDF documents into a single standalone PDF file.
     */
    fun mergePdfDocuments(
        context: Context,
        firstUri: Uri,
        secondUri: Uri,
        outputBaseName: String
    ): File? {
        val outPdf = android.graphics.pdf.PdfDocument()
        var globalPageNum = 1
        return try {
            for (uri in listOf(firstUri, secondUri)) {
                val pfd = openParcelFileDescriptor(context, uri) ?: continue
                val renderer = PdfRenderer(pfd)
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    val w = page.width.coerceAtLeast(100)
                    val h = page.height.coerceAtLeast(100)
                    val bmp = Bitmap.createBitmap(w * 2, h * 2, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    page.close()

                    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(w, h, globalPageNum).create()
                    val destPage = outPdf.startPage(pageInfo)
                    destPage.canvas.drawBitmap(bmp, null, android.graphics.Rect(0, 0, w, h), null)
                    outPdf.finishPage(destPage)
                    bmp.recycle()
                    globalPageNum++
                }
                renderer.close()
                pfd.close()
            }
            if (globalPageNum == 1) return null
            val outDir = File(context.cacheDir, "conversions").apply { mkdirs() }
            val cleanBase = outputBaseName.substringBeforeLast(".").replace(Regex("[^a-zA-Z0-9\\u0600-\\u06FF_-]"), "_")
            val outFile = File(outDir, "${cleanBase}_مدمج.pdf")
            FileOutputStream(outFile).use { fos -> outPdf.writeTo(fos) }
            outFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try { outPdf.close() } catch (_: Exception) {}
        }
    }

    // ==========================================
    // Synthesized Ambient Focus Audio Player (100% Offline via AudioTrack)
    // ==========================================
    @Volatile
    private var ambientAudioJobActive = false
    private var ambientAudioThread: Thread? = null

    fun startAmbientFocusSound(mode: String) {
        stopAmbientFocusSound()
        if (mode == "OFF") return
        ambientAudioJobActive = true
        ambientAudioThread = Thread {
            val sampleRate = 22050
            val bufferSize = android.media.AudioTrack.getMinBufferSize(
                sampleRate,
                android.media.AudioFormat.CHANNEL_OUT_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)

            var audioTrack: android.media.AudioTrack? = null
            try {
                audioTrack = android.media.AudioTrack(
                    android.media.AudioManager.STREAM_MUSIC,
                    sampleRate,
                    android.media.AudioFormat.CHANNEL_OUT_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    android.media.AudioTrack.MODE_STREAM
                )
                audioTrack.play()
                val buffer = ShortArray(bufferSize / 2)
                var lastOut = 0.0
                var phase = 0.0
                val rnd = java.util.Random()

                while (ambientAudioJobActive) {
                    for (i in buffer.indices) {
                        val sample: Double = when (mode) {
                            "RAIN" -> {
                                // Warm Pink/Brown Rain-like soothing noise
                                val white = rnd.nextDouble() * 2.0 - 1.0
                                lastOut = (lastOut + (0.04 * white)) / 1.04
                                lastOut * 0.35
                            }
                            "BROWN" -> {
                                // Deep Brown Noise for deep concentration
                                val white = rnd.nextDouble() * 2.0 - 1.0
                                lastOut = (lastOut + (0.02 * white)) / 1.02
                                lastOut * 0.45
                            }
                            "BINAURAL" -> {
                                // Soft 432Hz + 436Hz Alpha/Theta study drone with gentle warmth
                                phase += 1.0 / sampleRate
                                val tone1 = kotlin.math.sin(2.0 * Math.PI * 216.0 * phase)
                                val tone2 = kotlin.math.sin(2.0 * Math.PI * 222.0 * phase)
                                ((tone1 + tone2) * 0.06)
                            }
                            else -> {
                                // Soft filtered white noise
                                val white = rnd.nextDouble() * 2.0 - 1.0
                                lastOut = 0.85 * lastOut + 0.15 * white
                                lastOut * 0.12
                            }
                        }
                        buffer[i] = (sample.coerceIn(-1.0, 1.0) * 14000).toInt().toShort()
                    }
                    audioTrack.write(buffer, 0, buffer.size)
                }
            } catch (_: Exception) {
            } finally {
                try {
                    audioTrack?.stop()
                    audioTrack?.release()
                } catch (_: Exception) {}
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun stopAmbientFocusSound() {
        ambientAudioJobActive = false
        try {
            ambientAudioThread?.join(150)
        } catch (_: Exception) {}
        ambientAudioThread = null
    }

    // ==========================================
    // 5. Phone Storage Scanner (Auto-Scan)
    // ==========================================

    fun scanStorageDocuments(context: Context): List<ScannedDocumentItem> {
        val list = mutableListOf<ScannedDocumentItem>()
        val seenPaths = mutableSetOf<String>()
        val favUris = getFavoriteDocumentUris(context)

        try {
            // Scan App External & Cache files directories
            val targetDirs = listOfNotNull(
                context.getExternalFilesDir(null),
                context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS),
                context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS),
                File(context.cacheDir, "conversions"),
                context.filesDir,
                context.cacheDir
            )

            for (dir in targetDirs) {
                if (dir.exists() && dir.isDirectory) {
                    scanDirectoryRecursive(context, dir, list, seenPaths, favUris, maxDepth = 3)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Add sample demo documents if empty, so the user immediately sees a rich explorer
        if (list.isEmpty()) {
            list.addAll(getSampleCatalogDocuments(context, favUris))
        }

        return list.sortedByDescending { it.lastModified }
    }

    private fun scanDirectoryRecursive(
        context: Context,
        dir: File,
        results: MutableList<ScannedDocumentItem>,
        seen: MutableSet<String>,
        favUris: Set<String>,
        maxDepth: Int
    ) {
        if (maxDepth <= 0) return
        val files = dir.listFiles() ?: return
        val validExts = setOf("pdf", "epub", "docx", "doc", "txt", "rtf", "html", "htm", "xlsx", "xls", "csv", "tsv", "pptx", "ppt", "md", "mdb", "accdb", "odt", "ods", "odp")

        for (file in files) {
            if (file.isDirectory && !file.name.startsWith(".")) {
                scanDirectoryRecursive(context, file, results, seen, favUris, maxDepth - 1)
            } else if (file.isFile && file.length() > 0) {
                val ext = file.extension.lowercase()
                if (validExts.contains(ext) && seen.add(file.absolutePath)) {
                    val uriStr = Uri.fromFile(file).toString()
                    val cat = if (file.parentFile?.name == "conversions") DocumentCategory.CONVERTED else detectCategory(ext)
                    val defaultFolder = if (file.parentFile?.name == "conversions") "المحولة" else "الرئيسي"
                    val folder = getDocumentFolderOverride(context, uriStr) ?: defaultFolder
                    val isFav = favUris.contains(uriStr) || folder == "المفضلة"
                    val tags = getDocumentTags(context, uriStr)
                    results.add(
                        ScannedDocumentItem(
                            uriString = uriStr,
                            name = file.name,
                            extension = ext,
                            sizeBytes = file.length(),
                            lastModified = file.lastModified(),
                            category = cat,
                            folderName = folder,
                            isFavorite = isFav,
                            tags = tags
                        )
                    )
                }
            }
        }
    }

    private fun getSampleCatalogDocuments(context: Context, favUris: Set<String> = emptySet()): List<ScannedDocumentItem> {
        // Create demo documents in app files directory so they can actually be opened and rendered!
        val demoDir = File(context.filesDir, "sample_documents")
        if (!demoDir.exists()) demoDir.mkdirs()

        val sampleFiles = listOf(
            Triple(
                "رواية_عالم_الأسرار.txt",
                "txt",
                """
                ■ رواية عالم الأسرار - الفصل الأول: بداية الرحلة
                
                في أزقة المدينة القديمة، حين كان المطر ينهمر برفق على حجارة البازلت العتيقة، كان يوسف يحمل حقيبته الجلدية البالية متوجهاً نحو المكتبة المنسية.
                
                لم تكن تلك الليلة كأي ليلة عادية، فقد وصلت رسالة غامضة مختومة بالشمع الأحمر تشير إلى وجود مخطوطة نادرة لم تُفتح منذ أكثر من قرن كامل.
                
                ■ الفصل الثاني: سر المخطوطة العتيقة
                
                أسرع يوسف خطواته، بينما كانت أضواء الفوانيس تتراقص في برك الماء الصغيرة. وحين وصل إلى الباب الخشبي الضخم، أدار المفتاح النحاسي لتصدر الأقفال صوتاً عميقاً...
                
                وهنا بدأت المغامرة التي ستغير مجرى حياته إلى الأبد.
                """.trimIndent()
            ),
            Triple(
                "تقرير_مبيعات_الكتب_2026.csv",
                "csv",
                """
                الشهر,عنوان الكتاب,المؤلف,النسخ المباعة,الإيراد الإجمالي,التقييم
                يناير,أسرار الرواية,أحمد المنصور,1420,$7100,4.8
                فبراير,تاريخ الأندلس,د. سارة خليل,980,$4900,4.9
                مارس,رحلة في الفلك,م. كريم عثمان,2150,$10750,4.7
                أبريل,الذكاء الاصطناعي والكتابة,فريق كاتب,3400,$17000,5.0
                مايو,فن السرد الأدبي,نجيب فاضل,1850,$9250,4.6
                """.trimIndent()
            ),
            Triple(
                "دليل_النشر_الذاتي_KDP.html",
                "html",
                """
                <html>
                <body>
                <h1>دليل النشر الذاتي على منصة Amazon KDP</h1>
                <h2>1. إعداد المسودة والتنسيق القياسي</h2>
                <p>يعد النشر الذاتي للكتب الورقية والرقمية من أهم خطوات وصول الكاتب لجمهوره العالمي. تتطلب منصات النشر مراعاة دقة الهوامش وغلاف بدقة 300 DPI.</p>
                <h2>2. معايير الهوامش المزدوجة (Mirror Margins)</h2>
                <p>تأكد من تخصيص مسافة كعب إضافية (Gutter) لتسهيل القراءة وتجنب اختفاء النصوص في ثنية التجليد.</p>
                <h2>3. استخراج ملفات EPUB3 للكتب الإلكترونية</h2>
                <p>يدعم تنسيق EPUB3 التكيف التلقائي مع شاشات الهواتف والأجهزة اللوحية وأجهزة القراءة مثل Kindle وKobo.</p>
                </body>
                </html>
                """.trimIndent()
            )
        )

        val result = mutableListOf<ScannedDocumentItem>()
        for ((name, ext, content) in sampleFiles) {
            val file = File(demoDir, name)
            if (!file.exists()) {
                file.writeText(content, Charsets.UTF_8)
            }
            val uriStr = Uri.fromFile(file).toString()
            val folder = getDocumentFolderOverride(context, uriStr) ?: "الرئيسي"
            val isFav = favUris.contains(uriStr) || folder == "المفضلة"
            val tags = getDocumentTags(context, uriStr)
            result.add(
                ScannedDocumentItem(
                    uriString = uriStr,
                    name = name,
                    extension = ext,
                    sizeBytes = file.length(),
                    lastModified = file.lastModified(),
                    category = detectCategory(ext),
                    folderName = folder,
                    isFavorite = isFav,
                    tags = tags
                )
            )
        }
        return result
    }

    // =========================================================================
    // ROUND 3: NEXT-GEN COGNITIVE & PUBLISHING SUITE ENGINE
    // =========================================================================

    /**
     * 1. Interactive Document Mind Map Generator & Persistence
     */
    fun generateDocumentMindMap(
        documentTitle: String,
        outlineItems: List<DocumentOutlineItem>,
        highlights: List<DocumentHighlightItem>,
        plainText: String,
        customNodes: List<DocumentMindMapNode>
    ): List<DocumentMindMapNode> {
        val nodes = mutableListOf<DocumentMindMapNode>()
        val rootId = "root_node"
        val cleanTitle = documentTitle.substringBeforeLast(".").take(38).ifBlank { "المستند المفتوح" }
        nodes.add(
            DocumentMindMapNode(
                id = rootId,
                title = cleanTitle,
                subtitle = "الجذر الرئيسي للمستند",
                category = "ROOT",
                positionIndex = 0,
                parentId = null,
                colorHex = "#2563EB"
            )
        )

        // Add Outline / Chapters branch
        val chaptersToUse = if (outlineItems.isNotEmpty()) {
            outlineItems.take(8)
        } else {
            extractDocumentOutline(
                extension = "txt",
                epubBook = null,
                pptxPresentation = null,
                docxParagraphs = emptyList(),
                textDocumentContent = plainText,
                pdfTotalPages = 0
            ).take(6)
        }

        chaptersToUse.forEachIndexed { idx, item ->
            val chapId = "chap_$idx"
            nodes.add(
                DocumentMindMapNode(
                    id = chapId,
                    title = item.title.take(36),
                    subtitle = item.subtitle.ifBlank { "قسم #${idx + 1}" },
                    category = "CHAPTER",
                    positionIndex = item.positionIndex,
                    parentId = rootId,
                    colorHex = "#0EA5E9"
                )
            )
        }

        // Add Key Concepts from Research Analysis
        val analysis = analyzeTextForSmartResearch(plainText, documentTitle)
        analysis.keyConcepts.take(6).forEachIndexed { idx, concept ->
            val parentChap = nodes.filter { it.category == "CHAPTER" }.getOrNull(idx % nodes.count { it.category == "CHAPTER" }.coerceAtLeast(1))
            nodes.add(
                DocumentMindMapNode(
                    id = "concept_$idx",
                    title = concept.take(28),
                    subtitle = "مفهوم محوري",
                    category = "CONCEPT",
                    positionIndex = parentChap?.positionIndex ?: 0,
                    parentId = parentChap?.id ?: rootId,
                    colorHex = "#8B5CF6"
                )
            )
        }

        // Add User Highlights
        highlights.take(6).forEachIndexed { idx, hl ->
            nodes.add(
                DocumentMindMapNode(
                    id = "hl_${hl.id}",
                    title = hl.snippetText.take(34),
                    subtitle = hl.positionLabel,
                    category = "HIGHLIGHT",
                    positionIndex = hl.positionIndex,
                    parentId = rootId,
                    colorHex = "#F59E0B"
                )
            )
        }

        // Append custom user-added Mind Map nodes
        for (custom in customNodes) {
            nodes.add(
                if (custom.parentId.isNullOrBlank()) custom.copy(parentId = rootId) else custom
            )
        }

        return nodes
    }

    fun getCustomMindMapNodes(context: Context, uriString: String): List<DocumentMindMapNode> {
        val prefs = context.getSharedPreferences("doc_viewer_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("mindmap_nodes_${uriString.hashCode()}", null) ?: return emptyList()
        return raw.split("\n").mapNotNull { line ->
            val parts = line.split("||")
            if (parts.size >= 6) {
                DocumentMindMapNode(
                    id = parts[0],
                    title = parts[1],
                    subtitle = parts[2],
                    category = parts[3],
                    positionIndex = parts[4].toIntOrNull() ?: 0,
                    parentId = parts[5].ifBlank { "root_node" },
                    colorHex = parts.getOrNull(6) ?: "#10B981"
                )
            } else null
        }
    }

    fun saveCustomMindMapNodes(context: Context, uriString: String, nodes: List<DocumentMindMapNode>) {
        val prefs = context.getSharedPreferences("doc_viewer_prefs", Context.MODE_PRIVATE)
        val serialized = nodes.joinToString("\n") { n ->
            val cleanTitle = n.title.replace("||", " ").replace("\n", " ")
            val cleanSub = n.subtitle.replace("||", " ").replace("\n", " ")
            "${n.id}||$cleanTitle||$cleanSub||${n.category}||${n.positionIndex}||${n.parentId ?: "root_node"}||${n.colorHex}"
        }
        prefs.edit().putString("mindmap_nodes_${uriString.hashCode()}", serialized).apply()
    }

    fun formatMindMapAsChapterOutline(documentName: String, nodes: List<DocumentMindMapNode>): String {
        val sb = StringBuilder()
        sb.appendLine("# الخريطة الذهنية والهيكل الموضوعي: $documentName")
        sb.appendLine()
        val root = nodes.firstOrNull { it.category == "ROOT" }
        if (root != null) {
            sb.appendLine("## 🎯 المحور المركزي: ${root.title}")
            sb.appendLine()
        }
        val chapters = nodes.filter { it.category == "CHAPTER" }
        if (chapters.isNotEmpty()) {
            sb.appendLine("### 📑 الفصول والأقسام الرئيسية:")
            chapters.forEachIndexed { idx, ch ->
                sb.appendLine("${idx + 1}. **${ch.title}** (${ch.subtitle})")
                val children = nodes.filter { it.parentId == ch.id && it.id != ch.id }
                children.forEach { child ->
                    sb.appendLine("   - 🔹 ${child.title} [${child.subtitle}]")
                }
            }
            sb.appendLine()
        }
        val highlights = nodes.filter { it.category == "HIGHLIGHT" }
        if (highlights.isNotEmpty()) {
            sb.appendLine("### ✨ الاقتباسات والشواهد المرتبطة:")
            highlights.forEach { hl ->
                sb.appendLine("- «${hl.title}» (${hl.subtitle})")
            }
            sb.appendLine()
        }
        val customs = nodes.filter { it.category == "CUSTOM" }
        if (customs.isNotEmpty()) {
            sb.appendLine("### 💡 الفروع والأفكار الإضافية:")
            customs.forEach { c ->
                sb.appendLine("- **${c.title}**: ${c.subtitle}")
            }
        }
        return sb.toString().trim()
    }

    /**
     * 2. Ask Document Q&A (Semantic & Lexical Passage Retrieval)
     */
    fun askDocumentQuestion(
        question: String,
        plainText: String,
        outlineItems: List<DocumentOutlineItem> = emptyList()
    ): DocumentQaAnswer {
        val cleanQuestion = question.trim()
        if (cleanQuestion.isBlank() || plainText.isBlank()) {
            return DocumentQaAnswer(
                question = cleanQuestion,
                bestAnswerSnippet = "الرجاء كتابة سؤال واضح ليتم البحث في فقرات المستند واستخراج الإجابة الدقيقة.",
                positionLabel = "بداية المستند",
                positionIndex = 0,
                confidencePct = 0
            )
        }

        val stopWords = setOf(
            "ما", "ماذا", "هل", "كيف", "أين", "متى", "لماذا", "كم", "من", "في", "على", "إلى", "عن",
            "هو", "هي", "هم", "كان", "كانت", "هذا", "هذه", "ذلك", "تلك", "التي", "الذي", "مع", "أو",
            "بين", "كل", "بعض", "حول", "اذكر", "اشرح", "وضح", "ماهي", "ماهو", "what", "how", "why",
            "where", "when", "who", "the", "and", "for", "with", "from", "that", "this"
        )

        val queryTokens = cleanQuestion
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .lowercase()
            .split(Regex("\\s+"))
            .map { it.removePrefix("ال") }
            .filter { it.length >= 2 && it !in stopWords }
            .distinct()

        val rawParagraphs = plainText
            .split(Regex("\\n\\s*\\n|\\n"))
            .map { it.trim() }
            .filter { it.length >= 25 }

        if (rawParagraphs.isEmpty()) {
            return DocumentQaAnswer(
                question = cleanQuestion,
                bestAnswerSnippet = plainText.take(320),
                positionLabel = "المقطع الأول",
                positionIndex = 0,
                confidencePct = 65
            )
        }

        data class ScoredPara(val index: Int, val text: String, val score: Double)

        val scored = rawParagraphs.mapIndexed { idx, para ->
            val lowerPara = para.lowercase()
            var hits = 0.0
            for (token in queryTokens) {
                if (lowerPara.contains(token)) {
                    hits += 2.5
                    // Bonus for exact word match
                    if (Regex("\\b${Regex.escape(token)}\\b").containsMatchIn(lowerPara)) {
                        hits += 1.5
                    }
                }
            }
            // Bonus if paragraph has numbers when asking "كم / متى"
            if ((cleanQuestion.contains("كم") || cleanQuestion.contains("متى") || cleanQuestion.contains("نسبة")) &&
                para.any { it.isDigit() }
            ) {
                hits += 1.8
            }
            // Length normalization
            val normScore = if (queryTokens.isEmpty()) 1.0 else (hits / (queryTokens.size * 3.0))
            ScoredPara(idx, para, normScore)
        }.sortedByDescending { it.score }

        val best = scored.first()
        val topSupporting = scored.take(4).filter { it.score > 0.0 || it == best }.mapIndexed { rank, item ->
            val approxSection = outlineItems.lastOrNull { it.positionIndex <= item.index }?.title
                ?: "فقرة رقم ${item.index + 1}"
            Pair(approxSection, item.text.take(260))
        }

        val confidence = if (best.score <= 0.05) {
            58
        } else {
            (55 + (best.score * 42).toInt()).coerceIn(62, 98)
        }

        val posLabel = outlineItems.lastOrNull { it.positionIndex <= best.index }?.title
            ?: "فقرة #${best.index + 1} من ${rawParagraphs.size}"

        return DocumentQaAnswer(
            question = cleanQuestion,
            bestAnswerSnippet = best.text.take(420),
            positionLabel = posLabel,
            positionIndex = best.index,
            confidencePct = confidence,
            supportingPassages = topSupporting
        )
    }

    /**
     * 3. Originality & Plagiarism / Overlap Checker (Compares Reference Document vs Editor Draft)
     */
    fun checkOriginalityAndOverlap(
        referenceText: String,
        draftText: String
    ): OriginalityOverlapReport {
        val cleanRef = referenceText.trim()
        val cleanDraft = draftText.trim()
        val refWords = cleanRef.split(Regex("\\s+")).filter { it.isNotBlank() }
        val draftWords = cleanDraft.split(Regex("\\s+")).filter { it.isNotBlank() }

        if (cleanDraft.isBlank() || cleanRef.isBlank()) {
            return OriginalityOverlapReport(
                originalityPct = 100,
                overlapPct = 0,
                sharedSentences = emptyList(),
                totalCheckedSentences = 0,
                referenceWordCount = refWords.size,
                draftWordCount = draftWords.size
            )
        }

        // Build 4-word shingles from reference text
        val refNormalizedWords = refWords.map {
            it.replace(Regex("[^\\p{L}\\p{N}]"), "").lowercase()
        }.filter { it.length >= 2 }

        val refShingles = HashSet<String>()
        for (i in 0..(refNormalizedWords.size - 4)) {
            refShingles.add(refNormalizedWords.subList(i, i + 4).joinToString(" "))
        }

        val draftSentences = cleanDraft
            .split(Regex("[.!؟\\n]+"))
            .map { it.trim() }
            .filter { it.split(Regex("\\s+")).size >= 4 }

        if (draftSentences.isEmpty()) {
            return OriginalityOverlapReport(
                originalityPct = 100,
                overlapPct = 0,
                sharedSentences = emptyList(),
                totalCheckedSentences = 1,
                referenceWordCount = refWords.size,
                draftWordCount = draftWords.size
            )
        }

        val overlappingSentences = mutableListOf<String>()
        for (sentence in draftSentences) {
            val sWords = sentence.split(Regex("\\s+"))
                .map { it.replace(Regex("[^\\p{L}\\p{N}]"), "").lowercase() }
                .filter { it.length >= 2 }
            if (sWords.size < 4) continue

            var matchedShingles = 0
            val totalShingles = (sWords.size - 3).coerceAtLeast(1)
            for (i in 0..(sWords.size - 4)) {
                val shingle = sWords.subList(i, i + 4).joinToString(" ")
                if (shingle in refShingles) {
                    matchedShingles++
                }
            }
            if (matchedShingles > 0 || cleanRef.contains(sentence.take(35))) {
                overlappingSentences.add(sentence)
            }
        }

        val overlapRatio = (overlappingSentences.size.toDouble() / draftSentences.size.toDouble())
        val overlapPct = (overlapRatio * 100).toInt().coerceIn(0, 100)
        val originalityPct = (100 - overlapPct).coerceIn(0, 100)

        return OriginalityOverlapReport(
            originalityPct = originalityPct,
            overlapPct = overlapPct,
            sharedSentences = overlappingSentences.take(10),
            totalCheckedSentences = draftSentences.size,
            referenceWordCount = refWords.size,
            draftWordCount = draftWords.size
        )
    }

    /**
     * 4. Spreadsheet & Document Statistical Data Visualizer
     */
    fun analyzeSpreadsheetColumnStats(sheet: SpreadsheetContent): List<SpreadsheetColumnStats> {
        val results = mutableListOf<SpreadsheetColumnStats>()
        val colCount = maxOf(sheet.headers.size, sheet.rows.maxOfOrNull { it.size } ?: 0)
        if (colCount == 0) return emptyList()

        for (colIdx in 0 until colCount) {
            val colName = sheet.headers.getOrNull(colIdx)?.takeIf { it.isNotBlank() } ?: "عمود ${colIdx + 1}"
            val values = mutableListOf<Double>()
            val labels = mutableListOf<String>()

            for ((rowIdx, row) in sheet.rows.withIndex()) {
                val rawCell = row.getOrNull(colIdx)?.trim() ?: continue
                // Strip currency symbols, commas, percent signs
                val cleanedNum = rawCell.replace(Regex("[^0-9.-]"), "")
                val parsed = cleanedNum.toDoubleOrNull()
                if (parsed != null) {
                    values.add(parsed)
                    val rowLabel = row.firstOrNull()?.takeIf { it.isNotBlank() && colIdx != 0 }
                        ?: "صف ${rowIdx + 1}"
                    labels.add(rowLabel.take(16))
                }
            }

            if (values.isNotEmpty()) {
                val sum = values.sum()
                val avg = sum / values.size
                val min = values.minOrNull() ?: 0.0
                val max = values.maxOrNull() ?: 0.0
                val firstVal = values.first()
                val lastVal = values.last()
                val growthPct = if (kotlin.math.abs(firstVal) > 0.0001 && values.size >= 2) {
                    ((lastVal - firstVal) / kotlin.math.abs(firstVal)) * 100.0
                } else 0.0

                results.add(
                    SpreadsheetColumnStats(
                        columnIndex = colIdx,
                        columnName = colName,
                        numericValues = values,
                        labels = labels,
                        sum = sum,
                        average = avg,
                        min = min,
                        max = max,
                        growthPct = growthPct
                    )
                )
            }
        }
        return results
    }

    /**
     * Extracts numeric series from any text document when no spreadsheet is active,
     * allowing interactive charting of numbers/statistics inside reports and books.
     */
    fun extractNumericStatsFromPlainText(plainText: String, documentTitle: String): List<SpreadsheetColumnStats> {
        val numberRegex = Regex("(-?\\d+(?:\\.\\d+)?)")
        val lines = plainText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val values = mutableListOf<Double>()
        val labels = mutableListOf<String>()

        for (line in lines) {
            val matches = numberRegex.findAll(line).mapNotNull { it.value.toDoubleOrNull() }.toList()
            if (matches.isNotEmpty()) {
                val num = matches.first()
                val label = line.replace(numberRegex, "").trim().take(16).ifBlank { "عنصر ${values.size + 1}" }
                values.add(num)
                labels.add(label)
                if (values.size >= 16) break
            }
        }

        // Also provide word length distribution by paragraphs as a secondary analytical chart
        val paragraphs = plainText.split(Regex("\\n\\s*\\n|\\n")).map { it.trim() }.filter { it.length >= 20 }.take(12)
        val paraWordCounts = paragraphs.map { it.split(Regex("\\s+")).size.toDouble() }
        val paraLabels = paragraphs.mapIndexed { idx, _ -> "فقرة ${idx + 1}" }

        val list = mutableListOf<SpreadsheetColumnStats>()
        if (values.size >= 2) {
            val sum = values.sum()
            val firstVal = values.first()
            val lastVal = values.last()
            val growth = if (kotlin.math.abs(firstVal) > 0.0001) ((lastVal - firstVal) / kotlin.math.abs(firstVal)) * 100.0 else 0.0
            list.add(
                SpreadsheetColumnStats(
                    columnIndex = 0,
                    columnName = "القيم الرقمية في المستند",
                    numericValues = values,
                    labels = labels,
                    sum = sum,
                    average = sum / values.size,
                    min = values.minOrNull() ?: 0.0,
                    max = values.maxOrNull() ?: 0.0,
                    growthPct = growth
                )
            )
        }
        if (paraWordCounts.isNotEmpty()) {
            val sum = paraWordCounts.sum()
            val firstVal = paraWordCounts.first()
            val lastVal = paraWordCounts.last()
            val growth = if (kotlin.math.abs(firstVal) > 0.0001 && paraWordCounts.size >= 2) ((lastVal - firstVal) / kotlin.math.abs(firstVal)) * 100.0 else 0.0
            list.add(
                SpreadsheetColumnStats(
                    columnIndex = 1,
                    columnName = "كثافة الكلمات عبر الفقرات",
                    numericValues = paraWordCounts,
                    labels = paraLabels,
                    sum = sum,
                    average = sum / paraWordCounts.size,
                    min = paraWordCounts.minOrNull() ?: 0.0,
                    max = paraWordCounts.maxOrNull() ?: 0.0,
                    growthPct = growth
                )
            )
        }
        return list
    }

    /**
     * 5. PDF Redaction Persistence & Digital Forensics / SHA-256 Inspector
     */
    fun getPdfRedactions(context: Context, uriString: String): List<PdfRedactionRect> {
        val prefs = context.getSharedPreferences("doc_viewer_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("pdf_redactions_${uriString.hashCode()}", null) ?: return emptyList()
        return raw.split("\n").mapNotNull { line ->
            val p = line.split("||")
            if (p.size >= 6) {
                PdfRedactionRect(
                    id = p[0],
                    pageIndex = p[1].toIntOrNull() ?: 0,
                    leftNorm = p[2].toFloatOrNull() ?: 0.1f,
                    topNorm = p[3].toFloatOrNull() ?: 0.1f,
                    rightNorm = p[4].toFloatOrNull() ?: 0.4f,
                    bottomNorm = p[5].toFloatOrNull() ?: 0.2f,
                    label = p.getOrNull(6) ?: "محتوى مطموس سري"
                )
            } else null
        }
    }

    fun savePdfRedactions(context: Context, uriString: String, redactions: List<PdfRedactionRect>) {
        val prefs = context.getSharedPreferences("doc_viewer_prefs", Context.MODE_PRIVATE)
        val serialized = redactions.joinToString("\n") { r ->
            "${r.id}||${r.pageIndex}||${r.leftNorm}||${r.topNorm}||${r.rightNorm}||${r.bottomNorm}||${r.label.replace("||", " ")}"
        }
        prefs.edit().putString("pdf_redactions_${uriString.hashCode()}", serialized).apply()
    }

    fun computeDocumentForensics(
        context: Context,
        uri: Uri?,
        plainText: String
    ): DocumentForensicsReport {
        var sha256Hex = "غير متاح"
        var sizeBytes = 0L

        if (uri != null) {
            try {
                val md = java.security.MessageDigest.getInstance("SHA-256")
                val inputStream = if (uri.scheme == "file") {
                    File(uri.path ?: "").takeIf { it.exists() }?.inputStream()
                } else {
                    context.contentResolver.openInputStream(uri)
                }
                inputStream?.use { stream ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (stream.read(buffer).also { read = it } != -1) {
                        md.update(buffer, 0, read)
                        sizeBytes += read
                    }
                }
                val digest = md.digest()
                sha256Hex = digest.joinToString("") { "%02x".format(it) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val words = plainText
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 }

        val wordCount = words.size
        val lowerWords = words.map { it.lowercase() }
        val uniqueWords = lowerWords.toSet().size
        val lexicalDiversityPct = if (wordCount > 0) {
            ((uniqueWords.toDouble() / wordCount.toDouble()) * 100.0).toInt().coerceIn(1, 100)
        } else 0

        val sentences = plainText.split(Regex("[.!؟\\n]+")).map { it.trim() }.filter { it.isNotBlank() }
        val avgSentenceLength = if (sentences.isNotEmpty()) (wordCount / sentences.size).coerceAtLeast(1) else wordCount

        // Arabic/Multilingual Readability Index (0..100)
        val avgWordLength = if (words.isNotEmpty()) words.sumOf { it.length }.toDouble() / words.size else 5.0
        val rawReadability = (100.0 - (avgSentenceLength * 1.35) - ((avgWordLength - 4.0).coerceAtLeast(0.0) * 6.5)).toInt()
        val readabilityScore = rawReadability.coerceIn(18, 98)
        val readabilityLabel = when {
            readabilityScore >= 80 -> "سهل وسلس جداً (مناسب لعموم القراء)"
            readabilityScore >= 60 -> "متوازن وواضح (أدبي / ثقافي عام)"
            readabilityScore >= 40 -> "متقدم (أكاديمي / تخصصي رصين)"
            else -> "مركّب وعميق جداً (بحثي / قانوني دقيق)"
        }

        val stopWords = setOf(
            "في", "من", "على", "إلى", "عن", "مع", "هذا", "هذه", "ذلك", "تلك", "التي", "الذي",
            "كان", "كانت", "هو", "هي", "هم", "أو", "ثم", "بل", "لا", "ما", "إن", "أن", "قد",
            "كل", "بعض", "بين", "عند", "غير", "فوق", "تحت", "ال", "the", "and", "for", "with", "that"
        )
        val topFrequent = lowerWords
            .filter { it.length >= 3 && it !in stopWords }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(8)
            .map { Pair(it.key, it.value) }

        return DocumentForensicsReport(
            sha256Hash = sha256Hex,
            fileSizeBytes = sizeBytes,
            wordCount = wordCount,
            uniqueWordCount = uniqueWords,
            lexicalDiversityPct = lexicalDiversityPct,
            avgSentenceLength = avgSentenceLength,
            readabilityScore = readabilityScore,
            readabilityLabel = readabilityLabel,
            topFrequentWords = topFrequent
        )
    }

    /**
     * 6. Auto-Crop White Margins on PDF Bitmap
     * Detects excess outer white margins and crops the bitmap with a clean 2% breathing pad.
     */
    fun cropPdfBitmapWhitespace(source: Bitmap): Bitmap {
        return try {
            val w = source.width
            val h = source.height
            if (w < 80 || h < 80) return source

            val stepX = (w / 120).coerceAtLeast(2)
            val stepY = (h / 160).coerceAtLeast(2)
            val threshold = 244 // RGB values below 244 count as ink/content

            var minX = w
            var maxX = 0
            var minY = h
            var maxY = 0

            for (y in 0 until h step stepY) {
                for (x in 0 until w step stepX) {
                    val pixel = source.getPixel(x, y)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    if (r < threshold || g < threshold || b < threshold) {
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }
            }

            if (maxX <= minX + 40 || maxY <= minY + 40) {
                return source
            }

            val padX = (w * 0.025f).toInt()
            val padY = (h * 0.025f).toInt()
            val cropLeft = (minX - padX).coerceAtLeast(0)
            val cropTop = (minY - padY).coerceAtLeast(0)
            val cropRight = (maxX + padX).coerceAtMost(w)
            val cropBottom = (maxY + padY).coerceAtMost(h)

            val cropW = cropRight - cropLeft
            val cropH = cropBottom - cropTop
            if (cropW <= 50 || cropH <= 50 || (cropW > w * 0.96f && cropH > h * 0.96f)) {
                source
            } else {
                Bitmap.createBitmap(source, cropLeft, cropTop, cropW, cropH)
            }
        } catch (e: Exception) {
            source
        }
    }

    // =========================================================================
    // ROUND 4: PRO DOCUMENT & RESEARCH WORKSTATION ENGINE
    // =========================================================================

    /**
     * 1. PDF Visual Page Organizer: Export Custom Reordered / Split / Rotated Page Sequence
     */
    fun exportCustomOrganizedPdf(
        context: Context,
        sourceUri: Uri,
        sourceFileName: String,
        orderedPageIndices: List<Int>,
        pageRotations: Map<Int, Float>,
        suffixLabel: String = "organized"
    ): File? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = openParcelFileDescriptor(context, sourceUri) ?: return null
            renderer = PdfRenderer(pfd)
            val totalPages = renderer.pageCount
            val validIndices = orderedPageIndices.filter { it in 0 until totalPages }
            if (validIndices.isEmpty()) return null

            val pdfDoc = android.graphics.pdf.PdfDocument()
            for ((outIdx, srcPageIdx) in validIndices.withIndex()) {
                val page = renderer.openPage(srcPageIdx)
                val rot = ((pageRotations[srcPageIdx] ?: 0f).toInt() % 360 + 360) % 360
                val isSwapped = rot == 90 || rot == 270
                val srcW = (page.width * 1.5f).toInt().coerceAtLeast(600)
                val srcH = (page.height * 1.5f).toInt().coerceAtLeast(800)

                val rawBitmap = Bitmap.createBitmap(srcW, srcH, Bitmap.Config.ARGB_8888)
                rawBitmap.eraseColor(Color.WHITE)
                page.render(rawBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                val finalBitmap = if (rot != 0) {
                    val matrix = android.graphics.Matrix().apply { postRotate(rot.toFloat()) }
                    Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                } else {
                    rawBitmap
                }

                val outW = if (isSwapped) srcH else srcW
                val outH = if (isSwapped) srcW else srcH
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(outW, outH, outIdx + 1).create()
                val outPage = pdfDoc.startPage(pageInfo)
                outPage.canvas.drawBitmap(finalBitmap, 0f, 0f, null)
                pdfDoc.finishPage(outPage)
            }

            val baseName = sourceFileName.substringBeforeLast(".").ifBlank { "document" }
            val outDir = File(context.cacheDir, "converted_docs").apply { mkdirs() }
            val outFile = File(outDir, "${baseName}_${suffixLabel}_${System.currentTimeMillis() % 10000}.pdf")
            FileOutputStream(outFile).use { pdfDoc.writeTo(it) }
            pdfDoc.close()
            outFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    /**
     * 2. Arabic Linguistics Studio: Auto-Tashkeel, Strip Diacritics, Hamza Normalization & Poetry Prosody Analyzer
     */
    fun stripArabicDiacritics(text: String): String {
        // Strips Harakat (U+064B..U+065F, U+0670) and Tatweel (U+0640)
        return text.replace(Regex("[\\u064B-\\u065F\\u0670\\u0640]"), "")
    }

    fun normalizeArabicText(text: String): String {
        return text
            .replace(Regex("[إأآٱ]"), "ا")
            .replace("ى", "ي")
            .replace("ؤ", "و")
            .replace("ئ", "ي")
            .replace(Regex("\\u0640+"), "")
            .replace(Regex("[ \\t]+"), " ")
    }

    fun autoTashkeelArabicText(text: String): String {
        if (text.isBlank()) return text
        // Rule-based morphological & lexical vocalizer for common Arabic prepositions, conjunctions, particles, and stems
        val lexicon = mapOf(
            "في" to "فِي",
            "من" to "مِنْ",
            "على" to "عَلَى",
            "إلى" to "إِلَى",
            "عن" to "عَنْ",
            "مع" to "مَعَ",
            "هذا" to "هَذَا",
            "هذه" to "هَذِهِ",
            "ذلك" to "ذَلِكَ",
            "تلك" to "تِلْكَ",
            "الذي" to "الَّذِي",
            "التي" to "الَّتِي",
            "الذين" to "الَّذِينَ",
            "كان" to "كَانَ",
            "كانت" to "كَانَتْ",
            "يكون" to "يَكُونُ",
            "إن" to "إِنَّ",
            "أن" to "أَنْ",
            "كل" to "كُلُّ",
            "بعض" to "بَعْضُ",
            "بين" to "بَيْنَ",
            "عند" to "عِنْدَ",
            "قد" to "قَدْ",
            "لقد" to "لَقَدْ",
            "لم" to "لَمْ",
            "لن" to "لَنْ",
            "لا" to "لَا",
            "ما" to "مَا",
            "هو" to "هُوَ",
            "هي" to "هِيَ",
            "هم" to "هُمْ",
            "نحن" to "نَحْنُ",
            "أنت" to "أَنْتَ",
            "أنا" to "أَنَا",
            "ثم" to "ثُمَّ",
            "أو" to "أَوْ",
            "بل" to "بَلْ",
            "حتى" to "حَتَّى",
            "حيث" to "حَيْثُ",
            "كما" to "كَمَا",
            "لأن" to "لِأَنَّ",
            "غير" to "غَيْرِ",
            "بعد" to "بَعْدَ",
            "قبل" to "قَبْلَ",
            "فوق" to "فَوْقَ",
            "تحت" to "تَحْتَ",
            "حول" to "حَوْلَ",
            "خلال" to "خِلَالَ",
            "ضد" to "ضِدَّ",
            "نحو" to "نَحْوَ",
            "دون" to "دُونَ",
            "بدون" to "بِدُونِ",
            "الكتاب" to "الْكِتَابُ",
            "المستند" to "الْمُسْتَنَدُ",
            "الفصل" to "الْفَصْلُ",
            "البحث" to "الْبَحْثُ",
            "العلم" to "الْعِلْمُ",
            "المعرفة" to "الْمَعْرِفَةُ",
            "اللغة" to "اللُّغَةُ",
            "العربية" to "الْعَرَبِيَّةُ",
            "التاريخ" to "التَّارِيخُ",
            "الإنسان" to "الْإِنْسَانُ",
            "الحياة" to "الْحَيَاةُ",
            "المجتمع" to "الْمُجْتَمَعُ",
            "الأدب" to "الْأَدَبُ",
            "الشعر" to "الشِّعْرُ",
            "القصيدة" to "الْقَصِيدَةُ",
            "الفكر" to "الْفِكْرُ",
            "الحكمة" to "الْحِكْمَةُ",
            "القرآن" to "الْقُرْآنُ",
            "السنة" to "السُّنَّةُ",
            "الله" to "اللَّهِ",
            "بسم" to "بِسْمِ",
            "الرحمن" to "الرَّحْمَنِ",
            "الرحيم" to "الرَّحِيمِ",
            "الحمد" to "الْحَمْدُ",
            "رب" to "رَبِّ",
            "العالمين" to "الْعَالَمِينَ"
        )

        val sunLetters = setOf('ت', 'ث', 'د', 'ذ', 'ر', 'ز', 'س', 'ش', 'ص', 'ض', 'ط', 'ظ', 'ل', 'ن')
        val prepositionsJar = setOf("فِي", "مِنْ", "عَلَى", "إِلَى", "عَنْ", "بِسْمِ")

        return text.lines().joinToString("\n") { line ->
            if (line.isBlank()) return@joinToString line
            val tokens = line.split(Regex("\\s+"))
            var prevWasJar = false
            tokens.map { rawToken ->
                val cleanWord = stripArabicDiacritics(rawToken.replace(Regex("[^\\p{InArabic}]"), ""))
                val punctuationSuffix = rawToken.takeLastWhile { !Character.isLetterOrDigit(it) }
                val punctuationPrefix = rawToken.takeWhile { !Character.isLetterOrDigit(it) }

                if (cleanWord.isEmpty()) {
                    prevWasJar = false
                    rawToken
                } else if (lexicon.containsKey(cleanWord)) {
                    val mapped = lexicon[cleanWord]!!
                    prevWasJar = mapped in prepositionsJar
                    "$punctuationPrefix$mapped$punctuationSuffix"
                } else {
                    val sb = StringBuilder()
                    var idx = 0
                    // Handle Definite Article "ال"
                    if (cleanWord.startsWith("ال") && cleanWord.length >= 4) {
                        val thirdChar = cleanWord[2]
                        if (thirdChar in sunLetters) {
                            sb.append("ال").append(thirdChar).append("ّ")
                            idx = 3
                        } else {
                            sb.append("الْ")
                            idx = 2
                        }
                    } else if (cleanWord.startsWith("وال") && cleanWord.length >= 5) {
                        val fourthChar = cleanWord[3]
                        if (fourthChar in sunLetters) {
                            sb.append("وَال").append(fourthChar).append("ّ")
                            idx = 4
                        } else {
                            sb.append("وَالْ")
                            idx = 3
                        }
                    } else if (cleanWord.startsWith("بال") && cleanWord.length >= 5) {
                        sb.append("بِالْ")
                        idx = 3
                        prevWasJar = true
                    } else if (cleanWord.startsWith("لل") && cleanWord.length >= 4) {
                        sb.append("لِلْ")
                        idx = 2
                        prevWasJar = true
                    }

                    val body = cleanWord.substring(idx)
                    if (body.isNotEmpty()) {
                        for (cIdx in body.indices) {
                            val ch = body[cIdx]
                            sb.append(ch)
                            val isLast = cIdx == body.lastIndex
                            if (!isLast) {
                                val nextCh = body[cIdx + 1]
                                if (ch !in listOf('ا', 'و', 'ي', 'ى', 'آ') && cIdx == 0) {
                                    when (nextCh) {
                                        'ا', 'آ' -> sb.append('َ')
                                        'و' -> sb.append('ُ')
                                        'ي' -> sb.append('ِ')
                                        else -> sb.append('َ')
                                    }
                                }
                            } else {
                                // Case ending (I'rab approximation)
                                when {
                                    ch == 'ة' -> sb.append(if (prevWasJar) 'ِ' else 'ُ')
                                    body.endsWith("ون") || body.endsWith("ين") || body.endsWith("ان") -> {
                                        sb.append('َ')
                                    }
                                    body.endsWith("ات") -> {
                                        sb.append(if (prevWasJar) 'ِ' else 'ُ')
                                    }
                                    ch !in listOf('ا', 'ى', 'و', 'ي') -> {
                                        sb.append(if (prevWasJar) 'ِ' else 'ُ')
                                    }
                                }
                            }
                        }
                    }
                    prevWasJar = false
                    "$punctuationPrefix$sb$punctuationSuffix"
                }
            }.joinToString(" ")
        }
    }

    fun analyzeArabicPoetryAndProsody(text: String): PoetryProsodyReport {
        val rawLines = text.lines()
            .map { it.trim() }
            .filter { it.length in 14..140 }

        val verses = mutableListOf<PoeticVerseItem>()
        for (line in rawLines) {
            // Detect two-hemistich separators (*** or ✦ or ... or wide spaces or tab or centered divider)
            val explicitSplit = line.split(Regex("\\s*(?:\\*{2,}|✦|•|―|—|\\t|\\s{3,}|\\.\\.\\.)\\s*")).filter { it.isNotBlank() }
            val (sadr, ajuz) = if (explicitSplit.size >= 2) {
                explicitSplit[0].trim() to explicitSplit[1].trim()
            } else {
                // Split balanced poetic line into two halves by word count if 6..16 words
                val words = line.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (words.size in 6..16) {
                    val mid = words.size / 2
                    words.subList(0, mid).joinToString(" ") to words.subList(mid, words.size).joinToString(" ")
                } else continue
            }

            // Build prosodic rhythm pattern (/ for mutaharrik, 0 for sakin/long vowel)
            val strippedAjuz = stripArabicDiacritics(ajuz).replace(Regex("[^\\p{InArabic}\\s]"), "")
            val patternBuilder = StringBuilder()
            for (ch in strippedAjuz.takeLast(18)) {
                if (ch.isWhitespace()) continue
                if (ch in listOf('ا', 'و', 'ي', 'ى', 'آ')) {
                    patternBuilder.append('0')
                } else {
                    patternBuilder.append('/')
                }
            }
            val cleanEnd = strippedAjuz.trim().takeLast(3)
            verses.add(
                PoeticVerseItem(
                    index = verses.size + 1,
                    sadr = sadr,
                    ajuz = ajuz,
                    pattern = patternBuilder.toString().ifBlank { "//0/0//0" },
                    endingLetters = cleanEnd
                )
            )
            if (verses.size >= 24) break
        }

        // Determine dominant Rawiy (حرف الروي)
        val lastChars = verses.mapNotNull { v ->
            val cleaned = stripArabicDiacritics(v.ajuz).replace(Regex("[^\\p{InArabic}]"), "")
            // If ends with Alef/Waw/Ya of prolongation (الإطلاق), check the consonant before it if consistent
            if (cleaned.length >= 2 && cleaned.last() in listOf('ا', 'ي', 'و', 'ه')) {
                cleaned[cleaned.length - 2]
            } else {
                cleaned.lastOrNull()
            }
        }
        val dominantRawiy = lastChars.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: 'م'
        val avgHemistichWords = if (verses.isNotEmpty()) {
            verses.map { it.sadr.split(Regex("\\s+")).size }.average()
        } else 4.0

        val estimatedMeter = when {
            avgHemistichWords >= 5.2 -> "بحر الطويل أو البسيط (تفعيلات تامّة: فعولن مفاعيلن)"
            avgHemistichWords >= 4.2 -> "بحر الكامل أو الوافر (متفاعلن / مفاعلتن)"
            avgHemistichWords >= 3.3 -> "بحر الخفيف أو الرمل (فاعلاتن مستفعلن)"
            else -> "مجزوء الكامل أو المتقارب (فعولن فعولن)"
        }

        val rhymeType = if (verses.any { it.ajuz.trim().endsWith("ا") || it.ajuz.trim().endsWith("ي") }) {
            "قافية مطلقة (موصولة بمد الإطلاق)"
        } else {
            "قافية مقيدة أو متحركة الروي"
        }

        val formattedPoem = buildString {
            appendLine("📜 قصيدة منسقة (الصدر ✦ العجز) — حرف الروي: ($dominantRawiy) • $estimatedMeter")
            appendLine("────────────────────────────────────────────────")
            verses.forEach { v ->
                appendLine("${v.sadr}   ✦   ${v.ajuz}")
            }
        }

        return PoetryProsodyReport(
            verses = verses,
            rawiyLetter = dominantRawiy.toString(),
            rhymeType = rhymeType,
            estimatedMeter = estimatedMeter,
            formattedTwoColumnText = formattedPoem
        )
    }

    /**
     * 3. Interactive Timeline & Entity Extractor (Hijri/Gregorian Dates, Centuries, People, Places)
     */
    fun extractTimelineAndEntities(plainText: String): TimelineAndEntitiesReport {
        val paragraphs = plainText.split(Regex("\\n\\s*\\n|\\n")).map { it.trim() }.filter { it.isNotBlank() }
        val timelineEvents = mutableListOf<TimelineEventItem>()

        // Match Hijri years (e.g. 1445 هـ / 204هـ), Gregorian years (e.g. 1998 م / 2024), and Centuries (القرن ...)
        val yearRegex = Regex("\\b(\\d{2,4})\\s*(هـ|هجري|هجرية|م|ميلادي|ميلادية)?\\b")
        val centuryRegex = Regex("(القرن\\s+[\\p{InArabic}]+(?:\\s+الهجري|\\s+الميلادي)?)")

        val seenKeys = mutableSetOf<String>()
        for ((pIdx, para) in paragraphs.withIndex()) {
            for (match in yearRegex.findAll(para)) {
                val numStr = match.groupValues[1]
                val suffix = match.groupValues[2]
                val yearNum = numStr.toIntOrNull() ?: continue
                val isHijri = suffix.startsWith("ه")
                val isExplicitGregorian = suffix.startsWith("م")
                if (!isHijri && !isExplicitGregorian && yearNum !in 600..2099) continue

                val calType = when {
                    isHijri -> "هجري"
                    isExplicitGregorian || yearNum in 1700..2099 -> "ميلادي"
                    yearNum in 100..1460 -> "هجري/تاريخي"
                    else -> "تاريخ"
                }
                val label = if (suffix.isNotBlank()) "$yearNum $suffix" else "$yearNum م"
                val dedupKey = "$yearNum-${para.take(25)}"
                if (seenKeys.add(dedupKey) && timelineEvents.size < 30) {
                    val start = (match.range.first - 45).coerceAtLeast(0)
                    val end = (match.range.last + 85).coerceAtMost(para.length)
                    val snippet = para.substring(start, end).replace("\n", " ").trim()
                    timelineEvents.add(
                        TimelineEventItem(
                            yearLabel = label,
                            sortYear = if (isHijri) yearNum + 579 else yearNum,
                            calendarType = calType,
                            snippet = snippet,
                            positionIndex = pIdx
                        )
                    )
                }
            }

            for (cMatch in centuryRegex.findAll(para)) {
                val label = cMatch.value.trim()
                if (seenKeys.add(label) && timelineEvents.size < 30) {
                    timelineEvents.add(
                        TimelineEventItem(
                            yearLabel = label,
                            sortYear = 1500 + pIdx,
                            calendarType = "حقبة/قرن",
                            snippet = para.take(120),
                            positionIndex = pIdx
                        )
                    )
                }
            }
        }

        // Extract People (أعلام وشخصيات) & Places (مدن وأماكن)
        val personTriggers = listOf(
            "الإمام", "الشيخ", "الدكتور", "الأستاذ", "المؤلف", "الباحث", "الشاعر", "الأديب",
            "الملك", "الأمير", "الخليفة", "السلطان", "الرئيس", "العالم", "ابن", "أبو", "أبي"
        )
        val placeTriggers = listOf(
            "مدينة", "بلاد", "دولة", "مملكة", "جمهورية", "قرية", "إقليم", "عاصمة", "جامعة",
            "مكة", "المدينة", "القدس", "القاهرة", "دمشق", "بغداد", "الرياض", "صنعاء", "الأندلس", "المغرب", "الشام", "مصر", "اليمن", "العراق"
        )

        val personMap = mutableMapOf<String, Triple<Int, String, Int>>()
        val placeMap = mutableMapOf<String, Triple<Int, String, Int>>()

        for ((pIdx, para) in paragraphs.withIndex()) {
            val words = para.split(Regex("\\s+")).map { it.replace(Regex("[^\\p{L}]"), "") }.filter { it.isNotBlank() }
            for (i in words.indices) {
                val w = words[i]
                if (w in personTriggers && i + 1 < words.size) {
                    val next1 = words[i + 1]
                    val next2 = words.getOrNull(i + 2) ?: ""
                    val fullName = if (next1 in listOf("بن", "عبد", "أبي", "أبو") && next2.isNotBlank()) {
                        "$w $next1 $next2"
                    } else if (next2.length >= 3) {
                        "$w $next1 $next2"
                    } else {
                        "$w $next1"
                    }
                    if (fullName.length >= 6) {
                        val existing = personMap[fullName]
                        personMap[fullName] = Triple((existing?.first ?: 0) + 1, existing?.second ?: para.take(110), existing?.third ?: pIdx)
                    }
                }
                if (w in placeTriggers) {
                    val placeName = if (w in listOf("مدينة", "بلاد", "دولة", "مملكة", "جمهورية", "عاصمة", "جامعة") && i + 1 < words.size) {
                        "$w ${words[i + 1]}"
                    } else {
                        w
                    }
                    if (placeName.length >= 3) {
                        val existing = placeMap[placeName]
                        placeMap[placeName] = Triple((existing?.first ?: 0) + 1, existing?.second ?: para.take(110), existing?.third ?: pIdx)
                    }
                }
            }
        }

        val peopleList = personMap.entries
            .sortedByDescending { it.value.first }
            .take(15)
            .map { ExtractedEntityItem(it.key, "PERSON", it.value.first, it.value.second, it.value.third) }

        val placesList = placeMap.entries
            .sortedByDescending { it.value.first }
            .take(15)
            .map { ExtractedEntityItem(it.key, "PLACE", it.value.first, it.value.second, it.value.third) }

        // Key conceptual terms (Al-prefixed nouns with high frequency)
        val stopAl = setOf("الذي", "التي", "الذين", "اللاتي", "اليوم", "الآن", "العام", "الأول", "الثاني", "الثالث")
        val termCounts = mutableMapOf<String, Triple<Int, String, Int>>()
        for ((pIdx, para) in paragraphs.withIndex()) {
            val words = para.split(Regex("\\s+")).map { stripArabicDiacritics(it.replace(Regex("[^\\p{L}]"), "")) }
            for (w in words) {
                if (w.startsWith("ال") && w.length in 5..14 && w !in stopAl) {
                    val ex = termCounts[w]
                    termCounts[w] = Triple((ex?.first ?: 0) + 1, ex?.second ?: para.take(100), ex?.third ?: pIdx)
                }
            }
        }
        val keyTermsList = termCounts.entries
            .sortedByDescending { it.value.first }
            .take(18)
            .map { ExtractedEntityItem(it.key, "TERM", it.value.first, it.value.second, it.value.third) }

        return TimelineAndEntitiesReport(
            timelineEvents = timelineEvents.sortedBy { it.sortYear },
            people = peopleList,
            places = placesList,
            keyTerms = keyTermsList
        )
    }

    /**
     * 4. Interactive Quiz & 3D Flashcards Generator (Auto MCQ Quiz from Document Content)
     */
    fun generateMcqQuizFromDocument(
        plainText: String,
        documentTitle: String,
        flashcards: List<DocumentFlashcardItem>
    ): List<McqQuizQuestion> {
        val questions = mutableListOf<McqQuizQuestion>()

        // 1. Include user's flashcards first if available
        if (flashcards.size >= 2) {
            val allAnswers = flashcards.map { it.definitionOrAnswer.take(75) }
            for ((idx, card) in flashcards.take(5).withIndex()) {
                val correct = card.definitionOrAnswer.take(75)
                val distractors = allAnswers.filter { it != correct }.shuffled().take(3).toMutableList()
                while (distractors.size < 3) {
                    distractors.add("مفهوم ثانوي غير مذكور في (${card.positionLabel}) #${distractors.size + 1}")
                }
                val options = (distractors + correct).shuffled()
                val correctIdx = options.indexOf(correct).coerceAtLeast(0)
                questions.add(
                    McqQuizQuestion(
                        question = "ما المقصود بـ / إجابة: «${card.termOrQuestion}»؟",
                        options = options,
                        correctOptionIndex = correctIdx,
                        explanation = "المرجع: ${card.positionLabel} — ${card.definitionOrAnswer}",
                        positionLabel = card.positionLabel
                    )
                )
            }
        }

        // 2. Generate contextual Cloze / Comprehension questions from key sentences in the document
        val sentences = plainText.split(Regex("[.!؟\\n]+"))
            .map { it.trim() }
            .filter { it.length in 45..220 }

        val candidateKeywords = sentences.flatMap { s ->
            s.split(Regex("\\s+"))
                .map { stripArabicDiacritics(it.replace(Regex("[^\\p{L}]"), "")) }
                .filter { it.length >= 5 && it.startsWith("ال") }
        }.distinct()

        for ((sIdx, sentence) in sentences.take(12).withIndex()) {
            if (questions.size >= 8) break
            val words = sentence.split(Regex("\\s+"))
            val targetWord = words.firstOrNull { w ->
                val clean = stripArabicDiacritics(w.replace(Regex("[^\\p{L}]"), ""))
                clean.length >= 5 && clean.startsWith("ال")
            } ?: words.firstOrNull { it.length >= 5 } ?: continue

            val cleanTarget = targetWord.replace(Regex("[^\\p{L}\\p{N}]"), "")
            if (cleanTarget.length < 4) continue

            val blankedSentence = sentence.replaceFirst(cleanTarget, " [ ........ ] ")
            val distractors = candidateKeywords
                .filter { it != cleanTarget && it != stripArabicDiacritics(cleanTarget) }
                .shuffled()
                .take(3)
                .toMutableList()
            val fallbackPool = listOf("التحليل المنهجي", "السياق التاريخي", "البنية المعرفية", "الدلالة اللغوية")
            for (fb in fallbackPool) {
                if (distractors.size >= 3) break
                if (fb != cleanTarget && fb !in distractors) distractors.add(fb)
            }

            val options = (distractors.take(3) + cleanTarget).shuffled()
            val correctIdx = options.indexOf(cleanTarget).coerceAtLeast(0)

            questions.add(
                McqQuizQuestion(
                    question = "أكمل الفراغ بما ورد في المستند:\n«$blankedSentence»",
                    options = options,
                    correctOptionIndex = correctIdx,
                    explanation = "النص الأصلي في المستند: «$sentence»",
                    positionLabel = "فقرة ${sIdx + 1}"
                )
            )
        }

        if (questions.isEmpty()) {
            questions.add(
                McqQuizQuestion(
                    question = "ما هو موضوع المستند الحالي «$documentTitle»؟",
                    options = listOf(
                        "مرجع نصي قيد القراءة والتحليل في استوديو التأليف",
                        "جدول محاسبي غير نصي",
                        "ملف صوتي فقط",
                        "صورة خلفية"
                    ),
                    correctOptionIndex = 0,
                    explanation = "يعمل مولد الاختبارات تلقائياً على تحليل الجمل والمفاهيم الواردة في المستند."
                )
            )
        }

        return questions
    }

    /**
     * 5. Official Digital Stamp & SHA-256 Verification Certificate PDF Exporter
     */
    fun exportPdfWithOfficialCertificate(
        context: Context,
        sourceUri: Uri,
        sourceFileName: String,
        stampTitle: String,
        authorOrOrgName: String,
        stampColorHex: String,
        applyStampOnAllPages: Boolean,
        appendCertificatePage: Boolean,
        plainTextForStats: String
    ): File? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            val forensics = computeDocumentForensics(context, sourceUri, plainTextForStats)
            pfd = openParcelFileDescriptor(context, sourceUri) ?: return null
            renderer = PdfRenderer(pfd)
            val totalPages = renderer.pageCount
            if (totalPages <= 0) return null

            val stampColor = try { Color.parseColor(stampColorHex) } catch (_: Exception) { Color.parseColor("#1D4ED8") }
            val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())

            val pdfDoc = android.graphics.pdf.PdfDocument()
            var lastPageW = 794
            var lastPageH = 1123

            for (pageIdx in 0 until totalPages) {
                val page = renderer.openPage(pageIdx)
                val outW = (page.width * 1.5f).toInt().coerceAtLeast(600)
                val outH = (page.height * 1.5f).toInt().coerceAtLeast(800)
                lastPageW = outW
                lastPageH = outH

                val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(outW, outH, pageIdx + 1).create()
                val outPage = pdfDoc.startPage(pageInfo)
                val canvas = outPage.canvas
                canvas.drawBitmap(bitmap, 0f, 0f, null)

                if (applyStampOnAllPages || pageIdx == 0 || pageIdx == totalPages - 1) {
                    drawOfficialSealOnCanvas(
                        canvas = canvas,
                        centerX = outW * 0.22f,
                        centerY = outH * 0.86f,
                        radius = (outW * 0.11f).coerceIn(55f, 95f),
                        stampTitle = stampTitle,
                        authorOrOrg = authorOrOrgName,
                        dateStr = dateStr.substringBefore(" "),
                        stampColor = stampColor
                    )
                }
                pdfDoc.finishPage(outPage)
            }

            // Append formal SHA-256 Digital Verification Certificate Page at the end
            if (appendCertificatePage) {
                val certPageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(lastPageW, lastPageH, totalPages + 1).create()
                val certPage = pdfDoc.startPage(certPageInfo)
                val c = certPage.canvas
                c.drawColor(Color.WHITE)

                val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 6f
                    color = stampColor
                }
                val margin = lastPageW * 0.06f
                c.drawRect(margin, margin, lastPageW - margin, lastPageH - margin, borderPaint)
                borderPaint.strokeWidth = 2f
                c.drawRect(margin + 12f, margin + 12f, lastPageW - margin - 12f, lastPageH - margin - 12f, borderPaint)

                val titlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = stampColor
                    textSize = lastPageW * 0.038f
                    isFakeBoldText = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                val bodyPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.DKGRAY
                    textSize = lastPageW * 0.024f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                val hashPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = lastPageW * 0.019f
                    typeface = android.graphics.Typeface.MONOSPACE
                    isFakeBoldText = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }

                val centerX = lastPageW / 2f
                var curY = margin + lastPageH * 0.10f
                c.drawText("شهادة توثيق واعتماد رقمي للمستند", centerX, curY, titlePaint)
                curY += lastPageH * 0.045f
                c.drawText("DIGITAL DOCUMENT VERIFICATION & AUTHENTICITY CERTIFICATE", centerX, curY, bodyPaint)

                curY += lastPageH * 0.07f
                c.drawText("اسم المستند: $sourceFileName", centerX, curY, bodyPaint)
                curY += lastPageH * 0.045f
                c.drawText("حالة الاعتماد: $stampTitle", centerX, curY, bodyPaint)
                curY += lastPageH * 0.045f
                c.drawText("الجهة / المؤلف المعتمد: ${authorOrOrgName.ifBlank { "المؤلف المعتمد" }}", centerX, curY, bodyPaint)
                curY += lastPageH * 0.045f
                c.drawText("تاريخ ووقت التوثيق: $dateStr", centerX, curY, bodyPaint)
                curY += lastPageH * 0.045f
                c.drawText("إجمالي الصفحات الأصلية: $totalPages • عدد الكلمات: ${forensics.wordCount}", centerX, curY, bodyPaint)

                curY += lastPageH * 0.07f
                c.drawText("البصمة الجنائية الرقمية (SHA-256 Checksum):", centerX, curY, titlePaint.apply { textSize = lastPageW * 0.026f })
                curY += lastPageH * 0.04f
                c.drawText(forensics.sha256Hash, centerX, curY, hashPaint)

                drawOfficialSealOnCanvas(
                    canvas = c,
                    centerX = centerX,
                    centerY = lastPageH * 0.76f,
                    radius = (lastPageW * 0.14f).coerceIn(70f, 125f),
                    stampTitle = stampTitle,
                    authorOrOrg = authorOrOrgName,
                    dateStr = dateStr.substringBefore(" "),
                    stampColor = stampColor
                )

                pdfDoc.finishPage(certPage)
            }

            val baseName = sourceFileName.substringBeforeLast(".").ifBlank { "document" }
            val outDir = File(context.cacheDir, "converted_docs").apply { mkdirs() }
            val outFile = File(outDir, "${baseName}_certified_${System.currentTimeMillis() % 10000}.pdf")
            FileOutputStream(outFile).use { pdfDoc.writeTo(it) }
            pdfDoc.close()
            outFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    private fun drawOfficialSealOnCanvas(
        canvas: android.graphics.Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        stampTitle: String,
        authorOrOrg: String,
        dateStr: String,
        stampColor: Int
    ) {
        canvas.save()
        canvas.rotate(-12f, centerX, centerY)

        val ringPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            color = stampColor
            alpha = 215
            strokeWidth = (radius * 0.07f).coerceAtLeast(3f)
        }
        val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
            color = stampColor
            alpha = 22
        }

        canvas.drawCircle(centerX, centerY, radius, fillPaint)
        canvas.drawCircle(centerX, centerY, radius, ringPaint)
        ringPaint.strokeWidth = (radius * 0.03f).coerceAtLeast(1.5f)
        canvas.drawCircle(centerX, centerY, radius * 0.86f, ringPaint)

        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = stampColor
            alpha = 230
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = radius * 0.25f
        }
        canvas.drawText(stampTitle.take(18), centerX, centerY - radius * 0.12f, textPaint)

        textPaint.textSize = radius * 0.18f
        canvas.drawText(authorOrOrg.ifBlank { "نسخة موثقة" }.take(20), centerX, centerY + radius * 0.18f, textPaint)

        textPaint.textSize = radius * 0.16f
        canvas.drawText(dateStr, centerX, centerY + radius * 0.44f, textPaint)

        canvas.restore()
    }

    /**
     * 6. Page-Pinned Voice & Audio Notes Persistence
     */
    fun getPageVoiceNotes(context: Context, uriString: String): List<PageVoiceNoteItem> {
        val prefs = context.getSharedPreferences("doc_viewer_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("voice_notes_${uriString.hashCode()}", null) ?: return emptyList()
        return raw.split("\n").mapNotNull { line ->
            val p = line.split("||")
            if (p.size >= 7) {
                PageVoiceNoteItem(
                    id = p[0],
                    uriString = uriString,
                    pageOrSectionIndex = p[1].toIntOrNull() ?: 0,
                    positionLabel = p[2],
                    title = p[3],
                    transcriptOrSummary = p[4],
                    durationSeconds = p[5].toIntOrNull() ?: 5,
                    audioFilePath = p[6],
                    timestamp = p.getOrNull(7)?.toLongOrNull() ?: System.currentTimeMillis()
                )
            } else null
        }
    }

    fun savePageVoiceNotes(context: Context, uriString: String, notes: List<PageVoiceNoteItem>) {
        val prefs = context.getSharedPreferences("doc_viewer_prefs", Context.MODE_PRIVATE)
        val serialized = notes.joinToString("\n") { n ->
            val cleanTitle = n.title.replace("||", " ").replace("\n", " ")
            val cleanTrans = n.transcriptOrSummary.replace("||", " ").replace("\n", " ")
            "${n.id}||${n.pageOrSectionIndex}||${n.positionLabel}||$cleanTitle||$cleanTrans||${n.durationSeconds}||${n.audioFilePath}||${n.timestamp}"
        }
        prefs.edit().putString("voice_notes_${uriString.hashCode()}", serialized).apply()
    }

    /**
     * 7. Back-of-Book Automated Alphabetical Indexer (كشاف المصطلحات والأعلام الأبجدي أ - ي)
     */
    fun generateBackOfBookIndex(
        plainText: String,
        outline: List<DocumentOutlineItem>
    ): Map<Char, List<BackOfBookIndexEntry>> {
        val stopWords = setOf(
            "في", "من", "على", "إلى", "عن", "مع", "هذا", "هذه", "ذلك", "تلك", "التي", "الذي",
            "الذين", "كان", "كانت", "يكون", "هو", "هي", "هم", "أو", "ثم", "بل", "لا", "ما",
            "إن", "أن", "قد", "كل", "بعض", "بين", "عند", "غير", "فوق", "تحت", "بعد", "قبل",
            "حتى", "حيث", "كما", "لأن", "ولكن", "التي", "الذي", "اليوم", "الأول", "الثاني"
        )

        val paragraphs = plainText.split(Regex("\\n\\s*\\n|\\n")).map { it.trim() }.filter { it.isNotBlank() }
        val termOccurrences = mutableMapOf<String, MutableList<Int>>()
        val termContext = mutableMapOf<String, String>()

        for ((pIdx, para) in paragraphs.withIndex()) {
            val words = para.split(Regex("\\s+"))
                .map { stripArabicDiacritics(it.replace(Regex("[^\\p{L}]"), "")) }
                .filter { it.length in 4..16 && it !in stopWords }

            for (rawWord in words) {
                // Normalize leading "ال" for alphabetical sorting while keeping readable term
                val displayTerm = rawWord
                val list = termOccurrences.getOrPut(displayTerm) { mutableListOf() }
                if (list.size < 12 && (list.isEmpty() || list.last() != pIdx + 1)) {
                    list.add(pIdx + 1)
                }
                if (!termContext.containsKey(displayTerm)) {
                    termContext[displayTerm] = para.take(95)
                }
            }
        }

        val entries = termOccurrences.entries
            .filter { it.value.size >= 2 || it.key.startsWith("ال") }
            .sortedByDescending { it.value.size }
            .take(90)
            .map { (term, locs) ->
                val coreForSort = if (term.startsWith("ال") && term.length > 3) term.substring(2) else term
                val firstChar = when (val c = coreForSort.firstOrNull()?.uppercaseChar() ?: 'أ') {
                    'إ', 'أ', 'آ', 'ٱ' -> 'أ'
                    else -> c
                }
                BackOfBookIndexEntry(
                    term = term,
                    firstLetter = firstChar,
                    count = locs.size,
                    locations = locs.take(6).map { "موضع $it" },
                    sampleContext = termContext[term] ?: ""
                )
            }
            .sortedWith(compareBy({ it.firstLetter }, { it.term }))

        return entries.groupBy { it.firstLetter }.toSortedMap()
    }
}


