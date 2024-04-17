package co.nayan.c3v2.core.config

interface CoreConfig {

    /**
     * Provide the base url for login API
     */
    fun apiBaseUrl(): String
    fun apiGraphhopperBaseUrl(): String
}