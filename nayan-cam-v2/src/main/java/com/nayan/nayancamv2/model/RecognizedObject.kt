package com.nayan.nayancamv2.model

import android.graphics.Bitmap

data class RecognizedObject( val recognizedObject:Pair<Bitmap, String>, val aiModelIndex:Int=0, val workFlowIndex:Int=0)