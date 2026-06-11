package com.nuvio.app.features.backup

internal actual object BackupFilePlatform {
    actual suspend fun exportBackup(defaultFileName: String, bytes: ByteArray): BackupFileResult =
        BackupFileResult.Error("Backup export is not available on desktop yet.")

    actual suspend fun importBackup(): BackupFileReadResult =
        BackupFileReadResult.Error("Backup import is not available on desktop yet.")
}
