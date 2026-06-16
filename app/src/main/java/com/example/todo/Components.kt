package com.example.todo


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring.DampingRatioLowBouncy
import androidx.compose.animation.core.Spring.StiffnessMedium
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.todo.ui.theme.*
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt

// Define the custom font family
val ChappaBlack = FontFamily(
    Font(R.font.chappablack, FontWeight.Black)
)

private const val PlannerStackMaxVisibleItems = 3
private val PlannerStackCardHeight = 66.dp
private val PlannerStackCardSpacing = 8.dp

private data class PlannerStackTransition(
    val previousVisibleKeys: Set<Any>,
    val currentVisibleKeys: Set<Any>,
    val previousOrderKeys: List<Any>,
    val currentOrderKeys: List<Any>,
    val previousCardHeight: Dp,
    val currentCardHeight: Dp
)

private fun lerpDp(start: Dp, end: Dp, fraction: Float): Dp =
    start + (end - start) * fraction.coerceIn(0f, 1f)

@Composable
fun <T> PlannerCardStack(
    items: List<T>,
    itemKey: (T) -> Any,
    modifier: Modifier = Modifier,
    maxVisibleItems: Int = PlannerStackMaxVisibleItems,
    cardHeight: Dp = PlannerStackCardHeight,
    cardSpacing: Dp = PlannerStackCardSpacing,
    cardContent: @Composable (T, Modifier) -> Unit
) {
    val visibleLimit = maxVisibleItems.coerceAtLeast(0)
    val visibleItems = remember(items, visibleLimit) { items.take(visibleLimit) }
    val visibleKeySet = visibleItems.map(itemKey).toSet()
    val latestVisibleKeySet by rememberUpdatedState(visibleKeySet)
    val stackedItems = remember {
        mutableStateListOf<T>().apply { addAll(visibleItems) }
    }
    val stackProgress = remember { Animatable(1f) }

    val stackHeight = cardHeight * visibleLimit.toFloat() +
        cardSpacing * (visibleLimit - 1).coerceAtLeast(0).toFloat()
    val visibleCount = visibleItems.size
    val targetCardHeight = if (visibleCount == 0) {
        0.dp
    } else {
        (stackHeight - cardSpacing * (visibleCount - 1).toFloat()) / visibleCount.toFloat()
    }
    var transition by remember {
        val initialKeys = visibleItems.map(itemKey)
        mutableStateOf(
            PlannerStackTransition(
                previousVisibleKeys = visibleKeySet,
                currentVisibleKeys = visibleKeySet,
                previousOrderKeys = initialKeys,
                currentOrderKeys = initialKeys,
                previousCardHeight = targetCardHeight,
                currentCardHeight = targetCardHeight
            )
        )
    }

    LaunchedEffect(visibleItems) {
        val previousItems = stackedItems.toList()
        val previousKeys = previousItems.map(itemKey)
        val previousIndexByKey = previousItems
            .mapIndexed { index, item -> itemKey(item) to index }
            .toMap()
        val exitingItems = previousItems.filter { itemKey(it) !in visibleKeySet }
        val insertedExitingKeys = mutableSetOf<Any>()
        val mergedItems = mutableListOf<T>()

        visibleItems.forEachIndexed { visibleIndex, item ->
            exitingItems
                .filter { exiting ->
                    val key = itemKey(exiting)
                    key !in insertedExitingKeys &&
                        (previousIndexByKey[key] ?: Int.MAX_VALUE) <= visibleIndex
                }
                .forEach { exiting ->
                    insertedExitingKeys += itemKey(exiting)
                    mergedItems += exiting
                }

            mergedItems += item
        }

        exitingItems
            .filter { insertedExitingKeys.add(itemKey(it)) }
            .forEach { mergedItems += it }

        val mergedKeys = mergedItems.map(itemKey)
        val visibleKeysChanged = transition.currentVisibleKeys != visibleKeySet
        val renderedItemsChanged = previousKeys != mergedKeys || previousItems != mergedItems

        if (!visibleKeysChanged && !renderedItemsChanged) return@LaunchedEffect

        transition = PlannerStackTransition(
            previousVisibleKeys = transition.currentVisibleKeys,
            currentVisibleKeys = visibleKeySet,
            previousOrderKeys = transition.currentOrderKeys,
            currentOrderKeys = mergedKeys,
            previousCardHeight = transition.currentCardHeight,
            currentCardHeight = targetCardHeight
        )

        if (renderedItemsChanged) {
            stackedItems.clear()
            stackedItems.addAll(mergedItems)
        }

        stackProgress.snapTo(0f)
        stackProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
        )

        val visibleKeysAfterAnimation = latestVisibleKeySet
        stackedItems.removeAll { itemKey(it) !in visibleKeysAfterAnimation }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(stackHeight)
            .clipToBounds()
    ) {
        val stackSnapshot = stackedItems.toList()

        stackSnapshot.forEach { item ->
            val cardKey = itemKey(item)
            key(cardKey) {
                fun hasVisibleAfter(orderKeys: List<Any>, visibleKeys: Set<Any>): Boolean {
                    val keyIndex = orderKeys.indexOf(cardKey)
                    if (keyIndex < 0) return false
                    return orderKeys
                        .drop(keyIndex + 1)
                        .any { it in visibleKeys }
                }

                val progress = stackProgress.value
                val startHeight = if (cardKey in transition.previousVisibleKeys) {
                    transition.previousCardHeight
                } else {
                    0.dp
                }
                val endHeight = if (cardKey in transition.currentVisibleKeys) {
                    transition.currentCardHeight
                } else {
                    0.dp
                }
                val startGap = if (
                    cardKey in transition.previousVisibleKeys &&
                    hasVisibleAfter(transition.previousOrderKeys, transition.previousVisibleKeys)
                ) {
                    cardSpacing
                } else {
                    0.dp
                }
                val endGap = if (
                    cardKey in transition.currentVisibleKeys &&
                    hasVisibleAfter(transition.currentOrderKeys, transition.currentVisibleKeys)
                ) {
                    cardSpacing
                } else {
                    0.dp
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(lerpDp(startHeight, endHeight, progress))
                        .clipToBounds()
                ) {
                    cardContent(item, Modifier.fillMaxSize())
                }

                Spacer(modifier = Modifier.height(lerpDp(startGap, endGap, progress)))
            }
        }
    }
}

@Composable
fun BaseCard(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    fillMaxHeight: Boolean = false,
    titleColor: Color = Color.Black,
    titleTextDecoration: TextDecoration? = null,
    backgroundColor: Color = Color.White.copy(alpha = 0.55f),
    borderColor: Color = Color.Black.copy(alpha = 0.3f),
    leftContent: @Composable BoxScope.() -> Unit = {},
    rightContent: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fillMaxHeight) Modifier.fillMaxHeight() else Modifier)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            )
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(32.dp)
            )
            .background(backgroundColor, RoundedCornerShape(32.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        // Left Slot
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart),
            content = leftContent
        )

        // Center Title - Fixed in position
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(lineHeight = 26.sp),
            color = titleColor,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            textDecoration = titleTextDecoration,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 88.dp)
        )

        // Right Slot
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd),
            content = rightContent
        )
    }
}

@Composable
fun StretchedHeader(
    text: String,
    fontSize: TextUnit = 135.sp
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, bottom = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontSize = fontSize,
                fontFamily = ChappaBlack,
                fontWeight = FontWeight.Black,
                color = Color.Black,
                textAlign = TextAlign.Center,
                letterSpacing = 12.sp
            ),
            softWrap = false
        )
    }
}

/**
 * A horizontally-swipeable date header for navigating between adjacent days.
 *
 * Swipe left  → next day (today → tomorrow).
 * Swipe right → previous day (tomorrow → today).
 *
 * Small rounded triangles on the left / right hint whether further navigation
 * is available. The triangles hide during drag and fade back in after the date
 * has settled, avoiding visual clutter.
 *
 * @param dateText       The label to display (e.g. "JUN04").
 * @param canSwipeLeft   `true` when there is a previous day to navigate to.
 * @param canSwipeRight  `true` when there is a next day to navigate to.
 * @param onSwipeLeft    Called after the slide-out when swiping to the previous day.
 * @param onSwipeRight   Called after the slide-out when swiping to the next day.
 */
@Composable
fun SwipeableDateHeader(
    dateText: String,
    canSwipeLeft: Boolean,
    canSwipeRight: Boolean,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()

    val slideDistancePx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val swipeThresholdPx = with(density) { 80.dp.toPx() }

    val animatedOffset = remember { Animatable(0f) }
    var isAnimating by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }

    // Triangles are visible only when settled (not dragging, not animating).
    // A small delay + fade-in avoids an abrupt reappearance.
    var showTriangles by remember { mutableStateOf(true) }

    // Hold latest callback / state refs so pointerInput(Unit) never restarts
    val canSwipeLeftState = rememberUpdatedState(canSwipeLeft)
    val canSwipeRightState = rememberUpdatedState(canSwipeRight)
    val onSwipeLeftState = rememberUpdatedState(onSwipeLeft)
    val onSwipeRightState = rememberUpdatedState(onSwipeRight)

    val triangleSize = 22.dp
    // Slightly larger so the rounded stroke caps don't get clipped by AnimatedVisibility.
    val triangleBoxSize = triangleSize + 6.dp

    LaunchedEffect(isDragging, isAnimating) {
        if (isDragging || isAnimating) {
            showTriangles = false
        } else {
            delay(350)
            showTriangles = true
        }
    }

    fun performTransition(target: Float, onComplete: () -> Unit) {
        scope.launch {
            isAnimating = true
            animatedOffset.animateTo(target, tween(durationMillis = 200))
            onComplete()
            animatedOffset.snapTo(-target)
            animatedOffset.animateTo(0f, tween(durationMillis = 200))
            isAnimating = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 40.dp, bottom = 20.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = {
                        isDragging = false
                        if (!isAnimating) {
                            scope.launch {
                                val offset = animatedOffset.value
                                val cl = canSwipeLeftState.value
                                val cr = canSwipeRightState.value

                                if (abs(offset) > swipeThresholdPx) {
                                    when {
                                        offset > 0 && cl -> performTransition(slideDistancePx, onSwipeLeftState.value)
                                        offset < 0 && cr -> performTransition(-slideDistancePx, onSwipeRightState.value)
                                        else -> animatedOffset.animateTo(
                                            0f, spring(DampingRatioLowBouncy, StiffnessMedium)
                                        )
                                    }
                                } else {
                                    animatedOffset.animateTo(
                                        0f, spring(DampingRatioLowBouncy, StiffnessMedium)
                                    )
                                }
                            }
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                        scope.launch {
                            animatedOffset.animateTo(0f, spring(DampingRatioLowBouncy, StiffnessMedium))
                        }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        if (!isAnimating) {
                            scope.launch {
                                val cl = canSwipeLeftState.value
                                val cr = canSwipeRightState.value
                                val damped = when {
                                    !cl && dragAmount > 0 -> dragAmount * 0.3f
                                    !cr && dragAmount < 0 -> dragAmount * 0.3f
                                    else -> dragAmount
                                }
                                animatedOffset.snapTo(animatedOffset.value + damped)
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Date label — bottom layer, centred.  At 135 sp the text is wider than the
        // screen so it naturally overflows; that is fine as long as the triangles draw
        // on top at the viewport edges.
        Text(
            text = dateText,
            style = TextStyle(
                fontSize = 148.sp,
                fontFamily = ChappaBlack,
                fontWeight = FontWeight.Black,
                color = Color.Black,
                textAlign = TextAlign.Center,
                letterSpacing = 12.sp
            ),
            softWrap = false,
            modifier = Modifier
                .align(Alignment.Center)
                .offset { IntOffset(animatedOffset.value.roundToInt(), 0) }
        )

        // Left triangle — tip ←, hinting "swipe left to go back".
        // Slides together with the text.  Hidden during drag / transition.
        AnimatedVisibility(
            visible = showTriangles && canSwipeLeft,
            enter = fadeIn(tween(800)),
            exit = fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(animatedOffset.value.roundToInt(), 0) }
                .zIndex(1f)
        ) {
            // Outer Box gives the rounded stroke room to breathe so
            // AnimatedVisibility doesn't clip the caps during fade-in.
            Box(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(triangleBoxSize)
            ) {
                Canvas(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(triangleSize)
                ) {
                    drawRoundedTriangle(pointingLeft = true)
                }
            }
        }

        // Right triangle — tip →, hinting "swipe right to go forward".
        AnimatedVisibility(
            visible = showTriangles && canSwipeRight,
            enter = fadeIn(tween(800)),
            exit = fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset { IntOffset(animatedOffset.value.roundToInt(), 0) }
                .zIndex(1f)
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(triangleBoxSize)
            ) {
                Canvas(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(triangleSize)
                ) {
                    drawRoundedTriangle(pointingLeft = false)
                }
            }
        }
    }
}

/**
 * Draws a small equilateral triangle whose corners are naturally rounded
 * by using a thick stroke with [StrokeCap.Round] and [StrokeJoin.Round].
 *
 * The caller wraps the [Canvas] in a slightly larger [Box] so that the
 * rounded stroke caps / joins never overflow and get clipped by parent
 * containers such as [AnimatedVisibility] during fade-in.
 *
 * The result is a smooth, pill-like triangle that blends with the heavy
 * rounded letterforms of the ChappaBlack font.
 */
private fun DrawScope.drawRoundedTriangle(pointingLeft: Boolean) {
    val w = size.width
    val h = size.height
    val strokePx = 5.dp.toPx()

    // Minimal inset — just enough to keep the visual stroke comfortably
    // inside this Canvas.  The outer Box handles clip-proofing.
    val inset = with(density) { strokePx / 4f / w }

    // Compact equilateral triangle that fills most of the canvas.
    //   pointingLeft  = true  → tip on the left  ←
    //   pointingLeft  = false → tip on the right →
    val path = if (pointingLeft) {
        Path().apply {
            moveTo(w * (0.50f - inset), h * (0.27f + inset))
            lineTo(w * (0.15f + inset), h * 0.5f)
            lineTo(w * (0.50f - inset), h * (0.73f - inset))
            close()
        }
    } else {
        Path().apply {
            moveTo(w * (0.50f + inset), h * (0.27f + inset))
            lineTo(w * (0.85f - inset), h * 0.5f)
            lineTo(w * (0.50f + inset), h * (0.73f - inset))
            close()
        }
    }

    drawPath(
        path = path,
        color = Color.Black,
        style = Stroke(
            width = strokePx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

@Composable
fun ScheduleCard(
    schedule: Schedule,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    fillMaxHeight: Boolean = false
) {
    BaseCard(
        title = schedule.title,
        modifier = modifier,
        onClick = onClick,
        fillMaxHeight = fillMaxHeight,
        leftContent = {
            Text(
                text = schedule.startTime.toSmartString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.width(68.dp)
            )
        },
        rightContent = {
            Text(
                text = schedule.endTime.toSmartString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.width(68.dp),
                textAlign = TextAlign.End
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCard(
    task: Task,
    modifier: Modifier = Modifier,
    onComplete: (Task) -> Unit,
    onRestore: (Task) -> Unit = {},
    onClick: () -> Unit = {},
    fillMaxHeight: Boolean = false,
    completed: Boolean = task.isCompleted
) {
    key(task.id, completed) {
        val density = LocalDensity.current
        val configuration = LocalConfiguration.current
        val scope = rememberCoroutineScope()
        val direction = if (completed) -1f else 1f
        val dismissThresholdPx = with(density) { 96.dp.toPx() }
        val dismissDistancePx = with(density) { (configuration.screenWidthDp.dp + 96.dp).toPx() }
        var dragOffsetPx by remember(task.id, completed) { mutableFloatStateOf(0f) }
        var actionSubmitted by remember(task.id, completed) { mutableStateOf(false) }
        var offsetAnimationJob by remember(task.id, completed) { mutableStateOf<Job?>(null) }

        fun animateOffsetTo(
            target: Float,
            onFinished: () -> Unit = {}
        ) {
            offsetAnimationJob?.cancel()
            val startOffset = dragOffsetPx
            offsetAnimationJob = scope.launch {
                Animatable(startOffset).animateTo(
                    targetValue = target,
                    animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing)
                ) {
                    dragOffsetPx = value
                }
                dragOffsetPx = target
                onFinished()
            }
        }

        val swipeModifier = Modifier.pointerInput(task.id, completed, dismissThresholdPx, dismissDistancePx) {
            detectHorizontalDragGestures(
                onDragStart = {
                    if (!actionSubmitted) {
                        offsetAnimationJob?.cancel()
                    }
                },
                onDragCancel = {
                    if (!actionSubmitted) {
                        animateOffsetTo(0f)
                    }
                },
                onDragEnd = {
                    if (actionSubmitted) return@detectHorizontalDragGestures

                    val reachedThreshold = dragOffsetPx * direction >= dismissThresholdPx
                    if (reachedThreshold) {
                        actionSubmitted = true
                        animateOffsetTo(direction * dismissDistancePx) {
                            if (completed) {
                                onRestore(task)
                            } else {
                                onComplete(task)
                            }
                        }
                    } else {
                        animateOffsetTo(0f)
                    }
                },
                onHorizontalDrag = { change, dragAmount ->
                    if (actionSubmitted) return@detectHorizontalDragGestures

                    offsetAnimationJob?.cancel()
                    val nextOffset = dragOffsetPx + dragAmount
                    dragOffsetPx = if (completed) {
                        nextOffset.coerceAtMost(0f)
                    } else {
                        nextOffset.coerceAtLeast(0f)
                    }
                    change.consume()
                }
            )
        }

        Box(
            modifier = modifier.clipToBounds()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = dragOffsetPx }
                    .then(swipeModifier),
                contentAlignment = Alignment.Center
            ) {
                BaseCard(
                    title = task.title,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = if (actionSubmitted) null else onClick,
                    fillMaxHeight = fillMaxHeight,
                    titleColor = if (completed) Color.Black.copy(alpha = 0.52f) else Color.Black,
                    titleTextDecoration = if (completed) TextDecoration.LineThrough else null,
                    backgroundColor = if (completed) {
                        Color.White.copy(alpha = 0.36f)
                    } else {
                        Color.White.copy(alpha = 0.55f)
                    },
                    borderColor = if (completed) {
                        Color.Black.copy(alpha = 0.2f)
                    } else {
                        Color.Black.copy(alpha = 0.3f)
                    },
                    leftContent = {
                        Icon(
                            imageVector = if (completed) {
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft
                            } else {
                                Icons.AutoMirrored.Filled.KeyboardArrowRight
                            },
                            contentDescription = null,
                            tint = Color.Black.copy(alpha = if (completed) 0.52f else 1f),
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    rightContent = {
                        val isCourseTask = task.isTongjiCourseTask()
                        val deadlineReached = !isCourseTask && task.hasReachedDeadline()
                        Text(
                            text = if (isCourseTask) TongjiCourseType else task.deadline.toSmartString().ifEmpty { "—" },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                deadlineReached -> TaskDeadlineRed
                                completed -> Color.Black.copy(alpha = 0.48f)
                                else -> Color.Black
                            },
                            modifier = Modifier.width(82.dp),
                            textAlign = TextAlign.End
                        )
                    }
                )
            }
        }
    }
}

private val TaskDeadlineRed = Color(0xFFD93A32)

private fun Task.hasReachedDeadline(now: LocalDateTime = LocalDateTime.now()): Boolean {
    val month = deadline.month ?: return false
    val day = deadline.day ?: return false
    val year = deadline.year ?: now.year
    val dueDate = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return false
    val today = now.toLocalDate()

    if (dueDate.isBefore(today)) return true
    if (dueDate.isAfter(today)) return false

    val hour = deadline.hour ?: return true
    val minute = deadline.minute
    val dueTime = runCatching { LocalTime.of(hour, minute ?: 0) }.getOrNull() ?: return true
    return !now.toLocalTime().isBefore(dueTime)
}

@Composable
fun DetailPreview(
    item: Any,
    onClose: () -> Unit,
    onDelete: (Any) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Animation progress: 0 = fully hidden, 1 = fully visible
    val progress = remember { Animatable(0f) }
    var isDismissing by remember { mutableStateOf(false) }
    var deleteOnDismiss by remember { mutableStateOf(false) }
    val title = item.detailTitle()
    val typeLabel = when (item) {
        is Schedule -> stringResource(R.string.detail_type_schedule)
        is Task -> stringResource(R.string.detail_type_task)
        else -> stringResource(R.string.detail_type_item)
    }
    val accentColor = item.detailColor()
    val timeSummary = remember(item) { item.detailTimeSummary() }
    val description = item.detailDescription()
    val reminders = item.detailReminders()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val handleDismissThresholdPx = with(density) { 24.dp.toPx() }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val detailScrollState = rememberScrollState()

    fun requestDismiss() {
        if (!isDismissing) {
            deleteOnDismiss = false
            isDismissing = true
        }
    }

    fun requestDelete() {
        if (!isDismissing) {
            deleteOnDismiss = true
            isDismissing = true
        }
    }

    BackHandler(enabled = !isDismissing) {
        requestDismiss()
    }

    // Enter animation: animate from 0 to 1 when first composed
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 260,
                easing = FastOutSlowInEasing
            )
        )
    }

    // Exit animation: animate back to 0, then call onClose
    LaunchedEffect(isDismissing) {
        if (isDismissing) {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 140,
                    easing = FastOutSlowInEasing
                )
            )
            if (deleteOnDismiss) {
                onDelete(item)
            }
            onClose()
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Scrim – full-screen dimming overlay, fades in/out with progress
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value }
                .background(Color.Black.copy(alpha = 0.08f))
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = { requestDismiss() }
                )
        )

        // Detail content column – slides up from the bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .graphicsLayer {
                    val sheetProgress = progress.value
                    alpha = sheetProgress
                    translationY = (1f - sheetProgress) * 140f + dragOffsetPx
                    val scale = 0.985f + sheetProgress * 0.015f
                    scaleX = scale
                    scaleY = scale
                }
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = { /* Consume clicks to prevent dismissing */ }
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title area – slides down from slightly above final position.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.18f)
                    .padding(horizontal = 24.dp)
                    .graphicsLayer {
                        val titleProgress = ((progress.value - 0.04f) / 0.96f).coerceIn(0f, 1f)
                        alpha = titleProgress
                        translationY = (1f - titleProgress) * 18f
                        val titleScale = 0.98f + titleProgress * 0.02f
                        scaleX = titleScale
                        scaleY = titleScale
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 36.sp,
                        lineHeight = 42.sp
                    ),
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Detail panel – slides up from further below, creating a staggered effect
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.82f)
                    .graphicsLayer {
                        val panelProgress = ((progress.value - 0.015f) / 0.985f).coerceIn(0f, 1f)
                        alpha = panelProgress
                        translationY = (1f - panelProgress) * 64f
                    }
                    .border(
                        width = 2.dp,
                        color = Color.Black,
                        shape = RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp)
                    )
                    .background(
                        BeigeBackground,
                        shape = RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp)
                    )
                    .padding(horizontal = 28.dp, vertical = 26.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(detailScrollState)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .pointerInput(handleDismissThresholdPx) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var dismissTriggered = false

                                    do {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        val totalX = change.position.x - down.position.x
                                        val totalY = change.position.y - down.position.y

                                        val mostlyVertical = totalY > abs(totalX) * 0.45f
                                        if (totalY > 0f && mostlyVertical) {
                                            dragOffsetPx = totalY * 0.75f
                                            change.consume()
                                        } else {
                                            dragOffsetPx = 0f
                                        }

                                        if (totalY > handleDismissThresholdPx && mostlyVertical) {
                                            dismissTriggered = true
                                            requestDismiss()
                                            change.consume()
                                            break
                                        }
                                    } while (event.changes.any { it.pressed })

                                    if (!dismissTriggered) {
                                        val startOffset = dragOffsetPx
                                        scope.launch {
                                            Animatable(startOffset).animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f)
                                            ) {
                                                dragOffsetPx = value
                                            }
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .width(52.dp)
                                .height(4.dp)
                                .background(Color.Black.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(accentColor, RoundedCornerShape(999.dp))
                                .border(1.dp, Color.Black.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = typeLabel.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = Color.Black.copy(alpha = 0.62f),
                            letterSpacing = 1.2.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        DeleteConfirmationButton(
                            onDeleteConfirmed = ::requestDelete
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    DetailTimeBlock(
                        title = if (item is Task) stringResource(R.string.detail_deadline) else stringResource(R.string.detail_time),
                        summary = timeSummary,
                        accentColor = accentColor
                    )

                    DetailSection(
                        title = stringResource(R.string.detail_details),
                        emptyText = stringResource(R.string.detail_no_details),
                        rows = description.map { it.head to it.info }
                    )

                    DetailSection(
                        title = stringResource(R.string.detail_reminders),
                        emptyText = stringResource(R.string.detail_no_reminders),
                        rows = reminders.mapIndexed { index, reminder ->
                            val reminderSummary = reminder.time.toDetailTaskTimeSummary()
                            val status = if (reminder.enabled) stringResource(R.string.detail_enabled) else stringResource(R.string.detail_disabled)
                            val value = listOf(
                                reminderSummary.primary,
                                reminderSummary.secondary,
                                status
                            ).filter { it.isNotBlank() }.joinToString(" / ")
                            stringResource(R.string.reminder_label, index + 1) to value
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun DeleteConfirmationButton(
    onDeleteConfirmed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val revealProgress = remember { Animatable(0f) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var expanded by remember { mutableStateOf(false) }
    var isConfirming by remember { mutableStateOf(false) }

    val expandedWidth = 78.dp
    val knobSize = 40.dp
    val controlHeight = 42.dp
    val maxTravelPx = with(density) { (expandedWidth - knobSize).toPx() }
    val reveal = revealProgress.value.coerceIn(0f, 1f)
    val trackWidth = lerpDp(knobSize, expandedWidth, reveal)
    val destructiveRed = Color(0xFFE92D2D)

    LaunchedEffect(expanded) {
        if (expanded) {
            revealProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f)
            )
        } else {
            dragOffsetPx = 0f
            revealProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
            )
        }
    }

    val knobInteractionModifier = if (expanded) {
        Modifier.pointerInput(maxTravelPx, isConfirming) {
            if (isConfirming) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var confirmed = false

                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val totalX = (change.position.x - down.position.x).coerceIn(0f, maxTravelPx)

                    dragOffsetPx = totalX
                    change.consume()

                    if (totalX >= maxTravelPx * 0.86f) {
                        confirmed = true
                        isConfirming = true
                        val startOffset = dragOffsetPx
                        scope.launch {
                            Animatable(startOffset).animateTo(
                                targetValue = maxTravelPx,
                                animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f)
                            ) {
                                dragOffsetPx = value
                            }
                            onDeleteConfirmed()
                        }
                        break
                    }
                } while (event.changes.any { it.pressed })

                if (!confirmed && !isConfirming) {
                    val startOffset = dragOffsetPx
                    scope.launch {
                        Animatable(startOffset).animateTo(
                            targetValue = 0f,
                            animationSpec = spring(dampingRatio = 0.68f, stiffness = 420f)
                        ) {
                            dragOffsetPx = value
                        }
                    }
                }
            }
        }
    } else {
        Modifier.clickable(
            interactionSource = null,
            indication = null,
            onClick = { expanded = true }
        )
    }

    Box(
        modifier = modifier
            .width(expandedWidth)
            .height(controlHeight)
            .clipToBounds(),
        contentAlignment = Alignment.CenterEnd
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(trackWidth)
                .height(knobSize)
                .graphicsLayer {
                    scaleY = 0.86f + reveal * 0.14f
                    alpha = 0.18f + reveal * 0.82f
                }
                .border(
                    width = 2.dp,
                    color = Color.Black.copy(alpha = 0.22f + reveal * 0.56f),
                    shape = RoundedCornerShape(999.dp)
                )
                .background(
                    destructiveRed.copy(alpha = reveal),
                    RoundedCornerShape(999.dp)
                )
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = {
                        if (expanded && !isConfirming) expanded = false
                    }
                )
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(expandedWidth - knobSize)
                .graphicsLayer {
                    alpha = ((reveal - 0.28f) / 0.72f).coerceIn(0f, 1f)
                    translationX = (1f - reveal) * 6f
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset {
                    IntOffset(
                        x = (maxTravelPx * (1f - reveal) + dragOffsetPx).roundToInt(),
                        y = 0
                    )
                }
                .size(knobSize)
                .graphicsLayer {
                    val pressStretch = if (dragOffsetPx > 0f) 1.02f else 1f
                    scaleX = pressStretch
                    scaleY = 1f / pressStretch
                }
                .background(Color.Black, RoundedCornerShape(999.dp))
                .border(2.dp, Color.Black.copy(alpha = 0.86f), RoundedCornerShape(999.dp))
                .then(knobInteractionModifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.detail_delete),
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DetailTimeBlock(
    title: String,
    summary: DetailTimeSummary,
    accentColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 2.dp,
                color = Color.Black.copy(alpha = 0.32f),
                shape = RoundedCornerShape(28.dp)
            )
            .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(28.dp))
            .padding(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(5.dp)
                    .background(accentColor, RoundedCornerShape(999.dp))
                    .border(1.dp, Color.Black.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = Color.Black.copy(alpha = 0.58f),
                letterSpacing = 1.1.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = summary.primary,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Black,
                fontSize = 28.sp,
                lineHeight = 34.sp
            ),
            color = Color.Black,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        if (summary.secondary.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = summary.secondary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.Black.copy(alpha = 0.56f),
                lineHeight = 24.sp
            )
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    emptyText: String,
    rows: List<Pair<String, String>>
) {
    Spacer(modifier = Modifier.height(28.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Black,
        color = Color.Black
    )
    Spacer(modifier = Modifier.height(12.dp))

    if (rows.isEmpty()) {
        Text(
            text = emptyText,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = Color.Black.copy(alpha = 0.42f)
        )
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, (head, info) ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = Color.Black.copy(alpha = 0.16f),
                    thickness = 1.dp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = head,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.Black.copy(alpha = 0.52f),
                    modifier = Modifier.widthIn(min = 88.dp, max = 118.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = info,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    lineHeight = 26.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private data class DetailTimeSummary(
    val primary: String,
    val secondary: String = ""
)

private enum class DetailTimeLevel {
    Year,
    Month,
    Time
}

private val DetailMonthNames = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

private fun Any.detailTitle(): String = when (this) {
    is Schedule -> title
    is Task -> title
    else -> ""
}

private fun Any.detailTypeLabel(): String = when (this) {
    is Schedule -> if (isTongjiCourseSchedule()) TongjiCourseType else "Schedule"
    is Task -> when {
        isTongjiCourseTask() -> TongjiCourseType
        isTongjiExamTask() -> TongjiExamType
        else -> "Task"
    }
    else -> "Item"
}

private fun Any.detailColor(): Color = when (this) {
    is Schedule -> color
    is Task -> color
    else -> Color.White
}

private fun Any.detailDescription(): List<DetailEntry> = when (this) {
    is Schedule -> description
    is Task -> description
    else -> emptyList()
}

private fun Any.detailReminders(): List<Reminder> = when (this) {
    is Schedule -> reminders
    is Task -> reminders
    else -> emptyList()
}

private fun Any.detailTimeSummary(now: LocalDateTime = LocalDateTime.now()): DetailTimeSummary =
    when (this) {
        is Schedule -> toDetailScheduleTimeSummary(now)
        is Task -> deadline.toDetailTaskTimeSummary(now)
        else -> DetailTimeSummary("Any time")
    }

private fun Schedule.toDetailScheduleTimeSummary(now: LocalDateTime): DetailTimeSummary {
    val level = detailRangeLevel(startTime, endTime, now)
    val startDate = startTime.formatDetailDate(level)
    val endDate = endTime.formatDetailDate(level)
    val startClock = startTime.formatDetailClock()
    val endClock = endTime.formatDetailClock()
    val recurrence = sharedWildcardSummary(startTime, endTime)

    return if (startDate.isNotBlank() && startDate == endDate) {
        DetailTimeSummary(
            primary = "$startClock - $endClock",
            secondary = listOf(startDate, recurrence).filter { it.isNotBlank() }.joinToString(" / ")
        )
    } else {
        val startText = listOf(startDate, startClock).filter { it.isNotBlank() }.joinToString(", ")
        val endText = listOf(endDate, endClock).filter { it.isNotBlank() }.joinToString(", ")
        DetailTimeSummary(
            primary = "$startText - $endText",
            secondary = recurrence
        )
    }
}

private fun FlexibleDateTime.toDetailTaskTimeSummary(
    now: LocalDateTime = LocalDateTime.now()
): DetailTimeSummary {
    val level = detailPointLevel(this, now)
    val date = formatDetailDate(level)
    val clock = formatDetailClock()
    val primary = listOf(date, clock)
        .filter { it.isNotBlank() }
        .joinToString(", ")
        .ifBlank { "Any time" }

    return DetailTimeSummary(
        primary = primary,
        secondary = wildcardSummary()
    )
}

private fun detailRangeLevel(
    start: FlexibleDateTime,
    end: FlexibleDateTime,
    now: LocalDateTime
): DetailTimeLevel {
    return when {
        hasConcreteDifference(start.year, end.year, now.year) -> DetailTimeLevel.Year
        hasConcreteDifference(start.month, end.month, now.monthValue) ||
            hasConcreteDifference(start.day, end.day, now.dayOfMonth) -> DetailTimeLevel.Month
        else -> DetailTimeLevel.Time
    }
}

private fun detailPointLevel(
    time: FlexibleDateTime,
    now: LocalDateTime
): DetailTimeLevel {
    return when {
        time.year != null && time.year != now.year -> DetailTimeLevel.Year
        (time.month != null && time.month != now.monthValue) ||
            (time.day != null && time.day != now.dayOfMonth) -> DetailTimeLevel.Month
        else -> DetailTimeLevel.Time
    }
}

private fun hasConcreteDifference(first: Int?, second: Int?, current: Int): Boolean {
    val concreteValues = listOfNotNull(first, second)
    return concreteValues.any { it != current } || concreteValues.toSet().size > 1
}

private fun FlexibleDateTime.formatDetailDate(level: DetailTimeLevel): String {
    if (level == DetailTimeLevel.Time) return ""

    val parts = mutableListOf<String>()
    if (level == DetailTimeLevel.Year && year != null) {
        parts += year.toString()
    }

    if (month != null) {
        parts += DetailMonthNames.getOrElse(month - 1) { month.toString() }
    }
    if (day != null) {
        parts += day.toString()
    }

    return parts.joinToString(" ")
}

private fun FlexibleDateTime.formatDetailClock(): String {
    return when {
        hour != null && minute != null ->
            "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
        hour != null -> "${hour.toString().padStart(2, '0')}:any minute"
        minute != null -> "Every hour at :${minute.toString().padStart(2, '0')}"
        else -> "Any time"
    }
}

private fun sharedWildcardSummary(
    start: FlexibleDateTime,
    end: FlexibleDateTime
): String {
    val startSummary = start.wildcardSummary()
    val endSummary = end.wildcardSummary()
    return if (startSummary == endSummary) {
        startSummary
    } else {
        listOf(startSummary, endSummary)
            .filter { it.isNotBlank() }
            .joinToString(" - ")
    }
}

private fun FlexibleDateTime.wildcardSummary(): String {
    val dateScope = when {
        year == null && month == null && day == null -> "Every day"
        year == null && month != null && day != null -> "Every year"
        year == null && month == null && day != null -> "Every month"
        year == null && month != null && day == null -> "Every day in ${DetailMonthNames.getOrElse(month - 1) { month.toString() }}"
        year != null && month == null && day == null -> "Every month in $year"
        year != null && month != null && day == null -> "Every day in ${DetailMonthNames.getOrElse(month - 1) { month.toString() }} $year"
        else -> ""
    }

    val timeScope = when {
        hour == null && minute == null -> "Any time"
        hour == null && minute != null -> "Every hour"
        hour != null && minute == null -> "Every minute"
        else -> ""
    }

    return listOf(dateScope, timeScope)
        .filter { it.isNotBlank() }
        .joinToString(" / ")
}
