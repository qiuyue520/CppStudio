package com.xiaonai.cppstudio.editor.config

/**
 * CppStudio 的远端服务与项目地址配置。
 *
 * 所有可选远端能力都集中在这里，**默认全部留空**：空值即功能关闭，
 * 不会向任何第三方服务器发送数据。想启用某一项时填上自己的地址即可。
 *
 * - [GITHUB_REPO]：应用自己的 GitHub 仓库（`owner/name`），用于应用内更新与关于页链接
 * - [PLUGIN_MARKET_INDEX_URL]：插件市场索引 JSON 直链
 * - [API_BASE_URL]：崩溃上报 / 文档翻译后端根地址（形如 `https://api.example.com/api`）
 */
object cppstudio_backend_config {

    /** 形如 "yourname/CppStudio"。留空 = 关闭应用内更新、关于页源码/反馈/发布链接。 */
    const val GITHUB_REPO = ""

    /** 插件市场索引（raw JSON）。留空 = 插件页「浏览」提示未配置。 */
    const val PLUGIN_MARKET_INDEX_URL = ""

    /** 后端根地址。留空 = 不上报崩溃、不做文档翻译。 */
    const val API_BASE_URL = ""

    /** 翻译接口（后端实现时填全，留空则编辑器文档翻译走原文）。 */
    val TRANSLATION_BASE_URL: String =
        if (API_BASE_URL.isBlank()) "" else API_BASE_URL.trimEnd('/') + "/translation"

    /** 崩溃上报接口。 */
    val CRASH_BASE_URL: String =
        if (API_BASE_URL.isBlank()) "" else API_BASE_URL.trimEnd('/') + "/crash"

    /** 后端鉴权 key（仅当自建后端且有鉴权时需要）。 */
    const val TRANSLATION_BACKEND_KEY = ""
    const val CRASH_BACKEND_KEY = ""
}
