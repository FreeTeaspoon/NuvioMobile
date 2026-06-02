package com.nuvio.app.features.backup

internal expect object BackupFilePlatform {
    suspend fun exportBackup(defaultFileName: String, bytes: ByteArray): BackupFileResult
    suspend fun importBackup(): BackupFileReadResult
}

