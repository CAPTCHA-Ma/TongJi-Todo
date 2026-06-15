package com.example.todo

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * A date/time representation where every field is optional.
 *
 * Any field set to `null` acts as a wildcard — it matches all values for that unit.
 * Examples:
 *   - `FlexibleDateTime(hour=8, minute=0)` → every day at 08:00
 *   - `FlexibleDateTime(month=5, day=7)` → every May 7th, all day
 *   - `FlexibleDateTime(month=6)` → every day in June
 *   - All fields set → a specific point in time (year-month-day hour:minute)
 *   - All fields null → matches everything (e.g. "any time")
 *
 * @param year   null = every year
 * @param month  null = every month   (1..12)
 * @param day    null = every day     (1..31)
 * @param hour   null = every hour    (0..23)
 * @param minute null = every minute  (0..59)
 */
data class FlexibleDateTime(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null,
    val hour: Int? = null,
    val minute: Int? = null
) {
    /**
     * Returns `true` when every field is `null`.
     */
    val isEmpty: Boolean get() =
        year == null && month == null && day == null && hour == null && minute == null
}

// ────────────────────────────────────────────────────────
// Comparison
// ────────────────────────────────────────────────────────

/**
 * Lexicographically compare two [FlexibleDateTime] values
 * (year → month → day → hour → minute).
 *
 * Only compares fields where **both** values are non-null — a null
 * field is a wildcard and is treated as equal.
 *
 * @return -1 if `this` is definitely before [other],
 *          1 if `this` is definitely after  [other],
 *          0 if they are equal or the comparison is ambiguous.
 */
fun FlexibleDateTime.compareTo(other: FlexibleDateTime): Int {
    if (year != null && other.year != null) {
        val cmp = year.compareTo(other.year)
        if (cmp != 0) return cmp
    }
    if (month != null && other.month != null) {
        val cmp = month.compareTo(other.month)
        if (cmp != 0) return cmp
    }
    if (day != null && other.day != null) {
        val cmp = day.compareTo(other.day)
        if (cmp != 0) return cmp
    }
    if (hour != null && other.hour != null) {
        val cmp = hour.compareTo(other.hour)
        if (cmp != 0) return cmp
    }
    if (minute != null && other.minute != null) {
        val cmp = minute.compareTo(other.minute)
        if (cmp != 0) return cmp
    }
    return 0
}

// ────────────────────────────────────────────────────────
// Formatting helpers
// ────────────────────────────────────────────────────────

/**
 * Formats the time portion only (hour:minute), e.g. "08:00".
 * Returns an empty string if hour or minute are missing.
 */
fun FlexibleDateTime.toTimeString(): String {
    val h = hour ?: return ""
    val m = minute ?: return ""
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
}

/**
 * Formats the full date/time for display, e.g. "2026-06-04 08:00".
 * Skips any null portion gracefully.
 */
fun FlexibleDateTime.toDisplayString(): String {
    val parts = mutableListOf<String>()

    val datePortion = buildString {
        if (year != null) {
            append(year.toString().padStart(4, '0'))
            if (month != null || day != null) append("-")
        }
        if (month != null) {
            append(month.toString().padStart(2, '0'))
            if (day != null) append("-")
        }
        if (day != null) {
            append(day.toString().padStart(2, '0'))
        }
    }
    if (datePortion.isNotEmpty()) parts.add(datePortion)

    val time = toTimeString()
    if (time.isNotEmpty()) parts.add(time)

    return parts.joinToString(" ")
}

/**
 * Smart-format: compare against [now] and display only from the largest differing
 * unit downward.  A `null` field is treated as a wildcard (always matches).
 *
 * Three display levels (highest differing wins):
 *   - Year differs       →  `"2027"`
 *   - Month or day differs →  `"06-05"`
 *   - Only time differs  →  `"17:04"`
 *
 * Examples with `now = 2026-06-04 19:03`:
 *   - `FlexibleDateTime(month=6, day=4, hour=17, minute=4)`  →  `"17:04"`
 *   - `FlexibleDateTime(month=6, day=5, hour=19, minute=3)`  →  `"06-05"`
 *   - `FlexibleDateTime(year=2025, month=6, day=4)`          →  `"2025"`
 */
fun FlexibleDateTime.toSmartString(now: LocalDateTime = LocalDateTime.now()): String {
    val yearDiff  = year   != null && year   != now.year
    val monthDiff = month  != null && month  != now.monthValue
    val dayDiff   = day    != null && day    != now.dayOfMonth
    val hourDiff  = hour   != null && hour   != now.hour
    val minuteDiff = minute != null && minute != now.minute

    return when {
        yearDiff -> "${year}"
        monthDiff -> {
            buildString {
                if (month != null) append(month.toString().padStart(2, '0'))
                if (day != null) {
                    append("-")
                    append(day.toString().padStart(2, '0'))
                }
            }
        }
        dayDiff -> {
            buildString {
                if (month != null) append(month.toString().padStart(2, '0'))
                if (day != null) {
                    append("-")
                    append(day.toString().padStart(2, '0'))
                }
            }
        }
        hourDiff || minuteDiff -> toTimeString()
        else -> toTimeString()
    }
}

// ────────────────────────────────────────────────────────
// Date resolution
// ────────────────────────────────────────────────────────

/**
 * Converts this FlexibleDateTime to a concrete [LocalDate] when year,
 * month, and day are all non-null.  Returns `null` otherwise.
 */
fun FlexibleDateTime.toConcreteDateOrNull(): LocalDate? {
    val y = year ?: return null
    val m = month ?: return null
    val d = day ?: return null
    return runCatching { LocalDate.of(y, m, d) }.getOrNull()
}

/**
 * Number of days between [now] and the concrete date represented
 * by this FlexibleDateTime.
 *
 * - Wildcard deadline (any of year/month/day is null) → 30 (default).
 * - Past date → 0 (overdue = maximum urgency).
 * - Today → 0.
 * - Future date → positive days until that date.
 */
fun FlexibleDateTime.daysUntil(now: LocalDate = LocalDate.now()): Int {
    val concreteDate = toConcreteDateOrNull() ?: return 30
    val days = ChronoUnit.DAYS.between(now, concreteDate).toInt()
    return days.coerceAtLeast(0)
}
