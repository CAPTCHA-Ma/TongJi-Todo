package com.example.todo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TongjiExamImporterTest {
    @Test
    fun parsesStructuredExamRowsPayload() {
        val payload = """
            [[EXAM_ROWS]]
            $CourseNameLabel:$CourseName
            $ExamTimeLabel:2026-01-08 09:00-11:00
            $ExamLocationLabel:$ExamLocation
            $SeatLabel:12
        """.trimIndent()

        val exam = TongjiExamImporter.parseExams(payload).single()

        assertEquals(CourseName, exam.courseName)
        assertEquals("2026-01-08 09:00-11:00", exam.examTimeText)
        assertEquals(ExamLocation, exam.location)
        assertEquals("12", exam.seatNumber)
    }

    @Test
    fun combinesSeparateExamDateAndTimeColumns() {
        val payload = """
            [[EXAM_ROWS]]
            $CourseNameLabel:$CourseName
            $ExamDateLabel:2026$YearUnitText 1$MonthUnitText 8$DayUnitText
            $TimeLabel:09:00-11:00
            $ExamLocationLabel:$ExamLocation
        """.trimIndent()

        val task = TongjiExamImporter
            .toTasks(TongjiExamImporter.parseExams(payload))
            .single()

        assertEquals(FlexibleDateTime(2026, 1, 8, 9, 0), task.deadline)
    }

    @Test
    fun parsesWeekTextBetweenExamDateAndClock() {
        val payload = """
            [[EXAM_ROWS]]
            $CourseNameLabel:$CourseName
            $ExamTimeLabel:2026-07-02 第18 周 星期四 10:30-1 2:30
            $ExamLocationLabel:$ExamLocation
        """.trimIndent()

        val task = TongjiExamImporter
            .toTasks(TongjiExamImporter.parseExams(payload))
            .single()

        assertEquals(FlexibleDateTime(2026, 7, 2, 10, 30), task.deadline)
    }

    @Test
    fun acceptsExamArrangementListFieldNames() {
        val payload = """
            [[EXAM_ROWS]]
            $ExamSubjectLabel:$CourseName
            $ExamDateLabel:2026-01-08
            $StartTimeLabel:14:30-16:30
            $ExamRoomLabel:$ExamLocation
        """.trimIndent()

        val task = TongjiExamImporter
            .toTasks(TongjiExamImporter.parseExams(payload))
            .single()

        val details = task.description.associate { it.head to it.info }
        assertEquals("$CourseName$ExamTitleSuffix", task.title)
        assertEquals(FlexibleDateTime(2026, 1, 8, 14, 30), task.deadline)
        assertEquals(ExamLocation, details[ExamLocationLabel])
    }

    @Test
    fun importsOnlyRowsWithExamTimeFromArrangementListBody() {
        val payload = """
            [[BODY]]
            $ExamListTitle
            $OrderLabel
            $NewCourseCodeLabel
            $CourseCodeLabel
            $CourseNameLabel
            $ExamTimeLabel
            $ExamLocationLabel
            $ExamStatusLabel
            $ExamRemarkLabel
            $ActionLabel
            1
            DPE210295
            32000495
            体育(4)
            随堂考试：身体素质测试、专项技术评定。
            申请缓考
            2
            CST269301
            42014701
            计算机网络实验
            期末考核安排详见课程通知。
            申请缓考
            3
            CST239702
            42024402
            数据库原理与应用
            2026-07-02 第18
            周 星期四 10:30-1
            2:30
            博楼B306
            正常
            申请缓考
            4
            CST239401
            42034101
            算法设计与分析
            2026-06-25 第17
            周 星期四 10:30-1
            2:30
            博楼B314
            正常
            申请缓考
        """.trimIndent()

        val exams = TongjiExamImporter.parseExams(payload)
        val tasks = TongjiExamImporter.toTasks(exams)

        assertEquals("exams=$exams tasks=$tasks", 2, tasks.size)
        assertEquals("算法设计与分析$ExamTitleSuffix", tasks[0].title)
        assertEquals(FlexibleDateTime(2026, 6, 25, 10, 30), tasks[0].deadline)
        assertEquals("博楼B314", tasks[0].description.associate { it.head to it.info }[ExamLocationLabel])
        assertEquals("数据库原理与应用$ExamTitleSuffix", tasks[1].title)
        assertEquals(FlexibleDateTime(2026, 7, 2, 10, 30), tasks[1].deadline)
        assertEquals("博楼B306", tasks[1].description.associate { it.head to it.info }[ExamLocationLabel])
    }

    @Test
    fun doesNotGuessCourseNameWithoutArrangementListColumns() {
        val payload = """
            [[BODY]]
            $ExamListTitle
            1
            CST239702
            42024402
            $DatabaseCourseName
            2026-07-02 第18周 星期四 10:30-12:30
            博楼B306
            正常
            申请缓考
        """.trimIndent()

        val exams = TongjiExamImporter.parseExams(payload)

        assertTrue("exams=$exams", exams.isEmpty())
    }

    @Test
    fun mapsExamToTaskUsingStartTimeAndLocationDetail() {
        val exam = TongjiExam(
            courseName = CourseName,
            examTimeText = "2026/01/08 14:30-16:30",
            location = ExamLocation,
            examType = FinalExamType,
            seatNumber = "12"
        )

        val task = TongjiExamImporter.toTasks(listOf(exam)).single()
        val details = task.description.associate { it.head to it.info }

        assertTrue(task.id.startsWith(TongjiExamTaskIdPrefix))
        assertEquals("$CourseName$ExamTitleSuffix", task.title)
        assertEquals(FlexibleDateTime(2026, 1, 8, 14, 30), task.deadline)
        assertEquals(ExamLocation, details[ExamLocationLabel])
        assertEquals(TongjiExamSource, details[SourceLabel])
        assertEquals(TongjiExamType, details[TypeLabel])
        assertEquals(FinalExamType, details[ExamTypeLabel])
    }

    private companion object {
        const val CourseNameLabel = "\u8bfe\u7a0b\u540d\u79f0"
        const val ExamListTitle = "\u6392\u8003\u5217\u8868"
        const val OrderLabel = "\u5e8f\u53f7"
        const val NewCourseCodeLabel = "\u65b0\u8bfe\u7a0b\u5e8f\u53f7"
        const val CourseCodeLabel = "\u8bfe\u7a0b\u5e8f\u53f7"
        const val ExamSubjectLabel = "\u8003\u8bd5\u79d1\u76ee"
        const val ExamTimeLabel = "\u8003\u8bd5\u65f6\u95f4"
        const val ExamDateLabel = "\u8003\u8bd5\u65e5\u671f"
        const val TimeLabel = "\u8003\u8bd5\u65f6\u6bb5"
        const val StartTimeLabel = "\u5f00\u59cb\u65f6\u95f4"
        const val ExamLocationLabel = "\u8003\u8bd5\u5730\u70b9"
        const val ExamRoomLabel = "\u8003\u573a\u5730\u70b9"
        const val ExamStatusLabel = "\u8003\u8bd5\u60c5\u51b5"
        const val ExamRemarkLabel = "\u8003\u8bd5\u5907\u6ce8"
        const val ActionLabel = "\u64cd\u4f5c"
        const val SeatLabel = "\u5ea7\u4f4d\u53f7"
        const val SourceLabel = "\u6765\u6e90"
        const val TypeLabel = "\u7c7b\u578b"
        const val ExamTypeLabel = "\u8003\u8bd5\u7c7b\u578b"
        const val YearUnitText = "\u5e74"
        const val MonthUnitText = "\u6708"
        const val DayUnitText = "\u65e5"
        const val ExamTitleSuffix = "\u8003\u8bd5"
        const val CourseName = "\u6570\u5b66\u5206\u6790"
        const val DatabaseCourseName = "\u6570\u636e\u5e93\u539f\u7406\u4e0e\u5e94\u7528"
        const val ExamLocation = "\u56db\u5e73\u8def\u6821\u533a \u6559\u5b66\u5357\u697c101"
        const val FinalExamType = "\u671f\u672b\u8003\u8bd5"
    }
}
