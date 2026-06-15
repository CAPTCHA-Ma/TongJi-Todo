package com.example.todo

const val CanvasAssignmentTaskIdPrefix = "canvas-assignment-"
const val CanvasAssignmentSource = "来自 Canvas 课程作业"
const val CanvasAssignmentType = "Canvas 作业"
const val CanvasAssignmentCompactTag = "Canvas"

fun Task.isCanvasAssignmentTask(): Boolean =
    id.startsWith(CanvasAssignmentTaskIdPrefix) ||
        description.any { entry ->
            (entry.head == "Source" && entry.info == CanvasAssignmentSource) ||
                (entry.head == "来源" && entry.info == CanvasAssignmentSource)
        }
