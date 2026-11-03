package com.xiaonai.cppstudio.toolchain

import com.xiaonai.cppstudio.toolchain.runtime.ApkMirrorConfig
import com.xiaonai.cppstudio.toolchain.runtime.MirrorSpeedTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * C/C++ 工具链与第三方库的安装任务。
 *
 * 全部通过 apk 在 proot Alpine rootfs 内安装。安装前先测速国内镜像源，
 * 选最快的写入 /etc/apk/repositories；apk 失败自动切换下一个镜像重试。
 *
 * 所有命令经 [proot_manager] 在 proot 内执行，输出流式回调到 [onLog]。
 */

/**
 * 一键安装的核心包：
 * - build-base   gcc / g++ / make / musl-dev / binutils
 * - cmake        CMake 构建系统
 * - pkgconf      CMake 的 pkg_check_modules 依赖它查找库
 * - clang        clang 编译器（可选双编译器）
 * - clang-extra-tools  clangd（语言服务）+ clang-format（格式化）
 * - gdb          调试器
 * - git          版本控制
 * - ca-certificates  HTTPS 证书（git clone / 下载依赖）
 */
private const val CORE_PACKAGES =
    "build-base cmake pkgconf clang clang-extra-tools gdb git ca-certificates"

/** 单独安装 clangd 时的包（clang-extra-tools 提供 /usr/bin/clangd）。 */
private const val CLANGD_PACKAGES = "clang clang-extra-tools"

/** 单独安装 clang-format 时的包。 */
private const val CLANG_FORMAT_PACKAGES = "clang-extra-tools"

/**
 * 安装完整 C/C++ 工具链（build-base + cmake + clangd + gdb + git）。
 *
 * @param onLog 进度日志回调
 * @param onProgress 进度百分比回调（0-100，apk 无精确进度，用阶段值）
 */
suspend fun install_cpp_toolchain(
    onLog: (String) -> Unit,
    onProgress: (Int) -> Unit
): Boolean = withContext(Dispatchers.IO) {
    onLog("测速国内镜像源...")
    onProgress(5)
    val ranked = MirrorSpeedTest.testAndSort { candidate, ms, ok ->
        onLog("  ${candidate.name}: ${if (ok) "${ms}ms" else "不可用"}")
    }
    onLog("排序: ${ranked.joinToString { res -> "${res.candidate.name}(${res.latencyMs?.let { "${it}ms" } ?: "不可用"})" }}")
    onProgress(15)

    for (result in ranked) {
        val mirror = result.candidate
        if (result.latencyMs == null) {
            onLog("跳过不可用的 ${mirror.name}")
            continue
        }
        onLog("使用 ${mirror.name} 配置 apk 源...")
        ApkMirrorConfig.applyMirror(toolchain_runtime_provider.paths().rootfs_dir, mirror)
        onProgress(20)
        val ok = apk_update_and_install_core(onLog, onProgress)
        if (ok) {
            onLog("✅ ${mirror.name} 安装成功")
            toolchain_manager.invalidate_cpp_probe()
            verify_clangd(onLog, onProgress)
            return@withContext true
        }
        onLog("❌ ${mirror.name} 失败，切换下一个镜像源重试...")
    }
    onLog("所有镜像源均失败，请检查网络后重试")
    false
}

/**
 * 测速并把 rootfs 的 apk 源切到最快镜像。
 *
 * 安装页第一条 apk 命令（基础包）之前也要调用——minirootfs 自带的源指向官方
 * dl-cdn，国内直连不稳定；不先切镜像，基础包就会在官方源上 DNS/超时失败。
 */
suspend fun configure_best_apk_mirror(onLog: (String) -> Unit) {
    val ranked = MirrorSpeedTest.testAndSort()
    val best = ranked.firstOrNull { it.latencyMs != null } ?: return
    onLog("使用 ${best.candidate.name} 配置 apk 源...")
    ApkMirrorConfig.applyMirror(toolchain_runtime_provider.paths().rootfs_dir, best.candidate)
}

/** 单独安装/重装 C/C++ 编译器（build-base）。 */
suspend fun install_build_base(
    onLog: (String) -> Unit,
    onProgress: (Int) -> Unit
): Boolean {
    val ok = apk_install_with_best_mirror("build-base", onLog, onProgress)
    if (ok) toolchain_manager.invalidate_cpp_probe()
    return ok
}

/** 单独安装/重装 CMake。 */
suspend fun install_cmake(
    onLog: (String) -> Unit,
    onProgress: (Int) -> Unit
): Boolean {
    val ok = apk_install_with_best_mirror("cmake pkgconf", onLog, onProgress)
    if (ok) toolchain_manager.invalidate_cpp_probe()
    return ok
}

/**
 * 单独安装/重装 clangd（clang-extra-tools）。
 *
 * Alpine 的 clangd 有时落在 /usr/lib/llvmXX/bin 而不是 /usr/bin，
 * 装完补一次软链（工具链探测两种位置都能识别，软链只是为了终端里直接能用）。
 */
suspend fun install_clangd(
    onLog: (String) -> Unit,
    onProgress: (Int) -> Unit
): Boolean = withContext(Dispatchers.IO) {
    val ok = apk_install_with_best_mirror(CLANGD_PACKAGES, onLog, onProgress)
    if (ok) {
        toolchain_manager.invalidate_cpp_probe()
        proot_manager.execute_command_with_environment(
            command = buildString {
                append("for f in clangd clang-format; do ")
                append("if [ ! -e /usr/bin/\$f ]; then ")
                append("target=\$(ls -1 /usr/lib/llvm*/bin/\$f 2>/dev/null | head -n1); ")
                append("[ -n \"\$target\" ] && ln -sf \"\$target\" /usr/bin/\$f; ")
                append("fi; done")
            },
            working_dir = "/root",
            on_log = { onLog(it) }
        )
        verify_clangd(onLog, onProgress)
    }
    ok
}

/** 单独安装/重装 clang-format（clangd 同包）。 */
suspend fun install_clang_format(
    onLog: (String) -> Unit,
    onProgress: (Int) -> Unit
): Boolean {
    val ok = apk_install_with_best_mirror(CLANG_FORMAT_PACKAGES, onLog, onProgress)
    if (ok) toolchain_manager.invalidate_cpp_probe()
    return ok
}

/** 单独安装/重装 gdb。 */
suspend fun install_gdb(
    onLog: (String) -> Unit,
    onProgress: (Int) -> Unit
): Boolean {
    val ok = apk_install_with_best_mirror("gdb", onLog, onProgress)
    if (ok) toolchain_manager.invalidate_cpp_probe()
    return ok
}

/** 单独安装/重装 git。 */
suspend fun install_git(
    onLog: (String) -> Unit,
    onProgress: (Int) -> Unit
): Boolean = apk_install_with_best_mirror("git ca-certificates", onLog, onProgress)

/**
 * 通用 apk 安装（库管理页用它装第三方库）。
 *
 * @param packages apk 包名，空格分隔
 */
suspend fun apk_install(
    packages: String,
    onLog: (String) -> Unit,
    onProgress: (Int) -> Unit = {}
): Boolean {
    if (packages.isBlank()) return true
    val ok = apk_install_with_best_mirror(packages, onLog, onProgress)
    if (ok) toolchain_manager.invalidate_cpp_probe()
    return ok
}

/** 通用 apk 卸载（库管理页用它删第三方库）。 */
suspend fun apk_remove(
    packages: String,
    onLog: (String) -> Unit,
    onProgress: (Int) -> Unit = {}
): Boolean {
    if (packages.isBlank()) return true
    onProgress(20)
    val ok = proot_manager.execute_command_with_environment(
        command = "apk del $packages",
        working_dir = "/root",
        on_log = onLog
    )
    if (ok) toolchain_manager.invalidate_cpp_probe()
    return ok
}

/** 装完 clangd 后立即校验一次，把结果写进日志（失败不阻断安装流程）。 */
private suspend fun verify_clangd(onLog: (String) -> Unit, onProgress: (Int) -> Unit) {
    onProgress(95)
    val installed = toolchain_manager.let {
        toolchain_manager.invalidate_cpp_probe()
        it.is_clangd_installed()
    }
    onLog(if (installed) "clangd 可用（补全/诊断/跳转已就绪）" else "⚠️ 未找到 clangd，智能补全暂不可用，可稍后重试")
}

/**
 * 测速选最快镜像 → 配置源 → apk update + add [packages]。
 */
private suspend fun apk_install_with_best_mirror(
    packages: String,
    onLog: (String) -> Unit,
    onProgress: (Int) -> Unit
): Boolean {
    onProgress(10)
    val ranked = MirrorSpeedTest.testAndSort()
    val best = ranked.firstOrNull { it.latencyMs != null } ?: ranked.firstOrNull()
    if (best != null) {
        onLog("使用 ${best.candidate.name} 配置 apk 源...")
        ApkMirrorConfig.applyMirror(toolchain_runtime_provider.paths().rootfs_dir, best.candidate)
    }
    onProgress(20)
    return proot_manager.execute_command_with_environment(
        command = "apk update && apk add $packages",
        working_dir = "/root",
        on_log = onLog
    )
}

/** apk update + add 完整 C/C++ 工具链。 */
private suspend fun apk_update_and_install_core(
    onLog: (String) -> Unit,
    onProgress: (Int) -> Unit
): Boolean {
    onLog("更新 apk 索引...")
    onProgress(30)
    val updateOk = proot_manager.execute_command_with_environment(
        command = "apk update",
        working_dir = "/root",
        on_log = onLog
    )
    if (!updateOk) return false

    onLog("安装 build-base / cmake / clang / gdb / git（可能需要数分钟）...")
    onProgress(40)
    val apk_ok = proot_manager.execute_command_with_environment(
        // apk fix：包数据库标记"已装"但文件缺失时（安装中途进程被杀留下的事务残骸）
        // apk add 视为无操作，fix 会校验并补齐缺失文件。对完好的包是无操作。
        command = "apk add $CORE_PACKAGES && apk fix build-base",
        working_dir = "/root",
        on_log = onLog
    )
    if (!apk_ok) return false

    onProgress(70)
    onLog("创建 clangd / clang-format 软链...")
    proot_manager.execute_command_with_environment(
        command = buildString {
            append("for f in clangd clang-format; do ")
            append("if [ ! -e /usr/bin/\$f ]; then ")
            append("target=\$(ls -1 /usr/lib/llvm*/bin/\$f 2>/dev/null | head -n1); ")
            append("[ -n \"\$target\" ] && ln -sf \"\$target\" /usr/bin/\$f; ")
            append("fi; done")
        },
        working_dir = "/root",
        on_log = onLog
    )
    if (toolchain_manager.is_git_installed()) {
        proot_manager.execute_command_with_environment(
            command = "git config --global --get init.defaultBranch >/dev/null 2>&1 || git config --global init.defaultBranch main",
            working_dir = "/root",
            on_log = onLog
        )
    }
    return true
}
