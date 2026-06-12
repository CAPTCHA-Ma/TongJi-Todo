package com.example.todo

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class PlannerDayItems(
    val date: LocalDate,
    val schedules: List<Schedule>,
    val tasks: List<Task>
)

data class PlannerItemStore(
    private val scheduleBuckets: Map<LocalDate, List<Schedule>> = emptyMap(),
    private val floatingSchedules: List<Schedule> = emptyList(),
    private val taskBuckets: Map<LocalDate, List<Task>> = emptyMap(),
    private val floatingTasks: List<Task> = emptyList()
) {
    fun itemsFor(date: LocalDate): PlannerDayItems =
        PlannerDayItems(
            date = date,
            schedules = schedulesFor(date),
            tasks = tasksFor(date)
        )

    fun schedulesFor(date: LocalDate): List<Schedule> =
        (scheduleBuckets[date].orEmpty() + floatingSchedules.filter { it.matchesDate(date) })
            .distinctBy { it.id }
            .sortedWith(compareBy({ it.startTime.hour ?: -1 }, { it.startTime.minute ?: -1 }, { it.title }))

    fun tasksFor(date: LocalDate): List<Task> =
        tasksMatching(date)
            .distinctBy { it.id }
            .filterNot { it.isCompleted }
            .sortedForDisplay()

    fun completedTasksFor(date: LocalDate): List<Task> =
        tasksMatching(date)
            .distinctBy { it.id }
            .filter { it.isCompleted }
            .sortedForDisplay()

    fun activeTasks(): List<Task> =
        storedTasks()
            .filterNot { it.isCompleted }
            .sortedForAllDates()

    fun completedTasks(): List<Task> =
        storedTasks()
            .filter { it.isCompleted }
            .sortedForAllDates()

    fun addSchedule(schedule: Schedule): PlannerItemStore =
        withoutSchedule(schedule.id).insertSchedule(schedule)

    fun deleteSchedule(scheduleId: String): PlannerItemStore =
        withoutSchedule(scheduleId)

    fun addTask(task: Task): PlannerItemStore =
        withoutTask(task.id).insertTask(task)

    fun deleteTask(taskId: String): PlannerItemStore =
        withoutTask(taskId)

    fun completeTask(taskId: String): PlannerItemStore =
        updateTask(taskId) { it.copy(isCompleted = true) }

    fun reopenTask(taskId: String): PlannerItemStore =
        updateTask(taskId) { it.copy(isCompleted = false) }

    fun storedSchedules(): List<Schedule> =
        (scheduleBuckets.values.flatten() + floatingSchedules)
            .distinctBy { it.id }

    fun storedTasks(): List<Task> =
        (taskBuckets.values.flatten() + floatingTasks)
            .distinctBy { it.id }

    private fun insertSchedule(schedule: Schedule): PlannerItemStore {
        val dates = schedule.storageDates()
        if (dates.isEmpty()) {
            return copy(floatingSchedules = listOf(schedule) + floatingSchedules)
        }

        val nextBuckets = scheduleBuckets.toMutableMap()
        dates.forEach { date ->
            nextBuckets[date] = listOf(schedule) + nextBuckets[date].orEmpty()
        }
        return copy(scheduleBuckets = nextBuckets)
    }

    private fun insertTask(task: Task): PlannerItemStore {
        val date = task.deadline.toConcreteDateOrNull()
        if (date == null) {
            return copy(floatingTasks = listOf(task) + floatingTasks)
        }

        val nextBuckets = taskBuckets.toMutableMap()
        nextBuckets[date] = listOf(task) + nextBuckets[date].orEmpty()
        return copy(taskBuckets = nextBuckets)
    }

    private fun withoutSchedule(scheduleId: String): PlannerItemStore =
        copy(
            scheduleBuckets = scheduleBuckets
                .mapValues { (_, schedules) -> schedules.filterNot { it.id == scheduleId } }
                .filterValues { it.isNotEmpty() },
            floatingSchedules = floatingSchedules.filterNot { it.id == scheduleId }
        )

    private fun withoutTask(taskId: String): PlannerItemStore =
        copy(
            taskBuckets = taskBuckets
                .mapValues { (_, tasks) -> tasks.filterNot { it.id == taskId } }
                .filterValues { it.isNotEmpty() },
            floatingTasks = floatingTasks.filterNot { it.id == taskId }
        )

    private fun updateTask(taskId: String, transform: (Task) -> Task): PlannerItemStore {
        val current = storedTasks().firstOrNull { it.id == taskId } ?: return this
        return withoutTask(taskId).insertTask(transform(current))
    }

    private fun tasksMatching(date: LocalDate): List<Task> =
        taskBuckets[date].orEmpty() + floatingTasks.filter { it.matchesDate(date) }

    companion object {
        fun from(
            schedules: List<Schedule>,
            tasks: List<Task>
        ): PlannerItemStore {
            var store = PlannerItemStore()
            schedules.forEach { store = store.addSchedule(it) }
            tasks.forEach { store = store.addTask(it) }
            return store
        }
    }
}

private fun List<Task>.sortedForDisplay(): List<Task> =
    sortedWith(compareBy({ it.deadline.hour ?: Int.MAX_VALUE }, { it.deadline.minute ?: Int.MAX_VALUE }, { it.title }))

private fun List<Task>.sortedForAllDates(): List<Task> =
    sortedWith(
        compareBy(
            { it.deadline.toConcreteDateOrNull() ?: LocalDate.MAX },
            { it.deadline.hour ?: Int.MAX_VALUE },
            { it.deadline.minute ?: Int.MAX_VALUE },
            { it.title }
        )
    )

fun FlexibleDateTime.matchesDate(date: LocalDate): Boolean =
    (year == null || year == date.year) &&
        (month == null || month == date.monthValue) &&
        (day == null || day == date.dayOfMonth)

fun FlexibleDateTime.toConcreteDateOrNull(): LocalDate? {
    val y = year ?: return null
    val m = month ?: return null
    val d = day ?: return null
    return runCatching { LocalDate.of(y, m, d) }.getOrNull()
}

private fun Schedule.matchesDate(date: LocalDate): Boolean {
    val startDate = startTime.toConcreteDateOrNull()
    val endDate = endTime.toConcreteDateOrNull()

    if (startDate != null && endDate != null) {
        val first = minOf(startDate, endDate)
        val last = maxOf(startDate, endDate)
        return date >= first && date <= last
    }

    return startTime.matchesDate(date) || endTime.matchesDate(date)
}

private fun Task.matchesDate(date: LocalDate): Boolean =
    deadline.matchesDate(date)

private fun Schedule.storageDates(): List<LocalDate> {
    val startDate = startTime.toConcreteDateOrNull()
    val endDate = endTime.toConcreteDateOrNull()

    if (startDate == null && endDate == null) return emptyList()
    if (startDate == null) return listOfNotNull(endDate)
    if (endDate == null) return listOf(startDate)

    val first = minOf(startDate, endDate)
    val last = maxOf(startDate, endDate)
    val days = ChronoUnit.DAYS.between(first, last)
    if (days > MaxExpandedScheduleDays) return emptyList()

    return (0..days).map { offset -> first.plusDays(offset) }
}

private const val MaxExpandedScheduleDays = 366L
