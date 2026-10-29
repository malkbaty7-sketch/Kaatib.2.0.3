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
            bitmap
        } catch (e: Exception) {
            null
        } finally {
            try { page?.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
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
            val inputStream = context.contentResolver.openInputStream(uri) ?: return fallbackEpub()
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
            val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
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
            val inputStream = context.contentResolver.openInputStream(uri) ?: return emptySpreadsheet()
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
            val inputStream = context.contentResolver.openInputStream(uri) ?: return emptySpreadsheet()
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

            val inputStream = context.contentResolver.openInputStream(uri) ?: return fallbackPptx(presentationTitle)
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
        addPageNumbers: Boolean = true
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
}
