package com.example.todo

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderSchedulerTest {
    @Test
    fun concreteFutureReminderResolvesToThatDateTime() {
        val now = LocalDateTime.of(2026, 6, 15, 8, 30)
        val reminder = FlexibleDateTime(
            year = 2026,
            month = 6,
            day = 15,
            hour = 9,
            minute = 0
        )

        assertEquals(
            LocalDateTime.of(2026, 6, 15, 9, 0),
            reminder.nextReminderOccurrenceAfter(now)
        )
    }

    @Test
    fun recurringDailyReminderUsesTomorrowWhenTodaysTimeHasPassed() {
        val now = LocalDateTime.of(2026, 6, 15, 9, 30)
        val reminder = FlexibleDateTime(hour = 9, minute = 0)

        assertEquals(
            LocalDateTime.of(2026, 6, 16, 9, 0),
            reminder.nextReminderOccurrenceAfter(now)
        )
    }

    @Test
    fun pastConcreteReminderIsNotScheduled() {
        val now = LocalDateTime.of(2026, 6, 15, 9, 30)
        val reminder = FlexibleDateTime(
            year = 2026,
            month = 6,
            day = 15,
            hour = 9,
            minute = 0
        )

        assertNull(reminder.nextReminderOccurrenceAfter(now))
    }

    @Test
    fun reminderWithoutSpecificMinuteIsNotScheduled() {
        val now = LocalDateTime.of(2026, 6, 15, 9, 30)
        val reminder = FlexibleDateTime(hour = 10)

        assertNull(reminder.nextReminderOccurrenceAfter(now))
    }
}
