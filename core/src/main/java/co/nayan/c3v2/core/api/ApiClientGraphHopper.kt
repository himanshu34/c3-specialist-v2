package co.nayan.c3v2.core.api

import co.nayan.c3v2.core.models.driver_module.RouteLocationData
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiClientGraphHopper {

    @POST("/match")
    suspend fun getRouteData(
        @Query("profile") travelBy: String? = "car",
        @Query("type") type: String? = "json",
        @Query("points_encoded") points_encoded: Boolean? = false,
        @Query("instructions") instructions: Boolean? = false,
        @Body routeLocationData: RouteLocationData
    ): Response<ResponseBody>
}