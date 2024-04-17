package com.nayan.nayancamv2.scout

import co.nayan.nayancamv2.R
import com.nayan.nayancamv2.BaseFragment
import java.io.File

class ScoutModePreviewFragment: BaseFragment(R.layout.scout_preview_fragment) {

    private lateinit var file: File

    companion object {
        @JvmStatic
        fun newInstance(
            file: File
        ) = ScoutModePreviewFragment().apply {
            this.file = file
        }
    }
}