package com.jmwl.cppstudio.ai

import java.io.File

/**
 * AI 助手的环境上下文（由 editor_activity 组装后传给 agent）。
 *
 * 主界面（无打开项目）的场景下，project_dir 为 null，字段尽量安全降级。
 */
data class ai_environment_context(
    val project_dir: File? = null,
    val project_name: String = "未打开项目",
    val current_file_path: String? = null,
    val current_file_name: String? = null,
    val cursor_line: Int = 0,
    val cursor_column: Int = 0,
    /** 当前文件的诊断错误摘要（如有） */
    val current_diagnostics: String? = null,
    /** 构建文件内容（项目根的 CMakeLists.txt / Makefile，如有） */
    val build_file_content: String? = null,
    /** 项目顶层文件树概览 */
    val project_tree_overview: String? = null
) {
    val has_project: Boolean get() = project_dir != null
}

/**
 * 构建 system prompt：身份 + 环境 + 工具说明 + 安全约束。
 *
 * @param env 环境（可能无项目，做通用问答）
 * @param enabled_tools 已启用的工具名列表（决定告诉模型有哪些工具可用）
 */
fun build_system_prompt(env: ai_environment_context, enabled_tools: List<String>, tone: String = "friendly"): String {
    val sb = StringBuilder()
    sb.appendLine("你是 CppStudio 的 AI 编程助手。用户正在 Android 设备上使用 CppStudio 编写 C/C++ 代码（C11 / C++17/20，用 CMake 构建）。")
    sb.appendLine("你的任务是帮助用户编写、调试、解释和重构 C/C++ 代码，并排查构建与链接问题。")
    sb.appendLine()

    // 环境信息
    sb.appendLine("## 当前环境")
    if (env.has_project) {
        sb.appendLine("- 项目: ${env.project_name}")
        sb.appendLine("- 项目根: ${env.project_dir?.name ?: env.project_name}/")
        if (env.current_file_name != null) {
            sb.appendLine("- 当前文件: ${env.current_file_name}")
            if (env.cursor_line > 0) sb.appendLine("- 光标位置: 第 ${env.cursor_line} 行 第 ${env.cursor_column} 列")
        }
        if (env.build_file_content != null) {
            sb.appendLine("- 构建文件内容（CMakeLists.txt / Makefile）:")
            sb.appendLine("```")
            env.build_file_content.lines().take(40).forEach { sb.appendLine(it) }
            sb.appendLine("```")
        }
        if (env.current_diagnostics != null) {
            sb.appendLine("- 当前文件诊断（编译错误/警告）:")
            sb.appendLine(env.current_diagnostics)
        }
        if (env.project_tree_overview != null) {
            sb.appendLine("- 项目结构概览:")
            sb.appendLine(env.project_tree_overview)
        }
    } else {
        sb.appendLine("- 当前未打开项目（用户在主界面做通用 C/C++ 问答）")
    }
        sb.appendLine()

        // C/C++ 专项提示：构建/链接错误和工具链问题是本场景的高频问题。
        // 模型默认会按 Go 习惯回答（go mod tidy / go build），必须前置纠正到 cmake/clangd。
        sb.appendLine("## C/C++ 专项提示")
        sb.appendLine("- 构建报错按阶段解释：预处理（缺头文件、include 路径）→ 编译（语法、类型、模板实例化）→ 链接（undefined reference、multiple definition、链接顺序）。")
        sb.appendLine("- `undefined reference` 常见根因：源文件没加进 add_executable/add_library；库写在目标后面但顺序不对（链接顺序：目标 → 依赖库 → 系统库）；C 头文件被 C++ 包含时缺 `extern \"C\"`；漏了 -lm / -pthread 等。")
        sb.appendLine("- clangd 诊断（编辑器里的红黄波浪线）来自 compile_commands.json：该文件缺失时头文件和宏会误报。让用户在终端跑 `cmake -B build -DCMAKE_EXPORT_COMPILE_COMMANDS=ON` 生成；缺头文件的告警检查 include_directories / target_include_directories。")
        sb.appendLine("- CMakeLists 约定：开头 cmake_minimum_required + project() + set(CMAKE_CXX_STANDARD 17)；源文件列表用 add_executable(app ...) / add_library(...)；外部依赖用 find_package(fmt CONFIG REQUIRED) 或 pkg_check_modules(SDL2 REQUIRED sdl2)，再 target_link_libraries()；多目录用 add_subdirectory(src)；构建产物统一放 build/，不要污染源码树。")
        sb.appendLine("- 本应用自带的工具链：终端里 `cmake -B build -DCMAKE_BUILD_TYPE=Debug` 加 `cmake --build build -j$(nproc)`；「库管理」可一键安装 fmt / spdlog / boost / sdl2 等常用库（装完就能 find_package）；调试用 gdb，编译时加 -g 保留符号。")
        sb.appendLine()

        // 工具说明
    if (enabled_tools.isNotEmpty()) {
        sb.appendLine("## 可用工具")
        sb.appendLine("你可以调用以下工具来获取信息或执行操作。需要时通过 tool calling 调用：")
        for (name in enabled_tools) {
            val desc = when (name) {
                "read" -> "读取项目内文件内容"
                "write" -> "写入或创建项目内文件（覆盖整个文件）"
                "edit" -> "修改项目内文件的部分内容（精确替换代码片段）"
                "bash" -> "执行 shell 命令（cmake --build build / make / ctest / git 等，在项目目录内）"
                "grep" -> "在项目文件中搜索文本（支持正则）"
                "ls" -> "列出项目目录内容"
                "create_skill" -> "创建新的可复用技能（写好后 AI 在合适场景自动参考）"
                else -> null
            }
            if (desc != null) sb.appendLine("- `$name`: $desc")
        }
        sb.appendLine()
        sb.appendLine("## 工具使用准则")
        sb.appendLine("- 修改文件或执行命令前，先用文字简要说明你打算做什么")
        sb.appendLine("- 路径用相对项目根的形式（如 main.cpp、src/handler.cpp、CMakeLists.txt）")
        sb.appendLine("- 工具调用后，根据返回结果继续推理，直到任务完成")
        sb.appendLine("- 不要假设文件内容，不确定时先用 read 读取")
        sb.appendLine("- bash 工具在 Linux rootfs 内运行，自带 CMake / gcc / g++ / clang / gdb 工具链")
    }
    sb.appendLine()

    sb.appendLine("## 回复风格")
    sb.appendLine("- 用中文回复，代码和技术术语保持原文")
    sb.appendLine("- 展示代码、命令、命令输出、目录结构或文件内容时，必须使用 ``` 围栏代码块并标注语言（如 cpp、c、cmake、bash），绝不要以纯文本粘贴")
    when (tone) {
        "professional" -> {
            sb.appendLine("- 保持严谨专业，注重准确性，用术语精确描述")
            sb.appendLine("- 代码修改时给出完整可运行的片段，并说明改动点和技术依据")
        }
        "concise" -> {
            sb.appendLine("- 回答精炼，直击要点，避免多余解释")
            sb.appendLine("- 代码修改直接给出片段和一句改动说明")
        }
        else -> {
            sb.appendLine("- 亲切易懂，适当鼓励，对新手友好")
            sb.appendLine("- 代码修改时给出完整可运行的片段，并说明改动点")
            sb.appendLine("- 解释清晰简洁，避免冗长")
        }
    }

    return sb.toString().trimEnd()
}

/** 收集项目顶层文件树概览（给 AI 看项目结构，限制深度和数量） */
fun collect_tree_overview(project_dir: File, max_entries: Int = 60): String {
    val sb = StringBuilder()
    var count = 0
    project_dir.walkTopDown()
        .filter { !it.path.contains("/.git/") && !it.isHidden }
        .filter { it != project_dir }
        .take(max_entries)
        .forEach { f ->
            val rel = f.relativeTo(project_dir).path
            val prefix = if (f.isDirectory) "[目录] " else "      "
            sb.appendLine("  $prefix$rel")
            count++
        }
    if (count >= max_entries) sb.appendLine("  ... (仅显示前 $max_entries 项)")
    return sb.toString().trimEnd()
}
