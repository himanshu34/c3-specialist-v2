package co.nayan.c3v2.core.api

import co.nayan.c3v2.core.models.ActiveWfStepResponse
import co.nayan.c3v2.core.models.BankDetails
import co.nayan.c3v2.core.models.CityKmlRequest
import co.nayan.c3v2.core.models.CityKmlResponse
import co.nayan.c3v2.core.models.DataRecordsCorrupt
import co.nayan.c3v2.core.models.LearningVideosResponse
import co.nayan.c3v2.core.models.Records
import co.nayan.c3v2.core.models.SurgeLocationsResponse
import co.nayan.c3v2.core.models.User
import co.nayan.c3v2.core.models.WfStep
import co.nayan.c3v2.core.models.Work
import co.nayan.c3v2.core.models.WorkFlow
import co.nayan.c3v2.core.models.c3_module.AuthenticationResponse
import co.nayan.c3v2.core.models.c3_module.CreatePayoutResponse
import co.nayan.c3v2.core.models.c3_module.FaqDataConfirmationRequest
import co.nayan.c3v2.core.models.c3_module.FaqDataConfirmationResponse
import co.nayan.c3v2.core.models.c3_module.PayoutResponse
import co.nayan.c3v2.core.models.c3_module.ReferralPayoutResponse
import co.nayan.c3v2.core.models.c3_module.WFQnAnsData
import co.nayan.c3v2.core.models.c3_module.requests.AdminWorkAssignment
import co.nayan.c3v2.core.models.c3_module.requests.NewMemberRequest
import co.nayan.c3v2.core.models.c3_module.requests.PhoneVerificationRequest
import co.nayan.c3v2.core.models.c3_module.requests.RecordUpdateStarredRequest
import co.nayan.c3v2.core.models.c3_module.requests.SandboxSubmitAnnotationRequest
import co.nayan.c3v2.core.models.c3_module.requests.SandboxTrainingRequest
import co.nayan.c3v2.core.models.c3_module.requests.SendNotificationRequest
import co.nayan.c3v2.core.models.c3_module.requests.SubmitAnnotationsRequest
import co.nayan.c3v2.core.models.c3_module.requests.SubmitJudgmentsRequest
import co.nayan.c3v2.core.models.c3_module.requests.SubmitReviewRequest
import co.nayan.c3v2.core.models.c3_module.requests.SubmitSessionsRequest
import co.nayan.c3v2.core.models.c3_module.requests.TokenRequest
import co.nayan.c3v2.core.models.c3_module.requests.UpdateBankDetailsRequest
import co.nayan.c3v2.core.models.c3_module.requests.UpdatePasswordRequest
import co.nayan.c3v2.core.models.c3_module.requests.UpdatePersonalInfoRequest
import co.nayan.c3v2.core.models.c3_module.responses.AasmStatesResponse
import co.nayan.c3v2.core.models.c3_module.responses.AddTemplateRequest
import co.nayan.c3v2.core.models.c3_module.responses.AllowedLocationResponse
import co.nayan.c3v2.core.models.c3_module.responses.CallUserListResponse
import co.nayan.c3v2.core.models.c3_module.responses.DataRecordResponse
import co.nayan.c3v2.core.models.c3_module.responses.DataRecordsResponse
import co.nayan.c3v2.core.models.c3_module.responses.DriverStatsResponse
import co.nayan.c3v2.core.models.c3_module.responses.EventsResponse
import co.nayan.c3v2.core.models.c3_module.responses.FetchAppMinVersionResponse
import co.nayan.c3v2.core.models.c3_module.responses.IncorrectRecordsResponse
import co.nayan.c3v2.core.models.c3_module.responses.IncorrectWfStep
import co.nayan.c3v2.core.models.c3_module.responses.LanguageSuccessResponse
import co.nayan.c3v2.core.models.c3_module.responses.LeaderHomeStatsResponse
import co.nayan.c3v2.core.models.c3_module.responses.LeaderPerformanceResponse
import co.nayan.c3v2.core.models.c3_module.responses.MembersPerformanceResponse
import co.nayan.c3v2.core.models.c3_module.responses.OverallTeamPerformanceResponse
import co.nayan.c3v2.core.models.c3_module.responses.PhoneVerificationResponse
import co.nayan.c3v2.core.models.c3_module.responses.RecordStarredStatusResponse
import co.nayan.c3v2.core.models.c3_module.responses.ReferralCodeRequest
import co.nayan.c3v2.core.models.c3_module.responses.ReferralCodeResponse
import co.nayan.c3v2.core.models.c3_module.responses.RequestMemberResponse
import co.nayan.c3v2.core.models.c3_module.responses.RoleApiRequest
import co.nayan.c3v2.core.models.c3_module.responses.RoleRequestResponse
import co.nayan.c3v2.core.models.c3_module.responses.SandboxRecordResponse
import co.nayan.c3v2.core.models.c3_module.responses.SandboxRecordsResponse
import co.nayan.c3v2.core.models.c3_module.responses.SandboxSubmitAnswerResponse
import co.nayan.c3v2.core.models.c3_module.responses.SandboxTrainingResponse
import co.nayan.c3v2.core.models.c3_module.responses.SendNotificationResponse
import co.nayan.c3v2.core.models.c3_module.responses.StatsResponse
import co.nayan.c3v2.core.models.c3_module.responses.SubmitCanvasResponse
import co.nayan.c3v2.core.models.c3_module.responses.SubmitReviewResponse
import co.nayan.c3v2.core.models.c3_module.responses.TeamMemberResponse
import co.nayan.c3v2.core.models.c3_module.responses.TemplatesResponse
import co.nayan.c3v2.core.models.c3_module.responses.UpdatePasswordResponse
import co.nayan.c3v2.core.models.c3_module.responses.UserResponse
import co.nayan.c3v2.core.models.c3_module.responses.VideooCoordinatesResponse
import co.nayan.c3v2.core.models.driver_module.CameraAIWorkFlowResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiClientBase {

    /**
     * Specialist Work APIs
     **/
    @POST("/api/specialist/work_assignments")
    suspend fun specialistWorkAssignment(): Work?

    @GET("/api/user/work_requests/{id}/status")
    suspend fun getStatus(@Path(value = "id") workRequestId: Int?): Work?

    @GET("/api/specialist/work_assignments/{id}/records")
    suspend fun specialistNextRecords(@Path(value = "id") workAssignmentId: Int?): Records?

    @POST("/api/specialist/work/submit_judgment")
    suspend fun specialistSubmitJudgments(@Body request: SubmitJudgmentsRequest): SubmitCanvasResponse

    @POST("/api/user/role_requests")
    suspend fun createRolesRequest(@Body request: RoleApiRequest): RoleRequestResponse

    @GET("/api/user/role_requests")
    suspend fun getRolesRequest(): RoleRequestResponse

    @POST("/api/specialist/work/submit_annotation")
    suspend fun specialistSubmitAnnotations(@Body request: SubmitAnnotationsRequest): SubmitCanvasResponse

    @GET("/api/specialist/dashboard")
    suspend fun fetchSpecialistHomeStats(): StatsResponse?

    @GET("/api/specialist/dashboard/stats")
    suspend fun fetchSpecialistPerformance(
        @Query("start_time") startTime: String,
        @Query("end_time") endTime: String
    ): StatsResponse?

    @GET("/api/specialist/dashboard/work_summary")
    suspend fun fetchSpecialistWorkSummary(
        @Query("start_time") startTime: String,
        @Query("end_time") endTime: String
    ): StatsResponse?

    @GET("/api/specialist/dashboard/incorrect_judgments_by_wf_step")
    suspend fun fetchSpecialistIncorrectJudgmentsWfSteps(
        @Query("start_time") startTime: String?,
        @Query("end_time") endTime: String?
    ): List<IncorrectWfStep>?

    @GET("/api/specialist/dashboard/incorrect_judgments")
    suspend fun fetchSpecialistIncorrectJudgments(
        @Query("start_time") startTime: String?,
        @Query("end_time") endTime: String?,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int,
        @Query("wf_step_id") wfStepId: Int?
    ): IncorrectRecordsResponse?

    @GET("/api/specialist/dashboard/incorrect_annotations_by_wf_step")
    suspend fun fetchSpecialistIncorrectAnnotationsWfSteps(
        @Query("start_time") startTime: String?,
        @Query("end_time") endTime: String?
    ): List<IncorrectWfStep>?

    @GET("/api/specialist/dashboard/incorrect_annotations")
    suspend fun fetchSpecialistIncorrectAnnotations(
        @Query("start_time") startTime: String?,
        @Query("end_time") endTime: String?,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int,
        @Query("wf_step_id") wfStepId: Int?
    ): IncorrectRecordsResponse?

    @GET("/api/user/faqs")
    suspend fun fetchFaqData(@Query("wf_step_id") wfStepId: Int?): WFQnAnsData

    @POST("/api/user/faqs/seen")
    suspend fun submitFaqConfirmation(@Body request: FaqDataConfirmationRequest): FaqDataConfirmationResponse

    @GET("/api/learning_videos")
    suspend fun getLearningVideos(): LearningVideosResponse?

    /**
     * Sandbox APIs
     **/
    @POST("/api/specialist/sandbox_trainings")
    suspend fun sandboxTraining(@Body sandboxTrainingRequest: SandboxTrainingRequest): SandboxTrainingResponse?

    @GET("/api/specialist/sandbox_trainings/{id}/next_records")
    suspend fun specialistNextRecord(@Path(value = "id") training_id: Int?): SandboxRecordResponse?

    @POST("/api/specialist/sandbox_trainings/{id}/submit_annotation")
    suspend fun submitSpecialistAnnotation(
        @Path(value = "id") sandboxTrainingId: Int?,
        @Body request: SandboxSubmitAnnotationRequest
    ): SandboxRecordResponse?

    @GET("/api/admin/sandboxes/{id}/records")
    suspend fun adminRecords(@Path(value = "id") sandboxId: Int?): SandboxRecordsResponse?

    @POST("/api/admin/sandbox_records/{id}/submit_annotation")
    suspend fun submitAdminAnnotation(
        @Path(value = "id") recordId: Int?,
        @Body request: SandboxSubmitAnnotationRequest
    ): SandboxSubmitAnswerResponse

    /**
     * Manager Work APIs
     **/
    @POST("/api/manager/work_assignments")
    suspend fun managerWorkAssignment(): Work?

    @GET("/api/manager/work_assignments/{id}/records")
    suspend fun managerNextRecords(@Path(value = "id") workAssignmentId: Int?): Records?

    @POST("/api/manager/work/submit_judgment")
    suspend fun managerSubmitJudgments(@Body request: SubmitJudgmentsRequest): SubmitCanvasResponse

    @POST("/api/manager/work/submit_annotation")
    suspend fun managerSubmitAnnotations(@Body request: SubmitAnnotationsRequest): SubmitCanvasResponse

    @POST("/api/manager/work/submit_review")
    suspend fun submitManagerReview(@Body submitReviewRequest: SubmitReviewRequest): SubmitReviewResponse

    @GET("/api/manager/dashboard")
    suspend fun fetchManagerHomeStats(): StatsResponse?

    @GET("/api/manager/dashboard/work_summary")
    suspend fun fetchManagerWorkSummary(
        @Query("start_time") startTime: String,
        @Query("end_time") endTime: String
    ): StatsResponse?

    @GET("/api/manager/dashboard/stats")
    suspend fun fetchManagerPerformance(
        @Query("start_time") startTime: String,
        @Query("end_time") endTime: String
    ): StatsResponse?

    @GET("/api/manager/dashboard/incorrect_judgments_by_wf_step")
    suspend fun fetchManagerIncorrectJudgmentsWfSteps(
        @Query("start_time") startTime: String?,
        @Query("end_time") endTime: String?
    ): List<IncorrectWfStep>?

    @GET("/api/manager/dashboard/incorrect_judgments")
    suspend fun fetchManagerIncorrectJudgments(
        @Query("start_time") startTime: String?,
        @Query("end_time") endTime: String?,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int,
        @Query("wf_step_id") wfStepId: Int?
    ): IncorrectRecordsResponse?

    @GET("/api/manager/dashboard/incorrect_annotations_by_wf_step")
    suspend fun fetchManagerIncorrectAnnotationsWfSteps(
        @Query("start_time") startTime: String?,
        @Query("end_time") endTime: String?
    ): List<IncorrectWfStep>?

    @GET("/api/manager/dashboard/incorrect_annotations")
    suspend fun fetchManagerIncorrectAnnotations(
        @Query("start_time") startTime: String?,
        @Query("end_time") endTime: String?,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int,
        @Query("wf_step_id") wfStepId: Int?
    ): IncorrectRecordsResponse?

    @GET("/api/manager/dashboard/incorrect_reviews_by_wf_step")
    suspend fun fetchManagerIncorrectReviewsWfSteps(
        @Query("start_time") startTime: String?,
        @Query("end_time") endTime: String?
    ): List<IncorrectWfStep>?

    @GET("/api/manager/dashboard/incorrect_reviews")
    suspend fun fetchManagerIncorrectReviews(
        @Query("start_time") startTime: String?,
        @Query("end_time") endTime: String?,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int,
        @Query("wf_step_id") wfStepId: Int?
    ): IncorrectRecordsResponse?

    @GET("/api/work/templates")
    suspend fun fetchTemplates(@Query("wf_step_id") wfStepId: Int?): TemplatesResponse?

    @POST("/api/work/templates")
    suspend fun addLabel(@Body addTemplateRequest: AddTemplateRequest): TemplatesResponse?

    /**
     * LeaderHomeStatsResponse APIs
     * */
    @GET("/api/user/leaders/dashboard_stats")
    suspend fun fetchLeaderHomeStats(): LeaderHomeStatsResponse?

    @GET("/api/user/leaders/members")
    suspend fun fetchTeamMembers(): TeamMemberResponse

    @GET("/api/user/leaders/overall_stats")
    suspend fun fetchOverallTeamPerformance(
        @Query("start_time") startTime: String?,
        @Query("end_time") endTime: String?,
        @Query("user_type") userType: String,
    ): OverallTeamPerformanceResponse?

    @GET("/api/user/leaders/team_member_stats")
    suspend fun fetchTeamMembersPerformance(
        @Query("start_time") startTime: String?,
        @Query("end_time") endTime: String?,
        @Query("user_type") userType: String,
    ): MembersPerformanceResponse?

    @POST("/api/user/leaders/request_member")
    suspend fun requestNewMember(@Body request: NewMemberRequest): RequestMemberResponse?

    @GET("/api/user/leaders/member_incorrect_judgments_by_wf_step")
    suspend fun fetchMemberIncorrectJudgmentsWfSteps(
        @Query("start_time") startTime: String?,
        @Query("end_time") endTime: String?,
        @Query("user_role") userRole: String?,
        @Query("user_id") userId: Int?
    ): List<IncorrectWfStep>?

    @GET("/api/user/leaders/member_incorrect_judgments")
    suspend fun fetchMemberIncorrectJudgments(
        @Query("start_time") startTime: String?,
        @Query("end_time") endTime: String?,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int,
        @Query("user_role") userRole: String?,
        @Query("user_id") userId: Int?,
        @Query("wf_step_id") wfStepId: Int?
    ): IncorrectRecordsResponse?

    @GET("/api/user/leaders/member_incorrect_annotations_by_wf_step")
    suspend fun fetchMemberIncorrectAnnotationsWfSteps(
        @Query("start_time") startTime: String?,
        @Query("end_time") endTime: String?,
        @Query("user_role") userRole: String?,
        @Query("user_id") userId: Int?
    ): List<IncorrectWfStep>?

    @GET("/api/user/leaders/member_incorrect_annotations")
    suspend fun fetchMemberIncorrectAnnotations(
        @Query("start_time") startTime: String?,
        @Query("end_time") endTime: String?,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int,
        @Query("user_role") userRole: String?,
        @Query("user_id") userId: Int?,
        @Query("wf_step_id") wfStepId: Int?
    ): IncorrectRecordsResponse?

    @GET("/api/user/leaders/member_incorrect_reviews_by_wf_step")
    suspend fun fetchMemberIncorrectReviewsWfSteps(
        @Query("start_time") startTime: String?,
        @Query("end_time") endTime: String?,
        @Query("user_role") userRole: String?,
        @Query("user_id") userId: Int?
    ): List<IncorrectWfStep>?

    @GET("/api/user/leaders/member_incorrect_reviews")
    suspend fun fetchMemberIncorrectReviews(
        @Query("start_time") startTime: String?,
        @Query("end_time") endTime: String?,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int,
        @Query("user_role") userRole: String?,
        @Query("user_id") userId: Int?,
        @Query("wf_step_id") wfStepId: Int?
    ): IncorrectRecordsResponse?

    @GET("/api/user/leaders/leader_overall_stats")
    suspend fun fetchLeaderPerformance(
        @Query("start_time") startTime: String?,
        @Query("end_time") endTime: String?
    ): LeaderPerformanceResponse

    /**
     * Admin Work APIs
     **/
    @GET("/api/admin/dashboard")
    suspend fun fetchDeveloperHomeStats(): StatsResponse?

    @GET("/api/admin/workflows")
    suspend fun fetchWorkflows(): List<WorkFlow>?

    @GET("/api/admin/workflows/{work_flow_id}/wf_steps")
    suspend fun fetchWfSteps(@Path("work_flow_id") workFlowId: Int): List<WfStep>?

    @GET("/api/admin/work_assignments/wf_steps_for_work")
    suspend fun adminRequestWorkStep(): ActiveWfStepResponse

    @POST("/api/admin/work_assignments")
    suspend fun adminWorkAssignment(@Body requestBody: AdminWorkAssignment): Work?

    @GET("/api/admin/work_assignments/{id}/records")
    suspend fun adminNextRecords(@Path(value = "id") workAssignmentId: Int?): Records?

    @POST("/api/admin/work/submit_review")
    suspend fun submitAdminReview(@Body submitReviewRequest: SubmitReviewRequest): SubmitReviewResponse

    @GET("/api/system/data_records")
    suspend fun fetchDataRecords(
        @Query("page") page: Int,
        @Query("per_page") perPage: Int,
        @Query("aasm_state") aasmState: String?,
        @Query("start_time") startTime: String?,
        @Query("end_time") endTime: String?,
        @Query("wf_step_id") wfStepId: Int?
    ): DataRecordsResponse

    @GET("/api/system/data_records/{id}")
    suspend fun fetchDataRecord(@Path("id") id: Int?): DataRecordResponse

    @POST("/api/ai/data_records/{id}/update_starred")
    suspend fun updateRecordStarredStatus(
        @Path("id") recordId: Int,
        @Body requestBody: RecordUpdateStarredRequest
    ): RecordStarredStatusResponse

    @GET("/api/system/data_records/states")
    suspend fun fetchAasmStats(): AasmStatesResponse

    /**
     * Driver APIs
     * */
    @GET("/api/driver/dashboard/stats")
    suspend fun fetchDriverStats(
        @Query("start_time") startTime: String? = null,
        @Query("end_time") endTime: String? = null
    ): DriverStatsResponse?

    @GET("/api/driver/videos/coordinates")
    suspend fun fetchVideoCoordinates(
        @Query("start_time") startTime: String? = null,
        @Query("end_time") endTime: String? = null
    ): VideooCoordinatesResponse?

    @GET("/api/driver/surge_locations")
    suspend fun fetchSurgeLocations(): SurgeLocationsResponse?

    @POST("/api/driver/surge_locations/city_kml_data")
    suspend fun fetchCityWardSpecs(@Body cityKmlRequest: CityKmlRequest): CityKmlResponse?

    @GET("/api/driver/videos/record_event_type")
    suspend fun getEvents(): EventsResponse?

    @GET("/api/driver/camera_ai_flows")
    suspend fun getAiWorkFlow(
        @Query("latitude") latitude: String?,
        @Query("longitude") longitude: String?
    ): CameraAIWorkFlowResponse?

    /**
     *Wallet APIs
     **/
    @GET("/api/user/wallet/details")
    suspend fun fetchWalletDetails(): PayoutResponse

    @GET("/api/user/wallet/transactions")
    suspend fun fetchTransactions(
        @Query("wallet_type") walletType: String,
        @Query("page") pageNumber: Int
    ): PayoutResponse

    @GET("/api/user/profile/fetch_referal_network")
    suspend fun fetchReferralTransactions(): ReferralPayoutResponse

    @GET("/api/user/wallet/create_payout")
    suspend fun createPayout(@Query("wallet_type") walletType: String?): CreatePayoutResponse

    /**
     * User Profile APIs
     **/
    @Multipart
    @PUT("/api/user/profile/upload_user_photo")
    suspend fun uploadImage(@Part image: MultipartBody.Part): UserResponse

    @GET("/api/user/profile/details")
    suspend fun fetchUserDetails(): User

    @GET("/api/user/profile/authenticate_password")
    suspend fun authenticatePassword(@Query("password") password: String): AuthenticationResponse

    @POST("/api/user/profile/update_details")
    suspend fun updatePersonalInfo(@Body personalInfoRequest: UpdatePersonalInfoRequest): UserResponse

    @Multipart
    @POST("/api/user/profile/update_pan")
    suspend fun updatePanDetails(
        @Part("identification_type") idTypePart: RequestBody,
        @Part("pan_number") numberPart: RequestBody,
        @Part image: MultipartBody.Part
    ): UserResponse

    @Multipart
    @POST("/api/user/profile/update_photo_id")
    suspend fun updatePhotoIdDetails(
        @Part("identification_type") idTypePart: RequestBody,
        @Part("photo_id_number") numberPart: RequestBody,
        @Part image: MultipartBody.Part
    ): UserResponse

    @GET("/api/user/profile/bank_details")
    suspend fun fetchBankDetails(@Query("id_token") token: String?): BankDetails

    @POST("/api/user/profile/update_bank_details")
    suspend fun updateBankDetails(@Body request: UpdateBankDetailsRequest): BankDetails

    @FormUrlEncoded
    @POST("/api/user/profile/update_language")
    suspend fun saveLanguage(@Field("language") language: String): LanguageSuccessResponse

    @POST("/api/user/profile/update_phone_number")
    suspend fun updatePhoneNumber(@Body request: PhoneVerificationRequest): PhoneVerificationResponse?

    @PATCH("/api/user/profile/process_referal_code")
    suspend fun updateReferralCode(@Body request: ReferralCodeRequest): ReferralCodeResponse?

    @GET("/api/user/profile/validate_phone_otp")
    suspend fun validateOTP(
        @Query("id_token") token: String?,
        @Query("otp") otp: String?
    ): User?

    @POST("/api/user/profile/update_password")
    suspend fun updatePassword(@Body updatePasswordRequest: UpdatePasswordRequest): UpdatePasswordResponse

    /**
     * Sessions API
     * */
    @POST("/api/user/session_log")
    suspend fun submitSessions(@Body submitSessionsRequest: SubmitSessionsRequest)

    /**
     * App Utils
     **/
    @GET("/api/app_version")
    suspend fun fetchMinVersionCode(): FetchAppMinVersionResponse?

    @GET("/api/system/allowed_locations")
    suspend fun fetchAllowedLocations(): AllowedLocationResponse?

    /**
     * Notifications
     **/
    @POST("/api/user/notifications/create_or_update_token")
    suspend fun registerFCMToken(@Body fcmTokenRequest: TokenRequest)

    @POST("/api/user/notifications/remove_token")
    suspend fun unregisterFCMToken()

    @POST("/api/system/rt_supports/send_init_notification")
    suspend fun sendInitNotification(@Body sendNotificationRequest: SendNotificationRequest): SendNotificationResponse

    /**
     * RTC
     **/
    @GET("/api/system/rt_supports/call_user_list")
    suspend fun fetchUsersList(
        @Query("page") page: Int,
        @Query("per_page") perPage: Int
    ): CallUserListResponse

    @POST("/api/system/data_records/report_failure")
    suspend fun sendCorruptCallback(@Body dataRecordsCorrupt: DataRecordsCorrupt): Records?
}