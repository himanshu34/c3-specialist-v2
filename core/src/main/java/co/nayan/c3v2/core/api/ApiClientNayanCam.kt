package co.nayan.c3v2.core.api

import co.nayan.c3v2.core.models.driver_module.AttendanceRequest
import co.nayan.c3v2.core.models.driver_module.AttendanceResponse
import co.nayan.c3v2.core.models.driver_module.CameraAIWorkFlowResponse
import co.nayan.c3v2.core.models.driver_module.ServerSegments
import co.nayan.c3v2.core.models.driver_module.VideoFilesStatusRequest
import co.nayan.c3v2.core.models.driver_module.VideoFilesStatusResponse
import co.nayan.c3v2.core.models.driver_module.VideoUploadRes
import com.google.gson.JsonObject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Response
import retrofit2.http.*

/**
 * This class contains all the network calls
 *
 */
interface ApiClientNayanCam {

    @Multipart
    @POST("api/driver/videos")
    suspend fun uploadVideo(
        @Part("video[latitude]") lat: RequestBody?,
        @Part("video[longitude]") longitude: RequestBody?,
        @Part("video[recorded_on]") recordedOn: RequestBody?,
        @Part("pending_video_count]") offlineVideoCount: RequestBody?,
        @Part("video[metatag]") surge: RequestBody?,
        @Part("video[recorded_by_id]") recordedById: RequestBody?,
        @Part("video[speed]") speed: RequestBody?,
        @Part video: MultipartBody.Part
    ): Response<VideoUploadRes>

    @PATCH("/api/driver/videos/offline_video_count")
    suspend fun updateVideoCount(
        @Query("offline_video_count") offlineVideoCount: String
    ): Response<JSONObject>

    @POST("/api/user/config/{id}/set_config")
    suspend fun setConfig(
        @Path("id") id: String,
        @Body request: JsonObject
    ): Response<JSONObject>

    @GET("/api/driver/camera_ai_flows")
    suspend fun getAiWorkFlow(
        @Query("latitude") latitude: String?,
        @Query("longitude") longitude: String?
    ): Response<CameraAIWorkFlowResponse>

    @POST("/api/attendences")
    suspend fun postAttendance(@Body attendanceRequest: AttendanceRequest): Response<AttendanceResponse>

    @POST("/api/segments")
    suspend fun postSegments(@Body segmentList: ServerSegments): Response<ResponseBody>

    @GET("/api/segments")
    suspend fun getSegments(
        @Query("latitude") latitude: String?,
        @Query("longitude") longitude: String?
    ): Response<ServerSegments>

    @PUT("/api/driver/videos/verify_videos_sync")
    suspend fun checkUploadedVideoFilesStatus(
        @Body videoFilesStatusRequest: VideoFilesStatusRequest
    ): Response<VideoFilesStatusResponse>
}