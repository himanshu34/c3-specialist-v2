package com.nayan.nayancamv2.temperature

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.nayan.nayancamv2.getBatteryTemperature
import com.nayan.nayancamv2.storage.SharedPrefManager
import com.nayan.nayancamv2.temperature.StateManager.Companion.BATTERY_TEMP_RESULT_KEY
import com.nayan.nayancamv2.temperature.StateManager.Companion.BATTERY_TEMP_RESULT_UPDATE_TIME
import com.nayan.nayancamv2.temperature.StateManager.Companion.CPU_TEMP_RESULT_KEY
import com.nayan.nayancamv2.util.CommonUtils.Companion.readOneLineOrNull
import com.nayan.nayancamv2.util.DEVICE_PERFORMANCE.BATTERY
import com.nayan.nayancamv2.util.DEVICE_PERFORMANCE.CPU
import com.nayan.nayancamv2.util.Event
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Custom provider which provides all temperatures
 *
 * @property context
 * @property sharedPrefManager
 */
@Singleton
class TemperatureProvider @Inject constructor(
    val context: Context,
    val sharedPrefManager: SharedPrefManager
) {

    companion object {
        // Ugly but currently the easiest working solution is to search well known locations
        // If you know better solution please refactor this :)
        private val CPU_TEMP_FILE_PATHS = listOf(
            "/sys/devices/system/cpu/cpu0/cpufreq/cpu_temp",
            "/sys/devices/system/cpu/cpu0/cpufreq/FakeShmoo_cpu_temp",
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/i2c-adapter/i2c-4/4-004c/temperature",
            "/sys/devices/platform/tegra-i2c.3/i2c-4/4-004c/temperature",
            "/sys/devices/platform/omap/omap_temp_sensor.0/temperature",
            "/sys/devices/platform/tegra_tmon/temp1_input",
            "/sys/kernel/debug/tegra_thermal/temp_tj",
            "/sys/devices/platform/s5p-tmu/temperature",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/class/hwmon/hwmon0/device/temp1_input",
            "/sys/devices/virtual/thermal/thermal_zone1/temp",
            "/sys/devices/virtual/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone3/temp",
            "/sys/class/thermal/thermal_zone4/temp",
            "/sys/class/hwmon/hwmonX/temp1_input",
            "/sys/devices/platform/s5p-tmu/curr_temp"
        )
    }

    private var temperatureDisposable: Disposable? = null
    private var refreshingDisposable: Disposable? = null
    private var cpuTemperatureResult: CpuTemperatureResult? = null
    private var isBatteryTemperatureAvailable = false

    private val _temp = MutableLiveData<Event<TempEvent>>()
    val temp: LiveData<Event<TempEvent>> = _temp

    private val tempObserver = Consumer<TempContainer> { (cpuTemp, batTemp) ->
        if (cpuTemp != null) {
            _temp.postValue(Event(TempEvent.TemperatureUpdate(TemperatureItem(CPU, cpuTemp))))
            Timber.d("🦀cpuTemp $cpuTemp")
        }
        if (batTemp != null) {
            _temp.postValue(
                Event(TempEvent.TemperatureUpdate(TemperatureItem(BATTERY, batTemp.toFloat())))
            )
            sharedPrefManager.insert(BATTERY_TEMP_RESULT_KEY, batTemp.toFloat())
            sharedPrefManager.insert(BATTERY_TEMP_RESULT_UPDATE_TIME, System.currentTimeMillis())
            Timber.d("🦀batteryTemp $batTemp ")
        }
    }

    /**
     * Get temperature for CPU and if needed divided returned value by 1000 to get Celsius unit
     *
     * @return CPU temperature
     */
    fun getCpuTemp(path: String): Float {
        val temp = File(path).readOneLineOrNull() ?: 0.0
        return if (isTemperatureValid(temp)) temp.toFloat() else (temp / 1000).toFloat()
    }

    /**
     * Scan device looking for CPU temperature in all well known locations
     */
    fun getCpuTemperatureFinder(): Maybe<CpuTemperatureResult> {
        return Observable.fromIterable(CPU_TEMP_FILE_PATHS)
            .map { path ->
                val temp = File(path).readOneLineOrNull()
                var validPath = ""
                var currentTemp = 0.0
                if (temp != null) {
                    // Verify if we are in normal temperature range
                    if (isTemperatureValid(temp)) {
                        validPath = path
                        currentTemp = temp
                    } else if (isTemperatureValid(temp / 1000)) {
                        validPath = path
                        currentTemp = temp / 1000
                    }
                }
                CpuTemperatureResult(validPath, currentTemp.toInt())
            }
            .filter { (filePath) -> filePath.isNotEmpty() }
            .firstElement()
    }

    /**
     * Check if passed temperature is in normal range: -30 - 250 Celsius
     *
     * @param temp current temperature
     */
    private fun isTemperatureValid(temp: Double): Boolean = temp in -30.0..250.0

    fun startObservingTemp() {
        if (sharedPrefManager.contains(CPU_TEMP_RESULT_KEY)) {
            cpuTemperatureResult =
                sharedPrefManager.get(CPU_TEMP_RESULT_KEY, CpuTemperatureResult())
            verifyTemperaturesAvailability(tempObserver)
        } else {
            temperatureDisposable?.dispose()
            temperatureDisposable = getCpuAvailabilityTest(tempObserver)
        }
    }


    fun stopTemperatureRefreshing() {
        Timber.i("stopTemperatureRefreshing()")
        temperatureDisposable?.dispose()
        refreshingDisposable?.dispose()
    }

    /**
     * Try to find path with CPU temperature. If success try validate temperatures and schedule
     * refreshing process.
     */
    private fun getCpuAvailabilityTest(tempObserver: Consumer<TempContainer>): Disposable {
        return getCpuTemperatureFinder()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe {
                _temp.postValue(Event(TempEvent.Loading))
            }.doFinally {
                verifyTemperaturesAvailability(tempObserver)
            }.subscribe({ temperatureResult ->
                sharedPrefManager.insert(CPU_TEMP_RESULT_KEY, temperatureResult)
                cpuTemperatureResult = temperatureResult
            }, Timber::e, { Timber.i("List scan complete") })
    }

    /**
     * Verify which temperatures are available and schedule refreshing. If we don't have any
     * temperature info set isError flag to true
     */
    private fun verifyTemperaturesAvailability(tempObserver: Consumer<TempContainer>) {
        if (!isBatteryTemperatureAvailable) {
            val batteryTemp = context.getBatteryTemperature()
            if (batteryTemp != 0) isBatteryTemperatureAvailable = true
        }

        if (isBatteryTemperatureAvailable || cpuTemperatureResult != null)
            scheduleRefreshing(tempObserver)
        else _temp.postValue(Event(TempEvent.Error))
    }

    /**
     * Schedule refreshing process (for 3s)
     */
    private fun scheduleRefreshing(tempObserver: Consumer<TempContainer>) {
        // Dispose of the existing disposable if it exists
        refreshingDisposable?.dispose()
        refreshingDisposable = getRefreshingInvoker()
            .map {
                var batteryTemp: Int? = null
                if (isBatteryTemperatureAvailable)
                    batteryTemp = context.getBatteryTemperature()
                var cpuTemp: Float? = null
                if (cpuTemperatureResult != null)
                    cpuTemp = getCpuTemp(cpuTemperatureResult!!.filePath)
                TempContainer(cpuTemp, batteryTemp)
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(tempObserver, Timber::e)
    }

    /**
     * Return refreshing invoker
     */
    private fun getRefreshingInvoker() = Observable.interval(0, 10, TimeUnit.SECONDS)

    /**
     * Container for temperature value and path
     */
    data class CpuTemperatureResult(val filePath: String = "", val temp: Int = 0)

    sealed class TempEvent {
        data object Loading : TempEvent()
        data object Error : TempEvent()
        data class TemperatureUpdate(var data: TemperatureItem) : TempEvent()
    }
}