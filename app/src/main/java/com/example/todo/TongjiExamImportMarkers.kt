package com.example.todo

const val TongjiExamTaskIdPrefix = "tongji-exam-"
const val TongjiExamSource = "Tongji Exam Enquiries"
const val TongjiExamType = "Exam"

fun Task.isTongjiExamTask(): Boolean =
    id.startsWith(TongjiExamTaskIdPrefix) ||
        description.any { entry ->
            (entry.head == "Type" && entry.info == TongjiExamType) ||
                (entry.head == "Source" && entry.info == TongjiExamSource) ||
                (entry.head == "\u7c7b\u578b" && entry.info == TongjiExamType) ||
                (entry.head == "\u6765\u6e90" && entry.info == TongjiExamSource)
        }
