package co.nayan.c3v2.core.models

import android.graphics.Bitmap
import android.os.Parcelable
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

data class Records(
    val nextRecords: List<Record>?
)

/**
 * In Sandbox correct answer will be  [annotation] and
 * in sniffing [currentAnnotation] will be the correct answer
 * */
@Keep
@Parcelize
data class Record(
    val id: Int,
    val displayImage: String?,
    val workAssignmentId: Int?,
    val parentAnnotation: CurrentAnnotation?,
    val currentAnnotation: CurrentAnnotation?,
    val currentJudgment: RecordJudgment?,
    var annotation: List<AnnotationObjectsAttribute>?,
    var starred: Boolean? = false,
    val isSniffingRecord: Boolean?,
    val needsRejection: Boolean?,
    val mediaUrl: String?,
    val mediaType: String?,
    var randomSniffingId: Int?,
    val recordAnnotations: List<RecordAnnotationHistory>?,
    var applicationMode: String?,
    val questionAnnotation: Question?,
    val questionValidation: Question?,
    var driverId: String? = "",
    var videoRecordedOn: String? = "",
    var videoSourceId: String? = "",
    var videoId: String? = "",
    var annotationPriority: Int? = 0,
    var judgmentPriority: Int? = 0,
    var reviewPriority: Int? = 0,
    var cityKmlPriority: Int? = 0,
    var metatag: String? ="",
    var isDownloaded: Boolean = false,
    var isFramesExtracted: Boolean = false
) : Parcelable {
    override fun equals(other: Any?): Boolean {
        return (other != null) && (other is Record) && id == other.id
    }

    override fun hashCode(): Int {
        return id
    }
}

@Keep
@Parcelize
data class Question(
    val en: String?,
    val hi: String?
) : Parcelable

@Keep
@Parcelize
data class CurrentAnnotation(
    val id: Int? = null,
    val dataRecordId: Int? = null,
    val workAssignmentId: Int? = null,
    val userId: Int? = null,
    val annotationObjects: List<AnnotationObject>? = null,
    val potentialScore: String? = null,
    val score: String? = null,
    val scoreProcessed: Boolean? = null,
    val scoreAwarded: Boolean? = null,
    val complete: Boolean? = null,
    val result: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val discardedAt: String? = null,
    val reviewedById: Int? = null,
    val reviewedAt: String? = null
) : Parcelable

@Keep
@Parcelize
data class AnnotationObject(
    val id: Int?,
    val annotationValue: AnnotationValue
) : Parcelable

@Keep
@Parcelize
data class VideoAnnotationObject(
    val id: Int?,
    val timeStamp: String?,
    val annotationObjects: List<AnnotationObject>?
) : Parcelable

@Keep
@Parcelize
data class AnnotationData(
    var objectIndex: String? = null,
    var objectName: String? = null,
    var points: MutableList<ArrayList<Float>>? = null,
    val type: String?,
    var input: String? = null,
    var tags: List<String>? = null,
    val segmentRatioList: MutableList<Float>? = mutableListOf(),
    val inputList: MutableList<String>? = mutableListOf(),
    val thicknessRatio: Float? = null,
    val maskUrl: String? = null,
    var frameCount: Int? = null,
    var parentAnnotation: String? = null,
    @Transient var rawAnnotation: String? = null,
    var paintColor: String? = null,
    var inProgress: Boolean = true,
    val value: String? = null,
    @SerializedName("annotation_state")
    var annotationState: AnnotationState? = AnnotationState.DEFAULT,
    var shouldRemove: Boolean = false
) : Parcelable

data class VideoAnnotationData(
    var frameCount: Int?,
    var bitmap: Bitmap?,
    var selected: Boolean = false,
    var showPreview: Boolean = false,
    val annotations: MutableList<AnnotationData> = mutableListOf(),
    var isParent: Boolean = false,
    var rawAnnotation: String? = null,
    var isJunk: Boolean = false,
    var isConsiderForChildSandboxJudgment: Boolean = false,
)

data class SandboxVideoAnnotationData(
    val frameCount: Int?,
    val correctVideoAnnotation: VideoAnnotationData,
    var userVideoAnnotation: VideoAnnotationData? = null,
    var judgement: Boolean = false
)

@Keep
@Parcelize
data class Template(
    val templateName: String,
    val id: Int? = 0,
    val templateType: String? = "",
    val templateIcon: String? = "",
    val remoteTemplateIconUrl: String? = "",
    val superButton: Boolean? = false,
    var isSelected: Boolean = false,
    var annotationCount: Int = 0,
    var isClicked: Boolean = false
) : Parcelable

data class CacheTemplate(
    val wfStepId: Int?,
    var count: Int = 0,
    val template: Template
)

data class SubmittedAnswers(
    val recordIds: List<Int>?,
    val annotationIds: List<Int>?,
    val sniffingPassed: Boolean = true,
    val isAccountLocked: Boolean = false,
    val incorrectSniffingIds: List<Int>?
)

@Keep
@Parcelize
data class RecordAnnotationHistory(
    val id: Int?,
    val result: String?,
    val type: String?,
    val createdBy: String?,
    val annotationObjects: List<AnnotationObject>?,
    val annotationJudgments: List<AnnotationJudgment>?,
    val annotationReviews: List<AnnotationJudgment>?
) : Parcelable

@Keep
@Parcelize
data class AnnotationJudgment(
    val id: Int?,
    val type: String?,
    val createdBy: String?,
    val judgment: Boolean?
) : Parcelable

data class DataRecordsCorrupt(
    val dataRecordsCorruptRecord: DataRecordsCorruptRecord
)

data class DataRecordsCorruptRecord(
    val dataRecordId: Int?,
    val workAssignmentId: Int?,
    val firstRecord: Boolean?,
    val sniffing: Boolean?,
    val errorCode: String?,
    val message: String?
)