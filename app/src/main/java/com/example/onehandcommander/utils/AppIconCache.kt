package com.example.onehandcommander.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.collection.LruCache

/**
 * アプリアイコンを Bitmap 形式で安全かつ省メモリに保持するキャッシュマネージャー
 * Context への直接参照を抱える Drawable を常駐させず、メモリリークを根絶します。
 */
object AppIconCache {
    // 画面解像度に応じたアイコンサイズ (最大48dp相当) の Bitmap を最大 100 個キャッシュ
    private const val MAX_CACHE_ENTRIES = 100

    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_ENTRIES) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return 1 // エントリ数ベース
        }
    }

    @Synchronized
    fun get(packageName: String): Bitmap? {
        return cache.get(packageName)
    }

    @Synchronized
    fun put(packageName: String, bitmap: Bitmap) {
        cache.put(packageName, bitmap)
    }

    /**
     * Drawable を安全に Bitmap に変換してキャッシュに保存
     */
    @Synchronized
    fun putDrawable(packageName: String, drawable: Drawable, targetSizePx: Int = 128): Bitmap {
        val existing = cache.get(packageName)
        if (existing != null && !existing.isRecycled) {
            return existing
        }

        val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
            drawable.bitmap
        } else {
            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else targetSizePx
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else targetSizePx
            val bmp = Bitmap.createBitmap(width.coerceAtMost(targetSizePx), height.coerceAtMost(targetSizePx), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp
        }
        cache.put(packageName, bitmap)
        return bitmap
    }

    @Synchronized
    fun remove(packageName: String) {
        cache.remove(packageName)
    }

    @Synchronized
    fun clear() {
        cache.evictAll()
    }
}
