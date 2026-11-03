package com.xiaonai.cppstudio.ui.screens.learn

import android.os.Bundle
import io.github.rosemoe.sora.lang.completion.CompletionHelper
import io.github.rosemoe.sora.lang.completion.CompletionItemKind
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference

/**
 * 学习页使用轻量语义补全：精确匹配 C++ 关键字，并为 `std::` 与常见成员函数提供提示。
 * 这里不接入 clangd，避免练习编辑器依赖完整项目环境（compile_commands.json）。
 */
class learn_cpp_language : TextMateLanguage(
    checkNotNull(GrammarRegistry.getInstance().findGrammar("source.cpp")),
    GrammarRegistry.getInstance().findLanguageConfiguration("source.cpp"),
    GrammarRegistry.getInstance(),
    ThemeRegistry.getInstance(),
    true
) {

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle
    ) {
        val line_before = content.getLine(position.line).take(position.column)

        // std:: / Type:: 之后：给命名空间或类里常见的名字
        scope_regex.find(line_before)?.let { match ->
            val receiver = match.groupValues[1]
            val prefix = match.groupValues[2]
            val members = if (receiver == "std") {
                namespace_members
            } else {
                type_members
            }
            publisher.addItems(
                members
                    .filter { it.first.startsWith(prefix, ignoreCase = true) }
                    .map { (label, detail) ->
                        SimpleCompletionItem(label, detail, prefix.length, label)
                            .kind(CompletionItemKind.Function)
                    }
            )
            return
        }

        // 变量 / 表达式之后：给容器、字符串、流对象常见的成员函数
        dot_regex.find(line_before)?.let { match ->
            val prefix = match.groupValues[2]
            publisher.addItems(
                object_members
                    .filter { it.first.startsWith(prefix, ignoreCase = true) }
                    .map { (label, detail) ->
                        SimpleCompletionItem(label, detail, prefix.length, label)
                            .kind(CompletionItemKind.Function)
                    }
            )
            return
        }

        val prefix = CompletionHelper.computePrefix(content, position) { char ->
            char.isLetterOrDigit() || char == '_'
        }
        if (prefix.isEmpty()) {
            return
        }

        val identifiers = mutableSetOf<String>().apply {
            for (line_index in 0 until content.lineCount) {
                identifier_regex.findAll(content.getLine(line_index)).forEach {
                    add(it.value)
                }
            }
        }

        val keyword_items = cpp_keywords
            .filter { it.startsWith(prefix, ignoreCase = true) }
            .map { keyword ->
                SimpleCompletionItem(keyword, "Keyword", prefix.length, keyword)
                    .kind(CompletionItemKind.Keyword)
            }
        val identifier_items = identifiers
            .filter { it != prefix && it.startsWith(prefix, ignoreCase = true) }
            .sorted()
            .take(30)
            .map { identifier ->
                SimpleCompletionItem(identifier, "Identifier", prefix.length, identifier)
                    .kind(CompletionItemKind.Identifier)
            }

        publisher.addItems(identifier_items + keyword_items)
    }

    private companion object {
        val scope_regex = Regex("""([A-Za-z_][A-Za-z0-9_]*)\s*::\s*([A-Za-z_][A-Za-z0-9_]*)?$""")
        val dot_regex = Regex("""([A-Za-z_][A-Za-z0-9_]*)\s*\.\s*([A-Za-z_][A-Za-z0-9_]*)?$""")
        val identifier_regex = Regex("""[A-Za-z_][A-Za-z0-9_]*""")

        /** C++ 关键字 / 常用类型名（补全候选）。 */
        val cpp_keywords = arrayOf(
            "alignas", "auto", "bool", "break", "case", "catch", "char", "class", "const",
            "constexpr", "continue", "default", "delete", "do", "double", "else", "enum",
            "explicit", "extern", "false", "float", "for", "friend", "if", "inline", "int",
            "long", "namespace", "new", "noexcept", "nullptr", "operator", "override",
            "private", "protected", "public", "return", "short", "sizeof", "static",
            "struct", "switch", "template", "this", "throw", "true", "try", "typedef",
            "typename", "union", "unsigned", "using", "virtual", "void", "volatile", "while",
            "std", "size_t", "string", "vector", "map"
        )

        private fun member(label: String, detail: String) = label to detail

        /** `std::` 之后的名字。 */
        val namespace_members = listOf(
            member("cout", "std::ostream&"),
            member("cin", "std::istream&"),
            member("cerr", "std::ostream&"),
            member("endl", "std::ostream&"),
            member("string", "std::string"),
            member("vector", "std::vector<T>"),
            member("array", "std::array<T, N>"),
            member("map", "std::map<K, V>"),
            member("unordered_map", "std::unordered_map<K, V>"),
            member("set", "std::set<T>"),
            member("pair", "std::pair<A, B>"),
            member("make_pair", "std::pair<A, B>"),
            member("optional", "std::optional<T>"),
            member("string_view", "std::string_view"),
            member("unique_ptr", "std::unique_ptr<T>"),
            member("shared_ptr", "std::shared_ptr<T>"),
            member("make_unique", "std::unique_ptr<T>"),
            member("make_shared", "std::shared_ptr<T>"),
            member("move", "T&&"),
            member("sort", "std::sort(begin, end)"),
            member("find", "std::find(begin, end, value)"),
            member("reverse", "std::reverse(begin, end)"),
            member("accumulate", "std::accumulate(begin, end, init)"),
            member("to_string", "std::string"),
            member("stoi", "int"),
            member("stod", "double"),
            member("thread", "std::thread"),
            member("mutex", "std::mutex"),
            member("lock_guard", "std::lock_guard<std::mutex>"),
            member("async", "std::future<T>"),
            member("future", "std::future<T>"),
            member("runtime_error", "std::runtime_error"),
            member("exception", "std::exception"),
            member("out_of_range", "std::out_of_range"),
            member("filesystem", "std::filesystem")
        )

        /** 自定义类型 `::` 之后（静态成员、嵌套类型）：给最常见的几个。 */
        val type_members = listOf(
            member("npos", "static const size_t"),
            member("size", "static size_t()"),
            member("begin", "迭代器"),
            member("end", "迭代器")
        )

        /** `.` 之后：容器 / 字符串 / 流对象共有或常见的成员。 */
        val object_members = listOf(
            member("size", "size_t"),
            member("empty", "bool"),
            member("clear", "void"),
            member("begin", "iterator"),
            member("end", "iterator"),
            member("push_back", "void(T)"),
            member("pop_back", "void()"),
            member("emplace_back", "T&"),
            member("at", "T&"),
            member("front", "T&"),
            member("back", "T&"),
            member("insert", "iterator"),
            member("erase", "iterator"),
            member("find", "iterator"),
            member("count", "size_t"),
            member("substr", "std::string"),
            member("c_str", "const char*"),
            member("length", "size_t"),
            member("compare", "int"),
            member("append", "std::string&"),
            member("has_value", "bool"),
            member("value", "T&"),
            member("get", "T"),
            member("join", "void()"),
            member("detach", "void()"),
            member("lock", "void()"),
            member("unlock", "void()"),
            member("what", "const char*")
        )
    }
}
