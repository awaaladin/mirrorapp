package com.mirror.app.core.crash

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.mirror.app.BuildConfig
import com.mirror.app.data.network.NetworkConfig
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Tells mirrorcentral why the app closed, so a crash on someone's phone can be fixed without that
 * phone being plugged into a computer. Three sources:
 *  - Kotlin/Java crashes, written to a file by an uncaught-exception handler just before Android
 *    closes the app;
 *  - failures the app caught and turned into an on-screen error (recordHandled);
 *  - on Android 11+, the system's own record of how the last run ended, which also covers
 *    native (MediaPipe) crashes and the phone killing the app for memory, neither of which any
 *    in-app handler can see.
 * Reports are sent on the next launch, in the background. No photos or account details are sent:
 * only the error, the app version and the phone model.
 */
object CrashReporter {
    private const val CRASH_FILE = "last_crash.txt"
    private const val HANDLED_FILE = "handled_errors.txt"
    private const val PREFS = "crash_reporter"
    private const val LAST_EXIT_SEEN = "last_exit_seen"
    private const val MAX_TRACE = 12_000

    private var appContext: Context? = null

    fun install(context: Context) {
        val app = context.applicationContext
        appContext = app
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                File(app.filesDir, CRASH_FILE).writeText(describe(error, thread.name))
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
        Thread { sendPending(app) }.apply { name = "crash-report"; isDaemon = true }.start()
    }

    /** A failure the app recovered from (shown as an error instead of closing). Sent next launch. */
    fun recordHandled(error: Throwable) {
        val app = appContext ?: return
        try {
            val file = File(app.filesDir, HANDLED_FILE)
            if (file.length() < MAX_TRACE * 3) file.appendText(describe(error, Thread.currentThread().name) + "\n----\n")
        } catch (_: Throwable) {
        }
    }

    private fun describe(error: Throwable, thread: String): String =
        "thread=$thread\n" + error.stackTraceToString().take(MAX_TRACE)

    private fun sendPending(context: Context) {
        try {
            val reports = mutableListOf<JsonObject>()
            takeFile(context, CRASH_FILE)?.let { reports += report("crash", "Uncaught exception", it) }
            takeFile(context, HANDLED_FILE)?.let { reports += report("handled", "Recovered error", it) }
            reports += systemExitReports(context)
            if (reports.isEmpty()) return
            val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
            for (body in reports) {
                val request = Request.Builder()
                    .url(NetworkConfig.BASE_URL + "app/crash-reports")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(request).execute().close()
            }
        } catch (_: Throwable) {
            // Offline or the server is down: the system record is read again next time only if
            // it's newer, so a missed report is simply lost. That's acceptable for diagnostics.
        }
    }

    private fun takeFile(context: Context, name: String): String? {
        val file = File(context.filesDir, name)
        if (!file.exists()) return null
        val text = file.readText()
        file.delete()
        return text.ifBlank { null }
    }

    /** How earlier runs ended, per Android's own records (Android 11+), skipping ones already sent. */
    private fun systemExitReports(context: Context): List<JsonObject> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        val manager = context.getSystemService(ActivityManager::class.java) ?: return emptyList()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastSeen = prefs.getLong(LAST_EXIT_SEEN, 0L)
        val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 5)
            .filter { it.timestamp > lastSeen }
        exits.maxOfOrNull { it.timestamp }?.let { prefs.edit().putLong(LAST_EXIT_SEEN, it).apply() }
        // The first run also sends the recent history, so a crash from before this reporter existed
        // still gets reported once.
        return exits.mapNotNull { exit ->
            val kind = when (exit.reason) {
                ApplicationExitInfo.REASON_CRASH -> "crash"
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "native_crash"
                ApplicationExitInfo.REASON_LOW_MEMORY -> "low_memory"
                ApplicationExitInfo.REASON_ANR -> "anr"
                ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "init_failure"
                else -> null
            } ?: return@mapNotNull null
            // A native crash's tombstone is binary; its description already names the signal.
            val trace = if (exit.reason == ApplicationExitInfo.REASON_ANR) {
                try {
                    exit.traceInputStream?.bufferedReader()?.use { it.readText().take(MAX_TRACE) }
                } catch (_: Throwable) {
                    null
                }
            } else {
                null
            }
            report(kind, exit.description ?: "", trace ?: "", memoryKb = exit.pss)
        }
    }

    private fun report(kind: String, summary: String, trace: String, memoryKb: Long = 0): JsonObject {
        val context = appContext
        val ramMb = context?.getSystemService(ActivityManager::class.java)?.let {
            ActivityManager.MemoryInfo().also { info -> it.getMemoryInfo(info) }.totalMem / (1024 * 1024)
        } ?: 0
        return JsonObject(
            mapOf(
                "kind" to JsonPrimitive(kind),
                "summary" to JsonPrimitive(summary.take(500)),
                "trace" to JsonPrimitive(trace),
                "app_version" to JsonPrimitive(BuildConfig.VERSION_NAME),
                "version_code" to JsonPrimitive(BuildConfig.VERSION_CODE),
                "device" to JsonPrimitive("${Build.MANUFACTURER} ${Build.MODEL}"),
                "android_sdk" to JsonPrimitive(Build.VERSION.SDK_INT),
                "abis" to JsonArray(Build.SUPPORTED_ABIS.map { JsonPrimitive(it) }),
                "ram_mb" to JsonPrimitive(ramMb),
                "pss_kb" to JsonPrimitive(memoryKb)
            )
        )
    }
}
