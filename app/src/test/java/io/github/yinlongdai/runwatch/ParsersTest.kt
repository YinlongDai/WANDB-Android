package io.github.yinlongdai.runwatch

import io.github.yinlongdai.runwatch.data.Ansi
import io.github.yinlongdai.runwatch.data.ChartMath
import io.github.yinlongdai.runwatch.data.Parsers
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersTest {

    @Test
    fun numericHistoryKeys_skipsInternalSystemAndNonNumeric() {
        val hk = """
            {"keys": {
              "loss": {"typeCounts": [{"type": "number", "count": 10}]},
              "val/acc": {"typeCounts": [{"type": "number", "count": 3}]},
              "_step": {"typeCounts": [{"type": "number", "count": 10}]},
              "examples": {"typeCounts": [{"type": "image-file", "count": 2}]},
              "system/gpu": {"typeCounts": [{"type": "number", "count": 2}]},
              "Accuracy": {"typeCounts": [{"type": "number", "count": 1}]}
            }, "lastStep": 9}
        """
        // Accepts both an embedded JSON string and an object.
        assertEquals(listOf("Accuracy", "loss", "val/acc"), Parsers.numericHistoryKeys(hk))
        assertEquals(listOf("Accuracy", "loss", "val/acc"), Parsers.numericHistoryKeys(JSONObject(hk)))
        assertEquals(emptyList<String>(), Parsers.numericHistoryKeys(null))
    }

    @Test
    fun seriesFromRows_sortsAndDropsNonFinite() {
        val rows = JSONArray(
            """[{"_step": 2, "loss": 0.5}, {"_step": 0, "loss": 1.0}, {"_step": 1, "loss": "NaN"}, {"_step": 3}]"""
        )
        val s = Parsers.seriesFromRows(rows, "loss")
        assertArrayEquals(doubleArrayOf(0.0, 2.0), s.x, 0.0)
        assertArrayEquals(doubleArrayOf(1.0, 0.5), s.y, 0.0)
    }

    @Test
    fun systemCharts_groupsGpusTogether() {
        val events = JSONArray()
        for (t in 0..2) {
            events.put(
                JSONObject()
                    .put("_runtime", t * 60)
                    .put("system.gpu.0.gpu", 50 + t)
                    .put("system.gpu.1.gpu", 60 + t)
                    .put("system.cpu", 10)
                    .toString()
            )
        }
        val charts = Parsers.systemCharts(events)
        assertEquals("GPU utilization (%)", charts.first().title)
        assertEquals(listOf("GPU 0", "GPU 1"), charts.first().series.map { it.label })
        assertArrayEquals(doubleArrayOf(0.0, 1.0, 2.0), charts.first().series[0].x, 1e-9)
        assertTrue(charts.any { it.title == "Process CPU (%)" })
    }

    @Test
    fun configPairs_unwrapsValueAndFlattens() {
        val cfg = """{"lr": {"value": 0.001, "desc": null}, "model": {"value": {"depth": 12, "name": "vit"}}, "_wandb": {"value": {}}}"""
        assertEquals(
            listOf("lr" to "0.001", "model.depth" to "12", "model.name" to "vit"),
            Parsers.configPairs(cfg),
        )
    }

    @Test
    fun parseRun_readsFields() {
        val run = Parsers.parseRun(
            JSONObject(
                """{"id": "UnVuOjE=", "name": "abc123", "displayName": "brisk-sun-4", "state": "running",
                    "createdAt": "2026-10-17T03:00:00", "heartbeatAt": null, "group": null, "jobType": "train",
                    "tags": ["a", "b"], "user": {"username": "me"}}"""
            )
        )
        assertEquals("brisk-sun-4", run.displayName)
        assertEquals(null, run.group)
        assertEquals(null, run.heartbeatAt)
        assertEquals("me", run.user)
        assertTrue(run.isRunning)
        assertEquals(listOf("a", "b"), run.tags)
    }

    @Test
    fun smooth_zeroWeightIsIdentity_andConstantStaysConstant() {
        val y = doubleArrayOf(1.0, 2.0, 3.0)
        assertArrayEquals(y, ChartMath.smooth(y, 0.0), 0.0)
        val c = ChartMath.smooth(doubleArrayOf(5.0, 5.0, 5.0, 5.0), 0.9)
        c.forEach { assertEquals(5.0, it, 1e-9) }
    }

    @Test
    fun downsample_keepsExtremes() {
        val y = DoubleArray(10_000) { if (it == 4321) 100.0 else 0.0 }
        val idx = ChartMath.downsampleIndices(y.size, y, 200)
        assertTrue(idx.size <= 200)
        assertTrue(4321 in idx)
        assertTrue(idx.toList() == idx.sorted())
    }

    @Test
    fun niceTicks_areRound() {
        assertEquals(listOf(0.0, 0.25, 0.5, 0.75, 1.0), ChartMath.niceTicks(0.0, 1.0, 4))
        assertEquals(listOf(0.0, 500.0, 1000.0), ChartMath.niceTicks(-10.0, 1100.0, 4))
    }

    @Test
    fun ansi_parsesColorsAndStripsOtherEscapes() {
        val spans = Ansi.parse("\u001B[31mERR\u001B[0m ok\u001B[2K")
        assertEquals(2, spans.size)
        assertEquals("ERR", spans[0].text)
        assertEquals(1, spans[0].fg)
        assertEquals(" ok", spans[1].text)
        assertEquals(null, spans[1].fg)
        assertEquals("ERR ok", Ansi.strip("\u001B[31mERR\u001B[0m ok"))
    }
}
