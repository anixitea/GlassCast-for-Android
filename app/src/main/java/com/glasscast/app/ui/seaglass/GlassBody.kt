package com.glasscast.app.ui.seaglass

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The glass itself, as a runtime shader (Android 13 and up).
 *
 * The optics are the Sea Glass library's (see SeaGlass.kt): frost and grain as
 * layered value noise, a body that's deeper in the middle and pales toward its
 * edges, a soft reflection where the light from the upper left meets the dome,
 * a glancing highlight on the upper shoulder, pale pits where the surface is
 * worn, and the luminous lower edge where light that came through the glass
 * leaves it again. The geometry is GlassContour's: the distance in from the
 * tumbled edge is worked out here per pixel, so the shading follows the exact
 * outline the path draws, at any size.
 *
 * Positions are in dp (the coordinates divided by [density]), so a piece
 * looks the same on every screen.
 */
private const val BODY_AGSL = """
    uniform float2 size;
    uniform float density;
    uniform float4 outline;
    uniform float4 tumble;
    uniform float4 recipeA;
    uniform float4 recipeB;
    uniform float3 bodyColor;
    uniform float3 edgeColor;
    uniform float2 lightDirection;
    uniform float3 halfway;

    float hashNoise(float2 point, float salt) {
        float3 p = fract(float3(point.x, point.y, point.x) * 0.1031 + salt);
        p += dot(p, p.yzx + 33.33);
        return fract((p.x + p.y) * p.z) * 2.0 - 1.0;
    }

    float noiseGrid(float2 point, float spacing, float salt) {
        float2 cell = point / spacing;
        float2 index = floor(cell);
        float2 fraction = fract(cell);
        float2 eased = fraction * fraction * (3.0 - 2.0 * fraction);
        float a = hashNoise(index, salt);
        float b = hashNoise(index + float2(1.0, 0.0), salt);
        float c = hashNoise(index + float2(0.0, 1.0), salt);
        float d = hashNoise(index + float2(1.0, 1.0), salt);
        return mix(mix(a, b, eased.x), mix(c, d, eased.x), eased.y);
    }

    float3 noiseGridGradient(float2 point, float spacing, float salt) {
        float2 cell = point / spacing;
        float2 index = floor(cell);
        float2 fraction = fract(cell);
        float2 eased = fraction * fraction * (3.0 - 2.0 * fraction);
        float2 derivative = 6.0 * fraction * (1.0 - fraction) / spacing;
        float a = hashNoise(index, salt);
        float b = hashNoise(index + float2(1.0, 0.0), salt);
        float c = hashNoise(index + float2(0.0, 1.0), salt);
        float d = hashNoise(index + float2(1.0, 1.0), salt);
        float value = mix(mix(a, b, eased.x), mix(c, d, eased.x), eased.y);
        float dx = mix(b - a, d - c, eased.y) * derivative.x;
        float dy = mix(c - a, d - b, eased.x) * derivative.y;
        return float3(value, dx, dy);
    }

    float smoothMax(float a, float b, float k) {
        float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
        return mix(a, b, h) + k * h * (1.0 - h);
    }

    // Distance in from the tumbled edge, in dp. outline: half width, half height,
    // corner radius, crease smoothing. tumble: amplitude, two phases, unused.
    float edgeDistance(float2 position) {
        float2 p = position - size * 0.5;
        float2 q = abs(p) - outline.xy + outline.z;
        float signedDistance = length(max(q, 0.0)) + min(smoothMax(q.x, q.y, outline.w), 0.0) - outline.z;
        float angle = length(p) > 0.001 ? atan(p.y, p.x) : 0.0;
        float ripple = sin(2.0 * angle + tumble.y) * 0.75 + sin(3.0 * angle + tumble.z) * 0.2;
        return -signedDistance + tumble.x * ripple;
    }

    half4 main(float2 coordinates) {
        float2 position = coordinates / density;
        float frost = recipeA.x;
        float grain = recipeA.y;
        float thickness = recipeA.z;
        float bulge = recipeA.w;
        float transmission = recipeB.x;
        float seed = recipeB.y;
        float maxDistance = recipeB.z;
        float glow = recipeB.w;
        float shortSide = min(size.x, size.y);

        float fine = noiseGrid(position, 0.68, seed + 0.11);
        float3 micro = noiseGridGradient(position, 1.7, seed + 0.29);
        float worn = noiseGrid(position, 7.5, seed + 0.47);
        float cloud = noiseGrid(position, shortSide * 0.44, seed + 0.71) * 0.75 + worn * 0.25;
        float roughness = fine * 0.54 + micro.x * 0.32 + worn * 0.14;
        float2 roughGradient = micro.yz * (0.32 + min(position, float2(0.32)));

        float delta = 0.35;
        float inward = max(edgeDistance(position), 0.0);
        float2 toward = float2(
            edgeDistance(position + float2(delta, 0.0)) - edgeDistance(position - float2(delta, 0.0)),
            edgeDistance(position + float2(0.0, delta)) - edgeDistance(position - float2(0.0, delta))
        );
        float2 direction = toward / max(length(toward), 0.0001);

        float roll = shortSide * (0.205 + cloud * 0.023);
        float rising = exp(-2.0 * inward / roll);
        float dome = (1.0 - rising) / (1.0 + rising);
        float domeSlope = (1.0 - dome * dome) / roll;
        float q = clamp(inward / maxDistance, 0.0, 1.0);
        float arch = sqrt(q * (2.0 - q));
        float archSlope = (1.0 - q) / (maxDistance * max(arch, 0.035));
        float u = clamp(position.x / size.x, 0.0, 1.0);
        float along = sin(u * 3.14159265);
        float lengthArch = 0.86 + 0.14 * along * along;
        float lengthSlope = 0.14 * 3.14159265 / size.x * sin(u * 6.2831853);
        float roundDome = arch * lengthArch;
        float2 gradient = direction * domeSlope;
        float2 roundGradient = direction * archSlope * lengthArch + float2(arch * lengthSlope, 0.0);

        float domeHeight = mix(dome, roundDome, bulge);
        float2 slope = mix(gradient, roundGradient, bulge);
        float rise = 0.9 + min(thickness * 1.55, shortSide * 0.22) + bulge * shortSide * 0.34;
        float3 shapeNormal = normalize(float3(-slope * rise, 1.0));
        shapeNormal.z = clamp(shapeNormal.z, 0.0, 1.0);
        float rough = grain * (0.025 + frost * 0.04);
        float3 normal = normalize(float3(-slope * rise + roughGradient * rough, 1.0));
        float travel = clamp(position.y / size.y, 0.0, 1.0);
        float flow = travel * travel * (3.0 - 2.0 * travel);
        float depth = (1.05 - flow * transmission * 0.13)
            * (0.70 + domeHeight * (0.52 + thickness * 0.012))
            * (1.0 + (1.0 - shapeNormal.z) * max(0.0, -shapeNormal.y) * 0.28)
            * (1.0 + cloud * 0.035);
        float weathering = frost * (0.06 + (cloud + 1.0) * 0.025);
        float3 tint = bodyColor * 0.96 + edgeColor * 0.04;
        float3 color = 1.0 - (1.0 - tint) * depth;
        color += (1.0 - color) * weathering;

        float lx = lightDirection.x;
        float ly = lightDirection.y;
        float lightZ = 0.52;
        float lightSide = sqrt(1.0 - lightZ * lightZ);
        float3 light = float3(lx * lightSide, ly * lightSide, lightZ);
        float diffuse = max(0.0, dot(normal, light));
        float3 reflectionNormal = float3(2.0 * normal.xy * normal.z, 2.0 * normal.z * normal.z - 1.0);
        float reflectedLight = max(0.0, dot(reflectionNormal, light));
        float across = -ly * reflectionNormal.x + lx * reflectionNormal.y;
        float lengthwise = -lightZ * (lx * reflectionNormal.x + ly * reflectionNormal.y) + lightSide * reflectionNormal.z;
        float acrossScale = across / (0.78 + frost * 0.24);
        float alongScale = lengthwise / (0.46 + frost * 0.24);
        float reflection = exp(-(acrossScale * acrossScale + alongScale * alongScale))
            * clamp(reflectedLight / 0.30, 0.0, 1.0) * (0.15 + bulge * 0.12) * (0.82 + frost * 0.18);
        float scatter = pow(max(0.0, dot(normal, halfway)), 12.0 + (1.0 - frost) * 18.0) * (0.008 + frost * 0.012);
        float upperScale = (normal.y + 0.40) / 0.38;
        float upperTurn = exp(-(upperScale * upperScale)) * pow(max(normal.z, 0.000001), 0.55)
            * (0.10 + bulge * 0.14) * (0.85 + frost * 0.15);
        float palePits = pow(max(0.0, roughness + 0.20), 2.0) * grain
            * (5.0 + frost * 10.0) * (0.65 + diffuse * 0.35);
        float matte = roughness * grain * (0.012 + frost * 0.016);
        float grazing = sqrt(max(0.0, 1.0 - shapeNormal.z * shapeNormal.z));
        float lowerFacing = clamp((shapeNormal.y + 0.16) / 0.80, 0.0, 1.0);
        float shoulder = pow(grazing, 2.3) * lowerFacing * lowerFacing * (3.0 - 2.0 * lowerFacing);
        float exitAmount = clamp((transmission + glow * 0.35) * (0.10 * flow + 1.65 * shoulder)
            * (0.96 - thickness * 0.005), 0.0, 0.96);
        float exitCatch = pow(max(0.0, 1.0 - shapeNormal.z), 2.2) * lowerFacing * 0.85;
        color += (1.0 - color) * (reflection + scatter + upperTurn + glow * 0.06);
        float3 exitColor = edgeColor + (1.0 - edgeColor) * exitCatch;
        color += (exitColor - color) * exitAmount;
        color += (palePits / 255.0 + matte) * (1.0 - exitAmount * 0.45);
        return half4(clamp(color, 0.0, 1.0), 1.0);
    }
"""

internal object GlassBody {
    /*
     * One shader for every piece, compiled once. Each draw sets its uniforms
     * and then draws: the canvas takes a snapshot of the shader with those
     * uniforms when the draw is recorded, and setting a uniform afterwards
     * makes a fresh one, so pieces drawn one after another each keep their
     * own. Compiling it is the only slow part (a few milliseconds), so it can
     * be started early on a background thread (warm).
     */
    private val runtime: Any? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { RuntimeShader(BODY_AGSL.trimIndent()) }.getOrNull()
        } else {
            null
        }
    }

    /** Compile the shader ahead of the first piece, off the main thread. */
    fun warm() {
        runtime
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * Draws the body of [contour] (already outlined into [path], in pixels)
     * at the current origin.
     */
    fun DrawScope.drawBody(
        contour: GlassContour,
        path: Path,
        recipe: GlassRecipe,
        pigment: Pigment,
        glow: Float
    ) {
        val shader = runtime
        if (shader != null && !broken && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // A uniform the platform's compiler doesn't recognize throws when
            // set. That would be the same for every piece, so it's taken as a
            // sign to use the drawn glass from then on, never as a crash.
            val drawn = runCatching { drawWithShader(shader as RuntimeShader, contour, path, recipe, pigment, glow) }.isSuccess
            if (!drawn) {
                broken = true
                drawFallback(contour, path, pigment, glow)
            }
        } else {
            drawFallback(contour, path, pigment, glow)
        }
    }

    @Volatile
    private var broken = false

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun DrawScope.drawWithShader(
        shader: RuntimeShader,
        contour: GlassContour,
        path: Path,
        recipe: GlassRecipe,
        pigment: Pigment,
        glow: Float
    ) {
        val theta = Math.toRadians(recipe.light.toDouble())
        val lx = sin(theta).toFloat()
        val ly = -cos(theta).toFloat()
        val lz = 0.52f
        val side = sqrt(1f - lz * lz)
        val hl = sqrt((lx * side) * (lx * side) + (ly * side) * (ly * side) + (lz + 1f) * (lz + 1f))
        shader.setFloatUniform("size", contour.width, contour.height)
        shader.setFloatUniform("density", density)
        shader.setFloatUniform("outline", contour.halfWidth, contour.halfHeight, contour.radius, contour.smoothing)
        shader.setFloatUniform("tumble", contour.amplitude, contour.phase0, contour.phase1, 0f)
        shader.setFloatUniform("recipeA", recipe.frost, recipe.grain, recipe.thickness, recipe.bulge)
        shader.setFloatUniform("recipeB", recipe.transmission, contour.grainSeed, contour.maxDistance, glow)
        shader.setFloatUniform("bodyColor", pigment.body.red, pigment.body.green, pigment.body.blue)
        shader.setFloatUniform("edgeColor", pigment.edge.red, pigment.edge.green, pigment.edge.blue)
        shader.setFloatUniform("lightDirection", lx, ly)
        shader.setFloatUniform("halfway", lx * side / hl, ly * side / hl, (lz + 1f) / hl)
        paint.shader = shader
        drawIntoCanvas { it.nativeCanvas.drawPath(path.asAndroidPath(), paint) }
    }

    /**
     * Below Android 13: the same piece drawn with gradients. A body deepening
     * downward, the light's soft highlight at the upper left, the lit lower
     * edge, and a pale rim. It reads as glass, without the frost.
     */
    private fun DrawScope.drawFallback(contour: GlassContour, path: Path, pigment: Pigment, glow: Float) {
        val w = contour.width * density
        val h = contour.height * density
        val short = min(w, h)
        val body = pigment.body
        val edge = pigment.edge
        val top = mix(body, edge, 0.30f + glow * 0.2f)
        val bottom = mix(body, Color.Black, 0.06f)
        val android = path.asAndroidPath()
        drawIntoCanvas { canvas ->
            val c = canvas.nativeCanvas
            fallbackPaint.style = Paint.Style.FILL
            fallbackPaint.shader = LinearGradient(0f, 0f, 0f, h, top.toArgb(), bottom.toArgb(), Shader.TileMode.CLAMP)
            c.drawPath(android, fallbackPaint)
            fallbackPaint.shader = RadialGradient(
                w * 0.32f, h * 0.26f, short * 0.62f,
                Color.White.copy(alpha = 0.34f + glow * 0.1f).toArgb(), Color.White.copy(alpha = 0f).toArgb(),
                Shader.TileMode.CLAMP
            )
            c.drawPath(android, fallbackPaint)
            fallbackPaint.shader = LinearGradient(
                0f, h * 0.62f, 0f, h,
                edge.copy(alpha = 0f).toArgb(), edge.copy(alpha = 0.62f).toArgb(),
                Shader.TileMode.CLAMP
            )
            c.drawPath(android, fallbackPaint)
            fallbackPaint.shader = null
            val save = c.save()
            c.clipPath(android)
            fallbackPaint.style = Paint.Style.STROKE
            fallbackPaint.strokeWidth = 2.4f * density
            fallbackPaint.color = edge.copy(alpha = 0.55f).toArgb()
            c.drawPath(android, fallbackPaint)
            c.restoreToCount(save)
            fallbackPaint.style = Paint.Style.FILL
        }
    }

    private fun mix(a: Color, b: Color, t: Float) = Color(
        a.red + (b.red - a.red) * t,
        a.green + (b.green - a.green) * t,
        a.blue + (b.blue - a.blue) * t,
        1f
    )
}
