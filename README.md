# RunWatch

An unofficial Android client for [Weights & Biases](https://wandb.ai). Use it to watch your training runs from your phone.

The official W&B mobile app is iOS-only. RunWatch covers the core monitoring features for Android:

- **Projects and runs.** Sign in with your W&B API key, switch between your personal entity and your teams, and browse projects and runs with their live state.
- **Live metric charts.** Every numeric key you log with `wandb.log()` gets a chart, with EMA smoothing (the same as the web UI), a log Y-axis, and touch-to-read values. While a run is active, charts refresh every 15 s.
- **System metrics.** GPU utilization, memory, temperature and power, plus CPU, RAM, disk and network, grouped one chart per metric with a line per GPU.
- **Console logs.** The last 1,000 lines with ANSI colors, refreshing every 5 s while the run is live.
- **Stop runs.** This sends the same graceful stop signal as the Stop button in the web UI.
- **Failure alerts.** Star a project and RunWatch checks it about every 15 minutes in the background. You get a notification when a running run crashes or fails, and optionally when one finishes.

## Install

Each push to `main` builds an APK with GitHub Actions. Open the latest run in the **Actions** tab and download the `runwatch-apk` artifact. Each `v*` tag also attaches the APK to a GitHub Release.

On your phone, open the APK and allow installs from that source when Android asks.

## Build locally

You need Android Studio (or JDK 17 plus the Android SDK). Then run:

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # JVM unit tests for parsing and chart math
./gradlew recordPaparazziDebug  # render chart screenshots to app/src/test/snapshots/images
```

## How it works

RunWatch talks directly to the W&B GraphQL API at `https://api.wandb.ai/graphql`, which is the same endpoint the `wandb` Python SDK uses. It authenticates with HTTP Basic auth, user `api` and your API key. Your key is stored in app-private storage and is excluded from backups. For Dedicated Cloud or self-hosted servers, change the API host on the sign-in screen.

| Path | Contents |
|------|----------|
| `data/WandbApi.kt` | GraphQL queries: viewer, projects, runs, sampledHistory, events, logLines, stopRun |
| `data/Parsers.kt` | Pure JSON → model parsing (unit-tested) |
| `data/ChartMath.kt` | Smoothing, min/max downsampling, axis ticks |
| `ui/` | Jetpack Compose screens and a Canvas line chart |
| `alerts/RunAlertWorker.kt` | WorkManager job behind the failure alerts |

## Notes

- Android limits background checks to about every 15 minutes, so alerts are not instant.
- The release APK is signed with the debug key so CI can produce an installable file. Set up your own signing config before publishing to Google Play.
- RunWatch is not affiliated with Weights & Biases or CoreWeave.
