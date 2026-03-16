package com.xinkong.diary.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// 64 Colors Palette
val ColorPalette = listOf(
    // 红色系
    Color(0xFFFFCDD2), // 浅红
    Color(0xFFEF5350), // 红
    Color(0xFFD32F2F), // 深红

    // 粉红色系
    Color(0xFFF8BBD9), // 浅粉
    Color(0xFFEC407A), // 粉红
    Color(0xFFC2185B), // 深粉

    // 紫色系
    Color(0xFFE1BEE7), // 浅紫
    Color(0xFFAB47BC), // 紫
    Color(0xFF7B1FA2), // 深紫

    // 深紫色系
    Color(0xFFD1C4E9), // 浅深紫
    Color(0xFF7E57C2), // 深紫
    Color(0xFF512DA8), // 暗深紫

    // 靛蓝色系
    Color(0xFFC5CAE9), // 浅靛蓝
    Color(0xFF5C6BC0), // 靛蓝
    Color(0xFF303F9F), // 暗靛蓝

    // 蓝色系
    Color(0xFFBBDEFB), // 浅蓝
    Color(0xFF42A5F5), // 蓝
    Color(0xFF1976D2), // 深蓝

    // 天蓝色系
    Color(0xFFB3E5FC), // 浅天蓝
    Color(0xFF29B6F6), // 天蓝
    Color(0xFF0288D1), // 深天蓝

    // 青色系
    Color(0xFFB2EBF2), // 浅青
    Color(0xFF26C6DA), // 青
    Color(0xFF0097A7), // 深青

    // 蓝绿色系
    Color(0xFFB2DFDB), // 浅蓝绿
    Color(0xFF26A69A), // 蓝绿
    Color(0xFF00796B), // 深蓝绿

    // 绿色系
    Color(0xFFC8E6C9), // 浅绿
    Color(0xFF66BB6A), // 绿
    Color(0xFF388E3C), // 深绿

    // 浅绿色系
    Color(0xFFDCEDC8), // 浅浅绿
    Color(0xFF9CCC65), // 浅绿
    Color(0xFF689F38), // 深浅绿

    // 黄色系
    Color(0xFFFFF9C4), // 浅黄
    Color(0xFFFFCA28), // 黄
    Color(0xFFFBC02D), // 金黄

    // 橙色系
    Color(0xFFFFE0B2), // 浅橙
    Color(0xFFFFA726), // 橙
    Color(0xFFFFA000), // 深橙

    // 深橙色系
    Color(0xFFFFCCBC), // 浅深橙
    Color(0xFFFF7043), // 深橙
    Color(0xFFF57C00), // 橙红

    // 棕色系
    Color(0xFFD7CCC8), // 浅棕
    Color(0xFF8D6E63), // 棕
    Color(0xFF5D4037), // 深棕

    // 蓝灰色系
    Color(0xFFCFD8DC), // 浅蓝灰
    Color(0xFF78909C), // 蓝灰
    Color(0xFF455A64), // 深蓝灰

    // 纯色补充
    Color(0xFFEF9A9A), // 柔和红
    Color(0xFFCE93D8), // 柔和紫
    Color(0xFF9FA8DA), // 柔和靛蓝
    Color(0xFF90CAF9), // 柔和蓝
    Color(0xFF80CBC4), // 柔和蓝绿
    Color(0xFFA5D6A7), // 柔和绿
    Color(0xFFE6EE9C), // 柔和黄绿
    Color(0xFFFFE082), // 柔和黄
    Color(0xFFFFAB91)  // 柔和橙
)

// Diary Colors

// 通用 Diary 主题色
val CreamWhite = Color(0xFFFFF8E1)      // 主背景色（首页/底栏等）
val SweetWhite = Color(0xFFFFF8F8)      // FunScreen 背景色
val SweetText = Color(0xFF8B5F65)       // FunScreen 主要文字色
val Lavender = Color(0xFFE6E6FA)        // 按钮色/辅助色
val CoralPink = Color(0xFFFF7F50)       // 高亮/强调色
val SweetPink = Color(0xFFFFC0CB)       // 动画起始色/边框色
val SweetPurple = Color(0xFFE040FB)     // 动画终止色
val SweetButton = Lavender               // 统一按钮色（建议直接用 Lavender）
val SweetBorder= Color(0xFFFFB6C1)

val SweetBorder2 = Color(0xFFC8E6C9)
// 卡片/弹窗等边框色
val Blue1 = Color(0xFF2196F3)

val Blue2 = Color(0xFF03A9F4)

// 可扩展：如有更多色彩需求，建议在此处集中定义

val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = CreamWhite,
    surface = CreamWhite,
    onBackground = Color.Black,
    onSurface = Color.Black
)

@Immutable
data class DiaryColors(
    val background: Color = CreamWhite,         // 主背景色
    val background0: Color = SweetWhite,
    val sweetText: Color = SweetText,           // FunScreen 主要文字色
    val sweetButton: Color = Lavender,          // 按钮色
    val sweetHighlight: Color = CoralPink,      // 高亮/强调色
    val sweetPink: Color = SweetPink,           // 动画/边框色
    val sweetPurple: Color = SweetPurple,       // 动画终止色
    val border1: Color = SweetBorder2,
    val sweetBorder: Color = SweetBorder,       // 卡片/弹窗等边框色
    val primary: Color = Blue1,                 // 主要色（Material3 兼容）
    val secondary: Color = Blue2,               // 次要色
    val tertiary: Color = Pink40,               // 第三色

    val background2: Color = CreamWhite,        // 可调整背景色
    val border2: Color = SweetBorder2,         // 可调整边框色

)

val ThemeDefault = DiaryColors()

val ThemeOcean = DiaryColors(
    background = Color(0xFFFFF8E1),
    sweetText = Color(0xFF8B5F65),
    sweetButton = Color(0xFFE6E6FA),
    sweetHighlight = Color(0xFFFF7F50),
    sweetPink = Color(0xFFFFC0CB),
    sweetPurple = Color(0xFFE040FB),
    sweetBorder = Color(0xFFFFB6C1),
    primary = Color(0xFF2196F3),
    secondary = Color(0xFF03A9F4),
    tertiary = Color(0xFF7D5260),

    background2 = Color(0xFFBBDEFB),
    border2 = Color(0xFF2196F3),
)

val ThemeForest = DiaryColors(
    background = Color(0xFFFFF8E1),
    sweetText = Color(0xFF8B5F65),
    sweetButton = Color(0xFFE6E6FA),
    sweetHighlight = Color(0xFFFF7F50),
    sweetPink = Color(0xFFFFC0CB),
    sweetPurple = Color(0xFFE040FB),
    sweetBorder = Color(0xFFFFB6C1),
    primary = Color(0xFF2196F3),
    secondary = Color(0xFF03A9F4),
    tertiary = Color(0xFF7D5260),

    background2 = Color(0xFFC8E6C9),
    border2 = Color(0xFF4CAF50),
)


val ThemeList = listOf(
    "默认主题" to ThemeDefault,
    "海洋之蓝" to ThemeOcean,
    "森林之绿" to ThemeForest,
)
