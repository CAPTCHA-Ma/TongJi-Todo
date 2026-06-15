package com.example.todo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.MutableFloatState
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.todo.ui.theme.BeigeBackground
import androidx.compose.ui.res.stringResource
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private class MonthSwipeAnimationJobs {
    var rubberBackJob: Job? = null
    var switchJob: Job? = null
}

private data class MonthSchedulePageData(
    val dates: List<LocalDate>,
    val schedulesByDate: Map<LocalDate, List<Schedule>>,
    val rowCount: Int
)

@Composable
fun MonthScheduleView(
    selectedDate: LocalDate,
    schedulesForDate: (LocalDate) -> List<Schedule>,
    contentVersion: Int = 0,
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
    val rubberOffsetPx = remember { mutableFloatStateOf(0f) }
    var isSwitching by remember { mutableStateOf(false) }
    val switchProgress = remember { mutableFloatStateOf(1f) }
    val animationJobs = remember { MonthSwipeAnimationJobs() }
    val isSwitchingState by rememberUpdatedState(isSwitching)

    LaunchedEffect(selectedDate) {
        val selectedMonthIndex = selectedDate.toMonthIndex()
        if (!isSwitching && selectedMonthIndex != displayedMonthIndex) {
            displayedMonthIndex = selectedMonthIndex
            targetMonthIndex = null
            previousSelectedEpochDay = null
            transitionDirection = 0
            rubberOffsetPx.floatValue = 0f
            switchProgress.floatValue = 1f
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
            val startOffset = rubberOffsetPx.floatValue
            animationJobs.rubberBackJob = scope.launch {
                Animatable(startOffset).animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 460f)
                ) {
                    rubberOffsetPx.floatValue = value
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
            switchProgress.floatValue = 0f
            onDateChange(nextSelectedDate)

            animationJobs.switchJob = scope.launch {
                try {
                    val startOffset = rubberOffsetPx.floatValue
                    val rubberJob = launch {
                        Animatable(startOffset).animateTo(
                            targetValue = 0f,
                            animationSpec = spring(dampingRatio = 0.68f, stiffness = 520f)
                        ) {
                            rubberOffsetPx.floatValue = value
                        }
                    }
                    val pageJob = launch {
                        Animatable(0f).animateTo(
                            targetValue = 1f,
                            animationSpec = spring(dampingRatio = 0.78f, stiffness = 360f)
                        ) {
                            switchProgress.floatValue = value
                        }
                    }

                    rubberJob.join()
                    pageJob.join()

                    displayedMonthIndex = nextMonthIndex
                    targetMonthIndex = null
                    previousSelectedEpochDay = null
                    transitionDirection = 0
                    rubberOffsetPx.floatValue = 0f
                    switchProgress.floatValue = 1f
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
                                rubberOffsetPx.floatValue = monthRubberBandOffset(totalX, maxRubberPx)
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })

                        if (lockedHorizontal && !isSwitching) {
                            if (abs(totalX) > swipeThresholdPx) {
                                startMonthSwitch(if (totalX < 0f) 1 else -1)
                            } else {
                                animateRubberBack()
                            }
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
                contentVersion = contentVersion,
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
    contentVersion: Int,
    transitionDirection: Int,
    transitionProgress: MutableFloatState,
    rubberOffsetPx: MutableFloatState,
    rubberLimitPx: Float,
    onDateChange: (LocalDate) -> Unit,
    onScheduleClick: (Schedule) -> Unit,
    modifier: Modifier = Modifier
) {
    val monthPageData = remember(month, contentVersion) {
        month.toMonthSchedulePageData(schedulesForDate)
    }
    val targetMonthPageData = remember(targetMonth, contentVersion) {
        targetMonth?.toMonthSchedulePageData(schedulesForDate)
    }
    val activeRows = if (targetMonthPageData != null && transitionDirection != 0) {
        max(monthPageData.rowCount, targetMonthPageData.rowCount).toFloat()
    } else {
        monthPageData.rowCount.toFloat()
    }
    val currentSelectedDate = previousSelectedDate ?: selectedDate

    MonthScheduleGrid(
        dates = monthPageData.dates,
        targetDates = targetMonthPageData?.dates,
        selectedDate = currentSelectedDate,
        targetSelectedDate = selectedDate,
        schedulesByDate = monthPageData.schedulesByDate,
        targetSchedulesByDate = targetMonthPageData?.schedulesByDate,
        activeRows = activeRows,
        transitionDirection = transitionDirection,
        transitionProgress = transitionProgress,
        onDateChange = onDateChange,
        onScheduleClick = onScheduleClick,
        modifier = modifier.graphicsLayer {
            val offset = rubberOffsetPx.floatValue
            val pull = (abs(offset) / rubberLimitPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
            translationX = offset
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
    schedulesByDate: Map<LocalDate, List<Schedule>>,
    targetSchedulesByDate: Map<LocalDate, List<Schedule>>?,
    activeRows: Float,
    transitionDirection: Int,
    transitionProgress: MutableFloatState,
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

        val isTransitioning = targetDates != null && transitionDirection != 0
        val labelDates = if (isTransitioning) targetDates.orEmpty() else dates
        val labelSelectedDate = if (isTransitioning) targetSelectedDate else selectedDate
        val tappableSchedulesByDate = if (isTransitioning) {
            targetSchedulesByDate.orEmpty()
        } else {
            schedulesByDate
        }

        MonthDateLabelsLayer(
            dates = labelDates,
            selectedDate = labelSelectedDate,
            dayWidth = dayWidth,
            dayHeight = dayHeight,
            daySpacing = daySpacing,
            rowSpacing = rowSpacing,
            contentPadding = contentPadding,
            modifier = Modifier.matchParentSize()
        )

        if (isTransitioning) {
            val pageProgress = transitionProgress.floatValue.coerceIn(0f, 1.08f)
            val boundedProgress = pageProgress.coerceIn(0f, 1f)
            MonthScheduleCardsPage(
                dates = dates,
                schedulesByDate = schedulesByDate,
                dayWidth = dayWidth,
                dayHeight = dayHeight,
                daySpacing = daySpacing,
                rowSpacing = rowSpacing,
                contentPadding = contentPadding,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        translationX = -transitionDirection * gridWidthPx * pageProgress
                        alpha = 1f - 0.18f * boundedProgress
                        scaleX = 1f - 0.012f * boundedProgress
                        scaleY = scaleX
                    }
            )
            MonthScheduleCardsPage(
                dates = targetDates.orEmpty(),
                schedulesByDate = targetSchedulesByDate.orEmpty(),
                dayWidth = dayWidth,
                dayHeight = dayHeight,
                daySpacing = daySpacing,
                rowSpacing = rowSpacing,
                contentPadding = contentPadding,
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
            MonthScheduleCardsPage(
                dates = dates,
                schedulesByDate = schedulesByDate,
                dayWidth = dayWidth,
                dayHeight = dayHeight,
                daySpacing = daySpacing,
                rowSpacing = rowSpacing,
                contentPadding = contentPadding,
                modifier = Modifier.matchParentSize()
            )
        }

        MonthTapLayer(
            dates = labelDates,
            schedulesByDate = tappableSchedulesByDate,
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

@Composable
private fun MonthDateLabelsLayer(
    dates: List<LocalDate>,
    selectedDate: LocalDate,
    dayWidth: Dp,
    dayHeight: Dp,
    daySpacing: Dp,
    rowSpacing: Dp,
    contentPadding: Dp,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        val dateBarHeight = 18.dp
        val dateLabelHeight = 18.dp
        val cellPaddingV = 4.dp
        val rowCount = dates.monthRowCount().toInt()

        Canvas(modifier = Modifier.matchParentSize()) {
            val paddingPx = contentPadding.toPx()
            val dayWidthPx = dayWidth.toPx()
            val dayHeightPx = dayHeight.toPx()
            val daySpacingPx = daySpacing.toPx()
            val rowSpacingPx = rowSpacing.toPx()
            val dateBarHeightPx = dateBarHeight.toPx()

            repeat(rowCount) { row ->
                val dayCount = (dates.size - row * 7).coerceIn(0, 7)
                if (dayCount > 0) {
                    val barWidth = dayWidthPx * dayCount + daySpacingPx * (dayCount - 1)
                    val y = paddingPx + (dayHeightPx + rowSpacingPx) * row + 4.dp.toPx()
                    drawRoundRect(
                        color = Color.Black,
                        topLeft = Offset(paddingPx, y),
                        size = Size(barWidth, dateBarHeightPx),
                        cornerRadius = CornerRadius(dateBarHeightPx / 2f, dateBarHeightPx / 2f)
                    )
                }
            }
        }

        dates.forEachIndexed { index, date ->
            val column = index % 7
            val row = index / 7
            val selected = date == selectedDate
            val labelLeft = contentPadding + (dayWidth + daySpacing) * column.toFloat()
            val labelTop = contentPadding + (dayHeight + rowSpacing) * row.toFloat() +
                cellPaddingV

            Box(
                modifier = Modifier
                    .offset(x = labelLeft, y = labelTop)
                    .width(dayWidth)
                    .height(dateLabelHeight)
                    .background(
                        color = if (selected) Color.White else Color.Transparent,
                        shape = RoundedCornerShape(999.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = date.dayOfMonth.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        lineHeight = 10.sp
                    ),
                    fontWeight = FontWeight.Black,
                    color = if (selected) Color.Black else Color.White,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

@Composable
private fun MonthScheduleCardsPage(
    dates: List<LocalDate>,
    schedulesByDate: Map<LocalDate, List<Schedule>>,
    dayWidth: Dp,
    dayHeight: Dp,
    daySpacing: Dp,
    rowSpacing: Dp,
    contentPadding: Dp,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        val dateLabelHeight = 18.dp
        val dateLabelGap = 4.dp
        val cellPaddingH = 3.dp
        val cellPaddingV = 4.dp
        val cardSpacing = 3.dp

        dates.forEachIndexed { index, date ->
            val schedules = schedulesByDate[date].orEmpty()
            if (schedules.isEmpty()) return@forEachIndexed

            val layout = monthCellCardsLayout(dayHeight = dayHeight, scheduleCount = schedules.size)
            if (layout.renderedRows <= 0) return@forEachIndexed

            val column = index % 7
            val row = index / 7
            val cellLeft = contentPadding + (dayWidth + daySpacing) * column.toFloat()
            val cellTop = contentPadding + (dayHeight + rowSpacing) * row.toFloat()
            val cardLeft = cellLeft + cellPaddingH
            val cardAreaTop = cellTop + cellPaddingV + dateLabelHeight + dateLabelGap
            val cardWidth = (dayWidth - cellPaddingH * 2f).coerceAtLeast(1.dp)

            schedules.take(layout.visibleCount).forEachIndexed { scheduleIndex, schedule ->
                val cardTop = cardAreaTop +
                    (layout.cardHeight + cardSpacing) * scheduleIndex.toFloat()
                MonthScheduleCard(
                    schedule = schedule,
                    cardWidth = cardWidth,
                    cardHeight = layout.cardHeight,
                    modifier = Modifier
                        .offset(x = cardLeft, y = cardTop)
                        .width(cardWidth)
                        .height(layout.cardHeight)
                )
            }

            if (layout.moreCount > 0 && layout.availableSlots > 0) {
                val moreTop = cardAreaTop +
                    (layout.cardHeight + cardSpacing) * layout.visibleCount.toFloat()
                MonthMoreCard(
                    moreCount = layout.moreCount,
                    modifier = Modifier
                        .offset(x = cardLeft, y = moreTop)
                        .width(cardWidth)
                        .height(layout.cardHeight)
                )
            }
        }
    }
}

@Composable
private fun MonthScheduleCard(
    schedule: Schedule,
    cardWidth: Dp,
    cardHeight: Dp,
    modifier: Modifier = Modifier
) {
    val background = if (schedule.color == Color.White) {
        BeigeBackground.copy(alpha = 0.95f)
    } else {
        schedule.color
    }
    val showText = cardWidth >= 18.dp && cardHeight >= 16.dp
    val title = schedule.title.ifBlank { " " }
    val maxChars = ((cardWidth.value - 6f) / 5.4f)
        .toInt()
        .coerceIn(1, title.length)

    Box(
        modifier = modifier
            .background(background, RoundedCornerShape(8.dp))
            .padding(horizontal = 2.dp),
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
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MonthMoreCard(
    moreCount: Int,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.48f), shape)
            .border(1.dp, Color.Black.copy(alpha = 0.12f), shape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.more_count, moreCount),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 8.sp,
                lineHeight = 9.sp
            ),
            fontWeight = FontWeight.Black,
            color = Color.Black.copy(alpha = 0.54f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun MonthTapLayer(
    dates: List<LocalDate>,
    schedulesByDate: Map<LocalDate, List<Schedule>>,
    dayWidth: Dp,
    dayHeight: Dp,
    daySpacing: Dp,
    rowSpacing: Dp,
    contentPadding: Dp,
    onDateChange: (LocalDate) -> Unit,
    onScheduleClick: (Schedule) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.pointerInput(
            dates,
            schedulesByDate,
            dayWidth,
            dayHeight,
            daySpacing,
            rowSpacing,
            contentPadding
        ) {
            detectTapGestures { tap ->
                val paddingPx = contentPadding.toPx()
                val dayWidthPx = dayWidth.toPx()
                val dayHeightPx = dayHeight.toPx()
                val daySpacingPx = daySpacing.toPx()
                val rowSpacingPx = rowSpacing.toPx()
                val dateLabelHeightPx = 18.dp.toPx()
                val dateLabelGapPx = 4.dp.toPx()
                val cellPaddingHPx = 3.dp.toPx()
                val cellPaddingVPx = 4.dp.toPx()
                val cardSpacingPx = 3.dp.toPx()
                val minCardHeightPx = 18.dp.toPx()
                val maxCardHeightPx = 30.dp.toPx()

                val column = ((tap.x - paddingPx) / (dayWidthPx + daySpacingPx)).toInt()
                val row = ((tap.y - paddingPx) / (dayHeightPx + rowSpacingPx)).toInt()
                if (column !in 0..6 || row < 0) return@detectTapGestures

                val index = row * 7 + column
                val date = dates.getOrNull(index) ?: return@detectTapGestures
                val cellLeft = paddingPx + column * (dayWidthPx + daySpacingPx)
                val cellTop = paddingPx + row * (dayHeightPx + rowSpacingPx)
                val insideX = tap.x in cellLeft..(cellLeft + dayWidthPx)
                val insideY = tap.y in cellTop..(cellTop + dayHeightPx)
                if (!insideX || !insideY) return@detectTapGestures

                val schedules = schedulesByDate[date].orEmpty()
                val cardAreaTop = cellTop + cellPaddingVPx + dateLabelHeightPx + dateLabelGapPx
                val cardAreaHeight = dayHeightPx - cellPaddingVPx * 2f - dateLabelHeightPx -
                    dateLabelGapPx
                val availableSlots = ((cardAreaHeight + cardSpacingPx) /
                    (minCardHeightPx + cardSpacingPx)).toInt().coerceAtLeast(0)
                val visibleCount = when {
                    availableSlots <= 0 -> 0
                    schedules.size > availableSlots && availableSlots > 1 -> availableSlots - 1
                    else -> schedules.size.coerceAtMost(availableSlots)
                }
                val moreCount = schedules.size - visibleCount
                val renderedRows = visibleCount + if (moreCount > 0 && availableSlots > 0) 1 else 0
                val rawCardHeight = if (renderedRows > 0) {
                    (cardAreaHeight - cardSpacingPx * (renderedRows - 1)) / renderedRows
                } else {
                    0f
                }
                val cardHeight = rawCardHeight.coerceIn(minCardHeightPx, maxCardHeightPx)
                val cardLeft = cellLeft + cellPaddingHPx
                val cardRight = cellLeft + dayWidthPx - cellPaddingHPx

                if (tap.x in cardLeft..cardRight && tap.y >= cardAreaTop && visibleCount > 0) {
                    val relativeY = tap.y - cardAreaTop
                    val cardIndex = (relativeY / (cardHeight + cardSpacingPx)).toInt()
                    val cardTop = cardAreaTop + cardIndex * (cardHeight + cardSpacingPx)
                    if (cardIndex in 0 until visibleCount && tap.y <= cardTop + cardHeight) {
                        onDateChange(date)
                        onScheduleClick(schedules[cardIndex])
                        return@detectTapGestures
                    }
                }

                onDateChange(date)
            }
        }
    )
}

private data class MonthCellCardsLayout(
    val availableSlots: Int,
    val visibleCount: Int,
    val moreCount: Int,
    val renderedRows: Int,
    val cardHeight: Dp
)

private fun monthCellCardsLayout(dayHeight: Dp, scheduleCount: Int): MonthCellCardsLayout {
    val dateLabelHeight = 18.dp
    val dateLabelGap = 4.dp
    val cellPaddingV = 4.dp
    val cardSpacing = 3.dp
    val minCardHeight = 18.dp
    val maxCardHeight = 30.dp
    val cardAreaHeight = (dayHeight - cellPaddingV * 2f - dateLabelHeight - dateLabelGap)
        .coerceAtLeast(0.dp)
    val availableSlots = ((cardAreaHeight.value + cardSpacing.value) /
        (minCardHeight.value + cardSpacing.value)).toInt().coerceAtLeast(0)
    val visibleCount = when {
        availableSlots <= 0 -> 0
        scheduleCount > availableSlots && availableSlots > 1 -> availableSlots - 1
        else -> scheduleCount.coerceAtMost(availableSlots)
    }
    val moreCount = scheduleCount - visibleCount
    val renderedRows = visibleCount + if (moreCount > 0 && availableSlots > 0) 1 else 0
    val rawCardHeight = if (renderedRows > 0) {
        (cardAreaHeight.value - cardSpacing.value * (renderedRows - 1)) / renderedRows
    } else {
        0f
    }
    val cardHeight = rawCardHeight.coerceIn(minCardHeight.value, maxCardHeight.value).dp
    return MonthCellCardsLayout(
        availableSlots = availableSlots,
        visibleCount = visibleCount,
        moreCount = moreCount,
        renderedRows = renderedRows,
        cardHeight = cardHeight
    )
}

private fun monthRubberBandOffset(rawOffset: Float, limit: Float): Float {
    if (limit <= 0f) return 0f
    val magnitude = abs(rawOffset)
    val sign = if (rawOffset < 0f) -1f else 1f
    return sign * limit * (1f - 1f / (magnitude / limit + 1f))
}

private fun YearMonth.datesInMonth(): List<LocalDate> =
    (1..lengthOfMonth()).map { day -> atDay(day) }

private fun YearMonth.toMonthSchedulePageData(
    schedulesForDate: (LocalDate) -> List<Schedule>
): MonthSchedulePageData {
    val dates = datesInMonth()
    return MonthSchedulePageData(
        dates = dates,
        schedulesByDate = dates.associateWith(schedulesForDate),
        rowCount = dates.monthRowCount()
    )
}

private fun List<LocalDate>.monthRowCount(): Int {
    if (isEmpty()) return 1
    return (size + 6) / 7
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
