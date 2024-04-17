package co.nayan.nayancamv2

import android.location.Location
import co.nayan.c3v2.core.models.AIModelRuleSize
import co.nayan.c3v2.core.models.CameraAIModelRule
import com.nayan.nayancamv2.util.DEVICE_PERFORMANCE.DELAYED_ONE_WEEK
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.ParseException
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun getUTCTime() {
//        val sftpLink: String? = "pol-1940-lat-22.5622697lon-75.7633486dt-11-03-22ti-08-47-54.mp4"
        val sftpLink: String? = null
        val calendar = Calendar.getInstance()

        // parse to LocalDateTime
        val parsed: OffsetDateTime = LocalDateTime.parse("2022-11-16T20:22:18")
            // convert to UTC
            .atOffset(ZoneOffset.UTC)
        val outputFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")
        println(outputFormatter.format(parsed.atZoneSameInstant(ZoneId.systemDefault())))

//        val dateFormat = SimpleDateFormat("$DATE_FORMAT $TIME_FORMAT")
//        val date = dateFormat.parse("04-06-23 18-54-35")
//        val strLocalDate = dateFormat.format(date)
//        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
//        val strUTCDate = dateFormat.format(date)
//        println("Local Millis * " + date.time + "  ---Local DateTime  " + strLocalDate) //correct
//        println("Local Millis * " + date.time + "  ---UTC DateTime  " + strUTCDate) //correct

        /*val milliseconds = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("dd-MM-yy HH-mm-ss", Locale.getDefault())
        val utcDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = Date(milliseconds)
        val strLocalDate = dateFormat.format(date)
        utcDateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val strUTCDate = utcDateFormat.format(date)
        println("Local Millis * " + date.time + "  ---Local DateTime  " + strLocalDate) //correct
        println("Local Millis * " + date.time + "  ---UTC DateTime  " + strUTCDate) //correct
        println(Instant.ofEpochMilli(milliseconds).toString())*/

//        val dateFormatLocal = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
//        val utcDate = dateFormatLocal.parse(strUTCDate)
//        println("UTC Millis * " + utcDate.time + " ------  " + dateFormatLocal.format(utcDate))
//        println("UTC Millis * " + utcDate.time + " ------  " + dateFormatLocal.format(utcDate))
        /*val today = LocalDateTime.now()
//        val ssDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
//        println(ssDateTimeFormatter.format(today))
        val dateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yy HH-mm-ss")
        println(dateTimeFormatter.format(today))
        val localDateTime = Instant.ofEpochMilli(1668159127890)
            .atZone(ZoneId.systemDefault()).toLocalDateTime()
        println(dateTimeFormatter.format(localDateTime))
        println(localDateTime)*/

//        val zonedDateTime = ZonedDateTime.now()
//        println(zonedDateTime)
//        val instant = Instant.now()
//        println(instant.toString())
//        if (isValidDate(instant.toString())) {
//            println(true)
//        } else println(false)

//        println(Instant.ofEpochMilli(instant.toEpochMilli()).toString())
//
//        val dateTimeString = dateTimeFormatter.format(today)
//        // parsed date time without timezone information
//        val localDateTime = LocalDateTime.parse(dateTimeString, DateTimeFormatter.ofPattern("dd-MM-yy HH-mm-ss"))
//        // local date time at your system's default time zone
//        val systemZoneDateTime: ZonedDateTime = localDateTime.atZone(ZoneId.systemDefault())
//        // value converted to other timezone while keeping the point in time
//        val utcDateTime: ZonedDateTime = systemZoneDateTime.withZoneSameInstant(ZoneId.of("UTC"))
//        // timestamp of the original value represented in UTC
//        val utcTimestamp: Instant = systemZoneDateTime.toInstant()
//        println(utcDateTime)
//        println(utcTimestamp)

//        val currentDate = LocalDateTime.now().dayOfMonth
//        println(currentDate)
//        calendar.timeInMillis = ("1668011144893").toLong()
//        val usageDate = calendar.get(Calendar.DAY_OF_MONTH)
//        println(usageDate)
//        val localDateTime = Instant.ofEpochMilli(1668011144893).atZone(ZoneId.systemDefault()).toLocalDateTime()
//        println(localDateTime.dayOfMonth)

        /*if (sftpLink.isNullOrEmpty().not()) {
            val dateStr = sftpLink?.substringAfter("dt-")?.substringBefore("ti")
            val timeStr = sftpLink?.substringAfter("ti-")?.substringBefore(".mp4")
            // DATE_FORMAT = "dd-MM-yy" && TIME_FORMAT = "HH-mm-ss"
            val formatter = SimpleDateFormat("dd-MM-yy HH-mm-ss", Locale.ENGLISH)
            val date = formatter.parse("$dateStr $timeStr")
            if (date != null) calendar.time = date
        } else calendar.time = Date("1667999298000".toLong())
        println(calendar.time.toString())

        // Convert to UTC time zone
        val simpleDateFormat = SimpleDateFormat("EE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH)
        simpleDateFormat.timeZone = TimeZone.getTimeZone("UTC")
        println(simpleDateFormat.format(calendar.time))*/
    }

    private fun isValidDate(recordedOnDate: String): Boolean {
        return try {
            Instant.parse(recordedOnDate)
            true
        } catch (e: ParseException) {
            false
        }
    }

    @Test
    fun getDistanceInMeter() {
        try {
            val startPoint = Location("Location Start").apply {
                this.latitude = 25.466164
                this.longitude = 78.576404
            }

            val endPoint = Location("Location End").apply {
                this.latitude = 25.466167
                this.longitude = 78.576402
            }

            println(startPoint.distanceTo(endPoint))
        } catch (e: Exception) {
            e.printStackTrace()
            println(0f)
        }
    }

    @Test
    fun main() {
        val oneWeekAgoTimeStamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        val oneWeekAgoTimeStamp1 = System.currentTimeMillis() - DELAYED_ONE_WEEK
        println(oneWeekAgoTimeStamp)
        println(oneWeekAgoTimeStamp1)

        val items = listOf(
            CameraAIModelRule(255, "vest", AIModelRuleSize(10, 10), 0.1f, 0.1f, null),
            CameraAIModelRule(256, "helmet", AIModelRuleSize(10, 10), 0.1f, 0.1f, null)
        )

        val max = items.minBy {
            println("minByOrNull $it")
            it.confidence
        }

        println("\nresult: $max")
    }

    @Test
    fun getWorkingTime() {
        val timeStamp_in: Long = 309561
        val hours_in = TimeUnit.MILLISECONDS.toHours(timeStamp_in)
        val minutes_in = TimeUnit.MILLISECONDS.toMinutes(timeStamp_in) - TimeUnit.HOURS.toMinutes(hours_in)
        println(String.format("%02d:%02d", hours_in, minutes_in))
    }
}