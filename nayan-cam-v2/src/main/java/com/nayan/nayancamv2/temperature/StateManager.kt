package com.nayan.nayancamv2.temperature

import android.content.Context
import com.nayan.nayancamv2.getBatteryTemperature
import com.nayan.nayancamv2.storage.SharedPrefManager
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Manager class to handle state machine
 *
 * @property temperatureProvider
 * @property sharedPrefManager
 */
class StateManager @Inject constructor(
    val context: Context,
    private val temperatureProvider: TemperatureProvider,
    private val sharedPrefManager: SharedPrefManager
) {

    companion object {
        const val CPU_TEMP_RESULT_KEY = "temp_result_key"
        const val BATTERY_TEMP_RESULT_KEY = "BATTERY_TEMP_RESULT_KEY"
        const val BATTERY_TEMP_RESULT_UPDATE_TIME = "BATTERY_TEMP_RESULT_UPDATE_TIME"
    }

    // Binding fields
    private val isLoading = NonNullMutableLiveData(false)
    private val isError = NonNullMutableLiveData(false)

    private var temperatureDisposable: Disposable? = null
    private var refreshingDisposable: Disposable? = null
    private var cpuTemperatureResult: TemperatureProvider.CpuTemperatureResult? = null
    private var isBatteryTemperatureAvailable = false


    /**
     * Start temperature getting process. It also validates all temperatures availability.
     */
    fun startTemperatureRefreshing(tempObserver: Consumer<TempContainer>) {
        Timber.i("startTemperatureRefreshing()")
        if (sharedPrefManager.contains(CPU_TEMP_RESULT_KEY)) {
            cpuTemperatureResult = sharedPrefManager.get(
                CPU_TEMP_RESULT_KEY,
                TemperatureProvider.CpuTemperatureResult()
            )
            verifyTemperaturesAvailability(tempObserver)
        } else {
            temperatureDisposable = getCpuAvailabilityTest(tempObserver)
        }
    }

    /**
     * Stop temperature getting process
     */
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
        return temperatureProvider.getCpuTemperatureFinder()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe {
                isLoading.postValue(true)
                isError.postValue(false)
            }.doFinally {
                isLoading.postValue(false)
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
        else isError.postValue(true)
    }

    /**
     * Schedule refreshing process (for 3s)
     */
    private fun scheduleRefreshing(tempObserver: Consumer<TempContainer>) {
        if (refreshingDisposable != null) refreshingDisposable!!.dispose()
        refreshingDisposable = getRefreshingInvoker()
            .map {
                var batteryTemp: Int? = null
                if (isBatteryTemperatureAvailable) {
                    batteryTemp = context.getBatteryTemperature()
                }
                var cpuTemp: Float? = null
                if (cpuTemperatureResult != null) {
                    cpuTemp = temperatureProvider.getCpuTemp(cpuTemperatureResult!!.filePath)
                }
                TempContainer(cpuTemp, batteryTemp)
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(tempObserver, Timber::e)
    }

    /**
     * Return refreshing invoker
     */
    private fun getRefreshingInvoker(): Observable<Long> =
        Observable.interval(0, 10, TimeUnit.SECONDS)
}