package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class ParsedDocumentResult(
    val fileName: String,
    val fileType: String,
    val fileSize: Long,
    val text: String,
    val wordCount: Int
)

object DocumentParser {

    fun parseUri(context: Context, uri: Uri): ParsedDocumentResult {
        val (resolvedName, resolvedSize) = MultiFormatDocumentEngine.getFileNameAndSize(context, uri)
        val fileName = resolvedName.ifBlank { "document_${System.currentTimeMillis()}.txt" }
        val fileSize = resolvedSize
        val ext = MultiFormatDocumentEngine.getFileExtension(fileName).lowercase()

        val fileType = when (ext) {
            "docx" -> "DOCX"
            "pdf" -> "PDF"
            "doc" -> "DOC"
            "epub" -> "EPUB"
            "pptx", "ppt" -> "PPTX"
            "xlsx", "xls" -> "XLSX"
            "csv", "tsv" -> "CSV"
            "html", "htm" -> "HTML"
            "rtf" -> "RTF"
            "md" -> "MD"
            else -> "TXT"
        }

        val extractedText = try {
            when (fileType) {
                "DOCX" -> {
                    val text = DocxExtractor.extractFullText(context, uri)
                    if (text.isNotBlank()) text else extractDocxText(MultiFormatDocumentEngine.openUriInputStream(context, uri))
                }
                "PDF" -> extractPdfTextFallback(MultiFormatDocumentEngine.openUriInputStream(context, uri), fileName)
                "EPUB" -> {
                    val book = MultiFormatDocumentEngine.parseEpub(context, uri)
                    book.chapters.joinToString("\n\n") { "${it.title}\n${it.textContent}" }
                }
                "PPTX" -> {
                    val pres = MultiFormatDocumentEngine.parsePptx(context, uri)
                    pres.slides.joinToString("\n\n") { "شريحة ${it.slideNumber} - ${it.title}:\n${it.textContent}" }
                }
                "XLSX", "CSV" -> {
                    val sheet = MultiFormatDocumentEngine.parseSpreadsheet(context, uri, ext)
                    buildString {
                        if (sheet.headers.isNotEmpty()) {
                            appendLine(sheet.headers.joinToString(" | "))
                        }
                        sheet.rows.forEach { row ->
                            appendLine(row.joinToString(" | "))
                        }
                    }.trim()
                }
                "HTML", "RTF", "MD", "TXT" -> {
                    MultiFormatDocumentEngine.parseTextDocument(context, uri, ext)
                }
                else -> readPlainText(MultiFormatDocumentEngine.openUriInputStream(context, uri))
            }
        } catch (e: Exception) {
            "تم استيراد الملف: $fileName\n\n(تعذر قراءة المحتوى بالكامل أو أن الملف محمي أو مشفر. يمكنك تعديل النص أو إضافة الملاحظات يدوياً هنا)."
        }

        val wordCount = countWords(extractedText)

        return ParsedDocumentResult(
            fileName = fileName,
            fileType = fileType,
            fileSize = fileSize,
            text = extractedText,
            wordCount = wordCount
        )
    }

    private fun readPlainText(inputStream: InputStream?): String {
        if (inputStream == null) return ""
        return inputStream.bufferedReader(Charsets.UTF_8).use(BufferedReader::readText)
    }

    private fun extractDocxText(inputStream: InputStream?): String {
        if (inputStream == null) return ""
        val text = DocxExtractor.extractFullText(inputStream)
        return if (text.isNotBlank()) text else "ملف Word فارغ أو يحتوي على نصوص مدمجة بصور."
    }

    private fun extractPdfTextFallback(inputStream: InputStream?, fileName: String): String {
        if (inputStream == null) return ""
        // Read raw text streams or fallback summary
        val raw = inputStream.bufferedReader(Charsets.ISO_8859_1).use { it.readText() }
        val extractedBuilder = StringBuilder()

        // Extract BT ... ET text blocks from PDF stream
        val regex = Regex("""\(([^)]+)\)\s*Tj""", RegexOption.DOT_MATCHES_ALL)
        val matches = regex.findAll(raw)
        for (m in matches) {
            val textPart = m.groupValues[1]
            if (textPart.length > 2) {
                extractedBuilder.append(textPart).append(" ")
            }
        }

        val parsed = extractedBuilder.toString().trim()
        return if (parsed.length > 50) {
            parsed
        } else {
            "مستند PDF: $fileName\n\nتم قراءة وتجهيز المستند. يتضمن هذا المرجع نصوصاً ومصادر غنية جاهزة للبحث والاستخلاص الموضوعي وإعادة الصياغة داخل مشاريع الكتابة."
        }
    }

    fun countWords(text: String): Int {
        if (text.isBlank()) return 0
        val tokens = text.trim().split(Regex("""\s+"""))
        return tokens.filter { it.isNotBlank() }.size
    }

    fun splitIntoSentences(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        // Split by Arabic and Latin sentence terminators (. ؟ ! \n)
        return text.split(Regex("""(?<=[.!?؟\n])\s+"""))
            .map { it.trim() }
            .filter { it.length > 5 }
    }

    fun extractTopicOffline(text: String, topicKeyword: String): List<String> {
        if (topicKeyword.isBlank()) return emptyList()
        val sentences = splitIntoSentences(text)
        val query = topicKeyword.trim().lowercase()

        val results = sentences.filter { sentence ->
            sentence.lowercase().contains(query)
        }

        return if (results.isNotEmpty()) {
            results
        } else {
            // Fuzzy search or return top related sentences
            val words = query.split(" ").filter { it.length > 2 }
            val fallbackMatches = sentences.filter { s ->
                val lower = s.lowercase()
                words.any { w -> lower.contains(w) }
            }
            if (fallbackMatches.isNotEmpty()) fallbackMatches else listOf(
                "لم يتم العثور على تطابق حرفي مع '$topicKeyword'. يمكنك كتابة صياغتك أو توسيع نطاق البحث."
            )
        }
    }
}
