package pe.combustiblehoy.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Fechas que publica el pipeline: "AAAA-MM-DD" o "AAAA-MM-DDTHH:MM" (hora de Lima). */
object Dates {
    private val dayMonth = DateTimeFormatter.ofPattern("dd/MM")
    private val dayMonthYear = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val hourMinute = DateTimeFormatter.ofPattern("HH:mm")

    fun parse(text: String?): LocalDateTime? {
        if (text.isNullOrBlank()) return null
        return runCatching { LocalDateTime.parse(text) }.getOrNull()
            ?: runCatching { LocalDate.parse(text.take(10)).atStartOfDay() }.getOrNull()
    }

    /** "18/11 21:55", o "18/11/2025" si es de otro año. */
    fun pretty(text: String?, today: LocalDate = LocalDate.now()): String {
        val dt = parse(text) ?: return text.orEmpty()
        if (dt.year != today.year) return dt.format(dayMonthYear)
        val hasTime = text!!.length > 10
        return if (hasTime) "${dt.format(dayMonth)} ${dt.format(hourMinute)}" else dt.format(dayMonth)
    }

    fun daysAgo(text: String?, today: LocalDate = LocalDate.now()): Long? =
        parse(text)?.toLocalDate()?.let { ChronoUnit.DAYS.between(it, today) }

    /** Texto corto de antigüedad del precio declarado: "hoy", "ayer", "hace 5 días", "18/11/2025". */
    fun age(text: String?, today: LocalDate = LocalDate.now()): String {
        val days = daysAgo(text, today) ?: return ""
        return when {
            days <= 0L -> "hoy"
            days == 1L -> "ayer"
            days < 30L -> "hace $days días"
            else -> pretty(text, today)
        }
    }
}
