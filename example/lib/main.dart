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
    // 冷启动时自动检测保活是否已激活：app 被杀后由 daemon 自动拉起时，
    // native daemon / :daemon 进程通常仍在运行，这里据此还原真实状态。
    _check();
  }

  Future<void> _start() async {
    final ok = await FlutterDaemon.start(intervalSeconds: 120);
    setState(() {
      _running = ok;
      _status = ok ? '已启动保活（间隔 120s）' : '启动失败';
    });
  }

  Future<void> _stop() async {
    await FlutterDaemon.stop();
    setState(() {
      _running = false;
      _status = '已停止保活';
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
              ElevatedButton(onPressed: _start, child: const Text('启动保活')),
              const SizedBox(height: 12),
              ElevatedButton(onPressed: _stop, child: const Text('停止保活')),
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
