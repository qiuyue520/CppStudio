package com.jmwl.cppstudio.toolchain

import java.io.File

/**
 * C/C++ 工具链探测结果。
 *
 * 所有组件都装在 proot Alpine rootfs 里（apk 包），host 侧只做文件存在性检查：
 * 起一次 proot 拿版本号要几百毫秒，而本对象在 Compose 重组热路径上被读取。
 *
 * @param version 编译器版本（如 "14.2.0"，来自 apk 包数据库，拿不到时为空串）
 * @param compiler_path guest 内编译器路径（/usr/bin/g++）
 */
data class cpp_toolchain_info(
    val version: String,
    val compiler_path: String,
    val c_compiler_path: String
)

/**
 * 项目构建环境。
 *
 * @param environment proot 内的环境变量（PATH/CC/CXX 等）
 * @param cpp 已装的编译工具链；null 表示未装
 * @param missing 缺失项的人类可读描述
 */
data class project_toolchain_environment(
    val environment: Map<String, String>,
    val cpp: cpp_toolchain_info?,
    val missing: List<String>
)

/**
 * C/C++ 工具链管理器。
 *
 * 组件来源（全部 apk 包）：
 * - build-base    gcc / g++ / make / musl-dev / binutils（编译核心）
 * - cmake         CMake 构建系统
 * - ninja         更快的生成器（可选）
 * - clang / clang-extra-tools  clang 编译器 + clangd / clang-format
 * - gdb           调试器
 * - git           版本控制
 */
object toolchain_manager {

    private const val PROOT_CPPSTUDIO_HOME = "/home/cppstudio"
    private const val PROOT_BIN = "/usr/bin"

    /** guest 内的基础 PATH（标准 Linux 目录）。 */
    private val base_proot_path = listOf(
        "/home/.local/bin",
        "/usr/local/sbin",
        "/usr/local/bin",
        "/bin",
        "/usr/bin",
        "/sbin",
        "/usr/sbin"
    )

    fun proot_path(extra_paths: List<String> = emptyList()): String {
        return (extra_paths + base_proot_path)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(":")
    }

    /**
     * 探测已安装的编译工具链（只做文件存在性检查，结果走内存缓存）。
     *
     * 判定标准：rootfs 里同时存在 /usr/bin/gcc 与 /usr/bin/g++（build-base 的标志）。
     * 版本号从 apk 包数据库读取，无需运行进程。
     */
    fun installed_cpp(): cpp_toolchain_info? {
        if (cpp_probe_resolved) return cpp_probe_cache
        return probe_installed_cpp().also {
            cpp_probe_cache = it
            cpp_probe_resolved = true
        }
    }

    /** 工具链/库安装卸载后调用，让 [installed_cpp] 下次重新探测磁盘。 */
    fun invalidate_cpp_probe() {
        cpp_probe_resolved = false
        cpp_probe_cache = null
        installed_packages_cache = null
    }

    /** 兼容旧调用点（原 Go 版本的命名）。 */
    fun invalidate_go_probe() = invalidate_cpp_probe()

    @Volatile
    private var cpp_probe_cache: cpp_toolchain_info? = null

    @Volatile
    private var cpp_probe_resolved: Boolean = false

    @Volatile
    private var installed_packages_cache: Map<String, String>? = null

    /** 已安装 apk 包缓存（库管理页与工具链卡片共用，避免反复解析数据库）。 */
    fun installed_packages(): Map<String, String> {
        installed_packages_cache?.let { return it }
        val fresh = apk_packages.installed()
        installed_packages_cache = fresh
        return fresh
    }

    private fun probe_installed_cpp(): cpp_toolchain_info? {
        val rootfs = toolchain_runtime_provider.paths().rootfs_dir
        val gcc = File(rootfs, "usr/bin/gcc")
        val gpp = File(rootfs, "usr/bin/g++")
        if (!gcc.exists() && !gpp.exists()) return null

        val packages = installed_packages()
        val version = packages["gcc"].orEmpty()

        return cpp_toolchain_info(
            version = version,
            compiler_path = "$PROOT_BIN/g++",
            c_compiler_path = "$PROOT_BIN/gcc"
        )
    }

    /** 编译器（build-base）是否已安装。 */
    fun is_cpp_installed(): Boolean = installed_cpp() != null

    /** 兼容旧调用点。 */
    fun is_go_installed(): Boolean = is_cpp_installed()

    private fun rootfs_file(relative_path: String): File =
        File(toolchain_runtime_provider.paths().rootfs_dir, relative_path)

    /** CMake 是否已安装（rootfs 内 /usr/bin/cmake）。 */
    fun is_cmake_installed(): Boolean = rootfs_file("usr/bin/cmake").isFile

    /** clangd 是否已安装（/usr/bin/clangd 或 /usr/lib/llvmXX/bin/clangd）。 */
    fun is_clangd_installed(): Boolean {
        if (rootfs_file("usr/bin/clangd").isFile) return true
        val llvm_root = rootfs_file("usr/lib")
        return llvm_root.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("llvm") }
            ?.any { File(it, "bin/clangd").isFile }
            ?: false
    }

    /** guest 内 clangd 的可执行路径。 */
    fun clangd_proot_path(): String {
        if (rootfs_file("usr/bin/clangd").isFile) return "$PROOT_BIN/clangd"
        val llvm_root = rootfs_file("usr/lib")
        val versioned = llvm_root.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("llvm") }
            ?.sortedByDescending { it.name }
            ?.firstOrNull { File(it, "bin/clangd").isFile }
        return if (versioned != null) "/usr/lib/${versioned.name}/bin/clangd" else "$PROOT_BIN/clangd"
    }

    /** git 是否已安装（rootfs 内 /usr/bin/git）。 */
    fun is_git_installed(): Boolean = rootfs_file("usr/bin/git").isFile

    /** gdb 是否已安装。 */
    fun is_gdb_installed(): Boolean = rootfs_file("usr/bin/gdb").isFile

    /** clang-format 是否已安装（/usr/bin/clang-format 或 /usr/lib/llvmXX/bin/clang-format）。 */
    fun is_clang_format_installed(): Boolean {
        if (rootfs_file("usr/bin/clang-format").isFile) return true
        return rootfs_file("usr/lib").listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("llvm") }
            ?.any { File(it, "bin/clang-format").isFile }
            ?: false
    }

    /** guest 内 clang-format 的可执行路径。 */
    fun clang_format_proot_path(): String {
        if (rootfs_file("usr/bin/clang-format").isFile) return "$PROOT_BIN/clang-format"
        val llvm_root = rootfs_file("usr/lib")
        val versioned = llvm_root.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("llvm") }
            ?.sortedByDescending { it.name }
            ?.firstOrNull { File(it, "bin/clang-format").isFile }
        return if (versioned != null) "/usr/lib/${versioned.name}/bin/clang-format" else "$PROOT_BIN/clang-format"
    }

    /** ninja 是否已安装。 */
    fun is_ninja_installed(): Boolean = rootfs_file("usr/bin/ninja").isFile

    /**
     * 组装项目构建环境。
     *
     * @param project_path 项目 host 路径（仅用于日志）
     */
    fun project_environment(project_path: String): project_toolchain_environment {
        val cpp = installed_cpp()
        val missing = mutableListOf<String>()
        if (cpp == null) missing += "C/C++ 工具链未安装，请在「开发工具」安装 build-base"
        if (!is_cmake_installed()) missing += "CMake 未安装，请在「开发工具」安装 cmake"

        val environment = linkedMapOf(
            "CPPSTUDIO_HOME" to PROOT_CPPSTUDIO_HOME,
            "CC" to (cpp?.c_compiler_path ?: "$PROOT_BIN/gcc"),
            "CXX" to (cpp?.compiler_path ?: "$PROOT_BIN/g++"),
            // 中文注释/字符串按 UTF-8 处理，否则 gcc 在 C locale 下可能告警
            "LANG" to "C.UTF-8",
            "LC_ALL" to "C.UTF-8",
            "PATH" to proot_path()
        )
        return project_toolchain_environment(
            environment = environment,
            cpp = cpp,
            missing = missing
        )
    }

    /**
     * 清理 .bashrc/.profile 里的工具链环境块（历史版本写入的残留）。
     * 前三组 marker 保留，用于清理旧版（XCode / GoStudio / CppStudio 早期）写入的内容。
     */
    fun cleanup_removed_toolchain_environment() {
        val markers_list = listOf(
            "# >>> XCode toolchain environment >>>" to "# <<< XCode toolchain environment <<<",
            "# >>> xcode toolchain environment >>>" to "# <<< xcode toolchain environment <<<",
            "# >>> GoStudio toolchain environment >>>" to "# <<< GoStudio toolchain environment <<<",
            "# >>> CppStudio toolchain environment >>>" to "# <<< CppStudio toolchain environment <<<"
        )
        listOf(".bashrc", ".profile").forEach { file_name ->
            val file = File(toolchain_runtime_provider.paths().home_dir, file_name)
            if (!file.exists()) return@forEach
            val current = file.readText()
            val cleaned = markers_list.fold(current) { content, (start, end) ->
                remove_block(content, start, end)
            }
            if (cleaned != current) file.writeText(cleaned.trimEnd() + "\n")
        }
    }

    private fun remove_block(content: String, start_marker: String, end_marker: String): String {
        val start = content.indexOf(start_marker)
        val end = content.indexOf(end_marker)
        if (start < 0 || end < start) return content
        val after_end = end + end_marker.length
        val cleaned = (content.substring(0, start).trimEnd() + "\n" + content.substring(after_end).trimStart()).trimEnd()
        return remove_block(cleaned, start_marker, end_marker)
    }
}
