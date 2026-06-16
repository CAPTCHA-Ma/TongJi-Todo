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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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

private const val TongjiExamUrl = "https://1.tongji.edu.cn/StuExamEnquiries"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TongjiExamImportWebViewScreen(
    onClose: () -> Unit,
    onImportTasks: (List<Task>) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onClose)

    val buttonText = stringResource(R.string.tongji_exam_import_button_text)
    val importButtonScript = remember(buttonText) {
        tongjiExamImportButtonScript(buttonText)
    }
    val strOpenPage = stringResource(R.string.tongji_exam_open_page)
    val strParsing = stringResource(R.string.tongji_exam_parsing)
    val strWaiting = stringResource(R.string.tongji_exam_waiting)
    val strParseFailed = stringResource(R.string.tongji_exam_parse_failed_error)
    val strEmptyExam = stringResource(R.string.tongji_exam_empty_error)
    val strInvalidExam = stringResource(R.string.tongji_exam_invalid_error)
    val strImportComplete = stringResource(R.string.tongji_exam_import_complete)
    val strLoginThenImport = stringResource(R.string.tongji_exam_login_then_import)
    val strReloadPage = stringResource(R.string.tongji_exam_reload_page)

    var webView by remember { mutableStateOf<WebView?>(null) }
    var statusText by remember { mutableStateOf(strOpenPage) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var importedCount by remember { mutableIntStateOf(0) }
    var parseJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val latestParseJob by rememberUpdatedState(parseJob)
    val latestWebView by rememberUpdatedState(webView)
    val latestOnImportTasks by rememberUpdatedState(onImportTasks)

    fun handlePayload(payload: String) {
        if (parseJob?.isActive == true) return

        statusText = strParsing
        errorText = null
        importedCount = 0
        val diagnostics = tongjiExamPayloadDiagnostics(payload)
        Log.d(TongjiExamImportLogTag, "Payload length=${payload.length}; diagnostics=${diagnostics.orEmpty()}")

        parseJob = scope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val exams = TongjiExamImporter.parseExams(payload)
                    exams to TongjiExamImporter.toTasks(exams)
                }
            }

            val (exams, tasks) = result.getOrElse {
                Log.e(TongjiExamImportLogTag, "Failed to parse Tongji exam payload", it)
                statusText = strWaiting
                errorText = strParseFailed
                return@launch
            }

            when {
                exams.isEmpty() -> {
                    statusText = strWaiting
                    errorText = buildString {
                        append(strEmptyExam)
                        if (!diagnostics.isNullOrBlank()) {
                            append("\n\n")
                            append(diagnostics)
                        }
                    }
                }
                tasks.isEmpty() -> {
                    statusText = strWaiting
                    errorText = buildString {
                        append(strInvalidExam)
                        if (!diagnostics.isNullOrBlank()) {
                            append("\n\n")
                            append(diagnostics)
                        }
                    }
                }
                else -> {
                    latestOnImportTasks(tasks)
                    importedCount = tasks.size
                    statusText = strImportComplete
                    errorText = null
                }
            }
        }
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
            TongjiExamImportTopBar(
                statusText = statusText,
                onClose = onClose,
                onReload = {
                    errorText = null
                    importedCount = 0
                    statusText = strReloadPage
                    webView?.loadUrl(TongjiExamUrl)
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
                                TongjiExamImportBridge { payload ->
                                    Handler(Looper.getMainLooper()).post {
                                        handlePayload(payload)
                                    }
                                },
                                "TongjiExamImportBridge"
                            )
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String?) {
                                    super.onPageFinished(view, url)
                                    statusText = strLoginThenImport
                                    view.evaluateJavascript(importButtonScript, null)
                                }
                            }
                            loadUrl(TongjiExamUrl)
                        }
                    }
                )

                Text(
                    text = stringResource(R.string.tongji_exam_local_only),
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
            importedCount > 0 -> {
                ExamImportResultPanel(
                    importedCount = importedCount,
                    onDone = onClose,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
            currentError != null -> {
                ExamImportErrorPanel(
                    message = currentError,
                    onDismiss = { errorText = null },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

private const val TongjiExamImportLogTag = "TongjiExamImport"

private fun tongjiExamPayloadDiagnostics(payload: String): String? {
    val markerStart = payload.indexOf("[[DEBUG]]")
    if (markerStart < 0) return null

    val contentStart = markerStart + "[[DEBUG]]".length
    val markerEnd = listOf(
        "[[EXAM_ROWS]]",
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
        .take(18)
        .joinToString("\n")
        .takeIf { it.isNotBlank() }
}

class TongjiExamImportBridge(
    private val onPayload: (String) -> Unit
) {
    @JavascriptInterface
    fun submit(payload: String) {
        onPayload(payload)
    }
}

@Composable
private fun TongjiExamImportTopBar(
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
        ExamCircleIconButton(
            icon = Icons.Filled.Close,
            contentDescription = stringResource(R.string.cd_close),
            onClick = onClose
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.tongji_exam_import_screen_title),
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
        ExamCircleIconButton(
            icon = Icons.Filled.Refresh,
            contentDescription = stringResource(R.string.tongji_exam_refresh),
            onClick = onReload
        )
    }
}

@Composable
private fun ExamImportResultPanel(
    importedCount: Int,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    ExamBottomPanel(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ExamCircleIconButton(
                icon = Icons.Filled.Check,
                contentDescription = null,
                onClick = onDone,
                enabled = false
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.tongji_exam_import_complete),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = DeepCharcoal
                )
                Text(
                    text = stringResource(R.string.tongji_exam_saved_tasks, importedCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedBrown
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        ExamPanelActionButton(
            text = stringResource(R.string.tongji_done),
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            inverted = true
        )
    }
}

@Composable
private fun ExamImportErrorPanel(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ExamBottomPanel(modifier = modifier) {
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
        ExamPanelActionButton(
            text = stringResource(R.string.tongji_got_it),
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            inverted = true
        )
    }
}

@Composable
private fun ExamBottomPanel(
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
private fun ExamCircleIconButton(
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
private fun ExamPanelActionButton(
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

private fun tongjiExamImportButtonScript(buttonText: String): String {
    val buttonLabel = JSONObject.quote(buttonText)
    return """
(function() {
  var pageText = (location.href + ' ' + document.title + ' ' + (document.body ? document.body.innerText : ''));
  if (!/StuExamEnquiries|\u8003\u8bd5|Exam/i.test(pageText)) return;
  if (document.getElementById('__tongji_exam_import_button')) return;

  var courseLabels = ['\u8bfe\u7a0b\u540d\u79f0', '\u8bfe\u7a0b\u540d', 'course name'];
  var examTimeLabels = ['\u8003\u8bd5\u65f6\u95f4', 'exam time'];
  var locationLabels = ['\u8003\u8bd5\u5730\u70b9', 'exam room', 'location'];
  var dateRegex = /(\d{4}\s*(?:\u5e74|[-/.])\s*\d{1,2}\s*(?:\u6708|[-/.])\s*\d{1,2})|(\d{8})/;
  var timeRegex = /\d{1,2}\s*[:\uff1a]\s*\d{1,2}/;

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

  function visibleLines(element) {
    return String(element ? (element.innerText || element.textContent || '') : '')
      .replace(/\r/g, '\n')
      .split('\n')
      .map(function(line) { return line.replace(/\s+/g, ' ').trim(); })
      .filter(Boolean);
  }

  function cellText(cell) {
    if (!cell) return '';
    var reference = cell.querySelector('.el-popover__reference');
    if (reference) return plainCellText(reference);
    var content = cell.querySelector('.cell');
    return plainCellText(content || cell);
  }

  function normalizeHeader(text) {
    return String(text || '').toLowerCase().replace(/[\s:_\-\/\\()\[\]\u3010\u3011\uff08\uff09]+/g, '').trim();
  }

  function headerIndex(headers, names) {
    var normalizedHeaders = headers.map(normalizeHeader);
    var normalizedNames = names.map(normalizeHeader);
    for (var exactIndex = 0; exactIndex < normalizedHeaders.length; exactIndex += 1) {
      for (var exactNameIndex = 0; exactNameIndex < normalizedNames.length; exactNameIndex += 1) {
        if (normalizedHeaders[exactIndex] === normalizedNames[exactNameIndex]) return exactIndex;
      }
    }
    for (var i = 0; i < normalizedHeaders.length; i += 1) {
      for (var j = 0; j < normalizedNames.length; j += 1) {
        if (normalizedHeaders[i].indexOf(normalizedNames[j]) >= 0) return i;
      }
    }
    return -1;
  }

  function textContainsAny(text, needles) {
    var value = String(text || '').toLowerCase();
    return needles.some(function(needle) {
      return value.indexOf(String(needle || '').toLowerCase()) >= 0;
    });
  }

  function textLooksLikeDate(text) {
    return dateRegex.test(String(text || ''));
  }

  function textLooksLikeClock(text) {
    return timeRegex.test(String(text || ''));
  }

  function textLooksLikeLocation(text) {
    return /(\u6821\u533a|\u6559\u5ba4|\u6559\u5b66|\u697c|\u9986|\u5ba4|\u6821\u56ed|campus|room|classroom|building)/i.test(String(text || ''));
  }

  function textLooksLikeCode(text) {
    var value = String(text || '').trim();
    return /^[A-Za-z0-9][A-Za-z0-9_.\-]{4,}$/.test(value) && !textLooksLikeDate(value) && !textLooksLikeClock(value);
  }

  function textLooksLikeAction(text) {
    return /^(\u5e8f\u53f7|\u64cd\u4f5c|\u67e5\u770b|\u8be6\u60c5|\u6682\u65e0\u6570\u636e|no data|\d+)$/.test(String(text || '').trim().toLowerCase());
  }

  function textLooksLikeHeaderNoise(text) {
    return textContainsAny(text, [
      '\u6392\u8003\u5217\u8868',
      '\u8003\u8bd5\u67e5\u8be2',
      '\u8bfe\u7a0b\u540d\u79f0',
      '\u8003\u8bd5\u65e5\u671f',
      '\u8003\u8bd5\u65f6\u95f4',
      '\u8003\u8bd5\u5730\u70b9',
      '\u5ea7\u4f4d\u53f7',
      'course name',
      'exam date',
      'exam time'
    ]);
  }

  function findFirstCellIndex(cells, predicate) {
    for (var i = 0; i < cells.length; i += 1) {
      if (predicate(cells[i], i)) return i;
    }
    return -1;
  }

  function inferCourseIndex(headers, cells, excludedIndexes) {
    var bestIndex = -1;
    var bestScore = -999;
    var excluded = {};
    excludedIndexes.forEach(function(index) {
      if (index >= 0) excluded[index] = true;
    });

    cells.forEach(function(cell, index) {
      var text = String(cell || '').trim();
      if (!text || excluded[index]) return;
      if (textLooksLikeDate(text) || textLooksLikeClock(text) || textLooksLikeLocation(text) || textLooksLikeAction(text)) return;

      var header = headers[index] || '';
      var score = 0;
      if (textContainsAny(header, courseLabels)) score += 80;
      if (textContainsAny(header, ['\u8bfe', '\u79d1\u76ee', '\u6559\u5b66\u73ed', 'course', 'subject'])) score += 30;
      if (/[\u4e00-\u9fa5]/.test(text)) score += 18;
      if (/[A-Za-z]/.test(text)) score += 4;
      score += Math.min(text.length, 36);
      if (textLooksLikeCode(text)) score -= 20;
      if (index === 0) score -= 4;

      if (score > bestScore) {
        bestScore = score;
        bestIndex = index;
      }
    });

    return bestScore > 10 ? bestIndex : -1;
  }

  function inferLocationIndex(headers, cells, excludedIndexes) {
    var excluded = {};
    excludedIndexes.forEach(function(index) {
      if (index >= 0) excluded[index] = true;
    });

    var headerIndexMatch = headerIndex(headers, locationLabels);
    if (headerIndexMatch >= 0 && !excluded[headerIndexMatch]) return headerIndexMatch;

    return findFirstCellIndex(cells, function(cell, index) {
      return !excluded[index] && textLooksLikeLocation(cell);
    });
  }

  function combinedTimeFromCells(cells, dateIndex, timeIndex) {
    var datePart = dateIndex >= 0 ? (cells[dateIndex] || '') : '';
    var timePart = timeIndex >= 0 ? (cells[timeIndex] || '') : '';

    if (datePart && timePart && !textLooksLikeDate(timePart)) return datePart + ' ' + timePart;
    if (timePart) return timePart;
    if (datePart) return datePart;

    var dateCellIndex = findFirstCellIndex(cells, function(value) {
      return textLooksLikeDate(value);
    });
    var timeCellIndex = findFirstCellIndex(cells, function(value) {
      return textLooksLikeClock(value);
    });
    if (dateCellIndex >= 0 && timeCellIndex >= 0 && dateCellIndex !== timeCellIndex) {
      return cells[dateCellIndex] + ' ' + cells[timeCellIndex];
    }
    if (dateCellIndex >= 0) return cells[dateCellIndex];
    return '';
  }

  function cleanCourseText(text) {
    return String(text || '')
      .replace(/^\s*(\d+|No\.?|NO\.?)\s*/i, '')
      .replace(/^\s*[\uff1a:：\-]+\s*/, '')
      .replace(/\s+/g, ' ')
      .trim();
  }

  function courseCandidateScore(text) {
    var value = cleanCourseText(text);
    if (!value || value.length < 2) return -999;
    if (textLooksLikeDate(value) || textLooksLikeClock(value) || textLooksLikeLocation(value) || textLooksLikeCode(value)) return -999;
    if (textLooksLikeAction(value) || textLooksLikeHeaderNoise(value)) return -999;
    var score = 0;
    if (/[\u4e00-\u9fa5]/.test(value)) score += 28;
    if (/[A-Za-z]/.test(value)) score += 6;
    if (value.indexOf('\u8bfe') >= 0 || value.indexOf('\u79d1\u76ee') >= 0) score -= 4;
    score += Math.min(value.length, 36);
    return score;
  }

  function bestCourseFromText(text) {
    var value = String(text || '').replace(/\s+/g, ' ').trim();
    var dateMatch = value.match(dateRegex);
    var beforeDate = dateMatch && typeof dateMatch.index === 'number'
      ? value.slice(0, dateMatch.index)
      : value;
    var parts = beforeDate
      .split(/\s{2,}|[|｜,，;；]/)
      .map(cleanCourseText)
      .filter(Boolean);
    if (parts.length <= 1) {
      parts = beforeDate
        .split(/\s+/)
        .map(cleanCourseText)
        .filter(Boolean);
    }

    var best = '';
    var bestScore = -999;
    parts.forEach(function(part) {
      var score = courseCandidateScore(part);
      if (score > bestScore) {
        bestScore = score;
        best = part;
      }
    });
    return bestScore > 10 ? best : '';
  }

  function locationFromFreeText(text) {
    var parts = String(text || '')
      .replace(/\s+/g, ' ')
      .split(/\s{2,}|[|｜,，;；]/)
      .map(function(part) { return part.trim(); })
      .filter(Boolean);
    var match = parts.filter(textLooksLikeLocation).pop();
    if (match) return match;
    var value = String(text || '').replace(/\s+/g, ' ').trim();
    var markerMatch = value.match(/[\u4e00-\u9fa5A-Za-z0-9_\-]*?(\u6821\u533a|\u6559\u5ba4|\u6559\u5b66|\u697c|\u9986|\u5ba4)[\u4e00-\u9fa5A-Za-z0-9_\-\s]{0,40}/);
    return markerMatch ? markerMatch[0].trim() : '';
  }

  function rowFromFreeText(text) {
    var value = String(text || '').replace(/\s+/g, ' ').trim();
    if (!textLooksLikeDate(value) || !textLooksLikeClock(value)) return null;

    var dateMatch = value.match(dateRegex);
    var timeMatch = value.match(timeRegex);
    if (!dateMatch || !timeMatch) return null;

    var course = bestCourseFromText(value);
    if (!course) return null;

    return {
      course: course,
      code: '',
      time: dateMatch[0] + ' ' + timeMatch[0],
      location: locationFromFreeText(value),
      type: '',
      seat: ''
    };
  }

  function uniqueValues(values) {
    var seen = {};
    var result = [];
    values.forEach(function(value) {
      var text = String(value || '').replace(/\s+/g, ' ').trim();
      if (!text || seen[text]) return;
      seen[text] = true;
      result.push(text);
    });
    return result;
  }

  function candidateCellsFromElement(element) {
    var cells = [];
    var directChildren = Array.prototype.slice.call(element.children || []);
    directChildren.forEach(function(child) {
      var text = plainCellText(child);
      if (text) cells.push(text);
    });

    var nestedCells = Array.prototype.slice.call(element.querySelectorAll(
      '[role="cell"], td, .cell, .ant-table-cell, .vxe-cell, .vxe-cell--label, .ivu-table-cell, [class*="table-cell"], [class*="TableCell"]'
    )).map(cellText);
    if (nestedCells.length > cells.length) cells = nestedCells;

    var lines = visibleLines(element);
    if (cells.length < 2 && lines.length > cells.length) cells = lines;

    return uniqueValues(cells);
  }

  function firstMatching(root, selectors) {
    for (var i = 0; i < selectors.length; i += 1) {
      var match = root.querySelector(selectors[i]);
      if (match) return match;
    }
    return null;
  }

  function collectDocuments(debugLines) {
    var docs = [{ label: 'main', doc: document }];
    Array.prototype.slice.call(document.querySelectorAll('iframe,frame')).forEach(function(frame, index) {
      try {
        if (frame.contentDocument) {
          docs.push({ label: 'frame' + index, doc: frame.contentDocument });
        }
      } catch (error) {
        debugLines.push('frame' + index + ': inaccessible');
      }
    });
    return docs;
  }

  function activateExamListTab() {
    var clicked = false;
    collectDocuments([]).forEach(function(entry) {
      if (clicked) return;
      var doc = entry.doc;
      var candidates = Array.prototype.slice.call(doc.querySelectorAll(
        '[role="tab"], button, a, li, .el-tabs__item, .ant-tabs-tab, .van-tab, .ivu-tabs-tab, span, div'
      ));
      for (var i = 0; i < candidates.length; i += 1) {
        var item = candidates[i];
        var text = plainCellText(item);
        if (!text || text.length > 20) continue;
        if (text.indexOf('\u6392\u8003\u5217\u8868') >= 0) {
          try {
            item.click();
            clicked = true;
          } catch (error) {
          }
          break;
        }
      }
    });
    return clicked;
  }

  function collectAfterTabActivation() {
    var clicked = activateExamListTab();
    window.setTimeout(collect, clicked ? 900 : 120);
  }

  function collect() {
    var lines = [];
    var debugLines = [];
    var remaining = 160000;
    var rowCount = 0;

    function pushLine(text) {
      if (remaining <= 0) return;
      var value = String(text || '');
      if (value.length > remaining) value = value.slice(0, remaining);
      lines.push(value);
      remaining -= value.length;
    }

    function pushRow(row) {
      if (!row.course || !row.time || !dateRegex.test(row.time) || !timeRegex.test(row.time)) return false;
      if (rowCount === 0) pushLine('[[EXAM_ROWS]]');
      pushLine('\u8bfe\u7a0b\u540d\u79f0\uff1a' + row.course);
      if (row.code) pushLine('\u8bfe\u7a0b\u7f16\u53f7\uff1a' + row.code);
      pushLine('\u8003\u8bd5\u65f6\u95f4\uff1a' + row.time);
      if (row.location) pushLine('\u8003\u8bd5\u5730\u70b9\uff1a' + row.location);
      if (row.type) pushLine('\u8003\u8bd5\u7c7b\u578b\uff1a' + row.type);
      if (row.seat) pushLine('\u5ea7\u4f4d\u53f7\uff1a' + row.seat);
      pushLine('');
      rowCount += 1;
      return true;
    }

    function rowFromCells(headers, cells) {
      cells = cells.map(function(value) {
        return String(value || '').replace(/\s+/g, ' ').trim();
      });
      if (cells.filter(Boolean).length < 2) return null;

      var courseIndex = headerIndex(headers, courseLabels);
      var timeIndex = headerIndex(headers, examTimeLabels);
      var locationIndex = headerIndex(headers, locationLabels);
      if (courseIndex < 0 || timeIndex < 0) return null;

      var course = cells[courseIndex] || '';
      var time = cells[timeIndex] || '';
      var location = locationIndex >= 0 ? (cells[locationIndex] || '') : '';
      if (!course || !time) return null;
      if (textLooksLikeCode(course) || textLooksLikeDate(course) || textLooksLikeClock(course)) return null;

      return {
        course: course,
        code: '',
        time: time,
        location: location,
        type: '',
        seat: ''
      };
    }

    function collectElementTables(doc, scopeLabel) {
      var tables = Array.prototype.slice.call(doc.querySelectorAll('.el-table'));
      debugLines.push(scopeLabel + ': elTables=' + tables.length);
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
        if (!headerTable || !bodyTable) return;
        var headerRow = headerTable.querySelector('thead tr');
        if (!headerRow) return;
        var headers = Array.prototype.slice.call(headerRow.cells || []).map(cellText);
        var bodyRows = Array.prototype.slice.call(bodyTable.querySelectorAll('tbody tr'));
        debugLines.push(scopeLabel + ': elTable' + tableIndex + ' rows=' + bodyRows.length + ', headers=' + headers.join('|'));
        bodyRows.forEach(function(row) {
          var cells = Array.prototype.slice.call(row.cells || []).map(cellText);
          pushRow(rowFromCells(headers, cells) || {});
        });
      });
    }

    function collectNativeTables(doc, scopeLabel) {
      var tables = Array.prototype.slice.call(doc.querySelectorAll('table'));
      debugLines.push(scopeLabel + ': nativeTables=' + tables.length);
      tables.forEach(function(table, tableIndex) {
        var rows = Array.prototype.slice.call(table.querySelectorAll('tr'));
        if (!rows.length) return;
        var headerRow = table.querySelector('thead tr') || rows[0];
        var headers = Array.prototype.slice.call(headerRow.cells || []).map(cellText);
        rows.forEach(function(row, rowIndex) {
          if (row === headerRow && rowIndex === 0) return;
          var cells = Array.prototype.slice.call(row.cells || []).map(cellText);
          pushRow(rowFromCells(headers, cells) || {});
        });
        debugLines.push(scopeLabel + ': nativeTable' + tableIndex + ' rows=' + rows.length + ', headers=' + headers.join('|'));
      });
    }

    function collectTableLikeRows(doc, scopeLabel) {
      var rowSelectors = [
        '[role="row"]',
        '.el-table__row',
        '.ant-table-row',
        '.vxe-body--row',
        '.ivu-table-row',
        '[class*="table-row"]',
        '[class*="TableRow"]'
      ];
      var rows = Array.prototype.slice.call(doc.querySelectorAll(rowSelectors.join(',')));
      var seen = {};
      debugLines.push(scopeLabel + ': tableLikeRows=' + rows.length);
      rows.forEach(function(row) {
        var rawText = plainCellText(row);
        if (!rawText || seen[rawText]) return;
        seen[rawText] = true;

        var root = row.closest('.el-table,.ant-table,.vxe-table,.ivu-table,[role="table"],[role="grid"]') || doc;
        var headerNodes = Array.prototype.slice.call(root.querySelectorAll(
          '[role="columnheader"], thead th, .el-table__header th, .ant-table-thead th, .vxe-header--column, .ivu-table-header th'
        ));
        var headers = headerNodes.map(cellText).filter(Boolean);
        var cells = candidateCellsFromElement(row);
        pushRow(rowFromCells(headers, cells) || {});
      });
    }

    function collectHeuristicExamRows(doc, scopeLabel) {
      var candidates = [];
      var seen = {};
      Array.prototype.slice.call(doc.querySelectorAll('tr,[role="row"],.el-table__row,.ant-table-row,.vxe-body--row,.ivu-table-row,[class*="table-row"],[class*="TableRow"]')).forEach(function(row) {
        candidates.push(row);
      });

      Array.prototype.slice.call(doc.querySelectorAll('body *')).forEach(function(element) {
        var text = plainCellText(element);
        if (!text || text.length < 12 || text.length > 1200) return;
        if (!textLooksLikeDate(text) || !textLooksLikeClock(text)) return;

        var row = element.closest('tr,[role="row"],.el-table__row,.ant-table-row,.vxe-body--row,.ivu-table-row,[class*="table-row"],[class*="TableRow"]') || element;
        candidates.push(row);
      });

      var inspected = 0;
      candidates.forEach(function(candidate) {
        var text = plainCellText(candidate);
        if (!text || seen[text]) return;
        seen[text] = true;
        inspected += 1;
        pushRow(rowFromCells([], candidateCellsFromElement(candidate)) || {});
      });
      debugLines.push(scopeLabel + ': heuristicRows=' + inspected);
    }

    debugLines.push('url=' + location.href);
    debugLines.push('title=' + document.title);
    collectDocuments(debugLines).forEach(function(entry) {
      collectElementTables(entry.doc, entry.label);
      collectNativeTables(entry.doc, entry.label);
      collectTableLikeRows(entry.doc, entry.label);
      collectHeuristicExamRows(entry.doc, entry.label);
      var bodyText = visibleLines(entry.doc.body).join('\n');
      if (bodyText) {
        pushLine('[[BODY]]');
        pushLine(bodyText.slice(0, 60000));
      }
    });
    debugLines.push('examRows=' + rowCount);

    window.TongjiExamImportBridge.submit(['[[DEBUG]]'].concat(debugLines, lines).join('\n'));
  }

  var button = document.createElement('button');
  button.id = '__tongji_exam_import_button';
  button.type = 'button';
  button.textContent = $buttonLabel;
  button.onclick = collectAfterTabActivation;
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
