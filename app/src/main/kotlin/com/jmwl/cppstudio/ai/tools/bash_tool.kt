package com.jmwl.cppstudio.ai.tools

import com.google.gson.JsonObject
import com.jmwl.cppstudio.toolchain.proot_manager
import java.io.File

/**
 * bash 工具：在 proot rootfs 内执行 shell 命令（cmake --build build / make / ctest / g++ 等）。
 *
 * 工作目录默认项目根，环境变量由调用方注入（工具链的 PATH、CC/CXX 等）。
 * 建议调用方带上 CC/CXX/LANG=C.UTF-8 提示，避免源码里的中文在构建日志里变成乱码、
 * 以及多编译器场景下选错 gcc / clang。
 * 输出按行收集后截断，防止上下文溢出。
 */
class bash_tool(
    private val project_root: File,
    private val extra_environment: Map<String, String> = emptyMap()
) : ai_tool {
    override val name = "bash"
    override val description = "在项目目录内执行 shell 命令（如 cmake --build build、make、ctest、g++、pkg-config、gdb、git 等）。命令在 Linux rootfs 内运行，自带 CMake / gcc / g++ / clang / gdb 工具链。"
    override val parameters_schema: JsonObject = com.google.gson.JsonParser.parseString("""
        {"type":"object","properties":{"command":{"type":"string","description":"要执行的 shell 命令（如 cmake --build build -j4、ctest --output-on-failure）"}},"required":["command"]}
    """.trimIndent()).asJsonObject

    override suspend fun execute(params: JsonObject): String {
        val command = params.string_or("command")
        if (command.isBlank()) return "缺少 command 参数"
        val output = StringBuilder()
        val success = proot_manager.execute_command_with_environment(
            command = command,
            working_dir = project_root.absolutePath,
            extra_environment = extra_environment,
            on_log = { line -> output.appendLine(line) }
        )
        val result = output.toString().trim()
        val truncated = if (result.isEmpty()) "(命令无输出)" else truncate_output(result)
        return "$truncated\n\n[退出码: ${if (success) "0 (成功)" else "非0 (失败)"}]"
    }
}
