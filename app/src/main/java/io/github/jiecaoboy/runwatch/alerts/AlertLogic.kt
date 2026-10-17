package io.github.jiecaoboy.runwatch.alerts

import io.github.jiecaoboy.runwatch.data.Run

/**
 * Pure state-transition logic behind the run alerts, kept free of Android APIs
 * so it can be unit-tested on the JVM.
 *
 * Known states are stored as "entity/project/run" -> state. A project that has
 * been checked at least once also gets a marker entry "entity/project" ->
 * [SCANNED], so runs that appear for the first time can be told apart from the
 * backlog seen when a project is first starred.
 */
object AlertLogic {

    const val SCANNED = "__scanned"

    enum class Kind { FAILED, FINISHED }

    data class Alert(val key: String, val run: Run, val kind: Kind)

    data class Outcome(val states: Map<String, String>, val alerts: List<Alert>)

    private fun isActive(state: String?) = state == "running" || state == "pending"

    /**
     * Runs of [path] that were active at the last check but are not in [page]
     * (they have scrolled past the newest runs). Their state must be fetched
     * one by one, or they would never alert.
     */
    fun missingActiveRuns(previous: Map<String, String>, path: String, page: List<Run>): List<String> {
        val prefix = "$path/"
        val onPage = page.mapTo(HashSet()) { it.name }
        return previous.entries
            .filter { (k, v) -> k.startsWith(prefix) && isActive(v) }
            .map { it.key.removePrefix(prefix) }
            .filter { it !in onPage }
    }

    /**
     * Compares the latest [runs] of [path] (the newest page plus any runs fetched
     * via [missingActiveRuns]) against [previous] and returns the new states for
     * this project and the alerts to post.
     *
     * [unresolved] are run names whose state couldn't be fetched this time; their
     * previous state is kept so a transient error doesn't lose the alert.
     */
    fun evaluate(
        previous: Map<String, String>,
        path: String,
        runs: List<Run>,
        notifyOnFinish: Boolean,
        unresolved: Collection<String> = emptyList(),
    ): Outcome {
        val scannedBefore = previous[path] == SCANNED
        val states = linkedMapOf(path to SCANNED)
        val alerts = mutableListOf<Alert>()
        for (name in unresolved) {
            val key = "$path/$name"
            previous[key]?.let { states[key] = it }
        }
        for (run in runs) {
            val key = "$path/${run.name}"
            states[key] = run.state
            val before = previous[key]
            val shouldCheck = when {
                before != null -> isActive(before) && run.state != before
                // Unseen run in an already-watched project: it started and ended
                // between two checks, so we never saw it running.
                else -> scannedBefore
            }
            if (!shouldCheck) continue
            when {
                run.isFailure -> alerts += Alert(key, run, Kind.FAILED)
                run.state == "finished" && notifyOnFinish -> alerts += Alert(key, run, Kind.FINISHED)
            }
        }
        return Outcome(states, alerts)
    }
}
