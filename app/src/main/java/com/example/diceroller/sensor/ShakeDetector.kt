package com.example.diceroller.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.sqrt

/**
 * Detects a deliberate shake gesture from the device accelerometer.
 *
 * Usage:
 *   val detector = ShakeDetector(context) { /* on shake */ }
 *   detector.start()   // e.g. on lifecycle ON_RESUME
 *   detector.stop()    // e.g. on lifecycle ON_PAUSE / dispose
 *
 * Threshold and cooldown are tunable via the constants below.
 */
class ShakeDetector(
    context: Context,
    private val onShake: () -> Unit,
) : SensorEventListener {

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // Main-thread dispatcher so onShake() — which touches UI (haptic, sound,
    // ViewModel) — is delivered on the main thread regardless of how
    // SensorManager chooses to schedule sensor callbacks.
    private val mainHandler = Handler(Looper.getMainLooper())

    // Low-pass-filtered gravity estimate per axis (m/s^2).
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f

    // Timestamp (SystemClock.elapsedRealtime) of the last accepted shake.
    @Volatile private var lastShakeAtMs: Long = 0L

    // True once the gravity filter has had a chance to converge.
    @Volatile private var primed = false
    @Volatile private var eventCount = 0

    fun start() {
        val sensor = accelerometer ?: return
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME, mainHandler)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Low-pass filter to estimate gravity on each axis.
        gravityX = GRAVITY_FILTER_ALPHA * gravityX + (1 - GRAVITY_FILTER_ALPHA) * x
        gravityY = GRAVITY_FILTER_ALPHA * gravityY + (1 - GRAVITY_FILTER_ALPHA) * y
        gravityZ = GRAVITY_FILTER_ALPHA * gravityZ + (1 - GRAVITY_FILTER_ALPHA) * z

        // Skip the first few events while the filter converges, otherwise the
        // initial delta from (0,0,0) to full gravity looks like a shake.
        if (!primed) {
            eventCount++
            if (eventCount >= PRIMING_EVENTS) primed = true
            return
        }

        // Isolate linear acceleration (everything that isn't gravity).
        val lx = x - gravityX
        val ly = y - gravityY
        val lz = z - gravityZ
        val magnitude = sqrt(lx * lx + ly * ly + lz * lz)

        if (magnitude < SHAKE_THRESHOLD) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastShakeAtMs < COOLDOWN_MS) return

        lastShakeAtMs = now
        onShake()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op. Accuracy changes don't affect shake detection.
    }

    companion object {
        // Tunable. ~1.3 g of linear acceleration. Filters out walking + bumps,
        // requires a deliberate wrist flick.
        private const val SHAKE_THRESHOLD = 13f

        // Minimum gap between accepted shakes; one motion = one roll.
        private const val COOLDOWN_MS = 600L

        // Standard low-pass coefficient. Higher = slower gravity tracking.
        private const val GRAVITY_FILTER_ALPHA = 0.8f

        // Number of initial samples to drop while the gravity filter primes.
        // The first evaluated event is event #(PRIMING_EVENTS + 1), so at
        // SENSOR_DELAY_GAME (~20ms/event) the priming window is ~220ms.
        private const val PRIMING_EVENTS = 10
    }
}
