package com.flutter_daemon.flutter_daemon.daemon

import android.content.Context
import android.util.Log
import com.flutter_daemon.flutter_daemon.service.DaemonService
import java.io.File
import java.io.IOException

/**
 * Daemon：保活守护进程的启动入口。
 *
 * 迁自 com.coolerfall.daemon.Daemon（原作者 Vincent Cheung）。
 *
 * 机制：开线程将 native daemon 二进制从 assets 释放到私有目录并 chmod 0755，
 * 再以 `daemon -p <包名> -s <DaemonService 全名> -t <间隔秒>` 执行。
 * native daemon（见 android/src/main/jni/daemon/daemon.c）会 fork 出脱离 app 进程树
 * （setsid）的子进程，周期性 `am startservice` 拉起 [DaemonService]。
 */
internal object Daemon {
    private const val TAG = "Daemon"

    private const val BIN_DIR_NAME = "bin"
    private const val DAEMON_BIN_NAME = "daemon"

    const val INTERVAL_ONE_MINUTE = 60
    const val INTERVAL_ONE_HOUR = 3600

    /** 启动 daemon 二进制，参数：包名、daemon service 类、检查间隔秒数。 */
    private fun start(context: Context, daemonClazzName: Class<*>, interval: Int) {
        val cmd = context.getDir(BIN_DIR_NAME, Context.MODE_PRIVATE)
            .absolutePath + File.separator + DAEMON_BIN_NAME

        val cmdBuilder = StringBuilder()
        cmdBuilder.append(cmd)
        cmdBuilder.append(" -p ")
        cmdBuilder.append(context.packageName)
        cmdBuilder.append(" -s ")
        cmdBuilder.append(daemonClazzName.name)
        cmdBuilder.append(" -t ")
        cmdBuilder.append(interval)

        Log.i(TAG, "start, Thread ID: ${Thread.currentThread().id} $cmdBuilder")

        try {
            Runtime.getRuntime().exec(cmdBuilder.toString()).waitFor()
        } catch (e: IOException) {
            Log.e(TAG, "start daemon error: ${e.message}")
        } catch (e: InterruptedException) {
            Log.e(TAG, "start daemon interrupted: ${e.message}")
        }
    }

    /**
     * 启动保活。在后台线程安装二进制并执行 daemon 进程。
     *
     * @param context  上下文（建议用 application context，避免持有 Activity 引起泄漏）
     * @param interval 周期检查间隔秒数，daemon.c 内置下限为 120（小于 120 会被提升到 120）
     */
    fun run(context: Context, interval: Int = INTERVAL_ONE_MINUTE * 2) {
        Thread {
            Command.install(context, BIN_DIR_NAME, DAEMON_BIN_NAME)
            start(context, DaemonService::class.java, interval)
        }.start()
    }
}
