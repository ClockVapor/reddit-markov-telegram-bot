package clockvapor.redditmarkovtelegrambot

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun log(s: String) {
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    println("$timestamp: $s")
}

fun log(t: Throwable) {
    log(t.localizedMessage)
    t.printStackTrace()
}

inline fun tryOrLog(f: () -> Unit) = try {
    f()
} catch (e: Exception) {
    log(e)
}

inline fun <T> tryOrNull(reportException: Boolean = true, f: () -> T): T? = try {
    f()
} catch (e: Exception) {
    if (reportException) log(e)
    null
}
