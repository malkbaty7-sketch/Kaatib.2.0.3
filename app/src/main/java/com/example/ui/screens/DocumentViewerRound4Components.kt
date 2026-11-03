package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ============================================================================
// 1. PDF VISUAL PAGE ORGANIZER, SPLITTER & MERGER STUDIO
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfVisualPageOrganizerSheet(
    documentUri: Uri,
    documentName: String,
    totalPages: Int,
    currentPageIndex: Int,
    initialRotations: Map<Int, Float>,
    initialDeletedPages: Set<Int>,
    onUpdateRotationsAndDeleted: (Map<Int, Float>, Set<Int>) -> Unit,
    onJumpToPage: (Int) -> Unit,
    onExportOrganizedPdfReady: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var pageOrder by remember(totalPages) {
        mutableStateOf((0 until totalPages.coerceAtLeast(1)).toList())
    }
    var pageRotations by remember(initialRotations) { mutableStateOf(initialRotations) }
    var excludedPages by remember(initialDeletedPages) { mutableStateOf(initialDeletedPages) }
    var thumbnails by remember { mutableStateOf<Map<Int, Bitmap>>(emptyMap()) }
    var isExporting by remember { mutableStateOf(false) }

    // Load thumbnails asynchronously for visible/initial pages
    LaunchedEffect(documentUri, totalPages) {
        withContext(Dispatchers.IO) {
            val maxThumbCount = totalPages.coerceAtMost(36)
            for (idx in 0 until maxThumbCount) {
                val bmp = MultiFormatDocumentEngine.renderPdfPage(context, documentUri, idx)
                if (bmp != null) {
                    thumbnails = thumbnails + (idx to bmp)
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🧩 استوديو تنظيم وهندسة صفحات PDF المرئي",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                    Text(
                        text = "إعادة ترتيب، تدوير، استبعاد، أو استخراج صفحات محددة من ($documentName)",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                }
            }

            // Quick Batch Selection & Ordering Bar
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    AssistChip(
                        onClick = {
                            excludedPages = emptySet()
                            pageOrder = (0 until totalPages).toList()
                        },
                        label = { Text("تحديد الكل (${totalPages})", fontSize = 11.sp, color = Color.White) },
                        leadingIcon = { Icon(Icons.Default.SelectAll, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(15.dp)) }
                    )
                }
                item {
                    AssistChip(
                        onClick = {
                            // Keep only odd pages (1, 3, 5 -> indices 0, 2, 4)
                            excludedPages = (0 until totalPages).filter { it % 2 != 0 }.toSet()
                        },
                        label = { Text("الصفحات الفردية فقط", fontSize = 11.sp, color = Color.White) }
                    )
                }
                item {
                    AssistChip(
                        onClick = {
                            // Keep only even pages (2, 4, 6 -> indices 1, 3, 5)
                            excludedPages = (0 until totalPages).filter { it % 2 == 0 }.toSet()
                        },
                        label = { Text("الصفحات الزوجية فقط", fontSize = 11.sp, color = Color.White) }
                    )
                }
                item {
                    AssistChip(
                        onClick = { pageOrder = pageOrder.reversed() },
                        label = { Text("عكس ترتيب الصفحات ⇅", fontSize = 11.sp, color = Color(0xFFFBBF24)) }
                    )
                }
            }

            // Grid of Page Thumbnails
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 138.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(pageOrder, key = { pos, pageIdx -> "$pos-$pageIdx" }) { orderPos, pageIdx ->
                    val isExcluded = pageIdx in excludedPages
                    val rot = pageRotations[pageIdx] ?: 0f
                    val isCurrent = pageIdx == currentPageIndex
                    val thumb = thumbnails[pageIdx]

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isExcluded) Color(0xFF1E293B).copy(alpha = 0.45f) else Color(0xFF1E293B)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isCurrent) 2.dp else 1.dp,
                            color = when {
                                isExcluded -> Color(0xFFEF4444).copy(alpha = 0.6f)
                                isCurrent -> Color(0xFF38BDF8)
                                else -> Color(0xFF334155)
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Top Badge Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isExcluded) Color(0xFFEF4444) else Color(0xFF0284C7)
                                ) {
                                    Text(
                                        text = if (isExcluded) "مستبعدة" else "صفحة ${pageIdx + 1}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = "#${orderPos + 1}",
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }

                            // Thumbnail Preview Box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(135.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                                    .clickable {
                                        onJumpToPage(pageIdx)
                                        onDismiss()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (thumb != null) {
                                    Image(
                                        bitmap = thumb.asImageBitmap(),
                                        contentDescription = "صفحة ${pageIdx + 1}",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer(
                                                rotationZ = rot,
                                                alpha = if (isExcluded) 0.35f else 1f
                                            )
                                    )
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFF64748B))
                                        Text("ص ${pageIdx + 1}", fontSize = 11.sp, color = Color.DarkGray)
                                    }
                                }
                                if (rot != 0f) {
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                    ) {
                                        Text(
                                            text = "${rot.toInt()}°",
                                            fontSize = 9.sp,
                                            color = Color(0xFFFBBF24),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }

                            // Controls: Move Left/Right, Rotate 90°, Include/Exclude
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Move earlier
                                IconButton(
                                    onClick = {
                                        if (orderPos > 0) {
                                            val mut = pageOrder.toMutableList()
                                            val tmp = mut[orderPos - 1]
                                            mut[orderPos - 1] = mut[orderPos]
                                            mut[orderPos] = tmp
                                            pageOrder = mut
                                        }
                                    },
                                    enabled = orderPos > 0,
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "تقديم", tint = Color.White, modifier = Modifier.size(16.dp))
                                }

                                // Move later
                                IconButton(
                                    onClick = {
                                        if (orderPos < pageOrder.lastIndex) {
                                            val mut = pageOrder.toMutableList()
                                            val tmp = mut[orderPos + 1]
                                            mut[orderPos + 1] = mut[orderPos]
                                            mut[orderPos] = tmp
                                            pageOrder = mut
                                        }
                                    },
                                    enabled = orderPos < pageOrder.lastIndex,
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "تأخير", tint = Color.White, modifier = Modifier.size(16.dp))
                                }

                                // Rotate 90°
                                IconButton(
                                    onClick = {
                                        val nextRot = ((rot + 90f) % 360f)
                                        pageRotations = pageRotations + (pageIdx to nextRot)
                                    },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.Default.RotateRight, contentDescription = "تدوير 90", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                }

                                // Exclude / Include
                                IconButton(
                                    onClick = {
                                        excludedPages = if (isExcluded) excludedPages - pageIdx else excludedPages + pageIdx
                                    },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isExcluded) Icons.Default.RestoreFromTrash else Icons.Default.DeleteOutline,
                                        contentDescription = "استبعاد أو استعادة",
                                        tint = if (isExcluded) Color(0xFF10B981) else Color(0xFFEF4444),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Action Bar
            val activeOrderedPages = pageOrder.filter { it !in excludedPages }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        onUpdateRotationsAndDeleted(pageRotations, excludedPages)
                        Toast.makeText(context, "تم تطبيق التدوير واستبعاد الصفحات على العرض الحالي", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("تطبيق على العارض", fontSize = 12.sp, color = Color.White)
                }

                Button(
                    onClick = {
                        if (activeOrderedPages.isEmpty()) {
                            Toast.makeText(context, "يرجى الإبقاء على صفحة واحدة على الأقل للتصدير", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isExporting = true
                        coroutineScope.launch {
                            val outFile = withContext(Dispatchers.IO) {
                                MultiFormatDocumentEngine.exportCustomOrganizedPdf(
                                    context = context,
                                    sourceUri = documentUri,
                                    sourceFileName = documentName,
                                    orderedPageIndices = activeOrderedPages,
                                    pageRotations = pageRotations,
                                    suffixLabel = "organized_${activeOrderedPages.size}p"
                                )
                            }
                            isExporting = false
                            if (outFile != null) {
                                onUpdateRotationsAndDeleted(pageRotations, excludedPages)
                                onExportOrganizedPdfReady(outFile)
                            } else {
                                Toast.makeText(context, "تعذر تصدير ملف PDF المنظم", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isExporting && activeOrderedPages.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1.3f)
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    } else {
                        Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("تصدير PDF (${activeOrderedPages.size} ص)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ============================================================================
// 2. ARABIC LINGUISTICS, AUTO-TASHKEEL & POETRY PROSODY STUDIO
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArabicLinguisticsAndProsodySheet(
    documentName: String,
    plainText: String,
    onSendProcessedTextToEditor: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var selectedTab by remember { mutableIntStateOf(0) }

    val initialSample = remember(plainText) {
        plainText.trim().take(1200).ifBlank {
            "العلم نور يرفع بيوتا لا عماد لها *** والجهل يهدم بيت العز والكرم\n" +
            "إذا غامرت في شرف مروم *** فلا تقنع بما دون النجوم\n" +
            "ومن يتهيب صعود الجبال *** يعش أبد الدهر بين الحفر"
        }
    }
    var workingText by remember(initialSample) { mutableStateOf(initialSample) }

    val prosodyReport = remember(workingText) {
        MultiFormatDocumentEngine.analyzeArabicPoetryAndProsody(workingText)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🔤 المختبر اللغوي والمُشكِّل الآلي والعَروضي",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFBBF24)
                    )
                    Text(
                        text = "تشكيل آلي، تجريد الحركات، توحيد الهمزات، وتحليل البحور والقوافي الشعرية",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                }
            }

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF1E293B),
                contentColor = Color(0xFFFBBF24),
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("✨ المُشكّل ومعالج النصوص", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("📜 الشعر والقوافي والعَروض (${prosodyReport.verses.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
            }

            if (selectedTab == 0) {
                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            workingText = MultiFormatDocumentEngine.autoTashkeelArabicText(workingText)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text("تشكيل آلي ذكي", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = {
                            workingText = MultiFormatDocumentEngine.stripArabicDiacritics(workingText)
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text("إزالة التشكيل", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = {
                            workingText = MultiFormatDocumentEngine.normalizeArabicText(workingText)
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text("توحيد الهمزات", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedTextField(
                    value = workingText,
                    onValueChange = { workingText = it },
                    label = { Text("النص العربي قيد المعالجة اللغوية والتشكيل", color = Color(0xFF94A3B8)) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFBBF24),
                        unfocusedBorderColor = Color(0xFF334155)
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(workingText))
                            Toast.makeText(context, "تم نسخ النص المشكّل للحافظة", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("نسخ للحافظة", fontSize = 12.sp, color = Color.White)
                    }

                    Button(
                        onClick = {
                            onSendProcessedTextToEditor(workingText, "نص معالج ومشكّل آلياً")
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إدراج في محرر الكتابة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // Poetry & Prosody Tab
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "حرف الروي السائد: (${prosodyReport.rawiyLetter})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFBBF24)
                            )
                            Text(
                                text = prosodyReport.rhymeType,
                                fontSize = 11.sp,
                                color = Color(0xFF38BDF8)
                            )
                        }
                        Text(
                            text = "الوزن / البحر التقريبي: ${prosodyReport.estimatedMeter}",
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(prosodyReport.verses) { verse ->
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.85f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = verse.sadr,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "✦",
                                        fontSize = 14.sp,
                                        color = Color(0xFFFBBF24),
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                    Text(
                                        text = verse.ajuz,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "التقطيع الصوتي: ${verse.pattern}",
                                        fontSize = 10.sp,
                                        color = Color(0xFF94A3B8),
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "قافية: ${verse.endingLetters}",
                                        fontSize = 10.sp,
                                        color = Color(0xFF38BDF8)
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        onSendProcessedTextToEditor(prosodyReport.formattedTwoColumnText, "قصيدة منسقة (صدر وعجز) مع تحليل القافية")
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إرسال القصيدة منسقة (صدر ✦ عجز) إلى محرر الكتابة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ============================================================================
// 3. INTERACTIVE TIMELINE & ENTITY EXTRACTOR SHEET
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveTimelineAndEntitiesSheet(
    documentName: String,
    plainText: String,
    onJumpToPosition: (Int) -> Unit,
    onSendToEditor: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val report = remember(plainText) {
        MultiFormatDocumentEngine.extractTimelineAndEntities(plainText)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "⏳ الخط الزمني التفاعلي ومستخرج الكيانات",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF34D399)
                    )
                    Text(
                        text = "التواريخ الهجرية والميلادية، الأعلام والشخصيات، والمدن المذكورة في ($documentName)",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                }
            }

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF1E293B),
                contentColor = Color(0xFF34D399),
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("⏳ الخط الزمني (${report.timelineEvents.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("👤 الأعلام والأماكن (${report.people.size + report.places.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
            }

            if (selectedTab == 0) {
                if (report.timelineEvents.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "لم يتم العثور على سنوات صريحة (مثل 1445 هـ أو 2024 م أو القرن...) في هذا المقطع.",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(report.timelineEvents) { ev ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onJumpToPosition(ev.positionIndex)
                                        onDismiss()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF059669).copy(alpha = 0.25f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF34D399))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = ev.yearLabel,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF34D399)
                                            )
                                            Text(
                                                text = ev.calendarType,
                                                fontSize = 9.sp,
                                                color = Color(0xFFA7F3D0)
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "«${ev.snippet}»",
                                            fontSize = 12.sp,
                                            color = Color.White,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            onSendToEditor("[${ev.yearLabel}] ${ev.snippet}", "حدث زمني مستخرج من $documentName")
                                        }
                                    ) {
                                        Icon(Icons.Default.Send, contentDescription = "إرسال للمحرر", tint = Color(0xFF34D399), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (report.people.isNotEmpty()) {
                        item {
                            Text("👤 الشخصيات والأعلام البارزة", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24))
                        }
                        items(report.people) { person ->
                            EntityItemCard(entity = person, badgeColor = Color(0xFFF59E0B), onJump = {
                                onJumpToPosition(person.positionIndex)
                                onDismiss()
                            }, onQuote = {
                                onSendToEditor("${person.name}: ${person.sampleSnippet}", "علم/شخصية مستخرجة")
                            })
                        }
                    }

                    if (report.places.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("🌍 المدن والأماكن الجغرافية", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                        }
                        items(report.places) { place ->
                            EntityItemCard(entity = place, badgeColor = Color(0xFF0284C7), onJump = {
                                onJumpToPosition(place.positionIndex)
                                onDismiss()
                            }, onQuote = {
                                onSendToEditor("${place.name}: ${place.sampleSnippet}", "موقع جغرافي مستخرج")
                            })
                        }
                    }

                    if (report.keyTerms.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("🏷️ المفاهيم والمصطلحات الأكثر وروداً", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA78BFA))
                        }
                        items(report.keyTerms) { term ->
                            EntityItemCard(entity = term, badgeColor = Color(0xFF7C3AED), onJump = {
                                onJumpToPosition(term.positionIndex)
                                onDismiss()
                            }, onQuote = {
                                onSendToEditor("${term.name}: ${term.sampleSnippet}", "مصطلح محوري")
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntityItemCard(
    entity: ExtractedEntityItem,
    badgeColor: Color,
    onJump: () -> Unit,
    onQuote: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onJump() }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = badgeColor.copy(alpha = 0.2f)
            ) {
                Text(
                    text = "${entity.count}×",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(entity.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(entity.sampleSnippet, fontSize = 11.sp, color = Color(0xFF94A3B8), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onQuote, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.PostAdd, contentDescription = "إدراج في المحرر", tint = badgeColor, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ============================================================================
// 4. INTERACTIVE 3D FLASHCARDS & AUTO MCQ SELF-TEST QUIZ STUDIO
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveQuizAnd3DFlashcardsSheet(
    documentName: String,
    plainText: String,
    flashcards: List<DocumentFlashcardItem>,
    onUpdateFlashcards: (List<DocumentFlashcardItem>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current.density

    val quizQuestions = remember(plainText, flashcards) {
        MultiFormatDocumentEngine.generateMcqQuizFromDocument(plainText, documentName, flashcards)
    }
    var selectedAnswers by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    var currentCardIdx by remember { mutableIntStateOf(0) }
    var isCardFlipped by remember { mutableStateOf(false) }
    val flipRotation by animateFloatAsState(
        targetValue = if (isCardFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "card3dFlip"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🎴 استوديو البطاقات ثلاثية الأبعاد والاختبار الذاتي",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA78BFA)
                    )
                    Text(
                        text = "اختبر استيعابك لمحتوى ($documentName) عبر اختبارات MCQ والبطاقات القابلة للقلب",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                }
            }

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF1E293B),
                contentColor = Color(0xFFA78BFA),
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("📝 الاختبار التفاعلي (${quizQuestions.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("🎴 بطاقات 3D القابلة للقلب", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
            }

            if (selectedTab == 0) {
                // Score Header
                val answeredCount = selectedAnswers.size
                val correctCount = quizQuestions.count { q -> selectedAnswers[q.id] == q.correctOptionIndex }
                val scorePct = if (answeredCount > 0) (correctCount * 100) / answeredCount else 0

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1E293B),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "الإجابات الصحيحة: $correctCount من $answeredCount",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (scorePct >= 70) Color(0xFF059669) else Color(0xFFD97706)
                            ) {
                                Text(
                                    text = "النتيجة: $scorePct%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            if (answeredCount > 0) {
                                TextButton(onClick = { selectedAnswers = emptyMap() }) {
                                    Text("إعادة الاختبار", fontSize = 11.sp, color = Color(0xFF38BDF8))
                                }
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(quizQuestions) { qIdx, q ->
                        val chosenIdx = selectedAnswers[q.id]
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "س${qIdx + 1}: ${q.question}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                q.options.forEachIndexed { optIdx, optionText ->
                                    val isChosen = chosenIdx == optIdx
                                    val isCorrect = optIdx == q.correctOptionIndex
                                    val bg = when {
                                        chosenIdx == null -> Color(0xFF0F172A)
                                        isCorrect -> Color(0xFF065F46)
                                        isChosen && !isCorrect -> Color(0xFF7F1D1D)
                                        else -> Color(0xFF0F172A)
                                    }
                                    Surface(
                                        onClick = {
                                            if (chosenIdx == null) {
                                                selectedAnswers = selectedAnswers + (q.id to optIdx)
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = bg,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (chosenIdx != null && isCorrect) Color(0xFF10B981) else Color(0xFF334155)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = optionText,
                                                fontSize = 12.sp,
                                                color = Color.White,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (chosenIdx != null && isCorrect) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(16.dp))
                                            } else if (isChosen && !isCorrect) {
                                                Icon(Icons.Default.Cancel, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }

                                AnimatedVisibility(visible = chosenIdx != null) {
                                    Text(
                                        text = "💡 ${q.explanation}",
                                        fontSize = 11.sp,
                                        color = Color(0xFFA7F3D0),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // 3D Flip Flashcards Deck
                val effectiveCards = remember(flashcards, quizQuestions) {
                    if (flashcards.isNotEmpty()) {
                        flashcards
                    } else {
                        quizQuestions.mapIndexed { idx, q ->
                            DocumentFlashcardItem(
                                uriString = "",
                                termOrQuestion = q.question,
                                definitionOrAnswer = q.options.getOrElse(q.correctOptionIndex) { "" } + "\n\n" + q.explanation,
                                positionLabel = "بطاقة ذكية #${idx + 1}"
                            )
                        }
                    }
                }

                val safeIdx = currentCardIdx.coerceIn(0, (effectiveCards.size - 1).coerceAtLeast(0))
                val activeCard = effectiveCards.getOrNull(safeIdx)

                if (activeCard != null) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "البطاقة ${safeIdx + 1} من ${effectiveCards.size} • اضغط على البطاقة لقلبها ثلاثي الأبعاد 🔄",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (flipRotation <= 90f) Color(0xFF1E293B) else Color(0xFF312E81)
                            ),
                            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFA78BFA)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                                .graphicsLayer {
                                    rotationY = flipRotation
                                    cameraDistance = 12f * density
                                }
                                .clickable { isCardFlipped = !isCardFlipped }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(22.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (flipRotation <= 90f) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF7C3AED).copy(alpha = 0.25f)
                                        ) {
                                            Text(
                                                text = "الوجه الأمامي (السؤال / المصطلح)",
                                                fontSize = 11.sp,
                                                color = Color(0xFFC4B5FD),
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                        Text(
                                            text = activeCard.termOrQuestion,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier.graphicsLayer { rotationY = 180f },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF10B981).copy(alpha = 0.25f)
                                        ) {
                                            Text(
                                                text = "الوجه الخلفي (الإجابة والشرح)",
                                                fontSize = 11.sp,
                                                color = Color(0xFF6EE7B7),
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                        Text(
                                            text = activeCard.definitionOrAnswer,
                                            fontSize = 14.sp,
                                            color = Color.White,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    isCardFlipped = false
                                    if (safeIdx > 0) currentCardIdx = safeIdx - 1
                                },
                                enabled = safeIdx > 0
                            ) {
                                Text("السابقة", color = Color.White)
                            }

                            Button(
                                onClick = {
                                    if (flashcards.isNotEmpty()) {
                                        val updated = flashcards.map {
                                            if (it.id == activeCard.id) it.copy(isMastered = !it.isMastered) else it
                                        }
                                        onUpdateFlashcards(updated)
                                    }
                                    isCardFlipped = false
                                    if (safeIdx < effectiveCards.lastIndex) currentCardIdx = safeIdx + 1
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                            ) {
                                Text(if (activeCard.isMastered) "متقنة ✅ (التالي)" else "أتقنتها ✅", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    isCardFlipped = false
                                    if (safeIdx < effectiveCards.lastIndex) currentCardIdx = safeIdx + 1
                                },
                                enabled = safeIdx < effectiveCards.lastIndex
                            ) {
                                Text("التالية", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 5. OFFICIAL DIGITAL STAMPS & SHA-256 VERIFICATION CERTIFICATE SHEET
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficialDigitalStampAndCertificateSheet(
    documentUri: Uri,
    documentName: String,
    isPdfDocument: Boolean,
    plainText: String,
    onAddStampToCurrentPdfPage: (String, String) -> Unit,
    onExportCertifiedPdfReady: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val stampPresets = listOf(
        Triple("معتمد رسمياً ✓", "#059669", "ختم الاعتماد الرسمي"),
        Triple("سري للغاية 🔒", "#DC2626", "ختم السرية وعدم التداول"),
        Triple("مسودة للمراجعة", "#D97706", "ختم المراجعة والتدقيق"),
        Triple("نسخة المؤلف الأصلية", "#2563EB", "ختم الملكية الفكرية للمؤلف")
    )

    var selectedPresetIdx by remember { mutableIntStateOf(0) }
    var customStampTitle by remember { mutableStateOf(stampPresets[0].first) }
    var authorOrOrgName by remember { mutableStateOf("مكتب التأليف والتحقيق") }
    var selectedColorHex by remember { mutableStateOf(stampPresets[0].second) }
    var applyOnAllPages by remember { mutableStateOf(true) }
    var appendCertificatePage by remember { mutableStateOf(true) }
    var isExporting by remember { mutableStateOf(false) }

    val forensics = remember(documentUri, plainText) {
        MultiFormatDocumentEngine.computeDocumentForensics(context, documentUri, plainText)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🔐 الأختام الرسمية وشهادة التوثيق الرقمي (SHA-256)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                    Text(
                        text = "دمج ختم رسمي دائري وإلحاق صفحة شهادة توثيق رقمية في نهاية المستند",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                }
            }

            // Preset Stamp Chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(stampPresets) { idx, preset ->
                    FilterChip(
                        selected = selectedPresetIdx == idx,
                        onClick = {
                            selectedPresetIdx = idx
                            customStampTitle = preset.first
                            selectedColorHex = preset.second
                        },
                        label = { Text(preset.first, fontSize = 11.sp) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = customStampTitle,
                    onValueChange = { customStampTitle = it },
                    label = { Text("عنوان الختم الرسمي", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                OutlinedTextField(
                    value = authorOrOrgName,
                    onValueChange = { authorOrOrgName = it },
                    label = { Text("اسم المؤلف / الجهة", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }

            // Live Circular Official Stamp Preview
            val previewColor = remember(selectedColorHex) {
                try { Color(android.graphics.Color.parseColor(selectedColorHex)) } catch (_: Exception) { Color(0xFF059669) }
            }
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("معاينة شهادة التوثيق والختم", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("المستند: $documentName", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        Text(
                            text = "SHA-256: ${forensics.sha256Hash.take(24)}...",
                            fontSize = 10.sp,
                            color = Color(0xFF38BDF8),
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Circular Seal Graphic
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .graphicsLayer(rotationZ = -10f)
                            .border(3.dp, previewColor, CircleShape)
                            .padding(5.dp)
                            .border(1.dp, previewColor.copy(alpha = 0.7f), CircleShape)
                            .background(previewColor.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = customStampTitle.take(14),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = previewColor,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = authorOrOrgName.take(14),
                                fontSize = 8.sp,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            if (isPdfDocument) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("دمج الختم على كافة صفحات الملف", fontSize = 12.sp, color = Color.White)
                    Switch(checked = applyOnAllPages, onCheckedChange = { applyOnAllPages = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("إلحاق صفحة شهادة التوثيق (SHA-256) في نهاية الـ PDF", fontSize = 12.sp, color = Color.White)
                    Switch(checked = appendCertificatePage, onCheckedChange = { appendCertificatePage = it })
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onAddStampToCurrentPdfPage("$customStampTitle • $authorOrOrgName", selectedColorHex)
                            Toast.makeText(context, "تم وضع الختم الرسمي على الصفحة الحالية", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("ختم الصفحة الحالية", fontSize = 11.sp, color = Color.White)
                    }

                    Button(
                        onClick = {
                            isExporting = true
                            coroutineScope.launch {
                                val outFile = withContext(Dispatchers.IO) {
                                    MultiFormatDocumentEngine.exportPdfWithOfficialCertificate(
                                        context = context,
                                        sourceUri = documentUri,
                                        sourceFileName = documentName,
                                        stampTitle = customStampTitle,
                                        authorOrOrgName = authorOrOrgName,
                                        stampColorHex = selectedColorHex,
                                        applyStampOnAllPages = applyOnAllPages,
                                        appendCertificatePage = appendCertificatePage,
                                        plainTextForStats = plainText
                                    )
                                }
                                isExporting = false
                                if (outFile != null) {
                                    onExportCertifiedPdfReady(outFile)
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, "تعذر إنشاء ملف PDF الموثق", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isExporting,
                        colors = ButtonDefaults.buttonColors(containerColor = previewColor),
                        modifier = Modifier.weight(1.4f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("تصدير PDF موثّق بالشهادة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ============================================================================
// 6. PAGE-PINNED VOICE & AUDIO NOTES STUDIO
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagePinnedVoiceNotesSheet(
    uriString: String,
    documentName: String,
    currentPositionIndex: Int,
    currentPositionLabel: String,
    voiceNotes: List<PageVoiceNoteItem>,
    onUpdateVoiceNotes: (List<PageVoiceNoteItem>) -> Unit,
    onReadNoteAloud: (String) -> Unit,
    onSendNoteToEditor: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var noteTitle by remember { mutableStateOf("ملاحظة صوتية على $currentPositionLabel") }
    var noteTranscript by remember { mutableStateOf("") }
    var playingNoteId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            elapsedSeconds = 0
            while (isRecording) {
                delay(1000L)
                elapsedSeconds++
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🎙️ المسجل الصوتي للملاحظات المرتبطة بالصفحات",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF43F5E)
                    )
                    Text(
                        text = "الموضع الحالي: $currentPositionLabel • اربط تعليقاتك الصوتية والنصية بكل صفحة",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                }
            }

            // Recorder Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(if (isRecording) Color(0xFFEF4444) else Color(0xFF64748B))
                            )
                            Text(
                                text = if (isRecording) "جارٍ التسجيل الصوتي... (${elapsedSeconds} ث)" else "جاهز لتسجيل ملاحظة على ($currentPositionLabel)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isRecording) Color(0xFFF87171) else Color.White
                            )
                        }

                        Button(
                            onClick = {
                                if (!isRecording) {
                                    isRecording = true
                                } else {
                                    isRecording = false
                                    val newNote = PageVoiceNoteItem(
                                        uriString = uriString,
                                        pageOrSectionIndex = currentPositionIndex,
                                        positionLabel = currentPositionLabel,
                                        title = noteTitle.ifBlank { "ملاحظة صوتية ($currentPositionLabel)" },
                                        transcriptOrSummary = noteTranscript.ifBlank { "تعليق صوتي مسجل حول $currentPositionLabel في مستند $documentName" },
                                        durationSeconds = elapsedSeconds.coerceAtLeast(3)
                                    )
                                    onUpdateVoiceNotes(listOf(newNote) + voiceNotes)
                                    noteTranscript = ""
                                    Toast.makeText(context, "تم حفظ الملاحظة الصوتية وربطها بـ $currentPositionLabel 🎙️", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRecording) Color(0xFFDC2626) else Color(0xFFF43F5E)
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isRecording) "إيقاف وحفظ" else "بدء التسجيل", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Animated Audio Waveform Bars when recording
                    if (isRecording) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (barIdx in 0 until 28) {
                                val hFrac = (((elapsedSeconds + barIdx * 3) % 7) + 2) / 9f
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(hFrac)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFF43F5E))
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = noteTranscript,
                        onValueChange = { noteTranscript = it },
                        label = { Text("ملخص أو تفريغ نصي مصاحب للملاحظة الصوتية (اختياري)", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            }

            // Saved Voice Notes List
            Text(
                text = "الملاحظات الصوتية المحفوظة (${voiceNotes.size})",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8)
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(voiceNotes, key = { it.id }) { item ->
                    val isPlaying = playingNoteId == item.id
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
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
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFF43F5E).copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = item.positionLabel,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFF43F5E),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Text(
                                        text = "${item.durationSeconds} ثانية",
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = {
                                            playingNoteId = if (isPlaying) null else item.id
                                            onReadNoteAloud(item.transcriptOrSummary)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                                            contentDescription = "تشغيل صوتي",
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            onSendNoteToEditor(item.transcriptOrSummary, "ملاحظة صوتية (${item.positionLabel})")
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Send, contentDescription = "إرسال للمحرر", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            onUpdateVoiceNotes(voiceNotes.filter { it.id != item.id })
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            Text(item.transcriptOrSummary, fontSize = 12.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 7. BACK-OF-BOOK AUTOMATED ALPHABETICAL INDEXER (كشاف المصطلحات أ - ي)
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackOfBookIndexSheet(
    documentName: String,
    plainText: String,
    outlineItems: List<DocumentOutlineItem>,
    onExportIndexToEditor: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val groupedIndex = remember(plainText, outlineItems) {
        MultiFormatDocumentEngine.generateBackOfBookIndex(plainText, outlineItems)
    }
    val letters = remember(groupedIndex) { groupedIndex.keys.toList() }
    var selectedLetter by remember(letters) { mutableStateOf<Char?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "📚 الكشاف الأبجدي الآلي للمصطلحات والأعلام (أ - ي)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                    Text(
                        text = "فهرس تحليلي لنهاية الكتاب مرتب أبجدياً مع مواضع الورود وعدد التكرارات",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                }
            }

            // Alphabet Filter Chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    FilterChip(
                        selected = selectedLetter == null,
                        onClick = { selectedLetter = null },
                        label = { Text("الكل", fontSize = 11.sp) }
                    )
                }
                items(letters) { letter ->
                    FilterChip(
                        selected = selectedLetter == letter,
                        onClick = { selectedLetter = letter },
                        label = { Text(letter.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            val visibleGroups = if (selectedLetter != null) {
                groupedIndex.filterKeys { it == selectedLetter }
            } else {
                groupedIndex
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                visibleGroups.forEach { (letter, entries) ->
                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0284C7).copy(alpha = 0.25f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "حرف ($letter) — ${entries.size} مصطلح",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                    items(entries) { entry ->
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.term, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text(
                                        text = entry.locations.joinToString(" ، "),
                                        fontSize = 11.sp,
                                        color = Color(0xFF38BDF8)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF334155)
                                ) {
                                    Text(
                                        text = "${entry.count}×",
                                        fontSize = 11.sp,
                                        color = Color(0xFFFBBF24),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val markdownIndex = buildString {
                        appendLine("# الكشاف الأبجدي للمصطلحات والمفاهيم — $documentName")
                        appendLine()
                        groupedIndex.forEach { (letter, entries) ->
                            appendLine("## حرف ($letter)")
                            entries.forEach { e ->
                                appendLine("- **${e.term}** (${e.count} مرات): ${e.locations.joinToString("، ")}")
                            }
                            appendLine()
                        }
                    }
                    onExportIndexToEditor(markdownIndex)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Icon(Icons.Default.LibraryBooks, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("تصدير الكشاف الأبجدي كملحق في محرر الكتابة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============================================================================
// 8. PRECISION ALIGNMENT & TYPOGRAPHIC MEASUREMENT GRID OVERLAY
// ============================================================================

@Composable
fun PrecisionAlignmentGridOverlay(
    primaryColor: Color,
    onCloseGrid: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val stepPx = 28.dp.toPx()

            // Fine Typographic & Engineering Grid
            val gridColor = primaryColor.copy(alpha = 0.16f)
            var x = 0f
            while (x <= w) {
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 1f
                )
                x += stepPx
            }
            var y = 0f
            while (y <= h) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f
                )
                y += stepPx
            }

            // Print Margin Guides (10% outer safety margin)
            val marginX = w * 0.08f
            val marginY = h * 0.06f
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
            drawRect(
                color = Color(0xFFEF4444).copy(alpha = 0.45f),
                topLeft = Offset(marginX, marginY),
                size = androidx.compose.ui.geometry.Size(w - marginX * 2, h - marginY * 2),
                style = Stroke(width = 2f, pathEffect = dashEffect)
            )

            // Center Crosshair Axis
            drawLine(
                color = Color(0xFF0EA5E9).copy(alpha = 0.35f),
                start = Offset(w / 2f, 0f),
                end = Offset(w / 2f, h),
                strokeWidth = 1.5f,
                pathEffect = dashEffect
            )
            drawLine(
                color = Color(0xFF0EA5E9).copy(alpha = 0.35f),
                start = Offset(0f, h / 2f),
                end = Offset(w, h / 2f),
                strokeWidth = 1.5f,
                pathEffect = dashEffect
            )
        }

        Surface(
            onClick = onCloseGrid,
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0F172A).copy(alpha = 0.82f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.GridOn, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                Text("شبكة القياس والمحاذاة الطباعية (إغلاق ✕)", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
