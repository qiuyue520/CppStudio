package com.jmwl.cppstudio.project

import java.io.File

/** 项目构建系统类型。 */
enum class project_kind {
    /** CMake 项目（根目录有 CMakeLists.txt） */
    CMAKE,

    /** 仅 Makefile 的项目（根目录有 Makefile / makefile / GNUmakefile） */
    MAKE,
    UNKNOWN
}

/**
 * 项目探测结果。
 *
 * @param root_path 项目根目录（host 绝对路径）
 * @param kind 构建系统类型
 * @param build_file_path 构建描述文件（CMakeLists.txt / Makefile）的绝对路径
 * @param build_dir 构建输出目录（CMake 用 <root>/build；Make 项目产物就在根目录）
 */
data class detected_project(
    val root_path: String,
    val kind: project_kind,
    val build_file_path: String? = null,
    val build_dir: String? = null
) {
    val is_cpp: Boolean
        get() = kind != project_kind.UNKNOWN

    val is_cmake: Boolean
        get() = kind == project_kind.CMAKE
}

object project_detector {
    const val cmake_file_name = "CMakeLists.txt"
    const val default_build_dir = "build"

    private val makefile_names = listOf("Makefile", "makefile", "GNUmakefile")

    /**
     * 探测项目：先看 CMakeLists.txt，再看 Makefile，都没有则 UNKNOWN。
     *
     * 兜底：根目录没有构建文件时，向下找一层子目录里的 CMakeLists.txt
     * （克隆下来的仓库经常把工程放在子目录里，例如 `repo/src/CMakeLists.txt`）。
     */
    fun detect_project(project_path: String): detected_project {
        val root = File(project_path).absoluteFile
        val root_path = root.absolutePath
        if (!root.isDirectory) {
            return detected_project(root_path = root_path, kind = project_kind.UNKNOWN)
        }

        val cmake = File(root, cmake_file_name)
        if (cmake.isFile) {
            return cmake_project(root, cmake)
        }

        val makefile = makefile_names
            .map { File(root, it) }
            .firstOrNull { it.isFile }
        if (makefile != null) {
            return detected_project(
                root_path = root_path,
                kind = project_kind.MAKE,
                build_file_path = makefile.absolutePath,
                build_dir = root_path
            )
        }

        val nested = list_directories(root)
            .map { File(it, cmake_file_name) }
            .firstOrNull { it.isFile }
        if (nested != null) {
            return cmake_project(nested.parentFile!!, nested)
        }

        return detected_project(root_path = root_path, kind = project_kind.UNKNOWN)
    }

    private fun cmake_project(root: File, cmake: File): detected_project {
        return detected_project(
            root_path = root.absolutePath,
            kind = project_kind.CMAKE,
            build_file_path = cmake.absolutePath,
            build_dir = File(root, default_build_dir).absolutePath
        )
    }

    private fun list_directories(root: File): List<File> {
        return root.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") && it.name != default_build_dir }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }
}
