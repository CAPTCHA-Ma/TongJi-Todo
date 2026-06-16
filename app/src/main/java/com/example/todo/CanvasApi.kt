package com.example.todo

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

const val CanvasBaseUrl = "https://canvas.tongji.edu.cn"

data class CanvasCourse(
    val id: String,
    val name: String
)

data class CanvasAssignment(
    val id: String,
    val name: String,
    val descriptionHtml: String?,
    val dueAt: String?,
    val updatedAt: String?,
    val htmlUrl: String?,
    val completed: Boolean
)

sealed class CanvasApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Unauthorized : CanvasApiException("Canvas token is invalid or expired.")
    class NotFound : CanvasApiException("Canvas endpoint not found or token cannot access it.")
    class Server(message: String) : CanvasApiException(message)
    class Network(cause: Throwable) : CanvasApiException("Network error. Cannot connect to Canvas.", cause)
    class Parse(cause: Throwable) : CanvasApiException("Failed to parse Canvas response data.", cause)
}

interface CanvasApi {
    fun fetchActiveCourses(token: String): List<CanvasCourse>
    fun fetchAssignments(token: String, course: CanvasCourse): List<CanvasAssignment>
}

class CanvasApiClient(
    private val baseUrl: String = CanvasBaseUrl
) : CanvasApi {
    override fun fetchActiveCourses(token: String): List<CanvasCourse> =
        getPaginatedArray(
            token = token,
            path = "/api/v1/courses",
            query = listOf(
                "enrollment_state" to "active",
                "include[]" to "term",
                "per_page" to "100"
            )
        ).mapNotNull { json ->
            val id = json.idString() ?: return@mapNotNull null
            val name = json.optCleanString("name")
                ?: json.optCleanString("course_code")
                ?: return@mapNotNull null
            CanvasCourse(id = id, name = name)
        }

    override fun fetchAssignments(token: String, course: CanvasCourse): List<CanvasAssignment> =
        getPaginatedArray(
            token = token,
            path = "/api/v1/courses/${course.id}/assignments",
            query = listOf(
                "include[]" to "submission",
                "order_by" to "due_at",
                "per_page" to "100"
            )
        ).mapNotNull { json ->
            val id = json.idString() ?: return@mapNotNull null
            val name = json.optCleanString("name") ?: return@mapNotNull null
            val submission = json.optJSONObject("submission")
            val workflowState = submission?.optCleanString("workflow_state")
            val completed = workflowState in setOf("submitted", "graded", "complete") ||
                submission?.optCleanString("submitted_at") != null

            CanvasAssignment(
                id = id,
                name = name,
                descriptionHtml = json.optCleanString("description"),
                dueAt = json.optCleanString("due_at"),
                updatedAt = json.optCleanString("updated_at"),
                htmlUrl = json.optCleanString("html_url"),
                completed = completed
            )
        }

    private fun getPaginatedArray(
        token: String,
        path: String,
        query: List<Pair<String, String>>
    ): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        var nextUrl: String? = buildUrl(path, query)

        while (nextUrl != null) {
            val page = requestArray(nextUrl, token)
            for (index in 0 until page.body.length()) {
                result += page.body.getJSONObject(index)
            }
            nextUrl = page.nextUrl
        }

        return result
    }

    private fun requestArray(url: String, token: String): CanvasPage =
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer ${token.trim()}")
            }

            val code = connection.responseCode
            val bodyText = connection.readBody(code)

            when (code) {
                in 200..299 -> CanvasPage(
                    body = JSONArray(bodyText),
                    nextUrl = connection.getHeaderField("Link").parseNextLink()
                )
                401, 403 -> throw CanvasApiException.Unauthorized()
                404 -> throw CanvasApiException.NotFound()
                else -> throw CanvasApiException.Server("Canvas request failed: HTTP $code")
            }
        } catch (exception: CanvasApiException) {
            throw exception
        } catch (exception: org.json.JSONException) {
            throw CanvasApiException.Parse(exception)
        } catch (exception: Exception) {
            throw CanvasApiException.Network(exception)
        }

    private fun buildUrl(path: String, query: List<Pair<String, String>>): String {
        val encodedQuery = query.joinToString("&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }
        return "$baseUrl$path?$encodedQuery"
    }

    private fun HttpURLConnection.readBody(code: Int): String {
        val stream = if (code in 200..299) inputStream else errorStream
        return stream?.readUtf8().orEmpty()
    }

    private fun InputStream.readUtf8(): String =
        bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun String.urlEncode(): String =
        URLEncoder.encode(this, Charsets.UTF_8.name())

    private fun String?.parseNextLink(): String? {
        if (this == null) return null
        return split(",")
            .firstOrNull { it.contains("rel=\"next\"") }
            ?.substringAfter("<", missingDelimiterValue = "")
            ?.substringBefore(">", missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
    }

    private fun JSONObject.idString(): String? =
        optCleanString("id") ?: if (has("id")) optLong("id").toString() else null

    private fun JSONObject.optCleanString(name: String): String? =
        optString(name, "")
            .trim()
            .takeIf { it.isNotBlank() && it != "null" }
}

private data class CanvasPage(
    val body: JSONArray,
    val nextUrl: String?
)
