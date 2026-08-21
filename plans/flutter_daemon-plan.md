# 封装 flutter_daemon —— Flutter 保活插件

## Context

当前 `InformationCore_Flutter/library/` 目录下是一套 Android 原生保活（防杀/keep-alive）库（com.coolerfall.daemon，原作者 Vincent Cheung），由宿主 `app/` 通过 `:library` Gradle 依赖直接使用。它的机制是：fork 出一个脱离 app 进程树（`setsid()`）的 daemon 子进程，周期性 `am startservice` 把一个 `DaemonService` 拉起，Service 再拉起 app 的 LAUNCHER activity，从而在 app 被杀后自我恢复。

目标：把这套库**封装成一个独立、通用、可复用的 Flutter 插件 `flutter_daemon`**，放在 `../flutter_daemon/`（与 `InformationCore_Flutter`、`smartpad` 同级）。Flutter 程序通过 `FlutterDaemon.start()` 一行调用即可启用保活。本次只创建插件本体 + example，不改动 `InformationCore_Flutter/app`。

用户已确认的关键决策：
- 插件名 `flutter_daemon`，位置 `/Users/FlyingtimeICE/Documents/workspace/flutter/flutter_daemon`
- ABI：仅 `armeabi-v7a` + `arm64-v8a`（与宿主 abiFilters 一致）
- C 源码 + 预编译二进制都迁入插件
- `DaemonService` 改为通用版：`getLaunchIntentForPackage(getPackageName())` 自动拉起自身 LAUNCHER，去掉硬编码包名和废弃的 `getRunningTasks`+`GET_TASKS`
- 只创建插件，不替换宿主现有 `:library`

环境：Flutter 3.32.8（smartpad 用 FVM 3.10.6，SDK >=3.0），AGP 7.3.0，AndroidX，宿主 minSdk 21 / targetSdk 28。

---

## 实施方案

### 1. 创建插件工程骨架

用 FVM 的 flutter 执行（与 smartpad 环境对齐）：
```bash
cd /Users/FlyingtimeICE/Documents/workspace/flutter
fvm flutter create --template=plugin --platforms=android \
  --org com.flutter_daemon flutter_daemon
```
生成标准 Android-only 插件结构，再按需裁剪/替换。

最终目录结构（相对 `flutter_daemon/`）：
```
flutter_daemon/
├── .gitignore  CHANGELOG.md  LICENSE  README.md  analysis_options.yaml  pubspec.yaml
├── lib/flutter_daemon.dart                 # Dart 入口 (MethodChannel)
├── android/
│   ├── build.gradle  settings.gradle
│   └── src/main/
│       ├── AndroidManifest.xml             # 声明 DaemonService (会被 manifest merger 合并进宿主)
│       ├── assets/{armeabi-v7a,arm64-v8a}/daemon   # 预编译二进制 (从 library/src/main/assets 拷)
│       ├── jni/                             # C 源码 (便于后续重编)
│       │   ├── Android.mk  Application.mk
│       │   ├── common/{common.c, common.h, log.h, Android.mk}
│       │   └── daemon/{daemon.c, Android.mk}
│       └── kotlin/com/flutter_daemon/
│           ├── FlutterDaemonPlugin.kt       # MethodChannel handler
│           ├── daemon/Daemon.kt             # 迁自 Daemon.java
│           ├── daemon/Command.kt            # 迁自 Command.java (改 SUPPORTED_ABIS)
│           └── service/DaemonService.kt     # 通用版保活 Service
├── example/  (flutter create 自带的最小示例，用于自测)
└── test/flutter_daemon_test.dart
```

### 2. pubspec.yaml（关键部分）

```yaml
name: flutter_daemon
description: Android 进程保活插件，封装自 com.coolerfall.daemon。
version: 0.0.1

environment:
  sdk: '>=3.0.0 <4.0.0'
  flutter: '>=3.0.0'

dependencies:
  flutter: { sdk: flutter }
  plugin_platform_interface: ^2.0.2

dev_dependencies:
  flutter_test: { sdk: flutter }
  flutter_lints: ^2.0.0

flutter:
  plugin:
    platforms:
      android:
        package: com.flutter_daemon
        pluginClass: FlutterDaemonPlugin
```

### 3. Dart API（`lib/flutter_daemon.dart`）

用 MethodChannel（接口简单，无需 Pigeon）：
```dart
class FlutterDaemon {
  static const _channel = MethodChannel('flutter_daemon');

  /// 启动保活守护。intervalSeconds 为检查拉起间隔，最小 3（daemon.c 内置下限）。
  static Future<bool> start({int intervalSeconds = 3}) async {
    return await _channel.invokeMethod<bool>('start', {'intervalSeconds': intervalSeconds}) ?? false;
  }

  /// 停止保活：kill daemon 子进程。
  static Future<bool> stop() async => await _channel.invokeMethod<bool>('stop') ?? false;

  /// daemon 进程是否在运行。
  static Future<bool> isRunning() async => await _channel.invokeMethod<bool>('isRunning') ?? false;
}
```

### 4. Android 插件层迁移（Kotlin）

**`daemon/Daemon.kt`**（迁自 `library/src/main/java/com/coolerfall/daemon/Daemon.java`，包名改 `com.flutter_daemon.daemon`）：
- 保留 `run(context, daemonServiceClazz, interval)` 逻辑：开线程 → `Command.install()` 拷贝二进制并 chmod 0755 → `Runtime.exec("daemon -p <pkg> -s <service全名> -t <interval>")`。
- service 类固定为 `DaemonService::class.java`（内置），不再由调用方传。
- 常量 `INTERVAL_DELAY=3` 保留。

**`daemon/Command.kt`**（迁自 `Command.java`）：
- **修正 ABI 判断**：原 `Build.CPU_ABI`（API 21 废弃/26 移除）→ 改用 `Build.SUPPORTED_ABIS[0]`，匹配 `armeabi-v7a` / `arm64-v8a`（只带这两个，命中不到则回退 arm64-v8a）。
- `install(context, "bin", "daemon")`：从 assets `<abi>/daemon` 拷到 `context.getDir("bin", MODE_PRIVATE)/daemon`，chmod 0755。
- 资产路径：插件 `android/src/main/assets/<abi>/daemon` 会被打进 AAR assets，宿主打包合并后 `context.getAssets().open("<abi>/daemon")` 可正常读取（与原 library 同理）。

**`service/DaemonService.kt`**（通用版，替代 `app/.../DaemonService.java`）：
- `onCreate()`：`Daemon.run(this, DaemonService::class.java, INTERVAL_DELAY)`。
- `onStartCommand()`：
  - API 26+ 先 `startForeground()` + 通知（避免后台 Service 限制导致 ANR/crash），在 `onCreate` 建 channel。
  - 用 `PackageManager.getLaunchIntentForPackage(packageName)` 拉起**自身** LAUNCHER（去掉硬编码 `com.iotsk.showinformations`，去掉废弃的 `getRunningTasks` + `GET_TASKS` 权限）。
  - 返回 `START_STICKY`。
- 注意分层：daemon.c 的 `am startservice -n <pkg>/com.flutter_daemon.service.DaemonService` 拉起本 Service，Service 再拉 LAUNCHER activity——与原设计一致。

**`FlutterDaemonPlugin.kt`**：`onAttachedToEngine` 注册 `MethodChannel("flutter_daemon")`，处理 `start/stop/isRunning`：
- `start`：拿到 application context → `Daemon.run(context, DaemonService::class.java, interval)`。
- `stop`/`isRunning`：遍历 `/proc/*/cmdline` 找名为 `daemon` 的进程 kill/返回存在性（参考 `common.c::find_pid_by_name` 逻辑，Kotlin 重写）。

### 5. 插件 AndroidManifest.xml（声明 Service，自动合并进宿主）

```xml
<manifest xmlns:android="http://schemas.android.com/res/android">
    <application>
        <service
            android:name="com.flutter_daemon.service.DaemonService"
            android:process=":daemon"
            android:exported="false" />
    </application>
</manifest>
```
关键：插件的 manifest 会被 manifest merger 合并进宿主 APK，**宿主无需手动声明 Service**。`:daemon` 让 Service 跑独立进程（与原宿主一致）。

### 6. native 二进制与源码迁移

- 预编译：`cp library/src/main/assets/{armeabi-v7a,arm64-v8a}/daemon` → `flutter_daemon/android/src/main/assets/<abi>/daemon`（已确认是 stripped ELF，可直接用）。
- 源码：`cp -r library/src/main/jni` → `flutter_daemon/android/src/main/jni`（保留 daemon.c/common.c/common.h/log.h/4 个 .mk/Application.mk）。
- `android/build.gradle`：保留 `sourceSets { main { jni.srcDirs = [] } }`（用预编译 assets，不让 gradle 自动 ndk-build），`assets.srcDirs` 指向 `src/main/assets`。加一段注释说明：如需重新编译二进制，运行 `ndk-build -C src/main/jni` 后把 `libs/<abi>/daemon` 拷回 `src/main/assets/<abi>/daemon`。

### 7. example（自测用）

`example/lib/main.dart`：一个按钮调 `FlutterDaemon.start(intervalSeconds: 3)`，再 `stop()` / `isRunning()` 验证。`example/pubspec.yaml` 用 `path: ../` 依赖插件。用于验证插件能 `flutter pub get` + 编译 + 运行。

---

## 关键文件清单（待创建/修改，相对 `flutter_daemon/`）

| 文件 | 来源 / 动作 |
|---|---|
| `pubspec.yaml` | 新建（见上） |
| `lib/flutter_daemon.dart` | 新建（MethodChannel 封装） |
| `android/build.gradle` | 模板生成后改：加 `jni.srcDirs=[]`、assets.srcDirs、AGP 7.3、namespace、minSdk 21、abiFilters |
| `android/src/main/AndroidManifest.xml` | 新建（声明 DaemonService，见上） |
| `android/src/main/assets/{armeabi-v7a,arm64-v8a}/daemon` | 从 `InformationCore_Flutter/library/src/main/assets/` 拷 |
| `android/src/main/jni/**` | 从 `InformationCore_Flutter/library/src/main/jni/` 拷（C 源码 + .mk） |
| `android/src/main/kotlin/com/flutter_daemon/FlutterDaemonPlugin.kt` | 新建 |
| `android/src/main/kotlin/com/flutter_daemon/daemon/Daemon.kt` | 迁自 `library/.../Daemon.java`（改包名） |
| `android/src/main/kotlin/com/flutter_daemon/daemon/Command.kt` | 迁自 `Command.java`（改 SUPPORTED_ABIS） |
| `android/src/main/kotlin/com/flutter_daemon/service/DaemonService.kt` | 通用版重写（getLaunchIntentForPackage + 前台化） |
| `README.md` | 新建：用法 + **保活机制限制说明** |
| `example/**` | 模板生成后替换 main.dart |

---

## 现代 Android 兼容性风险（如实写入 README，不夸大）

这套 2015 年的 `am startservice` + fork daemon 机制在新型 Android 上不可靠，README 需注明：
- **Android 8+（API 26）后台 Service 限制**：后台 `startservice` 受限，故 `DaemonService.onStartCommand` 必须尽快 `startForeground()` + 通知（已纳入实现）。
- **Android 12+（API 31）**：后台启动前台 Service 受限，保活效果进一步下降。
- **targetSdk 30+**：跨进程 `am startservice` 拉起受限。当前宿主 targetSdk 28，效果相对好；升 targetSdk 后建议重新评估。
- **厂商激进省电**（MIUI/EMUI/ColorOS 等）：fork+setsid 的 daemon 子进程也可能被杀，效果因 ROM 而异，不保证。
- 结论：本插件提供与原库一致的保活能力，**不保证在所有设备/版本上生效**，建议配合开机自启、前台 Service、厂商白名单等手段。

---

## 验证方式（end-to-end）

1. `cd flutter_daemon && fvm flutter pub get` —— 依赖解析通过。
2. `fvm flutter analyze` —— 无错误。
3. `cd example && fvm flutter pub get && fvm flutter build apk --debug` —— 插件能打进 APK，确认 `assets/armeabi-v7a/daemon`、`arm64-v8a/daemon`、`DaemonService` 已合入。
4. 真机安装 example，点 Start，`adb logcat -s Daemon` 观察：`child process fork ok, daemon start`、周期 `check the service once`、`am startservice` 拉起 Service 日志。
5. 手动 kill app（`adb shell am force-stop <example_pkg>` 或后台清理），观察 daemon 进程（`adb shell ps | grep daemon`）是否存活并在 2 分钟内把 Service + LAUNCHER 拉起。
6. 调 `FlutterDaemon.isRunning()` 返回 true，`stop()` 后 daemon 进程消失、`isRunning()` 返回 false。

> 注：步骤 5 在高版本/严格 ROM 上可能失败，属预期（见上风险说明）。
