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
import kotlin.math.sqrt

val LocalImageStore = staticCompositionLocalOf<ImageStore> { error("No ImageStore") }

/**
 * A finished palette, not a bag of swatches for each caller to interpret.
 *
 * The previous version handed out raw extracted colours and let every screen
 * clamp them for its own background. That is unpredictable by construction: a
 * dark cover produced a legible page and a bright one produced highlighter red,
 * and each new failing cover earned another clamp bolted onto the last.
 *
 * Inverted here. **Hue and saturation carry the identity; lightness is pinned
 * by the theme.** Dark lands near L 0.13, light near L 0.91, always. The page is
 * legible by construction, and the hue is what makes it this show's page —
 * which is the part anyone actually recognises.
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
     * Four colours for the player's mesh, kept near the cover's true lightness
     * rather than pinned by the theme.
     *
     * The show page wants a theme-pinned ground: it is a list, it has to be
     * legible, and it sits next to the rest of the app. The player is a poster —
     * it fills the screen, it carries no list, and pinning its lightness flattens
     * exactly the thing that made it worth looking at. Different jobs, different
     * clamps.
     */
    val mesh: List<Color>,
    /** The mesh floor: the cover's dominant colour, extremes pulled back only. */
    val meshBase: Color,
    /** Black or white, whichever reads on [meshBase]. */
    val onMesh: Color,
    /** [accent] re-clamped for contrast against [meshBase], not the app ground. */
    val meshAccent: Color
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
 * Colours for a cover, cached for the session.
 *
 * [snapWhenKnown] reports whether the answer was already in the cache. There is
 * nothing to crossfade from when the colours were known up front, and animating
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

    val dominant = Color(generated.dominantSwatch?.rgb ?: found.first().rgb)

    /*
     * Accent by saturation × √population.
     *
     * The square root is the whole trick. Without it a cover that is four
     * fifths black sky accents in black, because area alone wins; with the
     * population term removed entirely, a single vivid pixel wins instead.
     * Damping the area term lets a small saturated mark beat a large flat one
     * without letting noise beat either.
     */
    val vibrant = Color(
        found.maxByOrNull { swatch ->
            val hsl = FloatArray(3).also { ColorUtils.colorToHSL(swatch.rgb, it) }
            hsl[1] * sqrt(swatch.population.toFloat())
        }?.rgb ?: found.first().rgb
    )

    val edge = bitmap.bottomEdgeColor()

    // The player's set. Distinct swatches, nudged rather than clamped, over a
    // base that keeps the cover's own lightness.
    val meshBase = dominant.withHsl({ it.coerceAtMost(0.92f) }, { it.coerceIn(0.08f, 0.93f) })
    val distinct = found.map { Color(it.rgb) }.distinctEnough()
    val meshColors = (if (distinct.size >= 4) distinct.take(4) else (distinct + distinct + distinct + distinct).take(4))
        .map { it.withHsl({ s -> (s * 1.12f).coerceAtMost(1f) }, { l -> l.coerceIn(0.12f, 0.90f) }) }
    val onMesh = if (meshBase.luminance() > 0.52f) Color(0xFF14141A) else Color(0xFFF7F7F9)
    val meshAccent = vibrant.withHsl(
        { it.coerceAtLeast(0.42f) },
        { if (meshBase.luminance() > 0.52f) it.coerceIn(0.26f, 0.46f) else it.coerceIn(0.58f, 0.76f) }
    )

    return if (dark) {
        ArtworkColors(
            background = dominant.withHsl({ it.coerceIn(0.20f, 0.62f) }, { 0.13f }),
            wash = edge.withHsl({ it.coerceIn(0.18f, 0.58f) }, { it.coerceIn(0.14f, 0.26f) }),
            elevated = dominant.withHsl({ it.coerceIn(0.20f, 0.62f) }, { 0.22f }),
            accent = vibrant.withHsl({ it.coerceAtLeast(0.55f) }, { it.coerceIn(0.62f, 0.78f) }),
            content = Color.White,
            // 0.80, not the usual 0.60: a tint is a coloured ground, not black,
            // so secondary text needs more of the content colour to separate.
            contentVariant = Color.White.copy(alpha = 0.80f),
            divider = Color.White.copy(alpha = 0.12f),
            mesh = meshColors,
            meshBase = meshBase,
            onMesh = onMesh,
            meshAccent = meshAccent
        )
    } else {
        ArtworkColors(
            background = dominant.withHsl({ it.coerceIn(0.16f, 0.52f) }, { 0.91f }),
            wash = edge.withHsl({ it.coerceIn(0.14f, 0.48f) }, { it.coerceIn(0.78f, 0.90f) }),
            elevated = dominant.withHsl({ it.coerceIn(0.16f, 0.52f) }, { 0.83f }),
            accent = vibrant.withHsl({ it.coerceAtLeast(0.50f) }, { it.coerceIn(0.30f, 0.44f) }),
            content = Color(0xFF101014),
            contentVariant = Color(0xFF101014).copy(alpha = 0.70f),
            divider = Color(0xFF101014).copy(alpha = 0.10f),
            mesh = meshColors,
            meshBase = meshBase,
            onMesh = onMesh,
            meshAccent = meshAccent
        )
    }
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
 * which colour is important, so the page has to match what the blur *produced*,
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
    hsl[1] = saturation(hsl[1]).coerceIn(0f, 1f)
    hsl[2] = lightness(hsl[2]).coerceIn(0f, 1f)
    return Color(ColorUtils.HSLToColor(hsl))
}

fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/*
 * Chrome colours — the mini player, tab bar and player panel.
 *
 * All dark, in both themes, as Cider's are: they're the player's surfaces, and
 * the player is dark. What changes with the episode is the hue. Each is the
 * cover's own colour pushed to a fixed lightness, so a red cover gives a deep
 * red bar and a grey one a neutral charcoal — the same rule that makes the
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
 * A button colour that is actually visible where it sits.
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
    var bitmap by remember(url) { mutableStateOf(store.peek(url, BACKDROP_PX)) }

    LaunchedEffect(url) {
        if (bitmap == null && url.isNotBlank()) bitmap = store.load(url, BACKDROP_PX)
    }

    /*
     * The fade masks the image's own alpha. It does NOT paint a colour over it.
     *
     * Painting a wash gradient here put a hard line across the page at the
     * header's bottom edge, and for a reason worth writing down: this overlay
     * runs 0→1 over the *header*, while the page ground behind it runs 0→1 over
     * the *screen*. Two gradients in different coordinate spaces cannot agree at
     * their shared boundary no matter which colours they use.
     *
     * With the image simply erased at its foot there is only one colour source —
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
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    // Scaled past the bounds so the blur has real pixels to
                    // sample at the edges instead of smearing the frame inward.
                    .scale(1.35f)
                    .blur(56.dp)
            )
        }
    }
}

private const val BACKDROP_PX = 200

/**
 * The page ground shared by the player and the show page: the artwork's edge
 * colour settling into the theme-pinned background.
 *
 * This replaces the four-blob mesh. Four fields at any real alpha average
 * toward one muddy tone, and every fix was another clamp on the blobs; two
 * colours that already agree with the artwork need no clamping, cost nothing
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
