package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2

/**
 * DeviceTiltSensor
 *
 * Captures microscopic phone tilt (pitch and roll) using the Android Sensor framework
 * to introduce physically believable dynamic lighting parallax.
 *
 * As the user holds and slightly rotates their mobile device, the virtual studio key light
 * and contact shadows shift by a minuscule fraction, providing the subtle optical cue
 * that convinces the eye the vinyl record is a physical 3D object in real space.
 *
 * Features:
 * - Uses [Sensor.TYPE_ROTATION_VECTOR] for drift-free orientation.
 * - Falls back to [Sensor.TYPE_GRAVITY] or [Sensor.TYPE_ACCELEROMETER] if rotation vector is absent.
 * - Applies a smooth exponential moving average (low-pass filter) to eliminate hardware sensor noise.
 * - Limits maximum tilt offset to prevent excessive, gimmicky visual distortion.
 * - Includes a graceful fallback if motion sensors are unavailable.
 */
class DeviceTiltSensor(context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val rotationSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /**
     * Normalized tilt coordinates in range [-1.0f, 1.0f]:
     * - [first] represents horizontal roll (tilting left/right)
     * - [second] represents vertical pitch (tilting top/bottom)
     */
    private val _tilt = MutableStateFlow(Pair(0.0f, 0.0f))
    val tilt: StateFlow<Pair<Float, Float>> = _tilt.asStateFlow()

    // Low-pass filter smoothing coefficient (0.0 to 1.0)
    private val alpha = 0.08f
    private var filteredX = 0.0f
    private var filteredY = 0.0f

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    /**
     * Starts listening to device motion sensors.
     */
    fun start() {
        rotationSensor?.let { sensor ->
            sensorManager?.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
    }

    /**
     * Stops listening to conserve battery when the player activity is paused or backgrounded.
     */
    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)

                // Azimuth = orientationAngles[0]
                // Pitch = orientationAngles[1]
                // Roll = orientationAngles[2]
                val pitch = orientationAngles[1] // radians
                val roll = orientationAngles[2]  // radians

                // Normalize roll to roughly [-1.0, 1.0] for +/- 30 degrees tilt
                val targetX = (roll / (PI.toFloat() / 6f)).coerceIn(-1.0f, 1.0f)
                val targetY = (pitch / (PI.toFloat() / 6f)).coerceIn(-1.0f, 1.0f)

                // Low-pass exponential smoothing
                filteredX += alpha * (targetX - filteredX)
                filteredY += alpha * (targetY - filteredY)

                _tilt.value = Pair(filteredX, filteredY)
            }

            Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER -> {
                // Gravity / Accel fallback
                // event.values[0] is X axis (lateral), values[1] is Y axis (longitudinal)
                val rawX = (-event.values[0] / 9.8f).coerceIn(-1.0f, 1.0f)
                val rawY = (event.values[1] / 9.8f).coerceIn(-1.0f, 1.0f)

                filteredX += alpha * (rawX - filteredX)
                filteredY += alpha * (rawY - filteredY)

                _tilt.value = Pair(filteredX, filteredY)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op for orientation tracking
    }
}
