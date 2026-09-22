package com.ahu.ahutong.core.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.ahu.ahutong.core.common.AppEnvironmentHolder
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * 主页自定义背景存储（v1）：
 * 选图 → 按屏幕比例 centerCrop → 落盘原图 + 模糊图（三遍盒式模糊 ≈ 高斯）；
 * 模糊滑杆只重出模糊图，不重解码原图。revision 递增驱动主页重组。
 */
object HomeBackgroundStore {
    private const val SRC_FILE = "home_bg_src.jpg"
    private const val BLUR_FILE = "home_bg.jpg"

    private val _revision = MutableStateFlow(0)
    val revision = _revision.asStateFlow()

    private val prefs
        get() = AppEnvironmentHolder.context()
            .getSharedPreferences("home_background", Context.MODE_PRIVATE)

    val isEnabled: Boolean get() = prefs.getString("path", null) != null
    val blurRadius: Int get() = prefs.getInt("blur", 0)

    fun blurredFile(context: Context): File = File(context.filesDir, BLUR_FILE)

    /** 选图落盘（crop 到屏幕比例）并生成当前模糊度的成品图。 */
    suspend fun importFromUri(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val raw = decodeBoundsSafe(context, uri) ?: return@withContext
        val metrics = context.resources.displayMetrics
        val cropped = centerCrop(raw, metrics.widthPixels, metrics.heightPixels)
        if (cropped != raw) raw.recycle()
        File(context.filesDir, SRC_FILE).outputStream().use {
            cropped.compress(Bitmap.CompressFormat.JPEG, 92, it)
        }
        regenerateBlurred(context, cropped, blurRadius)
        prefs.edit().putString("path", BLUR_FILE).putInt("blur", blurRadius).apply()
        _revision.value++
    }

    /** 调模糊度：从原图重出成品。 */
    suspend fun updateBlur(context: Context, radius: Int) = withContext(Dispatchers.IO) {
        val srcFile = File(context.filesDir, SRC_FILE)
        if (!srcFile.exists()) return@withContext
        val src = BitmapFactory.decodeFile(srcFile.absolutePath) ?: return@withContext
        regenerateBlurred(context, src, radius)
        prefs.edit().putString("path", BLUR_FILE).putInt("blur", radius).apply()
        _revision.value++
    }

    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        File(context.filesDir, SRC_FILE).delete()
        File(context.filesDir, BLUR_FILE).delete()
        prefs.edit().clear().apply()
        _revision.value++
    }

    /** 取主色调（缩样后量化统计，返回 #AARRGGBB）。 */
    fun dominantColorHex(context: Context): String? {
        val file = blurredFile(context)
        if (!file.exists()) return null
        val small = BitmapFactory.decodeFile(file.absolutePath)?.let {
            Bitmap.createScaledBitmap(it, 24, 24, true)
        } ?: return null
        // 量化到 4bit/通道分桶，取「饱和度尚可的最常见桶」
        val buckets = HashMap<Int, Int>()
        for (x in 0 until small.width) {
            for (y in 0 until small.height) {
                val c = small.getPixel(x, y)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val maxC = max(r, max(g, b))
                val minC = min(r, min(g, b))
                if (maxC - minC < 24) continue // 滤掉近灰像素
                val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
                buckets[key] = (buckets[key] ?: 0) + 1
            }
        }
        small.recycle()
        val best = buckets.maxByOrNull { it.value }?.key ?: return null
        val r = ((best shr 8) and 0xF) * 17
        val g = ((best shr 4) and 0xF) * 17
        val b = (best and 0xF) * 17
        return "#FF%02X%02X%02X".format(r, g, b)
    }

    private fun regenerateBlurred(context: Context, src: Bitmap, radius: Int) {
        val out = if (radius > 0) boxBlur(src, radius) else src
        blurredFile(context).outputStream().use {
            out.compress(Bitmap.CompressFormat.JPEG, 85, it)
        }
        if (out != src) out.recycle()
    }

    private fun decodeBoundsSafe(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        // 目标分辨率约 2 倍屏宽即可（模糊后细节不敏感）
        val target = context.resources.displayMetrics.widthPixels * 2
        val sample = max(1, bounds.outWidth / target)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }

    /** 按目标宽高比居中裁剪。 */
    private fun centerCrop(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val srcRatio = src.width.toFloat() / src.height
        val dstRatio = targetW.toFloat() / targetH
        if (kotlin.math.abs(srcRatio - dstRatio) < 0.01f) return src
        return if (srcRatio > dstRatio) {
            // 原图更宽：裁左右
            val newW = (src.height * dstRatio).toInt()
            val x = (src.width - newW) / 2
            Bitmap.createBitmap(src, x, 0, newW, src.height)
        } else {
            val newH = (src.width / dstRatio).toInt()
            val y = (src.height - newH) / 2
            Bitmap.createBitmap(src, 0, y, src.width, newH)
        }
    }

    /** 三遍盒式模糊 ≈ 高斯（纯 CPU，全 API 可用，半径建议 ≤25）。 */
    private fun boxBlur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        var pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        repeat(3) {
            boxBlurPass(pixels, tmp, w, h, radius, horizontal = true)
            boxBlurPass(tmp, pixels, w, h, radius, horizontal = false)
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun boxBlurPass(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
        val div = radius * 2 + 1
        val width = if (horizontal) w else h
        val height = if (horizontal) h else w
        for (line in 0 until height) {
            var sumR = 0
            var sumG = 0
            var sumB = 0
            fun px(i: Int): Int = if (horizontal) src[line * w + i] else src[i * w + line]
            for (i in -radius..radius) {
                val c = px(i.coerceIn(0, width - 1))
                sumR += (c shr 16) and 0xFF; sumG += (c shr 8) and 0xFF; sumB += c and 0xFF
            }
            for (i in 0 until width) {
                val c = (0xFF shl 24) or ((sumR / div) shl 16) or ((sumG / div) shl 8) or (sumB / div)
                if (horizontal) dst[line * w + i] = c else dst[i * w + line] = c
                val addC = px((i + radius + 1).coerceAtMost(width - 1))
                val subC = px((i - radius).coerceAtLeast(0))
                sumR += ((addC shr 16) and 0xFF) - ((subC shr 16) and 0xFF)
                sumG += ((addC shr 8) and 0xFF) - ((subC shr 8) and 0xFF)
                sumB += (addC and 0xFF) - (subC and 0xFF)
            }
        }
    }
}
