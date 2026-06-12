package com.example.todo

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.todo.ui.theme.BeigeBackground
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private class WeekSwipeAnimationJobs {
    var rubberBackJob: Job? = null
    var switchJob: Job? = null
}

@Composable
fun WeekScheduleView(
    selectedDate: LocalDate,
    schedulesForDate: (LocalDate) -> List<Schedule>,
    onDateChange: (LocalDate) -> Unit,
    onScheduleClick: (Schedule) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var visibleHours by rememberSaveable { mutableFloatStateOf(12f) }
    val animatedVisibleHours by animateFloatAsState(
        targetValue = visibleHours,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "WeekVisibleHours"
    )
    var displayedWeekStartEpochDay by rememberSaveable {
        mutableLongStateOf(selectedDate.weekStartDate().toEpochDay())
    }
    var targetWeekStartEpochDay by remember { mutableStateOf<Long?>(null) }
    var transitionDirection by remember { mutableStateOf(0) }
    var rubberOffsetPx by remember { mutableFloatStateOf(0f) }
    var isSwitching by remember { mutableStateOf(false) }
    var switchProgress by remember { mutableFloatStateOf(1f) }
    val animationJobs = remember { WeekSwipeAnimationJobs() }
    val isSwitchingState by rememberUpdatedState(isSwitching)
    val scrollState = rememberScrollState()

    LaunchedEffect(selectedDate) {
        val selectedWeekStartEpochDay = selectedDate.weekStartDate().toEpochDay()
        if (!isSwitching && selectedWeekStartEpochDay != displayedWeekStartEpochDay) {
            displayedWeekStartEpochDay = selectedWeekStartEpochDay
            targetWeekStartEpochDay = null
            transitionDirection = 0
            rubberOffsetPx = 0f
            switchProgress = 1f
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .padding(start = 6.dp, end = 14.dp, bottom = 12.dp)
            .clipToBounds()
            .pinchToZoomWeekTimeline(
                onScale = { zoom ->
                    visibleHours = (visibleHours / zoom).coerceIn(8f, 24f)
                }
            )
    ) {
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val maxRubberPx = with(density) { 76.dp.toPx() }
        val swipeThresholdPx = with(density) { 42.dp.toPx() }
        val touchSlopPx = with(density) { 8.dp.toPx() }
        val selectedDayOffset = (selectedDate.dayOfWeek.value - DayOfWeek.MONDAY.value)
            .coerceIn(0, 6)

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

        fun startWeekSwitch(direction: Int) {
            if (isSwitching || direction == 0 || animationJobs.switchJob?.isActive == true) return
            animationJobs.rubberBackJob?.cancel()
            animationJobs.rubberBackJob = null
            isSwitching = true

            val fromWeekStart = LocalDate.ofEpochDay(displayedWeekStartEpochDay)
            val nextWeekStart = fromWeekStart.plusWeeks(direction.toLong())
            val nextWeekStartEpochDay = nextWeekStart.toEpochDay()
            val nextSelectedDate = nextWeekStart.plusDays(selectedDayOffset.toLong())

            transitionDirection = direction
            targetWeekStartEpochDay = nextWeekStartEpochDay
            switchProgress = 0f
            onDateChange(nextSelectedDate)

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
                    val pageJob = launch {
                        Animatable(0f).animateTo(
                            targetValue = 1f,
                            animationSpec = spring(dampingRatio = 0.78f, stiffness = 360f)
                        ) {
                            switchProgress = value
                        }
                    }

                    rubberJob.join()
                    pageJob.join()

                    displayedWeekStartEpochDay = nextWeekStartEpochDay
                    targetWeekStartEpochDay = null
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
                .pointerInput(widthPx, displayedWeekStartEpochDay) {
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
                                rubberOffsetPx = weekRubberBandOffset(totalX, maxRubberPx)
                                change.consume()
                                if (abs(totalX) > swipeThresholdPx) {
                                    switchTriggered = true
                                    startWeekSwitch(if (totalX < 0f) 1 else -1)
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
            val displayedWeekStart = remember(displayedWeekStartEpochDay) {
                LocalDate.ofEpochDay(displayedWeekStartEpochDay)
            }
            val targetWeekStart = targetWeekStartEpochDay?.let { LocalDate.ofEpochDay(it) }

            WeekSchedulePage(
                weekStart = displayedWeekStart,
                targetWeekStart = targetWeekStart,
                selectedDate = selectedDate,
                schedulesForDate = schedulesForDate,
                animatedVisibleHours = animatedVisibleHours,
                scrollState = scrollState,
                transitionDirection = transitionDirection,
                transitionProgress = switchProgress,
                rubberOffsetPx = rubberOffsetPx,
                rubberLimitPx = maxRubberPx,
                onDateChange = onDateChange,
                onScheduleClick = onScheduleClick,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun WeekSchedulePage(
    weekStart: LocalDate,
    targetWeekStart: LocalDate?,
    selectedDate: LocalDate,
    schedulesForDate: (LocalDate) -> List<Schedule>,
    animatedVisibleHours: Float,
    scrollState: ScrollState,
    transitionDirection: Int,
    transitionProgress: Float,
    rubberOffsetPx: Float,
    rubberLimitPx: Float,
    onDateChange: (LocalDate) -> Unit,
    onScheduleClick: (Schedule) -> Unit,
    modifier: Modifier = Modifier
) {
    val weekDates = remember(weekStart) { (0..6).map { weekStart.plusDays(it.toLong()) } }
    val targetWeekDates = remember(targetWeekStart) {
        targetWeekStart?.let { start -> (0..6).map { start.plusDays(it.toLong()) } }
    }

    BoxWithConstraints(modifier = modifier) {
        val headerHeight = 40.dp
        val headerGap = 8.dp
        val axisWidth = 26.dp
        val axisGap = 6.dp
        val viewportHeight = (maxHeight - headerHeight - headerGap).coerceAtLeast(1.dp)
        val hourHeight = viewportHeight / animatedVisibleHours
        val timelineHeight = hourHeight * 24f
        val pull = (abs(rubberOffsetPx) / rubberLimitPx.coerceAtLeast(1f)).coerceIn(0f, 1f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = rubberOffsetPx
                    scaleX = 1f - pull * 0.01f
                    scaleY = 1f - pull * 0.005f
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(axisWidth + axisGap))
                WeekDayHeaderBar(
                    dates = weekDates,
                    selectedDate = selectedDate,
                    onDateClick = onDateChange,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(headerGap))

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(timelineHeight)
                ) {
                    WeekTimeAxis(
                        hourHeight = hourHeight,
                        modifier = Modifier
                            .width(axisWidth)
                            .fillMaxHeight()
                    )
                    Spacer(modifier = Modifier.width(axisGap))
                    WeekScheduleTimeline(
                        dates = weekDates,
                        targetDates = targetWeekDates,
                        selectedDate = selectedDate,
                        schedulesForDate = schedulesForDate,
                        hourHeight = hourHeight,
                        transitionDirection = transitionDirection,
                        transitionProgress = transitionProgress,
                        onDateChange = onDateChange,
                        onScheduleClick = onScheduleClick,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekDayHeaderBar(
    dates: List<LocalDate>,
    selectedDate: LocalDate,
    onDateClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(34.dp)
            .background(Color.Black, RoundedCornerShape(999.dp))
            .padding(horizontal = 3.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        dates.forEach { date ->
            val selected = date.dayOfWeek == selectedDate.dayOfWeek
            val background by animateColorAsState(
                targetValue = if (selected) Color.White else Color.Transparent,
                animationSpec = tween(180, easing = FastOutSlowInEasing),
                label = "WeekDayBackground"
            )
            val contentColor by animateColorAsState(
                targetValue = if (selected) Color.Black else Color.White,
                animationSpec = tween(180, easing = FastOutSlowInEasing),
                label = "WeekDayContent"
            )
            val selectedScale by animateFloatAsState(
                targetValue = if (selected) 1.08f else 1f,
                animationSpec = tween(180, easing = FastOutSlowInEasing),
                label = "WeekDayScale"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 1.dp)
                    .graphicsLayer { scaleY = selectedScale }
                    .background(background, RoundedCornerShape(999.dp))
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = { onDateClick(date) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = date.weekdayLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = contentColor,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

@Composable
private fun WeekTimeAxis(
    hourHeight: Dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    Box(modifier = modifier) {
        repeat(25) { hour ->
            val y = with(density) {
                val raw = (hourHeight * hour.toFloat()).toPx()
                val max = (hourHeight * 24f).toPx() - 14.dp.toPx()
                raw.coerceIn(0f, max)
            }
            Text(
                text = hour.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                fontWeight = FontWeight.Black,
                color = Color.Black.copy(alpha = 0.46f),
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
private fun WeekScheduleTimeline(
    dates: List<LocalDate>,
    targetDates: List<LocalDate>?,
    selectedDate: LocalDate,
    schedulesForDate: (LocalDate) -> List<Schedule>,
    hourHeight: Dp,
    transitionDirection: Int,
    transitionProgress: Float,
    onDateChange: (LocalDate) -> Unit,
    onScheduleClick: (Schedule) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val timelineShape = RoundedCornerShape(24.dp)

    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .border(2.dp, Color.Black.copy(alpha = 0.16f), timelineShape)
            .background(Color.White.copy(alpha = 0.18f), timelineShape)
    ) {
        val contentPadding = 6.dp
        val daySpacing = 3.dp
        val dayWidth = ((maxWidth - contentPadding * 2f - daySpacing * 6f) / 7f)
            .coerceAtLeast(1.dp)
        val selectedIndex = (selectedDate.dayOfWeek.value - DayOfWeek.MONDAY.value).coerceIn(0, 6)
        val timelineWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val hourHeightPx = hourHeight.toPx()
            val contentPaddingPx = contentPadding.toPx()
            val dayWidthPx = dayWidth.toPx()
            val daySpacingPx = daySpacing.toPx()

            for (hour in 0..24) {
                val y = hourHeightPx * hour
                drawLine(
                    color = Color.Black.copy(alpha = if (hour % 6 == 0) 0.15f else 0.075f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = if (hour % 6 == 0) 1.3f else 1f,
                    pathEffect = if (hour % 6 == 0) null else
                        PathEffect.dashPathEffect(floatArrayOf(6f, 9f), 0f)
                )
            }

            for (day in 1..6) {
                val x = contentPaddingPx + day * dayWidthPx + (day - 0.5f) * daySpacingPx
                drawLine(
                    color = Color.Black.copy(alpha = 0.08f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f
                )
            }

            if (selectedIndex >= 0) {
                val x = contentPaddingPx + selectedIndex * (dayWidthPx + daySpacingPx)
                drawRect(
                    color = Color.White.copy(alpha = 0.14f),
                    topLeft = Offset(x, 0f),
                    size = androidx.compose.ui.geometry.Size(dayWidthPx, size.height)
                )
            }
        }

        if (targetDates != null && transitionDirection != 0) {
            val pageProgress = transitionProgress.coerceIn(0f, 1.08f)
            val boundedProgress = pageProgress.coerceIn(0f, 1f)
            WeekScheduleCardsPage(
                dates = dates,
                schedulesForDate = schedulesForDate,
                dayWidth = dayWidth,
                daySpacing = daySpacing,
                contentPadding = contentPadding,
                hourHeight = hourHeight,
                onDateChange = onDateChange,
                onScheduleClick = onScheduleClick,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        translationX = -transitionDirection * timelineWidthPx * pageProgress
                        alpha = 1f - 0.18f * boundedProgress
                        scaleX = 1f - 0.012f * boundedProgress
                        scaleY = scaleX
                    }
            )
            WeekScheduleCardsPage(
                dates = targetDates,
                schedulesForDate = schedulesForDate,
                dayWidth = dayWidth,
                daySpacing = daySpacing,
                contentPadding = contentPadding,
                hourHeight = hourHeight,
                onDateChange = onDateChange,
                onScheduleClick = onScheduleClick,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        translationX = transitionDirection * timelineWidthPx * (1f - pageProgress)
                        alpha = 0.74f + 0.26f * boundedProgress
                        scaleX = 0.988f + 0.012f * boundedProgress
                        scaleY = scaleX
                    }
            )
        } else {
            WeekScheduleCardsPage(
                dates = dates,
                schedulesForDate = schedulesForDate,
                dayWidth = dayWidth,
                daySpacing = daySpacing,
                contentPadding = contentPadding,
                hourHeight = hourHeight,
                onDateChange = onDateChange,
                onScheduleClick = onScheduleClick,
                modifier = Modifier
                    .matchParentSize()
            )
        }
    }
}

@Composable
private fun BoxScope.WeekScheduleCardsPage(
    dates: List<LocalDate>,
    schedulesForDate: (LocalDate) -> List<Schedule>,
    dayWidth: Dp,
    daySpacing: Dp,
    contentPadding: Dp,
    hourHeight: Dp,
    onDateChange: (LocalDate) -> Unit,
    onScheduleClick: (Schedule) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        dates.forEachIndexed { dayIndex, date ->
            val schedules = schedulesForDate(date)
            val layouts = remember(schedules) { computeWeekScheduleLayouts(schedules) }
            WeekScheduleCardsLayer(
                date = date,
                schedules = schedules,
                layouts = layouts,
                dayIndex = dayIndex,
                dayWidth = dayWidth,
                daySpacing = daySpacing,
                contentPadding = contentPadding,
                hourHeight = hourHeight,
                onDateChange = onDateChange,
                onScheduleClick = onScheduleClick,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

@Composable
private fun BoxScope.WeekScheduleCardsLayer(
    date: LocalDate,
    schedules: List<Schedule>,
    layouts: Map<String, WeekScheduleLayoutInfo>,
    dayIndex: Int,
    dayWidth: Dp,
    daySpacing: Dp,
    contentPadding: Dp,
    hourHeight: Dp,
    onDateChange: (LocalDate) -> Unit,
    onScheduleClick: (Schedule) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    Box(modifier = modifier) {
        schedules.forEach { schedule ->
            val layout = layouts[schedule.id] ?: return@forEach
            val totalColumns = layout.totalColumns.coerceAtLeast(1)
            val laneSpacing = 2.dp
            val cardWidth = ((dayWidth - laneSpacing * (totalColumns - 1).toFloat()) /
                totalColumns.toFloat()).coerceAtLeast(5.dp)
            val dayOffset = contentPadding + (dayWidth + daySpacing) * dayIndex.toFloat()
            val xOffset = dayOffset + (cardWidth + laneSpacing) * layout.column.toFloat()
            val startMinutes = schedule.weekStartMinutesForDay()
            val endMinutes = schedule.weekEndMinutesForDay()
            val rawDuration = (endMinutes - startMinutes).coerceAtLeast(15)
            val topOffset = hourHeight * (startMinutes / 60f)
            val rawHeight = hourHeight * (rawDuration / 60f)
            val cardHeight = rawHeight.coerceAtLeast(30.dp)
            val maxAvailableHeight = (hourHeight * 24f - topOffset).coerceAtLeast(30.dp)
            val visibleCardHeight = cardHeight.coerceAtMost(maxAvailableHeight)

            WeekScheduleCard(
                schedule = schedule,
                cardWidth = cardWidth,
                cardHeight = visibleCardHeight,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = with(density) { xOffset.toPx() }.roundToInt(),
                            y = with(density) { topOffset.toPx() }.roundToInt()
                        )
                    }
                    .width(cardWidth)
                    .height(visibleCardHeight),
                onClick = {
                    onDateChange(date)
                    onScheduleClick(schedule)
                }
            )
        }
    }
}

@Composable
private fun WeekScheduleCard(
    schedule: Schedule,
    cardWidth: Dp,
    cardHeight: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val background = if (schedule.color == Color.White) {
        BeigeBackground.copy(alpha = 0.95f)
    } else {
        schedule.color
    }
    val showText = cardWidth >= 15.dp && cardHeight >= 34.dp
    val verticalText = cardWidth < 34.dp && cardHeight >= 58.dp
    val title = schedule.title.ifBlank { " " }
    val maxChars = when {
        verticalText -> ((cardHeight.value - 10f) / 13f).toInt().coerceIn(1, title.length)
        cardHeight >= 56.dp -> ((cardWidth.value - 7f) / 5.2f).toInt().coerceIn(1, title.length)
        else -> ((cardWidth.value - 6f) / 6.8f).toInt().coerceIn(1, title.length)
    }

    Box(
        modifier = modifier
            .background(background, RoundedCornerShape(9.dp))
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = if (verticalText) 2.dp else 4.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (showText) {
            if (verticalText) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    title.take(maxChars).forEach { char ->
                        Text(
                            text = char.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                lineHeight = 11.sp
                            ),
                            fontWeight = FontWeight.Black,
                            color = Color.Black.copy(alpha = 0.78f),
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Text(
                    text = title.take(maxChars),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = if (cardWidth < 42.dp) 9.sp else 10.sp,
                        lineHeight = 11.sp
                    ),
                    fontWeight = FontWeight.Black,
                    color = Color.Black.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center,
                    maxLines = if (cardHeight >= 56.dp) 2 else 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private data class WeekScheduleLayoutInfo(
    val column: Int,
    val totalColumns: Int
)

private fun computeWeekScheduleLayouts(schedules: List<Schedule>): Map<String, WeekScheduleLayoutInfo> {
    if (schedules.isEmpty()) return emptyMap()

    val sorted = schedules.sortedWith(compareBy({ it.weekStartMinutesForDay() }, { it.title }))
    val n = sorted.size
    val overlaps = Array(n) { BooleanArray(n) }

    for (i in 0 until n) {
        val iStart = sorted[i].weekStartMinutesForDay()
        val iEnd = sorted[i].weekEndMinutesForDay()
        for (j in 0 until n) {
            if (i == j) {
                overlaps[i][j] = true
            } else {
                val jStart = sorted[j].weekStartMinutesForDay()
                val jEnd = sorted[j].weekEndMinutesForDay()
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

    val result = mutableMapOf<String, WeekScheduleLayoutInfo>()
    for (i in 0 until n) {
        val events = mutableListOf<Pair<Int, Int>>()
        for (j in 0 until n) {
            if (overlaps[i][j]) {
                events += sorted[j].weekStartMinutesForDay() to 1
                events += sorted[j].weekEndMinutesForDay() to -1
            }
        }
        events.sortWith(compareBy({ it.first }, { it.second }))

        var current = 0
        var maxConcurrent = 0
        for ((_, delta) in events) {
            current += delta
            maxConcurrent = maxOf(maxConcurrent, current)
        }
        result[sorted[i].id] = WeekScheduleLayoutInfo(columns[i], maxConcurrent.coerceAtLeast(1))
    }

    return result
}

private fun Schedule.weekStartMinutesForDay(): Int =
    ((startTime.hour ?: 0) * 60 + (startTime.minute ?: 0)).coerceIn(0, 24 * 60)

private fun Schedule.weekEndMinutesForDay(): Int {
    val start = weekStartMinutesForDay()
    val end = ((endTime.hour ?: 24) * 60 + (endTime.minute ?: 0)).coerceIn(0, 24 * 60)
    return if (end <= start) (start + 60).coerceAtMost(24 * 60) else end
}

private fun LocalDate.weekdayLabel(): String = when (dayOfWeek) {
    DayOfWeek.MONDAY -> "MON"
    DayOfWeek.TUESDAY -> "TUE"
    DayOfWeek.WEDNESDAY -> "WED"
    DayOfWeek.THURSDAY -> "THU"
    DayOfWeek.FRIDAY -> "FRI"
    DayOfWeek.SATURDAY -> "SAT"
    DayOfWeek.SUNDAY -> "SUN"
}

private fun LocalDate.weekStartDate(): LocalDate =
    with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

private fun weekRubberBandOffset(rawOffset: Float, limit: Float): Float {
    if (limit <= 0f) return 0f
    val magnitude = abs(rawOffset)
    val sign = if (rawOffset < 0f) -1f else 1f
    return sign * limit * (1f - 1f / (magnitude / limit + 1f))
}

private fun Modifier.pinchToZoomWeekTimeline(onScale: (Float) -> Unit): Modifier =
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

private fun Offset.distanceTo(other: Offset): Float {
    val dx = x - other.x
    val dy = y - other.y
    return sqrt(dx * dx + dy * dy)
}
