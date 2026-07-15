package com.nuvio.app.features.downloads

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate

internal actual object DownloadsClock {
    @OptIn(ExperimentalForeignApi::class)
    actual fun nowEpochMs(): Long = (NSDate().timeIntervalSince1970 * 1_000.0).toLong()
}
