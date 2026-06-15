package com.example.todo

import androidx.compose.ui.graphics.Color
import com.example.todo.ui.theme.SoftBlue
import com.example.todo.ui.theme.SoftLavender
import com.example.todo.ui.theme.SoftPeach
import com.example.todo.ui.theme.SoftRose
import com.example.todo.ui.theme.SoftSage
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class TongjiCourse(
    val name: String,
    val courseCode: String = "",
    val teacher: String = "",
    val room: String = "",
    val weekText: String = "",
    val weeks: List<Int> = emptyList(),
    val weekday: Int,
    val startSection: Int,
    val endSection: Int
)

object TongjiTimetableImporter {
    private const val LOG_TAG = "TongjiImport"

    private fun log(msg: String) {
        try { android.util.Log.d(LOG_TAG, msg) } catch (_: Throwable) { }
    }
    private val softPalette = listOf(SoftBlue, SoftSage, SoftRose, SoftLavender, SoftPeach)

    fun parseCourses(payload: String): List<TongjiCourse> {
        val normalizedPayload = normalizePayload(payload.take(MaxPayloadLength))
        val hasSelectedCourseMarker = normalizedPayload.contains(SelectedCoursesMarker)
        val selectedCourses = runCatching { parseSelectedCourseBlocks(normalizedPayload) }
            .onFailure { log("Selected course parsing failed: ${it.message}") }
            .getOrDefault(emptyList())
        val timetableGridCourses = runCatching { parseTimetableGridBlocks(normalizedPayload) }
            .onFailure { log("Timetable grid parsing failed: ${it.message}") }
            .getOrDefault(emptyList())
        if (hasSelectedCourseMarker) {
            return selectedCourses.mergedCourseWeekSegments().deduplicatedAndSorted()
        }
        if (timetableGridCourses.isNotEmpty()) {
            return timetableGridCourses.deduplicatedAndSorted()
        }

        val courses = mutableListOf<TongjiCourse>()

        extractPlainTextTables(normalizedPayload).forEach { table ->
            courses += runCatching { parseGridTable(table) }.getOrDefault(emptyList())
        }

        extractHtmlTables(normalizedPayload).forEach { table ->
            courses += runCatching { parseGridTable(table) }.getOrDefault(emptyList())
        }

        val bodyPayload = if (normalizedPayload.contains(BodyMarker)) {
            markerSegment(normalizedPayload, BodyMarker, ScriptsMarker)
        } else {
            normalizedPayload
        }
        val scriptsPayload = markerSegment(normalizedPayload, ScriptsMarker, null)
        courses += runCatching { parseLooseBlocks(bodyPayload) }.getOrDefault(emptyList())
        courses += runCatching { parseLooseBlocks(scriptsPayload) }.getOrDefault(emptyList())

        return courses.deduplicatedAndSorted()
    }

    private fun List<TongjiCourse>.deduplicatedAndSorted(): List<TongjiCourse> =
        this
            .distinctBy {
                listOf(
                    it.name,
                    it.courseCode,
                    it.teacher,
                    it.room,
                    it.weekday,
                    it.startSection,
                    it.endSection,
                    it.weekText
                ).joinToString("|")
            }
            .sortedWith(compareBy({ it.weekday }, { it.startSection }, { it.name }))

    private fun List<TongjiCourse>.mergedCourseWeekSegments(): List<TongjiCourse> =
        groupBy { course ->
            listOf(
                course.name,
                course.courseCode,
                course.weekday,
                course.startSection,
                course.endSection
            ).joinToString("|")
        }
            .values
            .flatMap { group ->
                val sortedGroup = group.sortedWith(compareBy({ it.weekText }, { it.teacher }, { it.room }))
                val base = sortedGroup.firstOrNull() ?: return@flatMap emptyList<TongjiCourse>()
                val mergedWeeks = if (sortedGroup.any { it.weeks.isEmpty() }) {
                    emptyList()
                } else {
                    sortedGroup.flatMap { it.weeks }.distinct().sorted()
                }
                val mergedWeekText = mergedWeeks
                    .takeIf { it.isNotEmpty() }
                    ?.let(::formatWeekRanges)
                    .orEmpty()

                listOf(
                    base.copy(
                        teacher = sortedGroup.map { it.teacher }.mergedTextParts(),
                        room = sortedGroup.map { it.room }.mergedTextParts(),
                        weekText = mergedWeekText,
                        weeks = mergedWeeks
                    )
                )
            }

    private fun List<String>.mergedTextParts(): String =
        flatMap { value ->
            value
                .split(Regex("""\s*/\s*|\s*、\s*"""))
                .map { it.trim() }
        }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" / ")

    private fun formatWeekRanges(weeks: List<Int>): String =
        weeks
            .distinct()
            .sorted()
            .fold(mutableListOf<IntRange>()) { ranges, week ->
                val last = ranges.lastOrNull()
                if (last != null && week == last.last + 1) {
                    ranges[ranges.lastIndex] = last.first..week
                } else {
                    ranges += week..week
                }
                ranges
            }
            .joinToString(",") { range ->
                if (range.first == range.last) {
                    "${range.first}周"
                } else {
                    "${range.first}-${range.last}周"
                }
            }

    fun toSchedules(
        courses: List<TongjiCourse>,
        semesterStartDate: LocalDate
    ): List<Schedule> =
        courses.flatMap { course ->
            val classWeeks = course.weeks.ifEmpty { DefaultWeeks }
            classWeeks.mapNotNull { week ->
                val classDate = runCatching {
                    semesterStartDate.plusDays(((week - 1) * 7 + (course.weekday - 1)).toLong())
                }.getOrNull() ?: return@mapNotNull null
                val start = SectionTimes[course.startSection]?.first ?: return@mapNotNull null
                val end = SectionTimes[course.endSection]?.second ?: return@mapNotNull null

                Schedule(
                    id = stableScheduleId(course, week, classDate),
                    title = course.name,
                    startTime = FlexibleDateTime(
                        year = classDate.year,
                        month = classDate.monthValue,
                        day = classDate.dayOfMonth,
                        hour = start.first,
                        minute = start.second
                    ),
                    endTime = FlexibleDateTime(
                        year = classDate.year,
                        month = classDate.monthValue,
                        day = classDate.dayOfMonth,
                        hour = end.first,
                        minute = end.second
                    ),
                    description = course.toDetailEntries(week, start, end),
                    color = colorForCourse(course.name)
                )
            }
        }

    fun classClockRangeText(course: TongjiCourse): String? =
        sectionClockRangeText(course.startSection, course.endSection)

    fun sectionClockRangeText(startSection: Int, endSection: Int): String? {
        val start = SectionTimes[startSection]?.first ?: return null
        val end = SectionTimes[endSection]?.second ?: return null
        return "${start.toClockText()} - ${end.toClockText()}"
    }

    fun defaultSemesterStartDate(today: LocalDate = LocalDate.now()): LocalDate {
        val previousMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val nextMonday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
        return if (today.dayOfWeek.value <= 4) previousMonday else nextMonday
    }

    // ── private parsing ──────────────────────────────────────────

    private fun parseSelectedCourseBlocks(payload: String): List<TongjiCourse> {
        val selectedPayload = markerSegment(
            payload = payload,
            startMarker = SelectedCoursesMarker,
            endMarkers = listOf(TimetableGridMarker, TableMarker, BodyMarker, ScriptsMarker)
        )
            .takeIf { it.isNotBlank() }
            ?: return emptyList()

        return parseSelectedCourseListBlocks(selectedPayload).ifEmpty {
            parseLooseBlocks(selectedPayload)
        }
    }

    private fun parseSelectedCourseListBlocks(payload: String): List<TongjiCourse> =
        splitSelectedCourseBlocks(payload)
            .flatMap { block -> safeParseSelectedCourseListBlock(block) }

    private fun splitSelectedCourseBlocks(payload: String): List<String> {
        val normalized = normalizePayload(payload)
            .replace(CellBreakMarker, "\n")
            .replace(JsonNewLineRegex, "\n")
            .replace(Regex("""(?m)^\s*课程名称\s*[:：=]"""), "\n课程名称：")

        return normalized
            .split(Regex("""\n\s*\n+"""))
            .flatMap { chunk ->
                chunk
                    .split(Regex("""(?=课程名称\s*[:：=])"""))
                    .map { it.trim() }
            }
            .filter { it.contains("课程名称") && it.contains("上课时间") }
    }

    private fun safeParseSelectedCourseListBlock(block: String): List<TongjiCourse> =
        runCatching { parseSelectedCourseListBlock(block.take(MaxCourseBlockLength)) }
            .getOrDefault(emptyList())

    private fun parseSelectedCourseListBlock(block: String): List<TongjiCourse> {
        val clean = normalizeBlock(block)
        val name = extractLineLabel(clean, CourseNameLabels)
            ?.let(::sanitizeCourseName)
            ?.takeUnless(::isRejectedCourseName)
            ?: return emptyList()
        val timeText = extractLineLabel(clean, ClassTimeLabels) ?: return emptyList()
        val timeSlots = parseSelectedCourseTimeSlots(timeText)
        if (timeSlots.isEmpty()) return emptyList()

        val courseCode = extractLineLabel(clean, CourseCodeLabels).orEmpty()
        val teacher = extractLineLabel(clean, TeacherLabels).orEmpty()
        val room = extractLineLabel(clean, RoomLabels).orEmpty()

        return timeSlots.map { slot ->
            TongjiCourse(
                name = name,
                courseCode = courseCode,
                teacher = teacher,
                room = room,
                weekText = slot.weekText,
                weeks = parseWeekNumbers(slot.weekText),
                weekday = slot.weekday,
                startSection = slot.sections.first,
                endSection = slot.sections.last
            )
        }
    }

    private fun parseSelectedCourseTimeSlots(timeText: String): List<CourseTimeSlot> =
        SelectedCourseTimeSlotRegex.findAll(timeText)
            .mapNotNull { match ->
                val weekday = parseWeekday(match.groupValues[1]) ?: return@mapNotNull null
                val sections = toSectionRange(
                    startRaw = match.groupValues[2],
                    endRaw = match.groupValues.getOrNull(3)
                ) ?: return@mapNotNull null
                val weekText = match.groupValues
                    .getOrNull(4)
                    .orEmpty()
                    .toWeekText()

                CourseTimeSlot(
                    weekday = weekday,
                    sections = sections,
                    weekText = weekText
                )
            }
            .distinctBy {
                listOf(it.weekday, it.sections.first, it.sections.last, it.weekText)
                    .joinToString("|")
            }
            .toList()

    private fun parseTimetableGridBlocks(payload: String): List<TongjiCourse> {
        val gridPayload = markerSegment(
            payload = payload,
            startMarker = TimetableGridMarker,
            endMarkers = listOf(TableMarker, BodyMarker, ScriptsMarker)
        )
            .takeIf { it.isNotBlank() }
            ?: return emptyList()

        return parseLooseBlocks(gridPayload)
    }

    private fun parseGridTable(rows: List<List<String>>): List<TongjiCourse> {
        if (rows.isEmpty()) return emptyList()

        val headerIndex = rows.indexOfFirst { row ->
            row.mapNotNull(::parseWeekday).distinct().size >= 2
        }

        if (headerIndex < 0) {
            return rows.flatMap { row -> safeParseCourseBlock(row.joinToString("\n")) }
        }

        val header = rows[headerIndex]
        val weekdayByColumn = header.mapIndexedNotNull { index, cell ->
            parseWeekday(cell)?.let { weekday -> index to weekday }
        }.toMap()

        log("GridTable: header=${header.size}cells  weekdayMap=$weekdayByColumn")
        log("  Header[0..${minOf(header.size-1, 8)}]: ${header.take(9).joinToString(" | ")}")

        return rows
            .drop(headerIndex + 1)
            .flatMap { row ->
                val sectionHint = row.firstOrNull()?.let(::parseSectionRange)
                    ?: row.take(2).firstNotNullOfOrNull(::parseSectionRange)

                weekdayByColumn.flatMap { (columnIndex, weekday) ->
                    val rawCell = row.getOrNull(columnIndex).orEmpty()
                    splitCourseBlocks(rawCell).flatMap { block ->
                        safeParseCourseBlock(
                            block = block,
                            weekdayHint = weekday,
                            sectionHint = sectionHint
                        )
                    }
                }
            }
    }

    private fun parseLooseBlocks(payload: String): List<TongjiCourse> {
        val text = stripHtml(payload.take(MaxLoosePayloadLength))
            .replace(JsonNewLineRegex, "\n")
            .replace(Regex("""(?i)(?=["']?(?:courseName|course_name|课程名称|课程名|课程)["']?\s*[:：=])"""), "\n\n")
            .replace(Regex("""(?<=})\s*,\s*(?=\{)"""), "\n\n")

        return splitCourseBlocks(text)
            .asSequence()
            .take(MaxLooseBlocks)
            .flatMap { safeParseCourseBlock(it).asSequence() }
            .toList()
    }

    private fun safeParseCourseBlock(
        block: String,
        weekdayHint: Int? = null,
        sectionHint: IntRange? = null
    ): List<TongjiCourse> =
        runCatching {
            parseCourseBlock(
                block = block.take(MaxCourseBlockLength),
                weekdayHint = weekdayHint,
                sectionHint = sectionHint
            )
        }.getOrDefault(emptyList())

    private fun parseCourseBlock(
        block: String,
        weekdayHint: Int? = null,
        sectionHint: IntRange? = null
    ): List<TongjiCourse> {
        val clean = normalizeBlock(block)
        if (clean.isBlank()) return emptyList()

        val structuredInfo = extractStructuredCourseInfo(clean)
        val name = structuredInfo?.name ?: extractCourseName(clean) ?: return emptyList()
        if (isRejectedCourseName(name)) return emptyList()

        val commonWeekText = structuredInfo?.weekText ?: extractWeekText(clean)
        val timeSlots = parseInlineTimeSlots(clean).ifEmpty {
            val weekday = weekdayHint ?: parseWeekdayFromBlock(clean) ?: return emptyList()
            val sections = parseSectionRangeFromBlock(clean) ?: sectionHint ?: return emptyList()
            listOf(CourseTimeSlot(weekday = weekday, sections = sections, weekText = commonWeekText))
        }
        val courseCode = extractLabel(clean, CourseCodeLabels) ?: structuredInfo?.courseCode.orEmpty()
        val teacher = extractLabel(clean, TeacherLabels) ?: structuredInfo?.teacher.orEmpty()
        val room = extractLabel(clean, RoomLabels) ?: structuredInfo?.room.orEmpty()

        return timeSlots.map { slot ->
            val weekText = slot.weekText.ifBlank { commonWeekText }
            TongjiCourse(
                name = name,
                courseCode = courseCode,
                teacher = teacher,
                room = room,
                weekText = weekText,
                weeks = parseWeekNumbers(weekText),
                weekday = slot.weekday,
                startSection = slot.sections.first,
                endSection = slot.sections.last
            )
        }
    }

    private data class StructuredCourseInfo(
        val teacher: String = "",
        val name: String = "",
        val courseCode: String = "",
        val weekText: String = "",
        val room: String = ""
    )

    private data class CourseTimeSlot(
        val weekday: Int,
        val sections: IntRange,
        val weekText: String = ""
    )

    private data class WeekdayMatch(
        val index: Int,
        val length: Int,
        val weekday: Int
    )

    private fun parseInlineTimeSlots(text: String): List<CourseTimeSlot> =
        InlineTimeSlotRegex.findAll(text)
            .mapNotNull { match ->
                val weekday = parseWeekday(match.groupValues[1]) ?: return@mapNotNull null
                val sections = toSectionRange(
                    startRaw = match.groupValues[2],
                    endRaw = match.groupValues.getOrNull(3)
                ) ?: return@mapNotNull null
                val weekText = listOf(
                    match.groupValues.getOrNull(4).orEmpty(),
                    match.groupValues.getOrNull(5).orEmpty()
                ).firstOrNull { it.isNotBlank() }
                    ?.toWeekText()
                    .orEmpty()

                CourseTimeSlot(
                    weekday = weekday,
                    sections = sections,
                    weekText = weekText
                )
            }
            .distinctBy {
                listOf(it.weekday, it.sections.first, it.sections.last, it.weekText)
                    .joinToString("|")
            }
            .toList()

    private fun extractPlainTextTables(payload: String): List<List<List<String>>> {
        val tables = mutableListOf<MutableList<List<String>>>()
        var current: MutableList<List<String>>? = null

        payload.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd()
            when (line.trim()) {
                TableMarker -> {
                    current = mutableListOf<List<String>>().also { tables += it }
                }
                BodyMarker, ScriptsMarker -> {
                    current = null
                }
                else -> {
                    val table = current
                    if (table != null && line.isNotBlank()) {
                        table += line
                            .split('\t')
                            .map { it.replace(CellBreakMarker, "\n").trim() }
                    }
                }
            }
        }

        return tables.filter { it.isNotEmpty() }
    }

    private fun extractHtmlTables(payload: String): List<List<List<String>>> {
        val tableRegex = Regex("""(?is)<table\b.*?</table>""")
        val rowRegex = Regex("""(?is)<tr\b.*?</tr>""")
        val cellRegex = Regex("""(?is)<t[dh]\b.*?</t[dh]>""")

        return tableRegex.findAll(payload).mapNotNull { tableMatch ->
            val rows = rowRegex.findAll(tableMatch.value)
                .map { rowMatch ->
                    cellRegex.findAll(rowMatch.value)
                        .map { cellMatch -> stripHtml(cellMatch.value).trim() }
                        .toList()
                }
                .filter { it.isNotEmpty() }
                .toList()

            rows.takeIf { it.isNotEmpty() }
        }.toList()
    }

    private fun splitCourseBlocks(text: String): List<String> {
        val normalized = normalizePayload(text)
            .replace(CellBreakMarker, "\n")
            .replace(Regex("""(?i)<br\s*/?>"""), "\n")
            .replace(Regex("""(?i)(?=(?:课程名称|课程名|courseName|course_name)\s*[:：=])"""), "\n\n")

        return normalized
            .split(Regex("""\n\s*\n+"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun extractStructuredCourseInfo(text: String): StructuredCourseInfo? {
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
        val candidates = lines + lines.joinToString(" ")
        val compactCourseRegex = Regex(
            """([^\s()（）\[\]［］【】]{1,32})\s*[\(（]([^)）]{2,24})[\)）]\s+(.{2,80}?)\s*[\(（]([A-Za-z0-9\-]{4,})[\)）]\s*[\[［【]([^\]］】]+)[\]］】]\s*([^\n\r]*)"""
        )

        return candidates.asSequence()
            .mapNotNull { candidate -> compactCourseRegex.find(candidate) }
            .mapNotNull { match ->
                val teacher = match.groupValues.getOrNull(1).orEmpty().trim()
                val courseName = sanitizeCourseName(match.groupValues.getOrNull(3).orEmpty())
                val courseCode = match.groupValues.getOrNull(4).orEmpty().trim()
                val rawWeeks = match.groupValues.getOrNull(5).orEmpty().trim()
                val room = match.groupValues.getOrNull(6).orEmpty().trim()
                if (courseName.isBlank() || isRejectedCourseName(courseName)) {
                    null
                } else {
                    StructuredCourseInfo(
                        teacher = teacher,
                        name = courseName,
                        courseCode = courseCode,
                        weekText = rawWeeks.toWeekText(),
                        room = room
                    )
                }
            }
            .firstOrNull()
    }

    private fun extractCourseName(text: String): String? {
        extractLabel(text, CourseNameLabels)?.let { return sanitizeCourseName(it) }

        return text
            .lineSequence()
            .map { it.trim().trim('"', '\'', ',', ';', '；') }
            .map { line ->
                line.replace(Regex("""(?i)(教师|老师|任课教师|teacher|instructor|教室|地点|上课地点|room|location|周次|weeks?|节次|sections?|星期|weekday|周[一二三四五六日天]).*$"""), "")
                    .trim()
            }
            .firstOrNull { line ->
                line.isNotBlank() &&
                    !containsAnyLabel(line, MetadataLabels) &&
                    !line.matches(Regex("""^[\d\s:：.,，;；\-~至到第章节周单双]+$""")) &&
                    line.length <= MaxCourseNameLength
            }
            ?.let(::sanitizeCourseName)
    }

    private fun sanitizeCourseName(raw: String): String =
        raw
            .replace(Regex("""^[\s"'：:=]+|[\s"',，;；。]+$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun isRejectedCourseName(name: String): Boolean {
        val normalized = name.lowercase()
        return name.isBlank() ||
            name.length > MaxCourseNameLength ||
            name.matches(CourseCodeOnlyRegex) ||
            MetadataLabels.any { label -> name.contains(label, ignoreCase = true) } ||
            normalized in setOf("null", "undefined", "课程表", "课表", "schedule", "timetable")
    }

    private fun String.toWeekText(): String {
        val clean = trim()
            .trim('[', ']', '［', '］', '【', '】', '(', ')', '（', '）')
            .replace(Regex("""\s+"""), "")
        if (clean.isBlank()) return ""
        return if (clean.contains("周") || clean.contains("week", ignoreCase = true)) clean else "${clean}周"
    }

    private fun extractLabel(text: String, labels: List<String>): String? {
        val labelPattern = labels.joinToString("|") { Regex.escape(it) }
        val regex = Regex(
            pattern = """(?is)["']?(?:$labelPattern)["']?\s*[:：=]\s*["']?([^"'\n\r,，;；|<>{}\[\]]+)""",
            options = setOf(RegexOption.IGNORE_CASE)
        )
        return regex.find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.trim('"', '\'', ',', ';', '；')
            ?.takeIf { it.isNotBlank() }
    }

    private fun extractLineLabel(text: String, labels: List<String>): String? {
        val labelPattern = labels.joinToString("|") { Regex.escape(it) }
        val regex = Regex(
            pattern = """(?im)^\s*(?:$labelPattern)\s*[:：=]\s*(.+?)\s*$""",
            options = setOf(RegexOption.IGNORE_CASE)
        )
        return regex.find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.trim('"', '\'', ',', ';', '；')
            ?.takeIf { it.isNotBlank() }
    }

    private fun parseWeekdayFromBlock(text: String): Int? {
        extractLabel(text, WeekdayLabels)?.let { parseWeekday(it)?.let { weekday -> return weekday } }
        return parseWeekday(text)
    }

    private fun parseWeekday(text: String): Int? {
        val normalized = text.trim()
        val labeledNumber = Regex("""(?i)(?:weekday|weekDay|星期|周几|星期几)\s*[:：=]\s*(\d)""")
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        if (labeledNumber in 1..7) return labeledNumber

        val firstWeekdayToken = WeekdayTokens
            .mapNotNull { (token, weekday) ->
                val index = normalized.indexOf(token, ignoreCase = true)
                if (index >= 0) WeekdayMatch(index, token.length, weekday) else null
            }
            .minWithOrNull(compareBy<WeekdayMatch> { it.index }.thenByDescending { it.length })
        if (firstWeekdayToken != null) return firstWeekdayToken.weekday

        return normalized.toIntOrNull()?.takeIf { it in 1..7 }
    }

    private fun parseSectionRangeFromBlock(text: String): IntRange? {
        extractLabel(text, SectionLabels)?.let { parseSectionRange(it)?.let { range -> return range } }
        return parseSectionRange(text)
    }

    private fun parseSectionRange(text: String): IntRange? {
        val normalized = text.trim()
        val labeledRange = Regex("""(?:第\s*)?(\d{1,2})\s*(?:[-~至到,，、–—－]\s*(\d{1,2}))?\s*(?:节|節|section)""", RegexOption.IGNORE_CASE)
            .find(normalized)
        if (labeledRange != null) {
            return toSectionRange(labeledRange.groupValues[1], labeledRange.groupValues.getOrNull(2))
        }

        val standaloneRange = Regex("""^\s*(\d{1,2})\s*(?:[-~至到,，、–—－]\s*(\d{1,2}))?\s*$""")
            .find(normalized)
        if (standaloneRange != null) {
            return toSectionRange(standaloneRange.groupValues[1], standaloneRange.groupValues.getOrNull(2))
        }

        return null
    }

    private fun toSectionRange(startRaw: String, endRaw: String?): IntRange? {
        val start = startRaw.toIntOrNull() ?: return null
        val end = endRaw?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: start
        if (start !in 1..11 || end !in 1..11 || end < start) return null
        return start..end
    }

    private fun extractWeekText(text: String): String {
        extractLabel(text, WeekLabels)?.let { return it }

        val weekPattern = Regex("""(?:第\s*)?\d{1,2}\s*(?:[-~至到]\s*\d{1,2})?\s*周(?:\s*[\(（]?[单双]周[\)）]?)?""")
        return weekPattern.findAll(text)
            .joinToString(",") { it.value.trim() }
            .ifBlank {
                when {
                    text.contains("单周") -> "单周"
                    text.contains("双周") -> "双周"
                    else -> ""
                }
            }
    }

    private fun parseWeekNumbers(weekText: String): List<Int> {
        if (weekText.isBlank()) return emptyList()

        val oddOnly = weekText.contains("单") || weekText.contains("odd", ignoreCase = true)
        val evenOnly = weekText.contains("双") || weekText.contains("even", ignoreCase = true)
        val oddWeeksOnly = oddOnly && !evenOnly
        val evenWeeksOnly = evenOnly && !oddOnly
        val matches = Regex("""(\d{1,2})\s*(?:[-~至到]\s*(\d{1,2}))?""").findAll(weekText)
        val weeks = matches.flatMap { match ->
            val start = match.groupValues[1].toIntOrNull() ?: return@flatMap emptySequence<Int>()
            val end = match.groupValues.getOrNull(2)
                ?.takeIf { it.isNotBlank() }
                ?.toIntOrNull()
                ?: start
            (minOf(start, end)..maxOf(start, end)).asSequence()
        }.filter { it in 1..30 }.distinct().toList()

        val baseWeeks = weeks.ifEmpty {
            if (oddOnly || evenOnly) DefaultWeeks else emptyList()
        }

        return baseWeeks
            .filter { week -> !oddWeeksOnly || week % 2 == 1 }
            .filter { week -> !evenWeeksOnly || week % 2 == 0 }
    }

    // ── output builders ──────────────────────────────────────────

    private fun TongjiCourse.toDetailEntries(
        week: Int,
        start: Pair<Int, Int>,
        end: Pair<Int, Int>
    ): List<DetailEntry> =
        listOfNotNull(
            teacher.takeIf { it.isNotBlank() }?.let { DetailEntry("Teacher", it) },
            room.takeIf { it.isNotBlank() }?.let { DetailEntry("Room", it) },
            DetailEntry("Time", "${start.toClockText()} - ${end.toClockText()}"),
            DetailEntry("Weeks", weekText.ifBlank { "Week $week" }),
            DetailEntry("Weekday", weekdayName(weekday)),
            DetailEntry("Sections", sectionRangeText()),
            DetailEntry("Source", TongjiCourseSource)
        )

    private fun TongjiCourse.sectionRangeText(): String {
        val range = if (startSection == endSection) {
            startSection.toString()
        } else {
            "$startSection-$endSection"
        }
        return "Section $range"
    }

    private fun colorForCourse(name: String): Color {
        val index = (name.hashCode() and Int.MAX_VALUE) % softPalette.size
        return softPalette[index]
    }

    private fun stableScheduleId(course: TongjiCourse, week: Int, date: LocalDate): String {
        val raw = listOf(
            course.name,
            course.courseCode,
            course.teacher,
            course.room,
            week,
            date,
            course.weekday,
            course.startSection,
            course.endSection
        ).joinToString("|")
        return "tongji-${Integer.toHexString(raw.hashCode())}"
    }

    // ── formatting helpers ───────────────────────────────────────

    private fun Pair<Int, Int>.toClockText(): String =
        "${first.toString().padStart(2, '0')}:${second.toString().padStart(2, '0')}"

    private fun weekdayName(weekday: Int): String =
        when (weekday) {
            1 -> "Mon"
            2 -> "Tue"
            3 -> "Wed"
            4 -> "Thu"
            5 -> "Fri"
            6 -> "Sat"
            7 -> "Sun"
            else -> "Day $weekday"
        }

    private fun normalizePayload(payload: String): String =
        payload
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace("\\u003c", "<", ignoreCase = true)
            .replace("\\u003e", ">", ignoreCase = true)
            .replace("\\u0026", "&", ignoreCase = true)
            .replace("\\t", "\t")
            .replace("\\/", "/")

    private fun markerSegment(payload: String, startMarker: String, endMarker: String?): String {
        val markerStart = payload.indexOf(startMarker)
        if (markerStart < 0) return ""

        val contentStart = markerStart + startMarker.length
        val contentEnd = endMarker
            ?.let { payload.indexOf(it, contentStart) }
            ?.takeIf { it >= contentStart }
            ?: payload.length

        return payload.substring(contentStart, contentEnd)
    }

    private fun markerSegment(payload: String, startMarker: String, endMarkers: List<String>): String {
        val markerStart = payload.indexOf(startMarker)
        if (markerStart < 0) return ""

        val contentStart = markerStart + startMarker.length
        val contentEnd = endMarkers
            .asSequence()
            .mapNotNull { endMarker ->
                payload.indexOf(endMarker, contentStart).takeIf { it >= contentStart }
            }
            .minOrNull()
            ?: payload.length

        return payload.substring(contentStart, contentEnd)
    }

    private fun normalizeBlock(block: String): String =
        normalizePayload(block)
            .replace(CellBreakMarker, "\n")
            .replace(JsonNewLineRegex, "\n")
            .replace(Regex("""[ \t]+"""), " ")
            .replace(Regex("""\n\s+"""), "\n")
            .trim()

    private fun stripHtml(text: String): String =
        text
            .replace(Regex("""(?i)<br\s*/?>"""), "\n")
            .replace(Regex("""(?is)</t[dh]>"""), "\n")
            .replace(Regex("""(?is)<[^>]+>"""), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")

    private fun containsAnyLabel(text: String, labels: List<String>): Boolean =
        labels.any { text.contains(it, ignoreCase = true) }

    // ── constants ────────────────────────────────────────────────

    private val SectionTimes: Map<Int, Pair<Pair<Int, Int>, Pair<Int, Int>>> = mapOf(
        1 to ((8 to 0) to (8 to 45)),
        2 to ((8 to 50) to (9 to 35)),
        3 to ((10 to 0) to (10 to 45)),
        4 to ((10 to 50) to (11 to 35)),
        5 to ((13 to 30) to (14 to 15)),
        6 to ((14 to 20) to (15 to 5)),
        7 to ((15 to 30) to (16 to 15)),
        8 to ((16 to 20) to (17 to 5)),
        9 to ((18 to 30) to (19 to 15)),
        10 to ((19 to 20) to (20 to 5)),
        11 to ((20 to 10) to (20 to 55))
    )

    private val DefaultWeeks = (1..16).toList()
    private const val MaxPayloadLength = 140_000
    private const val MaxLoosePayloadLength = 80_000
    private const val MaxCourseBlockLength = 8_000
    private const val MaxLooseBlocks = 700
    private const val MaxCourseNameLength = 64
    private const val TableMarker = "[[TABLE]]"
    private const val SelectedCoursesMarker = "[[SELECTED_COURSES]]"
    private const val TimetableGridMarker = "[[TIMETABLE_GRID]]"
    private const val BodyMarker = "[[BODY]]"
    private const val ScriptsMarker = "[[SCRIPTS]]"
    private const val CellBreakMarker = "[[BR]]"
    private val JsonNewLineRegex = Regex("""\\n""")
    private val CourseCodeOnlyRegex = Regex("""^[A-Z]{2,}[A-Z0-9-]*\d[A-Z0-9-]*$""")
    private val InlineTimeSlotRegex = Regex(
        """(星期[一二三四五六日天]|周[一二三四五六日天]|Mon(?:day)?|Tue(?:sday)?|Wed(?:nesday)?|Thu(?:rsday)?|Fri(?:day)?|Sat(?:urday)?|Sun(?:day)?|(?:weekday|weekDay|dayOfWeek|星期|周几|星期几)\s*[:：=]\s*[1-7])\s*(?:[,，、;；]\s*)?(?:(?:节次|节数|上课节次|sections?|section)\s*[:：=]\s*)?(?:第\s*)?(\d{1,2})\s*(?:[-~至到,，、–—－]\s*(\d{1,2}))?\s*(?:节|節|sections?|section)\s*(?:(?:周次|上课周次|周数|weeks?|week)\s*[:：=]\s*)?(?:[\[［【(（]\s*([^\]］】)）]+)\s*[\]］】)）]|((?:第\s*)?\d{1,2}\s*(?:[-~至到]\s*\d{1,2})?\s*(?:[单双]\s*)?周?\s*(?:[\(（]?\s*[单双]\s*周?\s*[\)）]?)?|[单双]\s*周?))?""",
        RegexOption.IGNORE_CASE
    )
    private val SelectedCourseTimeSlotRegex = Regex(
        """(星期[一二三四五六日天]|周[一二三四五六日天]|Mon(?:day)?|Tue(?:sday)?|Wed(?:nesday)?|Thu(?:rsday)?|Fri(?:day)?|Sat(?:urday)?|Sun(?:day)?)\s*(?:第\s*)?(\d{1,2})\s*(?:[-~至到,，、–—－]\s*(\d{1,2}))?\s*(?:节|節|sections?|section)\s*(?:[\[［【(（]\s*([^\]］】)）]+)\s*[\]］】)）])?""",
        RegexOption.IGNORE_CASE
    )

    // ── parser labels (bilingual for Chinese page compatibility) ─

    private val CourseNameLabels = listOf("课程名称", "课程名", "课程", "courseName", "course_name", "name", "title")
    private val CourseCodeLabels = listOf("课程序号", "新课程序号", "课程编号", "课程代码", "courseCode", "course_code", "code")
    private val TeacherLabels = listOf("教师", "老师", "任课教师", "teacher", "instructor")
    private val RoomLabels = listOf("教室", "地点", "上课地点", "room", "location", "classroom")
    private val ClassTimeLabels = listOf("上课时间", "课程时间", "时间", "classTime", "time")
    private val WeekLabels = listOf("周次", "上课周次", "周数", "weeks", "week")
    private val WeekdayLabels = listOf("星期", "周几", "星期几", "weekday", "weekDay", "dayOfWeek")
    private val SectionLabels = listOf("节次", "节数", "上课节次", "sections", "section")
    private val MetadataLabels = CourseCodeLabels + TeacherLabels + RoomLabels + WeekLabels + WeekdayLabels + SectionLabels

    private val WeekdayTokens = listOf(
        "星期一" to 1, "周一" to 1, "Mon" to 1, "Monday" to 1,
        "星期二" to 2, "周二" to 2, "Tue" to 2, "Tuesday" to 2,
        "星期三" to 3, "周三" to 3, "Wed" to 3, "Wednesday" to 3,
        "星期四" to 4, "周四" to 4, "Thu" to 4, "Thursday" to 4,
        "星期五" to 5, "周五" to 5, "Fri" to 5, "Friday" to 5,
        "星期六" to 6, "周六" to 6, "Sat" to 6, "Saturday" to 6,
        "星期日" to 7, "星期天" to 7, "周日" to 7, "周天" to 7, "Sun" to 7, "Sunday" to 7
    )
}
