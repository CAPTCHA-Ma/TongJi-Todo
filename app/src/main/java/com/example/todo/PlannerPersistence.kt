package com.example.todo

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject

class PlannerPersistence(context: Context) {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun load(): PlannerItemStore? {
        val raw = preferences.getString(StoreKey, null) ?: return null
        return runCatching {
            val root = JSONObject(raw)
            val schedules = root.getJSONArray("schedules").mapObjects(::scheduleFromJson)
            val tasks = root.getJSONArray("tasks").mapObjects(::taskFromJson)
            PlannerItemStore.from(schedules = schedules, tasks = tasks)
        }.getOrNull()
    }

    fun save(store: PlannerItemStore) {
        val root = JSONObject()
            .put("version", StoreVersion)
            .put("schedules", JSONArray().apply {
                store.storedSchedules().forEach { put(it.toJson()) }
            })
            .put("tasks", JSONArray().apply {
                store.storedTasks().forEach { put(it.toJson()) }
            })

        preferences.edit()
            .putString(StoreKey, root.toString())
            .apply()
    }

    fun clear() {
        preferences.edit()
            .remove(StoreKey)
            .apply()
    }

    private fun Schedule.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("title", title)
            .put("startTime", startTime.toJson())
            .put("endTime", endTime.toJson())
            .put("description", detailEntriesToJson(description))
            .put("reminders", remindersToJson(reminders))
            .put("color", color.toArgb())

    private fun Task.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("title", title)
            .put("deadline", deadline.toJson())
            .put("description", detailEntriesToJson(description))
            .put("reminders", remindersToJson(reminders))
            .put("isCompleted", isCompleted)
            .put("color", color.toArgb())
            .put("cost", cost)

    private fun FlexibleDateTime.toJson(): JSONObject =
        JSONObject()
            .putNullable("year", year)
            .putNullable("month", month)
            .putNullable("day", day)
            .putNullable("hour", hour)
            .putNullable("minute", minute)

    private fun detailEntriesToJson(entries: List<DetailEntry>): JSONArray =
        JSONArray().apply {
            entries.forEach { entry ->
                put(
                    JSONObject()
                        .put("head", entry.head)
                        .put("info", entry.info)
                )
            }
        }

    private fun remindersToJson(reminders: List<Reminder>): JSONArray =
        JSONArray().apply {
            reminders.forEach { reminder ->
                put(
                    JSONObject()
                        .put("time", reminder.time.toJson())
                        .put("enabled", reminder.enabled)
                )
            }
        }

    private fun scheduleFromJson(json: JSONObject): Schedule =
        Schedule(
            id = json.getString("id"),
            title = json.getString("title"),
            startTime = flexibleDateTimeFromJson(json.getJSONObject("startTime")),
            endTime = flexibleDateTimeFromJson(json.getJSONObject("endTime")),
            description = json.optJSONArray("description").orEmptyObjects(::detailEntryFromJson),
            reminders = json.optJSONArray("reminders").orEmptyObjects(::reminderFromJson),
            color = Color(json.optInt("color", Color.White.toArgb()))
        )

    private fun taskFromJson(json: JSONObject): Task =
        Task(
            id = json.getString("id"),
            title = json.getString("title"),
            deadline = flexibleDateTimeFromJson(json.getJSONObject("deadline")),
            description = json.optJSONArray("description").orEmptyObjects(::detailEntryFromJson),
            reminders = json.optJSONArray("reminders").orEmptyObjects(::reminderFromJson),
            isCompleted = json.optBoolean("isCompleted", false),
            color = Color(json.optInt("color", Color.White.toArgb())),
            cost = json.optInt("cost", 3)
        )

    private fun flexibleDateTimeFromJson(json: JSONObject): FlexibleDateTime =
        FlexibleDateTime(
            year = json.optNullableInt("year"),
            month = json.optNullableInt("month"),
            day = json.optNullableInt("day"),
            hour = json.optNullableInt("hour"),
            minute = json.optNullableInt("minute")
        )

    private fun detailEntryFromJson(json: JSONObject): DetailEntry =
        DetailEntry(
            head = json.optString("head"),
            info = json.optString("info")
        )

    private fun reminderFromJson(json: JSONObject): Reminder =
        Reminder(
            time = flexibleDateTimeFromJson(json.getJSONObject("time")),
            enabled = json.optBoolean("enabled", true)
        )

    private fun JSONObject.putNullable(name: String, value: Int?): JSONObject =
        put(name, value ?: JSONObject.NULL)

    private fun JSONObject.optNullableInt(name: String): Int? =
        if (has(name) && !isNull(name)) optInt(name) else null

    private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
        (0 until length()).map { index -> transform(getJSONObject(index)) }

    private fun <T> JSONArray?.orEmptyObjects(transform: (JSONObject) -> T): List<T> =
        this?.mapObjects(transform).orEmpty()

    private companion object {
        const val PreferencesName = "planner_items"
        const val StoreKey = "planner_store"
        const val StoreVersion = 2
    }
}
