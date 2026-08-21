package com.flutter_daemon.flutter_daemon

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import com.flutter_daemon.flutter_daemon.daemon.Daemon
import com.flutter_daemon.flutter_daemon.service.DaemonService
import java.io.File

/** FlutterDaemonPlugin：Dart 侧 MethodChannel("flutter_daemon") 的 Android 实现。 */
class FlutterDaemonPlugin : FlutterPlugin, MethodCallHandler {
    private val tag = "FlutterDaemonPlugin"

    private lateinit var channel: MethodChannel
    private var applicationContext: Context? = null

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_daemon")
        channel.setMethodCallHandler(this)
        applicationContext = flutterPluginBinding.applicationContext
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        val context = applicationContext
        if (context == null) {
            result.error("no_context", "application context is null", null)
            return
        }
        when (call.method) {
            "start" -> {
                val interval = (call.argument<Int>("intervalSeconds") ?: 3)
                // 与原版 InformationCore_Flutter 一致：先直接 startService 把 :daemon
                // Service 拉起，保证保活即时生效，不必等到 native daemon 的首个 3s 周期。
                startDaemonService(context)
                // 再 fork native daemon 子进程做周期性兜底拉起。
                Daemon.run(context, interval)
                Log.i(tag, "start daemon, interval=$interval")
                result.success(true)
            }
            "stop" -> {
                val killed = killDaemonProcesses()
                result.success(killed)
            }
            "isRunning" -> {
                result.success(isDaemonRunning())
            }
            else -> result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        applicationContext = null
    }

    /**
     * 直接拉起 [DaemonService]（:daemon 独立进程）。
     *
     * 与原版 InformationCore_Flutter/app 的 `startService(new Intent(this, DaemonService.class))`
     * 行为一致：保证调用 start() 后 :daemon Service 立即就绪，不必等 native daemon 的首个
     * `interval`（默认 3s）周期。Service 起来后会在 [DaemonService.onCreate] 里再次
     * 启动 native daemon，形成"Service ↔ daemon"双向互拉。
     *
     * 注意：API 26+ 禁止后台应用直接 startService，故在后台调用时降级为只依赖 native daemon
     * 周期拉起（Daemon.run）；前台调用（应用启动时点击）不受影响。
     */
    private fun startDaemonService(context: Context) {
        val intent = Intent(context, DaemonService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.i(tag, "startDaemonService: :daemon service started")
        } catch (e: Exception) {
            // 后台启动前台 Service 受限（API 31+）或后台 startService 受限（API 26+），
            // 此处降级：仅依赖 native daemon 周期拉起，不影响保活主链路。
            Log.w(tag, "startDaemonService failed (will rely on native daemon): ${e.message}")
        }
    }

    /**
     * 判断保活是否处于激活状态：只要 daemon 子进程存活，**或**其托管的
     * [DaemonService]（独立 :daemon 进程）正在运行，即视为保活生效。
     *
     * 仅按 native daemon 进程判断会漏掉一类常见场景：daemon 进程自身在 app 被杀后
     * 可能被系统/省电策略回收，但 :daemon 进程内的 [DaemonService] 仍在跑、或刚被
     * daemon 拉起，此时保活其实已经生效，仅靠 daemon 进程 PID 会误报“未启动”。
     * 因此这里把两者并集判断。
     */
    private fun isDaemonRunning(): Boolean {
        if (findDaemonPids().isNotEmpty()) return true
        return findServicePids().isNotEmpty()
    }

    /**
     * 读取 /proc/<pid>/cmdline 的第一段参数（argv[0]）。
     *
     * cmdline 以 NUL(`\0`) 分隔各参数，第一段即 argv[0]，不能按空格切分——
     * 之前按空格拆分会把含路径的 argv[0] 误判，导致 isRunning 永远查不到 daemon。
     */
    private fun readArgv0(cmdline: File): String? {
        if (!cmdline.exists()) return null
        val bytes = try {
            cmdline.readBytes()
        } catch (_: Exception) {
            return null
        }
        if (bytes.isEmpty()) return null
        val nul = bytes.indexOf(0.toByte())
        val raw = if (nul >= 0) bytes.copyOf(nul) else bytes
        return String(raw, Charsets.UTF_8)
    }

    /**
     * 遍历 /proc 下各 PID 目录的 cmdline，查找名为 daemon 的进程 PID。
     * 逻辑等价于 native common.c 中的 find_pid_by_name，这里用 Kotlin 复刻，
     * 用于在 Dart 侧提供 stop / isRunning 能力。
     */
    private fun findDaemonPids(): List<Int> {
        val pids = mutableListOf<Int>()
        val files = File("/proc").listFiles() ?: return pids
        for (f in files) {
            val name = f.name
            if (name.isEmpty() || !name[0].isDigit()) continue
            val argv0 = readArgv0(File(f, "cmdline")) ?: continue
            // daemon 由 Daemon.start 以绝对路径启动，argv[0] 形如
            // /data/user/0/<pkg>/app_bin/daemon，故取其文件名；再按原 native
            // find_pid_by_name 的做法截到首个 '-'，兼容 daemon-<pid> 之类命名。
            val processName = argv0
                .substringAfterLast(File.separatorChar)
                .substringBefore('-')
            if (processName == "daemon") {
                pids.add(name.toInt())
            }
        }
        return pids
    }

    /** 查找 :daemon 进程（DaemonService 所在进程）PID。cmdline 形如 <pkg>:daemon。 */
    private fun findServicePids(): List<Int> {
        val pids = mutableListOf<Int>()
        val files = File("/proc").listFiles() ?: return pids
        val suffix = ":daemon"
        for (f in files) {
            val name = f.name
            if (name.isEmpty() || !name[0].isDigit()) continue
            val argv0 = readArgv0(File(f, "cmdline")) ?: continue
            if (argv0.endsWith(suffix)) pids.add(name.toInt())
        }
        return pids
    }

    /** 杀掉所有 daemon 进程；返回是否成功 kill 至少一个。 */
    private fun killDaemonProcesses(): Boolean {
        val pids = findDaemonPids()
        var killed = false
        for (pid in pids) {
            if (pid <= 1) continue
            android.os.Process.killProcess(pid)
            Log.d(tag, "kill daemon pid=$pid")
            killed = true
        }
        return killed
    }
}
