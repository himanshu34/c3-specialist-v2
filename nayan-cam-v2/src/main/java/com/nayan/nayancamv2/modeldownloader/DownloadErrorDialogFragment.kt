package com.nayan.nayancamv2.modeldownloader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import co.nayan.nayancamv2.databinding.LayoutDownloadErrorDialogBinding

class DownloadErrorDialogFragment(
    private val callClicked: (() -> Unit?)? = null
) : DialogFragment() {

    private lateinit var binding: LayoutDownloadErrorDialogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, co.nayan.appsession.R.style.SessionDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return LayoutDownloadErrorDialogBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isCancelable = false
        binding.callSupportBtn.setOnClickListener {
            callClicked?.invoke()
            dismiss()
        }
    }
}