import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_daemon/flutter_daemon_method_channel.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final platform = MethodChannelFlutterDaemon();
  const channel = MethodChannel('flutter_daemon');

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (methodCall) async {
      switch (methodCall.method) {
        case 'start':
          return true;
        case 'stop':
          return true;
        case 'isRunning':
          return false;
        default:
          return null;
      }
    });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  test('start returns true via method channel', () async {
    expect(await platform.start(intervalSeconds: 180), isTrue);
  });

  test('stop returns true via method channel', () async {
    expect(await platform.stop(), isTrue);
  });

  test('isRunning returns false via method channel', () async {
    expect(await platform.isRunning(), isFalse);
  });
}
