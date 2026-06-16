package com.example.todo

import com.example.todo.ui.theme.SoftRose
import java.time.LocalDate
import java.util.Locale

data class TongjiExam(
    val courseName: String,
    val examTimeText: String,
    val location: String = "",
    val examType: String = "",
    val seatNumber: String = "",
    val courseCode: String = ""
)

object TongjiExamImporter {
    fun parseExams(payload: String): List<TongjiExam> {
        val normalizedPayload = normalizePayload(payload.take(MaxPayloadLength))
        val rowsPayload = if (normalizedPayload.contains(ExamRowsMarker)) {
            markerSegment(
                payload = normalizedPayload,
                startMarker = ExamRowsMarker,
                endMarkers = listOf(BodyMarker, ScriptsMarker, DebugMarker)
            )
        } else {
            normalizedPayload
        }

        return parseExamBlocks(rowsPayload)
            .ifEmpty { parseExamListRows(markerSegment(normalizedPayload, BodyMarker, listOf(ScriptsMarker, DebugMarker))) }
            .ifEmpty { parseExamListRows(normalizedPayload) }
            .deduplicatedAndSorted()
    }

    fun toTasks(exams: List<TongjiExam>): List<Task> =
        exams.mapNotNull { exam ->
            val deadline = exam.examTimeText.toStartFlexibleDateTime() ?: return@mapNotNull null
            Task(
                id = stableTaskId(exam, deadline),
                title = "${exam.courseName}$ExamTitleSuffix",
                deadline = deadline,
                description = exam.toDetailEntries(),
                color = SoftRose,
                cost = 5
            )
        }

    private fun parseExamBlocks(payload: String): List<TongjiExam> =
        splitExamBlocks(payload)
            .mapNotNull(::parseExamBlock)

    private fun parseExamListRows(payload: String): List<TongjiExam> {
        if (payload.isBlank()) return emptyList()

        val lines = payload
            .lineSequence()
            .map { it.cleanCellText() }
            .filter { it.isNotBlank() }
            .filterNot { it in MarkerLines }
            .toList()
        val listStart = lines.indexOfFirst { it.contains(ExamListTitle) }
        val scopedLines = if (listStart >= 0) lines.drop(listStart + 1) else lines

        val columns = ExamListColumns.from(scopedLines.takeWhile { !it.isRowNumberLine() })
            ?: return emptyList()
        return splitNumberedRows(scopedLines)
            .mapNotNull { row -> parseNumberedExamRow(row, columns) }
    }

    private fun splitNumberedRows(lines: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var current = mutableListOf<String>()

        lines.forEach { line ->
            if (line.isRowNumberLine()) {
                if (current.isNotEmpty()) rows += current
                current = mutableListOf(line)
            } else if (current.isNotEmpty()) {
                current += line
            }
        }

        if (current.isNotEmpty()) rows += current
        return rows
    }

    private fun parseNumberedExamRow(row: List<String>, columns: ExamListColumns): TongjiExam? {
        val courseIndex = columns.courseIndex
        val timeStartIndex = columns.timeIndex
        val locationColumnIndex = columns.locationIndex
        if (timeStartIndex !in row.indices) return null

        val timeResult = collectExamTime(row, timeStartIndex) ?: return null
        val examTime = timeResult.text
        if (examTime.toStartFlexibleDateTime() == null) return null

        val courseName = row.getOrNull(courseIndex)
            ?.takeIf { it.looksLikeStrictCourseNameCell() }
            ?.cleanCourseName()
            ?: return null

        val location = if (locationColumnIndex != null && locationColumnIndex >= timeStartIndex) {
            val timeExtraLines = (timeResult.nextIndex - timeStartIndex - 1).coerceAtLeast(0)
            row.getOrNull(locationColumnIndex + timeExtraLines)
                ?.takeIf { it.looksLikeLocation() }
        } else {
            row.drop(timeResult.nextIndex).firstOrNull { it.looksLikeLocation() }
        }.orEmpty().cleanCellText()

        return TongjiExam(
            courseName = courseName,
            examTimeText = examTime,
            location = location
        )
    }

    private fun collectExamTime(row: List<String>, startIndex: Int): ParsedTimeText? {
        val parts = mutableListOf<String>()
        var cursor = startIndex
        while (cursor < row.size && parts.size < MaxExamTimeCellLines) {
            val line = row[cursor]
            if (cursor > startIndex && (line.looksLikeLocation() || line.isKnownNonTimeCell())) break
            parts += line

            val text = parts.joinToString(" ").cleanCellText()
            if (text.toStartFlexibleDateTime() != null) {
                val nextLine = row.getOrNull(cursor + 1)
                if (nextLine == null || nextLine.looksLikeLocation() || nextLine.isKnownNonTimeCell()) {
                    return ParsedTimeText(text = text, nextIndex = cursor + 1)
                }
            }
            cursor += 1
        }

        val text = parts.joinToString(" ").cleanCellText()
        return if (text.toStartFlexibleDateTime() != null) {
            ParsedTimeText(text = text, nextIndex = cursor)
        } else {
            null
        }
    }

    private fun splitExamBlocks(payload: String): List<List<String>> {
        val blocks = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        var hasCourseLine = false

        payload
            .lineSequence()
            .map { it.trim() }
            .filterNot { it in MarkerLines }
            .forEach { line ->
                if (line.isBlank()) {
                    if (current.isNotEmpty()) {
                        blocks += current
                        current = mutableListOf()
                        hasCourseLine = false
                    }
                    return@forEach
                }

                val isCourseLine = line.parseLabelValue()?.first?.matchesAnyLabel(CourseNameLabels) == true
                if (isCourseLine && hasCourseLine && current.isNotEmpty()) {
                    blocks += current
                    current = mutableListOf()
                    hasCourseLine = false
                }

                current += line
                hasCourseLine = hasCourseLine || isCourseLine
            }

        if (current.isNotEmpty()) blocks += current
        return blocks
    }

    private fun parseExamBlock(lines: List<String>): TongjiExam? {
        val entries = linkedMapOf<String, String>()
        lines.forEach { line ->
            val (label, value) = line.parseLabelValue() ?: return@forEach
            if (value.isNotBlank()) {
                entries[normalizeLabel(label)] = value.trim()
            }
        }

        val courseName = entries.firstValueFor(CourseNameLabels)?.cleanCellText() ?: return null
        val directTime = entries.firstValueFor(ExamTimeLabels)
        val datePart = entries.firstValueFor(ExamDateLabels)
        val timePart = entries.firstValueFor(TimeOnlyLabels) ?: directTime
        val examTime = when {
            datePart != null && timePart != null && !timePart.containsDate() -> "$datePart $timePart"
            directTime != null -> directTime
            datePart != null && timePart != null -> "$datePart $timePart"
            else -> null
        }
            ?: return null

        if (courseName.isBlank() || examTime.toStartFlexibleDateTime() == null) return null

        return TongjiExam(
            courseName = courseName,
            examTimeText = examTime.cleanCellText(),
            location = entries.firstValueFor(LocationLabels).orEmpty().cleanCellText(),
            examType = entries.firstValueFor(ExamTypeLabels).orEmpty().cleanCellText(),
            seatNumber = entries.firstValueFor(SeatLabels).orEmpty().cleanCellText(),
            courseCode = entries.firstValueFor(CourseCodeLabels).orEmpty().cleanCellText()
        )
    }

    private fun TongjiExam.toDetailEntries(): List<DetailEntry> =
        buildList {
            if (location.isNotBlank()) add(DetailEntry(ExamLocationHead, location))
            add(DetailEntry(ExamTimeHead, examTimeText))
            add(DetailEntry(SourceHead, TongjiExamSource))
            add(DetailEntry(TypeHead, TongjiExamType))
            if (examType.isNotBlank()) add(DetailEntry(ExamTypeHead, examType))
            if (seatNumber.isNotBlank()) add(DetailEntry(SeatHead, seatNumber))
        }

    private fun String.toStartFlexibleDateTime(): FlexibleDateTime? {
        val text = cleanCellText()
        val dateMatch = findDateIn(text) ?: return null
        val clockMatch = ClockPattern.find(text.substring(dateMatch.endIndex)) ?: return null
        val rawHour = clockMatch.groupValues[1].toIntOrNull() ?: return null
        val minute = clockMatch.groupValues[2].toIntOrNull() ?: return null
        val hour = adjustHourForPeriodMarker(text, rawHour)
        val date = runCatching {
            LocalDate.of(dateMatch.year, dateMatch.month, dateMatch.day)
        }.getOrNull() ?: return null

        if (hour !in 0..23 || minute !in 0..59) return null
        return FlexibleDateTime(
            year = date.year,
            month = date.monthValue,
            day = date.dayOfMonth,
            hour = hour,
            minute = minute
        )
    }

    private fun findDateIn(text: String): ParsedDate? {
        DateWithSeparatorPattern.find(text)?.let { match ->
            val year = match.groupValues[1].toIntOrNull() ?: return null
            val month = match.groupValues[2].toIntOrNull() ?: return null
            val day = match.groupValues[3].toIntOrNull() ?: return null
            if (!isValidExamDate(year, month, day)) return@let
            return ParsedDate(year = year, month = month, day = day, endIndex = match.range.last + 1)
        }

        CompactDatePattern.find(text)?.let { match ->
            val value = match.value
            val year = value.substring(0, 4).toIntOrNull() ?: return null
            val month = value.substring(4, 6).toIntOrNull() ?: return null
            val day = value.substring(6, 8).toIntOrNull() ?: return null
            if (!isValidExamDate(year, month, day)) return@let
            return ParsedDate(year = year, month = month, day = day, endIndex = match.range.last + 1)
        }

        return null
    }

    private fun isValidExamDate(year: Int, month: Int, day: Int): Boolean =
        year in 2000..2100 && runCatching { LocalDate.of(year, month, day) }.isSuccess

    private fun adjustHourForPeriodMarker(text: String, hour: Int): Int =
        when {
            hour !in 1..11 -> hour
            AfternoonMarkers.any { text.contains(it) } -> hour + 12
            else -> hour
        }

    private fun List<TongjiExam>.deduplicatedAndSorted(): List<TongjiExam> =
        distinctBy {
            listOf(it.courseName, it.examTimeText, it.location, it.seatNumber).joinToString("|")
        }
            .sortedWith(compareBy({ it.examTimeText }, { it.courseName }, { it.location }))

    private fun stableTaskId(exam: TongjiExam, deadline: FlexibleDateTime): String {
        val seed = listOf(
            exam.courseName,
            deadline.year,
            deadline.month,
            deadline.day,
            deadline.hour,
            deadline.minute,
            exam.location
        ).joinToString("|")
        return TongjiExamTaskIdPrefix + Integer.toUnsignedString(seed.hashCode(), 36)
    }

    private fun String.parseLabelValue(): Pair<String, String>? {
        val colonIndex = indexOfAny(charArrayOf(':', '\uff1a'))
        if (colonIndex <= 0) return null
        val label = substring(0, colonIndex).trim()
        val value = substring(colonIndex + 1).trim()
        if (label.isBlank() || value.isBlank()) return null
        return label to value
    }

    private fun String.matchesAnyLabel(labels: List<String>): Boolean {
        val label = normalizeLabel(this)
        return labels.any { candidate ->
            val normalizedCandidate = normalizeLabel(candidate)
            label == normalizedCandidate || label.contains(normalizedCandidate)
        }
    }

    private fun Map<String, String>.firstValueFor(labels: List<String>): String? {
        val normalizedLabels = labels.map(::normalizeLabel)
        normalizedLabels.firstNotNullOfOrNull { label -> this[label] }?.let { return it }
        return entries.firstOrNull { (key, _) ->
            normalizedLabels.any { label -> key.contains(label) || label.contains(key) }
        }?.value
    }

    private fun normalizeLabel(label: String): String =
        label
            .lowercase(Locale.ROOT)
            .replace(Regex("""[\s:_\-/\\()（）\[\]【】]+"""), "")
            .trim()

    private fun normalizePayload(payload: String): String =
        payload
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("""(?i)<br\s*/?>"""), "\n")
            .replace(Regex("""(?i)</p>"""), "\n")
            .replace(Regex("""(?is)<script\b.*?</script>"""), " ")
            .replace(Regex("""(?is)<style\b.*?</style>"""), " ")
            .replace(Regex("""(?is)<[^>]+>"""), " ")
            .replace(Regex("""[ \t]+"""), " ")
            .lines()
            .joinToString("\n") { it.trim() }

    private fun String.cleanCellText(): String =
        replace(Regex("""\s+"""), " ").trim()

    private fun String.containsDate(): Boolean =
        findDateIn(this) != null

    private fun String.isRowNumberLine(): Boolean =
        matches(Regex("""\d{1,3}"""))

    private fun String.looksLikeCourseCode(): Boolean =
        matches(Regex("""[A-Za-z]{2,}\d[A-Za-z0-9_.\-]{3,}""")) ||
            matches(Regex("""\d{6,}"""))

    private fun String.looksLikeLocation(): Boolean =
        LocationKeywords.any { contains(it, ignoreCase = true) }

    private fun String.isKnownNonTimeCell(): Boolean =
        this in KnownStatusCells || contains(ActionCellText)

    private fun String.looksLikeCourseNameCell(): Boolean =
        isNotBlank() &&
            !isRowNumberLine() &&
            !looksLikeCourseCode() &&
            !containsDate() &&
            !looksLikeLocation() &&
            !isKnownNonTimeCell() &&
            !HeaderNoise.any { contains(it, ignoreCase = true) }

    private fun String.looksLikeStrictCourseNameCell(): Boolean =
        isNotBlank() &&
            !isRowNumberLine() &&
            !looksLikeCourseCode() &&
            !containsDate() &&
            !looksLikeLocation() &&
            !HeaderNoise.any { contains(it, ignoreCase = true) } &&
            !contains(ActionCellText)

    private fun String.cleanCourseName(): String =
        replace(Regex("""^\d+\s*"""), "")
            .cleanCellText()

    private data class ParsedDate(
        val year: Int,
        val month: Int,
        val day: Int,
        val endIndex: Int
    )

    private data class ParsedTimeText(
        val text: String,
        val nextIndex: Int
    )

    private data class ExamListColumns(
        val courseIndex: Int,
        val timeIndex: Int,
        val locationIndex: Int?
    ) {
        companion object {
            fun from(headers: List<String>): ExamListColumns? {
                val courseIndex = headers.indexOfFirstLabel(CourseNameLabels)
                val timeIndex = headers.indexOfFirstLabel(ExamTimeLabels)
                val locationIndex = headers.indexOfFirstLabel(LocationLabels).takeIf { it >= 0 }
                if (courseIndex < 0 || timeIndex < 0) return null
                return ExamListColumns(
                    courseIndex = courseIndex,
                    timeIndex = timeIndex,
                    locationIndex = locationIndex
                )
            }

            private fun List<String>.indexOfFirstLabel(labels: List<String>): Int {
                val normalizedLabels = labels.map(::normalizeLabel)
                return indexOfFirst { header ->
                    val normalizedHeader = normalizeLabel(header)
                    normalizedLabels.any { normalizedHeader == it || normalizedHeader.contains(it) }
                }
            }
        }
    }

    private fun markerSegment(
        payload: String,
        startMarker: String,
        endMarkers: List<String>
    ): String {
        val markerStart = payload.indexOf(startMarker)
        if (markerStart < 0) return ""
        val contentStart = markerStart + startMarker.length
        val markerEnd = endMarkers
            .asSequence()
            .mapNotNull { marker -> payload.indexOf(marker, contentStart).takeIf { it >= contentStart } }
            .minOrNull()
            ?: payload.length
        return payload.substring(contentStart, markerEnd).trim()
    }

    private const val MaxPayloadLength = 180_000
    private const val ExamRowsMarker = "[[EXAM_ROWS]]"
    private const val BodyMarker = "[[BODY]]"
    private const val ScriptsMarker = "[[SCRIPTS]]"
    private const val DebugMarker = "[[DEBUG]]"
    private val MarkerLines = setOf(ExamRowsMarker, BodyMarker, ScriptsMarker, DebugMarker)
    private const val ExamListTitle = "\u6392\u8003\u5217\u8868"
    private const val ActionCellText = "\u7533\u8bf7\u7f13\u8003"
    private const val MaxExamTimeCellLines = 4

    private const val ExamTitleSuffix = "\u8003\u8bd5"
    private const val ExamLocationHead = "\u8003\u8bd5\u5730\u70b9"
    private const val ExamTimeHead = "\u8003\u8bd5\u65f6\u95f4"
    private const val SourceHead = "\u6765\u6e90"
    private const val TypeHead = "\u7c7b\u578b"
    private const val ExamTypeHead = "\u8003\u8bd5\u7c7b\u578b"
    private const val SeatHead = "\u5ea7\u4f4d\u53f7"

    private val CourseNameLabels = listOf(
        "\u8bfe\u7a0b\u540d\u79f0",
        "\u8bfe\u7a0b\u540d",
        "\u8003\u8bd5\u79d1\u76ee",
        "\u79d1\u76ee\u540d\u79f0",
        "\u79d1\u76ee",
        "\u6559\u5b66\u73ed\u540d\u79f0",
        "\u6559\u5b66\u73ed",
        "\u8bfe\u7a0b\u4fe1\u606f",
        "course name",
        "subject"
    )
    private val CourseCodeLabels = listOf(
        "\u8bfe\u7a0b\u7f16\u53f7",
        "\u8bfe\u7a0b\u4ee3\u7801",
        "\u8bfe\u7a0b\u53f7",
        "\u8bfe\u7a0b\u5e8f\u53f7",
        "\u8bfe\u7a0b\u4ee3\u53f7",
        "course code",
        "course id"
    )
    private val ExamTimeLabels = listOf(
        "\u8003\u8bd5\u65f6\u95f4",
        "\u8003\u8bd5\u65f6\u6bb5",
        "\u8003\u8bd5\u8d77\u6b62\u65f6\u95f4",
        "\u5f00\u8003\u65f6\u95f4",
        "\u5f00\u59cb\u65f6\u95f4",
        "\u65f6\u95f4",
        "\u65f6\u6bb5",
        "exam time",
        "start time",
        "time",
        "period"
    )
    private val ExamDateLabels = listOf(
        "\u8003\u8bd5\u65e5\u671f",
        "\u8003\u8bd5\u65e5\u671f\u65f6\u95f4",
        "\u65e5\u671f",
        "exam date",
        "date"
    )
    private val TimeOnlyLabels = listOf(
        "\u8003\u8bd5\u65f6\u6bb5",
        "\u65f6\u6bb5",
        "\u5f00\u59cb\u65f6\u95f4",
        "period",
        "start time"
    )
    private val LocationLabels = listOf(
        "\u8003\u8bd5\u5730\u70b9",
        "\u8003\u573a\u5730\u70b9",
        "\u8003\u573a\u540d\u79f0",
        "\u8003\u573a",
        "\u5730\u70b9",
        "\u5730\u70b9\u540d\u79f0",
        "\u6559\u5ba4",
        "\u6821\u533a",
        "\u6821\u533a\u6559\u5ba4",
        "exam room",
        "room",
        "location",
        "classroom"
    )
    private val ExamTypeLabels = listOf(
        "\u8003\u8bd5\u7c7b\u578b",
        "\u8003\u8bd5\u6027\u8d28",
        "\u8003\u8bd5\u65b9\u5f0f",
        "\u7c7b\u578b",
        "\u6027\u8d28",
        "exam type",
        "type"
    )
    private val SeatLabels = listOf(
        "\u5ea7\u4f4d\u53f7",
        "\u5ea7\u4f4d",
        "\u5ea7\u53f7",
        "\u5ea7\u4f4d\u4fe1\u606f",
        "seat number",
        "seat"
    )
    private val AfternoonMarkers = listOf(
        "\u4e0b\u5348",
        "\u665a\u4e0a",
        "\u591c\u95f4",
        "\u4e2d\u5348",
        "pm",
        "p.m."
    )
    private val DateWithSeparatorPattern =
        Regex("""(?<!\d)(\d{4})\s*(?:年|[-/.])\s*(\d{1,2})\s*(?:月|[-/.])\s*(\d{1,2})\s*(?:日)?""")
    private val CompactDatePattern = Regex("""(?<!\d)\d{8}(?!\d)""")
    private val ClockPattern = Regex("""(\d{1,2})\s*[:：]\s*(\d{1,2})""")
    private val LocationKeywords = listOf(
        "\u6821\u533a",
        "\u6559\u5ba4",
        "\u6559\u5b66",
        "\u697c",
        "\u9986",
        "\u5ba4",
        "campus",
        "room",
        "classroom",
        "building"
    )
    private val HeaderNoise = listOf(
        ExamListTitle,
        "\u5e8f\u53f7",
        "\u65b0\u8bfe\u7a0b\u5e8f\u53f7",
        "\u8bfe\u7a0b\u5e8f\u53f7",
        "\u8bfe\u7a0b\u540d\u79f0",
        "\u8003\u8bd5\u65f6\u95f4",
        "\u8003\u8bd5\u5730\u70b9",
        "\u8003\u8bd5\u60c5\u51b5",
        "\u8003\u8bd5\u5907\u6ce8",
        "\u5ba1\u6838\u8bf4\u660e",
        "\u5ba1\u6838\u72b6\u6001",
        "\u64cd\u4f5c"
    )
    private val KnownStatusCells = setOf(
        "\u6b63\u5e38",
        "\u5f85\u5ba1\u6838",
        "\u5df2\u5ba1\u6838",
        "\u5ba1\u6838\u901a\u8fc7",
        "\u4e0d\u901a\u8fc7"
    )
}
