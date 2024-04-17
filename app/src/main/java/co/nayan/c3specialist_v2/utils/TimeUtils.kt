package co.nayan.c3specialist_v2.utils

import java.text.SimpleDateFormat
import java.util.*

private val dateFormat: SimpleDateFormat
    get() = SimpleDateFormat("dd MMM,yyyy", Locale.getDefault())

fun Calendar.currentDate(): String {
    return dateFormat.format(time)
}

fun Calendar.weekStartDate(): String {
    set(Calendar.DAY_OF_MONTH, get(Calendar.DAY_OF_MONTH))
    set(Calendar.MONTH, get(Calendar.MONTH))
    set(Calendar.YEAR, get(Calendar.YEAR))
    add(Calendar.DAY_OF_MONTH, Calendar.SUNDAY - get(Calendar.DAY_OF_WEEK))
    return dateFormat.format(time)
}

fun Calendar.monthStartDate(): String {
    set(Calendar.DAY_OF_MONTH, get(Calendar.DAY_OF_MONTH))
    set(Calendar.MONTH, get(Calendar.MONTH))
    set(Calendar.YEAR, get(Calendar.YEAR))
    add(Calendar.DAY_OF_MONTH, 1 - get(Calendar.DAY_OF_MONTH))
    return dateFormat.format(time)
}

fun String.startTime(): String {
    return "$this 00:00:00"
}

fun String.endTime(): String {
    return "$this 23:59:59"
}

fun Date?.string(): String {
    return if (this == null) ""
    else {
        val dateFormat = SimpleDateFormat("dd MMM,yyyy hh:mm a", Locale.getDefault())
        dateFormat.format(this) ?: ""
    }
}

fun String.getDate(): Date? {
    return dateFormat.parse(this)
}