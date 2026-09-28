package com.nuvio.app.features.settings

internal actual object CrashDiagnosticsPlatform {
    actual val isSupported: Boolean = false
    actual val hasReport: Boolean = false
    actual fun shareLatestReport() = Unit
}
