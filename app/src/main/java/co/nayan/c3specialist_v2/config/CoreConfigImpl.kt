package co.nayan.c3specialist_v2.config

import co.nayan.c3specialist_v2.BuildConfig
import co.nayan.c3specialist_v2.storage.SharedStorage
import co.nayan.c3v2.core.config.CoreConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoreConfigImpl @Inject constructor(
    private val sharedStorage: SharedStorage
) : CoreConfig {
    override fun apiBaseUrl(): String {
        return BuildConfig.C3_SPECIALIST_BASE_URL
    }

    override fun apiGraphhopperBaseUrl(): String {
        return sharedStorage.getGraphhopperBaseUrl() ?: BuildConfig.BASE_GRAPH_HOPPER_URL
    }
}