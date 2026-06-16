package com.example.todo

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.todo.ui.theme.*
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Multi-day planner screen that displays schedules and recommended tasks
 * across a three-day window: today, tomorrow, and the day after tomorrow.
 *
 * Each section (Schedule / Tasks) shows at most three items.  When fewer
 * than three items exist the cards are proportionally taller so the section
 * always occupies the same visual footprint.
 *
 * The user can swipe horizontally on the date header to navigate between days.
 * Swiping up anywhere on the schedule section opens the full-day timeline overlay.
 * Tapping a schedule or task card opens a full-screen detail overlay.
// * Tasks can be swiped right-to-left (start-to-end) to mark them as completed.
 */
@Composable
fun DailyPlannerScreen() {
    val appContext = LocalContext.current.applicationContext
    val isPreview = LocalInspectionMode.current
    val state = remember(appContext, isPreview) {
        DailyPlannerState(
            persistence = PlannerPersistence(appContext),
            reminderScheduler = if (isPreview) null else PlannerReminderScheduler(appContext)
        )
    }
    var isFabMenuOpen by remember { mutableStateOf(false) }
    var isCanvasSyncOpen by remember { mutableStateOf(false) }
    // ── adaptive card sizing ────────────────────────────────────────

    /** Returns the per-card height so that [count] cards plus their spacers
     *  fill the same height as three standard cards would.
     *  Returns null when count is 0 or ≥3 — in both cases no expansion is needed. */

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // ── main content ──────────────────────────────────────────
        val isOverlayOpen = state.selectedItem != null ||
            state.isFullDayPreviewOpen ||
            state.isTaskPreviewOpen ||
            state.isCreateItemOpen ||
            isCanvasSyncOpen ||
            state.isTongjiImportOpen ||
            state.isTongjiExamImportOpen
        val overlayBlurRadius by animateDpAsState(
            targetValue = if (isOverlayOpen || isFabMenuOpen) 12.dp else 0.dp,
            animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
            label = "OverlayBlurRadius"
        )
        val fabMenuTintAlpha by animateFloatAsState(
            targetValue = if (isFabMenuOpen && !isOverlayOpen) 0.28f else 0f,
            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
            label = "FabMenuTintAlpha"
        )
        val fabMenuDepthAlpha by animateFloatAsState(
            targetValue = if (isFabMenuOpen && !isOverlayOpen) 0.045f else 0f,
            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
            label = "FabMenuDepthAlpha"
        )
        val overlayTintAlpha by animateFloatAsState(
            targetValue = if (isOverlayOpen) 0.34f else 0f,
            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
            label = "OverlayTintAlpha"
        )
        val overlayDepthAlpha by animateFloatAsState(
            targetValue = if (isOverlayOpen) 0.035f else 0f,
            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
            label = "OverlayDepthAlpha"
        )
        val fullDayDetailBlurRadius by animateDpAsState(
            targetValue = if (state.isFullDayPreviewOpen && state.selectedItem != null) 12.dp else 0.dp,
            animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
            label = "FullDayDetailBlurRadius"
        )
        val fullDayDetailTintAlpha by animateFloatAsState(
            targetValue = if (state.isFullDayPreviewOpen && state.selectedItem != null) 0.34f else 0f,
            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
            label = "FullDayDetailTintAlpha"
        )
        val fullDayDetailDepthAlpha by animateFloatAsState(
            targetValue = if (state.isFullDayPreviewOpen && state.selectedItem != null) 0.035f else 0f,
            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
            label = "FullDayDetailDepthAlpha"
        )
        val taskDetailBlurRadius by animateDpAsState(
            targetValue = if (state.isTaskPreviewOpen && state.selectedItem != null) 12.dp else 0.dp,
            animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
            label = "TaskDetailBlurRadius"
        )
        val taskDetailTintAlpha by animateFloatAsState(
            targetValue = if (state.isTaskPreviewOpen && state.selectedItem != null) 0.34f else 0f,
            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
            label = "TaskDetailTintAlpha"
        )
        val taskDetailDepthAlpha by animateFloatAsState(
            targetValue = if (state.isTaskPreviewOpen && state.selectedItem != null) 0.035f else 0f,
            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
            label = "TaskDetailDepthAlpha"
        )
        val plannerCardSpacing = 8.dp
        val plannerCardHeight = ((maxHeight - 388.dp - plannerCardSpacing * 4f) / 6f)
            .coerceIn(66.dp, 72.dp)
        BackHandler(enabled = isFabMenuOpen && !isOverlayOpen) {
            isFabMenuOpen = false
        }
        LaunchedEffect(isOverlayOpen) {
            if (isOverlayOpen) isFabMenuOpen = false
        }
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BeigeBackground)
                    .blur(
                        radius = overlayBlurRadius,
                        edgeTreatment = BlurredEdgeTreatment.Rectangle
                    )
            ) {
            Spacer(modifier = Modifier.height(44.dp))

            // Date header uses the full screen width so the navigation
            // triangles can reach the true viewport edges.
            SwipeableDateHeader(
                dateText = state.currentDateLabel,
                canSwipeLeft = state.canGoToPreviousDay,
                canSwipeRight = state.canGoToNextDay,
                onSwipeLeft = state::goToPreviousDay,
                onSwipeRight = state::goToNextDay
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Schedule section ──────────────────────────────────
            // The entire schedule area (header + cards) responds to swipe-up.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount < -20) {
                                state.isFullDayPreviewOpen = true
                            }
                        }
                    }
            ) {
                SectionTitle(stringResource(R.string.schedule_section))
                Spacer(modifier = Modifier.height(8.dp))

                PlannerCardStack(
                    items = state.schedules,
                    itemKey = { it.id },
                    modifier = Modifier.fillMaxWidth(),
                    cardHeight = plannerCardHeight,
                    cardSpacing = plannerCardSpacing
                ) { schedule, cardModifier ->
                    ScheduleCard(
                        schedule = schedule,
                        onClick = { state.selectItem(schedule) },
                        modifier = cardModifier,
                        fillMaxHeight = true
                    )
                }
            }

            SwipeUpHint()

            // ── Tasks section ─────────────────────────────────────
            Column(
                modifier = Modifier
                    .padding(horizontal = 28.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount < -20) {
                                state.isTaskPreviewOpen = true
                            }
                        }
                    }
            ) {
                SectionTitle(stringResource(R.string.task_section))
                Spacer(modifier = Modifier.height(8.dp))

                PlannerCardStack(
                    items = state.recommendedTasks,
                    itemKey = { it.id },
                    modifier = Modifier.fillMaxWidth(),
                    cardHeight = plannerCardHeight,
                    cardSpacing = plannerCardSpacing
                ) { task, cardModifier ->
                    TaskCard(
                        task = task,
                        onComplete = { completedTask ->
                            state.completeTask(completedTask)
                        },
                        onClick = { state.selectItem(task) },
                        modifier = cardModifier,
                        fillMaxHeight = true
                    )
                }
            }

            SwipeUpHint()
            Spacer(modifier = Modifier.height(28.dp))
        }

            // White scrim — lifts brightness of the blurred content
            // so large dark text doesn't bleed through as muddy smudges.
            if (fabMenuTintAlpha > 0.01f || fabMenuDepthAlpha > 0.005f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFFFFBF0).copy(alpha = fabMenuTintAlpha))
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            onClick = { isFabMenuOpen = false }
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = fabMenuDepthAlpha))
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            onClick = { isFabMenuOpen = false }
                        )
                )
            }

            FabQuickActionMenu(
                visible = isFabMenuOpen && !isOverlayOpen,
                onLanguageClick = {},
                onTimetableClick = {
                    isFabMenuOpen = false
                    state.openTongjiImport()
                },
                onExamsClick = {
                    isFabMenuOpen = false
                    state.openTongjiExamImport()
                },
                onCanvasClick = {
                    isFabMenuOpen = false
                    isCanvasSyncOpen = true
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 86.dp)
                    .zIndex(4f)
            )

            AddItemFab(
                vibrationContext = appContext,
                menuOpen = isFabMenuOpen,
                onClick = {
                    if (isFabMenuOpen) {
                        isFabMenuOpen = false
                    } else {
                        state.openCreateItem(CreateItemType.Schedule)
                    }
                },
                onLongPress = {
                    if (!isOverlayOpen) isFabMenuOpen = true
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
                    .zIndex(5f)
            )

            if (overlayTintAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFFFFBF0).copy(alpha = overlayTintAlpha))
                )
            }

            if (overlayDepthAlpha > 0.005f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = overlayDepthAlpha))
                )
            }

        }

        // ── full-day preview overlay ──────────────────────────────
        if (state.isFullDayPreviewOpen) {
            FullDaySchedulePreview(
                selectedDate = state.currentDate,
                schedulesForDate = state::schedulesFor,
                onClose = { state.isFullDayPreviewOpen = false },
                onScheduleClick = { schedule -> state.selectItem(schedule) },
                contentVersion = state.contentVersion,
                modifier = Modifier.blur(
                    radius = fullDayDetailBlurRadius,
                    edgeTreatment = BlurredEdgeTreatment.Rectangle
                )
            )
        }

        if (state.isFullDayPreviewOpen && fullDayDetailTintAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(15f)
                    .background(Color(0xFFFFFBF0).copy(alpha = fullDayDetailTintAlpha))
            )
        }

        if (state.isFullDayPreviewOpen && fullDayDetailDepthAlpha > 0.005f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(15f)
                    .background(Color.Black.copy(alpha = fullDayDetailDepthAlpha))
            )
        }

        if (state.isTaskPreviewOpen) {
            TaskListPreview(
                activeTasks = state.allTasks,
                completedTasks = state.allCompletedTasks,
                dateLabel = stringResource(R.string.all_dates),
                currentDate = state.currentDate,
                onClose = { state.isTaskPreviewOpen = false },
                onTaskClick = { task -> state.selectItem(task) },
                onCompleteTask = { task -> state.completeTask(task) },
                onRestoreTask = { task -> state.reopenTask(task) },
                onCreateTask = { state.openCreateItem(CreateItemType.Task) },
                modifier = Modifier.blur(
                    radius = taskDetailBlurRadius,
                    edgeTreatment = BlurredEdgeTreatment.Rectangle
                )
            )
        }

        if (state.isTaskPreviewOpen && taskDetailTintAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(15f)
                    .background(Color(0xFFFFFBF0).copy(alpha = taskDetailTintAlpha))
            )
        }

        if (state.isTaskPreviewOpen && taskDetailDepthAlpha > 0.005f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(15f)
                    .background(Color.Black.copy(alpha = taskDetailDepthAlpha))
            )
        }

        AnimatedVisibility(
            visible = state.isCreateItemOpen,
            enter = slideInVertically(
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                initialOffsetY = { it }
            ) + fadeIn(tween(durationMillis = 120)),
            exit = slideOutVertically(
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                targetOffsetY = { it }
            ) + fadeOut(tween(durationMillis = 90)),
            modifier = Modifier.zIndex(30f)
        ) {
            CreateItemScreen(
                initialType = state.createItemType,
                initialDate = state.currentDate,
                onClose = { state.closeCreateItem() },
                onCreateSchedule = { schedule ->
                    state.addSchedule(schedule)
                    state.finishCreateItem()
                },
                onCreateTask = { task ->
                    state.addTask(task)
                    state.finishCreateItem()
                }
            )
        }

        AnimatedVisibility(
            visible = state.isTongjiImportOpen,
            enter = slideInVertically(
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                initialOffsetY = { it }
            ) + fadeIn(tween(durationMillis = 120)),
            exit = slideOutVertically(
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                targetOffsetY = { it }
            ) + fadeOut(tween(durationMillis = 90)),
            modifier = Modifier.zIndex(40f)
        ) {
            TongjiImportWebViewScreen(
                onClose = state::closeTongjiImport,
                onImportSchedules = state::importTongjiCourseSchedules
            )
        }

        AnimatedVisibility(
            visible = state.isTongjiExamImportOpen,
            enter = slideInVertically(
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                initialOffsetY = { it }
            ) + fadeIn(tween(durationMillis = 120)),
            exit = slideOutVertically(
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                targetOffsetY = { it }
            ) + fadeOut(tween(durationMillis = 90)),
            modifier = Modifier.zIndex(39f)
        ) {
            TongjiExamImportWebViewScreen(
                onClose = state::closeTongjiExamImport,
                onImportTasks = state::importTongjiExamTasks
            )
        }

        AnimatedVisibility(
            visible = isCanvasSyncOpen,
            enter = slideInVertically(
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                initialOffsetY = { it }
            ) + fadeIn(tween(durationMillis = 120)),
            exit = slideOutVertically(
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                targetOffsetY = { it }
            ) + fadeOut(tween(durationMillis = 90)),
            modifier = Modifier.zIndex(38f)
        ) {
            CanvasSyncOverlay(
                currentStore = state.currentStoreSnapshot(),
                onSyncCompleted = { nextStore -> state.applyCanvasSyncedStore(nextStore) },
                onClose = { isCanvasSyncOpen = false }
            )
        }

        // ── detail overlay ────────────────────────────────────────
        // AnimatedVisibility uses EnterTransition.None / ExitTransition.None
        // so DetailPreview can handle its own enter/exit animations via Animatable.
        AnimatedVisibility(
            visible = state.selectedItem != null,
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            modifier = Modifier.zIndex(20f)
        ) {
            val item = state.selectedItem
            if (item != null) {
                DetailPreview(
                    item = item,
                    onClose = { state.clearSelection() },
                    onDelete = { deletedItem -> state.deleteItem(deletedItem) }
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        color = Color.Black,
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun FabQuickActionMenu(
    visible: Boolean,
    onLanguageClick: () -> Unit,
    onTimetableClick: () -> Unit,
    onExamsClick: () -> Unit,
    onCanvasClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
            initialOffsetY = { it / 4 }
        ) + fadeIn(tween(durationMillis = 120)),
        exit = slideOutVertically(
            animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
            targetOffsetY = { it / 4 }
        ) + fadeOut(tween(durationMillis = 90)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.width(188.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FabQuickActionButton(
                label = stringResource(R.string.home_menu_language),
                icon = Icons.Filled.Language,
                onClick = onLanguageClick
            )
            FabQuickActionButton(
                label = stringResource(R.string.home_menu_timetable),
                icon = Icons.Filled.Event,
                onClick = onTimetableClick
            )
            FabQuickActionButton(
                label = stringResource(R.string.home_menu_exams),
                icon = Icons.Filled.Event,
                onClick = onExamsClick
            )
            FabQuickActionButton(
                label = stringResource(R.string.home_menu_canvas),
                icon = Icons.Filled.CloudDownload,
                onClick = onCanvasClick
            )
        }
    }
}

@Composable
private fun FabQuickActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .border(2.dp, Color.Black.copy(alpha = 0.22f), RoundedCornerShape(25.dp))
            .background(Color.Black, RoundedCornerShape(25.dp))
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick
            )
            .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(Color.White, RoundedCornerShape(999.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AddItemFab(
    vibrationContext: Context,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    menuOpen: Boolean = false
) {
    val latestContext by rememberUpdatedState(vibrationContext)
    val latestOnClick by rememberUpdatedState(onClick)
    val latestOnLongPress by rememberUpdatedState(onLongPress)
    val scope = rememberCoroutineScope()
    var vibrationJob by remember { mutableStateOf<Job?>(null) }
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    val iconRotation by animateFloatAsState(
        targetValue = if (menuOpen) 45f else 0f,
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "AddFabIconRotation"
    )

    DisposableEffect(Unit) {
        onDispose {
            vibrationJob?.cancel()
            longPressJob?.cancel()
        }
    }

    Box(
        modifier = modifier
            .size(62.dp)
            .background(Color.Black, shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var longPressTriggered = false
                    vibrationJob?.cancel()
                    longPressJob?.cancel()
                    vibrationJob = scope.launch {
                        playFabLongPressRampVibration(latestContext)
                    }
                    longPressJob = scope.launch {
                        delay(FabLongPressDelayMs)
                        longPressTriggered = true
                        vibrationJob?.cancel()
                        vibrationJob = null
                        playFabLongPressTriggerVibration(latestContext)
                        latestOnLongPress()
                    }

                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                    } while (event.changes.any { it.pressed })

                    vibrationJob?.cancel()
                    vibrationJob = null
                    longPressJob?.cancel()
                    longPressJob = null

                    if (!longPressTriggered) {
                        latestOnClick()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = stringResource(R.string.add_item),
            tint = Color.White,
            modifier = Modifier
                .size(30.dp)
                .graphicsLayer { rotationZ = iconRotation }
        )
    }
}

private const val FabLongPressDelayMs = 720L

private suspend fun playFabLongPressRampVibration(context: Context) {
    val pulses = listOf(
        105L to 28,
        105L to 46,
        105L to 68,
        105L to 94,
        105L to 124,
        105L to 158
    )
    pulses.forEach { (delayMs, amplitude) ->
        delay(delayMs)
        vibrateOneShot(context, durationMs = 10L, amplitude = amplitude)
    }
}

private fun playFabLongPressTriggerVibration(context: Context) {
    vibrateOneShot(context, durationMs = 34L, amplitude = 255)
}

@Suppress("DEPRECATION")
private fun vibrateOneShot(
    context: Context,
    durationMs: Long,
    amplitude: Int
) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return

    if (!vibrator.hasVibrator()) return

    vibrator.vibrate(
        VibrationEffect.createOneShot(
            durationMs,
            amplitude.coerceIn(1, 255)
        )
    )
}

@Composable
private fun CanvasSyncOverlay(
    currentStore: PlannerItemStore,
    onSyncCompleted: (PlannerItemStore) -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BeigeBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .border(2.dp, Color.Black.copy(alpha = 0.32f), RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.46f), RoundedCornerShape(999.dp))
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = onClose
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_close),
                    tint = Color.Black,
                    modifier = Modifier.size(23.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Canvas Sync",
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
                    text = "TOKEN AND ASSIGNMENTS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.Black.copy(alpha = 0.48f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = if (currentStore.activeTasks().any { it.isCanvasAssignmentTask() }) "SYNCED" else "READY",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier
                    .background(Color.Black, RoundedCornerShape(999.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }

        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 20.dp, top = 4.dp, end = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            CanvasSyncSettingsPanel(
                currentStore = currentStore,
                onSyncCompleted = onSyncCompleted
            )
        }
    }
}

@Composable
private fun SwipeUpHint(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 32.dp
) {
    val transition = rememberInfiniteTransition(label = "SwipeUpHint")
    val yOffset by transition.animateFloat(
        initialValue = 4f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SwipeUpHintOffset"
    )
    val hintAlpha by transition.animateFloat(
        initialValue = 0.48f,
        targetValue = 0.86f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SwipeUpHintAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.KeyboardArrowUp,
            contentDescription = null,
            tint = Color.Black.copy(alpha = 0.68f),
            modifier = Modifier
                .size(28.dp)
                .graphicsLayer {
                    translationY = yOffset
                    alpha = hintAlpha
                }
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F5F0)
@Composable
fun DailyPlannerScreenPreview() {
    TodoTheme {
        DailyPlannerScreen()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F5F0)
@Composable
fun DetailPreviewPreview() {
    TodoTheme {
        DetailPreview(
            item = SampleData.defaultTasks.first(),
            onClose = {}
        )
    }
}
