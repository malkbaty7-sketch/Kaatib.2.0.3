package com.example.data.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class AppThemeData(
    val id: String,
    val name: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val textColor: Color,
    val surfaceColor: Color,
    val backgroundColors: List<Color>,
    val shadowColor: Color,
    val elevationDp: Dp,
    val cornerRadiusDp: Dp,
    val isDark: Boolean = false
) {
    val backgroundBrush: Brush
        get() = Brush.linearGradient(backgroundColors)

    val cardColor: Color
        get() = if (isDark) Color(0xFF1E293B) else surfaceColor

    val elevatedCardColor: Color
        get() = if (isDark) Color(0xFF263449) else Color.White

    val subtleTextColor: Color
        get() = textColor.copy(alpha = if (isDark) 0.72f else 0.68f)

    val borderColor: Color
        get() = primaryColor.copy(alpha = if (isDark) 0.28f else 0.16f)
}

val defaultAppThemes = listOf(
    // Kaatib Premium: restrained royal navy with warm gold accents.
    // The light surface keeps long writing sessions comfortable; the paired
    // royal-night palette is available separately for low-light environments.
    AppThemeData(
        id = "kaatibRoyal",
        name = "كاتب الملكي (أزرق وذهبي)",
        primaryColor = Color(0xFFB88A3B),
        secondaryColor = Color(0xFFD8B66A),
        textColor = Color(0xFF14233F),
        surfaceColor = Color(0xFFFAF8F2),
        backgroundColors = listOf(Color(0xFFF7F5EF), Color(0xFFF0EBDD)),
        shadowColor = Color(0x2614233F),
        elevationDp = 6.dp,
        cornerRadiusDp = 18.dp,
        isDark = false
    ),
    AppThemeData(
        id = "kaatibRoyalNight",
        name = "كاتب الملكي الليلي",
        primaryColor = Color(0xFFD8B66A),
        secondaryColor = Color(0xFFB88A3B),
        textColor = Color(0xFFF4EFE3),
        surfaceColor = Color(0xFF101C32),
        backgroundColors = listOf(Color(0xFF09162B), Color(0xFF172944)),
        shadowColor = Color(0x66000000),
        elevationDp = 6.dp,
        cornerRadiusDp = 18.dp,
        isDark = true
    ),

    AppThemeData(
        id = "warmSunset",
        name = "الغروب الدافئ",
        primaryColor = Color(0xFFD2691E),
        secondaryColor = Color(0xFFCD853F),
        textColor = Color(0xFF3E2723),
        surfaceColor = Color(0xFFFFF8EE),
        backgroundColors = listOf(Color(0xFFFFF5E1), Color(0xFFFFE4B5), Color(0xFFFFDAB9)),
        shadowColor = Color(0x338B4513),
        elevationDp = 8.dp,
        cornerRadiusDp = 16.dp,
        isDark = false
    ),
    AppThemeData(
        id = "naturalCalm",
        name = "الهدوء الطبيعي",
        primaryColor = Color(0xFF2E7D32),
        secondaryColor = Color(0xFF66BB6A),
        textColor = Color(0xFF1B5E20),
        surfaceColor = Color(0xFFF7FCF7),
        backgroundColors = listOf(Color(0xFFF0FFF4), Color(0xFFE8F5E9)),
        shadowColor = Color(0x332E7D32),
        elevationDp = 10.dp,
        cornerRadiusDp = 18.dp,
        isDark = false
    ),
    AppThemeData(
        id = "calmSky",
        name = "السماء الهادئة",
        primaryColor = Color(0xFF1565C0),
        secondaryColor = Color(0xFF42A5F5),
        textColor = Color(0xFF0D3B66),
        surfaceColor = Color(0xFFF5F9FD),
        backgroundColors = listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB)),
        shadowColor = Color(0x331565C0),
        elevationDp = 10.dp,
        cornerRadiusDp = 18.dp,
        isDark = false
    ),
    AppThemeData(
        id = "sepiaParchment",
        name = "الرقّ العربي الكلاسيكي (Sepia)",
        primaryColor = Color(0xFF8D4E2A),
        secondaryColor = Color(0xFFC88A55),
        textColor = Color(0xFF2C1D11),
        surfaceColor = Color(0xFFF8F1E5),
        backgroundColors = listOf(Color(0xFFF4ECD8), Color(0xFFEADBC0)),
        shadowColor = Color(0x335D4037),
        elevationDp = 8.dp,
        cornerRadiusDp = 16.dp,
        isDark = false
    ),
    AppThemeData(
        id = "royalNight",
        name = "الليل الملكي المريح للعين (Dark)",
        primaryColor = Color(0xFF38BDF8),
        secondaryColor = Color(0xFF818CF8),
        textColor = Color(0xFFF1F5F9),
        surfaceColor = Color(0xFF0F172A),
        backgroundColors = listOf(Color(0xFF0B1120), Color(0xFF1E293B)),
        shadowColor = Color(0x66000000),
        elevationDp = 10.dp,
        cornerRadiusDp = 18.dp,
        isDark = true
    )
)

data class PaperColorOption(
    val id: String,
    val name: String,
    val hex: String,
    val color: Color
)

val paperColorOptions = listOf(
    PaperColorOption("cream", "كريمي دافئ", "#FFFDD0", Color(0xFFFFFDD0)),
    PaperColorOption("white", "أبيض ناصع", "#FFFFFF", Color(0xFFFFFFFF)),
    PaperColorOption("beige", "بيج كلاسيكي", "#F5F5DC", Color(0xFFF5F5DC)),
    PaperColorOption("softBlue", "أزرق هادئ", "#E6F3FF", Color(0xFFE6F3FF)),
    PaperColorOption("softGreen", "أخضر باستيل", "#F0FFF4", Color(0xFFF0FFF4)),
    PaperColorOption("softGray", "رمادي خافت", "#F5F5F5", Color(0xFFF5F5F5))
)

data class PaperSizeOption(
    val id: String,
    val name: String,
    val widthMm: Float,
    val heightMm: Float,
    val aspectRatio: Float
)

val paperSizeOptions = listOf(
    PaperSizeOption("A4", "A4 (210 × 297 ملم)", 210f, 297f, 210f / 297f),
    PaperSizeOption("A5", "A5 (148 × 210 ملم)", 148f, 210f, 148f / 210f),
    PaperSizeOption("B5", "B5 (176 × 250 ملم)", 176f, 250f, 176f / 250f),
    PaperSizeOption("Letter", "Letter (8.5 × 11 بوصة)", 215.9f, 279.4f, 215.9f / 279.4f),
    PaperSizeOption("CUSTOM", "مقاس حر مخصص", 200f, 280f, 200f / 280f)
)

data class NumberingStyleOption(
    val id: Int,
    val name: String,
    val sampleText: String
)

val numberingStyleOptions = listOf(
    NumberingStyleOption(0, "أرقام عربية قياسية", "1، 2، 3"),
    NumberingStyleOption(1, "أرقام رومانية كلاسيكية", "I، II، III"),
    NumberingStyleOption(2, "حروف أبجدية", "أ، ب، ج"),
    NumberingStyleOption(3, "أرقام مع خط زخرفي", "━━━ 1 ━━━"),
    NumberingStyleOption(4, "أرقام داخل دائرة", "⊙ 1 ⊙"),
    NumberingStyleOption(5, "زخرفة إسلامية", "۞ 1 ۞")
)

fun formatPageNumber(pageIndex: Int, styleId: Int): String {
    val n = pageIndex + 1
    return when (styleId) {
        0 -> "$n"
        1 -> toRoman(n)
        2 -> toArabicAlphabet(n)
        3 -> "━━━ $n ━━━"
        4 -> "⊙ $n ⊙"
        5 -> "۞ $n ۞"
        else -> "$n"
    }
}

private fun toRoman(number: Int): String {
    val romanNumerals = listOf(
        1000 to "M", 900 to "CM", 500 to "D", 400 to "CD",
        100 to "C", 90 to "XC", 50 to "L", 40 to "XL",
        10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I"
    )
    var num = number
    val result = StringBuilder()
    for ((value, numeral) in romanNumerals) {
        while (num >= value) {
            result.append(numeral)
            num -= value
        }
    }
    return result.toString()
}

private fun toArabicAlphabet(number: Int): String {
    val arabicLetters = listOf(
        "أ", "ب", "ج", "د", "هـ", "و", "ز", "ح", "ط", "ي",
        "ك", "ل", "م", "ن", "س", "ع", "ف", "ص", "ق", "ر",
        "ش", "ت", "ث", "خ", "ذ", "ض", "ظ", "غ"
    )
    return if (number in 1..arabicLetters.size) {
        arabicLetters[number - 1]
    } else {
        "$number"
    }
}
