package io.github.jiecaoboy.runwatch

import io.github.jiecaoboy.runwatch.alerts.AlertLogic
import io.github.jiecaoboy.runwatch.alerts.AlertLogic.Kind
import io.github.jiecaoboy.runwatch.data.Run
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertLogicTest {

    private val path = "me/proj"

    private fun run(name: String, state: String) = Run(
        id = "id-$name", name = name, displayName = "Run $name", state = state,
        createdAt = "2026-10-17T00:00:00", heartbeatAt = null, user = null,
        group = null, jobType = null, tags = emptyList(),
    )

    private fun scanned(vararg states: Pair<String, String>) =
        mapOf(path to AlertLogic.SCANNED) + states.map { (n, s) -> "$path/$n" to s }

    private fun alertsOf(outcome: AlertLogic.Outcome) = outcome.alerts.map { it.run.name to it.kind }

    @Test
    fun runningToFailed_alerts() {
        val out = AlertLogic.evaluate(scanned("a" to "running"), path, listOf(run("a", "failed")), false)
        assertEquals(listOf("a" to Kind.FAILED), alertsOf(out))
        assertEquals("failed", out.states["$path/a"])
    }

    @Test
    fun pendingToCrashed_alerts() {
        val out = AlertLogic.evaluate(scanned("a" to "pending"), path, listOf(run("a", "crashed")), false)
        assertEquals(listOf("a" to Kind.FAILED), alertsOf(out))
    }

    @Test
    fun unchangedOrAlreadyTerminal_noAlert() {
        val prev = scanned("a" to "running", "b" to "failed", "c" to "finished")
        val out = AlertLogic.evaluate(prev, path, listOf(run("a", "running"), run("b", "failed"), run("c", "finished")), true)
        assertTrue(out.alerts.isEmpty())
    }

    @Test
    fun killedRun_noAlert() {
        val out = AlertLogic.evaluate(scanned("a" to "running"), path, listOf(run("a", "killed")), true)
        assertTrue(out.alerts.isEmpty())
    }

    @Test
    fun finished_alertsOnlyWhenEnabled() {
        val prev = scanned("a" to "running")
        val runs = listOf(run("a", "finished"))
        assertTrue(AlertLogic.evaluate(prev, path, runs, false).alerts.isEmpty())
        assertEquals(listOf("a" to Kind.FINISHED), alertsOf(AlertLogic.evaluate(prev, path, runs, true)))
    }

    @Test
    fun firstScanOfProject_doesNotAlertOnBacklog() {
        val out = AlertLogic.evaluate(emptyMap(), path, listOf(run("old", "failed"), run("b", "running")), true)
        assertTrue(out.alerts.isEmpty())
        assertEquals(AlertLogic.SCANNED, out.states[path])
        assertEquals("running", out.states["$path/b"])
    }

    @Test
    fun runNeverSeenActive_inWatchedProject_alerts() {
        // Started and crashed between two checks.
        val out = AlertLogic.evaluate(scanned("a" to "finished"), path, listOf(run("new", "crashed"), run("a", "finished")), false)
        assertEquals(listOf("new" to Kind.FAILED), alertsOf(out))
    }

    @Test
    fun runNeverSeen_stillRunning_noAlertButTracked() {
        val out = AlertLogic.evaluate(scanned(), path, listOf(run("new", "running")), false)
        assertTrue(out.alerts.isEmpty())
        assertEquals("running", out.states["$path/new"])
    }

    @Test
    fun missingActiveRuns_findsActiveRunsOffThePage() {
        val prev = scanned("old" to "running", "older" to "pending", "done" to "failed", "a" to "running") +
            ("other/proj/x" to "running")
        val missing = AlertLogic.missingActiveRuns(prev, path, listOf(run("a", "running")))
        assertEquals(setOf("old", "older"), missing.toSet())
    }

    @Test
    fun runFetchedIndividually_alertsWhenItFails() {
        // "old" fell off the newest page; the worker fetched it individually.
        val prev = scanned("old" to "running")
        val page = listOf(run("n1", "running"))
        val out = AlertLogic.evaluate(prev, path, page + run("old", "failed"), false)
        assertEquals(listOf("old" to Kind.FAILED), alertsOf(out))
    }

    @Test
    fun unresolvedRuns_keepPreviousState() {
        val prev = scanned("old" to "running")
        val out = AlertLogic.evaluate(prev, path, emptyList(), false, unresolved = listOf("old"))
        assertEquals("running", out.states["$path/old"])
        assertTrue(out.alerts.isEmpty())
    }

    @Test
    fun inactiveRunsOffThePage_areForgotten() {
        val prev = scanned("gone" to "finished")
        val out = AlertLogic.evaluate(prev, path, listOf(run("a", "running")), false)
        assertEquals(setOf(path, "$path/a"), out.states.keys)
    }
}
