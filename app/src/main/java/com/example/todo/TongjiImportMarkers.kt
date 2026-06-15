package com.example.todo

const val TongjiCourseTaskIdPrefix = "tongji-course-"
const val LegacyTongjiScheduleIdPrefix = "tongji-"
const val TongjiCourseSource = "Tongji Graduate Timetable"
const val TongjiCourseType = "Course"

fun Task.isTongjiCourseTask(): Boolean =
    id.startsWith(TongjiCourseTaskIdPrefix) ||
        description.any { entry ->
            (entry.head == "Type" && entry.info == TongjiCourseType) ||
                (entry.head == "Source" && entry.info == TongjiCourseSource)
        }

fun Schedule.isTongjiCourseSchedule(): Boolean =
    id.startsWith(LegacyTongjiScheduleIdPrefix) ||
        description.any { entry -> entry.head == "Source" && entry.info == TongjiCourseSource }
