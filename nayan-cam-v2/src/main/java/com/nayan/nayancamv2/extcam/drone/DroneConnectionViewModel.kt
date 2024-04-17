package com.nayan.nayancamv2.extcam.drone

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.extcam.common.DJIXNayan
import dji.common.error.DJIError
import dji.common.error.DJISDKError
import dji.keysdk.ProductKey
import dji.keysdk.callback.KeyListener
import dji.sdk.base.BaseComponent
import dji.sdk.base.BaseProduct
import dji.sdk.base.BaseProduct.ComponentKey
import dji.sdk.sdkmanager.DJISDKInitEvent
import dji.sdk.sdkmanager.DJISDKManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

class DroneConnectionViewModel : ViewModel(), DJISDKManager.SDKManagerCallback,
    BaseComponent.ComponentListener {
    val isRegistrationInProgress = AtomicBoolean(false)
    val firmKey: ProductKey = ProductKey.create(ProductKey.FIRMWARE_PACKAGE_VERSION)

    enum class ConnectionState { REGISTRATION_SUCCESS, REGISTRATION_FAILURE, PRODUCT_CONNECTED, PRODUCT_DISCONNECTED, PRODUCT_CHANGED, START_REGISTRATION }

    private val _connectionStatus = MutableLiveData<ConnectionState>()
    val connectionStatus: LiveData<ConnectionState> = _connectionStatus

    // val connectionStatus: MutableLiveData<String> = MutableLiveData()
    val productInfo: MutableLiveData<String> = MutableLiveData()
    val firmwareVersion: MutableLiveData<String> = MutableLiveData()
    val firmVersionListener =
        KeyListener { _: Any?, newValue: Any? -> firmwareVersion.postValue(newValue.toString()) }


    init {
        _connectionStatus.postValue(ConnectionState.START_REGISTRATION)
    }

    override fun onRegister(djiError: DJIError?) {
        viewModelScope.launch {
            if (djiError === DJISDKError.REGISTRATION_SUCCESS) {
                Timber.tag("App registration").e(DJISDKError.REGISTRATION_SUCCESS.description)
                try {
                    _connectionStatus.postValue(ConnectionState.REGISTRATION_SUCCESS)
                    delay(10000)
                    DJISDKManager.getInstance().startConnectionToProduct()
                } catch (e: Exception) {
                    Firebase.crashlytics.recordException(e)
                    e.printStackTrace()
                }
            } else _connectionStatus.postValue(ConnectionState.REGISTRATION_FAILURE)
            isRegistrationInProgress.set(false)
        }
    }

    override fun onProductDisconnect() {
        _connectionStatus.postValue(ConnectionState.PRODUCT_DISCONNECTED)
    }

    override fun onProductConnect(baseProduct: BaseProduct?) {
        isRegistrationInProgress.set(false)
        productInfo.postValue(baseProduct?.model?.toString())
        firmwareVersion.postValue(baseProduct?.firmwarePackageVersion?.toString())
        DJIXNayan.updateProduct(baseProduct)

        _connectionStatus.postValue(ConnectionState.PRODUCT_CONNECTED)
    }

    override fun onProductChanged(baseProduct: BaseProduct?) {
        _connectionStatus.postValue(ConnectionState.PRODUCT_CHANGED)
        if (baseProduct != null) {
            //adding null safety as it is sometimes causing runtime NPE
            productInfo.postValue(baseProduct?.model.toString())
            firmwareVersion.postValue(baseProduct?.firmwarePackageVersion.toString())
            DJIXNayan.updateProduct(baseProduct)
        }
    }

    override fun onComponentChange(
        componentKey: ComponentKey?,
        oldComponent: BaseComponent?,
        newComponent: BaseComponent?
    ) {
        newComponent?.setComponentListener(this@DroneConnectionViewModel)
    }

    override fun onInitProcess(djisdkInitEvent: DJISDKInitEvent?, i: Int) {
        //notify the init progress
    }

    override fun onDatabaseDownloadProgress(l: Long, l1: Long) {}
    override fun onConnectivityChange(b: Boolean) {
        val status = if (b) ConnectionState.PRODUCT_CONNECTED
        else ConnectionState.PRODUCT_DISCONNECTED
        _connectionStatus.postValue(status)
    }
}
