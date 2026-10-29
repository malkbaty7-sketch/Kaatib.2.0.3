package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppThemeData
import com.example.data.model.ReaderTheme
import com.example.util.DocumentFlashcardItem
import com.example.util.DocumentHighlightItem
import com.example.util.MultiFormatDocumentEngine
import com.example.util.PdfInkPoint
import com.example.util.PdfInkStroke
import com.example.util.PdfStampItem
import com.example.util.ScannedDocumentItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ============================================================================
// 1. PDF Freehand Ink Drawing, Stamps & Electronic Signature Overlay + Toolbar
// ============================================================================

@Composable
fun PdfFreehandInkAndStampOverlay(
    pageIndex: Int,
    isDrawingEnabled: Boolean,
    activeColorHex: String,
    activeStrokeWidth: Float,
    activeAlpha: Float,
    strokes: List<PdfInkStroke>,
    stamps: List<PdfStampItem>,
    onAddStroke: (PdfInkStroke) -> Unit,
    onRemoveStamp: (String) -> Unit
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val currentPoints = remember { mutableStateListOf<PdfInkPoint>() }

    val pageStrokes = remember(strokes, pageIndex) {
        strokes.filter { it.pageIndex == pageIndex }
    }
    val pageStamps = remember(stamps, pageIndex) {
        stamps.filter { it.pageIndex == pageIndex }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .then(
                if (isDrawingEnabled) {
                    Modifier.pointerInput(pageIndex, activeColorHex, activeStrokeWidth, activeAlpha) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                currentPoints.clear()
                                if (canvasSize.width > 0 && canvasSize.height > 0) {
                                    currentPoints.add(
                                        PdfInkPoint(
                                            xNorm = (startOffset.x / canvasSize.width).coerceIn(0f, 1f),
                                            yNorm = (startOffset.y / canvasSize.height).coerceIn(0f, 1f)
                                        )
                                    )
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                if (canvasSize.width > 0 && canvasSize.height > 0) {
                                    currentPoints.add(
                                        PdfInkPoint(
                                            xNorm = (change.position.x / canvasSize.width).coerceIn(0f, 1f),
                                            yNorm = (change.position.y / canvasSize.height).coerceIn(0f, 1f)
                                        )
                                    )
                                }
                            },
                            onDragEnd = {
                                if (currentPoints.size >= 2) {
                                    onAddStroke(
                                        PdfInkStroke(
                                            pageIndex = pageIndex,
                                            points = currentPoints.toList(),
                                            colorHex = activeColorHex,
                                            strokeWidth = activeStrokeWidth,
                                            alpha = activeAlpha
                                        )
                                    )
                                }
                                currentPoints.clear()
                            },
                            onDragCancel = {
                                currentPoints.clear()
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Draw saved strokes for this page
            for (stroke in pageStrokes) {
                if (stroke.points.size < 2) continue
                val strokeColor = parseHexColorWorkstation(stroke.colorHex).copy(alpha = stroke.alpha)
                val path = Path().apply {
                    val first = stroke.points.first()
                    moveTo(first.xNorm * w, first.yNorm * h)
                    for (i in 1 until stroke.points.size) {
                        val pt = stroke.points[i]
                        lineTo(pt.xNorm * w, pt.yNorm * h)
                    }
                }
                drawPath(
                    path = path,
                    color = strokeColor,
                    style = Stroke(
                        width = stroke.strokeWidth * 2.2f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // Draw active in-progress stroke
            if (currentPoints.size >= 2) {
                val liveColor = parseHexColorWorkstation(activeColorHex).copy(alpha = activeAlpha)
                val livePath = Path().apply {
                    val first = currentPoints.first()
                    moveTo(first.xNorm * w, first.yNorm * h)
                    for (i in 1 until currentPoints.size) {
                        val pt = currentPoints[i]
                        lineTo(pt.xNorm * w, pt.yNorm * h)
                    }
                }
                drawPath(
                    path = livePath,
                    color = liveColor,
                    style = Stroke(
                        width = activeStrokeWidth * 2.2f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }

        // Render Stamps & Electronic Signatures on this page
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            pageStamps.forEach { stamp ->
                val stampColor = parseHexColorWorkstation(stamp.colorHex)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = when {
                        stamp.yNorm < 0.35f -> Alignment.TopEnd
                        stamp.yNorm > 0.65f -> Alignment.BottomEnd
                        else -> Alignment.CenterEnd
                    }
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = stampColor.copy(alpha = 0.14f),
                        border = androidx.compose.foundation.BorderStroke(2.dp, stampColor),
                        modifier = Modifier.clickable {
                            if (isDrawingEnabled) {
                                onRemoveStamp(stamp.id)
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (stamp.isSignature) "✍️ ${stamp.label}" else "🔖 ${stamp.label}",
                                fontSize = if (stamp.isSignature) 13.sp else 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = stampColor
                            )
                            if (isDrawingEnabled) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "حذف الختم",
                                    tint = stampColor,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PdfDrawingToolbar(
    activeColorHex: String,
    activeStrokeWidth: Float,
    activeAlpha: Float,
    onColorChange: (String) -> Unit,
    onToolPresetChange: (strokeWidth: Float, alpha: Float) -> Unit,
    onUndoLastStroke: () -> Unit,
    onClearPageStrokes: () -> Unit,
    onOpenStampDialog: () -> Unit,
    onExportAnnotatedPdf: () -> Unit,
    onCloseDrawingMode: () -> Unit
) {
    val colors = listOf(
        "#EF4444" to "أحمر",
        "#2563EB" to "أزرق",
        "#16A34A" to "أخضر",
        "#F59E0B" to "أصفر",
        "#111827" to "أسود"
    )

    Surface(
        color = Color(0xFF0F172A).copy(alpha = 0.94f),
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                    Icon(Icons.Default.Brush, contentDescription = null, tint = Color(0xFFFDE047), modifier = Modifier.size(15.dp))
                    Text("وضع الرسم الحر والتوقيع على PDF", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Save Annotated PDF button
                    Surface(
                        onClick = onExportAnnotatedPdf,
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF059669)
                    ) {
                        Text(
                            text = "حفظ PDF بالرسومات 💾",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    IconButton(onClick = onCloseDrawingMode, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق الرسم", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Color circles
                colors.forEach { (hex, _) ->
                    val c = parseHexColorWorkstation(hex)
                    val isSelected = activeColorHex == hex
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 22.dp else 18.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(
                                width = if (isSelected) 2.dp else 0.5.dp,
                                color = if (isSelected) Color.White else Color.Gray,
                                shape = CircleShape
                            )
                            .clickable { onColorChange(hex) }
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Pen vs Marker presets
                val isMarker = activeAlpha < 0.6f
                Surface(
                    onClick = { onToolPresetChange(4f, 0.9f) },
                    shape = RoundedCornerShape(6.dp),
                    color = if (!isMarker && activeStrokeWidth <= 5f) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.12f)
                ) {
                    Text("✒️ قلم دقيق", fontSize = 9.sp, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                }

                Surface(
                    onClick = { onToolPresetChange(14f, 0.38f) },
                    shape = RoundedCornerShape(6.dp),
                    color = if (isMarker) Color(0xFFF59E0B) else Color.White.copy(alpha = 0.12f)
                ) {
                    Text("🖍️ ماركر تظليل", fontSize = 9.sp, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                }

                // Stamp & Signature
                Surface(
                    onClick = onOpenStampDialog,
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF8B5CF6)
                ) {
                    Text("✍️ ختم / توقيع", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                }

                // Undo
                IconButton(onClick = onUndoLastStroke, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Undo, contentDescription = "تراجع", tint = Color.White, modifier = Modifier.size(14.dp))
                }

                // Clear Page
                IconButton(onClick = onClearPageStrokes, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "مسح رسومات الصفحة", tint = Color(0xFFF87171), modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

@Composable
fun PdfStampAndSignatureDialog(
    pageIndex: Int,
    onAddStamp: (PdfStampItem) -> Unit,
    onDismiss: () -> Unit
) {
    var customSignatureText by remember { mutableStateOf("") }
    var selectedColorHex by remember { mutableStateOf("#1D4ED8") }
    var selectedPositionY by remember { mutableFloatStateOf(0.82f) } // 0.2f top, 0.5f center, 0.82f bottom

    val presetStamps = listOf(
        "معتمد ✅" to "#059669",
        "مهم جداً 🔥" to "#DC2626",
        "للمراجعة 🔍" to "#D97706",
        "مسودة أولية 📝" to "#4F46E5",
        "تم التدقيق ✔️" to "#0284C7",
        "مرجع أساسي ⭐" to "#9333EA"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF2563EB))
                Text("إضافة ختم أو توقيع إلكتروني ✍️", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("الأختام الجاهزة السريعة (تُضاف للصفحة ${pageIndex + 1}):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    presetStamps.chunked(2).forEach { pair ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            pair.forEach { (label, hex) ->
                                val c = parseHexColorWorkstation(hex)
                                Surface(
                                    onClick = {
                                        onAddStamp(
                                            PdfStampItem(
                                                pageIndex = pageIndex,
                                                label = label,
                                                yNorm = selectedPositionY,
                                                colorHex = hex,
                                                isSignature = false
                                            )
                                        )
                                        onDismiss()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = c.copy(alpha = 0.12f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, c),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = c,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                Text("أو أضف توقيعك الشخصي المعتمد:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = customSignatureText,
                    onValueChange = { customSignatureText = it },
                    label = { Text("اسم الموقع أو نص التوقيع (مثال: د. أحمد المنصور)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("موضع التوقيع:", fontSize = 10.sp, color = Color.Gray)
                    listOf(0.2f to "أعلى الصفحة", 0.5f to "الوسط", 0.82f to "أسفل الصفحة").forEach { (yVal, posLabel) ->
                        FilterChip(
                            selected = selectedPositionY == yVal,
                            onClick = { selectedPositionY = yVal },
                            label = { Text(posLabel, fontSize = 9.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sig = customSignatureText.trim().ifBlank { "توقيع معتمد" }
                    onAddStamp(
                        PdfStampItem(
                            pageIndex = pageIndex,
                            label = sig,
                            yNorm = selectedPositionY,
                            colorHex = selectedColorHex,
                            isSignature = true
                        )
                    )
                    onDismiss()
                }
            ) {
                Text("إدراج التوقيع ✍️", fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

// ============================================================================
// 2. Smart Research Assistant, Summarization & Instant Translator Sheet
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartResearchAndTranslationSheet(
    fileName: String,
    currentPositionLabel: String,
    activeReadableText: String,
    theme: AppThemeData,
    readerTheme: ReaderTheme,
    onInsertIntoEditor: (quoteText: String, note: String) -> Unit,
    onAddFlashcards: (List<DocumentFlashcardItem>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Summary & Concepts, 1 = Translator, 2 = Review Q&A

    val analysis = remember(activeReadableText, fileName) {
        MultiFormatDocumentEngine.analyzeTextForSmartResearch(activeReadableText, fileName)
    }

    var textToTranslate by remember(activeReadableText) {
        mutableStateOf(activeReadableText.take(400).ifBlank { "الفصل الأول: مقدمة حول الكتاب والذكاء الاصطناعي في النشر الذاتي." })
    }
    var targetLanguage by remember { mutableStateOf("EN") }
    val translatedOutput = remember(textToTranslate, targetLanguage) {
        MultiFormatDocumentEngine.translateDocumentSnippet(textToTranslate, targetLanguage)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = readerTheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = theme.primaryColor)
                    Column {
                        Text("المساعد البحثي الذكي والترجمة الفورية 🧠", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = readerTheme.text)
                        Text(
                            text = "$currentPositionLabel • ${analysis.wordCount} كلمة • وقت القراءة المقدر: ${analysis.estimatedReadingMinutes} د",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("📌 التلخيص والمفاهيم", fontSize = 10.sp) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("🌐 الترجمة الفورية", fontSize = 10.sp) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    label = { Text("❓ أسئلة بحثية", fontSize = 10.sp) },
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = readerTheme.text.copy(alpha = 0.1f))

            when (selectedTab) {
                0 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("أهم النقاط المستخلصة (Executive Summary):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.primaryColor)
                        analysis.bulletSummary.forEachIndexed { idx, point ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = readerTheme.bg,
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, theme.primaryColor.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = theme.primaryColor,
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("${idx + 1}", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Text(point, fontSize = 12.sp, color = readerTheme.text, lineHeight = 18.sp, modifier = Modifier.weight(1f))
                                }
                            }
                        }

                        Text("المفاهيم والمصطلحات المحورية:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.primaryColor)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(analysis.keyConcepts) { concept ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = theme.primaryColor.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "#$concept",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = theme.primaryColor,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val summaryBlock = analysis.bulletSummary.joinToString("\n• ", prefix = "• ")
                                onInsertIntoEditor(summaryBlock, "ملخص بحثي مستخرج من $fileName ($currentPositionLabel)")
                                Toast.makeText(context, "تم إرسال الملخص البحثي إلى شاشة الكتابة ✍️", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إدراج الملخص النقطي في مسودة الكتابة ✍️", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                1 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = textToTranslate,
                            onValueChange = { textToTranslate = it },
                            label = { Text("النص المراد ترجمته أو شرحه", fontSize = 11.sp) },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "EN" to "🇬🇧 الإنجليزية",
                                "AR" to "🇸🇦 العربية",
                                "FR" to "🇫🇷 الفرنسية",
                                "ES" to "🇪🇸 الإسبانية"
                            ).forEach { (code, label) ->
                                FilterChip(
                                    selected = targetLanguage == code,
                                    onClick = { targetLanguage = code },
                                    label = { Text(label, fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = readerTheme.bg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, theme.primaryColor.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = translatedOutput,
                                fontSize = 12.sp,
                                color = readerTheme.text,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }

                        Button(
                            onClick = {
                                onInsertIntoEditor(translatedOutput, "ترجمة مرجعية ($targetLanguage)")
                                Toast.makeText(context, "تم إرسال النص المترجم إلى شاشة الكتابة ✍️", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إدراج الترجمة في شاشة الكتابة ✍️", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                2 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("أسئلة استيعابية وبحثية مولدة من النص:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.primaryColor)
                        analysis.reviewQuestions.forEach { (q, a) ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = readerTheme.bg,
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("❓ $q", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.primaryColor)
                                    Text("💡 $a", fontSize = 11.sp, color = readerTheme.text)
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val generatedCards = analysis.reviewQuestions.map { (q, a) ->
                                    DocumentFlashcardItem(
                                        uriString = fileName,
                                        termOrQuestion = q,
                                        definitionOrAnswer = a,
                                        positionLabel = currentPositionLabel
                                    )
                                }
                                onAddFlashcards(generatedCards)
                                Toast.makeText(context, "تم تحويل الأسئلة إلى بطاقات مصطلحات واستذكار (Flashcards) 🗂️", Toast.LENGTH_LONG).show()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Style, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تحويل الأسئلة إلى بطاقات استذكار (Flashcards) 🗂️", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 3. Split-Screen Dual Document & Live Quick Scratchpad Pane
// ============================================================================

@Composable
fun SplitScreenReferenceAndScratchpadPane(
    primaryUriString: String,
    primaryFileName: String,
    currentPositionLabel: String,
    theme: AppThemeData,
    readerTheme: ReaderTheme,
    onSendDraftToEditor: (String) -> Unit,
    onCloseSplitScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var splitMode by remember { mutableIntStateOf(0) } // 0 = Live Scratchpad Draft, 1 = Second Document Comparison
    var scratchpadText by remember { mutableStateOf("") }

    // Second document comparison states
    val availableDocs = remember {
        MultiFormatDocumentEngine.scanStorageDocuments(context)
            .filter { it.uriString != primaryUriString }
    }
    var selectedSecondDoc by remember { mutableStateOf<ScannedDocumentItem?>(availableDocs.firstOrNull()) }
    var secondDocContent by remember { mutableStateOf("") }

    LaunchedEffect(selectedSecondDoc) {
        val doc = selectedSecondDoc ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(doc.uriString)
            secondDocContent = when (doc.extension.lowercase()) {
                "epub" -> {
                    val b = MultiFormatDocumentEngine.parseEpub(context, uri)
                    b.chapters.joinToString("\n\n") { "■ ${it.title}\n${it.textContent}" }
                }
                "pptx", "ppt" -> {
                    val p = MultiFormatDocumentEngine.parsePptx(context, uri)
                    p.slides.joinToString("\n\n") { "شريحة ${it.slideNumber}: ${it.title}\n${it.textContent}" }
                }
                "xlsx", "xls", "csv", "tsv" -> {
                    val s = MultiFormatDocumentEngine.parseSpreadsheet(context, uri, doc.extension)
                    buildString {
                        append(s.headers.joinToString(" | ")).append("\n")
                        s.rows.take(30).forEach { r -> append(r.joinToString(" | ")).append("\n") }
                    }
                }
                else -> MultiFormatDocumentEngine.parseTextDocument(context, uri, doc.extension)
            }
        }
    }

    Surface(
        color = readerTheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.primaryColor.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Split-Screen Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.primaryColor.copy(alpha = 0.1f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = { splitMode = 0 },
                        shape = RoundedCornerShape(6.dp),
                        color = if (splitMode == 0) theme.primaryColor else Color.Transparent
                    ) {
                        Text(
                            text = "✍️ مسودة الكتابة الفورية",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (splitMode == 0) Color.White else readerTheme.text,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Surface(
                        onClick = { splitMode = 1 },
                        shape = RoundedCornerShape(6.dp),
                        color = if (splitMode == 1) theme.primaryColor else Color.Transparent
                    ) {
                        Text(
                            text = "📑 مقارنة مستند ثانٍ (${availableDocs.size})",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (splitMode == 1) Color.White else readerTheme.text,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                IconButton(onClick = onCloseSplitScreen, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق التقسيم", tint = readerTheme.text, modifier = Modifier.size(14.dp))
                }
            }

            if (splitMode == 0) {
                // Mode 0: Quick Scratchpad Overlay connected to Active Editor Chapter
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = scratchpadText,
                        onValueChange = { scratchpadText = it },
                        placeholder = {
                            Text(
                                "اكتب مسودتك أو تلخيصك أو أفكارك هنا مباشرة أثناء قراءة ($primaryFileName)... وسيتم إرسالها لفصلك المفتوح في المحرر بنقرة واحدة.",
                                fontSize = 11.sp
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${scratchpadText.split(Regex("\\s+")).filter { it.isNotBlank() }.size} كلمة",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { scratchpadText = "" },
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("مسح", fontSize = 10.sp)
                            }
                            Button(
                                onClick = {
                                    if (scratchpadText.isNotBlank()) {
                                        onSendDraftToEditor(scratchpadText.trim())
                                        Toast.makeText(context, "تم إلحاق المسودة بالفصل الحالي في شاشة الكتابة ✍️", Toast.LENGTH_SHORT).show()
                                        scratchpadText = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("إرسال للمحرر ✍️", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                // Mode 1: Dual Document Comparison View
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(availableDocs, key = { it.uriString }) { doc ->
                            val isSelected = selectedSecondDoc?.uriString == doc.uriString
                            Surface(
                                onClick = { selectedSecondDoc = doc },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) theme.primaryColor else readerTheme.bg,
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, theme.primaryColor.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "📄 ${doc.name}",
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else readerTheme.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(readerTheme.bg)
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp)
                    ) {
                        Text(
                            text = secondDocContent.ifBlank { "اختر مستنداً ثانياً من الشريط أعلاه لمقارنته جنباً إلى جنب مع المستند الحالي." },
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = readerTheme.text
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 4. Academic Citation Generator & Flashcards / Glossary Builder Sheet
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardsAndCitationGeneratorSheet(
    uriString: String,
    fileName: String,
    currentPositionLabel: String,
    highlights: List<DocumentHighlightItem>,
    flashcards: List<DocumentFlashcardItem>,
    theme: AppThemeData,
    readerTheme: ReaderTheme,
    onUpdateFlashcards: (List<DocumentFlashcardItem>) -> Unit,
    onInsertCitationIntoEditor: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Flashcards & Glossary, 1 = Academic Citation Generator

    // Flashcard creation states
    var newTermInput by remember { mutableStateOf("") }
    var newDefInput by remember { mutableStateOf("") }
    val flippedCardIds = remember { mutableStateListOf<String>() }

    // Citation states
    var authorInput by remember { mutableStateOf("د. مؤلف المرجع") }
    var selectedCitationStyle by remember { mutableStateOf("APA") }

    val formattedCitation = remember(fileName, authorInput, currentPositionLabel, selectedCitationStyle) {
        MultiFormatDocumentEngine.generateAcademicCitation(
            fileName = fileName,
            authorName = authorInput.ifBlank { "مؤلف المرجع" },
            positionLabel = currentPositionLabel,
            style = selectedCitationStyle
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = readerTheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("بطاقات المصطلحات والتوثيق العلمي 🎓", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = readerTheme.text)
                Text("${flashcards.size} بطاقة", fontSize = 11.sp, color = theme.primaryColor, fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("🗂️ بطاقات المصطلحات (${flashcards.size})", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("🎓 مولّد التوثيق الأكاديمي", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = readerTheme.text.copy(alpha = 0.1f))

            if (selectedTab == 0) {
                // Tab 0: Flashcards & Glossary Builder
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newTermInput,
                        onValueChange = { newTermInput = it },
                        placeholder = { Text("المصطلح أو السؤال...", fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = newDefInput,
                        onValueChange = { newDefInput = it },
                        placeholder = { Text("التعريف أو الجواب...", fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    IconButton(
                        onClick = {
                            if (newTermInput.isNotBlank() && newDefInput.isNotBlank()) {
                                val card = DocumentFlashcardItem(
                                    uriString = uriString,
                                    termOrQuestion = newTermInput.trim(),
                                    definitionOrAnswer = newDefInput.trim(),
                                    positionLabel = currentPositionLabel
                                )
                                onUpdateFlashcards(flashcards + card)
                                newTermInput = ""
                                newDefInput = ""
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.primaryColor)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "إضافة بطاقة", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                if (highlights.isNotEmpty() && flashcards.isEmpty()) {
                    OutlinedButton(
                        onClick = {
                            val fromHighlights = highlights.map { hl ->
                                DocumentFlashcardItem(
                                    uriString = uriString,
                                    termOrQuestion = hl.note.ifBlank { "اقتباس من ${hl.positionLabel}" },
                                    definitionOrAnswer = hl.snippetText,
                                    positionLabel = hl.positionLabel
                                )
                            }
                            onUpdateFlashcards(flashcards + fromHighlights)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("✨ تحويل جميع تظليلاتي (${highlights.size}) إلى بطاقات مراجعة تلقائياً", fontSize = 11.sp)
                    }
                }

                if (flashcards.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "لا توجد بطاقات مصطلحات بعد. أضف مصطلحاً وتعريفه بالأعلى أو ولّد البطاقات تلقائياً من المساعد البحثي.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(flashcards, key = { it.id }) { card ->
                            val isFlipped = flippedCardIds.contains(card.id)
                            Surface(
                                onClick = {
                                    if (isFlipped) flippedCardIds.remove(card.id) else flippedCardIds.add(card.id)
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isFlipped) Color(0xFFFEF3C7) else readerTheme.bg,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (card.isMastered) Color(0xFF059669) else theme.primaryColor.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.fillMaxWidth()
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
                                        Text(
                                            text = if (isFlipped) "💡 الإجابة / التعريف (${card.positionLabel})" else "❓ المصطلح / السؤال (انقر لقلب البطاقة 🔄)",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isFlipped) Color(0xFF92400E) else theme.primaryColor
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                onClick = {
                                                    val updated = flashcards.map {
                                                        if (it.id == card.id) it.copy(isMastered = !it.isMastered) else it
                                                    }
                                                    onUpdateFlashcards(updated)
                                                },
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (card.isMastered) Color(0xFF059669) else Color.Gray.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = if (card.isMastered) "متقن ✅" else "قيد الحفظ",
                                                    fontSize = 9.sp,
                                                    color = if (card.isMastered) Color.White else Color.Gray,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    onUpdateFlashcards(flashcards.filter { it.id != card.id })
                                                },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = Color(0xFFEF4444), modifier = Modifier.size(13.dp))
                                            }
                                        }
                                    }

                                    Text(
                                        text = if (isFlipped) card.definitionOrAnswer else card.termOrQuestion,
                                        fontSize = 13.sp,
                                        fontWeight = if (isFlipped) FontWeight.Normal else FontWeight.Bold,
                                        color = if (isFlipped) Color(0xFF78350F) else readerTheme.text
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Tab 1: Academic Citation Generator (APA / MLA / Chicago / Harvard / Classic Arabic)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = authorInput,
                        onValueChange = { authorInput = it },
                        label = { Text("اسم المؤلف أو الجهة المصدرة للمرجع", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "APA" to "APA (7th)",
                            "MLA" to "MLA (9th)",
                            "CHICAGO" to "Chicago",
                            "HARVARD" to "Harvard",
                            "ARABIC" to "التوثيق العربي"
                        ).forEach { (styleCode, label) ->
                            FilterChip(
                                selected = selectedCitationStyle == styleCode,
                                onClick = { selectedCitationStyle = styleCode },
                                label = { Text(label, fontSize = 10.sp) }
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = readerTheme.bg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, theme.primaryColor.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("صيغة التوثيق الجاهزة ($selectedCitationStyle):", fontSize = 10.sp, color = theme.primaryColor, fontWeight = FontWeight.Bold)
                            Text(formattedCitation, fontSize = 12.sp, color = readerTheme.text, fontWeight = FontWeight.Medium)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cb?.setPrimaryClip(ClipData.newPlainText("citation", formattedCitation))
                                Toast.makeText(context, "تم نسخ التوثيق الأكاديمي للحافظة 📋", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("نسخ التوثيق 📋", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                onInsertCitationIntoEditor(formattedCitation)
                                Toast.makeText(context, "تم إدراج التوثيق الأكاديمي في شاشة الكتابة ✍️", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إدراج بالمحرر ✍️", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 5. PDF Power Tools, Page Rotation/Deletion, Watermark, Merge & Live OCR Dialog
// ============================================================================

@Composable
fun PdfPowerToolsAndOcrDialog(
    uri: Uri,
    fileName: String,
    currentPageIndex: Int,
    totalPages: Int,
    currentRotation: Float,
    isCurrentPageDeleted: Boolean,
    extractedTextForOcr: String,
    strokes: List<PdfInkStroke>,
    stamps: List<PdfStampItem>,
    pageRotations: Map<Int, Float>,
    deletedPages: Set<Int>,
    theme: AppThemeData,
    onRotateCurrentPage: (Float) -> Unit,
    onToggleDeleteCurrentPage: () -> Unit,
    onSendOcrTextToEditor: (String) -> Unit,
    onExportFileReady: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedSection by remember { mutableIntStateOf(0) } // 0 = Page & Watermark Tools, 1 = Live OCR Scanner, 2 = Merge PDFs

    var watermarkText by remember { mutableStateOf("") }
    var includePageNumbers by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }

    // OCR editable text for current page
    val pageEstimatedOcrText = remember(extractedTextForOcr, currentPageIndex, totalPages) {
        if (extractedTextForOcr.isBlank()) {
            "نص مستخرج بالماسح الضوئي (OCR) من الصفحة ${currentPageIndex + 1} في مستند $fileName.\nيمكنك تعديل هذا النص أو نسخه أو إرساله مباشرة إلى شاشة الكتابة."
        } else {
            val lines = extractedTextForOcr.lines().filter { it.isNotBlank() }
            val perPage = (lines.size / totalPages.coerceAtLeast(1)).coerceAtLeast(6)
            val start = (currentPageIndex * perPage).coerceAtMost((lines.size - 1).coerceAtLeast(0))
            lines.drop(start).take(perPage.coerceAtLeast(8)).joinToString("\n").ifBlank { extractedTextForOcr.take(600) }
        }
    }
    var editableOcrText by remember(pageEstimatedOcrText) { mutableStateOf(pageEstimatedOcrText) }

    // Available PDFs on device for merging
    val otherPdfs = remember {
        MultiFormatDocumentEngine.scanStorageDocuments(context)
            .filter { it.extension.equals("pdf", ignoreCase = true) && it.uriString != uri.toString() }
    }
    var selectedSecondPdf by remember { mutableStateOf<ScannedDocumentItem?>(otherPdfs.firstOrNull()) }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.BuildCircle, contentDescription = null, tint = theme.primaryColor)
                Text("أدوات هندسة PDF المتقدمة و OCR 🛠️", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = selectedSection == 0,
                        onClick = { selectedSection = 0 },
                        label = { Text("🔄 الصفحات والعلامة", fontSize = 9.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedSection == 1,
                        onClick = { selectedSection = 1 },
                        label = { Text("🔍 ماسح OCR", fontSize = 9.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedSection == 2,
                        onClick = { selectedSection = 2 },
                        label = { Text("🔗 دمج PDF", fontSize = 9.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                when (selectedSection) {
                    0 -> {
                        Text(
                            text = "التحكم في الصفحة الحالية (${currentPageIndex + 1} من $totalPages):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onRotateCurrentPage((currentRotation + 90f) % 360f) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تدوير 90° (${currentRotation.toInt()}°)", fontSize = 10.sp)
                            }

                            OutlinedButton(
                                onClick = onToggleDeleteCurrentPage,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (isCurrentPageDeleted) Color(0xFF059669) else Color(0xFFDC2626)
                                )
                            ) {
                                Icon(
                                    if (isCurrentPageDeleted) Icons.Default.RestoreFromTrash else Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isCurrentPageDeleted) "استعادة الصفحة" else "حذف الصفحة", fontSize = 10.sp)
                            }
                        }

                        HorizontalDivider()

                        OutlinedTextField(
                            value = watermarkText,
                            onValueChange = { watermarkText = it },
                            label = { Text("نص العلامة المائية (Watermark) على الصفحات", fontSize = 11.sp) },
                            placeholder = { Text("مثال: نسخة خاصة - منصة كاتب", fontSize = 10.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Checkbox(checked = includePageNumbers, onCheckedChange = { includePageNumbers = it })
                            Text("إضافة ترقيم تلقائي أسفل الصفحات (1 / $totalPages)", fontSize = 11.sp)
                        }
                    }
                    1 -> {
                        Text(
                            text = "النص المستخرج بالماسح الضوئي (OCR) من الصفحة ${currentPageIndex + 1}:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.primaryColor
                        )
                        OutlinedTextField(
                            value = editableOcrText,
                            onValueChange = { editableOcrText = it },
                            maxLines = 7,
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Button(
                            onClick = {
                                onSendOcrTextToEditor(editableOcrText)
                                Toast.makeText(context, "تم إرسال نص OCR إلى شاشة الكتابة ✍️", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إرسال النص المستخرج (OCR) إلى شاشة الكتابة ✍️", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    2 -> {
                        Text(
                            text = "دمج المستند الحالي ($fileName) مع ملف PDF آخر:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (otherPdfs.isEmpty()) {
                            Text(
                                text = "لا توجد ملفات PDF أخرى في الحافظة حالياً. يمكنك استخراج صفحات أو حفظ نسخة معدلة وسوف تظهر هنا للدمج.",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(otherPdfs, key = { it.uriString }) { pdfItem ->
                                    val isSel = selectedSecondPdf?.uriString == pdfItem.uriString
                                    Surface(
                                        onClick = { selectedSecondPdf = pdfItem },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSel) theme.primaryColor.copy(alpha = 0.15f) else Color.Transparent,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSel) theme.primaryColor else Color.Gray.copy(alpha = 0.3f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                                            Text(pdfItem.name, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    val sec = selectedSecondPdf ?: return@Button
                                    isProcessing = true
                                    coroutineScope.launch {
                                        val merged = withContext(Dispatchers.IO) {
                                            MultiFormatDocumentEngine.mergePdfDocuments(
                                                context = context,
                                                firstUri = uri,
                                                secondUri = Uri.parse(sec.uriString),
                                                outputBaseName = fileName
                                            )
                                        }
                                        isProcessing = false
                                        if (merged != null) {
                                            onExportFileReady(merged)
                                            onDismiss()
                                        } else {
                                            Toast.makeText(context, "تعذر دمج ملفي PDF", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = !isProcessing && selectedSecondPdf != null,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("دمج الملفين وحفظ PDF موحد 🔗", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (isProcessing) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("جارٍ معالجة وبناء ملف الـ PDF الجديد...", fontSize = 11.sp, color = theme.primaryColor)
                    }
                }
            }
        },
        confirmButton = {
            if (selectedSection == 0) {
                Button(
                    onClick = {
                        isProcessing = true
                        coroutineScope.launch {
                            val outFile = withContext(Dispatchers.IO) {
                                MultiFormatDocumentEngine.exportAnnotatedAndModifiedPdf(
                                    context = context,
                                    sourceUri = uri,
                                    sourceFileName = fileName,
                                    strokes = strokes,
                                    stamps = stamps,
                                    pageRotations = pageRotations,
                                    deletedPages = deletedPages,
                                    watermarkText = watermarkText.trim(),
                                    addPageNumbers = includePageNumbers
                                )
                            }
                            isProcessing = false
                            if (outFile != null) {
                                onExportFileReady(outFile)
                                onDismiss()
                            } else {
                                Toast.makeText(context, "تعذر تصدير ملف PDF المعدل", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor)
                ) {
                    Text("تصدير PDF بالتعديلات والعلامة المائية 💾", fontSize = 11.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isProcessing) {
                Text("إغلاق")
            }
        }
    )
}

// ============================================================================
// 6. Reading Focus Ruler / Line Mask Overlay & Pomodoro + Ambient Sound Studio
// ============================================================================

@Composable
fun ReadingFocusRulerOverlay(
    rulerYFraction: Float,
    rulerHeightDp: Int = 76,
    primaryColor: Color,
    onUpdateYFraction: (Float) -> Unit,
    onCloseRuler: () -> Unit
) {
    var boxHeightPx by remember { mutableIntStateOf(1) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { boxHeightPx = it.height.coerceAtLeast(1) }
    ) {
        val centerPx = rulerYFraction.coerceIn(0.1f, 0.9f) * boxHeightPx

        // Dimmed mask canvas above and below the focus strip
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stripHeightPx = rulerHeightDp.dp.toPx()
            val topBottom = (centerPx - stripHeightPx / 2f).coerceAtLeast(0f)
            val bottomTop = (centerPx + stripHeightPx / 2f).coerceAtMost(size.height)

            // Top dim mask
            drawRect(
                color = Color.Black.copy(alpha = 0.52f),
                topLeft = Offset.Zero,
                size = Size(size.width, topBottom)
            )
            // Bottom dim mask
            drawRect(
                color = Color.Black.copy(alpha = 0.52f),
                topLeft = Offset(0f, bottomTop),
                size = Size(size.width, (size.height - bottomTop).coerceAtLeast(0f))
            )
            // Highlighted guide lines
            drawLine(
                color = primaryColor,
                start = Offset(0f, topBottom),
                end = Offset(size.width, topBottom),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = primaryColor,
                start = Offset(0f, bottomTop),
                end = Offset(size.width, bottomTop),
                strokeWidth = 2.dp.toPx()
            )
        }

        // Draggable Handle Pill on the right edge of the ruler
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(boxHeightPx) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaFrac = dragAmount.y / boxHeightPx.toFloat()
                        onUpdateYFraction((rulerYFraction + deltaFrac).coerceIn(0.12f, 0.88f))
                    }
                }
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = primaryColor,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 8.dp, top = 8.dp)
                    .clickable { onCloseRuler() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Text("اسحب مسطرة التركيز أو انقر لإغلاقها ✕", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun FocusPomodoroAndAmbientDialog(
    isPomodoroRunning: Boolean,
    pomodoroRemainingSeconds: Int,
    activeAmbientSoundMode: String,
    showFocusRuler: Boolean,
    readingSpeedWpm: Int,
    totalWordCount: Int,
    theme: AppThemeData,
    onTogglePomodoro: () -> Unit,
    onResetPomodoro: (minutes: Int) -> Unit,
    onSelectAmbientSound: (String) -> Unit,
    onToggleFocusRuler: (Boolean) -> Unit,
    onUpdateWpm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val mins = pomodoroRemainingSeconds / 60
    val secs = pomodoroRemainingSeconds % 60
    val formattedTimer = String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
    val estimatedMinutes = (totalWordCount / readingSpeedWpm.coerceAtLeast(60)).coerceAtLeast(1)

    val ambientModes = listOf(
        "OFF" to "🔇 بدون صوت",
        "RAIN" to "🌧️ مطر دافئ",
        "BROWN" to "🌊 ضوضاء بنية عميقة",
        "BINAURAL" to "🎧 تردد تركيز هادئ",
        "WHITE" to "🍃 عزل ضجيج أبيض"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.SelfImprovement, contentDescription = null, tint = theme.primaryColor)
                Text("بيئة التركيز، بومودورو، ومسطرة القراءة 🎯", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 1. Pomodoro Focus Timer Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = theme.primaryColor.copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, theme.primaryColor.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("مؤقت جلسة القراءة والبحث (Pomodoro)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.primaryColor)
                        Text(formattedTimer, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = theme.primaryColor)

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = onTogglePomodoro,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isPomodoroRunning) Color(0xFFD97706) else Color(0xFF059669)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(if (isPomodoroRunning) "إيقاف مؤقت ⏸️" else "بدء الجلسة ▶️", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { onResetPomodoro(25) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("25 د", fontSize = 10.sp)
                            }
                            OutlinedButton(
                                onClick = { onResetPomodoro(15) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("15 د", fontSize = 10.sp)
                            }
                            OutlinedButton(
                                onClick = { onResetPomodoro(45) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("45 د", fontSize = 10.sp)
                            }
                        }
                    }
                }

                // 2. Reading Focus Ruler / Line Mask Toggle
                Surface(
                    onClick = { onToggleFocusRuler(!showFocusRuler) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (showFocusRuler) Color(0xFF059669).copy(alpha = 0.12f) else Color.Gray.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (showFocusRuler) Color(0xFF059669) else Color.Gray.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("📏 مسطرة التركيز البصري (Line Focus Mask)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("إضاءة السطر الحالي وتعتيم باقي الصفحة لراحة العين", fontSize = 10.sp, color = Color.Gray)
                        }
                        Switch(checked = showFocusRuler, onCheckedChange = { onToggleFocusRuler(it) })
                    }
                }

                // 3. Synthesized Ambient Focus Sounds
                Text("أصوات التركيز الخلفية لعزل الضجيج (تعمل بدون إنترنت):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ambientModes) { (modeKey, modeLabel) ->
                        FilterChip(
                            selected = activeAmbientSoundMode == modeKey,
                            onClick = { onSelectAmbientSound(modeKey) },
                            label = { Text(modeLabel, fontSize = 10.sp) }
                        )
                    }
                }

                // 4. Reading Speed WPM & Remaining Time
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("سرعة قراءتك: $readingSpeedWpm كلمة/دقيقة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("الوقت المقدر للإنهاء: ~$estimatedMinutes دقيقة", fontSize = 11.sp, color = theme.primaryColor, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = readingSpeedWpm.toFloat(),
                    onValueChange = { onUpdateWpm(it.toInt()) },
                    valueRange = 100f..350f,
                    modifier = Modifier.fillMaxWidth().height(24.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor)) {
                Text("تم")
            }
        }
    )
}

private fun parseHexColorWorkstation(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        Color(0xFFEF4444)
    }
}
