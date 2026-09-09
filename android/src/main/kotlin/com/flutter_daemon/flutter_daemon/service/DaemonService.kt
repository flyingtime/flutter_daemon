package com.flutter_daemon.flutter_daemon.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.flutter_daemon.flutter_daemon.KeepAliveChecker
import com.flutter_daemon.flutter_daemon.daemon.Daemon

/**
 * 保活 Service，跑在 :daemon 独立进程。
 *
 * 由 native daemon 进程通过 `am startservice` 拉起；本 Service 被拉起后：
 *  1. （API 26+）转前台 Service 并展示常驻通知，规避后台 Service 限制；
 *  2. 通过 [android.content.pm.PackageManager.getLaunchIntentForPackage] 拉起**自身**
 *     应用的 LAUNCHER activity，实现应用被杀后自恢复。
 *
 * 相比原 InformationCore_Flutter/app 中的 DaemonService：
 *  - 去掉硬编码包名 `com.iotsk.showinformations`，自动拉起 [getPackageName]；
 *  - 去掉已废弃失效的 `getRunningTasks` + GET_TASKS 权限判断逻辑。
 */
class DaemonService : Service() {

    companion object {
        private const val TAG = "Daemon"
        private const val CHANNEL_ID = "flutter_daemon_keepalive"
        private const val CHANNEL_NAME = "保活"
        private const val NOTIFICATION_ID = 0xD001

        /** watchdog 巡检间隔(毫秒) */
        private const val WATCHDOG_INTERVAL_MS = 5000L
    }

    /** native daemon 巡检器:daemon 被系统清理后由此重新拉起 */
    private val watchdog = Handler(Looper.getMainLooper())

    private val watchdogTask = object : Runnable {
        override fun run() {
            if (!Daemon.isRunning(packageName)) {
                Log.w(TAG, "watchdog: native daemon missing, restart it")
                Daemon.run(applicationContext, Daemon.INTERVAL_DELAY)
            }
            watchdog.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    private fun startWatchdog() {
        watchdog.postDelayed(watchdogTask, WATCHDOG_INTERVAL_MS)
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "DaemonService -> onCreate, Thread ID: ${Thread.currentThread().id}")
        // 再次启动 daemon 进程，保证 daemon 始终存活（daemon 内部会杀掉旧的 daemon 实例）
        Daemon.run(applicationContext, Daemon.INTERVAL_DELAY)
        ensureForeground()
        // 周期巡检 native daemon:部分 ROM(如 ZC-328E 的 GuardService)会清理同 uid 的
        // 孤儿 native 进程,daemon 被清后 am 通路全断。Service 是 Android 组件且有前台
        // 身份,不易被清,由它负责把 daemon 重新拉起,形成双活。
        startWatchdog()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "DaemonService -> onStartCommand, Thread ID: ${Thread.currentThread().id}")
        if (KeepAliveChecker.needRevive(this)) {
            KeepAliveChecker.launchSelf(this)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        Log.i(TAG, "DaemonService -> onDestroy, Thread ID: ${Thread.currentThread().id}")
        watchdog.removeCallbacks(watchdogTask)
        super.onDestroy()
    }

    /**
     * 转前台 Service 并展示常驻通知。
     *
     * **所有 API 都必须调用 [startForeground]**：前台 Service 优先级更高，被系统
     * 低内存/后台清理回收的概率更低，是这套保活机制能"扛普通杀"的关键一环。
     *
     * - API 26+：必须先建 [NotificationChannel]，且 [Notification.Builder] 要带 channelId，
     *   否则 startForeground 会静默失败或抛异常。
     * - API < 26：用无 channelId 的 [Notification.Builder]（该构造在 API 26 起废弃，
     *   但低版本只能这样用）。
     *
     * 之前的实现把整个逻辑包在 `SDK_INT >= O` 里，导致 Android 7.x（API 25）及以下
     * 完全不调 startForeground，Service 一直是后台 Service——既无常驻通知，优先级也低，
     * 被系统回收的概率明显更高。
     */
    private fun ensureForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    setShowBadge(false)
                    description = "保持应用运行"
                }
                manager.createNotificationChannel(channel)
            }
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val notification = builder
            .setContentTitle(CHANNEL_NAME)
            .setContentText("保活服务运行中")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }
}
