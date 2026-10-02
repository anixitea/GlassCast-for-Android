package com.glasscast.app.tv

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
        .then(FocusIndicationElement(shape, accent, scale, 3.dp, Color.Transparent))
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
        .then(FocusIndicationElement(shape, accent, 1f, 2.dp, surface.copy(alpha = 0.12f)))
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
    val fill: Color
) : ModifierNodeElement<FocusIndicationNode>() {
    override fun create() = FocusIndicationNode(shape, accent, scale, ringWidth, fill)
    override fun update(node: FocusIndicationNode) = node.update(shape, accent, scale, ringWidth, fill)
}

private class FocusIndicationNode(
    private var shape: Shape,
    private var accent: Color,
    private var growTo: Float,
    private var ringWidth: Dp,
    private var fill: Color
) : Modifier.Node(), FocusEventModifierNode, DrawModifierNode {

    private val level = Animatable(0f)
    private var focused = false

    private var outline: Outline? = null
    private var outlineSize = Size.Unspecified
    private var outlineDirection: LayoutDirection? = null

    fun update(shape: Shape, accent: Color, scale: Float, ringWidth: Dp, fill: Color) {
        if (shape != this.shape) outline = null
        this.shape = shape
        this.accent = accent
        this.growTo = scale
        this.ringWidth = ringWidth
        this.fill = fill
        invalidateDraw()
    }

    override fun onFocusEvent(focusState: FocusState) {
        val now = focusState.isFocused
        if (now == focused) return
        focused = now
        coroutineScope.launch {
            level.animateTo(
                targetValue = if (now) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
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
private fun Modifier.holdSelect(onClick: () -> Unit, onLongClick: () -> Unit): Modifier {
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
