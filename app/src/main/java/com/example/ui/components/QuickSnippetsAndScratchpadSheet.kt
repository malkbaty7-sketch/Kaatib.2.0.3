package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppThemeData

/**
 * QuickSnippetsAndScratchpadSheet:
 * Modal Bottom Sheet or Side Panel providing:
 * 1. Quick narrative snippets (ornamental separators, scene transition headers, common literary formulas).
 * 2. Instant floating scratchpad for capturing side ideas, dialogue snippets, and character notes without polluting the chapter text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSnippetsAndScratchpadSheet(
    theme: AppThemeData,
    initialScratchpadText: String,
    onSaveScratchpad: (String) -> Unit,
    onInsertSnippet: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var activeTab by remember { mutableStateOf(0) } // 0: Snippets, 1: Scratchpad
    var scratchpadContent by remember { mutableStateOf(initialScratchpadText) }
    var customSnippetInput by remember { mutableStateOf("") }
    var savedCustomSnippets by remember { mutableStateOf(listOf<String>()) }

    val defaultSnippets = listOf(
        "❖ ❖ ❖" to "فاصل زخرفي تراثي كلاسيكي",
        "* * *" to "فاصل انتقال المشهد الزمني",
        "❦ ❦ ❦" to "فاصل زهرة لوتس أدبية",
        "— قال بصوت خافت:\n" to "قالب حوار أدبي سريع",
        "╔══ 💡 تنويه وإضاءة فكرية ══╗\n\n╚═════════════════════════╝" to "صندوق فكرة بارزة",
        "« »" to "أقواس اقتباس تراثية",
        "— وفي اللحظة ذاتها، " to "بداية مشهد درامي",
        "يتذكر الكاتب في هذا السياق: " to "هامش واسترجاع",
        "• • •" to "فاصل نقاط أدبية ناعمة"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxHeight(0.75f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header + Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.BookmarkBorder, contentDescription = null, tint = theme.primaryColor)
                    Text("درج القصاصات والمسودة السريعة 📋", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = theme.textColor)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق")
                }
            }

            TabRow(
                selectedTabIndex = activeTab,
                containerColor = Color(0xFFF3F4F6),
                indicator = {},
                divider = {}
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("قوالب وقصاصات سريعة 🧩", fontSize = 12.sp, fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("مسودة الأفكار الخاطفة 📝", fontSize = 12.sp, fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Normal) }
                )
            }

            if (activeTab == 0) {
                // Snippets list
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text("انقر على أي فاصل أو قالب لإدراجه فوراً في موضع الكتابة الحالي:", fontSize = 11.sp, color = Color.Gray)
                    }

                    items(defaultSnippets) { (snippet, label) ->
                        Surface(
                            onClick = {
                                onInsertSnippet(snippet)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFBF9F5),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.textColor)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(snippet.trim(), fontSize = 11.sp, color = theme.primaryColor, maxLines = 1)
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = theme.primaryColor.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        "إدراج 📥",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = theme.primaryColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (savedCustomSnippets.isNotEmpty()) {
                        item {
                            Text("القصاصات المخصصة المضافة:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.primaryColor)
                        }
                        items(savedCustomSnippets) { customSnip ->
                            Surface(
                                onClick = {
                                    onInsertSnippet(customSnip)
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFEFF6FF),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(customSnip, fontSize = 12.sp, color = Color(0xFF1E3A8A), modifier = Modifier.weight(1f))
                                    Text("إدراج", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = customSnippetInput,
                                onValueChange = { customSnippetInput = it },
                                placeholder = { Text("أضف عبارة متكررة خاصة بك...", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(8.dp)
                            )
                            Button(
                                onClick = {
                                    if (customSnippetInput.isNotBlank()) {
                                        savedCustomSnippets = savedCustomSnippets + customSnippetInput.trim()
                                        customSnippetInput = ""
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Text("حفظ", fontSize = 11.sp)
                            }
                        }
                    }
                }
            } else {
                // Scratchpad editor
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "هذه المسودة ستبقى محفوظة جانباً لتدوين الخواطر والحوارات السريعة دون تلويث نص الفصل الأصلي:",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )

                    OutlinedTextField(
                        value = scratchpadContent,
                        onValueChange = {
                            scratchpadContent = it
                            onSaveScratchpad(it)
                        },
                        placeholder = { Text("دوّن فكرة مفاجئة، مساراً بديلاً، أو ملاحظة لشخصية...", fontSize = 12.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFFFFDF5),
                            unfocusedContainerColor = Color(0xFFFFFDF5)
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${scratchpadContent.length} حرف مسجّل",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (scratchpadContent.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        onInsertSnippet("\n$scratchpadContent\n")
                                        onDismiss()
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("نقل النص للفصل 📥", fontSize = 11.sp)
                                }
                            }
                            Button(
                                onClick = {
                                    onSaveScratchpad(scratchpadContent)
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("تم وحفظ المسودة ✓", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
