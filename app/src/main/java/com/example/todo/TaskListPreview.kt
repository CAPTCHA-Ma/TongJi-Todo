package com.example.todo

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.todo.ui.theme.BeigeBackground

@Composable
fun TaskListPreview(
    activeTasks: List<Task>,
    completedTasks: List<Task>,
    dateLabel: String,
    onClose: () -> Unit,
    onTaskClick: (Task) -> Unit,
    onCompleteTask: (Task) -> Unit,
    onRestoreTask: (Task) -> Unit,
    onCreateTask: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }
    var isDismissing by remember { mutableStateOf(false) }
    var completedExpanded by remember { mutableStateOf(false) }

    fun requestCompleteTask(task: Task) {
        onCompleteTask(task)
    }

    fun requestRestoreTask(task: Task) {
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(10f)
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
            TaskPreviewTopBar(
                dateLabel = dateLabel,
                onCreateTask = onCreateTask,
                onClose = ::requestDismiss
            )

            LazyColumn(
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
                if (activeTasks.isEmpty()) {
                    item(key = "empty-active") {
                        TaskPreviewEmptyState(
                            text = "No active tasks",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(84.dp)
                        )
                    }
                }

                items(activeTasks, key = { "active-${it.id}" }) { task ->
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
                        count = completedTasks.size,
                        expanded = completedExpanded,
                        onClick = { completedExpanded = !completedExpanded },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    )
                }

                if (completedExpanded) {
                    if (completedTasks.isEmpty()) {
                        item(key = "completed-empty") {
                            TaskPreviewEmptyState(
                                text = "No completed tasks",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp)
                            )
                        }
                    } else {
                        items(completedTasks, key = { "completed-${it.id}" }) { task ->
                            TaskCard(
                                task = task,
                                completed = true,
                                onComplete = onCompleteTask,
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
        }
    }
}

@Composable
private fun TaskPreviewTopBar(
    dateLabel: String,
    onCreateTask: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TaskPreviewIconButton(
            icon = Icons.Filled.Add,
            contentDescription = "Create task",
            dark = true,
            onClick = onCreateTask
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Tasks",
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
                maxLines = 1
            )
        }
        TaskPreviewIconButton(
            icon = Icons.Filled.Close,
            contentDescription = "Close",
            onClick = onClose
        )
    }
}

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
            text = "Completed",
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
