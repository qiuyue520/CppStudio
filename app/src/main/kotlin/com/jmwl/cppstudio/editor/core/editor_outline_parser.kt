package com.jmwl.cppstudio.editor.core

/**
 * 当前文件的代码结构（大纲）符号。
 * clangd documentSymbol 未接入/未连接时的兜底解析也用它，
 * 两种来源统一成同一模型供结构面板消费。
 */
data class editor_outline_symbol(
    val name: String,
    val kind: editor_outline_kind,
    /** 附加信息：函数参数摘要、继承关系、宏展开体等。 */
    val detail: String,
    /** 0 起始行号。 */
    val line: Int,
    /** 层级缩进（顶层 0，类成员/命名空间成员 1）。 */
    val depth: Int
)

enum class editor_outline_kind {
    FUNCTION, METHOD, STRUCT, CLASS, NAMESPACE, ENUM, TYPE_ALIAS, MACRO, VARIABLE, FIELD
}

/**
 * 轻量 C/C++ 大纲解析（正则逐行扫描，跳过注释与字符串字面量）。
 * clangd 可用时优先走 LSP documentSymbol，本解析器作为离线兜底。
 *
 * 覆盖：namespace / class / struct / union / enum、函数定义与声明、类成员函数、
 * typedef / using 别名、#define 宏、文件级 const/static 变量。
 */
object editor_outline_parser {

    /** 函数：返回类型 + 名字 + 参数列表。 */
    private val function_pattern = Regex(
        "^((?:(?:inline|static|virtual|constexpr|extern|const|unsigned|signed|long|short)\\s+)*" +
            "[A-Za-z_][\\w:<>,\\s*&]*?[\\s*&]+)([~A-Za-z_][\\w:~]*)\\s*\\(([^)]*)\\)"
    )
    private val class_pattern = Regex("^\\b(class|struct|union)\\s+([A-Za-z_][\\w:]*)\\s*(?::([^{]*))?\\{?")
    private val namespace_pattern = Regex("^\\bnamespace\\s+([A-Za-z_][\\w:]*)\\s*\\{?")
    private val enum_pattern = Regex("^\\benum(?:\\s+class|\\s+struct)?\\s+([A-Za-z_][\\w]*)\\s*(?::([^{]*))?\\{?")
    private val using_alias_pattern = Regex("^\\busing\\s+([A-Za-z_][\\w]*)\\s*=\\s*([^;]+);")
    private val typedef_pattern = Regex("^\\btypedef\\s+(.+?)\\s+([A-Za-z_][\\w]*)\\s*;")
    private val macro_pattern = Regex("^\\s*#\\s*define\\s+([A-Za-z_][\\w]*)\\s*(.*)$")
    private val variable_pattern = Regex("^\\b(?:const|constexpr|static|extern)\\s+.+?([A-Za-z_][\\w]*)\\s*(?:=|;)")

    private val control_keywords = setOf(
        "if", "for", "while", "switch", "catch", "return", "sizeof", "decltype",
        "static_assert", "alignof", "new", "delete", "throw", "do", "else"
    )

    /** 解析 [source] 的大纲符号；[file_name] 用于判断是否 C/C++ 文件（非 C/C++ 返回空表）。 */
    fun parse_cpp_outline(source: String, file_name: String): List<editor_outline_symbol> {
        if (!is_cpp_outline_target(file_name)) return emptyList()
        val symbols = mutableListOf<editor_outline_symbol>()
        var in_block_comment = false
        var scope_depth = 0
        var in_class = false

        source.lines().forEachIndexed { index, raw ->
            var line = strip_line(raw)
            var trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEachIndexed

            // 多行注释状态机
            if (in_block_comment) {
                if ("*/" in line) {
                    in_block_comment = false
                    line = line.substringAfter("*/")
                } else {
                    return@forEachIndexed
                }
            }
            if ("/*" in line && "*/" !in line.substringAfter("/*")) {
                in_block_comment = true
                line = line.substringBefore("/*")
            }
            trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("//")) return@forEachIndexed

            // 作用域深度：先记下本行之前的深度，再按本行花括号增减
            val net_braces = trimmed.count { it == '{' } - trimmed.count { it == '}' }
            val depth_before = scope_depth
            if (net_braces < 0) in_class = false

            macro_pattern.find(trimmed)?.let { match ->
                symbols += editor_outline_symbol(
                    name = match.groupValues[1],
                    kind = editor_outline_kind.MACRO,
                    detail = match.groupValues[2].trim().take(60),
                    line = index,
                    depth = 0
                )
                return@forEachIndexed
            }

            namespace_pattern.find(trimmed)?.let { match ->
                symbols += editor_outline_symbol(
                    match.groupValues[1],
                    editor_outline_kind.NAMESPACE,
                    "namespace",
                    index,
                    depth_before.coerceAtLeast(0)
                )
                scope_depth = (scope_depth + net_braces.coerceAtLeast(1)).coerceAtLeast(0)
                return@forEachIndexed
            }

            class_pattern.find(trimmed)?.let { match ->
                val keyword = match.groupValues[1]
                val bases = match.groupValues[3].trim()
                symbols += editor_outline_symbol(
                    name = match.groupValues[2],
                    kind = if (keyword == "class") editor_outline_kind.CLASS else editor_outline_kind.STRUCT,
                    detail = if (bases.isBlank()) keyword else "$keyword : $bases",
                    line = index,
                    depth = depth_before.coerceAtLeast(0)
                )
                if (trimmed.contains('{')) {
                    in_class = true
                    scope_depth += 1
                }
                return@forEachIndexed
            }

            enum_pattern.find(trimmed)?.let { match ->
                val base = match.groupValues[2].trim()
                symbols += editor_outline_symbol(
                    name = match.groupValues[1],
                    kind = editor_outline_kind.ENUM,
                    detail = if (base.isBlank()) "enum" else "enum : $base",
                    line = index,
                    depth = depth_before.coerceAtLeast(0)
                )
                if (trimmed.contains('{')) scope_depth += 1
                return@forEachIndexed
            }

            using_alias_pattern.find(trimmed)?.let { match ->
                symbols += editor_outline_symbol(
                    match.groupValues[1],
                    editor_outline_kind.TYPE_ALIAS,
                    match.groupValues[2].trim(),
                    index,
                    depth_before.coerceAtLeast(0)
                )
                return@forEachIndexed
            }

            typedef_pattern.find(trimmed)?.let { match ->
                symbols += editor_outline_symbol(
                    match.groupValues[2],
                    editor_outline_kind.TYPE_ALIAS,
                    match.groupValues[1].trim(),
                    index,
                    depth_before.coerceAtLeast(0)
                )
                return@forEachIndexed
            }

            function_pattern.find(trimmed)?.let { match ->
                val return_type = match.groupValues[1].trim()
                val name = match.groupValues[2].trim()
                val params = match.groupValues[3].trim()
                if (name in control_keywords) return@forEachIndexed
                if (return_type.isEmpty() && name.isEmpty()) return@forEachIndexed
                val is_member = in_class && depth_before > 0
                symbols += editor_outline_symbol(
                    name = name,
                    kind = if (is_member) editor_outline_kind.METHOD else editor_outline_kind.FUNCTION,
                    detail = buildString {
                        if (return_type.isNotBlank()) append(return_type)
                        append("(")
                        append(params.split(',').joinToString(", ") { it.trim().substringBefore('=').trim() })
                        append(")")
                    }.trim(),
                    line = index,
                    depth = if (is_member) 1 else depth_before.coerceAtLeast(0)
                )
                return@forEachIndexed
            }

            if (depth_before == 0) {
                variable_pattern.find(trimmed)?.let { match ->
                    symbols += editor_outline_symbol(
                        name = match.groupValues[1],
                        kind = editor_outline_kind.VARIABLE,
                        detail = "",
                        line = index,
                        depth = 0
                    )
                }
            }

            scope_depth = (scope_depth + net_braces).coerceAtLeast(0)
            if (scope_depth == 0) in_class = false
        }
        return symbols
    }

    private fun is_cpp_outline_target(file_name: String): Boolean {
        val lower = file_name.lowercase()
        return cpp_source_suffixes.any { lower.endsWith(it) } ||
            lower.endsWith(".cmake") ||
            lower == "cmakelists.txt"
    }

    /** 去掉行内注释与字符串字面量内容（保留引号本身），避免注释/字符串里的关键字被当成符号。 */
    private fun strip_line(raw: String): String {
        val sb = StringBuilder(raw.length)
        var i = 0
        var quote: Char? = null
        while (i < raw.length) {
            val c = raw[i]
            if (quote != null) {
                if (c == '\\') {
                    i += 2
                    continue
                }
                if (c == quote) {
                    sb.append(c)
                    quote = null
                }
                i++
                continue
            }
            when {
                c == '"' || c == '\'' -> {
                    quote = c
                    sb.append(c)
                }
                c == '/' && i + 1 < raw.length && raw[i + 1] == '/' -> return sb.toString()
                else -> sb.append(c)
            }
            i++
        }
        return sb.toString()
    }
}
