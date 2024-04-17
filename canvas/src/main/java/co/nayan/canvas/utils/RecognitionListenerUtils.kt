package co.nayan.canvas.utils

import android.os.Bundle
import android.speech.RecognitionListener
import java.util.*

open class VoiceRecognitionListener : RecognitionListener {
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onEndOfSpeech() {}
    override fun onError(error: Int) {}
    override fun onResults(results: Bundle?) {}
}

fun String.singleValue(): String {
    val input = this.lowercase(Locale("en"))
    digits.forEach {
        if (it.first == input) {
            return it.second
        }
    }
    return this
}

private val digits =
    listOf(
        Pair("zero", "0"),
        Pair("one", "1"),
        Pair("two", "2"),
        Pair("three", "3"),
        Pair("four", "4"),
        Pair("five", "5"),
        Pair("six", "6"),
        Pair("sex", "6"),
        Pair("seven", "7"),
        Pair("eight", "8"),
        Pair("nine", "9")
    )