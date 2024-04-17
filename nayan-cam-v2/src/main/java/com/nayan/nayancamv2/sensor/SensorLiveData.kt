package com.nayan.nayancamv2.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.Sensor.TYPE_ACCELEROMETER
import android.hardware.Sensor.TYPE_GRAVITY
import android.hardware.Sensor.TYPE_GYROSCOPE
import android.hardware.Sensor.TYPE_LINEAR_ACCELERATION
import android.hardware.Sensor.TYPE_MAGNETIC_FIELD
import android.hardware.Sensor.TYPE_PROXIMITY
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.SensorManager.SENSOR_DELAY_NORMAL
import androidx.lifecycle.LiveData
import com.nayan.nayancamv2.getDirection
import com.nayan.nayancamv2.model.SensorMeta
import com.nayan.nayancamv2.sensor.bias.GyroscopeBias
import com.nayan.nayancamv2.sensor.orientation.GyroscopeDeltaOrientation
import com.nayan.nayancamv2.sensor.orientation.MagneticFieldOrientation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.round

class SensorLiveData(val context: Context) : LiveData<SensorMeta>(), SensorEventListener {

    private var sensorManager: SensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val sensorDataJob = SupervisorJob()
    private val sensorDataScope = CoroutineScope(Dispatchers.IO + sensorDataJob)

    private var lastUpdateTime: Long = 0
    private lateinit var sensors: Array<Sensor?>
    private var gyroUBias: GyroscopeBias? = null

    private var sensorMeta: SensorMeta = SensorMeta()
    private lateinit var gyroCIntegration: GyroscopeDeltaOrientation
    private lateinit var gyroUIntegration: GyroscopeDeltaOrientation

    // Very small values for the accelerometer (on all three axes) should
    // be interpreted as 0. This value is the amount of acceptable
    // non-zero drift.
    private val valueDrift = 0.05f

    // Current data from accelerometer & magnetometer.  The arrays hold values
    // for X, Y, and Z.
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var accelerometerReading = FloatArray(3)
    private var magnetometerReading = FloatArray(3)
    private var gravityValues = FloatArray(3)

    private var gyroHeading = 0.0

    private val eulerGyroscopeSensitivity = 0.0025f
    private val gyroscopeSensitivity = 0f
    private var _lastTick = 0L

    /**
     * Registers to listen to sensor value updates when there are active observers.
     */
    override fun onActive() {
        super.onActive()
        _lastTick = System.currentTimeMillis()
        sensors = arrayOfNulls(6)
        sensors[0] = sensorManager.getDefaultSensor(TYPE_ACCELEROMETER)
        sensors[1] = sensorManager.getDefaultSensor(TYPE_LINEAR_ACCELERATION)
        sensors[2] = sensorManager.getDefaultSensor(TYPE_GYROSCOPE)
        sensors[3] = sensorManager.getDefaultSensor(TYPE_MAGNETIC_FIELD)
        sensors[4] = sensorManager.getDefaultSensor(TYPE_GRAVITY)
        sensors[5] = sensorManager.getDefaultSensor(TYPE_PROXIMITY)

        gyroUBias = GyroscopeBias(300)
        gyroCIntegration = GyroscopeDeltaOrientation(gyroscopeSensitivity, FloatArray(3))
        gyroUIntegration = GyroscopeDeltaOrientation(eulerGyroscopeSensitivity, null)
        // Looping all the sensors and register them
        sensors.map { sensorManager.registerListener(this, it, SENSOR_DELAY_NORMAL) }
    }

    /**
     * Un-registers listening to sensor value updates as there no active observers.
     */
    override fun onInactive() {
        super.onInactive()
        // Looping all the sensors and register them
        sensors.map { sensorManager.unregisterListener(this, it) }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * Triggered when sensor value changes.
     */
    override fun onSensorChanged(event: SensorEvent) {
        sensorDataScope.launch { handleSensorData(event) }
    }

    private fun handleSensorData(event: SensorEvent) {
        val sensorValuesList: ArrayList<Float> = ExtraFunctions.arrayToList(event.values)
        when (event.sensor.type) {
//            TYPE_PROXIMITY -> {
//                sensorMeta.proximity = event.values[0]
//            }
            TYPE_ACCELEROMETER -> {
                System.arraycopy(
                    event.values,
                    0,
                    accelerometerReading,
                    0,
                    accelerometerReading.size
                )
                sensorMeta.accelerometer = sensorValuesList
            }

            TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(
                    event.values,
                    0,
                    magnetometerReading,
                    0,
                    magnetometerReading.size
                )
                val magHeading = MagneticFieldOrientation.getHeading(
                    gravityValues,
                    event.values,
                    FloatArray(3)
                )
                sensorMeta.magHeading = magHeading
                sensorMeta.magneticField = sensorValuesList
            }

            TYPE_LINEAR_ACCELERATION -> {
                sensorMeta.linearAcceleration = sensorValuesList
            }

            TYPE_GYROSCOPE -> {
                val deltaOrientation =
                    gyroCIntegration.calcDeltaOrientation(event.timestamp, event.values)
                sensorMeta.angularVelocity = deltaOrientation.toCollection(ArrayList())
                gyroHeading += deltaOrientation[2]
                val gyroHeadingDegrees = ExtraFunctions.radsToDegrees(gyroHeading)
                sensorMeta.gyroHeadingDegrees = gyroHeadingDegrees

                sensorMeta.gyroscopeCalibrated = sensorValuesList
            }

            TYPE_GRAVITY -> {
                gravityValues = event.values
            }
        }

        updateOrientationAngles()
    }

    private fun updateOrientationAngles() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime >= 200) {  // Minimum time interval between updates in milliseconds (1000 ms / 5 Hz)
            lastUpdateTime = currentTime

            val rotationOK = SensorManager.getRotationMatrix(
                rotationMatrix,
                null,
                accelerometerReading,
                magnetometerReading
            )
            // Get the orientation of the device (azimuth, pitch, roll) based
            // on the rotation matrix. Output units are radians.
            if (rotationOK) {
                val orientation = SensorManager.getOrientation(
                    rotationMatrix,
                    orientationAngles
                )
                val degrees = (Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0
                val angle = round(degrees * 100) / 100
                val direction = getDirection(degrees)
                sensorMeta.angleWithDirection = "$angle $direction"
            }

            // Pull out the individual values from the array.
            val azimuth = orientationAngles[0] //(rotation around the -ve z-axis)
            var pitch = orientationAngles[1] //(rotation around the x-axis)
            var roll = orientationAngles[2] //(rotation around the y-axis)

            // Pitch and roll values that are close to but not 0 cause the
            // animation to flash a lot. Adjust pitch and roll to 0 for very
            // small values (as defined by valueDrift).
            if (abs(pitch) < valueDrift) pitch = 0f
            if (abs(roll) < valueDrift) roll = 0f
            sensorMeta.pitch = pitch
            sensorMeta.roll = roll
            sensorMeta.azimuth = azimuth
            val rotationMatrixList: ArrayList<Float> = ExtraFunctions.arrayToList(rotationMatrix)
            sensorMeta.rotationMatrix = rotationMatrixList

            postValue(sensorMeta)
        }
    }
}