package com.flutter_daemon.flutter_daemon

import android.content.Context

/**
 * 开机自启开关的持久化存储。
 *
 * 用应用私有 SharedPreferences 保存用户显式设置的开关状态；MethodChannel 侧
 * （主进程）与 [com.flutter_daemon.flutter_daemon.receiver.BootCompletedReceiver]
 * （开机时由系统拉起，同主进程）读写同一份，跨重启一致。
 *
 * 默认关闭：用户不调用 [FlutterDaemon.enableBootAutoStart] 就不会开机自启。
 */
object BootAutoStart {
    private const val PREFS = "flutter_daemon"
    private const val KEY = "boot_auto_start_enabled"

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY, enabled)
            .apply()
    }

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY, false)
    }
}
