package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppThemeData
import com.example.ui.MainViewModel

/**
 * Katib premium workspace dashboard.
 * All actions are callbacks to the existing feature screens/dialogs; no feature
 * logic is duplicated here and no existing workflow is replaced.
 */
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    theme: AppThemeData,
    onOpenFiles: () -> Unit,
    onOpenExtraction: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenDocuments: () -> Unit,
    onOpenConverter: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenSubscription: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartAudio: () -> Unit
) {
    val files by viewModel.allFiles.collectAsState()
    val scroll = rememberScrollState()
    val ink = theme.textColor
    val muted = theme.subtleTextColor
    val gold = Color(0xFFB9934C)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(theme.backgroundColors))
            .verticalScroll(scroll)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = if (theme.isDark) Color(0xFF182438) else Color(0xFF142744)),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(12.dp), color = gold.copy(alpha = 0.18f)) {
                        Icon(Icons.Default.AutoStories, null, tint = Color(0xFFD2B16B), modifier = Modifier.padding(10.dp).size(24.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("مساحة عمل كاتب", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("كل أدوات التأليف والمستندات في مكان واحد", color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp)
                    }
                }
                Text(
                    "حوّل أفكارك وملفاتك إلى أعمال منظمة، واكتب واقرأ واستخرج النصوص من مساحة عمل واحدة.",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    lineHeight = 21.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onOpenProjects,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD2B16B), contentColor = Color(0xFF142744)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Create, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("ابدأ الكتابة", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onOpenFiles,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("استيراد ملف")
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("الملفات المحفوظة", files.size.toString(), Icons.Default.Folder, theme, Modifier.weight(1f))
            MetricCard("مساحة العمل", "جاهزة", Icons.Default.AutoAwesome, theme, Modifier.weight(1f))
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("أدواتك الأساسية", color = ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("الوصول السريع", color = muted, fontSize = 10.sp)
        }

        val actions = listOf(
            DashboardAction("المشاريع والكتابة", "إنشاء الكتب والمقالات وتحريرها", Icons.Default.MenuBook, onOpenProjects),
            DashboardAction("الملفات والمصادر", "استيراد الملفات وتنظيمها", Icons.Default.Folder, onOpenFiles),
            DashboardAction("استخلاص النصوص", "استخراج النصوص من المستندات", Icons.Default.TextSnippet, onOpenExtraction),
            DashboardAction("مستندات الهاتف", "تصفح وفتح ملفات الجهاز", Icons.Default.FolderSpecial, onOpenDocuments),
            DashboardAction("التحويل والأدوات", "تحويل المستندات ومعالجة العربية", Icons.Default.Transform, onOpenConverter),
            DashboardAction("متصفح البحوث", "البحث وفتح مصادر الويب", Icons.Default.Public, onOpenBrowser),
            DashboardAction("الإحصائيات", "متابعة إنتاجيتك والنصوص", Icons.Default.Insights, onOpenAnalytics),
            DashboardAction("القراءة الصوتية", "استمع إلى محتوى مشروعك", Icons.Default.Headphones, onStartAudio),
            DashboardAction("الاشتراك والمزايا", "إدارة مزايا كاتب VIP", Icons.Default.WorkspacePremium, onOpenSubscription),
            DashboardAction("الإعدادات", "التفضيلات والمظهر واللغة", Icons.Default.Settings, onOpenSettings)
        )
        actions.chunked(2).forEach { rowActions ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                rowActions.forEach { action ->
                    FeatureCard(action, theme, Modifier.weight(1f))
                }
                if (rowActions.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenSettings),
            shape = RoundedCornerShape(16.dp),
            color = gold.copy(alpha = if (theme.isDark) 0.14f else 0.10f)
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, null, tint = gold)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("تخصيص تجربة كاتب", color = ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("اضبط اللغة والمظهر وإعدادات مساحة العمل", color = muted, fontSize = 10.sp)
                }
                Icon(Icons.Default.ChevronLeft, null, tint = gold)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private data class DashboardAction(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit
)

@Composable
private fun MetricCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, theme: AppThemeData, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = theme.elevatedCardColor)) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color(0xFFB9934C), modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(9.dp))
            Column {
                Text(value, color = theme.textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(label, color = theme.subtleTextColor, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun FeatureCard(action: DashboardAction, theme: AppThemeData, modifier: Modifier) {
    Card(
        modifier = modifier.heightIn(min = 112.dp).clickable(onClick = action.onClick),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = theme.elevatedCardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(10.dp), color = theme.primaryColor.copy(alpha = 0.09f)) {
                Icon(action.icon, null, tint = theme.primaryColor, modifier = Modifier.padding(8.dp).size(20.dp))
            }
            Text(action.title, color = theme.textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(action.subtitle, color = theme.subtleTextColor, fontSize = 9.sp, lineHeight = 14.sp)
        }
    }
}
