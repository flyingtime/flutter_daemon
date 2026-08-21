package com.flutter_daemon.flutter_daemon.daemon

import android.content.Context
import android.content.res.AssetManager
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * 工具类：把打包在 assets 中的 native daemon 二进制释放到应用私有目录并赋予可执行权限。
 *
 * 迁自 com.coolerfall.daemon.Command（原作者 Vincent Cheung），主要改动：
 * - [Build.CPU_ABI]（API 21 起废弃、26 起移除）改为 [Build.SUPPORTED_ABIS]。
 * - 仅支持 armeabi-v7a / arm64-v8a 两个 ABI。
 */
internal object Command {
    private const val TAG = "Command"

    /** 将输入流写入目标文件，并通过 chmod 设置权限。 */
    @Throws(IOException::class, InterruptedException::class)
    private fun copyFile(file: File, input: InputStream, mode: String) {
        val abspath = file.absolutePath
        FileOutputStream(file).use { out ->
            val buf = ByteArray(1024)
            var len = input.read(buf)
            while (len > 0) {
                out.write(buf, 0, len)
                len = input.read(buf)
            }
        }
        input.close()
        Runtime.getRuntime().exec("chmod $mode $abspath").waitFor()
    }

    /** 从 assets 拷贝指定文件到目标路径。 */
    @Throws(IOException::class, InterruptedException::class)
    private fun copyAssets(context: Context, assetsFilename: String, file: File, mode: String) {
        val manager: AssetManager = context.assets
        val input: InputStream = manager.open(assetsFilename)
        copyFile(file, input, mode)
    }

    /**
     * 选择当前设备对应的 ABI 目录名。仅识别 armeabi-v7a / arm64-v8a，其余回退到 arm64-v8a
     * （现代 64 位设备基本都兼容 arm64-v8a，且回退总比崩溃好）。
     */
    private fun pickAbi(): String {
        val supported = Build.SUPPORTED_ABIS
        for (abi in supported) {
            when {
                abi.startsWith("arm64-v8a") -> return "arm64-v8a"
                abi.startsWith("armeabi-v7a") -> return "armeabi-v7a"
                abi.startsWith("armeabi") -> return "armeabi-v7a"
            }
        }
        return "arm64-v8a"
    }

    /**
     * 将指定二进制从 assets 安装到私有目录。
     *
     * @param context  上下文
     * @param destDir  目标目录名（相对 [Context.getDir]）
     * @param filename 二进制文件名
     * @return true 表示本次执行了拷贝；false 表示二进制已存在（无需重复安装）
     */
    fun install(context: Context, destDir: String, filename: String): Boolean {
        val binaryDir = pickAbi()
        val assetfilename = "$binaryDir/$filename"
        return try {
            val f = File(context.getDir(destDir, Context.MODE_PRIVATE), filename)
            if (f.exists()) {
                Log.d(TAG, "binary has existed")
                return false
            }
            copyAssets(context, assetfilename, f, "0755")
            true
        } catch (e: Exception) {
            Log.e(TAG, "installBinary failed: ${e.message}")
            false
        }
    }
}
