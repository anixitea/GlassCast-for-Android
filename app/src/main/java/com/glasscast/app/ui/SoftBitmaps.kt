package com.glasscast.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect

/*
 * Blurred covers, made once instead of every frame.
 *
 * Every blurred backdrop in the app used to be `Modifier.blur` — a GPU blur
 * the renderer recomputes whenever the screen redraws. The screens those
 * backdrops sit on redraw constantly (the wave animates while anything plays,
 * lists scroll), so a full-screen blur was being paid for on every frame. It's
 * why the player felt heavier than Cider's.
 *
 * Here the cover is shrunk to a few dozen pixels and box-blurred once, off the
 * main thread. Drawn back at full size with bilinear filtering, a 32px image
 * is itself a heavy, smooth blur — and per frame it costs one bitmap draw.
 * Three box passes approximate a gaussian closely enough that the eye can't
 * tell the difference at this scale.
 */

/** The cover softened to [width] px wide: stretch it to any size for a blur. */
internal fun softened(source: Bitmap, width: Int = 32, radius: Int = 2, passes: Int = 3): Bitmap {
    val w = width
    val h = (width.toFloat() * source.height / source.width.coerceAtLeast(1)).toInt().coerceIn(8, width * 3)
    val small = Bitmap.createScaledBitmap(source, w, h, true)
    return blurred(small, radius, passes)
}

/**
 * The cover cropped the way the player's artwork is (center crop to
 * [aspect] = height ÷ width), with its own vertical reflection underneath —
 * the pair the player's backdrop is made of — softened as one image, so the
 * blur runs across the seam instead of stopping at it.
 */
internal fun softenedMirror(source: Bitmap, aspect: Float, width: Int = 48, radius: Int = 2, passes: Int = 3): Bitmap {
    val cropW: Int
    val cropH: Int
    if (source.height.toFloat() / source.width >= aspect) {
        cropW = source.width
        cropH = (source.width * aspect).toInt().coerceAtMost(source.height)
    } else {
        cropH = source.height
        cropW = (source.height / aspect).toInt().coerceAtMost(source.width)
    }
    val left = (source.width - cropW) / 2
    val top = (source.height - cropH) / 2

    val half = (width * aspect).toInt().coerceAtLeast(8)
    val pair = Bitmap.createBitmap(width, half * 2, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(pair)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    val src = Rect(left, top, left + cropW, top + cropH)
    canvas.drawBitmap(source, src, Rect(0, 0, width, half), paint)
    // The reflection: the same crop, flipped about the seam (y = half), so the
    // crop's bottom row lands at the seam and its top row at the foot.
    canvas.save()
    canvas.concat(Matrix().apply { setScale(1f, -1f, 0f, half.toFloat()) })
    canvas.drawBitmap(source, src, Rect(0, 0, width, half), paint)
    canvas.restore()
    return blurred(pair, radius, passes)
}

/**
 * [softenedMirror] turned on its side, for the landscape player: the cover
 * with its reflection to the right — flipped about the right edge — softened
 * as one image, so the blur runs across the seam toward the controls.
 * [aspect] is the cover band's height over its width.
 */
internal fun softenedMirrorSideways(source: Bitmap, aspect: Float, height: Int = 48, radius: Int = 2, passes: Int = 3): Bitmap {
    val cropW: Int
    val cropH: Int
    if (source.height.toFloat() / source.width >= aspect) {
        cropW = source.width
        cropH = (source.width * aspect).toInt().coerceAtMost(source.height)
    } else {
        cropH = source.height
        cropW = (source.height / aspect).toInt().coerceAtMost(source.width)
    }
    val left = (source.width - cropW) / 2
    val top = (source.height - cropH) / 2

    val half = (height / aspect).toInt().coerceAtLeast(8)
    val pair = Bitmap.createBitmap(half * 2, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(pair)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    val src = Rect(left, top, left + cropW, top + cropH)
    canvas.drawBitmap(source, src, Rect(0, 0, half, height), paint)
    canvas.save()
    canvas.concat(Matrix().apply { setScale(-1f, 1f, half.toFloat(), 0f) })
    canvas.drawBitmap(source, src, Rect(0, 0, half, height), paint)
    canvas.restore()
    return blurred(pair, radius, passes)
}

private fun blurred(bitmap: Bitmap, radius: Int, passes: Int): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    val a = IntArray(w * h)
    bitmap.getPixels(a, 0, w, 0, 0, w, h)
    val b = IntArray(w * h)
    repeat(passes) {
        boxPass(a, b, w, h, radius, horizontal = true)
        boxPass(b, a, w, h, radius, horizontal = false)
    }
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    out.setPixels(a, 0, w, 0, 0, w, h)
    return out
}

private fun boxPass(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
    val lines = if (horizontal) h else w
    val length = if (horizontal) w else h
    for (line in 0 until lines) {
        for (i in 0 until length) {
            var r = 0
            var g = 0
            var bl = 0
            var count = 0
            for (k in -radius..radius) {
                val j = (i + k).coerceIn(0, length - 1)
                val p = if (horizontal) src[line * w + j] else src[j * w + line]
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                bl += p and 0xFF
                count++
            }
            val index = if (horizontal) line * w + i else i * w + line
            dst[index] = (0xFF shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (bl / count)
        }
    }
}
