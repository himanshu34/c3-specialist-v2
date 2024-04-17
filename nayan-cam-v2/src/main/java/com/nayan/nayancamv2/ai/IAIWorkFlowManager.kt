package com.nayan.nayancamv2.ai

interface IAIWorkFlowManager {
    fun onStateChanged(state: InferenceState)
}