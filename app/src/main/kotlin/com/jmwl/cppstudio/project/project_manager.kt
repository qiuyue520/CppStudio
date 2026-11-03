package com.jmwl.cppstudio.project

import com.jmwl.cppstudio.toolchain.toolchain_manager
import com.jmwl.cppstudio.toolchain.toolchain_runtime_provider

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * C/C++ 项目管理（CppStudio）。
 *
 * 与 Go 版本的差异：
 * - 项目识别靠 CMakeLists.txt / Makefile（不再是 go.mod）
 * - 新建项目写 CMakeLists.txt + 源码，并按模板生成 .clang-format / .gitignore
 * - 项目配置记录构建类型、编译器、C/C++ 标准、并行任务数、运行入口与运行参数
 */
object project_manager {
    private const val max_recent_projects = 20
    private const val project_config_dir_name = ".cppstudio"
    private const val project_config_file_name = ".cppstudio-project.json"
    private const val legacy_project_config_dir_name = ".gostudio"
    private const val legacy_project_config_file_name = ".gostudio-project.json"
    private const val cmake_file_name = "CMakeLists.txt"
    private const val clang_format_file_name = ".clang-format"

    private val json = GsonBuilder().setPrettyPrinting().create()
    private val valid_project_name = Regex("^[a-zA-Z_][a-zA-Z0-9_]*$")
    private val supported_build_types = setOf("Debug", "Release", "RelWithDebInfo", "MinSizeRel")
    private val supported_compilers = setOf("gcc", "clang")
    private val supported_cpp_stds = setOf("c++11", "c++14", "c++17", "c++20", "c++23")
    private val supported_c_stds = setOf("c99", "c11", "c17")

    /**
     * 项目统一存放于 app 内部存储（proot 已绑定到 guest 内 /home/cppstudio）。
     * 放内部存储是为了让 proot（native 进程）能稳定访问项目文件——外部存储的
     * FUSE 路径 proot 无法可靠 bind，会导致 cmake / clangd 失败。
     */
    fun default_projects_dir(): File {
        val dir = File(toolchain_runtime_provider.paths().cppstudio_dir, "projects")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** 内部存储项目目录下所有可识别为 C/C++ 工程的项目。 */
    fun list_local_projects(): List<File> {
        val root = default_projects_dir()
        return root.listFiles()
            ?.filter { it.isDirectory && is_cpp_project(it) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    private fun is_cpp_project(dir: File): Boolean {
        return File(dir, cmake_file_name).isFile ||
            listOf("Makefile", "makefile", "GNUmakefile").any { File(dir, it).isFile }
    }

    // ---------------------------------------------------------------------
    // 新建项目
    // ---------------------------------------------------------------------

    suspend fun create_project(
        name: String,
        template_id: String,
        app_name: String = "",
        package_name: String = ""
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val project_name = name.trim()

            if (!valid_project_name.matches(project_name)) {
                return@withContext Result.failure(IllegalArgumentException("项目名称只能包含字母、数字和下划线，且不能以数字开头"))
            }

            val project_dir = File(default_projects_dir(), project_name)
            if (project_dir.exists()) {
                return@withContext Result.failure(IllegalStateException("项目已存在"))
            }

            if (!project_dir.mkdirs()) {
                return@withContext Result.failure(IllegalStateException("无法创建项目目录"))
            }

            create_cpp_project(project_dir, project_name, template_id)
            write_clang_format(project_dir)
            write_gitignore(project_dir)

            val template = normalize_template_id(template_id)
            write_project_config(
                dir = project_dir,
                name = project_name,
                template = template,
                build = project_build_config(
                    build_type = if (template == "lib") "Release" else "Debug",
                    cpp_std = if (template == "hello_c") "c++17" else "c++17"
                )
            )
            Result.success(project_dir)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 在项目里新建文件/文件夹（文件按模板写初始内容）。 */
    suspend fun create_project_entry(
        project_path: String,
        parent_path: String,
        name: String,
        directory: Boolean,
        template_id: String = "PLAIN"
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val entry_name = normalize_project_entry_name(name)
            require(entry_name.isNotBlank()) { "名称不能为空" }

            val root = File(project_path).canonicalFile
            val root_path = root.toPath()
            val parent_dir = File(parent_path).canonicalFile
            val parent_dir_path = parent_dir.toPath()
            require(parent_dir.exists() && parent_dir.isDirectory && parent_dir_path.startsWith(root_path)) {
                "目标文件夹不存在或不在项目中"
            }

            val target = File(parent_dir, entry_name)
            val target_path = target.canonicalFile.toPath()
            require(target_path.startsWith(root_path)) { "路径不能超出项目目录" }
            require(!target.exists()) { if (directory) "文件夹已存在" else "文件已存在" }

            if (directory) {
                if (!target.mkdirs()) {
                    throw IllegalStateException("无法创建文件夹")
                }
            } else {
                target.parentFile?.mkdirs()
                if (!target.createNewFile()) {
                    throw IllegalStateException("无法创建文件")
                }
                create_project_entry_content(
                    template_id = template_id,
                    target = target,
                    parent_dir = parent_dir,
                    project_root = root
                )?.let { target.writeText(it) }
            }
            target
        }
    }

    /**
     * 新建文件时的模板内容。
     *
     * 头文件保护宏 / 命名空间按文件路径生成，尽量贴近工程习惯。
     */
    private fun create_project_entry_content(
        template_id: String,
        target: File,
        parent_dir: File,
        project_root: File
    ): String? {
        return when (template_id) {
            "C_SOURCE" -> c_source_template(target)
            "C_HEADER" -> c_header_template(target, project_root)
            "CPP_SOURCE" -> cpp_source_template(target)
            "CPP_HEADER" -> cpp_header_template(target, project_root)
            "CMAKE" -> cmake_list_template(project_root.name.ifBlank { "app" })
            "MAKEFILE" -> makefile_template()
            "GITIGNORE" -> gitignore_content()
            else -> null
        }
    }

    private fun header_guard(path: String): String {
        val guard = path
            .replace(Regex("[^A-Za-z0-9]"), "_")
            .uppercase()
            .trim('_')
        return if (guard.isBlank()) "PROJECT_HEADER_H" else "${guard}_H"
    }

    private fun c_source_template(target: File): String {
        val base = target.nameWithoutExtension
        return """
#include <stdio.h>

int main(void) {
    printf("Hello from $base!\n");
    return 0;
}
""".trimIndent() + "\n"
    }

    private fun c_header_template(target: File, project_root: File): String {
        val guard = header_guard(target.relativeTo(project_root).path)
        val base = target.nameWithoutExtension
        return """
#ifndef $guard
#define $guard

#ifdef __cplusplus
extern "C" {
#endif

int ${base}_add(int a, int b);

#ifdef __cplusplus
}
#endif

#endif /* $guard */
""".trimIndent() + "\n"
    }

    private fun cpp_source_template(target: File): String {
        val base = target.nameWithoutExtension
        return """
#include <iostream>

int main() {
    std::cout << "Hello from $base!" << std::endl;
    return 0;
}
""".trimIndent() + "\n"
    }

    private fun cpp_header_template(target: File, project_root: File): String {
        val guard = header_guard(target.relativeTo(project_root).path)
        val base = target.nameWithoutExtension
        return """
#ifndef $guard
#define $guard

namespace $base {

class placeholder {
public:
    int add(int a, int b) const { return a + b; }
};

} // namespace $base

#endif // $guard
""".trimIndent() + "\n"
    }

    suspend fun rename_project_entry(
        project_path: String,
        path: String,
        new_name: String
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val entry_name = normalize_project_entry_name(new_name)
            require(entry_name.isNotBlank()) { "名称不能为空" }
            require(!entry_name.contains('/') && !entry_name.contains('\\')) { "名称不能包含路径分隔符" }

            val root = File(project_path).canonicalFile
            val source = File(path).canonicalFile
            val source_path = source.toPath()
            require(source.exists() && source_path.startsWith(root.toPath())) { "节点不存在或不在项目中" }
            require(source_path != root.toPath()) { "不能重命名项目根目录" }

            val parent = source.parentFile ?: throw IllegalStateException("无法读取父目录")
            val target = File(parent, entry_name).canonicalFile
            require(target.toPath().startsWith(root.toPath())) { "路径不能超出项目目录" }
            require(!target.exists()) { "同名文件或文件夹已存在" }
            if (!source.renameTo(target)) {
                throw IllegalStateException("重命名失败")
            }
            source.absolutePath to target.absolutePath
        }
    }

    suspend fun resolve_project_entry_for_delete(project_path: String, path: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val root = File(project_path).canonicalFile
            val source = File(path).canonicalFile
            val source_path = source.toPath()
            require(source.exists() && source_path.startsWith(root.toPath())) { "节点不存在或不在项目中" }
            require(source_path != root.toPath()) { "不能删除项目根目录" }
            source
        }
    }

    suspend fun delete_project_entry(project_path: String, path: String): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val source = resolve_project_entry_for_delete(project_path, path).getOrThrow()
            val source_path = source.absolutePath
            val parent_path = source.parentFile?.absolutePath ?: File(project_path).absolutePath
            val deleted = if (source.isDirectory) source.deleteRecursively() else source.delete()
            require(deleted) { "删除失败" }
            source_path to parent_path
        }
    }

    private fun normalize_project_entry_name(name: String): String {
        return name.trim().trim('/', '\\')
    }

    // ---------------------------------------------------------------------
    // 模板
    // ---------------------------------------------------------------------

    private fun normalize_template_id(template_id: String): String {
        return when (template_id) {
            "hello_c", "hello_cpp", "cli", "lib", "threads", "sqlite", "sdl2", "ctest" -> template_id
            else -> "hello_cpp"
        }
    }

    /**
     * 按模板写 CMakeLists.txt 与源码。
     *
     * 所有模板只用系统自带能力（libc / libstdc++ / pthread），
     * 需要第三方库的模板（sqlite / sdl2）会在注释里提示用「库管理」安装。
     */
    private fun create_cpp_project(dir: File, name: String, template_id: String) {
        val template = normalize_template_id(template_id)
        when (template) {
            "hello_c" -> {
                File(dir, "main.c").writeText(c_hello_source(name))
                File(dir, cmake_file_name).writeText(cmake_c_project(name))
            }
            "cli" -> {
                File(dir, "main.cpp").writeText(cli_source(name))
                File(dir, cmake_file_name).writeText(cmake_cpp_project(name))
            }
            "lib" -> {
                File(dir, "include").mkdirs()
                File(dir, "src").mkdirs()
                File(dir, "include/mathlib.h").writeText(lib_header(name))
                File(dir, "src/mathlib.cpp").writeText(lib_source())
                File(dir, "main.cpp").writeText(lib_demo_source(name))
                File(dir, cmake_file_name).writeText(cmake_lib_project(name))
            }
            "threads" -> {
                File(dir, "main.cpp").writeText(threads_source(name))
                File(dir, cmake_file_name).writeText(cmake_cpp_project(name, extra = "find_package(Threads REQUIRED)\ntarget_link_libraries($name PRIVATE Threads::Threads)\n", std = "c++17"))
            }
            "sqlite" -> {
                File(dir, "main.cpp").writeText(sqlite_source(name))
                File(dir, cmake_file_name).writeText(
                    cmake_cpp_project(
                        name,
                        extra = "# 需要 sqlite 开发包：在「库管理」里安装 sqlite\nfind_package(PkgConfig REQUIRED)\npkg_check_modules(SQLITE REQUIRED sqlite3)\ntarget_include_directories($name PRIVATE \${SQLITE_INCLUDE_DIRS})\ntarget_link_libraries($name PRIVATE \${SQLITE_LIBRARIES})\n",
                        std = "c++17"
                    )
                )
            }
            "sdl2" -> {
                File(dir, "main.cpp").writeText(sdl_source(name))
                File(dir, cmake_file_name).writeText(
                    cmake_cpp_project(
                        name,
                        extra = "# 需要 SDL2 开发包：在「库管理」里安装 sdl2\nfind_package(PkgConfig REQUIRED)\npkg_check_modules(SDL2 REQUIRED sdl2)\ntarget_include_directories($name PRIVATE \${SDL2_INCLUDE_DIRS})\ntarget_link_libraries($name PRIVATE \${SDL2_LIBRARIES})\n",
                        std = "c++17"
                    )
                )
            }
            "ctest" -> {
                File(dir, "src").mkdirs()
                File(dir, "src/mathlib.cpp").writeText(lib_source())
                File(dir, "include").mkdirs()
                File(dir, "include/mathlib.h").writeText(lib_header(name))
                File(dir, "tests").mkdirs()
                File(dir, "tests/test_mathlib.cpp").writeText(ctest_source())
                File(dir, cmake_file_name).writeText(cmake_ctest_project(name))
            }
            else -> {
                File(dir, "main.cpp").writeText(cpp_hello_source(name))
                File(dir, cmake_file_name).writeText(cmake_cpp_project(name))
            }
        }
    }

    private fun c_hello_source(name: String): String = """
#include <stdio.h>

int main(void) {
    printf("Hello from $name!\n");
    return 0;
}
""".trimIndent() + "\n"

    private fun cpp_hello_source(name: String): String = """
#include <iostream>
#include <string>

int main(int argc, char** argv) {
    std::string who = argc > 1 ? argv[1] : "World";
    std::cout << "Hello, " << who << "! (" << "$name" << ")" << std::endl;
    return 0;
}
""".trimIndent() + "\n"

    private fun cli_source(name: String): String = """
#include <iostream>
#include <string>
#include <vector>

int main(int argc, char** argv) {
    std::vector<std::string> args(argv + 1, argv + argc);
    if (args.empty()) {
        std::cout << "用法: $name <名称> [--verbose]" << std::endl;
        return 1;
    }
    bool verbose = false;
    for (const auto& arg : args) {
        if (arg == "--verbose" || arg == "-v") verbose = true;
    }
    std::cout << "Hello, " << args.front() << "!" << std::endl;
    if (verbose) {
        std::cout << "参数个数: " << args.size() << std::endl;
        for (size_t i = 0; i < args.size(); ++i) {
            std::cout << "  [" << i << "] " << args[i] << std::endl;
        }
    }
    return 0;
}
""".trimIndent() + "\n"

    private fun lib_header(name: String): String = """
#ifndef ${name.uppercase()}_MATHLIB_H
#define ${name.uppercase()}_MATHLIB_H

namespace mathlib {

/** 求和 1..n */
long long sum_to(int n);

/** 阶乘（n <= 20） */
long long factorial(int n);

} // namespace mathlib

#endif // ${name.uppercase()}_MATHLIB_H
""".trimIndent() + "\n"

    private fun lib_source(): String = """
#include "mathlib.h"

namespace mathlib {

long long sum_to(int n) {
    long long total = 0;
    for (int i = 1; i <= n; ++i) total += i;
    return total;
}

long long factorial(int n) {
    long long result = 1;
    for (int i = 2; i <= n; ++i) result *= i;
    return result;
}

} // namespace mathlib
""".trimIndent() + "\n"

    private fun lib_demo_source(name: String): String = """
#include <iostream>
#include "mathlib.h"

int main() {
    std::cout << "$name: sum_to(100) = " << mathlib::sum_to(100) << std::endl;
    std::cout << "$name: factorial(10) = " << mathlib::factorial(10) << std::endl;
    return 0;
}
""".trimIndent() + "\n"

    private fun threads_source(name: String): String = """
#include <iostream>
#include <mutex>
#include <thread>
#include <vector>

int main() {
    std::mutex mtx;
    long long total = 0;
    const int workers = 4;
    const int per_worker = 250000;

    std::vector<std::thread> pool;
    for (int w = 0; w < workers; ++w) {
        pool.emplace_back([&, w] {
            long long partial = 0;
            for (int i = 0; i < per_worker; ++i) partial += w * per_worker + i;
            std::lock_guard<std::mutex> lock(mtx);
            total += partial;
        });
    }
    for (auto& t : pool) t.join();

    std::cout << "$name: 多线程求和结果 = " << total << std::endl;
    std::cout << "硬件并发度: " << std::thread::hardware_concurrency() << std::endl;
    return 0;
}
""".trimIndent() + "\n"

    private fun sqlite_source(name: String): String = """
#include <cstdio>
#include <sqlite3.h>

int main() {
    sqlite3* db = nullptr;
    if (sqlite3_open("$name.db", &db) != SQLITE_OK) {
        std::fprintf(stderr, "打开数据库失败: %s\n", sqlite3_errmsg(db));
        return 1;
    }
    const char* ddl =
        "CREATE TABLE IF NOT EXISTS users ("
        "id INTEGER PRIMARY KEY AUTOINCREMENT,"
        "name TEXT NOT NULL,"
        "age INTEGER NOT NULL);";
    char* err = nullptr;
    if (sqlite3_exec(db, ddl, nullptr, nullptr, &err) != SQLITE_OK) {
        std::fprintf(stderr, "建表失败: %s\n", err);
        sqlite3_free(err);
        sqlite3_close(db);
        return 1;
    }
    sqlite3_exec(db, "INSERT INTO users (name, age) VALUES ('Alice', 20);", nullptr, nullptr, nullptr);

    sqlite3_stmt* stmt = nullptr;
    sqlite3_prepare_v2(db, "SELECT id, name, age FROM users;", -1, &stmt, nullptr);
    while (sqlite3_step(stmt) == SQLITE_ROW) {
        std::printf("%lld %s %lld\n",
            (long long)sqlite3_column_int64(stmt, 0),
            (const char*)sqlite3_column_text(stmt, 1),
            (long long)sqlite3_column_int64(stmt, 2));
    }
    sqlite3_finalize(stmt);
    sqlite3_close(db);
    std::printf("$name: sqlite 示例完成\n");
    return 0;
}
""".trimIndent() + "\n"

    private fun sdl_source(name: String): String = """
#include <cstdio>
#include <SDL2/SDL.h>

int main(int argc, char** argv) {
    (void)argc;
    (void)argv;
    if (SDL_Init(SDL_INIT_VIDEO) != 0) {
        std::fprintf(stderr, "SDL_Init 失败: %s\n", SDL_GetError());
        return 1;
    }
    SDL_Window* window = SDL_CreateWindow("$name",
        SDL_WINDOWPOS_CENTERED, SDL_WINDOWPOS_CENTERED, 640, 480, SDL_WINDOW_SHOWN);
    if (!window) {
        std::fprintf(stderr, "创建窗口失败: %s\n", SDL_GetError());
        SDL_Quit();
        return 1;
    }
    std::printf("$name: 窗口已创建（点关闭按钮退出）\n");
    bool running = true;
    SDL_Event event;
    while (running) {
        while (SDL_PollEvent(&event)) {
            if (event.type == SDL_QUIT) running = false;
        }
        SDL_Delay(16);
    }
    SDL_DestroyWindow(window);
    SDL_Quit();
    return 0;
}
""".trimIndent() + "\n"

    private fun ctest_source(): String = """
#include <cassert>
#include <cstdio>
#include "mathlib.h"

int main() {
    assert(mathlib::sum_to(100) == 5050);
    assert(mathlib::factorial(5) == 120);
    assert(mathlib::factorial(10) == 3628800);
    std::printf("all tests passed\n");
    return 0;
}
""".trimIndent() + "\n"

    private fun cmake_header(name: String): String = """
cmake_minimum_required(VERSION 3.20)
project($name LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

""".trimIndent() + "\n"

    private fun cmake_cpp_project(name: String, extra: String = "", std: String = "c++17"): String {
        val standard = std.removePrefix("c++")
        val header = """
cmake_minimum_required(VERSION 3.20)
project($name LANGUAGES CXX)

set(CMAKE_CXX_STANDARD $standard)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

""".trimIndent() + "\n"
        return header + "add_executable($name main.cpp)\n" + extra
    }

    private fun cmake_c_project(name: String): String = """
cmake_minimum_required(VERSION 3.20)
project($name LANGUAGES C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

add_executable($name main.c)
""".trimIndent() + "\n"

    private fun cmake_lib_project(name: String): String = """
cmake_minimum_required(VERSION 3.20)
project($name LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

add_library(mathlib STATIC src/mathlib.cpp)
target_include_directories(mathlib PUBLIC ${'$'}{CMAKE_CURRENT_SOURCE_DIR}/include)

add_executable($name main.cpp)
target_link_libraries($name PRIVATE mathlib)
""".trimIndent() + "\n"

    private fun cmake_ctest_project(name: String): String = """
cmake_minimum_required(VERSION 3.20)
project($name LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

enable_testing()

add_library(mathlib STATIC src/mathlib.cpp)
target_include_directories(mathlib PUBLIC ${'$'}{CMAKE_CURRENT_SOURCE_DIR}/include)

add_executable(test_mathlib tests/test_mathlib.cpp)
target_link_libraries(test_mathlib PRIVATE mathlib)
add_test(NAME mathlib_tests COMMAND test_mathlib)
""".trimIndent() + "\n"

    private fun cmake_list_template(name: String): String = """
cmake_minimum_required(VERSION 3.20)
project($name LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

file(GLOB SOURCES ${'$'}{CMAKE_CURRENT_SOURCE_DIR}/src/*.cpp)
add_executable($name ${'$'}{SOURCES})
""".trimIndent() + "\n"

    private fun makefile_template(): String = """
CXX ?= g++
CXXFLAGS ?= -std=c++17 -O2 -Wall -Wextra

SRC := ${'$'}(wildcard src/*.cpp)
OBJ := ${'$'}(SRC:.cpp=.o)
TARGET := app

all: ${'$'}(TARGET)

${'$'}(TARGET): ${'$'}(OBJ)
	${'$'}(CXX) ${'$'}(CXXFLAGS) -o ${'$'}@ ${'$'}^

%.o: %.cpp
	${'$'}(CXX) ${'$'}(CXXFLAGS) -c ${'$'}< -o ${'$'}@

clean:
	rm -f ${'$'}(OBJ) ${'$'}(TARGET)

.PHONY: all clean
""".trimIndent() + "\n"

    // ---------------------------------------------------------------------
    // 配置文件
    // ---------------------------------------------------------------------

    private fun project_config_file(project_dir: File): File {
        return File(File(project_dir, project_config_dir_name), project_config_file_name)
    }

    /** 写一份 .clang-format（CLion / clang-format 默认风格：Google 变体）。 */
    private fun write_clang_format(project_dir: File) {
        val file = File(project_dir, clang_format_file_name)
        if (file.exists()) return
        file.writeText(clang_format_content())
    }

    private fun clang_format_content(): String = """
BasedOnStyle: Google
IndentWidth: 4
ColumnLimit: 100
AccessModifierOffset: -4
AllowShortFunctionsOnASingleLine: Inline
SortIncludes: true
""".trimIndent() + "\n"

    private fun gitignore_content(): String = """
# 构建产物
/build/
/out/
/cmake-build-*/
CMakeCache.txt
CMakeFiles/
compile_commands.json
!CMakeLists.txt

# 编辑器
/.idea/
/.vscode/
*.swp

# 系统
.DS_Store
""".trimIndent() + "\n"

    private fun write_gitignore(project_dir: File) {
        val file = File(project_dir, ".gitignore")
        if (file.exists()) return
        file.writeText(gitignore_content())
    }

    /**
     * 保证项目带 .clang-format（格式化按钮 / clangd 格式化都用它）。
     * 已有文件不覆盖。
     */
    fun ensure_project_clang_format(path: String): Result<Unit> = runCatching {
        val project_dir = File(path)
        require(project_dir.isDirectory) { "项目目录不存在" }
        write_clang_format(project_dir)
    }

    fun ensure_project_config(path: String): Result<Unit> {
        return runCatching {
            val project_dir = File(path)
            require(project_dir.exists() && project_dir.isDirectory) { "项目目录不存在" }
            require(is_cpp_project(project_dir)) { "不是 C/C++ 项目（缺少 CMakeLists.txt 或 Makefile）" }

            migrate_legacy_project_config(project_dir)

            val config_file = project_config_file(project_dir)
            if (config_file.isFile) return@runCatching

            write_project_config(
                dir = project_dir,
                name = project_dir.name.ifBlank { "cpp_project" },
                template = detect_template(project_dir),
                build = project_build_config()
            )
        }
    }

    private fun detect_template(project_dir: File): String {
        return if (File(project_dir, cmake_file_name).isFile) "imported" else "makefile"
    }

    /** 一次性迁移：旧版（还叫 GoStudio 时）的 .gostudio/ 配置目录重命名为 .cppstudio/。 */
    private fun migrate_legacy_project_config(project_dir: File) {
        val new_dir = File(project_dir, project_config_dir_name)
        if (new_dir.exists()) return
        val legacy_dir = File(project_dir, legacy_project_config_dir_name)
        if (!legacy_dir.exists()) return
        runCatching {
            legacy_dir.renameTo(new_dir)
            val legacy_file = File(new_dir, legacy_project_config_file_name)
            if (legacy_file.isFile) {
                legacy_file.renameTo(File(new_dir, project_config_file_name))
            }
        }
    }

    private fun write_project_config(
        dir: File,
        name: String,
        template: String,
        build: project_build_config = project_build_config()
    ) {
        val config = project_config(
            name = name,
            template = template,
            created = System.currentTimeMillis(),
            build = normalize_project_build_config(build)
        )
        project_config_file(dir).apply {
            parentFile?.mkdirs()
            writeText(json.toJson(config) + "\n")
        }
    }

    fun read_project_build_config(path: String): project_build_config {
        return read_project_ide_config(path).build
    }

    fun read_project_ide_config(path: String): project_ide_config {
        return try {
            val config_file = project_config_file(File(path))
            val config = json.fromJson(config_file.readText(), project_config::class.java)
            project_ide_config(
                compiler_version = toolchain_manager.installed_cpp()?.version.orEmpty(),
                build = normalize_project_build_config(config?.build ?: project_build_config())
            )
        } catch (_: Exception) {
            project_ide_config()
        }
    }

    /** 读取项目模板类型；非 CppStudio 项目或读取失败返回空串。 */
    fun read_project_template(path: String): String {
        return try {
            val config_file = project_config_file(File(path))
            json.fromJson(config_file.readText(), project_config::class.java)?.template.orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    fun save_project_ide_config(path: String, ide_config: project_ide_config): Result<Unit> {
        return runCatching {
            val project_dir = File(path)
            require(is_cpp_project(project_dir)) { "不是 C/C++ 项目，无法保存配置（缺少 CMakeLists.txt 或 Makefile）" }

            val config_file = project_config_file(project_dir)
            config_file.parentFile?.mkdirs()
            val config = if (config_file.isFile) {
                json.fromJson(config_file.readText(), project_config::class.java)
            } else null
            val normalized_build = normalize_project_build_config(ide_config.build)
            val updated = (config ?: project_config(
                name = project_dir.name,
                template = detect_template(project_dir),
                created = System.currentTimeMillis()
            )).copy(build = normalized_build)
            config_file.writeText(json.toJson(updated) + "\n")
        }
    }

    private fun normalize_project_build_config(build: project_build_config): project_build_config {
        return project_build_config(
            build_type = build.build_type.takeIf { it in supported_build_types } ?: "Debug",
            compiler = build.compiler.takeIf { it in supported_compilers } ?: "gcc",
            cpp_std = build.cpp_std.takeIf { it in supported_cpp_stds } ?: "c++17",
            c_std = build.c_std.takeIf { it in supported_c_stds } ?: "c11",
            cmake_args = build.cmake_args.orEmpty().trim(),
            make_args = build.make_args.orEmpty().trim(),
            parallel_jobs = build.parallel_jobs.coerceIn(0, 16),
            run_entry = build.run_entry.orEmpty().trim(),
            run_args = build.run_args.orEmpty().trim()
        )
    }

    fun get_project_last_opened(path: String): String {
        return try {
            val last_modified = File(path).takeIf { it.exists() }?.lastModified() ?: System.currentTimeMillis()
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(last_modified))
        } catch (e: Exception) {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        }
    }

    fun update_project_opened_time(path: String) {
        try {
            val project_dir = File(path)
            if (project_dir.exists()) {
                project_dir.setLastModified(System.currentTimeMillis())
            }
        } catch (_: Exception) {
        }
    }

    fun get_project_info(path: String): project_info? {
        return try {
            require_cppstudio_project_info(path)
        } catch (_: Exception) {
            null
        }
    }

    private fun require_cppstudio_project_info(path: String): project_info {
        val project_dir = File(path)
        require(project_dir.exists() && project_dir.isDirectory) { "项目目录不存在" }
        require(is_cpp_project(project_dir)) { "不是 C/C++ 项目（缺少 CMakeLists.txt 或 Makefile）" }

        migrate_legacy_project_config(project_dir)
        val config_file = project_config_file(project_dir)
        require(config_file.isFile) { "不是 CppStudio 项目，缺少 $project_config_dir_name/$project_config_file_name" }

        val config = try {
            json.fromJson(config_file.readText(), project_config::class.java)
        } catch (e: Exception) {
            throw IllegalStateException("项目配置文件损坏")
        } ?: throw IllegalStateException("项目配置文件损坏")

        require(config.name.isNotBlank()) { "项目配置缺少名称" }
        require(config.template.isNotBlank()) { "项目配置缺少模板类型" }

        return project_info(
            name = config.name,
            path = project_dir.absolutePath,
            compiler_version = toolchain_manager.installed_cpp()?.version.orEmpty(),
            template = config.template,
            build_system = if (File(project_dir, cmake_file_name).isFile) "CMake" else "Make"
        )
    }

    suspend fun get_recent_projects(): List<recent_project_info> = withContext(Dispatchers.IO) {
        val records = load_recent_project_records()
            .filter { runCatching { require_cppstudio_project_info(it.path) }.isSuccess }
            .sortedByDescending { it.opened_at }
            .take(max_recent_projects)
        write_recent_project_records(records)
        records.map { create_recent_project_info(it) }
    }

    suspend fun check_project_toolchain(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val info = require_cppstudio_project_info(path)
            val project_dir = File(info.path)
            val environment = toolchain_manager.project_environment(project_dir.absolutePath)
            require(environment.missing.isEmpty()) { environment.missing.joinToString("；") }
        }
    }

    suspend fun add_recent_project(path: String): Result<recent_project_info> = withContext(Dispatchers.IO) {
        try {
            val project_path = normalize_project_path(path)
            if (project_path.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("项目路径不能为空"))
            }

            val project_dir = File(project_path)
            if (!project_dir.exists()) {
                return@withContext Result.failure(IllegalArgumentException("项目目录不存在"))
            }
            if (!project_dir.isDirectory) {
                return@withContext Result.failure(IllegalArgumentException("项目路径不是目录"))
            }

            val now = System.currentTimeMillis()
            val info = require_cppstudio_project_info(project_path)
            val record = recent_project_record(
                name = info.name,
                path = project_path,
                compiler_version = info.compiler_version,
                template = info.template,
                opened_at = now
            )

            val records = read_recent_project_records()
                .filterNot { normalize_project_path(it.path) == project_path }
                .toMutableList()
            records.add(0, record)
            write_recent_project_records(records.take(max_recent_projects))
            runCatching { project_dir.setLastModified(now) }

            Result.success(create_recent_project_info(record))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun remove_recent_project(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val project_path = normalize_project_path(path)
            val records = load_recent_project_records()
                .filterNot { normalize_project_path(it.path) == project_path }
            write_recent_project_records(records)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun recent_projects_file(): File {
        return File(toolchain_runtime_provider.paths().home_dir.parentFile, "recent_projects.json")
    }

    private fun load_recent_project_records(): MutableList<recent_project_record> {
        val file = recent_projects_file()
        if (file.exists()) {
            return read_recent_project_records()
        }

        val discovered_records = discover_project_records()
            .sortedByDescending { it.opened_at }
            .take(max_recent_projects)
        write_recent_project_records(discovered_records)
        return discovered_records.toMutableList()
    }

    private fun read_recent_project_records(): MutableList<recent_project_record> {
        return try {
            val file = recent_projects_file()
            if (!file.exists()) return mutableListOf()

            val type = object : TypeToken<List<recent_project_record>>() {}.type
            val records = json.fromJson<List<recent_project_record>>(file.readText(), type) ?: emptyList()
            records
                .filter { it.path.isNotBlank() }
                .distinctBy { normalize_project_path(it.path) }
                .toMutableList()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun write_recent_project_records(records: List<recent_project_record>) {
        val file = recent_projects_file()
        file.parentFile?.mkdirs()
        file.writeText(json.toJson(records.take(max_recent_projects)))
    }

    private fun discover_project_records(): List<recent_project_record> {
        return try {
            val projects = list_local_projects()
                .sortedByDescending { it.lastModified() }
                .take(max_recent_projects)

            projects.mapNotNull { project_dir ->
                val info = runCatching { require_cppstudio_project_info(project_dir.absolutePath) }.getOrNull()
                    ?: return@mapNotNull null
                recent_project_record(
                    name = info.name,
                    path = normalize_project_path(project_dir.absolutePath),
                    compiler_version = info.compiler_version,
                    template = info.template,
                    opened_at = project_dir.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun normalize_project_path(path: String): String {
        val trimmed_path = path.trim()
        if (trimmed_path.isBlank()) return ""

        return try {
            File(trimmed_path).canonicalPath
        } catch (_: Exception) {
            File(trimmed_path).absolutePath
        }
    }

    private fun create_recent_project_info(record: recent_project_record): recent_project_info {
        val project_info = get_project_info(record.path)
        val project_dir = File(record.path)

        return recent_project_info(
            name = project_info?.name ?: record.name.ifBlank { project_dir.name },
            path = record.path,
            compiler_version = project_info?.compiler_version ?: record.compiler_version,
            template = project_info?.template ?: record.template.ifBlank { "hello_cpp" },
            build_system = project_info?.build_system.orEmpty(),
            last_opened = format_last_opened(record.opened_at),
            opened_at = record.opened_at
        )
    }

    private fun format_last_opened(opened_at: Long): String {
        val diff = (System.currentTimeMillis() - opened_at).coerceAtLeast(0L)
        val minute = 60_000L
        val hour = 60 * minute
        val day = 24 * hour

        return when {
            diff < minute -> "刚刚"
            diff < hour -> "${diff / minute}分钟前"
            diff < day -> "${diff / hour}小时前"
            diff < 2 * day -> "昨天"
            diff < 7 * day -> "${diff / day}天前"
            else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(opened_at))
        }
    }
}

data class project_info(
    val name: String,
    val path: String,
    val compiler_version: String,
    val template: String,
    val build_system: String = ""
)

/**
 * 项目构建配置（CppStudio）。
 *
 * @param build_type Debug / Release / RelWithDebInfo / MinSizeRel
 * @param compiler gcc / clang（决定 CC/CXX）
 * @param cpp_std C++ 标准（c++11/14/17/20/23）
 * @param c_std C 标准（c99/c11/c17）
 * @param cmake_args 额外 CMake 参数（配置阶段）
 * @param make_args 额外 make 参数
 * @param parallel_jobs 并行任务数（0 = 自动）
 * @param run_entry 运行入口（相对 build 目录的可执行文件；空=自动探测）
 * @param run_args 运行参数
 */
data class project_build_config(
    val build_type: String = "Debug",
    val compiler: String = "gcc",
    val cpp_std: String = "c++17",
    val c_std: String = "c11",
    val cmake_args: String = "",
    val make_args: String = "",
    val parallel_jobs: Int = 0,
    val run_entry: String = "",
    val run_args: String = ""
)

data class project_ide_config(
    val compiler_version: String = "",
    val build: project_build_config = project_build_config()
)

private data class project_config(
    val name: String = "",
    val template: String = "",
    val created: Long = 0L,
    val build: project_build_config = project_build_config()
)

data class recent_project_info(
    val name: String,
    val path: String,
    val compiler_version: String,
    val template: String,
    val build_system: String = "",
    val last_opened: String,
    val opened_at: Long
)

private data class recent_project_record(
    val name: String = "",
    val path: String = "",
    val compiler_version: String = "",
    val template: String = "",
    val opened_at: Long = 0L
)
