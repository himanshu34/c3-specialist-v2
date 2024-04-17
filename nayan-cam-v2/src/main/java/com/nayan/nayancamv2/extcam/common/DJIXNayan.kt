package com.nayan.nayancamv2.extcam.common

import android.util.Log
import dji.sdk.base.BaseProduct
import dji.sdk.sdkmanager.DJISDKManager
import timber.log.Timber

class DJIXNayan {

    companion object {
        private var mProduct: BaseProduct? = null

        @get:Synchronized
        val productInstance: BaseProduct?
            get() {
                if (null == mProduct) {
                    mProduct = DJISDKManager.getInstance().product
                }
                return mProduct
            }

        @Synchronized
        fun updateProduct(product: BaseProduct?) {
            Timber.tag("DJIXNAYAN").d("product: $product")
            mProduct = product
        }
    }
}