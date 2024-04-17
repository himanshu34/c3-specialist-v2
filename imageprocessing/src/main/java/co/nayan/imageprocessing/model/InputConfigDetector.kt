package co.nayan.imageprocessing.model

import android.graphics.Bitmap
import java.io.File
import java.nio.MappedByteBuffer

data class InputConfigDetector(
    val bitmap: Bitmap,
    val inputWidth: Int,
    val inputHeight: Int,
    val modelFile: File,
    val labelArray: List<String>,
    val isQuantised: Boolean,
    val numberOfDetection: Int,
    val imageMean: Float?,
    val imageStd: Float?,
    val score: Float,
    val modelMappedByteBuffer: MappedByteBuffer?
)