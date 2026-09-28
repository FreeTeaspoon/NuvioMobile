package com.nuvio.app.features.settings

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal actual object CrashDiagnosticsPlatform {
    private const val TAG = "CrashDiagnostics"
    private const val preferencesName = "nuvio_crash_diagnostics"
    private const val lastExitTimestampKey = "last_exit_timestamp"
    private const val reportName = "last-exit.zip"
    private const val maxTraceBytes = 2 * 1024 * 1024
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var appContext: Context? = null

    actual val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    actual val hasReport: Boolean
        get() = appContext?.let(::reportFile)?.isFile == true

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (isSupported) {
            scope.launch {
                runCatching { captureLatestExit(context.applicationContext) }
                    .onFailure { Log.w(TAG, "Could not capture the previous process exit", it) }
            }
        }
    }

    actual fun shareLatestReport() {
        val context = appContext ?: return
        val report = reportFile(context).takeIf(File::isFile) ?: return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", report)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun reportFile(context: Context): File =
        File(context.filesDir, "diagnostics/$reportName")

    private fun captureLatestExit(context: Context) {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return
        val exit = manager.getHistoricalProcessExitReasons(context.packageName, 0, 25)
            .firstOrNull { info ->
                info.processName == context.packageName &&
                    info.reason in setOf(
                        ApplicationExitInfo.REASON_CRASH,
                        ApplicationExitInfo.REASON_CRASH_NATIVE,
                        ApplicationExitInfo.REASON_ANR,
                    )
            } ?: return
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        if (exit.timestamp <= preferences.getLong(lastExitTimestampKey, 0L)) return

        val report = reportFile(context)
        report.parentFile?.mkdirs()
        val pendingReport = File(report.parentFile, "$reportName.tmp")
        ZipOutputStream(pendingReport.outputStream()).use { archive ->
            archive.putNextEntry(ZipEntry("exit-summary.txt"))
            archive.write(
                buildString {
                    appendLine("timeMs=${exit.timestamp}")
                    appendLine("reason=${exit.reason}")
                    appendLine("status=${exit.status}")
                    appendLine("importance=${exit.importance}")
                    appendLine("description=${exit.description.orEmpty()}")
                    appendLine("process=${exit.processName}")
                }.toByteArray(),
            )
            archive.closeEntry()
            exit.traceInputStream?.use { trace ->
                archive.putNextEntry(ZipEntry("exit-trace.bin"))
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var remaining = maxTraceBytes
                while (remaining > 0) {
                    val count = trace.read(buffer, 0, minOf(buffer.size, remaining))
                    if (count < 0) break
                    archive.write(buffer, 0, count)
                    remaining -= count
                }
                archive.closeEntry()
            }
        }
        if (!pendingReport.renameTo(report)) {
            pendingReport.copyTo(report, overwrite = true)
            pendingReport.delete()
        }
        preferences.edit().putLong(lastExitTimestampKey, exit.timestamp).apply()
    }
}
