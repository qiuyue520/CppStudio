package com.xiaonai.cppstudio.editor.core

import java.io.File

fun replace_path_prefix(path: String, old_prefix: String, new_prefix: String): String {
    val normalized_old = old_prefix.trimEnd(File.separatorChar)
    return when {
        path == normalized_old -> new_prefix
        path.startsWith(normalized_old + File.separator) -> new_prefix + path.removePrefix(normalized_old)
        else -> path
    }
}

fun is_same_or_child_path(path: String, parent_path: String): Boolean {
    val normalized_parent = parent_path.trimEnd(File.separatorChar)
    return path == normalized_parent || path.startsWith(normalized_parent + File.separator)
}

fun is_readable_project_file(project_dir: File, file: File): Boolean {
    if (!file.exists() || !file.isFile) return false
    return runCatching {
        val root_path = project_dir.canonicalFile.toPath()
        val file_path = file.canonicalFile.toPath()
        file_path.startsWith(root_path)
    }.getOrDefault(false)
}

/** C/C++ 源码与头文件后缀（决定是否启动 clangd、是否用 clang-format）。 */
val cpp_source_suffixes = listOf(
    ".c", ".h",
    ".cc", ".cp", ".cpp", ".cxx", ".c++",
    ".hh", ".hpp", ".hxx", ".h++",
    ".ipp", ".tpp", ".inl", ".ino"
)

fun is_cpp_source_file(file_path: String?): Boolean {
    if (file_path.isNullOrBlank()) return false
    val name = File(file_path).name
    return cpp_source_suffixes.any { name.endsWith(it, ignoreCase = true) }
}

/** CMake 构建描述文件（保存后需要重新配置）。 */
fun is_cmake_file(file_path: String?): Boolean {
    if (file_path.isNullOrBlank()) return false
    val name = File(file_path).name
    return name.equals("CMakeLists.txt", ignoreCase = true) || name.endsWith(".cmake", ignoreCase = true)
}
