import 'flutter_daemon_platform_interface.dart';

/// Android 进程保活插件入口。
///
/// 封装自 com.coolerfall.daemon 原生库，通过 fork 出独立 daemon 子进程，
/// 周期性拉起 [DaemonService] 及应用 LAUNCHER，实现应用被杀后自恢复。
///
/// 典型用法：
/// ```dart
/// await FlutterDaemon.start(); // 默认 120 秒检查一次
/// ```
///
/// 注意：保活在新型 Android（API 26+ 后台 Service 限制、API 31+ 前台 Service
/// 启动限制、各厂商激进省电策略）上不可靠，详见 README「兼容性风险」。
class FlutterDaemon {
  /// 启动保活守护进程。
  ///
  /// [intervalSeconds] 为 daemon 周期性检查间隔，最小 120 秒（daemon.c 内置下限）。
  static Future<bool> start({int intervalSeconds = 120}) {
    return FlutterDaemonPlatform.instance.start(intervalSeconds: intervalSeconds);
  }

  /// 停止保活，终止 daemon 子进程。
  static Future<bool> stop() {
    return FlutterDaemonPlatform.instance.stop();
  }

  /// 保活是否处于激活状态。
  ///
  /// 只要 native daemon 子进程仍在运行，**或**其托管的 `DaemonService`
  /// （独立 `:daemon` 进程）仍在运行，即返回 true。两者任一存活都意味着保活
  /// 已经生效——这能正确覆盖「app 被杀后由 daemon 自动拉起、但 daemon 自身
  /// 短暂缺位」的场景，避免误报「未启动」。
  static Future<bool> isRunning() {
    return FlutterDaemonPlatform.instance.isRunning();
  }
}
