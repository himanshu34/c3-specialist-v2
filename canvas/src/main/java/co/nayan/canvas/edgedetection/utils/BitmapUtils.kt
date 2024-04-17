package co.nayan.canvas.edgedetection.utils

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import java.util.ArrayList

fun Bitmap.resizeToScreenContentSize(newWidth: Int, newHeight: Int): Bitmap? {
    val width = this.width
    val height = this.height
    val scaleWidth = newWidth.toFloat() / width
    val scaleHeight = newHeight.toFloat() / height
    // CREATE A MATRIX FOR THE MANIPULATION
    val matrix = Matrix()
    // RESIZE THE BIT MAP
    matrix.postScale(scaleWidth, scaleHeight)

    // "RECREATE" THE NEW BITMAP
    val resizedBitmap = Bitmap.createBitmap(this, 0, 0, width, height, matrix, false)
    this.recycle()
    return resizedBitmap
}

fun Bitmap.getPolygonDefaultPoints(): MutableList<PointF> {
    val points = mutableListOf<PointF>()
    points.add(PointF(this.width * 0.14f, this.height.toFloat() * 0.13f))
    points.add(PointF(this.width * 0.84f, this.height.toFloat() * 0.13f))
    points.add(PointF(this.width * 0.14f, this.height.toFloat() * 0.83f))
    points.add(PointF(this.width * 0.84f, this.height.toFloat() * 0.83f))
    return points
}