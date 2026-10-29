package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppThemeData

@Composable
fun RichWritingToolbar(
    theme: AppThemeData,
    fontSizeSp: Int,
    onFontSizeChange: (Int) -> Unit,
    textAlign: androidx.compose.ui.text.style.TextAlign,
    onTextAlignChange: (androidx.compose.ui.text.style.TextAlign) -> Unit,
    lineSpacingFactor: Float,
    onLineSpacingChange: (Float) -> Unit,
    textColor: Color,
    onTextColorChange: (Color) -> Unit,
    highlightColor: Color?,
    onHighlightColorChange: (Color?) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onUndoToSaveCheckpoint: (() -> Unit)? = null,
    undoCount: Int = 0,
    redoCount: Int = 0,
    onInsertText: (prefix: String, suffix: String, defaultPlaceholder: String) -> Unit,
    onAddImageClick: () -> Unit,
    onSearchReplaceClick: () -> Unit,
    onVoiceTypingClick: () -> Unit,
    content: String,
    imagesCount: Int = 0
) {
    var showColorPalette by remember { mutableStateOf(false) }
    var showHighlightPalette by remember { mutableStateOf(false) }

    val wordsCount = remember(content) {
        if (content.isBlank()) 0 else content.trim().split("\\s+".toRegex()).size
    }
    val charsCount = remember(content) { content.length }

    val presetColors = listOf(
        Color(0xFF222222) to "أسود داكن",
        Color(0xFF4A3728) to "بني كلاسيكي",
        Color(0xFF1E3A8A) to "أزرق نيلي",
        Color(0xFF065F46) to "أخضر زمردي",
        Color(0xFF881337) to "أحمر قاني",
        Color(0xFF581C87) to "بنفسجي ملكي"
    )

    val presetHighlights = listOf(
        null to "بدون",
        Color(0xFFFEF08A) to "أصفر",
        Color(0xFFA7F3D0) to "أخضر",
        Color(0xFFBAE6FD) to "سماوي",
        Color(0xFFFECDD3) to "وردي",
        Color(0xFFFED7AA) to "برتقالي"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF8F6F1),
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.primaryColor.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Row 1: Compact Primary Tools (Add Image, Undo/Redo, Font Size, Alignment, Color, Search, Mic, Live Stats)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // Add Image Action Button (Compact)
                Button(
                    onClick = onAddImageClick,
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("صورة", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    if (imagesCount > 0) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("($imagesCount)", fontSize = 9.sp)
                    }
                }

                VerticalDivider()

                // 1. Undo with long-press checkpoint support
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (canUndo) theme.primaryColor.copy(alpha = 0.08f) else Color.Transparent)
                        .border(
                            width = 1.dp,
                            color = if (canUndo) theme.primaryColor.copy(alpha = 0.35f) else Color.LightGray.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(5.dp)
                        )
                        .pointerInput(canUndo, onUndoToSaveCheckpoint) {
                            if (canUndo) {
                                detectTapGestures(
                                    onTap = { onUndo() },
                                    onLongPress = {
                                        if (onUndoToSaveCheckpoint != null) {
                                            onUndoToSaveCheckpoint()
                                        } else {
                                            onUndo()
                                        }
                                    }
                                )
                            }
                        }
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "تراجع",
                            modifier = Modifier.size(13.dp),
                            tint = if (canUndo) theme.primaryColor else Color.LightGray
                        )
                        if (undoCount > 0) {
                            Text(
                                text = "$undoCount",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (canUndo) theme.primaryColor else Color.LightGray
                            )
                        }
                    }
                }

                // 2. Redo
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (canRedo) theme.primaryColor.copy(alpha = 0.08f) else Color.Transparent)
                        .border(
                            width = 1.dp,
                            color = if (canRedo) theme.primaryColor.copy(alpha = 0.35f) else Color.LightGray.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(5.dp)
                        )
                        .pointerInput(canRedo) {
                            if (canRedo) {
                                detectTapGestures(
                                    onTap = { onRedo() },
                                    onLongPress = { onRedo() }
                                )
                            }
                        }
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "إعادة",
                            modifier = Modifier.size(13.dp),
                            tint = if (canRedo) theme.primaryColor else Color.LightGray
                        )
                        if (redoCount > 0) {
                            Text(
                                text = "$redoCount",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (canRedo) theme.primaryColor else Color.LightGray
                            )
                        }
                    }
                }

                VerticalDivider()

                // Font Size Decrement
                IconButton(
                    onClick = { onFontSizeChange((fontSizeSp - 2).coerceAtLeast(12)) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Text("A-", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "${fontSizeSp}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.primaryColor,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }

                // Font Size Increment
                IconButton(
                    onClick = { onFontSizeChange((fontSizeSp + 2).coerceAtMost(32)) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Text("A+", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                VerticalDivider()

                // Text Alignments
                IconButton(
                    onClick = { onTextAlignChange(androidx.compose.ui.text.style.TextAlign.Right) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.FormatAlignRight,
                        contentDescription = "محاذاة يمين",
                        modifier = Modifier.size(14.dp),
                        tint = if (textAlign == androidx.compose.ui.text.style.TextAlign.Right) theme.primaryColor else Color.Gray
                    )
                }

                IconButton(
                    onClick = { onTextAlignChange(androidx.compose.ui.text.style.TextAlign.Center) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.FormatAlignCenter,
                        contentDescription = "توسيط",
                        modifier = Modifier.size(14.dp),
                        tint = if (textAlign == androidx.compose.ui.text.style.TextAlign.Center) theme.primaryColor else Color.Gray
                    )
                }

                IconButton(
                    onClick = { onTextAlignChange(androidx.compose.ui.text.style.TextAlign.Left) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.FormatAlignLeft,
                        contentDescription = "محاذاة يسار",
                        modifier = Modifier.size(14.dp),
                        tint = if (textAlign == androidx.compose.ui.text.style.TextAlign.Left) theme.primaryColor else Color.Gray
                    )
                }

                IconButton(
                    onClick = { onTextAlignChange(androidx.compose.ui.text.style.TextAlign.Justify) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.FormatAlignJustify,
                        contentDescription = "ضبط كامل",
                        modifier = Modifier.size(14.dp),
                        tint = if (textAlign == androidx.compose.ui.text.style.TextAlign.Justify) theme.primaryColor else Color.Gray
                    )
                }

                VerticalDivider()

                // Line Spacing Toggle
                Surface(
                    onClick = {
                        val next = when (lineSpacingFactor) {
                            1.2f -> 1.5f
                            1.5f -> 1.8f
                            1.8f -> 2.2f
                            else -> 1.2f
                        }
                        onLineSpacingChange(next)
                    },
                    shape = RoundedCornerShape(4.dp),
                    color = Color.White,
                    modifier = Modifier.height(22.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FormatLineSpacing, contentDescription = null, modifier = Modifier.size(12.dp), tint = theme.primaryColor)
                        Text("${lineSpacingFactor}x", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.primaryColor)
                    }
                }

                // Color Palette Trigger
                IconButton(
                    onClick = { showColorPalette = !showColorPalette },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.FormatColorText, contentDescription = "لون الخط", modifier = Modifier.size(14.dp), tint = textColor)
                }

                // Highlight Color Trigger
                IconButton(
                    onClick = { showHighlightPalette = !showHighlightPalette },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.BorderColor,
                        contentDescription = "تمييز النص",
                        modifier = Modifier.size(14.dp),
                        tint = highlightColor ?: Color.Gray
                    )
                }

                // Search & Replace
                IconButton(
                    onClick = onSearchReplaceClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.FindReplace, contentDescription = "بحث واستبدال", modifier = Modifier.size(14.dp), tint = Color.DarkGray)
                }

                // Voice Typing
                IconButton(
                    onClick = onVoiceTypingClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "كتابة صوتية", modifier = Modifier.size(14.dp), tint = theme.primaryColor)
                }
            }

            // Row 2: Compact Formatting Styles + Inline Live Word/Char Counter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Surface(
                    onClick = { onInsertText("# ", "\n", "عنوان رئيسي") },
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Transparent,
                    modifier = Modifier.height(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
                        Text("H1", fontWeight = FontWeight.Black, fontSize = 11.sp, color = theme.primaryColor)
                    }
                }

                Surface(
                    onClick = { onInsertText("## ", "\n", "عنوان فرعي") },
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Transparent,
                    modifier = Modifier.height(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
                        Text("H2", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = theme.primaryColor)
                    }
                }

                Surface(
                    onClick = { onInsertText("### ", "\n", "عنوان جزئي") },
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Transparent,
                    modifier = Modifier.height(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
                        Text("H3", fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = theme.primaryColor)
                    }
                }

                VerticalDivider()

                IconButton(
                    onClick = { onInsertText("**", "**", "نص عريض") },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.FormatBold, contentDescription = "عريض", modifier = Modifier.size(14.dp))
                }

                IconButton(
                    onClick = { onInsertText("*", "*", "نص مائل") },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.FormatItalic, contentDescription = "مائل", modifier = Modifier.size(14.dp))
                }

                IconButton(
                    onClick = { onInsertText("<u>", "</u>", "نص مسطر") },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.FormatUnderlined, contentDescription = "تسطير", modifier = Modifier.size(14.dp))
                }

                IconButton(
                    onClick = { onInsertText("~~", "~~", "نص مشطوب") },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.FormatStrikethrough, contentDescription = "شطب", modifier = Modifier.size(14.dp))
                }

                VerticalDivider()

                IconButton(
                    onClick = { onInsertText("\n• ", "", "عنصر قائمة") },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "قائمة نقطية", modifier = Modifier.size(14.dp))
                }

                IconButton(
                    onClick = { onInsertText("\n1. ", "", "عنصر مرقم") },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.FormatListNumbered, contentDescription = "قائمة مرقمة", modifier = Modifier.size(14.dp))
                }

                IconButton(
                    onClick = { onInsertText("\n[ ] ", "", "مهمة مطلوب إنجازها") },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.CheckBoxOutlineBlank, contentDescription = "قائمة مهام", modifier = Modifier.size(14.dp))
                }

                VerticalDivider()

                IconButton(
                    onClick = { onInsertText("«", "»", "اقتباس أدبي مؤثر") },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.FormatQuote, contentDescription = "اقتباس", modifier = Modifier.size(14.dp))
                }

                IconButton(
                    onClick = { onInsertText("\n╔══ 💡 تنويه وإضاءة فكرية ══╗\n", "\n╚═════════════════════════╝\n", "اكتب الملاحظة هنا...") },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Lightbulb, contentDescription = "صندوق ملاحظة", modifier = Modifier.size(14.dp), tint = Color(0xFFD97706))
                }

                IconButton(
                    onClick = { onInsertText("\n\n❖ ❖ ❖\n\n", "", "") },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.HorizontalRule, contentDescription = "فاصل زخرفي", modifier = Modifier.size(14.dp))
                }

                VerticalDivider()

                // Inline compact word/char badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = theme.primaryColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "$wordsCount كلمة • $charsCount حرف",
                        fontSize = 9.sp,
                        color = theme.primaryColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            // Optional Sub-palette: Color Swatches
            if (showColorPalette) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("لون النص:", fontSize = 9.sp, color = Color.Gray)
                    presetColors.forEach { (color, _) ->
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (textColor == color) 2.dp else 1.dp,
                                    color = if (textColor == color) theme.primaryColor else Color.LightGray,
                                    shape = CircleShape
                                )
                                .clickable {
                                    onTextColorChange(color)
                                    showColorPalette = false
                                }
                        )
                    }
                }
            }

            // Optional Sub-palette: Highlight Swatches
            if (showHighlightPalette) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("التظليل:", fontSize = 9.sp, color = Color.Gray)
                    presetHighlights.forEach { (color, _) ->
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(color ?: Color.White)
                                .border(
                                    width = if (highlightColor == color) 2.dp else 1.dp,
                                    color = if (highlightColor == color) theme.primaryColor else Color.LightGray,
                                    shape = CircleShape
                                )
                                .clickable {
                                    onHighlightColorChange(color)
                                    showHighlightPalette = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (color == null) {
                                Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(10.dp), tint = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(14.dp)
            .background(Color.Black.copy(alpha = 0.12f))
    )
}
