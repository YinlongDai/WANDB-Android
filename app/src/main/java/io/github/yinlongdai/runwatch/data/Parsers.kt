package io.github.yinlongdai.runwatch.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure parsing helpers for W&B GraphQL payloads. Kept free of Android APIs so
 * they can be unit-tested on the JVM.
 */
object Parsers {

    /** GraphQL JSONString scalars arrive either as an embedded JSON string or as an object. */
    fun jsonObjectOf(value: Any?): JSONObject? = when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> value
        is String -> if (value.isBlank()) null else runCatching { JSONObject(value) }.getOrNull()
        else -> null
    }

    fun parseRun(node: JSONObject): Run {
        val tags = node.optJSONArray("tags")
        return Run(
            id = node.optString("id"),
            name = node.optString("name"),
            displayName = node.optStringOrNull("displayName") ?: node.optString("name"),
            state = node.optString("state", "unknown"),
            createdAt = node.optString("createdAt"),
            heartbeatAt = node.optStringOrNull("heartbeatAt"),
            user = node.optJSONObject("user")?.optStringOrNull("username"),
            group = node.optStringOrNull("group"),
            jobType = node.optStringOrNull("jobType"),
            tags = if (tags == null) emptyList() else List(tags.length()) { tags.optString(it) },
            summary = numericSummary(node.opt("summaryMetrics")),
        )
    }

    /** Top-level numeric summary values, skipping W&B internals. Nested and media values are ignored. */
    fun numericSummary(summary: Any?): Map<String, Double> {
        val obj = jsonObjectOf(summary) ?: return emptyMap()
        val out = LinkedHashMap<String, Double>()
        for (key in obj.keys()) {
            if (key.startsWith("_wandb")) continue
            numberOf(obj.opt(key))?.let { out[key] = it }
        }
        return out
    }

    /**
     * historyKeys looks like {"keys": {"loss": {"typeCounts": [{"type": "number", "count": 10}]}}, "lastStep": 9}.
     * Returns the user-logged numeric keys, skipping internal "_" keys and system metrics.
     */
    fun numericHistoryKeys(historyKeys: Any?): List<String> {
        val keys = jsonObjectOf(historyKeys)?.optJSONObject("keys") ?: return emptyList()
        val out = mutableListOf<String>()
        for (key in keys.keys()) {
            if (key.startsWith("_") || key.startsWith("system/") || key.startsWith("system.")) continue
            val counts = keys.optJSONObject(key)?.optJSONArray("typeCounts") ?: continue
            val numeric = (0 until counts.length()).any {
                counts.optJSONObject(it)?.optString("type") == "number"
            }
            if (numeric) out += key
        }
        return out.sortedWith(compareBy({ it.contains('/') }, { it.lowercase() }))
    }

    /** One sampledHistory spec result (a list of row objects) → one series of key against xKey. */
    fun seriesFromRows(rows: JSONArray, key: String, xKey: String = "_step"): Series {
        val xs = ArrayList<Double>(rows.length())
        val ys = ArrayList<Double>(rows.length())
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val x = numberOf(row.opt(xKey)) ?: continue
            val y = numberOf(row.opt(key)) ?: continue
            xs += x
            ys += y
        }
        return sortedSeries(key, xs, ys)
    }

    /**
     * System metrics ("events") come back as a list of JSON strings, one per sample.
     * Groups them into charts: per-GPU metrics share a chart, everything else gets its own.
     */
    fun systemCharts(events: JSONArray): List<ChartData> {
        val rows = (0 until events.length()).mapNotNull { jsonObjectOf(events.opt(it)) }
        if (rows.isEmpty()) return emptyList()
        val xKey = if (rows.any { it.has("_runtime") }) "_runtime" else "_timestamp"
        val t0 = if (xKey == "_timestamp") rows.mapNotNull { numberOf(it.opt(xKey)) }.minOrNull() ?: 0.0 else 0.0

        // group title -> (series label -> points)
        val groups = linkedMapOf<String, LinkedHashMap<String, Pair<ArrayList<Double>, ArrayList<Double>>>>()
        for (row in rows) {
            val x = numberOf(row.opt(xKey))?.minus(t0) ?: continue
            for (key in row.keys()) {
                if (!key.startsWith("system.") && !key.startsWith("system/")) continue
                val y = numberOf(row.opt(key)) ?: continue
                val (title, label) = systemGroup(key.substring(7))
                val series = groups.getOrPut(title) { linkedMapOf() }
                    .getOrPut(label) { ArrayList<Double>() to ArrayList() }
                series.first += x / 60.0 // minutes
                series.second += y
            }
        }
        return groups.entries
            .sortedBy { systemOrder(it.key) }
            .map { (title, seriesMap) ->
                ChartData(
                    title = title,
                    series = seriesMap.entries
                        .sortedBy { it.key }
                        .map { (label, pts) -> sortedSeries(label, pts.first, pts.second) },
                    xLabel = "minutes",
                )
            }
    }

    private val gpuKey = Regex("""^gpu\.(\d+)\.(.+)$""")

    private val gpuNames = mapOf(
        "gpu" to "GPU utilization (%)",
        "memory" to "GPU memory utilization (%)",
        "memoryAllocated" to "GPU memory allocated (%)",
        "memoryAllocatedBytes" to "GPU memory allocated (bytes)",
        "temp" to "GPU temperature (°C)",
        "powerWatts" to "GPU power (W)",
        "powerPercent" to "GPU power (%)",
        "smClock" to "GPU SM clock (MHz)",
        "memoryClock" to "GPU memory clock (MHz)",
    )

    private val otherNames = mapOf(
        "cpu" to "Process CPU (%)",
        "memory" to "System memory (%)",
        "memory_percent" to "System memory (%)",
        "proc.memory.percent" to "Process memory (%)",
        "proc.memory.rssMB" to "Process memory in use (MB)",
        "proc.memory.availableMB" to "Memory available (MB)",
        "proc.cpu.threads" to "Process CPU threads",
        "disk" to "Disk utilization (%)",
        "network.sent" to "Network sent (bytes)",
        "network.recv" to "Network received (bytes)",
    )

    /** Returns (chart title, series label) for a system metric key with the "system." prefix removed. */
    fun systemGroup(key: String): Pair<String, String> {
        gpuKey.matchEntire(key)?.let { m ->
            val (index, metric) = m.destructured
            return (gpuNames[metric] ?: "GPU $metric") to "GPU $index"
        }
        if (key.startsWith("cpu.") && key.endsWith(".cpu_percent")) {
            return "CPU core utilization (%)" to "core ${key.removePrefix("cpu.").removeSuffix(".cpu_percent")}"
        }
        if (key.startsWith("disk.") && key.endsWith(".usagePercent")) {
            return "Disk usage (%)" to key.removePrefix("disk.").removeSuffix(".usagePercent")
        }
        return (otherNames[key] ?: key) to key
    }

    private fun systemOrder(title: String): Int = when {
        title == "GPU utilization (%)" -> 0
        title.startsWith("GPU memory") -> 1
        title.startsWith("GPU") -> 2
        title.contains("CPU") -> 3
        title.contains("memory", ignoreCase = true) -> 4
        title.contains("Disk") -> 5
        title.contains("Network") -> 6
        else -> 7
    }

    /** summaryMetrics → displayable scalar pairs (skips media/histogram blobs). */
    fun summaryPairs(summary: Any?): List<Pair<String, String>> {
        val obj = jsonObjectOf(summary) ?: return emptyList()
        val out = mutableListOf<Pair<String, String>>()
        for (key in obj.keys()) {
            if (key.startsWith("_wandb")) continue
            when (val v = obj.opt(key)) {
                is Number -> out += key to formatNumber(v.toDouble())
                is String -> out += key to v
                is Boolean -> out += key to v.toString()
                else -> Unit
            }
        }
        return out.sortedWith(compareBy({ it.first.startsWith("_") }, { it.first.lowercase() }))
    }

    /** config is {"lr": {"value": 0.001, "desc": null}, ...}; flattens nested dicts with dots. */
    fun configPairs(config: Any?): List<Pair<String, String>> {
        val obj = jsonObjectOf(config) ?: return emptyList()
        val out = mutableListOf<Pair<String, String>>()
        for (key in obj.keys()) {
            if (key.startsWith("_wandb")) continue
            val entry = obj.opt(key)
            val value = if (entry is JSONObject && entry.has("value")) entry.opt("value") else entry
            flatten(key, value, out)
        }
        return out.sortedBy { it.first.lowercase() }
    }

    private fun flatten(prefix: String, value: Any?, out: MutableList<Pair<String, String>>) {
        when (value) {
            is JSONObject -> for (k in value.keys()) flatten("$prefix.$k", value.opt(k), out)
            is Number -> out += prefix to formatNumber(value.toDouble())
            null, JSONObject.NULL -> out += prefix to "null"
            else -> out += prefix to value.toString()
        }
    }

    fun numberOf(v: Any?): Double? {
        val d = when (v) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull()
            else -> null
        } ?: return null
        return if (d.isFinite()) d else null
    }

    fun formatNumber(d: Double): String = when {
        !d.isFinite() -> d.toString()
        d == Math.rint(d) && kotlin.math.abs(d) < 1e15 -> d.toLong().toString()
        kotlin.math.abs(d) >= 1e5 || kotlin.math.abs(d) < 1e-3 -> String.format(java.util.Locale.US, "%.4g", d)
        else -> String.format(java.util.Locale.US, "%.4f", d).trimEnd('0').trimEnd('.')
    }

    private fun sortedSeries(label: String, xs: List<Double>, ys: List<Double>): Series {
        val idx = xs.indices.sortedBy { xs[it] }
        return Series(
            label = label,
            x = DoubleArray(idx.size) { xs[idx[it]] },
            y = DoubleArray(idx.size) { ys[idx[it]] },
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
}
