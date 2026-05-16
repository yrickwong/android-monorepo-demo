package com.demo.monorepo.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.demo.foundations.communicate.IAppEnv

/**
 * `:app`-side implementation of [IAppEnv] — the producer of an SPI
 * whose interface lives in `:foundations:communicate`.
 *
 * Registered into `ServiceRegistry` in [DemoApp.onCreate] so that any
 * feature/bizlib can read the app's environment information without
 * taking a hard dependency on `:app`.
 */
internal class AppEnvImpl(context: Context) : IAppEnv {

    override val appVersionName: String
    override val appVersionCode: Long
    override val isDebuggable: Boolean
    override val channel: String = "default"

    init {
        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, 0)
        appVersionName = info.versionName ?: "0.0.0"
        appVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
}
