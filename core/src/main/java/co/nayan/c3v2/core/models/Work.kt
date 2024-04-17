package co.nayan.c3v2.core.models

import android.os.Parcelable
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
import java.util.Date

@Keep
@Parcelize
data class Work(
    val workRequestId: Int?,
    val workAssignment: WorkAssignment?,
    val status: String?,
    val locked: Boolean?,
    val message: String?,
    val wfStep: WfStep?,
    val incorrectSniffingRecords: List<Record>?
) : Parcelable

@Keep
@Parcelize
data class WorkAssignment(
    val id: Int?,
    val assigned: Boolean?,
    val lastAssignmentTime: Date?,
    val workType: String?,
    val applicationMode: String?,
    val wfStep: WfStep?,
    val sandboxRequired: Boolean?,
    val mediaType: String?,
    val potentialPoints: String?,
    var faqRequired: Boolean?,
    val illustration: Illustration?,
    val userCategory: String?,
    var isEarningShown: Boolean = false
) : Parcelable

@Keep
@Parcelize
data class Illustration(
    val id: Int?,
    val link: String?,
    val wfStepId: Int?,
) : Parcelable

data class ActiveWfStepResponse(
    val data: List<ActiveWfStep>?,
    val success: Boolean
)

@Keep
@Parcelize
data class ActiveWfStep(
    val id: Int,
    val name: String,
    val workflowName: String,
    val count: Int
) : Parcelable

@Keep
@Parcelize
data class WfStep(
    val id: Int?,
    val name: String?,
    val position: Int?,
    val requiredVotes: Int?,
    val thresholdVotes: Int?,
    val maxSpecialistAnnotationCycles: Int?,
    val question: String?,
    val applicationModeName: String?,
    val sandboxId: Int?,
    val mediaType: String?,
    val aiAssistEnabled: Boolean?,
    var cameraAiModel: CameraAIModel?,
    var annotationVariationThreshold: Int?
) : Parcelable

@Keep
@Parcelize
data class CameraAIModel(
    @SerializedName("id")
    val id: Int,
    @SerializedName("name")
    val name: String?,
    @SerializedName("version")
    val version: String?,
    @SerializedName("category")
    val category: String?,
    @SerializedName("active")
    val active: Boolean,
    @SerializedName("link")
    val link: String?,
    @SerializedName("label_file")
    val labelFile: String?,
    @SerializedName("input_type")
    val inputType: String?,
    @SerializedName("width")
    val width: Int,
    @SerializedName("height")
    val height: Int,
    @SerializedName("mean")
    val mean: Float?,
    @SerializedName("standard_deviation")
    val standardDeviation: Float?,
    @SerializedName("camera_ai_model_rules")
    val cameraAiModelRules: MutableList<CameraAIModelRule>?,
    @SerializedName("number_of_detection")
    val numberOfDetection: Int,
    @SerializedName("is_quantised")
    val isQuantised: Boolean,
    @SerializedName("detection_data_type")
    val detectionDataType: String?,
    @SerializedName("output_data_type")
    val outputDataType: String?,
    @SerializedName("label_array")
    val labelArray: MutableList<String>,
    @SerializedName("ram")
    val ram: Float,
    @SerializedName("temperature")
    val temperature: Float,
    @SerializedName("checksum")
    val checksum: String?
) : Parcelable

@Keep
@Parcelize
data class CameraAIModelRule(
    @SerializedName("id")
    val id: Int,
    @SerializedName("label")
    val label: String?,
    @SerializedName("size")
    val size: AIModelRuleSize?,
    @SerializedName("aspect_ratio")
    val aspectRatio: Float?,
    @SerializedName("confidence")
    val confidence: Float,
    @SerializedName("next_model")
    val nextModel: CameraAIModel?
) : Parcelable

@Keep
@Parcelize
data class AIModelRuleSize(
    @SerializedName("height")
    val height: Int?,
    @SerializedName("width")
    val width: Int?
) : Parcelable

@Keep
@Parcelize
data class RecordJudgment(
    val recordAnnotationId: Int?,
    val judgment: Boolean,
    val isSniffing: Boolean? = null,
    val dataRecordId: Int? = null,
    val isSniffingCorrect: Boolean? = null
) : Parcelable

@Keep
@Parcelize
data class RecordReview(
    val recordId: Int,
    val review: Boolean,
    val isSniffing: Boolean? = null,
    val isSniffingCorrect: Boolean? = null
) : Parcelable

@Keep
@Parcelize
data class RecordAnnotation(
    val dataRecordId: Int,
    val annotationObjectsAttributes: List<AnnotationObjectsAttribute>,
    val isSniffing: Boolean? = null,
    val isSniffingCorrect: Boolean? = null
) : Parcelable

@Keep
@Parcelize
data class AnnotationObjectsAttribute(
    val annotationValue: AnnotationValue?
) : Parcelable

@Keep
@Parcelize
data class AnnotationValue(
    val answer: String?
) : Parcelable