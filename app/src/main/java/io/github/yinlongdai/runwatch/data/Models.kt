package io.github.yinlongdai.runwatch.data

data class Viewer(
    val username: String,
    val entity: String,
    val teams: List<String>,
) {
    /** The personal entity first, then teams, without duplicates. */
    val entities: List<String> get() = (listOf(entity) + teams).distinct()
}

data class Project(
    val name: String,
    val entity: String,
    val createdAt: String,
) {
    val path: String get() = "$entity/$name"
}

data class Run(
    /** Storage id (base64), used for mutations such as stopRun. */
    val id: String,
    /** Short run id that appears in URLs. */
    val name: String,
    val displayName: String,
    val state: String,
    val createdAt: String,
    val heartbeatAt: String?,
    val user: String?,
    val group: String?,
    val jobType: String?,
    val tags: List<String>,
) {
    val isRunning: Boolean get() = state == "running" || state == "pending"
    val isFailure: Boolean get() = state == "crashed" || state == "failed"
}

data class RunPage(
    val runs: List<Run>,
    val endCursor: String?,
    val hasNextPage: Boolean,
    val totalCount: Int,
)

data class RunDetail(
    val run: Run,
    val metricKeys: List<String>,
    val summary: List<Pair<String, String>>,
    val config: List<Pair<String, String>>,
)

/** One plotted line. x and y have the same length. */
class Series(
    val label: String,
    val x: DoubleArray,
    val y: DoubleArray,
) {
    val isEmpty: Boolean get() = x.isEmpty()
}

/** A titled chart holding one or more series, e.g. "GPU utilization (%)" with one line per GPU. */
data class ChartData(
    val title: String,
    val series: List<Series>,
    val xLabel: String,
)

data class LogLine(
    val number: Int,
    val text: String,
    val isError: Boolean,
)
