package com.peiyu.reader.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.Display
import splitties.init.appCtx
import splitties.systemservices.displayManager
import splitties.systemservices.powerManager


@Suppress("unused")
object SystemUtils {

    @SuppressLint("ObsoleteSdkInt")
    fun ignoreBatteryOptimization(activity: Activity) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) return

        val hasIgnored = powerManager.isIgnoringBatteryOptimizations(activity.packageName)
        //  判断当前APP是否有加入电池优化的白名单，如果没有，弹出加入电池优化的白名单的设置对话框�?        if (!hasIgnored) {
            try {
                @SuppressLint("BatteryLife")
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:" + activity.packageName)
                activity.startActivity(intent)
            } catch (ignored: Throwable) {
            }

        }
    }

    fun isScreenOn(): Boolean {
        return displayManager.displays.filterNotNull().any {
            it.state != Display.STATE_OFF
        }
    }

    /**
     * 屏幕像素宽度
     */
    val screenWidthPx by lazy {
        appCtx.resources.displayMetrics.widthPixels
    }

    /**
     * 屏幕像素高度
     */
    val screenHeightPx by lazy {
        appCtx.resources.displayMetrics.heightPixels
    }
}
