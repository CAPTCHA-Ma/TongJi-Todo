package com.example.todo

import java.time.LocalDate
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class TongjiTimetableImporterTest {
    @Test
    fun parsesStructuredPayloadAndKeepsCourseNameAsScheduleTitle() {
        val payload = """
            {
              "courseName": "高等数学",
              "teacher": "张三",
              "room": "A101",
              "weekday": "周一",
              "sections": "1-2节",
              "weeks": "1-2周"
            }
        """.trimIndent()

        val courses = TongjiTimetableImporter.parseCourses(payload)
        val schedules = TongjiTimetableImporter.toSchedules(courses, LocalDate.of(2026, 2, 23))

        assertEquals(1, courses.size)
        assertEquals("高等数学", courses.single().name)
        assertEquals("张三", courses.single().teacher)
        assertEquals("A101", courses.single().room)
        assertEquals(2, schedules.size)
        assertTrue(schedules.all { it.title == "高等数学" })
        assertEquals(FlexibleDateTime(2026, 2, 23, 8, 0), schedules.first().startTime)
        assertEquals(FlexibleDateTime(2026, 2, 23, 9, 35), schedules.first().endTime)
    }

    @Test
    fun parsesHtmlGridPayloadWithCourseName() {
        val payload = """
            <table>
              <tr><th>节次</th><th>周一</th><th>周二</th></tr>
              <tr>
                <td>3-4节</td>
                <td></td>
                <td>人工智能<br>教师：李四<br>教室：B302<br>周次：3-4周</td>
              </tr>
            </table>
        """.trimIndent()

        val course = TongjiTimetableImporter.parseCourses(payload).single()
        val schedules = TongjiTimetableImporter.toSchedules(listOf(course), LocalDate.of(2026, 2, 23))

        assertEquals("人工智能", course.name)
        assertEquals(2, course.weekday)
        assertEquals(3, course.startSection)
        assertEquals(4, course.endSection)
        assertEquals(2, schedules.size)
        assertEquals(FlexibleDateTime(2026, 3, 10, 10, 0), schedules.first().startTime)
        assertEquals(FlexibleDateTime(2026, 3, 10, 11, 35), schedules.first().endTime)
    }

    @Test
    fun parsesOddWeekAndSectionRange() {
        val payload = """
            课程名称：机器学习
            星期：4
            节次：7-8节
            周次：1-5周 单周
            任课教师：王五
            上课地点：C101
        """.trimIndent()

        val course = TongjiTimetableImporter.parseCourses(payload).single()
        val schedules = TongjiTimetableImporter.toSchedules(listOf(course), LocalDate.of(2026, 2, 23))

        assertEquals(listOf(1, 3, 5), course.weeks)
        assertEquals(3, schedules.size)
        assertTrue(schedules.all { it.title == "机器学习" })
        assertEquals(FlexibleDateTime(2026, 2, 26, 15, 30), schedules.first().startTime)
        assertEquals(FlexibleDateTime(2026, 2, 26, 17, 5), schedules.first().endTime)
    }

    @Test
    fun ignoresNoisyPagePayloadAndKeepsParsingTableCourses() {
        val noisyScript = "function x(){return '课表';}".repeat(500)
        val payload = """
            [[TABLE]]
            节次	周一	周二
            1-2节	数据库系统[[BR]]教师：赵六[[BR]]教室：D204[[BR]]周次：1-2周	
            [[BODY]]
            ${"无关页面文本".repeat(500)}
            [[SCRIPTS]]
            $noisyScript
        """.trimIndent()

        val courses = TongjiTimetableImporter.parseCourses(payload)

        assertEquals(1, courses.size)
        assertEquals("数据库系统", courses.single().name)
        assertEquals("赵六", courses.single().teacher)
        assertEquals("D204", courses.single().room)
    }

    @Test
    fun convertsTongjiGridCourseToScheduleWithDetails() {
        val payload = """
            [[TABLE]]
            节次	周一
            1-2节	穆斌(04162) 数据库原理与应用(42024402) [1-16] 安楼A216
        """.trimIndent()

        val course = TongjiTimetableImporter.parseCourses(payload).single()
        val schedules = TongjiTimetableImporter.toSchedules(listOf(course), LocalDate.of(2026, 2, 23))
        val firstSchedule = schedules.first()
        val details = firstSchedule.description.associate { it.head to it.info }

        assertEquals("数据库原理与应用", course.name)
        assertEquals("42024402", course.courseCode)
        assertEquals("穆斌", course.teacher)
        assertEquals("安楼A216", course.room)
        assertEquals((1..16).toList(), course.weeks)
        assertEquals(16, schedules.size)
        assertTrue(schedules.all { it.title == "数据库原理与应用" })
        assertTrue(firstSchedule.id.startsWith(LegacyTongjiScheduleIdPrefix))
        assertEquals(FlexibleDateTime(2026, 2, 23, 8, 0), firstSchedule.startTime)
        assertEquals(FlexibleDateTime(2026, 2, 23, 9, 35), firstSchedule.endTime)
        assertEquals("08:00 - 09:35", details["Time"])
        assertEquals("Section 1-2", details["Sections"])
        assertEquals("穆斌", details["Teacher"])
        assertEquals("安楼A216", details["Room"])
        assertEquals("1-16周", details["Weeks"])
        assertEquals(TongjiCourseSource, details["Source"])
    }

    @Test
    fun usesTongjiElevenSectionClockForImportedSchedules() {
        val payload = """
            课程名称：晚间课程
            星期：5
            节次：9-11节
            周次：1周
            教室：F101
        """.trimIndent()

        val schedule = TongjiTimetableImporter
            .toSchedules(TongjiTimetableImporter.parseCourses(payload), LocalDate.of(2026, 2, 23))
            .single()

        assertEquals(FlexibleDateTime(2026, 2, 27, 18, 30), schedule.startTime)
        assertEquals(FlexibleDateTime(2026, 2, 27, 20, 55), schedule.endTime)
    }

    @Test
    fun expandsCommaSeparatedTimeSlotsWithIndependentWeekdaysAndSectionRanges() {
        val payload = """
            课程名称：组合课程
            任课教师：李老师
            上课时间：星期三 3-4节 [1-16],星期一 3-4节 [2-16双]
            教室：A101
        """.trimIndent()

        val courses = TongjiTimetableImporter.parseCourses(payload)
        val coursesByWeekday = courses.associateBy { it.weekday }
        val mondayCourse = coursesByWeekday.getValue(1)
        val wednesdayCourse = coursesByWeekday.getValue(3)
        val schedules = TongjiTimetableImporter.toSchedules(courses, LocalDate.of(2026, 2, 23))
        val firstWednesday = schedules.first { it.startTime == FlexibleDateTime(2026, 2, 25, 10, 0) }
        val firstMonday = schedules.first { it.startTime == FlexibleDateTime(2026, 3, 2, 10, 0) }

        assertEquals(2, courses.size)
        assertEquals(3, wednesdayCourse.startSection)
        assertEquals(4, wednesdayCourse.endSection)
        assertEquals((1..16).toList(), wednesdayCourse.weeks)
        assertEquals(3, mondayCourse.startSection)
        assertEquals(4, mondayCourse.endSection)
        assertEquals((2..16 step 2).toList(), mondayCourse.weeks)
        assertEquals(24, schedules.size)
        assertEquals(FlexibleDateTime(2026, 2, 25, 11, 35), firstWednesday.endTime)
        assertEquals(FlexibleDateTime(2026, 3, 2, 11, 35), firstMonday.endTime)
    }

    @Test
    fun prioritizesSelectedCourseListBlocksFromElementTablePage() {
        val payload = """
            [[SELECTED_COURSES]]
            课程名称：计算机网络
            课程序号：42034402
            新课程序号：CST269903
            教师：夏波涌(06005)
            上课时间：星期三 3-4节 [1-16],星期一 3-4节 [2-16双]
            上课地点：博楼B111

            课程名称：操作系统
            课程序号：42036904
            新课程序号：CST269904
            教师：王冬青(04064)
            上课时间：星期二 3-4节 [1-16],星期四 3-4节 [2-16双]
            上课地点：博楼B316

            课程名称：毛泽东思想和中国特色社会主义理论体系概论
            课程序号：5000295002905
            教师：任博(21074)
            上课时间：星期一 5-7节 [1-16]
            上课地点：广楼G107

            课程名称：操作系统课程设计
            课程序号：42028704
            教师：王冬青(04064)
            上课时间：2026-07-13 00:00:00-2026-07-17 00:00:00
            上课地点：校内

            [[TABLE]]
            节次/周次	周一	周二	周三	周四
            第3节课	夏波涌(06005)  计算机网络(42034402) [2-16双] 博楼B111	王冬青(04064)  操作系统(42036904) [1-16] 博楼B316	夏波涌(06005)  计算机网络(42034402) [1-16] 博楼B111	王冬青(04064)  操作系统(42036904) [2-16双] 博楼B316
        """.trimIndent()

        val courses = TongjiTimetableImporter.parseCourses(payload)
        val networkByWeekday = courses
            .filter { it.name == "计算机网络" }
            .associateBy { it.weekday }
        val osByWeekday = courses
            .filter { it.name == "操作系统" }
            .associateBy { it.weekday }
        val politics = courses.single { it.name == "毛泽东思想和中国特色社会主义理论体系概论" }
        val schedules = TongjiTimetableImporter.toSchedules(courses, LocalDate.of(2026, 2, 23))
        val networkWednesday = schedules.first {
            it.title == "计算机网络" && it.startTime == FlexibleDateTime(2026, 2, 25, 10, 0)
        }
        val networkMonday = schedules.first {
            it.title == "计算机网络" && it.startTime == FlexibleDateTime(2026, 3, 2, 10, 0)
        }
        val politicsFirst = schedules.first {
            it.title == "毛泽东思想和中国特色社会主义理论体系概论"
        }

        assertEquals(5, courses.size)
        assertEquals("42034402", networkByWeekday.getValue(3).courseCode)
        assertEquals("夏波涌(06005)", networkByWeekday.getValue(3).teacher)
        assertEquals("博楼B111", networkByWeekday.getValue(3).room)
        assertEquals((1..16).toList(), networkByWeekday.getValue(3).weeks)
        assertEquals((2..16 step 2).toList(), networkByWeekday.getValue(1).weeks)
        assertEquals(3, networkByWeekday.getValue(3).startSection)
        assertEquals(4, networkByWeekday.getValue(3).endSection)
        assertEquals((1..16).toList(), osByWeekday.getValue(2).weeks)
        assertEquals((2..16 step 2).toList(), osByWeekday.getValue(4).weeks)
        assertEquals(5, politics.startSection)
        assertEquals(7, politics.endSection)
        assertTrue(courses.none { it.name == "操作系统课程设计" })
        assertEquals(FlexibleDateTime(2026, 2, 25, 11, 35), networkWednesday.endTime)
        assertEquals(FlexibleDateTime(2026, 3, 2, 11, 35), networkMonday.endTime)
        assertEquals(FlexibleDateTime(2026, 2, 23, 13, 30), politicsFirst.startTime)
        assertEquals(FlexibleDateTime(2026, 2, 23, 16, 15), politicsFirst.endTime)
    }

    @Test
    fun selectedCourseListMarkerSuppressesTimetableGridFallback() {
        val payload = """
            [[SELECTED_COURSES]]
            课程名称：人工智能导论
            课程序号：42041201
            教师：梁爽(12108)
            上课时间：星期一 9-10节 [1]
            上课地点：复楼F211

            [[TIMETABLE_GRID]]
            星期：星期二
            节次：3-4节
            学生课表课程A(11111111) [1-16] A101

            星期：星期三
            节次：5-6节
            学生课表课程B(22222222) [1-16] B202
        """.trimIndent()

        val courses = TongjiTimetableImporter.parseCourses(payload)

        assertEquals(1, courses.size)
        assertEquals("人工智能导论", courses.single().name)
        assertEquals(1, courses.single().weekday)
        assertEquals(9, courses.single().startSection)
        assertTrue(courses.none { it.name.startsWith("学生课表课程") })
    }

    @Test
    fun selectedCourseListMarkerDoesNotFallbackWhenSelectedRowsHaveNoWeeklyTime() {
        val payload = """
            [[SELECTED_COURSES]]
            课程名称：专业实习
            课程序号：42039308
            教师：张惠娟(07001)
            上课时间：2026-07-20 00:00:00-2026-08-14 00:00:00
            上课地点：待定

            [[TIMETABLE_GRID]]
            星期：星期二
            节次：3-4节
            学生课表课程A(11111111) [1-16] A101
        """.trimIndent()

        val courses = TongjiTimetableImporter.parseCourses(payload)

        assertTrue(courses.isEmpty())
    }

    @Test
    fun extractsSelectedCourseListFromProvidedLessonHtmlFixtures() {
        val fixtures = listOf("lesson.html", "phonelesson.html")
            .map(::File)
            .filter { it.isFile }
        assumeTrue(
            "lesson.html fixtures are only available in the local project root",
            fixtures.isNotEmpty()
        )

        fixtures.forEach { fixture ->
            val payload = extractSelectedCoursePayloadFromElementTableHtml(fixture.readText(Charsets.UTF_8))
            val courses = TongjiTimetableImporter.parseCourses(payload)
            val debugWrappedCourses = TongjiTimetableImporter.parseCourses(
                """
                    [[DEBUG]]
                    url=https://1.tongji.edu.cn/GraduateStudentTimeTable
                    main: elTables=2, classTimeTables=1
                    selectedCourseRows=11
                    $payload
                """.trimIndent()
            )
            val databaseCourses = courses.filter { it.name == "数据库原理与应用" }.associateBy { it.weekday }
            val networkCourses = courses.filter { it.name == "计算机网络" }.associateBy { it.weekday }
            val aiCourse = courses.single { it.name == "人工智能导论" }
            val algorithmCourse = courses.single { it.name == "算法设计与分析" }

            assertTrue("${fixture.name} should produce selected course payload", payload.startsWith("[[SELECTED_COURSES]]"))
            assertEquals(fixture.name, 14, courses.size)
            assertEquals("${fixture.name} should parse payload with debug prefix", courses, debugWrappedCourses)
            assertEquals(fixture.name, (1..16).toList(), databaseCourses.getValue(1).weeks)
            assertEquals(fixture.name, (1..16).toList(), databaseCourses.getValue(3).weeks)
            assertEquals(fixture.name, (2..16 step 2).toList(), networkCourses.getValue(1).weeks)
            assertEquals(fixture.name, (1..16).toList(), networkCourses.getValue(3).weeks)
            assertEquals(fixture.name, (1..16).toList(), aiCourse.weeks)
            assertEquals(fixture.name, "1-16周", aiCourse.weekText)
            assertEquals(fixture.name, (1..16).toList(), algorithmCourse.weeks)
            assertEquals(fixture.name, "1-16周", algorithmCourse.weekText)
            assertTrue("${fixture.name} should ignore date-range practice rows", courses.none { it.name == "专业实习" })
            assertTrue("${fixture.name} should ignore date-range course design rows", courses.none { it.name == "操作系统课程设计" })
        }
    }

    @Test
    fun prioritizesTimetableGridBlocksWithDataIndexAndRowspanSections() {
        val payload = """
            [[TIMETABLE_GRID]]
            星期：星期二
            节次：7-8节
            沈莹(13070) 用户交互技术(42034501) [1-16] 广楼G205

            星期：星期四
            节次：7-8节
            金博(23125) 计算机系统结构(42036802) [1-16] 博楼B414

            星期：星期五
            节次：3-4节
            刘敏(97756) 体育(4)(320004E2) [1-16] 游泳馆

            [[TABLE]]
            节次/周次	周一	周二	周三	周四	周五
            第7节课		沈莹(13070) 用户交互技术(42034501) [1-16] 广楼G205	金博(23125) 计算机系统结构(42036802) [1-16] 博楼B414
        """.trimIndent()

        val courses = TongjiTimetableImporter.parseCourses(payload)
        val coursesByName = courses.associateBy { it.name }
        val interaction = coursesByName.getValue("用户交互技术")
        val architecture = coursesByName.getValue("计算机系统结构")
        val pe = coursesByName.getValue("体育(4)")
        val schedules = TongjiTimetableImporter.toSchedules(courses, LocalDate.of(2026, 2, 23))
        val interactionFirst = schedules.first { it.title == "用户交互技术" }
        val architectureFirst = schedules.first { it.title == "计算机系统结构" }
        val peFirst = schedules.first { it.title == "体育(4)" }

        assertEquals(3, courses.size)
        assertEquals(2, interaction.weekday)
        assertEquals(7, interaction.startSection)
        assertEquals(8, interaction.endSection)
        assertEquals(4, architecture.weekday)
        assertEquals(7, architecture.startSection)
        assertEquals(8, architecture.endSection)
        assertEquals(5, pe.weekday)
        assertEquals(3, pe.startSection)
        assertEquals(4, pe.endSection)
        assertEquals(FlexibleDateTime(2026, 2, 24, 15, 30), interactionFirst.startTime)
        assertEquals(FlexibleDateTime(2026, 2, 24, 17, 5), interactionFirst.endTime)
        assertEquals(FlexibleDateTime(2026, 2, 26, 15, 30), architectureFirst.startTime)
        assertEquals(FlexibleDateTime(2026, 2, 26, 17, 5), architectureFirst.endTime)
        assertEquals(FlexibleDateTime(2026, 2, 27, 10, 0), peFirst.startTime)
        assertEquals(FlexibleDateTime(2026, 2, 27, 11, 35), peFirst.endTime)
    }

    @Test
    fun usesTongjiClockForEverySingleSection() {
        val expected = listOf(
            1 to (FlexibleDateTime(2026, 2, 23, 8, 0) to FlexibleDateTime(2026, 2, 23, 8, 45)),
            2 to (FlexibleDateTime(2026, 2, 23, 8, 50) to FlexibleDateTime(2026, 2, 23, 9, 35)),
            3 to (FlexibleDateTime(2026, 2, 23, 10, 0) to FlexibleDateTime(2026, 2, 23, 10, 45)),
            4 to (FlexibleDateTime(2026, 2, 23, 10, 50) to FlexibleDateTime(2026, 2, 23, 11, 35)),
            5 to (FlexibleDateTime(2026, 2, 23, 13, 30) to FlexibleDateTime(2026, 2, 23, 14, 15)),
            6 to (FlexibleDateTime(2026, 2, 23, 14, 20) to FlexibleDateTime(2026, 2, 23, 15, 5)),
            7 to (FlexibleDateTime(2026, 2, 23, 15, 30) to FlexibleDateTime(2026, 2, 23, 16, 15)),
            8 to (FlexibleDateTime(2026, 2, 23, 16, 20) to FlexibleDateTime(2026, 2, 23, 17, 5)),
            9 to (FlexibleDateTime(2026, 2, 23, 18, 30) to FlexibleDateTime(2026, 2, 23, 19, 15)),
            10 to (FlexibleDateTime(2026, 2, 23, 19, 20) to FlexibleDateTime(2026, 2, 23, 20, 5)),
            11 to (FlexibleDateTime(2026, 2, 23, 20, 10) to FlexibleDateTime(2026, 2, 23, 20, 55))
        )
        val courses = expected.map { (section, _) ->
            TongjiCourse(
                name = "Course $section",
                weeks = listOf(1),
                weekday = 1,
                startSection = section,
                endSection = section
            )
        }

        val schedules = TongjiTimetableImporter
            .toSchedules(courses, LocalDate.of(2026, 2, 23))
            .associateBy { it.title.removePrefix("Course ").toInt() }

        assertEquals(11, schedules.size)
        expected.forEach { (section, times) ->
            val schedule = schedules.getValue(section)
            assertEquals(times.first, schedule.startTime)
            assertEquals(times.second, schedule.endTime)
            assertEquals(
                "${times.first.toTimeString()} - ${times.second.toTimeString()}",
                schedule.description.associate { it.head to it.info }["Time"]
            )
        }
    }

    @Test
    fun usesFirstStartAndLastEndForMultiSectionCourse() {
        val course = TongjiCourse(
            name = "Three Section Course",
            weeks = listOf(1),
            weekday = 1,
            startSection = 5,
            endSection = 7
        )

        val schedule = TongjiTimetableImporter
            .toSchedules(listOf(course), LocalDate.of(2026, 2, 23))
            .single()
        val details = schedule.description.associate { it.head to it.info }

        assertEquals(FlexibleDateTime(2026, 2, 23, 13, 30), schedule.startTime)
        assertEquals(FlexibleDateTime(2026, 2, 23, 16, 15), schedule.endTime)
        assertEquals("13:30 - 16:15", details["Time"])
        assertEquals("Section 5-7", details["Sections"])
        assertEquals("13:30 - 16:15", TongjiTimetableImporter.classClockRangeText(course))
    }

    private fun extractSelectedCoursePayloadFromElementTableHtml(html: String): String {
        fun stripTags(value: String): String =
            value
                .replace(Regex("""(?is)<script\b.*?</script>"""), " ")
                .replace(Regex("""(?is)<style\b.*?</style>"""), " ")
                .replace(Regex("""(?is)<!--.*?-->"""), " ")
                .replace(Regex("""(?is)<[^>]+>"""), " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace(Regex("""\s+"""), " ")
                .trim()

        fun visibleCellText(cellHtml: String): String {
            val buttonTexts = Regex("""(?is)<button\b.*?</button>""")
                .findAll(cellHtml)
                .map { stripTags(it.value) }
                .filter { it.isNotBlank() }
                .toList()
            return buttonTexts.lastOrNull() ?: stripTags(cellHtml)
        }

        fun cells(rowHtml: String, tagName: String): List<String> =
            Regex("""(?is)<$tagName\b.*?</$tagName>""")
                .findAll(rowHtml)
                .map { visibleCellText(it.value) }
                .toList()

        fun headerIndex(headers: List<String>, names: List<String>): Int {
            headers.indexOfFirst { header -> names.any { header == it } }
                .takeIf { it >= 0 }
                ?.let { return it }
            return headers.indexOfFirst { header -> names.any { header.contains(it) } }
        }

        val lines = mutableListOf<String>()
        Regex("""(?is)<div[^>]*class="[^"]*\bel-table\b[^"]*"[^>]*>.*?<div class="el-table__column-resize-proxy"""")
            .findAll(html)
            .forEach { tableMatch ->
                val table = tableMatch.value
                val headerWrapper = Regex("""(?is)<div class="el-table__header-wrapper">.*?</table></div>""")
                    .find(table)
                    ?.value
                    ?: return@forEach
                val bodyWrapper = Regex("""(?is)<div class="el-table__body-wrapper[^"]*"[^>]*>.*?</table>""")
                    .find(table)
                    ?.value
                    ?: return@forEach
                val headerRow = Regex("""(?is)<tr\b.*?</tr>""")
                    .find(headerWrapper)
                    ?.value
                    ?: return@forEach
                val headers = cells(headerRow, "th")
                val nameIndex = headerIndex(headers, listOf("课程名称", "课程名"))
                val timeIndex = headerIndex(headers, listOf("上课时间"))
                val teacherIndex = headerIndex(headers, listOf("教师", "任课教师", "老师"))
                val roomIndex = headerIndex(headers, listOf("上课地点", "教室", "地点"))
                val codeIndex = headerIndex(headers, listOf("课程序号"))
                val newCodeIndex = headerIndex(headers, listOf("新课程序号"))
                if (nameIndex < 0 || timeIndex < 0) return@forEach

                Regex("""(?is)<tr\b[^>]*class="[^"]*\bel-table__row\b[^"]*".*?</tr>""")
                    .findAll(bodyWrapper)
                    .forEach { rowMatch ->
                        val rowCells = cells(rowMatch.value, "td")
                        val name = rowCells.getOrNull(nameIndex).orEmpty()
                        val time = rowCells.getOrNull(timeIndex).orEmpty()
                        if (name.isBlank() || !SelectedCourseWeeklyTimeRegex.containsMatchIn(time)) {
                            return@forEach
                        }

                        if (lines.isEmpty()) lines += "[[SELECTED_COURSES]]"
                        lines += "课程名称：$name"
                        rowCells.getOrNull(codeIndex)
                            ?.takeIf { it.isNotBlank() }
                            ?.let { lines += "课程序号：$it" }
                        rowCells.getOrNull(newCodeIndex)
                            ?.takeIf { it.isNotBlank() }
                            ?.let { lines += "新课程序号：$it" }
                        rowCells.getOrNull(teacherIndex)
                            ?.takeIf { it.isNotBlank() }
                            ?.let { lines += "教师：$it" }
                        lines += "上课时间：$time"
                        rowCells.getOrNull(roomIndex)
                            ?.takeIf { it.isNotBlank() }
                            ?.let { lines += "上课地点：$it" }
                        lines += ""
                    }
            }
        return lines.joinToString("\n")
    }

    private companion object {
        val SelectedCourseWeeklyTimeRegex = Regex(
            """星期|周[一二三四五六日天]|Mon|Tue|Wed|Thu|Fri|Sat|Sun""",
            RegexOption.IGNORE_CASE
        )
    }
}
