package com.nayan.nayancamv2.helper

import android.media.Image
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Saves a JPEG [Image] into the specified [File].
 */
internal class ImageSaver(
    private val image: Image,
    private val file: File
) : Runnable {

    private val tagName = ImageSaver::class.java.simpleName
    private val imageSaveJob = SupervisorJob()
    private val imageSaveScope = CoroutineScope(Dispatchers.IO + imageSaveJob)

    override fun run() {
        try {
            imageSaveScope.launch(Dispatchers.IO) {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                var output: FileOutputStream? = null
                try {
                    output = FileOutputStream(file).apply { write(bytes) }
                } catch (e: IOException) {
                    e.printStackTrace()
                    Timber.tag(tagName).e(e.toString())
                } finally {
                    output?.close()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Timber.tag(tagName).e(e.toString())
        } finally {
            image.close()
        }
    }
}