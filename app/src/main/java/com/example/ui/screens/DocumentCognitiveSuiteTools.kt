package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.util.*
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ============================================================================
// 1. INTERACTIVE DOCUMENT MIND MAP (الخريطة الذهنية التفاعلية للمستند)
// ============================================================================

private data class PositionedMindMapNode(
    val node: DocumentMindMapNode,
    val center: Offset,
    val radiusPx: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveMindMapSheet(
    documentName: String,
    outlineItems: List<DocumentOutlineItem>,
    highlights: List<DocumentHighlightItem>,
    plainText: String,
    customNodes: List<DocumentMindMapNode>,
    onAddCustomNode: (title: String, subtitle: String, category: String) -> Unit,
    onDeleteCustomNode: (String) -> Unit,
    onJumpToPosition: (Int) -> Unit,
    onExportMindMapToEditor: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val allNodes = remember(documentName, outlineItems, highlights, plainText, customNodes) {
        MultiFormatDocumentEngine.generateDocumentMindMap(
            documentTitle = documentName,
            outlineItems = outlineItems,
            highlights = highlights,
            plainText = plainText,
            customNodes = customNodes
        )
    }

    var selectedNode by remember(allNodes) { mutableStateOf(allNodes.firstOrNull()) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var showAddNodeForm by remember { mutableStateOf(false) }
    var newNodeTitle by remember { mutableStateOf("") }
    var newNodeSubtitle by remember { mutableStateOf("") }
    var renderedPositions by remember { mutableStateOf<List<PositionedMindMapNode>>(emptyList()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AccountTree, contentDescription = null, tint = Color(0xFF2563EB))
                    Column {
                        Text(
                            "الخريطة الذهنية التفاعلية للمستند",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "اسحب للتنقل • انقر على أي عقدة للانتقال لموضعها في المستند",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalIconButton(
                        onClick = {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.CenterFocusStrong, contentDescription = "توسيط", modifier = Modifier.size(18.dp))
                    }
                    FilledTonalIconButton(
                        onClick = { showAddNodeForm = !showAddNodeForm },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "إضافة فرع", modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Legend Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MindMapLegendChip("الجذر الرئيسي", Color(0xFF2563EB))
                MindMapLegendChip("فصول وأقسام (${allNodes.count { it.category == "CHAPTER" }})", Color(0xFF0EA5E9))
                MindMapLegendChip("مفاهيم محورية (${allNodes.count { it.category == "CONCEPT" }})", Color(0xFF8B5CF6))
                MindMapLegendChip("اقتباسات (${allNodes.count { it.category == "HIGHLIGHT" }})", Color(0xFFF59E0B))
                MindMapLegendChip("فروع مخصصة (${allNodes.count { it.category == "CUSTOM" }})", Color(0xFF10B981))
            }

            // Add Custom Node Form
            AnimatedVisibility(visible = showAddNodeForm) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("إضافة عقدة ذهنية جديدة للمستند", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = newNodeTitle,
                            onValueChange = { newNodeTitle = it },
                            label = { Text("عنوان الفكرة أو المحور") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newNodeSubtitle,
                            onValueChange = { newNodeSubtitle = it },
                            label = { Text("ملاحظة فرعية أو رقم الصفحة") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showAddNodeForm = false }) { Text("إلغاء") }
                            Spacer(Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    if (newNodeTitle.isNotBlank()) {
                                        onAddCustomNode(
                                            newNodeTitle.trim(),
                                            newNodeSubtitle.trim().ifBlank { "فرع مخصص" },
                                            "CUSTOM"
                                        )
                                        newNodeTitle = ""
                                        newNodeSubtitle = ""
                                        showAddNodeForm = false
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("إضافة للخريطة")
                            }
                        }
                    }
                }
            }

            // Interactive Mind Map Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(310.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                    .pointerInput(allNodes) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            zoomScale = (zoomScale * zoom).coerceIn(0.6f, 2.4f)
                            panOffset += pan
                        }
                    }
                    .pointerInput(allNodes) {
                        detectTapGestures { tapOffset ->
                            val hit = renderedPositions.firstOrNull { pos ->
                                (tapOffset - pos.center).getDistance() <= pos.radiusPx * 1.25f
                            }
                            if (hit != null) {
                                selectedNode = hit.node
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerX = size.width / 2f + panOffset.x
                    val centerY = size.height / 2f + panOffset.y
                    val rootCenter = Offset(centerX, centerY)

                    // Subtle background grid
                    val gridStep = 40.dp.toPx() * zoomScale
                    if (gridStep > 14f) {
                        var gx = (centerX % gridStep)
                        while (gx < size.width) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.04f),
                                start = Offset(gx, 0f),
                                end = Offset(gx, size.height),
                                strokeWidth = 1f
                            )
                            gx += gridStep
                        }
                        var gy = (centerY % gridStep)
                        while (gy < size.height) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.04f),
                                start = Offset(0f, gy),
                                end = Offset(size.width, gy),
                                strokeWidth = 1f
                            )
                            gy += gridStep
                        }
                    }

                    val posList = mutableListOf<PositionedMindMapNode>()
                    val nodeOffsets = mutableMapOf<String, Offset>()

                    val rootNode = allNodes.firstOrNull { it.category == "ROOT" }
                    if (rootNode != null) {
                        nodeOffsets[rootNode.id] = rootCenter
                        posList.add(PositionedMindMapNode(rootNode, rootCenter, 42.dp.toPx() * zoomScale))
                    }

                    // Ring 1: Chapters, Highlights, Custom nodes attached to root
                    val ring1Nodes = allNodes.filter { it.category != "ROOT" && (it.parentId == "root_node" || it.category != "CONCEPT") }
                    val ring1Radius = min(size.width, size.height) * 0.34f * zoomScale

                    ring1Nodes.forEachIndexed { idx, node ->
                        val angle = (2.0 * Math.PI * idx / ring1Nodes.size.coerceAtLeast(1)) - (Math.PI / 2.0)
                        val nx = centerX + (ring1Radius * cos(angle)).toFloat()
                        val ny = centerY + (ring1Radius * sin(angle)).toFloat()
                        val offset = Offset(nx, ny)
                        nodeOffsets[node.id] = offset
                        posList.add(PositionedMindMapNode(node, offset, 30.dp.toPx() * zoomScale))
                    }

                    // Ring 2: Concept nodes attached to chapters or outer ring
                    val ring2Nodes = allNodes.filter { it.category == "CONCEPT" }
                    val ring2Radius = min(size.width, size.height) * 0.48f * zoomScale
                    ring2Nodes.forEachIndexed { idx, node ->
                        val angle = (2.0 * Math.PI * idx / ring2Nodes.size.coerceAtLeast(1)) - (Math.PI / 4.0)
                        val nx = centerX + (ring2Radius * cos(angle)).toFloat()
                        val ny = centerY + (ring2Radius * sin(angle)).toFloat()
                        val offset = Offset(nx, ny)
                        nodeOffsets[node.id] = offset
                        posList.add(PositionedMindMapNode(node, offset, 26.dp.toPx() * zoomScale))
                    }

                    renderedPositions = posList

                    // Draw Curved Bezier Connections
                    for (pos in posList) {
                        val parentId = pos.node.parentId ?: continue
                        val parentPos = nodeOffsets[parentId] ?: rootCenter
                        val childPos = pos.center
                        val nodeColor = try {
                            Color(android.graphics.Color.parseColor(pos.node.colorHex))
                        } catch (_: Exception) {
                            Color(0xFF38BDF8)
                        }
                        val path = Path().apply {
                            moveTo(parentPos.x, parentPos.y)
                            val ctrlX1 = (parentPos.x + childPos.x) / 2f
                            val ctrlY1 = parentPos.y
                            val ctrlX2 = (parentPos.x + childPos.x) / 2f
                            val ctrlY2 = childPos.y
                            cubicTo(ctrlX1, ctrlY1, ctrlX2, ctrlY2, childPos.x, childPos.y)
                        }
                        drawPath(
                            path = path,
                            color = nodeColor.copy(alpha = 0.65f),
                            style = Stroke(width = 2.5.dp.toPx() * zoomScale)
                        )
                    }

                    // Draw Nodes & Labels
                    drawIntoCanvas { canvas ->
                        val titlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.WHITE
                            textAlign = android.graphics.Paint.Align.CENTER
                            isFakeBoldText = true
                        }
                        val subPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb(200, 226, 232, 240)
                            textAlign = android.graphics.Paint.Align.CENTER
                        }

                        for (pos in posList) {
                            val isSelected = selectedNode?.id == pos.node.id
                            val baseColor = try {
                                Color(android.graphics.Color.parseColor(pos.node.colorHex))
                            } catch (_: Exception) {
                                Color(0xFF2563EB)
                            }
                            val boxW = if (pos.node.category == "ROOT") 132.dp.toPx() * zoomScale else 108.dp.toPx() * zoomScale
                            val boxH = if (pos.node.category == "ROOT") 48.dp.toPx() * zoomScale else 38.dp.toPx() * zoomScale
                            val topLeft = Offset(pos.center.x - boxW / 2f, pos.center.y - boxH / 2f)

                            if (isSelected) {
                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.28f),
                                    topLeft = Offset(topLeft.x - 4.dp.toPx(), topLeft.y - 4.dp.toPx()),
                                    size = Size(boxW + 8.dp.toPx(), boxH + 8.dp.toPx()),
                                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                                )
                            }

                            drawRoundRect(
                                color = baseColor,
                                topLeft = topLeft,
                                size = Size(boxW, boxH),
                                cornerRadius = CornerRadius(11.dp.toPx(), 11.dp.toPx())
                            )
                            drawRoundRect(
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.35f),
                                topLeft = topLeft,
                                size = Size(boxW, boxH),
                                cornerRadius = CornerRadius(11.dp.toPx(), 11.dp.toPx()),
                                style = Stroke(width = if (isSelected) 2.2.dp.toPx() else 1.dp.toPx())
                            )

                            titlePaint.textSize = (if (pos.node.category == "ROOT") 11.5.sp.toPx() else 10.sp.toPx()) * zoomScale
                            subPaint.textSize = 8.5.sp.toPx() * zoomScale

                            val shortTitle = if (pos.node.title.length > 18) pos.node.title.take(17) + "…" else pos.node.title
                            canvas.nativeCanvas.drawText(
                                shortTitle,
                                pos.center.x,
                                pos.center.y - (2.dp.toPx() * zoomScale),
                                titlePaint
                            )
                            val shortSub = if (pos.node.subtitle.length > 18) pos.node.subtitle.take(17) + "…" else pos.node.subtitle
                            canvas.nativeCanvas.drawText(
                                shortSub,
                                pos.center.x,
                                pos.center.y + (11.dp.toPx() * zoomScale),
                                subPaint
                            )
                        }
                    }
                }

                // Zoom indicator pill inside canvas corner
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp),
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.65f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "التكبير ${(zoomScale * 100).toInt()}% • ${allNodes.size} عقدة",
                            color = Color.White,
                            fontSize = 11.sp
                        )
                        Text(
                            "+",
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { zoomScale = (zoomScale + 0.2f).coerceAtMost(2.4f) }
                        )
                        Text(
                            "−",
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { zoomScale = (zoomScale - 0.2f).coerceAtLeast(0.6f) }
                        )
                    }
                }
            }

            // Selected Node Details & Direct Actions
            selectedNode?.let { sel ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sel.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${sel.subtitle} • الموضع: ${sel.positionIndex + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (sel.category == "CUSTOM") {
                                OutlinedIconButton(
                                    onClick = { onDeleteCustomNode(sel.id) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                            FilledTonalButton(
                                onClick = {
                                    onJumpToPosition(sel.positionIndex)
                                    onDismiss()
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("الانتقال للموضع", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Export Mind Map Outline to Active Editor Chapter
            Button(
                onClick = {
                    val markdownOutline = MultiFormatDocumentEngine.formatMindMapAsChapterOutline(documentName, allNodes)
                    onExportMindMapToEditor(markdownOutline)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.DriveFileMove, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("تصدير الخريطة الذهنية كـ «مخطط فصل» إلى محرر الكتابة")
            }
        }
    }
}

@Composable
private fun MindMapLegendChip(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ============================================================================
// 2. RSVP VISUAL SPEED READER (مشغل القراءة السريعة البصرية 200 - 900 كلمة/دقيقة)
// ============================================================================

@Composable
fun RsvpSpeedReaderDialog(
    documentTitle: String,
    plainText: String,
    onDismiss: () -> Unit
) {
    val words = remember(plainText) {
        plainText
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("لا", "يوجد", "نص", "قابل", "للقراءة", "السريعة", "في", "هذا", "المستند") }
    }

    var currentWordIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var wpm by remember { mutableIntStateOf(360) }

    // Loop for RSVP playback with intelligent punctuation pauses
    LaunchedEffect(isPlaying, wpm, words) {
        while (isPlaying && currentWordIndex < words.size) {
            val word = words[currentWordIndex]
            val baseDelayMs = (60_000L / wpm.coerceIn(120, 1000))
            val punctuationMultiplier = when {
                word.endsWith(".") || word.endsWith("!") || word.endsWith("؟") || word.endsWith("?") -> 2.1f
                word.endsWith("،") || word.endsWith(",") || word.endsWith(";") || word.endsWith("؛") || word.endsWith(":") -> 1.55f
                word.length >= 10 -> 1.25f
                else -> 1.0f
            }
            delay((baseDelayMs * punctuationMultiplier).toLong().coerceAtLeast(55L))
            if (currentWordIndex < words.lastIndex) {
                currentWordIndex++
            } else {
                isPlaying = false
            }
        }
    }

    val currentWord = words.getOrElse(currentWordIndex) { "" }
    val remainingWords = (words.size - currentWordIndex - 1).coerceAtLeast(0)
    val remainingSeconds = ((remainingWords * 60f) / wpm.coerceAtLeast(1)).toInt()

    Dialog(
        onDismissRequest = {
            isPlaying = false
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF0B1120),
            contentColor = Color.White,
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFFF59E0B))
                        Column {
                            Text("مشغل القراءة السريعة البصرية (RSVP)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                documentTitle.take(36),
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = {
                        isPlaying = false
                        onDismiss()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                    }
                }

                // Focal Reticle Box with ORP (Optimal Recognition Point) red pivot letter
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF020617))
                        .border(1.5.dp, Color(0xFF334155), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Top & Bottom Focal Reticle ticks
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = size.width / 2f
                        drawLine(
                            color = Color(0xFFEF4444).copy(alpha = 0.7f),
                            start = Offset(cx, 10.dp.toPx()),
                            end = Offset(cx, 28.dp.toPx()),
                            strokeWidth = 2.5.dp.toPx()
                        )
                        drawLine(
                            color = Color(0xFFEF4444).copy(alpha = 0.7f),
                            start = Offset(cx, size.height - 28.dp.toPx()),
                            end = Offset(cx, size.height - 10.dp.toPx()),
                            strokeWidth = 2.5.dp.toPx()
                        )
                        // Subtle horizontal eye-level guide
                        drawLine(
                            color = Color.White.copy(alpha = 0.06f),
                            start = Offset(16.dp.toPx(), size.height / 2f),
                            end = Offset(size.width - 16.dp.toPx(), size.height / 2f),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Build ORP highlighted word
                    val annotatedWord = remember(currentWord) {
                        buildAnnotatedString {
                            if (currentWord.isEmpty()) return@buildAnnotatedString
                            val pivotIndex = when {
                                currentWord.length <= 1 -> 0
                                currentWord.length <= 5 -> 1
                                currentWord.length <= 9 -> 2
                                else -> 3
                            }.coerceIn(0, currentWord.lastIndex)

                            val before = currentWord.substring(0, pivotIndex)
                            val pivotChar = currentWord.substring(pivotIndex, pivotIndex + 1)
                            val after = currentWord.substring(pivotIndex + 1)

                            withStyle(SpanStyle(color = Color.White)) {
                                append(before)
                            }
                            withStyle(SpanStyle(color = Color(0xFFEF4444), fontWeight = FontWeight.ExtraBold)) {
                                append(pivotChar)
                            }
                            withStyle(SpanStyle(color = Color.White)) {
                                append(after)
                            }
                        }
                    }

                    Text(
                        text = annotatedWord,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                // Surrounding context preview (previous 3 words + current + next 3 words)
                val contextWindow = remember(currentWordIndex, words) {
                    val start = (currentWordIndex - 3).coerceAtLeast(0)
                    val end = (currentWordIndex + 4).coerceAtMost(words.size)
                    words.subList(start, end).joinToString(" ")
                }
                Text(
                    text = contextWindow,
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Progress Slider & Telemetry
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = currentWordIndex.toFloat(),
                        onValueChange = { currentWordIndex = it.toInt().coerceIn(0, words.lastIndex) },
                        valueRange = 0f..words.lastIndex.coerceAtLeast(1).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFEF4444),
                            activeTrackColor = Color(0xFFEF4444)
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "الكلمة ${currentWordIndex + 1} من ${words.size}",
                            fontSize = 11.sp,
                            color = Color(0xFFCBD5E1)
                        )
                        Text(
                            "المتبقي: ${remainingSeconds / 60}د و ${remainingSeconds % 60}ث",
                            fontSize = 11.sp,
                            color = Color(0xFF38BDF8)
                        )
                    }
                }

                // Playback Transport Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedIconButton(
                        onClick = { currentWordIndex = 0; isPlaying = false },
                        border = BorderStroke(1.dp, Color(0xFF475569))
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = "من البداية", tint = Color.White)
                    }
                    OutlinedIconButton(
                        onClick = { currentWordIndex = (currentWordIndex - 10).coerceAtLeast(0) },
                        border = BorderStroke(1.dp, Color(0xFF475569))
                    ) {
                        Icon(Icons.Default.FastRewind, contentDescription = "-10 كلمات", tint = Color.White)
                    }
                    FloatingActionButton(
                        onClick = {
                            if (currentWordIndex >= words.lastIndex) currentWordIndex = 0
                            isPlaying = !isPlaying
                        },
                        containerColor = if (isPlaying) Color(0xFFF59E0B) else Color(0xFFEF4444),
                        contentColor = Color.White,
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "إيقاف مؤقت" else "تشغيل",
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    OutlinedIconButton(
                        onClick = { currentWordIndex = (currentWordIndex + 10).coerceAtMost(words.lastIndex) },
                        border = BorderStroke(1.dp, Color(0xFF475569))
                    ) {
                        Icon(Icons.Default.FastForward, contentDescription = "+10 كلمات", tint = Color.White)
                    }
                }

                // WPM Speed Controls (200 .. 900 WPM)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1E293B)
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
                            Text("سرعة القراءة البصرية", fontSize = 12.sp, color = Color(0xFFCBD5E1))
                            Text(
                                "$wpm كلمة / دقيقة (WPM)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFBBF24)
                            )
                        }
                        Slider(
                            value = wpm.toFloat(),
                            onValueChange = { wpm = it.toInt() },
                            valueRange = 200f..900f,
                            steps = 13
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(250 to "هادئة", 360 to "متوسطة", 500 to "سريعة", 750 to "فائقة").forEach { (preset, label) ->
                                FilterChip(
                                    selected = wpm == preset,
                                    onClick = { wpm = preset },
                                    label = { Text("$preset ($label)", fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 3. ASK DOCUMENT Q&A & ORIGINALITY / OVERLAP CHECKER
//    (مُحاور المستند الذكي وفاحص الأصالة والتناص)
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskDocumentAndOriginalitySheet(
    documentName: String,
    plainText: String,
    outlineItems: List<DocumentOutlineItem>,
    activeEditorChapterTitle: String,
    activeEditorChapterContent: String,
    onSendSnippetToEditor: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var questionInput by remember { mutableStateOf("") }
    val qaHistory = remember {
        mutableStateListOf<DocumentQaAnswer>()
    }

    // Initialize with an automatic welcome Q&A if empty
    LaunchedEffect(plainText) {
        if (qaHistory.isEmpty() && plainText.isNotBlank()) {
            qaHistory.add(
                MultiFormatDocumentEngine.askDocumentQuestion(
                    question = "ما هي الفكرة المحورية وأهم النقاط في هذا المستند؟",
                    plainText = plainText,
                    outlineItems = outlineItems
                )
            )
        }
    }

    var customDraftToCompare by remember(activeEditorChapterContent) {
        mutableStateOf(activeEditorChapterContent)
    }
    val originalityReport = remember(plainText, customDraftToCompare) {
        MultiFormatDocumentEngine.checkOriginalityAndOverlap(
            referenceText = plainText,
            draftText = customDraftToCompare
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(
                            "مُحاور المستند الذكي وفاحص الأصالة",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            documentName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق")
                }
            }

            // Segmented Tabs
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("🤖 اسأل المستند (Q&A)", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("🛡️ فاحص الأصالة والتناص", fontWeight = FontWeight.SemiBold) }
                )
            }

            if (selectedTab == 0) {
                // Quick Suggested Questions
                val suggestions = listOf(
                    "ما الفكرة المحورية للمستند؟",
                    "ما أهم النتائج أو التوصيات؟",
                    "ما الأرقام والإحصائيات الواردة؟",
                    "اشرح المفاهيم الأساسية في النص"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suggestions.forEach { sug ->
                        SuggestionChip(
                            onClick = {
                                val ans = MultiFormatDocumentEngine.askDocumentQuestion(sug, plainText, outlineItems)
                                qaHistory.add(0, ans)
                            },
                            label = { Text(sug, fontSize = 11.sp) }
                        )
                    }
                }

                // Question Input Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = questionInput,
                        onValueChange = { questionInput = it },
                        placeholder = { Text("اطرح أي سؤال حول محتوى المستند...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Button(
                        onClick = {
                            if (questionInput.isNotBlank()) {
                                val ans = MultiFormatDocumentEngine.askDocumentQuestion(
                                    question = questionInput,
                                    plainText = plainText,
                                    outlineItems = outlineItems
                                )
                                qaHistory.add(0, ans)
                                questionInput = ""
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "إرسال", modifier = Modifier.size(18.dp))
                    }
                }

                // Q&A Answers List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(qaHistory) { qa ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "❓ ${qa.question}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = Color(0xFF10B981).copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            "تطابق ${qa.confidencePct}%",
                                            fontSize = 10.sp,
                                            color = Color(0xFF059669),
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = qa.bestAnswerSnippet,
                                    style = MaterialTheme.typography.bodySmall,
                                    lineHeight = 20.sp
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "📍 المصدر: ${qa.positionLabel}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(
                                        onClick = {
                                            onSendSnippetToEditor("سؤال بحثي: ${qa.question}\nالإجابة من المستند ($documentName - ${qa.positionLabel}):\n«${qa.bestAnswerSnippet}»")
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("إرسال للمحرر", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // TAB 2: Originality & Plagiarism / Overlap Checker
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Gauges Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("نسبة الأصالة والاستقلالية", fontSize = 11.sp, color = Color(0xFF047857))
                                Text(
                                    "${originalityReport.originalityPct}%",
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF059669)
                                )
                                Text("صياغة حرة ومبتكرة", fontSize = 10.sp, color = Color(0xFF047857))
                            }
                        }

                        val overlapColor = when {
                            originalityReport.overlapPct >= 40 -> Color(0xFFEF4444)
                            originalityReport.overlapPct >= 15 -> Color(0xFFF59E0B)
                            else -> Color(0xFF3B82F6)
                        }
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = overlapColor.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, overlapColor.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("نسبة التداخل اللفظي المباشر", fontSize = 11.sp, color = overlapColor)
                                Text(
                                    "${originalityReport.overlapPct}%",
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = overlapColor
                                )
                                Text(
                                    "${originalityReport.sharedSentences.size} جمل متطابقة من ${originalityReport.totalCheckedSentences}",
                                    fontSize = 10.sp,
                                    color = overlapColor
                                )
                            }
                        }
                    }

                    // Draft Text Field (pre-filled with active chapter draft in Writing Editor)
                    OutlinedTextField(
                        value = customDraftToCompare,
                        onValueChange = { customDraftToCompare = it },
                        label = {
                            Text(
                                if (activeEditorChapterTitle.isNotBlank())
                                    "مسودة الفصل النشط للمقارنة: ($activeEditorChapterTitle)"
                                else
                                    "الصق مسودتك أو نص الفصل لفحص نسبة التناص والأصالة مع المستند"
                            )
                        },
                        placeholder = { Text("اكتب أو الصق مسودة كتابتك هنا لفحص مدى تطابقها اللفظي مع المستند المفتوح...") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (originalityReport.sharedSentences.isNotEmpty()) {
                        Text(
                            "⚠️ الجمل ذات التطابق اللفظي المباشر (يُنصح بإعادة صياغتها):",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD97706)
                        )
                        originalityReport.sharedSentences.forEach { sentence ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFEF3C7).copy(alpha = 0.55f),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "«$sentence»",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF92400E),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp)
                                )
                            }
                        }
                    } else if (customDraftToCompare.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFD1FAE5).copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF059669))
                                Text(
                                    "ممتاز! لم يتم رصد أي نسخ حرفي مباشر بين مسودتك وهذا المستند المرجعي.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF065F46),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 4. INTERACTIVE CHARTS & STATISTICAL DATA VISUALIZER
//    (استوديو الرسوم البيانية وتحليل الجداول والأرقام)
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpreadsheetChartsAndStatsSheet(
    documentName: String,
    spreadsheet: SpreadsheetContent?,
    plainText: String,
    onInsertStatsReportToEditor: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val statsList = remember(spreadsheet, plainText, documentName) {
        if (spreadsheet != null) {
            MultiFormatDocumentEngine.analyzeSpreadsheetColumnStats(spreadsheet)
        } else {
            MultiFormatDocumentEngine.extractNumericStatsFromPlainText(plainText, documentName)
        }
    }

    var selectedColIdx by remember { mutableIntStateOf(0) }
    var chartType by remember { mutableStateOf("BAR") } // BAR, LINE, PIE

    val activeStats = statsList.getOrNull(selectedColIdx) ?: statsList.firstOrNull()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 22.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.BarChart, contentDescription = null, tint = Color(0xFF2563EB))
                    Column {
                        Text(
                            "استوديو الرسوم البيانية والتحليل الإحصائي",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            documentName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق")
                }
            }

            if (activeStats == null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Text(
                        "لم يتم العثور على سلاسل رقمية كافية في هذا المستند لرسم المخططات البيانية.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                // Column Selector Pills
                if (statsList.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        statsList.forEachIndexed { idx, st ->
                            FilterChip(
                                selected = selectedColIdx == idx,
                                onClick = { selectedColIdx = idx },
                                label = { Text(st.columnName, fontSize = 12.sp) }
                            )
                        }
                    }
                }

                // Chart Type Switcher (Bar / Line / Donut)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "BAR" to "📊 أعمدة بيانية",
                        "LINE" to "📈 منحنى خطي",
                        "PIE" to "🍩 دائرة نسبية"
                    ).forEach { (type, label) ->
                        FilterChip(
                            selected = chartType == type,
                            onClick = { chartType = type },
                            label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Interactive Canvas Chart
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "المخطط البصري: ${activeStats.columnName} (${activeStats.numericValues.size} قراءات)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        val values = activeStats.numericValues.take(12)
                        val labels = activeStats.labels.take(12)
                        val palette = listOf(
                            Color(0xFF3B82F6),
                            Color(0xFF10B981),
                            Color(0xFFF59E0B),
                            Color(0xFF8B5CF6),
                            Color(0xFFEC4899),
                            Color(0xFF06B6D4),
                            Color(0xFFEF4444),
                            Color(0xFF84CC16)
                        )

                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                        ) {
                            val maxVal = (values.maxOrNull() ?: 1.0).coerceAtLeast(0.001)
                            val minVal = (values.minOrNull() ?: 0.0).coerceAtMost(0.0)
                            val span = (maxVal - minVal).coerceAtLeast(1.0)
                            val bottomPad = 28.dp.toPx()
                            val topPad = 18.dp.toPx()
                            val chartH = (size.height - bottomPad - topPad).coerceAtLeast(40f)

                            when (chartType) {
                                "BAR" -> {
                                    val count = values.size.coerceAtLeast(1)
                                    val slotW = size.width / count
                                    val barW = (slotW * 0.62f).coerceAtMost(42.dp.toPx())

                                    drawIntoCanvas { canvas ->
                                        val lblPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                            color = android.graphics.Color.argb(210, 203, 213, 225)
                                            textSize = 9.sp.toPx()
                                            textAlign = android.graphics.Paint.Align.CENTER
                                        }
                                        val valPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                            color = android.graphics.Color.WHITE
                                            textSize = 9.5.sp.toPx()
                                            isFakeBoldText = true
                                            textAlign = android.graphics.Paint.Align.CENTER
                                        }

                                        values.forEachIndexed { i, v ->
                                            val ratio = ((v - minVal) / span).toFloat().coerceIn(0.04f, 1f)
                                            val bh = ratio * chartH
                                            val left = i * slotW + (slotW - barW) / 2f
                                            val top = topPad + (chartH - bh)
                                            val barColor = palette[i % palette.size]

                                            drawRoundRect(
                                                color = barColor,
                                                topLeft = Offset(left, top),
                                                size = Size(barW, bh),
                                                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                                            )

                                            val cx = left + barW / 2f
                                            canvas.nativeCanvas.drawText(
                                                formatCompactNum(v),
                                                cx,
                                                (top - 4.dp.toPx()).coerceAtLeast(11.dp.toPx()),
                                                valPaint
                                            )
                                            val shortLbl = labels.getOrElse(i) { "#${i + 1}" }.take(7)
                                            canvas.nativeCanvas.drawText(
                                                shortLbl,
                                                cx,
                                                size.height - 6.dp.toPx(),
                                                lblPaint
                                            )
                                        }
                                    }
                                }

                                "LINE" -> {
                                    val count = values.size.coerceAtLeast(1)
                                    val stepX = if (count > 1) size.width / (count - 1) else size.width / 2f
                                    val points = values.mapIndexed { i, v ->
                                        val ratio = ((v - minVal) / span).toFloat().coerceIn(0f, 1f)
                                        Offset(
                                            x = if (count > 1) i * stepX else size.width / 2f,
                                            y = topPad + chartH * (1f - ratio)
                                        )
                                    }

                                    if (points.size >= 2) {
                                        val linePath = Path().apply {
                                            moveTo(points.first().x, points.first().y)
                                            for (k in 1 until points.size) {
                                                lineTo(points[k].x, points[k].y)
                                            }
                                        }
                                        drawPath(
                                            path = linePath,
                                            color = Color(0xFF38BDF8),
                                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                                        )
                                    }

                                    drawIntoCanvas { canvas ->
                                        val valPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                            color = android.graphics.Color.WHITE
                                            textSize = 9.5.sp.toPx()
                                            isFakeBoldText = true
                                            textAlign = android.graphics.Paint.Align.CENTER
                                        }
                                        points.forEachIndexed { i, pt ->
                                            drawCircle(color = Color(0xFF0284C7), radius = 6.dp.toPx(), center = pt)
                                            drawCircle(color = Color.White, radius = 3.dp.toPx(), center = pt)
                                            canvas.nativeCanvas.drawText(
                                                formatCompactNum(values[i]),
                                                pt.x.coerceIn(20f, size.width - 20f),
                                                (pt.y - 8.dp.toPx()).coerceAtLeast(12.dp.toPx()),
                                                valPaint
                                            )
                                        }
                                    }
                                }

                                "PIE" -> {
                                    val posValues = values.map { kotlin.math.abs(it) }
                                    val total = posValues.sum().coerceAtLeast(0.001)
                                    val diameter = min(size.width, size.height) * 0.78f
                                    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                                    var startAngle = -90f

                                    posValues.forEachIndexed { i, v ->
                                        val sweep = ((v / total) * 360.0).toFloat()
                                        drawArc(
                                            color = palette[i % palette.size],
                                            startAngle = startAngle,
                                            sweepAngle = sweep,
                                            useCenter = false,
                                            topLeft = topLeft,
                                            size = Size(diameter, diameter),
                                            style = Stroke(width = 28.dp.toPx())
                                        )
                                        startAngle += sweep
                                    }
                                }
                            }
                        }
                    }
                }

                // Statistical KPI Cards Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatMetricCard("المجموع", formatCompactNum(activeStats.sum), Color(0xFF2563EB), Modifier.weight(1f))
                    StatMetricCard("المتوسط", formatCompactNum(activeStats.average), Color(0xFF0EA5E9), Modifier.weight(1f))
                    StatMetricCard("الأعلى", formatCompactNum(activeStats.max), Color(0xFF10B981), Modifier.weight(1f))
                    StatMetricCard("الأدنى", formatCompactNum(activeStats.min), Color(0xFFF59E0B), Modifier.weight(1f))
                }

                // Growth Rate Banner
                val growthSign = if (activeStats.growthPct >= 0) "+" else ""
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("معدل التغير الكلي عبر السلسلة:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "$growthSign${"%.1f".format(activeStats.growthPct)}%",
                            fontWeight = FontWeight.ExtraBold,
                            color = if (activeStats.growthPct >= 0) Color(0xFF059669) else Color(0xFFDC2626)
                        )
                    }
                }

                // Send Statistical Report to Active Writing Editor Chapter
                Button(
                    onClick = {
                        val reportText = buildString {
                            appendLine("📊 خلاصة التحليل الإحصائي ($documentName - ${activeStats.columnName}):")
                            appendLine("- عدد القراءات: ${activeStats.numericValues.size}")
                            appendLine("- المجموع الإجمالي: ${formatCompactNum(activeStats.sum)}")
                            appendLine("- المتوسط الحسابي: ${formatCompactNum(activeStats.average)}")
                            appendLine("- القيمة الأعلى: ${formatCompactNum(activeStats.max)} | القيمة الأدنى: ${formatCompactNum(activeStats.min)}")
                            appendLine("- معدل التغير والنمو: $growthSign${"%.1f".format(activeStats.growthPct)}%")
                        }
                        onInsertStatsReportToEditor(reportText)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PostAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("إدراج التقرير الإحصائي في مسودة محرر الكتابة")
                }
            }
        }
    }
}

@Composable
private fun StatMetricCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 1)
        }
    }
}

private fun formatCompactNum(v: Double): String {
    return if (v % 1.0 == 0.0) {
        v.toLong().toString()
    } else {
        "%.2f".format(v)
    }
}

// ============================================================================
// 5. PDF BLACKOUT REDACTION OVERLAY & DIGITAL FORENSICS INSPECTOR
//    (أداة طمس البيانات الحساسة ومختبر الفحص الجنائي والبيانات الوصفية)
// ============================================================================

@Composable
fun PdfRedactionOverlay(
    currentPage: Int,
    redactions: List<PdfRedactionRect>,
    isRedactionModeActive: Boolean,
    onAddRedaction: (PdfRedactionRect) -> Unit,
    modifier: Modifier = Modifier
) {
    val pageRedactions = remember(redactions, currentPage) {
        redactions.filter { it.pageIndex == currentPage }
    }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (isRedactionModeActive) {
                    Modifier.pointerInput(currentPage) {
                        detectDragGestures(
                            onDragStart = { start ->
                                dragStart = start
                                dragCurrent = start
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                dragCurrent = change.position
                            },
                            onDragEnd = {
                                val st = dragStart
                                val cur = dragCurrent
                                if (st != null && cur != null && size.width > 10 && size.height > 10) {
                                    val l = (minOf(st.x, cur.x) / size.width).coerceIn(0f, 1f)
                                    val r = (maxOf(st.x, cur.x) / size.width).coerceIn(0f, 1f)
                                    val t = (minOf(st.y, cur.y) / size.height).coerceIn(0f, 1f)
                                    val b = (maxOf(st.y, cur.y) / size.height).coerceIn(0f, 1f)
                                    if ((r - l) > 0.02f && (b - t) > 0.01f) {
                                        onAddRedaction(
                                            PdfRedactionRect(
                                                pageIndex = currentPage,
                                                leftNorm = l,
                                                topNorm = t,
                                                rightNorm = r,
                                                bottomNorm = b
                                            )
                                        )
                                    }
                                }
                                dragStart = null
                                dragCurrent = null
                            }
                        )
                    }
                } else Modifier
            )
    ) {
        // Draw existing permanent blackout rectangles for this page
        for (red in pageRedactions) {
            val l = red.leftNorm * size.width
            val t = red.topNorm * size.height
            val w = (red.rightNorm - red.leftNorm) * size.width
            val h = (red.bottomNorm - red.topNorm) * size.height

            drawRect(
                color = Color(0xFF020617),
                topLeft = Offset(l, t),
                size = Size(w, h)
            )
            drawRect(
                color = Color(0xFFEF4444).copy(alpha = 0.7f),
                topLeft = Offset(l, t),
                size = Size(w, h),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        // Draw active drag preview
        val st = dragStart
        val cur = dragCurrent
        if (st != null && cur != null) {
            val l = minOf(st.x, cur.x)
            val t = minOf(st.y, cur.y)
            val w = kotlin.math.abs(cur.x - st.x)
            val h = kotlin.math.abs(cur.y - st.y)
            drawRect(
                color = Color.Black.copy(alpha = 0.75f),
                topLeft = Offset(l, t),
                size = Size(w, h)
            )
            drawRect(
                color = Color(0xFFEF4444),
                topLeft = Offset(l, t),
                size = Size(w, h),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfRedactionAndForensicsSheet(
    documentName: String,
    documentUri: Uri?,
    plainText: String,
    isPdfDocument: Boolean,
    isRedactionDrawingMode: Boolean,
    onToggleRedactionDrawingMode: (Boolean) -> Unit,
    redactions: List<PdfRedactionRect>,
    onClearRedactions: () -> Unit,
    onExportSanitizedPdf: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val forensics = remember(documentUri, plainText) {
        MultiFormatDocumentEngine.computeDocumentForensics(context, documentUri, plainText)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFFDC2626))
                    Column {
                        Text(
                            "مختبر الفحص الجنائي وطمس البيانات الحساسة",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            documentName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق")
                }
            }

            // PDF Redaction Controls (if PDF)
            if (isPdfDocument) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFEF2F2),
                    border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "🖊️ أداة الطمس الأمني الدائم (PDF Blackout Redaction)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF991B1B)
                                )
                                Text(
                                    "ارسم مستطيلات سوداء معتمة فوق الأسماء أو الأرقام السرية لطمسها نهائياً عند التصدير (${redactions.size} منطقة مطموسة)",
                                    fontSize = 11.sp,
                                    color = Color(0xFF7F1D1D)
                                )
                            }
                            Switch(
                                checked = isRedactionDrawingMode,
                                onCheckedChange = {
                                    onToggleRedactionDrawingMode(it)
                                    if (it) onDismiss()
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onClearRedactions,
                                enabled = redactions.isNotEmpty(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("مسح الطمس (${redactions.size})", fontSize = 12.sp)
                            }
                            Button(
                                onClick = {
                                    onExportSanitizedPdf()
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("تصدير PDF مطموس وآمن", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // SHA-256 Digital Fingerprint Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                            "🔐 البصمة الرقمية الجنائية للملف (SHA-256 Checksum)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("SHA-256", forensics.sha256Hash))
                                Toast.makeText(context, "تم نسخ البصمة الرقمية SHA-256", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("نسخ", fontSize = 11.sp)
                        }
                    }
                    Text(
                        text = forensics.sha256Hash,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "الحجم الدقيق: ${forensics.fileSizeBytes} بايت • إجمالي الكلمات: ${forensics.wordCount} كلمة",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Readability & Lexical Diversity Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatMetricCard(
                    label = "مؤشر سهولة القراءة",
                    value = "${forensics.readabilityScore}/100",
                    color = Color(0xFF0EA5E9),
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    label = "ثراء المفردات اللغوية",
                    value = "${forensics.lexicalDiversityPct}%",
                    color = Color(0xFF8B5CF6),
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    label = "متوسط طول الجملة",
                    value = "${forensics.avgSentenceLength} كلمة",
                    color = Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                "📌 تصنيف الأسلوب القرائي: ${forensics.readabilityLabel} (${forensics.uniqueWordCount} مفردة فريدة)",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )

            // Top Frequent Words Distribution
            if (forensics.topFrequentWords.isNotEmpty()) {
                Text(
                    "📊 الكلمات المفتاحية الأكثر تردداً في المستند:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                val maxFreq = forensics.topFrequentWords.maxOf { it.second }.coerceAtLeast(1)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    forensics.topFrequentWords.forEach { (word, count) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = word,
                                modifier = Modifier.width(90.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            LinearProgressIndicator(
                                progress = { count.toFloat() / maxFreq.toFloat() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(50))
                            )
                            Text(
                                text = "$count×",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 6. SMART MAGNIFIER LOUPE, AUTO-CROP MARGINS & PRESENTER LASER POINTER
//    (العدسة المكبرة الذكية 2.5x، قص الهوامش، ومؤشر الليزر المسرحي)
// ============================================================================

@Composable
fun SmartMagnifierAndLaserOverlay(
    pdfBitmap: Bitmap?,
    isMagnifierLoupeActive: Boolean,
    isLaserPointerActive: Boolean,
    modifier: Modifier = Modifier
) {
    var touchPos by remember { mutableStateOf<Offset?>(null) }
    val laserTrail = remember { mutableStateListOf<Offset>() }

    if (!isMagnifierLoupeActive && !isLaserPointerActive) return

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isMagnifierLoupeActive, isLaserPointerActive) {
                detectDragGestures(
                    onDragStart = { pos ->
                        touchPos = pos
                        if (isLaserPointerActive) {
                            laserTrail.clear()
                            laserTrail.add(pos)
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        touchPos = change.position
                        if (isLaserPointerActive) {
                            laserTrail.add(change.position)
                            if (laserTrail.size > 24) {
                                laserTrail.removeAt(0)
                            }
                        }
                    },
                    onDragEnd = {
                        touchPos = null
                        laserTrail.clear()
                    },
                    onDragCancel = {
                        touchPos = null
                        laserTrail.clear()
                    }
                )
            }
    ) {
        val currentTouch = touchPos ?: return@Canvas

        // 1. Smart 2.5x Magnifier Loupe over PDF Bitmap
        if (isMagnifierLoupeActive && pdfBitmap != null && size.width > 10 && size.height > 10) {
            val loupeRadius = 78.dp.toPx()
            val loupeCenter = Offset(
                x = currentTouch.x.coerceIn(loupeRadius + 8f, size.width - loupeRadius - 8f),
                y = (currentTouch.y - loupeRadius * 1.35f).coerceAtLeast(loupeRadius + 12f)
            )

            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()

                val clipPath = android.graphics.Path().apply {
                    addCircle(loupeCenter.x, loupeCenter.y, loupeRadius, android.graphics.Path.Direction.CW)
                }
                native.clipPath(clipPath)
                native.drawColor(android.graphics.Color.WHITE)

                // Sample region around currentTouch in bitmap coordinates
                val bx = (currentTouch.x / size.width) * pdfBitmap.width
                val by = (currentTouch.y / size.height) * pdfBitmap.height
                val sampleHalfW = (pdfBitmap.width * 0.11f).toInt().coerceAtLeast(20)
                val sampleHalfH = (pdfBitmap.height * 0.11f).toInt().coerceAtLeast(20)

                val srcRect = android.graphics.Rect(
                    (bx.toInt() - sampleHalfW).coerceAtLeast(0),
                    (by.toInt() - sampleHalfH).coerceAtLeast(0),
                    (bx.toInt() + sampleHalfW).coerceAtMost(pdfBitmap.width),
                    (by.toInt() + sampleHalfH).coerceAtMost(pdfBitmap.height)
                )
                val dstRect = android.graphics.RectF(
                    loupeCenter.x - loupeRadius,
                    loupeCenter.y - loupeRadius,
                    loupeCenter.x + loupeRadius,
                    loupeCenter.y + loupeRadius
                )
                native.drawBitmap(pdfBitmap, srcRect, dstRect, null)
                native.restore()
            }

            // Loupe glass rim & crosshair
            drawCircle(
                color = Color(0xFF2563EB),
                radius = loupeRadius,
                center = loupeCenter,
                style = Stroke(width = 4.dp.toPx())
            )
            drawCircle(
                color = Color.White,
                radius = loupeRadius - 3.dp.toPx(),
                center = loupeCenter,
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawLine(
                color = Color(0xFFEF4444).copy(alpha = 0.45f),
                start = Offset(loupeCenter.x - 14.dp.toPx(), loupeCenter.y),
                end = Offset(loupeCenter.x + 14.dp.toPx(), loupeCenter.y),
                strokeWidth = 1.2.dp.toPx()
            )
            drawLine(
                color = Color(0xFFEF4444).copy(alpha = 0.45f),
                start = Offset(loupeCenter.x, loupeCenter.y - 14.dp.toPx()),
                end = Offset(loupeCenter.x, loupeCenter.y + 14.dp.toPx()),
                strokeWidth = 1.2.dp.toPx()
            )
        }

        // 2. Presenter Glowing Red Laser Pointer & Trail
        if (isLaserPointerActive) {
            if (laserTrail.size >= 2) {
                for (i in 1 until laserTrail.size) {
                    val alpha = (i.toFloat() / laserTrail.size.toFloat()) * 0.75f
                    drawLine(
                        color = Color(0xFFEF4444).copy(alpha = alpha),
                        start = laserTrail[i - 1],
                        end = laserTrail[i],
                        strokeWidth = (i.toFloat() / laserTrail.size.toFloat()) * 6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
            // Outer glow
            drawCircle(
                color = Color(0xFFEF4444).copy(alpha = 0.32f),
                radius = 20.dp.toPx(),
                center = currentTouch
            )
            // Mid glow
            drawCircle(
                color = Color(0xFFEF4444).copy(alpha = 0.75f),
                radius = 10.dp.toPx(),
                center = currentTouch
            )
            // Hot white core
            drawCircle(
                color = Color.White,
                radius = 4.5.dp.toPx(),
                center = currentTouch
            )
        }
    }
}
