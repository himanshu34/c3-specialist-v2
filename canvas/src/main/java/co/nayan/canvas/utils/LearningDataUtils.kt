package co.nayan.canvas.utils

import android.os.Build
import android.text.Html
import android.text.Spanned
import co.nayan.canvas.sandbox.models.LpInputData
import timber.log.Timber

object LearningDataUtils {

    fun getFormattedCorrectAnswer(correctAnswer: String?, userAnswer: String?): Spanned {
        var formattedAnswer = ""
        when {
            correctAnswer.isNullOrEmpty() -> {
                formattedAnswer = ""
            }
            userAnswer.isNullOrEmpty() -> {
                formattedAnswer = "<font color='#FFC107'>$correctAnswer</font>"
            }
            else -> {
                correctAnswer.forEachIndexed { index, char ->
                    formattedAnswer +=
                        when {
                            index > userAnswer.length - 1 -> {
                                "<font color='#FFC107'>$char</font>"
                            }
                            char != userAnswer[index] -> {
                                "<font color='#4CAF50'>$char</font>"
                            }
                            else -> {
                                "<font color='#333333'>$char</font>"
                            }
                        }
                }
            }
        }

        Timber.e(formattedAnswer)
        return formattedAnswer.getSpannedValue()
    }

    fun getFormattedUserAnswer(correctAnswer: String?, userAnswer: String?): Spanned {
        var formattedAnswer = ""
        when {
            userAnswer.isNullOrEmpty() -> {
                formattedAnswer = ""
            }
            correctAnswer.isNullOrEmpty() -> {
                formattedAnswer = "<font color='#DD3A2E'>$userAnswer</font>"
            }
            else -> {
                userAnswer.forEachIndexed { index, char ->
                    formattedAnswer +=
                        if (index < correctAnswer.length && char == correctAnswer[index]) {
                            "<font color='#333333'>$char</font>"
                        } else {
                            "<font color='#DD3A2E'>$char</font>"
                        }
                }
            }
        }
        return formattedAnswer.getSpannedValue()
    }

    fun getLpData(answer: String) = when {
        answer.contains("-") -> {
            val inputs = answer.split("-")
            LpInputData(null, inputs.first(), inputs.last())
        }
        answer.contains("=") -> {
            val inputs = answer.split("=")
            LpInputData(inputs.first(), null, inputs.last())
        }
        else -> {
            LpInputData(null, null, answer)
        }
    }

    @Suppress("DEPRECATION")
    private fun String.getSpannedValue() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        Html.fromHtml(this, Html.FROM_HTML_OPTION_USE_CSS_COLORS)
    } else {
        Html.fromHtml(this)
    }
}