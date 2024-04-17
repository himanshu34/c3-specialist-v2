package co.nayan.canvas.edgedetection.utils

import android.graphics.Bitmap
import android.graphics.PointF
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat

class ImageScanner(private val imageScannerListener: ImageScannerListener) {

    private suspend fun processBitmap(screenSizeBitmap: Bitmap) = withContext(Dispatchers.IO) {
        val points = mutableListOf<PointF>()
        try {
            val originalMat = Mat(screenSizeBitmap.height, screenSizeBitmap.width, CvType.CV_8UC1)
            Utils.bitmapToMat(screenSizeBitmap, originalMat)
            ScanUtils.detectLargestQuadrilateral(originalMat).forEach { quad ->
                points.add(PointF(quad.points[0].x.toFloat(), quad.points[0].y.toFloat()))
                points.add(PointF(quad.points[1].x.toFloat(), quad.points[1].y.toFloat()))
                points.add(PointF(quad.points[3].x.toFloat(), quad.points[3].y.toFloat()))
                points.add(PointF(quad.points[2].x.toFloat(), quad.points[2].y.toFloat()))
            }
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
            e.printStackTrace()
        } catch (e: UnsatisfiedLinkError) {
            Firebase.crashlytics.recordException(e)
            e.printStackTrace()
        }
        return@withContext points
    }

    suspend fun initDetection(bitmap: Bitmap, size: Pair<Int, Int>) {
        val height = size.second
        val width = size.first
        val screenSizeBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            ?.resizeToScreenContentSize(width, height)

        if (screenSizeBitmap == null) {
            runOnMainThread {
                imageScannerListener.onScannedCompleted()
            }
        } else {
            val points = processBitmap(screenSizeBitmap)
            runOnMainThread {
                imageScannerListener.onPointsDetected(points, screenSizeBitmap)
            }
        }
    }

    private suspend fun runOnMainThread(action: () -> Unit) {
        withContext(Dispatchers.Main) {
            action()
        }
    }

    interface ImageScannerListener {
        fun onPointsDetected(points: List<PointF>, screenSizeBitmap: Bitmap)
        fun onScannedCompleted()
    }
}
