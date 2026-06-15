package com.example.todo

import com.example.todo.ui.theme.SoftBlue
import com.example.todo.ui.theme.SoftLavender
import com.example.todo.ui.theme.SoftPeach
import com.example.todo.ui.theme.SoftRose
import com.example.todo.ui.theme.SoftSage
import java.time.LocalDate

/**
 * In-memory sample data used while the app has no backend.
 *
 * All data is read-only — screens copy these lists into their own mutable state
 * so the originals stay pristine and can be reused on recomposition.
 */
object SampleData {
    val deprecatedDefaultScheduleIds: Set<String> = setOf("s1", "s2", "s3", "s4")
    val deprecatedDefaultTaskIds: Set<String> = setOf("t1", "t2", "t3")

    private val today: LocalDate = LocalDate.now()
    private val tomorrow: LocalDate = today.plusDays(1)

    private fun at(date: LocalDate, hour: Int, minute: Int): FlexibleDateTime =
        FlexibleDateTime(
            year = date.year,
            month = date.monthValue,
            day = date.dayOfMonth,
            hour = hour,
            minute = minute
        )

    val defaultSchedules: List<Schedule> = listOf(
        Schedule(
            id = "s1",
            title = "Advanced Mathematics",
            startTime = FlexibleDateTime(hour = 8, minute = 0),
            endTime = FlexibleDateTime(hour = 9, minute = 35),
            description = listOf(
                DetailEntry("Teacher", "Prof. Zhang"),
                DetailEntry("Room", "Teaching Bldg A201")
            ),
            reminders = listOf(
                Reminder(FlexibleDateTime(hour = 7, minute = 50))
            ),
            color = SoftBlue
        ),
        Schedule(
            id = "s2",
            title = "Computer Network",
            startTime = FlexibleDateTime(hour = 10, minute = 0),
            endTime = FlexibleDateTime(hour = 11, minute = 35),
            description = listOf(
                DetailEntry("Teacher", "Prof. Li"),
                DetailEntry("Room", "Lab B302")
            ),
            reminders = listOf(
                Reminder(FlexibleDateTime(hour = 9, minute = 50))
            ),
            color = SoftSage
        ),
        Schedule(
            id = "s3",
            title = "AI Principles",
            startTime = FlexibleDateTime(hour = 14, minute = 0),
            endTime = FlexibleDateTime(hour = 15, minute = 35),
            description = listOf(
                DetailEntry("Teacher", "Prof. Wang"),
                DetailEntry("Room", "Hall C101")
            ),
            reminders = listOf(
                Reminder(FlexibleDateTime(hour = 13, minute = 50))
            ),
            color = SoftLavender
        ),
        Schedule(
            id = "s4",
            title = "Overlap Test",
            startTime = FlexibleDateTime(hour = 14, minute = 30),
            endTime = FlexibleDateTime(hour = 16, minute = 0),
            description = listOf(
                DetailEntry("Note", "This should overlap with AI Principles")
            ),
            color = SoftRose
        )
    )

    val defaultTasks: List<Task> = listOf(
        Task(
            id = "t1",
            title = "Calculus Assignment",
            deadline = at(today, hour = 23, minute = 59),
            description = listOf(
                DetailEntry("Chapter", "5.1 – 5.4"),
                DetailEntry("Problems", "12, 15, 23, 28")
            ),
            reminders = listOf(
                Reminder(at(today, hour = 21, minute = 0))
            ),
            cost = 4,
            color = SoftRose
        ),
        Task(
            id = "t2",
            title = "Lab Report",
            deadline = at(tomorrow, hour = 23, minute = 59),
            description = listOf(
                DetailEntry("Subject", "Computer Network"),
                DetailEntry("Format", "PDF, max 5 pages")
            ),
            reminders = listOf(
                Reminder(at(tomorrow, hour = 20, minute = 0))
            ),
            cost = 2,
            color = SoftPeach
        ),
        Task(
            id = "t3",
            title = "Prepare Presentation",
            deadline = at(tomorrow, hour = 22, minute = 0),
            description = listOf(
                DetailEntry("Topic", "AI Ethics"),
                DetailEntry("Duration", "15 min")
            ),
            reminders = listOf(
                Reminder(at(tomorrow, hour = 18, minute = 0))
            ),
            cost = 3,
            color = SoftBlue
        )
    )
}
