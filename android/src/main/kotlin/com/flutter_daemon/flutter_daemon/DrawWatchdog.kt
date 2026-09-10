package com.flutter_daemon.flutter_daemon

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver

/**
 * 首帧绘制看门狗：自愈「Activity resumed 但窗口永远画不出第一帧」的白屏卡死。
 *
 * 现象（ZC-328E / Android 7.1.2 实测）：从后台 :daemon 进程 startActivity 拉回
 * LAUNCHER 时偶发主线程遍历卡死——Activity 显示 resumed、有输入焦点，但
 * ViewRootImpl 的 mTraversalScheduled 挂着一个永不执行的遍历（主线程消息队列
 * 被滞留的同步屏障阻塞），窗口 Surface 停在 DRAW_PENDING，屏幕上只剩系统的
 * Starting Window（白屏）。此时 AMS 侧一切「健康」，KeepAliveChecker 的
 * 进程/前台判据全部通过，保活巡检不会再拉起，白屏永久挂住。
 *
 * 自愈机制：Activity resume 后在 decorView 上挂 OnPreDrawListener，若
 * [TIMEOUT_MS] 内没有任何一次绘制回调真正执行（正常冷启动首帧约 4s，阈值
 * 留了一倍余量），说明绘制管线卡死，直接 kill 主进程。保活链路（native daemon
 * 每 3~10s 巡检）会随即冷启动拉回——该路径实测可靠（冷启动恢复正常）。
 *
 * 实现细节：
 * - 监控消息发在独立 HandlerThread 上，不能用主线程 Handler：卡死现场主线程
 *   消息队列常被同步屏障挡住，普通消息永远得不到执行，超时将永不触发。
 * - 仅监控 **resumed** 状态：onActivityPaused 即解除监控。被其他应用/Activity
 *   覆盖时本应用没有绘制机会，属正常静默，不能误杀；而白屏卡死现场 Activity
 *   永远停在 resumed，不受影响。
 * - resume 时主动对 decorView 调一次 invalidate() 制造绘制机会：兜住「无任何
 *   绘制需求」导致的静默。若 invalidate 后绘制回调仍不来，才是真的卡死。
 * - [enabled] 默认 false，仅在主进程调用过 FlutterDaemon.enable()（保活已启动、
 *   有 daemon 兜底可拉回）后才生效；否则 kill 进程后无人恢复。
 */
internal object DrawWatchdog {
    private const val TAG = "DrawWatchdog"

    /** 首帧超时阈值。正常冷启动到首帧 ~4s（ZC-328E 实测 Displayed +4s6ms），取 2 倍余量。 */
    private const val TIMEOUT_MS = 10_000L

    /**
     * 看门狗是否生效。由插件在 enable()（启动保活）时置 true——保证 kill 进程后
     * 一定有 native daemon 负责冷启动拉回，避免「杀了没人救」。
     */
    @Volatile
    var enabled: Boolean = false

    /** 监控专用线程：主线程卡死时其消息永远不执行，超时检查必须在别的线程跑。 */
    private val watchdogThread by lazy {
        HandlerThread("DrawWatchdog").apply { start() }
    }
    private val watchdogHandler by lazy { Handler(watchdogThread.looper) }

    /** 当前待判定的回调：resume 挂上、首帧绘制或 pause 摘除。看门狗线程会无锁读取。 */
    @Volatile
    private var pending: WatchdogDrawListener? = null

    /** 注册以来最近一次 resumed 的 Activity（供 enable() 晚于 resume 时补挂）。 */
    private var resumedActivity: Activity? = null

    /**
     * 注册生命周期回调，开始监控。在插件 onAttachedToEngine（主进程）时调用一次；
     * :daemon 进程没有 Flutter engine，天然不会走到这里。
     */
    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(LifecycleWatcher)
    }

    /**
     * 保活已启用（daemon 就绪）后调用：激活看门狗，并对「enable 之前已 resume、
     * 但至今未画过首帧」的 Activity 补挂监控——enable 常在首页 Dart 首帧之后甚至
     * 按钮触发时才调用，生命周期回调不会再来，不补挂则当前白屏无人监管。
     */
    fun onEnabled() {
        synchronized(this) {
            val activity = resumedActivity ?: return
            // 已画过首帧（或有监控在身）则无需补挂；否则重新 arm 覆盖残留状态。
            if (pending != null) return
            armLocked(activity)
        }
    }

    private object LifecycleWatcher : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            synchronized(DrawWatchdog) { DrawWatchdog.resumedActivity = activity }
            arm(activity)
        }
        override fun onActivityPaused(activity: Activity) {
            // 覆盖/切走时无绘制机会属正常静默，解除监控防止误杀。
            disarm(activity)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {
            synchronized(DrawWatchdog) {
                if (DrawWatchdog.resumedActivity === activity) {
                    DrawWatchdog.resumedActivity = null
                }
            }
        }
    }

    /** 挂上首帧监控；已有监控则先解除（resume 切换 / 快速往返）。 */
    private fun arm(activity: Activity) {
        synchronized(this) {
            armLocked(activity)
        }
    }

    /** 须持锁调用。 */
    private fun armLocked(activity: Activity) {
        disarmLocked(null)
        if (!enabled) return
        val decor = activity.window?.decorView ?: return
        val listener = WatchdogDrawListener(activity, decor)
        pending = listener
        decor.viewTreeObserver.addOnPreDrawListener(listener)
        // 主动制造一次绘制需求：兜住「无任何绘制需求」的静默 resume。
        // 若管线健康，invalidate 会引来 onPreDraw 并解除监控；若卡死，超时杀进程。
        decor.post { decor.invalidate() }
        watchdogHandler.postDelayed(listener, TIMEOUT_MS)
        Log.d(TAG, "armed for ${activity.localClassName}")
    }

    /** 仅当当前监控的正是 [activity] 时解除（销毁/暂停别的 Activity 不影响它）。 */
    private fun disarm(activity: Activity) {
        synchronized(this) { disarmLocked(activity) }
    }

    /** [activity] 为 null 表示无条件解除（arm 前置清理）。 */
    private fun disarmLocked(activity: Activity?) {
        val listener = pending ?: return
        if (activity != null && listener.activity !== activity) return
        watchdogHandler.removeCallbacks(listener)
        val observer = listener.decor.viewTreeObserver
        if (observer.isAlive) {
            observer.removeOnPreDrawListener(listener)
        }
        pending = null
        Log.d(TAG, "disarmed")
    }

    /**
     * 首帧判定回调：既是 OnPreDrawListener（绘制到达即解除），也是
     * 超时任务（postDelayed 到点执行即判定卡死、杀进程）。
     */
    private class WatchdogDrawListener(val activity: Activity, val decor: View) :
        ViewTreeObserver.OnPreDrawListener, Runnable {

        /** 已确认绘制完成/已解除，Runnable 不再生效。 */
        @Volatile
        private var settled = false

        /** 绘制遍历发生：管线存活，解除监控。返回 true 放行本次绘制。 */
        override fun onPreDraw(): Boolean {
            settle()
            return true
        }

        /** 超时仍未绘制：判定为绘制管线卡死，杀进程交给 daemon 冷启动恢复。 */
        override fun run() {
            if (!settled && pending === this) {
                Log.e(
                    TAG,
                    "no first frame within ${TIMEOUT_MS}ms after resume, " +
                        "drawing pipeline is stuck; kill main process for daemon revive"
                )
                // 置位防并发重复 kill；进程消亡后其余状态无需清理。
                settled = true
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }

        private fun settle() {
            settled = true
            synchronized(DrawWatchdog) {
                if (pending === this) {
                    watchdogHandler.removeCallbacks(this)
                    pending = null
                    Log.d(TAG, "first frame observed, watchdog settled")
                }
            }
        }
    }
}
