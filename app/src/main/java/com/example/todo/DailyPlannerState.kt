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
    private var itemStore by mutableStateOf(loadInitialStore())
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

    /** Whether the Tongji timetable import WebView flow is open. */
    var isTongjiImportOpen by mutableStateOf(false)

    /** Whether the Tongji exam import WebView flow is open. */
    var isTongjiExamImportOpen by mutableStateOf(false)

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

    fun openTongjiImport() {
        isTongjiImportOpen = true
    }

    fun closeTongjiImport() {
        isTongjiImportOpen = false
    }

    fun openTongjiExamImport() {
        isTongjiExamImportOpen = true
    }

    fun closeTongjiExamImport() {
        isTongjiExamImportOpen = false
    }

    fun importTongjiCourseSchedules(schedules: List<Schedule>) {
        if (schedules.isNotEmpty()) {
            updateStore(itemStore.replaceTongjiCourseSchedules(schedules))
        }
    }

    fun importTongjiExamTasks(tasks: List<Task>) {
        if (tasks.isNotEmpty()) {
            updateStore(itemStore.replaceTongjiExamTasks(tasks))
        }
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

    fun currentStoreSnapshot(): PlannerItemStore = itemStore

    fun applyCanvasSyncedStore(nextStore: PlannerItemStore) {
        updateStore(nextStore)
    }

    private fun updateStore(nextStore: PlannerItemStore) {
        itemStore = nextStore
        storeVersion++
        persistence?.save(nextStore)
        reminderScheduler?.sync(nextStore)
    }

    private fun loadInitialStore(): PlannerItemStore {
        val loadedStore = persistence?.load()
        val store = loadedStore ?: PlannerItemStore.from(
            schedules = SampleData.defaultSchedules,
            tasks = SampleData.defaultTasks
        )
        val cleanedStore = store
            .deleteSchedules(SampleData.deprecatedDefaultScheduleIds)
            .deleteTasks(SampleData.deprecatedDefaultTaskIds)

        if (loadedStore != null && cleanedStore != loadedStore) {
            persistence?.save(cleanedStore)
        }

        return cleanedStore
    }
}
