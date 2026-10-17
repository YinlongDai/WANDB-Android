package io.github.jiecaoboy.runwatch

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import io.github.jiecaoboy.runwatch.alerts.RunAlertWorker
import io.github.jiecaoboy.runwatch.ui.RunWatchNav
import io.github.jiecaoboy.runwatch.ui.RunWatchTheme

class RunWatchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RunAlertWorker.ensureChannel(this)
        RunAlertWorker.sync(this)
    }
}

class MainActivity : ComponentActivity() {

    /** "entity/project/run" from a tapped notification, consumed by the nav host. */
    private val pendingRunPath = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) pendingRunPath.value = intent.getStringExtra(EXTRA_RUN_PATH)
        setContent {
            RunWatchTheme {
                RunWatchNav(
                    pendingRunPath = pendingRunPath.value,
                    onPendingConsumed = { pendingRunPath.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_RUN_PATH)?.let { pendingRunPath.value = it }
    }

    companion object {
        const val EXTRA_RUN_PATH = "run_path"
    }
}
