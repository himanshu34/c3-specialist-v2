package co.nayan.c3v2.core.models.c3_module.responses

import com.google.gson.annotations.SerializedName

data class RoleApiRequest(@SerializedName("roles") val request: List<String>)