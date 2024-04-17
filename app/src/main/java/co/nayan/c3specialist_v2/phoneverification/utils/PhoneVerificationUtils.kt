package co.nayan.c3specialist_v2.phoneverification.utils

import co.nayan.c3specialist_v2.config.Regex
import io.michaelrocks.libphonenumber.android.NumberParseException
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import timber.log.Timber
import java.util.regex.Matcher
import java.util.regex.Pattern

fun String?.isValidOTP(): Boolean {
    return this?.length == 6
}

fun String.isValidPhoneNumber(): Boolean {
    return length == 10 && Pattern.compile(Regex.MOBILE).matcher(this).matches()
}

fun String.isValidReferralCode(): Boolean {
    return length in 7..9
}

fun String?.parseOTP(): String? {
    if (this == null) return null

    val pattern: Pattern = Pattern.compile("(|^)\\d{6}")
    val matcher: Matcher = pattern.matcher(this)
    return if (matcher.find()) matcher.group(0) else null
}

fun getCountryCode(phoneNumberUtil: PhoneNumberUtil, number: String?): String? {
    number?.let {
        val validatedNumber = if (it.startsWith("+")) it else "+$it"
        return try {
            val phoneNumber = phoneNumberUtil.parse(validatedNumber, null)
            val countryCode = phoneNumber.countryCode.toString()
            countryCode.let { code -> if (code.startsWith("+")) code else "+${code}" }
        } catch (e: NumberParseException) {
            Timber.e("Error during parsing a number")
            null
        }
    } ?: run { return null }
}