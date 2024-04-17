package com.nayan.nayancamv2.extcam.common

import android.graphics.Bitmap
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import co.nayan.c3v2.core.interactors.NayanCamModuleInteractor
import com.nayan.nayancamv2.AIResultsAdapter
import com.nayan.nayancamv2.extcam.media.VideoStreamDecoder
import com.nayan.nayancamv2.helper.IMetaDataHelper
import com.nayan.nayancamv2.helper.IRecordingHelper
import com.nayan.nayancamv2.model.UserLocation
import javax.inject.Inject

class ExtCamViewModel @Inject constructor(
    private val iMetaDataHelper: IMetaDataHelper,
    private val iRecordingHelper: IRecordingHelper,
    private val nayanCamModuleInteractor: NayanCamModuleInteractor
) : ViewModel(), LifecycleObserver {

    val aiResultsAdapter = AIResultsAdapter()
    val aiResultList = ArrayList<HashMap<Int, Pair<Bitmap, String>>>()
    var userLocation: UserLocation? = null
    var recordingState: MutableLiveData<String> = MutableLiveData()

    val videoStreamDecoder by lazy {
        VideoStreamDecoder(
            iRecordingHelper,
            iMetaDataHelper,
            nayanCamModuleInteractor.getDeviceModel(),
            false
        ).apply {
            width = 1280
            height = 720
        }
    }

    class RecordingState(
        val recordingState: Int,
        val message: String
    )
}