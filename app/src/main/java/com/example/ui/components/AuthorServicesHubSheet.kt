package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppThemeData

data class AuthorServiceItem(
    val id: String,
    val title: String,
    val description: String,
    val category: String, // DESIGN, QUALITY, NARRATIVE, PUBLISHING
    val icon: ImageVector,
    val accentColor: Color,
    val badge: String? = null
)

/**
 * AuthorServicesHubSheet:
 * Modern categorized visual hub replacing overcrowded vertical dropdowns.
 * Categorized into 4 intuitive domains:
 * 1. 🎨 استوديو التصميم والإخراج (Design & Layout Studio)
 * 2. 🔍 الجودة والتدقيق اللغوي (Proofreading & Quality Control)
 * 3. 🧠 الهندسة السردية والتخطيط (Narrative & Lore Engine)
 * 4. 📊 الإنتاجية والنشر (Publishing & Analytics)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorServicesHubSheet(
    theme: AppThemeData,
    onDismiss: () -> Unit,
    onSelectService: (serviceId: String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("ALL") }

    val allServices = listOf(
        // Category 1: Design & Styling
        AuthorServiceItem("COVERS_STUDIO", "استوديو أغلفة الكتب", "تصميم أغلفة أمامية وخلفية احترافية", "DESIGN", Icons.Default.PhotoLibrary, Color(0xFFD97706), "VIP"),
        AuthorServiceItem("PAPER_SETTINGS", "تخصيص الورقة والصفحة", "أحجام وهوامش ونمط الأوراق والترقيم", "DESIGN", Icons.Default.Palette, Color(0xFFB45309)),
        AuthorServiceItem("FONT_PICKER", "إدارة وتثبيت الخطوط", "تخصيص خطوط الطباعة وتنزيل الخطوط العربية", "DESIGN", Icons.Default.FontDownload, Color(0xFF2563EB)),
        AuthorServiceItem("AI_IMAGE_GEN", "توليد الصور بالذكاء", "تحويل المشاهد السردية إلى لوحات بصرية", "DESIGN", Icons.Default.AutoAwesome, Color(0xFF9333EA), "AI"),
        AuthorServiceItem("UNSPLASH_IMAGES", "مكتبة الصور السحابية", "البحث وإدراج صور فوتوغرافية عالمية", "DESIGN", Icons.Default.ImageSearch, Color(0xFF0D9488)),

        // Category 2: Quality & Proofreading
        AuthorServiceItem("PROOFREADING", "التدقيق اللغوي الشامل", "فحص الأخطاء النحوية والإملائية والأسلوبية", "QUALITY", Icons.Default.Spellcheck, Color(0xFF16A34A), "مُحدّث"),
        AuthorServiceItem("AUDIO_TTS", "التدقيق السمعي والقارئ", "الاستماع الصوتي مع تظليل النص المقروء", "QUALITY", Icons.Default.RecordVoiceOver, Color(0xFF2563EB), "صوتي"),
        AuthorServiceItem("PREFLIGHT_CHECK", "فحص الجاهزية للنشر", "تدقيق معايير النشر وسلامة الفهارس والملفات", "QUALITY", Icons.Default.Verified, Color(0xFF059669), "هام"),
        AuthorServiceItem("GRAMMAR_REFINE", "تنقيح وصقل العبارات", "تحسين البلاغة وانسيابية الأسلوب العربي", "QUALITY", Icons.Default.ModeEdit, Color(0xFFEA580C)),
        AuthorServiceItem("BETA_READING", "ملاحظات القراءة التجريبية", "تفاعل واستعراض تعليقات المراجعين والقراء", "QUALITY", Icons.Default.RateReview, Color(0xFF4F46E5)),

        // Category 3: Narrative & Lore
        AuthorServiceItem("CORKBOARD", "لوحة التخطيط المرئي", "تنظيم بطاقات الفصول بالسحب والإفلات", "NARRATIVE", Icons.Default.DashboardCustomize, Color(0xFFD97706)),
        AuthorServiceItem("STORYBOARD_ARC", "مخطط التوتر والدراما", "رسم منحنى الصراع وتصاعد الأحداث", "NARRATIVE", Icons.Default.ShowChart, Color(0xFFDC2626)),
        AuthorServiceItem("PLOT_BRANCHES", "تفرع مسارات الحبكة", "استكشاف سيناريوهات سردية بديلة بالذكاء", "NARRATIVE", Icons.Default.AltRoute, Color(0xFF7C3AED), "AI"),
        AuthorServiceItem("SCENE_BREAKDOWN", "تفكيك المشاهد السينمائية", "تحليل الزمان والمكان وشخصيات كل مشهد", "NARRATIVE", Icons.Default.AccountTree, Color(0xFF0284C7)),
        AuthorServiceItem("SPLIT_READER", "قارئ المصادر المرجعية", "شاشة منقسمة للرجوع للمراجع أثناء التحرير", "NARRATIVE", Icons.Default.VerticalSplit, Color(0xFF059669)),

        // Category 4: Publishing & Productivity
        AuthorServiceItem("ANALYTICS", "إحصائيات وتحليلات الأداء", "معدل الكلمات، سرعة الكتابة، وساعات الإنجاز", "PUBLISHING", Icons.Default.Insights, Color(0xFF0D9488)),
        AuthorServiceItem("SNAPSHOTS_HISTORY", "سجل اللقطات الاحتياطية", "استرجاع مسودات العمل مع مقارنة الفروق", "PUBLISHING", Icons.Default.History, Color(0xFF2563EB)),
        AuthorServiceItem("WORD_GOALS", "أهداف عدد الكلمات", "تحديد خطة الإنجاز اليومية ومتابعة التقدم", "PUBLISHING", Icons.Default.Flag, Color(0xFFF59E0B)),
        AuthorServiceItem("SHARE_PROJECT", "مشاركة وتصدير المشروع", "مشاركة المستند ونشر الكتاب بالصيغ المتاحة", "PUBLISHING", Icons.Default.Share, Color(0xFF10B981)),
        AuthorServiceItem("VIP_UPGRADE", "ترقية الكاتب المميز VIP", "فتح جميع ميزات النشر السحابي المتقدم", "PUBLISHING", Icons.Default.WorkspacePremium, Color(0xFFD97706), "VIP")
    )

    val filteredServices = remember(selectedCategory) {
        if (selectedCategory == "ALL") allServices else allServices.filter { it.category == selectedCategory }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFFCFBFA),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxHeight(0.85f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = theme.primaryColor.copy(alpha = 0.12f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Widgets, contentDescription = null, tint = theme.primaryColor, modifier = Modifier.size(20.dp))
                        }
                    }
                    Column {
                        Text("مركز خدمات واستوديوهات الكاتب 🌟", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = theme.textColor)
                        Text("مجموعة الأدوات الشاملة لإخراج ونشر وتدقيق العمل الأدبي", fontSize = 11.sp, color = Color.Gray)
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق")
                }
            }

            // Category Filter Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CategoryPill("ALL", "الكل (${allServices.size})", selectedCategory == "ALL", theme) { selectedCategory = "ALL" }
                CategoryPill("QUALITY", "🔍 التدقيق والجودة", selectedCategory == "QUALITY", theme) { selectedCategory = "QUALITY" }
                CategoryPill("DESIGN", "🎨 التصميم والإخراج", selectedCategory == "DESIGN", theme) { selectedCategory = "DESIGN" }
                CategoryPill("NARRATIVE", "🧠 الحبكة والسرد", selectedCategory == "NARRATIVE", theme) { selectedCategory = "NARRATIVE" }
                CategoryPill("PUBLISHING", "📊 النشر والإنتاج", selectedCategory == "PUBLISHING", theme) { selectedCategory = "PUBLISHING" }
            }

            // Services Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredServices) { service ->
                    Surface(
                        onClick = {
                            onDismiss()
                            onSelectService(service.id)
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        shadowElevation = 2.dp,
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, service.accentColor.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth().height(105.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = service.accentColor.copy(alpha = 0.12f),
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(service.icon, contentDescription = null, tint = service.accentColor, modifier = Modifier.size(16.dp))
                                    }
                                }

                                if (service.badge != null) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = service.accentColor.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = service.badge,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = service.accentColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Column {
                                Text(service.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.textColor, maxLines = 1)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(service.description, fontSize = 9.5.sp, color = Color.Gray, maxLines = 2, lineHeight = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPill(
    id: String,
    title: String,
    isSelected: Boolean,
    theme: AppThemeData,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) theme.primaryColor else Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) theme.primaryColor else Color(0xFFE5E7EB)),
        modifier = Modifier.height(30.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else Color(0xFF4B5563)
            )
        }
    }
}
