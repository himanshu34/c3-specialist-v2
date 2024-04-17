package com.nayan.nayancamv2

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.media.MediaCodecInfo
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import co.nayan.c3v2.core.isKentCam
import co.nayan.c3v2.core.models.MapData
import co.nayan.c3v2.core.models.OrientationState
import co.nayan.c3v2.core.models.ProgressState
import co.nayan.c3v2.core.models.SurgeLocation
import co.nayan.c3v2.core.models.SurgeMeta
import co.nayan.c3v2.core.models.driver_module.AttendanceData
import co.nayan.c3v2.core.models.driver_module.LocationData
import co.nayan.c3v2.core.models.driver_module.Segment
import co.nayan.c3v2.core.models.driver_module.SegmentTrackData
import co.nayan.c3v2.core.models.driver_module.VideoUploaderData
import co.nayan.c3v2.core.postDelayed
import co.nayan.c3v2.core.showToast
import co.nayan.nayancamv2.R
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.google.maps.android.PolyUtil
import com.nayan.nayancamv2.env.CompareByDistance
import com.nayan.nayancamv2.helper.GlobalParams.currentSegment
import com.nayan.nayancamv2.helper.GlobalParams.videoUploadingStatus
import com.nayan.nayancamv2.hovermode.BackgroundCameraService
import com.nayan.nayancamv2.hovermode.HoverPermissionCallback
import com.nayan.nayancamv2.storage.StorageUtil
import com.nayan.nayancamv2.util.Constants.DATE_FORMAT
import com.nayan.nayancamv2.util.Constants.TIME_FORMAT
import com.nayan.nayancamv2.util.Constants.VIDEO_UPLOADER_TAG
import com.nayan.nayancamv2.util.DEVICE_PERFORMANCE.DELAYED_2_sec
import com.nayan.nayancamv2.util.isServiceRunning
import com.nayan.nayancamv2.videouploder.worker.VideoUploadWorker
import com.vividsolutions.jts.geom.Coordinate
import com.vividsolutions.jts.geom.Envelope
import com.vividsolutions.jts.geom.GeometryFactory
import com.vividsolutions.jts.geom.LineString
import com.vividsolutions.jts.index.SpatialIndex
import com.vividsolutions.jts.index.strtree.STRtree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

fun Activity.launchHoverService() {
    showToast(getString(R.string.please_wait_opening_hover_mode))
    Intent(this, BackgroundCameraService::class.java).apply {
        if (isServiceRunning<BackgroundCameraService>()) stopService(this)
        postDelayed(DELAYED_2_sec) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(this)
            else startService(this)
        }
        finishAffinity()
    }
}

@SuppressLint("QueryPermissionsNeeded")
fun tapToCall(mobileNumber: String?, context: Context) {
    try {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$mobileNumber")).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }

        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Firebase.crashlytics.recordException(e)
        e.printStackTrace()
    }
}

fun getDirection(angle: Double): String {
    var direction = ""

    if (angle >= 350 || angle <= 10)
        direction = "N"
    if (angle < 350 && angle > 280)
        direction = "NW"
    if (angle <= 280 && angle > 260)
        direction = "W"
    if (angle <= 260 && angle > 190)
        direction = "SW"
    if (angle <= 190 && angle > 170)
        direction = "S"
    if (angle <= 170 && angle > 100)
        direction = "SE"
    if (angle <= 100 && angle > 80)
        direction = "E"
    if (angle <= 80 && angle > 10)
        direction = "NE"

    return direction
}

fun Int.between(min: Int, max: Int): Boolean {
    return this in (min + 1) until max
}

/**
 * @return battery temperature divided by 10 to get value in Celsius
 */
fun Context.getBatteryTemperature(): Int {
    return try {
        val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = registerReceiver(null, iFilter)
        val temperature = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        temperature?.let { temperature / 10 } ?: run { 0 }
    } catch (e: Exception) {
        Timber.e(e)
        0
    }
}

fun Context.getBatteryLevel(): Float {
    val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    return batteryIntent?.let {
        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

        // Error checking that probably isn't needed but I added just in case.
        if (level == -1 || scale == -1) {
            50.0f
        } else level.toFloat() / scale.toFloat() * 100.0f
    } ?: run { 50.0f }
}

fun isYuvFormatSupported(deviceModel: String): Boolean {
    val listOfSupportedDevice = listOf(
        "vivo v2025",
        "vivo v2207",
        "vivo 1820",
        "vivo",
        "xiaomi redmi",
        "xiaomi mi",
        "xiaomi",
        "realme rmx3261",
        "samsung sm-a",
        "samsung sm-j",
        "tecno",
        "kent cameye carcam",
        "xiaomi 22120rn86i"
    )
    val listOfUnsupportedDevice = listOf("xiaomi 23076rn4bi")
    if (listOfUnsupportedDevice.any { deviceModel.contains(it) }) {
        return false
    }

    return listOfSupportedDevice.any { deviceModel.contains(it) }
}

fun getColorFormatSurfaceFromDeviceName(deviceModel: String): Int {
    return when (deviceModel) {
        "tecno kg5p", "xiaomi 22120rn86i" -> MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
        "samsung sm-g973f" -> MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV422Flexible
        "samsung sm-a207f" -> MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        else -> MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
    }
}

suspend fun checkForSurgeLocations(
    latLng: LatLng,
    surgeLocations: List<SurgeLocation>?
): String? = withContext(Dispatchers.IO) {
    return@withContext try {
        if (latLng.latitude == 0.0 || latLng.longitude == 0.0 || surgeLocations.isNullOrEmpty()) null
        else {
            val surgeCollections: MutableList<SurgeMeta> = mutableListOf()
            surgeLocations.map { coordinate ->
                if (coordinate.latitude.isNullOrEmpty()
                    || coordinate.longitude.isNullOrEmpty()
                    || coordinate.radius.isNullOrEmpty()
                ) return@map

                val distance = getDistanceInMeter(
                    latLng,
                    coordinate.latitude!!.toDouble(),
                    coordinate.longitude!!.toDouble()
                )

                if (distance <= coordinate.radius!!.toFloat()) {
                    Timber.e("######### Distance $distance ######### Radius ${coordinate.radius!!.toFloat()}")
                    surgeCollections.add(
                        SurgeMeta(
                            coordinate.id,
                            coordinate.description,
                            distance,
                            coordinate.radius!!.toFloat()
                        )
                    )
                }
            }

            if (surgeCollections.isEmpty()) null
            else Collections.min(surgeCollections, CompareByDistance())?.id?.toString()
        }
    } catch (e: Exception) {
        Firebase.crashlytics.recordException(e)
        null
    }
}

suspend fun checkForKmlBoundaries(
    latLng: LatLng,
    cityKmlBoundaries: List<MapData>?
): String? = withContext(Dispatchers.IO) {
    return@withContext try {
        if (latLng.latitude == 0.0 || latLng.longitude == 0.0 || cityKmlBoundaries.isNullOrEmpty())
            null
        else {
            var surgeId: String? = null
            for (wardData in cityKmlBoundaries) {
                val wardCoordinates = wardData.geometry?.coordinates
                val wardPoints = wardCoordinates?.map { LatLng(it.lat, it.lon) }
                if (PolyUtil.containsLocation(latLng, wardPoints, false)) {
                    surgeId = wardData.surgeLocationId.toString()
                    break
                }
            }

            surgeId
        }
    } catch (e: Exception) {
        Firebase.crashlytics.recordException(e)
        null
    }
}

fun getListOfVideoFiles(videosBatch: List<VideoUploaderData>): List<VideoUploaderData> {
    return videosBatch.filter {
        !it.videoName.startsWith(".")
                && !it.videoName.contains("lat-0.0")
                && !it.videoName.contains("lon-0.0")
    }
}

fun getActualVideoFile(storageUtil: StorageUtil, videoName: String): File? {
    val directory = storageUtil.getNayanVideoTempStorageDirectory()
    if (directory.exists() && directory.isDirectory) {
        val file = File(directory, videoName)
        if (file.exists()) return file
    }

    return null
}

fun isValidLatLng(lat: Double, lng: Double): Boolean {
    if (lat == 0.0) {
        return false
    } else if (lng == 0.0) {
        return false
    } else if (lat < -90 || lat > 90) {
        return false
    } else if (lng < -180 || lng > 180) {
        return false
    }
    return true
}

fun getCurrentDate(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDate.now())
    else {
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        df.format(Calendar.getInstance().time)
    }
}

fun getCurrentDayOfMonth(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        LocalDateTime.now().dayOfMonth
    } else {
        val calendar = Calendar.getInstance()
        calendar.time = Date()
        calendar.get(Calendar.DAY_OF_MONTH)
    }
}

fun getDayOfMonth(milliseconds: Long): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // Local Date Time
        Instant.ofEpochMilli(milliseconds)
            .atZone(ZoneId.systemDefault()).toLocalDateTime().dayOfMonth
    } else {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = milliseconds
        calendar.get(Calendar.DAY_OF_MONTH)
    }
}

fun getCurrentTime(milliseconds: Long): Pair<String, String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // Local Date Time
        val dateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yy HH-mm-ss")
        val localDateTime = Instant.ofEpochMilli(milliseconds)
            .atZone(ZoneId.systemDefault()).toLocalDateTime()
        // UTC time
        Pair(
            dateTimeFormatter.format(localDateTime),
            Instant.ofEpochMilli(milliseconds).toString()
        )
    } else {
        val dateFormat = SimpleDateFormat("dd-MM-yy HH-mm-ss", Locale.getDefault())
        val utcDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = Date(milliseconds)
        val strLocalDate = dateFormat.format(date)
        utcDateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val strUTCDate = utcDateFormat.format(date)
        Pair(strLocalDate, strUTCDate)
    }
}

fun getCurrentUTCTime(milliseconds: Long): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        Instant.ofEpochMilli(milliseconds).toString()
    else {
        val utcDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        utcDateFormat.timeZone = TimeZone.getTimeZone("UTC")
        utcDateFormat.format(Date(milliseconds))
    }
}

suspend fun getUTCTime(
    map: HashMap<String, String>, fileName: String
): String = withContext(Dispatchers.IO) {
    return@withContext try {
        map["recordedOnUTC"] ?: run {
            map["recordedOn"]?.let {
                val timeStamp = it.toLong()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    Instant.ofEpochMilli(timeStamp).toString()
                else {
                    val utcDateFormat =
                        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }
                    utcDateFormat.format(Date(timeStamp))
                }
            } ?: run { getUTCFromFileName(fileName) }
        }
    } catch (e: Exception) {
        Firebase.crashlytics.log(e.message.toString())
        getUTCFromFileName(fileName)
    }
}

fun getUTCFromFileName(fileName: String): String {
    val dateStr = fileName.substringAfter("dt-").substringBefore("ti")
    val timeStr = fileName.substringAfter("ti-").substringBefore(".mp4")
    // DATE_FORMAT = "MM-dd-yy" && TIME_FORMAT = "HH-mm-ss"
    val dateTimeString = "$dateStr $timeStr"
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val formatter = DateTimeFormatter.ofPattern("$DATE_FORMAT $TIME_FORMAT")
        val localDateTime = LocalDateTime.parse(dateTimeString, formatter)
        // local date time at your system's default time zone
        val systemZoneDateTime: ZonedDateTime =
            localDateTime.atZone(ZoneId.systemDefault())
        // timestamp of the original value represented in UTC
        systemZoneDateTime.toInstant().toString()
    } else {
        val dateFormat = SimpleDateFormat("$DATE_FORMAT $TIME_FORMAT", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        try {
            val date = dateFormat.parse(dateTimeString)
            date?.let { dateFormat.format(date) } ?: "NA"
        } catch (e: ParseException) {
            e.printStackTrace()
            "NA"
        }
    }
}

fun getLatitudeFromFileName(fileName: String): String {
    return fileName.substringAfter("lat-").substringBefore("lon")
}

fun getLongitudeFromFileName(fileName: String): String {
    return fileName.substringAfter("lon-").substringBefore("dt")
}

fun getVideoRecordedOnMillis(fileName: String): Long {
    val dateStr = fileName.substringAfter("dt-").substringBefore("ti")
    val timeStr = fileName.substringAfter("ti-").substringBefore(".mp4")
    // DATE_FORMAT = "MM-dd-yy" && TIME_FORMAT = "HH-mm-ss"
    val dateTimeString = "$dateStr $timeStr"
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val formatter = DateTimeFormatter.ofPattern("$DATE_FORMAT $TIME_FORMAT")
        val localDateTime = LocalDateTime.parse(dateTimeString, formatter)
        // Local date time at your system's default time zone
        val systemZoneDateTime: ZonedDateTime = localDateTime.atZone(ZoneId.systemDefault())
        // Timestamp of the original value represented in UTC
        systemZoneDateTime.toInstant().toEpochMilli()
    } else {
        val dateFormat = SimpleDateFormat("$DATE_FORMAT $TIME_FORMAT", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        try {
            val date = dateFormat.parse(dateTimeString)
            date?.time ?: 0L
        } catch (e: ParseException) {
            e.printStackTrace()
            0L
        }
    }
}

suspend fun funIfUserLocationFallsWithInSurge(
    point: LatLng,
    surgeLocations: List<SurgeLocation>?
): Boolean = withContext(Dispatchers.IO) {
    return@withContext try {
        if (surgeLocations.isNullOrEmpty()) false
        else {
            var status = false
            val distance = FloatArray(2)
            for (surgeLocation in surgeLocations) {
                if (surgeLocation.radius.isNullOrEmpty() ||
                    surgeLocation.latitude.isNullOrEmpty() ||
                    surgeLocation.longitude.isNullOrEmpty()
                ) continue

                val radiusInMeters = surgeLocation.radius!!.toFloat()
                Location.distanceBetween(
                    point.latitude,
                    point.longitude,
                    surgeLocation.latitude!!.toDouble(),
                    surgeLocation.longitude!!.toDouble(),
                    distance
                )

                if (distance[0] <= radiusInMeters) {
                    status = true
                    break
                }
            }

            status
        }
    } catch (e: Exception) {
        Firebase.crashlytics.recordException(e)
        false
    }
}


suspend fun getAttendanceTimeForCityKmlBoundaries(
    allLocationHistory: List<LocationData>,
    cityKmlBoundaries: List<MapData>,
    surgeLocations: List<SurgeLocation>?,
    currentRole: String
): AttendanceData {
    // Start calculating attendance report
    var attendanceInTime = 0L
    var attendanceOutTime = 0L
    var lastMatchedTime = 0L
    var firstInPremisesTime = 0L
    var lastInPremisesTime = 0L

    return withContext(Dispatchers.IO) {
        allLocationHistory.forEachIndexed { index, it ->
            val point = LatLng(it.latitude, it.longitude)
            val contains = funIfPointFallsInKmlBoundaries(point, cityKmlBoundaries)
                    || funIfUserLocationFallsWithInSurge(point, surgeLocations)
            val timeDifference = if (index == 0) {
                lastMatchedTime = it.timeStamp
                0L // No previous timestamp
            } else it.timeStamp - lastMatchedTime

            if (timeDifference > TimeUnit.MINUTES.toMillis(1)) {
                lastMatchedTime = it.timeStamp
                return@forEachIndexed
            }

            if (contains) {
                if (firstInPremisesTime == 0L) firstInPremisesTime = it.timeStamp
                lastInPremisesTime = it.timeStamp
                attendanceInTime += (it.timeStamp - lastMatchedTime)
            } else attendanceOutTime += (it.timeStamp - lastMatchedTime)
            lastMatchedTime = it.timeStamp
        }

        AttendanceData(
            allLocationHistory.filter { it.timeStamp <= lastMatchedTime },
            attendanceInTime,
            attendanceOutTime,
            firstInPremisesTime,
            lastInPremisesTime,
            currentRole
        )
    }
}

suspend fun funIfPointFallsInKmlBoundaries(
    point: LatLng,
    cityKmlBoundaries: List<MapData>
): Boolean {
    return withContext(Dispatchers.IO) {
        if (cityKmlBoundaries.isEmpty()) false
        else {
            var status = false
            run breaking@{
                cityKmlBoundaries.forEach { wardData ->
                    val wardCoordinates = wardData.geometry?.coordinates
                    val wardPoints = wardCoordinates?.map { LatLng(it.lat, it.lon) }
                    status = PolyUtil.containsLocation(point, wardPoints, false)
                    if (status) return@breaking
                }
            }

            status
        }
    }
}

fun prepareSpatialTreeForAllSegments(
    allSegments: List<SegmentTrackData>
): SpatialIndex {
    // Initialize the spatial indexing data structure
    val index: SpatialIndex = STRtree()
    // Add segments to the index
    if (allSegments.isNotEmpty())
        allSegments.forEach { s ->
            val segmentKey = s.coordinates?.split(",")
            if (segmentKey != null) {
                val start = if (segmentKey.size > 1)
                    Coordinate(segmentKey[0].toDouble(), segmentKey[1].toDouble())
                else return@forEach
                val end = if (segmentKey.size > 3)
                    Coordinate(segmentKey[2].toDouble(), segmentKey[3].toDouble())
                else return@forEach

                val line: LineString = GeometryFactory().createLineString(arrayOf(start, end))
                val envelope: Envelope = line.envelopeInternal
                val segment = Segment(start, end, s.count)
                index.insert(envelope, segment)
            }
        }

    return index
}

suspend fun funIfUserRecordingOnBlackLines(
    location: Location,
    index: SpatialIndex,
    spatialThreshold: Double,
    spatialStickiness: Double,
    mAllowedSurveys: Int
): Boolean = withContext(Dispatchers.IO) {
    // Find the nearest segment to a given point
    val queryPoint = Coordinate(location.latitude, location.longitude) // user location
    val point = GeometryFactory().createPoint(queryPoint)
    val queryEnvelope = point.buffer(spatialThreshold).envelopeInternal // rectangle with 20m buffer
    // Perform the spatial query
    val result: MutableList<Segment> = ArrayList()
    // min distance from current segment's nodes (stored in memory)
    val currentSegmentDistance = currentSegment?.getDistance(queryPoint)
    index.query(queryEnvelope) { item -> result.add(item as Segment) } // All segments intersecting the 20m rectangle

    // Calculate distances and find the nearest segment
    var nearestSegment: Segment? = null
    var nearestDistance = Double.POSITIVE_INFINITY
    result.filter { it.count > 0 }.forEach { segment ->
        // Min distance from current iteration segments' nodes (stored in memory)
        val distance = segment.getDistance(queryPoint)
        Timber.e("Nearest Distance --> $distance, count --> ${nearestSegment?.count}")
        if (distance < nearestDistance) {
            nearestSegment = segment
            nearestDistance = distance
        }
    }
    currentSegment?.let {
        when {
            // Update current segment (new segment of interest found)
            (nearestDistance + spatialStickiness <= (currentSegmentDistance ?: 0.0)) -> {
                currentSegment = nearestSegment
            }

            // no new seg of interest found and also far away from last segment
            ((currentSegmentDistance ?: 0.0) > spatialThreshold) -> {
                currentSegment = null
            }

            // no new seg of interest found
            else -> {
                nearestSegment = currentSegment
            }
        }
    } ?: run { currentSegment = nearestSegment }
    nearestSegment?.let { it.count > mAllowedSurveys - 1 } ?: false
}

private fun Segment.getDistance(queryPoint: Coordinate): Double {
    return minOf(this.start.distance(queryPoint), this.end.distance(queryPoint))
}

fun Context.startVideoUploadRequest(
    isSurveyor: Boolean = false,
    forceUpload: Boolean = false
): UUID? {
    // Video uploader configuration setup
    return if (videoUploadingStatus.value != ProgressState) {
        val mConstraints = Constraints.Builder().apply {
            setRequiredNetworkType(NetworkType.CONNECTED)
        }.build()

        val oneTimeWorkRequest = OneTimeWorkRequestBuilder<VideoUploadWorker>()
            .also {
                if (forceUpload.not()) {
                    if (isSurveyor) it.setInitialDelay(30, TimeUnit.SECONDS)
                    else it.setInitialDelay(60, TimeUnit.SECONDS)
                }
                it.setConstraints(mConstraints)
                it.addTag(VIDEO_UPLOADER_TAG)
            }.build()

        WorkManager.getInstance(this).enqueue(oneTimeWorkRequest)
        oneTimeWorkRequest.id
    } else null
}

fun View.levitate(yDeviation: Float, animated: Boolean) {
    if (animated) {
        val yourDuration: Long = 200
        val interpolator = DecelerateInterpolator()

        animate()
            .translationYBy(yDeviation)
            .setDuration(yourDuration)
            .setInterpolator(interpolator)
            .withEndAction { levitate(-yDeviation, true) }
    }
}

fun Context.createRotateAnimation(): Animation {
    return AnimationUtils.loadAnimation(this, R.anim.rotate).apply {
        repeatCount = Animation.INFINITE
        repeatMode = Animation.RESTART / Animation.REVERSE
    }
}

fun Context.createFadeInOutAnimation(): Animation {
    return AnimationUtils.loadAnimation(this, R.anim.fade_in_out_blink_infinite).apply {
        duration = 1000 // 1 second duration for each animation cycle
        interpolator = LinearInterpolator()
        repeatCount = Animation.INFINITE
    }
}

fun createBlinkAnimation(): Animation {
    return AlphaAnimation(1F, 0F).apply { //to change visibility from visible to invisible
        duration = 1000 // 1 second duration for each animation cycle
        interpolator = LinearInterpolator()
        repeatCount = Animation.INFINITE // repeating indefinitely
        repeatMode = Animation.REVERSE // animation will start from the end point once ended.
    }
}

fun Context.getCurrentScreenOrientation(): OrientationState {
    val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val orientation = windowManager.defaultDisplay.rotation
    val rotation = when {
        orientation <= 45 -> Surface.ROTATION_0
        orientation <= 135 -> Surface.ROTATION_90
        orientation <= 225 -> Surface.ROTATION_180
        orientation <= 315 -> Surface.ROTATION_270
        else -> Surface.ROTATION_0
    }

    return OrientationState(rotation, orientation)
}

fun handleOrientation(
    state: OrientationState,
    deviceModel: String,
    callback: ((Boolean, Boolean) -> Unit)? = null,
    isDefault: Boolean = false
) {
    when (state.orientation) {
        // Condition placed for kent dash-cam
        in -1..1 -> {
            if (deviceModel.isKentCam() && state.orientationState == Surface.ROTATION_0)
                callback?.invoke(true, isDefault)
            else callback?.invoke(false, isDefault)
        }

        in 251..289 -> callback?.invoke(true, isDefault)
        else -> callback?.invoke(false, isDefault)
    }
}

fun Context.isHoverPermissionGranted(): Boolean = Settings.canDrawOverlays(this)

fun Context.requestHoverPermission(deviceModel: String, callback: HoverPermissionCallback) {
    if (!isHoverPermissionGranted()) {
        val currentModel = deviceModel.lowercase(Locale.getDefault())
        if (currentModel.contains("xiaomi")) {
            val intent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
                putExtra("extra_pkgname", this@requestHoverPermission.packageName)
            }
            callback.onPermissionDeniedAdditional(intent)
        } else {
            callback.onPermissionDenied(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${this.packageName}")
                )
            )
        }
    } else callback.onPermissionGranted()
}