package com.composepro.app.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * How the phone is held, from its gravity sensor.
 * flat = shooting straight down. offFlatDeg = how far from straight down. rollDeg = sideways tilt when upright.
 */
data class Tilt(val flat: Boolean, val offFlatDeg: Float, val rollDeg: Float) {
    companion object { val Unknown = Tilt(false, 0f, 0f) }
}

@Composable
fun rememberTilt(): State<Tilt> {
    val context = LocalContext.current
    val tilt = remember { mutableStateOf(Tilt.Unknown) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val (x, y, z) = Triple(e.values[0], e.values[1], e.values[2])
                val g = sqrt(x * x + y * y + z * z).coerceAtLeast(0.1f)
                val zN = abs(z) / g
                val flat = zN > 0.85f
                val offFlat = Math.toDegrees(acos(zN.coerceIn(0f, 1f).toDouble())).toFloat()
                val roll = Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()
                tilt.value = Tilt(flat, offFlat, roll)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
    return tilt
}
