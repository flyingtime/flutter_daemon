package com.flutter_daemon.flutter_daemon.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.flutter_daemon.flutter_daemon.BootAutoStart
import com.flutter_daemon.flutter_daemon.service.DaemonService

/**
 * 开机自启广播接收器。
 *
 * 用户在 Dart 侧显式调用 `FlutterDaemon.enableBootAutoStart()` 后，插件把开关
 * 持久化到 [BootAutoStart]；设备开机完成时系统广播 ACTION_BOOT_COMPLETED，
 * 本接收器仅在开关打开时拉起 [DaemonService]（:daemon 进程）——Service 会转前台、
 * 检测到主进程不在后拉起应用 LAUNCHER，复用既有保活链路实现"开机自动恢复应用"。
 *
 * 说明：
 *  - ACTION_BOOT_COMPLETED 属于隐式广播豁免清单，manifest 静态注册在各 API 级别
 *    均能收到；部分定制 ROM 还要求用户在系统设置里给应用开"自启动"权限，插件无法代授。
 *  - QUICKBOOT_POWERON 为部分盒子/广告板"快速开机"的等效广播，一并兼容。
 *  - 默认关闭：只有 Dart 侧显式开启开关后这里才会做事。
 */
class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "Daemon"
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != ACTION_QUICKBOOT_POWERON) {
            return
        }
        Log.i(TAG, "BootCompletedReceiver -> onReceive, action: $action")
        if (!BootAutoStart.isEnabled(context)) {
            Log.i(TAG, "boot auto start disabled, ignore")
            return
        }

        val service = Intent(context, DaemonService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(service)
            } else {
                context.startService(service)
            }
            Log.i(TAG, "boot: :daemon service started for auto start")
        } catch (e: Exception) {
            Log.e(TAG, "boot: start :daemon service failed: ${e.message}")
        }
    }
}
