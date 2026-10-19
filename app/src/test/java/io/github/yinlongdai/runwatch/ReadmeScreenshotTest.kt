package io.github.yinlongdai.runwatch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.yinlongdai.runwatch.data.ChartData
import io.github.yinlongdai.runwatch.data.Run
import io.github.yinlongdai.runwatch.data.RunDetail
import io.github.yinlongdai.runwatch.data.Series
import io.github.yinlongdai.runwatch.ui.ChartControls
import io.github.yinlongdai.runwatch.ui.ChartPadding
import io.github.yinlongdai.runwatch.ui.ChartPalette
import io.github.yinlongdai.runwatch.ui.GlassTopBar
import io.github.yinlongdai.runwatch.ui.LineChart
import io.github.yinlongdai.runwatch.ui.OverviewTab
import io.github.yinlongdai.runwatch.ui.PillSearchField
import io.github.yinlongdai.runwatch.ui.RunRow
import io.github.yinlongdai.runwatch.ui.RunTabsBar
import io.github.yinlongdai.runwatch.ui.RunWatchTheme
import io.github.yinlongdai.runwatch.ui.SearchLayout
import io.github.yinlongdai.runwatch.ui.SmallIconButton
import io.github.yinlongdai.runwatch.ui.StateBadge
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import kotlin.math.exp
import kotlin.random.Random

/**
 * The main screens (runs list, charts, compare, overview) built from the real
 * components with made-up demo data, at a fixed time so they render the same
 * every run. Layoutlib can't blur, so the README uses device screenshots instead.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
class ReadmeScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT), environment = testEnvironment())

    private val now = Instant.parse("2026-10-18T15:00:00Z")

    private fun run(
        name: String,
        display: String,
        state: String,
        created: String,
        heartbeat: String?,
        acc: Double?,
        tags: List<String> = emptyList(),
        group: String? = null,
    ) = Run(
        id = name, name = name, displayName = display, state = state,
        createdAt = created, heartbeatAt = heartbeat, user = "ada", group = group, jobType = "train", tags = tags,
        summary = acc?.let { mapOf("val/acc" to it) } ?: emptyMap(),
    )

    private val runs = listOf(
        run("k3p9x2aa", "vit-b16-lr3e-4", "running", "2026-10-18T09:12:00Z", null, 0.9132, listOf("sweep"), "vit-base"),
        run("q7m2c8bb", "vit-b16-lr1e-4", "running", "2026-10-18T09:10:00Z", null, 0.9047, listOf("sweep"), "vit-base"),
        run("z1w4r6cc", "vit-b16-warmup2k", "finished", "2026-10-17T20:31:00Z", "2026-10-18T04:02:00Z", 0.9268, listOf("best"), "vit-base"),
        run("h8t5n3dd", "vit-s16-baseline", "finished", "2026-10-17T14:05:00Z", "2026-10-17T19:47:00Z", 0.8815, group = "vit-small"),
        run("u2e9j7ee", "vit-b16-lr1e-3", "crashed", "2026-10-17T11:40:00Z", "2026-10-17T12:18:00Z", 0.4120, listOf("sweep"), "vit-base"),
        run("b6y1f4ff", "vit-s16-mixup", "finished", "2026-10-16T22:15:00Z", "2026-10-17T03:51:00Z", 0.8932, listOf("aug"), "vit-small"),
        run("p4d8s2gg", "resnet50-ref", "finished", "2026-10-16T08:00:00Z", "2026-10-16T13:26:00Z", 0.8701, group = "baselines"),
        run("c9v3k5hh", "vit-s16-cutmix", "failed", "2026-10-15T18:20:00Z", "2026-10-15T18:24:00Z", null, listOf("aug"), "vit-small"),
        run("r5a7m1ii", "vit-ti16-distill", "finished", "2026-10-15T09:44:00Z", "2026-10-15T14:09:00Z", 0.8423, listOf("distill"), "vit-tiny"),
    )

    // Smooth curves with seeded random jitter, like real training logs.
    private fun noise(n: Int, seed: Int): DoubleArray {
        val r = Random(seed)
        return DoubleArray(n) { r.nextDouble() * 2 - 1 }
    }
    private fun steps(n: Int) = DoubleArray(n) { it * 10.0 }
    private fun loss(seed: Int, tau: Double = 700.0, floor: Double = 0.18, n: Int = 300): DoubleArray {
        val e = noise(n, seed)
        return DoubleArray(n) { i -> (2.3 * exp(-i * 10 / tau) + floor) * (1 + 0.12 * e[i]) }
    }
    private fun acc(seed: Int, tau: Double = 800.0, top: Double = 0.92, n: Int = 300): DoubleArray {
        val e = noise(n, seed)
        return DoubleArray(n) { i -> 0.05 + (top - 0.05) * (1 - exp(-i * 10 / tau)) + 0.025 * e[i] }
    }

    @Composable
    private fun Glass(content: @Composable (HazeState) -> Unit) = RunWatchTheme {
        // Layoutlib can't blur, so the bars show their no-blur fallback here.
        content(rememberHazeState(blurEnabled = false))
    }

    @Composable
    private fun Subtitle(text: String) = Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )

    @Test
    fun runsList() {
        paparazzi.snapshot {
            Glass { glass ->
                Scaffold(
                    topBar = {
                        GlassTopBar(
                            glass,
                            title = { Column { Text("vit-imagenet"); Subtitle("demo-lab · 24 runs") } },
                            navigationIcon = { IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                            actions = {
                                IconButton(onClick = {}) { Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.primary) }
                                IconButton(onClick = {}) { Icon(Icons.Filled.Refresh, null) }
                            },
                        )
                    },
                ) { padding ->
                    SearchLayout(
                        atBottom = true,
                        barPadding = padding,
                        glass = glass,
                        search = { m, floating -> PillSearchField("", {}, "Search runs, tags, groups", m, floating, glass) },
                    ) { listPadding ->
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding) {
                            item {
                                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(selected = true, onClick = {}, label = { Text("All") })
                                    FilterChip(selected = false, onClick = {}, label = { Text("Running") })
                                    FilterChip(
                                        selected = true,
                                        onClick = {},
                                        label = { Text("val/acc") },
                                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
                                    )
                                    FilterChip(selected = true, onClick = {}, label = { Text("Highest first") })
                                }
                            }
                            items(runs.sortedByDescending { it.summary["val/acc"] ?: -1.0 }) { r ->
                                RunRow(r, "val/acc", selected = false, onClick = {}, onToggle = {}, now = now)
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun runCharts() {
        val charts = listOf(
            "train/loss" to Series("train/loss", steps(300), loss(1)),
            "val/acc" to Series("val/acc", steps(300), acc(2)),
            "train/grad_norm" to Series("train/grad_norm", steps(300), noise(300, 3).let { e -> DoubleArray(300) { i -> 1.1 * exp(-i / 90.0) + 0.35 + 0.12 * e[i] } }),
        )
        paparazzi.snapshot {
            Glass { glass ->
                Scaffold(
                    topBar = {
                        GlassTopBar(
                            glass,
                            title = { Column { Text("vit-b16-lr3e-4"); Subtitle("demo-lab/vit-imagenet") } },
                            navigationIcon = { IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                            actions = { StateBadge("running", Modifier.padding(end = 12.dp)) },
                        )
                    },
                    bottomBar = { RunTabsBar(0, glass) {} },
                ) { padding ->
                    SearchLayout(
                        atBottom = true,
                        barPadding = padding,
                        glass = glass,
                        search = { m, floating -> PillSearchField("", {}, "Filter 38 metrics", m, floating, glass) },
                    ) { listPadding ->
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = listPadding.calculateTopPadding(), bottom = listPadding.calculateBottomPadding()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item { ChartControls(0.6f, {}, false, {}, false, {}, "_step") }
                            item {
                                Text(
                                    "Pinned",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                                )
                            }
                            items(charts) { (key, s) ->
                                Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                                    LineChart(
                                        ChartData(key, listOf(s), "step"),
                                        smoothing = 0.6f,
                                        logY = false,
                                        modifier = ChartPadding,
                                        actions = {
                                            SmallIconButton(Icons.Filled.Share, "Share") {}
                                            val pinned = key != "train/grad_norm"
                                            SmallIconButton(
                                                if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                                "Pin",
                                                tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            ) {}
                                            SmallIconButton(Icons.Filled.OpenInFull, "Expand") {}
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun compareRuns() {
        val names = listOf("vit-b16-warmup2k", "vit-b16-lr3e-4", "vit-b16-lr1e-4", "vit-s16-baseline")
        val states = listOf("finished", "running", "running", "finished")
        val lengths = listOf(300, 210, 190, 300)
        fun lines(make: (Int, Int) -> DoubleArray) = names.mapIndexed { i, n ->
            Series(n, steps(lengths[i]), make(i, lengths[i]), colorIndex = i)
        }
        val charts = listOf(
            ChartData("val/acc", lines { i, n -> acc(i + 4, tau = 700.0 + 150 * i, top = listOf(0.93, 0.92, 0.91, 0.885)[i], n = n) }, "step"),
            ChartData("train/loss", lines { i, n -> loss(i + 8, tau = 650.0 + 120 * i, floor = 0.15 + 0.04 * i, n = n) }, "step"),
        )
        paparazzi.snapshot {
            Glass { glass ->
                Scaffold(
                    topBar = {
                        GlassTopBar(
                            glass,
                            title = { Column { Text("Compare 4 runs"); Subtitle("demo-lab/vit-imagenet") } },
                            navigationIcon = { IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                        )
                    },
                ) { padding ->
                    SearchLayout(
                        atBottom = true,
                        barPadding = padding,
                        glass = glass,
                        search = { m, floating -> PillSearchField("", {}, "Filter 38 metrics", m, floating, glass) },
                    ) { listPadding ->
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = listPadding.calculateTopPadding() + 4.dp, bottom = listPadding.calculateBottomPadding()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item {
                                FlowRow(
                                    Modifier.padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    names.forEachIndexed { i, n ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(10.dp).background(ChartPalette[i], CircleShape))
                                            Text(" $n", style = MaterialTheme.typography.labelMedium)
                                            StateBadge(states[i], Modifier.padding(start = 6.dp))
                                        }
                                    }
                                }
                            }
                            item { ChartControls(0.6f, {}, false, {}, false, {}, "_step") }
                            items(charts) { c ->
                                Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                                    LineChart(
                                        c,
                                        smoothing = 0.6f,
                                        logY = false,
                                        showStats = false,
                                        modifier = ChartPadding,
                                        actions = {
                                            SmallIconButton(Icons.Filled.Share, "Share") {}
                                            SmallIconButton(Icons.Filled.OpenInFull, "Expand") {}
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun runOverview() {
        val detail = RunDetail(
            run = runs[2],
            metricKeys = emptyList(),
            summary = listOf(
                "train/loss" to "0.1874", "val/acc" to "0.9268", "val/top5" to "0.9871",
                "epoch" to "90", "_runtime" to "27060", "_step" to "300000",
            ),
            config = listOf(
                "batch_size" to "1024", "dataset" to "imagenet-1k", "epochs" to "90",
                "model.depth" to "12", "model.heads" to "12", "model.patch" to "16", "model.width" to "768",
                "optimizer.lr" to "0.0003", "optimizer.name" to "adamw", "optimizer.weight_decay" to "0.05",
                "warmup_steps" to "2000",
            ),
        )
        paparazzi.snapshot {
            Glass { glass ->
                Scaffold(
                    topBar = {
                        GlassTopBar(
                            glass,
                            title = { Column { Text("vit-b16-warmup2k"); Subtitle("demo-lab/vit-imagenet") } },
                            navigationIcon = { IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                            actions = { StateBadge("finished", Modifier.padding(end = 12.dp)) },
                        )
                    },
                    bottomBar = { RunTabsBar(3, glass) {} },
                ) { padding ->
                    OverviewTab(detail, glass, padding, now)
                }
            }
        }
    }
}
