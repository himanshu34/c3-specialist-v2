package co.nayan.canvas.image_processing.utils

import android.graphics.Matrix
import android.graphics.RectF
import co.nayan.imageprocessing.classifiers.Recognition

/*fun List<Recognition>.rankObjects(shouldRank: Boolean): List<Recognition> {
    if (shouldRank.not()) return this
    val result = filter { r -> (r.location.height() / r.location.width()) > 0.8 }
        .filter { r -> r.confidence > 0.8 }
        .filter { r -> r.location.height() > 25 && r.location.width() > 25 }
    return result.ifEmpty { emptyList() }
}

fun List<Recognition>.rankLps(): List<Recognition> {
    val result = filter { r -> r.confidence > 0.8 }
    return result.ifEmpty { emptyList() }
}*/

fun List<Recognition>.transformedObjectsRect(cropToFrameTransform: Matrix?): List<RectF> {
    val listOfRect = mutableListOf<RectF>()
    forEach {
        val location = it.location
        cropToFrameTransform?.mapRect(location)
        listOfRect.add(location)
    }
    return listOfRect
}