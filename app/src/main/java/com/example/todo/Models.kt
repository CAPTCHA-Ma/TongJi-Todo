package com.example.todo

import androidx.compose.ui.graphics.Color

// ──────────────────────────────────────────────
// Shared detail & reminder types
// ──────────────────────────────────────────────

/** A labelled piece of information, e.g. head="Teacher", info="Prof. Zhang". */
data class DetailEntry(
    val head: String,
    val info: String
)

/** A single reminder trigger. */
data class Reminder(
    val time: FlexibleDateTime,
    val enabled: Boolean = true
)

// ──────────────────────────────────────────────
// Schedule (structured daily timetable entry)
// ──────────────────────────────────────────────

data class Schedule(
    val id: String,
    val title: String,

    /** Start date/time — any null field acts as a wildcard (match-all). */
    val startTime: FlexibleDateTime,

    /** End date/time — any null field acts as a wildcard (match-all). */
    val endTime: FlexibleDateTime,

    /** Arbitrary structured detail entries (teacher, room, notes, etc.). */
    val description: List<DetailEntry> = emptyList(),

    /** Ordered list of reminder triggers for this schedule. */
    val reminders: List<Reminder> = emptyList(),

    val color: Color = Color.White
)

// ──────────────────────────────────────────────
// Task (to-do / assignment)
// ──────────────────────────────────────────────

data class Task(
    val id: String,
    val title: String,

    /** Due date/time — any null field acts as a wildcard (match-all). */
    val deadline: FlexibleDateTime,

    /** Arbitrary structured detail entries (links, notes, etc.). */
    val description: List<DetailEntry> = emptyList(),

    /** Ordered list of reminder triggers for this task. */
    val reminders: List<Reminder> = emptyList(),

    val isCompleted: Boolean = false,
    val color: Color = Color.White
)
