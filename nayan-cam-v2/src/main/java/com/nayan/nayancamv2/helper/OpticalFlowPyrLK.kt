package com.nayan.nayancamv2.helper

import android.content.Context
import android.util.Size
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import timber.log.Timber
import kotlin.math.abs

object OpticalFlowPyrLK {

    private const val TAG = "OpenCV"
    private var openCVDidLoad = false
    private const val allowedDisplacement: Int = 10

    fun initOpenCV(context: Context) {
        val mLoaderCallback: BaseLoaderCallback = object : BaseLoaderCallback(context) {
            override fun onManagerConnected(status: Int) {
                when (status) {
                    SUCCESS -> {
                        openCVDidLoad = true
                        Timber.tag(TAG).e("Successfully loaded")
                    }
                    else -> {
                        openCVDidLoad = false
                        Timber.tag(TAG).e("Loading failed")
                    }
                }
                super.onManagerConnected(status)
            }
        }

        try {
            if (!OpenCVLoader.initDebug()) {
                Timber.tag(TAG)
                    .e("Internal OpenCV library not found. Using OpenCV Manager for initialization")
                OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, context, mLoaderCallback)
            } else {
                Timber.tag(TAG).e("OpenCV library found inside package. Using it!")
                mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Firebase.crashlytics.recordException(e)
        }
    }

    //var points: Array<Point?> = arrayOfNulls(24)
    var points = ArrayList<Point?>()

    fun initPoints(previewSize: Size) {
        if (points.size == 0) {
            var pointX = 0
            var pointY = 0
            val stepX = (previewSize.width / 4)
            val stepY = (previewSize.height / 4)
            for (i in 0..2) {
                pointX += stepX
                for (j in 0..2) {
                    pointY += stepY
                    points.add(Point(pointX.toDouble(), pointY.toDouble()))
                }
                pointY = 0
            }
        }
    }

    fun sparseFlow(currentFrame: Mat, prevFrame: Mat): Boolean {
        if (openCVDidLoad.not() || points.isEmpty()) return true
        else {
            val status = MatOfByte()
            val err = MatOfFloat()

            val mPrevGray = Mat()
            val mCurrentGray = Mat()
            Imgproc.cvtColor(currentFrame, mCurrentGray, Imgproc.COLOR_RGB2GRAY)
            Imgproc.cvtColor(prevFrame, mPrevGray, Imgproc.COLOR_RGB2GRAY)

            var xAvg1 = 0.0
            var xAvg2 = 0.0
            var yAvg1 = 0.0
            var yAvg2 = 0.0

            val prevFeatures = MatOfPoint2f()
            val currentFeatures = MatOfPoint2f()
            prevFeatures.fromArray(*points.toTypedArray())
            currentFeatures.fromArray(*points.toTypedArray())

            Video.calcOpticalFlowPyrLK(
                mPrevGray,
                mCurrentGray,
                prevFeatures,
                currentFeatures,
                status,
                err
            )

            val prevList = prevFeatures.toList()
            val currentList = currentFeatures.toList()
            val listSize = prevList.size
            for (i in 0 until listSize) {
                if (prevList[i] != null) {
                    xAvg1 += prevList[i].x
                    yAvg1 += prevList[i].y
                }
                if (currentList[i] != null) {
                    xAvg2 += currentList[i].x
                    yAvg2 += currentList[i].y
                }
            }

            xAvg1 /= listSize
            xAvg2 /= listSize
            yAvg1 /= listSize
            yAvg2 /= listSize

            val pointX = abs(xAvg1 - xAvg2).toInt()
            val pointY = abs(yAvg1 - yAvg2).toInt()

            return (abs(pointX) > allowedDisplacement || abs(pointY) > allowedDisplacement)
        }
    }
}