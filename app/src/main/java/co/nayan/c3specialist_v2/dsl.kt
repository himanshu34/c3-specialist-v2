package co.nayan.c3specialist_v2

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.Location
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils
import android.widget.EditText
import android.widget.ImageView
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import co.nayan.c3specialist_v2.phoneverification.otp.SmsBroadcastReceiver
import co.nayan.c3specialist_v2.utils.FileDownloadWorker
import co.nayan.c3v2.core.showToast
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.extcam.common.ExtCamConnectionActivity
import com.nayan.nayancamv2.extcam.common.ExternalCameraProcessingService
import com.nayan.nayancamv2.util.Constants.SEGMENTS_SYNC_TAG
import com.nayan.nayancamv2.util.isServiceRunning
import com.nayan.nayancamv2.videouploder.worker.GraphHopperSyncWorker
import id.zelory.compressor.Compressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

fun Activity.launchDashCam() {
    showToast(getString(co.nayan.nayancamv2.R.string.please_wait_opening_hover_mode))
    val serviceIntent = Intent(
        this,
        ExternalCameraProcessingService::class.java
    )
    if (isServiceRunning<ExternalCameraProcessingService>()) stopService(serviceIntent)
    Intent(this, ExtCamConnectionActivity::class.java).apply {
        putExtra("coming_from", "hover")
        putExtra("selected", "dashcam")
        startActivity(this)
        finishAffinity()
    }
}

fun getDistanceInMeter(latLng: LatLng, latitude: String?, longitude: String?): Float {
    return try {
        val startPoint = Location("Location Start").apply {
            this.latitude = latLng.latitude
            this.longitude = latLng.longitude
        }

        val endPoint = Location("Location End").apply {
            this.latitude = latitude?.toDouble() ?: 0.0
            this.longitude = longitude?.toDouble() ?: 0.0
        }

        startPoint.distanceTo(endPoint)
    } catch (e: Exception) {
        Firebase.crashlytics.recordException(e)
        Timber.e(e)
        0f
    }
}

fun getDistanceInMeter(latLng: LatLng, latitude: Double, longitude: Double): Float {
    return try {
        val startPoint = Location("Location Start").apply {
            this.latitude = latLng.latitude
            this.longitude = latLng.longitude
        }

        val endPoint = Location("Location End").apply {
            this.latitude = latitude
            this.longitude = longitude
        }

        startPoint.distanceTo(endPoint)
    } catch (e: Exception) {
        Firebase.crashlytics.recordException(e)
        Timber.e(e)
        0f
    }
}

fun EditText.onTextChanged(listener: (String) -> Unit) {
    this.addTextChangedListener(object : TextWatcher {

        override fun afterTextChanged(s: Editable?) {
            listener(s.toString())
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    })
}

fun Context.getTrackColor(count: Int): Int {
    return when (count) {
        0 -> getColor(R.color.tracking_blue)
        1 -> getColor(R.color.tracking_blue)
        2 -> getColor(R.color.tracking_green)
        3 -> getColor(R.color.tracking_yellow)
        4 -> getColor(R.color.tracking_orange)
        5 -> getColor(R.color.tracking_red)
        else -> getColor(R.color.tracking_black)
    }
}

fun Context.startGraphHopperSyncingRequest(isUserLoggingOut: Boolean): UUID {
    // GraphHopper syncing configuration setup
    val mConstraints = Constraints.Builder().apply {
        setRequiredNetworkType(NetworkType.CONNECTED)
    }.build()

    val inputData = Data.Builder()
        .putBoolean("isUserLoggingOut", isUserLoggingOut)
        .build()

    val oneTimeWorkRequest = OneTimeWorkRequestBuilder<GraphHopperSyncWorker>()
        .also {
            it.setConstraints(mConstraints)
            it.setInputData(inputData)
            it.addTag(SEGMENTS_SYNC_TAG)
        }.build()

    WorkManager.getInstance(this).enqueue(oneTimeWorkRequest)
    return oneTimeWorkRequest.id
}

fun Context.startDownloadingInvoice(data: Data, tag: String): UUID {
    // Downloading Invoice Receipt
    val mConstraints = Constraints.Builder().apply {
        setRequiredNetworkType(NetworkType.CONNECTED)
    }.build()

    val downloadWorkManager = OneTimeWorkRequestBuilder<FileDownloadWorker>()
        .also {
            it.setConstraints(mConstraints)
            it.setInputData(data)
            it.addTag(tag)
        }.build()

    WorkManager.getInstance(this).enqueue(downloadWorkManager)
    return downloadWorkManager.id
}

fun Context.setExportedAttribute(status: Boolean) {
    // Set the exported attribute to false programmatically
    val pm = this.packageManager
    val componentName = ComponentName(this, SmsBroadcastReceiver::class.java)
    try {
        val receiverInfo = pm.getReceiverInfo(componentName, PackageManager.GET_META_DATA)
        receiverInfo.exported = status
        pm.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
    }
}

suspend fun Context.getMarkerBitmap(
    image: String,
    dimension: Int
): Bitmap? = withContext(Dispatchers.IO) {
    return@withContext try {
        Glide.with(this@getMarkerBitmap).asBitmap().load(image).circleCrop()
            .submit(dimension, dimension).get()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun Long.formatMilliseconds(): String {
    return String.format(
        "%02d:%02d",
        TimeUnit.MILLISECONDS.toMinutes(this),
        TimeUnit.MILLISECONDS.toSeconds(this) -
                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(this))
    )
}

fun Drawable.getBitmapFromDrawable(width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(
        width,
        height,
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    this.setBounds(0, 0, canvas.width, canvas.height)
    this.draw(canvas)
    return bitmap
}

fun ImageView.loadProfileImage(imageUrl: String, errorImage: Int) {
    val formattedUrl = imageUrl.replace("\u0026", "&")
    Glide.with(this.context).load(formattedUrl)
        .diskCacheStrategy(DiskCacheStrategy.NONE)
        .skipMemoryCache(true)
        .error(errorImage)
        .fitCenter()
        .into(this)
}

fun ImageView.loadImage(imageUri: Uri, errorImage: Int) {
    Glide.with(this.context)
        .load(imageUri)
        .diskCacheStrategy(DiskCacheStrategy.NONE)
        .skipMemoryCache(true)
        .apply(RequestOptions().fitCenter().centerCrop())
        .error(errorImage)
        .placeholder(errorImage)
        .into(this)
}

fun Activity.getFrontCameraSpecs(): String? {
    val manager = this.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val cameraIds = manager.cameraIdList
    var frontCameraId: String? = null

    for (cameraId in cameraIds) {
        val characteristics = manager.getCameraCharacteristics(cameraId)
        val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
        if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
            frontCameraId = cameraId
            break
        }
    }

    return frontCameraId
}

suspend fun Context.compressImage(imageUri: Uri): File? {
    return try {
        val file = this.getFileFromContentUri(imageUri)
        val compressedFile = Compressor.compress(this, file)
        if (compressedFile != null && compressedFile.exists()) compressedFile else file
    } catch (e: Exception) {
        e.printStackTrace()
        Firebase.crashlytics.recordException(e)
        null
    }
}

fun Context.getFileFromContentUri(contentUri: Uri): File {
    this.contentResolver.openInputStream(contentUri)?.use { stream ->
        val byteArray = ByteArray(stream.available())
        stream.read(byteArray)
        return byteArray.toFile(queryName(contentUri))
    }

    // Handle the case when the InputStream is null or an exception occurs
    throw IOException("Unable to open InputStream for the given content URI: $contentUri")
}

private fun ByteArray.toFile(fileName: String): File {
    val filePrefix = fileName.substringBeforeLast("-")
    val fileExtension = fileName.substringAfterLast(".")
    val file = File.createTempFile("$filePrefix-temp", ".$fileExtension")
    file.deleteOnExit()
    val outputStream = FileOutputStream(file)
    outputStream.write(this)
    return file
}

@SuppressLint("Recycle")
fun Context.queryName(uri: Uri): String {
    var name: String? = ""
    this.contentResolver.query(
        uri,
        null,
        null,
        null,
        null
    )?.use { cursor ->
        /*
             * Get the column indexes of the data in the Cursor,
             * move to the first row in the Cursor, get the data,
             * and display it.
             */
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        cursor.moveToFirst()
        val imageName = cursor.getString(nameIndex)
        name = imageName
        cursor.close()
    }

    return name ?: ""
}

fun Long.getTimeAgo(): String {
    return DateUtils.getRelativeTimeSpanString(
        this,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
}