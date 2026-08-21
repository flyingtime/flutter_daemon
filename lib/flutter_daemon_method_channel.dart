import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'flutter_daemon_platform_interface.dart';

/// [FlutterDaemonPlatform] 的 MethodChannel 实现，与 Android 端
/// [com.flutter_daemon.flutter_daemon.FlutterDaemonPlugin] 通信。
class MethodChannelFlutterDaemon extends FlutterDaemonPlatform {
  @visibleForTesting
  final methodChannel = const MethodChannel('flutter_daemon');

  @override
  Future<bool> enable({int intervalSeconds = 3}) async {
    final result = await methodChannel.invokeMethod<bool>(
      'enable',
      <String, dynamic>{'intervalSeconds': intervalSeconds},
    );
    return result ?? false;
  }

  @override
  Future<bool> isRunning() async {
    final result = await methodChannel.invokeMethod<bool>('isRunning');
    return result ?? false;
  }
}
