package co.nayan.canvas.utils

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class FileManager @Inject constructor(@ApplicationContext private val context: Context) {

    fun getFile(recordId: Int): File {
        val dir = File(context.filesDir, "NayanVideoMode")
        return File(dir, "$recordId.mp4")
    }

    fun getDir(recordId: Int): File {
        val dir = File(context.filesDir, "NayanVideoMode/$recordId")
        if (dir.exists().not()) {
            dir.mkdir()
        }
        return dir
    }
}