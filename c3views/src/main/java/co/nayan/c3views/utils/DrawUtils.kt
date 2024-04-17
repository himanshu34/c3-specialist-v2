package co.nayan.c3views.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.Base64
import co.nayan.c3v2.core.config.DrawType
import co.nayan.c3v2.core.models.AnnotationData
import co.nayan.c3v2.core.models.AnnotationObjectsAttribute
import co.nayan.c3v2.core.models.AnnotationState
import co.nayan.c3v2.core.models.AnnotationValue
import co.nayan.c3v2.core.models.CurrentAnnotation
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.models.RecordAnnotationHistory
import co.nayan.c3v2.core.models.VideoAnnotationObject
import co.nayan.c3views.crop.CropPoint
import co.nayan.c3views.crop.Cropping
import co.nayan.c3views.dragsplit.SplitCropping
import co.nayan.c3views.paint.PaintData
import co.nayan.c3views.quadrilateral.Quadrilateral
import co.nayan.c3views.quadrilateral.QuadrilateralPoint
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import timber.log.Timber
import java.io.ByteArrayOutputStream

fun scaleFactor(scale: Float): Float {
    return when (scale) {
        1.0f -> 1.0f
        else -> 1.0f + (scale * 0.25f)
    }
}

fun Record.drawType(): String? {
    return currentAnnotation?.drawType()
}

fun CurrentAnnotation.drawType(): String? {
    val annotationObjects = annotationObjects
    return if (annotationObjects.isNullOrEmpty()) {
        null
    } else {
        annotationObjects.first().annotationValue.drawType()
    }
}

fun RecordAnnotationHistory.drawType(): String? {
    val annotationObjects = annotationObjects
    return if (annotationObjects.isNullOrEmpty()) {
        null
    } else {
        annotationObjects.first().annotationValue.drawType()
    }
}

fun List<AnnotationObjectsAttribute>.drawType(): String? {
    return if (this.isNullOrEmpty()) {
        null
    } else {
        this.first().annotationValue?.drawType()
    }
}

fun AnnotationValue?.drawType(): String? {
    val answer = this?.answer
    return if (answer.isNullOrEmpty()) null
    else {
        val gson = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
        return try {
            gson.fromJson<AnnotationData>(
                answer,
                object : TypeToken<AnnotationData>() {}.type
            )?.type
        } catch (e: JsonSyntaxException) {
            null
        }
    }
}

fun Record.answer(isSandboxRecord: Boolean? = false): String? {
    return if (isSandboxRecord == true)
        annotation?.answer()
    else currentAnnotation?.answer()
}

fun RecordAnnotationHistory.answer(): String? {
    return annotationObjects?.firstOrNull()?.annotationValue?.answer
}

fun CurrentAnnotation.answer(): String? {
    return annotationObjects?.firstOrNull()?.annotationValue?.answer
}

fun List<AnnotationObjectsAttribute>?.answer(): String {
    return this?.firstOrNull()?.annotationValue?.answer ?: ""
}

fun Record?.annotations(isSandboxRecord: Boolean? = false): List<AnnotationData> {
    return if (isSandboxRecord == true)
        this?.annotation?.annotations() ?: emptyList()
    else this?.currentAnnotation?.annotations() ?: emptyList()
}

fun CurrentAnnotation?.annotations(): List<AnnotationData> {
    return if (this == null) emptyList()
    else {
        val annotations = mutableListOf<AnnotationData>()
        annotationObjects?.forEach {
            val answer = it.annotationValue.answer
            val gson = GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create()
            try {
                if (!answer.equals("junk", ignoreCase = true)) {
                    val annotationData = gson.fromJson<AnnotationData>(
                        answer,
                        object : TypeToken<AnnotationData>() {}.type
                    )
                    annotationData?.let { data ->
                        if (data.annotationState == null)
                            data.annotationState = AnnotationState.DEFAULT
                        annotations.add(data)
                    }
                }
            } catch (e: JsonSyntaxException) {
                e.printStackTrace()
            }
        }
        annotations
    }
}

fun RecordAnnotationHistory?.annotations(): List<AnnotationData> {
    return if (this == null) emptyList()
    else {
        val annotations = mutableListOf<AnnotationData>()
        annotationObjects?.forEach {
            val answer = it.annotationValue.answer
            val gson = GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create()
            try {
                val annotationData = gson.fromJson<AnnotationData>(
                    answer,
                    object : TypeToken<AnnotationData>() {}.type
                )
                annotationData?.let { data -> annotations.add(data) }
            } catch (e: JsonSyntaxException) {
                e.printStackTrace()
            }
        }
        annotations
    }
}

fun CurrentAnnotation?.videoAnnotations(): List<List<AnnotationData>> {
    return if (this == null) {
        emptyList()
    } else {
        val annotations = mutableListOf<MutableList<AnnotationData>>()
        annotationObjects?.forEach {
            val answer = it.annotationValue.answer
            val stepInfo = mutableListOf<AnnotationData>()
            extractStepInformation(answer, stepInfo)
            annotations.add(stepInfo)
        }
        annotations
    }
}

fun List<AnnotationObjectsAttribute>?.videoAnnotations(): List<List<AnnotationData>> {
    return if (this.isNullOrEmpty()) {
        emptyList()
    } else {
        val annotations = mutableListOf<MutableList<AnnotationData>>()
        forEach { stepAnnotations ->
            val stepInfo = mutableListOf<AnnotationData>()
            stepAnnotations.annotationValue?.let { value ->
                extractStepInformation(value.answer, stepInfo)
                annotations.add(stepInfo)
            }
        }
        annotations
    }
}

fun extractStepInformation(
    answer: String?,
    stepInfoList: MutableList<AnnotationData>
) {
    answer?.let {
        try {
            val gson = GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create()
            val annotationData =
                gson.fromJson<AnnotationData>(answer, object : TypeToken<AnnotationData>() {}.type)
            annotationData?.let { data ->
                data.parentAnnotation?.let { extractStepInformation(it, stepInfoList) }
                data.rawAnnotation = answer
                stepInfoList.add(data)
            }
        } catch (e: JsonSyntaxException) {
            e.printStackTrace()
        }
    }
}

fun VideoAnnotationObject?.annotations(): List<AnnotationData> {
    return if (this == null) emptyList()
    else {
        val annotations = mutableListOf<AnnotationData>()
        annotationObjects?.forEach {
            val answer = it.annotationValue.answer
            val gson = GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create()
            try {
                val annotationData = gson.fromJson<AnnotationData>(
                    answer,
                    object : TypeToken<AnnotationData>() {}.type
                )
                annotationData?.let { data -> annotations.add(data) }
            } catch (e: JsonSyntaxException) {
                e.printStackTrace()
            }
        }
        annotations
    }
}

fun List<AnnotationObjectsAttribute>?.annotations(): List<AnnotationData> {
    return if (this.isNullOrEmpty()) emptyList()
    else {
        val annotationData = mutableListOf<AnnotationData>()
        forEach {
            it.annotationValue?.annotationData()?.let { data ->
                annotationData.add(data)
            }
        }
        annotationData
    }
}

fun AnnotationValue.annotationData(): AnnotationData? {
    return if (answer.isNullOrEmpty()) null
    else {
        val gson = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
        try {
            gson.fromJson<AnnotationData>(answer, object : TypeToken<AnnotationData>() {}.type)
        } catch (e: JsonSyntaxException) {
            e.printStackTrace()
            null
        }
    }
}

fun getAnnotationAttribute(annotationData: AnnotationData): AnnotationObjectsAttribute {
    val points = annotationData.points
    val answer = AnnotationData(
        points = points,
        input = annotationData.input,
        tags = annotationData.tags,
        type = DrawType.BOUNDING_BOX
    )
    val gson = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()
    return AnnotationObjectsAttribute(AnnotationValue(answer = gson.toJson(answer)))
}

fun List<AnnotationData>.crops(bitmap: Bitmap, color: String? = null): List<Cropping> {
    val crops = mutableListOf<Cropping>()
    forEach { it.crop(bitmap, color)?.let { cropping -> crops.add(cropping) } }
    return crops
}

fun AnnotationData.crop(bitmap: Bitmap, color: String? = null): Cropping? {
    val points = points
    if (points?.size == 2) {
        if (isValidCrop(bitmap)) {
            val firstPoint = CropPoint(points.first().first(), points.first().last())
                .transformWrtBitmap(bitmap)
            val secondPoint = CropPoint(points.last().first(), points.last().last())
                .transformWrtBitmap(bitmap)
            return Cropping(
                firstPoint,
                secondPoint,
                input,
                tags,
                paintColor = color ?: paintColor,
                id = objectIndex,
                annotationState = annotationState,
                shouldRemove = shouldRemove
            )
        }
    }
    return null
}

private fun AnnotationData.isValidCrop(bitmap: Bitmap): Boolean {
    val points = points
    if (points?.size == 2) {
        val p1 = points.first()
        val p2 = points.last()
        return if (p1.size == 2 && p2.size == 2) {
            val rect = RectF(p1.first(), p1.last(), p2.first(), p2.last())
            return rect.isValid(bitmap)
        } else false
    }
    return false
}

private fun RectF.isValid(bitmap: Bitmap): Boolean {
    val isValid = left >= 0 && top >= 0 && right <= bitmap.width && bottom <= bitmap.height
    if (isValid) Timber.d("$this, [${bitmap.width}, ${bitmap.height}]")
    else Timber.e("$this, [${bitmap.width}, ${bitmap.height}]")
    return isValid
}

fun List<RectF>.drawCrops(bitmap: Bitmap, labelName: String? = null): MutableList<Cropping> {
    val crops = mutableListOf<Cropping>()
    forEach {
        if (it.isValid(bitmap)) {
            val firstPoint = CropPoint(it.left, it.top).transformWrtBitmap(bitmap)
            val secondPoint = CropPoint(it.right, it.bottom).transformWrtBitmap(bitmap)
            val cropping = Cropping(
                firstPoint,
                secondPoint,
                labelName,
                annotationState = AnnotationState.AI_ASSIST,
                shouldRemove = true
            )
            crops.add(cropping)
        }
    }
    return crops
}

fun List<RectF>.splitCrops(bitmap: Bitmap): List<SplitCropping> {
    val splitCrops = mutableListOf<SplitCropping>()
    forEach {
        if (it.isValid(bitmap)) {
            val touchDown = CropPoint(it.left, it.top).transformWrtBitmap(bitmap)
            val topRight = CropPoint(it.right, it.top).transformWrtBitmap(bitmap)
            val releasePoint = CropPoint(it.right, it.bottom).transformWrtBitmap(bitmap)
            val bottomLeft = CropPoint(it.left, it.bottom).transformWrtBitmap(bitmap)

            val splitCropping = SplitCropping(
                touchDown,
                releasePoint,
                mutableListOf(1f),
                mutableListOf("")
            )
            splitCropping.setOtherPoints(topRight, bottomLeft)
            splitCrops.add(splitCropping)
        }
    }
    return splitCrops
}

fun List<AnnotationData>.splitCrops(bitmap: Bitmap, color: String? = null): List<SplitCropping> {
    val splitCrops = mutableListOf<SplitCropping>()
    forEach {
        if (it.type == DrawType.SPLIT_BOX) {
            val points = it.points
            if (points?.size == 4) {
                val p1 = points[0]
                val p2 = points[1]
                val p3 = points[2]
                val p4 = points[3]
                if (p1.size == 2 && p3.size == 2 && p2.size == 2 && p4.size == 2) {
                    val touchDown = CropPoint(p1.first(), p1.last()).transformWrtBitmap(bitmap)
                    val topRight = CropPoint(p2.first(), p2.last()).transformWrtBitmap(bitmap)
                    val releasePoint = CropPoint(p3.first(), p3.last()).transformWrtBitmap(bitmap)
                    val bottomLeft = CropPoint(p4.first(), p4.last()).transformWrtBitmap(bitmap)

                    val splitCropping = SplitCropping(
                        touchDown,
                        releasePoint,
                        it.segmentRatioList ?: mutableListOf(1f),
                        it.inputList ?: mutableListOf(""),
                        paintColor = color
                    )
                    splitCropping.setOtherPoints(topRight, bottomLeft)
                    splitCrops.add(splitCropping)
                }
            }
        }
    }
    return splitCrops
}

fun List<RectF>.quadrilaterals(bitmap: Bitmap): List<Quadrilateral> {
    val quadrilaterals = mutableListOf<Quadrilateral>()
    forEach {
        if (it.isValid(bitmap)) {
            val touchDown = QuadrilateralPoint(it.left, it.top).transformWrtBitmap(bitmap)
            val topRight = QuadrilateralPoint(it.right, it.top).transformWrtBitmap(bitmap)
            val releasePoint = QuadrilateralPoint(it.right, it.bottom).transformWrtBitmap(bitmap)
            val bottomLeft = QuadrilateralPoint(it.left, it.bottom).transformWrtBitmap(bitmap)
            val quadrilateral = Quadrilateral(touchDown, releasePoint)
            quadrilateral.setOtherPoints(topRight, bottomLeft)
            quadrilaterals.add(quadrilateral)
        }
    }
    return quadrilaterals
}

fun List<AnnotationData>.quadrilaterals(
    bitmap: Bitmap,
    color: String? = null
): List<Quadrilateral> {
    val quadrilaterals = mutableListOf<Quadrilateral>()
    forEach {
        val points = it.points
        if (points?.size == 4) {
            val p1 = points[0]
            val p2 = points[1]
            val p3 = points[2]
            val p4 = points[3]
            if (p1.size == 2 && p3.size == 2 && p2.size == 2 && p4.size == 2) {
                val touchDown = QuadrilateralPoint(p1.first(), p1.last()).transformWrtBitmap(bitmap)
                val topRight = QuadrilateralPoint(p2.first(), p2.last()).transformWrtBitmap(bitmap)
                val releasePoint =
                    QuadrilateralPoint(p3.first(), p3.last()).transformWrtBitmap(bitmap)
                val bottomLeft =
                    QuadrilateralPoint(p4.first(), p4.last()).transformWrtBitmap(bitmap)
                val quadrilateral = Quadrilateral(touchDown, releasePoint, paintColor = color)
                quadrilateral.setOtherPoints(topRight, bottomLeft)
                quadrilaterals.add(quadrilateral)
            }
        }
    }
    return quadrilaterals
}

fun List<AnnotationData>.polygonPoints(bitmap: Bitmap): List<PointF> {
    val polyPoints = mutableListOf<PointF>()
    forEach {
        it.points?.forEach { point ->
            if (point.size == 2) {
                val x = point.first() / bitmap.width
                val y = point.last() / bitmap.height
                polyPoints.add(PointF(x, y))
            }
        }
    }
    return polyPoints
}

fun List<AnnotationData>.paintDataList(
    color: String? = "#FFEB3B",
    bitmap: Bitmap? = null
): List<PaintData> {
    val dataList = mutableListOf<PaintData>()
    forEach {
        val type = it.type
        val points = mutableListOf<List<Float>>()
        it.points?.forEach { p1 -> points.add(p1) }
        if (!type.isNullOrEmpty() && !points.isNullOrEmpty()) {
            val paint = Paint()
            paint.style = Paint.Style.STROKE
            paint.color = Color.parseColor(color)

            val paintData = PaintData(
                points = points,
                isAIGenerated = true,
                paint = paint,
                connectedPoints = mutableListOf(),
                type = type,
                thicknessRatio = it.thicknessRatio
            )
            paintData.fixViewPoints(bitmap?.width?.toFloat(), bitmap?.height?.toFloat())
            dataList.add(paintData)
        }
    }
    return dataList
}

fun String?.question(answer: String?): String {
    return if (this.isNullOrEmpty()) ""
    else {
        when {
            this.contains("%{answer}") -> {
                val replaceBy = if (!answer.isNullOrEmpty()) answer
                else "\" \""
                this.replaceFirst("%{answer}", replaceBy, true)
            }

            this.contains("{answer}") -> {
                val replaceBy = if (!answer.isNullOrEmpty()) answer
                else "\" \""
                this.replaceFirst("{answer}", replaceBy, true)
            }

            else -> this
        }
    }
}

fun Bitmap?.bitmapToByteArray(): ByteArray? {
    if (this == null) return null
    val compressionQuality = 100
    val byteArrayBitmapStream = ByteArrayOutputStream()
    compress(
        Bitmap.CompressFormat.JPEG, compressionQuality,
        byteArrayBitmapStream
    )
    return byteArrayBitmapStream.toByteArray()
}

fun ByteArray.byteArrayToBitmap(): Bitmap {
    return BitmapFactory.decodeByteArray(this, 0, this.size)
}

fun String.getBitmap(): Bitmap? {
    val decodedString =
        Base64.decode(this, Base64.DEFAULT)
    return BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
}

fun String?.initials(): String {
    if (this.isNullOrEmpty()) return ""
    val list = this.trim().split(" ")
    val firstCharSeq = StringBuilder()
    list.forEach {
        if (it.isNotEmpty()) firstCharSeq.append(it.first())
    }

    return firstCharSeq.toString().trim()
}

fun List<String>.initials(): String {
    val tagsValue = StringBuilder()
    forEach {
        tagsValue.append(it.initials()).append(",")
    }
    return tagsValue.toString().trim().substring(0, tagsValue.toString().trim().length - 1)
}