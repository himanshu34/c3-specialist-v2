package co.nayan.c3specialist_v2.profile.utils

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.core.content.FileProvider
import co.nayan.c3specialist_v2.BuildConfig
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.*
import javax.inject.Inject

const val PICK_IMAGE_FROM_CAMERA = 0
const val PICK_IMAGE_DEVICE = 1

class ImagePickerManager(
    private val context: Context?,
    activityResultCaller: ActivityResultCaller?
) {

    private var imageUri: Uri? = null
    var imagePickerType: Int = PICK_IMAGE_FROM_CAMERA
    var imagePickerListener: ImagePickerListener? = null
    var imagePath: String? = null
    var isImageUpdated: Boolean = false

    private val requestPermission =
        activityResultCaller?.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions.entries.all { it.value }
            if (granted) imagePickerListener?.permissionsGranted()
            else imagePickerListener?.report("Permissions denied.")
        }

    fun requestPermissions() {
        requestPermission?.launch(
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            } else {
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_MEDIA_IMAGES
                )
            }
        )
    }

    fun pickImage() {
        isImageUpdated = false
        if (imagePickerType == PICK_IMAGE_FROM_CAMERA) captureImage()
        else galleryImage()
    }

    private val cameraContract =
        activityResultCaller?.registerForActivityResult(ActivityResultContracts.TakePicture()) {
            if (it) {
                isImageUpdated = true
                imagePickerListener?.setImage(imageUri)
            }
        }

    private fun captureImage() {
        context?.let { context ->
            val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val fileTemp = File.createTempFile("image", ".jpg", storageDir).also {
                imageUri = FileProvider.getUriForFile(
                    context,
                    "${BuildConfig.APPLICATION_ID}.provider",
                    it
                )
            }
            imagePath = fileTemp.absolutePath
            cameraContract?.launch(imageUri)
        }
    }

    private val galleryContract =
        activityResultCaller?.registerForActivityResult(PickVisualMedia()) { uri ->
            if (uri == null) return@registerForActivityResult
            isImageUpdated = true
            imageUri = uri
            setPathFromInputStreamUri(uri)
            imagePickerListener?.setImage(uri)
        }

    private fun galleryImage() {
        galleryContract?.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
    }

    private fun setPathFromInputStreamUri(uri: Uri) {
        imagePath = null
        uri.authority?.let {
            try {
                context?.contentResolver?.openInputStream(uri).use {
                    createTemporalFileFrom(it)?.apply { imagePath = path }
                }
            } catch (e: FileNotFoundException) {
                Firebase.crashlytics.recordException(e)
                e.printStackTrace()
            } catch (e: IOException) {
                Firebase.crashlytics.recordException(e)
                e.printStackTrace()
            }
        }
    }

    @Throws(IOException::class)
    private fun createTemporalFileFrom(inputStream: InputStream?): File? {
        var targetFile: File? = null
        return if (inputStream == null) targetFile
        else {
            var read: Int
            val buffer = ByteArray(8 * 1024)
            targetFile = File(
                context?.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "tempPicture.jpg"
            )
            FileOutputStream(targetFile).use { out ->
                while (inputStream.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                }
                out.flush()
            }
            targetFile
        }
    }
}

class ImagePickerProvider @Inject constructor(@ApplicationContext private val context: Context) {
    fun provide(
        activityResultCaller: ActivityResultCaller?
    ): ImagePickerManager {
        return ImagePickerManager(context, activityResultCaller)
    }
}

interface ImagePickerListener {
    fun report(message: String)
    fun setImage(uri: Uri?)
    fun permissionsGranted()
}