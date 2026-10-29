package com.zambudio.registropesos.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.zambudio.registropesos.R

/**
 * Barlow Condensed (OFL, `third_party/fonts`) para títulos y cifras: da el carácter deportivo y
 * hace que pesos y repeticiones se lean de un vistazo. El texto corrido sigue en la fuente del
 * sistema, más legible en tamaños pequeños.
 */
val BarlowCondensed = FontFamily(
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
    Font(R.font.barlow_condensed_extrabold, FontWeight.ExtraBold),
)

private val Base = Typography()

private fun TextStyle.display(weight: FontWeight, size: Int, line: Int) = copy(
    fontFamily = BarlowCondensed,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = 0.sp,
)

internal val GymTypography = Typography(
    displayLarge = Base.displayLarge.display(FontWeight.ExtraBold, size = 64, line = 68),
    displayMedium = Base.displayMedium.display(FontWeight.ExtraBold, size = 52, line = 56),
    displaySmall = Base.displaySmall.display(FontWeight.Bold, size = 40, line = 44),
    headlineLarge = Base.headlineLarge.display(FontWeight.Bold, size = 34, line = 38),
    headlineMedium = Base.headlineMedium.display(FontWeight.Bold, size = 30, line = 34),
    headlineSmall = Base.headlineSmall.display(FontWeight.Bold, size = 26, line = 30),
    titleLarge = Base.titleLarge.display(FontWeight.Bold, size = 24, line = 28),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = Base.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.06.em),
)
