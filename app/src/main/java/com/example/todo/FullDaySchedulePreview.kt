package com.example.todo

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.todo.ui.theme.BeigeBackground
import com.example.todo.ui.theme.TodoTheme
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class SchedulePeriod(val label: String) {
    Day("Day"),
    Week("Week"),
    Month("Month")
}

private enum class ScheduleDateField(val label: String) {
    Year("Year"),
    Month("Month"),
    Day("Day")
}

private class ScheduleSwipeAnimationJobs {
    var rubberBackJob: Job? = null
    var switchJob: Job? = null
}

@Composable
fun FullDaySchedulePreview(
    selectedDate: LocalDate,
    schedulesForDate: (LocalDate) -> List<Schedule>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onScheduleClick: (Schedule) -> Unit = {},
    contentVersion: Int = 0
) {
    val progress = remember { Animatable(0f) }
    var isDismissing by remember { mutableStateOf(false) }
    var period by rememberSaveable { mutableStateOf(SchedulePeriod.Day) }
    var selectedEpochDay by rememberSaveable(selectedDate) {
        mutableLongStateOf(selectedDate.toEpochDay())
    }
    var isDatePickerOpen by remember { mutableStateOf(false) }
    var isPeriodMenuOpen by remember { mutableStateOf(false) }
    val currentDate = remember(selectedEpochDay) { LocalDate.ofEpochDay(selectedEpochDay) }

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(10f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value }
                .background(Color.Black.copy(alpha = 0.1f))
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = ::requestDismiss
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val sheetProgress = progress.value
                    alpha = sheetProgress
                    translationY = (1f - sheetProgress) * 180f
                    val scale = 0.985f + sheetProgress * 0.015f
                    scaleX = scale
                    scaleY = scale
                }
                .background(BeigeBackground)
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                ScheduleTopBar(
                    selectedPeriod = period,
                    selectedDate = currentDate,
                    periodMenuExpanded = isPeriodMenuOpen,
                    onPeriodMenuExpandedChange = { isPeriodMenuOpen = it },
                    onDateClick = {
                        isPeriodMenuOpen = false
                        isDatePickerOpen = true
                    },
                    onClose = ::requestDismiss
                )

                AnimatedContent(
                    targetState = period,
                    transitionSpec = {
                        fadeIn(tween(180)) togetherWith fadeOut(tween(120)) using
                            SizeTransform(clip = false)
                    },
                    label = "SchedulePeriodContent",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 6.dp)
                ) { activePeriod ->
                    when (activePeriod) {
                        SchedulePeriod.Day -> {
                            SwipeableDayScheduleView(
                                selectedDate = currentDate,
                                schedulesForDate = schedulesForDate,
                                onDateChange = { selectedEpochDay = it.toEpochDay() },
                                onScheduleClick = onScheduleClick,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        SchedulePeriod.Week -> {
                            WeekScheduleView(
                                selectedDate = currentDate,
                                schedulesForDate = schedulesForDate,
                                onDateChange = { selectedEpochDay = it.toEpochDay() },
                                onScheduleClick = onScheduleClick,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        SchedulePeriod.Month -> {
                            MonthScheduleView(
                                selectedDate = currentDate,
                                schedulesForDate = schedulesForDate,
                                contentVersion = contentVersion,
                                onDateChange = { selectedEpochDay = it.toEpochDay() },
                                onScheduleClick = onScheduleClick,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            PeriodDropdownOverlay(
                visible = isPeriodMenuOpen,
                selectedPeriod = period,
                onPeriodChange = {
                    period = it
                    isPeriodMenuOpen = false
                },
                onDismiss = { isPeriodMenuOpen = false }
            )

            DatePickerOverlay(
                visible = isDatePickerOpen,
                date = currentDate,
                onDateChange = { selectedEpochDay = it.toEpochDay() },
                onDismiss = { isDatePickerOpen = false }
            )
        }
    }
}

@Composable
private fun ScheduleTopBar(
    selectedPeriod: SchedulePeriod,
    selectedDate: LocalDate,
    periodMenuExpanded: Boolean,
    onPeriodMenuExpandedChange: (Boolean) -> Unit,
    onDateClick: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PeriodMenu(
            selectedPeriod = selectedPeriod,
            expanded = periodMenuExpanded,
            onExpandedChange = onPeriodMenuExpandedChange
        )
        Spacer(modifier = Modifier.width(10.dp))
        IconPillButton(
            icon = Icons.Filled.Event,
            contentDescription = "Select date",
            onClick = onDateClick
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = selectedDate.formatScheduleHeaderDate(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = selectedDate.dayOfWeek.name.lowercase()
                    .replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = Color.Black.copy(alpha = 0.48f),
                maxLines = 1
            )
        }
        IconPillButton(
            icon = Icons.Filled.Close,
            contentDescription = "Close",
            onClick = onClose
        )
    }
}

@Composable
private fun PeriodMenu(
    selectedPeriod: SchedulePeriod,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .width(102.dp)
            .height(48.dp)
            .zIndex(4f)
    ) {
        PeriodPill(
            text = selectedPeriod.label,
            selected = true,
            trailingIcon = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            onClick = { onExpandedChange(!expanded) },
            modifier = Modifier.align(Alignment.TopStart)
        )
    }
}

@Composable
private fun BoxScope.PeriodDropdownOverlay(
    visible: Boolean,
    selectedPeriod: SchedulePeriod,
    onPeriodChange: (SchedulePeriod) -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(100)),
        modifier = Modifier
            .align(Alignment.TopStart)
            .zIndex(24f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = onDismiss
                )
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = tween(220, easing = FastOutSlowInEasing),
            initialOffsetY = { -it / 2 }
        ) + fadeIn(tween(120)),
        exit = slideOutVertically(
            animationSpec = tween(170, easing = FastOutSlowInEasing),
            targetOffsetY = { -it / 3 }
        ) + fadeOut(tween(110)),
        modifier = Modifier
            .align(Alignment.TopStart)
            .statusBarsPadding()
            .padding(start = 18.dp, top = 69.dp)
            .zIndex(25f)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SchedulePeriod.entries.filter { it != selectedPeriod }.forEach { period ->
                PeriodPill(
                    text = period.label,
                    selected = true,
                    onClick = { onPeriodChange(period) }
                )
            }
        }
    }
}

@Composable
private fun PeriodPill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    darkWhenIdle: Boolean = false,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    val dark = selected || darkWhenIdle
    val background by animateColorAsState(
        targetValue = if (dark) Color.Black else Color.White.copy(alpha = 0.72f),
        animationSpec = tween(180),
        label = "PeriodPillBackground"
    )
    val borderColor by animateColorAsState(
        targetValue = if (dark) Color.Black else Color.Black.copy(alpha = 0.28f),
        animationSpec = tween(180),
        label = "PeriodPillBorder"
    )
    val contentColor by animateColorAsState(
        targetValue = if (dark) Color.White else Color.Black,
        animationSpec = tween(180),
        label = "PeriodPillContent"
    )

    Row(
        modifier = modifier
            .width(102.dp)
            .height(48.dp)
            .border(2.dp, borderColor, RoundedCornerShape(999.dp))
            .background(background, RoundedCornerShape(999.dp))
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (trailingIcon != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun IconPillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
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
            modifier = Modifier.size(23.dp)
        )
    }
}

@Composable
private fun SwipeableDayScheduleView(
    selectedDate: LocalDate,
    schedulesForDate: (LocalDate) -> List<Schedule>,
    onDateChange: (LocalDate) -> Unit,
    onScheduleClick: (Schedule) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var displayedEpochDay by rememberSaveable { mutableLongStateOf(selectedDate.toEpochDay()) }
    var targetEpochDay by remember { mutableStateOf<Long?>(null) }
    var transitionDirection by remember { mutableStateOf(0) }
    var rubberOffsetPx by remember { mutableFloatStateOf(0f) }
    var isSwitching by remember { mutableStateOf(false) }
    var switchProgress by remember { mutableFloatStateOf(1f) }
    val animationJobs = remember { ScheduleSwipeAnimationJobs() }
    val isSwitchingState by rememberUpdatedState(isSwitching)

    LaunchedEffect(selectedDate) {
        val selectedEpochDay = selectedDate.toEpochDay()
        if (!isSwitching && selectedEpochDay != displayedEpochDay) {
            displayedEpochDay = selectedEpochDay
            targetEpochDay = null
            transitionDirection = 0
            rubberOffsetPx = 0f
            switchProgress = 1f
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
    ) {
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val maxRubberPx = with(density) { 58.dp.toPx() }
        val swipeThresholdPx = with(density) { 42.dp.toPx() }
        val touchSlopPx = with(density) { 8.dp.toPx() }

        fun animateRubberBack() {
            animationJobs.rubberBackJob?.cancel()
            val startOffset = rubberOffsetPx
            animationJobs.rubberBackJob = scope.launch {
                Animatable(startOffset).animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 460f)
                ) {
                    rubberOffsetPx = value
                }
                animationJobs.rubberBackJob = null
            }
        }

        fun startDateSwitch(direction: Int) {
            if (isSwitching || direction == 0 || animationJobs.switchJob?.isActive == true) return
            animationJobs.rubberBackJob?.cancel()
            animationJobs.rubberBackJob = null
            isSwitching = true
            val fromEpochDay = displayedEpochDay
            val nextDate = LocalDate.ofEpochDay(fromEpochDay).plusDays(direction.toLong())
            val nextEpochDay = nextDate.toEpochDay()

            transitionDirection = direction
            targetEpochDay = nextEpochDay
            switchProgress = 0f
            onDateChange(nextDate)

            animationJobs.switchJob = scope.launch {
                try {
                    val startOffset = rubberOffsetPx
                    val rubberJob = launch {
                        Animatable(startOffset).animateTo(
                            targetValue = 0f,
                            animationSpec = spring(dampingRatio = 0.68f, stiffness = 520f)
                        ) {
                            rubberOffsetPx = value
                        }
                    }
                    val cardsJob = launch {
                        Animatable(0f).animateTo(
                            targetValue = 1f,
                            animationSpec = spring(dampingRatio = 0.78f, stiffness = 360f)
                        ) {
                            switchProgress = value
                        }
                    }

                    rubberJob.join()
                    cardsJob.join()

                    displayedEpochDay = nextEpochDay
                    targetEpochDay = null
                    transitionDirection = 0
                    rubberOffsetPx = 0f
                    switchProgress = 1f
                } finally {
                    isSwitching = false
                    animationJobs.switchJob = null
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(widthPx, displayedEpochDay) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (isSwitchingState) {
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                            } while (event.changes.any { it.pressed })
                            return@awaitEachGesture
                        }

                        animationJobs.rubberBackJob?.cancel()
                        animationJobs.rubberBackJob = null
                        var lockedHorizontal = false
                        var lockedVertical = false
                        var switchTriggered = false
                        var totalX = 0f

                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressedChanges = event.changes.filter { it.pressed }
                            if (pressedChanges.size > 1) {
                                lockedVertical = true
                                break
                            }

                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            totalX = change.position.x - down.position.x
                            val totalY = change.position.y - down.position.y
                            val absX = abs(totalX)
                            val absY = abs(totalY)

                            if (!lockedHorizontal && !lockedVertical) {
                                when {
                                    absX > touchSlopPx && absX > absY * 1.08f -> lockedHorizontal = true
                                    absY > touchSlopPx && absY > absX -> lockedVertical = true
                                }
                            }

                            if (lockedHorizontal) {
                                rubberOffsetPx = rubberBandOffset(totalX, maxRubberPx)
                                change.consume()
                                if (abs(totalX) > swipeThresholdPx) {
                                    switchTriggered = true
                                    startDateSwitch(if (totalX < 0f) 1 else -1)
                                    break
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        if (lockedHorizontal && !switchTriggered && !isSwitching) {
                            animateRubberBack()
                        }
                    }
                }
        ) {
            val displayedDate = remember(displayedEpochDay) {
                LocalDate.ofEpochDay(displayedEpochDay)
            }
            val targetDate = targetEpochDay?.let { LocalDate.ofEpochDay(it) }

            DayScheduleView(
                schedules = schedulesForDate(displayedDate),
                targetSchedules = targetDate?.let(schedulesForDate),
                transitionDirection = transitionDirection,
                transitionProgress = switchProgress,
                rubberOffsetPx = rubberOffsetPx,
                rubberLimitPx = maxRubberPx,
                onScheduleClick = onScheduleClick,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun rubberBandOffset(rawOffset: Float, limit: Float): Float {
    if (limit <= 0f) return 0f
    val magnitude = abs(rawOffset)
    val sign = if (rawOffset < 0f) -1f else 1f
    return sign * limit * (1f - 1f / (magnitude / limit + 1f))
}

@Composable
private fun DayScheduleView(
    schedules: List<Schedule>,
    targetSchedules: List<Schedule>? = null,
    transitionDirection: Int = 0,
    transitionProgress: Float = 1f,
    rubberOffsetPx: Float = 0f,
    rubberLimitPx: Float = 1f,
    onScheduleClick: (Schedule) -> Unit,
    modifier: Modifier = Modifier
) {
    var visibleHours by rememberSaveable { mutableFloatStateOf(12f) }
    val animatedVisibleHours by animateFloatAsState(
        targetValue = visibleHours,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "VisibleHours"
    )

    BoxWithConstraints(
        modifier = modifier
            .padding(start = 6.dp, end = 14.dp, bottom = 12.dp)
            .pinchToZoomTimeline(
                onScale = { zoom ->
                    visibleHours = (visibleHours / zoom).coerceIn(8f, 24f)
                }
            )
    ) {
        val viewportHeight = maxHeight.coerceAtLeast(1.dp)
        val hourHeight = viewportHeight / animatedVisibleHours
        val timelineHeight = hourHeight * 24f
        val scrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(timelineHeight)
                    .graphicsLayer {
                        translationX = rubberOffsetPx
                        val pull = (abs(rubberOffsetPx) / rubberLimitPx.coerceAtLeast(1f))
                            .coerceIn(0f, 1f)
                        scaleX = 1f - pull * 0.01f
                        scaleY = 1f - pull * 0.005f
                    }
            ) {
                TimeAxis(
                    hourHeight = hourHeight,
                    modifier = Modifier
                        .width(38.dp)
                        .fillMaxHeight()
                )
                Spacer(modifier = Modifier.width(6.dp))
                ScheduleTimeline(
                    schedules = schedules,
                    targetSchedules = targetSchedules,
                    transitionDirection = transitionDirection,
                    transitionProgress = transitionProgress,
                    hourHeight = hourHeight,
                    onScheduleClick = onScheduleClick,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun TimeAxis(
    hourHeight: Dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    Box(modifier = modifier) {
        repeat(25) { hour ->
            val y = with(density) {
                val raw = (hourHeight * hour.toFloat()).toPx()
                val max = (hourHeight * 24f).toPx() - 18.dp.toPx()
                raw.coerceIn(0f, max)
            }
            Text(
                text = "${hour.toString().padStart(2, '0')}:00",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = Color.Black.copy(alpha = 0.48f),
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, y.roundToInt()) }
            )
        }
    }
}

@Composable
private fun ScheduleTimeline(
    schedules: List<Schedule>,
    targetSchedules: List<Schedule>? = null,
    transitionDirection: Int = 0,
    transitionProgress: Float = 1f,
    hourHeight: Dp,
    onScheduleClick: (Schedule) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val layouts = remember(schedules) { computeScheduleLayouts(schedules) }
    val targetLayouts = remember(targetSchedules) {
        targetSchedules?.let(::computeScheduleLayouts).orEmpty()
    }
    val timelineShape = RoundedCornerShape(24.dp)

    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .border(2.dp, Color.Black.copy(alpha = 0.16f), timelineShape)
            .background(Color.White.copy(alpha = 0.18f), timelineShape)
    ) {
        val laneSpacing = 6.dp
        val contentPadding = 8.dp
        val laneAreaWidth = (maxWidth - contentPadding * 2f).coerceAtLeast(1.dp)
        val timelineWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val hourHeightPx = hourHeight.toPx()
            for (hour in 0..24) {
                val y = hourHeightPx * hour
                drawLine(
                    color = Color.Black.copy(alpha = if (hour % 6 == 0) 0.16f else 0.08f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = if (hour % 6 == 0) 1.4f else 1f,
                    pathEffect = if (hour % 6 == 0) null else
                        PathEffect.dashPathEffect(floatArrayOf(7f, 9f), 0f)
                )
            }
        }

        if (targetSchedules != null && transitionDirection != 0) {
            val pageProgress = transitionProgress.coerceIn(0f, 1.08f)
            ScheduleCardsLayer(
                schedules = schedules,
                layouts = layouts,
                hourHeight = hourHeight,
                laneAreaWidth = laneAreaWidth,
                laneSpacing = laneSpacing,
                contentPadding = contentPadding,
                onScheduleClick = onScheduleClick,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        translationX = -transitionDirection * timelineWidthPx * pageProgress
                        alpha = 1f - 0.18f * pageProgress.coerceIn(0f, 1f)
                        scaleX = 1f - 0.012f * pageProgress.coerceIn(0f, 1f)
                        scaleY = scaleX
                    }
            )
            ScheduleCardsLayer(
                schedules = targetSchedules,
                layouts = targetLayouts,
                hourHeight = hourHeight,
                laneAreaWidth = laneAreaWidth,
                laneSpacing = laneSpacing,
                contentPadding = contentPadding,
                onScheduleClick = onScheduleClick,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        translationX = transitionDirection * timelineWidthPx * (1f - pageProgress)
                        alpha = 0.74f + 0.26f * pageProgress.coerceIn(0f, 1f)
                        scaleX = 0.988f + 0.012f * pageProgress.coerceIn(0f, 1f)
                        scaleY = scaleX
                    }
            )
        } else {
            ScheduleCardsLayer(
                schedules = schedules,
                layouts = layouts,
                hourHeight = hourHeight,
                laneAreaWidth = laneAreaWidth,
                laneSpacing = laneSpacing,
                contentPadding = contentPadding,
                onScheduleClick = onScheduleClick,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

@Composable
private fun BoxScope.ScheduleCardsLayer(
    schedules: List<Schedule>,
    layouts: Map<String, ScheduleLayoutInfo>,
    hourHeight: Dp,
    laneAreaWidth: Dp,
    laneSpacing: Dp,
    contentPadding: Dp,
    onScheduleClick: (Schedule) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    Box(modifier = modifier) {
        schedules.forEach { schedule ->
            val layout = layouts[schedule.id] ?: return@forEach
            val totalColumns = layout.totalColumns.coerceAtLeast(1)
            val columnWidth = (laneAreaWidth - laneSpacing * (totalColumns - 1).toFloat()) /
                totalColumns.toFloat()
            val xOffset = contentPadding + (columnWidth + laneSpacing) * layout.column.toFloat()
            val startMinutes = schedule.startMinutesForDay()
            val endMinutes = schedule.endMinutesForDay()
            val rawDuration = (endMinutes - startMinutes).coerceAtLeast(15)
            val topOffset = hourHeight * (startMinutes / 60f)
            val rawHeight = hourHeight * (rawDuration / 60f)
            val cardHeight = rawHeight.coerceAtLeast(46.dp)
            val maxAvailableHeight = (hourHeight * 24f - topOffset).coerceAtLeast(46.dp)
            val visibleCardHeight = cardHeight.coerceAtMost(maxAvailableHeight)

            ScheduleTimelineCard(
                schedule = schedule,
                showTime = visibleCardHeight >= 86.dp,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = with(density) { xOffset.toPx() }.roundToInt(),
                            y = with(density) { topOffset.toPx() }.roundToInt()
                        )
                    }
                    .width(columnWidth)
                    .height(visibleCardHeight),
                onClick = { onScheduleClick(schedule) }
            )
        }
    }
}

@Composable
private fun ScheduleTimelineCard(
    schedule: Schedule,
    showTime: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(180),
        label = "TimelineCardScale"
    )

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(2.dp, Color.Black.copy(alpha = 0.3f), shape)
            .background(Color.White.copy(alpha = 0.62f), shape)
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = schedule.title,
            style = MaterialTheme.typography.titleSmall.copy(lineHeight = 20.sp),
            fontWeight = FontWeight.Black,
            color = Color.Black,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(7.dp))
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(42.dp)
                .height(5.dp)
                .border(1.dp, Color.Black.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
                .background(schedule.color, RoundedCornerShape(999.dp))
        )
        val timeRange = schedule.cardTimeRange()
        if (showTime && timeRange.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = timeRange,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = Color.Black.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun Modifier.pinchToZoomTimeline(onScale: (Float) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            var previousDistance: Float? = null
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    val first = pressed[0].position
                    val second = pressed[1].position
                    val distance = first.distanceTo(second)
                    val previous = previousDistance
                    if (previous != null && previous > 0f) {
                        val zoom = (distance / previous).coerceIn(0.88f, 1.14f)
                        if (zoom.isFinite()) onScale(zoom)
                    }
                    previousDistance = distance
                    pressed.forEach { it.consume() }
                } else {
                    previousDistance = null
                }
            } while (event.changes.any { it.pressed })
        }
    }

@Composable
private fun BoxScope.DatePickerOverlay(
    visible: Boolean,
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(130)),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.16f))
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = onDismiss
                )
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = tween(260, easing = FastOutSlowInEasing),
            initialOffsetY = { it }
        ) + fadeIn(tween(150)),
        exit = slideOutVertically(
            animationSpec = tween(210, easing = FastOutSlowInEasing),
            targetOffsetY = { it }
        ) + fadeOut(tween(120)),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        DatePickerPanel(
            date = date,
            onDateChange = onDateChange,
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun DatePickerPanel(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedField by remember { mutableStateOf(ScheduleDateField.Year) }

    Surface(
        color = BeigeBackground,
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.58f)
            .border(
                width = 2.dp,
                color = Color.Black.copy(alpha = 0.32f),
                shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(top = 18.dp, bottom = 18.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(54.dp)
                    .height(5.dp)
                    .background(Color.Black.copy(alpha = 0.24f), RoundedCornerShape(999.dp))
            )
            Spacer(modifier = Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Select Date",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 28.sp,
                            lineHeight = 34.sp
                        ),
                        color = Color.Black
                    )
                    Text(
                        text = date.formatScheduleHeaderDate(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black.copy(alpha = 0.56f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .border(2.dp, Color.Black, RoundedCornerShape(999.dp))
                        .background(Color.Black, RoundedCornerShape(999.dp))
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            onClick = onDismiss
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Done",
                        tint = Color.White,
                        modifier = Modifier.size(23.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            DateFieldTabs(
                selectedField = selectedField,
                onSelect = { selectedField = it }
            )
            Spacer(modifier = Modifier.height(14.dp))

            AnimatedContent(
                targetState = selectedField,
                transitionSpec = {
                    fadeIn(tween(160)) togetherWith fadeOut(tween(110)) using
                        SizeTransform(clip = false)
                },
                label = "DatePickerField"
            ) { field ->
                DateOptionList(
                    field = field,
                    date = date,
                    onSelect = { value ->
                        val nextDate = date.withDateField(field, value)
                        onDateChange(nextDate)
                        val nextField = ScheduleDateField.entries.getOrNull(field.ordinal + 1)
                        if (nextField != null) selectedField = nextField
                    }
                )
            }
        }
    }
}

@Composable
private fun DateFieldTabs(
    selectedField: ScheduleDateField,
    onSelect: (ScheduleDateField) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .border(2.dp, Color.Black.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.42f), RoundedCornerShape(999.dp))
            .padding(4.dp)
    ) {
        ScheduleDateField.entries.forEach { field ->
            val selected = field == selectedField
            val background by animateColorAsState(
                targetValue = if (selected) Color.Black else Color.Transparent,
                animationSpec = tween(180),
                label = "DateFieldBackground"
            )
            val contentColor by animateColorAsState(
                targetValue = if (selected) Color.White else Color.Black.copy(alpha = 0.62f),
                animationSpec = tween(180),
                label = "DateFieldContent"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(background, RoundedCornerShape(999.dp))
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = { onSelect(field) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = field.label.take(3),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = contentColor,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun DateOptionList(
    field: ScheduleDateField,
    date: LocalDate,
    onSelect: (Int) -> Unit
) {
    val selectedValue = date.valueFor(field)
    val options = remember(field, date.year, date.monthValue) {
        buildDateOptions(field, date)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(options, key = { it }) { option ->
            DateOptionRow(
                label = option.formatDateOption(field),
                secondary = field.label,
                selected = selectedValue == option,
                onClick = { onSelect(option) }
            )
        }
    }
}

@Composable
private fun DateOptionRow(
    label: String,
    secondary: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background by animateColorAsState(
        targetValue = if (selected) Color.Black else Color.White.copy(alpha = 0.46f),
        animationSpec = tween(160),
        label = "DateOptionBackground"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) Color.White else Color.Black,
        animationSpec = tween(160),
        label = "DateOptionContent"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(
                width = 2.dp,
                color = if (selected) Color.Black else Color.Black.copy(alpha = 0.18f),
                shape = RoundedCornerShape(20.dp)
            )
            .background(background, RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = contentColor,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = secondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = contentColor.copy(alpha = 0.62f),
            modifier = Modifier.widthIn(min = 58.dp),
            textAlign = TextAlign.End,
            maxLines = 1
        )
    }
}

private data class ScheduleLayoutInfo(
    val column: Int,
    val totalColumns: Int
)

private fun computeScheduleLayouts(schedules: List<Schedule>): Map<String, ScheduleLayoutInfo> {
    if (schedules.isEmpty()) return emptyMap()

    val sorted = schedules.sortedWith(compareBy({ it.startMinutesForDay() }, { it.title }))
    val n = sorted.size
    val overlaps = Array(n) { BooleanArray(n) }

    for (i in 0 until n) {
        val iStart = sorted[i].startMinutesForDay()
        val iEnd = sorted[i].endMinutesForDay()
        for (j in 0 until n) {
            if (i == j) {
                overlaps[i][j] = true
            } else {
                val jStart = sorted[j].startMinutesForDay()
                val jEnd = sorted[j].endMinutesForDay()
                overlaps[i][j] = iStart < jEnd && jStart < iEnd
            }
        }
    }

    val columns = IntArray(n) { -1 }
    for (i in 0 until n) {
        val used = mutableSetOf<Int>()
        for (j in 0 until i) {
            if (overlaps[i][j] && columns[j] != -1) used += columns[j]
        }
        var column = 0
        while (column in used) column++
        columns[i] = column
    }

    val result = mutableMapOf<String, ScheduleLayoutInfo>()
    for (i in 0 until n) {
        val events = mutableListOf<Pair<Int, Int>>()
        for (j in 0 until n) {
            if (overlaps[i][j]) {
                events += sorted[j].startMinutesForDay() to 1
                events += sorted[j].endMinutesForDay() to -1
            }
        }
        events.sortWith(compareBy({ it.first }, { it.second }))

        var current = 0
        var maxConcurrent = 0
        for ((_, delta) in events) {
            current += delta
            maxConcurrent = maxOf(maxConcurrent, current)
        }
        result[sorted[i].id] = ScheduleLayoutInfo(columns[i], maxConcurrent.coerceAtLeast(1))
    }

    return result
}

private fun Schedule.startMinutesForDay(): Int =
    ((startTime.hour ?: 0) * 60 + (startTime.minute ?: 0)).coerceIn(0, 24 * 60)

private fun Schedule.endMinutesForDay(): Int {
    val start = startMinutesForDay()
    val end = ((endTime.hour ?: 24) * 60 + (endTime.minute ?: 0)).coerceIn(0, 24 * 60)
    return if (end <= start) (start + 60).coerceAtMost(24 * 60) else end
}

private fun Schedule.cardTimeRange(): String {
    val start = startTime.toTimeString()
    val end = endTime.toTimeString()
    return listOf(start, end).filter { it.isNotBlank() }.joinToString(" - ")
}

private fun LocalDate.formatScheduleHeaderDate(): String {
    val months = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )
    return "${months[monthValue - 1]} ${dayOfMonth.toString().padStart(2, '0')}, $year"
}

private fun LocalDate.valueFor(field: ScheduleDateField): Int = when (field) {
    ScheduleDateField.Year -> year
    ScheduleDateField.Month -> monthValue
    ScheduleDateField.Day -> dayOfMonth
}

private fun LocalDate.withDateField(field: ScheduleDateField, value: Int): LocalDate = when (field) {
    ScheduleDateField.Year -> {
        val safeDay = dayOfMonth.coerceAtMost(YearMonth.of(value, monthValue).lengthOfMonth())
        LocalDate.of(value, monthValue, safeDay)
    }
    ScheduleDateField.Month -> {
        val safeDay = dayOfMonth.coerceAtMost(YearMonth.of(year, value).lengthOfMonth())
        LocalDate.of(year, value, safeDay)
    }
    ScheduleDateField.Day -> LocalDate.of(year, monthValue, value)
}

private fun buildDateOptions(field: ScheduleDateField, date: LocalDate): List<Int> = when (field) {
    ScheduleDateField.Year -> (date.year - 5..date.year + 8).toList()
    ScheduleDateField.Month -> (1..12).toList()
    ScheduleDateField.Day -> (1..YearMonth.of(date.year, date.monthValue).lengthOfMonth()).toList()
}

private fun Int.formatDateOption(field: ScheduleDateField): String = when (field) {
    ScheduleDateField.Year -> "$this"
    ScheduleDateField.Month -> arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    ).getOrElse(this - 1) { toString() }
    ScheduleDateField.Day -> toString().padStart(2, '0')
}

private fun Offset.distanceTo(other: Offset): Float {
    val dx = x - other.x
    val dy = y - other.y
    return sqrt(dx * dx + dy * dy)
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F5F0)
@Composable
fun FullDaySchedulePreviewPreview() {
    TodoTheme {
        FullDaySchedulePreview(
            selectedDate = LocalDate.now(),
            schedulesForDate = { SampleData.defaultSchedules },
            onClose = {}
        )
    }
}
