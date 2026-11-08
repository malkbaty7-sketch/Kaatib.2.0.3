package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppThemeData
import com.example.util.ArabicTypoAndPunctuationHelper

/**
 * InEditorFindReplaceBar:
 * A sleek, non-intrusive floating bar at the top or bottom of the editor canvas.
 * Supports:
 * - Match counter (e.g. "3 من 12")
 * - Next / Previous match navigation
 * - Replace individual match / Replace all
 * - Arabic normalized search toggle (ignoring harakat and hamza differences)
 */
@Composable
fun InEditorFindReplaceBar(
    theme: AppThemeData,
    currentText: String,
    onNavigateToMatch: (start: Int, end: Int) -> Unit,
    onApplyTextUpdate: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var showReplaceRow by remember { mutableStateOf(false) }
    var ignoreHarakatAndHamza by remember { mutableStateOf(true) }
    var activeMatchIndex by remember { mutableIntStateOf(0) }

    // Compute matches
    val matchRanges = remember(searchQuery, currentText, ignoreHarakatAndHamza) {
        if (searchQuery.isBlank()) emptyList()
        else {
            try {
                if (!ignoreHarakatAndHamza) {
                    val regex = Regex(Regex.escape(searchQuery), RegexOption.IGNORE_CASE)
                    regex.findAll(currentText).map { it.range }.toList()
                } else {
                    // Match normalized Arabic or direct ignore-case
                    val normQuery = ArabicTypoAndPunctuationHelper.normalizeArabicForSearch(searchQuery)
                    val normText = ArabicTypoAndPunctuationHelper.normalizeArabicForSearch(currentText)
                    if (normQuery.isBlank()) emptyList()
                    else {
                        // Direct match fallback if identical length, or standard regex
                        val directRegex = Regex(Regex.escape(searchQuery), RegexOption.IGNORE_CASE)
                        val directMatches = directRegex.findAll(currentText).map { it.range }.toList()
                        if (directMatches.isNotEmpty()) {
                            directMatches
                        } else {
                            // Find normalized matches
                            val normRegex = Regex(Regex.escape(normQuery), RegexOption.IGNORE_CASE)
                            normRegex.findAll(normText).map { it.range }.toList()
                        }
                    }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    LaunchedEffect(matchRanges) {
        if (matchRanges.isNotEmpty()) {
            activeMatchIndex = 0
            val range = matchRanges[0]
            onNavigateToMatch(range.first, range.last + 1)
        } else {
            activeMatchIndex = 0
        }
    }

    fun jumpToNext() {
        if (matchRanges.isNotEmpty()) {
            val nextIdx = (activeMatchIndex + 1) % matchRanges.size
            activeMatchIndex = nextIdx
            val range = matchRanges[nextIdx]
            onNavigateToMatch(range.first, range.last + 1)
        }
    }

    fun jumpToPrev() {
        if (matchRanges.isNotEmpty()) {
            val prevIdx = if (activeMatchIndex <= 0) matchRanges.size - 1 else activeMatchIndex - 1
            activeMatchIndex = prevIdx
            val range = matchRanges[prevIdx]
            onNavigateToMatch(range.first, range.last + 1)
        }
    }

    fun replaceCurrent() {
        if (matchRanges.isNotEmpty() && activeMatchIndex in matchRanges.indices) {
            val range = matchRanges[activeMatchIndex]
            val updated = currentText.substring(0, range.first) + replaceQuery + currentText.substring(range.last + 1)
            onApplyTextUpdate(updated)
        }
    }

    fun replaceAll() {
        if (searchQuery.isNotBlank()) {
            val updated = currentText.replace(searchQuery, replaceQuery, ignoreCase = true)
            onApplyTextUpdate(updated)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFBF9F5),
        shadowElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.primaryColor.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Row 1: Search query input + match navigation + close
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size(18.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("بحث في الفصل...", fontSize = 12.sp) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )

                // Match counter badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (matchRanges.isNotEmpty()) theme.primaryColor.copy(alpha = 0.14f) else Color.LightGray.copy(alpha = 0.25f)
                ) {
                    Text(
                        text = if (matchRanges.isEmpty()) "0" else "${activeMatchIndex + 1} / ${matchRanges.size}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (matchRanges.isNotEmpty()) theme.primaryColor else Color.Gray,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }

                // Prev Match
                IconButton(
                    onClick = { jumpToPrev() },
                    enabled = matchRanges.isNotEmpty(),
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "السابق", modifier = Modifier.size(16.dp))
                }

                // Next Match
                IconButton(
                    onClick = { jumpToNext() },
                    enabled = matchRanges.isNotEmpty(),
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "التالي", modifier = Modifier.size(16.dp))
                }

                // Toggle Replace Row
                IconButton(
                    onClick = { showReplaceRow = !showReplaceRow },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.FindReplace,
                        contentDescription = "استبدال",
                        tint = if (showReplaceRow) theme.primaryColor else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Close button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
            }

            // Row 2: Replace fields and actions (if expanded)
            AnimatedVisibility(visible = showReplaceRow) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = replaceQuery,
                            onValueChange = { replaceQuery = it },
                            placeholder = { Text("استبدال بـ...", fontSize = 12.sp) },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            )
                        )

                        Button(
                            onClick = { replaceCurrent() },
                            enabled = matchRanges.isNotEmpty(),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("استبدال", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { replaceAll() },
                            enabled = matchRanges.isNotEmpty(),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("الكل", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Arabic Normalization Toggle Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = ignoreHarakatAndHamza,
                            onClick = { ignoreHarakatAndHamza = !ignoreHarakatAndHamza },
                            label = { Text("تجاهل التشكيل ونوع الهمزة (أ/إ/آ)", fontSize = 10.sp) },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }
        }
    }
}
