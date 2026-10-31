package com.glasscast.app.tv

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

/**
 * The focus vocabulary: on a TV there is no finger, so the interface has to
 * say "you are here" for every item, continuously. The focused item **grows**
 * and gains a **ring** in the cover's pale accent; list rows, which can't grow
 * without disturbing the layout, **fill** instead.
 *
 * ## Why this is a Modifier.Node
 *
 * The first version animated with `animateFloatAsState` and fed the value to
 * `border(width = …)`. Both reads happened during composition, so every frame
 * of the focus spring — about 300ms — recomposed the entire focused item: its
 * artwork, its text, everything. A D-pad press animates two items (the one
 * losing focus and the one gaining it), so each press meant two full
 * recompositions per frame for the length of the spring. On the Streamer's
 * chip that was the stutter on every move. `border` with an animated width
 * also rebuilt its drawing node every frame.
 *
 * Here the animation lives in a node and is read only in `draw()`. A focus
 * change costs a handful of draw calls per frame and never recomposes or
 * re-lays-out anything. The outline is cached per size and shape.
 */
@Composable
fun Modifier.tvFocusable(
    shape: Shape = RoundedCornerShape(14.dp),
    accent: Color,
    scale: Float = 1.07f,
    enabled: Boolean = true,
    /** Holding select opens this instead of clicking. See [holdSelect]. */
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this
        .then(if (onLongClick != null && enabled) Modifier.holdSelect(onClick, onLongClick) else Modifier)
        .then(FocusIndicationElement(shape, accent, scale, 3.dp, Color.Transparent, glass = tvLiquid()))
        .clip(shape)
        .focusable(enabled = enabled, interactionSource = interaction)
        .clickable(
            enabled = enabled,
            interactionSource = interaction,
            // No ripple: a touch idiom, and on a focused-but-unclicked item it
            // reads as a second, competing highlight.
            indication = null,
            onClick = onClick
        )
}

/**
 * A focusable row, where growing would disturb the layout. A fill and the
 * ring carry focus instead.
 */
@Composable
fun Modifier.tvFocusableRow(
    accent: Color,
    surface: Color,
    shape: Shape = RoundedCornerShape(12.dp),
    enabled: Boolean = true,
    /** Holding select opens this, while still held — as on Discover. See [holdSelect]. */
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this
        .then(if (onLongClick != null && enabled) Modifier.holdSelect(onClick, onLongClick) else Modifier)
        .then(FocusIndicationElement(shape, accent, 1f, 2.dp, surface.copy(alpha = 0.12f), glass = tvLiquid()))
        .clip(shape)
        .focusable(enabled = enabled, interactionSource = interaction)
        .clickable(
            enabled = enabled,
            interactionSource = interaction,
            indication = null,
            onClick = onClick
        )
}

private data class FocusIndicationElement(
    val shape: Shape,
    val accent: Color,
    val scale: Float,
    val ringWidth: Dp,
    val fill: Color,
    /** The Glass look: focus is glass catching the light, not a ring (see [FocusIndicationNode.drawGlass]). */
    val glass: Boolean = false
) : ModifierNodeElement<FocusIndicationNode>() {
    override fun create() = FocusIndicationNode(shape, accent, scale, ringWidth, fill, glass)
    override fun update(node: FocusIndicationNode) = node.update(shape, accent, scale, ringWidth, fill, glass)
}

private class FocusIndicationNode(
    private var shape: Shape,
    private var accent: Color,
    private var growTo: Float,
    private var ringWidth: Dp,
    private var fill: Color,
    private var glass: Boolean
) : Modifier.Node(), FocusEventModifierNode, DrawModifierNode {

    private val level = Animatable(0f)
    private var focused = false

    private var outline: Outline? = null
    private var outlineSize = Size.Unspecified
    private var outlineDirection: LayoutDirection? = null

    fun update(shape: Shape, accent: Color, scale: Float, ringWidth: Dp, fill: Color, glass: Boolean) {
        if (shape != this.shape) outline = null
        this.shape = shape
        this.accent = accent
        this.growTo = scale
        this.ringWidth = ringWidth
        this.fill = fill
        this.glass = glass
        invalidateDraw()
    }

    override fun onFocusEvent(focusState: FocusState) {
        val now = focusState.isFocused
        if (now == focused) return
        focused = now
        coroutineScope.launch {
            // Quick, with no bounce. The bouncy spring took about half a
            // second to settle on every press, so with the D-pad held the
            // whole screen looked like it was moving in slow motion.
            level.animateTo(
                targetValue = if (now) 1f else 0f,
                animationSpec = tween(if (now) 140 else 110, easing = FastOutSlowInEasing)
            )
        }
    }

    override fun ContentDrawScope.draw() {
        // Read here, and only here: this is what keeps a focus change out of
        // composition and layout entirely.
        val p = level.value
        if (p <= 0.001f) {
            drawContent()
            return
        }
        if (outline == null || outlineSize != size || outlineDirection != layoutDirection) {
            outline = shape.createOutline(size, layoutDirection, this)
            outlineSize = size
            outlineDirection = layoutDirection
        }
        val shapeOutline = outline!!
        val grow = 1f + (growTo - 1f) * p
        if (glass) {
            scale(grow) { this@draw.drawGlass(shapeOutline, p) }
            return
        }
        scale(grow) {
            if (fill.alpha > 0f) drawOutline(shapeOutline, fill.copy(alpha = fill.alpha * p))
            this@draw.drawContent()
            drawOutline(
                shapeOutline,
                accent.copy(alpha = p),
                style = Stroke(width = ringWidth.toPx() * p)
            )
        }
    }

    /**
     * Glass focus. A row lights up as a pane of glass under it (a body with
     * a breath of the cover's color, light along its top, a rim of light);
     * a cover or a card, which fills its shape, rises with a soft shadow
     * under it and catches the light: a sheen over its top, a glare in its
     * top left and a bright rim, as tvOS lifts a poster. No ring.
     */
    private fun ContentDrawScope.drawGlass(shapeOutline: Outline, p: Float) {
        if (fill.alpha > 0f) {
            drawOutline(shapeOutline, androidx.compose.ui.graphics.lerp(Color.White, accent, 0.25f).copy(alpha = 0.14f * p))
            drawOutline(
                shapeOutline,
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.12f * p),
                    0.5f to Color.White.copy(alpha = 0f),
                    startY = 0f,
                    endY = size.height
                )
            )
            this.drawContent()
            drawOutline(
                shapeOutline,
                androidx.compose.ui.graphics.Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.6f * p),
                    0.5f to Color.White.copy(alpha = 0.05f * p),
                    1f to Color.White.copy(alpha = 0.25f * p),
                    start = androidx.compose.ui.geometry.Offset.Zero,
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height)
                ),
                style = Stroke(width = 1.5.dp.toPx())
            )
            return
        }
        val drop = 4.dp.toPx()
        for (i in 1..3) {
            translate(0f, drop * i * 0.8f) {
                drawOutline(shapeOutline, Color.Black.copy(alpha = 0.16f * p / i))
            }
        }
        this.drawContent()
        drawOutline(
            shapeOutline,
            androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.22f * p),
                0.42f to Color.White.copy(alpha = 0f),
                startY = 0f,
                endY = size.height
            )
        )
        drawOutline(
            shapeOutline,
            androidx.compose.ui.graphics.Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.20f * p),
                1f to Color.White.copy(alpha = 0f),
                center = androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.12f),
                radius = maxOf(size.minDimension * 0.75f, 1f)
            )
        )
        drawOutline(
            shapeOutline,
            androidx.compose.ui.graphics.Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.9f * p),
                0.5f to Color.White.copy(alpha = 0.15f * p),
                1f to Color.White.copy(alpha = 0.45f * p),
                start = androidx.compose.ui.geometry.Offset.Zero,
                end = androidx.compose.ui.geometry.Offset(size.width, size.height)
            ),
            style = Stroke(width = 2.5.dp.toPx() * p)
        )
    }
}

// ------------------------------------------------------------ scrolling

/**
 * How every TV list scrolls to the item that takes focus. Critically damped,
 * so a new focus while a scroll is still moving carries its speed on; the
 * default restarted from a standstill on every press, and holding the D-pad
 * crawled.
 */
internal val TvScrollSpring: AnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = 650f)

/**
 * Scrolls only as far as it must, so the focused item (grown, with its ring)
 * sits at least [marginPx] inside the list's edges.
 *
 * Without this, Compose on a device with Android TV's leanback feature pins
 * every focused item 30% of the way in. Rows jumped as focus moved, and the
 * first cards of a shelf were sliced at its edge.
 */
@OptIn(ExperimentalFoundationApi::class)
class TvBringIntoView(private val marginPx: Float) : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> = TvScrollSpring

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        // A list too small for both margins keeps what it can.
        val margin = marginPx.coerceAtMost(((containerSize - size) / 2f).coerceAtLeast(0f))
        val start = margin
        val end = containerSize - margin
        return when {
            offset < start -> offset - start
            offset + size > end -> offset + size - end
            else -> 0f
        }
    }
}

/** [TvBringIntoView] for this screen's density. Provided once, at the TV root. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberTvBringIntoView(margin: Dp = 48.dp): BringIntoViewSpec {
    val px = with(LocalDensity.current) { margin.toPx() }
    return remember(px) { TvBringIntoView(px) }
}

// ------------------------------------------------------------ hold select

/** Select, OK or Enter — the keys a remote or keyboard "clicks" with. */
internal fun KeyEvent.isSelectKey(): Boolean =
    key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter

private class HoldState {
    var job: Job? = null
    var fired = false
}

/**
 * Hold select to open [onLongClick]; a short press is [onClick].
 *
 * Built by hand because Compose 1.7's `combinedClickable` has no long press
 * for keys at all — only for touch. The key events are taken in preview, ahead
 * of the clickable, and consumed: a clickable in 1.7 clicks on *any* key-up,
 * so if the release reached it after a hold, the show would open underneath
 * its own menu. A press that starts here and ends here is the click.
 *
 * The timer starts on the first key-down only; while the button is held the
 * remote sends repeats, which are swallowed. If focus leaves mid-press the
 * timer is dropped, so a menu never opens over something else.
 */
@Composable
internal fun Modifier.holdSelect(onClick: () -> Unit, onLongClick: () -> Unit): Modifier {
    val scope = rememberCoroutineScope()
    val timeout = LocalViewConfiguration.current.longPressTimeoutMillis
    val click by rememberUpdatedState(onClick)
    val long by rememberUpdatedState(onLongClick)
    val state = remember { HoldState() }
    return this
        .onFocusChanged {
            if (!it.hasFocus) {
                state.job?.cancel()
                state.job = null
            }
        }
        .onPreviewKeyEvent { e ->
            if (!e.isSelectKey()) return@onPreviewKeyEvent false
            when (e.type) {
                KeyEventType.KeyDown -> {
                    if (e.nativeKeyEvent.repeatCount == 0) {
                        state.job?.cancel()
                        state.fired = false
                        state.job = scope.launch {
                            delay(timeout)
                            state.fired = true
                            long()
                        }
                    }
                    true
                }
                KeyEventType.KeyUp -> {
                    val job = state.job
                    state.job = null
                    if (job != null && !state.fired) {
                        job.cancel()
                        click()
                    }
                    state.fired = false
                    true
                }
                else -> false
            }
        }
}

private class HeldGate {
    var fresh = false
}

/**
 * For anything that opens while select is still held (a hold menu): ignore
 * that press until a new one begins.
 *
 * Focus moves into the menu while the remote's button is still down, so the
 * key's repeats and its release arrive at the menu's first item — and a 1.7
 * clickable clicks on any release. Without this, letting go of the button
 * that opened the menu would choose its first option.
 */
@Composable
fun Modifier.ignoreHeldSelect(): Modifier {
    val gate = remember { HeldGate() }
    return onPreviewKeyEvent { e ->
        when {
            !e.isSelectKey() || gate.fresh -> false
            e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0 -> {
                gate.fresh = true
                false
            }
            else -> true
        }
    }
}
