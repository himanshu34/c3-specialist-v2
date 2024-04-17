package co.nayan.c3v2.core.api

import co.nayan.c3v2.core.models.login_module.CarCamRequest
import co.nayan.c3v2.core.models.login_module.ForgotPasswordRequest
import co.nayan.c3v2.core.models.login_module.ForgotPasswordResponse
import co.nayan.c3v2.core.models.login_module.LoginRequest
import co.nayan.c3v2.core.models.login_module.LoginResponse
import co.nayan.c3v2.core.models.login_module.SignUpRequest
import co.nayan.c3v2.core.models.login_module.SignUpResponse
import co.nayan.c3v2.core.models.login_module.SocialSignUpRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiClientLogin {

    @POST("/api/auth/sign_in")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("/api/auth")
    suspend fun signUp(@Body request: SignUpRequest): Response<SignUpResponse>

    @POST("/api/passwords/forgot")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<ForgotPasswordResponse>

    @POST("/api/auth")
    suspend fun socialSignUp(@Body request: SocialSignUpRequest): Response<SignUpResponse>

    @POST("/api/auth/tp_sign_in")
    suspend fun camCamLogin(@Body request: CarCamRequest): Response<LoginResponse>
}