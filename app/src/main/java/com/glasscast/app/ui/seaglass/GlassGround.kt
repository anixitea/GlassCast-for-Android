package com.glasscast.app.ui.seaglass

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * What a piece of glass does to the ground under it: a soft cast shadow, a
 * tighter contact shadow where it rests, and a pool of its own color where
 * light that came through it lands, offset down and away from the light.
 *
 * Every shape here is a blurred rounded rectangle, so each is a mask made once
 * in software (a few milliseconds the first time a size appears) and kept,
 * then drawn tinted. Nothing is blurred while drawing, and no platform shadow
 * is involved: those are what froze pull-to-refresh in 1.7, on an animated,
 * concave outline. The masks are made at about 1.5 pixels per dp and scaled
 * up, which a blur this soft doesn't show.
 *
 * Formulas from the library's realtime ground (see SeaGlass.kt).
 */
internal object GlassGround {

    private const val CAST = 0
    private const val CONTACT = 1
    private const val POOL = 2

    private val cache = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()

    /**
     * The ground for a piece [width] × [height] dp with corner [radius] dp,
     * drawn relative to the piece's top-left corner. [strength] scales it all,
     * for fading a piece in.
     */
    fun DrawScope.drawGround(
        width: Float,
        height: Float,
        radius: Float,
        recipe: GlassRecipe,
        pigment: Pigment,
        dark: Boolean,
        strength: Float = 1f
    ) {
        if (strength <= 0.01f || width < 1f || height < 1f) return
        val short = min(width, height)
        val t = recipe.transmission
        val shade = if (dark) 1.8f else 1f

        // Cast shadow.
        val castOffset = min(16f, recipe.thickness * 0.40f + recipe.elevation * 0.72f + recipe.bulge * short * 0.03f)
        val castSigma = min(6f, 2.5f + recipe.elevation * 0.18f + recipe.frost * 0.7f + recipe.bulge * short * 0.018f)
        val castAlpha = ((0.18f + recipe.elevation * 0.001f) * (1f - t * 0.82f) * shade).coerceAtMost(0.6f)
        layer(CAST, width, height, radius, castSigma, castOffset, Color(0xFF3F4342).copy(alpha = castAlpha * strength))

        // Contact shadow.
        val contactOffset = min(8f, recipe.thickness * 0.32f + 0.8f)
        val contactSigma = 1.4f + recipe.elevation * 0.065f
        val contactAlpha = (0.10f * (1f - t * 0.70f) * shade).coerceAtMost(0.4f)
        layer(CONTACT, width, height, radius, contactSigma, contactOffset, Color(0xFF3D413F).copy(alpha = contactAlpha * strength))

        // Transmitted light: on paper, the body color deepened; at night, the
        // edge color, so it reads as light on wet sand rather than a stain.
        val travel = 4f + recipe.elevation * 0.56f + short * 0.055f
        val poolOffset = min(22f, recipe.thickness * 0.42f + travel)
        val poolSigma = min(8f, 2.8f + short * 0.033f + recipe.frost * 1.6f)
        val tint = if (dark) pigment.edge.copy(alpha = 0.30f) else deepen(pigment.body)
        layer(POOL, width, height, radius, poolSigma, poolOffset, tint.copy(alpha = tint.alpha * strength), transmission = t)
    }

    /**
     * A cover's ground: the shadow of a card lying on the page, and a pool of
     * the cover's color under its lower half. [inset] narrows the pool so it
     * shows below the cover rather than beside it.
     */
    fun DrawScope.drawCoverGround(
        width: Float,
        height: Float,
        radius: Float,
        pigment: Pigment,
        dark: Boolean,
        strength: Float = 1f
    ) {
        if (strength <= 0.01f || width < 1f || height < 1f) return
        val short = min(width, height)
        val shade = if (dark) 2.2f else 1f
        val inset = short * 0.05f
        layer(
            CAST, width - inset * 2f, height, radius, (short * 0.05f).coerceIn(3f, 10f), short * 0.035f + 1f,
            Color(0xFF3F4342).copy(alpha = (0.11f * shade).coerceAtMost(0.4f) * strength),
            dx = inset
        )
        val tint = if (dark) pigment.edge.copy(alpha = 0.32f) else deepen(pigment.body).copy(alpha = 0.85f)
        layer(
            POOL, width - inset * 2.4f, height, radius, (short * 0.07f).coerceIn(4f, 14f), short * 0.08f + 2f,
            tint.copy(alpha = tint.alpha * strength), transmission = 0.62f, dx = inset * 1.2f
        )
    }

    /** The pool color: the body pushed further from white. */
    private fun deepen(c: Color) = Color(
        (1f - (1f - c.red) * 1.35f).coerceIn(0f, 1f),
        (1f - (1f - c.green) * 1.35f).coerceIn(0f, 1f),
        (1f - (1f - c.blue) * 1.35f).coerceIn(0f, 1f),
        1f
    )

    private fun DrawScope.layer(
        kind: Int,
        width: Float,
        height: Float,
        radius: Float,
        sigma: Float,
        offset: Float,
        color: Color,
        transmission: Float = 0f,
        dx: Float = 0f
    ) {
        if (color.alpha <= 0.003f || width <= 0f) return
        val d = density
        val scale = (1.5f / d).coerceAtMost(1f)
        val pad = ceil(sigma * 3f)
        val mask = mask(kind, width, height, radius, sigma, pad, scale, transmission) ?: return
        dst.set(
            (dx - pad) * d,
            (offset - pad) * d,
            (dx + width + pad) * d,
            (offset + height + pad) * d
        )
        drawPaint.color = color.toArgb()
        drawIntoCanvas { it.nativeCanvas.drawBitmap(mask, null, dst, drawPaint) }
    }

    /** The blurred rounded rectangle as an alpha mask, from the cache or made now. */
    private fun mask(
        kind: Int,
        width: Float,
        height: Float,
        radius: Float,
        sigma: Float,
        pad: Float,
        scale: Float,
        transmission: Float
    ): Bitmap? {
        // Sizes are rounded to steps of about 3% (and at least half a dp),
        // and the mask stretched to the exact size when drawn: a blur this
        // soft doesn't show a 3% stretch, and a bar whose width animates
        // then needs a handful of masks, not a new one every frame.
        fun step(v: Float) = (v * 0.03f).coerceAtLeast(0.5f)
        val qWidth = (width / step(width)).roundToInt() * step(width)
        val qHeight = (height / step(height)).roundToInt() * step(height)
        val key = "$kind:${qWidth.roundToInt()}:${qHeight.roundToInt()}:${(width / step(width)).roundToInt()}:" +
            "${(height / step(height)).roundToInt()}:${(radius * 2f).roundToInt()}:${(sigma * 4f).roundToInt()}:" +
            "${(scale * 10f).roundToInt()}:${(transmission * 20f).roundToInt()}"
        cache.get(key)?.let { return it }
        return make(kind, qWidth, qHeight, radius, sigma, pad, scale, transmission).also { if (it != null) cache.put(key, it) }
    }

    private fun make(
        kind: Int,
        width: Float,
        height: Float,
        radius: Float,
        sigma: Float,
        pad: Float,
        scale: Float,
        transmission: Float
    ): Bitmap? {
        val bw = ceil((width + pad * 2f) * scale).toInt().coerceAtLeast(1)
        val bh = ceil((height + pad * 2f) * scale).toInt().coerceAtLeast(1)
        if (bw > 2048 || bh > 2048) return null
        val bitmap = runCatching { Bitmap.createBitmap(bw, bh, Bitmap.Config.ALPHA_8) }.getOrNull() ?: return null
        val canvas = Canvas(bitmap)
        // (A matrix rather than canvas.scale, which the import checker reads as Compose's.)
        canvas.concat(android.graphics.Matrix().apply { setScale(scale, scale) })
        maskPaint.reset()
        maskPaint.isAntiAlias = true
        // A blur mask filter takes a radius: sigma = radius × 0.57735 + 0.5.
        val blurRadius = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.5f)
        maskPaint.maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
        maskPaint.color = android.graphics.Color.BLACK
        if (kind == POOL) {
            val t = transmission
            maskPaint.shader = LinearGradient(
                0f, pad, 0f, pad + height,
                intArrayOf(
                    android.graphics.Color.argb(0, 0, 0, 0),
                    android.graphics.Color.argb((t * 0.06f * 255).roundToInt(), 0, 0, 0),
                    android.graphics.Color.argb((t * 0.48f * 255).roundToInt(), 0, 0, 0),
                    android.graphics.Color.argb((t * 0.94f * 255).roundToInt(), 0, 0, 0)
                ),
                floatArrayOf(0f, 0.45f, 0.75f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        val r = radius.coerceAtMost(min(width, height) / 2f)
        canvas.drawRoundRect(pad, pad, pad + width, pad + height, r, r, maskPaint)
        maskPaint.shader = null
        maskPaint.maskFilter = null
        return bitmap
    }
}
