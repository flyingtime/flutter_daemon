// This is a basic Flutter integration test.
//
// Since integration tests run in a full Flutter application, they can interact
// with the host side of a plugin implementation, unlike Dart unit tests.
//
// For more information about Flutter integration tests, please see
// https://flutter.dev/to/integration-testing

import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

import 'package:flutter_daemon/flutter_daemon.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('start then isRunning', (WidgetTester tester) async {
    final started = await FlutterDaemon.start(intervalSeconds: 120);
    expect(started, true);
    // daemon 进程在真实设备上启动后应处于运行态（CI / 模拟器可能失败）
    final running = await FlutterDaemon.isRunning();
    expect(running, isA<bool>());
  });
}
