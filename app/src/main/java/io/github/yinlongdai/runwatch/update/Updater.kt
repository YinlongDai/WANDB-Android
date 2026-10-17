package io.github.yinlongdai.runwatch.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import io.github.yinlongdai.runwatch.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The newest published release. */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val notes: String,
)

/** Pure parsing of the GitHub "latest release" payload, kept free of Android APIs for unit tests. */
object ReleaseParser {

    /** Tags look like "v0.2.17"; the last number is the CI run number, which is also the versionCode. */
    fun versionCodeOf(tag: String): Int? = tag.substringAfterLast('.').toIntOrNull()

    fun parse(json: JSONObject): UpdateInfo? {
        val tag = json.optString("tag_name")
        val code = versionCodeOf(tag) ?: return null
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null
        val assets = json.optJSONArray("assets") ?: return null
        val apk = (0 until assets.length())
            .mapNotNull { assets.optJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk") }
            ?.optString("browser_download_url")
            ?.takeIf { it.startsWith("https://") }
            ?: return null
        return UpdateInfo(
            versionName = tag.removePrefix("v"),
            versionCode = code,
            apkUrl = apk,
            notes = json.optString("body").trim(),
        )
    }
}

/**
 * Checks this app's GitHub Releases for a newer build, downloads it and hands
 * it to Android's package installer. The user confirms every install.
 *
 * Debug builds never offer updates: they are signed with a different key than
 * releases, so Android would refuse to install over them.
 */
object Updater {

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val info: UpdateInfo) : State
        data class Downloading(val info: UpdateInfo, val progress: Float) : State
        data class Failed(val message: String) : State
    }

    /** App-wide so the banner and Settings agree, and a check runs once per launch. */
    var state by mutableStateOf<State>(State.Idle)
        private set

    val enabled: Boolean get() = !BuildConfig.DEBUG

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Checks once per process unless [force] is set (the Settings button). */
    suspend fun check(force: Boolean = false) {
        if (!enabled) return
        if (!force && state != State.Idle) return
        state = State.Checking
        state = try {
            val info = latest()
            if (info != null && info.versionCode > BuildConfig.VERSION_CODE) State.Available(info) else State.UpToDate
        } catch (e: IOException) {
            State.Failed("Couldn't check for updates: ${e.message ?: "network error"}")
        }
    }

    private suspend fun latest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "RunWatch-Android")
            .build()
        http.newCall(request).execute().use { resp ->
            // 404: no releases yet, or the repo isn't public.
            if (resp.code == 404) return@withContext null
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            ReleaseParser.parse(JSONObject(resp.body?.string().orEmpty()))
        }
    }

    /** Downloads the APK into the cache and opens the system installer. */
    suspend fun downloadAndInstall(context: Context, info: UpdateInfo) {
        val app = context.applicationContext
        if (!app.packageManager.canRequestPackageInstalls()) {
            // Android asks once per installer app; the user comes back and taps Update again.
            app.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        state = State.Downloading(info, 0f)
        val file = try {
            download(app, info)
        } catch (e: IOException) {
            state = State.Failed("Download failed: ${e.message ?: "network error"}")
            return
        }
        state = State.Available(info) // so the banner still offers Update if the install is cancelled
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", file)
        app.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private suspend fun download(context: Context, info: UpdateInfo): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // keep only the newest download
        val out = File(dir, "runwatch-${info.versionName}.apk")
        val request = Request.Builder().url(info.apkUrl).header("User-Agent", "RunWatch-Android").build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("empty response")
            val total = body.contentLength().takeIf { it > 0 }
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        read += n
                        if (total != null) state = State.Downloading(info, read.toFloat() / total)
                    }
                }
            }
        }
        out
    }
}
