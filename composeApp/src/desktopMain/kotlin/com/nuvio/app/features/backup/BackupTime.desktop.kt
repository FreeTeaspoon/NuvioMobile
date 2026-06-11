package com.nuvio.app.features.backup

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()

internal actual fun currentDateForFileName(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
