package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.AppFile
import com.example.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FilesScreen(
    viewModel: MainViewModel,
    onNavigateToExtraction: () -> Unit,
    onOpenDeviceDocuments: () -> Unit = {}
) {
    val context = LocalContext.current
    val files by viewModel.allFiles.collectAsState()
    val theme by viewModel.currentTheme.collectAsState()
    val selectedIds by viewModel.selectedFileIdsForExtraction.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFileForPreview by remember { mutableStateOf<AppFile?>(null) }
    var showManualTextDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            viewModel.importFileFromUri(uri)
            Toast.makeText(context, "جاري استيراد واستخلاص الملف...", Toast.LENGTH_SHORT).show()
        }
    }

    val filteredFiles = remember(files, searchQuery) {
        if (searchQuery.isBlank()) files
        else files.filter { it.fileName.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Header Card with 3D Elevation
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(theme.elevationDp, RoundedCornerShape(theme.cornerRadiusDp), spotColor = theme.shadowColor),
            shape = RoundedCornerShape(theme.cornerRadiusDp),
            colors = CardDefaults.cardColors(containerColor = theme.surfaceColor)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.primaryColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White)
                        }
                        Column {
                            Text(
                                text = "إدارة الملفات والمصادر",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.textColor
                            )
                            Text(
                                text = "استيراد PDF و DOCX ونصوص للتحليل والاستخلاص",
                                fontSize = 11.sp,
                                color = theme.textColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // Quick access to Device Documents Manager
                Surface(
                    onClick = onOpenDeviceDocuments,
                    shape = RoundedCornerShape(12.dp),
                    color = theme.primaryColor.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, theme.primaryColor.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size(20.dp))
                            Text("تصفح مدير مستندات الهاتف (PDF, Word, Excel) 📂", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.primaryColor)
                        }
                        Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size(20.dp))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            filePickerLauncher.launch(
                                arrayOf(
                                    "application/pdf",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "application/msword",
                                    "application/epub+zip",
                                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "text/csv",
                                    "text/html",
                                    "application/rtf",
                                    "text/plain",
                                    "*/*"
                                )
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("رفع ملف", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { showManualTextDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("نص مباشر", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Selection Notification Bar (If files are selected for extraction)
        if (selectedIds.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = theme.primaryColor),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                        Text(
                            text = "تم تحديد ${selectedIds.size} ملف للاستخلاص",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onNavigateToExtraction,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("استخلاص الآن", color = theme.primaryColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Search and Select All row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("ابحث في أسماء الملفات...", color = theme.subtleTextColor) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = theme.primaryColor) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = theme.primaryColor,
                    unfocusedBorderColor = theme.borderColor,
                    unfocusedContainerColor = theme.elevatedCardColor,
                    focusedContainerColor = theme.elevatedCardColor,
                    focusedTextColor = theme.textColor,
                    unfocusedTextColor = theme.textColor
                )
            )

            if (files.isNotEmpty()) {
                TextButton(onClick = { viewModel.selectAllFilesForExtraction(files.map { it.id }) }) {
                    Text(
                        if (selectedIds.size == files.size) "إلغاء التحديد" else "تحديد الكل",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.primaryColor
                    )
                }
            }
        }

        // Files List or Rich Empty State
        if (filteredFiles.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .shadow(theme.elevationDp / 2, RoundedCornerShape(20.dp), spotColor = theme.shadowColor)
                    .border(1.dp, theme.borderColor, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = theme.elevatedCardColor)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(theme.primaryColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(38.dp),
                            tint = theme.primaryColor
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isBlank()) "لا توجد ملفات أو مصادر مرفوعة حالياً" else "لا توجد نتائج مطابقة لبحثك",
                        color = theme.textColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "يدعم التطبيق استيراد ملفات PDF، Word، EPUB، Excel، والعروض التقديمية لاستخلاص الأفكار منها.",
                        fontSize = 12.sp,
                        color = theme.subtleTextColor,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    // Format preview pills
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "PDF" to Color(0xFFDC2626),
                            "WORD" to Color(0xFF2563EB),
                            "EPUB" to Color(0xFF059669),
                            "EXCEL" to Color(0xFF16A34A),
                            "PPTX" to Color(0xFFEA580C)
                        ).forEach { (fmt, col) ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = col.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = fmt,
                                    color = col,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(filteredFiles, key = { it.id }) { file ->
                    val isSelected = selectedIds.contains(file.id)
                    FileItemCard(
                        file = file,
                        isSelected = isSelected,
                        theme = theme,
                        onToggleSelect = { viewModel.toggleFileSelectionForExtraction(file.id) },
                        onViewContent = { selectedFileForPreview = file },
                        onExtractTopic = {
                            viewModel.toggleFileSelectionForExtraction(file.id)
                            onNavigateToExtraction()
                        },
                        onDelete = { viewModel.deleteFile(file.id) }
                    )
                }
            }
        }
    }

    // Modal: Preview File Text
    selectedFileForPreview?.let { file ->
        FilePreviewDialog(
            file = file,
            theme = theme,
            onDismiss = { selectedFileForPreview = null }
        )
    }

    // Modal: Add Manual Text
    if (showManualTextDialog) {
        ManualTextDialog(
            theme = theme,
            onDismiss = { showManualTextDialog = false },
            onSave = { title, content ->
                viewModel.addManualTextFile(title, content)
                showManualTextDialog = false
            }
        )
    }
}

@Composable
fun FileItemCard(
    file: AppFile,
    isSelected: Boolean,
    theme: com.example.data.model.AppThemeData,
    onToggleSelect: () -> Unit,
    onViewContent: () -> Unit,
    onExtractTopic: () -> Unit,
    onDelete: () -> Unit
) {
    val extUpper = file.fileType.uppercase()
    val (badgeColor, formatIcon, formatLabel) = when (extUpper) {
        "PDF" -> Triple(Color(0xFFDC2626), Icons.Default.PictureAsPdf, "PDF")
        "DOCX", "DOC" -> Triple(Color(0xFF2563EB), Icons.Default.Description, "WORD")
        "EPUB" -> Triple(Color(0xFF059669), Icons.Default.MenuBook, "EPUB")
        "XLSX", "XLS", "CSV" -> Triple(Color(0xFF16A34A), Icons.Default.TableChart, extUpper)
        "PPTX", "PPT" -> Triple(Color(0xFFEA580C), Icons.Default.Slideshow, "PPTX")
        else -> Triple(Color(0xFF7C3AED), Icons.Default.Article, extUpper.ifBlank { "TXT" })
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewContent() }
            .shadow(
                if (isSelected) 6.dp else 2.dp,
                RoundedCornerShape(16.dp),
                spotColor = theme.shadowColor
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) theme.primaryColor else theme.borderColor,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = theme.elevatedCardColor)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                        colors = CheckboxDefaults.colors(checkedColor = theme.primaryColor)
                    )

                    // Colored Format Icon & Badge Box
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(badgeColor.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            formatIcon,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(badgeColor.copy(alpha = 0.16f))
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = formatLabel,
                                    color = badgeColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Text(
                                text = "${file.wordCount} كلمة • ${(file.fileSize / 1024).coerceAtLeast(1)} KB",
                                fontSize = 10.sp,
                                color = theme.subtleTextColor
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = file.fileName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Delete button with 48dp touch target
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "حذف",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Preview button
                    Surface(
                        onClick = onViewContent,
                        shape = RoundedCornerShape(10.dp),
                        color = theme.primaryColor.copy(alpha = 0.08f),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(15.dp), tint = theme.textColor)
                            Text("معاينة النص", fontSize = 11.sp, color = theme.textColor, fontWeight = FontWeight.Medium)
                        }
                    }

                    // Extract button
                    Surface(
                        onClick = onExtractTopic,
                        shape = RoundedCornerShape(10.dp),
                        color = theme.primaryColor.copy(alpha = 0.16f),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp), tint = theme.primaryColor)
                            Text("استخلاص ذكي", fontSize = 11.sp, color = theme.primaryColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilePreviewDialog(
    file: AppFile,
    theme: com.example.data.model.AppThemeData,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.fileName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "عدد الكلمات: ${file.wordCount} | النوع: ${file.fileType}",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }

                Divider(color = Color.Black.copy(alpha = 0.08f))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFFFDF8))
                        .border(1.dp, Color.Black.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    val scrollState = rememberScrollState()
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            text = if (file.extractedText.isNotBlank()) file.extractedText else "(لا يوجد نص مستخلص)",
                            fontSize = 13.sp,
                            lineHeight = 22.sp,
                            color = Color(0xFF2B2B2B),
                            modifier = Modifier.verticalScroll(scrollState)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(file.extractedText))
                            Toast.makeText(context, "تم نسخ النص إلى الحافظة!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("نسخ النص الكامل", fontSize = 12.sp)
                    }

                    OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                        Text("إغلاق", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ManualTextDialog(
    theme: com.example.data.model.AppThemeData,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "إضافة نص أو مرجع مباشر",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.textColor
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان المرجع أو المستند") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("محتوى النص أو المقال...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(10.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("إلغاء")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (content.isNotBlank()) {
                                onSave(title, content)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor),
                        shape = RoundedCornerShape(10.dp),
                        enabled = content.isNotBlank()
                    ) {
                        Text("حفظ المرجع", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
