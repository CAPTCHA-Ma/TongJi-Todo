package com.example.todo

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private class MonthSwipeAnimationJobs {
    var rubberBackJob: Job? = null
    var switchJob: Job? = null
}

@Composable
fun MonthScheduleView(
    selectedDate: LocalDate,
    schedulesForDate: (LocalDate) -> List<Schedule>,
    onDateChange: (LocalDate) -> Unit,
    onScheduleClick: (Schedule) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var displayedMonthIndex by rememberSaveable {
        mutableLongStateOf(selectedDate.toMonthIndex())
    }
    var targetMonthIndex by remember { mutableStateOf<Long?>(null) }
    var previousSelectedEpochDay by remember { mutableStateOf<Long?>(null) }
    var transitionDirection by remember { mutableStateOf(0) }
    var rubberOffsetPx by remember { mutableFloatStateOf(0f) }
    var isSwitching by remember { mutableStateOf(false) }
    var switchProgress by remember { mutableFloatStateOf(1f) }
    val animationJobs = remember { MonthSwipeAnimationJobs() }
    val isSwitchingState by rememberUpdatedState(isSwitching)

    LaunchedEffect(selectedDate) {
        val selectedMonthIndex = selectedDate.toMonthIndex()
        if (!isSwitching && selectedMonthIndex != displayedMonthIndex) {
            displayedMonthIndex = selectedMonthIndex
            targetMonthIndex = null
            previousSelectedEpochDay = null
            transitionDirection = 0
            rubberOffsetPx = 0f
            switchProgress = 1f
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .padding(horizontal = 14.dp, vertical = 2.dp)
            .padding(bottom = 12.dp)
            .clipToBounds()
    ) {
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val maxRubberPx = with(density) { 76.dp.toPx() }
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

        fun startMonthSwitch(direction: Int) {
            if (isSwitching || direction == 0 || animationJobs.switchJob?.isActive == true) return
            animationJobs.rubberBackJob?.cancel()
            animationJobs.rubberBackJob = null
            isSwitching = true

            val fromMonth = displayedMonthIndex.toYearMonth()
            val nextMonth = fromMonth.plusMonths(direction.toLong())
            val nextMonthIndex = nextMonth.toMonthIndex()
            val nextSelectedDay = selectedDate.dayOfMonth.coerceAtMost(nextMonth.lengthOfMonth())
            val nextSelectedDate = nextMonth.atDay(nextSelectedDay)

            previousSelectedEpochDay = selectedDate.toEpochDay()
            transitionDirection = direction
            targetMonthIndex = nextMonthIndex
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

                    displayedMonthIndex = nextMonthIndex
                    targetMonthIndex = null
                    previousSelectedEpochDay = null
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
                .pointerInput(widthPx, displayedMonthIndex) {
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
                                rubberOffsetPx = monthRubberBandOffset(totalX, maxRubberPx)
                                change.consume()
                                if (abs(totalX) > swipeThresholdPx) {
                                    switchTriggered = true
                                    startMonthSwitch(if (totalX < 0f) 1 else -1)
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
            val displayedMonth = remember(displayedMonthIndex) {
                displayedMonthIndex.toYearMonth()
            }
            val targetMonth = targetMonthIndex?.let { it.toYearMonth() }
            val previousSelectedDate = previousSelectedEpochDay?.let { LocalDate.ofEpochDay(it) }

            MonthSchedulePage(
                month = displayedMonth,
                targetMonth = targetMonth,
                selectedDate = selectedDate,
                previousSelectedDate = previousSelectedDate,
                schedulesForDate = schedulesForDate,
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
private fun MonthSchedulePage(
    month: YearMonth,
    targetMonth: YearMonth?,
    selectedDate: LocalDate,
    previousSelectedDate: LocalDate?,
    schedulesForDate: (LocalDate) -> List<Schedule>,
    transitionDirection: Int,
    transitionProgress: Float,
    rubberOffsetPx: Float,
    rubberLimitPx: Float,
    onDateChange: (LocalDate) -> Unit,
    onScheduleClick: (Schedule) -> Unit,
    modifier: Modifier = Modifier
) {
    val monthDates = remember(month) { month.datesInMonth() }
    val targetMonthDates = remember(targetMonth) { targetMonth?.datesInMonth() }
    val currentRows = monthDates.monthRowCount()
    val targetRows = targetMonthDates?.monthRowCount() ?: currentRows
    val progress = if (targetMonthDates != null && transitionDirection != 0) {
        transitionProgress.coerceIn(0f, 1f)
    } else {
        1f
    }
    val activeRows = if (targetMonthDates != null && transitionDirection != 0) {
        currentRows + (targetRows - currentRows) * progress
    } else {
        currentRows.toFloat()
    }
    val currentSelectedDate = previousSelectedDate ?: selectedDate
    val pull = (abs(rubberOffsetPx) / rubberLimitPx.coerceAtLeast(1f)).coerceIn(0f, 1f)

    MonthScheduleGrid(
        dates = monthDates,
        targetDates = targetMonthDates,
        selectedDate = currentSelectedDate,
        targetSelectedDate = selectedDate,
        schedulesForDate = schedulesForDate,
        activeRows = activeRows,
        transitionDirection = transitionDirection,
        transitionProgress = transitionProgress,
        onDateChange = onDateChange,
        onScheduleClick = onScheduleClick,
        modifier = modifier.graphicsLayer {
            translationX = rubberOffsetPx
            scaleX = 1f - pull * 0.01f
            scaleY = 1f - pull * 0.005f
        }
    )
}

@Composable
private fun MonthScheduleGrid(
    dates: List<LocalDate>,
    targetDates: List<LocalDate>?,
    selectedDate: LocalDate,
    targetSelectedDate: LocalDate,
    schedulesForDate: (LocalDate) -> List<Schedule>,
    activeRows: Float,
    transitionDirection: Int,
    transitionProgress: Float,
    onDateChange: (LocalDate) -> Unit,
    onScheduleClick: (Schedule) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val gridShape = RoundedCornerShape(24.dp)

    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .border(2.dp, Color.Black.copy(alpha = 0.16f), gridShape)
            .background(Color.White.copy(alpha = 0.18f), gridShape)
    ) {
        val contentPadding = 7.dp
        val daySpacing = 4.dp
        val rowSpacing = 5.dp
        val rows = activeRows.coerceAtLeast(1f)
        val dayWidth = ((maxWidth - contentPadding * 2f - daySpacing * 6f) / 7f)
            .coerceAtLeast(1.dp)
        val dayHeight = ((maxHeight - contentPadding * 2f - rowSpacing * (rows - 1f)) / rows)
            .coerceAtLeast(32.dp)
        val gridWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val rowLineCount = (ceil(rows.toDouble()).toInt() - 1).coerceAtLeast(0)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val paddingPx = contentPadding.toPx()
            val dayWidthPx = dayWidth.toPx()
            val daySpacingPx = daySpacing.toPx()
            val dayHeightPx = dayHeight.toPx()
            val rowSpacingPx = rowSpacing.toPx()

            for (column in 1..6) {
                val x = paddingPx + column * dayWidthPx + (column - 0.5f) * daySpacingPx
                drawLine(
                    color = Color.Black.copy(alpha = 0.065f),
                    start = Offset(x, paddingPx),
                    end = Offset(x, size.height - paddingPx),
                    strokeWidth = 1f
                )
            }

            for (row in 1..rowLineCount) {
                val y = paddingPx + row * dayHeightPx + (row - 0.5f) * rowSpacingPx
                drawLine(
                    color = Color.Black.copy(alpha = 0.075f),
                    start = Offset(paddingPx, y),
                    end = Offset(size.width - paddingPx, y),
                    strokeWidth = 1f
                )
            }
        }

        if (targetDates != null && transitionDirection != 0) {
            val pageProgress = transitionProgress.coerceIn(0f, 1.08f)
            val boundedProgress = pageProgress.coerceIn(0f, 1f)
            MonthDaysPage(
                dates = dates,
                selectedDate = selectedDate,
                schedulesForDate = schedulesForDate,
                dayWidth = dayWidth,
                dayHeight = dayHeight,
                daySpacing = daySpacing,
                rowSpacing = rowSpacing,
                contentPadding = contentPadding,
                onDateChange = onDateChange,
                onScheduleClick = onScheduleClick,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        translationX = -transitionDirection * gridWidthPx * pageProgress
                        alpha = 1f - 0.18f * boundedProgress
                        scaleX = 1f - 0.012f * boundedProgress
                        scaleY = scaleX
                    }
            )
            MonthDaysPage(
                dates = targetDates,
                selectedDate = targetSelectedDate,
                schedulesForDate = schedulesForDate,
                dayWidth = dayWidth,
                dayHeight = dayHeight,
                daySpacing = daySpacing,
                rowSpacing = rowSpacing,
                contentPadding = contentPadding,
                onDateChange = onDateChange,
                onScheduleClick = onScheduleClick,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        translationX = transitionDirection * gridWidthPx * (1f - pageProgress)
                        alpha = 0.74f + 0.26f * boundedProgress
                        scaleX = 0.988f + 0.012f * boundedProgress
                        scaleY = scaleX
                    }
            )
        } else {
            MonthDaysPage(
                dates = dates,
                selectedDate = selectedDate,
                schedulesForDate = schedulesForDate,
                dayWidth = dayWidth,
                dayHeight = dayHeight,
                daySpacing = daySpacing,
                rowSpacing = rowSpacing,
                contentPadding = contentPadding,
                onDateChange = onDateChange,
                onScheduleClick = onScheduleClick,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

@Composable
private fun MonthDaysPage(
    dates: List<LocalDate>,
    selectedDate: LocalDate,
    schedulesForDate: (LocalDate) -> List<Schedule>,
    dayWidth: Dp,
    dayHeight: Dp,
    daySpacing: Dp,
    rowSpacing: Dp,
    contentPadding: Dp,
    onDateChange: (LocalDate) -> Unit,
    onScheduleClick: (Schedule) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    Box(modifier = modifier) {
        val dateBarHeight = 18.dp
        val rowCount = dates.monthRowCount().toInt()

        repeat(rowCount) { row ->
            val dayCount = (dates.size - row * 7).coerceIn(0, 7)
            if (dayCount > 0) {
                val barWidth = dayWidth * dayCount.toFloat() + daySpacing * (dayCount - 1).toFloat()
                val yOffset = contentPadding + (dayHeight + rowSpacing) * row.toFloat() + 4.dp

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = with(density) { contentPadding.toPx() }.roundToInt(),
                                y = with(density) { yOffset.toPx() }.roundToInt()
                            )
                        }
                        .width(barWidth)
                        .height(dateBarHeight)
                        .background(Color.Black, RoundedCornerShape(999.dp))
                )
            }
        }

        dates.forEachIndexed { index, date ->
            val column = index % 7
            val row = index / 7
            val xOffset = contentPadding + (dayWidth + daySpacing) * column.toFloat()
            val yOffset = contentPadding + (dayHeight + rowSpacing) * row.toFloat()

            MonthDayCell(
                date = date,
                selected = date == selectedDate,
                schedules = schedulesForDate(date),
                dayWidth = dayWidth,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = with(density) { xOffset.toPx() }.roundToInt(),
                            y = with(density) { yOffset.toPx() }.roundToInt()
                        )
                    }
                    .width(dayWidth)
                    .height(dayHeight),
                onDateClick = { onDateChange(date) },
                onScheduleClick = { schedule ->
                    onDateChange(date)
                    onScheduleClick(schedule)
                }
            )
        }
    }
}

@Composable
private fun MonthDayCell(
    date: LocalDate,
    selected: Boolean,
    schedules: List<Schedule>,
    dayWidth: Dp,
    modifier: Modifier = Modifier,
    onDateClick: () -> Unit,
    onScheduleClick: (Schedule) -> Unit
) {
    Column(
        modifier = modifier
            .clipToBounds()
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onDateClick
            )
            .padding(horizontal = 3.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MonthDateLabel(
            day = date.dayOfMonth,
            selected = selected,
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val cardSpacing = 3.dp
            val minCardHeight = 18.dp
            val maxCardHeight = 30.dp
            val availableSlots = ((maxHeight.value + cardSpacing.value) /
                (minCardHeight.value + cardSpacing.value)).toInt().coerceAtLeast(0)
            val visibleCount = when {
                availableSlots <= 0 -> 0
                schedules.size > availableSlots && availableSlots > 1 -> availableSlots - 1
                else -> schedules.size.coerceAtMost(availableSlots)
            }
            val moreCount = schedules.size - visibleCount
            val rowCount = visibleCount + if (moreCount > 0 && availableSlots > 0) 1 else 0
            val rawCardHeight = if (rowCount > 0) {
                (maxHeight - cardSpacing * (rowCount - 1).toFloat()) / rowCount.toFloat()
            } else {
                0.dp
            }
            val cardHeight = rawCardHeight.coerceIn(minCardHeight, maxCardHeight)

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(cardSpacing),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                schedules.take(visibleCount).forEach { schedule ->
                    MonthScheduleCard(
                        schedule = schedule,
                        cardWidth = dayWidth,
                        cardHeight = cardHeight,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(cardHeight),
                        onClick = { onScheduleClick(schedule) }
                    )
                }
                if (moreCount > 0 && availableSlots > 0) {
                    MonthMoreCard(
                        count = moreCount,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(cardHeight)
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthDateLabel(
    day: Int,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val background by animateColorAsState(
        targetValue = if (selected) Color.White else Color.Transparent,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "MonthDateLabelBackground"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) Color.Black else Color.White,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "MonthDateLabelContent"
    )
    val selectedScale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "MonthDateLabelScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = selectedScale
                scaleY = selectedScale
            }
            .background(background, RoundedCornerShape(999.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = day.toString().padStart(2, '0'),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                lineHeight = 10.sp
            ),
            fontWeight = FontWeight.Black,
            color = contentColor,
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun MonthScheduleCard(
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
    val title = schedule.title.ifBlank { " " }
    val showText = cardWidth >= 18.dp && cardHeight >= 16.dp
    val maxChars = ((cardWidth.value - 6f) / 5.4f).toInt().coerceIn(1, title.length)

    Box(
        modifier = modifier
            .background(background, RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 3.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        if (showText) {
            Text(
                text = title.take(maxChars),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (cardWidth < 42.dp) 8.sp else 9.sp,
                    lineHeight = 10.sp
                ),
                fontWeight = FontWeight.Black,
                color = Color.Black.copy(alpha = 0.78f),
                textAlign = TextAlign.Center,
                maxLines = if (cardHeight >= 27.dp) 2 else 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MonthMoreCard(
    count: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.48f), RoundedCornerShape(8.dp))
            .border(1.dp, Color.Black.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 3.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+$count",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 8.sp,
                lineHeight = 9.sp
            ),
            fontWeight = FontWeight.Black,
            color = Color.Black.copy(alpha = 0.54f),
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Clip
        )
    }
}

private fun monthRubberBandOffset(rawOffset: Float, limit: Float): Float {
    if (limit <= 0f) return 0f
    val magnitude = abs(rawOffset)
    val sign = if (rawOffset < 0f) -1f else 1f
    return sign * limit * (1f - 1f / (magnitude / limit + 1f))
}

private fun YearMonth.datesInMonth(): List<LocalDate> =
    (1..lengthOfMonth()).map { day -> atDay(day) }

private fun List<LocalDate>.monthRowCount(): Float {
    if (isEmpty()) return 1f
    return ((size + 6) / 7).toFloat()
}

private fun LocalDate.toMonthIndex(): Long =
    YearMonth.from(this).toMonthIndex()

private fun YearMonth.toMonthIndex(): Long =
    year * 12L + (monthValue - 1L)

private fun Long.toYearMonth(): YearMonth {
    val year = (this / 12L).toInt()
    val month = (this % 12L).toInt() + 1
    return YearMonth.of(year, month)
}
