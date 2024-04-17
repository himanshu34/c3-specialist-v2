package co.nayan.canvas.modes.input

import co.nayan.c3v2.core.config.Judgment
import javax.inject.Inject

class LPInputManager @Inject constructor() {

    var userSingleLineInput: String = ""
    var userDoubleLineInput: String = ""
    var userSecondInput: String = ""

    var isSingleLineInputSelected: Boolean = true
    var isFirstInputFieldSelected: Boolean = true

    private var lpInputInteractionListener: LPInputInteractionListener? = null

    fun setupInteractionListener(toSet: LPInputInteractionListener) {
        lpInputInteractionListener = toSet
    }

    fun getLpInput(): String {
        return if (userSingleLineInput.isEmpty() && userDoubleLineInput.isEmpty()) {
            userSecondInput
        } else {
            if (isSingleLineInputSelected) {
                if (userSingleLineInput.isNotEmpty())
                    "$userSingleLineInput-$userSecondInput"
                else userSecondInput
            } else {
                if (userDoubleLineInput.isNotEmpty())
                    "$userDoubleLineInput=$userSecondInput"
                else userSecondInput
            }
        }
    }

    fun appendInput(value: String) {
        value.forEach { char ->
            if (isFirstInputFieldSelected &&
                (userSingleLineInput.length == FIRST_INPUT_MAX_LENGTH ||
                        userDoubleLineInput.length == FIRST_INPUT_MAX_LENGTH)
            ) lpInputInteractionListener?.selectSecondInputField()

            if (isFirstInputFieldSelected) {
                if (isSingleLineInputSelected) {
                    userSingleLineInput += char
                    if (userSingleLineInput.length == FIRST_INPUT_MAX_LENGTH) {
                        lpInputInteractionListener?.selectSecondInputField()
                    }
                    lpInputInteractionListener?.updateSingleLineInput(userSingleLineInput)
                } else {
                    userDoubleLineInput += char
                    if (userDoubleLineInput.length == FIRST_INPUT_MAX_LENGTH) {
                        lpInputInteractionListener?.selectSecondInputField()
                    }
                    lpInputInteractionListener?.updateDoubleLineInput(userDoubleLineInput)
                }
            } else {
                if (userSecondInput.length < SECOND_INPUT_MAX_LENGTH) {
                    userSecondInput += char
                }
                if (userSecondInput.isEmpty()) {
                    lpInputInteractionListener?.selectFirstInputField()
                }
                lpInputInteractionListener?.updateSecondInput(userSecondInput)
            }
        }
    }

    fun deleteInput() {
        if (isFirstInputFieldSelected) {
            if (isSingleLineInputSelected) {
                userSingleLineInput = if (userSingleLineInput.isNotEmpty())
                    userSingleLineInput.substring(0, userSingleLineInput.length - 1) else ""

                if (userSingleLineInput.length == FIRST_INPUT_MAX_LENGTH)
                    lpInputInteractionListener?.selectSecondInputField()

                lpInputInteractionListener?.updateSingleLineInput(userSingleLineInput)
            } else {
                userDoubleLineInput = if (userDoubleLineInput.isNotEmpty())
                    userDoubleLineInput.substring(0, userDoubleLineInput.length - 1) else ""

                if (userDoubleLineInput.length == FIRST_INPUT_MAX_LENGTH)
                    lpInputInteractionListener?.selectSecondInputField()

                lpInputInteractionListener?.updateDoubleLineInput(userDoubleLineInput)
            }
        } else {
            userSecondInput = if (userSecondInput.isNotEmpty())
                userSecondInput.substring(0, userSecondInput.length - 1) else ""

            if (userSecondInput.isEmpty())
                lpInputInteractionListener?.selectFirstInputField()

            lpInputInteractionListener?.updateSecondInput(userSecondInput)
        }
    }

    fun setLpInputValues(input: String) {
        if (input.isNotEmpty() && input != Judgment.JUNK) {
            when {
                input.contains("-") -> {
                    userSingleLineInput = input.split("-").first()
                    userSecondInput = input.split("-").last()
                    lpInputInteractionListener?.updateSingleLineInput(userSingleLineInput)
                }
                input.contains("=") -> {
                    userDoubleLineInput = input.split("=").first()
                    userSecondInput = input.split("=").last()
                    lpInputInteractionListener?.updateDoubleLineInput(userDoubleLineInput)
                }
                else -> {
                    userSecondInput = input
                }
            }
            lpInputInteractionListener?.updateSecondInput(userSecondInput)
        }
    }

    companion object {
        private const val FIRST_INPUT_MAX_LENGTH = 2
        private const val SECOND_INPUT_MAX_LENGTH = 7
    }
}

interface LPInputInteractionListener {
    fun selectFirstInputField()
    fun selectSecondInputField()
    fun updateSingleLineInput(input: String)
    fun updateDoubleLineInput(input: String)
    fun updateSecondInput(input: String)
}