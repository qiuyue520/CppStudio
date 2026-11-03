package com.jmwl.cppstudio.editor.core

/**
 * C/C++ 补全关键字（用于 sora 编辑器的基础词法补全；语义补全由 clangd 提供）。
 *
 * 覆盖 C++17/20 关键字 + 常用标准库类型/算法名，让没装 clangd 时也能补全出个大概。
 */
val cpp_completion_keywords = arrayOf(
    // 控制流
    "if", "else", "for", "while", "do", "switch", "case", "default",
    "break", "continue", "return", "goto", "try", "catch", "throw", "noexcept",
    // 声明与类型
    "class", "struct", "union", "enum", "namespace", "using", "typedef",
    "template", "typename", "concept", "requires", "auto", "decltype",
    "const", "constexpr", "consteval", "constinit", "static", "extern",
    "inline", "virtual", "override", "final", "explicit", "mutable",
    "friend", "public", "private", "protected", "operator", "this",
    "new", "delete", "sizeof", "alignof", "static_assert", "static_cast",
    "dynamic_cast", "const_cast", "reinterpret_cast", "typeid",
    "void", "bool", "char", "char8_t", "char16_t", "char32_t", "wchar_t",
    "short", "int", "long", "float", "double", "signed", "unsigned",
    "size_t", "ptrdiff_t", "int8_t", "int16_t", "int32_t", "int64_t",
    "uint8_t", "uint16_t", "uint32_t", "uint64_t",
    // 字面量 / 修饰
    "true", "false", "nullptr", "volatile", "register", "thread_local",
    "co_await", "co_return", "co_yield", "export",
    // 标准库常用
    "std", "string", "string_view", "vector", "array", "map", "unordered_map",
    "set", "unordered_set", "deque", "list", "queue", "stack", "pair",
    "tuple", "optional", "variant", "any", "function", "shared_ptr",
    "unique_ptr", "weak_ptr", "make_shared", "make_unique", "move",
    "forward", "swap", "begin", "end", "size", "empty", "push_back",
    "emplace_back", "insert", "erase", "find", "sort", "reverse",
    "accumulate", "min", "max", "abs", "cout", "cin", "cerr", "endl",
    "printf", "fprintf", "snprintf", "scanf", "malloc", "free",
    "memcpy", "memset", "strlen", "strcpy", "strcmp"
)
