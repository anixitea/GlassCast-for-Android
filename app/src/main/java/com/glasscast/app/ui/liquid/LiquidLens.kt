package com.glasscast.app.ui.liquid

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Liquid Glass: the third look's material (2.0).
 *
 * The optics are the lens shader from QWEA0/Liquid-Glass-Android (MIT; see
 * LIQUID-GLASS-ANDROID-LICENSE.txt), adapted for Compose. That library is a
 * View (a FrameLayout that records its parent); GlassCast is all Compose, so
 * the shader is ported and fed differently: the page is recorded into a
 * graphics layer once per frame (liquidBackdrop, the way Haze records its
 * source), and every piece of glass draws the part of that layer behind it
 * through a RenderEffect: the backdrop blurred, then the lens.
 *
 * What the lens does, per pixel, from a rounded box's distance field: inside
 * a band along the edge (the bevel) it samples the backdrop pulled inward,
 * hardest right at the rim (an inverse-power falloff), so the rim shows a
 * squeezed mirror of what's just inside it; the red, green and blue channels
 * bend by slightly different amounts, which fringes the rim with color; the
 * colors get the library's vibrancy curve; then a tint, and two thin lines of
 * light where the edge faces toward and away from the light (upper left).
 *
 * Android 13 and up. On 12 the glass is blurred and tinted with a drawn rim,
 * and below that it's a tinted pane. If the shader fails to build on some
 * GPU, the same fallback is used from then on; nothing crashes.
 *
 * Glass must never be inside the page it refracts: a piece that recorded
 * itself would draw itself forever. So the backdrop is only handed to the
 * chrome that floats over the page (LocalLiquidBackdrop), and glass drawn in
 * a page uses liquidPane instead, which is tint and rim without the lens.
 */

private const val LENS_AGSL = """
uniform shader content;
uniform float margin;
uniform float2 viewSize;
uniform float radius;
uniform float bevel;
uniform float refractPx;
uniform float falloff;
uniform float dispersion;
uniform float2 lightDir;
uniform float specStrength;
uniform float rimBand;
uniform float px;
uniform float4 tint;
uniform float adaptive;
uniform float satFactor;

float sdRoundedBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

float sdf(float2 p) {
    return sdRoundedBox(p - viewSize * 0.5, viewSize * 0.5, radius);
}

half4 main(float2 coord) {
    float2 p = coord - float2(margin, margin);
    float d = sdf(p);
    float cov = clamp(0.5 - d / 1.5, 0.0, 1.0);
    if (cov <= 0.004) {
        return half4(0.0);
    }

    float2 n = float2(sdf(p + float2(1.0, 0.0)) - sdf(p - float2(1.0, 0.0)),
                      sdf(p + float2(0.0, 1.0)) - sdf(p - float2(0.0, 1.0)));
    float nLen = length(n);
    n = nLen > 0.0001 ? n / nLen : float2(0.0, -1.0);

    // The bevel: 0 at the rim, 1 where the glass goes flat. The inverse-power
    // profile piles the bending up at the very edge.
    float t = clamp(-d / max(bevel, 1.0), 0.0, 1.0);
    float slope;
    if (falloff > 0.001) {
        float gB = pow(5.0, -falloff);
        slope = (pow(1.0 + 4.0 * t, -falloff) - gB) / (1.0 - gB);
    } else {
        float e = 1.0 - t;
        slope = e * e;
    }
    float2 offset = n * (-slope * refractPx);

    float2 lo = float2(margin + 1.0, margin + 1.0);
    float2 hi = float2(margin, margin) + viewSize - float2(1.0, 1.0);
    float2 cR = clamp(coord + offset * (1.0 - dispersion * slope), lo, hi);
    float2 cG = clamp(coord + offset, lo, hi);
    float2 cB = clamp(coord + offset * (1.0 + dispersion * slope), lo, hi);
    float3 col = float3(content.eval(cR).r, content.eval(cG).g, content.eval(cB).b);

    // Vibrancy: dull colors gain more than vivid ones, highlights are spared.
    float lum = dot(col, float3(0.2126, 0.7152, 0.0722));
    float satNow = max(col.r, max(col.g, col.b)) - min(col.r, min(col.g, col.b));
    float room = 1.0 - smoothstep(0.2, 0.85, satNow);
    float hl = 1.0 - smoothstep(0.75, 0.98, lum);
    float amount = 1.0 + (satFactor - 1.0) * mix(0.3, 1.0, room * hl);
    col = clamp(mix(float3(lum), col, amount), float3(0.0), float3(1.0));

    if (adaptive > 0.5) {
        // Lighter over dark, a touch darker over light, per pixel.
        float lt = dot(col, float3(0.2126, 0.7152, 0.0722));
        float e = smoothstep(0.35, 0.75, lt);
        col = mix(col, float3(1.0 - e), 0.14 + 0.08 * e);
    }
    col = mix(col, tint.rgb, tint.a);

    // Two lines of light along the rim, toward and away from the light, and
    // a little glow inside the lit one. Widths are in the library's pixels,
    // scaled by px for this screen's density.
    float facing = dot(n, -lightDir);
    float lobeF = pow(max(facing, 0.0), 4.5);
    float lobeB = pow(max(-facing, 0.0), 4.5);
    float dd = d / px;
    float bandW = clamp(bevel / px * 0.3, 2.0, rimBand);
    float glowIn = clamp((-dd - 1.0) / 2.0, 0.0, 1.0);
    float glow = glowIn * pow(clamp(1.0 - (-dd - 3.0) / bandW, 0.0, 1.0), 1.5) * cov;
    float hair = clamp(1.0 - abs(dd + 1.0) / 2.0, 0.0, 1.0) * cov;
    col += float3((hair * 0.70 * (lobeF + lobeB) + glow * 0.10 * lobeF) * specStrength);

    col = clamp(col, float3(0.0), float3(1.0));
    return half4(half3(col * cov), half(cov));
}
"""

/**
 * The Glass look's finish (Settings > Appearance > Glass): frosted, the
 * default, or liquid, clearer glass: about two thirds of the tint, a little
 * over half the blur, the edge bending a touch further with a little color,
 * and brighter rims (the factories below). The drawn panes follow it more
 * gently (PaneFinish).
 *
 * A snapshot state: the materials below read it as they're made, so the
 * glass that uses them is rebuilt when it changes. Set from
 * Settings.glassLiquid by GlassCastApp.followGlassFinish.
 */
object GlassFinish {
    var liquid by androidx.compose.runtime.mutableStateOf(false)
}

/**
 * How a piece of glass is made. [tint] is mixed over what it refracts (its
 * alpha is the strength); [adaptive] lets the glass lighten over dark and
 * darken over light by itself, as Apple's regular glass does. [blur] in dp.
 */
@androidx.compose.runtime.Immutable
data class LiquidMaterial(
    val tint: Color,
    val adaptive: Boolean = false,
    val blur: Dp = 7.dp,
    val spec: Float = 1f,
    val dispersion: Float = 0.10f,
    val saturation: Float = 1.4f,
    /** How far the edge bends what's behind it, against the usual lens (1). */
    val refraction: Float = 1f
) {
    companion object {
        /*
         * The bars: readable over anything. The first build's (a third white,
         * 7dp of blur, colors pushed 40%) let a red cover come through as
         * red, with a black label on it; at night the labels sank into it.
         * Now the blur is heavy enough that nothing under the bar can be
         * read, the tint is past half, and the colors are lifted only a
         * little, so the bar is tinted by what's under it, not painted.
         */
        fun regular(dark: Boolean) = if (GlassFinish.liquid) {
            // Liquid: clearer glass, about the first round's (which read as
            // glass) a step clearer still. Less tint, a little over half
            // the blur (still enough that text under the bar doesn't read),
            // the edge bending a touch further with a little color, and a
            // brighter rim. No sheen or adaptive tint here: on the bar they
            // read as a milky wash.
            LiquidMaterial(
                tint = if (dark) Color(0xFF1C1C1E).copy(alpha = 0.44f) else Color(0xFFFAFAFC).copy(alpha = 0.40f),
                blur = 6.5.dp,
                spec = 1.3f,
                dispersion = 0.14f,
                saturation = 1.35f,
                refraction = 1.1f
            )
        } else {
            LiquidMaterial(
                tint = if (dark) Color(0xFF1C1C1E).copy(alpha = 0.64f) else Color(0xFFFAFAFC).copy(alpha = 0.60f),
                blur = 11.dp,
                saturation = 1.2f
            )
        }
        /** A small button floating over a page: clearer, so the page reads through it as glass. */
        fun button(dark: Boolean) = if (GlassFinish.liquid) {
            LiquidMaterial(
                tint = if (dark) Color(0xFF1C1C1E).copy(alpha = 0.24f) else Color.White.copy(alpha = 0.30f),
                blur = 3.dp,
                spec = 1.45f,
                dispersion = 0.14f,
                saturation = 1.3f,
                refraction = 1.1f
            )
        } else {
            LiquidMaterial(
                tint = if (dark) Color(0xFF1C1C1E).copy(alpha = 0.34f) else Color.White.copy(alpha = 0.42f),
                blur = 5.dp,
                spec = 1.3f,
                saturation = 1.2f
            )
        }
        /** Over the player's picture: the picture shows through, softened, a step darker. */
        fun media() = if (GlassFinish.liquid) {
            LiquidMaterial(Color.Black.copy(alpha = 0.24f), blur = 11.dp, spec = 1.3f, dispersion = 0.14f, saturation = 1.25f, refraction = 1.1f)
        } else {
            LiquidMaterial(Color.Black.copy(alpha = 0.30f), blur = 18.dp, spec = 1.1f, saturation = 1.15f)
        }
        /**
         * The player's pull-up panel: [media] with more light on its rim and
         * a little less blur, so it reads as a slab of glass, not a dark bar.
         */
        fun panel() = if (GlassFinish.liquid) {
            LiquidMaterial(Color.Black.copy(alpha = 0.24f), blur = 9.dp, spec = 1.6f, dispersion = 0.14f, saturation = 1.25f, refraction = 1.1f)
        } else {
            LiquidMaterial(Color.Black.copy(alpha = 0.28f), blur = 15.dp, spec = 1.4f, saturation = 1.2f)
        }
        /** The panel's buttons gathered into a pill: glass set into the glass, as dark as the panel. */
        fun inset() = if (GlassFinish.liquid) {
            LiquidMaterial(Color.Black.copy(alpha = 0.20f), blur = 5.dp, spec = 1.6f, dispersion = 0.14f, saturation = 1.2f, refraction = 1.08f)
        } else {
            LiquidMaterial(Color.Black.copy(alpha = 0.24f), blur = 8.dp, spec = 1.45f, saturation = 1.15f)
        }
        /** A button over the player's picture: a disc of clear glass, a little lighter than what's under it. */
        fun disc() = if (GlassFinish.liquid) {
            LiquidMaterial(Color.White.copy(alpha = 0.10f), blur = 2.5.dp, spec = 1.5f, dispersion = 0.14f, saturation = 1.15f, refraction = 1.1f)
        } else {
            LiquidMaterial(Color.White.copy(alpha = 0.14f), blur = 4.dp, spec = 1.35f, saturation = 1.1f)
        }
    }
}

/** The page the chrome refracts, recorded every frame (see [liquidBackdrop]). */
@Stable
class LiquidBackdrop {
    /**
     * The page's ground color. Glass lays it down before the page, so where
     * a page leaves its ground to the window (most do), the lens sees the
     * ground rather than transparent black.
     */
    var ground: Color = Color.White
    internal var layer: GraphicsLayer? = null
    internal var origin: Offset = Offset.Zero
    internal var size: IntSize = IntSize.Zero
    /** False until the page has drawn into the layer at least once. */
    internal var ready: Boolean = false
}

/** Handed only to chrome that floats over the page; see the note at the top. */
val LocalLiquidBackdrop = staticCompositionLocalOf<LiquidBackdrop?> { null }

/** A pill of real glass over a page's own backdrop (for SeaControls' chromePill). */
object GlassPill {
    fun Modifier.lens(backdrop: LiquidBackdrop, dark: Boolean): Modifier =
        liquidGlass(backdrop, LiquidMaterial.button(dark), dark = dark)
}

/**
 * A page's own backdrop, so the buttons floating over it (a show's back and
 * info, Library's bar buttons) can be real glass too. The page's scrolling
 * content records into it ([liquidBackdrop] on the list), and the buttons,
 * drawn beside the list rather than in it, refract it: the same rule as the
 * tab bar's, one level down. [ground] is what the glass sees where the list
 * leaves the page bare. Null outside Frosted Glass.
 */
@androidx.compose.runtime.Composable
fun rememberPageBackdrop(ground: Color): LiquidBackdrop? {
    val on = LocalLiquid.current != null
    val backdrop = androidx.compose.runtime.remember { LiquidBackdrop() }
    backdrop.ground = ground
    return if (on) backdrop else null
}

/** Records what this draws, for glass over it to refract. Draws it as usual too. */
fun Modifier.liquidBackdrop(backdrop: LiquidBackdrop?): Modifier =
    if (backdrop == null) this else this.then(LiquidBackdropElement(backdrop))

private data class LiquidBackdropElement(val backdrop: LiquidBackdrop) : ModifierNodeElement<LiquidBackdropNode>() {
    override fun create() = LiquidBackdropNode(backdrop)
    override fun update(node: LiquidBackdropNode) {
        node.backdrop = backdrop
    }
}

private class LiquidBackdropNode(var backdrop: LiquidBackdrop) :
    Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {

    override fun ContentDrawScope.draw() {
        if (!drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
            drawContent()
            return
        }
        val layer = backdrop.layer?.takeUnless { it.isReleased }
            ?: requireGraphicsContext().createGraphicsLayer().also { backdrop.layer = it }
        layer.record { this@draw.drawContent() }
        drawLayer(layer)
        backdrop.ready = true
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        backdrop.origin = coordinates.positionInRoot()
        backdrop.size = coordinates.size
    }

    override fun onDetach() {
        backdrop.layer?.let { runCatching { requireGraphicsContext().releaseGraphicsLayer(it) } }
        backdrop.layer = null
        backdrop.ready = false
    }
}

/**
 * This element is a piece of liquid glass: a rounded box of [corner] (a
 * capsule when it's half the height or more) refracting [backdrop] behind
 * it. Its content draws on top, unrefracted, as content sits on glass.
 */
fun Modifier.liquidGlass(
    backdrop: LiquidBackdrop?,
    material: LiquidMaterial,
    corner: Dp = 999.dp,
    dark: Boolean
): Modifier = this.then(LiquidGlassElement(backdrop, material, corner, dark))

private data class LiquidGlassElement(
    val backdrop: LiquidBackdrop?,
    val material: LiquidMaterial,
    val corner: Dp,
    val dark: Boolean
) : ModifierNodeElement<LiquidGlassNode>() {
    override fun create() = LiquidGlassNode(backdrop, material, corner, dark)
    override fun update(node: LiquidGlassNode) {
        node.update(backdrop, material, corner, dark)
    }
}

private class LiquidGlassNode(
    var backdrop: LiquidBackdrop?,
    var material: LiquidMaterial,
    var corner: Dp,
    var dark: Boolean
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {

    private var position = Offset.Zero
    private var layer: GraphicsLayer? = null
    private var shader: Any? = null
    private var effectKey: Any? = null

    fun update(backdrop: LiquidBackdrop?, material: LiquidMaterial, corner: Dp, dark: Boolean) {
        if (backdrop !== this.backdrop || material != this.material || corner != this.corner || dark != this.dark) {
            this.backdrop = backdrop
            this.material = material
            this.corner = corner
            this.dark = dark
            effectKey = null
            invalidateDraw()
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val now = coordinates.positionInRoot()
        if (now != position) {
            position = now
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        val radius = min(corner.toPx(), min(size.width, size.height) / 2f)
        drawShadow(radius)
        val source = backdrop
        val drewGlass = source != null && source.ready && drawContext.canvas.nativeCanvas.isHardwareAccelerated &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { drawLens(source, radius) }.getOrDefault(false)
        if (!drewGlass) drawPane(radius)
        drawContent()
    }

    /** A soft drawn shadow under the glass (no platform shadows; see the 1.7 hang). */
    private fun DrawScope.drawShadow(radius: Float) {
        val base = if (dark) 0.30f else 0.10f
        val step = 3.dp.toPx()
        for (i in 1..3) {
            val grow = step * i
            drawRoundRect(
                color = Color.Black.copy(alpha = base / (i * 1.8f)),
                topLeft = Offset(-grow / 2f, -grow / 2f + 2.dp.toPx() * i),
                size = Size(size.width + grow, size.height + grow),
                cornerRadius = CornerRadius(radius + grow / 2f)
            )
        }
    }

    /** No lens: the tint, a little stronger so it stands on its own, and a drawn rim. */
    private fun DrawScope.drawPane(radius: Float) {
        val tint = material.tint
        drawRoundRect(
            color = tint.copy(alpha = (tint.alpha * 2.4f).coerceIn(0.72f, 0.94f)),
            cornerRadius = CornerRadius(radius)
        )
        drawRim(radius)
    }

    private fun DrawScope.drawRim(radius: Float) {
        drawRoundRect(
            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                0f to Color.White.copy(alpha = if (dark) 0.30f else 0.85f),
                0.45f to Color.White.copy(alpha = 0f),
                0.55f to Color.White.copy(alpha = 0f),
                1f to Color.White.copy(alpha = if (dark) 0.18f else 0.55f),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            ),
            cornerRadius = CornerRadius(radius),
            style = Stroke(width = 1.dp.toPx())
        )
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun ContentDrawScope.drawLens(source: LiquidBackdrop, radius: Float): Boolean {
        val content = source.layer?.takeUnless { it.isReleased } ?: return false
        val blurPx = material.blur.toPx()
        val margin = ceil(blurPx * 2f).toInt()
        val w = size.width.roundToInt()
        val h = size.height.roundToInt()
        if (w <= 0 || h <= 0) return false
        val layer = this@LiquidGlassNode.layer?.takeUnless { it.isReleased }
            ?: requireGraphicsContext().createGraphicsLayer().also { this@LiquidGlassNode.layer = it }

        val key = listOf(w, h, radius, material, dark, density)
        if (key != effectKey) {
            val built = buildEffect(w, h, margin, radius, blurPx) ?: return false
            layer.renderEffect = built.first.asComposeRenderEffect()
            lensOn = built.second
            // Without the lens the coverage isn't drawn by the shader, so the
            // layer clips to the shape and the tint and rim go on top.
            layer.clip = !lensOn
            if (!lensOn) {
                layer.setRoundRectOutline(Offset(margin.toFloat(), margin.toFloat()), Size(w.toFloat(), h.toFloat()), radius)
            }
            effectKey = key
        }
        layer.topLeft = IntOffset(-margin, -margin)
        val shift = position - source.origin
        val ground = source.ground
        layer.record(IntSize(w + margin * 2, h + margin * 2)) {
            drawRect(ground)
            translate(margin - shift.x, margin - shift.y) { drawLayer(content) }
        }
        drawLayer(layer)
        if (!lensOn) {
            drawRoundRect(material.tint, cornerRadius = CornerRadius(radius))
            drawRim(radius)
        }
        return true
    }

    private var lensOn = false

    /** The effect, and whether it has the lens in it (or is only the blur). */
    private fun ContentDrawScope.buildEffect(w: Int, h: Int, margin: Int, radius: Float, blurPx: Float): Pair<RenderEffect, Boolean>? {
        val blur = RenderEffect.createBlurEffect(blurPx.coerceAtLeast(0.6f), blurPx.coerceAtLeast(0.6f), Shader.TileMode.CLAMP)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || LensShader.broken) return blur to false
        val runtime: RuntimeShader = (shader as? RuntimeShader)
            ?: (LensShader.make() as? RuntimeShader)?.also { shader = it }
            ?: return blur to false
        // Sizes follow the library's adaptive lens scale: a small control
        // isn't all edge.
        val short = min(w, h).toFloat()
        val ref = 110.dp.toPx()
        val bevel = min(16.dp.toPx() * material.refraction, short * 0.3f * material.refraction.coerceAtMost(1.4f))
        val refract = min(53.dp.toPx(), 0.7f * short * min(1f, short / ref)) * material.refraction
        val px = density / 3f
        return runCatching {
            runtime.setFloatUniform("margin", margin.toFloat())
            runtime.setFloatUniform("viewSize", w.toFloat(), h.toFloat())
            runtime.setFloatUniform("radius", radius)
            runtime.setFloatUniform("bevel", bevel)
            runtime.setFloatUniform("refractPx", refract)
            runtime.setFloatUniform("falloff", 2f)
            runtime.setFloatUniform("dispersion", material.dispersion)
            runtime.setFloatUniform("lightDir", 0.866f, 0.5f)
            runtime.setFloatUniform("specStrength", material.spec)
            runtime.setFloatUniform("rimBand", (short * 0.05f / px).coerceIn(2f, 6f))
            runtime.setFloatUniform("px", px)
            val t = material.tint
            runtime.setFloatUniform("tint", t.red, t.green, t.blue, t.alpha)
            runtime.setFloatUniform("adaptive", if (material.adaptive) 1f else 0f)
            runtime.setFloatUniform("satFactor", material.saturation)
            val lens = RenderEffect.createRuntimeShaderEffect(runtime, "content")
            RenderEffect.createChainEffect(lens, blur) to true
        }.getOrElse {
            LensShader.broken = true
            blur to false
        }
    }

    override fun onDetach() {
        layer?.let { runCatching { requireGraphicsContext().releaseGraphicsLayer(it) } }
        layer = null
        effectKey = null
    }
}

internal object LensShader {
    @Volatile
    var broken = false

    /** A fresh shader for one piece of glass: each holds its own uniforms. */
    fun make(): Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !broken) {
        runCatching { RuntimeShader(LENS_AGSL) }.onFailure { broken = true }.getOrNull()
    } else {
        null
    }

    /** Builds one early, off the main thread, so the first bar doesn't wait on the compiler. */
    fun warm() {
        make()
    }
}

/**
 * This element's own content seen through a lens: a drop of glass laid on it.
 * The mini player's cover is drawn this way in Liquid Glass, so it sits in a
 * bubble whose rim bends the picture, with the light running round its edge.
 * Unlike [liquidGlass] nothing is recorded from elsewhere: the lens works on
 * what this element draws, so it's safe anywhere, a page included.
 *
 * Without the shader (Android 12 and below, or a GPU that won't build it) the
 * content is clipped to the shape and a highlight is drawn over it.
 */
fun Modifier.liquidLens(corner: Dp = 999.dp, spec: Float = 1.2f): Modifier =
    this.then(LiquidLensElement(corner, spec))

private data class LiquidLensElement(val corner: Dp, val spec: Float) : ModifierNodeElement<LiquidLensNode>() {
    override fun create() = LiquidLensNode(corner, spec)
    override fun update(node: LiquidLensNode) {
        if (node.corner != corner || node.spec != spec) {
            node.corner = corner
            node.spec = spec
            node.reset()
        }
    }
}

private class LiquidLensNode(var corner: Dp, var spec: Float) : Modifier.Node(), DrawModifierNode {
    private var layer: GraphicsLayer? = null
    private var shader: Any? = null
    private var effectKey: Any? = null
    private val clip = androidx.compose.ui.graphics.Path()

    fun reset() {
        effectKey = null
        invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        val radius = min(corner.toPx(), min(size.width, size.height) / 2f)
        val lensed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !LensShader.broken &&
            drawContext.canvas.nativeCanvas.isHardwareAccelerated &&
            runCatching { drawLensed(radius) }.getOrDefault(false)
        if (lensed) return
        clip.reset()
        clip.addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
        clipPath(clip) { this@draw.drawContent() }
        // The drawn stand-in: light caught at the upper left, a darker lower rim.
        drawRoundRect(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.38f),
                1f to Color.White.copy(alpha = 0f),
                center = Offset(size.width * 0.32f, size.height * 0.26f),
                radius = size.minDimension * 0.42f
            ),
            cornerRadius = CornerRadius(radius)
        )
        drawRoundRect(
            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.75f),
                0.5f to Color.White.copy(alpha = 0f),
                1f to Color.White.copy(alpha = 0.35f),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            ),
            cornerRadius = CornerRadius(radius),
            style = Stroke(width = 1.dp.toPx())
        )
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun ContentDrawScope.drawLensed(radius: Float): Boolean {
        val w = size.width.roundToInt()
        val h = size.height.roundToInt()
        if (w <= 0 || h <= 0) return false
        val runtime: RuntimeShader = (shader as? RuntimeShader)
            ?: (LensShader.make() as? RuntimeShader)?.also { shader = it }
            ?: return false
        val layer = this@LiquidLensNode.layer?.takeUnless { it.isReleased }
            ?: requireGraphicsContext().createGraphicsLayer().also { this@LiquidLensNode.layer = it }
        val key = listOf(w, h, radius, spec, density)
        if (key != effectKey) {
            val short = min(w, h).toFloat()
            val px = density / 3f
            runtime.setFloatUniform("margin", 0f)
            runtime.setFloatUniform("viewSize", w.toFloat(), h.toFloat())
            runtime.setFloatUniform("radius", radius)
            // A small round drop: the bevel is most of it, so the whole
            // picture swells toward the middle the way a bead of water does.
            runtime.setFloatUniform("bevel", short * 0.42f)
            runtime.setFloatUniform("refractPx", short * 0.16f)
            runtime.setFloatUniform("falloff", 2f)
            runtime.setFloatUniform("dispersion", 0.12f)
            runtime.setFloatUniform("lightDir", 0.866f, 0.5f)
            runtime.setFloatUniform("specStrength", spec)
            runtime.setFloatUniform("rimBand", (short * 0.06f / px).coerceIn(2f, 6f))
            runtime.setFloatUniform("px", px)
            runtime.setFloatUniform("tint", 1f, 1f, 1f, 0.04f)
            runtime.setFloatUniform("adaptive", 0f)
            runtime.setFloatUniform("satFactor", 1.12f)
            layer.renderEffect = RenderEffect.createRuntimeShaderEffect(runtime, "content").asComposeRenderEffect()
            effectKey = key
        }
        layer.record { this@drawLensed.drawContent() }
        drawLayer(layer)
        return true
    }

    override fun onDetach() {
        layer?.let { runCatching { requireGraphicsContext().releaseGraphicsLayer(it) } }
        layer = null
        effectKey = null
    }
}

/**
 * Glass drawn inside a page, where there's no backdrop to refract (see the
 * note at the top): the tint over the page, and the rim's light.
 */
fun Modifier.liquidPane(color: Color, corner: Dp = 999.dp, dark: Boolean): Modifier =
    this.drawBehind {
        val radius = min(corner.toPx(), min(size.width, size.height) / 2f)
        val light = PaneFinish.light
        drawRoundRect(PaneFinish.fill(color), cornerRadius = CornerRadius(radius))
        if (GlassFinish.liquid) drawPaneSheen(radius, dark)
        drawRoundRect(
            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                0f to Color.White.copy(alpha = ((if (dark) 0.26f else 0.9f) * light).coerceAtMost(1f)),
                0.45f to Color.White.copy(alpha = 0f),
                0.55f to Color.White.copy(alpha = 0f),
                1f to Color.White.copy(alpha = ((if (dark) 0.14f else 0.6f) * light).coerceAtMost(1f)),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            ),
            cornerRadius = CornerRadius(radius),
            style = Stroke(width = PaneFinish.rim.toPx())
        )
    }

/**
 * What the finish does to a drawn pane (cards, Settings sections, fields,
 * small buttons). These don't blur what's under them, so the liquid finish
 * can only make them a little clearer: a quarter less body where the pane
 * is already see-through, a tenth less where it's nearly solid, and brighter
 * rims. Never more: a drawn pane over content with half its body was
 * unreadable (the Downloads menu, sixth round). Anything that floats over
 * content (menus) opts out (`frostedGlass(finish = false)`).
 * Read while drawing, so a change of finish only redraws.
 */
internal object PaneFinish {
    fun fill(c: Color): Color = when {
        !GlassFinish.liquid -> c
        c.alpha >= 0.85f -> c.copy(alpha = c.alpha * 0.9f)
        else -> c.copy(alpha = c.alpha * 0.75f)
    }
    val light: Float get() = if (GlassFinish.liquid) 1.4f else 1f
    val rim: Dp get() = if (GlassFinish.liquid) 1.2.dp else 1.dp
}

/** The liquid finish's light on a drawn pane: a sheen along the top and a faint glare in the top left. */
internal fun DrawScope.drawPaneSheen(radius: Float, dark: Boolean) {
    drawRoundRect(
        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
            0f to Color.White.copy(alpha = if (dark) 0.08f else 0.20f),
            0.45f to Color.White.copy(alpha = 0f)
        ),
        cornerRadius = CornerRadius(radius)
    )
    drawRoundRect(
        brush = androidx.compose.ui.graphics.Brush.radialGradient(
            0f to Color.White.copy(alpha = if (dark) 0.07f else 0.14f),
            1f to Color.White.copy(alpha = 0f),
            center = Offset(size.width * 0.12f, size.height * 0.15f),
            radius = maxOf(minOf(size.minDimension * 1.6f, size.maxDimension * 0.6f), 1f)
        ),
        cornerRadius = CornerRadius(radius)
    )
}
