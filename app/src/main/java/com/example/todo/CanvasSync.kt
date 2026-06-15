package com.example.todo

import android.content.Context

enum class CanvasSyncStatus {
    Success,
    MissingToken,
    AuthorizationFailed,
    NetworkFailed,
    Failed
}

data class CanvasSyncResult(
    val status: CanvasSyncStatus,
    val message: String,
    val totalAssignments: Int = 0,
    val insertedCount: Int = 0,
    val updatedCount: Int = 0,
    val skippedCount: Int = 0,
    val syncedAtMillis: Long? = null,
    val nextStore: PlannerItemStore? = null
) {
    val isSuccess: Boolean get() = status == CanvasSyncStatus.Success
}

class CanvasSync(
    context: Context,
    private val api: CanvasApi = CanvasApiClient(),
    private val localStore: CanvasSyncLocalStore = CanvasSyncLocalStore(context),
    private val persistence: PlannerPersistence = PlannerPersistence(context)
) {
    fun syncPersistedPlannerStore(forceRefresh: Boolean = false): CanvasSyncResult {
        val currentStore = persistence.load() ?: PlannerItemStore.from(
            schedules = SampleData.defaultSchedules,
            tasks = SampleData.defaultTasks
        )
        val result = syncPlannerStore(currentStore, forceRefresh)
        result.nextStore?.let(persistence::save)
        return result
    }

    fun syncPlannerStore(
        currentStore: PlannerItemStore,
        forceRefresh: Boolean = false
    ): CanvasSyncResult {
        return try {
            val token = localStore.loadToken()
            if (token.isNullOrBlank()) {
                return CanvasSyncResult(
                    status = CanvasSyncStatus.MissingToken,
                    message = "请先在设置中保存 Canvas API Token。"
                )
            }

            val startedAtMillis = System.currentTimeMillis()
            val lastSyncMillis = localStore.loadLastSyncTimeMillis()
            val courses = api.fetchActiveCourses(token)
            val existingTasks = currentStore.storedTasks().associateBy { it.id }
            val assignments = courses.flatMap { course ->
                api.fetchAssignments(token, course).map { assignment -> course to assignment }
            }

            val candidates = assignments.filter { (course, assignment) ->
                val taskId = CanvasAssignmentMapper.taskId(course, assignment)
                forceRefresh ||
                    lastSyncMillis == null ||
                    existingTasks[taskId] == null ||
                    CanvasAssignmentMapper.updatedAtMillis(assignment)?.let { it > lastSyncMillis } != false
            }

            val mappedTasks = candidates.map { (course, assignment) ->
                CanvasAssignmentMapper.toTask(course, assignment, importedAtMillis = startedAtMillis)
            }
            val mergedTasks = mappedTasks.map { task ->
                val existing = existingTasks[task.id]
                if (existing == null) {
                    task
                } else {
                    task.copy(isCompleted = existing.isCompleted || task.isCompleted)
                }
            }
            val nextStore = currentStore.upsertCanvasAssignmentTasks(mappedTasks)
            val insertedCount = mappedTasks.count { existingTasks[it.id] == null }
            val updatedCount = mergedTasks.count { task ->
                val existing = existingTasks[task.id]
                existing != null && existing != task
            }

            localStore.saveLastSyncTimeMillis(startedAtMillis)

            CanvasSyncResult(
                status = CanvasSyncStatus.Success,
                message = "Canvas 同步完成。",
                totalAssignments = assignments.size,
                insertedCount = insertedCount,
                updatedCount = updatedCount,
                skippedCount = assignments.size - candidates.size,
                syncedAtMillis = startedAtMillis,
                nextStore = nextStore
            )
        } catch (exception: CanvasApiException.Unauthorized) {
            CanvasSyncResult(
                status = CanvasSyncStatus.AuthorizationFailed,
                message = exception.message ?: "Canvas Token 无效或已过期。"
            )
        } catch (exception: CanvasApiException.Network) {
            CanvasSyncResult(
                status = CanvasSyncStatus.NetworkFailed,
                message = exception.message ?: "网络异常，无法同步 Canvas。"
            )
        } catch (exception: CanvasApiException) {
            CanvasSyncResult(
                status = CanvasSyncStatus.Failed,
                message = exception.message ?: "Canvas 同步失败。"
            )
        } catch (exception: Exception) {
            CanvasSyncResult(
                status = CanvasSyncStatus.Failed,
                message = exception.message ?: "Canvas 同步失败。"
            )
        }
    }
}
