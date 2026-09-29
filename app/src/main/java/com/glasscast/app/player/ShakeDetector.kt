package com.glasscast.app.player

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

/**
 * Accelerometer shake detection, registered only while a timer is armed or
 * inside its grace window — a listener running all night would cost battery for
 * a feature used once a night.
 *
 * Requires two distinct jolts within a second, so a phone sliding off a pillow
 * doesn't restart the episode.
 *
 * Ported from GlassBook, where this is already proven on device.
 */
class ShakeDetector(
    context: Context,
    private val onShake: () -> Unit
) : SensorEventListener {

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var lastJolt = 0L
    private var joltCount = 0
    private var listening = false

    fun start() {
        if (listening || sensor == null) return
        manager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        listening = true
        joltCount = 0
    }

    fun stop() {
        if (!listening) return
        manager?.unregisterListener(this)
        listening = false
        joltCount = 0
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val gx = event.values[0] / SensorManager.GRAVITY_EARTH
        val gy = event.values[1] / SensorManager.GRAVITY_EARTH
        val gz = event.values[2] / SensorManager.GRAVITY_EARTH
        val force = sqrt(gx * gx + gy * gy + gz * gz)

        if (force < SHAKE_G) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastJolt < JOLT_DEBOUNCE_MS) return
        if (now - lastJolt > JOLT_WINDOW_MS) joltCount = 0

        lastJolt = now
        joltCount++

        if (joltCount >= JOLTS_REQUIRED) {
            joltCount = 0
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val SHAKE_G = 2.2f
        const val JOLT_DEBOUNCE_MS = 180L
        const val JOLT_WINDOW_MS = 1_000L
        const val JOLTS_REQUIRED = 2
    }
}
