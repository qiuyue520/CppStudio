package com.xiaonai.cppstudio.lsp.clangd

import com.xiaonai.cppstudio.toolchain.runtime.toolchain_runtime_paths
import io.github.rosemoe.sora.lsp.client.languageserver.LspFeature
import java.io.File

/**
 * clangd LSP 配置。
 *
 * clangd 由 apk 装进 proot rootfs 的 /usr/bin/clangd（clang-extra-tools 包）。
 * 它靠 compile_commands.json 理解「这个文件用什么编译参数」；CppStudio 的
 * CMake 模板都开了 CMAKE_EXPORT_COMPILE_COMMANDS，构建目录里会生成该文件，
 * 所以这里把 --compile-commands-dir 指向项目 build 目录。
 *
 * 没有编译数据库时 clangd 会退化到内建启发式（补全/跳转仍可用，诊断会变少），
 * 所以缺 build/ 不是错误，不阻断连接。
 *
 * @param runtime_paths proot 运行时路径
 * @param project_dir 项目目录（host 路径，proot 会绑定）
 * @param path guest 内 PATH（含 /usr/bin）
 * @param clangd_command guest 内 clangd 路径（默认 /usr/bin/clangd）
 * @param build_dir 编译数据库目录（host 路径；默认 <project>/build）
 * @param extra_environment 额外环境变量
 * @param disabled_features 禁用的 LSP 功能（编辑器设置里的开关）
 * @param extra_arguments 额外 clangd 命令行参数
 * @param on_stderr clangd stderr 日志回调
 */
data class clangd_lsp_config(
    val runtime_paths: toolchain_runtime_paths,
    val project_dir: File,
    val path: String,
    val clangd_command: String = "/usr/bin/clangd",
    val build_dir: File = File(project_dir, "build"),
    val extra_environment: Map<String, String> = emptyMap(),
    val disabled_features: Set<LspFeature> = emptySet(),
    val extra_arguments: List<String> = emptyList(),
    val on_stderr: (String) -> Unit = {}
) {
    init {
        require(clangd_command.isNotBlank()) { "clangd command path is required" }
    }

    /** guest 内看到的编译数据库目录（项目已 bind 到 guest 同名 host 路径）。 */
    fun build_dir_arg(): String = build_dir.absolutePath

    /**
     * clangd 启动参数。
     * - `--compile-commands-dir`：读 CMake 导出的 compile_commands.json
     * - `--background-index`：后台索引整个项目（跨文件跳转、补全质量关键）
     * - `--pch-storage=memory`：手机上避免写大量 PCH 文件
     * - `--header-insertion=never`：不在补全时自动插 #include（手机屏幕小，避免误触）
     * - `--log=error`：只把错误写进输出面板
     */
    fun arguments(): List<String> {
        val defaults = listOf(
            "--compile-commands-dir=" + build_dir_arg(),
            "--background-index",
            "--pch-storage=memory",
            "--header-insertion=never",
            "--log=error",
            "--limit-results=100",
            "--completion-style=detailed",
            "--function-arg-placeholders"
        )
        return listOf(clangd_command) + defaults + extra_arguments
    }
}
