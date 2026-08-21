#!/usr/bin/env bash
#
# daemon.sh —— 重新编译 native daemon 二进制并安装到 assets。
#
# 做的事：
#   1. 用 ndk-build 编译 android/src/main/jni（产物落在临时构建目录的 libs/<abi>/daemon）
#   2. 把 armeabi-v7a / arm64-v8a 两个 ABI 的产物拷到
#      android/src/main/assets/<abi>/daemon（覆盖旧二进制）
#   3. 清理临时构建目录，不污染源码树
#
# 用法：
#   daemon.sh                # 编译并安装到 assets（默认）
#   daemon.sh --build-only   # 只编译，不拷贝；产物留在临时目录的 libs/<abi>/daemon
#   daemon.sh -h|--help      # 查看帮助
#
# 依赖：Android NDK（通过 ANDROID_NDK_HOME / ANDROID_NDK_ROOT 定位，或 PATH 中的 ndk-build）。
#       仅编译插件实际支持的两个 ARM ABI（见 Application.mk / Command.kt#pickAbi /
#       build.gradle 的 abiFilters），assets 不放 x86/x86_64。

set -euo pipefail

# 定位 jni 目录（脚本所在目录），不依赖当前工作目录。
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JNI_DIR="$SCRIPT_DIR"                                       # android/src/main/jni
# assets 与 jni 同为 android/src/main 下的兄弟目录，向上 1 级即可定位。
MAIN_DIR="$(cd "$JNI_DIR/.." && pwd)"                       # android/src/main
ASSETS_DIR="$MAIN_DIR/assets"                               # android/src/main/assets

# ndk-build 的中间产物（obj）和最终产物（libs）默认落在项目根下，会污染源码树。
# 用 NDK_OUT / NDK_LIBS_OUT 显式重定向到独立临时目录，编译完再拷到 assets，随后清理。
# 注意：不要用 NDK_PROJECT_PATH 重定向，那会改变构建脚本查找位置导致找不到 Android.mk。
BUILD_TMP="$(mktemp -d -t flutter_daemon_build.XXXXXX)"
trap 'rm -rf "$BUILD_TMP"' EXIT

# 仅编译插件实际支持的两个 ABI。
ABIS=("armeabi-v7a" "arm64-v8a")

# 解析参数。
BUILD_ONLY=0
for arg in "$@"; do
  case "$arg" in
    --build-only) BUILD_ONLY=1 ;;
    -h|--help)
      sed -n '3,20p' "${BASH_SOURCE[0]}"
      exit 0
      ;;
    *)
      echo "错误：未知参数 '$arg'（可用：--build-only）" >&2
      exit 1
      ;;
  esac
done

# 定位 ndk-build：优先 ANDROID_NDK_HOME / ANDROID_NDK_ROOT，其次 PATH。
NDK_BUILD="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}/ndk-build"
if [ ! -x "$NDK_BUILD" ]; then
  if command -v ndk-build >/dev/null 2>&1; then
    NDK_BUILD="$(command -v ndk-build)"
  else
    echo "错误：找不到 ndk-build。请设置 ANDROID_NDK_HOME 或将 ndk-build 加入 PATH。" >&2
    exit 1
  fi
fi

echo "==> 使用 ndk-build : $NDK_BUILD"
echo "==> 编译 jni 目录  : $JNI_DIR"
echo "==> 目标 ABI       : ${ABIS[*]}"
echo "==> 编译输出目录   : $BUILD_TMP"

# 显式传 APP_ABI 限定两个 ARM 架构，避免 Application.mk 里多余的 x86/x86_64 也编译
# （assets 与 Command.kt 均不支持它们，编了也无用）。
# NDK_OUT / NDK_LIBS_OUT 把中间产物和最终产物重定向到独立临时目录，
# 既不污染 jni 源码树，也保证路径可预期；编译完拷贝后由 trap 清理。
"$NDK_BUILD" -C "$JNI_DIR" \
  NDK_OUT="$BUILD_TMP/obj" \
  NDK_LIBS_OUT="$BUILD_TMP/libs" \
  APP_ABI="${ABIS[*]}"

if [ "$BUILD_ONLY" -eq 1 ]; then
  echo "==> --build-only：跳过拷贝。产物位于 $BUILD_TMP/libs/<abi>/daemon"
  exit 0
fi

echo "==> 安装产物到 assets: $ASSETS_DIR"
for abi in "${ABIS[@]}"; do
  src="$BUILD_TMP/libs/$abi/daemon"
  dst="$ASSETS_DIR/$abi/daemon"
  if [ ! -f "$src" ]; then
    echo "错误：编译产物不存在: $src" >&2
    exit 1
  fi
  mkdir -p "$ASSETS_DIR/$abi"
  cp -f "$src" "$dst"
  chmod 0644 "$dst"
  echo "    $abi  :  $src  ->  $dst"
done

echo "==> 完成。请重新打包插件 / APK 以使新二进制生效。"
