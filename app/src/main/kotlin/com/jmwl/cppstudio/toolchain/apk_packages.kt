package com.jmwl.cppstudio.toolchain

import com.jmwl.cppstudio.toolchain.runtime.toolchain_runtime_paths
import java.io.File

/**
 * Alpine rootfs 里的 apk 包数据库查询。
 *
 * 直接解析 `/lib/apk/db/installed`（apk 的包数据库，纯文本），不需要启动 proot：
 * 库管理页要实时显示「已安装 / 版本」，这个查询在 Compose 重组热路径上，
 * 起一次 proot 就是几百毫秒，不可接受。
 */
object apk_packages {

    /** apk 包数据库在 guest 内的固定路径。 */
    private const val installed_db_path = "lib/apk/db/installed"

    /**
     * 读取全部已安装包（name → version）。
     *
     * apk 数据库格式：每个包一个块，块内每行 `X:value`，块之间空行分隔。
     * 关注 `P:`（包名）和 `V:`（版本）两行。
     */
    fun installed(): Map<String, String> {
        val db = File(toolchain_runtime_provider.paths().rootfs_dir, installed_db_path)
        if (!db.isFile) return emptyMap()
        return try {
            parse_installed_db(db.readText())
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /** 解析 apk installed 数据库文本（拆出来便于单元测试）。 */
    fun parse_installed_db(text: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        var name: String? = null
        var version: String? = null
        for (raw_line in text.lineSequence()) {
            if (raw_line.isBlank()) {
                commit(result, name, version)
                name = null
                version = null
                continue
            }
            when {
                raw_line.startsWith("P:") -> name = raw_line.substring(2).trim()
                raw_line.startsWith("V:") -> version = raw_line.substring(2).trim()
            }
        }
        commit(result, name, version)
        return result
    }

    private fun commit(target: MutableMap<String, String>, name: String?, version: String?) {
        if (!name.isNullOrBlank()) {
            target[name] = version.orEmpty()
        }
    }

    /** 是否已安装某个包（含已安装包的版本号）。 */
    fun version_of(package_name: String, installed: Map<String, String> = installed()): String? {
        return installed[package_name]
    }

    fun is_installed(package_name: String, installed: Map<String, String> = installed()): Boolean {
        return installed.containsKey(package_name)
    }

    /** 一次查询里判断一组包，返回缺失的那些。 */
    fun missing(package_names: List<String>, installed: Map<String, String> = installed()): List<String> {
        return package_names.filterNot { installed.containsKey(it) }
    }

    /** rootfs 是否已经装好（apk 数据库存在即可用）。 */
    fun rootfs_ready(paths: toolchain_runtime_paths = toolchain_runtime_provider.paths()): Boolean {
        return File(paths.rootfs_dir, installed_db_path).isFile
    }
}
