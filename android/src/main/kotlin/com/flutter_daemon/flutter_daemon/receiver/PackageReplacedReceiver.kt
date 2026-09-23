package com.flutter_daemon.flutter_daemon.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.flutter_daemon.flutter_daemon.KeepAliveChecker
import com.flutter_daemon.flutter_daemon.service.DaemonService

/**
 * 覆盖安装后自启动接收器。
 *
 * 应用被**覆盖安装**（设备上已有旧版本，`adb install -r xx.apk` 或应用商店更新）时，
 * 系统会向本包发送 [Intent.ACTION_MY_PACKAGE_REPLACED] 广播。本接收器收到后拉起
 * [DaemonService]（:daemon 进程），由 Service 复用既有保活链路检测主进程不在后拉起
 * 应用 LAUNCHER，实现"更新完成即自动启动"。
 *
 * 设计说明：
 *  - 跑在主进程（**不声明** android:process），与 [BootCompletedReceiver] 同构。
 *  - **无条件启动**：不判断任何开关——覆盖安装即拉起，符合"装完就要跑起来"的预期。
 *  - 走 Service 链路而非直接 startActivity：覆盖安装后本进程属后台，Android 10+ 的
 *    后台启动 Activity（BAL）限制可能拦截直接 startActivity；先起前台 Service 更稳，
 *    且顺带把保活链立起来。Service 拉起失败时才在 catch 中兜底直接拉起界面。
 *  - 该广播为系统受保护广播，**无需任何权限**，静态注册即可收到。
 *  - 仅覆盖"覆盖安装"场景；**全新安装**（首次装到无本应用的设备）系统不会向本包
 *    发送可用广播，无法自启动，属平台限制。
 */
class PackageReplacedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "Daemon"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Log.i(TAG, "PackageReplacedReceiver -> onReceive (app updated), auto start")

        val service = Intent(context, DaemonService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(service)
            } else {
                context.startService(service)
            }
            Log.i(TAG, "package replaced: :daemon service started for auto start")
        } catch (e: Exception) {
            // 后台启动受限等情况下 Service 起不来，兜底直接拉起应用界面。
            Log.e(TAG, "package replaced: start :daemon service failed: ${e.message}")
            KeepAliveChecker.launchSelf(context)
        }
    }
}
