package com.nayan.nayancamv2.storage

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import co.nayan.c3v2.core.interactors.NayanCamModuleInteractor
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nayan.nayancamv2.model.CurrentDataUsage
import com.nayan.nayancamv2.model.UserLocation
import com.nayan.nayancamv2.util.Constants.DATE_FORMAT
import com.nayan.nayancamv2.util.Constants.DIRECTORY_NAME
import com.nayan.nayancamv2.util.Constants.TIME_FORMAT
import com.nayan.nayancamv2.util.PHONE_EXTERNAL_STORAGE
import com.nayan.nayancamv2.util.SD_CARD_STORAGE
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

class StorageUtil @Inject constructor(
    private val appContext: Context,
    val sharedPrefManager: SharedPrefManager,
    val nayanCamModuleInteractor: NayanCamModuleInteractor
) {
    private val internalStorageNayanRoot: File =
        appContext.getDir(DIRECTORY_NAME, Context.MODE_PRIVATE)
    private val storageOptions: MutableList<RootStorageOptions> = ArrayList()
    private lateinit var externalNonRemovableStorageOption: RootStorageOptions
    private lateinit var externalRemovableStorageOption: RootStorageOptions

    private val nayanPrimaryStorage = PHONE_EXTERNAL_STORAGE

    // Checks if a volume containing external storage is available
    // for read and write.
    fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    // Checks if a volume containing external storage is available to at least read.
    fun isExternalStorageReadable(): Boolean {
        return Environment.getExternalStorageState() in
                setOf(Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY)
    }

    init {
        initStorageOption()
    }

    private fun getNayanRoot(): File = internalStorageNayanRoot

    private fun initStorageOption() {
        val externalStorageVolumes = ContextCompat.getExternalFilesDirs(appContext, null)
        externalStorageVolumes.forEach { rootDir ->
            if (rootDir != null && rootDir.isDirectory) {
                val rootFile = File(rootDir.path.split("/Android".toRegex()).toTypedArray()[0])
                Timber.d("Root Directory : ${rootDir.absolutePath}")
                Timber.d("Root File : ${rootFile.absolutePath}")
                val storageInfo = getStorageCapacity(rootFile.absolutePath)
                val isRemovable = try {
                    if (rootFile.isDirectory) Environment.isExternalStorageRemovable(rootFile) else false
                } catch (e: IllegalArgumentException) {
                    e.printStackTrace()
                    false
                }
                val tempDir: File = if (isRemovable) File("$rootDir/temp")
                else File(getNayanRoot(), "temp")
                if (!tempDir.exists()) tempDir.mkdir()

                val storageOption = RootStorageOptions(
                    rootFile, rootFile.absolutePath,
                    if (isRemovable) rootDir else File(getNayanRoot().absolutePath),
                    if (isRemovable) rootDir.absolutePath else getNayanRoot().absolutePath,
                    tempDir, tempDir.absolutePath, isRemovable,
                    storageInfo.first, storageInfo.second
                )

                if (isRemovable) externalRemovableStorageOption = storageOption
                else externalNonRemovableStorageOption = storageOption

                storageOptions.add(storageOption)
            }
        }

        if (externalStorageVolumes.isEmpty())
            Firebase.crashlytics.log("No storage available")
    }

    fun getNayanVideoTempStorageDirectory(): File {
        return if (nayanPrimaryStorage == SD_CARD_STORAGE)
            getSDCardStorage().tempDir
        else getPhoneStorage().tempDir
    }

    fun getPhoneStorage() = externalNonRemovableStorageOption
    private fun getSDCardStorage() = externalRemovableStorageOption

    private fun getStorageCapacity(rootPath: String): Pair<StorageUnit, StorageUnit> {
        val stat = StatFs(rootPath)
        val totalBytes: Long = stat.blockSizeLong * stat.blockCountLong
        val bytesAvailable: Long = stat.blockSizeLong * stat.availableBlocksLong
        val totalStorage = totalBytes / (1024 * 1024)
        val availableStorage = bytesAvailable / (1024 * 1024)
        return Pair(
            StorageUnit(availableStorage, "MB"),
            StorageUnit(totalStorage, "MB")
        )
    }

    fun getAllocatedPhoneStorage(deviceModel: String) =
        sharedPrefManager.getAllocatedPhoneStorage(deviceModel)

    fun setAllocatedPhoneStorage(percent: Float) =
        sharedPrefManager.setAllocatedPhoneStorage(percent)

    fun isMemoryAvailableForRecording(deviceModel: String) = checkStorageCapacity(deviceModel)

    fun setUploadNetworkType(type: Int) = sharedPrefManager.setUploadNetworkType(type)

    fun setCurrentDataUsage(data: Double) = sharedPrefManager.setCurrentDataUsage(data)

    fun getDataLimitForTheDay(): Float = sharedPrefManager.getDataLimitForTheDay()

    fun getCurrentDataUsage(): CurrentDataUsage = sharedPrefManager.getCurrentDataUsage()

    fun getUploadNetworkType(): Int = sharedPrefManager.getUploadNetworkType()

    fun setDefaultHoverMode(type: Boolean) = sharedPrefManager.setDefaultHoverMode(type)

    fun isDefaultHoverMode(): Boolean = sharedPrefManager.isDefaultHoverMode()

    fun setVolumeLevel(vol: Int) = sharedPrefManager.setVolumeLevel(vol)

    fun getVolumeLevel(): Int = sharedPrefManager.getVolumeLevel()

    private fun updateAvailableStorage(storageOptions: RootStorageOptions) {
        val storage = getStorageCapacity(storageOptions.rootDirPath)
        storageOptions.availableStorage = storage.first
        storageOptions.totalStorage = storage.second
    }

    private fun checkStorageCapacity(deviceModel: String): Boolean {
        when (nayanPrimaryStorage) {
            PHONE_EXTERNAL_STORAGE -> {
                updateAvailableStorage(getPhoneStorage())
                val memoryAvailableForRecordingInMB =
                    ((getAllocatedPhoneStorage(deviceModel) / 100F) * getPhoneStorage().availableStorage.value)

                var localVideoSize: Long = 0
                getNayanVideoTempStorageDirectory().listFiles()?.filter {
                    it.name.startsWith(".").not() &&
                            it.name.contains("lat-0.0").not() &&
                            it.name.contains("lon-0.0").not()
                }?.forEachIndexed { _, file ->
                    val fileSizeInBytes = file.length()
                    val fileSizeInKB = fileSizeInBytes / 1024
                    val fileSizeInMB = fileSizeInKB / 1024

                    localVideoSize += fileSizeInMB
                }

                return memoryAvailableForRecordingInMB >= localVideoSize
            }
        }
        return false
    }

    fun createNewVideoFile(
        prefix: String = "pol",
        location: UserLocation,
        isManual: Boolean = false,
        isManualTap: Boolean = false
    ): File? {
        val date = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            DateTimeFormatter.ofPattern(DATE_FORMAT).format(LocalDate.now())
        else {
            val df = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
            df.format(Calendar.getInstance().time)
        }
        val time = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            DateTimeFormatter.ofPattern(TIME_FORMAT).format(LocalTime.now())
        else {
            val df = SimpleDateFormat(TIME_FORMAT, Locale.getDefault())
            df.format(Calendar.getInstance().time)
        }
        val finalPrefix = getPrefix(prefix, isManual, isManualTap)
        val userId = nayanCamModuleInteractor.getId()
        return if (userId == 0) null
        else {
            val videoFileName =
                "$finalPrefix-$userId-lat-${location.latitude}lon-${location.longitude}dt-${date}ti-${time}.mp4"
            val video = File(getNayanVideoTempStorageDirectory(), videoFileName)
            Timber.d("🦀 createNewVideoFile(): ${video.absolutePath}")
            video
        }
    }

    private fun getPrefix(prefix: String, isManual: Boolean, isManualTap: Boolean): String {
        return if (isManual && isManualTap) "man_tap"
        else if (isManual) "man_vol"
        else prefix
    }

    private fun getRootFolder(context: Context, folderName: String): File {
        val internal = context.filesDir
        val imagePath = File(internal, folderName)
        if (!imagePath.exists()) imagePath.mkdirs()
        return imagePath
    }

    fun saveUserImage(context: Context): File {
        val date = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            DateTimeFormatter.ofPattern(DATE_FORMAT).format(LocalDate.now())
        else {
            val df = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
            df.format(Calendar.getInstance().time)
        }
        val time = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            DateTimeFormatter.ofPattern(TIME_FORMAT).format(LocalTime.now())
        else {
            val df = SimpleDateFormat(TIME_FORMAT, Locale.getDefault())
            df.format(Calendar.getInstance().time)
        }
        val userId = nayanCamModuleInteractor.getId()
        val imageFileName = "img-$userId-dt-${date}ti-${time}.jpeg"
        val profileDir = getRootFolder(context, "user")
        val profileImage = File(profileDir, imageFileName)
        Timber.d("🦀 createNewProfileFile(): ${profileImage.absolutePath}")
        return profileImage
    }

    fun createNewScoutModeFile(context: Context, location: UserLocation): File? {
        val date = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            DateTimeFormatter.ofPattern(DATE_FORMAT).format(LocalDate.now())
        else {
            val df = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
            df.format(Calendar.getInstance().time)
        }
        val time = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            DateTimeFormatter.ofPattern(TIME_FORMAT).format(LocalTime.now())
        else {
            val df = SimpleDateFormat(TIME_FORMAT, Locale.getDefault())
            df.format(Calendar.getInstance().time)
        }
        val userId = nayanCamModuleInteractor.getId()
        return if (userId == 0) null
        else {
            val imageFileName =
                "img-$userId-lat-${location.latitude}lon-${location.longitude}dt-${date}ti-${time}.jpeg"
            val scoutDir = getRootFolder(context, "scout")
            val scoutImage = File(scoutDir, imageFileName)
            Timber.d("🦀 createNewScoutModeFile(): ${scoutImage.absolutePath}")
            scoutImage
        }
    }

    fun saveInDownloads(appContext: Context, fromFile: File) {
        val dst = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            saveInDownloadsForQ(appContext, fromFile.name)
        else saveInDownloadsBelowQ(fromFile.name)
        dst?.let {
            try {
                val src = FileInputStream(fromFile)
                dst.channel.transferFrom(src.channel, 0, src.channel.size())
                src.close()
                dst.close()
            } catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
                e.printStackTrace()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveInDownloadsForQ(appContext: Context, fileName: String): FileOutputStream? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
            put(MediaStore.Downloads.DATE_ADDED, (System.currentTimeMillis() / 1000).toInt())
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + File.separator + "NayanOfflineVideos"
            )
        }

        val file =
            File(Environment.DIRECTORY_DOWNLOADS + File.separator + "NayanOfflineVideos" + File.separator + fileName)
        if (file.exists()) file.delete()

        val resolver = appContext.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        return uri?.let {
            resolver.openOutputStream(uri) as FileOutputStream
        }
    }

    private fun saveInDownloadsBelowQ(fileName: String): FileOutputStream {
        val path = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        ).toString()

        val dir = File(path, "NayanOfflineVideos")
        dir.mkdirs()

        val file = File(dir, fileName)
        if (file.exists()) file.delete()
        return FileOutputStream(File(dir, fileName))
    }

    fun getAllSegments() = sharedPrefManager.getAllSegments()
    fun getNearbyDrivers() = sharedPrefManager.getNearbyDrivers()
    fun saveOfflineVideoBatch(offlineVideoBatch: MutableList<String>) {
        sharedPrefManager.saveOfflineVideoBatch(offlineVideoBatch)
    }

    fun saveNDVVideoBatch(ndvVideoBatch: MutableList<String>) {
        sharedPrefManager.saveNDVVideoBatch(ndvVideoBatch)
    }

    fun clearPreferences() = sharedPrefManager.clearPreferences()
}