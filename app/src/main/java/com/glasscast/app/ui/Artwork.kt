package com.glasscast.app.ui

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.glasscast.app.R
import com.glasscast.app.data.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt

val LocalImageStore = staticCompositionLocalOf<ImageStore> { error("No ImageStore") }

/**
 * A finished palette, not a bag of swatches for each caller to interpret.
 *
 * The previous version handed out raw extracted colors and let every screen
 * clamp them for its own background. That is unpredictable by construction: a
 * dark cover produced a legible page and a bright one produced highlighter red,
 * and each new failing cover earned another clamp bolted onto the last.
 *
 * Inverted here. **Hue and saturation carry the identity; lightness is pinned
 * by the theme.** Dark lands near L 0.13, light near L 0.91, always. The page is
 * legible by construction, and the hue is what makes it this show's page —
 * which is the part anyone actually recognizes.
 */
@Immutable
data class ArtworkColors(
    /** The page ground. */
    val background: Color,
    /** What the artwork's bottom edge blurs down to. See [bottomEdgeColor]. */
    val wash: Color,
    /** Pills, chips and glass buttons sitting on [background]. */
    val elevated: Color,
    /** Play, scrubber fill, links. */
    val accent: Color,
    val content: Color,
    val contentVariant: Color,
    val divider: Color,
    /**
     * Four colors for the player's mesh, kept near the cover's true lightness
     * rather than pinned by the theme.
     *
     * The show page wants a theme-pinned ground: it is a list, it has to be
     * legible, and it sits next to the rest of the app. The player is a poster —
     * it fills the screen, it carries no list, and pinning its lightness flattens
     * exactly the thing that made it worth looking at. Different jobs, different
     * clamps.
     */
    val mesh: List<Color>,
    /** The mesh floor: the cover's dominant color, extremes pulled back only. */
    val meshBase: Color,
    /** Black or white, whichever reads on [meshBase]. */
    val onMesh: Color,
    /** [accent] re-clamped for contrast against [meshBase], not the app ground. */
    val meshAccent: Color,
    /** Episode cards on the show page: kept apart from the cover's blur. Unspecified → [elevated]. */
    val card: Color = Color.Unspecified
) {
    /** White or near-black, whichever reads on [accent]. */
    val onAccent: Color
        get() = if (accent.luminance() > 0.62f) Color(0xFF101014) else Color.White
}

private const val PALETTE_PX = 160

/** Sized so the pre-extraction fallback and the real answer share a shape. */
private fun neutralColors(dark: Boolean, brand: Color): ArtworkColors = if (dark) {
    ArtworkColors(
        background = Color(0xFF17171B),
        wash = Color(0xFF202027),
        elevated = Color(0xFF2A2A31),
        accent = brand,
        content = Color.White,
        contentVariant = Color.White.copy(alpha = 0.80f),
        divider = Color.White.copy(alpha = 0.12f),
        mesh = List(4) { Color(0xFF1E1E23) },
        meshBase = Color(0xFF17171B),
        onMesh = Color.White,
        meshAccent = brand
    )
} else {
    ArtworkColors(
        background = Color(0xFFF4F4F6),
        wash = Color(0xFFE8E8EC),
        elevated = Color(0xFFDFDFE5),
        accent = brand,
        content = Color(0xFF101014),
        contentVariant = Color(0xFF101014).copy(alpha = 0.70f),
        divider = Color(0xFF101014).copy(alpha = 0.10f),
        mesh = List(4) { Color(0xFFE8E8EC) },
        meshBase = Color(0xFFF4F4F6),
        onMesh = Color(0xFF101014),
        meshAccent = brand
    )
}

private val paletteCache = object : LruCache<String, ArtworkColors>(64) {}

/**
 * Colors for a cover, cached for the session.
 *
 * [snapWhenKnown] reports whether the answer was already in the cache. There is
 * nothing to crossfade from when the colors were known up front, and animating
 * anyway just puts a delay in front of a surface that could already be right —
 * so callers snap in that case and only fade a genuinely new cover in.
 */
@Composable
fun rememberArtworkColors(
    url: String,
    dark: Boolean,
    brand: Color
): Pair<ArtworkColors, Boolean> {
    val store = LocalImageStore.current
    val key = "$url@${if (dark) "d" else "l"}"
    val seed = remember(key) { paletteCache.get(key) }
    val knownUpFront = remember(key) { seed != null }

    var colors by remember(key) { mutableStateOf(seed ?: neutralColors(dark, brand)) }

    LaunchedEffect(key) {
        if (url.isBlank() || paletteCache.get(key) != null) return@LaunchedEffect
        val bitmap = store.load(url, PALETTE_PX) ?: return@LaunchedEffect
        val extracted = withContext(Dispatchers.Default) { extract(bitmap, dark, brand) }
        paletteCache.put(key, extracted)
        colors = extracted
    }

    return colors to knownUpFront
}

/** The common case: the theme decides light/dark and the brand supplies the fallback. */
@Composable
fun rememberArtworkColors(url: String): Pair<ArtworkColors, Boolean> = rememberArtworkColors(
    url = url,
    dark = com.glasscast.app.ui.theme.LocalIsDark.current,
    brand = com.glasscast.app.ui.theme.LocalBrandAccent.current
)

private fun extract(bitmap: Bitmap, dark: Boolean, brand: Color): ArtworkColors {
    var generated = Palette.from(bitmap).maximumColorCount(24).generate()
    if (generated.swatches.isEmpty()) {
        // The default filter discards near-black and near-white, which on a
        // monochrome cover is the whole image.
        generated = Palette.from(bitmap).clearFilters().maximumColorCount(24).generate()
    }

    val found = generated.swatches
    if (found.isEmpty()) return neutralColors(dark, brand)

    /*
     * Colorfulness is chroma, not HSL saturation.
     *
     * HSL calls a pale cream about 50% saturated — its saturation is measured
     * against how little room there is near white. Ranked by that, the cream
     * background of a cream-and-teal cover won the accent, and carried to an
     * accent's lightness at the same saturation it turned olive: dark yellow
     * reads as green. That was the chartreuse. Chroma (the RGB spread) says the
     * same cream is nearly gray, which is what the eye sees.
     *
     * Swatches under [NEUTRAL_CHROMA] are neutrals — pooled into one family of
     * their own, never the accent. The rest pool into 30° hue families.
     *
     * The ground (dominant) is now the biggest *family*, not the biggest single
     * swatch. A flat color — a line of lime lettering — lands in one swatch,
     * while a textured wall of grays is split across a dozen; the letters won
     * and the whole page went lime. Pooled, a family's whole area counts.
     *
     * The accent is still chroma × √population per family (the square root
     * lets a small vivid mark beat a large dull area without letting noise
     * beat either), a family needs 4% of the cover to qualify, and with none
     * that large, 1.5% — below that the cover is treated as having no accent
     * color and stays neutral rather than promoting a speck.
     */
    val total = found.sumOf { it.population }.coerceAtLeast(1)
    class Family(
        var population: Int,
        var best: Palette.Swatch,
        var bestScore: Float,
        var chroma: Float,
        var biggest: Palette.Swatch
    )
    val families = HashMap<Int, Family>() // -1 holds the neutrals
    found.forEach { swatch ->
        val c = Color(swatch.rgb).chroma()
        val hsl = FloatArray(3).also { ColorUtils.colorToHSL(swatch.rgb, it) }
        val bin = if (c < NEUTRAL_CHROMA) -1 else (hsl[0] / 30f).toInt().coerceIn(0, 11)
        val score = c * sqrt(swatch.population.toFloat())
        val f = families[bin]
        if (f == null) {
            families[bin] = Family(swatch.population, swatch, score, c, swatch)
        } else {
            f.population += swatch.population
            if (score > f.bestScore) { f.best = swatch; f.bestScore = score; f.chroma = c }
            if (swatch.population > f.biggest.population) f.biggest = swatch
        }
    }
    fun Family.rank() = chroma * sqrt(population.toFloat())
    val colored = families.filterKeys { it >= 0 }.values
    // The ground is a color whenever color is at least a fifth of the cover
    // (neutrals no more than four times all the colors together): the
    // largest color family. Only a cover that is overwhelmingly neutral — The
    // Broski Report is ~90% cream and teal-gray — gets a neutral page. Letting
    // neutrals win whenever they were the biggest family (first 1.4 build)
    // turned red-and-black-and-white covers griege and The Toast gray.
    val neutralPopulation = families[-1]?.population ?: 0
    val coloredPopulation = colored.sumOf { it.population }
    val groundFamily = if (colored.isNotEmpty() && coloredPopulation * 4 >= neutralPopulation) {
        colored.maxByOrNull { it.population }
    } else {
        families[-1] ?: colored.maxByOrNull { it.population }
    }
    val dominantSwatch = groundFamily?.biggest ?: generated.dominantSwatch ?: found.first()
    val dominant = Color(dominantSwatch.rgb)
    val vibrantSwatch = colored.filter { it.population >= total * 0.04f }.maxByOrNull { it.rank() }?.best
        ?: colored.filter { it.population >= total * 0.015f }.maxByOrNull { it.rank() }?.best
        ?: dominantSwatch
    val vibrant = Color(vibrantSwatch.rgb)

    // What the cover averages to — its white and black included, which the
    // palette discards. The show page's blur is this color, more or less.
    val average = bitmap.averageColor()
    val averageL = FloatArray(3).also { ColorUtils.colorToHSL(average.toArgb(), it) }[2]

    val edge = bitmap.bottomEdgeColor()

    // A neutral source gives a neutral color at any lightness. The ranges
    // below put a floor under saturation, which on a gray or a cream lifted
    // its faint cast into a real tint — usually yellow-green.
    val dominantNeutral = dominant.chroma() < NEUTRAL_CHROMA
    val vibrantNeutral = vibrant.chroma() < NEUTRAL_CHROMA
    val edgeNeutral = edge.chroma() < NEUTRAL_CHROMA

    // The player's set. Distinct swatches, nudged rather than clamped, over a
    // base that keeps the cover's own lightness.
    val meshBase = dominant.tinted(dominantNeutral, { it.coerceAtMost(0.92f) }, { it.coerceIn(0.08f, 0.93f) })
    val distinct = found.map { Color(it.rgb) }.distinctEnough()
    val meshColors = (if (distinct.size >= 4) distinct.take(4) else (distinct + distinct + distinct + distinct).take(4))
        .map { it.withHsl({ s -> (s * 1.12f).coerceAtMost(1f) }, { l -> l.coerceIn(0.12f, 0.90f) }) }
    val onMesh = if (meshBase.luminance() > 0.52f) Color(0xFF14141A) else Color(0xFFF7F7F9)
    val meshAccent = vibrant.tinted(vibrantNeutral, 
        { it.coerceAtLeast(0.42f) },
        { if (meshBase.luminance() > 0.52f) it.coerceIn(0.26f, 0.46f) else it.coerceIn(0.58f, 0.76f) }
    )

    return if (dark) {
        ArtworkColors(
            background = dominant.tinted(dominantNeutral, { it.coerceIn(0.20f, 0.62f) }, { 0.13f }),
            wash = edge.tinted(edgeNeutral, { it.coerceIn(0.18f, 0.58f) }, { it.coerceIn(0.14f, 0.26f) }),
            elevated = dominant.tinted(dominantNeutral, { it.coerceIn(0.20f, 0.62f) }, { 0.22f }),
            accent = vibrant.tinted(vibrantNeutral, { it.coerceAtLeast(0.55f) }, { it.coerceIn(0.62f, 0.78f) }),
            content = Color.White,
            // 0.80, not the usual 0.60: a tint is a colored ground, not black,
            // so secondary text needs more of the content color to separate.
            contentVariant = Color.White.copy(alpha = 0.80f),
            divider = Color.White.copy(alpha = 0.12f),
            mesh = meshColors,
            meshBase = meshBase,
            onMesh = onMesh,
            meshAccent = meshAccent,
            // Lifted clear of a dark blur; otherwise the usual dark card.
            card = dominant.tinted(dominantNeutral, 
                { it.coerceIn(0.20f, 0.62f) },
                { if (averageL < 0.42f) (averageL + 0.16f).coerceIn(0.24f, 0.40f) else 0.22f }
            )
        )
    } else {
        ArtworkColors(
            background = dominant.tinted(dominantNeutral, { it.coerceIn(0.16f, 0.52f) }, { 0.91f }),
            wash = edge.tinted(edgeNeutral, { it.coerceIn(0.14f, 0.48f) }, { it.coerceIn(0.78f, 0.90f) }),
            elevated = dominant.tinted(dominantNeutral, { it.coerceIn(0.16f, 0.52f) }, { 0.83f }),
            accent = vibrant.tinted(vibrantNeutral, { it.coerceAtLeast(0.50f) }, { it.coerceIn(0.30f, 0.44f) }),
            content = Color(0xFF101014),
            contentVariant = Color(0xFF101014).copy(alpha = 0.70f),
            divider = Color(0xFF101014).copy(alpha = 0.10f),
            mesh = meshColors,
            meshBase = meshBase,
            onMesh = onMesh,
            meshAccent = meshAccent,
            // A light pastel, a little under a light blur so it never dissolves
            // into it. It used to sit 16% under, which on a light cover made
            // mid-tone cards (The Toast's slate, Trixie & Katya's brick red).
            card = dominant.tinted(dominantNeutral, 
                { it.coerceIn(0.14f, 0.40f) },
                { if (averageL > 0.60f) (averageL - 0.06f).coerceIn(0.78f, 0.84f) else 0.83f }
            )
        )
    }
}

/** The mean color of the whole bitmap, from an 8×8 reduction. */
private fun android.graphics.Bitmap.averageColor(): Color {
    val small = android.graphics.Bitmap.createScaledBitmap(this, 8, 8, true)
    var r = 0L
    var g = 0L
    var b = 0L
    for (y in 0 until 8) for (x in 0 until 8) {
        val px = small.getPixel(x, y)
        r += android.graphics.Color.red(px)
        g += android.graphics.Color.green(px)
        b += android.graphics.Color.blue(px)
    }
    if (small !== this) small.recycle()
    return Color(r / 64f / 255f, g / 64f / 255f, b / 64f / 255f)
}

/** Drop near-duplicates, so the mesh's blobs don't collapse into one wash. */
private fun List<Color>.distinctEnough(): List<Color> {
    val kept = mutableListOf<Color>()
    forEach { candidate ->
        if (kept.none { it.isCloseTo(candidate) }) kept += candidate
    }
    return kept
}

private fun Color.isCloseTo(other: Color): Boolean {
    val a = FloatArray(3).also { ColorUtils.colorToHSL(toArgb(), it) }
    val b = FloatArray(3).also { ColorUtils.colorToHSL(other.toArgb(), it) }
    val hueGap = kotlin.math.abs(a[0] - b[0]).let { minOf(it, 360f - it) }
    return hueGap < 15f && kotlin.math.abs(a[2] - b[2]) < 0.12f
}

/**
 * The flat mean of the artwork's bottom 18% — what a blur wide enough to lose
 * the picture actually leaves behind at that edge.
 *
 * A mean and not a quantised swatch, deliberately: a blur has no notion of
 * which color is important, so the page has to match what the blur *produced*,
 * not what the picture is about. Start the page from this where the artwork
 * ends and it reads as the blur carrying on rather than a second surface
 * starting.
 */
private fun Bitmap.bottomEdgeColor(): Color {
    val band = (height * 0.18f).toInt().coerceIn(1, height)
    val pixels = IntArray(width * band)
    getPixels(pixels, 0, width, 0, height - band, width, band)

    var r = 0L
    var g = 0L
    var b = 0L
    pixels.forEach {
        r += (it shr 16) and 0xFF
        g += (it shr 8) and 0xFF
        b += it and 0xFF
    }
    val n = pixels.size.coerceAtLeast(1)
    return Color((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
}

private fun Color.withHsl(
    saturation: (Float) -> Float,
    lightness: (Float) -> Float
): Color {
    val hsl = FloatArray(3).also { ColorUtils.colorToHSL(toArgb(), it) }
    val sourceChroma = chroma()
    val l = lightness(hsl[2]).coerceIn(0f, 1f)
    var s = saturation(hsl[1]).coerceIn(0f, 1f)
    // Keep the result's chroma near the source's. Moving a light color toward
    // mid lightness at the same saturation multiplies its chroma — a cream at
    // 0.9 becomes a strong khaki at 0.4. Up to 1.5× is allowed, so real colors
    // still get their lift; a near-gray can't be inflated into a hue.
    val room = 1f - abs(2f * l - 1f)
    if (room > 0.001f) s = s.coerceAtMost(sourceChroma * 1.5f / room)
    // Yellows turn toward gold at every lightness. Dark, a yellow reads as
    // olive-green; light, on the dark theme, a lemon is a highlighter, and the
    // containers tinted from it go olive on dark gray. Gold stays warm.
    var h = hsl[0]
    if (s * room >= 0.06f && h in 45f..75f) h = 40f + (h - 45f) * 0.3f
    return Color(ColorUtils.HSLToColor(floatArrayOf(h, s, l)))
}

/** [withHsl], but a [neutral] source keeps at most a faint cast. */
private fun Color.tinted(
    neutral: Boolean,
    saturation: (Float) -> Float,
    lightness: (Float) -> Float
): Color = withHsl({ if (neutral) it.coerceAtMost(0.08f) else saturation(it) }, lightness)

/** The RGB spread, 0–1: how far from gray a color actually looks. */
private fun Color.chroma(): Float = maxOf(red, green, blue) - minOf(red, green, blue)

/** Below this chroma a swatch is treated as a neutral (a cream is ~0.09). */
private const val NEUTRAL_CHROMA = 0.14f

fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/*
 * Chrome colors — the mini player, tab bar and player panel.
 *
 * All dark, in both themes, as Cider's are: they're the player's surfaces, and
 * the player is dark. What changes with the episode is the hue. Each is the
 * cover's own color pushed to a fixed lightness, so a red cover gives a deep
 * red bar and a gray one a neutral charcoal — the same rule that makes the
 * pages legible, applied to the chrome.
 */
/** The mini player's card: light enough to sit above the bar. */
val ArtworkColors.chromeSurface: Color
    get() = meshBase.withHsl({ it.coerceAtMost(0.36f) }, { 0.22f })

/** The tab bar: the same hue a step darker, so the two stack as one object. */
val ArtworkColors.chromeBar: Color
    get() = meshBase.withHsl({ it.coerceAtMost(0.32f) }, { 0.14f })

/** A pale tone of the accent for filled buttons on the chrome — Cider's pink pill. */
val ArtworkColors.chromeButton: Color
    get() = meshAccent.withHsl({ it.coerceIn(0.22f, 0.62f) }, { 0.82f })

/** The player's pull-up panel: darker again, and opaque enough to read on. */
val ArtworkColors.panelSurface: Color
    get() = meshBase.withHsl({ it.coerceAtMost(0.30f) }, { 0.12f })

/**
 * Ad breaks on the progress line: a second color from the cover, as far in
 * hue from the accent as the cover allows (40° at least), so a break reads as
 * a different stretch of the same line. A one-color cover gets its accent,
 * darker, which still separates from a white or pale line.
 */
val ArtworkColors.adMark: Color
    get() {
        fun hue(c: Color) = FloatArray(3).also { ColorUtils.colorToHSL(c.toArgb(), it) }[0]
        fun gap(a: Float, b: Float) = abs(a - b).let { minOf(it, 360f - it) }
        val accentHue = hue(meshAccent)
        val other = mesh
            .filter { it.chroma() >= NEUTRAL_CHROMA }
            .maxByOrNull { gap(hue(it), accentHue) }
            ?.takeIf { gap(hue(it), accentHue) >= 40f }
        return (other ?: meshAccent).withHsl({ it.coerceIn(0.5f, 0.9f) }, { if (other != null) 0.70f else 0.58f })
    }

/**
 * Re-clamp an artwork accent for the app's own ground, which is not the
 * player's. Skipping this puts a 0.7-lightness yellow tuned for a dark player
 * onto a white library screen, where it is a highlighter.
 */
fun themeAccent(accent: Color, darkTheme: Boolean): Color = accent.withHsl(
    saturation = { it.coerceIn(0.50f, 0.95f) },
    lightness = { if (darkTheme) it.coerceIn(0.56f, 0.74f) else it.coerceIn(0.28f, 0.42f) }
)

/** Mean luminance of the bottom-right 38% — the region a corner control covers. */
fun cornerLuminance(bitmap: Bitmap?): Float? {
    if (bitmap == null) return null
    val w = (bitmap.width * 0.38f).toInt().coerceIn(1, bitmap.width)
    val h = (bitmap.height * 0.38f).toInt().coerceIn(1, bitmap.height)
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, bitmap.width - w, bitmap.height - h, w, h)

    var sum = 0.0
    pixels.forEach {
        val r = ((it shr 16) and 0xFF) / 255f
        val g = ((it shr 8) and 0xFF) / 255f
        val b = (it and 0xFF) / 255f
        sum += 0.2126f * r + 0.7152f * g + 0.0722f * b
    }
    return (sum / pixels.size.coerceAtLeast(1)).toFloat()
}

/**
 * The show page's Play button: the cover's own background color.
 *
 * What people see as a cover's color is usually its ground — the sky behind
 * Trixie & Katya, the teal wall behind Brittany Broski, Pod Save America's
 * blue — not the accent, which the check button and the author line already
 * carry. So this reads the band around the cover's edges, groups it by hue,
 * and takes the biggest colored group (at least 15% of the band), made rich
 * enough to be a button. Chartreuse (65–100°) is skipped — it makes an ugly
 * button — and yellows turn gold, as everywhere else.
 *
 * Falls back to the accent, then to black or white, only when the color
 * doesn't clear the page it sits on. (It used to be checked against the
 * cover's bottom-right corner, which suits a control over the artwork — but
 * this button sits below the cover, so a blue accent on a blue cover went
 * black or white for no reason.)
 */
fun showPlayColors(cover: Bitmap?, accent: Color, ground: Color, dark: Boolean): Pair<Color, Color> {
    fun clears(c: Color) = abs(c.luminance() - ground.luminance()) >= 0.18f
    val edge = cover?.let { coverEdgeColor(it) }?.let { e ->
        val hsl = FloatArray(3).also { ColorUtils.colorToHSL(e.toArgb(), it) }
        var h = hsl[0]
        if (h in 45f..65f) h = 40f + (h - 45f) * 0.3f
        val l = if (dark) hsl[2].coerceIn(0.50f, 0.68f) else hsl[2].coerceIn(0.32f, 0.55f)
        Color(ColorUtils.HSLToColor(floatArrayOf(h, hsl[1].coerceIn(0.45f, 0.85f), l)))
    }
    val fill = edge?.takeIf(::clears)
        ?: accent.takeIf(::clears)
        ?: if (dark) Color.White else Color(0xFF101014)
    return fill to (if (fill.luminance() > 0.5f) Color(0xFF101014) else Color.White)
}

/**
 * The cover's background hue: the band around its edges, grouped into 30° hue
 * bins and read two neighboring bins at a time (a teal wall straddles 180°).
 * The top band counts double and the bottom half — covers put their subject
 * at the foot and their background at the top, so Trixie & Katya's sky beats
 * their outfits. Near-grays, pale creams, near-black and near-white, and
 * chartreuse don't count. Null when no hue reaches 15% of the band.
 *
 * Tuned on five covers cropped from screenshots: Broski → teal, Trixie &
 * Katya → cyan, Evolution of a Snake → red, The Toast and Deutschland3000 →
 * none (their accents, pink and purple).
 */
private fun coverEdgeColor(bitmap: Bitmap): Color? {
    val n = 48
    val small = Bitmap.createScaledBitmap(bitmap, n, n, true)
    val band = 5
    val counts = FloatArray(12)
    val r = FloatArray(12)
    val g = FloatArray(12)
    val b = FloatArray(12)
    var ring = 0f
    val hsl = FloatArray(3)
    for (y in 0 until n) for (x in 0 until n) {
        if (x in band until n - band && y in band until n - band) continue
        val w = when {
            y < band -> 2f
            y >= n - band -> 0.5f
            else -> 1f
        }
        ring += w
        val px = small.getPixel(x, y)
        ColorUtils.colorToHSL(px, hsl)
        val rr = android.graphics.Color.red(px) / 255f
        val gg = android.graphics.Color.green(px) / 255f
        val bb = android.graphics.Color.blue(px) / 255f
        val chroma = maxOf(rr, gg, bb) - minOf(rr, gg, bb)
        if (hsl[2] < 0.05f || hsl[2] > 0.95f || chroma < 0.04f || hsl[1] < 0.20f) continue
        if (hsl[2] > 0.8f && chroma < 0.15f) continue
        if (hsl[0] in 65f..100f) continue
        val bin = (hsl[0] / 30f).toInt().coerceIn(0, 11)
        counts[bin] += w
        r[bin] += rr * w
        g[bin] += gg * w
        b[bin] += bb * w
    }
    if (small !== bitmap) small.recycle()
    val best = (0 until 12).maxByOrNull { counts[it] + counts[(it + 1) % 12] } ?: return null
    val next = (best + 1) % 12
    val total = counts[best] + counts[next]
    if (total < ring * 0.15f) return null
    return Color((r[best] + r[next]) / total, (g[best] + g[next]) / total, (b[best] + b[next]) / total)
}

/**
 * A button color that is actually visible where it sits.
 *
 * The accent comes from the whole cover, so a bright flash in the corner the
 * button occupies gives a yellow button on a yellow stripe. If the accent
 * doesn't clear a 0.20 luminance gap against that corner, fall back to a
 * neutral — a plain white button beats an invisible on-brand one.
 */
fun visibleOn(accent: Color, against: Float?): Pair<Color, Color> {
    val corner = against ?: return accent to (
        if (accent.luminance() > 0.62f) Color(0xFF101014) else Color.White
        )
    return if (kotlin.math.abs(accent.luminance() - corner) >= 0.20f) {
        accent to (if (accent.luminance() > 0.62f) Color(0xFF101014) else Color.White)
    } else if (corner > 0.5f) {
        Color(0xFF15151A) to Color.White
    } else {
        Color.White to Color(0xFF15151A)
    }
}

/**
 * The cover as the page's backdrop: cropped to the header band, blurred hard,
 * and dissolved into [ArtworkColors.wash] — which is the mean of the artwork's
 * own bottom edge, so there is no line where one stops and the other starts.
 */
@Composable
fun ArtworkBackdrop(
    url: String,
    colors: ArtworkColors,
    modifier: Modifier = Modifier
) {
    val store = LocalImageStore.current
    // Softened once, off the main thread (SoftBitmaps.kt). This was a live
    // 56dp Modifier.blur that scrolled with the page, recomputed every frame.
    var soft by remember(url) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(url) {
        if (url.isBlank()) return@LaunchedEffect
        val source = store.peek(url, BACKDROP_PX) ?: store.load(url, BACKDROP_PX) ?: return@LaunchedEffect
        soft = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            softened(source, width = 40).asImageBitmap()
        }
    }

    /*
     * The fade masks the image's own alpha. It does NOT paint a color over it.
     *
     * Painting a wash gradient here put a hard line across the page at the
     * header's bottom edge, and for a reason worth writing down: this overlay
     * runs 0→1 over the *header*, while the page ground behind it runs 0→1 over
     * the *screen*. Two gradients in different coordinate spaces cannot agree at
     * their shared boundary no matter which colors they use.
     *
     * With the image simply erased at its foot there is only one color source —
     * the page ground, drawn once, spanning the whole screen — so there is
     * nothing left to disagree.
     */
    Box(
        modifier
            .clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0.00f to Color.Black,
                        0.42f to Color.Black,
                        0.66f to Color.Black.copy(alpha = 0.80f),
                        0.86f to Color.Black.copy(alpha = 0.34f),
                        1.00f to Color.Transparent
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
    ) {
        val image = soft
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low,   // bilinear: the stretch is the blur
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.35f)
            )
        }
    }
}

private const val BACKDROP_PX = 200

/**
 * The page ground shared by the player and the show page: the artwork's edge
 * color settling into the theme-pinned background.
 *
 * This replaces the four-blob mesh. Four fields at any real alpha average
 * toward one muddy tone, and every fix was another clamp on the blobs; two
 * colors that already agree with the artwork need no clamping, cost nothing
 * per frame, and remove the seam the mesh existed to hide.
 */
fun artworkGround(colors: ArtworkColors): Brush = Brush.verticalGradient(
    0.00f to colors.wash,
    0.34f to colors.wash,
    0.72f to colors.background,
    1.00f to colors.background
)

/**
 * Never decodes during composition — a synchronous cache peek covers the first
 * frame, then the load happens off the main thread. The same Bitmap instance
 * comes back for the same key, so the row doesn't look changed to Compose.
 */
@Composable
fun Artwork(
    url: String,
    sizeDp: Dp,
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp,
    /** Square that fills its parent's width. [sizeDp] is then only the decode hint. */
    fill: Boolean = false,
    onBitmap: (Bitmap?) -> Unit = {}
) {
    val store = LocalImageStore.current
    val px = with(LocalDensity.current) { sizeDp.roundToPx() }

    var bitmap by remember(url, px) { mutableStateOf(store.peek(url, px)) }

    LaunchedEffect(url, px) {
        if (bitmap == null && url.isNotBlank()) bitmap = store.load(url, px)
        onBitmap(bitmap)
    }

    Box(
        modifier
            .then(if (fill) Modifier.fillMaxWidth().aspectRatio(1f) else Modifier.size(sizeDp))
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_mark),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(sizeDp * 0.28f)
            )
        }
    }
}
