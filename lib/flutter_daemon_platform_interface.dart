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

  /// 启用持续保活。
  ///
  /// [intervalSeconds] 为 daemon 周期性检查并拉起 Service/应用的间隔秒数，
  Future<bool> enable({int intervalSeconds = 3}) {
    throw UnimplementedError('enable() has not been implemented.');
  }

  /// 保活是否处于激活状态：native daemon 子进程或其托管的 `DaemonService`
  /// （独立 `:daemon` 进程）任一存活即为 true。
  Future<bool> isRunning() {
    throw UnimplementedError('isRunning() has not been implemented.');
  }

  /// 开启开机自启开关。
  ///
  /// 设备下次开机完成时，由插件内置的 `BOOT_COMPLETED` 接收器拉起保活
  /// `:daemon` Service，进而恢复应用。需要用户显式调用（而非随保活自动开启）。
  Future<bool> enableBootAutoStart() {
    throw UnimplementedError('enableBootAutoStart() has not been implemented.');
  }

  /// 关闭开机自启开关。
  Future<bool> disableBootAutoStart() {
    throw UnimplementedError('disableBootAutoStart() has not been implemented.');
  }

  /// 查询开机自启开关状态。
  Future<bool> isBootAutoStartEnabled() {
    throw UnimplementedError('isBootAutoStartEnabled() has not been implemented.');
  }
}
