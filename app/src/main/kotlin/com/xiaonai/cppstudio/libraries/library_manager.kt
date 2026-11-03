package com.xiaonai.cppstudio.libraries

import com.xiaonai.cppstudio.toolchain.apk_install
import com.xiaonai.cppstudio.toolchain.apk_packages
import com.xiaonai.cppstudio.toolchain.apk_remove
import com.xiaonai.cppstudio.toolchain.toolchain_manager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 仓库搜索返回的一个 apk 包。
 *
 * @param name 包名
 * @param version 版本
 * @param description 仓库里的描述（apk search -v 提供）
 */
data class repository_package(
    val name: String,
    val version: String,
    val description: String
)

/**
 * 库管理：已安装状态查询 + 一键安装/卸载 + 仓库搜索。
 *
 * 所有落盘操作都走 apk（proot 内执行），状态查询走 [apk_packages]
 * （直接解析包数据库，不起进程）。
 */
object library_manager {

    /** 已安装包表（name → version）。 */
    fun installed(): Map<String, String> = toolchain_manager.installed_packages()

    /** 刷新缓存（安装/卸载后调用）。 */
    fun refresh() {
        toolchain_manager.invalidate_cpp_probe()
    }

    fun is_installed(entry: library_entry, installed_map: Map<String, String> = installed()): Boolean {
        return entry.packages.any { installed_map.containsKey(it) }
    }

    /** 已安装版本（多个候选包取第一个装上的）。 */
    fun installed_version(entry: library_entry, installed_map: Map<String, String> = installed()): String? {
        entry.packages.forEach { pkg ->
            installed_map[pkg]?.let { return it }
        }
        return null
    }

    /** 已安装的精选库数量。 */
    fun installed_count(installed_map: Map<String, String> = installed()): Int {
        return library_catalog.entries.count { is_installed(it, installed_map) }
    }

    /** 精选库之外、看起来像开发包的已安装包（-dev 结尾，供「已安装」页展示）。 */
    fun installed_dev_packages(installed_map: Map<String, String> = installed()): List<repository_package> {
        val catalogued = library_catalog.entries.flatMap { it.packages }.toSet()
        return installed_map.entries
            .filter { (name, _) -> name.endsWith("-dev") && name !in catalogued }
            .sortedBy { it.key }
            .map { (name, version) -> repository_package(name, version, "") }
    }

    /**
     * 安装精选库：按候选包名依次尝试，任一成功即返回。
     *
     * 逐个尝试是必要的：Alpine 不同版本的包名会变（catch2-dev / catch2-3-dev、
     * eigen-dev / eigen3-dev），一次装不上不代表这个库没有。
     */
    suspend fun install(
        entry: library_entry,
        on_log: (String) -> Unit,
        on_progress: (Int) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        var attempt = 0
        for (package_name in entry.packages) {
            attempt++
            on_log("安装 ${entry.title}（apk add $package_name）...")
            on_progress(10 + attempt * 10)
            val ok = apk_install(package_name, on_log, on_progress)
            refresh()
            if (ok || apk_packages.is_installed(package_name)) {
                on_progress(100)
                on_log("✅ ${entry.title} 已安装：" + entry.packages.firstNotNullOfOrNull { installed()[it] }.orEmpty())
                return@withContext true
            }
            on_log("$package_name 不可用，尝试下一个包名...")
        }
        on_log("❌ ${entry.title} 安装失败，可在「仓库搜索」里查找实际包名")
        false
    }

    /** 卸载精选库（把它所有候选包一起删掉，避免残留）。 */
    suspend fun uninstall(
        entry: library_entry,
        on_log: (String) -> Unit,
        on_progress: (Int) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val targets = entry.packages.filter { apk_packages.is_installed(it) }
        if (targets.isEmpty()) {
            on_log("${entry.title} 未安装")
            return@withContext true
        }
        on_log("卸载 ${entry.title}（apk del ${targets.joinToString(" ")}）...")
        val ok = apk_remove(targets.joinToString(" "), on_log, on_progress)
        refresh()
        if (ok) on_log("✅ 已卸载 ${entry.title}") else on_log("❌ 卸载失败")
        ok
    }

    /** 直接按包名安装（仓库搜索结果用）。 */
    suspend fun install_package(
        package_name: String,
        on_log: (String) -> Unit,
        on_progress: (Int) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val ok = apk_install(package_name, on_log, on_progress)
        refresh()
        ok
    }

    /** 同步仓库索引（apk update）。搜索结果为空时提示先做这一步。 */
    suspend fun update_index(on_log: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        com.xiaonai.cppstudio.toolchain.proot_manager.execute_command_with_environment(
            command = "apk update",
            working_dir = "/root",
            on_log = on_log
        )
    }

    /**
     * 在 apk 仓库里搜索包（`apk search -v`）。
     *
     * 输出形如 `fmt-dev-11.0.2-r0 - A modern formatting library for C++`，
     * 解析成包名 + 版本 + 描述。只保留开发相关包（-dev / -dev 之外的工具链包）。
     */
    suspend fun search_repository(
        keyword: String,
        on_log: (String) -> Unit = {}
    ): List<repository_package> = withContext(Dispatchers.IO) {
        val query = keyword.trim()
        if (query.isEmpty()) return@withContext emptyList()
        val lines = mutableListOf<String>()
        val command = "apk search -v --no-cache " + shell_quote(query) + " 2>/dev/null | head -n 120"
        com.xiaonai.cppstudio.toolchain.proot_manager.execute_command_with_environment(
            command = command,
            working_dir = "/root",
            on_log = { line -> lines += line }
        )
        parse_search_output(lines)
    }

    /** 解析 apk search 输出（拆出来便于单元测试）。 */
    fun parse_search_output(lines: List<String>): List<repository_package> {
        val result = LinkedHashMap<String, repository_package>()
        // 左侧形如 fmt-dev-11.0.2-r0，右侧是描述
        val left_pattern = Regex("^(.+?)-(\\d[^-]*)-r(\\d+)$")
        for (raw in lines) {
            val line = clean_log_line(raw)
            if (line.isBlank()) continue
            val separator = line.indexOf(" - ")
            val left = if (separator >= 0) line.substring(0, separator).trim() else line
            val description = if (separator >= 0) line.substring(separator + 3).trim() else ""
            val match = left_pattern.find(left) ?: continue
            val name = match.groupValues[1].trim()
            val version = "${match.groupValues[2]}-r${match.groupValues[3]}"
            if (name.isEmpty()) continue
            result[name] = repository_package(name, version, description)
        }
        return result.values.sortedBy { it.name }.toList()
    }

    /** 去掉 proot_shell_runner 加的输出前缀（"[OUT] -- " / "[ERR] -- "）。 */
    private fun clean_log_line(line: String): String {
        var text = line.trim()
        if (text.startsWith("[OUT]")) text = text.removePrefix("[OUT]")
        if (text.startsWith("[ERR]")) text = text.removePrefix("[ERR]")
        text = text.trim()
        if (text.startsWith("--")) text = text.removePrefix("--")
        return text.trim()
    }

    private fun shell_quote(value: String): String {
        return "'" + value.replace("'", "'\\''") + "'"
    }
}
