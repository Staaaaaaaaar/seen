package com.seen.film.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

class PosterStore(private val context: Context) {
    private val directory = File(context.filesDir, "posters").apply { mkdirs() }
    fun file(key: String?): File? = key?.takeIf { it.matches(Regex("[a-f0-9-]{36}\\.jpg")) }
        ?.let { File(directory, it) }?.takeIf { it.isFile }

    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        val staging = File.createTempFile("poster-", ".input", context.cacheDir)
        try {
            // Bound provider input before decoding; never retain an external URI.
            context.contentResolver.openInputStream(uri)?.use { input ->
                staging.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024); var total = 0
                    while (true) {
                        val count = input.read(buffer); if (count == -1) break
                        total += count; require(total <= 20 * 1024 * 1024) { "图片不能超过 20 MiB" }
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error("无法读取所选图片")
            val bitmap = if (android.os.Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(staging)) { decoder, info, _ ->
                    require(info.size.width > 0 && info.size.height > 0)
                    val scale = max(info.size.width, info.size.height) / 1600f
                    if (scale > 1) decoder.setTargetSize((info.size.width / scale).toInt().coerceAtLeast(1), (info.size.height / scale).toInt().coerceAtLeast(1))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(staging.path, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法识别这张图片" }
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 1
                    while (max(bounds.outWidth, bounds.outHeight) / inSampleSize > 1600) inSampleSize *= 2
                }
                BitmapFactory.decodeFile(staging.path, options) ?: error("无法识别这张图片")
            }
            val key = "${UUID.randomUUID()}.jpg"
            val target = File(directory, key)
            try {
                FileOutputStream(target).use { require(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it)) { "无法保存海报" } }
            } catch (e: Exception) { target.delete(); throw e }
            finally { bitmap.recycle() }
            key
        } finally { staging.delete() }
    }
    suspend fun removeUnused(key: String?, histories: List<MovieHistory>) = withContext(Dispatchers.IO) {
        if (histories.none { it.movie.posterKey == key }) file(key)?.delete()
    }
}
