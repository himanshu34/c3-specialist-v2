package co.nayan.c3specialist_v2.profile.utils

import co.nayan.c3specialist_v2.config.AppLanguage
import java.util.regex.Pattern

fun String?.getLanguage() = if (this == AppLanguage.HINDI) {
    "हिन्दी"
} else {
    "English"
}

fun String.maskPanNumber() = "${this.substring(0, 5)}XXXX${this.substring(9, 10)}"

fun String.isValidPan(): Boolean {
    val regex = "[A-Z]{5}[0-9]{4}[A-Z]"
    return Pattern.compile(regex).matcher(this).matches()
}
