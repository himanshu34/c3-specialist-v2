package co.nayan.canvas.utils

import java.util.regex.Pattern

class TextToSpeechUtils {

    companion object {

        fun getSeparatedTextByNumbers(text: String): String {
            var updatedText = text
            val pattern = Pattern.compile("-?\\d+")
            val matcher = pattern.matcher(text)
            val listOfNumbers = mutableListOf<String>()
            while (matcher.find()) {
                listOfNumbers.add(matcher.group())
            }

            listOfNumbers.forEach {
                var digitsWithUnderScore = ""
                it.split("").forEach { digit ->
                    digitsWithUnderScore += "$digit "
                }
                updatedText = updatedText.replace(it, digitsWithUnderScore)
            }

            return updatedText
        }
    }

}

object TextToSpeechConstants {
    const val PITCH = 0.5F
    const val SPEED_RATE = 0.8F
}