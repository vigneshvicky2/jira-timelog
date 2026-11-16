package io.github.vigneshvicky2.timelog.update

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A release on GitHub that is newer than the installed app. */
data class Release(val version: String, val notes: String, val apkUrl: String, val size: Long, val pageUrl: String)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class ReadyToInstall(val release: Release, val file: File) : UpdateState
    data class Failed(val message: String, val release: Release?) : UpdateState
}

/**
 * Over-the-air updates from this repo's GitHub Releases: check the latest release, download its APK
 * into the cache and hand it to the system installer. Also decides when to show "What's new" once.
 */
class UpdateViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("timelog", Context.MODE_PRIVATE)
    private val pkg = app.packageManager.getPackageInfo(app.packageName, 0)
    val currentVersion: String = pkg.versionName ?: "0"
    private val currentCode: Long = if (Build.VERSION.SDK_INT >= 28) pkg.longVersionCode else @Suppress("DEPRECATION") pkg.versionCode.toLong()

    var state by mutableStateOf<UpdateState>(UpdateState.Idle); private set
    /** The update prompt is visible (auto check found something, or the user asked). */
    var promptVisible by mutableStateOf(false); private set
    var whatsNew by mutableStateOf<List<ChangelogEntry>>(emptyList()); private set

    private var job: Job? = null

    init {
        // "What's new" once per update: shown when the installed version is newer than the last one seen.
        val seen = prefs.getLong("seenVersionCode", 0L)
        val wasUpdated = pkg.lastUpdateTime != pkg.firstInstallTime
        if (currentCode > seen && (seen > 0 || wasUpdated)) {
            val lastSeenName = prefs.getString("seenVersionName", null)
            whatsNew = CHANGELOG.takeWhile { lastSeenName == null || newer(it.version, lastSeenName) }
                .ifEmpty { CHANGELOG.take(1) }
        } else if (seen < currentCode) {
            markSeen() // fresh install: nothing to announce
        }
        // stale downloads from earlier versions
        File(app.cacheDir, "updates").listFiles()?.forEach { if (!it.name.contains(currentVersion)) it.delete() }
        check(userInitiated = false)
    }

    fun dismissWhatsNew() {
        whatsNew = emptyList()
        markSeen()
    }

    private fun markSeen() {
        prefs.edit().putLong("seenVersionCode", currentCode).putString("seenVersionName", currentVersion).apply()
    }

    fun check(userInitiated: Boolean = true) {
        if (state is UpdateState.Checking || state is UpdateState.Downloading) return
        if (userInitiated) promptVisible = true
        state = UpdateState.Checking
        viewModelScope.launch {
            state = try {
                val r = fetchLatest()
                when {
                    r == null || !newer(r.version, currentVersion) -> UpdateState.UpToDate
                    !userInitiated && prefs.getString("skippedVersion", null) == r.version -> UpdateState.Available(r)
                    else -> { promptVisible = true; UpdateState.Available(r) }
                }
            } catch (e: Exception) {
                if (userInitiated) UpdateState.Failed(friendly(e), null) else UpdateState.Idle
            }
        }
    }

    fun later() {
        (state as? UpdateState.Available)?.let { prefs.edit().putString("skippedVersion", it.release.version).apply() }
        promptVisible = false
    }

    fun closePrompt() {
        if (state is UpdateState.Downloading) return
        promptVisible = false
        if (state is UpdateState.UpToDate || state is UpdateState.Failed) state = UpdateState.Idle
    }

    fun download() {
        val r = when (val s = state) {
            is UpdateState.Available -> s.release
            is UpdateState.Failed -> s.release ?: return
            else -> return
        }
        job?.cancel()
        job = viewModelScope.launch {
            state = UpdateState.Downloading(r, 0f)
            try {
                val file = withContext(Dispatchers.IO) { fetchApk(r) { p -> state = UpdateState.Downloading(r, p) } }
                state = UpdateState.ReadyToInstall(r, file)
                install()
            } catch (e: Exception) {
                state = UpdateState.Failed(friendly(e), r)
            }
        }
    }

    /** Opens the system installer, or the "install unknown apps" setting the first time. */
    fun install() {
        val s = state as? UpdateState.ReadyToInstall ?: return
        val ctx = getApplication<Application>()
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", s.file)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private suspend fun fetchLatest(): Release? = withContext(Dispatchers.IO) {
        val c = (URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Stint-Android")
        }
        try {
            if (c.responseCode == 404) return@withContext null
            if (c.responseCode !in 200..299) throw IllegalStateException("GitHub returned ${c.responseCode}")
            val o = JSONObject(c.inputStream.bufferedReader().readText())
            val assets = o.optJSONArray("assets")
            val apk = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") } ?: return@withContext null
            Release(
                version = o.optString("tag_name").removePrefix("v"),
                notes = o.optString("body").trim(),
                apkUrl = apk.getString("browser_download_url"),
                size = apk.optLong("size"),
                pageUrl = o.optString("html_url"),
            )
        } finally {
            c.disconnect()
        }
    }

    private fun fetchApk(r: Release, onProgress: (Float) -> Unit): File {
        val dir = File(getApplication<Application>().cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "Stint-v${r.version}.apk")
        if (out.exists() && r.size > 0 && out.length() == r.size) return out
        var url = URL(r.apkUrl)
        var c: HttpURLConnection
        var hops = 0
        while (true) { // GitHub redirects to its CDN; follow by hand to be safe across hosts
            c = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000; readTimeout = 30_000
                setRequestProperty("User-Agent", "Stint-Android")
            }
            if (c.responseCode in 300..399 && hops++ < 5) {
                url = URL(url, c.getHeaderField("Location")); c.disconnect(); continue
            }
            break
        }
        try {
            if (c.responseCode !in 200..299) throw IllegalStateException("Download failed (${c.responseCode})")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: r.size
            val tmp = File(dir, out.name + ".part")
            c.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    var lastReport = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        read += n
                        if (total > 0 && read - lastReport > 64 * 1024) { onProgress(read.toFloat() / total); lastReport = read }
                    }
                }
            }
            onProgress(1f)
            if (!tmp.renameTo(out)) throw IllegalStateException("Couldn't save the update")
            return out
        } finally {
            c.disconnect()
        }
    }

    private fun friendly(e: Exception) = when (e) {
        is java.net.UnknownHostException -> "No internet connection."
        is java.io.IOException -> "Network problem. Try again."
        else -> e.message ?: "Something went wrong."
    }

    companion object {
        const val REPO = "vigneshvicky2/jira-timelog"

        /** True when version [a] is newer than [b] (dotted numbers, e.g. 1.10.0 > 1.9.2). */
        fun newer(a: String, b: String): Boolean {
            val x = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
            val y = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(x.size, y.size)) {
                val d = (x.getOrNull(i) ?: 0) - (y.getOrNull(i) ?: 0)
                if (d != 0) return d > 0
            }
            return false
        }
    }
}
