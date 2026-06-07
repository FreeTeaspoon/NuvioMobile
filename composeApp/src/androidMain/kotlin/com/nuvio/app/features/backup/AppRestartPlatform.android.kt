package com.nuvio.app.features.backup

import android.content.Context
import android.content.Intent
import kotlin.system.exitProcess

internal actual object AppRestartPlatform {
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    actual fun restartApp() {
        val context = appContext ?: return
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, com.nuvio.app.MainActivity::class.java)
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launchIntent)
        exitProcess(0)
    }
}
