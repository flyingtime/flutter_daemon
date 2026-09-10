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
  bool _bootAutoStart = false;
  String _status = '未启动';

  @override
  void initState() {
    super.initState();
    // 示例应用启动后直接启用保活，不依赖用户再次点击按钮。
    _enable();
    // 开机自启默认关闭，仅当用户点击开关后才开启；这里只读取当前状态用于显示。
    _refreshBootAutoStart();
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

  Future<void> _refreshBootAutoStart() async {
    final enabled = await FlutterDaemon.isBootAutoStartEnabled();
    setState(() {
      _bootAutoStart = enabled;
    });
  }

  Future<void> _toggleBootAutoStart(bool value) async {
    bool ok;
    try {
      ok = value
          ? await FlutterDaemon.enableBootAutoStart()
          : await FlutterDaemon.disableBootAutoStart();
    } catch (e) {
      ok = false;
    }
    if (!mounted) return;
    if (!ok) {
      setState(() {
        _status = '设置开机自启失败';
      });
    }
    await _refreshBootAutoStart();
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
              // 开机自启开关：点击开启后设备重启会自动拉起应用；不点击保持关闭。
              SizedBox(
                width: 260,
                child: SwitchListTile(
                  title: const Text('开机自启动'),
                  subtitle: Text(_bootAutoStart ? '已开启：重启后自动启动' : '未开启'),
                  value: _bootAutoStart,
                  onChanged: _toggleBootAutoStart,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
