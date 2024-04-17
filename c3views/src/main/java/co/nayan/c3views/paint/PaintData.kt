package co.nayan.c3views.paint

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathDashPathEffect
import androidx.annotation.Keep
import co.nayan.c3v2.core.config.DrawType
import co.nayan.c3v2.core.models.AnnotationData
import co.nayan.c3v2.core.models.AnnotationObjectsAttribute
import co.nayan.c3v2.core.models.AnnotationValue
import co.nayan.c3v2.core.models.Template
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import timber.log.Timber
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.max
import kotlin.math.sqrt

@Keep
data class PaintData(
    val points: MutableList<List<Float>>,
    val type: String = DrawType.POINTS,
    var paint: Paint = Paint(),
    val connectedPoints: MutableList<List<Float>> = mutableListOf(),
    var isSelected: Boolean = false,
    var isClassified: Boolean = false,
    var isAIGenerated: Boolean = false,
    var template: Template? = null,
    var makeTransparent: Boolean = false,
    var thicknessRatio: Float? = 10f
) {
    val id = UUID.randomUUID()!!

    fun fixViewPoints(bitmapWidth: Float?, bitmapHeight: Float?): PaintData {
        if (bitmapWidth == null || bitmapHeight == null) {
            return this
        }
        for (index in 0 until points.size) {
            val xResult = points[index].first() / bitmapWidth
            val yResult = points[index].last() / bitmapHeight
            points.removeAt(index)
            points.add(index, listOf(xResult, yResult))
        }
        return this
    }

    fun isValidLine(bitmapWidth: Float, bitmapHeight: Float): Boolean {
        return points.size >= 2 && shouldDiscard(bitmapWidth, bitmapHeight)
    }

    private fun shouldDiscard(
        bitmapWidth: Float,
        bitmapHeight: Float
    ): Boolean {
        val maxCoordinate = max(max(bitmapWidth, bitmapHeight), 100f)
        val threshold = (3.0 * maxCoordinate / 100).toInt()
        val distance = distanceOfLastLineSegment(bitmapWidth, bitmapHeight)
        return distance >= threshold
    }

    private fun distanceOfLastLineSegment(
        bitmapWidth: Float,
        bitmapHeight: Float
    ): Float {
        if (points.size < 1) return 0f
        val size = points.size
        val transformedPoints = mutableListOf<List<Float>>()
        points.forEach {
            val x = bitmapWidth * it.first()
            val y = bitmapHeight * it.last()
            transformedPoints.add(listOf(x, y))
        }
        val first = transformedPoints[size - 2]
        val second = transformedPoints[size - 1]
        return sqrt(
            (second.first() - first.first()) * (second.first() - first.first())
                    + (second.last() - first.last()) * (second.last() - first.last())
        )
    }

    fun getTemplatePaint(): Paint {
        template?.let { it ->
            var pathDashPathEffect: PathDashPathEffect? = null
            when {
                it.templateName.contains("white", ignoreCase = true) -> {
                    paint.color = Color.WHITE
                }
                it.templateName.contains("yellow", ignoreCase = true) -> {
                    paint.color = Color.YELLOW
                }
                else -> {
                    paint.color = Color.parseColor("#ccE66332")
                }
            }

            when {
                it.templateName.contains("ouble.*ashed".toRegex()) -> {
                    pathDashPathEffect = PathDashPathEffect(
                        makeDoubleLanePath(),
                        45f,
                        0f,
                        PathDashPathEffect.Style.MORPH
                    )
                }
                it.templateName.contains("broken solid", ignoreCase = true) -> {
                    pathDashPathEffect = PathDashPathEffect(
                        makeBrokenSolidLanePath(),
                        30f,
                        0f,
                        PathDashPathEffect.Style.MORPH
                    )
                }
                it.templateName.contains("double", ignoreCase = true) -> {
                    pathDashPathEffect = PathDashPathEffect(
                        makeDoubleLanePath(),
                        30f,
                        0f,
                        PathDashPathEffect.Style.MORPH
                    )
                }
                it.templateName.contains("dash", ignoreCase = true) -> {
                    pathDashPathEffect = PathDashPathEffect(
                        makeDefaultLanePath(),
                        45f,
                        0f,
                        PathDashPathEffect.Style.MORPH
                    )
                }
                it.templateName.contains("single", ignoreCase = true) -> {
                    pathDashPathEffect = PathDashPathEffect(
                        makeDefaultLanePath(),
                        30f,
                        0f,
                        PathDashPathEffect.Style.MORPH
                    )
                }
            }

            pathDashPathEffect?.let { effect ->
                paint.pathEffect = effect
            }
        }
        return paint
    }

    private fun makeDefaultLanePath(): Path {
        val p = Path()
        p.moveTo(-15f, 5f)
        p.lineTo(15f, 5f)
        p.lineTo(15f, -5f)
        p.lineTo(-15f, -5f)
        return p
    }

    private fun makeDoubleLanePath(): Path {
        val p = Path()
        p.moveTo(-15f, 6f)
        p.lineTo(15f, 6f)
        p.lineTo(15f, 2f)
        p.lineTo(-15f, 2f)
        p.close()
        p.moveTo(-15f, -6f)
        p.lineTo(15f, -6f)
        p.lineTo(15f, -2f)
        p.lineTo(-15f, -2f)
        return p
    }

    private fun makeBrokenSolidLanePath(): Path {
        val p = Path()
        p.moveTo(-15f, 6f)
        p.lineTo(0f, 6f)
        p.lineTo(0f, 2f)
        p.lineTo(-15f, 2f)
        p.close()
        p.moveTo(-15f, -6f)
        p.lineTo(15f, -6f)
        p.lineTo(15f, -2f)
        p.lineTo(-15f, -2f)
        return p
    }

    fun getAnnotationAttribute(
        bitmapWidth: Float, bitmapHeight: Float
    ): AnnotationObjectsAttribute {
        val transformedPoints = mutableListOf<ArrayList<Float>>()
        points.forEach {
            val x = bitmapWidth * it.first()
            val y = bitmapHeight * it.last()
            transformedPoints.add(arrayListOf(x, y))
        }

        Timber.e(transformedPoints.joinToString { "[$it]" })

        val answer = AnnotationData(
            points = transformedPoints,
            type = type,
            thicknessRatio = thicknessRatio
        )
        val gson = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
        return AnnotationObjectsAttribute(AnnotationValue(answer = gson.toJson(answer)))
    }
}

data class PaintDataOperation(
    val paintDataList: MutableList<PaintData>, val isDeleteOperation: Boolean
)