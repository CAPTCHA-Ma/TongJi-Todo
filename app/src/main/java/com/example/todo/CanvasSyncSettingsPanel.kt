package com.example.todo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CanvasSyncSettingsPanel(
    currentStore: PlannerItemStore,
    onSyncCompleted: (PlannerItemStore) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val resources = LocalContext.current.resources
    val localStore = remember(context) { CanvasSyncLocalStore(context) }
    val scope = rememberCoroutineScope()
    var tokenInput by remember { mutableStateOf("") }
    var hasToken by remember { mutableStateOf(localStore.hasSavedToken()) }
    var lastSyncMillis by remember { mutableStateOf(localStore.loadLastSyncTimeMillis()) }
    var isSyncing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, Color.Black.copy(alpha = 0.24f), RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.42f), RoundedCornerShape(28.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CanvasPanelHeader(hasToken = hasToken)

        errorMessage?.let { message ->
            CanvasMessageCard(
                text = message,
                error = true,
                onClose = { errorMessage = null }
            )
        }

        statusMessage?.let { message ->
            CanvasMessageCard(
                text = message,
                error = message.contains("fail", ignoreCase = true),
                onClose = { statusMessage = null }
            )
        }

        CanvasTokenField(
            value = tokenInput,
            onValueChange = { tokenInput = it },
            label = if (hasToken) stringResource(R.string.canvas_update_token) else stringResource(R.string.canvas_token_label)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            CanvasPanelButton(
                text = if (hasToken) stringResource(R.string.canvas_save_new_token) else stringResource(R.string.canvas_save_token),
                icon = Icons.Filled.Key,
                onClick = {
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                localStore.saveToken(tokenInput)
                            }
                        }.onSuccess {
                            tokenInput = ""
                            hasToken = true
                            statusMessage = resources.getString(R.string.canvas_token_saved)
                            runCatching { CanvasSyncScheduler.schedule(context) }
                                .onFailure { Log.e(CanvasSyncSettingsLogTag, "Failed to schedule after token save", it) }
                        }.onFailure { error ->
                            errorMessage = error.message ?: resources.getString(R.string.canvas_save_token_failed)
                        }
                    }
                },
                enabled = tokenInput.isNotBlank() && !isSyncing,
                modifier = Modifier.weight(1f),
                inverted = true
            )

            CanvasIconActionButton(
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = stringResource(R.string.canvas_open_token_page),
                onClick = { context.openCanvasTokenPage() },
                enabled = !isSyncing
            )
        }

        CanvasSyncInfoRow(
            label = stringResource(R.string.canvas_last_sync),
            value = lastSyncMillis?.formatSyncTime() ?: stringResource(R.string.canvas_never)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            CanvasPanelButton(
                text = if (isSyncing) stringResource(R.string.canvas_syncing) else stringResource(R.string.canvas_sync_now),
                icon = Icons.Filled.Sync,
                onClick = {
                    isSyncing = true
                    statusMessage = resources.getString(R.string.canvas_syncing_assignments)
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                CanvasSync(context).syncPlannerStore(currentStore, forceRefresh = true)
                            }
                        }.onSuccess { result ->
                            isSyncing = false
                            if (result.isSuccess) {
                                result.nextStore?.let(onSyncCompleted)
                                lastSyncMillis = result.syncedAtMillis ?: localStore.loadLastSyncTimeMillis()
                                statusMessage = resources.getString(
                                    R.string.canvas_sync_complete,
                                    result.insertedCount,
                                    result.updatedCount,
                                    result.totalAssignments
                                )
                                runCatching { CanvasSyncScheduler.schedule(context) }
                                    .onFailure { Log.e(CanvasSyncSettingsLogTag, "Failed to schedule Canvas sync", it) }
                            } else {
                                errorMessage = result.message
                                statusMessage = null
                            }
                        }.onFailure { error ->
                            isSyncing = false
                            Log.e(CanvasSyncSettingsLogTag, "Manual Canvas sync crashed", error)
                            errorMessage = error.message ?: resources.getString(R.string.canvas_sync_error)
                            statusMessage = null
                        }
                    }
                },
                enabled = hasToken && !isSyncing,
                modifier = Modifier.weight(1f),
                inverted = true,
                loading = isSyncing
            )

            CanvasIconActionButton(
                icon = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.canvas_clear_token),
                onClick = {
                    localStore.clearToken()
                    CanvasSyncScheduler.cancel(context)
                    hasToken = false
                    tokenInput = ""
                    statusMessage = resources.getString(R.string.canvas_token_cleared)
                },
                enabled = hasToken && !isSyncing
            )
        }
    }
}

@Composable
private fun CanvasPanelHeader(hasToken: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color.Black, RoundedCornerShape(999.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Sync,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.canvas_sync_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (hasToken) stringResource(R.string.canvas_status_token_saved) else stringResource(R.string.canvas_status_token_required),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = Color.Black.copy(alpha = 0.48f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CanvasMessageCard(
    text: String,
    error: Boolean,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                2.dp,
                if (error) Color(0xFFD93A32).copy(alpha = 0.42f) else Color.Black.copy(alpha = 0.18f),
                RoundedCornerShape(22.dp)
            )
            .background(
                if (error) Color(0xFFFFE7E4).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.54f),
                RoundedCornerShape(22.dp)
            )
            .padding(start = 14.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            color = if (error) Color(0xFFD93A32) else Color.Black.copy(alpha = 0.68f),
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = onClose
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.canvas_dismiss),
                tint = Color.Black.copy(alpha = 0.54f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun CanvasTokenField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, Color.Black.copy(alpha = 0.24f), RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.52f), RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Key,
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.62f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = Color.Black.copy(alpha = 0.58f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = Color.Black,
                fontWeight = FontWeight.Bold
            ),
            cursorBrush = SolidColor(Color.Black),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isBlank()) {
                        Text(
                            text = stringResource(R.string.canvas_paste_token),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Black.copy(alpha = 0.28f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun CanvasSyncInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .border(2.dp, Color.Black.copy(alpha = 0.14f), RoundedCornerShape(22.dp))
            .background(Color.Black.copy(alpha = 0.06f), RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            color = Color.Black.copy(alpha = 0.46f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            color = Color.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CanvasPanelButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    inverted: Boolean = false,
    loading: Boolean = false
) {
    val background = when {
        !enabled -> Color.Black.copy(alpha = 0.18f)
        inverted -> Color.Black
        else -> Color.White.copy(alpha = 0.46f)
    }
    val border = when {
        !enabled -> Color.Black.copy(alpha = 0.12f)
        inverted -> Color.Black
        else -> Color.Black.copy(alpha = 0.28f)
    }
    val content = when {
        !enabled -> Color.Black.copy(alpha = 0.34f)
        inverted -> Color.White
        else -> Color.Black
    }

    Row(
        modifier = modifier
            .height(48.dp)
            .border(2.dp, border, RoundedCornerShape(999.dp))
            .background(background, RoundedCornerShape(999.dp))
            .clickable(
                enabled = enabled,
                interactionSource = null,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = content
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CanvasIconActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean
) {
    val content = if (enabled) Color.Black else Color.Black.copy(alpha = 0.32f)
    Box(
        modifier = Modifier
            .size(48.dp)
            .border(2.dp, Color.Black.copy(alpha = if (enabled) 0.28f else 0.12f), RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = if (enabled) 0.46f else 0.22f), RoundedCornerShape(999.dp))
            .clickable(
                enabled = enabled,
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
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun Context.openCanvasTokenPage() {
    val intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://canvas.tongji.edu.cn/profile/settings")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

private fun Long.formatSyncTime(): String =
    Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

private const val CanvasSyncSettingsLogTag = "CanvasSyncSettings"
