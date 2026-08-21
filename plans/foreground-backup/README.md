# flutter_daemon

Android 进程保活（keep-alive / 防杀）Flutter 插件，封装自 [com.coolerfall.daemon](https://github.com/Coolerfall/Android-Daemon) 原生库。

通过 fork 出脱离 app 进程树（`setsid()`）的 native daemon 子进程，周期性 `am startservice` 拉起内置的 `DaemonService`，Service 再拉起应用自身的 LAUNCHER，实现应用被杀后自恢复。

## 用法

```dart
import 'package:flutter_daemon/flutter_daemon.dart';

// 启动保活（默认 3 秒检查一次）
await FlutterDaemon.start();

// 检查保活是否生效：native daemon 子进程或其托管的 :daemon Service 任一存活即为 true
final running = await FlutterDaemon.isRunning();

// 停止保活
await FlutterDaemon.stop();
```

`intervalSeconds` 最小为 3（daemon.c 内置下限），小于 3 会被提升到 3。

### isRunning 的判定

`isRunning()` 返回 native daemon 子进程 **或** 其托管的 `DaemonService`（独立 `:daemon` 进程）任一是否存活。app 被杀后由 daemon 自动拉起时，两者往往交替存活（daemon 可能短暂缺位而 `:daemon` Service 仍在跑），任一存活即视为保活已生效，避免把“已自动恢复”误报成“未启动”。

## 工作机制

1. `FlutterDaemon.start()` → `Daemon.run()`：把打包在 assets 里的 native `daemon` 二进制释放到应用私有目录并 `chmod 0755`，再执行 `daemon -p <包名> -s <DaemonService 全名> -t <间隔秒>`。
2. native daemon（`android/src/main/jni/daemon/daemon.c`）fork 出子进程，`setsid()` 成为会话首、关闭标准 IO，进入循环：每隔 `interval` 秒执行一次 `am startservice -n <pkg>/<DaemonService>`。
3. `DaemonService`（`com.flutter_daemon.flutter_daemon.service.DaemonService`）被拉起后：API 26+ 转前台 Service，并通过 `PackageManager.getLaunchIntentForPackage(packageName)` 拉起应用自身的 LAUNCHER activity。

## 宿主接入

插件的 `AndroidManifest.xml` 已声明 `DaemonService`（独立 `:daemon` 进程），会通过 manifest merger 自动合并进宿主 APK，**宿主无需手动声明 Service**。

如需开机自启等增强效果，宿主可自行配合 `RECEIVE_BOOT_COMPLETED` 等机制（本插件不内置）。

## ABI 支持

仅 `armeabi-v7a` + `arm64-v8a`，与常见宿主 `abiFilters` 一致。如需 x86（模拟器调试）可参考下方「重新编译 daemon」自行补充。

## 重新编译 daemon

预编译的 `daemon` 二进制位于 `android/src/main/assets/<abi>/daemon`。如需修改 native 逻辑后重新编译：

```bash
cd android/src/main/jni
ndk-build
# 产物在 android/src/main/jni/libs/<abi>/daemon，拷回 assets：
cp libs/armeabi-v7a/daemon ../src/main/assets/armeabi-v7a/daemon
cp libs/arm64-v8a/daemon   ../src/main/assets/arm64-v8a/daemon
```

如需新增 ABI，在 `Application.mk` 的 `APP_ABI` 增加并在 `Command.kt` 的 `pickAbi()` 中识别。

## 兼容性风险（重要）

这套 `am startservice` + fork daemon 的机制源自 2015 年，在新型 Android 上不可靠，本插件**仅提供与原库一致的能力，不保证在所有设备/版本上生效**：

- **Android 8+（API 26）**：后台 `startservice` 受限，故 `DaemonService` 已实现 `startForeground()` + 常驻通知。
- **Android 12+（API 31）**：后台启动前台 Service 受限，保活效果进一步下降。
- **targetSdk 30+**：跨进程 `am startservice` 拉起受限。当前宿主 targetSdk 28 效果相对好；升 targetSdk 后建议重新评估。
- **厂商激进省电**（MIUI / EMUI / ColorOS 等）：fork+setsid 的 daemon 子进程也可能被杀，效果因 ROM 而异。

建议配合开机自启、前台 Service、厂商后台白名单等手段综合使用。
