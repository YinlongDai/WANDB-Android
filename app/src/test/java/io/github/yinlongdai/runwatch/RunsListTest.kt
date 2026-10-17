package io.github.yinlongdai.runwatch

import io.github.yinlongdai.runwatch.data.Parsers
import io.github.yinlongdai.runwatch.data.Run
import io.github.yinlongdai.runwatch.ui.RunSort
import io.github.yinlongdai.runwatch.ui.matches
import io.github.yinlongdai.runwatch.ui.sortRuns
import io.github.yinlongdai.runwatch.ui.summaryColumns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunsListTest {

    private fun run(name: String, summary: Map<String, Double> = emptyMap(), tags: List<String> = emptyList(), group: String? = null) = Run(
        id = name, name = name, displayName = "run-$name", state = "finished", createdAt = "", heartbeatAt = null,
        user = "ana", group = group, jobType = null, tags = tags, summary = summary,
    )

    @Test
    fun search_matchesNameTagsGroupUser() {
        val r = run("a1", tags = listOf("baseline"), group = "sweep-3")
        assertTrue(r.matches("RUN-A"))
        assertTrue(r.matches("base"))
        assertTrue(r.matches("sweep"))
        assertTrue(r.matches("ana"))
        assertTrue(r.matches("  "))
        assertFalse(r.matches("lora"))
    }

    @Test
    fun sort_byMetricWithMissingLast() {
        val runs = listOf(run("a", mapOf("acc" to 0.7)), run("b"), run("c", mapOf("acc" to 0.9)), run("d", mapOf("acc" to 0.1)))
        assertEquals(listOf("c", "a", "d", "b"), sortRuns(runs, "acc", RunSort.HighFirst).map { it.name })
        assertEquals(listOf("d", "a", "c", "b"), sortRuns(runs, "acc", RunSort.LowFirst).map { it.name })
        assertEquals(listOf("a", "b", "c", "d"), sortRuns(runs, "acc", RunSort.Newest).map { it.name })
        assertEquals(listOf("a", "b", "c", "d"), sortRuns(runs, null, RunSort.HighFirst).map { it.name })
    }

    @Test
    fun columns_userMetricsFirstAndInternalsHidden() {
        val runs = listOf(
            run("a", mapOf("val/loss" to 1.0, "_step" to 9.0, "_runtime" to 30.0, "loss" to 2.0)),
            run("b", mapOf("acc" to 0.5)),
        )
        assertEquals(listOf("acc", "loss", "val/loss", "_runtime"), summaryColumns(runs))
    }

    @Test
    fun numericSummary_keepsNumbersOnly() {
        val s = Parsers.numericSummary("""{"loss": 0.25, "name": "x", "hist": {"_type": "histogram"}, "_wandb": {"runtime": 3}, "epoch": 4}""")
        assertEquals(mapOf("loss" to 0.25, "epoch" to 4.0), s)
    }
}
