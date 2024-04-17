package co.nayan.c3v2.core.api.factory

import co.nayan.c3v2.core.api.ApiClientBase
import co.nayan.c3v2.core.api.ApiClientGraphHopper
import co.nayan.c3v2.core.api.ApiClientLogin
import co.nayan.c3v2.core.api.ApiClientNayanCam

interface IApiFactory {
    val apiClientLogin: ApiClientLogin
    val apiClientBase: ApiClientBase
    val apiClientNayanCam: ApiClientNayanCam
    val apiClientGraphHopper: ApiClientGraphHopper
}