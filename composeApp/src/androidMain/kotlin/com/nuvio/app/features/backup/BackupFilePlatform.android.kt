package com.nuvio.app.features.backup

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.nuvio.app.MainActivity
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

internal actual object BackupFilePlatform {
    private const val createDocumentRequestCode = 7841
    private const val openDocumentRequestCode = 7842
    private const val mimeType = "application/octet-stream"

    private var activity: MainActivity? = null
    private var pendingExportBytes: ByteArray? = null
    private var exportContinuation: ((BackupFileResult) -> Unit)? = null
    private var importContinuation: ((BackupFileReadResult) -> Unit)? = null

    fun bindActivity(activity: MainActivity) {
        this.activity = activity
    }

    fun unbindActivity(activity: MainActivity) {
        if (this.activity === activity) {
            this.activity = null
        }
    }

    actual suspend fun exportBackup(
        defaultFileName: String,
        bytes: ByteArray,
    ): BackupFileResult =
        suspendCancellableCoroutine { continuation ->
            val currentActivity = activity
            if (currentActivity == null) {
                continuation.resume(BackupFileResult.Error("Backup export is not available."))
                return@suspendCancellableCoroutine
            }
            pendingExportBytes = bytes
            exportContinuation = { result -> continuation.resume(result) }
            continuation.invokeOnCancellation {
                pendingExportBytes = null
                exportContinuation = null
            }
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = mimeType
                putExtra(Intent.EXTRA_TITLE, defaultFileName)
            }
            currentActivity.startActivityForResult(intent, createDocumentRequestCode)
        }

    actual suspend fun importBackup(): BackupFileReadResult =
        suspendCancellableCoroutine { continuation ->
            val currentActivity = activity
            if (currentActivity == null) {
                continuation.resume(BackupFileReadResult.Error("Backup import is not available."))
                return@suspendCancellableCoroutine
            }
            importContinuation = { result -> continuation.resume(result) }
            continuation.invokeOnCancellation {
                importContinuation = null
            }
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            currentActivity.startActivityForResult(intent, openDocumentRequestCode)
        }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        return when (requestCode) {
            createDocumentRequestCode -> {
                handleCreateDocumentResult(resultCode, data?.data)
                true
            }
            openDocumentRequestCode -> {
                handleOpenDocumentResult(resultCode, data?.data)
                true
            }
            else -> false
        }
    }

    private fun handleCreateDocumentResult(resultCode: Int, uri: Uri?) {
        val continuation = exportContinuation
        exportContinuation = null
        val bytes = pendingExportBytes
        pendingExportBytes = null
        if (resultCode != Activity.RESULT_OK || uri == null || bytes == null) {
            continuation?.invoke(BackupFileResult.Cancelled)
            return
        }
        val result = runCatching {
            activity?.contentResolver?.openOutputStream(uri)?.use { stream ->
                stream.write(bytes)
                stream.flush()
            } ?: error("Unable to open backup destination.")
            BackupFileResult.Success
        }.getOrElse { error ->
            BackupFileResult.Error(error.message ?: "Backup export failed.")
        }
        continuation?.invoke(result)
    }

    private fun handleOpenDocumentResult(resultCode: Int, uri: Uri?) {
        val continuation = importContinuation
        importContinuation = null
        if (resultCode != Activity.RESULT_OK || uri == null) {
            continuation?.invoke(BackupFileReadResult.Cancelled)
            return
        }
        val result = runCatching {
            val bytes = activity?.contentResolver?.openInputStream(uri)?.use { stream ->
                stream.readBytes()
            } ?: error("Unable to read backup file.")
            BackupFileReadResult.Success(bytes)
        }.getOrElse { error ->
            BackupFileReadResult.Error(error.message ?: "Backup import failed.")
        }
        continuation?.invoke(result)
    }
}

