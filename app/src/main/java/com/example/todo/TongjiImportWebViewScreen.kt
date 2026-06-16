package com.example.todo

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.todo.ui.theme.BeigeBackground
import com.example.todo.ui.theme.DeepCharcoal
import com.example.todo.ui.theme.GlassStroke
import com.example.todo.ui.theme.MutedBrown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val TongjiTimetableUrl = "https://1.tongji.edu.cn/GraduateStudentTimeTable"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TongjiImportWebViewScreen(
    onClose: () -> Unit,
    onImportSchedules: (List<Schedule>) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onClose)
    val buttonText = stringResource(R.string.tongji_import_button_text)
    val importButtonScript = remember(buttonText) {
        tongjiImportButtonScript(buttonText)
    }
    val strOpenPage = stringResource(R.string.tongji_open_page)
    val strParsing = stringResource(R.string.tongji_parsing)
    val strWaiting = stringResource(R.string.tongji_waiting)
    val strParseFailed = stringResource(R.string.tongji_parse_failed_error)
    val strEmptyCourse = stringResource(R.string.tongji_empty_course_error)
    val strRecognizedTemplate = stringResource(R.string.tongji_recognized_courses)
    val strInvalidCourse = stringResource(R.string.tongji_invalid_course_error)
    val strImportComplete = stringResource(R.string.tongji_import_complete)
    val strLoginThenImport = stringResource(R.string.tongji_login_then_import)
    val strReloadPage = stringResource(R.string.tongji_reload_page)

    var webView by remember { mutableStateOf<WebView?>(null) }
    var statusText by remember { mutableStateOf(strOpenPage) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var parsedCourses by remember { mutableStateOf<List<TongjiCourse>>(emptyList()) }
    var semesterStart by remember { mutableStateOf(TongjiTimetableImporter.defaultSemesterStartDate()) }
    var importedCount by remember { mutableIntStateOf(0) }
    var parseJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val latestParseJob by rememberUpdatedState(parseJob)
    val latestWebView by rememberUpdatedState(webView)
    val latestOnImportSchedules by rememberUpdatedState(onImportSchedules)

    fun handlePayload(payload: String) {
        if (parseJob?.isActive == true) return

        statusText = strParsing
        errorText = null
        val diagnostics = tongjiPayloadDiagnostics(payload)
        Log.d(TongjiImportLogTag, "Payload length=${payload.length}; diagnostics=${diagnostics.orEmpty()}")

        parseJob = scope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    TongjiTimetableImporter.parseCourses(payload)
                }
            }

            val courses = result.getOrElse {
                Log.e(TongjiImportLogTag, "Failed to parse Tongji timetable payload", it)
                statusText = strWaiting
                errorText = strParseFailed
                return@launch
            }

            if (courses.isEmpty()) {
                statusText = strWaiting
                errorText = buildString {
                    append(strEmptyCourse)
                    if (!diagnostics.isNullOrBlank()) {
                        append("\n\n")
                        append(diagnostics)
                    }
                }
                return@launch
            }

            parsedCourses = courses
            statusText = strRecognizedTemplate.format(courses.size)
        }
    }

    fun confirmImport() {
        val schedules = TongjiTimetableImporter.toSchedules(parsedCourses, semesterStart)
        if (schedules.isEmpty()) {
            errorText = strInvalidCourse
            return
        }

        latestOnImportSchedules(schedules)
        importedCount = schedules.size
        parsedCourses = emptyList()
        statusText = strImportComplete
        errorText = null
    }

    DisposableEffect(Unit) {
        onDispose {
            latestParseJob?.cancel()
            latestWebView?.destroy()
            webView = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BeigeBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TongjiImportTopBar(
                statusText = statusText,
                onClose = onClose,
                onReload = {
                    errorText = null
                    statusText = strReloadPage
                    webView?.loadUrl(TongjiTimetableUrl)
                }
            )

            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            webView = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            settings.builtInZoomControls = false
                            settings.displayZoomControls = false
                            addJavascriptInterface(
                                TongjiImportBridge { payload ->
                                    Handler(Looper.getMainLooper()).post {
                                        handlePayload(payload)
                                    }
                                },
                                "TongjiImportBridge"
                            )
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String?) {
                                    super.onPageFinished(view, url)
                                    statusText = strLoginThenImport
                                    view.evaluateJavascript(importButtonScript, null)
                                }
                            }
                            loadUrl(TongjiTimetableUrl)
                        }
                    }
                )

                Text(
                    text = stringResource(R.string.tongji_local_only),
                    color = DeepCharcoal.copy(alpha = 0.62f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                        .background(Color.White.copy(alpha = 0.78f), RoundedCornerShape(999.dp))
                        .border(1.dp, GlassStroke, RoundedCornerShape(999.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        val currentError = errorText
        when {
            parsedCourses.isNotEmpty() -> {
                SemesterStartPanel(
                    courses = parsedCourses,
                    semesterStart = semesterStart,
                    onSemesterStartChange = { semesterStart = it },
                    onCancel = { parsedCourses = emptyList() },
                    onConfirm = ::confirmImport,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
            importedCount > 0 -> {
                ImportResultPanel(
                    importedCount = importedCount,
                    onDone = onClose,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
            currentError != null -> {
                ImportErrorPanel(
                    message = currentError,
                    onDismiss = { errorText = null },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

private const val TongjiImportLogTag = "TongjiImport"

private fun tongjiPayloadDiagnostics(payload: String): String? {
    val markerStart = payload.indexOf(DebugMarker)
    if (markerStart < 0) return null

    val contentStart = markerStart + DebugMarker.length
    val markerEnd = listOf(
        "[[SELECTED_COURSES]]",
        "[[TIMETABLE_GRID]]",
        "[[TABLE]]",
        "[[BODY]]",
        "[[SCRIPTS]]"
    )
        .asSequence()
        .mapNotNull { marker -> payload.indexOf(marker, contentStart).takeIf { it >= contentStart } }
        .minOrNull()
        ?: payload.length

    return payload
        .substring(contentStart, markerEnd)
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .take(12)
        .joinToString("\n")
        .takeIf { it.isNotBlank() }
}

private const val DebugMarker = "[[DEBUG]]"

class TongjiImportBridge(
    private val onPayload: (String) -> Unit
) {
    @JavascriptInterface
    fun submit(payload: String) {
        onPayload(payload)
    }
}

@Composable
private fun TongjiImportTopBar(
    statusText: String,
    onClose: () -> Unit,
    onReload: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(BeigeBackground)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleIconButton(
            icon = Icons.Filled.Close,
            contentDescription = stringResource(R.string.cd_close),
            onClick = onClose
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.tongji_import_screen_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = DeepCharcoal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                color = MutedBrown,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        CircleIconButton(
            icon = Icons.Filled.Refresh,
            contentDescription = stringResource(R.string.tongji_refresh),
            onClick = onReload
        )
    }
}

@Composable
private fun SemesterStartPanel(
    courses: List<TongjiCourse>,
    semesterStart: LocalDate,
    onSemesterStartChange: (LocalDate) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    BottomPanel(modifier = modifier) {
        Text(
            text = stringResource(R.string.tongji_confirm_semester_start),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = DeepCharcoal
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.tongji_recognized_courses_need_start, courses.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MutedBrown,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        CoursePreviewList(
            courses = courses,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        DateStepper(
            date = semesterStart,
            onDateChange = onSemesterStartChange
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PanelActionButton(
                text = stringResource(R.string.tongji_cancel),
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                inverted = false
            )
            PanelActionButton(
                text = stringResource(R.string.tongji_import_action),
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                inverted = true
            )
        }
    }
}

@Composable
private fun CoursePreviewList(
    courses: List<TongjiCourse>,
    modifier: Modifier = Modifier
) {
    val sortedCourses = remember(courses) {
        courses.sortedWith(compareBy({ it.weekday }, { it.startSection }, { it.name }))
    }

    LazyColumn(
        modifier = modifier
            .heightIn(max = 230.dp)
            .border(1.dp, Color.Black.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.34f), RoundedCornerShape(18.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(sortedCourses) { course ->
            CoursePreviewRow(course = course)
        }
    }
}

@Composable
private fun CoursePreviewRow(course: TongjiCourse) {
    val primaryLine = localizedCoursePreviewPrimaryLine(course)
    val detailLine = localizedCoursePreviewDetailLine(course)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.Black.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .background(BeigeBackground.copy(alpha = 0.82f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = course.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            color = DeepCharcoal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = primaryLine,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = DeepCharcoal.copy(alpha = 0.82f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (detailLine.isNotBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = detailLine,
                style = MaterialTheme.typography.bodySmall,
                color = MutedBrown,
                lineHeight = 17.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun courseWeekdayName(weekday: Int): String? =
    LocalContext.current.resources.getStringArray(R.array.weekday_short).getOrNull(weekday - 1)

@Composable
private fun localizedCoursePreviewPrimaryLine(course: TongjiCourse): String =
    listOfNotNull(
        courseWeekdayName(course.weekday),
        courseSectionLabel(course.startSection, course.endSection),
        TongjiTimetableImporter.classClockRangeText(course)
    ).joinToString(" / ")

@Composable
private fun localizedCoursePreviewDetailLine(course: TongjiCourse): String =
    listOfNotNull(
        course.teacher.takeIf { it.isNotBlank() }?.let { "${stringResource(R.string.detail_head_teacher)}: $it" },
        course.room.takeIf { it.isNotBlank() }?.let { "${stringResource(R.string.detail_head_room)}: $it" },
        course.weeks.takeIf { it.isNotEmpty() }?.let { weeksText(course) }
            ?.let { stringResource(R.string.tongji_weeks_preview, it) }
    ).joinToString(" / ")

@Composable
private fun coursePreviewPrimaryLine(course: TongjiCourse): String =
    listOfNotNull(
        courseWeekdayName(course.weekday),
        courseSectionLabel(course.startSection, course.endSection),
        TongjiTimetableImporter.classClockRangeText(course)
    ).joinToString(" · ")

@Composable
private fun courseSectionLabel(startSection: Int, endSection: Int): String {
    val range = if (startSection == endSection) startSection.toString() else "$startSection-$endSection"
    return "${stringResource(R.string.detail_head_sections)} $range"
}

@Composable
private fun coursePreviewDetailLine(course: TongjiCourse): String =
    listOfNotNull(
        course.teacher.takeIf { it.isNotBlank() }?.let { "${stringResource(R.string.detail_head_teacher)}: $it" },
        course.room.takeIf { it.isNotBlank() }?.let { "${stringResource(R.string.detail_head_room)}: $it" },
        course.weeks.takeIf { it.isNotEmpty() }?.let { weeksText(course) }
            ?.let { stringResource(R.string.tongji_weeks_preview, it) }
    ).joinToString(" · ")

private fun weeksText(course: TongjiCourse): String {
    if (course.weekText.isNotBlank()) return course.weekText

    val ranges = course.weeks
        .distinct()
        .sorted()
        .fold(mutableListOf<IntRange>()) { result, week ->
            val last = result.lastOrNull()
            if (last != null && week == last.last + 1) {
                result[result.lastIndex] = last.first..week
            } else {
                result += week..week
            }
            result
        }
        .joinToString(", ") { range ->
            if (range.first == range.last) range.first.toString() else "${range.first}-${range.last}"
        }

    return ranges
}

@Composable
private fun DateStepper(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit
) {
    val dateText = remember(date) { date.format(DateTimeFormatter.ISO_LOCAL_DATE) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.Black.copy(alpha = 0.18f), RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.52f), RoundedCornerShape(28.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleIconButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = stringResource(R.string.tongji_previous_day),
            onClick = { onDateChange(date.minusDays(1)) }
        )
        Text(
            text = dateText,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = DeepCharcoal
        )
        CircleIconButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.tongji_next_day),
            onClick = { onDateChange(date.plusDays(1)) }
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SmallDateButton("-7", onClick = { onDateChange(date.minusWeeks(1)) }, modifier = Modifier.weight(1f))
        SmallDateButton("+7", onClick = { onDateChange(date.plusWeeks(1)) }, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ImportResultPanel(
    importedCount: Int,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    BottomPanel(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(
                icon = Icons.Filled.Check,
                contentDescription = null,
                onClick = onDone,
                enabled = false
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.tongji_import_complete),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = DeepCharcoal
                )
                Text(
                    text = stringResource(R.string.tongji_saved_course_schedules, importedCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedBrown
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        PanelActionButton(
            text = stringResource(R.string.tongji_done),
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            inverted = true
        )
    }
}

@Composable
private fun ImportErrorPanel(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BottomPanel(modifier = modifier) {
        Text(
            text = stringResource(R.string.tongji_cannot_import),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = DeepCharcoal
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MutedBrown,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        PanelActionButton(
            text = stringResource(R.string.tongji_got_it),
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            inverted = true
        )
    }
}

@Composable
private fun BottomPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(18.dp)
            .border(1.dp, GlassStroke, RoundedCornerShape(30.dp))
            .background(BeigeBackground.copy(alpha = 0.96f), RoundedCornerShape(30.dp))
            .padding(20.dp),
        content = content
    )
}

@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .background(
                if (enabled) Color.Black else Color.Black.copy(alpha = 0.18f),
                RoundedCornerShape(999.dp)
            )
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun PanelActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    inverted: Boolean
) {
    val background = if (inverted) Color.Black else Color.White.copy(alpha = 0.52f)
    val foreground = if (inverted) Color.White else DeepCharcoal

    Box(
        modifier = modifier
            .height(48.dp)
            .border(1.dp, Color.Black.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
            .background(background, RoundedCornerShape(999.dp))
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = foreground,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun SmallDateButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .border(1.dp, Color.Black.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.38f), RoundedCornerShape(999.dp))
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = DeepCharcoal,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black
        )
    }
}

private fun tongjiImportButtonScript(buttonText: String): String {
    val buttonLabel = JSONObject.quote(buttonText)
    return """
(function() {
  var pageText = (location.href + ' ' + document.title + ' ' + (document.body ? document.body.innerText : ''));
  if (!/GraduateStudentTimeTable|课表|课程|TimeTable|timetable/i.test(pageText)) return;
  if (document.getElementById('__tongji_import_button')) return;

  function plainCellText(element) {
    return String(element ? (element.innerText || element.textContent || '') : '')
      .replace(/\r/g, '\n')
      .split('\n')
      .map(function(line) { return line.replace(/\s+/g, ' ').trim(); })
      .filter(Boolean)
      .join(' ')
      .replace(/\t/g, ' ')
      .trim();
  }

  function courseCellText(cell) {
    if (!cell) return '';
    var reference = cell.querySelector('.el-popover__reference');
    if (reference) return plainCellText(reference);
    var content = cell.querySelector('.cell');
    return plainCellText(content || cell);
  }

  function firstMatching(root, selectors) {
    for (var i = 0; i < selectors.length; i += 1) {
      var match = root.querySelector(selectors[i]);
      if (match) return match;
    }
    return null;
  }

  function headerIndex(headers, names) {
    for (var exactIndex = 0; exactIndex < headers.length; exactIndex += 1) {
      for (var exactNameIndex = 0; exactNameIndex < names.length; exactNameIndex += 1) {
        if (headers[exactIndex] === names[exactNameIndex]) return exactIndex;
      }
    }
    for (var i = 0; i < headers.length; i += 1) {
      for (var j = 0; j < names.length; j += 1) {
        if (headers[i].indexOf(names[j]) >= 0) return i;
      }
    }
    return -1;
  }

  function collect() {
    var lines = [];
    var debugLines = [];
    var remaining = 120000;

    function pushLine(text) {
      if (remaining <= 0) return;
      var value = String(text || '');
      if (value.length > remaining) value = value.slice(0, remaining);
      lines.push(value);
      remaining -= value.length;
    }

    function pushDebug(text) {
      debugLines.push(String(text || '').replace(/\s+/g, ' ').trim());
    }

    function collectDocuments() {
      var docs = [{ label: 'main', doc: document }];
      Array.prototype.slice.call(document.querySelectorAll('iframe,frame')).forEach(function(frame, index) {
        try {
          if (frame.contentDocument) {
            docs.push({ label: 'frame' + index, doc: frame.contentDocument });
          }
        } catch (error) {
          pushDebug('frame' + index + ': inaccessible');
        }
      });
      return docs;
    }

    function collectSelectedCourseTables(doc, scopeLabel) {
      var found = 0;
      var tables = Array.prototype.slice.call(doc.querySelectorAll('.el-table'));
      pushDebug(scopeLabel + ': elTables=' + tables.length + ', classTimeTables=' + doc.querySelectorAll('table.classTimeTable').length);
      tables.forEach(function(root, tableIndex) {
        var headerTable = firstMatching(root, [
          '.el-table__header-wrapper table',
          '.el-table__fixed-header-wrapper table',
          '.el-table__fixed-right .el-table__fixed-header-wrapper table'
        ]);
        var bodyTable = firstMatching(root, [
          '.el-table__body-wrapper table',
          '.el-table__fixed-body-wrapper table',
          '.el-table__fixed-right .el-table__fixed-body-wrapper table'
        ]);
        if (!headerTable || !bodyTable) {
          pushDebug(scopeLabel + ': table' + tableIndex + ' missing header/body');
          return;
        }

        var headerRow = headerTable.querySelector('thead tr');
        if (!headerRow) {
          pushDebug(scopeLabel + ': table' + tableIndex + ' missing header row');
          return;
        }
        var headers = Array.prototype.slice.call(headerRow.cells || []).map(function(cell) {
          return courseCellText(cell);
        });
        var bodyRows = Array.prototype.slice.call(bodyTable.querySelectorAll('tbody tr'));

        var nameIndex = headerIndex(headers, ['课程名称', '课程名']);
        var timeIndex = headerIndex(headers, ['上课时间']);
        var teacherIndex = headerIndex(headers, ['教师', '任课教师', '老师']);
        var roomIndex = headerIndex(headers, ['上课地点', '教室', '地点']);
        var codeIndex = headerIndex(headers, ['课程序号']);
        var newCodeIndex = headerIndex(headers, ['新课程序号']);
        pushDebug(scopeLabel + ': table' + tableIndex + ' rows=' + bodyRows.length + ', nameIndex=' + nameIndex + ', timeIndex=' + timeIndex + ', headers=' + headers.join('|'));
        if (nameIndex < 0 || timeIndex < 0) return;

        bodyRows.forEach(function(row) {
          var cells = Array.prototype.slice.call(row.cells || []).map(function(cell) {
            return courseCellText(cell);
          });
          var name = cells[nameIndex] || '';
          var time = cells[timeIndex] || '';
          if (!name || !/星期|周[一二三四五六日天]|Mon|Tue|Wed|Thu|Fri|Sat|Sun/i.test(time)) return;
          if (found < 3) {
            pushDebug(scopeLabel + ': sample' + found + ' name=' + name + ', time=' + time + ', sectionMatch=' + (/(\d{1,2})\s*[-~至到,，、–—－]\s*(\d{1,2})\s*节|\d{1,2}\s*节/.test(time)));
          }

          if (found === 0) pushLine('[[SELECTED_COURSES]]');
          pushLine('课程名称：' + name);
          if (codeIndex >= 0 && cells[codeIndex]) pushLine('课程序号：' + cells[codeIndex]);
          if (newCodeIndex >= 0 && cells[newCodeIndex]) pushLine('新课程序号：' + cells[newCodeIndex]);
          if (teacherIndex >= 0 && cells[teacherIndex]) pushLine('教师：' + cells[teacherIndex]);
          pushLine('上课时间：' + time);
          if (roomIndex >= 0 && cells[roomIndex]) pushLine('上课地点：' + cells[roomIndex]);
          pushLine('');
          found += 1;
        });
      });
      return found;
    }

    pushDebug('url=' + location.href);
    pushDebug('title=' + document.title);
    pushDebug('bodyTextLength=' + (document.body ? plainCellText(document.body).length : 0));

    var selectedCourseCount = 0;
    collectDocuments().forEach(function(entry) {
      selectedCourseCount += collectSelectedCourseTables(entry.doc, entry.label);
    });
    if (selectedCourseCount > 0) console.log('[TongjiImport] Selected course rows: ' + selectedCourseCount);
    if (selectedCourseCount === 0) console.log('[TongjiImport] No selected course rows found.');
    pushDebug('selectedCourseRows=' + selectedCourseCount);

    window.TongjiImportBridge.submit(['[[DEBUG]]'].concat(debugLines, lines).join('\n'));
  }

  var button = document.createElement('button');
  button.id = '__tongji_import_button';
  button.type = 'button';
  button.textContent = $buttonLabel;
  button.onclick = collect;
  button.style.position = 'fixed';
  button.style.right = '18px';
  button.style.bottom = '22px';
  button.style.zIndex = '2147483647';
  button.style.border = '1px solid rgba(255,255,255,.55)';
  button.style.borderRadius = '999px';
  button.style.background = '#1f1f1f';
  button.style.color = '#fff';
  button.style.fontSize = '15px';
  button.style.fontWeight = '700';
  button.style.padding = '12px 18px';
  button.style.boxShadow = '0 10px 28px rgba(0,0,0,.22)';
  document.documentElement.appendChild(button);
})();
""".trimIndent()
}
