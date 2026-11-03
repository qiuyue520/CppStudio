package com.jmwl.cppstudio.toolchain

import android.content.Context
import com.jmwl.cppstudio.toolchain.runtime.ApkMirrorConfig
import com.jmwl.cppstudio.toolchain.runtime.MirrorCandidate
import com.jmwl.cppstudio.toolchain.runtime.MirrorSpeedResult
import com.jmwl.cppstudio.toolchain.runtime.MirrorSpeedTest
import com.jmwl.cppstudio.toolchain.runtime.toolchain_runtime_paths

/**
 * Alpine 软件源（apk 镜像）选择的存储与应用。
 *
 * C/C++ 工具链和所有第三方库都来自 apk 仓库，所以「换源」是本项目唯一需要的
 * 网络配置项（Go 版本里的 GOPROXY 在 C/C++ 场景没有对应概念）。
 *
 * 选中的镜像持久化在 SharedPreferences 并缓存在内存：[current] 会被编辑器
 * 重组热路径读取，不能每次读磁盘。切换后立刻重写 rootfs 的
 * /etc/apk/repositories（[ApkMirrorConfig]），交互终端里的 apk 命令同样生效。
 */
object apk_mirror_store {

    private const val prefs_name = "apk_mirror_settings"
    private const val key_host = "apk_mirror_host"
    private const val key_name = "apk_mirror_name"

    /** 内置镜像（顺序 = 默认回退顺序，与测速器保持一致）。 */
    val builtin_mirrors: List<MirrorCandidate> = MirrorSpeedTest.DEFAULT_CANDIDATES

    private val default_mirror: MirrorCandidate = builtin_mirrors.first()

    @Volatile
    private var current_host: String = default_mirror.host

    @Volatile
    private var current_name: String = default_mirror.name

    @Volatile
    private var loaded: Boolean = false

    /** app 启动时加载持久化的选择，并把 rootfs 的源同步成它。 */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(prefs_name, Context.MODE_PRIVATE)
        current_host = prefs.getString(key_host, null) ?: default_mirror.host
        current_name = prefs.getString(key_name, null) ?: default_mirror.name
        loaded = true
        apply_to_rootfs()
    }

    /** 当前选中的镜像候选（host 用于生成仓库地址）。 */
    fun current_candidate(): MirrorCandidate = MirrorCandidate(
        name = current_name,
        host = current_host,
        probeUrl = ""
    )

    /** 当前镜像的显示名。 */
    fun current_display_name(): String = current_name

    /** 持久化选择并立即应用到 rootfs。 */
    fun set(context: Context, candidate: MirrorCandidate) {
        current_host = candidate.host
        current_name = candidate.name
        context.getSharedPreferences(prefs_name, Context.MODE_PRIVATE)
            .edit()
            .putString(key_host, candidate.host)
            .putString(key_name, candidate.name)
            .apply()
        apply_to_rootfs()
    }

    /** 把当前选择写进 rootfs 的 /etc/apk/repositories（rootfs 未装好时静默跳过）。 */
    fun apply_to_rootfs() {
        if (!loaded) return
        runCatching {
            val paths: toolchain_runtime_paths = toolchain_runtime_provider.paths()
            if (!paths.rootfs_dir.isDirectory) return
            ApkMirrorConfig.applyMirror(paths.rootfs_dir, current_candidate())
        }
    }

    /** 并发测速内置镜像（可选再带一个自定义 host）。 */
    suspend fun speed_test(custom_host: String? = null): List<MirrorSpeedResult> {
        val candidates = builtin_mirrors.toMutableList()
        val custom = custom_host?.trim().orEmpty()
        if (custom.isNotEmpty()) {
            candidates += MirrorCandidate(
                name = "自定义",
                host = custom,
                probeUrl = "https://$custom/alpine/v3.24/main/aarch64/APKINDEX.tar.gz"
            )
        }
        return MirrorSpeedTest.testAndSort(candidates)
    }
}
