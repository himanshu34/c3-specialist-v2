package com.nayan.nayancamv2.storage

import androidx.annotation.Keep
import java.io.File

@Keep
data class StorageUnit(
    val value : Long,
    val unit : String
) {
    fun memoryInfoInMB() = "$value $unit"

    fun memoryInfoInGB() : String {
        val inGB: Float = value.toFloat() / 1024
        return String.format("%.2f GB ", inGB)
    }

    fun covertToGB() : Float {
        return value.toFloat() / 1024
    }
}

@Keep
data class RootStorageOptions (
    val storageDir: File,
    val storageDirPath : String,
    val rootDir: File,
    val rootDirPath : String,
    val tempDir: File,
    val tempDirPath : String,
    val removable : Boolean,
    var availableStorage : StorageUnit,
    var totalStorage : StorageUnit
)