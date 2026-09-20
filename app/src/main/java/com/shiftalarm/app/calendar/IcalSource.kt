package com.shiftalarm.app.calendar

import android.content.Context
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object IcalSource {

    fun fetchEventsFromUrl(url: String, fromMillis: Long, toMillis: Long): List<CalEvent> {
        val text = download(url)
        return parseAll(text, toMillis).filter { it.begin >= fromMillis && it.begin <= toMillis }
    }

    fun parseFromUri(context: Context, uri: Uri): List<CalEvent> {
        val text = context.contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: throw IllegalStateException("\u8b80\u5514\u5230\u6a94\u6848")
        // Expand recurring events about a year ahead; the sync window filters
        // down to the lookahead afterwards.
        return parseAll(text, System.currentTimeMillis() + 370L * 86400_000L)
    }

    private fun download(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.instanceFollowRedirects = true
        try {
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun parseAll(text: String, horizonMillis: Long): List<CalEvent> {
        val lines = unfold(text.split("\r\n", "\n"))
        val events = mutableListOf<CalEvent>()
        var inEvent = false
        var summary = ""
        var location = ""
        var dtstart = 0L
        var dtend = 0L
        var uid = ""
        var rrule = ""
        for (raw in lines) {
            val line = raw.trimEnd()
            when {
                line == "BEGIN:VEVENT" -> {
                    inEvent = true
                    summary = ""
                    location = ""
                    dtstart = 0L
                    dtend = 0L
                    uid = ""
                    rrule = ""
                }
                line == "END:VEVENT" -> {
                    if (inEvent && dtstart > 0L) {
                        val base = CalEvent(
                            instanceId = ((uid.ifEmpty { summary + dtstart }).hashCode().toLong() and 0x7FFFFFFFL),
                            begin = dtstart,
                            end = if (dtend > 0L) dtend else dtstart,
                            title = summary,
                            location = location,
                            calendarId = -1L
                        )
                        if (rrule.isNotEmpty()) {
                            events += expandRrule(uid, summary, base, rrule, horizonMillis)
                        } else {
                            events += base
                        }
                    }
                    inEvent = false
                }
                inEvent && line.startsWith("SUMMARY:") -> summary = unescape(line.substring(8))
                inEvent && line.startsWith("LOCATION:") -> location = unescape(line.substring(9))
                inEvent && line.startsWith("UID:") -> uid = line.substring(4)
                inEvent && line.startsWith("RRULE:") -> rrule = line.substring(6)
                inEvent && (line.startsWith("DTSTART") || line.startsWith("DTEND")) -> {
                    val t = parseDateTime(line)
                    if (line.startsWith("DTSTART")) dtstart = t else dtend = t
                }
            }
        }
        return events
    }

    /**
     * Expand a recurring event (RRULE) into individual occurrences so future
     * repeats of a roster shift actually appear as separate days.
     *
     * Supported: FREQ=DAILY/WEEKLY/MONTHLY, INTERVAL, COUNT, UNTIL and
     * BYDAY (weekly). Every occurrence gets its own instanceId derived from
     * its start time, so each day is its own alarm group. Overridden or
     * cancelled single occurrences (RECURRENCE-ID / EXDATE) are not handled.
     */
    private fun expandRrule(
        uid: String,
        summary: String,
        base: CalEvent,
        rrule: String,
        horizonMillis: Long
    ): List<CalEvent> {
        val parts = rrule.split(";").mapNotNull {
            val idx = it.indexOf('=')
            if (idx <= 0) null else it.substring(0, idx).uppercase() to it.substring(idx + 1)
        }.toMap()

        val freq = parts["FREQ"]?.uppercase() ?: return listOf(base)
        val interval = (parts["INTERVAL"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
        val count = parts["COUNT"]?.toIntOrNull()
        val until = parts["UNTIL"]?.let { parseBareDate(it) } ?: 0L
        val byDay = parts["BYDAY"]
            ?.split(",")
            ?.mapNotNull { dayCodeToCalendarDay(it.trim().uppercase()) }
            ?.toSet()
            ?: emptySet()

        val duration = (base.end - base.begin).coerceAtLeast(0L)
        val out = mutableListOf<CalEvent>()
        val cal = Calendar.getInstance().apply { timeInMillis = base.begin }
        // Week index (relative to the DTSTART week) used for WEEKLY+BYDAY with
        // INTERVAL > 1: only weeks where (weekIndex % interval == 0) repeat.
        val anchorWeekStart = weekStartMillis(base.begin)

        var generated = 0
        var guard = 0
        while (generated < 1000 && guard < 5000) {
            if (guard++ == 5000) break
            val t = cal.timeInMillis
            if (until > 0L && t > until) break
            if (t > horizonMillis) break
            val include = when (freq) {
                "DAILY", "MONTHLY" -> true
                "WEEKLY" -> byDay.isEmpty() || cal.get(Calendar.DAY_OF_WEEK) in byDay
                else -> return listOf(base)
            } && weekAligned(freq, byDay, interval, t, anchorWeekStart, base.begin)
            if (include && t >= base.begin) {
                out += CalEvent(
                    instanceId = ((uid.ifEmpty { summary } + t).hashCode().toLong() and 0x7FFFFFFFL),
                    begin = t,
                    end = t + duration,
                    title = base.title,
                    location = base.location,
                    calendarId = -1L
                )
                generated++
                if (count != null && generated >= count) break
            }
            when (freq) {
                "DAILY" -> cal.add(Calendar.DAY_OF_YEAR, interval)
                "WEEKLY" -> if (byDay.isEmpty()) cal.add(Calendar.WEEK_OF_YEAR, interval)
                else cal.add(Calendar.DAY_OF_YEAR, 1)
                "MONTHLY" -> {
                    val day = cal.get(Calendar.DAY_OF_MONTH)
                    cal.add(Calendar.MONTH, interval)
                    // Months without the anchor day (e.g. 31st) are skipped.
                    if (cal.get(Calendar.DAY_OF_MONTH) != day) {
                        cal.add(Calendar.MONTH, 1)
                        cal.set(Calendar.DAY_OF_MONTH, 1)
                    }
                }
            }
        }
        return if (out.isEmpty()) listOf(base) else out
    }

    /** For WEEKLY+BYDAY with INTERVAL > 1, only every `interval`-th week repeats. */
    private fun weekAligned(
        freq: String,
        byDay: Set<Int>,
        interval: Int,
        t: Long,
        anchorWeekStart: Long,
        baseBegin: Long
    ): Boolean {
        if (freq != "WEEKLY" || byDay.isEmpty() || interval <= 1) return true
        val weeksFromAnchor = (t - anchorWeekStart) / (7L * 86400_000L)
        // Negative before the anchor week — the t >= base.begin check in the
        // caller filters those anyway.
        return weeksFromAnchor % interval == 0L || t == baseBegin
    }

    private fun weekStartMillis(t: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = t }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        return cal.timeInMillis
    }

    private fun dayCodeToCalendarDay(code: String): Int? = when (code) {
        "MO" -> Calendar.MONDAY
        "TU" -> Calendar.TUESDAY
        "WE" -> Calendar.WEDNESDAY
        "TH" -> Calendar.THURSDAY
        "FR" -> Calendar.FRIDAY
        "SA" -> Calendar.SATURDAY
        "SU" -> Calendar.SUNDAY
        else -> null
    }

    /** UNTIL value: 20260916, 20260916T235959 or 20260916T235959Z. */
    private fun parseBareDate(value: String): Long = try {
        when {
            value.endsWith("Z") -> {
                val f = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
                f.timeZone = TimeZone.getTimeZone("UTC")
                f.parse(value)?.time ?: 0L
            }
            value.length == 8 -> {
                val f = SimpleDateFormat("yyyyMMdd", Locale.US)
                f.timeZone = TimeZone.getDefault()
                f.parse(value)?.time ?: 0L
            }
            else -> {
                val f = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US)
                f.timeZone = TimeZone.getDefault()
                f.parse(value)?.time ?: 0L
            }
        }
    } catch (e: Exception) {
        0L
    }

    private fun unfold(rawLines: List<String>): List<String> {
        val out = mutableListOf<String>()
        for (l in rawLines) {
            if ((l.startsWith(" ") || l.startsWith("\t")) && out.isNotEmpty()) {
                out[out.size - 1] = out.last() + l.substring(1)
            } else {
                out.add(l)
            }
        }
        return out
    }

    private fun parseDateTime(line: String): Long {
        val value = line.substringAfter(":")
        return try {
            when {
                value.endsWith("Z") -> {
                    val f = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
                    f.timeZone = TimeZone.getTimeZone("UTC")
                    f.parse(value)?.time ?: 0L
                }
                value.length == 8 -> {
                    val f = SimpleDateFormat("yyyyMMdd", Locale.US)
                    f.timeZone = TimeZone.getDefault()
                    f.parse(value)?.time ?: 0L
                }
                else -> {
                    val f = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US)
                    f.timeZone = TimeZone.getDefault()
                    f.parse(value)?.time ?: 0L
                }
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun unescape(s: String): String =
        s.replace("\\,", ",").replace("\\;", ";").replace("\\n", " ").replace("\\N", " ")
}
