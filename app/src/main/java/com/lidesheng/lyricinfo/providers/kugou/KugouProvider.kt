package com.lidesheng.lyricinfo.providers.kugou

import android.annotation.SuppressLint
import android.util.Log
import com.lidesheng.lyricinfo.core.LyricProvider
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

class KugouProvider : LyricProvider {

    companion object {
        private const val TAG = "LyricInfo"
        const val PACKAGE_NAME = "com.kugou.android"
    }

    override val packageName = PACKAGE_NAME
    // kugou_service is where PlaybackService usually runs, we should hook the main and service process
    override val processNames = listOf(PACKAGE_NAME, "$PACKAGE_NAME:kugou_service")

    private val hookHandles = mutableListOf<XposedInterface.HookHandle>()

    override fun onAppLoaded(module: XposedModule, param: PackageLoadedParam) {
        Log.i(TAG, "[Hook] ${param.packageName}")
        
        mockBuildBrand()
        installSystemPropertiesHook(module, param)
        installKugouSysUtilsHook(module, param)
    }

    override fun replaceHooks(
        module: XposedModule,
        param: PackageLoadedParam,
        oldHooks: List<XposedInterface.HookHandle>
    ): List<XposedInterface.HookHandle> {
        oldHooks.forEach { it.unhook() }
        hookHandles.clear()
        onAppLoaded(module, param)
        return hookHandles.toList()
    }

    private fun mockBuildBrand() {
        try {
            val brandField = android.os.Build::class.java.getField("BRAND")
            brandField.isAccessible = true
            brandField.set(null, "OPPO")

            val manufacturerField = android.os.Build::class.java.getField("MANUFACTURER")
            manufacturerField.isAccessible = true
            manufacturerField.set(null, "OPPO")
            Log.i(TAG, "[Kugou] ✓ Mocked Build.BRAND to OPPO")
        } catch (e: Exception) {
            Log.e(TAG, "[Kugou] ✗ Failed to mock Build.BRAND", e)
        }
    }

    @SuppressLint("PrivateApi")
    private fun installSystemPropertiesHook(module: XposedModule, param: PackageLoadedParam) {
        try {
            val systemPropertiesClass = Class.forName("android.os.SystemProperties", true, param.defaultClassLoader)

            val getMethodWith2Args = systemPropertiesClass.getMethod("get", String::class.java, String::class.java)
            module.deoptimize(getMethodWith2Args)
            hookHandles.add(module.hook(getMethodWith2Args).intercept { chain ->
                val key = chain.args[0] as? String
                if (key == "ro.build.version.oplusrom" || key == "ro.build.version.opporom") {
                    "V16.0.0"
                } else {
                    chain.proceed()
                }
            })

            val getMethodWith1Arg = systemPropertiesClass.getMethod("get", String::class.java)
            module.deoptimize(getMethodWith1Arg)
            hookHandles.add(module.hook(getMethodWith1Arg).intercept { chain ->
                val key = chain.args[0] as? String
                if (key == "ro.build.version.oplusrom" || key == "ro.build.version.opporom") {
                    "V16.0.0"
                } else {
                    chain.proceed()
                }
            })

            Log.i(TAG, "[Kugou] ✓ SystemProperties Hooked")
        } catch (e: Exception) {
            Log.e(TAG, "[Kugou] ✗ SystemProperties", e)
        }
    }

    private fun installKugouSysUtilsHook(module: XposedModule, param: PackageLoadedParam) {
        try {
            // Kugou uses Runtime.getRuntime().exec("getprop ...") wrapped in SystemUtils.d1(String)
            // Hooking d1 is the most direct way to bypass the shell command execution and fake the ROM version.
            val sysUtilsClass = Class.forName("com.kugou.common.utils.SystemUtils", true, param.defaultClassLoader)
            val d1Method = sysUtilsClass.getDeclaredMethod("d1", String::class.java)
            module.deoptimize(d1Method)
            hookHandles.add(module.hook(d1Method).intercept { chain ->
                val cmd = chain.args[0] as? String
                if (cmd != null && (cmd.contains("ro.build.version.oplusrom") || cmd.contains("ro.build.version.opporom"))) {
                    "V16.0.0"
                } else {
                    chain.proceed()
                }
            })
            Log.i(TAG, "[Kugou] ✓ SystemUtils.d1 Hooked")
        } catch (e: Exception) {
            Log.e(TAG, "[Kugou] ✗ SystemUtils.d1", e)
        }
    }
}
