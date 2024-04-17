package co.nayan.c3specialist_v2

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.regex.Matcher
import java.util.regex.Pattern

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
    fun readOTP() {
        val message = "<#> Please use OTP 134637 to login into your Nayan account. OTP valid for 5 minutes \nYDJ9FDE8Ern"
        // This will match any 6 digit number in the message
        val pattern: Pattern = Pattern.compile("(|^)\\d{6}")
        val matcher: Matcher = pattern.matcher(message)
        if (matcher.find()) println(matcher.group(0))
        else println("")
    }
}