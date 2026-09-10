import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_daemon/flutter_daemon.dart';
import 'package:flutter_daemon/flutter_daemon_platform_interface.dart';
import 'package:flutter_daemon/flutter_daemon_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockFlutterDaemonPlatform
    with MockPlatformInterfaceMixin
    implements FlutterDaemonPlatform {
  bool enableCalled = false;
  int? lastInterval;
  bool isRunningCalled = false;
  bool enableBootAutoStartCalled = false;
  bool disableBootAutoStartCalled = false;
  bool isBootAutoStartEnabledCalled = false;

  @override
  Future<bool> enable({int intervalSeconds = 3}) async {
    enableCalled = true;
    lastInterval = intervalSeconds;
    return true;
  }

  @override
  Future<bool> isRunning() async {
    isRunningCalled = true;
    return false;
  }

  @override
  Future<bool> enableBootAutoStart() async {
    enableBootAutoStartCalled = true;
    return true;
  }

  @override
  Future<bool> disableBootAutoStart() async {
    disableBootAutoStartCalled = true;
    return true;
  }

  @override
  Future<bool> isBootAutoStartEnabled() async {
    isBootAutoStartEnabledCalled = true;
    return true;
  }
}

void main() {
  final FlutterDaemonPlatform initialPlatform = FlutterDaemonPlatform.instance;

  test('$MethodChannelFlutterDaemon is the default instance', () {
    expect(initialPlatform, isA<MethodChannelFlutterDaemon>());
  });

  test('enable delegates to platform with interval', () async {
    final fake = MockFlutterDaemonPlatform();
    FlutterDaemonPlatform.instance = fake;

    final ok = await FlutterDaemon.enable(intervalSeconds: 180);
    expect(ok, isTrue);
    expect(fake.enableCalled, isTrue);
    expect(fake.lastInterval, 180);
  });

  test('isRunning delegates to platform', () async {
    final fake = MockFlutterDaemonPlatform();
    FlutterDaemonPlatform.instance = fake;

    final running = await FlutterDaemon.isRunning();
    expect(running, isFalse);
    expect(fake.isRunningCalled, isTrue);
  });

  test('enableBootAutoStart delegates to platform', () async {
    final fake = MockFlutterDaemonPlatform();
    FlutterDaemonPlatform.instance = fake;

    final ok = await FlutterDaemon.enableBootAutoStart();
    expect(ok, isTrue);
    expect(fake.enableBootAutoStartCalled, isTrue);
  });

  test('disableBootAutoStart delegates to platform', () async {
    final fake = MockFlutterDaemonPlatform();
    FlutterDaemonPlatform.instance = fake;

    final ok = await FlutterDaemon.disableBootAutoStart();
    expect(ok, isTrue);
    expect(fake.disableBootAutoStartCalled, isTrue);
  });

  test('isBootAutoStartEnabled delegates to platform', () async {
    final fake = MockFlutterDaemonPlatform();
    FlutterDaemonPlatform.instance = fake;

    final enabled = await FlutterDaemon.isBootAutoStartEnabled();
    expect(enabled, isTrue);
    expect(fake.isBootAutoStartEnabledCalled, isTrue);
  });
}
