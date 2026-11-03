package com.xiaonai.cppstudio.lsp.clangd

import io.github.rosemoe.sora.lsp.client.connection.StreamConnectionProvider
import io.github.rosemoe.sora.lsp.client.languageserver.LspFeature
import io.github.rosemoe.sora.lsp.client.languageserver.serverdefinition.CustomLanguageServerDefinition
import io.github.rosemoe.sora.lsp.client.languageserver.serverdefinition.LanguageServerDefinition
import java.net.URI

/** clangd 处理的文件扩展名（C / C++ 源码与头文件）。 */
private val clangd_extensions = listOf(
    "c",
    "h",
    "cc",
    "cp",
    "cpp",
    "cxx",
    "c++",
    "hh",
    "hpp",
    "hxx",
    "h++",
    "ipp",
    "tpp",
    "inl",
    "ino"
)

/**
 * clangd 初始化选项。
 *
 * - `clangdFileStatus`：编辑器里显示「索引中/就绪」指示
 * - `fallbackFlags`：没有 compile_commands.json 时用的兜底编译参数
 *   （C++17 + 常见告警，保证补全至少能解析标准库）
 * - `compilationDatabasePath`：与 --compile-commands-dir 等价的 LSP 侧写法
 */
private val clangd_initialization_options: Map<String, Any> = mapOf(
    "clangdFileStatus" to true,
    "fallbackFlags" to listOf("-std=c++17", "-Wall"),
    "compilationDatabasePath" to "build"
)

/**
 * 为 C/C++ 扩展名创建 clangd 的 LanguageServerDefinition 列表。
 * 每个源码/头文件会通过 [config_factory] 获得 clangd 连接。
 */
fun create_clangd_language_server_definitions(
    config_factory: (working_dir: String) -> clangd_lsp_config,
    disabled_features: Set<LspFeature> = emptySet()
): List<LanguageServerDefinition> {
    return clangd_extensions.map { extension ->
        object : CustomLanguageServerDefinition(
            ext = extension,
            serverConnectProvider = CustomLanguageServerDefinition.ServerConnectProvider { working_dir ->
                create_clangd_connection_provider(config_factory(working_dir))
            },
            name = clangd_server_name,
            expectedCapabilitiesOverride = null,
            extensionsOverride = listOf(extension)
        ) {
            override val disabledFeatures: Set<LspFeature> = disabled_features

            override fun getInitializationOptions(uri: URI?): Any? {
                return clangd_initialization_options
            }
        }
    }
}

fun create_clangd_connection_provider(config: clangd_lsp_config): StreamConnectionProvider {
    return clangd_stream_connection_provider(config)
}

const val clangd_server_name = "clangd"
