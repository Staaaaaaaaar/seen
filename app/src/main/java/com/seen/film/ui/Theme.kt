package com.seen.film.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seen.film.R

val SeenSerif=FontFamily(Font(R.font.seen_serif,FontWeight.Normal))
val SeenSecondary:Color @Composable get()=MaterialTheme.colorScheme.onSurfaceVariant

private val SeenTypography=Typography(
    headlineLarge=TextStyle(fontFamily=SeenSerif,fontWeight=FontWeight.Normal,fontSize=34.sp,lineHeight=46.sp),
    headlineSmall=TextStyle(fontFamily=SeenSerif,fontWeight=FontWeight.Normal,fontSize=26.sp,lineHeight=36.sp),
    titleLarge=TextStyle(fontFamily=SeenSerif,fontWeight=FontWeight.Normal,fontSize=22.sp,lineHeight=32.sp),
    titleMedium=TextStyle(fontFamily=SeenSerif,fontWeight=FontWeight.Normal,fontSize=17.sp,lineHeight=27.sp),
    titleSmall=TextStyle(fontFamily=SeenSerif,fontWeight=FontWeight.Normal,fontSize=15.sp,lineHeight=24.sp),
    bodyLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=16.sp,lineHeight=27.sp),
    bodyMedium=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=14.sp,lineHeight=23.sp),
    bodySmall=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=12.sp,lineHeight=19.sp),
    labelLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=13.sp,lineHeight=21.sp),
    labelMedium=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=12.sp,lineHeight=18.sp),
    labelSmall=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=12.sp,lineHeight=18.sp),
)

@Composable
fun SeenTheme(darkTheme:Boolean=isSystemInDarkTheme(),content: @Composable () -> Unit){
    val colors=if(darkTheme)darkColorScheme(
        primary=Color(0xFFD4B28C),onPrimary=Color(0xFF30251C),
        background=Color(0xFF231F1B),onBackground=Color(0xFFF2E8DB),
        surface=Color(0xFF231F1B),onSurface=Color(0xFFF2E8DB),
        surfaceVariant=Color(0xFF3B3128),onSurfaceVariant=Color(0xFFC6B5A2),
        surfaceContainer=Color(0xFF2C2621),surfaceContainerHigh=Color(0xFF382F27),
        outline=Color(0xFF9D8873),outlineVariant=Color(0xFF55483B),
        error=Color(0xFFFFB4AB),onError=Color(0xFF690005),
    )else lightColorScheme(
        primary=Color(0xFF785638),onPrimary=Color.White,
        background=Color(0xFFF6F0E7),onBackground=Color(0xFF302A24),
        surface=Color(0xFFF6F0E7),onSurface=Color(0xFF302A24),
        surfaceVariant=Color(0xFFEBE1D3),onSurfaceVariant=Color(0xFF716353),
        surfaceContainer=Color(0xFFF0E7DB),surfaceContainerHigh=Color(0xFFF2E8DB),
        outline=Color(0xFF95816B),outlineVariant=Color(0xFFD9CDBD),
        error=Color(0xFFA63732),onError=Color.White,
    )
    MaterialTheme(colorScheme=colors,typography=SeenTypography,
        shapes=Shapes(small=RoundedCornerShape(5.dp),medium=RoundedCornerShape(12.dp),large=RoundedCornerShape(18.dp)),content=content)
}

val SeenGenreColors:List<Color> @Composable get()=if(MaterialTheme.colorScheme.background.luminance()<.5f)listOf(Color(0xFFD4B28C),Color(0xFFAD9483),Color(0xFF99A18D),Color(0xFFC5947A),Color(0xFF80786F))
        else listOf(Color(0xFF785638),Color(0xFFAA8267),Color(0xFF8A9178),Color(0xFFC29B7B),Color(0xFFC2B8AA))
