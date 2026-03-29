package com.xinkong.diary.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log

object DeviceKeeper {

    /**
     * 去往自启动权限/后台受限等特定品牌的管理界面
     */
    fun jumpToAutoStartUI(context: Context) {
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        Log.d("DeviceKeeper", "Manufacturer: $manufacturer")

        val intent = Intent()
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            when {
                manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                    // 华为/荣耀 的应用自启动设置页
                    intent.component = android.content.ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                }
                manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> {
                    // 小米 的自启动页
                    intent.component = android.content.ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                }
                manufacturer.contains("oppo") || manufacturer.contains("realme") -> {
                    // OPPO
                    intent.component = android.content.ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                }
                manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> {
                    // Vivo
                    intent.component = android.content.ComponentName(
                        "com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                    )
                }
                else -> {
                    // 降级：去详情页面
                    throw Exception("Unknown manufacturer")
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w("DeviceKeeper", "Could not resolve specific activity, falling back to AppInfo", e)
            fallbackToAppInfo(context)
        }
    }

    private fun fallbackToAppInfo(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.fromParts("package", context.packageName, null)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("DeviceKeeper", "Even fallback to AppInfo failed", e)
        }
    }
}