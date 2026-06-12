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
}
