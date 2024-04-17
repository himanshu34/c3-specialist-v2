package com.nayan.nayancamv2.storage

import org.jcodec.containers.mp4.boxes.MetaValue
import org.jcodec.movtool.MetadataEditor
import timber.log.Timber
import java.io.File

/**
 * Utility class to manipulate metadata
 *
 */
class FileMetaDataEditor {

    fun addMetaDataToFile(file: File, metaData: HashMap<String, String>?) {
        try {
            if (file.exists()) {
                val mediaMeta = MetadataEditor.createFrom(file)
                val keyedMeta = mediaMeta.keyedMeta

                metaData?.forEach { (key, value) ->
                    keyedMeta[key] = MetaValue.createString(value)
                }
                mediaMeta.save(false)
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    internal fun extractMetaDataFromFile(file: File): HashMap<String, String> {
        return try {
            val fileMetadata = HashMap<String, String>()
            val mediaMeta = MetadataEditor.createFrom(file)

            if (mediaMeta.keyedMeta.isNullOrEmpty().not()) {
                mediaMeta.keyedMeta.forEach { (key, value) ->
                    println("$key = $value")
                    fileMetadata[key] = value.string
                }
            }

            if (mediaMeta.itunesMeta.isNullOrEmpty().not()) {
                mediaMeta.itunesMeta.forEach { (key, value) ->
                    println("$key = $value")
                    fileMetadata[key.toString()] = value.string
                }
            }

            fileMetadata
        } catch (e: Exception) {
            Timber.e(e)
            HashMap()
        }
    }

}