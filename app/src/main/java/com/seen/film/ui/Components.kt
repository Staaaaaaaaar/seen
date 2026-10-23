package com.seen.film.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun Poster(file: File?, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, file?.path) {
        value = withContext(Dispatchers.IO) {
            file?.let {
                runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(it.path, bounds)
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 768) inSampleSize *= 2
                    }
                    BitmapFactory.decodeFile(it.path, options)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    Box(modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Outlined.Movie, null, Modifier.size(30.dp), tint = SeenSecondary)
    }
}

@Composable
fun PageMessage(title: String, description: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text(description, color = SeenSecondary)
        if (action != null) { Spacer(Modifier.height(24.dp)); Button(onAction) { Text(action) } }
    }
}
