package co.nayan.canvas.edgedetection.utils

import co.nayan.canvas.edgedetection.config.ScanConstants
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import timber.log.Timber
import java.util.*
import kotlin.math.min

object ScanUtils {

    private fun sortPoints(src: Array<Point>): Array<Point> {
        val srcPoints = listOf(*src)
        val result = arrayOf<Point?>(null, null, null, null)
        val sumComparator =
            Comparator { lhs: Point, rhs: Point -> (lhs.y + lhs.x).compareTo(rhs.y + rhs.x) }
        val diffComparator =
            Comparator { lhs: Point, rhs: Point -> (lhs.y - lhs.x).compareTo(rhs.y - rhs.x) }

        // top-left corner = minimal sum
        result[0] = Collections.min(srcPoints, sumComparator)
        // bottom-right corner = maximal sum
        result[2] = Collections.max(srcPoints, sumComparator)
        // top-right corner = minimal difference
        result[1] = Collections.min(srcPoints, diffComparator)
        // bottom-left corner = maximal difference
        result[3] = Collections.max(srcPoints, diffComparator)
        return result.requireNoNulls()
    }

    private val morph_kernel = Mat(
        Size(ScanConstants.KSIZE_CLOSE.toDouble(), ScanConstants.KSIZE_CLOSE.toDouble()),
        CvType.CV_8UC1,
        Scalar(255.0)
    )

    fun detectLargestQuadrilateral(originalMat: Mat): List<ScannedQuadrilateral> {
        Imgproc.cvtColor(originalMat, originalMat, Imgproc.COLOR_BGR2GRAY, 4)

        // Just OTSU/Binary thresholding is not enough.
        //Imgproc.threshold(mGrayMat, mGrayMat, 150, 255, THRESH_BINARY + THRESH_OTSU);

        /*
         *  1. We shall first blur and normalize the image for uniformity,
         *  2. Truncate light-gray to white and normalize,
         *  3. Apply canny edge detection,
         *  4. Cutoff weak edges,
         *  5. Apply closing(morphology), then proceed to finding contours.
         */

        // step 1.
        Imgproc.blur(
            originalMat,
            originalMat,
            Size(ScanConstants.KSIZE_BLUR.toDouble(), ScanConstants.KSIZE_BLUR.toDouble())
        )
        Core.normalize(originalMat, originalMat, 0.0, 255.0, Core.NORM_MINMAX)
        // step 2.
        // As most papers are bright in color, we can use truncation to make it uniformly bright.
        Imgproc.threshold(
            originalMat,
            originalMat,
            ScanConstants.TRUNC_THRESH.toDouble(),
            255.0,
            Imgproc.THRESH_TRUNC
        )
        Core.normalize(originalMat, originalMat, 0.0, 255.0, Core.NORM_MINMAX)
        // step 3.
        // After above preprocessing, canny edge detection can now work much better.
        Imgproc.Canny(
            originalMat,
            originalMat,
            ScanConstants.CANNY_THRESH_U.toDouble(),
            ScanConstants.CANNY_THRESH_L.toDouble()
        )
        // step 4.
        // Cutoff the remaining weak edges
        Imgproc.threshold(
            originalMat,
            originalMat,
            ScanConstants.CUTOFF_THRESH.toDouble(),
            255.0,
            Imgproc.THRESH_TOZERO
        )
        // step 5.
        // Closing - closes small gaps. Completes the edges on canny image; AND also reduces stringy lines near edge of paper.
        Imgproc.morphologyEx(
            originalMat,
            originalMat,
            Imgproc.MORPH_CLOSE,
            morph_kernel,
            Point(-1.0, -1.0),
            1
        )

        // Get only the 10 largest contours (each approximated to their convex hulls)
        val largestContour = findLargestContours(originalMat, 10000)
        return if (null != largestContour) {
            findQuadrilateral(largestContour)
        } else {
            emptyList()
        }
    }

    private fun hull2Points(hull: MatOfInt, contour: MatOfPoint): MatOfPoint {
        val indexes = hull.toList()
        val points: MutableList<Point> = ArrayList()
        val ctrList = contour.toList()
        for (index in indexes) {
            points.add(ctrList[index])
        }
        val point = MatOfPoint()
        point.fromList(points)
        return point
    }

    private fun findLargestContours(inputMat: Mat, numTopContours: Int): List<MatOfPoint>? {
        val mHierarchy = Mat()
        val mContourList: List<MatOfPoint> = ArrayList()
        //finding contours - as we are sorting by area anyway, we can use RETR_LIST - faster than RETR_EXTERNAL.
        Imgproc.findContours(
            inputMat,
            mContourList,
            mHierarchy,
            Imgproc.RETR_LIST,
            Imgproc.CHAIN_APPROX_SIMPLE
        )

        // Convert the contours to their Convex Hulls i.e. removes minor nuances in the contour
        val mHullList: MutableList<MatOfPoint> = ArrayList()
        val tempHullIndices = MatOfInt()
        for (i in mContourList.indices) {
            Imgproc.convexHull(mContourList[i], tempHullIndices)
            mHullList.add(hull2Points(tempHullIndices, mContourList[i]))
        }
        // Release mContourList as its job is done
        for (c in mContourList) c.release()
        tempHullIndices.release()
        mHierarchy.release()
        if (mHullList.size != 0) {
            mHullList.sortWith { lhs: MatOfPoint?, rhs: MatOfPoint? ->
                Imgproc.contourArea(rhs).compareTo(Imgproc.contourArea(lhs))
            }
            return mHullList.subList(0, min(mHullList.size, numTopContours))
        }
        return null
    }

    private fun findQuadrilateral(mContourList: List<MatOfPoint>): MutableList<ScannedQuadrilateral> {
        val scannedQuadrilaterals = mutableListOf<ScannedQuadrilateral>()
        for (c in mContourList) {
            val c2f = MatOfPoint2f(*c.toArray())
            val peri = Imgproc.arcLength(c2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)
            val points = approx.toArray()
            // select biggest 4 angles polygon
            if (approx.rows() == 4) {
                val foundPoints = sortPoints(points)
                scannedQuadrilaterals.add(ScannedQuadrilateral(approx, foundPoints))
            }
        }
        return scannedQuadrilaterals
    }
}

data class ScannedQuadrilateral(val contour: MatOfPoint2f, val points: Array<Point>)
