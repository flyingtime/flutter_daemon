import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'flutter_daemon_method_channel.dart';

/// 保活插件的平台接口。
///
/// Android 为唯一实现平台；其它平台调用将抛 [UnimplementedError]。
abstract class FlutterDaemonPlatform extends PlatformInterface {
  FlutterDaemonPlatform() : super(token: _token);

  static final Object _token = Object();

  static FlutterDaemonPlatform _instance = MethodChannelFlutterDaemon();

  static FlutterDaemonPlatform get instance => _instance;

  static set instance(FlutterDaemonPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  /// 启动保活守护进程。
  ///
  /// [intervalSeconds] 为 daemon 周期性检查并拉起 Service/应用的间隔秒数，
  /// daemon.c 内置下限为 120 秒，传入小于 120 的值会被自动提升到 120。
  Future<bool> start({int intervalSeconds = 120}) {
    throw UnimplementedError('start() has not been implemented.');
  }

  /// 停止保活：终止 daemon 子进程。
  Future<bool> stop() {
    throw UnimplementedError('stop() has not been implemented.');
  }

  /// 保活是否处于激活状态：native daemon 子进程或其托管的 `DaemonService`
  /// （独立 `:daemon` 进程）任一存活即为 true。
  Future<bool> isRunning() {
    throw UnimplementedError('isRunning() has not been implemented.');
  }
}
