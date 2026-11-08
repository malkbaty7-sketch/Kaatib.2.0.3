package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppThemeData

/**
 * SmartKeyboardAccessoryBar:
 * Floats immediately above the soft keyboard (imePadding) or sticks above the paper,
 * offering instant Arabic punctuation, navigation cursors, snippets, and quick formatting.
 */
@Composable
fun SmartKeyboardAccessoryBar(
    theme: AppThemeData,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onInsertPunctuation: (String) -> Unit,
    onInsertEnclosing: (prefix: String, suffix: String) -> Unit,
    onMoveCursorLeft: () -> Unit,
    onMoveCursorRight: () -> Unit,
    onJumpLineStart: () -> Unit,
    onJumpLineEnd: () -> Unit,
    onToggleSearch: () -> Unit,
    onToggleSnippets: () -> Unit,
    onApplyAutoPunctuationFix: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        color = Color(0xFFF9F7F2),
        shadowElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(0.8.dp, theme.primaryColor.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Undo / Redo micro buttons
            Surface(
                onClick = onUndo,
                enabled = canUndo,
                shape = RoundedCornerShape(6.dp),
                color = if (canUndo) theme.primaryColor.copy(alpha = 0.12f) else Color.Transparent,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "تراجع",
                        tint = if (canUndo) theme.primaryColor else Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Surface(
                onClick = onRedo,
                enabled = canRedo,
                shape = RoundedCornerShape(6.dp),
                color = if (canRedo) theme.primaryColor.copy(alpha = 0.12f) else Color.Transparent,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "إعادة",
                        tint = if (canRedo) theme.primaryColor else Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            VerticalDivider(modifier = Modifier.height(20.dp), color = Color.LightGray)

            // Cursor movement keys
            Surface(
                onClick = onJumpLineEnd,
                shape = RoundedCornerShape(6.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.LightGray.copy(alpha = 0.6f)),
                modifier = Modifier.height(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                    Text("نهاية ⇥", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.textColor)
                }
            }

            Surface(
                onClick = onMoveCursorLeft,
                shape = RoundedCornerShape(6.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.LightGray.copy(alpha = 0.6f)),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "يسار", modifier = Modifier.size(15.dp), tint = theme.primaryColor)
                }
            }

            Surface(
                onClick = onMoveCursorRight,
                shape = RoundedCornerShape(6.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.LightGray.copy(alpha = 0.6f)),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "يمين", modifier = Modifier.size(15.dp), tint = theme.primaryColor)
                }
            }

            Surface(
                onClick = onJumpLineStart,
                shape = RoundedCornerShape(6.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.LightGray.copy(alpha = 0.6f)),
                modifier = Modifier.height(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                    Text("⇤ بداية", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.textColor)
                }
            }

            VerticalDivider(modifier = Modifier.height(20.dp), color = Color.LightGray)

            // Arabic Quotes & Enclosures
            PunctuationKey(label = "« »", sub = "اقتباس") { onInsertEnclosing("«", "»") }
            PunctuationKey(label = "“ ”", sub = "تنصيص") { onInsertEnclosing("“", "”") }
            PunctuationKey(label = "( )", sub = "قوسان") { onInsertEnclosing("(", ")") }

            VerticalDivider(modifier = Modifier.height(20.dp), color = Color.LightGray)

            // Essential Arabic Punctuations
            PunctuationKey(label = "،", sub = "فاصلة") { onInsertPunctuation("، ") }
            PunctuationKey(label = "؛", sub = "منقوطة") { onInsertPunctuation("؛ ") }
            PunctuationKey(label = "؟", sub = "استفهام") { onInsertPunctuation("؟ ") }
            PunctuationKey(label = "!", sub = "تعجب") { onInsertPunctuation("! ") }
            PunctuationKey(label = "—", sub = "حوار") { onInsertPunctuation("— ") }
            PunctuationKey(label = "…", sub = "تردد") { onInsertPunctuation("… ") }
            PunctuationKey(label = "ـ", sub = "تطويل") { onInsertPunctuation("ـ") }

            VerticalDivider(modifier = Modifier.height(20.dp), color = Color.LightGray)

            // Quick Auto-Fix Arabic Punctuation & Typo
            Surface(
                onClick = onApplyAutoPunctuationFix,
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF10B981).copy(alpha = 0.14f),
                border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                modifier = Modifier.height(32.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(13.dp))
                    Text("ضبط الترقيم ✨", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                }
            }

            // Quick Search Toggle
            Surface(
                onClick = onToggleSearch,
                shape = RoundedCornerShape(8.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.LightGray.copy(alpha = 0.6f)),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Search, contentDescription = "بحث", tint = theme.primaryColor, modifier = Modifier.size(16.dp))
                }
            }

            // Quick Snippets / Scratchpad Drawer Toggle
            Surface(
                onClick = onToggleSnippets,
                shape = RoundedCornerShape(8.dp),
                color = theme.primaryColor.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(0.8.dp, theme.primaryColor.copy(alpha = 0.4f)),
                modifier = Modifier.height(32.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.BookmarkBorder, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size(14.dp))
                    Text("القصاصات 📋", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.primaryColor)
                }
            }
        }
    }
}

@Composable
private fun PunctuationKey(
    label: String,
    sub: String? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFD1D5DB)),
        modifier = Modifier.height(32.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F2937)
            )
        }
    }
}
