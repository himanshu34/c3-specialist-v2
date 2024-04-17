package com.nayan.nayancamv2

import android.app.Application
import android.content.Context
import co.nayan.c3v2.core.utils.LocaleHelper
import com.secneo.sdk.Helper

open class NayanCamApplication:Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(base))
        Helper.install(this@NayanCamApplication)
    }
}