package com.flutter_daemon.flutter_daemon.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.flutter_daemon.flutter_daemon.KeepAliveChecker
import com.flutter_daemon.flutter_daemon.service.DaemonService

/**
 * 保活广播接收器，跑在 :daemon 独立进程（与 [DaemonService] 同进程）。
 *
 * 由 native daemon 进程周期性通过 `am broadcast` 触发。
 * 背景：部分 Android 7.x ROM 对"已运行 Service"重复 `am startservice` 不再派发
 * onStartCommand,Service 内的检测逻辑失去触发机会;而 broadcast 对已注册的
 * 动态 Receiver 总是有效派发,与 startservice 互为备份。
 *
 * 收到广播后执行与 [DaemonService.onStartCommand] 相同的检测:
 * 主进程不存在或应用 task 不存在时,拉起本应用 LAUNCHER。
 */
class DaemonReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "Daemon"

        /** native daemon.c 的 send_keepalive_broadcast 使用的 action 后缀 */
        const val ACTION_SUFFIX = ".DaemonService.CHECK"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "DaemonReceiver -> onReceive, action: ${intent.action}")
        if (KeepAliveChecker.needRevive(context)) {
            KeepAliveChecker.launchSelf(context)
        }
    }
}
