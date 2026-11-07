package com.seen.film.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
        primary=Color(0xFFBFD1E7),onPrimary=Color(0xFF17293F),
        background=Color(0xFF111C2B),onBackground=Color(0xFFE3EAF3),
        surface=Color(0xFF111C2B),onSurface=Color(0xFFE3EAF3),
        surfaceVariant=Color(0xFF213248),onSurfaceVariant=Color(0xFFA1B4CC),
        surfaceContainer=Color(0xFF152235),surfaceContainerHigh=Color(0xFF202F43),
        outline=Color(0xFF5B718B),outlineVariant=Color(0xFF34475F),
        error=Color(0xFFFFB4AB),onError=Color(0xFF690005),
    )else lightColorScheme(
        primary=Color(0xFF365877),onPrimary=Color.White,
        background=Color(0xFFF7F9FC),onBackground=Color(0xFF203247),
        surface=Color(0xFFF7F9FC),onSurface=Color(0xFF203247),
        surfaceVariant=Color(0xFFE9EFF6),onSurfaceVariant=Color(0xFF5C7087),
        surfaceContainer=Color(0xFFECF1F7),surfaceContainerHigh=Color(0xFFE3EAF3),
        outline=Color(0xFF6C8097),outlineVariant=Color(0xFFD6DFE9),
        error=Color(0xFFA63732),onError=Color.White,
    )
    MaterialTheme(colorScheme=colors,typography=SeenTypography,
        shapes=Shapes(small=RoundedCornerShape(5.dp),medium=RoundedCornerShape(12.dp),large=RoundedCornerShape(18.dp)),content=content)
}
