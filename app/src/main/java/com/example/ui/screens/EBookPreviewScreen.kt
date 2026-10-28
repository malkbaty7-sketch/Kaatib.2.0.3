package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppThemeData
import com.example.data.model.BookCoverTemplate
import com.example.data.model.ChapterItem
import com.example.data.model.PageImageItem
import com.example.data.model.PageImageSerializer
import com.example.data.model.Project
import com.example.ui.components.BookCoverView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class EBookReadingTheme(val label: String, val bg: Color, val text: Color, val accent: Color) {
    WHITE("ورق ناصع", Color(0xFFFFFFFF), Color(0xFF1E293B), Color(0xFF2563EB)),
    SEPIA("ورق عاجي دافئ", Color(0xFFFBF0D9), Color(0xFF382C18), Color(0xFF92400E)),
    DARK("وضع ليلي مريح", Color(0xFF18181B), Color(0xFFF4F4F5), Color(0xFF38BDF8))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EBookPreviewScreen(
    project: Project?,
    chapters: List<ChapterItem>,
    frontCover: BookCoverTemplate?,
    backCover: BookCoverTemplate?,
    theme: AppThemeData,
    onBackToEditor: () -> Unit
) {
    var readingTheme by remember { mutableStateOf(EBookReadingTheme.SEPIA) }
    var fontSizeSp by remember { mutableIntStateOf(17) }
    var lineSpacingFactor by remember { mutableFloatStateOf(1.8f) }

    // Pages list:
    // Page 0: Front Cover
    // Page 1: Book Info & Table of Contents
    // Pages 2..N: Chapter pages
    // Final Page: Back Cover
    val nonCoverChapters = remember(chapters) {
        chapters.filterNot { it.sectionType == "FRONT_COVER" || it.sectionType == "BACK_COVER" }
    }

    // Determine front cover template
    val effectiveFrontCover = remember(frontCover, project) {
        frontCover ?: BookCoverTemplate(
            id = "default_front",
            title = "غلاف الكتاب",
            category = "عام",
            backgroundType = if (!project?.coverImagePath.isNullOrBlank()) "IMAGE_URI" else "GRADIENT",
            backgroundColors = listOf("#1E3A8A", "#0F172A"),
            imageUri = project?.coverImagePath,
            bookTitle = project?.projectName ?: "عنوان الكتاب",
            subtitle = "كتاب إلكتروني متكامل",
            authorName = "مؤلف العمل",
            publisherName = "منشورات كاتب الرقمية"
        )
    }

    val effectiveBackCover = remember(backCover, project) {
        backCover ?: BookCoverTemplate(
            id = "default_back",
            title = "الغلاف الخلفي",
            category = "عام",
            backgroundType = "GRADIENT",
            backgroundColors = listOf("#1E3A8A", "#0F172A"),
            bookTitle = project?.projectName ?: "عنوان الكتاب",
            subtitle = "",
            authorName = "مؤلف العمل",
            backSynopsis = "نبذة خاتمة عن محتوى هذا العمل الإلكتروني وما اشتمل عليه من فصول ومباحث متكاملة أُعدت بعناية في تطبيق كاتب.",
            backAuthorBio = "عن الكاتب: كاتب ومؤلف رقمي."
        )
    }

    val totalPages = 1 + 1 + nonCoverChapters.size + 1 // FrontCover + Info/TOC + Chapters + BackCover
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val currentVisiblePage by remember {
        derivedStateOf { listState.firstVisibleItemIndex.coerceIn(0, totalPages - 1) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "معاينة الكتاب الإلكتروني المكتمل",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${project?.projectName ?: "الكتاب"} • صفحة ${currentVisiblePage + 1} من $totalPages",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                },
                navigationIcon = {
                    Button(
                        onClick = onBackToEditor,
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("عودة إلى صفحات الكتابة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    // Font size controls
                    IconButton(onClick = { if (fontSizeSp > 13) fontSizeSp -= 2 }) {
                        Icon(Icons.Default.TextDecrease, contentDescription = "تصغير الخط")
                    }
                    Text("${fontSizeSp}sp", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { if (fontSizeSp < 26) fontSizeSp += 2 }) {
                        Icon(Icons.Default.TextIncrease, contentDescription = "تكبير الخط")
                    }

                    // Reading themes
                    Row(
                        modifier = Modifier.padding(end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        EBookReadingTheme.values().forEach { t ->
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(t.bg)
                                    .border(
                                        width = if (readingTheme == t) 2.dp else 1.dp,
                                        color = if (readingTheme == t) theme.primaryColor else Color.Gray.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                                    .clickable { readingTheme = t }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = theme.surfaceColor)
            )
        },
        bottomBar = {
            Surface(
                color = theme.surfaceColor,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    // Vertical Reading Mode Indicator
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = theme.primaryColor.copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.SwapVert,
                                    contentDescription = null,
                                    tint = theme.primaryColor,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "تصفح رأسي بالسكرول (اسحب لأعلى ⬆ ولأسفل ⬇)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.primaryColor
                                )
                            }
                        }

                        Text(
                            text = "صفحة ${currentVisiblePage + 1} من $totalPages",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.primaryColor
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous page (scroll up)
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    if (currentVisiblePage > 0) {
                                        listState.animateScrollToItem(currentVisiblePage - 1)
                                    }
                                }
                            },
                            enabled = currentVisiblePage > 0,
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1.1f)
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "السابقة", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("السابقة ⬆", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // Jump chips
                        OutlinedButton(
                            onClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                            modifier = Modifier.weight(0.9f)
                        ) {
                            Text("الغلاف", fontSize = 10.sp)
                        }

                        OutlinedButton(
                            onClick = { coroutineScope.launch { listState.animateScrollToItem(1) } },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                            modifier = Modifier.weight(0.9f)
                        ) {
                            Text("الفهرس", fontSize = 10.sp)
                        }

                        OutlinedButton(
                            onClick = { coroutineScope.launch { listState.animateScrollToItem(totalPages - 1) } },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                            modifier = Modifier.weight(0.9f)
                        ) {
                            Text("الخاتمة", fontSize = 10.sp)
                        }

                        // Next page (scroll down)
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    if (currentVisiblePage < totalPages - 1) {
                                        listState.animateScrollToItem(currentVisiblePage + 1)
                                    }
                                }
                            },
                            enabled = currentVisiblePage < totalPages - 1,
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1.1f)
                        ) {
                            Text("التالية ⬇", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "التالية", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFE2E8F0)),
            contentPadding = PaddingValues(vertical = 16.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Item 0: Front Cover
            item {
                Card(
                    modifier = Modifier
                        .widthIn(max = 580.dp)
                        .fillMaxWidth(0.96f)
                        .height(600.dp)
                        .shadow(8.dp, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = readingTheme.bg)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BookCoverView(
                            template = effectiveFrontCover,
                            isBackCover = false,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Item 1: TOC and Metadata
            item {
                Card(
                    modifier = Modifier
                        .widthIn(max = 580.dp)
                        .fillMaxWidth(0.96f)
                        .shadow(8.dp, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = readingTheme.bg)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = project?.projectName ?: "عنوان الكتاب",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = readingTheme.text,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "تأليف: ${effectiveFrontCover.authorName}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = readingTheme.accent,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        val dateStr = SimpleDateFormat("dd MMMM yyyy", Locale("ar")).format(Date(project?.createdDate ?: System.currentTimeMillis()))
                        Text(
                            text = "تاريخ الإعداد: $dateStr • عدد الفصول: ${nonCoverChapters.size}",
                            fontSize = 11.sp,
                            color = readingTheme.text.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Divider(color = readingTheme.text.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 8.dp))

                        Text(
                            text = "فهرس محتويات الكتاب (انقر للانتقال المباشر)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = readingTheme.accent
                        )

                        nonCoverChapters.forEachIndexed { idx, ch ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(idx + 2)
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${idx + 1}. ${ch.title}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = readingTheme.text
                                )
                                Text(
                                    text = "ص ${idx + 3}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = readingTheme.accent
                                )
                            }
                        }
                    }
                }
            }

            // Chapters 2..N
            itemsIndexed(nonCoverChapters) { idx, chapter ->
                val chapterImages = remember(chapter.imagesJson) {
                    PageImageSerializer.fromJson(chapter.imagesJson)
                }
                Card(
                    modifier = Modifier
                        .widthIn(max = 580.dp)
                        .fillMaxWidth(0.96f)
                        .shadow(8.dp, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = readingTheme.bg)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 22.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = project?.projectName ?: "",
                                fontSize = 10.sp,
                                color = readingTheme.text.copy(alpha = 0.5f)
                            )
                            Text(
                                text = chapter.title.ifBlank { "الفصل ${idx + 1}" },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = readingTheme.accent
                            )
                        }

                        Divider(color = readingTheme.text.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 6.dp))

                        Text(
                            text = chapter.title.ifBlank { "الفصل ${idx + 1}" },
                            fontSize = (fontSizeSp + 4).sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = readingTheme.accent,
                            modifier = Modifier.padding(top = 10.dp, bottom = 10.dp)
                        )

                        // Render any embedded page images
                        if (chapterImages.isNotEmpty()) {
                            chapterImages.forEach { img ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(img.heightDp.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    coil.compose.AsyncImage(
                                        model = img.sourceUri,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                }
                            }
                        }

                        Text(
                            text = chapter.content.ifBlank { "لم يتم كتابة نص لهذا الفصل بعد..." },
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp * lineSpacingFactor).sp,
                            color = readingTheme.text,
                            textAlign = TextAlign.Right
                        )

                        // Footer: Page Number
                        Spacer(modifier = Modifier.height(16.dp))
                        Divider(color = readingTheme.text.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 6.dp))
                        Text(
                            text = "صفحة ${idx + 3}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = readingTheme.text.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Final Page: Back Cover
            item {
                Card(
                    modifier = Modifier
                        .widthIn(max = 580.dp)
                        .fillMaxWidth(0.96f)
                        .height(600.dp)
                        .shadow(8.dp, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = readingTheme.bg)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BookCoverView(
                            template = effectiveBackCover,
                            isBackCover = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
