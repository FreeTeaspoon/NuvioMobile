package com.nuvio.app.features.settings

internal expect object CrashDiagnosticsPlatform {
    val isSupported: Boolean
    val hasReport: Boolean
    fun shareLatestReport()
}
