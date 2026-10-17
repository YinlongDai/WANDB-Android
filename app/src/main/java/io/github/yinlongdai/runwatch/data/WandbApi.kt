package io.github.yinlongdai.runwatch.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

class ApiException(
    message: String,
    val isAuthError: Boolean = false,
    val isNotFound: Boolean = false,
) : Exception(message)

/**
 * Thin client for the W&B GraphQL API (the same endpoint the Python SDK uses).
 * Auth is HTTP Basic with user "api" and the API key as the password.
 */
class WandbApi(baseUrl: String, apiKey: String) {

    private val endpoint = baseUrl.trim().trimEnd('/') + "/graphql"
    private val authHeader = "Basic " + Base64.getEncoder()
        .encodeToString("api:${apiKey.trim()}".toByteArray())

    suspend fun viewer(): Viewer {
        val data = gql(
            """
            query Viewer {
              viewer { username entity teams { edges { node { name } } } }
            }
            """
        )
        val v = data.optJSONObject("viewer")
            ?: throw ApiException("This API key is not valid", isAuthError = true)
        val teams = v.optJSONObject("teams")?.optJSONArray("edges")
        return Viewer(
            username = v.optString("username"),
            entity = v.optString("entity"),
            teams = teams.nodes().map { it.optString("name") }.filter { it.isNotEmpty() },
        )
    }

    suspend fun projects(entity: String): List<Project> {
        val out = mutableListOf<Project>()
        var cursor: String? = null
        do {
            val data = gql(
                """
                query Projects(${'$'}entity: String, ${'$'}cursor: String) {
                  models(entityName: ${'$'}entity, after: ${'$'}cursor, first: 100) {
                    pageInfo { endCursor hasNextPage }
                    edges { node { name entityName createdAt } }
                  }
                }
                """,
                JSONObject().put("entity", entity).put("cursor", cursor ?: JSONObject.NULL),
            )
            val models = data.optJSONObject("models") ?: break
            models.optJSONArray("edges").nodes().forEach {
                out += Project(it.optString("name"), it.optString("entityName", entity), it.optString("createdAt"))
            }
            val page = models.optJSONObject("pageInfo")
            cursor = page?.optString("endCursor")?.takeIf { page.optBoolean("hasNextPage") && it.isNotEmpty() }
        } while (cursor != null && out.size < 1000)
        return out
    }

    /** [withSummary] also fetches each run's summary (larger payload), for the metric column. */
    suspend fun runs(
        entity: String,
        project: String,
        cursor: String? = null,
        perPage: Int = 30,
        withSummary: Boolean = false,
    ): RunPage {
        val fields = if (withSummary) "$RUN_FIELDS summaryMetrics" else RUN_FIELDS
        val data = gql(
            """
            query Runs(${'$'}entity: String!, ${'$'}project: String!, ${'$'}cursor: String, ${'$'}perPage: Int) {
              project(name: ${'$'}project, entityName: ${'$'}entity) {
                runCount
                runs(after: ${'$'}cursor, first: ${'$'}perPage, order: "-created_at") {
                  pageInfo { endCursor hasNextPage }
                  edges { node { $fields } }
                }
              }
            }
            """,
            JSONObject().put("entity", entity).put("project", project)
                .put("cursor", cursor ?: JSONObject.NULL).put("perPage", perPage),
        )
        val p = data.optJSONObject("project") ?: throw ApiException("Project $entity/$project not found")
        val runs = p.optJSONObject("runs")
        val page = runs?.optJSONObject("pageInfo")
        return RunPage(
            runs = runs?.optJSONArray("edges").nodes().map(Parsers::parseRun),
            endCursor = page?.optString("endCursor")?.takeIf { it.isNotEmpty() },
            hasNextPage = page?.optBoolean("hasNextPage") ?: false,
            totalCount = p.optInt("runCount"),
        )
    }

    suspend fun runDetail(entity: String, project: String, run: String): RunDetail {
        val node = runNode(
            entity, project, run,
            "$RUN_FIELDS config summaryMetrics historyKeys",
        )
        return RunDetail(
            run = Parsers.parseRun(node),
            metricKeys = Parsers.numericHistoryKeys(node.opt("historyKeys")),
            summary = Parsers.summaryPairs(node.opt("summaryMetrics")),
            config = Parsers.configPairs(node.opt("config")),
        )
    }

    /** Light poll used while a run is live. */
    suspend fun runState(entity: String, project: String, run: String): Run =
        Parsers.parseRun(runNode(entity, project, run, RUN_FIELDS))

    /**
     * Sampled history for several keys in one request (one spec per key).
     * [xKey] is "_step" or "_runtime"; runtime comes back in seconds and is
     * returned in minutes.
     */
    suspend fun sampledHistory(
        entity: String,
        project: String,
        run: String,
        keys: List<String>,
        samples: Int = 500,
        xKey: String = "_step",
    ): List<Series> {
        if (keys.isEmpty()) return emptyList()
        val specs = JSONArray()
        keys.forEach { specs.put(JSONObject().put("keys", JSONArray().put(xKey).put(it)).put("samples", samples).toString()) }
        val data = gql(
            """
            query History(${'$'}entity: String!, ${'$'}project: String!, ${'$'}run: String!, ${'$'}specs: [JSONString!]!) {
              project(name: ${'$'}project, entityName: ${'$'}entity) {
                run(name: ${'$'}run) { sampledHistory(specs: ${'$'}specs) }
              }
            }
            """,
            JSONObject().put("entity", entity).put("project", project).put("run", run).put("specs", specs),
        )
        val lists = data.optJSONObject("project")?.optJSONObject("run")?.optJSONArray("sampledHistory")
            ?: return emptyList()
        return keys.mapIndexed { i, key ->
            val s = Parsers.seriesFromRows(lists.optJSONArray(i) ?: JSONArray(), key, xKey)
            if (xKey == "_runtime") Series(s.label, DoubleArray(s.x.size) { s.x[it] / 60.0 }, s.y) else s
        }
    }

    suspend fun systemMetrics(entity: String, project: String, run: String, samples: Int = 500): List<ChartData> {
        val data = gql(
            """
            query Events(${'$'}entity: String!, ${'$'}project: String!, ${'$'}run: String!, ${'$'}samples: Int) {
              project(name: ${'$'}project, entityName: ${'$'}entity) {
                run(name: ${'$'}run) { events(samples: ${'$'}samples) }
              }
            }
            """,
            JSONObject().put("entity", entity).put("project", project).put("run", run).put("samples", samples),
        )
        val events = data.optJSONObject("project")?.optJSONObject("run")?.optJSONArray("events")
            ?: return emptyList()
        return Parsers.systemCharts(events)
    }

    /**
     * Last [last] console lines. Uses the logLines field (newer servers); if the
     * server doesn't support it, falls back to downloading output.log.
     */
    suspend fun consoleTail(entity: String, project: String, run: String, last: Int = 1000): List<LogLine> =
        try {
            val data = gql(
                """
                query Log(${'$'}entity: String!, ${'$'}project: String!, ${'$'}run: String!, ${'$'}last: Int!) {
                  project(name: ${'$'}project, entityName: ${'$'}entity) {
                    run(name: ${'$'}run) {
                      logLines(last: ${'$'}last) { edges { node { number line level } } }
                    }
                  }
                }
                """,
                JSONObject().put("entity", entity).put("project", project).put("run", run).put("last", last),
            )
            val edges = data.optJSONObject("project")?.optJSONObject("run")
                ?.optJSONObject("logLines")?.optJSONArray("edges")
            edges.nodes().mapIndexed { i, n ->
                LogLine(
                    number = n.optInt("number", i),
                    text = n.optString("line").replace("\r", ""),
                    isError = n.optString("level").equals("error", ignoreCase = true),
                )
            }
        } catch (e: ApiException) {
            if (e.isAuthError) throw e
            outputLogTail(entity, project, run, last)
        }

    private suspend fun outputLogTail(entity: String, project: String, run: String, last: Int): List<LogLine> {
        val data = gql(
            """
            query Files(${'$'}entity: String!, ${'$'}project: String!, ${'$'}run: String!) {
              project(name: ${'$'}project, entityName: ${'$'}entity) {
                run(name: ${'$'}run) { files(names: ["output.log"]) { edges { node { name directUrl } } } }
              }
            }
            """,
            JSONObject().put("entity", entity).put("project", project).put("run", run),
        )
        val url = data.optJSONObject("project")?.optJSONObject("run")?.optJSONObject("files")
            ?.optJSONArray("edges").nodes().firstOrNull()?.optString("directUrl")
            ?.takeIf { it.isNotEmpty() } ?: return emptyList()
        val text = withContext(Dispatchers.IO) {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) throw ApiException("Couldn't download output.log (HTTP ${resp.code})")
                resp.body?.string().orEmpty()
            }
        }
        val lines = text.split('\n').map { it.substringAfterLast('\r') }
        val start = (lines.size - last).coerceAtLeast(0)
        return lines.subList(start, lines.size).mapIndexed { i, l ->
            LogLine(number = start + i, text = l, isError = l.startsWith("ERROR"))
        }
    }

    /** Asks the run's process to stop at its next heartbeat (same as the web UI's Stop button). */
    suspend fun stopRun(runStorageId: String) {
        val data = gql(
            """
            mutation StopRun(${'$'}id: ID!) { stopRun(input: { id: ${'$'}id }) { success } }
            """,
            JSONObject().put("id", runStorageId),
        )
        val ok = data.optJSONObject("stopRun")?.optBoolean("success") ?: false
        if (!ok) throw ApiException("The server did not accept the stop request")
    }

    private suspend fun runNode(entity: String, project: String, run: String, fields: String): JSONObject {
        val data = gql(
            """
            query Run(${'$'}entity: String!, ${'$'}project: String!, ${'$'}run: String!) {
              project(name: ${'$'}project, entityName: ${'$'}entity) { run(name: ${'$'}run) { $fields } }
            }
            """,
            JSONObject().put("entity", entity).put("project", project).put("run", run),
        )
        return data.optJSONObject("project")?.optJSONObject("run")
            ?: throw ApiException("Run $entity/$project/$run not found", isNotFound = true)
    }

    private suspend fun gql(query: String, variables: JSONObject = JSONObject()): JSONObject =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("query", query.trimIndent())
                .put("variables", variables)
                .toString()
                .toRequestBody(JSON)
            val request = Request.Builder()
                .url(endpoint)
                .header("Authorization", authHeader)
                .header("User-Agent", "RunWatch-Android")
                .post(body)
                .build()
            val (code, text) = try {
                http.newCall(request).execute().use { resp ->
                    resp.code to resp.body?.string().orEmpty()
                }
            } catch (e: IOException) {
                throw ApiException("Network error: ${e.message ?: e.javaClass.simpleName}")
            }
            if (code == 401 || code == 403) throw ApiException("Authentication failed. Check your API key.", isAuthError = true)
            if (code !in 200..299) throw ApiException("Server returned HTTP $code")
            val json = runCatching { JSONObject(text) }.getOrElse { throw ApiException("Unexpected response from server") }
            val errors = json.optJSONArray("errors")
            if (errors != null && errors.length() > 0) {
                throw ApiException(errors.optJSONObject(0)?.optString("message") ?: "GraphQL error")
            }
            json.optJSONObject("data") ?: throw ApiException("Empty response from server")
        }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.wandb.ai"

        private val JSON = "application/json".toMediaType()

        private const val RUN_FIELDS =
            "id name displayName state createdAt heartbeatAt group jobType tags user { username }"

        private val http: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        private fun JSONArray?.nodes(): List<JSONObject> {
            if (this == null) return emptyList()
            return (0 until length()).mapNotNull { optJSONObject(it)?.optJSONObject("node") }
        }
    }
}
