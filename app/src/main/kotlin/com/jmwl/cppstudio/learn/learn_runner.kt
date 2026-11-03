package com.jmwl.cppstudio.learn

import com.jmwl.cppstudio.toolchain.proot_manager
import com.jmwl.cppstudio.toolchain.toolchain_manager
import com.jmwl.cppstudio.toolchain.toolchain_runtime_provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** 判题结果。 */
data class exercise_result(
    val passed: Boolean,
    /** 是否编译通过（false = 编译错误）。 */
    val compiled: Boolean,
    val output: String,
    val message: String
)

/**
 * 交互练习执行器（移植自 CodeAssist LearnBackend 的 check 流程，C/C++ 版）：
 * 把学习者代码写入常驻 scratch 目录（一个 `main.cpp`），在 proot 里
 * `g++ -std=c++17` 编译成 `main` 再运行，捕获 stdout 后按 [exercise_check] 判题：
 * 输出比对 + 反硬编码（require_source 必须真的出现在源码里）。
 *
 * 编译和运行分成两步：编译失败会给出编译器输出，运行期崩溃（段错误等）
 * 则照常按「输出不对」处理，不会被误报成编译错误。
 */
object learn_runner {

    /** 首次编译 + 运行的上限；死循环会在这里被掐断。 */
    private const val RUN_TIMEOUT_MS = 60_000L

    /** scratch 目录（host 侧路径，guest 内对应 /home/cppstudio/learn-scratch）。 */
    val scratch_dir: File
        get() = File(toolchain_runtime_provider.paths().cppstudio_dir, "learn-scratch")

    /** 编译器（build-base）是否已安装。 */
    fun is_cpp_available(): Boolean = toolchain_manager.is_cpp_installed()

    /** 兼容旧调用点（学习页历史上按 Go 命名调用）。 */
    fun is_go_available(): Boolean = is_cpp_available()

    /**
     * 预热：先编译一次空程序，确认工具链可用（g++ 没有跨进程编译缓存，
     * 这一步主要是把 rootfs 的二进制与动态库预热进页缓存）。
     */
    suspend fun warm_up(): Boolean {
        val dir = scratch_dir
        return withContext(Dispatchers.IO) {
            dir.mkdirs()
            val source = File(dir, "warmup.cpp")
            source.writeText("int main() { return 0; }\n")
            val guest_dir = toolchain_runtime_provider.paths().host_to_guest_path(dir.absolutePath)
            val environment = toolchain_manager.project_environment(dir.absolutePath).environment
            withTimeoutOrNull(RUN_TIMEOUT_MS) {
                proot_manager.execute_command_with_environment(
                    command = "g++ -std=c++17 -o warmup warmup.cpp && ./warmup",
                    working_dir = guest_dir,
                    extra_environment = environment,
                    on_log = { }
                )
            } ?: false
        }
    }

    /**
     * 编译并运行学习者代码，再判题。
     */
    suspend fun check(code: String, check: exercise_check): exercise_result {
        if (!is_cpp_available()) {
            return exercise_result(false, false, "", "C/C++ 工具链未安装（需要 build-base），请先到「开发工具」页安装")
        }

        val dir = scratch_dir
        val guest_dir = withContext(Dispatchers.IO) {
            dir.mkdirs()
            File(dir, "main.cpp").writeText(code)
            toolchain_runtime_provider.paths().host_to_guest_path(dir.absolutePath)
        }
        val environment = toolchain_manager.project_environment(dir.absolutePath).environment

        // ---- 第一步：编译 ----
        val compile_log = StringBuilder()
        val compiled = run_command(
            command = "g++ -std=c++17 -O2 -Wall -o main main.cpp",
            working_dir = guest_dir,
            environment = environment
        ) { line -> compile_log.appendLine(line) }
        if (!compiled) {
            val first_error = compile_log.toString().lineSequence()
                .firstOrNull { it.contains("error", ignoreCase = true) }
            return exercise_result(
                passed = false,
                compiled = false,
                output = compile_log.toString().trimEnd(),
                message = "代码没有通过编译，看看下面的错误再试一次。" + (first_error?.let { "\n$it" } ?: "")
            )
        }

        // ---- 第二步：运行 ----
        val raw_output = StringBuilder()
        val program_output = mutableListOf<String>()
        // stdin 接到 /dev/null：练习里不会有交互输入，避免 cin >> 把判题卡到超时
        val ran = run_command(
            command = "./main < /dev/null",
            working_dir = guest_dir,
            environment = environment
        ) { line ->
            raw_output.appendLine(line)
            extract_program_output(line)?.let(program_output::add)
        }
        if (!ran && program_output.isEmpty()) {
            return exercise_result(
                passed = false,
                compiled = true,
                output = raw_output.toString().trimEnd(),
                message = "程序运行时出错了（可能是段错误、越界或超时），看看下面的输出。"
            )
        }

        val actual = normalize(program_output.joinToString("\n"), check.case_sensitive)
        val output_ok = when {
            check.expected_output != null -> actual == normalize(check.expected_output, check.case_sensitive)
            check.must_contain.isNotEmpty() -> check.must_contain.all { actual.contains(normalize(it, check.case_sensitive)) }
            else -> true
        }
        val missing = missing_constructs(code, check.require_source)
        val passed = output_ok && missing.isEmpty()
        val message = when {
            passed -> "回答正确，漂亮！"
            !output_ok && check.expected_output != null ->
                "还差一点。期望输出：\n${check.expected_output}"
            !output_ok ->
                "还差一点。输出里应该包含：${check.must_contain.joinToString("、")}"
            else ->
                "输出对了，但练习要求你真的用上 ${missing.joinToString("、") { "`$it`" }} —— 不能只把答案打印出来哦。"
        }
        return exercise_result(passed, true, program_output.joinToString("\n").trimEnd(), message)
    }

    /**
     * 在 scratch 目录里执行一条命令。
     * [on_line] 收到 stdout/stderr 的每一行（主线程回调）。
     */
    private suspend fun run_command(
        command: String,
        working_dir: String,
        environment: Map<String, String>,
        on_line: (String) -> Unit
    ): Boolean {
        val result = withTimeoutOrNull(RUN_TIMEOUT_MS) {
            proot_manager.execute_command_with_environment(
                command = command,
                working_dir = working_dir,
                extra_environment = environment,
                on_log = { line -> on_line(line) }
            )
        }
        if (result == null) {
            on_line("运行超时（${RUN_TIMEOUT_MS / 1000}s），检查是否有死循环或一直在等输入")
            return false
        }
        return result
    }

    private fun normalize(s: String, case_sensitive: Boolean): String {
        val t = s.replace("\r\n", "\n").lines().joinToString("\n") { it.trimEnd() }.trim()
        return if (case_sensitive) t else t.lowercase()
    }

    /**
     * proot shell 会给进程输出加 `[OUT] --` 前缀，登录 shell 还会输出欢迎语。
     * 判题只关心学习者程序的 stdout，因此在这里还原并过滤外壳产生的行。
     */
    private fun extract_program_output(line: String): String? {
        val trimmed = line.trimStart()
        if (!trimmed.startsWith("[OUT] ")) return null

        var output = trimmed.removePrefix("[OUT] ")
        if (output.startsWith("-- ")) output = output.removePrefix("-- ")
        if (output.equals("Welcome To CppStudio Terminal", ignoreCase = true)) return null
        return output
    }
}

/**
 * 反硬编码检查（移植自 CodeAssist missingConstructs）：
 * 源码剥掉注释与字符串内容、去空白后，required 里的每个构造都必须出现，
 * 否则学习者可以用 `std::cout << "5";` 骗过输出比对。
 */
internal fun missing_constructs(code: String, required: List<String>): List<String> {
    if (required.isEmpty()) return emptyList()
    val stripped = strip_comments_and_strings(code).filterNot { it.isWhitespace() }
    return required.filter { pat -> !stripped.contains(pat.filterNot { it.isWhitespace() }) }
}

/** 去掉行注释、块注释以及字符串字面量的内容（保留引号本身）。 */
internal fun strip_comments_and_strings(code: String): String {
    val sb = StringBuilder(code.length)
    var i = 0
    val n = code.length
    while (i < n) {
        val c = code[i]
        when {
            c == '/' && i + 1 < n && code[i + 1] == '/' -> {
                i += 2
                while (i < n && code[i] != '\n') i++
            }
            c == '/' && i + 1 < n && code[i + 1] == '*' -> {
                i += 2
                while (i < n && !(code[i] == '*' && i + 1 < n && code[i + 1] == '/')) i++
                i = (i + 2).coerceAtMost(n)
            }
            c == '"' || c == '`' || c == '\'' -> {
                sb.append(c)
                i++
                while (i < n && code[i] != c && code[i] != '\n') {
                    if (code[i] == '\\') i++
                    i++
                }
                if (i < n && code[i] == c) {
                    sb.append(c)
                    i++
                }
            }
            else -> {
                sb.append(c)
                i++
            }
        }
    }
    return sb.toString()
}
