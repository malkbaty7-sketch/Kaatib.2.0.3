package com.example.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class PdfDiagnosticReport(
    val fileName: String,
    val fileSizeBytes: Long,
    val scannedBytes: Int,
    val hasValidPdfHeader: Boolean,
    val hasEncryptionMarker: Boolean,
    val hasToUnicodeMap: Boolean,
    val approximateImageCount: Int,
    val approximatePageMarkers: Int,
    val hasRawArabicChars: Boolean,
    val findings: List<String>,
    val fullSummary: String
)

data class AccessDatabaseInfo(
    val fileName: String,
    val format: String,
    val sizeBytes: Long,
    val signature: String,
    val recognized: Boolean,
    val noticeMessage: String
)

data class DocxEditableTable(
    val rows: List<List<String>>
)

object DocmoDocumentBridge {

    fun sanitizeFilename(filename: String?, fallback: String): String {
        val raw = if (filename.isNullOrBlank()) fallback else filename
        val cleaned = raw.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return if (cleaned.isBlank()) fallback else cleaned
    }

    fun guessMimeType(filename: String): String {
        val ext = filename.substringAfterLast(".", "").lowercase()
        return when (ext) {
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "rtf" -> "application/rtf"
            "csv" -> "text/csv"
            "txt", "md" -> "text/plain"
            "html", "htm" -> "text/html"
            "epub" -> "application/epub+zip"
            "odt" -> "application/vnd.oasis.opendocument.text"
            "ods" -> "application/vnd.oasis.opendocument.spreadsheet"
            "odp" -> "application/vnd.oasis.opendocument.presentation"
            "mdb", "accdb" -> "application/octet-stream"
            else -> "application/octet-stream"
        }
    }

    // =========================================================================
    // 1. Native Plugin Ports (OpenDocumentPlugin, OpenPdfPlugin, SaveFilePlugin)
    // =========================================================================

    fun openDocumentFromBytes(
        context: Context,
        filename: String,
        mimeType: String?,
        bytes: ByteArray
    ): Result<Boolean> {
        if (bytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("لم تصل بيانات المستند"))
        }
        if (bytes.size > 45 * 1024 * 1024) {
            return Result.failure(IllegalArgumentException("حجم المستند كبير جدًا للفتح المباشر. احفظه أولًا ثم افتحه من مدير الملفات."))
        }
        var cached: File? = null
        return try {
            val dir = File(context.cacheDir, "docmo-open")
            if (!dir.exists() && !dir.mkdirs()) {
                return Result.failure(IllegalStateException("تعذر تجهيز مساحة فتح المستند"))
            }
            val safeName = sanitizeFilename(filename, "document")
            cached = File(dir, "${System.currentTimeMillis()}-$safeName")
            FileOutputStream(cached).use { it.write(bytes) }

            val resolvedMime = if (mimeType.isNullOrBlank()) guessMimeType(filename) else mimeType
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cached)
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, resolvedMime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(viewIntent, "فتح المستند باستخدام").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(chooser)
                Result.success(true)
            } catch (noViewer: ActivityNotFoundException) {
                cached.delete()
                Result.failure(IllegalStateException("لا يوجد تطبيق مثبت يدعم هذه الصيغة. ثبّت عارضًا أو محررًا مناسبًا ثم أعد المحاولة."))
            }
        } catch (e: Exception) {
            cached?.let { if (it.exists()) it.delete() }
            Result.failure(IllegalStateException("تعذر فتح المستند: ${e.message}", e))
        }
    }

    fun openDocumentFromUri(
        context: Context,
        sourceUri: Uri,
        filename: String,
        mimeType: String? = null
    ): Result<Boolean> {
        return try {
            val bytes = if (sourceUri.scheme == "file") {
                File(sourceUri.path ?: "").readBytes()
            } else {
                context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
                    ?: return Result.failure(IllegalStateException("تعذر قراءة بيانات المستند"))
            }
            openDocumentFromBytes(context, filename, mimeType ?: guessMimeType(filename), bytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openPdfFromBytes(
        context: Context,
        filename: String,
        bytes: ByteArray
    ): Result<Boolean> {
        if (bytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("لم تصل بيانات ملف PDF"))
        }
        return try {
            val dir = File(context.cacheDir, "docmo-preview")
            if (!dir.exists() && !dir.mkdirs()) {
                return Result.failure(IllegalStateException("تعذر تجهيز مساحة المعاينة"))
            }
            var safeName = sanitizeFilename(filename, "document.pdf")
            if (!safeName.lowercase().endsWith(".pdf")) safeName += ".pdf"
            val pdfFile = File(dir, safeName)
            FileOutputStream(pdfFile).use { it.write(bytes) }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                Result.success(true)
            } catch (noViewer: ActivityNotFoundException) {
                Result.failure(IllegalStateException("لا يوجد عارض PDF مثبت. ثبّت عارض PDF ثم أعد المحاولة."))
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException("تعذر فتح ملف PDF: ${e.message}", e))
        }
    }

    fun openPdfFromUri(context: Context, sourceUri: Uri, filename: String): Result<Boolean> {
        return try {
            val bytes = if (sourceUri.scheme == "file") {
                File(sourceUri.path ?: "").readBytes()
            } else {
                context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
                    ?: return Result.failure(IllegalStateException("تعذرت قراءة ملف PDF"))
            }
            openPdfFromBytes(context, filename, bytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun saveBytesToDestinationUri(context: Context, destUri: Uri, bytes: ByteArray): Result<String> {
        return try {
            context.contentResolver.openOutputStream(destUri, "w")?.use { output ->
                output.write(bytes)
                output.flush()
            } ?: return Result.failure(IllegalStateException("تعذر فتح الملف للكتابة"))
            Result.success(destUri.toString())
        } catch (e: Exception) {
            Result.failure(IllegalStateException("تعذر حفظ الملف: ${e.message}", e))
        }
    }

    fun saveFileToDestinationUri(context: Context, destUri: Uri, sourceFile: File): Result<String> {
        return try {
            val bytes = sourceFile.readBytes()
            saveBytesToDestinationUri(context, destUri, bytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // 2. Local PDF Diagnostic Inspector (DOCMO inspectPdf port)
    // =========================================================================

    fun inspectPdfLocally(context: Context, uri: Uri, fileName: String): PdfDiagnosticReport {
        val (_, totalSize) = MultiFormatDocumentEngine.getFileNameAndSize(context, uri)
        val maxSample = 12 * 1024 * 1024
        val bytes = try {
            if (uri.scheme == "file") {
                val f = File(uri.path ?: "")
                f.inputStream().use { ins ->
                    val buf = ByteArray(minOf(f.length().toInt().coerceAtLeast(0), maxSample))
                    val read = ins.read(buf)
                    if (read > 0) buf.copyOf(read) else ByteArray(0)
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { ins ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    var totalRead = 0
                    while (totalRead < maxSample) {
                        val toRead = minOf(buf.size, maxSample - totalRead)
                        val r = ins.read(buf, 0, toRead)
                        if (r <= 0) break
                        out.write(buf, 0, r)
                        totalRead += r
                    }
                    out.toByteArray()
                } ?: ByteArray(0)
            }
        } catch (_: Exception) {
            ByteArray(0)
        }

        val sample = String(bytes, StandardCharsets.ISO_8859_1)
        val utf8Sample = try { String(bytes, StandardCharsets.UTF_8) } catch (_: Exception) { sample }
        val isPdf = sample.startsWith("%PDF-")
        val encryptedMarker = Regex("""/Encrypt\b""").containsMatchIn(sample)
        val hasToUnicode = Regex("""/ToUnicode\b""").containsMatchIn(sample)
        val imageObjects = Regex("""/Subtype\s*/Image\b""").findAll(sample).count()
        val pageMarkers = Regex("""/Type\s*/Page\b""").findAll(sample).count()
        val hasArabicLiteral = Regex("""[\u0600-\u06FF]""").containsMatchIn(utf8Sample)

        val findings = listOf(
            "ترويسة PDF: ${if (isPdf) "صحيحة مبدئيًا (%PDF-)" else "لم يتم التعرف عليها"}",
            "علامة تشفير ظاهرة: ${if (encryptedMarker) "نعم — قد يتطلب الملف كلمة مرور أو صلاحيات إضافية" else "لم تُكتشف في الجزء المفحوص"}",
            "خرائط Unicode للخطوط (/ToUnicode): ${if (hasToUnicode) "موجودة في الجزء المفحوص" else "لم تُكتشف في الجزء المفحوص"}",
            "علامات صور تقريبية: $imageObjects",
            "علامات صفحات تقريبية: $pageMarkers",
            "محارف عربية ظاهرة في البيانات الخام: ${if (hasArabicLiteral) "نعم" else "لم تُكتشف؛ قد تكون النصوص مضغوطة أو مرمّزة"}"
        )

        val limitNote = if (totalSize > bytes.size && bytes.isNotEmpty()) {
            " فُحص أول 12 ميغابايت فقط لتقليل استهلاك ذاكرة الهاتف."
        } else ""

        val summary = "فحص PDF محلي لملف $fileName: ${findings.joinToString(" • ")}.$limitNote هذا فحص استدلالي محلي 100% دون رفع الملف."

        return PdfDiagnosticReport(
            fileName = fileName,
            fileSizeBytes = totalSize.coerceAtLeast(bytes.size.toLong()),
            scannedBytes = bytes.size,
            hasValidPdfHeader = isPdf,
            hasEncryptionMarker = encryptedMarker,
            hasToUnicodeMap = hasToUnicode,
            approximateImageCount = imageObjects,
            approximatePageMarkers = pageMarkers,
            hasRawArabicChars = hasArabicLiteral,
            findings = findings,
            fullSummary = summary
        )
    }

    // =========================================================================
    // 3. Microsoft Access (.mdb / .accdb) Inspector & Backup Copy
    // =========================================================================

    fun inspectAccessDatabase(context: Context, uri: Uri, fileName: String): AccessDatabaseInfo {
        val ext = fileName.substringAfterLast(".", "mdb").lowercase()
        val (_, totalSize) = MultiFormatDocumentEngine.getFileNameAndSize(context, uri)
        val header = try {
            if (uri.scheme == "file") {
                val f = File(uri.path ?: "")
                f.inputStream().use { ins ->
                    val buf = ByteArray(32)
                    val r = ins.read(buf)
                    if (r > 0) buf.copyOf(r) else ByteArray(0)
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { ins ->
                    val buf = ByteArray(32)
                    val r = ins.read(buf)
                    if (r > 0) buf.copyOf(r) else ByteArray(0)
                } ?: ByteArray(0)
            }
        } catch (_: Exception) {
            ByteArray(0)
        }

        val signature = if (header.size >= 20) {
            String(header.copyOfRange(4, 20), StandardCharsets.US_ASCII)
                .replace("\u0000", "")
                .trim()
        } else {
            ""
        }

        val recognized = if (ext == "mdb") {
            signature.contains("Standard Jet DB", ignoreCase = true)
        } else {
            signature.contains("Standard ACE DB", ignoreCase = true)
        }

        val notice = if (recognized) {
            "تم التعرف مبدئيًا على ملف ${ext.uppercase()} محليًا (${signature})."
        } else {
            "تم فتح معلومات الملف، لكن ترويسة قاعدة البيانات غير مطابقة للنمط المتوقع."
        }

        return AccessDatabaseInfo(
            fileName = fileName,
            format = ext.uppercase(),
            sizeBytes = totalSize,
            signature = signature.ifBlank { "غير معروف" },
            recognized = recognized,
            noticeMessage = notice
        )
    }

    fun createAccessBackupCopy(context: Context, uri: Uri, fileName: String): File {
        val outDir = File(context.cacheDir, "conversions").apply { if (!exists()) mkdirs() }
        val ext = fileName.substringAfterLast(".", "mdb").lowercase()
        val base = fileName.substringBeforeLast(".")
        val outFile = File(outDir, "${base}-copy.$ext")

        val inputStream = if (uri.scheme == "file") {
            File(uri.path ?: "").inputStream()
        } else {
            context.contentResolver.openInputStream(uri)
        }
        inputStream?.use { ins ->
            FileOutputStream(outFile).use { outs ->
                ins.copyTo(outs)
            }
        }
        return outFile
    }

    // =========================================================================
    // 4. Office Open XML (.xlsx & .csv & .docx) Local Exporters (DOCMO parity)
    // =========================================================================

    private fun columnRef(colIndex: Int): String {
        var n = colIndex + 1
        var ref = ""
        while (n > 0) {
            val rem = (n - 1) % 26
            ref = (65 + rem).toChar() + ref
            n = (n - 1) / 26
        }
        return ref
    }

    private fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    fun exportRowsToXlsx(
        context: Context,
        baseName: String,
        headers: List<String>,
        rows: List<List<String>>,
        suffix: String = "-edited"
    ): File {
        val outDir = File(context.cacheDir, "conversions").apply { if (!exists()) mkdirs() }
        val cleanBase = baseName.replace(Regex("""\.(xlsx|xls|csv|tsv|pdf|txt)$""", RegexOption.IGNORE_CASE), "")
        val outFile = File(outDir, "${cleanBase}${suffix}.xlsx")

        val allRows = buildList {
            if (headers.isNotEmpty()) add(headers)
            addAll(rows)
        }

        val sheetXmlBuilder = StringBuilder()
        sheetXmlBuilder.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sheetXmlBuilder.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView rightToLeft="1" workbookViewId="0"/></sheetViews><sheetData>""")

        for ((ri, row) in allRows.withIndex()) {
            sheetXmlBuilder.append("""<row r="${ri + 1}">""")
            for ((ci, cell) in row.withIndex()) {
                val ref = "${columnRef(ci)}${ri + 1}"
                val safeText = escapeXml(cell)
                sheetXmlBuilder.append("""<c r="$ref" t="inlineStr"><is><t xml:space="preserve">$safeText</t></is></c>""")
            }
            sheetXmlBuilder.append("""</row>""")
        }
        sheetXmlBuilder.append("""</sheetData></worksheet>""")

        ZipOutputStream(FileOutputStream(outFile)).use { zos ->
            putZipString(
                zos,
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""
            )
            putZipString(
                zos,
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
            )
            putZipString(
                zos,
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""
            )
            putZipString(
                zos,
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""
            )
            putZipString(zos, "xl/worksheets/sheet1.xml", sheetXmlBuilder.toString())
        }

        return outFile
    }

    fun exportExtractedPdfTextToXlsx(context: Context, baseName: String, extractedText: String): File {
        val headers = listOf("الصفحة", "السطر", "النص")
        val rows = parsePdfTextIntoPageLineRows(extractedText)
        return exportRowsToXlsx(context, baseName, headers, rows, suffix = "-extracted")
    }

    fun exportExtractedPdfTextToCsv(context: Context, baseName: String, extractedText: String): File {
        val outDir = File(context.cacheDir, "conversions").apply { if (!exists()) mkdirs() }
        val cleanBase = baseName.replace(Regex("""\.pdf(\.txt)?$""", RegexOption.IGNORE_CASE), "")
        val outFile = File(outDir, "${cleanBase}-extracted.csv")

        val rows = listOf(listOf("الصفحة", "السطر", "النص")) + parsePdfTextIntoPageLineRows(extractedText)
        val csvBody = rows.joinToString("\r\n") { row ->
            row.joinToString(",") { cell ->
                "\"${cell.replace("\"", "\"\"")}\""
            }
        }
        // UTF-8 BOM for Arabic support in Microsoft Excel
        outFile.writeText("\uFEFF" + csvBody, Charsets.UTF_8)
        return outFile
    }

    private fun parsePdfTextIntoPageLineRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var currentPage = 1
        var lineIndex = 0
        for (rawLine in text.split(Regex("""\r?\n"""))) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue
            val pageMatch = Regex("""^===\s*صفحة\s+(\d+)\s*===$""").find(trimmed)
            if (pageMatch != null) {
                currentPage = pageMatch.groupValues[1].toIntOrNull() ?: currentPage
                lineIndex = 0
                continue
            }
            lineIndex++
            rows.add(listOf(currentPage.toString(), lineIndex.toString(), trimmed))
        }
        if (rows.isEmpty()) {
            rows.add(listOf("1", "1", text.trim().ifEmpty { "لا يوجد نص مستخرج" }))
        }
        return rows
    }

    fun exportEditedDocxParagraphs(
        context: Context,
        baseName: String,
        paragraphs: List<String>,
        tables: List<DocxEditableTable> = emptyList()
    ): File {
        val outDir = File(context.cacheDir, "conversions").apply { if (!exists()) mkdirs() }
        val cleanBase = baseName.replace(Regex("""\.docx$""", RegexOption.IGNORE_CASE), "")
        val outFile = File(outDir, "${cleanBase}-edited.docx")

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>""")

        for (p in paragraphs) {
            val isRtl = isRtlText(p)
            sb.append("<w:p><w:pPr>")
            if (isRtl) sb.append("<w:bidi/>")
            sb.append("<w:spacing w:after=\"160\"/></w:pPr><w:r><w:rPr>")
            if (isRtl) sb.append("<w:rtl/>")
            sb.append("<w:sz w:val=\"24\"/></w:rPr><w:t xml:space=\"preserve\">")
            sb.append(escapeXml(p))
            sb.append("</w:t></w:r></w:p>")
        }

        for (table in tables) {
            sb.append("<w:tbl><w:tblPr><w:bidiVisual/></w:tblPr>")
            for (row in table.rows) {
                sb.append("<w:tr>")
                for (cell in row) {
                    val isRtl = isRtlText(cell)
                    sb.append("<w:tc><w:p><w:pPr>")
                    if (isRtl) sb.append("<w:bidi/>")
                    sb.append("</w:pPr><w:r><w:rPr>")
                    if (isRtl) sb.append("<w:rtl/>")
                    sb.append("</w:rPr><w:t xml:space=\"preserve\">")
                    sb.append(escapeXml(cell))
                    sb.append("</w:t></w:r></w:p></w:tc>")
                }
                sb.append("</w:tr>")
            }
            sb.append("</w:tbl>")
        }

        sb.append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/></w:sectPr>")
        sb.append("</w:body></w:document>")

        ZipOutputStream(FileOutputStream(outFile)).use { zos ->
            putZipString(
                zos,
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""
            )
            putZipString(
                zos,
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""
            )
            putZipString(zos, "word/document.xml", sb.toString())
        }

        return outFile
    }

    private fun isRtlText(text: String): Boolean {
        return Regex("""[\u0600-\u06FF\u0750-\u077F\u08A0-\u08FF\uFB50-\uFDFF\uFE70-\uFEFF]""").containsMatchIn(text)
    }

    private fun putZipString(zos: ZipOutputStream, entryName: String, content: String) {
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        zos.write(bytes, 0, bytes.size)
        zos.closeEntry()
    }
}
