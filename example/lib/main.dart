import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_daemon/flutter_daemon.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  bool _running = false;
  String _status = '未启动';

  @override
  void initState() {
    super.initState();
    // 示例应用启动后直接启用保活，不依赖用户再次点击按钮。
    _enable();
  }

  Future<void> _enable() async {
    final ok = await FlutterDaemon.enable(intervalSeconds: 3);
    setState(() {
      _running = ok;
      _status = ok ? '已启动保活（间隔 3s）' : '启动失败';
    });
  }

  Future<void> _check() async {
    final running = await FlutterDaemon.isRunning();
    setState(() {
      _running = running;
      _status = running ? '保活运行中（daemon 或 :daemon 进程存活）' : '保活未运行';
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(title: const Text('flutter_daemon 示例')),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(_status),
              const SizedBox(height: 24),
              ElevatedButton(onPressed: _enable, child: const Text('启用保活')),
              const SizedBox(height: 12),
              ElevatedButton(onPressed: _check, child: const Text('检查状态')),
              const SizedBox(height: 24),
              Text('运行状态: $_running'),
            ],
          ),
        ),
      ),
    );
  }
}
