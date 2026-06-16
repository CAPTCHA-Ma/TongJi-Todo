package com.example.todo

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.todo.ui.theme.BeigeBackground
import com.example.todo.ui.theme.SoftBlue
import com.example.todo.ui.theme.SoftLavender
import com.example.todo.ui.theme.SoftPeach
import com.example.todo.ui.theme.SoftRose
import com.example.todo.ui.theme.SoftSage
import androidx.compose.ui.res.stringResource
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

enum class CreateItemType {
    Schedule,
    Task
}

private enum class CreateTimeField { Year, Month, Day, Hour, Minute }

@Composable
private fun CreateTimeField.title(): String = when (this) {
    CreateTimeField.Year -> stringResource(R.string.time_year)
    CreateTimeField.Month -> stringResource(R.string.time_month)
    CreateTimeField.Day -> stringResource(R.string.time_day)
    CreateTimeField.Hour -> stringResource(R.string.time_hour)
    CreateTimeField.Minute -> stringResource(R.string.time_minute)
}

@Composable
private fun CreateTimeField.shortTitle(): String = when (this) {
    CreateTimeField.Year -> stringResource(R.string.time_year).take(3)
    CreateTimeField.Month -> stringResource(R.string.time_month).take(3)
    CreateTimeField.Day -> stringResource(R.string.time_day).take(3)
    CreateTimeField.Hour -> stringResource(R.string.time_hour).take(3)
    CreateTimeField.Minute -> stringResource(R.string.time_minute).take(3)
}

@Composable
private fun CreateTimeField.nullLabel(): String = when (this) {
    CreateTimeField.Year -> stringResource(R.string.time_every_year)
    CreateTimeField.Month -> stringResource(R.string.time_every_month)
    CreateTimeField.Day -> stringResource(R.string.time_every_day)
    CreateTimeField.Hour -> stringResource(R.string.time_every_hour)
    CreateTimeField.Minute -> stringResource(R.string.time_every_minute)
}

private data class DetailDraft(
    val id: Long,
    val head: String = "",
    val info: String = ""
)

private data class ReminderDraft(
    val id: Long,
    val time: FlexibleDateTime
)

private data class TimePickerState(
    val title: String,
    val value: FlexibleDateTime,
    val pairValue: FlexibleDateTime? = null,
    val onValueChange: (FlexibleDateTime) -> Unit,
    val onPairValueChange: ((FlexibleDateTime) -> Unit)? = null
)

@Composable
fun CreateItemScreen(
    initialType: CreateItemType,
    initialDate: LocalDate,
    onClose: () -> Unit,
    onCreateSchedule: (Schedule) -> Unit,
    onCreateTask: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    val res = LocalContext.current.resources
    val now = remember(initialDate) { LocalDateTime.of(initialDate, LocalTime.now()) }
    val defaultTime = remember(now) { now.toFlexibleDateTime() }
    val details = remember { mutableStateListOf<DetailDraft>() }
    val reminders = remember { mutableStateListOf<ReminderDraft>() }
    var nextDraftId by remember { mutableLongStateOf(1L) }

    var selectedType by rememberSaveable(initialType) { mutableStateOf(initialType) }
    var title by rememberSaveable { mutableStateOf("") }
    var scheduleStart by remember(defaultTime) { mutableStateOf(defaultTime) }
    var scheduleEnd by remember(defaultTime) { mutableStateOf(defaultTime) }
    var taskDeadline by remember(defaultTime) { mutableStateOf(defaultTime) }
    var accentColor by remember { mutableStateOf(SoftBlue) }
    var taskCost by remember { mutableIntStateOf(3) }
    var pickerState by remember { mutableStateOf<TimePickerState?>(null) }

    BackHandler(onBack = onClose)

    fun sanitizeDetails(): List<DetailEntry> =
        details
            .map { it.head.trim() to it.info.trim() }
            .filter { (head, info) -> head.isNotEmpty() || info.isNotEmpty() }
            .map { (head, info) -> DetailEntry(head, info) }

    fun save() {
        val cleanedTitle = title.trim().ifEmpty {
            if (selectedType == CreateItemType.Schedule) res.getString(R.string.new_schedule) else res.getString(R.string.new_task)
        }
        val cleanedDetails = sanitizeDetails()
        val createdReminders = reminders.map { Reminder(time = it.time, enabled = true) }
        val id = "${if (selectedType == CreateItemType.Schedule) "s" else "t"}${System.currentTimeMillis()}"

        if (selectedType == CreateItemType.Schedule) {
            onCreateSchedule(
                Schedule(
                    id = id,
                    title = cleanedTitle,
                    startTime = scheduleStart,
                    endTime = scheduleEnd,
                    description = cleanedDetails,
                    reminders = createdReminders,
                    color = accentColor
                )
            )
        } else {
            onCreateTask(
                Task(
                    id = id,
                    title = cleanedTitle,
                    deadline = taskDeadline,
                    description = cleanedDetails,
                    reminders = createdReminders,
                    color = accentColor,
                    cost = taskCost
                )
            )
        }
        onClose()
    }

    fun addDetail() {
        details.add(DetailDraft(id = nextDraftId++))
    }

    fun addReminder() {
        val reminderId = nextDraftId++
        reminders.add(ReminderDraft(id = reminderId, time = defaultTime))
        pickerState = TimePickerState(
            title = res.getString(R.string.reminder_label, reminders.size),
            value = defaultTime,
            onValueChange = { newValue ->
                val index = reminders.indexOfFirst { it.id == reminderId }
                if (index >= 0) reminders[index] = reminders[index].copy(time = newValue)
                pickerState = pickerState?.copy(value = newValue)
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BeigeBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            CreateTopBar(
                selectedType = selectedType,
                onTypeChange = { selectedType = it },
                onClose = onClose,
                onSave = ::save
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 28.dp)
                    .animateContentSize(tween(260, easing = FastOutSlowInEasing))
            ) {
                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = if (selectedType == CreateItemType.Schedule) {
                        stringResource(R.string.create_schedule)
                    } else {
                        stringResource(R.string.create_task)
                    },
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 34.sp,
                        lineHeight = 40.sp
                    ),
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(18.dp))

                CreateLabel(stringResource(R.string.label_title))
                CreateTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = if (selectedType == CreateItemType.Schedule) {
                        stringResource(R.string.hint_schedule_title)
                    } else {
                        stringResource(R.string.hint_task_title)
                    },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(26.dp))

                CreateLabel(stringResource(R.string.label_time))
                AnimatedContent(
                    targetState = selectedType,
                    transitionSpec = {
                        fadeIn(tween(180)) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false)
                    },
                    label = "CreateItemTimeMode"
                ) { type ->
                    if (type == CreateItemType.Schedule) {
                        Column {
                            PairedTimeRow(
                                label = stringResource(R.string.label_start),
                                time = scheduleStart,
                                accentColor = accentColor,
                                onClick = {
                                    pickerState = TimePickerState(
                                        title = res.getString(R.string.label_start_time),
                                        value = scheduleStart,
                                        pairValue = scheduleEnd,
                                        onValueChange = { newStart ->
                                            scheduleStart = newStart
                                            if (newStart.compareTo(scheduleEnd) > 0) {
                                                scheduleEnd = newStart
                                            }
                                            pickerState = pickerState?.copy(value = newStart)
                                        },
                                        onPairValueChange = {
                                            scheduleEnd = it
                                            pickerState = pickerState?.copy(pairValue = it)
                                        }
                                    )
                                }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            PairedTimeRow(
                                label = stringResource(R.string.label_end),
                                time = scheduleEnd,
                                accentColor = accentColor,
                                onClick = {
                                    pickerState = TimePickerState(
                                        title = res.getString(R.string.label_end_time),
                                        value = scheduleEnd,
                                        pairValue = scheduleStart,
                                        onValueChange = { newEnd ->
                                            if (newEnd.compareTo(scheduleStart) >= 0) {
                                                scheduleEnd = newEnd
                                            }
                                            pickerState = pickerState?.copy(value = newEnd)
                                        },
                                        onPairValueChange = {
                                            scheduleStart = it
                                            pickerState = pickerState?.copy(pairValue = it)
                                        }
                                    )
                                }
                            )
                        }
                    } else {
                        PairedTimeRow(
                            label = stringResource(R.string.label_deadline),
                            time = taskDeadline,
                            accentColor = accentColor,
                            onClick = {
                                pickerState = TimePickerState(
                                    title = res.getString(R.string.label_deadline),
                                    value = taskDeadline,
                                    onValueChange = {
                                        taskDeadline = it
                                        pickerState = pickerState?.copy(value = it)
                                    }
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))

                CreateLabel(stringResource(R.string.label_accent))
                ColorSwatches(
                    selectedColor = accentColor,
                    onSelect = { accentColor = it }
                )

                if (selectedType == CreateItemType.Task) {
                    Spacer(modifier = Modifier.height(26.dp))
                    CreateLabel(stringResource(R.string.label_estimated_effort))
                    CostSelector(
                        selectedCost = taskCost,
                        onSelect = { taskCost = it }
                    )
                }

                Spacer(modifier = Modifier.height(30.dp))

                CreateSectionHeader(
                    title = stringResource(R.string.label_details),
                    actionText = stringResource(R.string.action_new),
                    onAction = ::addDetail
                )
                if (details.isEmpty()) {
                    EmptyCreateText(stringResource(R.string.empty_no_detail_fields))
                } else {
                    Column(
                        modifier = Modifier.animateContentSize(tween(240, easing = FastOutSlowInEasing))
                    ) {
                        details.forEachIndexed { index, detail ->
                            DetailDraftRow(
                                detail = detail,
                                onHeadChange = { value ->
                                    details[index] = detail.copy(head = value)
                                },
                                onInfoChange = { value ->
                                    details[index] = detail.copy(info = value)
                                },
                                onRemove = {
                                    details.removeAt(index)
                                }
                            )
                            if (index < details.lastIndex) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))

                CreateSectionHeader(
                    title = stringResource(R.string.label_reminders),
                    actionText = stringResource(R.string.action_new),
                    onAction = ::addReminder
                )
                if (reminders.isEmpty()) {
                    EmptyCreateText(stringResource(R.string.empty_no_reminders))
                } else {
                    Column(
                        modifier = Modifier.animateContentSize(tween(240, easing = FastOutSlowInEasing))
                    ) {
                        reminders.forEachIndexed { index, reminder ->
                            ReminderDraftRow(
                                label = stringResource(R.string.reminder_label, index + 1),
                                time = reminder.time,
                                onClick = {
                                    pickerState = TimePickerState(
                                        title = res.getString(R.string.reminder_label, index + 1),
                                        value = reminder.time,
                                        onValueChange = { newValue ->
                                            val currentIndex = reminders.indexOfFirst { it.id == reminder.id }
                                            if (currentIndex >= 0) {
                                                reminders[currentIndex] = reminder.copy(time = newValue)
                                            }
                                            pickerState = pickerState?.copy(value = newValue)
                                        }
                                    )
                                },
                                onRemove = {
                                    reminders.removeAt(index)
                                }
                            )
                            if (index < reminders.lastIndex) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
        }

        FlexibleDateTimePickerOverlay(
            state = pickerState,
            onDismiss = { pickerState = null }
        )
    }
}

@Composable
private fun CreateTopBar(
    selectedType: CreateItemType,
    onTypeChange: (CreateItemType) -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconCircleButton(
            icon = Icons.Filled.Close,
            contentDescription = stringResource(R.string.cd_close),
            onClick = onClose
        )
        Spacer(modifier = Modifier.width(14.dp))
        TypeSegmentedControl(
            selectedType = selectedType,
            onTypeChange = onTypeChange,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(14.dp))
        IconCircleButton(
            icon = Icons.Filled.Check,
            contentDescription = stringResource(R.string.cd_save),
            onClick = onSave,
            filled = true
        )
    }
}

@Composable
private fun TypeSegmentedControl(
    selectedType: CreateItemType,
    onTypeChange: (CreateItemType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .border(2.dp, Color.Black.copy(alpha = 0.32f), RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.42f), RoundedCornerShape(999.dp))
            .padding(4.dp)
    ) {
        CreateItemType.entries.forEach { type ->
            val selected = type == selectedType
            val background by animateColorAsState(
                targetValue = if (selected) Color.Black else Color.Transparent,
                animationSpec = tween(180),
                label = "TypeSegmentBackground"
            )
            val contentColor by animateColorAsState(
                targetValue = if (selected) Color.White else Color.Black.copy(alpha = 0.72f),
                animationSpec = tween(180),
                label = "TypeSegmentContent"
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(background, RoundedCornerShape(999.dp))
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = { onTypeChange(type) }
                    )
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (type == CreateItemType.Schedule) Icons.Filled.Event else Icons.Filled.TaskAlt,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (type == CreateItemType.Schedule) {
                        stringResource(R.string.type_schedule)
                    } else {
                        stringResource(R.string.type_task)
                    },
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

@Composable
private fun IconCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .border(
                width = 2.dp,
                color = Color.Black.copy(alpha = if (filled) 1f else 0.32f),
                shape = RoundedCornerShape(999.dp)
            )
            .background(
                color = if (filled) Color.Black else Color.White.copy(alpha = 0.42f),
                shape = RoundedCornerShape(999.dp)
            )
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
            tint = if (filled) Color.White else Color.Black,
            modifier = Modifier.size(23.dp)
        )
    }
}

@Composable
private fun CreateLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Black,
        color = Color.Black.copy(alpha = 0.56f),
        letterSpacing = 1.2.sp
    )
    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
private fun CreateTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minHeight: androidx.compose.ui.unit.Dp = 58.dp
) {
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = value,
                selection = TextRange(value.length)
            )
        )
    }

    LaunchedEffect(value) {
        if (value != textFieldValue.text) {
            textFieldValue = TextFieldValue(
                text = value,
                selection = TextRange(value.length)
            )
        }
    }

    BasicTextField(
        value = textFieldValue,
        onValueChange = { nextValue ->
            textFieldValue = nextValue
            if (nextValue.text != value) {
                onValueChange(nextValue.text)
            }
        },
        singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            lineHeight = 28.sp
        ),
        cursorBrush = SolidColor(Color.Black),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .border(2.dp, Color.Black.copy(alpha = 0.28f), RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
            .padding(horizontal = 18.dp, vertical = 15.dp),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black.copy(alpha = 0.34f)
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun PairedTimeRow(
    label: String,
    time: FlexibleDateTime,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .border(2.dp, Color.Black.copy(alpha = 0.28f), RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(30.dp)
                .height(6.dp)
                .background(accentColor, RoundedCornerShape(999.dp))
                .border(1.dp, Color.Black.copy(alpha = 0.2f), RoundedCornerShape(999.dp))
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = Color.Black.copy(alpha = 0.52f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = time.toCreatorSummary(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = Color.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Filled.Schedule,
            contentDescription = null,
            tint = Color.Black.copy(alpha = 0.58f),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun ColorSwatches(
    selectedColor: Color,
    onSelect: (Color) -> Unit
) {
    val colors = remember { listOf(SoftBlue, SoftSage, SoftRose, SoftLavender, SoftPeach, Color.White) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        colors.forEach { color ->
            val selected = color == selectedColor
            val scale by animateFloatAsState(
                targetValue = if (selected) 1f else 0.88f,
                animationSpec = tween(180),
                label = "SwatchScale"
            )
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .border(
                        width = if (selected) 3.dp else 2.dp,
                        color = Color.Black.copy(alpha = if (selected) 0.9f else 0.25f),
                        shape = RoundedCornerShape(999.dp)
                    )
                    .background(color, RoundedCornerShape(999.dp))
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = { onSelect(color) }
                    )
            )
        }
    }
}

@Composable
private fun CostSelector(
    selectedCost: Int,
    onSelect: (Int) -> Unit
) {
    val costs = listOf(1, 2, 3, 4, 5)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .border(2.dp, Color.Black.copy(alpha = 0.32f), RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.42f), RoundedCornerShape(999.dp))
            .padding(4.dp)
    ) {
        costs.forEach { cost ->
            val selected = cost == selectedCost
            val background by animateColorAsState(
                targetValue = if (selected) Color.Black else Color.Transparent,
                animationSpec = tween(180),
                label = "CostSegmentBG"
            )
            val contentColor by animateColorAsState(
                targetValue = if (selected) Color.White else Color.Black.copy(alpha = 0.62f),
                animationSpec = tween(180),
                label = "CostSegmentText"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(background, RoundedCornerShape(999.dp))
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = { onSelect(cost) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = cost.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
private fun CreateSectionHeader(
    title: String,
    actionText: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = Color.Black,
            modifier = Modifier.weight(1f)
        )
        Row(
            modifier = Modifier
                .height(42.dp)
                .border(2.dp, Color.Black.copy(alpha = 0.3f), RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.42f), RoundedCornerShape(999.dp))
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = onAction
                )
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = actionText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = Color.Black
            )
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun EmptyCreateText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
        color = Color.Black.copy(alpha = 0.42f),
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun DetailDraftRow(
    detail: DetailDraft,
    onHeadChange: (String) -> Unit,
    onInfoChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, Color.Black.copy(alpha = 0.22f), RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.42f), RoundedCornerShape(24.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.detail_field),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = Color.Black.copy(alpha = 0.52f),
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.cd_remove_detail),
                tint = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier
                    .size(34.dp)
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = onRemove
                    )
                    .padding(6.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CreateTextField(
                value = detail.head,
                onValueChange = onHeadChange,
                placeholder = stringResource(R.string.hint_head),
                singleLine = true,
                minHeight = 52.dp,
                modifier = Modifier.weight(0.9f)
            )
            CreateTextField(
                value = detail.info,
                onValueChange = onInfoChange,
                placeholder = stringResource(R.string.hint_info),
                minHeight = 52.dp,
                modifier = Modifier.weight(1.4f)
            )
        }
    }
}

@Composable
private fun ReminderDraftRow(
    label: String,
    time: FlexibleDateTime,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .border(2.dp, Color.Black.copy(alpha = 0.22f), RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.42f), RoundedCornerShape(24.dp))
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Notifications,
            contentDescription = null,
            tint = Color.Black.copy(alpha = 0.62f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = onClick
                )
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = Color.Black.copy(alpha = 0.52f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = time.toCreatorSummary(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                color = Color.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = stringResource(R.string.cd_remove_reminder),
            tint = Color.Black.copy(alpha = 0.55f),
            modifier = Modifier
                .size(40.dp)
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = onRemove
                )
                .padding(8.dp)
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun BoxScope.FlexibleDateTimePickerOverlay(
    state: TimePickerState?,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = state != null,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(140)),
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
        visible = state != null,
        enter = slideInVertically(
            animationSpec = tween(260, easing = FastOutSlowInEasing),
            initialOffsetY = { it }
        ) + fadeIn(tween(160)),
        exit = slideOutVertically(
            animationSpec = tween(220, easing = FastOutSlowInEasing),
            targetOffsetY = { it }
        ) + fadeOut(tween(140)),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        if (state != null) {
            FlexibleDateTimePickerPanel(
                state = state,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun FlexibleDateTimePickerPanel(
    state: TimePickerState,
    onDismiss: () -> Unit
) {
    var selectedField by remember(state.title) { mutableStateOf(CreateTimeField.Year) }

    Surface(
        color = BeigeBackground,
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.74f)
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
                        text = state.title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 28.sp,
                            lineHeight = 34.sp
                        ),
                        color = Color.Black
                    )
                    Text(
                        text = state.value.toCreatorSummary(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black.copy(alpha = 0.56f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconCircleButton(
                    icon = Icons.Filled.Check,
                    contentDescription = stringResource(R.string.cd_done),
                    onClick = onDismiss,
                    filled = true
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            TimeFieldTabs(
                selectedField = selectedField,
                onSelect = { selectedField = it }
            )
            Spacer(modifier = Modifier.height(14.dp))

            AnimatedContent(
                targetState = selectedField,
                transitionSpec = {
                    fadeIn(tween(160)) togetherWith fadeOut(tween(110)) using SizeTransform(clip = false)
                },
                label = "FlexibleDateTimeField"
            ) { field ->
                TimeOptionList(
                    field = field,
                    value = state.value,
                    onSelect = { selected ->
                        val nextValue = state.value.withField(field, selected)
                        state.onValueChange(nextValue)

                        if (selected == null && state.pairValue != null && state.onPairValueChange != null) {
                            state.onPairValueChange.invoke(state.pairValue.withField(field, null))
                        }

                        val nextField = CreateTimeField.entries.getOrNull(field.ordinal + 1)
                        if (nextField != null) selectedField = nextField
                    }
                )
            }
        }
    }
}

@Composable
private fun TimeFieldTabs(
    selectedField: CreateTimeField,
    onSelect: (CreateTimeField) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .border(2.dp, Color.Black.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.42f), RoundedCornerShape(999.dp))
            .padding(4.dp)
    ) {
        CreateTimeField.entries.forEach { field ->
            val selected = field == selectedField
            val background by animateColorAsState(
                targetValue = if (selected) Color.Black else Color.Transparent,
                animationSpec = tween(180),
                label = "TimeFieldBackground"
            )
            val contentColor by animateColorAsState(
                targetValue = if (selected) Color.White else Color.Black.copy(alpha = 0.62f),
                animationSpec = tween(180),
                label = "TimeFieldContent"
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
                    text = field.shortTitle(),
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
private fun TimeOptionList(
    field: CreateTimeField,
    value: FlexibleDateTime,
    onSelect: (Int?) -> Unit
) {
    val selectedValue = value.getField(field)
    val options = remember(field, value.year, value.month) {
        buildTimeOptions(field, value)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "null") {
            TimeOptionRow(
                label = field.nullLabel(),
                secondary = stringResource(R.string.label_any),
                selected = selectedValue == null,
                onClick = { onSelect(null) }
            )
        }
        items(options, key = { it }) { option ->
            TimeOptionRow(
                label = option.formatTimeOption(field),
                secondary = field.title(),
                selected = selectedValue == option,
                onClick = { onSelect(option) }
            )
        }
    }
}

@Composable
private fun TimeOptionRow(
    label: String,
    secondary: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background by animateColorAsState(
        targetValue = if (selected) Color.Black else Color.White.copy(alpha = 0.46f),
        animationSpec = tween(160),
        label = "TimeOptionBackground"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) Color.White else Color.Black,
        animationSpec = tween(160),
        label = "TimeOptionContent"
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

private fun LocalDateTime.toFlexibleDateTime(): FlexibleDateTime =
    FlexibleDateTime(
        year = year,
        month = monthValue,
        day = dayOfMonth,
        hour = hour,
        minute = minute
    )

private fun FlexibleDateTime.getField(field: CreateTimeField): Int? = when (field) {
    CreateTimeField.Year -> year
    CreateTimeField.Month -> month
    CreateTimeField.Day -> day
    CreateTimeField.Hour -> hour
    CreateTimeField.Minute -> minute
}

private fun FlexibleDateTime.withField(field: CreateTimeField, value: Int?): FlexibleDateTime = when (field) {
    CreateTimeField.Year -> copy(
        year = value,
        day = day?.coerceAtMost(maxDayFor(value, month))
    )
    CreateTimeField.Month -> copy(
        month = value,
        day = day?.coerceAtMost(maxDayFor(year, value))
    )
    CreateTimeField.Day -> copy(day = value?.coerceAtMost(maxDayFor(year, month)))
    CreateTimeField.Hour -> copy(hour = value)
    CreateTimeField.Minute -> copy(minute = value)
}

private fun buildTimeOptions(field: CreateTimeField, value: FlexibleDateTime): List<Int> = when (field) {
    CreateTimeField.Year -> {
        val center = value.year ?: LocalDateTime.now().year
        (center - 5..center + 8).toList()
    }
    CreateTimeField.Month -> (1..12).toList()
    CreateTimeField.Day -> {
        val maxDay = maxDayFor(value.year, value.month)
        (1..maxDay).toList()
    }
    CreateTimeField.Hour -> (0..23).toList()
    CreateTimeField.Minute -> (0..59).toList()
}

private fun maxDayFor(year: Int?, month: Int?): Int {
    val safeMonth = month ?: 1
    return if (year != null && month != null) {
        YearMonth.of(year, safeMonth).lengthOfMonth()
    } else {
        when (safeMonth) {
            4, 6, 9, 11 -> 30
            2 -> 29
            else -> 31
        }
    }
}

@Composable
private fun Int.formatTimeOption(field: CreateTimeField): String = when (field) {
    CreateTimeField.Year -> "$this"
    CreateTimeField.Month -> monthName(this)
    CreateTimeField.Day -> toString().padStart(2, '0')
    CreateTimeField.Hour -> toString().padStart(2, '0')
    CreateTimeField.Minute -> toString().padStart(2, '0')
}

@Composable
private fun FlexibleDateTime.toCreatorSummary(): String {
    val date = listOf(
        year?.toString() ?: stringResource(R.string.time_any_year),
        month?.let { monthName(it) } ?: stringResource(R.string.time_any_month),
        day?.toString()?.padStart(2, '0') ?: stringResource(R.string.time_any_day)
    ).joinToString(" ")

    val time = listOf(
        hour?.toString()?.padStart(2, '0') ?: stringResource(R.string.time_any_hour),
        minute?.toString()?.padStart(2, '0') ?: stringResource(R.string.time_any_minute)
    ).joinToString(":")

    return "$date / $time"
}

@Composable
private fun monthName(month: Int): String =
    LocalContext.current.resources
        .getStringArray(R.array.month_names_short)
        .getOrElse(month - 1) { month.toString() }
