# flutter_daemon

Android 进程保活（keep-alive / 防杀）Flutter 插件，封装自 [com.coolerfall.daemon](https://github.com/Coolerfall/Android-Daemon) 原生库。

通过 fork 出脱离 app 进程树（`setsid()`）的 native daemon 子进程，周期性 `am startservice` 拉起内置的 `DaemonService`，Service 再拉起应用自身的 LAUNCHER，实现应用被杀后自恢复。

## 用法

```dart
import 'package:flutter_daemon/flutter_daemon.dart';

// 启用持续保活（默认 3 秒检查一次）。建议在应用启动时调用。
await FlutterDaemon.enable();

// 检查保活是否生效：native daemon 子进程或其托管的 :daemon Service 任一存活即为 true
final running = await FlutterDaemon.isRunning();

// 开机自启（可选，需用户显式开启；默认关闭）
await FlutterDaemon.enableBootAutoStart();    // 开启：设备下次开机完成后自动拉起应用
final bootOn = await FlutterDaemon.isBootAutoStartEnabled(); // 查询开关状态
await FlutterDaemon.disableBootAutoStart();   // 关闭
```

插件只提供 `enable()`，不提供停止保活接口。保活由应用启动时启用，并持续运行到应用被卸载或用户在系统设置中强行停止应用。

`intervalSeconds` 最小为 3（daemon.c 内置下限），小于 3 会被提升到 3。

### 开机自启

插件内置 `BOOT_COMPLETED` 广播接收器，但**默认关闭**：只有 Dart 侧显式调用 `enableBootAutoStart()` 后，设备下次开机完成才会拉起 `:daemon` 保活 Service，由 Service 检测主进程不在后恢复应用界面。开关状态持久化在应用私有 SharedPreferences 中，跨重启有效，用 `isBootAutoStartEnabled()` 查询、`disableBootAutoStart()` 关闭。

注意：部分定制 ROM（MIUI / EMUI 等）还要求用户在系统设置里给应用开"自启动"权限，插件无法代授；RECEIVE_BOOT_COMPLETED 权限已由插件 manifest 声明，宿主无需重复声明。

### isRunning 的判定

`isRunning()` 返回 native daemon 子进程 **或** 其托管的 `DaemonService`（独立 `:daemon` 进程）任一是否存活。app 被杀后由 daemon 自动拉起时，两者往往交替存活（daemon 可能短暂缺位而 `:daemon` Service 仍在跑），任一存活即视为保活已生效，避免把“已自动恢复”误报成“未启动”。

### 推荐接入方式

在宿主应用的第一个 Flutter 页面或应用初始化逻辑中调用一次：

```dart
Future<void> initializeKeepAlive() async {
  await FlutterDaemon.enable(intervalSeconds: 3);
}
```

示例应用已经在启动时自动调用 `enable()`，并提供“检查状态”按钮用于观察 daemon 或前台 Service 是否存活。

## 工作机制

1. `FlutterDaemon.enable()` → `Daemon.run()`：把打包在 assets 里的 native `daemon` 二进制释放到应用私有目录并 `chmod 0755`，再执行 `daemon -p <包名> -s <DaemonService 全名> -t <间隔秒>`。
2. native daemon（`android/src/main/jni/daemon/daemon.c`）fork 出子进程，`setsid()` 成为会话首、关闭标准 IO，进入循环：每隔 `interval` 秒执行一次 `am startservice -n <pkg>/<DaemonService>`。
3. `DaemonService`（`com.flutter_daemon.flutter_daemon.service.DaemonService`）被拉起后：API 26+ 转前台 Service，并通过 `PackageManager.getLaunchIntentForPackage(packageName)` 拉起应用自身的 LAUNCHER activity。

## 宿主接入

插件的 `AndroidManifest.xml` 已声明 `DaemonService`（独立 `:daemon` 进程）与开机自启接收器（`BootCompletedReceiver`），会通过 manifest merger 自动合并进宿主 APK，**宿主无需手动声明 Service 或权限**。

## ABI 支持

仅 `armeabi-v7a` + `arm64-v8a`，与常见宿主 `abiFilters` 一致。如需 x86（模拟器调试）可参考下方「重新编译 daemon」自行补充。

## 重新编译 daemon

预编译的 `daemon` 二进制位于 `android/src/main/assets/<abi>/daemon`。如需修改 native 逻辑后重新编译，直接运行项目自带的脚本（会自动 `ndk-build` 并把两个 ARM ABI 的产物拷回 assets）：

```bash
bash android/src/main/jni/daemon.sh
```

脚本说明：

- 自动定位 `ndk-build`（读取 `ANDROID_NDK_HOME` / `ANDROID_NDK_ROOT`，或 PATH）。
- 只编译 `armeabi-v7a` + `arm64-v8a`，与 `Command.kt#pickAbi()` / `build.gradle` 的 `abiFilters` 一致；中间产物落在临时目录，不污染源码树。
- `--build-only`：只编译不拷贝；`-h` 查看帮助。

如需手动操作（等价于脚本内部做的事）：

```bash
cd android/src/main/jni
ndk-build APP_ABI="armeabi-v7a arm64-v8a"
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
