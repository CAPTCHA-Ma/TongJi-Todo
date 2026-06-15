package com.example.todo

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerItemStoreTest {
    @Test
    fun wildcardScheduleAppearsOnEveryQueriedDate() {
        val schedule = Schedule(
            id = "s1",
            title = "Daily Class",
            startTime = FlexibleDateTime(hour = 8, minute = 0),
            endTime = FlexibleDateTime(hour = 9, minute = 0)
        )
        val store = PlannerItemStore.from(schedules = listOf(schedule), tasks = emptyList())

        assertEquals(listOf(schedule), store.schedulesFor(LocalDate.of(2026, 6, 8)))
        assertEquals(listOf(schedule), store.schedulesFor(LocalDate.of(2026, 6, 9)))
    }

    @Test
    fun concreteTaskAppearsOnlyOnMatchingDate() {
        val task = Task(
            id = "t1",
            title = "Report",
            deadline = FlexibleDateTime(year = 2026, month = 6, day = 8, hour = 22, minute = 0)
        )
        val store = PlannerItemStore.from(schedules = emptyList(), tasks = listOf(task))

        assertEquals(listOf(task), store.tasksFor(LocalDate.of(2026, 6, 8)))
        assertTrue(store.tasksFor(LocalDate.of(2026, 6, 9)).isEmpty())
    }

    @Test
    fun completedTaskStaysStoredButIsHiddenFromDailyTasks() {
        val task = Task(
            id = "t1",
            title = "Report",
            deadline = FlexibleDateTime(year = 2026, month = 6, day = 8, hour = 22, minute = 0)
        )
        val store = PlannerItemStore
            .from(schedules = emptyList(), tasks = listOf(task))
            .completeTask(task.id)

        assertTrue(store.tasksFor(LocalDate.of(2026, 6, 8)).isEmpty())
        assertEquals(listOf(task), store.reopenTask(task.id).tasksFor(LocalDate.of(2026, 6, 8)))
    }

    @Test
    fun allTasksIncludesTasksFromEveryDate() {
        val todayTask = Task(
            id = "t1",
            title = "Today",
            deadline = FlexibleDateTime(year = 2026, month = 6, day = 8, hour = 9, minute = 0)
        )
        val tomorrowTask = Task(
            id = "t2",
            title = "Tomorrow",
            deadline = FlexibleDateTime(year = 2026, month = 6, day = 9, hour = 9, minute = 0)
        )
        val completedTask = Task(
            id = "t3",
            title = "Done",
            deadline = FlexibleDateTime(year = 2026, month = 6, day = 10, hour = 9, minute = 0),
            isCompleted = true
        )
        val store = PlannerItemStore.from(
            schedules = emptyList(),
            tasks = listOf(tomorrowTask, completedTask, todayTask)
        )

        assertEquals(listOf(todayTask, tomorrowTask), store.activeTasks())
        assertEquals(listOf(completedTask), store.completedTasks())
    }

    @Test
    fun recommendedTasks_scoresByCostAndDeadlineProximity() {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val dayAfter = today.plusDays(2)

        val urgentEasy = Task(
            id = "te",
            title = "Urgent Easy",
            deadline = FlexibleDateTime(
                year = today.year, month = today.monthValue,
                day = today.dayOfMonth, hour = 12, minute = 0
            ),
            cost = 1
        )
        val distantHard = Task(
            id = "td",
            title = "Distant Hard",
            deadline = FlexibleDateTime(
                year = dayAfter.year, month = dayAfter.monthValue,
                day = dayAfter.dayOfMonth, hour = 12, minute = 0
            ),
            cost = 5
        )
        val tomorrowMedium = Task(
            id = "tm",
            title = "Tomorrow Medium",
            deadline = FlexibleDateTime(
                year = tomorrow.year, month = tomorrow.monthValue,
                day = tomorrow.dayOfMonth, hour = 12, minute = 0
            ),
            cost = 3
        )
        val wildcardHigh = Task(
            id = "tw",
            title = "Someday Hard",
            deadline = FlexibleDateTime(hour = 12, minute = 0),
            cost = 5
        )

        val store = PlannerItemStore.from(
            schedules = emptyList(),
            tasks = listOf(distantHard, tomorrowMedium, urgentEasy, wildcardHigh)
        )
        val recommended = store.recommendedTasks()

        // Scoring:
        //   urgentEasy:     cost=1 / (0+1) = 1.0
        //   distantHard:    cost=5 / (2+1) = 1.67
        //   tomorrowMedium: cost=3 / (1+1) = 1.5
        //   wildcardHigh:   cost=5 / (30+1) = 0.16
        // Expected order: distantHard(1.67), tomorrowMedium(1.5), urgentEasy(1.0)
        assertEquals(3, recommended.size)
        assertEquals("Distant Hard", recommended[0].title)
        assertEquals("Tomorrow Medium", recommended[1].title)
        assertEquals("Urgent Easy", recommended[2].title)
    }
}
