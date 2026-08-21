import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_daemon/flutter_daemon.dart';
import 'package:flutter_daemon/flutter_daemon_platform_interface.dart';
import 'package:flutter_daemon/flutter_daemon_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockFlutterDaemonPlatform
    with MockPlatformInterfaceMixin
    implements FlutterDaemonPlatform {
  bool startCalled = false;
  int? lastInterval;
  bool stopCalled = false;
  bool isRunningCalled = false;

  @override
  Future<bool> start({int intervalSeconds = 3}) async {
    startCalled = true;
    lastInterval = intervalSeconds;
    return true;
  }

  @override
  Future<bool> stop() async {
    stopCalled = true;
    return true;
  }

  @override
  Future<bool> isRunning() async {
    isRunningCalled = true;
    return false;
  }
}

void main() {
  final FlutterDaemonPlatform initialPlatform = FlutterDaemonPlatform.instance;

  test('$MethodChannelFlutterDaemon is the default instance', () {
    expect(initialPlatform, isA<MethodChannelFlutterDaemon>());
  });

  test('start delegates to platform with interval', () async {
    final fake = MockFlutterDaemonPlatform();
    FlutterDaemonPlatform.instance = fake;

    final ok = await FlutterDaemon.start(intervalSeconds: 180);
    expect(ok, isTrue);
    expect(fake.startCalled, isTrue);
    expect(fake.lastInterval, 180);
  });

  test('stop delegates to platform', () async {
    final fake = MockFlutterDaemonPlatform();
    FlutterDaemonPlatform.instance = fake;

    final ok = await FlutterDaemon.stop();
    expect(ok, isTrue);
    expect(fake.stopCalled, isTrue);
  });

  test('isRunning delegates to platform', () async {
    final fake = MockFlutterDaemonPlatform();
    FlutterDaemonPlatform.instance = fake;

    final running = await FlutterDaemon.isRunning();
    expect(running, isFalse);
    expect(fake.isRunningCalled, isTrue);
  });
}
