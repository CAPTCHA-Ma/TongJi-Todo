package com.example.todo

import com.example.todo.ui.theme.SoftBlue
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CanvasAssignmentMapper {
    fun taskId(course: CanvasCourse, assignment: CanvasAssignment): String =
        "$CanvasAssignmentTaskIdPrefix${course.id}-${assignment.id}"

    fun updatedAtMillis(assignment: CanvasAssignment): Long? =
        assignment.updatedAt?.toEpochMillisOrNull()

    fun toTask(
        course: CanvasCourse,
        assignment: CanvasAssignment,
        importedAtMillis: Long = System.currentTimeMillis()
    ): Task {
        val deadline = assignment.dueAt.toFlexibleDateTimeOrEmpty()
        val details = buildList {
            add(DetailEntry("课程", course.name))
            add(DetailEntry("来源", CanvasAssignmentSource))
            add(DetailEntry("类型", CanvasAssignmentType))
            assignment.descriptionHtml
                ?.htmlToPlainText()
                ?.takeIf { it.isNotBlank() }
                ?.let { add(DetailEntry("作业详情", it)) }
            assignment.dueAt
                ?.formatCanvasDateTime()
                ?.let { add(DetailEntry("截止时间", it)) }
            assignment.htmlUrl
                ?.let { add(DetailEntry("Canvas 链接", it)) }
            add(DetailEntry("完成状态", if (assignment.completed) "已提交" else "未完成"))
            add(DetailEntry("Canvas Course ID", course.id))
            add(DetailEntry("Canvas Assignment ID", assignment.id))
            assignment.updatedAt?.let { add(DetailEntry("Canvas Updated At", it)) }
            add(DetailEntry("Imported At", importedAtMillis.toString()))
        }

        return Task(
            id = taskId(course, assignment),
            title = assignment.name,
            deadline = deadline,
            description = details,
            reminders = assignment.dueAt.toReminderBeforeDue(),
            isCompleted = assignment.completed,
            color = SoftBlue
        )
    }

    private fun String?.toFlexibleDateTimeOrEmpty(): FlexibleDateTime {
        val local = this?.toLocalDateTimeOrNull() ?: return FlexibleDateTime()
        return FlexibleDateTime(
            year = local.year,
            month = local.monthValue,
            day = local.dayOfMonth,
            hour = local.hour,
            minute = local.minute
        )
    }

    private fun String?.toReminderBeforeDue(): List<Reminder> {
        val local = this?.toLocalDateTimeOrNull() ?: return emptyList()
        val reminder = local.minusDays(1)
        return listOf(
            Reminder(
                time = FlexibleDateTime(
                    year = reminder.year,
                    month = reminder.monthValue,
                    day = reminder.dayOfMonth,
                    hour = reminder.hour,
                    minute = reminder.minute
                )
            )
        )
    }

    private fun String.toLocalDateTimeOrNull(): LocalDateTime? =
        runCatching {
            OffsetDateTime.parse(this)
                .atZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime()
        }.getOrNull()

    private fun String.toEpochMillisOrNull(): Long? =
        runCatching { OffsetDateTime.parse(this).toInstant().toEpochMilli() }.getOrNull()

    private fun String.formatCanvasDateTime(): String? =
        toLocalDateTimeOrNull()?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

    private fun String.htmlToPlainText(): String =
        replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .lines()
            .joinToString("\n") { line -> line.trim().replace(Regex("\\s+"), " ") }
            .trim()
}
