package info.plateaukao.einkbro.util

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Replacement for the SimpleDateFormat patterns used by the browsing history
 * and record UIs ("MMM dd" / "HH:mm" style).
 */
object DateFormat {
    private val MONTHS = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )

    fun monthDay(epochMillis: Long): String {
        val dt = Instant.fromEpochMilliseconds(epochMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        return "${MONTHS[dt.monthNumber - 1]} ${dt.dayOfMonth.toString().padStart(2, '0')}"
    }

    fun hourMinute(epochMillis: Long): String {
        val dt = Instant.fromEpochMilliseconds(epochMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        return "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
    }

    fun format(epochMillis: Long, pattern: String): String {
        val dt = Instant.fromEpochMilliseconds(epochMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        return pattern
            .replace("yyyy", dt.year.toString())
            .replace("MMM", MONTHS[dt.monthNumber - 1])
            .replace("MM", dt.monthNumber.toString().padStart(2, '0'))
            .replace("dd", dt.dayOfMonth.toString().padStart(2, '0'))
            .replace("HH", dt.hour.toString().padStart(2, '0'))
            .replace("mm", dt.minute.toString().padStart(2, '0'))
    }
}
