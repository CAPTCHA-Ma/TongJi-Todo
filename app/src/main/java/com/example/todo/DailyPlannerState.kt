package com.example.todo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate

/**
 * State holder for the daily-planner screen.
 *
 * Encapsulates all mutable state and date-navigation logic so the screen
 * composable only deals with rendering.  The planner covers a three-day
 * window: today (offset 0), tomorrow (1), and the day after tomorrow (2).
 */
class DailyPlannerState(
    private val persistence: PlannerPersistence? = null,
    private val reminderScheduler: PlannerReminderScheduler? = null
) {

    // ── schedule & task data ──────────────────────────────────────

    private val baseDate: LocalDate = LocalDate.now()
    private var itemStore by mutableStateOf(
        persistence?.load() ?: PlannerItemStore.from(
            schedules = SampleData.defaultSchedules,
            tasks = SampleData.defaultTasks
        )
    )
    private var storeVersion by mutableIntStateOf(0)

    init {
        reminderScheduler?.sync(itemStore)
    }

    val contentVersion: Int get() = storeVersion

    val currentDate: LocalDate get() = baseDate.plusDays(currentDayOffset.toLong())
    val currentDayItems: PlannerDayItems get() = itemStore.itemsFor(currentDate)
    val schedules: List<Schedule> get() = currentDayItems.schedules
    val tasks: List<Task> get() = currentDayItems.tasks
    val completedTasks: List<Task> get() = itemStore.completedTasksFor(currentDate)
    val allTasks: List<Task> get() = itemStore.activeTasks()
    val allCompletedTasks: List<Task> get() = itemStore.completedTasks()

    /** Top 3 recommended tasks from all active tasks, ranked by cost & deadline proximity. */
    val recommendedTasks: List<Task> get() = itemStore.recommendedTasks()

    fun schedulesFor(date: LocalDate): List<Schedule> =
        itemStore.schedulesFor(date)

    // ── UI selection ──────────────────────────────────────────────

    /** Currently selected item shown in the detail overlay (null = hidden). */
    var selectedItem by mutableStateOf<Any?>(null)

    /** Whether the full-day schedule preview screen is open. */
    var isFullDayPreviewOpen by mutableStateOf(false)

    /** Whether the full task list preview screen is open. */
    var isTaskPreviewOpen by mutableStateOf(false)

    /** Whether the reusable full-screen create screen is open. */
    var isCreateItemOpen by mutableStateOf(false)

    /** The type initially selected when the create screen opens. */
    var createItemType by mutableStateOf(CreateItemType.Schedule)
        private set

    // ── day navigation ────────────────────────────────────────────

    /** 0 = today, 1 = tomorrow, 2 = day after tomorrow */
    var currentDayOffset by mutableIntStateOf(0)
        private set

    val canGoToPreviousDay: Boolean get() = currentDayOffset > 0
    val canGoToNextDay: Boolean get() = currentDayOffset < 2

    fun goToPreviousDay() {
        if (canGoToPreviousDay) currentDayOffset--
    }

    fun goToNextDay() {
        if (canGoToNextDay) currentDayOffset++
    }

    // ── date labels ───────────────────────────────────────────────

    /** Pre-computed formatted date labels for the three-day window. */
    val dateLabels: List<String> = run {
        val months = arrayOf(
            "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
            "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"
        )
        (0..2).map { offset ->
            val date = baseDate.plusDays(offset.toLong())
            months[date.monthValue - 1] + date.dayOfMonth.toString().padStart(2, '0')
        }
    }

    /** The date label for the currently selected day offset. */
    val currentDateLabel: String get() = dateLabels[currentDayOffset]

    // ── actions ───────────────────────────────────────────────────

    fun completeTask(task: Task) {
        updateStore(itemStore.completeTask(task.id))
    }

    fun reopenTask(task: Task) {
        updateStore(itemStore.reopenTask(task.id))
    }

    fun selectItem(item: Any?) {
        selectedItem = item
    }

    fun clearSelection() {
        selectedItem = null
    }

    fun openCreateItem(type: CreateItemType = CreateItemType.Schedule) {
        createItemType = type
        isCreateItemOpen = true
    }

    fun closeCreateItem() {
        isCreateItemOpen = false
    }

    fun finishCreateItem() {
        isCreateItemOpen = false
        selectedItem = null
    }

    fun addSchedule(schedule: Schedule) {
        updateStore(itemStore.addSchedule(schedule))
    }

    fun addTask(task: Task) {
        updateStore(itemStore.addTask(task))
    }

    fun deleteSchedule(schedule: Schedule) {
        updateStore(itemStore.deleteSchedule(schedule.id))
    }

    fun deleteTask(task: Task) {
        updateStore(itemStore.deleteTask(task.id))
    }

    fun deleteItem(item: Any) {
        when (item) {
            is Schedule -> deleteSchedule(item)
            is Task -> deleteTask(item)
        }
    }

    private fun updateStore(nextStore: PlannerItemStore) {
        itemStore = nextStore
        storeVersion++
        persistence?.save(nextStore)
        reminderScheduler?.sync(nextStore)
    }
}
