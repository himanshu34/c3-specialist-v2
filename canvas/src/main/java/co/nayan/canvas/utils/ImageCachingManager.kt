package co.nayan.canvas.utils

import android.content.Context
import android.graphics.Bitmap
import co.nayan.c3v2.core.models.AnnotationData
import co.nayan.c3v2.core.models.Record
import co.nayan.c3views.crop.Cropping
import co.nayan.c3views.utils.crop
import com.bumptech.glide.Glide
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageCachingManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val requestManager = Glide.with(context)

    fun cacheImages(records: List<Record>) {
        records.forEach {
            it.displayImage?.let { mediaUrl ->
                if (mediaUrl.isVideo().not()) {
                    Timber.d("Caching Image : ${it.displayImage}")
                    requestManager.load(it.displayImage).submit()
                }
            }
        }
    }

    /*fun cacheTemplateIcons(templates: List<Template>) {
        templates.forEach {
            it.templateIcon?.let { templateUrl ->
                if (templateUrl.isVideo().not()) {
                    Timber.d("Caching Image : ${it.templateIcon}")
                    requestManager.load(it.templateIcon).submit()
                }
            }
        }
    }*/
}

fun Bitmap.extractBitmap(crop: Cropping?): Bitmap? {
    if (crop == null) return null
    val annotationObject = crop.transformWrtBitmap(width.toFloat(), height.toFloat())
    val answer = annotationObject.annotationValue?.answer
    val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()
    return try {
        gson.fromJson<AnnotationData>(answer, object : TypeToken<AnnotationData>() {}.type)
            ?.let { data ->
                val points = data.points
                if (points.isNullOrEmpty()) null
                else {
                    Bitmap.createBitmap(
                        this,
                        points[0].first().toInt(),
                        points[0].last().toInt(),
                        (points[1].first() - points[0].first()).toInt(),
                        (points[1].last() - points[0].last()).toInt()
                    )
                }
            }
    } catch (e: Exception) {
        Firebase.crashlytics.recordException(e)
        null
    }
}