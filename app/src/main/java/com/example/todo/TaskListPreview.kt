package com.example.todo

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.todo.ui.theme.BeigeBackground
import androidx.compose.ui.res.stringResource
import java.time.LocalDate
import java.time.YearMonth

/** Which view mode is active in the task preview overlay. */
private enum class TaskViewMode {
    ListView,
    CalendarView
}

@Composable
fun TaskListPreview(
    activeTasks: List<Task>,
    completedTasks: List<Task>,
    dateLabel: String,
    contentVersion: Int,
    currentDate: LocalDate = LocalDate.now(),
    onClose: () -> Unit,
    onTaskClick: (Task) -> Unit,
    onCompleteTask: (Task) -> Unit,
    onRestoreTask: (Task) -> Unit,
    onCreateTask: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }
    val listState = rememberLazyListState()
    var isDismissing by remember { mutableStateOf(false) }
    var completedExpanded by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(TaskViewMode.ListView) }
    var previewActiveTasks by remember { mutableStateOf(activeTasks) }
    var previewCompletedTasks by remember { mutableStateOf(completedTasks) }
    var restoredTaskToRevealId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(contentVersion, activeTasks, completedTasks) {
        previewActiveTasks = activeTasks
        previewCompletedTasks = completedTasks
    }

    // ── Calendar view state ──────────────────────────────────────
    val allTasks = remember(previewActiveTasks, previewCompletedTasks) {
        (previewActiveTasks + previewCompletedTasks).distinctBy { it.id }
    }
    val tasksByDate = remember(allTasks) {
        allTasks
            .mapNotNull { task -> task.deadline.toConcreteDateOrNull()?.let { date -> date to task } }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .mapValues { (_, tasks) -> tasks.sortedForCalendar() }
    }
    val initialDate = remember(currentDate, tasksByDate) {
        val currentMonth = YearMonth.from(currentDate)
        tasksByDate.keys
            .sorted()
            .firstOrNull { YearMonth.from(it) == currentMonth }
            ?: tasksByDate.keys.minOrNull()
            ?: currentDate
    }
    var visibleMonth by remember(initialDate) { mutableStateOf(YearMonth.from(initialDate)) }
    var selectedDate by remember(initialDate) { mutableStateOf(initialDate) }
    val topBarDateLabel =
        if (viewMode == TaskViewMode.CalendarView) selectedDate.toTaskPreviewDateLabel() else dateLabel

    fun requestCompleteTask(task: Task) {
        val completedTask = task.copy(isCompleted = true)
        previewActiveTasks = previewActiveTasks.filterNot { it.id == task.id }
        previewCompletedTasks = (listOf(completedTask) + previewCompletedTasks.filterNot { it.id == task.id })
            .sortedForTaskPreview()
        onCompleteTask(task)
    }

    fun requestRestoreTask(task: Task) {
        val restoredTask = task.copy(isCompleted = false)
        previewCompletedTasks = previewCompletedTasks.filterNot { it.id == task.id }
        previewActiveTasks = (listOf(restoredTask) + previewActiveTasks.filterNot { it.id == task.id })
            .sortedForTaskPreview()
        restoredTaskToRevealId = task.id
        onRestoreTask(task)
    }

    fun requestDismiss() {
        if (!isDismissing) isDismissing = true
    }

    BackHandler(enabled = !isDismissing) {
        requestDismiss()
    }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 420f))
    }

    LaunchedEffect(isDismissing) {
        if (isDismissing) {
            progress.animateTo(0f, tween(durationMillis = 160, easing = FastOutSlowInEasing))
            onClose()
        }
    }

    LaunchedEffect(viewMode, restoredTaskToRevealId, previewActiveTasks) {
        val taskId = restoredTaskToRevealId ?: return@LaunchedEffect
        if (viewMode != TaskViewMode.ListView) return@LaunchedEffect

        val activeIndex = previewActiveTasks.indexOfFirst { it.id == taskId }
        if (activeIndex >= 0) {
            listState.animateScrollToItem(activeIndex)
            restoredTaskToRevealId = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(10f)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            }
            .graphicsLayer {
                val value = progress.value
                alpha = value
                translationY = (1f - value) * 180f
                val scale = 0.985f + value * 0.015f
                scaleX = scale
                scaleY = scale
            }
            .background(BeigeBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ── Top bar with integrated view-mode toggle ──────────
            TaskPreviewTopBar(
                dateLabel = topBarDateLabel,
                viewMode = viewMode,
                onViewModeChange = { viewMode = it },
                onCreateTask = onCreateTask,
                onClose = ::requestDismiss
            )

            if (viewMode == TaskViewMode.ListView) {
                // ── List view (existing Todo behaviour) ───────────
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 24.dp,
                        end = 24.dp,
                        top = 4.dp,
                        bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (previewActiveTasks.isEmpty()) {
                        item(key = "empty-active") {
                            TaskPreviewEmptyState(
                                text = stringResource(R.string.empty_no_active_tasks),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(84.dp)
                            )
                        }
                    }

                    items(previewActiveTasks, key = { "active-${it.id}" }) { task ->
                        TaskCard(
                            task = task,
                            onComplete = ::requestCompleteTask,
                            onClick = { onTaskClick(task) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(66.dp),
                            fillMaxHeight = true
                        )
                    }

                    item(key = "completed-toggle") {
                        CompletedTaskToggle(
                            count = previewCompletedTasks.size,
                            expanded = completedExpanded,
                            onClick = { completedExpanded = !completedExpanded },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        )
                    }

                    if (completedExpanded) {
                        if (previewCompletedTasks.isEmpty()) {
                            item(key = "completed-empty") {
                                TaskPreviewEmptyState(
                                    text = stringResource(R.string.empty_no_completed_tasks),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(72.dp)
                                )
                            }
                        } else {
                            items(previewCompletedTasks, key = { "completed-${it.id}" }) { task ->
                                TaskCard(
                                    task = task,
                                    completed = true,
                                    onComplete = ::requestCompleteTask,
                                    onRestore = ::requestRestoreTask,
                                    onClick = { onTaskClick(task) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(66.dp),
                                    fillMaxHeight = true
                                )
                            }
                        }
                    }

                    item(key = "nav-padding") {
                        Spacer(modifier = Modifier.navigationBarsPadding())
                    }
                }
            } else {
                // ── Calendar view (ported from TongJi-Todo) ───────
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp)
                ) {
                    TaskCalendarMonthHeader(
                        visibleMonth = visibleMonth,
                        onPreviousMonth = { visibleMonth = visibleMonth.minusMonths(1) },
                        onNextMonth = { visibleMonth = visibleMonth.plusMonths(1) }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    TaskCalendarGrid(
                        visibleMonth = visibleMonth,
                        selectedDate = selectedDate,
                        taskCounts = tasksByDate.mapValues { (_, tasks) -> tasks.size },
                        onDateClick = { selectedDate = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    SelectedDateTaskList(
                        selectedDate = selectedDate,
                        tasks = tasksByDate[selectedDate].orEmpty(),
                        onTaskClick = onTaskClick,
                        onCompleteTask = ::requestCompleteTask,
                        onRestoreTask = ::requestRestoreTask,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Top bar with view-mode toggle
// ─────────────────────────────────────────────────────────────────

@Composable
private fun TaskPreviewTopBar(
    dateLabel: String,
    viewMode: TaskViewMode,
    onViewModeChange: (TaskViewMode) -> Unit,
    onCreateTask: () -> Unit,
    onClose: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
    ) {
        val showToggleLabels = maxWidth >= 412.dp

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TaskPreviewIconButton(
                icon = Icons.Filled.Add,
                contentDescription = stringResource(R.string.cd_create_task),
                dark = true,
                onClick = onCreateTask
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.task_list_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 25.sp,
                        lineHeight = 29.sp
                    ),
                    fontWeight = FontWeight.Black,
                    color = Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.Black.copy(alpha = 0.48f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            TaskViewModeToggle(
                currentMode = viewMode,
                onModeChange = onViewModeChange,
                showLabels = showToggleLabels,
                modifier = Modifier.width(if (showToggleLabels) 196.dp else 96.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            TaskPreviewIconButton(
                icon = Icons.Filled.Close,
                contentDescription = stringResource(R.string.cd_close),
                onClick = onClose
            )
        }
    }
}

@Composable
private fun TaskViewModeToggle(
    currentMode: TaskViewMode,
    onModeChange: (TaskViewMode) -> Unit,
    showLabels: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .border(2.dp, Color.Black.copy(alpha = 0.32f), RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.42f), RoundedCornerShape(999.dp))
            .padding(4.dp)
    ) {
        TaskViewMode.entries.forEach { mode ->
            val selected = mode == currentMode
            val background by animateColorAsState(
                targetValue = if (selected) Color.Black else Color.Transparent,
                animationSpec = tween(180),
                label = "ViewToggleBg"
            )
            val contentColor by animateColorAsState(
                targetValue = if (selected) Color.White else Color.Black.copy(alpha = 0.72f),
                animationSpec = tween(180),
                label = "ViewToggleContent"
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(background, RoundedCornerShape(999.dp))
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = { onModeChange(mode) }
                    )
                    .padding(horizontal = if (showLabels) 6.dp else 0.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = mode.icon,
                    contentDescription = if (showLabels) null else mode.title,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                if (showLabels) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = mode.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private val TaskViewMode.icon: ImageVector
    get() = when (this) {
        TaskViewMode.ListView -> Icons.AutoMirrored.Filled.List
        TaskViewMode.CalendarView -> Icons.Filled.DateRange
    }

private val TaskViewMode.title: String
    get() = when (this) {
        TaskViewMode.ListView -> "List"
        TaskViewMode.CalendarView -> "Calendar"
    }

private fun LocalDate.toTaskPreviewDateLabel(): String =
    "$year-${monthValue.toString().padStart(2, '0')}-${dayOfMonth.toString().padStart(2, '0')}"

@Composable
private fun TaskPreviewIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    onClick: () -> Unit
) {
    val background = if (dark) Color.Black else Color.White.copy(alpha = 0.46f)
    val border = if (dark) Color.Black else Color.Black.copy(alpha = 0.32f)
    val content = if (dark) Color.White else Color.Black

    Box(
        modifier = modifier
            .size(48.dp)
            .border(2.dp, border, RoundedCornerShape(999.dp))
            .background(background, RoundedCornerShape(999.dp))
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = content,
            modifier = Modifier.size(23.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────
// List-view components (existing Todo code, unchanged)
// ─────────────────────────────────────────────────────────────────

@Composable
private fun CompletedTaskToggle(
    count: Int,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .border(2.dp, Color.Black.copy(alpha = 0.22f), RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.38f), RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.label_completed),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            color = Color.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = Color.Black.copy(alpha = 0.52f),
            textAlign = TextAlign.End,
            modifier = Modifier.width(36.dp),
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun TaskPreviewEmptyState(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .border(2.dp, Color.Black.copy(alpha = 0.14f), RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.28f), RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = Color.Black.copy(alpha = 0.42f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 18.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────
// Calendar-view components (ported from TongJi-Todo, Todo-styled)
// ─────────────────────────────────────────────────────────────────

@Composable
private fun TaskCalendarMonthHeader(
    visibleMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val monthTitle = remember(visibleMonth) {
        val names = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        "${names.getOrElse(visibleMonth.monthValue - 1) { visibleMonth.monthValue.toString() }} ${visibleMonth.year}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .border(2.dp, Color.Black.copy(alpha = 0.24f), RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.44f), RoundedCornerShape(24.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CalendarNavButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = "Previous month",
            onClick = onPreviousMonth
        )
        Text(
            text = monthTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = Color.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        CalendarNavButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Next month",
            onClick = onNextMonth
        )
    }
}

@Composable
private fun CalendarNavButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .border(2.dp, Color.Black.copy(alpha = 0.32f), RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.46f), RoundedCornerShape(999.dp))
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.Black,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun TaskCalendarGrid(
    visibleMonth: YearMonth,
    selectedDate: LocalDate,
    taskCounts: Map<LocalDate, Int>,
    onDateClick: (LocalDate) -> Unit
) {
    val calendarCells = remember(visibleMonth) { visibleMonth.calendarCells() }
    val weekdays = remember { listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, Color.Black.copy(alpha = 0.2f), RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.28f), RoundedCornerShape(28.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            weekdays.forEach { weekday ->
                Text(
                    text = weekday,
                    color = Color.Black.copy(alpha = 0.46f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        calendarCells.chunked(7).forEach { week ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        TaskCalendarDayCell(
                            date = date,
                            selected = date == selectedDate,
                            taskCount = taskCounts[date] ?: 0,
                            onClick = { onDateClick(date) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun TaskCalendarDayCell(
    date: LocalDate,
    selected: Boolean,
    taskCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasTasks = taskCount > 0
    val background = when {
        selected -> Color.Black
        hasTasks -> Color.White.copy(alpha = 0.9f)
        else -> Color.Black.copy(alpha = 0.09f)
    }
    val border = when {
        selected -> Color.Black
        hasTasks -> Color.Black.copy(alpha = 0.3f)
        else -> Color.Black.copy(alpha = 0.08f)
    }
    val content = when {
        selected -> Color.White
        hasTasks -> Color.Black
        else -> Color.Black.copy(alpha = 0.32f)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .border(1.dp, border, RoundedCornerShape(15.dp))
            .background(background, RoundedCornerShape(15.dp))
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            color = content,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        if (hasTasks) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 5.dp)
                    .size(4.dp)
                    .background(content.copy(alpha = 0.86f), RoundedCornerShape(999.dp))
            )
        }
    }
}

@Composable
private fun SelectedDateTaskList(
    selectedDate: LocalDate,
    tasks: List<Task>,
    onTaskClick: (Task) -> Unit,
    onCompleteTask: (Task) -> Unit,
    onRestoreTask: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTasks = remember(tasks) { tasks.filterNot { it.isCompleted }.sortedForCalendar() }
    val completedTasks = remember(tasks) { tasks.filter { it.isCompleted }.sortedForCalendar() }
    val dateTitle = remember(selectedDate) { selectedDate.toTaskPreviewDateLabel() }
    val taskCountText = remember(tasks.size) {
        if (tasks.size == 1) "1 task" else "${tasks.size} tasks"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(tween(220, easing = FastOutSlowInEasing))
    ) {
        Text(
            text = dateTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = Color.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = taskCountText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            color = Color.Black.copy(alpha = 0.5f),
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (tasks.isEmpty()) {
                item(key = "empty") {
                    CalendarEmptyState(
                        text = "No tasks on this date",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(78.dp)
                    )
                }
            } else {
                if (activeTasks.isNotEmpty()) {
                    item(key = "active-title") {
                        CalendarListTitle("Active")
                    }
                    items(activeTasks, key = { "cal-active-${it.id}" }) { task ->
                        TaskCard(
                            task = task,
                            onComplete = onCompleteTask,
                            onClick = { onTaskClick(task) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(66.dp),
                            fillMaxHeight = true
                        )
                    }
                }

                if (completedTasks.isNotEmpty()) {
                    item(key = "completed-title") {
                        CalendarListTitle("Completed")
                    }
                    items(completedTasks, key = { "cal-completed-${it.id}" }) { task ->
                        TaskCard(
                            task = task,
                            completed = true,
                            onComplete = onCompleteTask,
                            onRestore = onRestoreTask,
                            onClick = { onTaskClick(task) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(66.dp),
                            fillMaxHeight = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarListTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Black,
        color = Color.Black.copy(alpha = 0.5f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 2.dp)
    )
}

@Composable
private fun CalendarEmptyState(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .border(2.dp, Color.Black.copy(alpha = 0.14f), RoundedCornerShape(28.dp))
            .background(Color.Black.copy(alpha = 0.06f), RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = Color.Black.copy(alpha = 0.42f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 18.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────
// Calendar helpers
// ─────────────────────────────────────────────────────────────────

private fun YearMonth.calendarCells(): List<LocalDate?> {
    val firstDay = atDay(1)
    val leadingBlankCount = firstDay.dayOfWeek.value - 1
    val cells = MutableList<LocalDate?>(leadingBlankCount) { null }
    (1..lengthOfMonth()).forEach { day -> cells += atDay(day) }
    while (cells.size % 7 != 0) cells += null
    return cells
}

private fun List<Task>.sortedForCalendar(): List<Task> =
    sortedWith(
        compareBy(
            { it.deadline.hour ?: Int.MAX_VALUE },
            { it.deadline.minute ?: Int.MAX_VALUE },
            { it.title }
        )
    )

private fun List<Task>.sortedForTaskPreview(): List<Task> =
    distinctBy { it.id }
        .sortedWith(
            compareBy(
                { it.deadline.toConcreteDateOrNull() ?: LocalDate.MAX },
                { it.deadline.hour ?: Int.MAX_VALUE },
                { it.deadline.minute ?: Int.MAX_VALUE },
                { it.title }
            )
        )
