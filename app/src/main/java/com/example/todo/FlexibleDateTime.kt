package com.example.todo

import java.time.LocalDateTime

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
 * Formats the full date/time for display, e.g. "6月4日 08:00".
 * Skips any null portion gracefully.
 */
fun FlexibleDateTime.toDisplayString(): String {
    val parts = mutableListOf<String>()

    if (year != null) parts.add("${year}年")

    val monthDay = buildString {
        if (month != null) append("${month}月")
        if (day != null) append("${day}日")
    }
    if (monthDay.isNotEmpty()) parts.add(monthDay)

    val time = toTimeString()
    if (time.isNotEmpty()) parts.add(time)

    return parts.joinToString(" ")
}

/**
 * Smart-format: compare against [now] and display only from the largest differing
 * unit downward.  A `null` field is treated as a wildcard (always matches).
 *
 * Examples with `now = 2026-06-04 19:03`:
 *   - `FlexibleDateTime(month=6, day=4, hour=17, minute=4)`  →  `"17:04"`
 *   - `FlexibleDateTime(month=6, day=5, hour=19, minute=3)`  →  `"6月5日"`
 *   - `FlexibleDateTime(year=2025, month=6, day=4)`          →  `"2025年6月4日"`
 */
fun FlexibleDateTime.toSmartString(now: LocalDateTime = LocalDateTime.now()): String {
    // Determine the first (largest) unit that differs from now.
    // null fields never differ — they are wildcards.
    val yearDiff  = year   != null && year   != now.year
    val monthDiff = month  != null && month  != now.monthValue
    val dayDiff   = day    != null && day    != now.dayOfMonth
    val hourDiff  = hour   != null && hour   != now.hour
    val minuteDiff = minute != null && minute != now.minute

    return when {
        yearDiff -> {
            // Year is the biggest difference → show year + month + day
            buildString {
                append("${year}年")
                if (month != null) append("${month}月")
                if (day != null) append("${day}日")
            }
        }
        monthDiff -> {
            // Month differs (year same) → show month + day
            buildString {
                if (month != null) append("${month}月")
                if (day != null) append("${day}日")
            }
        }
        dayDiff -> {
            // Day differs (year & month same) → show month + day
            buildString {
                if (month != null) append("${month}月")
                if (day != null) append("${day}日")
            }
        }
        hourDiff || minuteDiff -> {
            // Only time portion differs → show HH:MM
            toTimeString()
        }
        else -> {
            // All specified fields match now (or are null) → still show time
            toTimeString()
        }
    }
}
