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
        case 'enable':
          return true;
        case 'isRunning':
          return false;
        case 'enableBootAutoStart':
          return true;
        case 'disableBootAutoStart':
          return true;
        case 'isBootAutoStartEnabled':
          return true;
        default:
          return null;
      }
    });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  test('enable returns true via method channel', () async {
    expect(await platform.enable(intervalSeconds: 180), isTrue);
  });

  test('isRunning returns false via method channel', () async {
    expect(await platform.isRunning(), isFalse);
  });

  test('enableBootAutoStart returns true via method channel', () async {
    expect(await platform.enableBootAutoStart(), isTrue);
  });

  test('disableBootAutoStart returns true via method channel', () async {
    expect(await platform.disableBootAutoStart(), isTrue);
  });

  test('isBootAutoStartEnabled returns true via method channel', () async {
    expect(await platform.isBootAutoStartEnabled(), isTrue);
  });
}
